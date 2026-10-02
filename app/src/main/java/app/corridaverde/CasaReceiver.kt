package app.corridaverde

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** Botão "Indo pra casa" da notificação: liga ou desliga o modo (ligado vale só até o fim do dia). */
class CasaReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        alternar(ctx)
        Notificacao.atualizar(ctx)
    }

    companion object {
        fun alternar(ctx: Context) {
            val c = Config.carregar(ctx)
            c.copy(indoPraCasaDia = if (c.indoPraCasa) "" else LocalDate.now().toString()).salvar(ctx)
        }
    }
}
