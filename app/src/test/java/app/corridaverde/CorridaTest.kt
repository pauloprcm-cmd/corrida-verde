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
    fun sairDoModoDestinoNaoEFimDeCorrida() {
        val a = AcompanhaCorrida("Uber")
        a.oferta(oferta(23.06), t0)
        assertNull(a.tela(listOf("Ignorar", "Encerrar o modo \"Destino\"?", "Se continuar, você sairá do modo \"Destino\"."), t0.plusMinutes(5)))
        assertEquals(23.06, a.tela(listOf("Encerrar UberX"), t0.plusMinutes(20))!!.valor, 0.001)
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

    @Test
    fun leOTotalDoDiaNaTelaInicialDaUber() {
        val t = TotaisUber.ler(listOf("Uber Driver", "Página inicial", "R$ 214,45", "Pesquisar locais"), t0)!!
        assertEquals(214.45, t.valor, 0.001)
        assertEquals(1234.5, TotaisUber.ler(listOf("Página inicial", "R$ 1.234,50"), t0)!!.valor, 0.001)
        // O cartão de ganhos e a última viagem não são o total do topo.
        assertNull(TotaisUber.ler(listOf("VER PROGRESSO", "R$ 214,45"), t0))
        // Modo de privacidade: o topo não tem valor, o cartão de hoje tem.
        assertNull(TotaisUber.ler(listOf("Modo de privacidade", "Página inicial", "Pesquisar locais"), t0))
        val cartao = listOf("HOJE", "8 viagens concluídas", "16 pontos", "VER RESUMO SEMANAL", "VER PROGRESSO", "R$ 267,07")
        assertEquals(267.07, TotaisUber.ler(cartao, t0)!!.valor, 0.001)
        assertNull(TotaisUber.ler(listOf("Página inicial", "Pesquisar locais", "Última viagem de Black", "R$ 18,20"), t0))
    }

    @Test
    fun totalDaUberSubstituiSessoesECorridasAntesDele() {
        val dia = LocalDate.of(2026, 9, 29)
        val sessoes = listOf(Sessao(LocalDateTime.of(2026, 9, 29, 8, 0), LocalDateTime.of(2026, 9, 29, 9, 0), 40.0, 2))
        val corridas = listOf(
            Corrida(LocalDateTime.of(2026, 9, 29, 10, 0), "Uber", 20.0),
            Corrida(LocalDateTime.of(2026, 9, 29, 13, 0), "Uber", 25.0),
            Corrida(LocalDateTime.of(2026, 9, 29, 11, 0), "99", 13.01),
        )
        val total = TotalUber(LocalDateTime.of(2026, 9, 29, 12, 0), 100.0)
        assertEquals(100.0 + 25.0 + 13.01, Ganhos.doDia(sessoes, corridas, dia, total), 0.001)
    }
}
