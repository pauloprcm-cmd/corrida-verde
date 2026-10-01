package app.corridaverde

/** Uma oferta lida da tela da Uber. Distâncias em km, tempos em minutos. */
data class Oferta(
    val valor: Double,
    val buscaKm: Double,
    val buscaMin: Int,
    val viagemKm: Double,
    val viagemMin: Int,
    val nota: Double?,
    val paradas: Int = 0,
) {
    val chave get() = "$valor|$buscaKm|$viagemKm"
}

object LeitorOferta {
    private val VALOR = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})""")
    private val POR_UNIDADE = Regex("""^\s*/\s*(km|h|min)""", RegexOption.IGNORE_CASE)
    private val TRECHO = Regex(
        """((?:\d+\s*h(?:oras?|r)?\s*(?:e\s+)?)?(?:\d+\s*min(?:utos?)?)?)\s*\(\s*(\d+(?:[.,]\d+)?)\s*(km|m)\s*\)""",
        RegexOption.IGNORE_CASE,
    )
    private val HORAS = Regex("""(\d+)\s*h""", RegexOption.IGNORE_CASE)
    private val MINUTOS = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
    private val NOTA = Regex("""\b([1-5][,.]\d{1,2})\s*\(\s*\d+\s*\)""")
    private val PARADAS = Regex("""\b(\d+)\s*paradas?\b""", RegexOption.IGNORE_CASE)

    /** Lê os textos da tela (na ordem da árvore). Devolve null se não houver oferta. */
    fun ler(textos: List<String>): Oferta? {
        val tela = textos.joinToString("\n").replace(' ', ' ').replace(' ', ' ')

        // Trechos "8 min (1.4 km)": o primeiro é a busca, os demais são a viagem (com paradas).
        val trechos = TRECHO.findAll(tela).filter { it.groupValues[1].isNotBlank() }.toList()
        if (trechos.size < 2) return null

        // O valor da corrida é o último "R$" antes da busca que não seja R$/km.
        val valores = VALOR.findAll(tela).filter { !POR_UNIDADE.containsMatchIn(tela.substring(it.range.last + 1)) }.toList()
        val valor = (valores.lastOrNull { it.range.first < trechos[0].range.first } ?: valores.firstOrNull())
            ?.let { dinheiro(it.groupValues[1]) } ?: return null

        val busca = trechos[0]
        val viagem = trechos.drop(1)
        return Oferta(
            valor = valor,
            buscaKm = km(busca),
            buscaMin = minutos(busca.groupValues[1]),
            viagemKm = viagem.sumOf { km(it) },
            viagemMin = viagem.sumOf { minutos(it.groupValues[1]) },
            nota = NOTA.find(tela)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull(),
            // A Uber escreve "1 parada"; sem o texto, cada trecho a mais depois da busca e da viagem é uma parada.
            paradas = PARADAS.find(tela)?.groupValues?.get(1)?.toInt() ?: maxOf(0, trechos.size - 2),
        )
    }

    private fun dinheiro(s: String) = s.replace(".", "").replace(',', '.').toDouble()

    private fun km(m: MatchResult): Double {
        val n = m.groupValues[2].replace(',', '.').toDouble()
        return if (m.groupValues[3].equals("m", ignoreCase = true)) n / 1000 else n
    }

    private fun minutos(s: String): Int {
        val h = HORAS.find(s)?.groupValues?.get(1)?.toInt() ?: 0
        val min = MINUTOS.find(s)?.groupValues?.get(1)?.toInt() ?: 0
        return h * 60 + min
    }
}
