package app.corridaverde

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Alerta de radar: borda amarela suave em volta da tela e uma etiqueta embaixo com a
 * placa do limite e a distância. Não recebe toques.
 */
class RadarPopup(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var borda: View? = null
    private var etiqueta: LinearLayout? = null
    private var placa: TextView? = null
    private var distancia: TextView? = null

    fun mostrar(r: Radar, novo: Boolean) {
        if (borda == null) criar()
        placa?.text = r.limite?.toString() ?: "!"
        distancia?.text = "${r.metros} m"
        if (novo) borda?.let { b ->
            ObjectAnimator.ofFloat(b, View.ALPHA, 1f, 0.35f, 1f).apply {
                duration = 800
                repeatCount = 1
                start()
            }
        }
    }

    private fun criar() {
        val raio = dp(28).toFloat()
        // Três contornos, do mais forte na beira ao mais fraco para dentro: parece um brilho.
        val camadas = listOf(dp(5) to 255, dp(14) to 90, dp(26) to 40).map { (largura, alfa) ->
            GradientDrawable().apply {
                cornerRadius = raio
                setColor(Color.TRANSPARENT)
                setStroke(largura, Color.argb(alfa, 0xF2, 0xB0, 0x1E))
            }
        }
        borda = View(ctx).apply { background = LayerDrawable(camadas.toTypedArray()) }
        wm.addView(borda, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, Gravity.TOP, 0))

        placa = TextView(ctx).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0x11, 0x14, 0x18))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(4), Color.rgb(0xD5, 0x2B, 0x1E))
            }
        }
        distancia = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), 0, dp(6), 0)
        }
        etiqueta = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(14), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(Color.argb(238, 0x0D, 0x11, 0x17))
                setStroke(dp(2), Color.rgb(0xF2, 0xB0, 0x1E))
            }
            addView(placa, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(distancia)
        }
        wm.addView(etiqueta, params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, dp(170)))
    }

    private fun params(w: Int, h: Int, g: Int, y: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = g
        this.y = y
    }

    fun esconder() {
        listOf(borda, etiqueta).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        borda = null
        etiqueta = null
        placa = null
        distancia = null
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    companion object {
        private const val TAXA = 22050

        /** Som "Madeira": três batidinhas graves e curtas. Sai junto com a voz do navegador. */
        val madeira: ShortArray by lazy {
            val total = (TAXA * 0.7).toInt()
            val s = DoubleArray(total)
            for ((i, inicio) in listOf(0.0, 0.16, 0.32).withIndex()) {
                val vol = if (i == 2) 0.67 else 0.50
                val ini = (inicio * TAXA).toInt()
                for (k in 0 until (0.30 * TAXA).toInt()) {
                    val t = k.toDouble() / TAXA
                    val env = minOf(1.0, t / 0.015) * exp(-t * 16)
                    val fase = (t * 392) % 1.0
                    val triangulo = 4 * abs(fase - 0.5) - 1
                    val brilho = sin(2 * PI * 1176 * t) * exp(-t * 45) * 0.25
                    if (ini + k < total) s[ini + k] += vol * env * (triangulo + brilho)
                }
            }
            ShortArray(total) { (s[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
        }

        fun tocar() {
            runCatching {
                val dados = madeira
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(TAXA)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(dados.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(dados, 0, dados.size)
                track.play()
                Thread { Thread.sleep(1500); runCatching { track.release() } }.start()
            }
        }
    }
}
