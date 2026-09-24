package app.corridaverde

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.time.LocalDateTime

/**
 * Lê a tela do app de motorista da Uber e mostra o popup.
 * Só lê: não toca na tela, não aceita nem recusa corridas.
 */
class LeitorService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var popup: Popup
    private var chaveAtual: String? = null
    private var ultimoDiagnostico = ""

    private val lerTela = Runnable { ler() }
    private val esconder = Runnable {
        popup.esconder()
        chaveAtual = null
    }

    override fun onServiceConnected() {
        popup = Popup(this)
        instancia = this
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
        handler.removeCallbacks(lerTela)
        handler.postDelayed(lerTela, 150)
    }

    private fun ler() {
        val textos = textosDaUber()
        val oferta = LeitorOferta.ler(textos)
        val cfg = Config.carregar(this)
        if (cfg.diagnostico) salvarDiagnostico(textos, oferta)

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

    private fun coletar(n: AccessibilityNodeInfo, textos: MutableList<String>) {
        if (!n.isVisibleToUser) return
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { textos += it }
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() && it != n.text?.toString() }?.let { textos += it }
        for (i in 0 until n.childCount) n.getChild(i)?.let { coletar(it, textos) }
    }

    private fun salvarDiagnostico(textos: List<String>, oferta: Oferta?) {
        if (textos.none { it.contains("R$") }) return
        val texto = buildString {
            appendLine("Leitura de ${LocalDateTime.now().withNano(0)}")
            appendLine(if (oferta != null) "Oferta reconhecida: $oferta" else "Oferta NÃO reconhecida")
            appendLine("--- textos da tela da Uber ---")
            textos.forEach { appendLine(it) }
        }
        if (texto == ultimoDiagnostico) return
        ultimoDiagnostico = texto
        File(filesDir, ARQUIVO_DIAGNOSTICO).writeText(texto)
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
        var instancia: LeitorService? = null
            private set
    }
}
