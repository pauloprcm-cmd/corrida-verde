package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimpaAssinaturaTest {
    private fun cinza(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    /** Papel com luz desigual (mais escuro à direita) e um traço azul escuro no meio. */
    private fun foto(w: Int, h: Int, traco: Boolean): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (traco && y in 48..51 && x in 60..140) (0xFF shl 24) or (0x20 shl 16) or (0x28 shl 8) or 0x70
        else cinza(235 - x * 60 / w)
    }

    @Test
    fun recortaOTracoETiraOPapel() {
        val r = LimpaAssinatura.limpar(foto(200, 100, traco = true), 200, 100)
        assertNotNull(r)
        r!!
        // Só o traço com uma folguinha, não a folha inteira.
        assertTrue(r.largura in 81..100)
        assertTrue(r.altura in 4..20)
        // Canto do recorte é papel: transparente. O meio é tinta: opaco e escuro.
        assertEquals(0, r.pixels[0] ushr 24)
        val meio = r.pixels[(r.altura / 2) * r.largura + r.largura / 2]
        assertEquals(255, meio ushr 24)
        assertTrue((meio and 255) > (meio shr 16 and 255))
    }

    @Test
    fun papelEmBrancoNaoViraAssinatura() {
        assertNull(LimpaAssinatura.limpar(foto(200, 100, traco = false), 200, 100))
    }
}
