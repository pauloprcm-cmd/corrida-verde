package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FalaTest {
    private fun ganho(fala: String): Ganho {
        assertTrue("devia ser ganho: $fala", LeitorFala.ehGanho(fala))
        return LeitorFala.lerGanho(fala)
    }

    @Test
    fun faturei240NaUberEOTotalDoDia() {
        val g = ganho("faturei 240 na Uber")
        assertEquals("Uber", g.app)
        assertEquals(240.0, g.valor!!, 0.001)
        assertTrue(g.total)
    }

    @Test
    fun fiz200Na99EOTotalDoDia() {
        val g = ganho("fiz 200 na 99")
        assertEquals("99", g.app)
        assertEquals(200.0, g.valor!!, 0.001)
        assertTrue(g.total)
        // Com "corrida" é uma corrida só.
        assertFalse(ganho("fiz uma corrida de 30 na 99").total)
    }

    @Test
    fun ganheiNaTelaDoAppEOTotalDoDia() {
        // De manhã 146, à tarde a tela da 99 mostra 346: o segundo substitui o primeiro.
        val g = ganho("ganhei 346 na 99")
        assertEquals("99", g.app)
        assertEquals(346.0, g.valor!!, 0.001)
        assertTrue(g.total)
        assertTrue(ganho("recebi 180 na Uber").total)
        // Táxi e corrida particular não têm tela com o total: somam.
        assertFalse(ganho("taxímetro deu 62 reais").total)
        assertFalse(ganho("particular 80 reais").total)
    }

    @Test
    fun corridaDa99NaoConfundeONomeComOValor() {
        val g = ganho("corrida da 99 23 e 50")
        assertEquals("99", g.app)
        assertEquals(23.50, g.valor!!, 0.001)
        assertFalse(g.total)
        assertEquals(18.90, ganho("ganhei R$ 18,90 na 99").valor!!, 0.001)
        assertEquals("99", ganho("noventa e nove 30 reais").app)
        // Sem "da"/"na", o 99 é dinheiro.
        assertEquals(99.0, ganho("recebi 99 reais no inDrive").valor!!, 0.001)
    }

    @Test
    fun outrasFormasDeFalar() {
        assertEquals("inDrive", ganho("in drive 30 reais").app)
        assertEquals(45.0, ganho("fiz 3 corridas na Uber deu 45 reais").valor!!, 0.001)
        assertEquals("Táxi", ganho("taxímetro deu 62 reais").app)
        assertEquals(240.50, ganho("hoje a 99 fechou 240 reais e 50 centavos").valor!!, 0.001)
        assertTrue(ganho("total da 99 hoje 210").total)
        val semApp = ganho("ganhei 50 reais")
        assertNull(semApp.app)
        assertEquals(50.0, semApp.valor!!, 0.001)
    }

    @Test
    fun semONomeDoAppSoma() {
        // Passageiro de rua: sem app, cada valor dito soma.
        assertFalse(ganho("ganhei 50 reais").total)
        assertFalse(ganho("fiz 80").total)
        assertFalse(ganho("faturei 120").total)
    }

    @Test
    fun gastoContinuaGasto() {
        assertFalse(LeitorFala.ehGanho("abasteci 120 reais de etanol tanque cheio"))
        assertFalse(LeitorFala.ehGanho("paguei 15 de estacionamento"))
        assertFalse(LeitorFala.ehGanho("almoço 25 reais"))
        assertFalse(LeitorFala.ehGanho("gastei 40 no lava rápido perto da Uber"))
        // Sem nada que indique ganho, fica gasto (como antes).
        assertFalse(LeitorFala.ehGanho("120 reais"))
    }
}
