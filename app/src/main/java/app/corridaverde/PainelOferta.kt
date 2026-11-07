package app.corridaverde

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Painel Última oferta por cima da Uber e da 99 (desenho do Paulo, 07/11/2026): uma aba fina na borda esquerda que,
 * tocada, abre a última oferta da Uber e a da 99 lado a lado, com o embarque e o destino e um "Ver no Maps" em
 * cada um. Serve para conferir depois de aceitar se o lugar é seguro ou se é uma região aonde o motorista não quer
 * ir. Quem decide quando a aba aparece é o [LeitorService].
 */
class PainelOferta(
    private val ctx: Context,
    private val aoDispensar: () -> Unit,
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val visivel get() = view != null

    /** Mostra a aba recolhida (não faz nada se já está na tela, aberta ou não). */
    fun mostrar() {
        if (view == null) recolher()
    }

    fun esconder() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun recolher() {
        esconder()
        val aba = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 0, 0, 0))
                cornerRadii = floatArrayOf(0f, 0f, dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), 0f, 0f)
                setStroke(dp(2), VERDE)
            }
            contentDescription = "Última oferta"
            addView(TextView(ctx).apply {
                text = "»"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(VERDE)
                gravity = Gravity.CENTER
            })
            setOnClickListener { abrir() }
        }
        val p = parametros(dp(26), dp(96), tocavelFora = false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (ctx.resources.displayMetrics.heightPixels * 0.40).toInt()
        }
        runCatching { wm.addView(aba, p) }.onSuccess { view = aba }
    }

    private fun abrir() {
        esconder()
        val m = ctx.resources.displayMetrics
        val caixa = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(242, 0, 0, 0))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), VERDE)
            }
            // Tocou fora do painel: recolhe para a aba.
            setOnTouchListener { _, ev -> if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) recolher(); false }
        }
        val topo = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        topo.addView(texto("ÚLTIMA OFERTA", 15f, VERDE, negrito = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topo.addView(texto("Fechar «", 15f, CINZA).apply {
            setPadding(dp(12), dp(6), 0, dp(6))
            setOnClickListener { recolher() }
        })
        caixa.addView(topo)

        val colunas = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        colunas.addView(coluna(UltimaOferta.UBER, "Uber"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        colunas.addView(View(ctx).apply { setBackgroundColor(Color.argb(90, 255, 255, 255)) },
            LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(dp(10), dp(4), dp(10), dp(4)) })
        colunas.addView(coluna(UltimaOferta.NOVENTA_E_NOVE, "99"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        caixa.addView(colunas, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        caixa.addView(texto("Esconder até a próxima oferta", 13f, CINZA).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
            setOnClickListener {
                esconder()
                aoDispensar()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val p = parametros(m.widthPixels - dp(16), WindowManager.LayoutParams.WRAP_CONTENT, tocavelFora = true).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = (m.heightPixels * 0.22).toInt()
        }
        runCatching { wm.addView(caixa, p) }.onSuccess { view = caixa }
    }

    /** A oferta de um app: nome, quando chegou e o valor; embarque e destino, cada um com o seu botão. */
    private fun coluna(app: String, nome: String): View {
        val c = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(texto(nome, 18f, Color.WHITE, negrito = true))
        val o = UltimaOferta.carregar(ctx, app)
        if (o == null) {
            c.addView(texto("Nenhuma oferta ainda", 14f, CINZA).apply { setPadding(0, dp(6), 0, 0) })
            return c
        }
        c.addView(texto("${quando(o.quando)} · R$ ${Popup.br(o.valor)}", 13f, CINZA))
        endereco(c, "● EMBARQUE", VERDE, o.embarque)
        endereco(c, "⚑ DESTINO", Color.WHITE, o.destino)
        return c
    }

    private fun endereco(c: LinearLayout, rotulo: String, corRotulo: Int, endereco: String?) {
        c.addView(texto(rotulo, 12f, corRotulo, negrito = true).apply { setPadding(0, dp(10), 0, dp(2)) })
        c.addView(texto(endereco ?: "não deu para ler", 14f, if (endereco == null) CINZA else Color.WHITE).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        })
        if (endereco == null) return
        c.addView(TextView(ctx).apply {
            text = "Ver no Maps"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(9), dp(8), dp(9))
            background = GradientDrawable().apply {
                setColor(VERDE)
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener {
                recolher()
                UltimaOferta.abrirNoMapa(ctx, endereco)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
    }

    /** "agora", "há 12 min" ou, depois de uma hora, "às 14:05". */
    private fun quando(ms: Long): String {
        val min = (System.currentTimeMillis() - ms) / 60_000
        return when {
            min < 1 -> "agora"
            min < 60 -> "há $min min"
            else -> "às " + Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(HORA)
        }
    }

    private fun texto(t: String, tamanho: Float, cor: Int, negrito: Boolean = false) = TextView(ctx).apply {
        text = t
        textSize = tamanho
        setTextColor(cor)
        if (negrito) typeface = Typeface.DEFAULT_BOLD
    }

    private fun parametros(largura: Int, altura: Int, tocavelFora: Boolean) = WindowManager.LayoutParams(
        largura, altura,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (tocavelFora) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else 0),
        PixelFormat.TRANSLUCENT,
    )

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private companion object {
        val VERDE = Color.rgb(0x2E, 0xC4, 0x4F)
        val CINZA = Color.rgb(0x9A, 0xA0, 0xA6)
        val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
