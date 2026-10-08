package app.corridaverde

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Confere sozinho o que costuma dar errado e mostra ✅ ou ❌, cada ❌ com uma frase simples e um botão
 * Resolver que abre a tela certa do Android. Roda de novo sempre que a pessoa volta para a tela.
 */
class VerificarActivity : Activity() {
    private lateinit var v: Visual

    /** Um item da lista: [ok] diz se está certo; [resolver] é o botão, quando há o que fazer. */
    private class Item(val titulo: String, val ok: Boolean, val problema: String, val resolver: (() -> Unit)? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = Visual(this)
    }

    override fun onResume() {
        super.onResume()
        montar()
    }

    private fun montar() {
        val tela = v.tela()
        tela.addView(v.topo("Verificar problemas"))
        val itens = conferir()
        val problemas = itens.count { !it.ok }
        tela.addView(v.texto(
            if (problemas == 0) "Tudo certo. O Corrida Verde está pronto para as ofertas da Uber."
            else if (problemas == 1) "Achei 1 coisa para resolver." else "Achei $problemas coisas para resolver.",
            19f, negrito = true, cor = if (problemas == 0) v.verde else v.texto,
        ).apply { setPadding(0, 0, 0, v.dp(6)) })
        // Primeiro o que precisa de atenção.
        itens.sortedBy { it.ok }.forEach { v.por(tela, linha(it)) }
    }

    private fun conferir(): List<Item> {
        val cfg = Config.carregar(this)
        val lista = mutableListOf(
            Item("Leitura ligada", LeitorService.instancia != null, "A leitura está desligada. Sem ela, o aviso não aparece na Uber.") {
                AvisoLeitura.ligar(this)
            },
            Item("Notificações permitidas", notificacoes(), "Sem notificação, o botão Falar não aparece na barra do topo (o da tela principal continua funcionando).") {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                } else {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
            },
            Item("Fora da economia de bateria", (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName),
                "O celular pode desligar o app para poupar bateria. Na tela que abrir, toque em Bateria (ou Uso da bateria) e escolha Sem restrições.") {
                // A página do próprio app: a lista geral de otimização vem filtrada em muitos celulares (Samsung) e o app some dela.
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                    .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            },
            Item("App Uber Driver instalado", instalado(UBER), "Não achei o app de motorista da Uber neste celular.") {
                abrirLoja(UBER)
            },
            Item("Reconhecimento de voz", packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty(),
                "Este celular não tem o reconhecimento de voz do Google, usado para falar ganhos, gastos, Pix e recibo.") {
                abrirLoja(GOOGLE)
            },
        )
        if (cfg.gravar) {
            lista += Item("Câmera e microfone permitidos", GravarActivity.permitido(this), "Sem essas permissões, o botão Gravar corrida não funciona.") {
                requestPermissions(GravarActivity.PERMISSOES, 2)
            }
        }
        if (cfg.diagnostico) {
            lista += Item("Modo diagnóstico desligado", false, "O modo diagnóstico está ligado e guarda os textos da tela. Desligue quando o suporte não precisar mais.") {
                startActivity(Intent(this, AjustesActivity::class.java))
            }
        }
        return lista
    }

    private fun linha(i: Item): LinearLayout {
        val c = v.cartao(destaque = if (i.ok) null else v.vermelho)
        val cabeca = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@VerificarActivity).apply { setImageResource(if (i.ok) R.drawable.ic_ok else R.drawable.ic_erro) },
                LinearLayout.LayoutParams(v.dp(30), v.dp(30)))
            addView(v.texto(i.titulo, 19f, negrito = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = v.dp(14) })
        }
        c.addView(cabeca)
        if (!i.ok) {
            c.addView(v.texto(i.problema, 17f).apply { setPadding(0, v.dp(10), 0, 0) })
            i.resolver?.let { r ->
                c.addView(v.botaoPrincipal("Resolver") { r() },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, v.dp(60)).apply { topMargin = v.dp(14) })
            }
        }
        return c
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Notificacao.atualizar(this)
        montar()
    }

    private fun notificacoes(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
    }

    private fun instalado(pacote: String) = runCatching { packageManager.getPackageInfo(pacote, 0) }.isSuccess

    private fun abrirLoja(pacote: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pacote"))) }
            .onFailure { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pacote"))) }
    }

    private companion object {
        const val UBER = "com.ubercab.driver"
        const val GOOGLE = "com.google.android.googlequicksearchbox"
    }
}
