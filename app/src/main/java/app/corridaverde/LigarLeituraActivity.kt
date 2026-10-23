package app.corridaverde

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch

/**
 * Passo a passo antes de abrir a Acessibilidade, para quem tem pouca prática com o celular: o botão
 * no topo e os desenhos do que vai aparecer (no Samsung, Aplicativos instalados antes do nome do app).
 * No teste de 23/10/2026 o usuário tocou nos desenhos achando que eram botões: agora eles também
 * abrem as configurações. Quando a leitura liga, o [LeitorService] traz a tela inicial de volta, com o "Pronto!".
 */
class LigarLeituraActivity : Activity() {
    private lateinit var v: Visual

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = Visual(this)
        val tela = v.tela()
        tela.addView(v.topo("Ligar a leitura"))
        val passos = if (samsung) 4 else 3
        tela.addView(v.texto("Toque no botão verde. No celular, faça os $passos passos abaixo. Depois, o Corrida Verde volta sozinho para cá.", 19f))
        v.por(tela, abrir(), espaco = 20, altura = v.dp(68))

        v.por(tela, v.secao("O que tocar nas configurações"), espaco = 8)
        var n = 0
        if (samsung) passo(tela, ++n, "Toque em Aplicativos instalados", desenhoSamsung(),
            "Fica no fim da lista. Em alguns Samsung o nome é Serviços instalados.")
        passo(tela, ++n, "Toque em Corrida Verde", desenhoNome(),
            if (samsung) null else "Não achou? Procure em Apps instalados, Serviços instalados ou Aplicativos baixados.")
        passo(tela, ++n, "Ligue a chave", desenhoChave(), null)
        passo(tela, ++n, "Toque em Permitir", desenhoPermitir(),
            "O Android avisa que o app pode ver a tela. O Corrida Verde só lê a oferta e o aviso de radar: não toca em nada.")

        v.por(tela, abrir(), espaco = 24, altura = v.dp(68))
        // Só quem instala fora da Play (versão do GitHub) cai na "Configuração restrita" do Android 13+. Sem botão
        // aqui: no teste, o botão de liberar levou para o lugar errado quem não precisava dele.
        if (BuildConfig.ATUALIZADOR && Build.VERSION.SDK_INT >= 33)
            v.por(tela, v.texto("Apareceu “Configuração restrita” e a chave não liga? Veja em Ajuda › Como ligar a leitura.", 16f, cor = v.texto2), espaco = 20)
    }

    private val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private fun abrir() = v.botaoPrincipal("Abrir as configurações") { AvisoLeitura.abrirConfiguracoes(this) }

    override fun onResume() {
        super.onResume()
        // Ligou e voltou por conta própria (sem a volta automática): não há mais o que mostrar aqui.
        if (LeitorService.instancia != null) finish()
    }

    /** Número verde, o que fazer em letra grande, o desenho e, se tiver, uma dica embaixo. */
    private fun passo(tela: LinearLayout, n: Int, titulo: String, desenho: View, dica: String?) {
        val c = v.cartao()
        c.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(v.texto("$n", 22f, negrito = true, cor = v.fundo).apply {
                gravity = Gravity.CENTER
                background = v.forma(v.verde, v.verde, 20, 0)
            }, LinearLayout.LayoutParams(v.dp(40), v.dp(40)))
            addView(v.texto(titulo, 22f, negrito = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = v.dp(14) })
        })
        // Quem toca no desenho achando que é botão também chega nas configurações.
        desenho.setOnClickListener { AvisoLeitura.abrirConfiguracoes(this) }
        c.addView(desenho, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = v.dp(16) })
        dica?.let { c.addView(v.texto(it, 16f, cor = v.texto2).apply { setPadding(0, v.dp(12), 0, 0) }) }
        v.por(tela, c, espaco = 18)
    }

    /** Uma linha como a da lista do Android, com o contorno verde mostrando onde tocar. */
    private fun linha(): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = v.dp(64)
        setPadding(v.dp(16), v.dp(10), v.dp(16), v.dp(10))
        background = v.forma(v.fundo, v.verde, 14, 3)
    }

    private fun desenhoSamsung() = linha().apply {
        addView(v.texto("Aplicativos instalados", 19f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(this@LigarLeituraActivity).apply { setImageResource(R.drawable.ic_seta) }, LinearLayout.LayoutParams(v.dp(24), v.dp(24)))
    }

    private fun desenhoNome() = linha().apply {
        addView(ImageView(this@LigarLeituraActivity).apply { setImageResource(R.mipmap.ic_launcher) }, LinearLayout.LayoutParams(v.dp(40), v.dp(40)))
        addView(v.texto("Corrida Verde", 19f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = v.dp(14) })
    }

    private fun desenhoChave() = linha().apply {
        // Escrito como cada celular mostra a chave.
        addView(v.texto(if (samsung) "Ativado" else "Usar Corrida Verde", 19f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        // Só desenho: a chave aparece ligada e não responde ao toque.
        addView(Switch(this@LigarLeituraActivity).apply {
            isChecked = true
            isClickable = false
            isFocusable = false
            thumbTintList = ColorStateList.valueOf(v.verde)
            trackTintList = ColorStateList.valueOf(v.verde)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
    }

    /** O aviso do Android em miniatura: Negar apagado, Permitir em destaque. */
    private fun desenhoPermitir() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(v.dp(16), v.dp(14), v.dp(16), v.dp(14))
        background = v.forma(v.fundo, v.borda, 14)
        addView(v.texto("Permitir que o Corrida Verde tenha controle total do seu dispositivo?", 16f, cor = v.texto2))
        addView(LinearLayout(this@LigarLeituraActivity).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(0, v.dp(12), 0, 0)
            addView(v.texto("Negar", 18f, cor = v.texto2).apply { setPadding(v.dp(12), v.dp(8), v.dp(20), v.dp(8)) })
            addView(v.texto("Permitir", 18f, negrito = true, cor = v.verde).apply {
                setPadding(v.dp(18), v.dp(8), v.dp(18), v.dp(8))
                background = v.forma(v.fundo, v.verde, 12, 3)
            })
        })
    }
}
