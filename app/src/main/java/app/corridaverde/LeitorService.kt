package app.corridaverde

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Lê a tela do app de motorista da Uber e mostra o popup.
 * Só lê: não toca na tela, não aceita nem recusa corridas.
 */
class LeitorService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var popup: Popup
    private var chaveAtual: String? = null
    private var ultimoDiagnostico = ""
    private var ultimoStatus = 0L
    private var eventosDaUber = 0

    /** Textos que vieram junto com os eventos da Uber nos últimos segundos (tempo, textos). */
    private val textosDosEventos = ArrayDeque<Pair<Long, List<String>>>()
    private val ultimosEventos = ArrayDeque<String>()
    private val historicoJanelas = ArrayDeque<String>()

    private var leituraAgendada = false
    private val lerTela = Runnable {
        leituraAgendada = false
        // Um nó que some no meio da leitura não pode derrubar o serviço.
        runCatching { ler() }
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
        if (::popup.isInitialized) popup.esconder()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.packageName?.toString() != UBER) return
        guardarEvento(e)
        // Não adia a leitura a cada evento: a barra do botão e o mapa da oferta
        // mudam o tempo todo, e adiar a cada mudança fazia a leitura nunca acontecer.
        if (leituraAgendada) return
        leituraAgendada = true
        handler.postDelayed(lerTela, 150)
    }

    /**
     * Guarda os textos do próprio evento. Se o cartão da oferta estiver numa janela
     * que não aparece na lista de janelas, é por aqui que ele chega.
     */
    private fun guardarEvento(e: AccessibilityEvent) {
        eventosDaUber++
        val textos = mutableListOf<String>()
        e.text.mapNotNullTo(textos) { it?.toString()?.takeIf { t -> t.isNotBlank() } }
        e.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { textos += it }
        runCatching { e.source?.let { coletar(it, textos, limite = 300) } }

        val agora = System.currentTimeMillis()
        while (textosDosEventos.isNotEmpty() && agora - textosDosEventos.first().first > 3_000) textosDosEventos.removeFirst()
        if (textos.isNotEmpty()) textosDosEventos.addLast(agora to textos)
        while (textosDosEventos.size > 20) textosDosEventos.removeFirst()

        // O mapa gera eventos sem texto o tempo todo: esses não entram no registro.
        if (textos.isEmpty() && e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val tipo = AccessibilityEvent.eventTypeToString(e.eventType).removePrefix("TYPE_")
        ultimosEventos.addLast("${LocalTime.now().withNano(0)} $tipo ${e.className?.toString()?.substringAfterLast('.')} ${textos.take(4).joinToString(" | ").take(120)}")
        while (ultimosEventos.size > 40) ultimosEventos.removeFirst()
    }

    private fun ler() {
        val textos = textosDaUber()
        val dosEventos = textosDosEventos.flatMap { it.second }.distinct()
        val oferta = LeitorOferta.ler(textos) ?: LeitorOferta.ler(dosEventos)
        val cfg = Config.carregar(this)
        if (cfg.diagnostico) salvarDiagnostico(textos, dosEventos, oferta)

        if (oferta == null) {
            // A oferta sumiu (aceita, recusada ou expirou).
            if (chaveAtual != null) {
                handler.removeCallbacks(esconder)
                handler.postDelayed(esconder, 800)
            }
            return
        }
        handler.removeCallbacks(esconder)
        handler.postDelayed(esconder, 60_000)
        if (oferta.chave == chaveAtual) return
        chaveAtual = oferta.chave
        popup.mostrar(Avaliador.avaliar(oferta, cfg), cfg)
    }

    /** Textos só das janelas da Uber, ignorando os cards de outros apps por cima. */
    private fun textosDaUber(): List<String> {
        val raizes = windows.mapNotNull { it.root }.filter { it.packageName?.toString() == UBER }
            .ifEmpty { listOfNotNull(rootInActiveWindow?.takeIf { it.packageName?.toString() == UBER }) }
        val textos = mutableListOf<String>()
        for (r in raizes) coletar(r, textos)
        return textos
    }

    private fun coletar(n: AccessibilityNodeInfo, textos: MutableList<String>, limite: Int = Int.MAX_VALUE) {
        if (!n.isVisibleToUser || textos.size >= limite) return
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { textos += it }
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() && it != n.text?.toString() }?.let { textos += it }
        for (i in 0 until n.childCount) n.getChild(i)?.let { coletar(it, textos, limite) }
    }

    private fun salvarDiagnostico(textos: List<String>, dosEventos: List<String>, oferta: Oferta?) {
        salvarStatus()
        if ((textos + dosEventos).none { it.contains("R$") }) return
        val texto = buildString {
            appendLine("Leitura de ${LocalDateTime.now().withNano(0)}")
            appendLine(if (oferta != null) "Oferta reconhecida: $oferta" else "Oferta NÃO reconhecida")
            appendLine("--- textos da tela da Uber ---")
            textos.forEach { appendLine(it) }
            appendLine("--- textos dos eventos da Uber ---")
            dosEventos.forEach { appendLine(it) }
        }
        if (texto == ultimoDiagnostico) return
        ultimoDiagnostico = texto
        File(filesDir, ARQUIVO_DIAGNOSTICO).writeText(texto)
    }

    /** Janelas na tela e últimos eventos da Uber, gravados no máximo uma vez por segundo. */
    private fun salvarStatus() {
        val agora = System.currentTimeMillis()
        if (agora - ultimoStatus < 1_000) return
        ultimoStatus = agora
        val janelas = windows.joinToString("\n") { w ->
            val r = w.root
            val n = mutableListOf<String>()
            r?.let { runCatching { coletar(it, n) } }
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
        var instancia: LeitorService? = null
            private set
    }
}
