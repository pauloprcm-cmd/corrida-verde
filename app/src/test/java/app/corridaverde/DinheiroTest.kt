package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DinheiroTest {
    private fun h(dia: Int, hora: Int) = LocalDateTime.of(2026, 10, dia, hora, 0)

    private val totais = listOf(
        TotalDoDia(h(1, 12), 146.0, "Uber"),
        TotalDoDia(h(1, 18), 346.0, "Uber"),
        TotalDoDia(h(2, 20), 200.0, "99"),
    )
    private val corridas = listOf(Corrida(h(1, 21), "Táxi", 40.0), Corrida(h(2, 10), "Táxi", 30.0))
    private val gastos = listOf(
        Gasto(h(1, 8), Tipo.ETANOL, 92.0),
        Gasto(h(2, 9), Tipo.LAVAGEM, 25.0),
        Gasto(LocalDateTime.of(2026, 9, 30, 9, 0), Tipo.GASOLINA, 100.0),
    )

    @Test
    fun sobrouNoDia() {
        val b = Dinheiro.balanco(corridas, totais, gastos, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1))
        assertEquals(386.0, b.entrou, 0.001)
        assertEquals(92.0, b.gastou, 0.001)
        assertEquals(294.0, b.sobrou, 0.001)
        assertEquals(mapOf("Uber" to 346.0, "Táxi" to 40.0), b.porApp)
    }

    @Test
    fun somaOsDiasDaSemana() {
        val (de, ate) = Visao.SEMANA.periodo(LocalDate.of(2026, 10, 1))
        assertEquals(LocalDate.of(2026, 9, 28), de)
        assertEquals(LocalDate.of(2026, 10, 4), ate)
        val b = Dinheiro.balanco(corridas, totais, gastos, de, ate)
        assertEquals(616.0, b.entrou, 0.001)
        assertEquals(217.0, b.gastou, 0.001)
        assertEquals(192.0, b.combustivel, 0.001)
        assertEquals(-100.0, b.sobrouPorDia[LocalDate.of(2026, 9, 30)]!!, 0.001)
        assertEquals(205.0, b.sobrouPorDia[LocalDate.of(2026, 10, 2)]!!, 0.001)
        assertEquals(7, b.sobrouPorDia.size)
    }

    @Test
    fun periodosETitulos() {
        val hoje = LocalDate.of(2026, 10, 1)
        assertEquals(LocalDate.of(2026, 10, 31), Visao.MES.periodo(hoje).second)
        assertEquals("Outubro", Visao.MES.titulo(hoje, hoje))
        assertEquals("Setembro", Visao.MES.anterior(hoje))
        assertEquals("Ontem", Visao.DIA.titulo(hoje.minusDays(1), hoje))
        assertEquals("Dezembro de 2025", Visao.MES.titulo(LocalDate.of(2025, 12, 5), hoje))
        assertEquals("R$ 4.870,00", Dinheiro.rs(4870.0))
        assertEquals("− R$ 20,50", Dinheiro.rs(-20.5))
    }

    @Test
    fun lancamentosVoltamIguaisDoArquivo() {
        // A correção acha o lançamento antigo comparando o que volta do arquivo.
        val g = Gasto(h(1, 8), Tipo.ETANOL, 120.0, litros = 22.5, km = 45320, tanqueCheio = true)
        assertEquals(g, Gastos.deLinha(Gastos.paraLinha(g)))
        assertEquals(totais[0], Totais.deLinha(Totais.paraLinha(totais[0])))
        assertEquals(corridas[0], Corridas.deLinha(Corridas.paraLinha(corridas[0])))
    }
}
