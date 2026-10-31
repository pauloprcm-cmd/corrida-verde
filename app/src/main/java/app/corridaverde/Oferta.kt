package app.corridaverde

import kotlin.math.abs

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
    // Acréscimo dentro do valor ("+R$ 3,75 incluído"), que no Black fica entre o valor e a busca.
    // Não confundir com o "Inclui um valor extra de R$ 4" do Táxi Promo, que vem depois do valor da corrida.
    private val INCLUIDO = Regex("""^\s*inclu[íi]d""", RegexOption.IGNORE_CASE)
    // "8 min (1.4 km)" ou, desde 26/10/2026, "8 min • 1.4 km".
    private val TRECHO = Regex(
        """((?:\d+\s*h(?:oras?|r)?\s*(?:e\s+)?)?(?:\d+\s*min(?:utos?)?)?)\s*(?:\(|[•·])\s*(\d+(?:[.,]\d+)?)\s*(km|m)\b\s*\)?""",
        RegexOption.IGNORE_CASE,
    )
    private val HORAS = Regex("""(\d+)\s*h""", RegexOption.IGNORE_CASE)
    private val MINUTOS = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
    private val NOTA = Regex("""\b([1-5][,.]\d{1,2})\s*\(\s*\d+\s*\)""")
    /** Textos depois da viagem que não são o endereço. */
    private val NAO_ENDERECO = Regex("""^(selecionar|aceitar|recusar|ver todas|combina|\d+ solicita|verificado|exclusivo)""", RegexOption.IGNORE_CASE)
    private val PARADAS = Regex("""\b(\d+)\s*paradas?\b""", RegexOption.IGNORE_CASE)

    /** Um trecho do cartão ("8 min • 1.4 km"); no print, o km pode estar com o ponto que o OCR perdeu de volta. */
    private class Trecho(val m: MatchResult, val km: Double, val min: Int)

    /** Lê os textos da tela (na ordem da árvore). Devolve null se não houver oferta. */
    fun ler(textos: List<String>): Oferta? = ler(textos, doPrint = false, totalJaSaiu = false)

    /**
     * [doPrint]: texto do OCR, que às vezes perde o ponto do km ("7.2 km" vira "72 km": 6 ofertas em 30/10/2026).
     * [totalJaSaiu]: a linha do total com "Est. de R$/km" já foi tirada antes.
     */
    private fun ler(textos: List<String>, doPrint: Boolean, totalJaSaiu: Boolean): Oferta? {
        val tela = textos.joinToString("\n").replace(' ', ' ').replace(' ', ' ')

        // Trechos "8 min (1.4 km)" ou "8 min • 1.4 km": o primeiro é a busca, os demais são a viagem (com paradas).
        val lidos = TRECHO.findAll(tela).filter { it.groupValues[1].isNotBlank() }
            .map { Trecho(it, km(it), minutos(it.groupValues[1])) }.toList()
        if (lidos.isEmpty()) return null
        val opcoes = if (doPrint) comPontoDeVolta(lidos) else listOf(lidos)
        // Desde 28/10/2026 o Táxi mostra o total ("8 min • 1.7 km") embaixo do valor, antes da busca: é a soma dos
        // outros trechos e não pode virar busca nem parada.
        val comTotal = opcoes.firstOrNull { it.size >= 3 && ehTotal(it[0], it.drop(1)) }
        val trechos = when {
            comTotal != null -> comTotal.drop(1)
            // No print, o trecho logo embaixo do valor (sem a linha "Est. de") é o total. Se a soma não fecha, o OCR
            // errou um número ou perdeu a linha da viagem (30/10/2026: o total virou busca e o aviso deu 624%).
            // Sem aviso é melhor que aviso errado: o próximo print tenta de novo.
            doPrint && !totalJaSaiu && logoAbaixoDoValor(tela, lidos[0]) &&
                (lidos.size >= 3 || TAXI.containsMatchIn(tela.substring(0, lidos[0].m.range.first))) -> return null
            doPrint -> opcoes.firstOrNull { plausivel(it) } ?: return null
            else -> lidos
        }
        if (trechos.size < 2) return null

        val faixa = FAIXA.findAll(tela).lastOrNull { it.range.first < trechos[0].m.range.first }
        // O valor da corrida é o último "R$" antes da busca que não seja R$/km nem o acréscimo "+R$ 3,75 incluído".
        val valores = VALOR.findAll(tela).filter {
            val depois = tela.substring(it.range.last + 1)
            !POR_UNIDADE.containsMatchIn(depois) && !INCLUIDO.containsMatchIn(depois) &&
                !tela.substring(0, it.range.first).trimEnd().endsWith('+')
        }.toList()
        val valor = faixa?.let { dinheiro(it.groupValues[1]) } ?: (valores.lastOrNull { it.range.first < trechos[0].m.range.first } ?: valores.firstOrNull())
            ?.let { dinheiro(it.groupValues[1]) } ?: return null

        val busca = trechos[0]
        val viagem = trechos.drop(1)
        return Oferta(
            valor = valor,
            buscaKm = busca.km,
            buscaMin = busca.min,
            viagemKm = viagem.sumOf { it.km },
            viagemMin = viagem.sumOf { it.min },
            nota = NOTA.find(tela)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull(),
            // A Uber escreve "1 parada"; sem o texto, cada trecho a mais depois da busca e da viagem é uma parada.
            paradas = PARADAS.find(tela)?.groupValues?.get(1)?.toInt() ?: maxOf(0, trechos.size - 2),
            valorMax = faixa?.let { dinheiro(it.groupValues[2]) },
            // O endereço vem logo depois do último trecho ("30 minutos (6.1 km)").
            destino = tela.substring(viagem.last().m.range.last + 1).lines().map { it.trim() }
                .firstOrNull { it.length >= 5 && it.any(Char::isLetter) && !NAO_ENDERECO.containsMatchIn(it) },
            // A categoria vem antes da busca; o endereço do destino pode ter "Black" ou "Comfort" no nome.
            grupo = Grupo.da(tela.substring(0, busca.m.range.first)),
        )
    }

    /**
     * A leitura como veio e, depois, com o ponto de volta nos km sem casa decimal ("72 km" → 7.2): a Uber sempre
     * escreve o km com uma casa, então "72" no print é o OCR que perdeu o ponto. Primeiro as que mudam menos.
     */
    private fun comPontoDeVolta(lidos: List<Trecho>): List<List<Trecho>> {
        val semPonto = lidos.indices.filter { i ->
            val n = lidos[i].m.groupValues[2]
            lidos[i].m.groupValues[3].equals("km", ignoreCase = true) && n.length in 2..3 && n.all(Char::isDigit)
        }.take(4)
        return (0 until (1 shl semPonto.size)).sortedBy { Integer.bitCount(it) }.map { trocados ->
            lidos.mapIndexed { i, t ->
                val k = semPonto.indexOf(i)
                if (k >= 0 && trocados and (1 shl k) != 0) Trecho(t.m, t.km / 10, t.min) else t
            }
        }
    }

    /** Nenhum trecho mais rápido que 90 km/h (72 km em 28 min é o ponto que sumiu, não a viagem). */
    private fun plausivel(trechos: List<Trecho>) = trechos.all { it.km <= 2 || (it.min > 0 && it.km / it.min * 60 <= 90) }

    /** Entre o valor e o trecho só há sobras do mapa (até 3 letras por linha, como "MI" e "PA"). */
    private fun logoAbaixoDoValor(tela: String, t: Trecho): Boolean {
        val antes = tela.substring(0, t.m.range.first)
        val valor = VALOR_DO_PRINT.findAll(antes).lastOrNull() ?: return false
        return antes.substring(valor.range.last + 1).lines().all { it.trim().length <= 3 }
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

    /**
     * Cartão da oferta da Uber lido do print pelo OCR (versão da Play: a Uber esconde o texto do cartão dos leitores
     * que não se declaram ferramenta de acessibilidade). Depois de consertar o que o OCR troca, é a leitura de sempre.
     */
    fun lerUberDoPrint(linhas: List<String>): Oferta? {
        // A linha do total ("32 min • 8.5 km • Est. de R$2,27/km") sai fora: com um trecho mal lido, a soma não
        // fecha e o total virava a busca.
        val tela = linhas.filterNot { EST_DO_TOTAL.containsMatchIn(it) }.joinToString("\n")
            .replace(REAL_DO_OCR) { "R\$" }
            .replace(HORA_DO_OCR) { m -> m.groupValues[1].map { DIGITO_DO_OCR[it] ?: it }.joinToString("") + " h e " }
            .replace(NUMERO_DO_OCR) { m -> m.value.map { DIGITO_DO_OCR[it] ?: it }.joinToString("") }
            .replace(PONTO_DO_OCR) { "${it.groupValues[1]} • " }
        return ler(listOf(tela), doPrint = true, totalJaSaiu = linhas.any { EST_DO_TOTAL.containsMatchIn(it) })
    }

    /** Como está o cartão da Uber no print, além da oferta. */
    enum class Cartao { NORMAL, EM_TRANSICAO, ENCERRADO }

    /**
     * Depois do "Tenho interesse" o cartão fica esmaecido ("Combinação em andamento") e o OCR erra os números; quando
     * a oferta acaba ("não está mais disponível", "Encontramos outro motorista") o texto velho ainda aparece.
     */
    fun cartaoDoPrint(linhas: List<String>): Cartao {
        val tela = linhas.joinToString(" ")
        return when {
            ENCERRADO.containsMatchIn(tela) -> Cartao.ENCERRADO
            EM_TRANSICAO.containsMatchIn(tela) -> Cartao.EM_TRANSICAO
            else -> Cartao.NORMAL
        }
    }

    /** Os valores em R$ do print, para saber se o cartão na tela ainda é o da oferta do aviso. */
    fun valoresDoPrint(linhas: List<String>): List<Double> =
        VALOR_DO_PRINT.findAll(linhas.joinToString("\n").replace(REAL_DO_OCR) { "R\$" })
            .mapNotNull { runCatching { dinheiro(it.groupValues[1]) }.getOrNull() }.toList()

    private val CORTADO = Regex("""\s*\S*(\.{3}|…)$""")
    // O "•" entre o tempo e a distância ("6 min • 1.4 km") às vezes sai do OCR como ".", "-", "*" ou some.
    private val EST_DO_TOTAL = Regex("""\bEst\.?\s*de\b""", RegexOption.IGNORE_CASE)
    // Número antes de "min" ou "km" com letra no lugar de algarismo ("1l min", "l.4 km"); precisa ter um algarismo.
    private val NUMERO_DO_OCR = Regex("""(?<![\w.,])(?=[\dlI|O.,]*\d)[\dlI|O]+(?:[.,][\dlI|O]+)?(?=\s*(?:min|k?m\b))""")
    // "1 h e 3 min" sai do OCR como "lhe3min" ou "1he 3 min".
    private val HORA_DO_OCR = Regex("""(?<!\w)([\dlI|]{1,2})\s*h\s*(?:e\s*)?(?=\d+\s*min)""")
    private val DIGITO_DO_OCR = mapOf('l' to '1', 'I' to '1', '|' to '1', 'O' to '0')
    private val PONTO_DO_OCR = Regex("""(min(?:utos?)?)\s*[.\-·*°+]?\s*(?=\d+(?:[.,]\d+)?\s*k?m\b)""", RegexOption.IGNORE_CASE)

    private val REAL_DO_OCR = Regex("""\bR\s?[S5]\s?(?=\d)""")
    /** "R$ 34,90" ou "R$ 24" (a faixa do Táxi vem sem centavos). */
    private val VALOR_DO_PRINT = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*(?:,\d{2})?)""")
    private val TAXI = Regex("""\bt[áa]xi\b""", RegexOption.IGNORE_CASE)
    private val EM_TRANSICAO = Regex("""combina[çc][ãa]o em andamento""", RegexOption.IGNORE_CASE)
    private val ENCERRADO = Regex("""n[ãa]o est[áa] mais dispon[íi]vel|encontramos outro motorista""", RegexOption.IGNORE_CASE)
    private val NOTA_99 = Regex("""\b([1-5][,.]\d{2})\D{0,4}\d+\s*corridas""", RegexOption.IGNORE_CASE)

    /** O trecho é a soma dos outros (a Uber arredonda cada um para 0,1 km e para o minuto). */
    private fun ehTotal(total: Trecho, resto: List<Trecho>) =
        abs(total.km - resto.sumOf { it.km }) <= 0.05 * resto.size + 0.01 && abs(total.min - resto.sumOf { it.min }) <= resto.size

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
