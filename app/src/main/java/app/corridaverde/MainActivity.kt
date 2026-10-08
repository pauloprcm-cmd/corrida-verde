package app.corridaverde

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tela inicial curta, como no desenho aprovado: o estado da leitura (verde LIGADO ou vermelho
 * DESLIGADO com o botão que resolve), quanto sobrou hoje e quatro botões grandes. Os ajustes e o
 * resto ficam nas telas Meu dia, Ajustes, Ajuda e Verificar problemas.
 */
class MainActivity : Activity() {
    private lateinit var v: Visual
    private var versao: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = Visual(this)
        Abertura.talvezMostrar(this, savedInstanceState)
        // A notificação com o botão "🎤 Falar" precisa desta permissão no Android 13 ou mais novo.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        mostrarTela()
    }

    private fun mostrarTela() {
        if (faltaEscolherPerfil()) return montarEscolha()
        montar()
        Notificacao.atualizar(this)
        tirarIconeFalar()
        Atualizador.verificar(this, perguntar = true) { runOnUiThread { versao?.text = it } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Notificacao.atualizar(this)
    }

    private fun montar() {
        val ligado = LeitorService.instancia != null
        // Guarda que a leitura já funcionou uma vez: assim, se cair, a tela diz "o celular desligou".
        if (ligado) getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(JA_LIGOU, true).apply()
        val jaLigou = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(JA_LIGOU, false)

        val tela = v.tela()
        tela.addView(v.cabecalho())
        v.por(tela, if (ligado) quadroLigado() else quadroDesligado(jaLigou), espaco = 0)
        v.por(tela, quadroSobrou(ligado), espaco = 16)
        // O caminho mais curto para a voz: o maior botão da tela (o da notificação fica escondido para muita gente).
        v.por(tela, v.botaoPrincipal("Falar ganho ou gasto") { abrir(GastoActivity::class.java) }
            .apply { setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_voz, 0, 0, 0) }, espaco = 16, altura = v.dp(76))
        v.por(tela, v.item(R.drawable.ic_carteira, "Meu dia") { abrir(MeuDiaActivity::class.java) }, espaco = 18)
        v.por(tela, v.item(R.drawable.ic_ajustes, "Ajustes") { abrir(AjustesActivity::class.java) })
        v.por(tela, v.item(R.drawable.ic_ajuda, "Ajuda") { abrir(AjudaActivity::class.java) })
        if (ligado) v.por(tela, v.item(R.drawable.ic_chave, "Verificar problemas") { abrir(VerificarActivity::class.java) })

        // O rodapé fica no pé da tela, mesmo com pouca coisa em cima.
        tela.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        tela.addView(v.texto("Só lê a tela. Nunca aceita nem recusa corrida.", 15f, cor = v.texto2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, v.dp(28), 0, v.dp(4))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        versao = v.texto("Versão ${Atualizador.versaoAtual}", 13f, cor = v.texto2).apply { gravity = Gravity.CENTER_HORIZONTAL }
        tela.addView(versao, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun quadroLigado(): View {
        val c = v.cartao(destaque = v.verde)
        c.addView(titulo(R.drawable.ic_ok, "LIGADO", v.verde))
        val cfg = Config.carregar(this)
        val le99 = cfg.aviso99 && Build.VERSION.SDK_INT >= 30
        c.addView(v.texto(if (le99) "Lendo as ofertas da Uber e da 99." else "Lendo as ofertas da Uber.", 19f).apply { setPadding(0, v.dp(8), 0, 0) })
        // Celular antigo: diz por que a 99 não aparece, em vez de deixar o motorista esperando o aviso.
        if (cfg.aviso99 && !le99) c.addView(v.texto(Celular.semAviso99, 16f, cor = v.texto2).apply { setPadding(0, v.dp(6), 0, 0) })
        val linha = listOfNotNull(
            if (cfg.motoristaDeApp) "Motorista de app" else "Taxímetro: ${if (cfg.luxo) "Luxo" else "Comum"}",
            "Indo pra casa".takeIf { cfg.indoPraCasa },
        )
        c.addView(v.texto(linha.joinToString(" · "), 16f, cor = v.texto2).apply { setPadding(0, v.dp(6), 0, 0) })
        return c
    }

    private fun quadroDesligado(jaLigou: Boolean): View {
        val c = v.cartao(destaque = v.vermelho, fundoCor = v.vermelhoFundo)
        c.addView(titulo(R.drawable.ic_erro, "DESLIGADO", v.vermelho))
        val explica = if (jaLigou) "O celular desligou a leitura. Sem ela, o aviso colorido não aparece na Uber nem na 99."
        else "A leitura ainda não está ligada. Sem ela, o aviso colorido não aparece na Uber nem na 99."
        c.addView(v.texto(explica, 19f).apply { setPadding(0, v.dp(10), 0, v.dp(16)) })
        c.addView(v.botaoPrincipal(if (jaLigou) "Ligar de novo" else "Ligar a leitura") {
            AvisoLeitura.ligar(this@MainActivity)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, v.dp(64)))
        c.addView(v.texto(if (jaLigou) "Por que isso acontece?" else "Como ligar, passo a passo", 17f, cor = v.verde).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, v.dp(16), 0, v.dp(4))
            setOnClickListener { AjudaActivity.comoLigar(this@MainActivity, jaLigou) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return c
    }

    /** "Hoje sobrou R$ 187,50" e, embaixo, ganhos em verde e gastos em vermelho. Toque abre o Meu dia. */
    private fun quadroSobrou(detalhe: Boolean): View {
        val b = Dinheiro.hoje(this)
        val c = v.cartao()
        c.background = v.toque(v.forma(v.cartao, v.cartao, 22, 0))
        c.isClickable = true
        c.setOnClickListener { abrir(MeuDiaActivity::class.java) }
        c.addView(v.texto("Hoje sobrou", 17f, cor = v.texto2))
        c.addView(v.texto(Dinheiro.rs(b.sobrou), 40f, negrito = true, cor = if (b.sobrou < 0) v.vermelhoClaro else v.texto))
        if (detalhe) {
            val linha = android.text.SpannableStringBuilder()
            fun parte(rotulo: String, valor: String, cor: Int) {
                linha.append(rotulo)
                val ini = linha.length
                linha.append(valor)
                linha.setSpan(android.text.style.ForegroundColorSpan(cor), ini, linha.length, 0)
                linha.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), ini, linha.length, 0)
            }
            parte("Ganhos ", Dinheiro.rs(b.entrou), v.verde)
            linha.append("     ")
            parte("Gastos ", Dinheiro.rs(b.gastou), v.vermelhoClaro)
            c.addView(v.texto(linha, 17f).apply { setPadding(0, v.dp(6), 0, 0) })
        }
        return c
    }

    private fun titulo(icone: Int, t: String, cor: Int) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@MainActivity).apply { setImageResource(icone) }, LinearLayout.LayoutParams(v.dp(40), v.dp(40)))
        addView(v.texto(t, 32f, negrito = true, cor = cor), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = v.dp(14) })
    }

    private fun abrir(c: Class<*>) = startActivity(Intent(this, c))

    /**
     * Na primeira abertura o app pergunta como a pessoa trabalha. Quem já usava o app antes dos perfis
     * (leitura já ligada ou ajustes salvos) continua taxista, sem pergunta.
     */
    private fun faltaEscolherPerfil(): Boolean {
        val cfg = Config.carregar(this)
        if (cfg.perfil.isNotEmpty()) return false
        val jaUsava = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(JA_LIGOU, false) ||
            getSharedPreferences("config", Context.MODE_PRIVATE).all.isNotEmpty()
        if (!jaUsava) return true
        cfg.copy(perfil = Config.TAXI).salvar(this)
        return false
    }

    /** Tela da primeira abertura: dois cartões grandes, do mesmo jeito dos botões da tela inicial. */
    private fun montarEscolha() {
        val tela = v.tela()
        tela.addView(v.cabecalho())
        v.por(tela, v.texto("Como você trabalha?", 28f, negrito = true), espaco = 24)
        tela.addView(v.texto("Toque na sua opção. Dá para trocar depois em Ajustes.", 17f, cor = v.texto2)
            .apply { setPadding(0, v.dp(8), 0, 0) })
        val escolher = { perfil: String ->
            Config.carregar(this).copy(perfil = perfil).salvar(this)
            mostrarTela()
        }
        v.por(tela, v.item(R.drawable.ic_taxi, "Sou taxista", "Compara a corrida com o taxímetro") { escolher(Config.TAXI) },
            espaco = 24, altura = v.dp(96))
        v.por(tela, v.item(R.drawable.ic_carro, "Sou motorista de aplicativo", "UberX, Comfort, Black, 99Pop…") { escolher(Config.APP) },
            espaco = 14, altura = v.dp(96))
    }

    /**
     * O ícone Falar da tela inicial (versão 1.35) virou a bolinha por cima da Uber e da 99:
     * desliga o que tiver ficado no celular, com um aviso de onde a voz está agora.
     */
    private fun tirarIconeFalar() = runCatching {
        val sm = getSystemService(ShortcutManager::class.java) ?: return@runCatching
        sm.removeAllDynamicShortcuts()
        sm.disableShortcuts(listOf("falar"), "Agora o Falar é a bolinha verde em cima da Uber e da 99. Pode remover este ícone.")
    }

    private companion object {
        const val PREFS = "inicio"
        const val JA_LIGOU = "ja_ligou"
    }
}
