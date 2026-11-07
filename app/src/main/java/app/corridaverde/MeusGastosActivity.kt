package app.corridaverde

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Os tipos de gasto que o motorista criou ("Supermercado", "Escola dos filhos"): trocar o nome (os gastos antigos
 * vão junto), dizer se é pessoal ou do trabalho e esconder da lista o que não usa mais (os gastos ficam).
 */
class MeusGastosActivity : Activity() {
    private lateinit var v: Visual

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = Visual(this)
        montar()
    }

    private fun montar() {
        val tela = v.tela()
        tela.addView(v.topo("Meus tipos de gasto"))
        tela.addView(v.texto("Nascem quando você escreve o nome de um gasto \"Outro\". Os pessoais ficam fora do Sobrou do trabalho " +
            "e aparecem separados em Dia, semana e mês.", 16f, cor = v.texto2))
        val gastos = Gastos.todos(this)
        val tipos = TiposGasto.todos(this)
        if (tipos.isEmpty()) {
            v.por(tela, v.texto("Você ainda não criou nenhum. No Falar ganho ou gasto, escolha \"Outro (escrever qual)\" e escreva o nome, " +
                "por exemplo Supermercado ou Dentista.", 17f), espaco = 20)
            return
        }
        val usos = gastos.filter { it.tipo == Tipo.OUTRO }.groupingBy { TiposGasto.chave(it.descricao) }.eachCount()
        (TiposGasto.naLista(tipos, gastos) + tipos.filter { it.escondido }.sortedBy { TiposGasto.chave(it.nome) }).forEach { t ->
            val n = usos[TiposGasto.chave(t.nome)] ?: 0
            val detalhe = listOfNotNull(
                if (t.pessoal) "Pessoal" else "Do trabalho",
                if (n == 1) "1 gasto" else "$n gastos",
                "escondido da lista".takeIf { t.escondido },
            ).joinToString(" · ")
            v.por(tela, v.item(R.drawable.ic_carteira, t.nome, detalhe) { editar(t) }, espaco = 10)
        }
    }

    private fun editar(t: TipoProprio) {
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(v.dp(20), v.dp(8), v.dp(20), 0)
        }
        val nome = EditText(this).apply {
            setText(t.nome)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        caixa.addView(nome)
        val pessoal = CheckBox(this).apply { text = "É gasto pessoal (fica fora do Sobrou do trabalho)"; isChecked = t.pessoal }
        caixa.addView(pessoal)
        val esconder = CheckBox(this).apply { text = "Esconder da lista (os gastos já lançados continuam)"; isChecked = t.escondido }
        caixa.addView(esconder)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Tipo de gasto")
            .setView(caixa)
            .setPositiveButton("Salvar") { _, _ ->
                val novo = TiposGasto.limpo(nome.text.toString())
                if (novo.isEmpty()) {
                    Toast.makeText(this, "O nome não pode ficar vazio", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                TiposGasto.atualizar(this, t, TipoProprio(novo, pessoal.isChecked, esconder.isChecked))
                Notificacao.atualizar(this)
                montar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
