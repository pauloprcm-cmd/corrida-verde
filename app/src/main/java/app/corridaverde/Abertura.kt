package app.corridaverde

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Animação de abertura: o velocímetro da marca acende vermelho, amarelo e verde, o ponteiro sobe até o
 * verde e aparece CORRIDA VERDE. Uns 1,5 s; um toque pula.
 *
 * Só quando o app abre do zero: a tela inicial sendo criada pelo ícone. Voltar da Uber, da notificação ou
 * dos recentes só traz a tela de volta e não repete. (O processo fica vivo por causa da leitura, então ele
 * não serve para saber se é "do zero".)
 */
object Abertura {
    fun talvezMostrar(a: Activity, estadoSalvo: android.os.Bundle?) {
        val peloIcone = a.intent?.action == android.content.Intent.ACTION_MAIN &&
            a.intent?.hasCategory(android.content.Intent.CATEGORY_LAUNCHER) == true
        if (estadoSalvo != null || !peloIcone) return
        val raiz = a.window.decorView as ViewGroup
        val tela = Tela(a, a.resources.getFont(R.font.orbitron))
        raiz.addView(tela, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tela.comecar { raiz.removeView(tela) }
    }

    /**
     * Desenhada no Canvas com a geometria do ícone (ic_velocimetro, 24 × 24): centro (12; 12,8), raio 7,6,
     * vermelho de 135° a 198°, amarelo de 204° a 250°, verde de 256° a 405° e o ponteiro em 315°.
     */
    private class Tela(ctx: Context, marca: Typeface) : View(ctx) {
        private val fundo = ctx.getColor(R.color.fundo)
        private val verde = ctx.getColor(R.color.verde)
        private val claro = Color.rgb(0xEA, 0xF5, 0xEE)
        private val trilho = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(40, 255, 255, 255) }
        private val arco = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val ponteiro = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = claro }
        private val nome = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = marca; textAlign = Paint.Align.LEFT; letterSpacing = 0.06f }
        private val frase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ctx.resources.getFont(R.font.atkinson_regular); textAlign = Paint.Align.CENTER; color = ctx.getColor(R.color.texto2)
        }
        private val caixa = RectF()

        /** Tempo da animação em ms (0 a [DURACAO]). */
        private var t = 0f
        private var animador: ValueAnimator? = null
        private var fim: (() -> Unit)? = null

        init {
            setBackgroundColor(fundo)
            isClickable = true
            // Um toque pula para o fim.
            setOnClickListener { sair() }
        }

        fun comecar(aoTerminar: () -> Unit) {
            fim = aoTerminar
            animador = ValueAnimator.ofFloat(0f, DURACAO).apply {
                duration = DURACAO.toLong()
                interpolator = LinearInterpolator()
                addUpdateListener { t = it.animatedValue as Float; invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) = sair()
                })
                start()
            }
        }

        private fun sair() {
            val f = fim ?: return
            fim = null
            animador?.removeAllListeners()
            animador?.cancel()
            animate().alpha(0f).setDuration(250).withEndAction(f).start()
        }

        override fun onDraw(c: Canvas) {
            val lado = min(width.toFloat(), height * 0.6f) * 0.8f
            val u = lado / 24f
            val cx = width / 2f
            val cy = height * 0.42f
            val r = 7.6f * u
            caixa.set(cx - r, cy - r, cx + r, cy + r)
            trilho.strokeWidth = 2.4f * u
            arco.strokeWidth = 2.4f * u
            ponteiro.strokeWidth = 1.32f * u

            // O trilho apagado, depois cada cor acende na sua vez.
            for ((de, ate) in listOf(135f to 198f, 204f to 250f, 256f to 405f)) c.drawArc(caixa, de, ate - de, false, trilho)
            acender(c, Color.rgb(0xFF, 0x5A, 0x4F), 135f, 198f, 0f)
            acender(c, Color.rgb(0xF5, 0xC2, 0x1B), 204f, 250f, 150f)
            acender(c, verde, 256f, 405f, 300f)

            // O ponteiro sai do vermelho e sobe até o verde, com um leve tranco no fim.
            val p = SOBE.getInterpolation(((t - 100f) / 600f).coerceIn(0f, 1f))
            val ang = Math.toRadians((135.0 + p * 180.0))
            val dx = cos(ang).toFloat()
            val dy = sin(ang).toFloat()
            c.drawLine(cx + dx * 0.9f * u, cy + dy * 0.9f * u, cx + dx * 4.4f * u, cy + dy * 4.4f * u, ponteiro)
            ponteiro.strokeWidth = 1.2f * u
            c.drawCircle(cx, cy, 0.99f * u, ponteiro)

            // CORRIDA VERDE sobe um pouco e aparece; a frase vem logo depois.
            val n = ((t - 450f) / 350f).coerceIn(0f, 1f)
            if (n > 0f) {
                nome.textSize = width * 0.085f
                val y = cy + r + width * 0.2f + (1 - n) * width * 0.04f
                val a = (n * 255).toInt()
                val corrida = "CORRIDA "
                val total = nome.measureText("CORRIDA VERDE")
                val x0 = cx - total / 2
                nome.color = Color.argb(a, Color.red(claro), Color.green(claro), Color.blue(claro))
                c.drawText(corrida, x0, y, nome)
                nome.color = Color.argb(a, Color.red(verde), Color.green(verde), Color.blue(verde))
                c.drawText("VERDE", x0 + nome.measureText(corrida), y, nome)
            }
            val f = ((t - 650f) / 300f).coerceIn(0f, 1f)
            if (f > 0f) {
                frase.textSize = width * 0.045f
                frase.alpha = (f * 255).toInt()
                c.drawText("Vale a corrida? Veja em um segundo.", cx, cy + r + width * 0.29f, frase)
            }
        }

        /** Um pedaço do arco que vai se preenchendo, começando em [inicio] ms e levando 150 ms. */
        private fun acender(c: Canvas, cor: Int, de: Float, ate: Float, inicio: Float) {
            val p = ((t - inicio) / 150f).coerceIn(0f, 1f)
            if (p == 0f) return
            arco.color = cor
            c.drawArc(caixa, de, (ate - de) * p, false, arco)
        }

        private companion object {
            const val DURACAO = 1300f
            val SOBE = OvershootInterpolator(1.6f)
        }
    }
}
