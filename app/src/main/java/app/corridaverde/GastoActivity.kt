package app.corridaverde

import android.app.Activity
import android.app.AlertDialog
import android.view.ContextThemeWrapper
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDateTime
import java.util.Locale

/**
 * Aberta pelo botão Falar (tela principal, Meu dia, bolinha em cima da Uber e da 99 ou notificação): ouve a fala, decide se é gasto ou ganho,
 * mostra o que entendeu para o motorista conferir e salva. Depois de um tanque cheio, mostra o consumo.
 * Aberta por [editar], mostra um lançamento já salvo para corrigir ou apagar.
 */
class GastoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val editando = intent.getStringExtra(EXTRA_GASTO)?.let { Gastos.deLinha(it) }
            ?: intent.getStringExtra(EXTRA_CORRIDA)?.let { Corridas.deLinha(it) }
            ?: intent.getStringExtra(EXTRA_TOTAL)?.let { Totais.deLinha(it) }
        if (editando != null) formulario(null, editando) else ouvir()
    }

    private fun ouvir() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ex.: fiz 200 na 99 · corrida da Uber 23 e 50 · abasteci 120 de etanol, tanque cheio")
        try {
            startActivityForResult(i, OUVIR)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Este celular não tem reconhecimento de voz. Preencha à mão.", Toast.LENGTH_LONG).show()
            formulario(null)
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
        // "Recibo de 50 reais, da Paulista até a Vila Mariana": vai para o recibo, que lança o ganho junto.
        // Só no perfil taxista; para o motorista de app, "recibo de 50" vira um ganho comum.
        if (LeitorRecibo.ehRecibo(fala) && !Config.carregar(this).motoristaDeApp) {
            startActivity(ReciboActivity.daFala(this, fala))
            finish()
            return
        }
        // "Gera um Pix de 70 reais", "QR Code de 70": mostra o QR Code para o passageiro pagar.
        if (LeitorPix.ehPix(fala)) {
            startActivity(PixActivity.daFala(this, fala))
            finish()
            return
        }
        formulario(fala)
    }

    /** [editando] é o lançamento a corrigir (Gasto, Corrida ou TotalDoDia), ou null num lançamento novo. */
    private fun formulario(fala: String?, editando: Any? = null) {
        val l = (editando as? Gasto)?.let { Leitura(it.tipo, it.valor, it.litros, it.km, it.tanqueCheio) }
            ?: fala?.let { LeitorGasto.ler(it) } ?: Leitura()
        val g = when (editando) {
            is Corrida -> Ganho(editando.app, editando.valor, false)
            is TotalDoDia -> Ganho(editando.app, editando.valor, true)
            else -> fala?.let { LeitorFala.lerGanho(it) } ?: Ganho(null, null, false)
        }
        val hora = when (editando) {
            is Gasto -> editando.quando
            is Corrida -> editando.hora
            is TotalDoDia -> editando.hora
            else -> null
        }
        // A lista de tipos: os fixos, depois os que o motorista criou (mais usados primeiro) e, no fim, "Outro".
        val todosGastos = Gastos.todos(this)
        val proprios = TiposGasto.todos(this)
        val editado = (editando as? Gasto)?.takeIf { it.tipo == Tipo.OUTRO && it.descricao.isNotBlank() }
            ?.let { TiposGasto.achar(proprios, it.descricao) ?: TipoProprio(TiposGasto.limpo(it.descricao)) }
        val daFala = if (editando == null && fala != null) TiposGasto.naFala(proprios, fala) else null
        val naLista = TiposGasto.naLista(proprios, todosGastos).let { lista ->
            listOfNotNull(editado).filter { e -> lista.none { TiposGasto.chave(it.nome) == TiposGasto.chave(e.nome) } } + lista
        }
        val fixos = Tipo.entries.filter { it != Tipo.OUTRO }
        val opcoes: List<Any> = fixos + naLista + Tipo.OUTRO
        fun tipoDe(o: Any) = o as? Tipo ?: Tipo.OUTRO
        val inicial: Any = editado?.let { e -> naLista.first { TiposGasto.chave(it.nome) == TiposGasto.chave(e.nome) } }
            ?: daFala?.let { d -> naLista.firstOrNull { it === d } }
            ?: l.tipo?.takeIf { it != Tipo.OUTRO }
            ?: Tipo.OUTRO
        // As views com o tema do diálogo (claro): com o da tela (escuro) o texto saía branco no branco (11/11/2026).
        val tema = ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        val caixa = LinearLayout(tema).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun rotulo(texto: String) = TextView(tema).apply { text = texto; setPadding(0, dp(10), 0, 0) }.also { caixa.addView(it) }
        fun campo(valor: String, tipoTeclado: Int) = EditText(tema).apply {
            setText(valor)
            inputType = tipoTeclado
            if (ehDecimal(tipoTeclado)) aceitarVirgula()
        }.also { caixa.addView(it) }
        val decimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        if (fala != null) caixa.addView(TextView(tema).apply { text = "Você disse: “$fala”"; setTypeface(typeface, android.graphics.Typeface.ITALIC) })
        val ehGasto = RadioButton(tema).apply { text = "Gasto"; id = View.generateViewId() }
        val ehGanho = RadioButton(tema).apply { text = "Ganho"; id = View.generateViewId() }
        caixa.addView(RadioGroup(tema).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(ehGasto)
            addView(ehGanho)
            // Na correção o gasto continua gasto e o ganho continua ganho.
            if (editando != null) visibility = View.GONE
        })
        if (hora != null) {
            val quando = "${String.format(PT, "%02d/%02d", hora.dayOfMonth, hora.monthValue)} às ${hora.toLocalTime().toString().take(5)}"
            caixa.addView(TextView(tema).apply { text = (if (editando is Gasto) "Gasto" else "Ganho") + " de $quando"; textSize = 16f })
        }

        // Ganho: app, valor e se é o total do dia.
        val apps = (LeitorFala.APPS + listOfNotNull(g.app)).distinct()
        val rotuloApp = rotulo("App")
        val app = Spinner(tema).apply {
            adapter = ArrayAdapter(tema, android.R.layout.simple_spinner_dropdown_item, apps)
            // Sem o nome do app é passageiro de rua.
            setSelection(apps.indexOf(g.app ?: "Táxi"))
        }
        caixa.addView(app)
        val rotuloValorGanho = rotulo("Valor (R$)")
        val valorGanho = campo(g.valor?.let { Popup.br(it) } ?: "", decimal)
        val total = CheckBox(tema).apply {
            text = "É o total do dia deste app (troca o anterior)"
            isChecked = g.total
        }
        caixa.addView(total)
        val soGanho = listOf(rotuloApp, app, rotuloValorGanho, valorGanho, total)

        val rotuloTipo = rotulo("Tipo")
        val tipo = Spinner(tema).apply {
            adapter = ArrayAdapter(tema, android.R.layout.simple_spinner_dropdown_item, opcoes.map {
                when (it) {
                    is TipoProprio -> if (it.pessoal) "${it.nome} (pessoal)" else it.nome
                    Tipo.OUTRO -> "Outro (escrever qual)"
                    else -> (it as Tipo).nome
                }
            })
            setSelection(opcoes.indexOf(inicial))
        }
        caixa.addView(tipo)
        // "Outro": o nome do gasto, que vira um tipo dele para as próximas vezes.
        val rotuloNome = rotulo("Qual gasto? (ex.: Supermercado, Dentista)")
        val nomeNovo = AutoCompleteTextView(tema).apply {
            setAdapter(ArrayAdapter(tema, android.R.layout.simple_dropdown_item_1line, proprios.map { it.nome }))
            threshold = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(if (editando == null && daFala == null && l.tipo == null) fala?.let { TiposGasto.sugerirNome(it) } ?: "" else "")
        }
        caixa.addView(nomeNovo)
        val pessoal = CheckBox(tema).apply { text = "É gasto pessoal (casa, família): fica fora do Sobrou do trabalho" }
        caixa.addView(pessoal)
        // Escreveu o nome de um tipo que já existe: marca como ele é. Nome novo de casa (mercado, dentista): já vem pessoal.
        fun marcarPessoal(nome: String) {
            pessoal.isChecked = TiposGasto.achar(proprios, nome)?.pessoal ?: TiposGasto.parecePessoal(nome)
        }
        nomeNovo.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = marcarPessoal(s?.toString() ?: "")
        })
        marcarPessoal(nomeNovo.text.toString())
        val rotuloValor = rotulo("Valor (R$)")
        val valor = campo(l.valor?.let { Popup.br(it) } ?: "", decimal)

        val rotuloLitros = rotulo("Quantidade")
        val litros = campo(l.litros?.let { String.format(PT, "%.2f", it) } ?: "", decimal)
        val rotuloKm = rotulo("Quilometragem do painel (opcional)")
        val km = campo(l.km?.toString() ?: "", InputType.TYPE_CLASS_NUMBER)
        val cheio = CheckBox(tema).apply { text = "Tanque cheio"; isChecked = l.tanqueCheio }
        caixa.addView(cheio)

        val soCombustivel = listOf(rotuloLitros, litros, rotuloKm, km, cheio)
        val soGasto = listOf(rotuloTipo, tipo, rotuloValor, valor)
        fun mostrar() {
            val gasto = ehGasto.isChecked
            soGanho.forEach { it.visibility = if (gasto) View.GONE else View.VISIBLE }
            soGasto.forEach { it.visibility = if (gasto) View.VISIBLE else View.GONE }
            val t = tipoDe(opcoes[tipo.selectedItemPosition])
            val outro = gasto && opcoes[tipo.selectedItemPosition] == Tipo.OUTRO
            listOf(rotuloNome, nomeNovo, pessoal).forEach { it.visibility = if (outro) View.VISIBLE else View.GONE }
            soCombustivel.forEach { it.visibility = if (gasto && t.combustivel) View.VISIBLE else View.GONE }
            rotuloLitros.text = if (t == Tipo.GNV) "Metros cúbicos (m³)" else "Litros"
        }
        tipo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = mostrar()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        val ehGanhoAgora = if (editando != null) editando !is Gasto else fala != null && LeitorFala.ehGanho(fala)
        if (ehGanhoAgora) ehGanho.isChecked = true else ehGasto.isChecked = true
        ehGasto.setOnCheckedChangeListener { _, _ -> mostrar() }
        mostrar()

        val dialogo = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(if (editando != null) "Corrigir lançamento" else "Confere?")
            .setView(ScrollView(tema).apply { addView(caixa) })
            .setPositiveButton("Salvar", null)
            .apply {
                if (editando != null) setNeutralButton("Apagar") { _, _ -> apagar(editando) }
                else setNeutralButton("Falar de novo") { _, _ -> ouvir() }
            }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            fun num(e: EditText): Double? {
                val s = e.text.toString().trim()
                return (if (',' in s) s.replace(".", "").replace(',', '.') else s).toDoubleOrNull()
            }
            if (ehGanho.isChecked) {
                val v = num(valorGanho)
                if (v == null || v <= 0) {
                    valorGanho.error = "Informe o valor"
                    return@setOnClickListener
                }
                val nomeApp = apps[app.selectedItemPosition]
                fun seguir() {
                    dialogo.setOnCancelListener(null)
                    dialogo.dismiss()
                    salvarGanho(nomeApp, v, total.isChecked, hora, editando)
                }
                // O mesmo valor do mesmo app já lançado hoje: quase sempre é o total do dia falado de novo, e somaria duas vezes.
                if (editando == null && !total.isChecked && jaLancadoHoje(nomeApp, v)) {
                    AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                        .setMessage("Você já lançou R$ ${Popup.br(v)} da $nomeApp hoje. Lançar de novo vai somar mais R$ ${Popup.br(v)}.")
                        .setPositiveButton("Lançar de novo") { _, _ -> seguir() }
                        .setNegativeButton("Não lançar", null)
                        .show()
                    return@setOnClickListener
                }
                seguir()
                return@setOnClickListener
            }
            val escolhido = opcoes[tipo.selectedItemPosition]
            val t = tipoDe(escolhido)
            val descricao = when (escolhido) {
                is TipoProprio -> escolhido.nome
                Tipo.OUTRO -> TiposGasto.limpo(nomeNovo.text.toString())
                else -> ""
            }
            val v = num(valor)
            if (v == null || v <= 0) {
                valor.error = "Informe o valor"
                return@setOnClickListener
            }
            val gasto = Gasto(
                quando = hora ?: LocalDateTime.now().withNano(0),
                tipo = t,
                valor = v,
                litros = if (t.combustivel) num(litros)?.takeIf { it > 0 } else null,
                km = if (t.combustivel) km.text.toString().filter { it.isDigit() }.toIntOrNull() else null,
                tanqueCheio = t.combustivel && cheio.isChecked,
                descricao = descricao,
            )
            // Nome novo (ou o de um tipo que estava escondido): entra na lista dele.
            if (escolhido == Tipo.OUTRO && descricao.isNotEmpty()) TiposGasto.registrar(this, descricao, pessoal.isChecked)
            dialogo.setOnCancelListener(null)
            dialogo.dismiss()
            salvar(gasto, editando as? Gasto)
        }
    }

    /** Já tem hoje um ganho (avulso ou total do dia) deste app com este valor. */
    private fun jaLancadoHoje(app: String, valor: Double): Boolean {
        val hoje = java.time.LocalDate.now()
        return Totais.todos(this).any { it.hora.toLocalDate() == hoje && it.app == app && kotlin.math.abs(it.valor - valor) < 0.01 } ||
            Corridas.todas(this).any { it.hora.toLocalDate() == hoje && it.app == app && kotlin.math.abs(it.valor - valor) < 0.01 }
    }

    private fun apagar(item: Any) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setMessage("Apagar este lançamento?")
            .setPositiveButton("Apagar") { _, _ ->
                when (item) {
                    is Gasto -> Gastos.apagar(this, item)
                    is Corrida -> Corridas.apagar(this, item)
                    is TotalDoDia -> Totais.apagar(this, item)
                }
                Notificacao.atualizar(this)
                Toast.makeText(this, "Lançamento apagado.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun salvarGanho(app: String, valor: Double, total: Boolean, hora: LocalDateTime?, antigo: Any?) {
        val agora = hora ?: LocalDateTime.now().withNano(0)
        // Corrigindo: tira o lançamento antigo (ele pode ter virado total do dia, ou o contrário).
        when (antigo) {
            is Corrida -> Corridas.apagar(this, antigo)
            is TotalDoDia -> Totais.apagar(this, antigo)
        }
        if (antigo != null) {
            if (total) Totais.guardar(this, TotalDoDia(agora, valor, app)) else Corridas.guardar(this, Corrida(agora, app, valor))
            Notificacao.atualizar(this)
            Toast.makeText(this, "Lançamento corrigido.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (total) Totais.guardar(this, TotalDoDia(agora, valor, app)) else Corridas.guardar(this, Corrida(agora, app, valor))
        Notificacao.atualizar(this)
        val doApp = Ganhos.porApp(this)[app] ?: valor
        Toast.makeText(this, "Ganho salvo: $app R$ ${Popup.br(valor)}. $app hoje: R$ ${Popup.br(doApp)}", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun salvar(g: Gasto, antigo: Gasto?) {
        if (antigo != null) {
            Gastos.substituir(this, antigo, g)
            Notificacao.atualizar(this)
            Toast.makeText(this, "Lançamento corrigido.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Gastos.adicionar(this, g)
        Notificacao.atualizar(this)
        val todos = if (g.tipo.combustivel) Gastos.todos(this) else emptyList()
        val c = if (g.tipo.combustivel) Gastos.consumo(todos) else null
        // Tanque que rendeu bem menos que a média do mesmo combustível, e a conta do etanol contra a gasolina.
        val queda = c?.let { Abastecimentos.trechos(todos) }?.let { t -> Abastecimentos.queda(t)?.let { it to t.last() } }
        val preco = if (g.tipo.combustivel) Abastecimentos.compararPreco(todos, g) else null
        if (c == null && preco == null) {
            val dica = if (g.tanqueCheio && g.km != null) " No próximo tanque cheio com a quilometragem, o app mostra o consumo." else ""
            Toast.makeText(this, "Gasto salvo: ${g.nome} R$ ${Popup.br(g.valor)}.$dica", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val texto = buildString {
            if (c != null) {
                append("Rodou ${c.kmRodados} km\n")
                append("Colocou ${String.format(PT, "%.1f", c.litros)} ${c.unidade}\n\n")
                append("${Abastecimentos.kmL(c.kmPorLitro)} km/${c.unidade.lowercase()}\n")
                append("R$ ${Popup.br(c.custoKm)} por km")
            }
            queda?.let { (q, t) ->
                append("\n\nAtenção: este tanque rendeu ${Math.round(q * 100)}% menos que a sua média com ${t.combustivel!!.nome.lowercase()}. ")
                append("Confira a calibragem dos pneus, o filtro de ar e o óleo.")
            }
            preco?.let {
                if (isNotEmpty()) append("\n\n")
                append(it)
            }
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(if (c != null) "Consumo desde o último tanque cheio" else "Gasto salvo: ${g.nome} R$ ${Popup.br(g.valor)}")
            .setMessage(texto)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val OUVIR = 1
        private const val EXTRA_GASTO = "gasto"
        private const val EXTRA_CORRIDA = "corrida"
        private const val EXTRA_TOTAL = "total"

        /** Abre o formulário com um lançamento já salvo (Gasto, Corrida ou TotalDoDia), para corrigir ou apagar. */
        fun editar(ctx: android.content.Context, item: Any): Intent {
            val i = Intent(ctx, GastoActivity::class.java)
            return when (item) {
                is Gasto -> i.putExtra(EXTRA_GASTO, Gastos.paraLinha(item))
                is Corrida -> i.putExtra(EXTRA_CORRIDA, Corridas.paraLinha(item))
                is TotalDoDia -> i.putExtra(EXTRA_TOTAL, Totais.paraLinha(item))
                else -> i
            }
        }
        private val PT = Locale("pt", "BR")
    }
}
