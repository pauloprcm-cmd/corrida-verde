package app.corridaverde

import android.content.Context
import java.time.LocalDate

data class Config(
    val luxo: Boolean = false,
    val limiteVerde: Int = 100,
    val limiteAmarelo: Int = 85,
    val buscaMaxKm: Double = 3.0,
    val notaMinima: Double = 4.7,
    val viagemLongaKm: Double = 25.0,
    val posicaoY: Int = 60,
    val diagnostico: Boolean = false,
    val gravar: Boolean = false,
    val radar: Boolean = true,
    val radarSom: Boolean = true,
    /** Casa do motorista, para o modo "Indo pra casa". */
    val casaEndereco: String = "",
    val casa: Ponto? = null,
    val raioCasaKm: Double = 3.0,
    /** Dia em que o modo "Indo pra casa" foi ligado: ele desliga sozinho no dia seguinte. */
    val indoPraCasaDia: String = "",
) {
    val indoPraCasa get() = casa != null && indoPraCasaDia == LocalDate.now().toString()

    fun salvar(ctx: Context) {
        ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putBoolean("luxo", luxo)
            .putInt("limiteVerde", limiteVerde)
            .putInt("limiteAmarelo", limiteAmarelo)
            .putFloat("buscaMaxKm", buscaMaxKm.toFloat())
            .putFloat("notaMinima", notaMinima.toFloat())
            .putFloat("viagemLongaKm", viagemLongaKm.toFloat())
            .putInt("posicaoY", posicaoY)
            .putBoolean("diagnostico", diagnostico)
            .putBoolean("gravar", gravar)
            .putBoolean("radar", radar)
            .putBoolean("radarSom", radarSom)
            .putString("casaEndereco", casaEndereco)
            .putString("casa", casa?.let { "${it.lat};${it.lon}" } ?: "")
            .putFloat("raioCasaKm", raioCasaKm.toFloat())
            .putString("indoPraCasaDia", indoPraCasaDia)
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
                notaMinima = p.getFloat("notaMinima", d.notaMinima.toFloat()).toDouble(),
                viagemLongaKm = p.getFloat("viagemLongaKm", d.viagemLongaKm.toFloat()).toDouble(),
                posicaoY = p.getInt("posicaoY", d.posicaoY),
                diagnostico = p.getBoolean("diagnostico", d.diagnostico),
                gravar = p.getBoolean("gravar", d.gravar),
                radar = p.getBoolean("radar", d.radar),
                radarSom = p.getBoolean("radarSom", d.radarSom),
                casaEndereco = p.getString("casaEndereco", d.casaEndereco) ?: "",
                casa = p.getString("casa", "")?.split(';')?.takeIf { it.size == 2 }?.let { (a, b) ->
                    val lat = a.toDoubleOrNull()
                    val lon = b.toDoubleOrNull()
                    if (lat != null && lon != null) Ponto(lat, lon) else null
                },
                raioCasaKm = p.getFloat("raioCasaKm", d.raioCasaKm.toFloat()).toDouble(),
                indoPraCasaDia = p.getString("indoPraCasaDia", "") ?: "",
            )
        }
    }
}
