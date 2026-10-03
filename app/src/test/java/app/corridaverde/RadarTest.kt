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
    fun leRadarSemaforicoDoWaze() {
        // Textos do diagnóstico de 03/10/2026: sem o "de" depois de "Radar".
        val a = LeitorRadar.ler(listOf("Radar semafórico em 240 m"))!!
        assertEquals(240, a.metros)
        assertEquals("semafórico", a.tipo)
        val b = LeitorRadar.ler(listOf("Av. Inajar de Souza", "Radar semafórico e velocidade em 20 m"))!!
        assertEquals(20, b.metros)
        assertEquals("semafórico e velocidade", b.tipo)
        // Sem distância (o Waze também escreve assim) não dá para alertar.
        assertNull(LeitorRadar.ler(listOf("Radar semafórico e velocidade")))
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

    @Test
    fun mapsIgnoraPuloEContinuaSemAviso() {
        val e = EstimaRadar()
        val radar = { m: Int -> Radar(m, null, "velocidade") }
        assertEquals(550, e.atualizar(radar(550), 44, 0)!!.metros)
        // 7 s depois o Maps diz 80 m, mas a 44 km/h só deu para andar ~86 m.
        val pulo = e.atualizar(radar(80), 45, 7_000)!!.metros
        assertTrue(pulo in 450..480)
        // O aviso some e o carro para no farol: o alerta fica e a distância não anda.
        e.atualizar(null, 4, 8_000)
        val parado = e.atualizar(null, 4, 20_000)!!.metros
        assertTrue(parado in 430..470)
        // Volta a andar a 36 km/h (10 m/s): ~45 s depois passou do radar.
        assertTrue(e.atualizar(null, 36, 21_000) != null)
        assertTrue(e.atualizar(null, 36, 60_000) != null)
        assertNull(e.atualizar(null, 36, 70_000))
    }

    @Test
    fun mapsAceitaLeituraCoerenteEOutroRadar() {
        val e = EstimaRadar()
        e.atualizar(Radar(850, null, "velocidade"), 36, 0)
        assertEquals(750, e.atualizar(Radar(750, null, "velocidade"), 36, 10_000)!!.metros)
        // Outro radar bem mais longe que a conta.
        assertEquals(1500, e.atualizar(Radar(1500, null, "velocidade"), 36, 11_000)!!.metros)
    }

    @Test
    fun mapsDesisteSemAvisoPorMuitoTempo() {
        val e = EstimaRadar()
        e.atualizar(Radar(500, null, "velocidade"), 0, 0)
        assertTrue(e.atualizar(null, 0, 80_000) != null)
        assertNull(e.atualizar(null, 0, 91_000))
    }

    @Test
    fun leVelocidadeDoMaps() {
        assertEquals(44, LeitorVelocidade.ler(listOf("Radar de velocidade em 550 m", "Velocidade atual 44 quilômetros por hora")))
        assertNull(LeitorVelocidade.ler(listOf("44", "km/h")))
    }
}
