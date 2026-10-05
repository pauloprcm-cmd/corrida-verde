package app.corridaverde

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
        // A notificação com o botão "🎤 Falar" precisa desta permissão no Android 13 ou mais novo.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        montar()
        Notificacao.atualizar(this)
        Atalho.publicar(this)
        oferecerAtalho()
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
        c.addView(v.texto(if (cfg.aviso99) "Lendo as ofertas da Uber e da 99." else "Lendo as ofertas da Uber.", 19f).apply { setPadding(0, v.dp(8), 0, 0) })
        val linha = listOfNotNull("Taxímetro: ${if (cfg.luxo) "Luxo" else "Comum"}", "Indo pra casa".takeIf { cfg.indoPraCasa })
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
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
     * Uma vez só, depois que a leitura já está ligada (para não juntar com os pedidos do primeiro uso):
     * oferece o ícone "Falar" na tela inicial. Quem disser não acha o botão em Ajustes.
     */
    private fun oferecerAtalho() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(OFERECEU_ATALHO, false) || LeitorService.instancia == null) return
        if (!Atalho.podeColocar(this) || Atalho.naTelaInicial(this)) return
        prefs.edit().putBoolean(OFERECEU_ATALHO, true).apply()
        AlertDialog.Builder(this)
            .setTitle("Botão de falar na tela inicial")
            .setMessage("Quer um botão “Falar” na tela inicial do celular, perto da Uber e da 99? " +
                "Com um toque você fala um ganho ou gasto, sem abrir o app.\n\n" +
                "Se não quiser mais, é só segurar o dedo nele e tocar em Remover.")
            .setPositiveButton("Colocar") { _, _ -> Atalho.colocar(this) }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private companion object {
        const val PREFS = "inicio"
        const val JA_LIGOU = "ja_ligou"
        const val OFERECEU_ATALHO = "ofereceu_atalho"
    }
}
