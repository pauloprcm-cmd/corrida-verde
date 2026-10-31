package app.corridaverde

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

enum class Cor { VERDE, AMARELO, VERMELHO }

data class Resultado(
    val oferta: Oferta,
    val taximetro: Double,
    val minParado: Int,
    /** % do taxímetro que a oferta paga; no uberTAXI, o taxímetro limitado ao máximo da faixa, menos a taxa da Uber. */
    val pct: Int,
    val cor: Cor,
    val rsKm: Double,
    val rsHora: Double?,
    val bandeira2: Boolean,
    val buscaLonga: Boolean,
    /** Pegadinhas da oferta, numa linha só do popup. Não mudam a cor (só a busca longa muda). */
    val avisos: List<String>,
)

/** Tabela do táxi de SP (Prefeitura, vigente desde 11/08/2025). */
object Tarifa {
    const val BANDEIRADA_COMUM = 6.55
    const val KM_COMUM = 4.80
    const val BANDEIRADA_LUXO = 9.83
    const val KM_LUXO = 7.20
    const val ACRESCIMO_BANDEIRA2 = 1.30
    const val HORA_COMUM = 55.50
    const val HORA_LUXO = 83.25

    /**
     * Velocidade média com o carro andando. O tempo que a Uber prevê além de km / essa velocidade é trânsito,
     * que o taxímetro cobra pela hora parada (parado ou abaixo de 15 km/h). Era 25 (2 corridas de 25/09/2026);
     * as 4 corridas de 30/10/2026 deram o taxímetro 10% a 22% acima do real com 25, e de -1% a +9% com 18,5.
     * Provisório: recalibrar com mais corridas (km do hodômetro parcial e hora de início e fim).
     */
    const val KMH_ANDANDO = 18.5

    /** Taxa de serviço da Uber no uberTAXI (detalhe da corrida de 30/10/2026: preço R$ 32,08, taxa R$ 4,81). */
    const val TAXA_UBER_TAXI = 0.15

    /** Bandeira 2: das 20h às 6h de segunda a sábado, e o dia todo em domingos e feriados. */
    fun bandeira2(agora: LocalDateTime): Boolean {
        val dia = agora.toLocalDate()
        if (dia.dayOfWeek == DayOfWeek.SUNDAY || Feriados.eh(dia)) return true
        return agora.hour >= 20 || agora.hour < 6
    }

    /** Minutos da viagem que o taxímetro deve cobrar como hora parada. */
    fun minutosParado(km: Double, min: Int): Double = maxOf(0.0, min - km / KMH_ANDANDO * 60)

    /** O taxímetro usa só a viagem, sem a busca. A bandeira 2 aumenta o km, não a hora parada. */
    fun taximetro(km: Double, min: Int, luxo: Boolean, bandeira2: Boolean): Double {
        val porKm = (if (luxo) KM_LUXO else KM_COMUM) * (if (bandeira2) ACRESCIMO_BANDEIRA2 else 1.0)
        val porHora = if (luxo) HORA_LUXO else HORA_COMUM
        return (if (luxo) BANDEIRADA_LUXO else BANDEIRADA_COMUM) + km * porKm + minutosParado(km, min) * porHora / 60
    }
}

object Avaliador {
    fun avaliar(o: Oferta, cfg: Config, agora: LocalDateTime = LocalDateTime.now()): Resultado {
        val b2 = Tarifa.bandeira2(agora)
        val taximetro = Tarifa.taximetro(o.viagemKm, o.viagemMin, cfg.luxo, b2)
        // uberTAXI (faixa "R$ 31 - R$ 46"): o passageiro paga o taxímetro, mas nunca mais que o máximo, e a Uber
        // fica com 15%. Se o taxímetro estimado passa do máximo, a diferença o motorista perde. No Táxi Promo o valor
        // da oferta já é o que cai para o motorista.
        val recebe = o.valorMax?.let { minOf(it, taximetro) * (1 - Tarifa.TAXA_UBER_TAXI) } ?: o.valor
        val pct = (recebe / taximetro * 100).roundToInt()
        val kmTotal = o.buscaKm + o.viagemKm
        val minTotal = o.buscaMin + o.viagemMin
        val rsKm = if (kmTotal > 0) recebe / kmTotal else 0.0
        // Taxista: % do taxímetro. Motorista de app: R$/km (busca + viagem), com o mínimo do grupo da categoria.
        var cor = if (cfg.motoristaDeApp) when {
            rsKm >= cfg.verde(o.grupo) -> Cor.VERDE
            rsKm >= cfg.amarelo(o.grupo) -> Cor.AMARELO
            else -> Cor.VERMELHO
        } else when {
            pct >= cfg.limiteVerde -> Cor.VERDE
            pct >= cfg.limiteAmarelo -> Cor.AMARELO
            else -> Cor.VERMELHO
        }
        val buscaLonga = o.buscaKm > cfg.buscaMaxKm
        if (buscaLonga) cor = if (cor == Cor.VERDE) Cor.AMARELO else Cor.VERMELHO
        return Resultado(
            oferta = o,
            taximetro = taximetro,
            minParado = Tarifa.minutosParado(o.viagemKm, o.viagemMin).roundToInt(),
            pct = pct,
            cor = cor,
            rsKm = rsKm,
            rsHora = if (minTotal > 0) recebe / minTotal * 60 else null,
            bandeira2 = b2,
            buscaLonga = buscaLonga,
            avisos = avisos(o, cfg, buscaLonga),
        )
    }

    private fun avisos(o: Oferta, cfg: Config, buscaLonga: Boolean) = buildList {
        if (o.paradas > 0) add(if (o.paradas == 1) "1 parada" else "${o.paradas} paradas")
        if (buscaLonga) add("busca longa")
        o.nota?.takeIf { it < cfg.notaMinima }?.let { add("nota ${String.format(java.util.Locale("pt", "BR"), "%.2f", it)}") }
        if (o.viagemKm > cfg.viagemLongaKm) add("viagem longa")
    }
}

/** Feriados nacionais, do estado e da cidade de São Paulo. */
object Feriados {
    private val FIXOS = setOf(
        1 to 1, 1 to 25, 4 to 21, 5 to 1, 7 to 9, 9 to 7,
        10 to 12, 11 to 2, 11 to 15, 11 to 20, 12 to 25,
    )

    fun eh(d: LocalDate): Boolean {
        if ((d.monthValue to d.dayOfMonth) in FIXOS) return true
        val pascoa = pascoa(d.year)
        return d == pascoa.minusDays(2) || d == pascoa.plusDays(60) // Sexta-feira Santa e Corpus Christi
    }

    fun pascoa(ano: Int): LocalDate {
        val a = ano % 19
        val b = ano / 100
        val c = ano % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val mes = (h + l - 7 * m + 114) / 31
        val dia = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(ano, mes, dia)
    }
}
