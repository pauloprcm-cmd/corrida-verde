package app.corridaverde

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Recibo para o passageiro de rua, de ponto ou particular: o motorista fala ("recibo de 50 reais, da
 * Avenida Paulista até a Vila Mariana, no Pix"), confere o recibo desenhado e envia pelo WhatsApp ou
 * pelo e-mail. Recibo enviado não se edita: a correção sai com outro número e marca o antigo.
 */
class ReciboActivity : Activity() {
    private lateinit var tela: LinearLayout
    private lateinit var rolagem: ScrollView
    private var motorista = Motorista()
    /** Número do recibo que está sendo corrigido, se for correção. */
    private var substitui: Int? = null
    private var lancarGanho = false
    /** O recibo que está na prévia, para redesenhar quando o motorista volta de gravar a assinatura. */
    private var previa: Recibo? = null
    /** Campo que espera o endereço do "📍 Aqui" enquanto o Android pede a permissão de localização. */
    private var campoAqui: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        rolagem = ScrollView(this).apply { fitsSystemWindows = true; addView(tela) }
        setContentView(rolagem)
        motorista = Motorista.carregar(this)
        if (savedInstanceState != null) return
        val inicio = {
            val pronto = intent.getStringExtra(EXTRA_PRONTO)?.let { Recibos.deLinha(it) }
            when {
                pronto != null -> {
                    // Recibo de cliente fixo, montado na ClientesActivity: vai direto para a prévia.
                    substitui = pronto.substitui
                    lancarGanho = intent.getBooleanExtra(EXTRA_LANCAR, false)
                    telaPrevia(pronto)
                }
                intent.getBooleanExtra(EXTRA_LISTA, false) -> telaLista()
                intent.getBooleanExtra(EXTRA_OUVIR, false) -> ouvir()
                else -> formularioDaFala(intent.getStringExtra(EXTRA_FALA))
            }
        }
        if (motorista.completo || intent.getBooleanExtra(EXTRA_LISTA, false)) inicio() else telaDados(inicio)
    }

    private fun ouvir() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ex.: recibo de 50 reais, da Avenida Paulista até a Vila Mariana, no Pix")
        try {
            startActivityForResult(i, OUVIR)
        } catch (e: ActivityNotFoundException) {
            formularioDaFala(null)
        }
    }

    private fun assinar(foto: Boolean) {
        startActivityForResult(Intent(this, AssinaturaActivity::class.java).putExtra(AssinaturaActivity.EXTRA_FOTO, foto), ASSINAR)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Voltou da assinatura: mostra o mesmo recibo de novo, agora assinado.
        if (requestCode == ASSINAR) { previa?.let { telaPrevia(it) }; return }
        if (requestCode != OUVIR) return
        val fala = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (resultCode != RESULT_OK || fala.isNullOrBlank()) {
            if (tela.childCount == 0) finish()
            return
        }
        formularioDaFala(fala)
    }

    /** Monta o rascunho com o que foi falado. Sem valor na fala, usa o último ganho de Táxi de hoje. */
    private fun formularioDaFala(fala: String?) {
        val f = fala?.let { LeitorRecibo.ler(it) }
        val ultimo = Corridas.todas(this).lastOrNull { it.hora.toLocalDate() == LocalDate.now() && it.app in APPS_DE_RUA }
        val valor = f?.valor ?: ultimo?.valor ?: 0.0
        val quando = if (f?.valor == null && ultimo != null) ultimo.hora else LocalDateTime.now().withSecond(0).withNano(0)
        substitui = null
        // Se o motorista já falou esse ganho ("ganhei 50"), o recibo não soma de novo.
        lancarGanho = !jaLancado(valor, quando.toLocalDate())
        telaFormulario(
            Recibo(0, quando, valor, f?.passageiro ?: "", f?.de ?: "", f?.ate ?: "", f?.pagamento ?: ""),
            fala,
        )
    }

    private fun jaLancado(valor: Double, dia: LocalDate) =
        Corridas.todas(this).any { it.hora.toLocalDate() == dia && it.app in APPS_DE_RUA && abs(it.valor - valor) < 0.01 }

    private fun telaFormulario(r: Recibo, fala: String?) {
        limpar()
        titulo(if (substitui != null) "Corrigir recibo" else "Recibo")
        substitui?.let { aviso("Correção do recibo nº ${numero(it)}: sai um recibo novo, com outro número, e o antigo fica marcado como substituído.") }
        fala?.let { aviso("Você disse: “$it”") }

        val valor = campo("Valor (R$)", if (r.valor > 0) Popup.br(r.valor) else "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val data = campo("Data", r.quando.format(DATA), InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE)
        val hora = campo("Hora", r.quando.format(HORA), InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
        val passageiro = campo("Passageiro (opcional)", r.passageiro, InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val de = campo("De (opcional)", r.de, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val ate = campo("Até (opcional)", r.ate, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        tela.addView(Button(this).apply {
            text = "📍 Aqui (onde estou agora)"
            setOnClickListener { aqui(ate) }
        })
        rotulo("Pagamento")
        val pagamentos = listOf("") + LeitorRecibo.PAGAMENTOS
        val pagamento = Spinner(this).apply {
            adapter = ArrayAdapter(this@ReciboActivity, android.R.layout.simple_spinner_dropdown_item, pagamentos.map { it.ifEmpty { "Não informar" } })
            setSelection(maxOf(0, pagamentos.indexOf(r.pagamento)))
        }
        tela.addView(pagamento)
        val telefone = campo("Celular do passageiro (para o WhatsApp)", r.telefone, InputType.TYPE_CLASS_PHONE)
        val email = campo("E-mail do passageiro", r.email, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val ganho = CheckBox(this).apply {
            text = "Lançar também como ganho de Táxi (entra no Sobrou hoje)"
            textSize = 16f
            isChecked = lancarGanho
            visibility = if (substitui == null) View.VISIBLE else View.GONE
        }
        tela.addView(ganho)

        botao("Ver recibo") {
            val v = LeitorGasto.numero(valor.text.toString().trim())
            val quando = runCatching {
                LocalDateTime.of(LocalDate.parse(data.text.toString().trim(), DATA), LocalTime.parse(hora.text.toString().trim(), HORA))
            }.getOrNull()
            when {
                v == null || v <= 0 -> valor.error = "Informe o valor"
                quando == null -> data.error = "Use dia/mês/ano e hora:minuto, como 01/10/2026 e 21:05"
                else -> {
                    lancarGanho = ganho.isChecked && substitui == null
                    telaPrevia(Recibo(
                        numero = 0,
                        quando = quando,
                        valor = v,
                        passageiro = passageiro.text.toString().trim(),
                        de = de.text.toString().trim(),
                        ate = ate.text.toString().trim(),
                        pagamento = pagamentos[pagamento.selectedItemPosition],
                        telefone = telefone.text.toString().trim(),
                        email = email.text.toString().trim(),
                        substitui = substitui,
                    ))
                }
            }
        }
        if (substitui == null) botao("🎤 Falar de novo") { ouvir() }
        botao("Meus dados do recibo") { telaDados { telaFormulario(r, fala) } }
        botao("🎨 Personalizar recibo") { startActivity(Intent(this, PersonalizarReciboActivity::class.java)) }
    }

    /** O recibo desenhado, exatamente como o passageiro vai receber. Os botões de envio só aparecem aqui. */
    private fun telaPrevia(inicial: Recibo) {
        var r = inicial
        limpar()
        titulo("Confira o recibo")
        val imagem = ImageView(this).apply {
            adjustViewBounds = true
            setBackgroundColor(Color.rgb(0xDD, 0xDD, 0xDD))
            setPadding(dp(1), dp(1), dp(1), dp(1))
        }
        val nota = TextView(this).apply { textSize = 14f; setTextColor(CINZA) }
        fun desenhar() {
            previa = r
            // Antes de enviar, a prévia já mostra o número que o recibo vai ter; ele só fica guardado no envio,
            // para não pular números quando o motorista desiste.
            val mostrado = if (r.numero == 0) r.copy(numero = Recibos.proximoNumero(this)) else r
            imagem.setImageBitmap(DesenhoRecibo.imagem(DesenhoRecibo.folha(this, mostrado, motorista)))
            nota.text = if (r.numero == 0) "Este será o recibo nº ${mostrado.numeroTexto}." else "Recibo nº ${r.numeroTexto} enviado."
        }
        desenhar()
        tela.addView(imagem, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        tela.addView(nota)
        if (!EstiloRecibo.arquivoAssinatura(this).exists()) {
            aviso("Você ainda não gravou a sua assinatura. Grave uma vez e todo recibo já sai assinado.")
            botao("✍️ Assinar com o dedo") { assinar(foto = false) }
            botao("📷 Foto da assinatura no papel") { assinar(foto = true) }
        }

        /** Na primeira vez que envia, o recibo ganha número, fica guardado e (se marcado) vira ganho. */
        fun emitido(): Recibo {
            if (r.numero == 0) {
                r = Recibos.emitir(this, r)
                if (lancarGanho) {
                    Corridas.guardar(this, Corrida(r.quando, "Táxi", r.valor))
                    lancarGanho = false
                    Notificacao.atualizar(this)
                }
                desenhar()
            }
            return r
        }

        botao("WhatsApp") { whatsapp(emitido()) }
        botao("E-mail") { email(emitido()) }
        botao("Outro app (PDF)") { startActivity(Intent.createChooser(intentPdf(emitido()), "Enviar recibo")) }
        botao("✏️ Corrigir") {
            if (r.varias) {
                // Recibo de cliente fixo: a correção é na tela das corridas.
                startActivity(ClientesActivity.corrigir(this, r, lancarGanho))
                finish()
                return@botao
            }
            if (r.numero > 0) substitui = r.numero
            telaFormulario(r.copy(numero = 0), null)
        }
        rolagem.post { rolagem.scrollTo(0, 0) }
    }

    private fun telaLista() {
        limpar()
        titulo("Recibos enviados")
        val todos = Recibos.todos(this)
        val corrigidoPor = todos.filter { it.substitui != null }.associate { it.substitui!! to it.numero }
        if (todos.isEmpty()) aviso("Nenhum recibo ainda. Para fazer um, toque em Falar e diga “recibo de 50 reais…”.")
        todos.sortedByDescending { it.numero }.forEach { r ->
            val linha = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, dp(12))
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { substitui = null; telaPrevia(r) }
            }
            linha.addView(texto("Nº ${r.numeroTexto} · ${r.quando.format(DATA)} ${r.quando.format(HORA)} · R$ ${Popup.br(r.valor)}", 17f, negrito = true))
            val detalhe = listOfNotNull(
                r.passageiro.ifBlank { null },
                if (r.varias) "${r.corridas.size} corridas" else r.ate.ifBlank { null }?.let { "até $it" },
            ).joinToString(" · ")
            if (detalhe.isNotBlank()) linha.addView(texto(detalhe, 15f))
            corrigidoPor[r.numero]?.let { linha.addView(texto("Substituído pelo nº ${numero(it)}", 14f, cor = VERMELHO)) }
            tela.addView(linha)
        }
        botao("Fazer recibo novo") { ouvir() }
    }

    /** Nome, placa e os outros dados que vão no pé do recibo. Pedidos uma vez só. */
    private fun telaDados(depois: () -> Unit) {
        limpar()
        titulo("Meus dados do recibo")
        aviso("Preencha uma vez só. Eles aparecem embaixo da assinatura, em todos os recibos.")
        val nome = campo("Seu nome completo", motorista.nome, InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val placa = campo("Placa do táxi", motorista.placa, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val alvara = campo("Alvará ou prefixo (opcional)", motorista.alvara, InputType.TYPE_CLASS_TEXT)
        val documento = campo("CPF ou CNPJ (opcional)", motorista.documento, InputType.TYPE_CLASS_NUMBER)
        val telefone = campo("Seu telefone (opcional)", motorista.telefone, InputType.TYPE_CLASS_PHONE)
        val cidade = campo("Cidade ou região", motorista.cidade, InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val fantasia = campo("Nome do táxi no recibo, ex.: Táxi do João (opcional)", motorista.fantasia, InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val emailM = campo("Seu e-mail (opcional)", motorista.email, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val instagram = campo("Seu Instagram (opcional)", motorista.instagram, InputType.TYPE_CLASS_TEXT)
        botao("Salvar") {
            if (nome.text.isBlank() || placa.text.isBlank()) {
                Toast.makeText(this, "Nome e placa são obrigatórios", Toast.LENGTH_LONG).show()
                return@botao
            }
            motorista = Motorista(
                nome.text.toString().trim(), placa.text.toString().trim().uppercase(), alvara.text.toString().trim(),
                documento.text.toString().trim(), telefone.text.toString().trim(), cidade.text.toString().trim(),
                fantasia.text.toString().trim(), emailM.text.toString().trim(),
                instagram.text.toString().trim().let { if (it.isNotEmpty() && !it.startsWith("@")) "@$it" else it },
            )
            motorista.salvar(this)
            depois()
        }
    }

    /** Com o celular do passageiro, abre a conversa com o recibo em texto (mesmo sem o contato salvo); sem ele, manda o PDF. */
    private fun whatsapp(r: Recibo) {
        val digitos = r.telefone.filter(Char::isDigit).let { if (it.length in 10..11) "55$it" else it }
        if (digitos.length >= 12) {
            val url = "https://wa.me/$digitos?text=" + Uri.encode(Recibos.texto(r, motorista))
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            return
        }
        try {
            startActivity(intentPdf(r).setPackage("com.whatsapp"))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent.createChooser(intentPdf(r), "Enviar recibo"))
        }
    }

    /** Abre o app de e-mail já com destinatário, assunto, texto e o PDF anexado. */
    private fun email(r: Recibo) {
        val i = intentPdf(r).apply {
            if (r.email.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(r.email))
            putExtra(Intent.EXTRA_SUBJECT, "Recibo de táxi nº ${r.numeroTexto} – ${r.quando.format(DATA)}")
            val ola = r.passageiro.split(' ').firstOrNull()?.takeIf { it.isNotBlank() }?.let { "Olá, $it." } ?: "Olá."
            putExtra(Intent.EXTRA_TEXT, "$ola\n\nSegue o recibo ${if (r.varias) "das corridas" else "da corrida"}.\n\n${Recibos.texto(r, motorista)}")
        }
        try {
            startActivity(Intent(i).apply { selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) })
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent.createChooser(i, "Enviar recibo"))
        }
    }

    private fun intentPdf(r: Recibo): Intent {
        val uri = ArquivosProvider.uri(DesenhoRecibo.pdf(this, DesenhoRecibo.folha(this, r, motorista)))
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Preenche o campo com a rua e o bairro de onde o carro está. Usa a localização só neste toque. */
    @SuppressLint("MissingPermission")
    private fun aqui(campo: EditText) {
        val permitido = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!permitido) {
            campoAqui = campo
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), PEDIR_LOCAL)
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        val provedor = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { lm.isProviderEnabled(it) }
        if (provedor == null) {
            Toast.makeText(this, "Ligue a localização do celular", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Procurando onde você está…", Toast.LENGTH_SHORT).show()
        val usar = { local: Location? ->
            if (local == null) {
                Toast.makeText(this, "Não deu para achar onde você está", Toast.LENGTH_LONG).show()
            } else {
                Thread {
                    val endereco = runCatching { Enderecos.endereco(this, Ponto(local.latitude, local.longitude)) }.getOrNull()
                    runOnUiThread {
                        if (endereco != null) campo.setText(endereco)
                        else Toast.makeText(this, "Não achei o nome da rua (sem internet?)", Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
        }
        if (Build.VERSION.SDK_INT >= 30) lm.getCurrentLocation(provedor, null, mainExecutor) { usar(it) }
        else usar(lm.getLastKnownLocation(provedor))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val campo = campoAqui ?: return
        campoAqui = null
        if (requestCode == PEDIR_LOCAL && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) aqui(campo)
    }

    private fun limpar() = tela.removeAllViews()

    private fun titulo(t: String) = tela.addView(texto(t, 26f, negrito = true).apply { setPadding(0, 0, 0, dp(8)) })

    private fun aviso(t: String) = tela.addView(texto(t, 15f, cor = CINZA).apply { setPadding(0, dp(4), 0, dp(4)) })

    private fun rotulo(t: String) = tela.addView(texto(t, 15f).apply { setPadding(0, dp(12), 0, 0) })

    private fun campo(nome: String, valor: String, tipo: Int): EditText {
        rotulo(nome)
        return EditText(this).apply {
            setText(valor)
            textSize = 18f
            inputType = if ((tipo and InputType.TYPE_MASK_CLASS) == 0) InputType.TYPE_CLASS_TEXT or tipo else tipo
            if (ehDecimal(tipo)) aceitarVirgula()
        }.also { tela.addView(it) }
    }

    private fun botao(t: String, acao: () -> Unit) = tela.addView(Button(this).apply {
        text = t
        textSize = 18f
        setOnClickListener { acao() }
    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply { topMargin = dp(8) })

    private fun texto(t: String, tamanho: Float, negrito: Boolean = false, cor: Int = PRETO) = TextView(this).apply {
        text = t
        textSize = tamanho
        setTextColor(cor)
        gravity = Gravity.START
        if (negrito) setTypeface(typeface, Typeface.BOLD)
    }

    private fun numero(n: Int) = String.format(java.util.Locale.ROOT, "%04d", n)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val OUVIR = 1
        private const val ASSINAR = 2
        private const val PEDIR_LOCAL = 2
        private const val EXTRA_FALA = "fala"
        private const val EXTRA_OUVIR = "ouvir"
        private const val EXTRA_LISTA = "lista"
        private const val EXTRA_PRONTO = "pronto"
        private const val EXTRA_LANCAR = "lancar"
        private val APPS_DE_RUA = setOf("Táxi", "Particular")
        private val DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val HORA = DateTimeFormatter.ofPattern("HH:mm")
        private val PRETO = Color.rgb(0x1A, 0x1A, 0x1A)
        private val CINZA = Color.rgb(0x5F, 0x63, 0x68)
        private val VERMELHO = Color.rgb(0xC6, 0x28, 0x28)

        /** Recibo a partir do que o motorista falou no Registrar por voz. */
        fun daFala(ctx: Context, fala: String) = Intent(ctx, ReciboActivity::class.java).putExtra(EXTRA_FALA, fala)

        fun porVoz(ctx: Context) = Intent(ctx, ReciboActivity::class.java).putExtra(EXTRA_OUVIR, true)

        fun lista(ctx: Context) = Intent(ctx, ReciboActivity::class.java).putExtra(EXTRA_LISTA, true)

        /** Prévia e envio de um recibo já montado (cliente fixo); [lancar] põe o total no Meu dinheiro ao enviar. */
        fun pronto(ctx: Context, r: Recibo, lancar: Boolean) = Intent(ctx, ReciboActivity::class.java)
            .putExtra(EXTRA_PRONTO, Recibos.paraLinha(r))
            .putExtra(EXTRA_LANCAR, lancar)
    }
}
