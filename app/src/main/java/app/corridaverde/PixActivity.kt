package app.corridaverde

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Cobrança no Pix para o passageiro que pergunta "aceita Pix?": o motorista fala "gera um Pix de 70
 * reais" e o app mostra o QR Code com o valor, para o passageiro escanear no app do banco. O app não
 * fala com banco nenhum, então não sabe se o Pix caiu: o motorista confere no app do banco dele.
 */
class PixActivity : Activity() {
    private lateinit var tela: LinearLayout
    private var conta = ContaPix()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
            setBackgroundColor(Color.WHITE)
        }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; setBackgroundColor(Color.WHITE); addView(tela) })
        conta = ContaPix.carregar(this)
        val valor = intent.getDoubleExtra(EXTRA_VALOR, 0.0).takeIf { it > 0 }
        val seguir = { if (valor != null) telaQr(valor) else telaValor() }
        if (conta.completa) seguir() else telaConta(seguir)
    }

    /** O QR Code em tela cheia, com a tela no máximo do brilho para o celular do passageiro ler fácil. */
    private fun telaQr(valor: Double) {
        limpar()
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val codigo = BrCode.daConta(conta, valor) ?: return telaConta { telaQr(valor) }

        tela.addView(texto("Pague com Pix", 20f, negrito = true, centro = true))
        tela.addView(texto("R$ ${Popup.br(valor)}", 44f, negrito = true, centro = true, cor = VERDE))
        val lado = minOf(resources.displayMetrics.widthPixels - dp(40), dp(360))
        tela.addView(ImageView(this).apply { setImageBitmap(qr(codigo, lado)) },
            LinearLayout.LayoutParams(lado, lado).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) })
        tela.addView(texto("Abra o app do seu banco, escolha Pix › Ler QR Code e aponte para a tela.", 16f, centro = true, cor = CINZA))
        tela.addView(texto("Para: ${conta.nome}\nChave: ${conta.chave.trim()}", 16f, centro = true).apply { setPadding(0, dp(10), 0, dp(14)) })

        botao("Copiar o código (Pix Copia e Cola)") {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pix", codigo))
            Toast.makeText(this, "Código copiado: cole no WhatsApp do passageiro", Toast.LENGTH_LONG).show()
        }
        botao("Mandar o código pelo WhatsApp ou outro app") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, codigo), "Mandar o Pix"))
        }
        tela.addView(texto("O app não vê a sua conta: confira no app do seu banco se o Pix caiu.", 15f, cor = CINZA).apply { setPadding(0, dp(14), 0, dp(4)) })
        val lancar = botao("✅ Caiu: lançar R$ ${Popup.br(valor)} como ganho de Táxi") {}
        lancar.setOnClickListener {
            if (!jaLancado(valor)) {
                Corridas.guardar(this, Corrida(LocalDateTime.now().withSecond(0).withNano(0), "Táxi", valor))
                Notificacao.atualizar(this)
            }
            lancar.isEnabled = false
            lancar.text = "Ganho lançado"
        }
        botao("Fazer recibo desta corrida") {
            startActivity(ReciboActivity.daFala(this, "recibo de ${Popup.br(valor)} reais no pix"))
            finish()
        }
        botao("Outro valor") { telaValor() }
        botao("Minha chave Pix") { telaConta { telaQr(valor) } }
    }

    private fun telaValor() {
        limpar()
        tela.addView(texto("Cobrar no Pix", 24f, negrito = true))
        val campo = campo("Valor (R$)", "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        botao("Mostrar QR Code") {
            val v = LeitorGasto.numero(campo.text.toString().trim())
            if (v == null || v <= 0) campo.error = "Informe o valor" else telaQr(v)
        }
        botao("Minha chave Pix") { telaConta { telaValor() } }
    }

    /** Cadastro da chave, uma vez só. */
    private fun telaConta(depois: () -> Unit) {
        limpar()
        tela.addView(texto("Minha chave Pix", 24f, negrito = true))
        tela.addView(texto("Preencha uma vez só. O nome aparece para o passageiro no app do banco dele antes de pagar.", 16f, cor = CINZA)
            .apply { setPadding(0, dp(6), 0, dp(6)) })
        rotulo("Tipo de chave")
        val tipos = TipoChave.values()
        val tipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@PixActivity, android.R.layout.simple_spinner_dropdown_item, tipos.map { it.nome })
            setSelection(conta.tipo.ordinal)
        }
        tela.addView(tipo)
        val chave = campo("Chave", conta.chave, InputType.TYPE_CLASS_TEXT)
        val nome = campo("Seu nome (como está no banco)", conta.nome.ifBlank { Motorista.carregar(this).nome }, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val cidade = campo("Cidade", conta.cidade, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        botao("Salvar") {
            val nova = ContaPix(tipos[tipo.selectedItemPosition], chave.text.toString().trim(), nome.text.toString().trim(), cidade.text.toString().trim())
            when {
                nova.chaveFormatada() == null -> chave.error = when (nova.tipo) {
                    TipoChave.CELULAR -> "Use o celular com DDD, ex.: 11 99999-0000"
                    TipoChave.CPF -> "O CPF tem 11 números"
                    TipoChave.CNPJ -> "O CNPJ tem 14 números"
                    TipoChave.EMAIL -> "Confira o e-mail"
                    TipoChave.ALEATORIA -> "Cole a chave aleatória inteira, com os tracinhos"
                }
                nova.nome.isBlank() -> nome.error = "Informe o seu nome"
                else -> {
                    conta = nova
                    conta.salvar(this)
                    depois()
                }
            }
        }
    }

    private fun jaLancado(valor: Double) =
        Corridas.todas(this).any { it.hora.toLocalDate() == LocalDate.now() && it.app == "Táxi" && abs(it.valor - valor) < 0.01 }

    private fun qr(codigo: String, lado: Int): Bitmap {
        val m = QRCodeWriter().encode(codigo, BarcodeFormat.QR_CODE, lado, lado,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }

    private fun limpar() = tela.removeAllViews()

    private fun campo(rotulo: String, valor: String, tipo: Int): EditText {
        rotulo(rotulo)
        return EditText(this).apply {
            setText(valor)
            textSize = 18f
            inputType = tipo
            if (ehDecimal(tipo)) aceitarVirgula()
            setTextColor(PRETO)
        }.also { tela.addView(it) }
    }

    private fun rotulo(t: String) = tela.addView(texto(t, 16f, negrito = true).apply { setPadding(0, dp(14), 0, dp(2)) })

    private fun botao(t: String, acao: () -> Unit) = Button(this).apply {
        text = t
        textSize = 17f
        minHeight = dp(56)
        setOnClickListener { acao() }
    }.also { tela.addView(it) }

    private fun texto(t: String, tamanho: Float, negrito: Boolean = false, centro: Boolean = false, cor: Int = PRETO) = TextView(this).apply {
        text = t
        textSize = tamanho
        setTextColor(cor)
        if (centro) gravity = Gravity.CENTER_HORIZONTAL
        if (negrito) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_VALOR = "valor"
        private val PRETO = Color.rgb(0x1A, 0x1A, 0x1A)
        private val CINZA = Color.rgb(0x5F, 0x63, 0x68)
        private val VERDE = Color.rgb(0x1B, 0x8A, 0x3C)

        /** Da fala "gera um Pix de 70": com valor vai direto ao QR; sem valor, pergunta. */
        fun daFala(ctx: Context, fala: String) = Intent(ctx, PixActivity::class.java).apply {
            LeitorPix.valor(fala)?.let { putExtra(EXTRA_VALOR, it) }
        }

        fun abrir(ctx: Context) = Intent(ctx, PixActivity::class.java)
    }
}
