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
        private val ENCERRAR = Regex("""Encerrar (?!sessão).{2,30}""", RegexOption.IGNORE_CASE)
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

object Ganhos {
    /**
     * Ganhos do dia: as sessões da Uber (valor oficial, com gorjeta e ajuste) mais as
     * corridas que ainda não estão dentro de uma sessão fechada, e todas as da 99.
     */
    fun doDia(sessoes: List<Sessao>, corridas: List<Corrida>, dia: LocalDate): Double {
        val soltas = corridas.filter { c ->
            c.hora.toLocalDate() == dia &&
                (c.app != "Uber" || sessoes.none { s -> !c.hora.isBefore(s.inicio) && !c.hora.isAfter(s.fim.plusMinutes(1)) })
        }
        return Sessoes.totalDoDia(sessoes, dia) + soltas.sumOf { it.valor }
    }

    fun doDia(ctx: Context, dia: LocalDate = LocalDate.now()) = doDia(Sessoes.todas(ctx), Corridas.todas(ctx), dia)

    fun corridasDoDia(ctx: Context, dia: LocalDate = LocalDate.now()) = Corridas.todas(ctx).count { it.hora.toLocalDate() == dia }
}
