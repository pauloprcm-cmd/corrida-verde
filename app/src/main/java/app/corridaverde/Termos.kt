package app.corridaverde

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.widget.TextView

/**
 * Termos de uso e política de privacidade, numa página só do GitHub Pages (o link vai também no Play Console).
 * O aceite aparece na primeira abertura; quando os termos mudarem, subir [VERSAO] faz ele aparecer de novo.
 */
object Termos {
    const val ENDERECO = "https://pauloprcm-cmd.github.io/corrida-verde/"
    private const val VERSAO = 1
    private const val PREFS = "termos"
    private const val ACEITOU = "versao_aceita"

    fun aceitou(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(ACEITOU, 0) >= VERSAO

    fun aceitar(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(ACEITOU, VERSAO).apply()

    /** [ancora]: "termos" ou "privacidade", a parte da página que abre. */
    fun abrir(ctx: Context, ancora: String) =
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$ENDERECO#$ancora"))) }

    /** "Ao continuar, você concorda com os Termos de uso e a Política de privacidade", com os dois nomes tocáveis. */
    fun linhaDoAceite(v: Visual, ctx: Context): TextView {
        val t = SpannableStringBuilder("Ao continuar, você concorda com os ")
        fun link(nome: String, ancora: String) {
            val inicio = t.length
            t.append(nome)
            t.setSpan(object : ClickableSpan() {
                override fun onClick(w: android.view.View) { abrir(ctx, ancora) }
                override fun updateDrawState(ds: TextPaint) { ds.color = v.verde; ds.isUnderlineText = true }
            }, inicio, t.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        link("Termos de uso", "termos")
        t.append(" e a ")
        link("Política de privacidade", "privacidade")
        t.append(".")
        return v.texto(t, 15f, cor = v.texto2).apply {
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = 0
        }
    }
}
