package app.corridaverde

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Moldura de aspecto futurista do painel Última oferta (desenho do Paulo, 07/11/2026): cantos cortados em diagonal,
 * contorno neon com brilho (várias linhas por cima, da mais larga e apagada à mais fina e forte), fundo escuro com
 * listras finas e tracinhos decorativos. Desenhada em código, e não a partir da imagem, para ficar nítida em
 * qualquer tela e com os endereços de cada oferta.
 */
class MolduraNeon(
    private val densidade: Float,
    /** Tamanho do corte em diagonal nos cantos de cima à esquerda e de baixo à direita, em dp. */
    private val corteDp: Float = 14f,
    private val listras: Boolean = true,
    /** Tracinhos no alto à direita e embaixo à esquerda. */
    private val detalhes: Boolean = true,
    private val brilho: Boolean = true,
    private val fundoAlto: Int = Color.rgb(0x10, 0x1E, 0x18),
    private val fundoBaixo: Int = Color.rgb(0x03, 0x08, 0x06),
) : Drawable() {
    private val contorno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.MITER }
    private val preenchimento = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val traco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NEON }
    private val forma = Path()

    private fun dp(v: Float) = v * densidade

    /** A folga em volta da moldura onde cabe o brilho. */
    val folga get() = if (brilho) dp(4f) else dp(1f)

    override fun draw(c: Canvas) {
        val b = bounds
        val f = folga
        val l = b.left + f
        val t = b.top + f
        val r = b.right - f
        val bo = b.bottom - f
        val corte = minOf(dp(corteDp), (r - l) / 3, (bo - t) / 3)
        forma.reset()
        forma.moveTo(l + corte, t)
        forma.lineTo(r, t)
        forma.lineTo(r, bo - corte)
        forma.lineTo(r - corte, bo)
        forma.lineTo(l, bo)
        forma.lineTo(l, t + corte)
        forma.close()

        preenchimento.shader = LinearGradient(0f, t, 0f, bo, fundoAlto, fundoBaixo, Shader.TileMode.CLAMP)
        c.drawPath(forma, preenchimento)

        if (listras) {
            c.save()
            c.clipPath(forma)
            linha.color = Color.argb(16, 0x3D, 0xF5, 0xA8)
            linha.strokeWidth = dp(1f)
            val passo = dp(7f)
            var x = l - (bo - t)
            while (x < r) {
                c.drawLine(x, bo, x + (bo - t), t, linha)
                x += passo
            }
            c.restore()
        }

        // O brilho: do contorno largo e quase transparente ao fino e forte.
        if (brilho) {
            for ((largura, alfa) in listOf(7f to 28, 4f to 60, 2.5f to 120)) {
                contorno.strokeWidth = dp(largura)
                contorno.color = Color.argb(alfa, 0x3D, 0xF5, 0xA8)
                c.drawPath(forma, contorno)
            }
        }
        contorno.strokeWidth = dp(1.3f)
        contorno.color = NEON
        c.drawPath(forma, contorno)

        // Os cortes dos cantos mais grossos, como as presilhas do desenho.
        linha.color = NEON
        linha.strokeWidth = dp(3f)
        c.drawLine(l, t + corte, l + corte, t, linha)
        c.drawLine(r, bo - corte, r - corte, bo, linha)

        if (detalhes && r - l > dp(120f)) {
            val w = dp(4f)
            val vao = dp(2.5f)
            val h = dp(2.5f)
            for (i in 0 until 7) {
                val x = r - corte - dp(8f) - (i + 1) * (w + vao)
                c.drawRect(x, t + dp(4f), x + w, t + dp(4f) + h, traco)
            }
            for (i in 0 until 7) {
                val x = l + dp(10f) + i * (w + vao)
                c.drawRect(x, bo - dp(4f) - h, x + w, bo - dp(4f), traco)
            }
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** O verde-água neon do desenho. */
        val NEON = Color.rgb(0x3D, 0xF5, 0xA8)
    }
}
