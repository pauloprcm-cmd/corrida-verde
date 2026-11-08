package app.corridaverde

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Popup por cima da Uber. Não recebe toques: só mostra. Com a tela dividida há um para cada app, e [mostrar] recebe
 * o pedaço da tela do app ([area]): o aviso fica no alto desse pedaço, no lado escolhido nos Ajustes.
 */
class Popup(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null

    fun mostrar(r: Resultado, cfg: Config, area: Rect? = null) {
        val cor = when (r.cor) {
            Cor.VERDE -> Color.rgb(0x2E, 0xC4, 0x4F)
            Cor.AMARELO -> Color.rgb(0xF5, 0xC2, 0x1B)
            Cor.VERMELHO -> Color.rgb(0xE5, 0x39, 0x35)
        }
        val o = r.oferta
        if (cfg.motoristaDeApp) {
            // Motorista de app: o número grande é o R$/km; a última linha diz o grupo e o mínimo usado, para conferir.
            val linhasApp = listOf(
                "R$ ${br(o.valor)}" + (r.rsHora?.let { " · R$ ${br(it)}/h" } ?: ""),
                "busca ${km(o.buscaKm)} · viagem ${km(o.viagemKm)} ${o.viagemMin} min",
                "${o.grupo.nome} · verde a partir de R$ ${br(cfg.verde(o.grupo))}/km",
            ) + (if (r.avisos.isNotEmpty()) listOf("⚠ " + r.avisos.joinToString(" · ")) else emptyList())
            return exibir(cfg, cor, "R$ ${br(r.rsKm)}/km", linhasApp, area)
        }
        val linhas = listOf(
            (o.valorMax?.let { "taxímetro −15%, até R$ ${br(it)}" } ?: "R$ ${br(o.valor)}") +
                " · taxímetro R$ ${br(r.taximetro)}${if (r.bandeira2) " (B2)" else ""}",
            "R$ ${br(r.rsKm)}/km" + (r.rsHora?.let { " · R$ ${br(it)}/h" } ?: ""),
            "busca ${km(o.buscaKm)} · viagem ${km(o.viagemKm)} ${o.viagemMin} min",
        ) + (if (r.minParado > 0) listOf("trânsito: ~${r.minParado} min parado no taxímetro") else emptyList()) + (if (r.avisos.isNotEmpty()) listOf("⚠ " + r.avisos.joinToString(" · ")) else emptyList())
        exibir(cfg, cor, "${r.pct}%", linhas, area)
    }

    private fun exibir(cfg: Config, cor: Int, titulo: CharSequence, linhas: List<String>, area: Rect?) {
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
            if (area == null) {
                gravity = Gravity.TOP or when (cfg.ladoAviso) {
                    Config.ESQUERDA -> Gravity.START
                    Config.DIREITA -> Gravity.END
                    else -> Gravity.CENTER_HORIZONTAL
                }
                // Nos cantos, uma folga da borda da tela.
                if (cfg.ladoAviso != Config.CENTRO) x = dp(8)
                y = dp(cfg.posicaoY)
            } else {
                // Pedaço da tela do app: mede o aviso para centralizar (ou encostar no lado) dentro dele.
                gravity = Gravity.TOP or Gravity.START
                // O pedaço vem em coordenadas da tela inteira: sem empurrar o aviso para fora das barras do sistema.
                if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
                caixa.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val largura = caixa.measuredWidth
                x = when (cfg.ladoAviso) {
                    Config.ESQUERDA -> area.left + dp(8)
                    Config.DIREITA -> area.right - largura - dp(8)
                    else -> area.left + (area.width() - largura) / 2
                }.coerceAtLeast(area.left)
                y = area.top + dp(cfg.posicaoY)
            }
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
