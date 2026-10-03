package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixTest {
    @Test
    fun exemploDoManualDoBancoCentral() {
        assertEquals("29B1", BrCode.crc16("123456789"))
        assertEquals(
            "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR" +
                "5913Fulano de Tal6008BRASILIA62070503***63041D3D",
            BrCode.payload("123e4567-e12b-12d1-a456-426655440000", "Fulano de Tal", "BRASILIA", null),
        )
    }

    @Test
    fun qrComValorEContaDoMotorista() {
        val c = ContaPix(TipoChave.CELULAR, "(11) 99999-0000", "João da Silva", "São Paulo")
        assertEquals(
            "00020126360014br.gov.bcb.pix0114+5511999990000520400005303986540570.005802BR" +
                "5913JOAO DA SILVA6009SAO PAULO62070503***6304D0BC",
            BrCode.daConta(c, 70.0),
        )
    }

    @Test
    fun formatoDasChaves() {
        assertEquals("+5511999990000", ContaPix(TipoChave.CELULAR, "11 99999-0000").chaveFormatada())
        assertEquals("+5511999990000", ContaPix(TipoChave.CELULAR, "+55 11 99999-0000").chaveFormatada())
        assertEquals("12345678900", ContaPix(TipoChave.CPF, "123.456.789-00").chaveFormatada())
        assertNull(ContaPix(TipoChave.CPF, "123.456.789").chaveFormatada())
        assertEquals("joao@email.com", ContaPix(TipoChave.EMAIL, "Joao@Email.com").chaveFormatada())
        assertNull(ContaPix(TipoChave.ALEATORIA, "abc").chaveFormatada())
    }

    @Test
    fun falaDoPix() {
        listOf("gera um pix de 70 reais", "gerar QR code de 70", "QR Code de R$ 70,00", "cria chave pix de 70",
            "pix de 70", "cobrar 70 no Pix", "faz um Pix de 45 e 50").forEach { assertTrue(it, LeitorPix.ehPix(it)) }
        listOf("recebi 70 no pix", "cobrei 70 no pix", "paguei 30 no pix de lanche", "ganhei 200 na Uber")
            .forEach { assertFalse(it, LeitorPix.ehPix(it)) }
        assertEquals(70.0, LeitorPix.valor("gera um pix de 70 reais")!!, 0.001)
        assertEquals(45.5, LeitorPix.valor("faz um Pix de 45 e 50")!!, 0.001)
    }
}
