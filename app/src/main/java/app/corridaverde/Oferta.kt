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
    /** Oferta "Táxi" com faixa ("R$ 31 - R$ 46"): [valor] é o mínimo e este é o máximo. */
    val valorMax: Double? = null,
    /** Endereço do destino final, como a Uber escreve ("Ambience Vila Mariana, Vila Mariana, São Paulo"). */
    val destino: String? = null,
    /** Grupo da categoria escrita no cartão (UberX, Comfort, Black, 99Pop...), para o perfil de motorista de app. */
    val grupo: Grupo = Grupo.ECONOMICO,
) {
    val chave get() = "$valor|$valorMax|$buscaKm|$viagemKm"
}

/** As categorias da Uber e da 99 em três grupos, cada um com o seu mínimo de R$/km no perfil de motorista de app. */
enum class Grupo(val nome: String) {
    ECONOMICO("Econômico"), CONFORTO("Conforto"), PREMIUM("Premium");

    companion object {
        private val PREMIUM_NA_TELA = Regex("""\bblack\b|electric[- ]?pro""", RegexOption.IGNORE_CASE)
        private val CONFORTO_NA_TELA = Regex("""\bcomfort\b|\b99\s?plus\b""", RegexOption.IGNORE_CASE)

        /** Sem nome conhecido no cartão (UberX, 99Pop e o que não deu para ler), fica no econômico. */
        fun da(tela: String) = when {
            PREMIUM_NA_TELA.containsMatchIn(tela) -> PREMIUM
            CONFORTO_NA_TELA.containsMatchIn(tela) -> CONFORTO
            else -> ECONOMICO
        }
    }
}

object LeitorOferta {
    private val VALOR = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})""")
    // Categoria Táxi: "R$ 31 - R$ 46" (sem centavos). O valor final fica entre os dois.
    private val FAIXA = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*(?:,\d{2})?)\s*[-–—]\s*R\$\s*(\d{1,3}(?:\.\d{3})*(?:,\d{2})?)""")
    private val POR_UNIDADE = Regex("""^\s*/\s*(km|h|min)""", RegexOption.IGNORE_CASE)
    private val TRECHO = Regex(
        """((?:\d+\s*h(?:oras?|r)?\s*(?:e\s+)?)?(?:\d+\s*min(?:utos?)?)?)\s*\(\s*(\d+(?:[.,]\d+)?)\s*(km|m)\s*\)""",
        RegexOption.IGNORE_CASE,
    )
    private val HORAS = Regex("""(\d+)\s*h""", RegexOption.IGNORE_CASE)
    private val MINUTOS = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
    private val NOTA = Regex("""\b([1-5][,.]\d{1,2})\s*\(\s*\d+\s*\)""")
    /** Textos depois da viagem que não são o endereço. */
    private val NAO_ENDERECO = Regex("""^(selecionar|aceitar|recusar|ver todas|combina|\d+ solicita|verificado|exclusivo)""", RegexOption.IGNORE_CASE)
    private val PARADAS = Regex("""\b(\d+)\s*paradas?\b""", RegexOption.IGNORE_CASE)

    /** Lê os textos da tela (na ordem da árvore). Devolve null se não houver oferta. */
    fun ler(textos: List<String>): Oferta? {
        val tela = textos.joinToString("\n").replace(' ', ' ').replace(' ', ' ')

        // Trechos "8 min (1.4 km)": o primeiro é a busca, os demais são a viagem (com paradas).
        val trechos = TRECHO.findAll(tela).filter { it.groupValues[1].isNotBlank() }.toList()
        if (trechos.size < 2) return null

        val faixa = FAIXA.findAll(tela).lastOrNull { it.range.first < trechos[0].range.first }
        // O valor da corrida é o último "R$" antes da busca que não seja R$/km.
        val valores = VALOR.findAll(tela).filter { !POR_UNIDADE.containsMatchIn(tela.substring(it.range.last + 1)) }.toList()
        val valor = faixa?.let { dinheiro(it.groupValues[1]) } ?: (valores.lastOrNull { it.range.first < trechos[0].range.first } ?: valores.firstOrNull())
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
            valorMax = faixa?.let { dinheiro(it.groupValues[2]) },
            // O endereço vem logo depois do último trecho ("30 minutos (6.1 km)").
            destino = tela.substring(viagem.last().range.last + 1).lines().map { it.trim() }
                .firstOrNull { it.length >= 5 && it.any(Char::isLetter) && !NAO_ENDERECO.containsMatchIn(it) },
            // A categoria vem antes da busca; o endereço do destino pode ter "Black" ou "Comfort" no nome.
            grupo = Grupo.da(tela.substring(0, busca.range.first)),
        )
    }

    /**
     * Cartão da oferta da 99, lido do print pelo OCR (linhas de cima para baixo). O valor grande é o primeiro
     * R$: depois dele vêm o R$/km, o "R$ 4,02 a mais por corrida" e as contrapropostas do Negocia, que são maiores.
     * A nota vem como "4,92 · 127 corridas".
     */
    fun ler99(linhas: List<String>): Oferta? {
        // O OCR às vezes lê o "$" como S ou 5.
        val tela = linhas.joinToString("\n").replace(REAL_DO_OCR) { "R\$" }
        val base = ler(listOf(tela)) ?: return null
        val valor = VALOR.findAll(tela).firstOrNull { !POR_UNIDADE.containsMatchIn(tela.substring(it.range.last + 1)) }
            ?.let { dinheiro(it.groupValues[1]) } ?: return null
        val nota = NOTA_99.find(tela)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        // O destino ocupa até duas linhas depois da viagem e às vezes termina cortado ("Avenida Dep. Cantídio Samp...").
        val ultimoTrecho = TRECHO.findAll(tela).last { it.groupValues[1].isNotBlank() }
        val destino = tela.substring(ultimoTrecho.range.last + 1).lines().map { it.trim() }.filter { it.isNotEmpty() }
            .takeWhile { it.any(Char::isLetter) && "R$" !in it && !NAO_ENDERECO.containsMatchIn(it) }.take(2)
            .joinToString(" ").replace(CORTADO, "").trim().trimEnd(',').takeIf { it.length >= 5 }
        return base.copy(valor = valor, valorMax = null, nota = nota ?: base.nota, destino = destino)
    }

    private val CORTADO = Regex("""\s*\S*(\.{3}|…)$""")

    private val REAL_DO_OCR = Regex("""\bR\s?[S5]\s?(?=\d)""")
    private val NOTA_99 = Regex("""\b([1-5][,.]\d{2})\D{0,4}\d+\s*corridas""", RegexOption.IGNORE_CASE)

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
