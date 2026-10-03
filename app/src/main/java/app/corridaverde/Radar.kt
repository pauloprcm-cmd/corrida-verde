package app.corridaverde

/** Um radar avisado pelo navegador: a distância até ele e, se aparecer, o limite da via. */
data class Radar(val metros: Int, val limite: Int?, val tipo: String)

object LeitorRadar {
    // "Radar de velocidade em 210 m" e "Radar semafórico e velocidade em 240 m" (Waze),
    // "Radar de semáforo e velocidade a 286 m" (99).
    private val RADAR = Regex("""radar((?: de)? [\p{L} ]{3,40}?)?\s+(?:em|a|à)\s+(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    // "50 Limite" (99 e Uber) ou "Limite de velocidade 60".
    private val LIMITE = Regex("""\b(\d{2,3})\s*(?:km/h\s*)?limite\b|\blimite(?: de velocidade)?:?\s*(\d{2,3})\b""", RegexOption.IGNORE_CASE)

    fun ler(textos: List<String>): Radar? {
        // O botão "Radar de Viagens" da Uber não é radar de trânsito.
        val tela = textos.joinToString(" ") { it.replace('\n', ' ') }.replace(Regex("Radar de Viagens", RegexOption.IGNORE_CASE), " ")
        val m = RADAR.find(tela) ?: return null
        val numero = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        val metros = if (m.groupValues[3].equals("km", ignoreCase = true)) (numero * 1000).toInt() else numero.toInt()
        if (metros > 3000) return null
        val limite = LIMITE.find(tela)?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() }
            ?.takeIf { it in 20..130 && it % 10 == 0 }
        return Radar(metros, limite, m.groupValues[1].trim().removePrefix("de ").trim().ifEmpty { "velocidade" })
    }
}

/** O alerta só aparece com o radar a esta distância ou menos, mesmo que o navegador avise antes. */
const val ALERTA_RADAR_METROS = 600

/** Decide quando o radar é outro (toca o som de novo) e quando é o mesmo chegando mais perto. */
class AcompanhaRadar {
    private var ultimo: Radar? = null
    private var hora = 0L

    /** Devolve true se é um radar novo. */
    fun visto(r: Radar, agoraMs: Long): Boolean {
        val antes = ultimo
        val novo = antes == null || agoraMs - hora > 15_000 || r.metros > antes.metros + 150
        ultimo = r
        hora = agoraMs
        return novo
    }
}
