package app.corridaverde

import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.RadioButton

/**
 * Passo a passo para tirar o app da economia de bateria, que fecha a leitura no meio do dia e faz o
 * aviso sumir no fim da tarde. No Samsung: Bateria › Não restrito, na página do app. Quando a opção
 * muda, o app volta sozinho para a tela inicial com o "Pronto!" (vigia por até 3 minutos).
 */
class BateriaActivity : ManualActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = Visual(this)
        val tela = v.tela()
        tela.addView(v.topo("Bateria sem restrição"))
        tela.addView(v.texto("Para poupar bateria, o celular pode desligar o Corrida Verde no meio do dia. Aí o aviso some e você nem percebe.", 19f))
        tela.addView(v.texto("Toque no botão verde e faça os 2 passos abaixo. Depois, o Corrida Verde volta sozinho para cá.", 19f)
            .apply { setPadding(0, v.dp(12), 0, 0) })
        v.por(tela, abrir(), espaco = 20, altura = v.dp(68))

        v.por(tela, v.secao("O que tocar nas configurações"), espaco = 8)
        passo(tela, 1, "Toque em Bateria", linha().apply {
            addView(LinearLayout(this@BateriaActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(v.texto("Bateria", 19f))
                addView(v.texto("2% usados desde a última carga completa", 14f, cor = v.texto2))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }, if (samsung) "Role a tela para baixo: fica na parte Uso." else "Em alguns celulares o nome é Uso da bateria do app.")
        val livre = if (samsung) "Não restrito" else "Sem restrições"
        passo(tela, 2, "Escolha $livre", LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(opcao(livre, marcada = true), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(opcao(if (samsung) "Otimizado" else "Otimizada", marcada = false), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = v.dp(8) })
        }, "A bateria quase não sente: o app só trabalha quando aparece uma oferta ou um radar.")

        v.por(tela, abrir(), espaco = 24, altura = v.dp(68))
    }

    override fun onResume() {
        super.onResume()
        // Voltou por conta própria (ou a volta automática não pôde abrir o app): fecha com o "Pronto!" do mesmo jeito.
        if (!livre(this)) return
        vigia.removeCallbacksAndMessages(null)
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.PRONTO, MainActivity.BATERIA)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    /** A página do próprio app: a lista geral de otimização vem filtrada em muitos celulares (Samsung) e o app some dela. */
    override fun abrirConfiguracoes() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        vigiar(applicationContext)
    }

    /** A opção do Android em miniatura: a escolhida com o contorno verde, a outra apagada. */
    private fun opcao(texto: String, marcada: Boolean) = (if (marcada) linha() else LinearLayout(this).apply {
        minimumHeight = v.dp(56)
        setPadding(v.dp(16), v.dp(6), v.dp(16), v.dp(6))
        background = v.forma(v.fundo, v.borda, 14)
        alpha = 0.6f
    }).apply {
        addView(RadioButton(this@BateriaActivity).apply {
            text = texto
            isChecked = marcada
            isClickable = false
            isFocusable = false
            buttonTintList = ColorStateList.valueOf(if (marcada) v.verde else v.texto2)
        })
    }

    companion object {
        private val vigia = Handler(Looper.getMainLooper())

        fun livre(ctx: Context) = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

        /**
         * Confere a cada segundo, por até 3 minutos, se a pessoa já escolheu Não restrito e, se sim, traz o app
         * de volta. Abrir o app do fundo só funciona com a leitura ligada (o Android deixa por causa dela).
         */
        private fun vigiar(ctx: Context) {
            vigia.removeCallbacksAndMessages(null)
            val fim = System.currentTimeMillis() + 3 * 60_000L
            vigia.post(object : Runnable {
                override fun run() {
                    if (livre(ctx)) {
                        if (LeitorService.instancia != null) ctx.startActivity(Intent(ctx, MainActivity::class.java)
                            .putExtra(MainActivity.PRONTO, MainActivity.BATERIA)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        return
                    }
                    if (System.currentTimeMillis() < fim) vigia.postDelayed(this, 1000)
                }
            })
        }
    }
}
