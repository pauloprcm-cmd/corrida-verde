package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrimeiroUsoTest {
    @Test
    fun ficaADataMaisAntiga() {
        assertEquals("2026-10-01", Config.maisAntiga("2026-10-09", "2026-10-01"))
        assertEquals("2026-09-30", Config.maisAntiga("2026-09-30", "2026-10-01"))
    }

    @Test
    fun copiaSemDataNaoZeraAContagem() {
        assertEquals("2026-10-01", Config.maisAntiga("2026-10-01", null))
        assertEquals("2026-10-01", Config.maisAntiga(null, "2026-10-01"))
        assertEquals("2026-10-01", Config.maisAntiga("2026-10-01", "lixo"))
        assertNull(Config.maisAntiga(null, null))
    }
}
