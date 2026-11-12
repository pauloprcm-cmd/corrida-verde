package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendenciasTest {
    @Test fun pedidoComHoraELugar() {
        val p = Pendencias.ler("PA 4B QTR(07:50) MELIA IBIRAPUERA - AVENIDA IBIRAPUERA, 2534 Dist.3.981 mts /// QTI Area 5A /// WPET")!!
        assertEquals("Indianópolis", p.pa?.nome)
        assertEquals("AVENIDA IBIRAPUERA, 2534", p.endereco)
        assertEquals("Vila Olímpia", p.destino.nome)
        assertEquals("→ Vila Olímpia · ~3 km", Pendencias.etiqueta(p, null))
    }

    @Test fun pedidoSemHoraEComQuebraDeLinha() {
        val p = Pendencias.ler("PA 7B AVENIDA ARMANDO\nFERRENTINI, 668 Dist.8.034 mts ///\nQTI Area 16 /// WPET")!!
        assertEquals("Aclimação", p.pa?.nome)
        assertEquals("AVENIDA ARMANDO FERRENTINI, 668", p.endereco)
        assertEquals("Ipiranga", p.destino.nome)
    }

    @Test fun semPagamentoNoFim() {
        val p = Pendencias.ler("PA 08 QTR(08:25) RUA ZACARIAS DE GOIS, 715 Dist.6.347 mts /// QTI Area 20C ///")!!
        assertEquals("Congonhas", p.pa?.nome)
        assertEquals("Parque da Cidade", p.destino.nome)
    }

    @Test fun codigosDeForaDeSp() {
        val p = Pendencias.ler("PA OSA HOSPITAL E MATERNIDADE SAO LUIZ OSASCO - AVENIDA MARECHAL RONDON, 299 Dist.22.339 mts /// QTI Area CRP /// WPET")!!
        assertEquals("Osasco", p.pa?.nome)
        assertEquals("AVENIDA MARECHAL RONDON, 299", p.endereco)
        assertEquals("Carapicuíba", p.destino.nome)
    }

    @Test fun codigoComEspaco() {
        val p = Pendencias.ler("PA 3A PTO RUA JAGUARIBE, 100 Dist.1.000 mts /// QTI Area STA A /// WPET")!!
        assertEquals("Rua Jaguaribe", p.pa?.nome)
        assertEquals("Utinga (Santo André)", p.destino.nome)
    }

    @Test fun naoEPedido() {
        assertNull(Pendencias.ler("PENDÊNCIAS"))
        assertNull(Pendencias.ler("PA 4B RUA X, 1 Dist.100 mts /// QTI Area ZZZ /// WPET"))
    }
}
