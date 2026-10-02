package app.corridaverde

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** "Meu dinheiro": quanto entrou, quanto saiu e quanto sobrou no dia, na semana ou no mês. */
class DinheiroActivity : Activity() {
    private var visao = Visao.DIA
    private var dia = LocalDate.now()
    private lateinit var conteudo: LinearLayout
    private val abas = mutableMapOf<Visao, Button>()
    private lateinit var titulo: TextView
    private lateinit var proximo: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        raiz.addView(texto("Meu dinheiro", 28f, negrito = true))

        val linhaAbas = LinearLayout(this).apply { setPadding(0, dp(12), 0, 0) }
        Visao.values().forEach { v ->
            val b = Button(this).apply {
                text = v.nome
                textSize = 18f
                setOnClickListener {
                    visao = v
                    mostrar()
                }
            }
            abas[v] = b
            linhaAbas.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        raiz.addView(linhaAbas)

        val navegar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        navegar.addView(Button(this).apply {
            text = "‹"
            textSize = 24f
            setOnClickListener { dia = visao.andar(dia, -1); mostrar() }
        })
        titulo = texto("", 20f, negrito = true).apply { gravity = Gravity.CENTER }
        navegar.addView(titulo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        proximo = Button(this).apply {
            text = "›"
            textSize = 24f
            setOnClickListener { dia = visao.andar(dia, 1); mostrar() }
        }
        navegar.addView(proximo)
        raiz.addView(navegar)

        conteudo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(conteudo)
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(raiz) })
    }

    // Também ao voltar do formulário de correção.
    override fun onResume() {
        super.onResume()
        mostrar()
    }

    private fun mostrar() {
        val hoje = LocalDate.now()
        if (dia.isAfter(hoje)) dia = hoje
        abas.forEach { (v, b) -> b.setTypeface(null, if (v == visao) Typeface.BOLD else Typeface.NORMAL); b.alpha = if (v == visao) 1f else 0.6f }
        titulo.text = visao.titulo(dia, hoje)
        // Não deixa ir para um período que ainda não começou.
        proximo.isEnabled = !visao.periodo(visao.andar(dia, 1)).first.isAfter(hoje)

        val b = Dinheiro.balanco(this, visao, dia)
        conteudo.removeAllViews()
        conteudo.addView(texto("Sobrou", 16f).apply { setPadding(0, dp(12), 0, 0) })
        conteudo.addView(texto(Dinheiro.rs(b.sobrou), 40f, negrito = true, cor = if (b.sobrou < 0) VERMELHO else VERDE))
        conteudo.addView(texto("Entrou ${Dinheiro.rs(b.entrou)}\nGastou ${Dinheiro.rs(b.gastou)}", 18f))

        val anterior = visao.andar(dia, -1)
        val antes = Dinheiro.balanco(this, visao, anterior)
        if (antes.entrou > 0 || antes.gastou > 0) {
            val dif = b.sobrou - antes.sobrou
            val seta = if (dif >= 0) "▲ +" else "▼ "
            conteudo.addView(texto("${visao.anterior(dia)}: sobrou ${Dinheiro.rs(antes.sobrou)}  $seta${Dinheiro.rs(dif).removePrefix("− ")}", 15f, cor = CINZA))
        }

        if (b.porApp.isNotEmpty()) {
            secao("De onde veio")
            b.porApp.entries.sortedByDescending { it.value }.forEach { (app, v) ->
                linha(app, Dinheiro.rs(v), Dinheiro.pct(v, b.entrou)?.let { "$it%" })
            }
        }

        if (b.porTipo.isNotEmpty()) {
            secao("Para onde foi")
            b.porTipo.entries.sortedByDescending { it.value }.forEach { (tipo, v) -> linha(tipo.nome, Dinheiro.rs(v), null) }
            Dinheiro.pct(b.combustivel, b.entrou)?.takeIf { b.combustivel > 0 }?.let {
                conteudo.addView(texto("O combustível levou $it% do que entrou.", 15f, cor = CINZA).apply { setPadding(0, dp(6), 0, 0) })
            }
        }

        if (visao == Visao.DIA) lancamentos() else barrasPorDia(b, hoje)
        if (b.entrou == 0.0 && b.gastou == 0.0) {
            conteudo.addView(texto("Nada lançado neste período. Ganhos e gastos entram pelo Registrar por voz.", 15f, cor = CINZA).apply {
                setPadding(0, dp(16), 0, 0)
            })
        }
    }

    /** Uma barrinha por dia com o que sobrou: verde para cima, vermelha quando gastou mais do que ganhou. */
    private fun barrasPorDia(b: Balanco, hoje: LocalDate) {
        val dias = b.sobrouPorDia.filterKeys { !it.isAfter(hoje) }
            .let { d -> if (visao == Visao.MES) d.filterValues { it != 0.0 } else d }
        if (dias.values.all { it == 0.0 }) return
        secao("Dia a dia")
        val maior = dias.values.maxOf { kotlin.math.abs(it) }
        dias.forEach { (d, v) ->
            val nome = d.dayOfWeek.getDisplayName(TextStyle.SHORT, PT).removeSuffix(".").replaceFirstChar { it.uppercase() } +
                if (visao == Visao.MES) " ${d.dayOfMonth}" else ""
            val l = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3)) }
            l.addView(texto(nome, 15f), LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT))
            val area = LinearLayout(this)
            val parte = (kotlin.math.abs(v) / maior).toFloat().coerceAtLeast(0.02f)
            area.addView(View(this).apply { setBackgroundColor(if (v < 0) VERMELHO else VERDE) },
                LinearLayout.LayoutParams(0, dp(18), parte))
            area.addView(View(this), LinearLayout.LayoutParams(0, dp(18), 1f - parte + 0.0001f))
            l.addView(area, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            l.addView(texto(Dinheiro.rs(v), 15f).apply { gravity = Gravity.END },
                LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT))
            conteudo.addView(l)
        }
    }

    /** Os lançamentos do dia; tocar num deles abre o formulário para corrigir ou apagar. */
    private fun lancamentos() {
        fun hora(t: LocalDateTime) = t.toLocalTime().toString().take(5)
        val itens = mutableListOf<Triple<LocalDateTime, String, Any>>()
        Totais.todos(this).filter { it.hora.toLocalDate() == dia }.forEach {
            itens += Triple(it.hora, "${it.app} · total do dia", it)
        }
        Corridas.todas(this).filter { it.hora.toLocalDate() == dia }.forEach {
            itens += Triple(it.hora, "${it.app} · corrida", it)
        }
        Gastos.todos(this).filter { it.quando.toLocalDate() == dia }.forEach {
            itens += Triple(it.quando, "Gasto · ${it.tipo.nome}", it)
        }
        if (itens.isEmpty()) return
        secao("Lançamentos")
        conteudo.addView(texto("Toque num lançamento para corrigir ou apagar.", 14f, cor = CINZA))
        itens.sortedBy { it.first }.forEach { (quando, nome, item) ->
            val valor = when (item) {
                is Gasto -> -item.valor
                is Corrida -> item.valor
                is TotalDoDia -> item.valor
                else -> 0.0
            }
            val l = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { startActivity(GastoActivity.editar(this@DinheiroActivity, item)) }
            }
            l.addView(texto(hora(quando), 16f, cor = CINZA), LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT))
            l.addView(texto(nome, 17f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            l.addView(texto(Dinheiro.rs(valor), 17f, negrito = true, cor = if (valor < 0) VERMELHO else VERDE))
            l.addView(texto("  ›", 20f, cor = CINZA))
            conteudo.addView(l)
        }
    }

    private fun secao(nome: String) =
        conteudo.addView(texto(nome, 17f, negrito = true).apply { setPadding(0, dp(20), 0, dp(4)) })

    private fun linha(nome: String, valor: String, extra: String?) {
        val l = LinearLayout(this).apply { setPadding(0, dp(3), 0, dp(3)) }
        l.addView(texto(nome, 17f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (extra != null) l.addView(texto(extra, 15f, cor = CINZA).apply { setPadding(0, 0, dp(12), 0) })
        l.addView(texto(valor, 17f, negrito = true))
        conteudo.addView(l)
    }

    private fun texto(t: String, tamanho: Float, negrito: Boolean = false, cor: Int = Color.rgb(0x1A, 0x1A, 0x1A)) =
        TextView(this).apply {
            text = t
            textSize = tamanho
            setTextColor(cor)
            if (negrito) setTypeface(typeface, Typeface.BOLD)
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        val PT = Locale("pt", "BR")
        val VERDE = Color.rgb(0x1B, 0x8A, 0x3C)
        val VERMELHO = Color.rgb(0xC6, 0x28, 0x28)
        val CINZA = Color.rgb(0x5F, 0x63, 0x68)
    }
}
