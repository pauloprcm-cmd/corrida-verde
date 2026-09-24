package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LeitorOfertaTest {
    // Textos da oferta do print de 24/09/2026 (endereços trocados).
    private val print = listOf(
        "Business Comfort", "Exclusivo", "R$ 62,10", "R\$9,86/km aprox.", "4,95 (426)", "Verificado",
        "8 min (1.4 km)", "Rua A, São Paulo", "35 minutos (4.9 km)", "Rua B, 308, Itaim Bibi, São Paulo", "Aceitar",
    )

    @Test
    fun leOfertaDoPrint() {
        val o = LeitorOferta.ler(print)!!
        assertEquals(62.10, o.valor, 0.001)
        assertEquals(1.4, o.buscaKm, 0.001)
        assertEquals(8, o.buscaMin)
        assertEquals(4.9, o.viagemKm, 0.001)
        assertEquals(35, o.viagemMin)
        assertEquals(4.95, o.nota!!, 0.001)
    }

    @Test
    fun avaliaOfertaDoPrint() {
        val quintaDeManha = LocalDateTime.of(2026, 9, 24, 8, 46)
        val r = Avaliador.avaliar(LeitorOferta.ler(print)!!, Config(), quintaDeManha)
        assertEquals(30.07, r.taximetro, 0.001)
        assertEquals(207, r.pct)
        assertEquals(Cor.VERDE, r.cor)
        assertEquals(9.86, r.rsKm, 0.005)
        assertEquals(86.65, r.rsHora!!, 0.005)
        assertFalse(r.bandeira2)
    }

    @Test
    fun ignoraGanhosDoDiaAntesDaOferta() {
        val o = LeitorOferta.ler(listOf("R$ 1.234,56") + print)!!
        assertEquals(62.10, o.valor, 0.001)
    }

    @Test
    fun leHorasMetrosEParadas() {
        val o = LeitorOferta.ler(listOf("R$ 1.180,00", "3 min (800 m)", "1 h 5 min (45.2 km)", "10 min (3.1 km)"))!!
        assertEquals(1180.0, o.valor, 0.001)
        assertEquals(0.8, o.buscaKm, 0.001)
        assertEquals(48.3, o.viagemKm, 0.001)
        assertEquals(75, o.viagemMin)
    }

    @Test
    fun semOfertaDevolveNull() {
        assertNull(LeitorOferta.ler(listOf("Você está online", "R$ 150,00")))
    }

    @Test
    fun buscaLongaRebaixaUmNivel() {
        val o = Oferta(valor = 62.10, buscaKm = 3.5, buscaMin = 12, viagemKm = 4.9, viagemMin = 35, nota = null)
        val r = Avaliador.avaliar(o, Config(), LocalDateTime.of(2026, 9, 24, 8, 46))
        assertTrue(r.buscaLonga)
        assertEquals(Cor.AMARELO, r.cor)
    }

    @Test
    fun bandeira2() {
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 9, 24, 21, 0)))  // quinta à noite
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 9, 27, 12, 0)))  // domingo
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 11, 20, 12, 0))) // Consciência Negra
        assertFalse(Tarifa.bandeira2(LocalDateTime.of(2026, 9, 26, 12, 0))) // sábado de dia
        assertEquals(LocalDate.of(2026, 4, 5), Feriados.pascoa(2026))
        assertEquals(9.83 + 10 * 7.20 * 1.3, Tarifa.taximetro(10.0, luxo = true, bandeira2 = true), 0.001)
    }
}
