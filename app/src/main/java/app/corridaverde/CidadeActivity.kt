package app.corridaverde

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast

/**
 * A cidade do taxista e a tabela do taxímetro dela. Dois passos: escolher a cidade (busca pelo nome, com a
 * sugestão de onde o celular está) e conferir a tabela com a colada no táxi, que dá para corrigir quando a
 * prefeitura muda os valores. Ajustes abre direto no segundo passo ([EDITAR]).
 */
class CidadeActivity : Activity() {
    private lateinit var v: Visual
    private lateinit var tela: LinearLayout
    private var lista: LinearLayout? = null
    private var sugestao: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = Visual(this)
        tela = v.tela()
        val cfg = Config.carregar(this)
        if (intent.getBooleanExtra(EDITAR, false) && cfg.cidade.isNotEmpty()) {
            mostrarTabela(cfg.cidadeDaBase, cfg.cidade, cfg.tabela, veioDaLista = false)
        } else {
            mostrarLista()
        }
    }

    private fun novaTela(titulo: String, aoVoltar: () -> Unit) {
        tela.removeAllViews()
        lista = null
        sugestao = null
        tela.addView(v.topo(titulo) { aoVoltar() })
        (tela.parent as? ScrollView)?.post { (tela.parent as ScrollView).scrollTo(0, 0) }
    }

    // ---------- Passo 1: a cidade ----------

    private fun mostrarLista() {
        novaTela("Sua cidade") { finish() }
        tela.addView(v.texto("Em que cidade você trabalha com o táxi?", 26f, negrito = true))
        v.por(tela, v.texto("Cada prefeitura tem a sua tabela de táxi. O aviso compara a oferta com o taxímetro da sua cidade.",
            17f, cor = v.texto2), espaco = 8)
        val busca = EditText(this, null, 0, R.style.Campo).apply {
            hint = "Digite o nome da cidade"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = encherLista(s?.toString() ?: "")
            })
        }
        v.por(tela, busca, espaco = 20)
        sugestao = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { v.por(tela, it, espaco = 0) }
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { v.por(tela, it, espaco = 4) }
        encherLista("")
        sugerirPeloLocal()
    }

    private fun encherLista(texto: String) {
        val l = lista ?: return
        l.removeAllViews()
        Cidades.procurar(texto).forEach { c ->
            val t = c.tabela.comum
            v.por(l, v.item(R.drawable.ic_taxi, "${c.nome} · ${c.uf}", "Bandeirada R$ ${Popup.br(t.bandeirada)} · km R$ ${Popup.br(t.km1)}") {
                mostrarTabela(c, c.chave, c.tabela)
            }, espaco = 10)
        }
        v.por(l, v.item(R.drawable.ic_chave, "Minha cidade não está na lista", "Digite a tabela colada no seu táxi") {
            mostrarTabela(null, nomeDigitado(texto), null)
        }, espaco = 18)
    }

    /** O que foi digitado na busca vira o nome da cidade nova ("osasco" fica "Osasco"). */
    private fun nomeDigitado(texto: String) = texto.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .joinToString(" ") { p -> if (p.length <= 2 && p.lowercase() in setOf("de", "do", "da", "dos", "das")) p.lowercase() else p.replaceFirstChar { it.uppercase() } }

    /** Com a localização já liberada, diz em que cidade o celular está (sem pedir permissão nova). */
    private fun sugerirPeloLocal() {
        val permitido = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!permitido) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        val local = runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull() ?: return
        Thread {
            val nomes = runCatching { Enderecos.cidade(this, Ponto(local.latitude, local.longitude)) }.getOrNull().orEmpty()
            val achou = nomes.firstNotNullOfOrNull { Cidades.pelaLocalidade(it) }
            runOnUiThread {
                val s = sugestao ?: return@runOnUiThread
                if (isFinishing) return@runOnUiThread
                if (achou != null) {
                    v.por(s, v.texto("Você está em ${achou.nome}", 15f, negrito = true, cor = v.verde), espaco = 16)
                    v.por(s, v.item(R.drawable.ic_ok, "${achou.nome} · ${achou.uf}", "Toque para usar esta") {
                        mostrarTabela(achou, achou.chave, achou.tabela)
                    }, espaco = 8)
                } else nomes.firstOrNull()?.let { nome ->
                    v.por(s, v.texto("Você está em $nome, que ainda não está na lista. Toque em \"Minha cidade não está na lista\", " +
                        "no fim, e digite a tabela do seu táxi.", 15f, cor = v.texto2), espaco = 16)
                }
            }
        }.start()
    }

    // ---------- Passo 2: a tabela ----------

    /**
     * [cidade] null = cidade fora da base: o taxista digita o nome e a tabela. [atual] é a tabela a mostrar
     * (a guardada, se ele já corrigiu); null começa vazia.
     */
    private fun mostrarTabela(cidade: Cidade?, chave: String, atual: TabelaTaxi?, veioDaLista: Boolean = true) {
        novaTela("Tabela do táxi") { if (veioDaLista) mostrarLista() else finish() }
        var nome: EditText? = null
        var uf: EditText? = null
        if (cidade == null) {
            tela.addView(v.texto("Sua cidade", 24f, negrito = true))
            nome = campo("Nome da cidade", chave.substringBefore('/'), InputType.TYPE_TEXT_FLAG_CAP_WORDS)
            uf = campo("Estado (sigla, ex.: SP)", chave.substringAfter('/', ""), InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
            v.por(tela, v.texto("Digite os valores da tabela colada no vidro ou no painel do seu táxi.", 17f, cor = v.texto2), espaco = 16)
        } else {
            tela.addView(v.texto("${cidade.nome} · ${cidade.uf}", 26f, negrito = true))
            if (cidade.conferir) {
                val aviso = v.cartao(destaque = v.amarelo)
                aviso.addView(v.texto("Confira com a tabela do seu táxi", 18f, negrito = true, cor = v.amarelo))
                aviso.addView(v.texto("Não achamos o decreto mais novo desta cidade (a tabela abaixo é de ${cidade.vigencia}). " +
                    "Confira cada valor com a tabela colada no táxi e corrija o que for diferente.", 16f).apply { setPadding(0, v.dp(6), 0, 0) })
                v.por(tela, aviso, espaco = 14)
            } else {
                v.por(tela, v.texto("Tabela da prefeitura de ${cidade.vigencia}. Confira com a colada no seu táxi: se a prefeitura " +
                    "mudar os valores, é só corrigir aqui.", 17f, cor = v.texto2), espaco = 8)
            }
        }
        val t = atual ?: TabelaTaxi(Valores(0.0, 0.0, 0.0, 0.0))
        val comum = campos(if (t.especial != null) "Táxi comum" else "Valores", t.comum)
        val especial = t.especial?.let { campos("Táxi ${t.nomeEspecial.lowercase()}", it) }

        tela.addView(v.secao("Bandeira 2"))
        tela.addView(v.texto("Domingo e feriado: o dia todo.", 16f, cor = v.texto2))
        val linhaNoite = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val de = campoHora("À noite, a partir das (h)", t.b2De, linhaNoite)
        val ate = campoHora("Até as (h da manhã)", t.b2Ate, linhaNoite)
        v.por(tela, linhaNoite, espaco = 4)
        tela.addView(v.texto("Sábado", 15f, cor = v.texto2).apply { setPadding(v.dp(4), v.dp(16), 0, v.dp(2)) })
        val sabado = RadioGroup(this)
        fun opcao(texto: String) = RadioButton(this, null, 0, R.style.Opcao).apply { id = View.generateViewId(); text = texto }
            .also { sabado.addView(it) }
        val soNoite = opcao("Só à noite, como nos outros dias")
        val diaTodo = opcao("O dia todo")
        val aPartir = opcao("A partir de uma hora da tarde")
        tela.addView(sabado)
        val horaSabado = campo(null, if (t.b2Sabado > 0) t.b2Sabado.toString() else "14", InputType.TYPE_CLASS_NUMBER).apply {
            hint = "Hora (ex.: 14)"
        }
        sabado.setOnCheckedChangeListener { _, id -> horaSabado.visibility = if (id == aPartir.id) View.VISIBLE else View.GONE }
        sabado.check(when (t.b2Sabado) { TabelaTaxi.SABADO_NORMAL -> soNoite.id; 0 -> diaTodo.id; else -> aPartir.id })
        val dezembro = CheckBox(this, null, 0, R.style.Caixa).apply {
            text = "Dezembro inteiro em bandeira 2"
            isChecked = t.b2Dezembro
        }
        v.por(tela, dezembro, espaco = 8)

        v.por(tela, v.botaoPrincipal("Está certo, salvar") {
            val vc = ler(comum) ?: return@botaoPrincipal avisar("Preencha os quatro valores do táxi comum")
            val ve = especial?.let { ler(it) ?: return@botaoPrincipal avisar("Preencha os quatro valores do táxi ${t.nomeEspecial.lowercase()}") }
            if (vc.km2 < vc.km1) return@botaoPrincipal avisar("O km da bandeira 2 não pode ser menor que o da bandeira 1")
            val h1 = de.text.toString().toIntOrNull()?.takeIf { it in 12..23 }
                ?: return@botaoPrincipal avisar("A bandeira 2 da noite começa entre 12 e 23 h")
            val h2 = ate.text.toString().toIntOrNull()?.takeIf { it in 0..11 }
                ?: return@botaoPrincipal avisar("A bandeira 2 da noite acaba entre 0 e 11 h")
            val sab = when (sabado.checkedRadioButtonId) {
                diaTodo.id -> 0
                aPartir.id -> horaSabado.text.toString().toIntOrNull()?.takeIf { it in 1..23 }
                    ?: return@botaoPrincipal avisar("Diga a hora em que a bandeira 2 começa no sábado")
                else -> TabelaTaxi.SABADO_NORMAL
            }
            val novaChave = if (cidade != null) chave else {
                val n = nome!!.text.toString().trim()
                if (n.length < 2) return@botaoPrincipal avisar("Digite o nome da cidade")
                val u = uf!!.text.toString().trim().uppercase()
                if (u.isEmpty()) n else "$n/$u"
            }
            val nova = t.copy(comum = vc, especial = ve, b2De = h1, b2Ate = h2, b2Sabado = sab, b2Dezembro = dezembro.isChecked)
            val cfg = Config.carregar(this)
            cfg.copy(cidade = novaChave, tabela = nova, luxo = cfg.luxo && ve != null).salvar(this)
            Config.acompanharCidade(this, antiga = cfg.nomeCidade.ifBlank { "São Paulo" }, nova = novaChave.substringBefore('/'))
            Toast.makeText(this, "Tabela de ${novaChave.substringBefore('/')} salva", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }, espaco = 28, altura = v.dp(64))

        // Corrigiu a tabela e quer voltar à da prefeitura, ou trocar de cidade (vindo de Ajustes).
        if (cidade != null && atual != cidade.tabela) {
            v.por(tela, v.botao("Voltar à tabela da prefeitura") { mostrarTabela(cidade, chave, cidade.tabela, veioDaLista) }, espaco = 12)
        }
        if (!veioDaLista) v.por(tela, v.botao("Trocar de cidade") { mostrarLista() }, espaco = 12)
    }

    private class CamposValores(val bandeirada: EditText, val km1: EditText, val km2: EditText, val hora: EditText)

    private fun campos(titulo: String, x: Valores): CamposValores {
        tela.addView(v.secao(titulo))
        fun valor(n: Double) = if (n > 0) Popup.br(n) else ""
        val decimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        return CamposValores(
            campo("Bandeirada (R$)", valor(x.bandeirada), decimal),
            campo("Km na bandeira 1 (R$)", valor(x.km1), decimal),
            campo("Km na bandeira 2 (R$)", valor(x.km2), decimal),
            campo("Hora parada (R$)", valor(x.hora), decimal),
        )
    }

    private fun ler(c: CamposValores): Valores? {
        fun n(e: EditText) = e.text.toString().trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 1000 }
        return Valores(n(c.bandeirada) ?: return null, n(c.km1) ?: return null, n(c.km2) ?: return null, n(c.hora) ?: return null)
    }

    private fun campo(rotulo: String?, valor: String, tipo: Int, dentro: LinearLayout = tela): EditText {
        rotulo?.let { dentro.addView(v.texto(it, 15f, cor = v.texto2).apply { setPadding(v.dp(4), v.dp(14), 0, v.dp(6)) }) }
        return EditText(this, null, 0, R.style.Campo).apply {
            setText(valor)
            inputType = if ((tipo and InputType.TYPE_MASK_CLASS) == 0) InputType.TYPE_CLASS_TEXT or tipo else tipo
            if (ehDecimal(tipo)) aceitarVirgula()
        }.also { dentro.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    }

    /** Meia linha com o rótulo e o campo da hora, para "das 20 às 6" ficarem lado a lado. */
    private fun campoHora(rotulo: String, hora: Int, linha: LinearLayout): EditText {
        val coluna = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        linha.addView(coluna, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (linha.childCount > 0) marginStart = v.dp(12)
        })
        return campo(rotulo, hora.toString(), InputType.TYPE_CLASS_NUMBER, coluna)
    }

    private fun avisar(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_LONG).show()

    companion object {
        /** Abre direto na tabela guardada (vindo de Ajustes). */
        const val EDITAR = "editar"

        fun abrir(ctx: Context, editar: Boolean = false) =
            ctx.startActivity(Intent(ctx, CidadeActivity::class.java).putExtra(EDITAR, editar))
    }
}
