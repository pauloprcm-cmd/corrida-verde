package app.corridaverde

/**
 * A barra da Uber durante a corrida ("12 min", "2,4 km", "A caminho da última parada"). Chega pelo evento, sem
 * print, e serve para tirar menos prints enquanto falta muito para o fim (diagnóstico de 30/10/2026).
 */
object LeitorViagem {
    private val A_CAMINHO = Regex("""^a caminho d""", RegexOption.IGNORE_CASE)
    /** Com parada no meio, os minutos da barra são só até a parada. */
    private val PARADA_NO_MEIO = Regex("""primeira parada|pr[óo]xima parada""", RegexOption.IGNORE_CASE)
    private val TEMPO = Regex("""^(?:(\d+)\s*h\s*(?:e\s*)?)?(\d+)\s*min$""", RegexOption.IGNORE_CASE)
    private val FORA_DA_CORRIDA = Regex("""^(procurando viagens|você está online|como foi a viagem|avaliar usuário)""", RegexOption.IGNORE_CASE)

    /**
     * Minutos até o fim da corrida; [Int.MAX_VALUE] se ainda falta uma parada; 0 na tela de fora da corrida
     * (procurando viagens, avaliação do passageiro); null se o evento não diz nada disso.
     */
    fun minutosAteOFim(textos: List<String>): Int? {
        if (textos.any { FORA_DA_CORRIDA.containsMatchIn(it.trim()) }) return 0
        val aCaminho = textos.firstOrNull { A_CAMINHO.containsMatchIn(it.trim()) } ?: return null
        if (PARADA_NO_MEIO.containsMatchIn(aCaminho)) return Int.MAX_VALUE
        val t = textos.firstNotNullOfOrNull { TEMPO.find(it.trim()) } ?: return null
        return (t.groupValues[1].toIntOrNull() ?: 0) * 60 + t.groupValues[2].toInt()
    }
}
