package app.corridaverde

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
    /** Acabou um passo a passo ([LEITURA] ou [BATERIA]): mostra o "Pronto!" dele. */
    private var pronto: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = Visual(this)
        pronto = intent.getStringExtra(PRONTO)
        // Só a versão da Play tem os 30 dias grátis; a do GitHub não tem limite de uso.
        if (BuildConfig.PERIODO_GRATIS) Config.marcarPrimeiroUso(this)
        Abertura.talvezMostrar(this, savedInstanceState)
        // A notificação com o botão "🎤 Falar" precisa desta permissão no Android 13 ou mais novo.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        mostrarTela()
        when (pronto) {
            LEITURA -> if (LeitorService.instancia != null) prontoLeitura()
            BATERIA -> if (BateriaActivity.livre(this)) mostrarPronto(
                "A bateria está liberada. O celular não vai mais desligar o Corrida Verde no meio do dia.")
        }
        // Girar a tela recria a Activity com o mesmo Intent: o "Pronto!" não pode voltar.
        pronto = null
        intent.removeExtra(PRONTO)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(PRONTO)?.let { pronto = it }
    }

    /** Leitura ligada; se a bateria ainda estiver restrita, já emenda o passo a passo dela. */
    private fun prontoLeitura() {
        val oferta = "Agora é só abrir a Uber ou a 99: quando chegar uma oferta, o aviso colorido aparece por cima."
        if (BateriaActivity.livre(this)) return mostrarPronto("A leitura está ligada. $oferta")
        mostrarPronto("A leitura está ligada.\n\nFalta só mais uma coisa: liberar a bateria, para o celular não desligar o aviso no fim da tarde.",
            botao = "Continuar", depois = { abrir(BateriaActivity::class.java) })
    }

    /** Fecha um passo a passo com a confirmação em letra grande. */
    private fun mostrarPronto(texto: String, botao: String = "Entendi", depois: (() -> Unit)? = null) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(v.dp(24), v.dp(28), v.dp(24), v.dp(8))
        }
        c.addView(ImageView(this).apply { setImageResource(R.drawable.ic_ok) }, LinearLayout.LayoutParams(v.dp(72), v.dp(72)))
        c.addView(v.texto("Pronto!", 32f, negrito = true, cor = v.verde).apply { setPadding(0, v.dp(12), 0, 0) })
        c.addView(v.texto(texto, 19f).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, v.dp(12), 0, 0) })
        val d = AlertDialog.Builder(this).setView(c).setPositiveButton(botao) { _, _ -> depois?.invoke() }
        if (depois != null) d.setNegativeButton("Agora não", null)
        d.show()
    }

    private fun mostrarTela() {
        if (!Termos.aceitou(this)) return montarTermos()
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
        // A economia de bateria desliga a leitura no fim da tarde sem avisar: o alerta fica à vista, não só no Verificar problemas.
        if (ligado && !BateriaActivity.livre(this)) v.por(tela, quadroBateria(), espaco = 16)
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

    private fun quadroBateria(): View {
        val c = v.cartao(destaque = v.amarelo)
        c.addView(v.texto("Falta 1 coisa", 24f, negrito = true, cor = v.amarelo))
        c.addView(v.texto("O celular pode desligar a leitura para poupar bateria. Aí o aviso some no fim da tarde.", 18f)
            .apply { setPadding(0, v.dp(8), 0, v.dp(16)) })
        c.addView(v.botaoPrincipal("Resolver agora") { abrir(BateriaActivity::class.java) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, v.dp(64)))
        return c
    }

    /** "Hoje sobrou R$ 187,50" e, embaixo, ganhos em verde e gastos em vermelho. Toque abre o Meu dia. */
    private fun quadroSobrou(detalhe: Boolean): View {
        val b = Dinheiro.hoje(this)
        val c = v.cartao()
        c.background = v.toque(v.forma(v.cartao, v.cartao, 22, 0))
        c.isClickable = true
        c.setOnClickListener { abrir(MeuDiaActivity::class.java) }
        val esconde = Dinheiro.escondido(this)
        c.addView(v.rotuloComOlho("Hoje sobrou", esconde) { Dinheiro.alternarEscondido(this); mostrarTela() })
        if (esconde) {
            c.addView(v.texto(Dinheiro.OCULTO, 40f, negrito = true))
            return c
        }
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

    /** Boas-vindas com o aceite dos termos: uma linha discreta com os links e o Continuar (desenho aprovado em 20/10/2026). */
    private fun montarTermos() {
        val tela = v.tela()
        tela.addView(v.cabecalho())
        tela.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        tela.addView(v.texto("Saiba na hora se a corrida vale a pena.", 30f, negrito = true))
        v.por(tela, v.texto("O aviso verde, amarelo ou vermelho aparece em cima da oferta da Uber e da 99, " +
            "comparando com o taxímetro ou com o valor por km.", 17f, cor = v.texto2), espaco = 16)
        fun cor(c: Int, t: String) = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(View(this@MainActivity).apply { background = v.forma(c, c, 5, 0) }, LinearLayout.LayoutParams(v.dp(10), v.dp(10)))
            addView(v.texto(t, 17f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = v.dp(12) })
        }
        v.por(tela, cor(v.verde, "Corrida boa fica verde"), espaco = 20)
        v.por(tela, cor(getColor(R.color.amarelo), "Mais ou menos fica amarela"))
        v.por(tela, cor(getColor(R.color.vermelho), "Corrida ruim fica vermelha"))
        tela.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        v.por(tela, Termos.linhaDoAceite(v, this).apply { gravity = Gravity.CENTER_HORIZONTAL }, espaco = 32)
        v.por(tela, v.botaoPrincipal("Continuar") {
            Termos.aceitar(this)
            mostrarTela()
        }, espaco = 16, altura = v.dp(64))
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

    companion object {
        const val PRONTO = "pronto"
        const val LEITURA = "leitura"
        const val BATERIA = "bateria"
        private const val PREFS = "inicio"
        private const val JA_LIGOU = "ja_ligou"
    }
}
