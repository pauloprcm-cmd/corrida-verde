package app.corridaverde

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Uma sessão online na Uber, lida da tela "Resumo da sessão" que aparece ao ficar offline. */
data class Sessao(
    val inicio: LocalDateTime,
    val fim: LocalDateTime,
    val valor: Double,
    val viagens: Int? = null,
)

object LeitorSessao {
    private val QUANDO = Regex("""(\d{1,2}) de ([a-zç]{3})[a-zç]*\.?,?\s*(\d{1,2}):(\d{2})""", RegexOption.IGNORE_CASE)
    private val VALOR = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})""")
    private val MESES = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    /**
     * Lê os textos da tela. Ex.: "28 de set., 17:09 – 28 de set., 18:11", "R$ 59,67",
     * "Viagens concluídas", "1". A tela não traz o ano: vale o de [agora], ou o anterior
     * se a data cair no futuro (resumo de dezembro visto em janeiro).
     */
    fun ler(textos: List<String>, agora: LocalDateTime = LocalDateTime.now()): Sessao? {
        val tela = textos.joinToString("\n").replace(' ', ' ').replace(' ', ' ')
        if (!tela.contains("Resumo da sessão", ignoreCase = true)) return null
        val datas = QUANDO.findAll(tela).mapNotNull { data(it, agora) }.take(2).toList()
        if (datas.size < 2) return null
        val depoisDasDatas = tela.substring(QUANDO.findAll(tela).elementAt(1).range.last)
        val valor = VALOR.find(depoisDasDatas)?.groupValues?.get(1)
            ?.replace(".", "")?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val i = textos.indexOfFirst { it.trim().equals("Viagens concluídas", ignoreCase = true) }
        val viagens = if (i >= 0) textos.drop(i + 1).firstNotNullOfOrNull { it.trim().toIntOrNull() } else null
        return Sessao(datas[0], datas[1], valor, viagens)
    }

    private fun data(m: MatchResult, agora: LocalDateTime): LocalDateTime? = runCatching {
        val (dia, mes, hora, minuto) = m.destructured
        val nMes = MESES.indexOf(mes.lowercase()) + 1
        if (nMes == 0) return null
        val d = LocalDateTime.of(agora.year, nMes, dia.toInt(), hora.toInt(), minuto.toInt())
        if (d.toLocalDate().isAfter(agora.toLocalDate().plusDays(1))) d.minusYears(1) else d
    }.getOrNull()
}

object Sessoes {
    private const val ARQUIVO = "ganhos.tsv"

    fun todas(ctx: Context): List<Sessao> {
        val f = File(ctx.filesDir, ARQUIVO)
        return if (f.exists()) f.readLines().mapNotNull { deLinha(it) } else emptyList()
    }

    /**
     * Guarda a sessão. Cada resumo só traz as corridas daquela sessão, então a mesma
     * sessão (mesmo início) vista de novo substitui a anterior em vez de somar duas vezes.
     * Devolve false se ela já estava guardada igual.
     */
    fun guardar(ctx: Context, s: Sessao): Boolean {
        val antes = todas(ctx)
        if (s in antes) return false
        val lista = antes.filter { it.inicio != s.inicio } + s
        File(ctx.filesDir, ARQUIVO).writeText(lista.sortedBy { it.inicio }.joinToString("") { paraLinha(it) + "\n" })
        return true
    }

    fun paraLinha(s: Sessao) = listOf(s.inicio, s.fim, s.valor, s.viagens ?: "").joinToString("\t")

    fun deLinha(l: String): Sessao? = runCatching {
        val c = l.split('\t')
        Sessao(LocalDateTime.parse(c[0]), LocalDateTime.parse(c[1]), c[2].toDouble(), c[3].toIntOrNull())
    }.getOrNull()

    /** A sessão conta no dia em que começou (quem vira a noite soma tudo no dia do início). */
    fun totalDoDia(sessoes: List<Sessao>, dia: LocalDate) = sessoes.filter { it.inicio.toLocalDate() == dia }.sumOf { it.valor }
}
