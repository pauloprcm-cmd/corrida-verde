package app.corridaverde

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import java.time.LocalDate

/** Notificação fixa enquanto a leitura está ligada, com o saldo do dia e os botões "Registrar por voz" e "Gravar corrida". */
object Notificacao {
    private const val CANAL = "gastos"
    private const val ID = 1

    fun atualizar(ctx: Context) {
        if (LeitorService.instancia == null) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Registro de gastos", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
        val gastos = Gastos.totalDoDia(Gastos.todos(ctx), LocalDate.now())
        val ganhos = Ganhos.doDia(ctx)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val abrirApp = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), flags)
        val registrar = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, GastoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags,
        )
        val b = Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_microfone)
            .setContentTitle("Leitor de ofertas ligado")
            .setContentText("Hoje: ganhos R$ ${Popup.br(ganhos)} · gastos R$ ${Popup.br(gastos)} · sobra R$ ${Popup.br(ganhos - gastos)}")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(abrirApp)
            .addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_microfone), "Registrar por voz", registrar).build())
        if (Config.carregar(ctx).gravar && !GravacaoService.gravando) {
            val gravar = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, GravarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags,
            )
            b.addAction(Notification.Action.Builder(null, "Gravar corrida", gravar).build())
        }
        runCatching { nm.notify(ID, b.build()) }
    }

    fun remover(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
