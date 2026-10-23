package app.corridaverde

import android.app.Activity
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * Base dos passo a passos que levam a uma tela do Android (ligar a leitura, bateria sem restrição):
 * o botão verde no topo e os passos numerados com o desenho do que vai aparecer. Os desenhos também
 * abrem as configurações, porque no teste de 23/10/2026 o usuário tocou neles achando que eram botões.
 */
abstract class ManualActivity : Activity() {
    protected lateinit var v: Visual
    protected val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    protected abstract fun abrirConfiguracoes()

    protected fun abrir() = v.botaoPrincipal("Abrir as configurações") { abrirConfiguracoes() }

    /** Número verde, o que fazer em letra grande, o desenho e, se tiver, uma dica embaixo. */
    protected fun passo(tela: LinearLayout, n: Int, titulo: String, desenho: View, dica: String?) {
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
        desenho.setOnClickListener { abrirConfiguracoes() }
        c.addView(desenho, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = v.dp(16) })
        dica?.let { c.addView(v.texto(it, 16f, cor = v.texto2).apply { setPadding(0, v.dp(12), 0, 0) }) }
        v.por(tela, c, espaco = 18)
    }

    /** Uma linha como a da lista do Android, com o contorno verde mostrando onde tocar. */
    protected fun linha(): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = v.dp(64)
        setPadding(v.dp(16), v.dp(10), v.dp(16), v.dp(10))
        background = v.forma(v.fundo, v.verde, 14, 3)
    }
}
