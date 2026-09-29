package app.corridaverde

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import java.time.LocalDate

/** Notificação fixa enquanto a leitura está ligada, com o saldo do dia e o botão "Registrar gasto". */
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
        val ganhos = Sessoes.totalDoDia(Sessoes.todas(ctx), LocalDate.now())
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val abrirApp = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), flags)
        val registrar = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, GastoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags,
        )
        val n = Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_microfone)
            .setContentTitle("Leitor de ofertas ligado")
            .setContentText("Hoje: ganhos R$ ${Popup.br(ganhos)} · gastos R$ ${Popup.br(gastos)} · sobra R$ ${Popup.br(ganhos - gastos)}")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(abrirApp)
            .addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_microfone), "Registrar gasto", registrar).build())
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun remover(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
