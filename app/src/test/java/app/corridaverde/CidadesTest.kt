package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CidadesTest {
    private fun cidade(nome: String) = Cidades.TODAS.first { it.nome == nome }

    @Test
    fun todasAsTabelasFazemSentido() {
        Cidades.TODAS.forEach { c ->
            val t = c.tabela
            listOfNotNull(t.comum, t.especial).forEach { v ->
                assertTrue(c.nome, v.completo)
                assertTrue("${c.nome}: bandeira 2 mais barata", v.km2 >= v.km1)
                assertTrue("${c.nome}: km fora do normal", v.km1 in 2.0..8.0)
            }
            assertTrue(c.nome, t.b2De in 12..23 && t.b2Ate in 0..11)
        }
        assertEquals("cidade repetida", Cidades.TODAS.size, Cidades.TODAS.map { it.chave }.toSet().size)
    }

    @Test
    fun procuraSemAcento() {
        assertEquals(listOf("São Luís", "São Paulo"), Cidades.procurar("sao").map { it.nome })
        assertEquals("Niterói", Cidades.procurar("niteroi").single().nome)
        assertEquals("Belo Horizonte", Cidades.pelaLocalidade("belo horizonte")?.nome)
        assertNull(Cidades.pelaLocalidade("Osasco"))
    }

    @Test
    fun saoPauloContinuaIgual() {
        // Quem ainda não escolheu a cidade fica com a tabela de SP, como antes.
        val sp = Cidades.SAO_PAULO.tabela
        assertEquals(sp, Config().tabela)
        assertEquals(6.55 + 10 * 4.80, Tarifa.taximetro(10.0, 0, sp, luxo = false, bandeira2 = false), 0.001)
        assertEquals(6.55 + 10 * 4.80 * 1.3, Tarifa.taximetro(10.0, 0, sp, luxo = false, bandeira2 = true), 0.001)
        // Cidade sem segunda categoria: o "luxo" guardado não muda nada.
        val gru = cidade("Guarulhos").tabela
        assertEquals(6.29 + 10 * 4.47, Tarifa.taximetro(10.0, 0, gru, luxo = true, bandeira2 = false), 0.001)
    }

    @Test
    fun bandeira2DeCadaCidade() {
        val sabadoTarde = LocalDateTime.of(2026, 11, 7, 15, 0)
        val bh = cidade("Belo Horizonte")
        assertTrue(Tarifa.bandeira2(sabadoTarde, bh)) // BH: sábado a partir das 14h
        assertFalse(Tarifa.bandeira2(sabadoTarde, Cidades.SAO_PAULO)) // SP: sábado de dia é bandeira 1
        assertFalse(Tarifa.bandeira2(LocalDateTime.of(2026, 11, 5, 21, 0), bh))      // BH: quinta 21h ainda bandeira 1
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 11, 5, 21, 0), Cidades.SAO_PAULO))
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 12, 2, 10, 0), bh))       // dezembro inteiro
        val rio = cidade("Rio de Janeiro")
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 1, 20, 10, 0), rio))     // São Sebastião
        assertFalse(Tarifa.bandeira2(LocalDateTime.of(2026, 1, 26, 10, 0), rio))
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 2, 17, 10, 0), rio))     // terça de Carnaval
        val df = cidade("Brasília")
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 11, 7, 9, 0), df))       // DF: sábado o dia todo
        // Cidade digitada pelo taxista (fora da base): sempre bandeira 1, nem à noite nem no domingo.
        assertFalse(Tarifa.bandeira2(LocalDateTime.of(2026, 11, 5, 23, 0), null))
        assertFalse(Tarifa.bandeira2(LocalDateTime.of(2026, 11, 8, 12, 0), null))
    }

    @Test
    fun tabelaCorrigidaVale() {
        // O taxista corrigiu a bandeirada em Ajustes: o taxímetro usa a dele, não a da base.
        val minha = Cidades.SAO_PAULO.tabela.let { it.copy(comum = it.comum.copy(bandeirada = 7.00)) }
        val cfg = Config(perfil = Config.TAXI, cidade = "São Paulo/SP", tabela = minha)
        val r = Avaliador.avaliar(Oferta(30.0, 0.5, 3, 5.0, 16, null), cfg, LocalDateTime.of(2026, 11, 5, 10, 0))
        assertEquals(7.00 + 5 * 4.80, r.taximetro, 0.01)
    }
}
