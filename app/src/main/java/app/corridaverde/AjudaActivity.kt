package app.corridaverde

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Central de Ajuda, primeira versão: perguntas que abrem e fecham com um toque. Os textos vêm do que
 * o app já explicava; a versão revisada (Plano de Ajuda, seção 4) entra depois.
 */
class AjudaActivity : Activity() {
    private lateinit var v: Visual

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = Visual(this)
        val tela = v.tela()
        tela.addView(v.topo("Ajuda"))
        val cfg = Config.carregar(this)

        pergunta(tela, "Como ligar a leitura?", PASSO_A_PASSO) {
            addView(v.botaoPrincipal("Ir para Acessibilidade") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, cheio())
            addView(v.botao("Liberar configuração restrita") { liberar(this@AjudaActivity) }, cheio())
        }
        pergunta(tela, "O aviso parou de aparecer no meio do dia. Por quê?",
            "Quase sempre é a economia de bateria do celular: para poupar bateria, ele fecha o Corrida Verde sem avisar. " +
                "Aí o aviso da Uber e da 99, o alerta de radar e o botão Falar da notificação param até você ligar a leitura de novo. " +
                "Seus ganhos, gastos e recibos continuam guardados.\n\n" +
                "Para não acontecer mais, deixe o app Sem restrições: segure o dedo no ícone do Corrida Verde, toque em Informações do app › Bateria " +
                "(ou Uso da bateria) e escolha Sem restrições. A bateria quase não sente: o app só trabalha quando aparece uma oferta ou um radar.\n\n" +
                "No Samsung, confira também se o Corrida Verde não está em Apps em suspensão profunda " +
                "(Configurações › Bateria ou Cuidados com o aparelho › Limites de uso em segundo plano). Se estiver, tire de lá.") {
            addView(v.botaoPrincipal("Verificar problemas") { startActivity(Intent(this@AjudaActivity, VerificarActivity::class.java)) }, cheio())
        }
        if (cfg.motoristaDeApp) pergunta(tela, "O que o aviso colorido quer dizer?",
            "O número grande é quanto a oferta da Uber ou da 99 paga por km, somando a busca até o passageiro e a viagem.\n\n" +
                "Cada grupo de categoria tem o seu mínimo: Econômico (UberX, 99Pop), Conforto (Comfort, 99Plus) e Premium (Black). " +
                "A última linha do aviso mostra o grupo que o app reconheceu e o mínimo usado.\n\n" +
                "Verde: a partir do mínimo verde do grupo. Amarelo: entre o amarelo e o verde. Vermelho: abaixo do amarelo.\n\n" +
                "Se a busca até o passageiro passar de ${Popup.br(cfg.buscaMaxKm).removeSuffix(",00")} km, a cor cai um nível. " +
                "Dá para mudar os mínimos em Ajustes.\n\n" +
                "Na 99, o valor pode subir no fim da corrida (trânsito, pedágio): o aviso mostra o mínimo. " +
                "Para não ver o aviso na 99, desmarque em Ajustes › Avisos na oferta.")
        else pergunta(tela, "O que o aviso colorido quer dizer?",
            "O número grande é quanto a oferta da Uber ou da 99 paga perto do que o taxímetro daria na mesma viagem.\n\n" +
                "Verde: ${cfg.limiteVerde}% ou mais.\nAmarelo: de ${cfg.limiteAmarelo}% a ${cfg.limiteVerde - 1}%.\nVermelho: abaixo de ${cfg.limiteAmarelo}%.\n\n" +
                "Se a busca até o passageiro passar de ${Popup.br(cfg.buscaMaxKm).removeSuffix(",00")} km, a cor cai um nível. " +
                "Dá para mudar esses números em Ajustes.\n\n" +
                "Na 99, o valor pode subir no fim da corrida (taxímetro, trânsito, pedágio): o aviso mostra o mínimo. " +
                "Para não ver o aviso na 99, desmarque em Ajustes › Avisos na oferta.")
        pergunta(tela, "Como registro ganhos e gastos falando?",
            "Toque em Falar ganho ou gasto, na tela principal ou no Meu dia. Com a Uber ou a 99 aberta, toque na bolinha verde do microfone (dá para arrastar; para tirar, arraste até o X ou desmarque em Ajustes › Botão de falar). Também dá pelo 🎤 Falar da notificação do Corrida Verde.\n\n" +
                "Ganhos: fale o total que a tela do app mostra, como “ganhei 346 na 99”. Ele substitui o que você já tinha falado da 99 hoje. " +
                "Para somar uma corrida só, diga “corrida”: “corrida da Uber 23 e 50”. Sem dizer o app (passageiro de rua), soma: “ganhei 50”.\n\n" +
                "Gastos: “abasteci 120 reais de etanol, 22 litros, tanque cheio, quilometragem 45.320”.\n\n" +
                "Errou? Em Meu dia › Dia, semana e mês, escolha o dia e toque no lançamento para corrigir ou apagar.")
        pergunta(tela, "Como cobro no Pix com QR Code?",
            "O passageiro perguntou se aceita Pix? Fale “gera um Pix de 70 reais” ou toque em Cobrar no Pix, no Meu dia. " +
                "Na primeira vez o app pede a sua chave Pix. Depois mostra o QR Code com o valor para o passageiro pagar pelo app do banco.\n\n" +
                "O app não vê a sua conta: confira no app do seu banco se o Pix caiu.")
        if (!cfg.motoristaDeApp) pergunta(tela, "Como faço um recibo?",
            "Para o passageiro de rua, de ponto ou particular. Fale: “recibo de 50 reais, da Avenida Paulista até a Vila Mariana, no Pix”. " +
                "Você confere o recibo e manda pelo WhatsApp ou pelo e-mail. O valor entra sozinho como ganho de Táxi.\n\n" +
                "Em Meu dia › Personalizar, escolha o modelo, a cor, a logo e grave a sua assinatura.")
        pergunta(tela, "Como funciona o alerta de radar?",
            "Funciona só navegando pelo Waze ou pela 99. Quando eles avisam de um radar, a tela ganha uma borda amarela e aparece a placa do limite com a distância.\n\n" +
                "No Google Maps e na navegação da Uber o alerta não funciona: eles só desenham o radar no mapa, sem dizer a que distância ele está. " +
                "Se quiser o alerta, navegue pelo Waze ou pela 99.")
        pergunta(tela, "O que é o Indo pra casa?",
            "No fim do dia, ligue o Indo pra casa no Meu dia ou na notificação. Quando a corrida termina perto da sua casa, o aviso mostra 🏠 Perto de casa, em verde. " +
                "Se termina longe, aparece só uma linha pequena com a distância. Cadastre a casa em Ajustes. Desliga sozinho no dia seguinte.")
        pergunta(tela, "Troquei de celular. Meus dados voltam?",
            "Se o backup do Google estiver ligado no celular, os ganhos, gastos, recibos e ajustes voltam sozinhos quando você " +
                "configura o celular novo com a mesma conta Google e escolhe restaurar. Esse backup é feito uma vez por dia.\n\n" +
                "Para garantir, guarde também a sua cópia: em Ajustes › Cópia dos seus dados, toque em Guardar uma cópia " +
                "(no Google Drive) ou em Mandar a cópia (WhatsApp ou e-mail para você mesmo). No celular novo, toque em Restaurar " +
                "de uma cópia e escolha o arquivo.") {
            addView(v.botao("Abrir Ajustes") { startActivity(Intent(this@AjudaActivity, AjustesActivity::class.java)) }, cheio())
        }
        pergunta(tela, "O app aceita ou recusa corrida sozinho?",
            "Não. O Corrida Verde só lê a tela e mostra o aviso. Quem aceita ou recusa é sempre você, no app da Uber.")
    }

    /** Uma pergunta que abre e fecha com um toque; [extra] acrescenta botões dentro da resposta. */
    private fun pergunta(tela: LinearLayout, titulo: String, resposta: String, extra: (LinearLayout.() -> Unit)? = null) {
        val cartao = v.cartao().apply { background = v.forma(v.cartao, v.borda, 20) }
        val seta = ImageView(this).apply { setImageResource(R.drawable.ic_seta); rotation = 90f }
        val cabeca = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(v.texto(titulo, 19f, negrito = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(seta, LinearLayout.LayoutParams(v.dp(24), v.dp(24)))
        }
        val corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            addView(v.texto(resposta, 17f).apply { setPadding(0, v.dp(12), 0, 0) })
            extra?.invoke(this)
        }
        cartao.addView(cabeca)
        cartao.addView(corpo)
        cartao.isClickable = true
        cartao.setOnClickListener {
            val abrir = corpo.visibility != android.view.View.VISIBLE
            corpo.visibility = if (abrir) android.view.View.VISIBLE else android.view.View.GONE
            seta.rotation = if (abrir) -90f else 90f
        }
        v.por(tela, cartao)
    }

    private fun cheio() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, v.dp(60)).apply { topMargin = v.dp(12) }

    companion object {
        private const val PASSO_A_PASSO =
            "1. Toque em Ligar a leitura (tela inicial). Abre a tela Acessibilidade do Android.\n\n" +
                "2. Procure Apps instalados (em alguns celulares: Serviços instalados ou Aplicativos baixados).\n\n" +
                "3. Toque em Corrida Verde e ligue a chave.\n\n" +
                "4. O Android avisa que o app pode ver a tela. Toque em Permitir. O Corrida Verde só lê a oferta e o aviso de radar: não toca em nada.\n\n" +
                "5. Volte para este app. No topo deve aparecer LIGADO, em verde.\n\n" +
                "Apareceu “Configuração restrita” e a chave não liga? Isso acontece com apps instalados fora da Play Store. " +
                "Toque em Liberar configuração restrita, depois no ⋮ (canto de cima, à direita) e em Permitir configurações restritas. " +
                "Confirme com o PIN ou a digital e repita os passos de 1 a 4."

        private const val POR_QUE_DESLIGA =
            "O Android às vezes desliga a leitura sozinho: depois de uma atualização do app, quando o celular reinicia, " +
                "ou quando a economia de bateria fecha apps que ficam ligados o tempo todo.\n\n" +
                "Para ligar de novo, toque em Ligar de novo e ligue a chave do Corrida Verde. " +
                "Se acontecer sempre, use Verificar problemas na tela inicial para tirar o app da economia de bateria."

        /** O passo a passo para ligar a leitura (ou por que ela caiu), com atalho para as telas do Android. */
        fun comoLigar(a: Activity, jaLigou: Boolean = false) {
            AlertDialog.Builder(a)
                .setTitle(if (jaLigou) "Por que a leitura desligou?" else "Como ligar a leitura")
                .setMessage(if (jaLigou) POR_QUE_DESLIGA + "\n\n" + PASSO_A_PASSO else PASSO_A_PASSO)
                .setPositiveButton("Ir para Acessibilidade") { _, _ -> a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNeutralButton("Liberar configuração") { _, _ -> liberar(a) }
                .setNegativeButton("Fechar", null)
                .show()
        }

        fun liberar(a: Activity) =
            a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.packageName, null)))
    }
}
