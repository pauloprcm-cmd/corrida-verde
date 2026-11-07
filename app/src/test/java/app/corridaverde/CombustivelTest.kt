package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CombustivelTest {
    private fun a(dia: Int, tipo: Tipo, valor: Double, litros: Double?, km: Int?, cheio: Boolean) =
        Gasto(LocalDateTime.of(2026, 11, dia, 10, 0), tipo, valor, litros, km, cheio)

    private val gastos = listOf(
        a(1, Tipo.GASOLINA, 300.0, 50.0, 10000, true),
        a(3, Tipo.GASOLINA, 120.0, 20.0, null, false),     // meio tanque no caminho, sem km
        a(5, Tipo.GASOLINA, 180.0, 30.0, 10530, true),     // 530 km com 50 L: 10,6 km/l
        a(8, Tipo.ETANOL, 200.0, 50.0, 10900, true),       // 370 km com 50 L de etanol
        a(10, Tipo.ETANOL, 160.0, 40.0, 11200, true),      // 300 km com 40 L
    )

    @Test
    fun separaOsTrechosPorCombustivel() {
        val t = Abastecimentos.trechos(gastos)
        assertEquals(listOf(Tipo.GASOLINA, Tipo.ETANOL, Tipo.ETANOL), t.map { it.combustivel })
        assertEquals(10.6, t[0].kmPorLitro, 0.001)
        val lista = Abastecimentos.porCombustivel(gastos, LocalDate.of(2026, 10, 1))
        // Etanol faz menos km/l, mas sai mais barato por km: vem primeiro.
        assertEquals(Tipo.ETANOL, lista[0].tipo)
        assertEquals(2, lista[0].tanques)
        assertEquals(360.0 / 670, lista[0].custoKm, 0.0001)
        assertEquals(300.0 / 530, lista[1].custoKm, 0.0001)
        assertEquals((670.0 / 90) / 10.6, Abastecimentos.limiteEtanol(lista)!!, 0.0001)
    }

    @Test
    fun trechoMisturadoNaoEntraNaComparacao() {
        val misturado = listOf(
            a(1, Tipo.GASOLINA, 300.0, 50.0, 10000, true),
            a(2, Tipo.ETANOL, 100.0, 25.0, null, false),
            a(4, Tipo.GASOLINA, 150.0, 25.0, 10400, true),
        )
        assertNull(Abastecimentos.trechos(misturado).single().combustivel)
        assertTrue(Abastecimentos.porCombustivel(misturado, LocalDate.of(2026, 10, 1)).isEmpty())
    }

    @Test
    fun mediaDoMesSemTanqueCheio() {
        val parciais = listOf(
            a(1, Tipo.GASOLINA, 120.0, 20.0, 10000, false),
            a(6, Tipo.GASOLINA, 120.0, 20.0, 10210, false),
            a(12, Tipo.GASOLINA, 120.0, 20.0, 10420, false),
        )
        val m = Abastecimentos.mediaDoPeriodo(parciais, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))!!
        assertEquals(420, m.kmRodados)
        assertEquals(10.5, m.kmPorLitro, 0.001)    // 420 km com os 40 L colocados depois do primeiro
        // Com poucos km ainda não mostra.
        assertNull(Abastecimentos.mediaDoPeriodo(parciais.take(2), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        assertEquals(1, Abastecimentos.semKm(gastos, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun avisaQuandoOConsumoCai() {
        assertNull(Abastecimentos.queda(Abastecimentos.trechos(gastos)))
        val pior = gastos + a(13, Tipo.ETANOL, 200.0, 50.0, 11500, true) // 300 km com 50 L: 6,0 km/l
        assertEquals(1 - 6.0 / (670.0 / 90), Abastecimentos.queda(Abastecimentos.trechos(pior))!!, 0.001)
    }

    @Test
    fun contaDoEtanolNaHoraDeAbastecer() {
        val msg = Abastecimentos.compararPreco(gastos, gastos[4])
        assertNotNull(msg)
        assertTrue(msg!!, msg.startsWith("No seu carro, o etanol compensa até 70%"))
        assertTrue(msg, msg.endsWith("está a 67% da gasolina a R$ 6,00: o etanol compensa."))
        // Sem preço do outro combustível nos últimos 60 dias, não diz nada.
        assertNull(Abastecimentos.compararPreco(gastos.drop(3), gastos[4]))
    }
}
