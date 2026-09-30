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
    private var eventosDa99 = 0
    private val ultimosEventos = ArrayDeque<String>()
    private val historicoJanelas = ArrayDeque<String>()
    /** Cada tela diferente e a hora em que apareceu pela primeira vez, para achar o início e o fim da corrida. */
    private val telas = LinkedHashMap<String, String>()
    private var ultimaSessao: Sessao? = null
    private var ultimaCorrida = "nenhuma ainda"
    private val corridaUber = AcompanhaCorrida("Uber")
    private val corrida99 = AcompanhaCorrida("99")

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
    /** Refaz a notificação: o total do dia vira à meia-noite e, no Android 14, ela pode ser dispensada. */
    private val notificar = object : Runnable {
        override fun run() {
            Notificacao.atualizar(this@LeitorService)
            handler.postDelayed(this, 60 * 60_000L)
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
        handler.post(notificar)
    }

    override fun onDestroy() {
        instancia = null
        Notificacao.remover(this)
        handler.removeCallbacksAndMessages(null)
        fundo.removeCallbacksAndMessages(null)
        trabalho.quitSafely()
        if (::popup.isInitialized) popup.esconder()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val app = e.packageName?.toString()
        if (app == NOVENTA_E_NOVE) return lerOferta99(e)
        if (app != UBER) return
        // O evento é reciclado quando esta função volta: copia o que interessa antes.
        val textos = mutableListOf<String>()
        e.text.mapNotNullTo(textos) { it?.toString()?.takeIf { t -> t.isNotBlank() } }
        e.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { textos += it }
        val fonte = e.source
        val tipo = e.eventType
        val classe = e.className?.toString()
        fundo.post { runCatching { processar(textos, fonte, tipo, classe) } }
    }

    /** Na 99 não tem popup: os eventos servem para somar as corridas e para o diagnóstico. */
    private fun lerOferta99(e: AccessibilityEvent) {
        val textos = mutableListOf<String>()
        e.text.mapNotNullTo(textos) { it?.toString()?.takeIf { t -> t.isNotBlank() } }
        e.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { textos += it }
        val fonte = e.source
        val tipo = e.eventType
        val classe = e.className?.toString()
        fundo.post {
            runCatching {
                eventosDa99++
                val todos = textos + (fonte?.let { textosDo(it) } ?: emptyList())
                val oferta = LeitorOferta.ler(todos)
                acompanhar(corrida99, oferta, todos)
                val cfg = Config.carregar(this)
                if (cfg.diagnostico) salvarDiagnostico("99", todos, oferta, tipo, classe)
            }
        }
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
        acompanhar(corridaUber, oferta, textos)
        if (textos.any { t -> TELA_RESUMO.any { it in t } }) lerResumoDaSessao(textos)
        val cfg = Config.carregar(this)
        if (cfg.diagnostico) salvarDiagnostico("Uber", textos, oferta, tipo, classe)
    }

    private fun acompanhar(a: AcompanhaCorrida, oferta: Oferta?, textos: List<String>) {
        val agora = LocalDateTime.now()
        if (oferta != null) return a.oferta(oferta, agora)
        val corrida = a.tela(textos, agora) ?: return
        Corridas.guardar(this, corrida)
        ultimaCorrida = "${agora.toLocalTime().withNano(0)} ${corrida.app} R$ ${Popup.br(corrida.valor)}"
        handler.post { Notificacao.atualizar(this) }
    }

    /** Ao ficar offline, a Uber mostra o ganho da sessão: guarda para somar o dia. */
    private fun lerResumoDaSessao(textosDoEvento: List<String>) {
        val tela = windows.firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == UBER } }
        val sessao = LeitorSessao.ler(textosDoEvento + (tela?.let { textosDo(it) } ?: emptyList())) ?: return
        if (sessao == ultimaSessao) return
        ultimaSessao = sessao
        if (Sessoes.guardar(this, sessao)) handler.post { Notificacao.atualizar(this) }
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

    private fun salvarDiagnostico(app: String, textos: List<String>, oferta: Oferta?, tipo: Int, classe: String?) {
        if (textos.isNotEmpty() || tipo == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val nome = AccessibilityEvent.eventTypeToString(tipo).removePrefix("TYPE_")
            ultimosEventos.addLast("${LocalTime.now().withNano(0)} $app $nome ${classe?.substringAfterLast('.')} ${textos.take(4).joinToString(" | ").take(120)}")
            while (ultimosEventos.size > 40) ultimosEventos.removeFirst()
        }
        if (textos.isNotEmpty()) {
            val tela = "$app ${textos.take(6).joinToString(" | ").take(160)}"
            // Números (hora, distância, minutos) mudam o tempo todo: não contam como tela nova.
            val chave = tela.replace(Regex("\\d+"), "#")
            if (chave !in telas) {
                telas[chave] = "${LocalTime.now().withNano(0)} $tela"
                if (telas.size > 80) telas.remove(telas.keys.first())
            }
        }
        salvarStatus()
        if (textos.none { it.contains("R$") }) return
        val texto = buildString {
            appendLine("Leitura de ${LocalDateTime.now().withNano(0)}")
            appendLine(if (oferta != null) "Oferta reconhecida: $oferta" else "Oferta NÃO reconhecida")
            appendLine("--- textos do evento da $app ---")
            textos.forEach { appendLine(it) }
        }
        if (texto == ultimoDiagnostico) return
        ultimoDiagnostico = texto
        File(filesDir, ARQUIVO_DIAGNOSTICO).writeText(texto)
    }

    /** Janelas na tela e últimos eventos da Uber e da 99. Lê a tela inteira, então só a cada 5 s. */
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
            appendLine("Eventos recebidos: Uber $eventosDaUber · 99 $eventosDa99")
            appendLine("Última corrida somada: $ultimaCorrida")
            appendLine("Gravação: ${GravacaoService.situacao}")
            appendLine("--- janelas (cada vez que mudaram) ---")
            historicoJanelas.forEach { appendLine(it) }
            appendLine("--- últimos eventos com texto ---")
            ultimosEventos.forEach { appendLine(it) }
            appendLine("--- telas diferentes (primeira vez de cada) ---")
            telas.values.forEach { appendLine(it) }
        }
        File(filesDir, ARQUIVO_STATUS).writeText(texto)
    }

    /** Mostra um popup de exemplo para conferir a posição. */
    fun testar() {
        val cfg = Config.carregar(this)
        val exemplo = Oferta(valor = 62.10, buscaKm = 1.4, buscaMin = 8, viagemKm = 4.9, viagemMin = 35, nota = 4.95, paradas = 1)
        chaveAtual = "teste"
        popup.mostrar(Avaliador.avaliar(exemplo, cfg), cfg)
        handler.removeCallbacks(esconder)
        handler.postDelayed(esconder, 5_000)
    }

    companion object {
        const val UBER = "com.ubercab.driver"
        private val TELA_RESUMO = listOf("Resumo da sessão", "Viagens concluídas", "Viagens oferecidas", "Histórico de ganhos")
        const val NOVENTA_E_NOVE = "com.app99.driver"
        const val ARQUIVO_DIAGNOSTICO = "diagnostico.txt"
        const val ARQUIVO_STATUS = "status.txt"
        private const val LIMITE_NOS = 400
        var instancia: LeitorService? = null
            private set
    }
}
