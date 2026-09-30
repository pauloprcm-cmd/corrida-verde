package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTest {
    @Test
    fun leRadarDoWaze() {
        val r = LeitorRadar.ler(listOf("340 m", "Siga em frente", "Vd. João Julião da Costa Aguiar", "31", "km/h", "60", "Radar de velocidade em 210 m", "17:10"))!!
        assertEquals(210, r.metros)
        assertEquals("velocidade", r.tipo)
    }

    @Test
    fun leRadarDa99ComLimite() {
        val r = LeitorRadar.ler(listOf("2,9 km", "Av. Deputado Emílio Carlos", "50", "Limite", "9 min", "3,6 km", "Radar de semáforo e velocidade a 286 m"))!!
        assertEquals(286, r.metros)
        assertEquals(50, r.limite)
        assertEquals("semáforo e velocidade", r.tipo)
    }

    @Test
    fun leTextoQuebradoEmKm() {
        val r = LeitorRadar.ler(listOf("Radar de velocidade", "em 1,2 km", "Limite de velocidade 60"))!!
        assertEquals(1200, r.metros)
        assertEquals(60, r.limite)
    }

    @Test
    fun ignoraORadarDeViagensDaUber() {
        assertNull(LeitorRadar.ler(listOf("50", "LIMITE", "Radar de Viagens", "0", "12 min", "3,9 km")))
        assertNull(LeitorRadar.ler(listOf("Avenida Inajar de Souza", "1.0 km")))
    }

    @Test
    fun somSoNoRadarNovo() {
        val a = AcompanhaRadar()
        assertTrue(a.visto(Radar(300, 60, "velocidade"), 0))
        assertFalse(a.visto(Radar(250, 60, "velocidade"), 1_000))
        assertFalse(a.visto(Radar(40, 60, "velocidade"), 8_000))
        // Passou um radar e já apareceu o próximo, mais longe.
        assertTrue(a.visto(Radar(700, 60, "velocidade"), 9_000))
        // Mesmo radar, mas o aviso ficou sumido por mais de 15 s.
        assertTrue(a.visto(Radar(650, 60, "velocidade"), 30_000))
    }
}
