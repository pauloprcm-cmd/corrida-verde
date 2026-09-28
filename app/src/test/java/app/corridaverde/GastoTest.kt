package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class GastoTest {
    @Test
    fun leAbastecimentoCompleto() {
        val l = LeitorGasto.ler("Abasteci 120 reais de etanol, 22 litros, tanque cheio, quilometragem 45.320")
        assertEquals(Tipo.ETANOL, l.tipo)
        assertEquals(120.0, l.valor!!, 0.001)
        assertEquals(22.0, l.litros!!, 0.001)
        assertEquals(45320, l.km)
        assertTrue(l.tanqueCheio)
    }

    @Test
    fun leComoOReconhecimentoDeVozEscreve() {
        val l = LeitorGasto.ler("abasteci R$ 150,50 de gasolina completei o tanque km 45320")
        assertEquals(Tipo.GASOLINA, l.tipo)
        assertEquals(150.50, l.valor!!, 0.001)
        assertNull(l.litros)
        assertEquals(45320, l.km)
        assertTrue(l.tanqueCheio)
    }

    @Test
    fun calculaLitrosPeloPreco() {
        val l = LeitorGasto.ler("coloquei 100 reais de álcool a 4,00 o litro")
        assertEquals(Tipo.ETANOL, l.tipo)
        assertEquals(100.0, l.valor!!, 0.001)
        assertEquals(25.0, l.litros!!, 0.001)
        assertFalse(l.tanqueCheio)
    }

    @Test
    fun leGnvEmMetrosCubicos() {
        val l = LeitorGasto.ler("abasteci GNV 60 reais 14,5 metros cúbicos")
        assertEquals(Tipo.GNV, l.tipo)
        assertEquals(14.5, l.litros!!, 0.001)
    }

    @Test
    fun leOutrasDespesas() {
        assertEquals(Tipo.LAVAGEM, LeitorGasto.ler("gastei 35 reais de lavagem").tipo)
        val e = LeitorGasto.ler("paguei 80 de estacionamento")
        assertEquals(Tipo.ESTACIONAMENTO, e.tipo)
        assertEquals(80.0, e.valor!!, 0.001)
        assertEquals(Tipo.ALIMENTACAO, LeitorGasto.ler("almoço 32 reais").tipo)
        assertNull(LeitorGasto.ler("gastei 10 reais").tipo)
    }

    private fun abast(dia: Int, valor: Double, litros: Double?, km: Int?, cheio: Boolean) =
        Gasto(LocalDateTime.of(2026, 9, dia, 10, 0), Tipo.ETANOL, valor, litros, km, cheio)

    @Test
    fun consumoDeTanqueCheioATanqueCheio() {
        val gastos = listOf(
            abast(20, 200.0, 45.0, 45000, true),
            Gasto(LocalDateTime.of(2026, 9, 21, 12, 0), Tipo.LAVAGEM, 35.0),
            abast(22, 50.0, 12.0, null, false),
            abast(24, 120.0, 22.0, 45340, true),
        )
        val c = Gastos.consumo(gastos)!!
        assertEquals(340, c.kmRodados)
        assertEquals(34.0, c.litros, 0.001)
        assertEquals(10.0, c.kmPorLitro, 0.001)
        assertEquals(0.5, c.custoKm, 0.001)
    }

    @Test
    fun semConsumoQuandoFaltaDado() {
        // Primeiro tanque cheio: ainda não há com o que comparar.
        assertNull(Gastos.consumo(listOf(abast(20, 200.0, 45.0, 45000, true))))
        // Último não foi tanque cheio.
        assertNull(Gastos.consumo(listOf(abast(20, 200.0, 45.0, 45000, true), abast(24, 50.0, 12.0, 45100, false))))
        // Um abastecimento no meio sem litros.
        assertNull(Gastos.consumo(listOf(
            abast(20, 200.0, 45.0, 45000, true), abast(22, 50.0, null, null, false), abast(24, 120.0, 22.0, 45340, true),
        )))
    }

    @Test
    fun guardaELeALinha() {
        val g = abast(24, 120.0, 22.0, 45340, true)
        assertEquals(g, Gastos.deLinha(Gastos.paraLinha(g)))
        val semLitros = Gasto(LocalDateTime.of(2026, 9, 24, 10, 0), Tipo.PEDAGIO, 9.8)
        assertEquals(semLitros, Gastos.deLinha(Gastos.paraLinha(semLitros)))
    }
}
