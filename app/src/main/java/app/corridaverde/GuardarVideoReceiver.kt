package app.corridaverde

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Botão "Guardar este vídeo" da notificação que aparece quando a gravação para. */
class GuardarVideoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pedacos = intent.getStringArrayExtra(PEDACOS)?.toList().orEmpty()
        if (pedacos.isEmpty()) return
        Videos.guardar(ctx, pedacos)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.notify(ID, Notification.Builder(ctx, CANAL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Vídeo guardado")
            .setContentText("Está em Movies/${Videos.PASTA} e não será apagado na limpeza.")
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build())
    }

    companion object {
        const val PEDACOS = "pedacos"
        const val ID = 6
        /** O mesmo canal da gravação (criado por ela). */
        private const val CANAL = "gravacao"
    }
}
