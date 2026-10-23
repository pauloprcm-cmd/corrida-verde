package app.corridaverde

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/**
 * Aviso em destaque que a Play exige antes de mandar ligar a Acessibilidade: diz o que o app lê e
 * para quê. Só depois do Concordo vem o passo a passo e a tela do Android; o Concordo fica guardado e não pergunta de novo.
 */
object AvisoLeitura {
    private const val PREFS = "aviso_leitura"
    private const val CONCORDOU = "concordou"

    const val TEXTO =
        "O Corrida Verde usa o Serviço de Acessibilidade do Android para ler, na tela, as ofertas de corrida " +
            "da Uber e da 99 e os avisos de radar do Waze e da navegação da 99. Na 99, ele tira uma foto da oferta " +
            "para ler o texto e apaga a foto na hora.\n\n" +
            "Com isso ele calcula quanto a corrida paga em relação ao taxímetro e avisa dos radares.\n\n" +
            "O app só lê. Nunca toca na tela, nunca aceita nem recusa corrida. Nada do que é lido sai do seu " +
            "celular, a não ser que você mesmo toque em Enviar diagnóstico."

    /** Abre o passo a passo de ligar ([LigarLeituraActivity]), mostrando antes o aviso se a pessoa ainda não concordou. */
    fun ligar(a: Activity) {
        val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(CONCORDOU, false)) return passoAPasso(a)
        AlertDialog.Builder(a)
            .setTitle("Antes de ligar a leitura")
            .setMessage(TEXTO)
            .setPositiveButton("Concordo") { _, _ ->
                p.edit().putBoolean(CONCORDOU, true).apply()
                passoAPasso(a)
            }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun passoAPasso(a: Activity) = a.startActivity(Intent(a, LigarLeituraActivity::class.java))

    /**
     * Abre a lista de Acessibilidade com o Corrida Verde em destaque (os celulares que não aceitam o
     * destaque abrem a lista normal) e guarda a hora, para o app voltar sozinho quando a leitura ligar.
     */
    fun abrirConfiguracoes(a: Activity) {
        a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(FOI_LIGAR, System.currentTimeMillis()).apply()
        val nome = ComponentName(a, LeitorService::class.java).flattenToString()
        a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(DESTAQUE, nome)
            .putExtra(DESTAQUE_ARGS, Bundle().apply { putString(DESTAQUE, nome) }))
    }

    /** A leitura acabou de ligar: diz se foi pelo botão Abrir as configurações nos últimos 15 minutos, e esquece. */
    fun ligouAgora(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val quando = p.getLong(FOI_LIGAR, 0)
        if (quando == 0L) return false
        p.edit().remove(FOI_LIGAR).apply()
        return System.currentTimeMillis() - quando < 15 * 60_000L
    }

    private const val FOI_LIGAR = "foi_ligar"
    // Extras da tela de Configurações que rolam a lista até o item e o fazem piscar.
    private const val DESTAQUE = ":settings:fragment_args_key"
    private const val DESTAQUE_ARGS = ":settings:show_fragment_args"
}
