package app.corridaverde

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Recibo de cliente fixo: o taxista cadastra o passageiro uma vez, com os trajetos que ele costuma fazer, e
 * depois junta as corridas da semana num recibo só (até [Recibo.MAX_CORRIDAS]), cada uma com a sua data. A
 * data do recibo é a do pagamento. A prévia, a assinatura e o envio são os do recibo por voz ([ReciboActivity]).
 */
class ClientesActivity : Activity() {
    private lateinit var v: Visual
    private lateinit var tela: LinearLayout
    /** O que a seta do topo e o voltar do Android fazem na tela que está aberta. */
    private var voltar: () -> Unit = { finish() }

    // O recibo que está sendo montado.
    private var cliente: Cliente? = null
    private var diaPagamento: LocalDate = LocalDate.now()
    private var pagamento = ""
    private val corridas = mutableListOf<CorridaRecibo>()
    private var substitui: Int? = null
    private var lancarGanho = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = Visual(this)
        tela = v.tela()
        val corrigir = intent.getStringExtra(EXTRA_RECIBO)?.let { Recibos.deLinha(it) }
        if (corrigir != null) carregar(corrigir) else telaLista()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() = voltar()

    // ---------- Lista ----------

    private fun telaLista() {
        novaTela("Clientes fixos") { finish() }
        val todos = Clientes.todos(this)
        val ultimo = Recibos.todos(this).filter { it.varias }.groupBy { it.passageiro }
            .mapValues { (_, rs) -> rs.maxOf { it.quando }.toLocalDate() }
        val busca = campo(null, "", InputType.TYPE_TEXT_FLAG_CAP_WORDS).apply { hint = "🔍  Buscar pelo nome" }
        val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        v.por(tela, lista, espaco = 0)
        fun mostrar() {
            lista.removeAllViews()
            val achados = Clientes.buscar(todos, busca.text.toString())
            if (todos.isEmpty()) v.por(lista, v.texto("Cadastre o passageiro uma vez, com os trajetos que ele costuma fazer. Depois é só buscar pelo nome e marcar as corridas.", 16f, cor = v.texto2), espaco = 16)
            else if (achados.isEmpty()) v.por(lista, v.texto("Ninguém com esse nome.", 16f, cor = v.texto2), espaco = 16)
            achados.forEach { c ->
                val detalhe = listOfNotNull(
                    "${c.trajetos.size} ${if (c.trajetos.size == 1) "trajeto" else "trajetos"}",
                    ultimo[c.nome]?.let { "último recibo ${it.format(DIA_MES)}" },
                ).joinToString(" · ")
                v.por(lista, v.item(R.drawable.ic_pessoa, c.nome, detalhe) { telaCadastro(c) })
            }
        }
        busca.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = mostrar()
        })
        mostrar()
        v.por(tela, v.botao("+ Cadastrar cliente") { telaCadastro(Cliente(System.currentTimeMillis(), "")) }, espaco = 20)
    }

    // ---------- Cadastro ----------

    private fun telaCadastro(c: Cliente) {
        val novo = c.nome.isBlank()
        lateinit var lido: () -> Cliente
        novaTela(if (novo) "Novo cliente" else c.nome) { salvar(lido()); telaLista() }
        if (!novo) {
            v.por(tela, v.botaoPrincipal("Fazer recibo de ${primeiroNome(c.nome)}") {
                val atual = lido()
                if (!salvar(atual)) return@botaoPrincipal
                // Voltou ao cadastro no meio de um recibo: continua o mesmo.
                if (cliente?.id == atual.id && corridas.isNotEmpty()) { cliente = atual; telaRecibo() } else comecarRecibo(atual)
            }, espaco = 0)
        }
        val nome = campo("Nome", c.nome, InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val telefone = campo("Celular (para o WhatsApp)", c.telefone, InputType.TYPE_CLASS_PHONE)
        val email = campo("E-mail (opcional)", c.email, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val documento = campo("CPF ou CNPJ (opcional, sai no recibo)", c.documento, InputType.TYPE_CLASS_NUMBER)
        lido = {
            c.copy(
                nome = nome.text.toString().trim(), telefone = telefone.text.toString().trim(),
                email = email.text.toString().trim(), documento = documento.text.toString().trim(),
            )
        }

        tela.addView(v.secao("Trajetos"))
        if (c.trajetos.isEmpty()) tela.addView(v.texto("Os caminhos que ele costuma fazer, com o preço. A data entra só na hora do recibo.", 15f, cor = v.texto2))
        c.trajetos.forEachIndexed { i, t -> v.por(tela, cartaoTrajeto(t) { telaTrajeto(lido(), i) }, espaco = 8) }
        v.por(tela, v.botao("+ Novo trajeto") { telaTrajeto(lido(), null) })

        v.por(tela, v.botaoPrincipal("Salvar") { if (salvar(lido(), avisar = true)) telaLista() }, espaco = 24)
        if (!novo) {
            v.por(tela, v.botao("Apagar cliente") {
                AlertDialog.Builder(this, R.style.Dialogo)
                    .setMessage("Apagar ${c.nome} e os trajetos? Os recibos já enviados continuam guardados.")
                    .setPositiveButton("Apagar") { _, _ -> Clientes.apagar(this, c.id); telaLista() }
                    .setNegativeButton("Cancelar", null)
                    .show()
            })
        }
    }

    /** Guarda o cliente se ele tem nome; sem nome, só avisa quando [avisar]. */
    private fun salvar(c: Cliente, avisar: Boolean = false): Boolean {
        if (c.nome.isBlank()) {
            if (avisar || c.trajetos.isNotEmpty()) Toast.makeText(this, "Escreva o nome do cliente", Toast.LENGTH_LONG).show()
            return false
        }
        Clientes.salvar(this, c)
        return true
    }

    private fun telaTrajeto(c: Cliente, indice: Int?) {
        val t = indice?.let { c.trajetos[it] } ?: Trajeto(Trajeto.TIPOS[0], "", "", 0.0)
        novaTela(if (indice == null) "Novo trajeto" else "Trajeto") { telaCadastro(c) }
        val campos = camposDeTrajeto(t)
        v.por(tela, v.botaoPrincipal("Salvar trajeto") {
            val novo = campos.ler() ?: return@botaoPrincipal
            val lista = c.trajetos.toMutableList()
            if (indice == null) lista += novo else lista[indice] = novo
            val atualizado = c.copy(trajetos = lista)
            salvar(atualizado)
            telaCadastro(atualizado)
        }, espaco = 24)
        if (indice != null) {
            v.por(tela, v.botao("Apagar trajeto") {
                val atualizado = c.copy(trajetos = c.trajetos.filterIndexed { i, _ -> i != indice })
                salvar(atualizado)
                telaCadastro(atualizado)
            })
        }
    }

    /** Tipo, de, até, valor e observação de um trajeto, com a conferência do que foi digitado. */
    /** [valor] nulo: o valor vem de fora, como no "Outro trajeto" da corrida, que tem o próprio campo de valor. */
    private inner class CamposTrajeto(val tipos: RadioGroup, val de: EditText, val ate: EditText, val valor: EditText?, val obs: EditText) {
        fun ler(precoDeFora: Double? = null): Trajeto? {
            val preco = precoDeFora ?: valor?.let { LeitorGasto.numero(it.text.toString().trim()) }
            return when {
                de.text.isBlank() && ate.text.isBlank() -> { ate.error = "Escreva de onde e até onde"; null }
                preco == null || preco <= 0 -> { valor?.error = "Informe o valor"; null }
                else -> Trajeto(
                    Trajeto.TIPOS[maxOf(0, tipos.indexOfChild(tipos.findViewById(tipos.checkedRadioButtonId)))],
                    de.text.toString().trim(), ate.text.toString().trim(), preco, obs.text.toString().trim(),
                )
            }
        }
    }

    private fun camposDeTrajeto(t: Trajeto, dentro: LinearLayout = tela, comValor: Boolean = true): CamposTrajeto {
        val tipos = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        Trajeto.TIPOS.forEach { nome ->
            tipos.addView(RadioButton(this, null, 0, R.style.Opcao).apply {
                id = View.generateViewId()
                text = nome
                isChecked = nome == t.tipo
            }, RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        if (tipos.checkedRadioButtonId == View.NO_ID) tipos.check(tipos.getChildAt(0).id)
        v.por(dentro, tipos)
        val de = campo("De", t.de, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, dentro)
        val ate = campo("Até", t.ate, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, dentro)
        val valor = if (!comValor) null
            else campo("Valor (R$)", if (t.valor > 0) Popup.br(t.valor) else "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, dentro)
        val obs = campo("Observação (opcional), ex.: Pedágios incluídos", t.obs, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, dentro)
        return CamposTrajeto(tipos, de, ate, valor, obs)
    }

    // ---------- Recibo ----------

    private fun comecarRecibo(c: Cliente) {
        cliente = c
        diaPagamento = LocalDate.now()
        pagamento = ""
        corridas.clear()
        substitui = null
        lancarGanho = true
        telaRecibo()
    }

    /** Correção de um recibo de cliente fixo (ou volta da prévia antes de enviar): monta tudo de novo com ele. */
    private fun carregar(r: Recibo) {
        cliente = Clientes.todos(this).firstOrNull { it.nome == r.passageiro }
            ?: Cliente(System.currentTimeMillis(), r.passageiro, r.telefone, r.email, r.documento)
        diaPagamento = r.quando.toLocalDate()
        pagamento = r.pagamento
        corridas.clear()
        corridas += r.corridas
        substitui = if (r.numero > 0) r.numero else r.substitui
        lancarGanho = substitui == null && intent.getBooleanExtra(EXTRA_LANCAR, true)
        telaRecibo()
    }

    private fun telaRecibo() {
        val c = cliente ?: return telaLista()
        novaTela(if (substitui != null) "Corrigir recibo" else "Recibo · ${primeiroNome(c.nome)}") {
            if (substitui != null) finish() else telaCadastro(Clientes.todos(this).firstOrNull { it.id == c.id } ?: c)
        }
        substitui?.let {
            tela.addView(v.texto("Correção do recibo nº ${numero(it)}: sai um recibo novo, com outro número, e o antigo fica marcado como substituído.", 15f, cor = v.texto2))
        }

        tela.addView(v.secao("Data do pagamento"))
        v.por(tela, seletorDia(diaPagamento) { diaPagamento = it; telaRecibo() }, espaco = 0)
        v.por(tela, v.botao("Pagamento: ${pagamento.ifBlank { "não informar" }}") {
            val opcoes = listOf("") + LeitorRecibo.PAGAMENTOS
            AlertDialog.Builder(this, R.style.Dialogo)
                .setItems(opcoes.map { it.ifBlank { "Não informar" } }.toTypedArray()) { _, i -> pagamento = opcoes[i]; telaRecibo() }
                .show()
        })

        tela.addView(v.secao("Corridas (${corridas.size} de ${Recibo.MAX_CORRIDAS})"))
        if (corridas.isEmpty()) tela.addView(v.texto("Toque em Adicionar corrida e escolha o dia e o trajeto.", 15f, cor = v.texto2))
        corridas.forEachIndexed { i, cr -> v.por(tela, cartaoCorrida(cr) { telaCorrida(i) }, espaco = 8) }
        if (corridas.size < Recibo.MAX_CORRIDAS) v.por(tela, v.botao("+ Adicionar corrida") { telaCorrida(null) })
        else tela.addView(v.texto("Chegou a ${Recibo.MAX_CORRIDAS} corridas, o que cabe na folha. As outras vão em outro recibo.", 15f, cor = v.texto2))

        val total = totalCorridas()
        val linhaTotal = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(v.dp(4), 0, v.dp(4), 0)
            addView(v.texto("Total", 18f, cor = v.texto2), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(v.texto("R$ ${Popup.br(total)}", 28f, negrito = true, cor = v.verde))
        }
        v.por(tela, linhaTotal, espaco = 20)

        if (substitui == null) {
            v.por(tela, CheckBox(this, null, 0, R.style.Caixa).apply {
                text = "Lançar o total como ganho de Táxi em ${diaPagamento.format(DIA_MES)} (entra no Meu dinheiro)"
                isChecked = lancarGanho
                setOnCheckedChangeListener { _, marcado -> lancarGanho = marcado }
            })
        }

        v.por(tela, v.botaoPrincipal("Ver recibo") {
            if (corridas.isEmpty()) {
                Toast.makeText(this, "Adicione pelo menos uma corrida", Toast.LENGTH_LONG).show()
                return@botaoPrincipal
            }
            val r = Recibo(
                numero = 0,
                quando = LocalDateTime.of(diaPagamento, LocalTime.now().withSecond(0).withNano(0)),
                valor = total,
                passageiro = c.nome,
                pagamento = pagamento,
                telefone = c.telefone,
                email = c.email,
                substitui = substitui,
                documento = c.documento,
                corridas = corridas.toList(),
            )
            startActivity(ReciboActivity.pronto(this, r, lancarGanho && substitui == null))
            finish()
        }, espaco = 16)
    }

    private fun totalCorridas() = Math.round(corridas.sumOf { it.trajeto.valor } * 100) / 100.0

    /** O dia seguinte ao da última corrida, sem passar do dia do pagamento; a primeira corrida começa no pagamento. */
    private fun proximoDia(): LocalDate {
        val ultima = corridas.maxOfOrNull { it.dia } ?: return diaPagamento
        return minOf(ultima.plusDays(1), maxOf(diaPagamento, ultima))
    }

    /** Adiciona uma corrida ([indice] nulo) ou muda o dia, o trajeto e o valor de uma que já está no recibo. */
    private fun telaCorrida(indice: Int?) {
        var c = cliente ?: return telaLista()
        val editando = indice?.let { corridas[it] }
        var dia = editando?.dia ?: proximoDia()
        // Trajeto escolhido da lista do cliente; nulo = "Outro trajeto", digitado.
        var escolhido: Trajeto? = editando?.trajeto?.let { e -> c.trajetos.firstOrNull { mesmoTrajeto(it, e) } }
            ?: if (editando == null) c.trajetos.firstOrNull() else null
        novaTela(if (editando == null) "Adicionar corrida" else "Corrida") { telaRecibo() }

        tela.addView(v.secao("Dia da corrida"))
        v.por(tela, seletorDia(dia) { dia = it }, espaco = 0)

        tela.addView(v.secao("Trajeto"))
        val cartoes = mutableListOf<Pair<View, Trajeto>>()
        val outro = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lateinit var valor: EditText
        fun marcar() {
            cartoes.forEach { (cartao, t) -> cartao.background = v.toque(v.forma(v.cartao, if (t == escolhido) v.verde else v.borda, 16, if (t == escolhido) 2 else 1)) }
            outro.visibility = if (escolhido == null) View.VISIBLE else View.GONE
        }
        c.trajetos.forEach { t ->
            val cartao = cartaoTrajeto(t) {
                escolhido = t
                valor.setText(Popup.br(t.valor))
                marcar()
            }
            cartoes += cartao to t
            v.por(tela, cartao, espaco = 8)
        }
        if (c.trajetos.isEmpty()) tela.addView(v.texto("${primeiroNome(c.nome)} ainda não tem trajetos guardados. Digite o trajeto abaixo.", 15f, cor = v.texto2))
        else v.por(tela, v.botao("+ Outro trajeto (digitar)") { escolhido = null; marcar() })

        // Trajeto digitado, com a opção de guardar no cadastro do cliente.
        v.por(tela, outro, espaco = 0)
        val digitado = camposDeTrajeto(editando?.trajeto?.takeIf { escolhido == null } ?: Trajeto(Trajeto.TIPOS[0], "", "", 0.0), outro, comValor = false)
        val guardar = CheckBox(this, null, 0, R.style.Caixa).apply {
            text = "Guardar nos trajetos de ${primeiroNome(c.nome)}"
            isChecked = true
        }
        v.por(outro, guardar)

        valor = campo("Valor desta corrida (R$)", (editando?.trajeto ?: escolhido)?.valor?.let { Popup.br(it) } ?: "",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        tela.addView(v.texto("Mudar o valor aqui vale só para este recibo (ex.: teve espera).", 14f, cor = v.texto2))
        marcar()

        v.por(tela, v.botaoPrincipal(if (editando == null) "Adicionar" else "Salvar corrida") {
            val preco = LeitorGasto.numero(valor.text.toString().trim())
            if (preco == null || preco <= 0) { valor.error = "Informe o valor"; return@botaoPrincipal }
            val t = escolhido?.copy(valor = preco) ?: run {
                val novo = digitado.ler(preco) ?: return@botaoPrincipal
                if (guardar.isChecked) {
                    c = c.copy(trajetos = c.trajetos + novo)
                    cliente = c
                    salvar(c)
                }
                novo
            }
            val corrida = CorridaRecibo(dia, t)
            if (indice == null) corridas += corrida else corridas[indice] = corrida
            corridas.sortBy { it.dia }
            telaRecibo()
        }, espaco = 20)
        if (indice != null) v.por(tela, v.botao("Tirar do recibo") { corridas.removeAt(indice); telaRecibo() })
    }

    /** O mesmo trajeto, mesmo que o valor tenha sido mudado só naquele recibo. */
    private fun mesmoTrajeto(a: Trajeto, b: Trajeto) = a.copy(valor = 0.0) == b.copy(valor = 0.0)

    // ---------- Peças das telas ----------

    private fun novaTela(titulo: String, aoVoltar: () -> Unit) {
        tela.removeAllViews()
        voltar = aoVoltar
        tela.addView(v.topo(titulo) { voltar() })
        (tela.parent as? ScrollView)?.post { (tela.parent as ScrollView).scrollTo(0, 0) }
    }

    private fun campo(rotulo: String?, valor: String, tipo: Int, dentro: LinearLayout = tela): EditText {
        rotulo?.let { dentro.addView(v.texto(it, 15f, cor = v.texto2).apply { setPadding(v.dp(4), v.dp(14), 0, v.dp(6)) }) }
        return EditText(this, null, 0, R.style.Campo).apply {
            setText(valor)
            inputType = if ((tipo and InputType.TYPE_MASK_CLASS) == 0) InputType.TYPE_CLASS_TEXT or tipo else tipo
            if (ehDecimal(tipo)) aceitarVirgula()
        }.also { dentro.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    }

    /** ◀ dia ▶: as setas andam um dia; tocar na data abre o calendário. */
    private fun seletorDia(inicial: LocalDate, mudou: (LocalDate) -> Unit): View {
        var dia = inicial
        val linha = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        lateinit var data: Button
        fun por(d: LocalDate) { dia = d; data.text = d.format(DIA_SEMANA); mudou(d) }
        linha.addView(v.botao("◀") { por(dia.minusDays(1)) }, LinearLayout.LayoutParams(v.dp(64), v.dp(60)))
        data = v.botao(dia.format(DIA_SEMANA)) {
            DatePickerDialog(this, { _, a, m, d -> por(LocalDate.of(a, m + 1, d)) }, dia.year, dia.monthValue - 1, dia.dayOfMonth).show()
        }
        linha.addView(data, LinearLayout.LayoutParams(0, v.dp(60), 1f).apply { marginStart = v.dp(8); marginEnd = v.dp(8) })
        linha.addView(v.botao("▶") { por(dia.plusDays(1)) }, LinearLayout.LayoutParams(v.dp(64), v.dp(60)))
        return linha
    }

    /** Cartão de um trajeto: tipo em verde, de → até, observação e o valor à direita. */
    private fun cartaoTrajeto(t: Trajeto, acao: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(v.dp(16), v.dp(12), v.dp(16), v.dp(12))
        background = v.toque(v.forma(v.cartao, v.borda, 16))
        isClickable = true
        setOnClickListener { acao() }
        val textos = LinearLayout(this@ClientesActivity).apply { orientation = LinearLayout.VERTICAL }
        if (t.tipo.isNotBlank()) textos.addView(v.texto(t.tipo.uppercase(), 13f, negrito = true, cor = v.verde).apply { letterSpacing = 0.06f })
        textos.addView(v.texto(listOf(t.de, t.ate).filter { it.isNotBlank() }.joinToString(" → "), 17f, negrito = true))
        if (t.obs.isNotBlank()) textos.addView(v.texto(t.obs, 14f, cor = v.texto2))
        addView(textos, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(v.texto("R$ ${Popup.br(t.valor)}", 17f, negrito = true).apply { setPadding(v.dp(12), 0, 0, 0) })
    }

    /** Cartão de uma corrida do recibo: o dia em verde, o trajeto e o valor. */
    private fun cartaoCorrida(cr: CorridaRecibo, acao: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(v.dp(16), v.dp(12), v.dp(16), v.dp(12))
        background = v.toque(v.forma(v.cartao, v.borda, 16))
        isClickable = true
        setOnClickListener { acao() }
        addView(v.texto(cr.dia.format(DIA_MES), 17f, negrito = true, cor = v.verde), LinearLayout.LayoutParams(v.dp(62), LinearLayout.LayoutParams.WRAP_CONTENT))
        val textos = LinearLayout(this@ClientesActivity).apply { orientation = LinearLayout.VERTICAL }
        textos.addView(v.texto(listOf(cr.trajeto.de, cr.trajeto.ate).filter { it.isNotBlank() }.joinToString(" → "), 16f, negrito = true))
        textos.addView(v.texto(listOf(cr.trajeto.tipo, cr.trajeto.obs).filter { it.isNotBlank() }.joinToString(" · "), 14f, cor = v.texto2))
        addView(textos, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(v.texto(Popup.br(cr.trajeto.valor), 17f, negrito = true).apply { setPadding(v.dp(10), 0, 0, 0) })
    }

    private fun primeiroNome(nome: String) = nome.trim().split(' ').firstOrNull().orEmpty().ifBlank { nome }

    private fun numero(n: Int) = String.format(java.util.Locale.ROOT, "%04d", n)

    companion object {
        private const val EXTRA_RECIBO = "recibo"
        private const val EXTRA_LANCAR = "lancar"
        private val DIA_MES = DateTimeFormatter.ofPattern("dd/MM")
        private val DIA_SEMANA = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", java.util.Locale("pt", "BR"))

        /** Abre o recibo de cliente fixo [r] para corrigir: o que já foi enviado sai com outro número. */
        fun corrigir(ctx: Context, r: Recibo, lancar: Boolean) = Intent(ctx, ClientesActivity::class.java)
            .putExtra(EXTRA_RECIBO, Recibos.paraLinha(r))
            .putExtra(EXTRA_LANCAR, lancar)
    }
}
