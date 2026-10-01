package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CorridaTest {
    private val dia = LocalDate.of(2026, 9, 30)
    private fun hora(h: Int) = LocalDateTime.of(2026, 9, 30, h, 0)

    @Test
    fun totalDitoPorVozMaisAsCorridasDepoisDele() {
        val corridas = listOf(
            Corrida(hora(10), "99", 20.0),
            Corrida(hora(19), "99", 15.0),
            Corrida(hora(11), "inDrive", 30.0),
            Corrida(LocalDateTime.of(2026, 9, 29, 11, 0), "inDrive", 50.0),
        )
        val totais = listOf(
            TotalDoDia(hora(18), 200.0, "99"),
            TotalDoDia(hora(21), 240.0, "Uber"),
        )
        val p = Ganhos.porApp(corridas, dia, totais)
        assertEquals(215.0, p["99"]!!, 0.001)
        assertEquals(30.0, p["inDrive"]!!, 0.001)
        assertEquals(240.0, p["Uber"]!!, 0.001)
        assertEquals(3, p.size)
    }

    @Test
    fun semTotalSomaAsCorridas() {
        val corridas = listOf(Corrida(hora(10), "Uber", 23.5), Corrida(hora(12), "Uber", 18.0))
        assertEquals(41.5, Ganhos.porApp(corridas, dia)["Uber"]!!, 0.001)
        assertEquals(corridas[0], Corridas.deLinha(Corridas.paraLinha(corridas[0])))
    }
}
