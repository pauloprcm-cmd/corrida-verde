package app.corridaverde

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Uma corrida feita, com o valor da oferta aceita (a tela do fim da corrida não mostra o valor). */
data class Corrida(val hora: LocalDateTime, val app: String, val valor: Double)

/**
 * Acompanha as telas de um app para saber qual oferta virou corrida.
 *
 * Uber: a oferta vira a corrida da vez quando aparece "Iniciar <categoria>" (chegou no
 * passageiro) e conta quando aparece "Encerrar <categoria>" (passageiro a bordo). Uma
 * oferta que chega no meio da corrida não troca a da vez, porque ela já foi presa no "Iniciar".
 * 99: conta a última oferta quando aparece "Finalizar corrida".
 */
class AcompanhaCorrida(private val app: String) {
    private var ultimaOferta: Oferta? = null
    private var horaOferta: LocalDateTime? = null
    private var daVez: Oferta? = null

    fun oferta(o: Oferta, agora: LocalDateTime) {
        ultimaOferta = o
        horaOferta = agora
    }

    /** Devolve a corrida quando ela deve entrar na soma, uma vez só. */
    fun tela(textos: List<String>, agora: LocalDateTime): Corrida? {
        val linhas = textos.flatMap { it.lines() }.map { it.trim() }
        if (app == "99") {
            if (linhas.none { it.equals("Finalizar corrida", ignoreCase = true) }) return null
            return pegarUltima(agora)?.let { Corrida(agora, app, it.valor) }
        }
        if (linhas.any { INICIAR.matches(it) } && daVez == null) daVez = pegarUltima(agora)
        if (linhas.any { ENCERRAR.matches(it) }) {
            val o = daVez ?: pegarUltima(agora) ?: return null
            daVez = null
            return Corrida(agora, app, o.valor)
        }
        return null
    }

    /** A última oferta, se for recente; ela sai daqui para não ser contada duas vezes. */
    private fun pegarUltima(agora: LocalDateTime): Oferta? {
        val o = ultimaOferta ?: return null
        val h = horaOferta ?: return null
        ultimaOferta = null
        return if (h.plusHours(2).isAfter(agora)) o else null
    }

    companion object {
        private val INICIAR = Regex("""Iniciar (?!sessão|navega).{2,30}""", RegexOption.IGNORE_CASE)
        private val ENCERRAR = Regex("""Encerrar (?!sessão|o modo|modo)[^?]{2,30}""", RegexOption.IGNORE_CASE)
    }
}

object Corridas {
    private const val ARQUIVO = "corridas.tsv"

    fun todas(ctx: Context): List<Corrida> {
        val f = File(ctx.filesDir, ARQUIVO)
        return if (f.exists()) f.readLines().mapNotNull { deLinha(it) } else emptyList()
    }

    fun guardar(ctx: Context, c: Corrida) = File(ctx.filesDir, ARQUIVO).appendText(paraLinha(c) + "\n")

    fun paraLinha(c: Corrida) = listOf(c.hora, c.app, c.valor).joinToString("\t")

    fun deLinha(l: String): Corrida? = runCatching {
        val c = l.split('\t')
        Corrida(LocalDateTime.parse(c[0]), c[1], c[2].toDouble())
    }.getOrNull()
}

/** O ganho do dia que a Uber mostra no topo da tela inicial ("Página inicial", "R$ 214,45"). */
data class TotalUber(val hora: LocalDateTime, val valor: Double)

object TotaisUber {
    private const val ARQUIVO = "uber_hoje.tsv"
    private val VALOR = Regex("""^R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})$""")

    /**
     * O valor vem logo depois de "Página inicial". No Modo de privacidade esse fica escondido,
     * mas o cartão "HOJE | 8 viagens concluídas | … | VER PROGRESSO | R$ 267,07" ainda mostra.
     */
    fun ler(textos: List<String>, agora: LocalDateTime = LocalDateTime.now()): TotalUber? {
        val linhas = textos.map { it.replace('\u00a0', ' ').trim() }
        val topo = linhas.indexOfFirst { it.equals("Página inicial", ignoreCase = true) }
        val hoje = linhas.indexOfFirst { it.equals("HOJE", ignoreCase = true) }
        val cartao = if (hoje >= 0) linhas.withIndex().indexOfFirst { (i, t) -> i > hoje && t.equals("VER PROGRESSO", ignoreCase = true) } else -1
        val m = listOf(topo, cartao).filter { it >= 0 }
            .firstNotNullOfOrNull { i -> linhas.getOrNull(i + 1)?.let { VALOR.find(it) } } ?: return null
        val valor = m.groupValues[1].replace(".", "").replace(',', '.').toDoubleOrNull() ?: return null
        return TotalUber(agora, valor)
    }

    /** Guarda a última leitura de cada dia. Devolve true se o valor do dia mudou. */
    fun guardar(ctx: Context, t: TotalUber): Boolean {
        val antes = todos(ctx)
        val dia = t.hora.toLocalDate()
        if (antes.any { it.hora.toLocalDate() == dia && it.valor == t.valor }) return false
        val lista = antes.filter { it.hora.toLocalDate() != dia && it.hora.toLocalDate().isAfter(dia.minusDays(60)) } + t
        File(ctx.filesDir, ARQUIVO).writeText(lista.sortedBy { it.hora }.joinToString("") { "${it.hora}\t${it.valor}\n" })
        return true
    }

    fun todos(ctx: Context): List<TotalUber> {
        val f = File(ctx.filesDir, ARQUIVO)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { l ->
            runCatching { l.split('\t').let { TotalUber(LocalDateTime.parse(it[0]), it[1].toDouble()) } }.getOrNull()
        }
    }

    fun doDia(ctx: Context, dia: LocalDate) = todos(ctx).lastOrNull { it.hora.toLocalDate() == dia }
}

object Ganhos {
    /**
     * Ganhos do dia. Uber: o total que ela mostra na tela inicial, mais as corridas encerradas
     * depois dessa leitura; sem essa leitura, as sessões (valor oficial, com gorjeta e ajuste)
     * mais as corridas fora de uma sessão fechada. 99: todas as corridas.
     */
    fun doDia(sessoes: List<Sessao>, corridas: List<Corrida>, dia: LocalDate, totalUber: TotalUber? = null): Double {
        val doDia = corridas.filter { it.hora.toLocalDate() == dia }
        val noventaENove = doDia.filter { it.app != "Uber" }.sumOf { it.valor }
        if (totalUber != null) {
            return totalUber.valor + noventaENove + doDia.filter { it.app == "Uber" && it.hora.isAfter(totalUber.hora) }.sumOf { it.valor }
        }
        val soltas = doDia.filter { c ->
            c.app == "Uber" && sessoes.none { s -> !c.hora.isBefore(s.inicio) && !c.hora.isAfter(s.fim.plusMinutes(1)) }
        }
        return Sessoes.totalDoDia(sessoes, dia) + noventaENove + soltas.sumOf { it.valor }
    }

    fun doDia(ctx: Context, dia: LocalDate = LocalDate.now()) =
        doDia(Sessoes.todas(ctx), Corridas.todas(ctx), dia, TotaisUber.doDia(ctx, dia))

    fun corridasDoDia(ctx: Context, dia: LocalDate = LocalDate.now()) = Corridas.todas(ctx).count { it.hora.toLocalDate() == dia }
}
