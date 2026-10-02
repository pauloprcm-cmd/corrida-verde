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
        assertEquals("Rua B, 308, Itaim Bibi, São Paulo", o.destino)
    }

    @Test
    fun avaliaOfertaDoPrint() {
        val quintaDeManha = LocalDateTime.of(2026, 9, 24, 8, 46)
        val r = Avaliador.avaliar(LeitorOferta.ler(print)!!, Config(), quintaDeManha)
        // 4,9 km em 35 min: ~23 min de trânsito cobrados pela hora parada.
        assertEquals(51.57, r.taximetro, 0.005)
        assertEquals(23, r.minParado)
        assertEquals(120, r.pct)
        assertEquals(Cor.VERDE, r.cor)
        assertEquals(9.86, r.rsKm, 0.005)
        assertEquals(86.65, r.rsHora!!, 0.005)
        assertFalse(r.bandeira2)
    }

    @Test
    fun leOfertaUberXComSelecionar() {
        // Oferta do print de 24/09/2026, 21:23 (botão "Selecionar" em vez de "Aceitar").
        val o = LeitorOferta.ler(listOf(
            "UberX", "R$ 14,36", "R\$2,02/km aprox.", "4,87 (153)", "Verificado",
            "10 min (3.0 km)", "Rua A, Brasilandia, São Paulo", "13 minutos (4.1 km)", "Rua B, 6, Brasilandia, São Paulo",
            "Selecionar",
        ))!!
        assertEquals(14.36, o.valor, 0.001)
        assertEquals(3.0, o.buscaKm, 0.001)
        assertEquals(4.1, o.viagemKm, 0.001)
        assertEquals(13, o.viagemMin)
    }

    @Test
    fun leFaixaDaCategoriaTaxi() {
        // Print de 01/10/2026, 17:28: o valor final fica entre o mínimo e o máximo.
        val o = LeitorOferta.ler(listOf(
            "Táxi", "R$ 31 - R$ 46", "4,97 (2106)", "Verificado",
            "2 min (0.1 km)", "Rua A, Itaim Bibi, São Paulo", "30 minutos (6.1 km)", "Rua B, Vila Mariana, São Paulo", "Selecionar",
        ))!!
        assertEquals(31.0, o.valor, 0.001)
        assertEquals(46.0, o.valorMax!!, 0.001)
        assertEquals(6.1, o.viagemKm, 0.001)
        assertEquals("Rua B, Vila Mariana, São Paulo", o.destino)
        val r = Avaliador.avaliar(o, Config(), LocalDateTime.of(2026, 10, 1, 17, 28))
        assertTrue(r.pctMax!! > r.pct)
        assertEquals(Cor.VERMELHO, r.cor)
    }

    @Test
    fun ignoraGanhosDoDiaAntesDaOferta() {
        val o = LeitorOferta.ler(listOf("R$ 1.234,56") + print)!!
        assertEquals(62.10, o.valor, 0.001)
    }

    @Test
    fun leHoraComE() {
        val o = LeitorOferta.ler(listOf("Black", "R$ 173,39", "R$3,09/km aprox.", "4,79 (840)", "Verificado", "5 min (1.2 km)",
            "Av Parada Pinto, Cachoeirinha, São Paulo", "1 parada", "1 h e 29 min (54.9 km)", "Rua Imbituba, 66", "Selecionar"))!!
        assertEquals(173.39, o.valor, 0.001)
        assertEquals(89, o.viagemMin)
        assertEquals(54.9, o.viagemKm, 0.001)
    }

    @Test
    fun leHorasMetrosEParadas() {
        val o = LeitorOferta.ler(listOf("R$ 1.180,00", "3 min (800 m)", "1 h 5 min (45.2 km)", "10 min (3.1 km)"))!!
        assertEquals(1180.0, o.valor, 0.001)
        assertEquals(0.8, o.buscaKm, 0.001)
        assertEquals(48.3, o.viagemKm, 0.001)
        assertEquals(75, o.viagemMin)
        assertEquals(1, o.paradas)
    }

    @Test
    fun ofertaExclusivaComParadaAvisaNoPopup() {
        // Textos do print Chamada Uber Black Exclusiva de 27/09/2026 (endereços trocados).
        val o = LeitorOferta.ler(listOf(
            "Black", "Exclusivo", "R$ 85,13", "R\$6,81/km aprox.", "4,94 (1511)", "Verificado",
            "3 min (0.6 km)", "Rua A, São Paulo", "1 parada", "37 minutos (11.9 km)", "Rua B, Liberdade, São Paulo", "Aceitar",
        ))!!
        assertEquals(85.13, o.valor, 0.001)
        assertEquals(11.9, o.viagemKm, 0.001)
        assertEquals(1, o.paradas)
        val r = Avaliador.avaliar(o, Config(luxo = true), LocalDateTime.of(2026, 9, 27, 23, 35))
        assertEquals(listOf("1 parada"), r.avisos)
    }

    @Test
    fun semPegadinhaNaoTemAviso() {
        val r = Avaliador.avaliar(LeitorOferta.ler(print)!!, Config(), LocalDateTime.of(2026, 9, 24, 8, 46))
        assertEquals(0, r.oferta.paradas)
        assertTrue(r.avisos.isEmpty())
    }

    @Test
    fun avisosJuntosNumaLinha() {
        val o = Oferta(valor = 150.0, buscaKm = 3.5, buscaMin = 12, viagemKm = 30.0, viagemMin = 50, nota = 4.6, paradas = 2)
        val r = Avaliador.avaliar(o, Config(), LocalDateTime.of(2026, 9, 24, 8, 46))
        assertEquals(listOf("2 paradas", "busca longa", "nota 4,60", "viagem longa"), r.avisos)
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
    fun corridasReaisDe25DeSetembro() {
        val sextaDeManha = LocalDateTime.of(2026, 9, 25, 8, 37)
        // Táxi Promo R$ 32,24, 20 minutos (6.7 km): o taxímetro deu R$ 40,85.
        val semTransito = Avaliador.avaliar(Oferta(32.24, 0.4, 3, 6.7, 20, null), Config(), sextaDeManha)
        assertEquals(40.85, semTransito.taximetro, 40.85 * 0.05)
        assertEquals(Cor.VERMELHO, semTransito.cor)
        // R$ 36,71, 7,24 km que levaram 32 min: o taxímetro deu R$ 57,65.
        val comTransito = Avaliador.avaliar(Oferta(36.71, 1.0, 4, 7.24, 32, null), Config(), sextaDeManha)
        assertEquals(57.65, comTransito.taximetro, 57.65 * 0.05)
        assertEquals(Cor.VERMELHO, comTransito.cor)
    }

    @Test
    fun bandeira2() {
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 9, 24, 21, 0)))  // quinta à noite
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 9, 27, 12, 0)))  // domingo
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 11, 20, 12, 0))) // Consciência Negra
        assertFalse(Tarifa.bandeira2(LocalDateTime.of(2026, 9, 26, 12, 0))) // sábado de dia
        assertEquals(LocalDate.of(2026, 4, 5), Feriados.pascoa(2026))
        assertEquals(9.83 + 10 * 7.20 * 1.3, Tarifa.taximetro(10.0, 24, luxo = true, bandeira2 = true), 0.001)
    }

    @Test
    fun leNumeroDaTagDaRelease() {
        assertEquals(12, Atualizador.numeroDaTag("""{"url":"x","tag_name": "v1.12","name":"Corrida Verde 1.12"}"""))
        assertNull(Atualizador.numeroDaTag("""{"message":"Not Found"}"""))
    }

    @Test
    fun leOfertaDa99() {
        val o = LeitorOferta.ler(listOf("Táxi", "R\$13,01", "1,0x", "4,97", "125 corridas", "Perfil Premium",
            "5 min (705 m)", "Rua Dr. Araújo Castro, 194, Jardim Peri", "4 min (1,1 km)", "Rua Várzea Grande, 153, Jardim Peri"))!!
        assertEquals(13.01, o.valor, 0.001)
        assertEquals(0.705, o.buscaKm, 0.001)
        assertEquals(1.1, o.viagemKm, 0.001)
    }
}
