package app.corridaverde

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * O visual aprovado (fundo escuro, verde da marca, Atkinson Hyperlegible, botões grandes) para as
 * telas montadas em código: início, Meu dia, Ajuda e Verificar problemas. As cores vêm de cores.xml.
 */
class Visual(private val a: Activity) {
    val fundo = cor(R.color.fundo)
    val cartao = cor(R.color.cartao)
    val borda = cor(R.color.borda)
    val verde = cor(R.color.verde)
    val vermelho = cor(R.color.vermelho)
    val vermelhoFundo = cor(R.color.vermelho_fundo)
    val vermelhoClaro = cor(R.color.vermelho_claro)
    val texto = cor(R.color.texto)
    val texto2 = cor(R.color.texto2)

    val letra: Typeface = a.resources.getFont(R.font.atkinson)
    val letraNegrito: Typeface = Typeface.create(letra, Typeface.BOLD)
    val marca: Typeface = a.resources.getFont(R.font.orbitron)

    /** A coluna da tela, dentro de uma rolagem, já colocada na Activity. */
    fun tela(): LinearLayout {
        val coluna = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        a.setContentView(ScrollView(a).apply {
            fitsSystemWindows = true
            isFillViewport = true
            setBackgroundColor(fundo)
            addView(coluna)
        })
        return coluna
    }

    /** Topo com o velocímetro e CORRIDA VERDE, como no desenho aprovado. */
    fun cabecalho(): View = LinearLayout(a).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, dp(20))
        addView(ImageView(a).apply {
            setImageResource(R.drawable.ic_velocimetro)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = forma(cartao, borda, 14)
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        addView(TextView(a).apply {
            text = android.text.SpannableString("CORRIDA VERDE").apply {
                setSpan(android.text.style.ForegroundColorSpan(verde), 8, 13, 0)
            }
            typeface = marca
            setTextColor(texto)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            letterSpacing = 0.04f
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(16) })
    }

    /** Topo das telas internas: seta de voltar e o título. [aoVoltar] serve para telas com passos dentro da mesma Activity. */
    fun topo(titulo: String, aoVoltar: () -> Unit = { a.finish() }): View = LinearLayout(a).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(16))
        addView(ImageView(a).apply {
            setImageResource(R.drawable.ic_voltar)
            contentDescription = "Voltar"
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = toque(forma(cartao, borda, 14))
            setOnClickListener { aoVoltar() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(texto(titulo, 26f, negrito = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
    }

    fun texto(t: CharSequence, tamanho: Float = 17f, negrito: Boolean = false, cor: Int = texto) = TextView(a).apply {
        text = t
        typeface = if (negrito) letraNegrito else letra
        setTextColor(cor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanho)
        setLineSpacing(0f, 1.15f)
    }

    /** Título pequeno de seção, em verde e maiúsculas. */
    fun secao(t: String) = texto(t.uppercase(), 14f, negrito = true, cor = verde).apply {
        letterSpacing = 0.08f
        setPadding(0, dp(24), 0, dp(8))
    }

    /** Botão grande de menu: ícone verde, texto em negrito e a seta, como "Meu dia ›". */
    /** Linha tocável com ícone, título e detalhe; [bolinha] põe um ponto colorido antes da seta (verde = ligado, vermelho = desligado). */
    fun item(icone: Int, titulo: String, detalhe: String? = null, bolinha: Int? = null, acao: () -> Unit): View = LinearLayout(a).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(68)
        setPadding(dp(20), dp(14), dp(16), dp(14))
        background = toque(forma(cartao, borda, 20))
        isClickable = true
        isFocusable = true
        setOnClickListener { acao() }
        addView(ImageView(a).apply { setImageResource(icone) }, LinearLayout.LayoutParams(dp(28), dp(28)))
        val textos = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        textos.addView(texto(titulo, 20f, negrito = true))
        detalhe?.let { textos.addView(texto(it, 15f, cor = texto2)) }
        addView(textos, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(18) })
        bolinha?.let { c ->
            addView(View(a).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) } },
                LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(12) })
        }
        addView(ImageView(a).apply { setImageResource(R.drawable.ic_seta) }, LinearLayout.LayoutParams(dp(24), dp(24)))
    }

    /** Botão verde cheio, para a ação principal da tela. */
    fun botaoPrincipal(t: String, acao: () -> Unit) = Button(a, null, 0, R.style.Botao_Principal).apply {
        text = t
        setOnClickListener { acao() }
    }

    fun botao(t: String, acao: () -> Unit) = Button(a, null, 0, R.style.Botao).apply {
        text = t
        setOnClickListener { acao() }
    }

    /** Cartão com fundo e borda; [destaque] pinta a borda (verde = ligado, vermelho = problema). */
    fun cartao(destaque: Int? = null, fundoCor: Int = cartao): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(20), dp(22), dp(20))
        background = forma(fundoCor, destaque ?: fundoCor, 22, if (destaque != null) 2 else 0)
    }

    /** Põe [v] na [coluna] com um espaço em cima. */
    fun por(coluna: LinearLayout, v: View, espaco: Int = 12, altura: Int = LinearLayout.LayoutParams.WRAP_CONTENT) {
        coluna.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, altura).apply { topMargin = dp(espaco) })
    }

    fun forma(fundoCor: Int, bordaCor: Int, raio: Int, larguraBorda: Int = 1) = GradientDrawable().apply {
        setColor(fundoCor)
        cornerRadius = dp(raio).toFloat()
        if (larguraBorda > 0) setStroke(dp(larguraBorda), bordaCor)
    }

    fun toque(d: GradientDrawable) = RippleDrawable(ColorStateList.valueOf(cor(R.color.toque)), d, null)

    fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()

    private fun cor(id: Int) = a.getColor(id)
}
