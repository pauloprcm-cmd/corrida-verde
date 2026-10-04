package app.corridaverde

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CamposTest {
    @Test
    fun nomeComMaiusculaNaoViraTecladoDeNumero() {
        assertFalse(ehDecimal(InputType.TYPE_TEXT_FLAG_CAP_WORDS))
        assertFalse(ehDecimal(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS))
        assertFalse(ehDecimal(InputType.TYPE_CLASS_NUMBER))
        assertTrue(ehDecimal(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL))
    }
}
