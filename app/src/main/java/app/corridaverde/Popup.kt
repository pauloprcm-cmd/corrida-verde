package app.corridaverde

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
            "R$ ${br(o.valor)} · taxímetro R$ ${br(r.taximetro)}${if (r.bandeira2) " (B2)" else ""}",
            "R$ ${br(r.rsKm)}/km" + (r.rsHora?.let { " · R$ ${br(it)}/h" } ?: ""),
            "busca ${km(o.buscaKm)} · viagem ${km(o.viagemKm)}",
        ) + (if (r.buscaLonga) listOf("⚠ busca longa") else emptyList())
        exibir(cfg, cor, "${r.pct}%", linhas)
    }

    private fun exibir(cfg: Config, cor: Int, titulo: String, linhas: List<String>) {
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

    fun esconder() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    companion object {
        private val PT = Locale("pt", "BR")
        fun br(v: Double) = String.format(PT, "%.2f", v)
        fun km(v: Double) = String.format(PT, "%.1f km", v)
    }
}
