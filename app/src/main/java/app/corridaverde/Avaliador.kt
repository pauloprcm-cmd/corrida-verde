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
    val pct: Int,
    val cor: Cor,
    val rsKm: Double,
    val rsHora: Double?,
    val bandeira2: Boolean,
    val buscaLonga: Boolean,
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
     * que o taxímetro cobra pela hora parada (parado ou abaixo de 15 km/h). Calibrada nos prints de 25/09/2026:
     * 6,7 km em 20 min deu R$ 40,85 no taxímetro; 7,24 km em 32 min deu R$ 57,65.
     */
    const val KMH_ANDANDO = 25.0

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
        val pct = (o.valor / taximetro * 100).roundToInt()
        var cor = when {
            pct >= cfg.limiteVerde -> Cor.VERDE
            pct >= cfg.limiteAmarelo -> Cor.AMARELO
            else -> Cor.VERMELHO
        }
        val buscaLonga = o.buscaKm > cfg.buscaMaxKm
        if (buscaLonga) cor = if (cor == Cor.VERDE) Cor.AMARELO else Cor.VERMELHO
        val kmTotal = o.buscaKm + o.viagemKm
        val minTotal = o.buscaMin + o.viagemMin
        return Resultado(
            oferta = o,
            taximetro = taximetro,
            minParado = Tarifa.minutosParado(o.viagemKm, o.viagemMin).roundToInt(),
            pct = pct,
            cor = cor,
            rsKm = if (kmTotal > 0) o.valor / kmTotal else 0.0,
            rsHora = if (minTotal > 0) o.valor / minTotal * 60 else null,
            bandeira2 = b2,
            buscaLonga = buscaLonga,
        )
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
