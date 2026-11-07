package app.corridaverde

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Painel Última oferta por cima da Uber e da 99 (desenho do Paulo, 07/11/2026): uma aba fina na borda esquerda que,
 * tocada, cresce da lateral e mostra a última oferta da Uber e a da 99 lado a lado, com o embarque e o destino e um
 * "Ver no Maps" em cada um. Serve para conferir depois de aceitar se o lugar é seguro ou se é uma região aonde o
 * motorista não quer ir. Visual futurista da [MolduraNeon]. Quem decide quando a aba aparece é o [LeitorService].
 */
class PainelOferta(
    private val ctx: Context,
    private val aoDispensar: () -> Unit,
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val densidade = ctx.resources.displayMetrics.density
    private var view: View? = null
    /** No meio da animação de abrir ou fechar: toques a mais não fazem nada. */
    private var animando = false

    val visivel get() = view != null

    /** Mostra a aba recolhida (não faz nada se já está na tela, aberta ou não). */
    fun mostrar() {
        if (view == null) mostrarAba()
    }

    fun esconder() {
        view?.let { v ->
            v.animate().cancel()
            runCatching { wm.removeView(v) }
        }
        view = null
        animando = false
    }

    // ---------- A aba recolhida ----------

    private fun mostrarAba() {
        esconder()
        val aba = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = MolduraNeon(densidade, corteDp = 8f, listras = true, detalhes = false)
            contentDescription = "Última oferta"
            // Tracinhos na vertical, como no desenho, e as setas.
            addView(View(ctx).apply { background = Tracinhos() }, LinearLayout.LayoutParams(dp(3), dp(26)))
            addView(texto("»", 22f, MolduraNeon.NEON, negrito = true).apply { setPadding(0, dp(6), 0, dp(6)) })
            addView(View(ctx).apply { background = Tracinhos() }, LinearLayout.LayoutParams(dp(3), dp(26)))
            setOnClickListener { if (!animando) abrir() }
        }
        val p = parametros(dp(30), dp(118), tocavelFora = false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = -dp(4) // A folga do brilho do lado esquerdo fica fora da tela: a aba encosta na borda.
            y = (ctx.resources.displayMetrics.heightPixels * 0.40).toInt()
        }
        runCatching { wm.addView(aba, p) }.onSuccess { view = aba }
    }

    /** Uma coluna de traços curtos, a decoração da aba e da divisória. */
    private inner class Tracinhos : Drawable() {
        private val p = Paint().apply { color = MolduraNeon.NEON }
        override fun draw(c: Canvas) {
            val b = bounds
            var y = b.top.toFloat()
            while (y + dp(3) <= b.bottom) {
                c.drawRect(b.left.toFloat(), y, b.right.toFloat(), y + dp(3), p)
                y += dp(5)
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    // ---------- O painel aberto ----------

    private fun abrir() {
        esconder()
        val m = ctx.resources.displayMetrics
        val moldura = MolduraNeon(densidade)
        val f = moldura.folga.toInt()
        val caixa = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = moldura
            setPadding(f + dp(12), f + dp(10), f + dp(12), f + dp(12))
        }

        // Cabeçalho: a etiqueta "◆ ÚLTIMA OFERTA" e o fechar.
        val topo = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val etiqueta = MolduraNeon(densidade, corteDp = 6f, listras = false, detalhes = false, brilho = false,
            fundoAlto = Color.rgb(0x06, 0x12, 0x0D), fundoBaixo = Color.rgb(0x06, 0x12, 0x0D))
        topo.addView(texto("◆  ÚLTIMA OFERTA", 13f, MolduraNeon.NEON, negrito = true).apply {
            background = etiqueta
            letterSpacing = 0.08f
            setPadding(dp(10), dp(5), dp(12), dp(5))
        })
        topo.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        topo.addView(texto("Fechar «", 14f, CINZA).apply {
            setPadding(dp(12), dp(6), dp(4), dp(6))
            setOnClickListener { fechar() }
        })
        caixa.addView(topo)

        val colunas = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        colunas.addView(coluna(UltimaOferta.UBER, "UBER", Color.WHITE), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // Divisória: linha neon fina com tracinhos no meio.
        colunas.addView(FrameLayout(ctx).apply {
            addView(View(ctx).apply { setBackgroundColor(Color.argb(110, 0x3D, 0xF5, 0xA8)) },
                FrameLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
            addView(View(ctx).apply { background = Tracinhos() }, FrameLayout.LayoutParams(dp(3), dp(30), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(5), ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(dp(10), dp(6), dp(10), dp(6)) })
        colunas.addView(coluna(UltimaOferta.NOVENTA_E_NOVE, "99", AMARELO_99), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        caixa.addView(colunas, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        caixa.addView(texto("Esconder até a próxima oferta", 13f, CINZA).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(2))
            setOnClickListener {
                esconder()
                aoDispensar()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Tocou fora do painel: recolhe para a aba.
        caixa.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) fechar()
            false
        }

        val p = parametros(m.widthPixels - dp(8), WindowManager.LayoutParams.WRAP_CONTENT, tocavelFora = true).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (m.heightPixels * 0.22).toInt()
        }
        // Cresce da lateral esquerda, onde estava a aba, em menos de dois décimos de segundo.
        caixa.pivotX = 0f
        caixa.scaleX = 0.08f
        caixa.alpha = 0.3f
        runCatching { wm.addView(caixa, p) }.onSuccess {
            view = caixa
            animando = true
            caixa.animate().scaleX(1f).alpha(1f).setDuration(ABRIR_MS).setInterpolator(DecelerateInterpolator(1.6f))
                .withEndAction { animando = false }.start()
        }
    }

    /** Volta para a lateral, encolhendo, e a aba reaparece. */
    private fun fechar() {
        val v = view ?: return
        if (animando) return
        animando = true
        v.pivotX = 0f
        v.animate().scaleX(0.08f).alpha(0f).setDuration(FECHAR_MS).setInterpolator(AccelerateInterpolator())
            .withEndAction { if (view === v) mostrarAba() }.start()
    }

    /**
     * A oferta de um app: nome, quando chegou e o valor; embarque e destino, cada um com o seu botão. O nome vai
     * escrito em negrito com a cor do app ("UBER" branco, "99" amarelo), sem o logo: logo de outra empresa no app
     * arrisca a revisão da Play (Paulo, 07/11/2026).
     */
    private fun coluna(app: String, nome: String, cor: Int): View {
        val c = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val cabeca = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START }
        cabeca.addView(texto(nome, 20f, cor, negrito = true))
        cabeca.addView(texto("  Última oferta", 13f, CINZA))
        c.addView(cabeca)
        val o = UltimaOferta.carregar(ctx, app)
        if (o == null) {
            c.addView(texto("Nenhuma oferta ainda", 14f, CINZA).apply { setPadding(0, dp(8), 0, 0) })
            return c
        }
        c.addView(texto("${quando(o.quando)} · R$ ${Popup.br(o.valor)}", 13f, CINZA))
        endereco(c, "●  EMBARQUE", MolduraNeon.NEON, o.embarque)
        endereco(c, "⚑  DESTINO", Color.WHITE, o.destino)
        return c
    }

    private fun endereco(c: LinearLayout, rotulo: String, corRotulo: Int, endereco: String?) {
        c.addView(texto(rotulo, 12f, corRotulo, negrito = true).apply {
            letterSpacing = 0.06f
            setPadding(0, dp(12), 0, dp(3))
        })
        c.addView(texto(endereco ?: "não deu para ler", 14f, if (endereco == null) CINZA else Color.WHITE).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        })
        if (endereco == null) return
        val botao = MolduraNeon(densidade, corteDp = 7f, listras = false, detalhes = false, brilho = false,
            fundoAlto = Color.rgb(0x0B, 0x2A, 0x1E), fundoBaixo = Color.rgb(0x05, 0x14, 0x0E))
        c.addView(texto("📍  Ver no Maps", 14f, Color.WHITE, negrito = true).apply {
            gravity = Gravity.CENTER
            background = botao
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener {
                if (animando) return@setOnClickListener
                mostrarAba()
                UltimaOferta.abrirNoMapa(ctx, endereco)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) })
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
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (tocavelFora) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else 0),
        PixelFormat.TRANSLUCENT,
    )

    private fun dp(v: Int) = (v * densidade).toInt()

    private companion object {
        val CINZA = Color.rgb(0x9A, 0xA0, 0xA6)
        val AMARELO_99 = Color.rgb(0xFF, 0xD4, 0x00)
        val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        const val ABRIR_MS = 170L
        const val FECHAR_MS = 130L
    }
}
