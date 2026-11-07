package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TiposGastoTest {
    private fun h(dia: Int, hora: Int) = LocalDateTime.of(2026, 11, dia, hora, 0)
    private fun outro(dia: Int, valor: Double, nome: String) = Gasto(h(dia, 12), Tipo.OUTRO, valor, descricao = nome)

    @Test
    fun gastoComNomeVoltaIgualDoArquivo() {
        val g = outro(5, 45.0, "Dentista")
        assertEquals(g, Gastos.deLinha(Gastos.paraLinha(g)))
        assertEquals("Dentista", g.nome)
        // Linha de antes dos tipos próprios (6 colunas) continua valendo.
        val antiga = Gastos.deLinha("2026-10-01T08:00\tOUTRO\t30.0\t\t\t0")!!
        assertEquals("", antiga.descricao)
        assertEquals("Outro", antiga.nome)
        // Tabulação no nome não quebra o arquivo.
        assertEquals("Escola dos filhos", Gastos.deLinha(Gastos.paraLinha(outro(5, 1.0, "Escola\tdos filhos")))!!.descricao.replace("  ", " "))
    }

    @Test
    fun listaDeCadaMotorista() {
        val gastos = listOf(outro(1, 10.0, "Padaria"), outro(2, 300.0, "Supermercado"), outro(3, 80.0, "supermercado "),
            Gasto(h(3, 8), Tipo.GASOLINA, 100.0))
        val doArquivo = listOf(TipoProprio("Supermercado", pessoal = true), TipoProprio("Academia", escondido = true))
        val tipos = TiposGasto.juntar(doArquivo, gastos)
        assertEquals(listOf("Supermercado", "Academia", "Padaria"), tipos.map { it.nome })
        // Mais usado primeiro; o escondido some da lista.
        assertEquals(listOf("Supermercado", "Padaria"), TiposGasto.naLista(tipos, gastos).map { it.nome })
        assertEquals("Supermercado", TiposGasto.achar(tipos, "SUPERMERCADO")?.nome)
    }

    @Test
    fun reconheceNaFala() {
        val tipos = listOf(TipoProprio("Café"), TipoProprio("Café da manhã"), TipoProprio("Dentista"))
        assertEquals("Dentista", TiposGasto.naFala(tipos, "gastei 45 reais no dentista")?.nome)
        assertEquals("Café da manhã", TiposGasto.naFala(tipos, "paguei 18 no café da manhã")?.nome)
        assertNull(TiposGasto.naFala(tipos, "gastei 20 no dentinho"))
        assertEquals("Dentista", TiposGasto.sugerirNome("gastei 45 reais no dentista"))
        assertEquals("Escola dos filhos", TiposGasto.sugerirNome("paguei 450 da escola dos filhos"))
        assertEquals("Farmácia", TiposGasto.sugerirNome("gastei 30 reais na farmácia"))
        assertNull(TiposGasto.sugerirNome("gastei 45 reais"))
    }

    @Test
    fun pessoalFicaForaDoSobrou() {
        val gastos = listOf(Gasto(h(5, 8), Tipo.GASOLINA, 60.0), outro(5, 85.0, "Supermercado"), outro(5, 45.0, "Dentista"),
            outro(5, 10.0, "Lanche"))
        val pessoais = setOf("supermercado", "dentista")
        val dia = LocalDate.of(2026, 11, 5)
        val b = Dinheiro.balanco(listOf(Corrida(h(5, 10), "Uber", 250.0)), emptyList(), gastos, dia, dia, pessoais)
        assertEquals(70.0, b.gastou, 0.001)   // gasolina + lanche (tipo próprio do trabalho)
        assertEquals(180.0, b.sobrou, 0.001)
        assertEquals(130.0, b.pessoal, 0.001)
        assertEquals(50.0, b.noBolso, 0.001)
        assertEquals(60.0, b.combustivel, 0.001)
        assertEquals(mapOf("Gasolina" to 60.0, "Lanche" to 10.0), b.porTipo)
        assertEquals(180.0, b.sobrouPorDia[dia]!!, 0.001)
        assertTrue(TiposGasto.ehPessoal(gastos[1], pessoais))
        assertFalse(TiposGasto.ehPessoal(gastos[0], pessoais))
    }
}
