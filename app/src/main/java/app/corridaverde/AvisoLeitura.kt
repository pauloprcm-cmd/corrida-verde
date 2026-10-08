package app.corridaverde

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Aviso em destaque que a Play exige antes de mandar ligar a Acessibilidade: diz o que o app lê e
 * para quê. Só depois do Concordo abre a tela do Android; o Concordo fica guardado e não pergunta de novo.
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

    /** Abre a tela de Acessibilidade, mostrando antes o aviso se a pessoa ainda não concordou. */
    fun ligar(a: Activity) {
        val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(CONCORDOU, false)) return abrir(a)
        AlertDialog.Builder(a)
            .setTitle("Antes de ligar a leitura")
            .setMessage(TEXTO)
            .setPositiveButton("Concordo") { _, _ ->
                p.edit().putBoolean(CONCORDOU, true).apply()
                abrir(a)
            }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun abrir(a: Activity) = a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
