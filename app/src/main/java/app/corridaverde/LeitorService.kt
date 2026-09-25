package app.corridaverde

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Lê a oferta do app de motorista da Uber e mostra o popup.
 * Só lê: não toca na tela, não aceita nem recusa corridas.
 *
 * O cartão da oferta não aparece na árvore da janela da Uber: ele só chega pelos
 * eventos (o texto e o nó de origem de cada um). Por isso a oferta é lida no próprio
 * evento, e para saber se ela sumiu o app olha de novo o nó de onde ela veio.
 */
class LeitorService : AccessibilityService() {
    /** Tela: popup e chaveAtual. */
    private val handler = Handler(Looper.getMainLooper())
    /** Leitura dos nós, que é lenta (cada nó é uma consulta à Uber), fica fora da tela. */
    private val trabalho = HandlerThread("leitor").apply { start() }
    private val fundo = Handler(trabalho.looper)

    private lateinit var popup: Popup
    private var chaveAtual: String? = null

    // Só usados na linha de trabalho.
    private var noOferta: AccessibilityNodeInfo? = null
    private var ultimoDiagnostico = ""
    private var ultimoStatus = 0L
    private var eventosDaUber = 0
    private val ultimosEventos = ArrayDeque<String>()
    private val historicoJanelas = ArrayDeque<String>()

    /** Confere se a oferta ainda está na tela; se sumiu, esconde o popup. */
    private val conferir = object : Runnable {
        override fun run() {
            val no = noOferta ?: return
            val oferta = runCatching { if (no.refresh()) LeitorOferta.ler(textosDo(no)) else null }.getOrNull()
            if (oferta == null) {
                noOferta = null
                handler.post(esconder)
                return
            }
            mostrar(oferta)
            fundo.postDelayed(this, 400)
        }
    }

    /** Em segundo plano só dá para atualizar sem perguntar a partir do Android 12. */
    private val buscarAtualizacao = object : Runnable {
        override fun run() {
            if (chaveAtual == null && Build.VERSION.SDK_INT >= 31) Atualizador.verificar(this@LeitorService, perguntar = false)
            handler.postDelayed(this, 6 * 60 * 60_000L)
        }
    }
    private val esconder = Runnable {
        popup.esconder()
        chaveAtual = null
    }

    override fun onServiceConnected() {
        popup = Popup(this)
        instancia = this
        handler.postDelayed(buscarAtualizacao, 60_000)
    }

