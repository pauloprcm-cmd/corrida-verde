package app.corridaverde

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import java.util.Locale

/**
 * Tudo do dia a dia num lugar: quanto sobrou, registrar por voz, cobrar no Pix, recibo, o resumo de
 * dia/semana/mês e o Indo pra casa.
 */
class MeuDiaActivity : Activity() {
    private lateinit var v: Visual

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
        tela.addView(v.topo("Meu dia"))

        // Quanto sobrou hoje, com ganhos por app e o último consumo. Toque abre dia, semana e mês.
        val b = Dinheiro.hoje(this)
        val quadro = v.cartao()
        quadro.addView(v.texto("Hoje sobrou", 17f, cor = v.texto2))
        quadro.addView(v.texto(Dinheiro.rs(b.sobrou), 40f, negrito = true, cor = if (b.sobrou < 0) v.vermelhoClaro else v.texto))
        quadro.addView(v.texto("Ganhos ${Dinheiro.rs(b.entrou)}   ·   Gastos ${Dinheiro.rs(b.gastou)}", 17f).apply { setPadding(0, v.dp(4), 0, 0) })
        if (b.porApp.isNotEmpty()) {
            quadro.addView(v.texto(b.porApp.entries.joinToString(" · ") { "${it.key} ${Dinheiro.rs(it.value)}" }, 15f, cor = v.texto2))
        }
        Gastos.consumo(Gastos.todos(this))?.let {
            quadro.addView(v.texto(
                "Último consumo: ${String.format(Locale("pt", "BR"), "%.1f", it.kmPorLitro)} km/${it.unidade.lowercase()} · R$ ${Popup.br(it.custoKm)}/km",
                15f, cor = v.texto2,
            ))
        }
        v.por(tela, quadro, espaco = 0)

        v.por(tela, v.botaoPrincipal("Falar um ganho ou gasto") { startActivity(Intent(this, GastoActivity::class.java)) }
            .apply { setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_voz, 0, 0, 0) }, espaco = 18, altura = v.dp(68))
        tela.addView(v.texto("Ex.: “ganhei 50” · “fiz 346 na 99” · “abasteci 120 de etanol, tanque cheio”", 15f, cor = v.texto2)
            .apply { setPadding(v.dp(4), v.dp(8), v.dp(4), 0) })

        v.por(tela, v.item(R.drawable.ic_qr, "Cobrar no Pix", "QR Code com o valor, para o passageiro pagar") {
            startActivity(PixActivity.abrir(this))
        }, espaco = 18)
        v.por(tela, v.item(R.drawable.ic_recibo, "Fazer recibo", "Fale o valor e o trajeto") { startActivity(ReciboActivity.porVoz(this)) })
        val recibos = LinearLayout(this)
        recibos.addView(v.botao("Recibos enviados") { startActivity(ReciboActivity.lista(this)) },
            LinearLayout.LayoutParams(0, v.dp(60), 1f).apply { marginEnd = v.dp(6) })
        recibos.addView(v.botao("Personalizar") { startActivity(Intent(this, PersonalizarReciboActivity::class.java)) },
            LinearLayout.LayoutParams(0, v.dp(60), 1f).apply { marginStart = v.dp(6) })
        v.por(tela, recibos, espaco = 8)

        v.por(tela, v.item(R.drawable.ic_grafico, "Dia, semana e mês", "Quanto entrou, saiu e sobrou; corrigir lançamentos") {
            startActivity(Intent(this, DinheiroActivity::class.java))
        }, espaco = 18)

        val cfg = Config.carregar(this)
        val casa = when {
            cfg.casa == null -> "Cadastre sua casa em Ajustes"
            cfg.indoPraCasa -> "LIGADO: toque para desligar"
            else -> "Desligado: toque para ligar"
        }
        v.por(tela, v.item(R.drawable.ic_casa, "Indo pra casa", casa) {
            if (cfg.casa == null) {
                startActivity(Intent(this, AjustesActivity::class.java))
            } else {
                CasaReceiver.alternar(this)
                Notificacao.atualizar(this)
                montar()
            }
        })
    }
}
