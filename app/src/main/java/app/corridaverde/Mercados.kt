package app.corridaverde

/**
 * Aviso de embarque em mercado: o passageiro sai com o carrinho e o motorista perde tempo carregando e descarregando.
 * Vale só para o embarque e só avisa, sem mudar a cor. Só funciona quando o app escreve o nome do lugar no endereço.
 */
object Mercados {
    /** Nomes que não se confundem com outra coisa: "Dia" e "Extra" ficam de fora de propósito (alarme falso). */
    private val CONHECIDOS = listOf(
        "atacadao" to "Atacadão",
        "assai" to "Assaí",
        "carrefour" to "Carrefour",
        "makro" to "Makro",
        "roldao" to "Roldão",
        "sams club" to "Sam's Club",
        "pao de acucar" to "Pão de Açúcar",
        "andorinha hiper" to "Andorinha Hiper",
        "giga atacadista" to "Giga Atacadista",
        "giga atacado" to "Giga Atacadista",
        "rede plus" to "Rede Plus",
        "cobal" to "Cobal",
        "supermercado" to "supermercado",
        "supermercados" to "supermercado",
        "hipermercado" to "hipermercado",
        "atacadista" to "atacadista",
        "atacado" to "atacado",
    )

    /** O começo do endereço quando é o nome da rua: "Rua Andorinha Hiper, 30" não é o mercado. */
    private val RUA = Regex("""^(rua|r|avenida|av|travessa|tv|alameda|al|praca|pca|estrada|estr|est|rodovia|rod|viela|largo)\.?\s+[^,\-–]*""")

    /** Os nomes que o motorista digitou, separados por vírgula ou em linhas. */
    fun proprios(texto: String): List<String> =
        texto.split(',', ';', '\n').map { it.trim() }.filter { it.length >= 3 }

    /** O nome do mercado para mostrar no aviso, ou null se o embarque não parece ser em mercado. */
    fun achar(embarque: String?, proprios: List<String> = emptyList()): String? {
        if (embarque.isNullOrBlank()) return null
        val lugar = normal(embarque).replace(RUA, "")
        proprios.firstOrNull { tem(lugar, normal(it)) }?.let { return it }
        return CONHECIDOS.firstOrNull { (termo, _) -> tem(lugar, termo) }?.second
    }

    private fun normal(s: String) = Cidades.semAcento(s).replace("'", "").replace("’", "").trim()

    /** Palavra inteira: "cobal" não acha "Cobalto". */
    private fun tem(lugar: String, termo: String) =
        termo.isNotEmpty() && Regex("(^|[^a-z0-9])" + Regex.escape(termo) + "($|[^a-z0-9])").containsMatchIn(lugar)
}
