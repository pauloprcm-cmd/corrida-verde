package app.corridaverde

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** O que entrou e o que saiu num período (dia, semana ou mês). */
data class Balanco(
    val de: LocalDate,
    val ate: LocalDate,
    val porApp: Map<String, Double>,
    val porTipo: Map<Tipo, Double>,
    /** O que sobrou em cada dia do período, para as barrinhas da semana e do mês. */
    val sobrouPorDia: Map<LocalDate, Double>,
) {
    val entrou get() = porApp.values.sum()
    val gastou get() = porTipo.values.sum()
    val sobrou get() = entrou - gastou
    val combustivel get() = porTipo.filterKeys { it.combustivel }.values.sum()
}

enum class Visao(val nome: String) {
    DIA("Dia"), SEMANA("Semana"), MES("Mês");

    /** Primeiro e último dia do período que contém [dia]. A semana vai de segunda a domingo. */
    fun periodo(dia: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        DIA -> dia to dia
        SEMANA -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        MES -> dia.withDayOfMonth(1) to dia.with(TemporalAdjusters.lastDayOfMonth())
    }

    /** Um dia do período anterior (ou do seguinte, com [passos] positivo). */
    fun andar(dia: LocalDate, passos: Long): LocalDate = when (this) {
        DIA -> dia.plusDays(passos)
        SEMANA -> dia.plusWeeks(passos)
        MES -> dia.plusMonths(passos)
    }

    fun titulo(dia: LocalDate, hoje: LocalDate = LocalDate.now()): String {
        val (de, ate) = periodo(dia)
        return when (this) {
            DIA -> when (dia) {
                hoje -> "Hoje"
                hoje.minusDays(1) -> "Ontem"
                else -> "${diaDaSemana(dia)}, ${dataCurta(dia)}"
            }
            SEMANA -> if (hoje in de..ate) "Esta semana" else "${dataCurta(de)} a ${dataCurta(ate)}"
            MES -> de.month.getDisplayName(TextStyle.FULL_STANDALONE, PT).replaceFirstChar { it.uppercase() } +
                if (de.year != hoje.year) " de ${de.year}" else ""
        }
    }

    /** Como chamar o período anterior na comparação: "Ontem", "Semana passada", "Setembro". */
    fun anterior(dia: LocalDate): String = when (this) {
        DIA -> "Dia anterior"
        SEMANA -> "Semana anterior"
        MES -> titulo(andar(dia, -1), dia)
    }
}

object Dinheiro {
    fun balanco(corridas: List<Corrida>, totais: List<TotalDoDia>, gastos: List<Gasto>, de: LocalDate, ate: LocalDate): Balanco {
        val porApp = mutableMapOf<String, Double>()
        val sobrouPorDia = linkedMapOf<LocalDate, Double>()
        var d = de
        while (!d.isAfter(ate)) {
            val ganhos = Ganhos.porApp(corridas, d, totais)
            ganhos.forEach { (app, v) -> porApp.merge(app, v, Double::plus) }
            sobrouPorDia[d] = ganhos.values.sum() - Gastos.totalDoDia(gastos, d)
            d = d.plusDays(1)
        }
        val porTipo = gastos.filter { it.quando.toLocalDate() in de..ate }
            .groupBy { it.tipo }.mapValues { (_, l) -> l.sumOf { it.valor } }
        return Balanco(de, ate, porApp.filterValues { it != 0.0 }, porTipo, sobrouPorDia)
    }

    fun balanco(ctx: Context, visao: Visao, dia: LocalDate): Balanco {
        val (de, ate) = visao.periodo(dia)
        return balanco(Corridas.todas(ctx), Totais.todos(ctx), Gastos.todos(ctx), de, ate)
    }

    fun hoje(ctx: Context) = balanco(ctx, Visao.DIA, LocalDate.now())

    /** "R$ 4.870,00" e, se negativo, "− R$ 20,00". */
    fun rs(v: Double) = (if (v < 0) "− " else "") + "R$ " + String.format(PT, "%,.2f", kotlin.math.abs(v))

    /** Quanto [parte] é de [todo], em %, ou null se não há base. */
    fun pct(parte: Double, todo: Double): Int? = if (todo > 0) (parte / todo * 100).let { Math.round(it).toInt() } else null
}

private val PT = Locale("pt", "BR")

private fun dataCurta(d: LocalDate) = String.format(PT, "%02d/%02d", d.dayOfMonth, d.monthValue)

private fun diaDaSemana(d: LocalDate) =
    d.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, PT).substringBefore('-').replaceFirstChar { it.uppercase() }
