package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CasaTest {
    private val pracaDaSe = Ponto(-23.5503, -46.6339)
    private val ibirapuera = Ponto(-23.5874, -46.6576)

    @Test
    fun distanciaEmLinhaReta() {
        val km = Casa.distanciaKm(pracaDaSe, ibirapuera)
        assertTrue(km in 4.5..4.9)
        assertEquals(0.0, Casa.distanciaKm(pracaDaSe, pracaDaSe), 0.0001)
    }

    @Test
    fun textoPertoELonge() {
        assertEquals("🏠 Perto de casa · 1,8 km", Casa.texto(1.83, 3.0))
        assertEquals("12 km de casa", Casa.texto(12.4, 3.0))
    }

    @Test
    fun consultaDoDestino() {
        assertEquals("Ambience Vila Mariana, Vila Mariana, São Paulo, Brasil", Casa.consulta("Ambience Vila Mariana,  Vila Mariana,\nSão Paulo"))
    }
}
