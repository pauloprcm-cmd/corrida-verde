package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReciboTest {
    @Test
    fun falaCompleta() {
        val f = LeitorRecibo.ler("Recibo de 50 reais, da Avenida Paulista até a Vila Mariana, no Pix")
        assertEquals(50.0, f.valor!!, 0.001)
        assertEquals("Avenida Paulista", f.de)
        assertEquals("Vila Mariana", f.ate)
        assertEquals("Pix", f.pagamento)
        assertNull(f.passageiro)
    }

    @Test
    fun centavosERuaComDaNoNome() {
        val f = LeitorRecibo.ler("recibo de 23 e 50 da rua da Consolação até o aeroporto de Congonhas em dinheiro")
        assertEquals(23.5, f.valor!!, 0.001)
        assertEquals("Rua da Consolação", f.de)
        assertEquals("Aeroporto de Congonhas", f.ate)
        assertEquals("Dinheiro", f.pagamento)
    }

    @Test
    fun passageiroSemOrigem() {
        val f = LeitorRecibo.ler("recibo para a passageira Maria Souza de 45 reais até Pinheiros")
        assertEquals(45.0, f.valor!!, 0.001)
        assertEquals("Maria Souza", f.passageiro)
        assertNull(f.de)
        assertEquals("Pinheiros", f.ate)
    }

    @Test
    fun soValorEPagamento() {
        val f = LeitorRecibo.ler("recibo de 80 reais no cartão de crédito")
        assertEquals(80.0, f.valor!!, 0.001)
        assertEquals("Cartão de crédito", f.pagamento)
        assertNull(f.de)
        assertNull(f.ate)
        assertTrue(LeitorRecibo.ehRecibo("Recibo de 80"))
    }

    @Test
    fun valorPorExtenso() {
        assertEquals("cinquenta reais", Extenso.reais(50.0))
        assertEquals("cem reais", Extenso.reais(100.0))
        assertEquals("um real", Extenso.reais(1.0))
        assertEquals("vinte e um reais", Extenso.reais(21.0))
        assertEquals("cento e vinte e três reais e cinquenta centavos", Extenso.reais(123.5))
        assertEquals("setenta e cinco centavos", Extenso.reais(0.75))
        assertEquals("mil e cem reais", Extenso.reais(1100.0))
        assertEquals("mil duzentos e cinquenta reais", Extenso.reais(1250.0))
        assertEquals("dois mil reais", Extenso.reais(2000.0))
    }

    @Test
    fun fraseETextoDoRecibo() {
        val r = Recibo(42, LocalDateTime.of(2026, 10, 1, 21, 5), 50.0, "Maria Souza", "Avenida Paulista", "Vila Mariana", "Pix")
        assertEquals(
            "Recebi de Maria Souza a quantia de R$ 50,00 (cinquenta reais), referente a corrida de táxi em 01/10/2026 às 21:05, de Avenida Paulista até Vila Mariana.",
            Recibos.frase(r),
        )
        val t = Recibos.texto(r, Motorista("João da Silva", "ABC1D23", "12345"))
        assertTrue(t.startsWith("RECIBO DE TÁXI Nº 0042"))
        assertTrue("Táxi placa ABC1D23 · Alvará 12345" in t)
        assertEquals(r, Recibos.deLinha(Recibos.paraLinha(r)))
        assertEquals("Corrida de táxi\nDe: Avenida Paulista\nAté: Vila Mariana", Recibos.descricao(r))
        assertEquals("Corrida de táxi\nAté: Vila Mariana", Recibos.descricao(r.copy(de = "")))
        assertEquals("Corrida de táxi", Recibos.descricao(r.copy(de = "", ate = "")))
        assertEquals("Táxi do João", Motorista("João da Silva", fantasia = "Táxi do João").marca)
        assertEquals("João da Silva", Motorista("João da Silva").marca)
    }
}
