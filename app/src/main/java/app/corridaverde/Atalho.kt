package app.corridaverde

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/**
 * Atalho "Falar" (registrar ganho ou gasto por voz com um toque): aparece segurando o dedo no ícone do app
 * e pode virar um ícone próprio na tela inicial, ao lado da Uber e da 99. Quem não quiser, remove como
 * qualquer ícone; o app continua instalado.
 */
object Atalho {
    private const val ID = "falar"

    private fun info(ctx: Context): ShortcutInfo = ShortcutInfo.Builder(ctx, ID)
        .setShortLabel("Falar")
        .setLongLabel("Falar ganho ou gasto")
        .setIcon(Icon.createWithResource(ctx, R.mipmap.ic_falar))
        .setIntent(Intent(ctx, GastoActivity::class.java).setAction(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        .build()

    private fun gerente(ctx: Context) = ctx.getSystemService(ShortcutManager::class.java)

    /** O atalho do menu do ícone (segurar o dedo no Corrida Verde). */
    fun publicar(ctx: Context) {
        runCatching { gerente(ctx)?.dynamicShortcuts = listOf(info(ctx)) }
    }

    fun naTelaInicial(ctx: Context) = runCatching { gerente(ctx)?.pinnedShortcuts?.any { it.id == ID } == true }.getOrDefault(false)

    fun podeColocar(ctx: Context) = runCatching { gerente(ctx)?.isRequestPinShortcutSupported == true }.getOrDefault(false)

    /** O próprio Android pergunta e coloca o ícone. Devolve false se este celular não deixa. */
    fun colocar(ctx: Context): Boolean = podeColocar(ctx) && runCatching { gerente(ctx)!!.requestPinShortcut(info(ctx), null) }.getOrDefault(false)
}
