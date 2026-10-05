package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class PerfilAppTest {
    private val app = Config(perfil = Config.APP)
    private val segunda = LocalDateTime.of(2026, 10, 5, 14, 0)

    private fun oferta(valor: Double, grupo: Grupo = Grupo.ECONOMICO, buscaKm: Double = 1.0) =
        Oferta(valor = valor, buscaKm = buscaKm, buscaMin = 3, viagemKm = 9.0, viagemMin = 20, nota = null, grupo = grupo)

    @Test
    fun corPeloRsPorKmDoGrupo() {
        // 10 km no total (1 de busca + 9 de viagem).
        assertEquals(Cor.VERDE, Avaliador.avaliar(oferta(20.0), app, segunda).cor)
        assertEquals(Cor.AMARELO, Avaliador.avaliar(oferta(18.0), app, segunda).cor)
        assertEquals(Cor.VERMELHO, Avaliador.avaliar(oferta(16.0), app, segunda).cor)
        // O mesmo R$ 2,00/km no Black fica vermelho: o mínimo do premium é maior.
        assertEquals(Cor.VERMELHO, Avaliador.avaliar(oferta(20.0, Grupo.PREMIUM), app, segunda).cor)
        assertEquals(Cor.VERDE, Avaliador.avaliar(oferta(33.0, Grupo.PREMIUM), app, segunda).cor)
    }

    @Test
    fun buscaLongaBaixaUmaCor() {
        // 4 km de busca + 9 de viagem = 13 km; R$ 30 dá R$ 2,31/km (verde), mas a busca passa de 3 km.
        assertEquals(Cor.AMARELO, Avaliador.avaliar(oferta(30.0, buscaKm = 4.0), app, segunda).cor)
    }

    @Test
    fun taxistaContinuaPeloTaximetro() {
        val taxi = Config(perfil = Config.TAXI)
        val r = Avaliador.avaliar(oferta(20.0), taxi, segunda)
        // 9 km em 20 min no comum, sem bandeira 2 e sem hora parada: R$ 6,55 + 9 × 4,80 = R$ 49,75.
        assertEquals(Cor.VERMELHO, r.cor)
        assertEquals(40, r.pct)
    }

    @Test
    fun grupoDaCategoriaNoCartao() {
        assertEquals(Grupo.PREMIUM, Grupo.da("Uber Black\nR$ 45,20"))
        assertEquals(Grupo.CONFORTO, Grupo.da("Business Comfort\nR$ 30,00"))
        assertEquals(Grupo.CONFORTO, Grupo.da("99Plus"))
        assertEquals(Grupo.ECONOMICO, Grupo.da("UberX\nR$ 12,00"))
        assertEquals(Grupo.ECONOMICO, Grupo.da("99Pop"))
    }

    @Test
    fun destinoComNomeDeCategoriaNaoMudaOGrupo() {
        val o = LeitorOferta.ler(listOf("UberX", "R$ 18,00", "5 min (1.2 km)", "20 min (8.8 km)", "Rua Black Hawk, 100, São Paulo"))!!
        assertEquals(Grupo.ECONOMICO, o.grupo)
    }
}
