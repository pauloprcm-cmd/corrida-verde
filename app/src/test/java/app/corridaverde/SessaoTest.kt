package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SessaoTest {
    private val agora = LocalDateTime.of(2026, 9, 28, 18, 12)

    @Test
    fun leResumoDaSessao() {
        val s = LeitorSessao.ler(
            listOf(
                "Resumo da sessão", "28 de set., 17:09 – 28 de set., 18:11", "R$ 59,67",
                "Viagens concluídas", "1", "Viagens oferecidas", "1",
                "Você não teve cancelamentos nesta sessão. Muito bem!", "Sua sessão (de 17:09 até 18:11)",
            ),
            agora,
        )!!
        assertEquals(LocalDateTime.of(2026, 9, 28, 17, 9), s.inicio)
        assertEquals(LocalDateTime.of(2026, 9, 28, 18, 11), s.fim)
        assertEquals(59.67, s.valor, 0.001)
        assertEquals(1, s.viagens)
    }

    @Test
    fun leComMilharETextosJuntos() {
        val s = LeitorSessao.ler(listOf("Resumo da sessão\n27 de set., 20:00 - 28 de set., 03:10\nR$ 1.234,50"), agora)!!
        assertEquals(LocalDateTime.of(2026, 9, 27, 20, 0), s.inicio)
        assertEquals(LocalDateTime.of(2026, 9, 28, 3, 10), s.fim)
        assertEquals(1234.50, s.valor, 0.001)
        assertNull(s.viagens)
    }

    @Test
    fun dezembroVistoEmJaneiroEhDoAnoAnterior() {
        val s = LeitorSessao.ler(listOf("Resumo da sessão", "31 de dez., 22:00 – 1 de jan., 01:00", "R$ 80,00"), LocalDateTime.of(2027, 1, 1, 1, 5))!!
        assertEquals(LocalDateTime.of(2026, 12, 31, 22, 0), s.inicio)
        assertEquals(LocalDateTime.of(2027, 1, 1, 1, 0), s.fim)
    }

    @Test
    fun ignoraOutrasTelas() {
        assertNull(LeitorSessao.ler(listOf("R$ 38,94", "7 min (1.5 km)", "24 minutos (7.7 km)"), agora))
        assertNull(LeitorSessao.ler(listOf("Resumo da sessão"), agora))
    }

    @Test
    fun somaAsSessoesDoDia() {
        val dia = LocalDate.of(2026, 9, 28)
        val sessoes = listOf(
            Sessao(LocalDateTime.of(2026, 9, 28, 15, 16), LocalDateTime.of(2026, 9, 28, 16, 0), 10.18, 1),
            Sessao(LocalDateTime.of(2026, 9, 28, 17, 9), LocalDateTime.of(2026, 9, 28, 18, 11), 59.67, 1),
            Sessao(LocalDateTime.of(2026, 9, 27, 17, 0), LocalDateTime.of(2026, 9, 27, 18, 0), 40.0, 1),
        )
        assertEquals(69.85, Sessoes.totalDoDia(sessoes, dia), 0.001)
        assertEquals(Sessao(sessoes[0].inicio, sessoes[0].fim, 10.18, 1), Sessoes.deLinha(Sessoes.paraLinha(sessoes[0])))
    }
}
