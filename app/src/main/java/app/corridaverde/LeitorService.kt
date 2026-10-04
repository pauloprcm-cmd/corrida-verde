package app.corridaverde

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.annotation.TargetApi
import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executor
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Lê a oferta do app de motorista da Uber (e a da 99, por print e OCR) e mostra o popup. Também lê o aviso de
 * radar do Waze e da 99 e mostra o alerta de radar (no Google Maps e na navegação da
 * Uber o aviso não traz a distância, então lá não tem alerta).
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
    /** Modo "Indo pra casa": o destino vira ponto no mapa pela internet, numa linha própria para não atrasar o popup. */
    private val trabalhoCasa = HandlerThread("casa").apply { start() }
    private val fundoCasa = Handler(trabalhoCasa.looper)
    /** Cada destino consultado, a distância e quanto demorou, para o motorista conferir no diagnóstico. */
    private val consultasCasa = ConcurrentLinkedDeque<String>()
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
    /** Apps com leitura de radar já marcada: no máximo uma leitura da tela a cada meio segundo. */
    private val radarAgendado = mutableSetOf<String>()
    /** Só na linha do radar: última vez que pediu para salvar o diagnóstico. */
    private var ultimoPedidoStatus = 0L
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
    /** Oferta da 99 (só na linha de trabalho): se o cartão está na tela, o último print e as leituras para o diagnóstico. */
    private var ofertaNa99 = false
    private var ultimoPrint99 = 0L
    private var tentativas99 = 0
    private var diag99 = false
    private var avisar99 = false
    /** Chave do aviso que veio da 99, para esconder só ele quando o cartão some. */
    private var chave99: String? = null
    private val prints99 = ArrayDeque<String>()
    private var reconhecedor: TextRecognizer? = null
    private val noFundo = Executor { fundo.post(it) }
    private val lerCartao99 = Runnable { if (ofertaNa99 && Build.VERSION.SDK_INT >= 30) runCatching { tirarPrint99() } }
    /** A 99 nem sempre manda evento quando o cartão some: confere a cada 0,7 s enquanto ele está na tela. */
    private val vigiar99 = object : Runnable {
        override fun run() {
            if (!ofertaNa99) return
            if (runCatching { oferta99NaTela() }.getOrDefault(false)) fundo.postDelayed(this, 700)
            else {
                ofertaNa99 = false
                fecharOferta99()
            }
        }
    }
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
        fundoCasa.removeCallbacksAndMessages(null)
        trabalhoCasa.quitSafely()
        if (::popup.isInitialized) popup.esconder()
        if (::radarPopup.isInitialized) radarPopup.esconder()
        reconhecedor?.close()
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
            // Só com o Waze ou o Maps aberto não chega evento da Uber nem da 99: o diagnóstico é salvo daqui.
            val agora = System.currentTimeMillis()
            if (app in NAVEGADORES && agora - ultimoPedidoStatus >= 5_000 && Config.carregar(this).diagnostico) {
                ultimoPedidoStatus = agora
                fundo.post { runCatching { salvarStatus() } }
            }
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
     * fica na tela; 4 s depois de sumir (radar passou), o alerta some também.
     */
    private fun lerRadar(app: String) {
        val cfg = Config.carregar(this)
        if (!cfg.radar && !cfg.diagnostico) return
        // Na navegação da Uber e no Google Maps o aviso vem sem distância: só lê para o diagnóstico.
        val semAlerta = app == UBER || app == MAPS
        if (semAlerta && !cfg.diagnostico) return
        val tela = windows.firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == app } } ?: return
        val textos = textosDo(tela, LIMITE_NOS_RADAR)
        if (cfg.diagnostico) guardarTextosDeRadar(app, textos)
        if (!cfg.radar || semAlerta) return
        val radar = LeitorRadar.ler(textos)
        if (radar == null || radar.metros > ALERTA_RADAR_METROS) return
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

    /**
     * A 99 desenha o cartão da oferta em Flutter, que chega sem texto. Os eventos dela servem para saber quando
     * o cartão aparece ([ID_OFERTA_99]): aí o app tira um print, lê com o OCR e mostra o aviso.
     */
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
                val cfg = Config.carregar(this)
                if (cfg.aviso99 || cfg.diagnostico) conferirOferta99(cfg)
                if (cfg.diagnostico) {
                    val todos = textos + (fonte?.let { textosDo(it) } ?: emptyList())
                    guardarEstrutura99()
                    salvarDiagnostico("99", todos, LeitorOferta.ler(todos), tipo, classe)
                }
            }
        }
    }

    private fun oferta99NaTela() = windows.any { w ->
        val raiz = w.root?.takeIf { it.packageName?.toString() == NOVENTA_E_NOVE } ?: return@any false
        raiz.findAccessibilityNodeInfosByViewId(ID_OFERTA_99).any { it.isVisibleToUser }
    }

    /** Só na linha de trabalho. Um print por oferta, quando o cartão aparece; quando ele some, o aviso sai. */
    private fun conferirOferta99(cfg: Config) {
        if (Build.VERSION.SDK_INT < 30) return
        val aberta = oferta99NaTela()
        val agora = System.currentTimeMillis()
        if (aberta && !ofertaNa99 && agora - ultimoPrint99 > 1_500) {
            ultimoPrint99 = agora
            diag99 = cfg.diagnostico
            avisar99 = cfg.aviso99
            tentativas99 = 0
            // Espera o cartão terminar de subir.
            fundo.removeCallbacks(lerCartao99)
            fundo.postDelayed(lerCartao99, 300)
            fundo.removeCallbacks(vigiar99)
            fundo.postDelayed(vigiar99, 700)
        }
        if (!aberta && ofertaNa99) fecharOferta99()
        ofertaNa99 = aberta
    }

    private fun fecharOferta99() {
        fundo.removeCallbacks(lerCartao99)
        val chave = chave99 ?: return
        chave99 = null
        handler.post { if (chaveAtual == chave) esconder.run() }
    }

    @TargetApi(30)
    private fun tirarPrint99() {
        takeScreenshot(Display.DEFAULT_DISPLAY, noFundo, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                val hora = LocalTime.now().withNano(0)
                runCatching {
                    val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace) ?: error("sem imagem")
                    val tela = hw.copy(Bitmap.Config.ARGB_8888, false)
                    hw.recycle()
                    if (diag99) {
                        File(ArquivosProvider.pasta(this@LeitorService), PRINT_99).outputStream().use {
                            tela.compress(Bitmap.CompressFormat.JPEG, 85, it)
                        }
                        anotar99("$hora print ${tela.width}x${tela.height}${if (quasePreto(tela)) " · SAIU PRETO" else ""}")
                    }
                    // O cartão fica na metade de baixo, preso ao rodapé: o OCR lê só daí para baixo.
                    val topo = tela.height * 35 / 100
                    val cartao = Bitmap.createBitmap(tela, 0, topo, tela.width, tela.height - topo)
                    tela.recycle()
                    lerTexto99(cartao, hora)
                }.onFailure { anotar99("$hora print falhou: ${it.message}") }
                r.hardwareBuffer.close()
            }

            override fun onFailure(codigo: Int) {
                anotar99("${LocalTime.now().withNano(0)} o Android recusou o print (código $codigo)")
                tentarDeNovo99()
            }
        })
    }

    /** O recorte só fica na memória até o OCR devolver o texto. */
    private fun lerTexto99(cartao: Bitmap, hora: LocalTime) {
        val ocr = reconhecedor ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { reconhecedor = it }
        val inicio = System.currentTimeMillis()
        ocr.process(InputImage.fromBitmap(cartao, 0))
            .addOnSuccessListener(noFundo) { t ->
                cartao.recycle()
                // De cima para baixo, como o motorista lê.
                val linhas = t.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.top ?: 0 }.map { it.text }
                val oferta = LeitorOferta.ler99(linhas)
                anotar99("$hora leu em ${System.currentTimeMillis() - inicio} ms: ${oferta ?: "oferta NÃO reconhecida"} | " +
                    linhas.joinToString(" | ").replace('\n', ' ').take(400))
                if (oferta == null) return@addOnSuccessListener tentarDeNovo99()
                if (ofertaNa99 && avisar99) {
                    chave99 = oferta.chave
                    mostrar(oferta)
                }
            }
            .addOnFailureListener(noFundo) { erro ->
                cartao.recycle()
                // Logo depois de instalar, o Google Play ainda pode estar baixando o leitor de texto.
                anotar99("$hora o leitor de texto falhou: ${erro.message}")
            }
    }

    /** Não leu: outro print, até 3 por oferta. O Android só deixa tirar um print por segundo. */
    private fun tentarDeNovo99() {
        if (ofertaNa99 && ++tentativas99 < 3) fundo.postDelayed(lerCartao99, 1_100)
    }

    /** A metade de baixo (onde fica o cartão) quase toda preta: a 99 bloqueia print. */
    private fun quasePreto(b: Bitmap): Boolean {
        var soma = 0L
        var n = 0
        for (y in b.height / 2 until b.height step 40) for (x in 0 until b.width step 40) {
            val p = b.getPixel(x, y)
            soma += Color.red(p) + Color.green(p) + Color.blue(p)
            n++
        }
        return n > 0 && soma / (3 * n) < 12
    }

    /** Só com o diagnóstico ligado quando a oferta chegou. */
    private fun anotar99(linha: String) {
        if (!diag99) return
        prints99.addLast(linha)
        while (prints99.size > 10) prints99.removeFirst()
        ultimoStatus = 0
        salvarStatus()
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
                if (cfg.indoPraCasa) calcularCasa(oferta, cfg)
            }
        }
    }

    /** Só na linha da tela. A conta corre em [fundoCasa]; a linha entra no popup se ele ainda for desta oferta. */
    private fun calcularCasa(oferta: Oferta, cfg: Config) {
        val casa = cfg.casa ?: return
        val destino = oferta.destino ?: return popup.linhaCasa("distância de casa: destino não lido", false)
        fundoCasa.post {
            val inicio = System.currentTimeMillis()
            val ponto = runCatching { Enderecos.destino(this, destino, casa) }.getOrNull()
            val km = ponto?.let { Casa.distanciaKm(it, casa) }
            consultasCasa.addLast("${LocalTime.now().withNano(0)} ${destino.replace('\n', ' ').take(80)} → " +
                (km?.let { String.format(Locale("pt", "BR"), "%.1f km", it) } ?: "não achou") + " em ${System.currentTimeMillis() - inicio} ms")
            while (consultasCasa.size > 30) consultasCasa.pollFirst()
            handler.post {
                if (chaveAtual != oferta.chave) return@post
                if (km == null) popup.linhaCasa("distância de casa: sem sinal", false)
                else popup.linhaCasa(Casa.texto(km, cfg.raioCasaKm), Casa.perto(km, cfg.raioCasaKm))
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
            appendLine("--- Indo pra casa (destino → distância até casa, tempo da consulta) ---")
            consultasCasa.forEach { appendLine(it) }
            appendLine("--- textos com R$ (com o de antes e o de depois) ---")
            textosComValor.values.forEach { appendLine(it) }
            appendLine("--- notificações da Uber e da 99 ---")
            notificacoes.forEach { appendLine(it) }
            appendLine("--- estrutura das janelas da 99 (cada formato novo) ---")
            estruturas99.values.forEach { appendLine(it) }
            appendLine("--- oferta da 99: print e leitura (códigos: 1 desligue e ligue a leitura, 2 cedo demais, 5 tela protegida) ---")
            if (Build.VERSION.SDK_INT < 30) appendLine("Este Android não deixa tirar print (precisa do Android 11)")
            prints99.forEach { appendLine(it) }
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
            prints99.clear()
            ofertaNa99 = false
            ultimoPrint99 = 0
            apagarArquivosDoDiagnostico(this)
        }
    }

    /** Mostra um popup de exemplo para conferir a posição. */
    fun testar() {
        val cfg = Config.carregar(this)
        val exemplo = Oferta(valor = 62.10, buscaKm = 1.4, buscaMin = 8, viagemKm = 4.9, viagemMin = 35, nota = 4.95, paradas = 1)
        chaveAtual = "teste"
        popup.mostrar(Avaliador.avaliar(exemplo, cfg), cfg)
        if (cfg.indoPraCasa) popup.linhaCasa(Casa.texto(1.8, cfg.raioCasaKm), true)
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
        /** Fica na pasta do [ArquivosProvider] para ir junto com o diagnóstico; o cache não entra no backup. */
        const val PRINT_99 = "oferta-99.jpg"
        private const val ID_OFERTA_99 = "$NOVENTA_E_NOVE:id/broad_order_container"
        private const val LIMITE_NOS = 400
        private const val LIMITE_NOS_RADAR = 1500
        private const val LIMITE_NOS_ESTRUTURA = 120
        private val PARECE_OFERTA = Regex("""\d\s*(min|km|m\b)""")
        var instancia: LeitorService? = null
            private set

        fun apagarArquivosDoDiagnostico(ctx: android.content.Context) {
            File(ctx.filesDir, ARQUIVO_STATUS).delete()
            File(ctx.filesDir, ARQUIVO_DIAGNOSTICO).delete()
            File(ArquivosProvider.pasta(ctx), PRINT_99).delete()
        }
    }
}
