package app.corridaverde

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL

/** Busca a versão mais nova nas releases do GitHub e instala por cima. */
object Atualizador {
    private const val REPO = "pauloprcm-cmd/corrida-verde"
    const val EXTRA_PERGUNTAR = "perguntar"

    @Volatile private var ocupado = false

    val versaoAtual get() = "1.${BuildConfig.VERSION_CODE}"

    /** Número do build na tag "v1.N" da resposta da API do GitHub. */
    fun numeroDaTag(json: String): Int? =
        Regex(""""tag_name"\s*:\s*"v\d+\.(\d+)"""").find(json)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * perguntar: pode abrir a confirmação do Android (só com o app aberto na frente).
     * Sem perguntar, só instala se o Android deixar fazer isso em silêncio.
     */
    fun verificar(ctx: Context, perguntar: Boolean, aviso: (String) -> Unit = {}) {
        if (ocupado) return
        ocupado = true
        val app = ctx.applicationContext
        Thread {
            try {
                val numero = numeroDaTag(baixarTexto("https://api.github.com/repos/$REPO/releases/latest"))
                if (numero == null || numero <= BuildConfig.VERSION_CODE) {
                    aviso("Versão $versaoAtual (a mais nova)")
                } else {
                    aviso("Instalando a versão 1.$numero…")
                    instalar(app, "https://github.com/$REPO/releases/download/v1.$numero/corrida-verde.apk", perguntar)
                }
            } catch (e: Exception) {
                aviso("Versão $versaoAtual (não deu para buscar atualização)")
            } finally {
                ocupado = false
            }
        }.start()
    }

    private fun instalar(ctx: Context, url: String, perguntar: Boolean) {
        val instalador = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = instalador.createSession(params)
        try {
            instalador.openSession(id).use { sessao ->
                abrir(url).inputStream.use { entrada ->
                    sessao.openWrite("corrida-verde.apk", 0, -1).use { saida ->
                        entrada.copyTo(saida)
                        sessao.fsync(saida)
                    }
                }
                val retorno = Intent(ctx, ResultadoInstalacao::class.java).putExtra(EXTRA_PERGUNTAR, perguntar)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                sessao.commit(PendingIntent.getBroadcast(ctx, id, retorno, flags).intentSender)
            }
        } catch (e: Exception) {
            instalador.abandonSession(id)
            throw e
        }
    }

    private fun abrir(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
    }

    private fun baixarTexto(url: String) = abrir(url).inputStream.bufferedReader().use { it.readText() }
}

/** Recebe o resultado da instalação. Se o Android pedir confirmação, só mostra com o app aberto. */
class ResultadoInstalacao : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        // Em segundo plano não abre nada por cima da Uber: pergunta quando o app for aberto.
        if (!i.getBooleanExtra(Atualizador.EXTRA_PERGUNTAR, false)) return
        val confirmar = if (Build.VERSION.SDK_INT >= 33) {
            i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        confirmar?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
