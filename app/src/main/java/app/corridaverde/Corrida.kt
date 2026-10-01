package app.corridaverde

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Um ganho lançado por voz: uma corrida, ou o valor que o motorista quis somar. */
data class Corrida(val hora: LocalDateTime, val app: String, val valor: Double)

object Corridas {
    private const val ARQUIVO = "corridas.tsv"

    fun todas(ctx: Context): List<Corrida> {
        val f = File(ctx.filesDir, ARQUIVO)
        return if (f.exists()) f.readLines().mapNotNull { deLinha(it) } else emptyList()
    }

    fun guardar(ctx: Context, c: Corrida) = File(ctx.filesDir, ARQUIVO).appendText(paraLinha(c) + "\n")

    /** Apaga uma corrida (a primeira igual, se houver duas iguais). */
    fun apagar(ctx: Context, c: Corrida) {
        val linhas = File(ctx.filesDir, ARQUIVO).takeIf { it.exists() }?.readLines() ?: return
        val i = linhas.indexOfFirst { deLinha(it) == c }
        if (i < 0) return
        File(ctx.filesDir, ARQUIVO).writeText(linhas.filterIndexed { j, _ -> j != i }.joinToString("") { it + "\n" })
    }

    fun paraLinha(c: Corrida) = listOf(c.hora, c.app, c.valor).joinToString("\t")

    fun deLinha(l: String): Corrida? = runCatching {
        val c = l.split('\t')
        Corrida(LocalDateTime.parse(c[0]), c[1], c[2].toDouble())
    }.getOrNull()
}

/** O total de um app no dia, dito por voz ("faturei 240 na Uber", "fiz 200 na 99"). */
data class TotalDoDia(val hora: LocalDateTime, val valor: Double, val app: String)

object Totais {
    private const val ARQUIVO = "totais.tsv"

    /** Guarda o último total de cada app em cada dia. */
    fun guardar(ctx: Context, t: TotalDoDia) {
        val dia = t.hora.toLocalDate()
        gravar(ctx, todos(ctx).filter { !(it.app == t.app && it.hora.toLocalDate() == dia) && it.hora.toLocalDate().isAfter(dia.minusDays(60)) } + t)
    }

    fun apagar(ctx: Context, t: TotalDoDia) = gravar(ctx, todos(ctx).filter { it != t })

    private fun gravar(ctx: Context, lista: List<TotalDoDia>) =
        File(ctx.filesDir, ARQUIVO).writeText(lista.sortedBy { it.hora }.joinToString("") { "${it.hora}\t${it.valor}\t${it.app}\n" })

    fun todos(ctx: Context): List<TotalDoDia> {
        val f = File(ctx.filesDir, ARQUIVO)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { l ->
            runCatching { l.split('\t').let { TotalDoDia(LocalDateTime.parse(it[0]), it[1].toDouble(), it[2]) } }.getOrNull()
        }
    }
}

object Ganhos {
    /** Ganhos do dia por app: o último total dito por voz mais as corridas lançadas depois dele. */
    fun porApp(corridas: List<Corrida>, dia: LocalDate, totais: List<TotalDoDia> = emptyList()): Map<String, Double> {
        val doDia = corridas.filter { it.hora.toLocalDate() == dia }
        val totaisDoDia = totais.filter { it.hora.toLocalDate() == dia }
        return (totaisDoDia.map { it.app } + doDia.map { it.app }).distinct().associateWith { app ->
            val total = totaisDoDia.lastOrNull { it.app == app }
            (total?.valor ?: 0.0) + doDia.filter { it.app == app && (total == null || it.hora.isAfter(total.hora)) }.sumOf { it.valor }
        }
    }

    fun porApp(ctx: Context, dia: LocalDate = LocalDate.now()) = porApp(Corridas.todas(ctx), dia, Totais.todos(ctx))

    fun doDia(ctx: Context, dia: LocalDate = LocalDate.now()) = porApp(ctx, dia).values.sum()
}
