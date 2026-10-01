package app.corridaverde

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Lê a oferta do app de motorista da Uber e mostra o popup. Também lê o aviso de
 * radar do Waze e da 99 e mostra o alerta de radar.
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
    /** O radar lê a tela inteira do navegador: fica numa linha própria para não atrasar a oferta. */
    private val trabalhoRadar = HandlerThread("radar").apply { start() }
    private val fundoRadar = Handler(trabalhoRadar.looper)

    private lateinit var popup: Popup
    private lateinit var radarPopup: RadarPopup
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
    private val acompanhaRadar = AcompanhaRadar()
    private val estimaRadarMaps = EstimaRadar()
    /** Apps com leitura de radar já marcada: no máximo uma leitura da tela a cada meio segundo. */
    private val radarAgendado = mutableSetOf<String>()
    private val eventosDosNavegadores = ConcurrentHashMap<String, Int>()
    /** Textos que falam de radar ou limite, para descobrir o que cada navegador mostra. */
    private val textosDeRadar = ConcurrentLinkedDeque<String>()
    /** Notificações da 99 e da Uber: a oferta da 99 pode vir por elas. */
    private val notificacoes = ArrayDeque<String>()
    /**
     * Estrutura das janelas da 99 (tipo, id e texto de cada nó, inclusive os invisíveis), uma por
     * formato de tela: o cartão da oferta da 99 chega sem texto e é aqui que se procura o valor.
     */
    private val estruturas99 = LinkedHashMap<String, String>()
    private var ultimaEstrutura99 = 0L
    /** Cada texto com R$ que a Uber e a 99 mostram, com o texto de antes e o de depois. */
    private val textosComValor = LinkedHashMap<String, String>()

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
    private val esconderRadar = Runnable { radarPopup.esconder() }
    private val esconder = Runnable {
        popup.esconder()
        chaveAtual = null
    }

    override fun onServiceConnected() {
        popup = Popup(this)
        radarPopup = RadarPopup(this)
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
        fundoRadar.removeCallbacksAndMessages(null)
        trabalhoRadar.quitSafely()
        if (::popup.isInitialized) popup.esconder()
        if (::radarPopup.isInitialized) radarPopup.esconder()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val app = e.packageName?.toString() ?: return
        if (e.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return notificacao(app, e)
        agendarRadar(app)
        if (app in NAVEGADORES) return
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

    private fun agendarRadar(app: String) {
        fundoRadar.post {
            eventosDosNavegadores.merge(app, 1) { a, b -> a + b }
            marcarLeituraDoRadar(app, 500)
        }
    }

    /** Só na linha do radar. */
    private fun marcarLeituraDoRadar(app: String, atrasoMs: Long) {
        if (!radarAgendado.add(app)) return
        fundoRadar.postDelayed({
            radarAgendado.remove(app)
            runCatching { lerRadar(app) }
        }, atrasoMs)
    }

    /**
     * Lê a tela inteira do navegador atrás do aviso de radar. Enquanto ele aparece, o alerta
     * fica na tela; 4 s depois de sumir (radar passou), o alerta some também. No Maps o aviso
     * some antes do radar: lá quem decide é a conta do [EstimaRadar].
     */
    private fun lerRadar(app: String) {
        val cfg = Config.carregar(this)
        if (!cfg.radar && !cfg.diagnostico) return
        // Na navegação da Uber o radar é só desenho no mapa: só lê para o diagnóstico.
        if (app == UBER && !cfg.diagnostico) return
        val tela = windows.firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == app } }
        // No Maps o alerta segue pela conta mesmo sem a janela dele (outro app por cima).
        if (tela == null && app != MAPS) return
        val textos = tela?.let { textosDo(it, LIMITE_NOS_RADAR) } ?: emptyList()
        if (cfg.diagnostico) guardarTextosDeRadar(app, textos)
        if (!cfg.radar) return
        val lido = LeitorRadar.ler(textos)
        val radar = if (app == MAPS) {
            estimaRadarMaps.atualizar(lido, LeitorVelocidade.ler(textos), System.currentTimeMillis())
                // O Maps só manda evento quando a tela muda: enquanto há radar, a conta anda sozinha.
                ?.also { marcarLeituraDoRadar(app, 1_000) }
        } else lido
        if (radar == null) return
        val novo = acompanhaRadar.visto(radar, System.currentTimeMillis())
        handler.post {
            radarPopup.mostrar(radar, novo)
            if (novo && cfg.radarSom) RadarPopup.tocar()
            handler.removeCallbacks(esconderRadar)
            handler.postDelayed(esconderRadar, 4_000)
        }
    }

    private fun guardarTextosDeRadar(app: String, textos: List<String>) {
        val nome = NOMES[app] ?: app
        textos.filter { t -> PALAVRAS_DE_RADAR.any { t.contains(it, ignoreCase = true) } }.forEach { t ->
            val linha = "$nome: ${t.replace('\n', ' ').take(120)}"
            if (textosDeRadar.none { it.substringAfter(' ') == linha }) {
                textosDeRadar.addLast("${LocalTime.now().withNano(0)} $linha")
                while (textosDeRadar.size > 60) textosDeRadar.pollFirst()
            }
        }
    }

    /** Na 99 não tem popup: os eventos servem só para o diagnóstico. */
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
                val cfg = Config.carregar(this)
                if (cfg.diagnostico) {
                    guardarEstrutura99()
                    salvarDiagnostico("99", todos, oferta, tipo, classe)
                }
            }
        }
    }

    /** Guarda o título e o texto da notificação da Uber e da 99, para o diagnóstico. */
    private fun notificacao(app: String, e: AccessibilityEvent) {
        if (app != UBER && app != NOVENTA_E_NOVE) return
        val textos = mutableListOf<String>()
        e.text.mapNotNullTo(textos) { it?.toString()?.takeIf { t -> t.isNotBlank() } }
        (e.parcelableData as? Notification)?.extras?.let { x ->
            listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT)
                .mapNotNullTo(textos) { k -> x.getCharSequence(k)?.toString()?.takeIf { it.isNotBlank() } }
            x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.mapNotNullTo(textos) { it?.toString() }
        }
        fundo.post {
            runCatching {
                val unicos = textos.distinct()
                val oferta = LeitorOferta.ler(unicos)
                if (!Config.carregar(this).diagnostico) return@runCatching
                val nome = NOMES[app] ?: app
                notificacoes.addLast("${LocalTime.now().withNano(0)} $nome ${if (oferta != null) "(oferta) " else ""}${unicos.joinToString(" | ").replace('\n', ' ').take(200)}")
                while (notificacoes.size > 15) notificacoes.removeFirst()
            }
        }
    }

    /** No máximo a cada 2 s, guarda a estrutura das janelas da 99 se o formato for novo. */
    private fun guardarEstrutura99() {
        val agora = System.currentTimeMillis()
        if (agora - ultimaEstrutura99 < 2_000) return
        ultimaEstrutura99 = agora
        windows.forEach { w ->
            val raiz = w.root?.takeIf { it.packageName?.toString() == NOVENTA_E_NOVE } ?: return@forEach
            val linhas = mutableListOf<String>()
            val formato = StringBuilder()
            estrutura(raiz, linhas, formato, 0, IntArray(1))
            // O formato é só tipo e id dos nós: a mesma tela com outra rua ou outro valor não conta de novo.
            val chave = "${w.type} $formato"
            if (chave in estruturas99) return@forEach
            estruturas99[chave] = "${LocalTime.now().withNano(0)} janela tipo ${w.type} · ${linhas.size} nós\n" + linhas.joinToString("\n")
            while (estruturas99.size > 10) estruturas99.remove(estruturas99.keys.first())
        }
    }

    private fun estrutura(n: AccessibilityNodeInfo, linhas: MutableList<String>, formato: StringBuilder, nivel: Int, visitados: IntArray) {
        if (++visitados[0] > LIMITE_NOS_ESTRUTURA) return
        val classe = n.className?.toString()?.substringAfterLast('.') ?: "?"
        val id = n.viewIdResourceName?.substringAfter(":id/")
        formato.append(classe).append(id ?: "").append(';')
        val r = Rect().also { n.getBoundsInScreen(it) }
        val partes = listOfNotNull(
            classe,
            id?.let { "#$it" },
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let { "\"${it.replace('\n', ' ').take(50)}\"" },
            n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { "desc=\"${it.take(50)}\"" },
            n.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { "dica=\"${it.take(30)}\"" },
            "clicável".takeIf { n.isClickable },
            "invisível".takeIf { !n.isVisibleToUser },
            "[${r.left},${r.top} ${r.width()}x${r.height()}]",
        )
        linhas += "  ".repeat(minOf(nivel, 10)) + partes.joinToString(" ")
        for (i in 0 until n.childCount) n.getChild(i)?.let { estrutura(it, linhas, formato, nivel + 1, visitados) }
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
        if (cfg.diagnostico) salvarDiagnostico("Uber", textos, oferta, tipo, classe)
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

    private fun textosDo(no: AccessibilityNodeInfo, limite: Int = LIMITE_NOS): List<String> {
        val textos = mutableListOf<String>()
        coletar(no, textos, IntArray(1), limite)
        return textos
    }

    /** Lê no máximo [LIMITE_NOS] nós: um evento do mapa pode vir com a tela inteira. */
    private fun coletar(n: AccessibilityNodeInfo, textos: MutableList<String>, visitados: IntArray, limite: Int) {
        if (!n.isVisibleToUser || ++visitados[0] > limite) return
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { textos += it }
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() && it != n.text?.toString() }?.let { textos += it }
        for (i in 0 until n.childCount) n.getChild(i)?.let { coletar(it, textos, visitados, limite) }
    }

    private fun salvarDiagnostico(app: String, textos: List<String>, oferta: Oferta?, tipo: Int, classe: String?) {
        if (textos.isNotEmpty() || tipo == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val nome = AccessibilityEvent.eventTypeToString(tipo).removePrefix("TYPE_")
            ultimosEventos.addLast("${LocalTime.now().withNano(0)} $app $nome ${classe?.substringAfterLast('.')} ${textos.take(4).joinToString(" | ").take(120)}")
            while (ultimosEventos.size > 40) ultimosEventos.removeFirst()
        }
        textos.forEachIndexed { i, t ->
            if ("R$" !in t) return@forEachIndexed
            val vizinhos = listOfNotNull(textos.getOrNull(i - 1), t, textos.getOrNull(i + 1)).joinToString(" | ").replace('\n', ' ').take(160)
            val chave = "$app ${vizinhos.replace(Regex("\\d+"), "#")}"
            if (chave !in textosComValor) {
                textosComValor[chave] = "${LocalTime.now().withNano(0)} $app $vizinhos"
                if (textosComValor.size > 40) textosComValor.remove(textosComValor.keys.first())
            }
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
        // Só telas com cara de oferta (valor e tempo ou distância), para a de Missões não apagar a última oferta.
        if (oferta == null && (textos.none { it.contains("R$") } || textos.none { PARECE_OFERTA.containsMatchIn(it) })) return
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
        // A janela flutuante da 99 pode aparecer sem mandar evento próprio.
        guardarEstrutura99()
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
            appendLine("Eventos recebidos: Uber $eventosDaUber · 99 $eventosDa99 · " +
                NAVEGADORES.joinToString(" · ") { "${NOMES[it]} ${eventosDosNavegadores[it] ?: 0}" })
            appendLine("Gravação: ${GravacaoService.situacao}")
            appendLine("--- janelas (cada vez que mudaram) ---")
            historicoJanelas.forEach { appendLine(it) }
            appendLine("--- últimos eventos com texto ---")
            ultimosEventos.forEach { appendLine(it) }
            appendLine("--- telas diferentes (primeira vez de cada) ---")
            telas.values.forEach { appendLine(it) }
            appendLine("--- textos sobre radar e limite ---")
            textosDeRadar.forEach { appendLine(it) }
            appendLine("--- textos com R$ (com o de antes e o de depois) ---")
            textosComValor.values.forEach { appendLine(it) }
            appendLine("--- notificações da Uber e da 99 ---")
            notificacoes.forEach { appendLine(it) }
            appendLine("--- estrutura das janelas da 99 (cada formato novo) ---")
            estruturas99.values.forEach { appendLine(it) }
        }
        File(filesDir, ARQUIVO_STATUS).writeText(texto)
    }

    /** Apaga o que o diagnóstico juntou até agora (os arquivos e as listas na memória). */
    fun limparDiagnostico() {
        textosDeRadar.clear()
        eventosDosNavegadores.clear()
        fundo.post {
            eventosDaUber = 0
            eventosDa99 = 0
            ultimosEventos.clear()
            historicoJanelas.clear()
            telas.clear()
            notificacoes.clear()
            estruturas99.clear()
            textosComValor.clear()
            ultimoDiagnostico = ""
            ultimoStatus = 0
            ultimaEstrutura99 = 0
            apagarArquivosDoDiagnostico(this)
        }
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

    /** Mostra um alerta de radar de exemplo, com o som. */
    fun testarRadar() {
        val cfg = Config.carregar(this)
        radarPopup.mostrar(Radar(210, 60, "velocidade"), novo = true)
        if (cfg.radarSom) RadarPopup.tocar()
        handler.removeCallbacks(esconderRadar)
        handler.postDelayed(esconderRadar, 5_000)
    }

    companion object {
        const val UBER = "com.ubercab.driver"
        const val WAZE = "com.waze"
        const val MAPS = "com.google.android.apps.maps"
        private val NAVEGADORES = listOf(WAZE, MAPS)
        private val NOMES = mapOf(UBER to "Uber", "com.app99.driver" to "99", WAZE to "Waze", MAPS to "Maps")
        private val PALAVRAS_DE_RADAR = listOf("radar", "câmera", "camera", "fiscaliza", "limite", "velocidade")
        const val NOVENTA_E_NOVE = "com.app99.driver"
        const val ARQUIVO_DIAGNOSTICO = "diagnostico.txt"
        const val ARQUIVO_STATUS = "status.txt"
        private const val LIMITE_NOS = 400
        private const val LIMITE_NOS_RADAR = 1500
        private const val LIMITE_NOS_ESTRUTURA = 120
        private val PARECE_OFERTA = Regex("""\d\s*(min|km|m\b)""")
        var instancia: LeitorService? = null
            private set

        fun apagarArquivosDoDiagnostico(ctx: android.content.Context) {
            File(ctx.filesDir, ARQUIVO_STATUS).delete()
            File(ctx.filesDir, ARQUIVO_DIAGNOSTICO).delete()
        }
    }
}
