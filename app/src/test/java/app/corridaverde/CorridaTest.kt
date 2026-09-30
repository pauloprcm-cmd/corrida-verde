package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CorridaTest {
    private val t0 = LocalDateTime.of(2026, 9, 29, 12, 40)
    private fun oferta(valor: Double) = Oferta(valor = valor, buscaKm = 1.0, buscaMin = 3, viagemKm = 5.0, viagemMin = 15, nota = 4.9)

    @Test
    fun uberContaAOfertaQuandoOPassageiroEmbarca() {
        val a = AcompanhaCorrida("Uber")
        a.oferta(oferta(38.94), t0)
        assertNull(a.tela(listOf("Usuário notificado", "Lucineide", "Iniciar Táxi Promo"), t0.plusMinutes(8)))
        val c = a.tela(listOf("0 min", "0 km", "Encerrar Táxi Promo"), t0.plusMinutes(20))!!
        assertEquals(38.94, c.valor, 0.001)
        assertEquals("Uber", c.app)
        // A tela continua com "Encerrar" até o fim: não conta de novo.
        assertNull(a.tela(listOf("Encerrar Táxi Promo"), t0.plusMinutes(21)))
    }

    @Test
    fun ofertaNoMeioDaCorridaNaoTrocaADaVez() {
        val a = AcompanhaCorrida("Uber")
        a.oferta(oferta(30.0), t0)
        a.tela(listOf("Iniciar Black"), t0.plusMinutes(5))
        a.oferta(oferta(99.0), t0.plusMinutes(10))
        assertEquals(30.0, a.tela(listOf("Encerrar Black"), t0.plusMinutes(15))!!.valor, 0.001)
        a.tela(listOf("Iniciar UberX"), t0.plusMinutes(30))
        assertEquals(99.0, a.tela(listOf("Encerrar UberX"), t0.plusMinutes(40))!!.valor, 0.001)
    }

    @Test
    fun ofertaRecusadaEAntigaNaoConta() {
        val a = AcompanhaCorrida("Uber")
        a.oferta(oferta(30.0), t0)
        assertNull(a.tela(listOf("Encerrar Black"), t0.plusHours(3)))
        assertNull(a.tela(listOf("Encerrar Black"), t0.plusHours(3)))
    }

    @Test
    fun noveNoveContaNoFinalizarCorrida() {
        val a = AcompanhaCorrida("99")
        a.oferta(oferta(13.01), t0)
        assertNull(a.tela(listOf("Pagamento no aplicativo"), t0.plusMinutes(5)))
        assertEquals(13.01, a.tela(listOf("1 min · 10 m", "Finalizar corrida"), t0.plusMinutes(10))!!.valor, 0.001)
        assertNull(a.tela(listOf("Finalizar corrida"), t0.plusMinutes(11)))
    }

    @Test
    fun resumoDaSessaoSubstituiAsCorridasDela() {
        val dia = LocalDate.of(2026, 9, 29)
        val sessoes = listOf(Sessao(LocalDateTime.of(2026, 9, 29, 12, 0), LocalDateTime.of(2026, 9, 29, 13, 30), 45.0, 2))
        val corridas = listOf(
            Corrida(LocalDateTime.of(2026, 9, 29, 12, 20), "Uber", 20.0),
            Corrida(LocalDateTime.of(2026, 9, 29, 13, 10), "Uber", 22.0),
            Corrida(LocalDateTime.of(2026, 9, 29, 13, 0), "99", 13.01),
            Corrida(LocalDateTime.of(2026, 9, 29, 15, 0), "Uber", 30.0),
            Corrida(LocalDateTime.of(2026, 9, 28, 15, 0), "Uber", 50.0),
        )
        assertEquals(45.0 + 13.01 + 30.0, Ganhos.doDia(sessoes, corridas, dia), 0.001)
        assertEquals(corridas[2], Corridas.deLinha(Corridas.paraLinha(corridas[2])))
    }
}
