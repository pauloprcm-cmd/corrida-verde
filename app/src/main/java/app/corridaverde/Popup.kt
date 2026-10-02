package app.corridaverde

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Popup por cima da Uber. Não recebe toques: só mostra. */
class Popup(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null

    fun mostrar(r: Resultado, cfg: Config) {
        val cor = when (r.cor) {
            Cor.VERDE -> Color.rgb(0x2E, 0xC4, 0x4F)
            Cor.AMARELO -> Color.rgb(0xF5, 0xC2, 0x1B)
            Cor.VERMELHO -> Color.rgb(0xE5, 0x39, 0x35)
        }
        val o = r.oferta
        val linhas = listOf(
            (o.valorMax?.let { "R$ ${br(o.valor)} – R$ ${br(it)}" } ?: "R$ ${br(o.valor)}") + " · taxímetro R$ ${br(r.taximetro)}${if (r.bandeira2) " (B2)" else ""}",
            "R$ ${br(r.rsKm)}/km" + (r.rsHora?.let { " · R$ ${br(it)}/h" } ?: ""),
            "busca ${km(o.buscaKm)} · viagem ${km(o.viagemKm)} ${o.viagemMin} min",
        ) + (if (r.minParado > 0) listOf("trânsito: ~${r.minParado} min parado no taxímetro") else emptyList()) + (if (r.avisos.isNotEmpty()) listOf("⚠ " + r.avisos.joinToString(" · ")) else emptyList())
        // Faixa do Táxi: o mínimo grande (é ele que decide a cor) e até quanto pode chegar, menor.
        val titulo = SpannableString("${r.pct}%" + (r.pctMax?.let { " → até $it%" } ?: ""))
        titulo.setSpan(RelativeSizeSpan(0.5f), "${r.pct}%".length, titulo.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        exibir(cfg, cor, titulo, linhas)
    }

    private fun exibir(cfg: Config, cor: Int, titulo: CharSequence, linhas: List<String>) {
        esconder()
        val caixa = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 0, 0, 0))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(4), cor)
            }
        }
        caixa.addView(TextView(ctx).apply {
            text = titulo
            setTextColor(cor)
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
        })
        for (l in linhas) {
            caixa.addView(TextView(ctx).apply {
                text = l
                setTextColor(if (l.startsWith("⚠")) Color.rgb(0xF5, 0xC2, 0x1B) else Color.WHITE)
                textSize = 15f
            })
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(cfg.posicaoY)
        }
        wm.addView(caixa, lp)
        view = caixa
    }

    /**
     * A linha da casa chega depois do resto: o destino vira ponto no mapa pela internet.
     * Perto: verde e grande. Longe ou sem resposta: cinza e pequena, para não chamar o olho.
     */
    fun linhaCasa(texto: String, perto: Boolean) {
        val caixa = view ?: return
        val linha = caixa.findViewWithTag<TextView>(LINHA_CASA) ?: TextView(ctx).apply { tag = LINHA_CASA }.also { caixa.addView(it) }
        linha.text = texto
        linha.textSize = if (perto) 18f else 13f
        linha.typeface = if (perto) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        linha.setTextColor(if (perto) Color.rgb(0x3B, 0xFF, 0x8B) else Color.rgb(0x9A, 0xA0, 0xA6))
    }

    fun esconder() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    companion object {
        private val PT = Locale("pt", "BR")
        private const val LINHA_CASA = "casa"
        fun br(v: Double) = String.format(PT, "%.2f", v)
        fun km(v: Double) = String.format(PT, "%.1f km", v)
    }
}
