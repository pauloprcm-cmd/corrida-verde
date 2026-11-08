package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MercadosTest {
    @Test
    fun achaRedeConhecidaSemAcento() {
        assertEquals("Assaí", Mercados.achar("ASSAI Atacadista, Vila Maria, São Paulo"))
        assertEquals("Atacadão", Mercados.achar("Atacadão - Vila Guilherme"))
        assertEquals("Sam's Club", Mercados.achar("Sam's Club Tatuapé, São Paulo"))
    }

    @Test
    fun redesDaRegiaoDoPaulo() {
        assertEquals("Andorinha Hiper", Mercados.achar("Andorinha Hiper Center, Vila Nova Cachoeirinha"))
        assertEquals("Giga Atacadista", Mercados.achar("Giga Atacadista, Limão, São Paulo"))
        assertEquals("Rede Plus", Mercados.achar("Rede Plus Supermercados, Casa Verde"))
        assertEquals("Cobal", Mercados.achar("Cobal, Mandaqui"))
    }

    @Test
    fun nomeDeRuaNaoEMercado() {
        assertNull(Mercados.achar("Rua Andorinha Hiper, 30, Vila Nova"))
        assertNull(Mercados.achar("Av. Cobal, 100, Centro"))
        assertNull(Mercados.achar("Rua Penedo da Castanheira, 42, Vila Bela Vista"))
    }

    @Test
    fun mercadoDepoisDaRuaAindaConta() {
        assertEquals("Carrefour", Mercados.achar("Rua das Flores, 10, Carrefour Limão"))
    }

    @Test
    fun palavraInteira() {
        assertNull(Mercados.achar("Cobalto Autopeças, Lapa"))
        assertNull(Mercados.achar("Mercado Livre, Cajamar"))
    }

    @Test
    fun oNomeQueOMotoristaDigitou() {
        val meus = Mercados.proprios("Empório do Zé, Sacolão Bom Preço\nab")
        assertEquals(listOf("Empório do Zé", "Sacolão Bom Preço"), meus)
        assertEquals("Empório do Zé", Mercados.achar("EMPORIO DO ZE, Jaçanã", meus))
    }

    @Test
    fun semEmbarque() {
        assertNull(Mercados.achar(null))
    }
}
