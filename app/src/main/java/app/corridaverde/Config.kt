package app.corridaverde

import android.content.Context

data class Config(
    val luxo: Boolean = false,
    val limiteVerde: Int = 100,
    val limiteAmarelo: Int = 85,
    val buscaMaxKm: Double = 3.0,
    val posicaoY: Int = 60,
    val diagnostico: Boolean = false,
) {
    fun salvar(ctx: Context) {
        ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putBoolean("luxo", luxo)
            .putInt("limiteVerde", limiteVerde)
            .putInt("limiteAmarelo", limiteAmarelo)
            .putFloat("buscaMaxKm", buscaMaxKm.toFloat())
            .putInt("posicaoY", posicaoY)
            .putBoolean("diagnostico", diagnostico)
            .apply()
    }

    companion object {
        private const val ARQUIVO = "config"

        fun carregar(ctx: Context): Config {
            val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            val d = Config()
            return Config(
                luxo = p.getBoolean("luxo", d.luxo),
                limiteVerde = p.getInt("limiteVerde", d.limiteVerde),
                limiteAmarelo = p.getInt("limiteAmarelo", d.limiteAmarelo),
                buscaMaxKm = p.getFloat("buscaMaxKm", d.buscaMaxKm.toFloat()).toDouble(),
                posicaoY = p.getInt("posicaoY", d.posicaoY),
                diagnostico = p.getBoolean("diagnostico", d.diagnostico),
            )
        }
    }
}
