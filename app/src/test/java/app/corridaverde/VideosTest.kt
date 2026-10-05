package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Test

class VideosTest {
    private fun v(chave: String, mb: Long, criado: Long) = Videos.Video(chave, mb * 1024 * 1024, criado)

    @Test
    fun apagaOsMaisAntigosAteCaberNoLimite() {
        val videos = listOf(v("c", 40, 3), v("a", 40, 1), v("b", 40, 2))
        assertEquals(listOf("a", "b"), Videos.quaisApagar(videos, limite = 50L * 1024 * 1024).map { it.chave })
    }

    @Test
    fun naoApagaNadaDentroDoLimite() {
        assertEquals(emptyList<Videos.Video>(), Videos.quaisApagar(listOf(v("a", 100, 1), v("b", 100, 2))))
    }

    @Test
    fun videoGuardadoNuncaEApagadoNemEntraNaConta() {
        val videos = listOf(v("c", 40, 3), v("a", 40, 1), v("b", 40, 2))
        // Sem o "a" (guardado), b + c = 80 MB: só o "b" precisa sair para caber em 50 MB.
        assertEquals(listOf("b"), Videos.quaisApagar(videos, limite = 50L * 1024 * 1024, guardados = setOf("a")).map { it.chave })
    }

    @Test
    fun escolhe480pOuOMenorAcimaDisso() {
        val tamanhos = listOf(1920 to 1080, 1280 to 720, 640 to 480, 320 to 240, 176 to 144)
        assertEquals(640 to 480, Videos.escolherTamanho(tamanhos))
        assertEquals(1280 to 720, Videos.escolherTamanho(listOf(1920 to 1080, 1280 to 720, 320 to 240)))
        assertEquals(320 to 240, Videos.escolherTamanho(listOf(176 to 144, 320 to 240)))
    }
}
