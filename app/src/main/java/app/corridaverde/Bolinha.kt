package app.corridaverde

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.hypot

/**
 * Bolinha Falar por cima da Uber e da 99, como a bolinha da 99: um toque abre a voz, dá para arrastar
 * (gruda na borda mais perto e o app lembra onde ficou) e, solta em cima do X, ela some até ligar de novo em Ajustes.
 * Quem decide quando ela aparece é o [LeitorService].
 */
class Bolinha(private val ctx: Context, private val aoTocar: () -> Unit, private val aoDescartar: () -> Unit) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val tamanho = dp(58)
    /** Distância da borda da tela. */
    private val margem = dp(6)
    private var view: View? = null
    private var lp: WindowManager.LayoutParams? = null
    private var lixeira: TextView? = null

    val visivel get() = view != null

    fun mostrar() {
        if (view != null) return
        val (largura, altura) = tela()
        val bola = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ctx.getColor(R.color.verde))
                setStroke(dp(2), Color.argb(140, 0, 0, 0))
            }
            elevation = dp(6).toFloat()
            contentDescription = "Falar ganho ou gasto"
            addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_voz) },
                FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        }
        val p = WindowManager.LayoutParams(
            tamanho, tamanho,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Primeira vez: na direita, um pouco abaixo do meio (longe do aviso, que fica no alto, e do botão Aceitar, embaixo).
            x = if (prefs.getBoolean(ESQUERDA, false)) margem else largura - tamanho - margem
            y = prefs.getInt(Y, (altura * 0.45).toInt()).coerceIn(limitesY(altura))
        }
        bola.setOnClickListener { aoTocar() }
        bola.setOnTouchListener(Arrastar(bola, p))
        runCatching { wm.addView(bola, p) }.onSuccess {
            view = bola
            lp = p
        }
    }

    fun esconder() {
        esconderLixeira()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        lp = null
    }

    /** Toque curto vira clique; passou do toque curto, arrasta e mostra o X embaixo. */
    private inner class Arrastar(private val bola: View, private val p: WindowManager.LayoutParams) : View.OnTouchListener {
        private val folga = ViewConfiguration.get(ctx).scaledTouchSlop
        private var x0 = 0f
        private var y0 = 0f
        private var px0 = 0
        private var py0 = 0
        private var arrastando = false

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = ev.rawX; y0 = ev.rawY; px0 = p.x; py0 = p.y
                    arrastando = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - x0
                    val dy = ev.rawY - y0
                    if (!arrastando && hypot(dx, dy) > folga) {
                        arrastando = true
                        mostrarLixeira()
                    }
                    if (arrastando) {
                        p.x = px0 + dx.toInt()
                        p.y = py0 + dy.toInt()
                        runCatching { wm.updateViewLayout(bola, p) }
                        lixeira?.alpha = if (sobreLixeira(ev.rawX, ev.rawY)) 1f else 0.7f
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!arrastando) {
                        v.performClick()
                        return true
                    }
                    val descartar = sobreLixeira(ev.rawX, ev.rawY)
                    esconderLixeira()
                    if (descartar) {
                        esconder()
                        aoDescartar()
                    } else grudar(bola, p)
                }
                MotionEvent.ACTION_CANCEL -> if (arrastando) {
                    esconderLixeira()
                    grudar(bola, p)
                }
            }
            return true
        }
    }

    /** Vai para a borda mais perto, como a bolinha da 99, e guarda o lugar. */
    private fun grudar(bola: View, p: WindowManager.LayoutParams) {
        val (largura, altura) = tela()
        val esquerda = p.x + tamanho / 2 < largura / 2
        p.x = if (esquerda) margem else largura - tamanho - margem
        p.y = p.y.coerceIn(limitesY(altura))
        runCatching { wm.updateViewLayout(bola, p) }
        prefs.edit().putBoolean(ESQUERDA, esquerda).putInt(Y, p.y).apply()
    }

    private fun mostrarLixeira() {
        if (lixeira != null) return
        val x = TextView(ctx).apply {
            text = "✕"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.7f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 0, 0, 0))
                setStroke(dp(3), Color.rgb(0xE5, 0x39, 0x35))
            }
        }
        val p = WindowManager.LayoutParams(
            dp(TAMANHO_X), dp(TAMANHO_X),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(BAIXO_X)
        }
        runCatching { wm.addView(x, p) }.onSuccess { lixeira = x }
    }

    private fun esconderLixeira() {
        lixeira?.let { runCatching { wm.removeView(it) } }
        lixeira = null
    }

    /** O dedo perto do X (posição na tela inteira). */
    private fun sobreLixeira(rawX: Float, rawY: Float): Boolean {
        val x = lixeira ?: return false
        val onde = IntArray(2).also { x.getLocationOnScreen(it) }
        val cx = onde[0] + x.width / 2f
        val cy = onde[1] + x.height / 2f
        return hypot(rawX - cx, rawY - cy) < dp(TAMANHO_X)
    }

    private fun tela(): Pair<Int, Int> {
        val m = ctx.resources.displayMetrics
        return m.widthPixels to m.heightPixels
    }

    private fun limitesY(altura: Int) = dp(40)..(altura - tamanho - dp(120)).coerceAtLeast(dp(40))

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private companion object {
        const val PREFS = "bolinha"
        const val ESQUERDA = "esquerda"
        const val Y = "y"
        const val TAMANHO_X = 64
        const val BAIXO_X = 56
    }
}
