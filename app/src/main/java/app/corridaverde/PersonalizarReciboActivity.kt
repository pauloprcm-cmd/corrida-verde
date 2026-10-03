package app.corridaverde

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
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
import android.widget.AdapterView
import java.time.LocalDateTime

/**
 * O motorista escolhe o modelo, a cor, o título, a logo, a assinatura, a chave Pix e a frase do
 * rodapé, vendo na hora como o recibo fica. Nada de arrastar campos: o recibo sempre sai legível.
 */
class PersonalizarReciboActivity : Activity() {
    private var estilo = EstiloRecibo()
    private lateinit var previa: ImageView
    private val botoesModelo = mutableMapOf<Modelo, Button>()
    private val bolinhas = mutableMapOf<Int, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        estilo = EstiloRecibo.carregar(this)
        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        tela.addView(texto("Personalizar recibo", 26f, negrito = true))
        tela.addView(texto("Toque nas opções e veja como o recibo fica. Salve no fim.", 15f, cor = CINZA))

        previa = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(0xDD, 0xDD, 0xDD))
            setPadding(dp(1), dp(1), dp(1), dp(1))
        }
        tela.addView(previa, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380)).apply { topMargin = dp(12) })

        tela.addView(rotulo("Modelo"))
        // Os modelos em linhas de três, para os nomes caberem.
        Modelo.values().toList().chunked(3).forEach { linha ->
            val modelos = LinearLayout(this)
            linha.forEach { m ->
                val b = Button(this).apply {
                    text = m.nome
                    textSize = 16f
                    setOnClickListener { estilo = estilo.copy(modelo = m); atualizar() }
                }
                botoesModelo[m] = b
                modelos.addView(b, LinearLayout.LayoutParams(0, dp(60), 1f))
            }
            repeat(3 - linha.size) { modelos.addView(View(this), LinearLayout.LayoutParams(0, dp(60), 1f)) }
            tela.addView(modelos)
        }

        tela.addView(rotulo("Cor"))
        val cores = LinearLayout(this)
        EstiloRecibo.CORES.forEach { cor ->
            val v = View(this).apply { setOnClickListener { estilo = estilo.copy(cor = cor); atualizar() } }
            bolinhas[cor] = v
            cores.addView(v, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) })
        }
        tela.addView(cores)

        tela.addView(rotulo("Título"))
        tela.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@PersonalizarReciboActivity, android.R.layout.simple_spinner_dropdown_item, EstiloRecibo.TITULOS)
            setSelection(maxOf(0, EstiloRecibo.TITULOS.indexOf(estilo.titulo)))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    estilo = estilo.copy(titulo = EstiloRecibo.TITULOS[pos]); atualizar()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })

        tela.addView(rotulo("Logo ou foto (não aparece no Simples)"))
        tela.addView(botao("Escolher imagem") {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PEGAR_LOGO)
        })
        tela.addView(botao("Tirar a logo") { EstiloRecibo.arquivoLogo(this).delete(); atualizar() })
        tela.addView(CheckBox(this).apply {
            text = "Logo clarinha no meio do recibo (marca d'água)"
            textSize = 16f
            isChecked = estilo.marcaDagua
            setOnCheckedChangeListener { _, marcado -> estilo = estilo.copy(marcaDagua = marcado); atualizar() }
        })

        tela.addView(rotulo("Assinatura"))
        tela.addView(texto("Grave uma vez e todo recibo já sai assinado.", 15f, cor = CINZA))
        tela.addView(botao("✍️ Assinar com o dedo (celular deitado)") { assinar(foto = false) })
        tela.addView(botao("📷 Foto da assinatura no papel") { assinar(foto = true) })
        tela.addView(botao("Tirar a assinatura") { EstiloRecibo.arquivoAssinatura(this).delete(); atualizar() })

        tela.addView(rotulo("Chave Pix (opcional)"))
        tela.addView(campo(estilo.pix, InputType.TYPE_CLASS_TEXT) { estilo = estilo.copy(pix = it) })
        tela.addView(rotulo("Frase no rodapé (opcional)"))
        tela.addView(campo(estilo.rodape, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE) {
            estilo = estilo.copy(rodape = it.take(140))
        }.apply { hint = "Ex.: Obrigado pela preferência! Corridas: (11) 99999-9999" })

        tela.addView(botao("Salvar") {
            estilo.salvar(this)
            Toast.makeText(this, "Recibo personalizado salvo", Toast.LENGTH_SHORT).show()
            finish()
        })
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(tela) })
        atualizar()
    }

    /** Redesenha a prévia e marca o modelo e a cor escolhidos. */
    private fun atualizar() {
        val m = Motorista.carregar(this).let { if (it.nome.isBlank()) it.copy(nome = "Seu nome", placa = "ABC1D23") else it }
        val exemplo = Recibo(42, LocalDateTime.now().withSecond(0).withNano(0), 50.0, "Maria Souza", "Avenida Paulista", "Vila Mariana", "Pix")
        previa.setImageBitmap(DesenhoRecibo.imagem(DesenhoRecibo.folha(this, exemplo, m, estilo), larguraPx = 900))
        botoesModelo.forEach { (modelo, b) ->
            b.setTypeface(null, if (modelo == estilo.modelo) Typeface.BOLD else Typeface.NORMAL)
            b.alpha = if (modelo == estilo.modelo) 1f else 0.6f
        }
        bolinhas.forEach { (cor, v) ->
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cor)
                if (cor == estilo.cor) setStroke(dp(4), Color.rgb(0xF5, 0xC2, 0x1B))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ASSINAR) { atualizar(); return }
        val uri = data?.data
        if (requestCode != PEGAR_LOGO || resultCode != RESULT_OK || uri == null) return
        // Reduz a imagem: a logo ocupa um cantinho do recibo, não precisa de foto de 12 MP.
        val b = runCatching {
            val tamanho = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, tamanho) }
            var amostra = 1
            while (maxOf(tamanho.outWidth, tamanho.outHeight) / (amostra * 2) >= 400) amostra *= 2
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra }) }
        }.getOrNull()
        if (b == null) {
            Toast.makeText(this, "Não deu para abrir essa imagem", Toast.LENGTH_LONG).show()
            return
        }
        EstiloRecibo.guardar(EstiloRecibo.arquivoLogo(this), b)
        atualizar()
    }

    private fun assinar(foto: Boolean) {
        startActivityForResult(Intent(this, AssinaturaActivity::class.java).putExtra(AssinaturaActivity.EXTRA_FOTO, foto), ASSINAR)
    }

    private fun campo(valor: String, tipo: Int, mudou: (String) -> Unit) = EditText(this).apply {
        setText(valor)
        textSize = 17f
        inputType = tipo
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { mudou(s?.toString()?.trim() ?: ""); atualizar() }
        })
    }

    private fun botao(t: String, acao: () -> Unit) = Button(this).apply {
        text = t
        textSize = 17f
        setOnClickListener { acao() }
    }

    private fun rotulo(t: String) = texto(t, 16f, negrito = true).apply { setPadding(0, dp(18), 0, dp(4)) }

    private fun texto(t: String, tamanho: Float, negrito: Boolean = false, cor: Int = Color.rgb(0x1A, 0x1A, 0x1A)) = TextView(this).apply {
        text = t
        textSize = tamanho
        setTextColor(cor)
        if (negrito) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val PEGAR_LOGO = 1
        const val ASSINAR = 2
        val CINZA = Color.rgb(0x5F, 0x63, 0x68)
    }
}