    override fun onDestroy() {
        instancia = null
        handler.removeCallbacksAndMessages(null)
        fundo.removeCallbacksAndMessages(null)
        trabalho.quitSafely()
        if (::popup.isInitialized) popup.esconder()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.packageName?.toString() != UBER) return
        // O evento é reciclado quando esta função volta: copia o que interessa antes.
        val textos = mutableListOf<String>()
        e.text.mapNotNullTo(textos) { it?.toString()?.takeIf { t -> t.isNotBlank() } }
        e.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { textos += it }
        val fonte = e.source
        val tipo = e.eventType
        val classe = e.className?.toString()
        fundo.post { runCatching { processar(textos, fonte, tipo, classe) } }
    }

    private fun processar(textosDoEvento: List<String>, fonte: AccessibilityNodeInfo?, tipo: Int, classe: String?) {
        eventosDaUber++
        val textos = textosDoEvento + (fonte?.let { textosDo(it) } ?: emptyList())
        val oferta = LeitorOferta.ler(textos)
        if (oferta != null) {
            noOferta = fonte
            mostrar(oferta)
            fundo.removeCallbacks(conferir)
            fundo.postDelayed(conferir, 400)
        }
        val cfg = Config.carregar(this)
        if (cfg.diagnostico) salvarDiagnostico(textos, oferta, tipo, classe)
    }

    private fun mostrar(oferta: Oferta) {
        val cfg = Config.carregar(this)
        handler.post {
            if (oferta.chave != chaveAtual) {
                chaveAtual = oferta.chave
                popup.mostrar(Avaliador.avaliar(oferta, cfg), cfg)
            }
        }
    }

    private fun textosDo(no: AccessibilityNodeInfo): List<String> {
        val textos = mutableListOf<String>()
        coletar(no, textos, IntArray(1))
        return textos
    }

    /** Lê no máximo [LIMITE_NOS] nós: um evento do mapa pode vir com a tela inteira. */
    private fun coletar(n: AccessibilityNodeInfo, textos: MutableList<String>, visitados: IntArray) {
        if (!n.isVisibleToUser || ++visitados[0] > LIMITE_NOS) return
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { textos += it }
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() && it != n.text?.toString() }?.let { textos += it }
        for (i in 0 until n.childCount) n.getChild(i)?.let { coletar(it, textos, visitados) }
    }

    private fun salvarDiagnostico(textos: List<String>, oferta: Oferta?, tipo: Int, classe: String?) {
        if (textos.isNotEmpty() || tipo == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val nome = AccessibilityEvent.eventTypeToString(tipo).removePrefix("TYPE_")
            ultimosEventos.addLast("${LocalTime.now().withNano(0)} $nome ${classe?.substringAfterLast('.')} ${textos.take(4).joinToString(" | ").take(120)}")
            while (ultimosEventos.size > 40) ultimosEventos.removeFirst()
        }
        salvarStatus()
        if (textos.none { it.contains("R$") }) return
        val texto = buildString {
            appendLine("Leitura de ${LocalDateTime.now().withNano(0)}")
            appendLine(if (oferta != null) "Oferta reconhecida: $oferta" else "Oferta NÃO reconhecida")
            appendLine("--- textos do evento da Uber ---")
            textos.forEach { appendLine(it) }
        }
        if (texto == ultimoDiagnostico) return
        ultimoDiagnostico = texto
        File(filesDir, ARQUIVO_DIAGNOSTICO).writeText(texto)
    }

    /** Janelas na tela e últimos eventos da Uber. Lê a tela inteira, então só a cada 5 s. */
    private fun salvarStatus() {
        val agora = System.currentTimeMillis()
        if (agora - ultimoStatus < 5_000) return
        ultimoStatus = agora
        val janelas = windows.joinToString("\n") { w ->
            val r = w.root
            val n = r?.let { runCatching { textosDo(it) }.getOrNull() } ?: emptyList()
            "tipo ${w.type} · ${r?.packageName ?: "sem acesso"} · ${w.title ?: ""} · ${n.size} textos"
        }
        if (historicoJanelas.lastOrNull()?.substringAfter('\n') != janelas) {
            historicoJanelas.addLast("${LocalTime.now().withNano(0)}\n$janelas")
            while (historicoJanelas.size > 8) historicoJanelas.removeFirst()
        }
        val texto = buildString {
            appendLine("Estado de ${LocalDateTime.now().withNano(0)} · versão ${Atualizador.versaoAtual} · Android ${Build.VERSION.SDK_INT}")
            appendLine("Eventos da Uber recebidos: $eventosDaUber")
            appendLine("--- janelas (cada vez que mudaram) ---")
            historicoJanelas.forEach { appendLine(it) }
            appendLine("--- últimos eventos da Uber com texto ---")
            ultimosEventos.forEach { appendLine(it) }
        }
        File(filesDir, ARQUIVO_STATUS).writeText(texto)
    }

    /** Mostra um popup de exemplo para conferir a posição. */
    fun testar() {
        val cfg = Config.carregar(this)
        val exemplo = Oferta(valor = 62.10, buscaKm = 1.4, buscaMin = 8, viagemKm = 4.9, viagemMin = 35, nota = 4.95)
        chaveAtual = "teste"
        popup.mostrar(Avaliador.avaliar(exemplo, cfg), cfg)
        handler.removeCallbacks(esconder)
        handler.postDelayed(esconder, 5_000)
    }

    companion object {
        const val UBER = "com.ubercab.driver"
        const val ARQUIVO_DIAGNOSTICO = "diagnostico.txt"
        const val ARQUIVO_STATUS = "status.txt"
        private const val LIMITE_NOS = 400
        var instancia: LeitorService? = null
            private set
    }
}
