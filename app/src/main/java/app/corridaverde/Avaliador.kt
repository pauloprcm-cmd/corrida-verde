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

/** O cálculo do taxímetro, com a tabela da cidade do taxista ([Config.tabela], escolhida em [Cidades]). */
object Tarifa {
    /**
     * Velocidade média com o carro andando. O tempo que a Uber prevê além de km / essa velocidade é trânsito,
     * que o taxímetro cobra pela hora parada (parado ou abaixo de 15 km/h). Era 25 (2 corridas de 25/09/2026);
     * as 4 corridas de 30/10/2026 deram o taxímetro 10% a 22% acima do real com 25, e de -1% a +9% com 18,5.
     * Provisório: recalibrar com mais corridas (km do hodômetro parcial e hora de início e fim).
     */
    const val KMH_ANDANDO = 18.5

    /** Taxa de serviço da Uber no uberTAXI (detalhe da corrida de 30/10/2026: preço R$ 32,08, taxa R$ 4,81). */
    const val TAXA_UBER_TAXI = 0.15

    /**
     * Bandeira 2 pela regra da prefeitura, que vem da base ([Cidades]) e o taxista não muda (pedido do Paulo em
     * 07/11/2026: os horários na tela confundiam): à noite ([TabelaTaxi.b2De] às [TabelaTaxi.b2Ate]), no sábado a
     * partir da hora da cidade, o dia todo em domingos e feriados e na terça de Carnaval onde é feriado. Sem o
     * "dezembro inteiro" que algumas prefeituras liberam: só vale na corrida cobrada fora da plataforma, e a Uber e a 99
     * não pagam bandeira 2, então toda oferta de dezembro ficaria vermelha (Paulo, 07/11/2026).
     * Cidade fora da base (null, digitada pelo taxista): sempre bandeira 1.
     */
    fun bandeira2(agora: LocalDateTime, cidade: Cidade? = Cidades.SAO_PAULO): Boolean {
        cidade ?: return false
        val t = cidade.tabela
        val dia = agora.toLocalDate()
        if (dia.dayOfWeek == DayOfWeek.SUNDAY || Feriados.eh(dia, cidade)) return true
        if (dia.dayOfWeek == DayOfWeek.SATURDAY && t.b2Sabado != TabelaTaxi.SABADO_NORMAL && agora.hour >= t.b2Sabado) return true
        return agora.hour >= t.b2De || agora.hour < t.b2Ate
    }

    /** Minutos da viagem que o taxímetro deve cobrar como hora parada. */
    fun minutosParado(km: Double, min: Int): Double = maxOf(0.0, min - km / KMH_ANDANDO * 60)

    /** O taxímetro usa só a viagem, sem a busca. A bandeira 2 muda o km, não a hora parada. */
    fun taximetro(km: Double, min: Int, t: TabelaTaxi, luxo: Boolean, bandeira2: Boolean): Double {
        val v = t.especial?.takeIf { luxo } ?: t.comum
        return v.bandeirada + km * (if (bandeira2) v.km2 else v.km1) + minutosParado(km, min) * v.hora / 60
    }
}

object Avaliador {
    fun avaliar(o: Oferta, cfg: Config, agora: LocalDateTime = LocalDateTime.now()): Resultado {
        val b2 = Tarifa.bandeira2(agora, cfg.cidadeDaBase)
        val taximetro = Tarifa.taximetro(o.viagemKm, o.viagemMin, cfg.tabela, cfg.luxo, b2)
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
        if (cfg.alertaMercado) Mercados.achar(o.embarque, Mercados.proprios(cfg.mercadosProprios))?.let { add("embarque em mercado: $it") }
    }
}

/** Feriados nacionais e os da cidade e do estado do taxista ([Cidade.feriados]). */
object Feriados {
    /** Nacionais fixos (20/11 é nacional desde a Lei 14.759/2023). */
    private val NACIONAIS = setOf(
        1 to 1, 4 to 21, 5 to 1, 9 to 7,
        10 to 12, 11 to 2, 11 to 15, 11 to 20, 12 to 25,
    )

    fun eh(d: LocalDate, cidade: Cidade? = Cidades.SAO_PAULO): Boolean {
        val md = d.monthValue to d.dayOfMonth
        if (md in NACIONAIS || cidade?.feriados?.contains(md) == true) return true
        val pascoa = pascoa(d.year)
        if (cidade?.carnaval == true && d == pascoa.minusDays(47)) return true // terça de Carnaval
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
