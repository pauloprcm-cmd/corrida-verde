package app.corridaverde

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDateTime
import java.util.Locale

/**
 * Aberta pelo botão "Registrar gasto" da notificação: ouve a fala, mostra o que entendeu
 * para o motorista conferir e salva. Depois de um tanque cheio, mostra o consumo.
 */
class GastoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) ouvir()
    }

    private fun ouvir() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ex.: abasteci 120 reais de etanol, 22 litros, tanque cheio, km 45.320")
        try {
            startActivityForResult(i, OUVIR)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Este celular não tem reconhecimento de voz. Preencha à mão.", Toast.LENGTH_LONG).show()
            formulario(Leitura(), null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != OUVIR) return
        val fala = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (resultCode != RESULT_OK || fala.isNullOrBlank()) {
            finish()
            return
        }
        formulario(LeitorGasto.ler(fala), fala)
    }

    private fun formulario(l: Leitura, fala: String?) {
        val tipos = Tipo.entries
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun rotulo(texto: String) = TextView(this).apply { text = texto; setPadding(0, dp(10), 0, 0) }.also { caixa.addView(it) }
        fun campo(valor: String, tipoTeclado: Int) = EditText(this).apply { setText(valor); inputType = tipoTeclado }.also { caixa.addView(it) }
        val decimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        if (fala != null) caixa.addView(TextView(this).apply { text = "Você disse: “$fala”"; setTypeface(typeface, android.graphics.Typeface.ITALIC) })
        rotulo("Tipo")
        val tipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@GastoActivity, android.R.layout.simple_spinner_dropdown_item, tipos.map { it.nome })
            setSelection(tipos.indexOf(l.tipo ?: Tipo.OUTRO))
        }
        caixa.addView(tipo)
        rotulo("Valor (R$)")
        val valor = campo(l.valor?.let { Popup.br(it) } ?: "", decimal)

        val rotuloLitros = rotulo("Quantidade")
        val litros = campo(l.litros?.let { String.format(PT, "%.2f", it) } ?: "", decimal)
        val rotuloKm = rotulo("Quilometragem do painel (opcional)")
        val km = campo(l.km?.toString() ?: "", InputType.TYPE_CLASS_NUMBER)
        val cheio = CheckBox(this).apply { text = "Tanque cheio"; isChecked = l.tanqueCheio }
        caixa.addView(cheio)

        val soCombustivel = listOf(rotuloLitros, litros, rotuloKm, km, cheio)
        tipo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val t = tipos[pos]
                soCombustivel.forEach { it.visibility = if (t.combustivel) View.VISIBLE else View.GONE }
                rotuloLitros.text = if (t == Tipo.GNV) "Metros cúbicos (m³)" else "Litros"
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val dialogo = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Confere o gasto?")
            .setView(ScrollView(this).apply { addView(caixa) })
            .setPositiveButton("Salvar", null)
            .setNeutralButton("Falar de novo") { _, _ -> ouvir() }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            fun num(e: EditText): Double? {
                val s = e.text.toString().trim()
                return (if (',' in s) s.replace(".", "").replace(',', '.') else s).toDoubleOrNull()
            }
            val t = tipos[tipo.selectedItemPosition]
            val v = num(valor)
            if (v == null || v <= 0) {
                valor.error = "Informe o valor"
                return@setOnClickListener
            }
            val g = Gasto(
                quando = LocalDateTime.now().withNano(0),
                tipo = t,
                valor = v,
                litros = if (t.combustivel) num(litros)?.takeIf { it > 0 } else null,
                km = if (t.combustivel) km.text.toString().filter { it.isDigit() }.toIntOrNull() else null,
                tanqueCheio = t.combustivel && cheio.isChecked,
            )
            dialogo.setOnCancelListener(null)
            dialogo.dismiss()
            salvar(g)
        }
    }

    private fun salvar(g: Gasto) {
        Gastos.adicionar(this, g)
        Notificacao.atualizar(this)
        val c = if (g.tipo.combustivel) Gastos.consumo(Gastos.todos(this)) else null
        if (c == null) {
            val dica = if (g.tanqueCheio && g.km != null) " No próximo tanque cheio com a quilometragem, o app mostra o consumo." else ""
            Toast.makeText(this, "Gasto salvo: ${g.tipo.nome} R$ ${Popup.br(g.valor)}.$dica", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Consumo desde o último tanque cheio")
            .setMessage(
                "Rodou ${c.kmRodados} km\n" +
                    "Colocou ${String.format(PT, "%.1f", c.litros)} ${c.unidade}\n\n" +
                    "${String.format(PT, "%.1f", c.kmPorLitro)} km/${c.unidade.lowercase()}\n" +
                    "R$ ${Popup.br(c.custoKm)} por km",
            )
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val OUVIR = 1
        private val PT = Locale("pt", "BR")
    }
}