/** Quadro branco para assinar com o dedo. A imagem sai recortada e com fundo transparente. */
class QuadroAssinatura(ctx: Context) : View(ctx) {
    private val traco = Path()
    private val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x10, 0x2A, 0x6B)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * ctx.resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var vazio = true

    init {
        setBackgroundColor(Color.WHITE)
    }

    private val guia = Paint().apply { color = Color.rgb(0xC8, 0xC8, 0xC8); strokeWidth = ctx.resources.displayMetrics.density }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        // A linha de "assine aqui"; não entra na imagem salva.
        val y = height * 0.72f
        c.drawLine(width * 0.06f, y, width * 0.94f, y, guia)
        c.drawPath(traco, tinta)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Sem isto, uma rolagem em volta rouba o traço.
        parent?.requestDisallowInterceptTouchEvent(true)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> traco.moveTo(e.x, e.y)
            MotionEvent.ACTION_MOVE -> { traco.lineTo(e.x, e.y); vazio = false }
            else -> return true
        }
        invalidate()
        return true
    }

    fun limpar() {
        traco.reset()
        vazio = true
        invalidate()
    }

    /** Só a parte assinada, com uma folguinha, sem o fundo branco. */
    fun imagem(): Bitmap? {
        if (vazio) return null
        val limites = RectF().also { traco.computeBounds(it, true) }
        val folga = tinta.strokeWidth * 2
        limites.inset(-folga, -folga)
        val b = Bitmap.createBitmap(maxOf(1, limites.width().toInt()), maxOf(1, limites.height().toInt()), Bitmap.Config.ARGB_8888)
        Canvas(b).apply { translate(-limites.left, -limites.top); drawPath(traco, tinta) }
        return b
    }
}
