package app.corridaverde

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * A última oferta lida de cada app (uma da Uber e uma da 99, sem lista), para o painel Última oferta: depois de
 * aceitar, o motorista confere no Maps onde ficam o embarque e o destino. Uma oferta da 99 não apaga a da Uber.
 */
data class UltimaOferta(
    /** [UBER] ou [NOVENTA_E_NOVE]. */
    val app: String,
    val valor: Double,
    val embarque: String?,
    val destino: String?,
    /** Quando foi lida (System.currentTimeMillis). */
    val quando: Long,
) {
    companion object {
        const val UBER = "uber"
        const val NOVENTA_E_NOVE = "99"
        private const val ARQUIVO = "ultima_oferta"
        /** Depois disso a oferta é velha demais para o painel aparecer sozinho. */
        const val VALIDADE_MS = 3 * 60 * 60_000L

        /** Guarda por cima da anterior do mesmo app. Sem nenhum endereço lido, não há o que mostrar no mapa. */
        fun guardar(ctx: Context, app: String, o: Oferta) {
            if (o.embarque == null && o.destino == null) return
            ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
                .putFloat("${app}_valor", o.valor.toFloat())
                .putString("${app}_embarque", o.embarque ?: "")
                .putString("${app}_destino", o.destino ?: "")
                .putLong("${app}_quando", System.currentTimeMillis())
                .apply()
        }

        fun carregar(ctx: Context, app: String): UltimaOferta? {
            val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            val quando = p.getLong("${app}_quando", 0)
            if (quando == 0L) return null
            return UltimaOferta(
                app = app,
                valor = p.getFloat("${app}_valor", 0f).toDouble(),
                embarque = p.getString("${app}_embarque", "")?.ifBlank { null },
                destino = p.getString("${app}_destino", "")?.ifBlank { null },
                quando = quando,
            )
        }

        /** A mais nova das duas, em milissegundos; 0 se não há nenhuma. */
        fun maisRecente(ctx: Context): Long =
            listOf(UBER, NOVENTA_E_NOVE).maxOf { carregar(ctx, it)?.quando ?: 0L }

        /**
         * Abre o endereço no Google Maps (ou no navegador, sem o Maps). Do Maps dá para ir ao Street View.
         * O "Brasil" no fim evita que "Rua A, Santana" vá parar em outro país.
         */
        fun abrirNoMapa(ctx: Context, endereco: String) {
            val url = "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(Casa.consulta(endereco))
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(Intent(i).setPackage("com.google.android.apps.maps")) }
                .onFailure { runCatching { ctx.startActivity(i) } }
        }
    }
}
