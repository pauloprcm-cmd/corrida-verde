package app.corridaverde

import android.content.Context
import java.time.LocalDate

data class Config(
    /** [TAXI] ou [APP]; vazio enquanto a pessoa não escolheu (primeira abertura). */
    val perfil: String = "",
    val luxo: Boolean = false,
    val limiteVerde: Int = 100,
    val limiteAmarelo: Int = 85,
    val buscaMaxKm: Double = 3.0,
    val notaMinima: Double = 4.7,
    val viagemLongaKm: Double = 25.0,
    val posicaoY: Int = 60,
    val diagnostico: Boolean = false,
    /** Até quando o modo diagnóstico fica ligado (24 h depois de ligar), para ninguém esquecer ele lendo a tela. */
    val diagnosticoAte: Long = 0,
    val gravar: Boolean = false,
    val radar: Boolean = true,
    val radarSom: Boolean = true,
    /** Aviso também nas ofertas da 99 (lidas por imagem). */
    val aviso99: Boolean = true,
    /** Bolinha Falar por cima da Uber e da 99. */
    val bolinha: Boolean = true,
    /**
     * Perfil de motorista de app: R$/km (busca + viagem) a partir do qual a oferta fica verde e amarela, por grupo.
     * Padrões provisórios (pesquisa de 01/10/2026; média do GigU de R$ 1,72/km), a conferir no teste com motoristas.
     */
    val verdeEconomico: Double = 2.0,
    val amareloEconomico: Double = 1.7,
    val verdeConforto: Double = 2.4,
    val amareloConforto: Double = 2.0,
    val verdePremium: Double = 3.2,
    val amareloPremium: Double = 2.7,
    /** Casa do motorista, para o modo "Indo pra casa". */
    val casaEndereco: String = "",
    val casa: Ponto? = null,
    val raioCasaKm: Double = 3.0,
    /** Dia em que o modo "Indo pra casa" foi ligado: ele desliga sozinho no dia seguinte. */
    val indoPraCasaDia: String = "",
) {
    val indoPraCasa get() = casa != null && indoPraCasaDia == LocalDate.now().toString()
    val motoristaDeApp get() = perfil == APP

    fun verde(g: Grupo) = when (g) {
        Grupo.ECONOMICO -> verdeEconomico
        Grupo.CONFORTO -> verdeConforto
        Grupo.PREMIUM -> verdePremium
    }

    fun amarelo(g: Grupo) = when (g) {
        Grupo.ECONOMICO -> amareloEconomico
        Grupo.CONFORTO -> amareloConforto
        Grupo.PREMIUM -> amareloPremium
    }

    fun salvar(ctx: Context) {
        ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putString("perfil", perfil)
            .putBoolean("luxo", luxo)
            .putInt("limiteVerde", limiteVerde)
            .putInt("limiteAmarelo", limiteAmarelo)
            .putFloat("buscaMaxKm", buscaMaxKm.toFloat())
            .putFloat("notaMinima", notaMinima.toFloat())
            .putFloat("viagemLongaKm", viagemLongaKm.toFloat())
            .putInt("posicaoY", posicaoY)
            .putBoolean("diagnostico", diagnostico)
            .putLong("diagnosticoAte", diagnosticoAte)
            .putBoolean("gravar", gravar)
            .putBoolean("radar", radar)
            .putBoolean("radarSom", radarSom)
            .putBoolean("aviso99", aviso99)
            .putBoolean("bolinha", bolinha)
            .putFloat("verdeEconomico", verdeEconomico.toFloat())
            .putFloat("amareloEconomico", amareloEconomico.toFloat())
            .putFloat("verdeConforto", verdeConforto.toFloat())
            .putFloat("amareloConforto", amareloConforto.toFloat())
            .putFloat("verdePremium", verdePremium.toFloat())
            .putFloat("amareloPremium", amareloPremium.toFloat())
            .putString("casaEndereco", casaEndereco)
            .putString("casa", casa?.let { "${it.lat};${it.lon}" } ?: "")
            .putFloat("raioCasaKm", raioCasaKm.toFloat())
            .putString("indoPraCasaDia", indoPraCasaDia)
            .apply()
    }

    companion object {
        private const val ARQUIVO = "config"
        const val TAXI = "taxi"
        const val APP = "app"
        const val DURACAO_DIAGNOSTICO = 24 * 60 * 60_000L

        fun carregar(ctx: Context): Config {
            val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            val d = Config()
            fun real(chave: String, padrao: Double) = p.getFloat(chave, padrao.toFloat()).toDouble()
            return Config(
                perfil = p.getString("perfil", d.perfil) ?: "",
                luxo = p.getBoolean("luxo", d.luxo),
                limiteVerde = p.getInt("limiteVerde", d.limiteVerde),
                limiteAmarelo = p.getInt("limiteAmarelo", d.limiteAmarelo),
                buscaMaxKm = p.getFloat("buscaMaxKm", d.buscaMaxKm.toFloat()).toDouble(),
                notaMinima = p.getFloat("notaMinima", d.notaMinima.toFloat()).toDouble(),
                viagemLongaKm = p.getFloat("viagemLongaKm", d.viagemLongaKm.toFloat()).toDouble(),
                posicaoY = p.getInt("posicaoY", d.posicaoY),
                diagnostico = p.getBoolean("diagnostico", d.diagnostico) && System.currentTimeMillis() < p.getLong("diagnosticoAte", 0),
                diagnosticoAte = p.getLong("diagnosticoAte", 0),
                gravar = BuildConfig.GRAVACAO && p.getBoolean("gravar", d.gravar),
                radar = p.getBoolean("radar", d.radar),
                radarSom = p.getBoolean("radarSom", d.radarSom),
                aviso99 = p.getBoolean("aviso99", d.aviso99),
                bolinha = p.getBoolean("bolinha", d.bolinha),
                verdeEconomico = real("verdeEconomico", d.verdeEconomico),
                amareloEconomico = real("amareloEconomico", d.amareloEconomico),
                verdeConforto = real("verdeConforto", d.verdeConforto),
                amareloConforto = real("amareloConforto", d.amareloConforto),
                verdePremium = real("verdePremium", d.verdePremium),
                amareloPremium = real("amareloPremium", d.amareloPremium),
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
