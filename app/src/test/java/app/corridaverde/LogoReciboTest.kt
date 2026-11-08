package app.corridaverde

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoReciboTest {
    @Test
    fun quadradaFicaComoSempre() {
        assertEquals(54f, LogoRecibo.largura(400, 400, 54f, 54f, 200f), 0.01f)
        assertEquals(54f, LogoRecibo.largura(300, 500, 54f, 54f, 200f), 0.01f)
    }

    @Test
    fun compridaGanhaLarguraAteOLimite() {
        assertEquals(130f, LogoRecibo.largura(1045, 220, 54f, 54f, 200f), 0.01f)
        assertEquals(81f, LogoRecibo.largura(300, 200, 54f, 54f, 200f), 0.01f)
    }

    @Test
    fun naoPassaPorCimaDoTexto() {
        assertEquals(90f, LogoRecibo.largura(1045, 220, 54f, 54f, 90f), 0.01f)
        assertEquals(54f, LogoRecibo.largura(1045, 220, 54f, 54f, 10f), 0.01f)
    }

    @Test
    fun recortaAMargemBranca() {
        val b = 0xFFFFFFFF.toInt()
        val p = 0xFF000000.toInt()
        val px = intArrayOf(
            b, b, b, b,
            b, p, p, b,
            b, b, b, b,
        )
        assertArrayEquals(intArrayOf(1, 1, 3, 2), LogoRecibo.recorte(px, 4, 3))
    }

    @Test
    fun imagemTodaBrancaNaoRecorta() {
        assertNull(LogoRecibo.recorte(IntArray(4) { 0xFFFFFFFF.toInt() }, 2, 2))
    }
}
