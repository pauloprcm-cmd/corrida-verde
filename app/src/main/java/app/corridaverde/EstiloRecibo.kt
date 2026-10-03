package app.corridaverde

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File

enum class Modelo(val nome: String) {
    TABELA("Tabela"), COMPLETO("Completo"), CLASSICO("Clássico"), MODERNO("Moderno"), SIMPLES("Simples"),
}

/** Como cada motorista quer o seu recibo. Os dados dele (nome, placa…) ficam em [Motorista]. */
data class EstiloRecibo(
    val modelo: Modelo = Modelo.TABELA,
    val cor: Int = CORES[0],
    val titulo: String = TITULOS[0],
    /** Frase no pé da folha: "Obrigado pela preferência! Corridas: (11) 99999-9999". */
    val rodape: String = "",
    val pix: String = "",
    /** A logo bem clarinha no meio da folha, por trás do texto. */
    val marcaDagua: Boolean = true,
) {
    fun salvar(ctx: Context) {
        ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putString("modelo", modelo.name).putInt("cor", cor).putString("titulo", titulo)
            .putString("rodape", rodape).putString("pix", pix).putBoolean("marca_dagua", marcaDagua)
            .apply()
    }

    companion object {
        private const val ARQUIVO = "recibo_estilo"
        private const val LOGO = "recibo_logo.png"
        private const val ASSINATURA = "recibo_assinatura.png"

        /** Cores fortes, que saem bem impressas e na tela. */
        val CORES = listOf(
            Color.rgb(0x1B, 0x8A, 0x3C), Color.rgb(0x1F, 0x5F, 0xAF), Color.rgb(0x1A, 0x1A, 0x1A), Color.rgb(0xB3, 0x26, 0x1E),
            Color.rgb(0xD9, 0x73, 0x0D), Color.rgb(0x6A, 0x3F, 0xB5), Color.rgb(0x9A, 0x74, 0x0A), Color.rgb(0x45, 0x5A, 0x64),
            Color.rgb(0x75, 0x75, 0x75),
        )
        val TITULOS = listOf("Recibo", "Recibo de táxi", "Recibo de transporte")

        fun carregar(ctx: Context): EstiloRecibo {
            val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            val d = EstiloRecibo()
            return EstiloRecibo(
                modelo = runCatching { Modelo.valueOf(p.getString("modelo", d.modelo.name)!!) }.getOrDefault(d.modelo),
                cor = p.getInt("cor", d.cor),
                titulo = p.getString("titulo", d.titulo) ?: d.titulo,
                rodape = p.getString("rodape", "") ?: "",
                pix = p.getString("pix", "") ?: "",
                marcaDagua = p.getBoolean("marca_dagua", d.marcaDagua),
            )
        }

        fun arquivoLogo(ctx: Context) = File(ctx.filesDir, LOGO)
        fun arquivoAssinatura(ctx: Context) = File(ctx.filesDir, ASSINATURA)

        fun logo(ctx: Context): Bitmap? = arquivoLogo(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        fun assinatura(ctx: Context): Bitmap? = arquivoAssinatura(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

        fun guardar(f: File, b: Bitmap) = f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
