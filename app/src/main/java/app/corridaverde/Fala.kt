package app.corridaverde

/** Um ganho dito por voz. [total] = o total do dia daquele app (substitui o que já tinha). */
data class Ganho(val app: String?, val valor: Double?, val total: Boolean)

/**
 * Decide se a fala do botão "Registrar por voz" é um gasto ou um ganho, sem frase pronta:
 * procura palavras de gasto, o nome de um app e palavras de ganho, em qualquer ordem.
 * Na dúvida fica gasto, como era antes; o motorista troca no formulário.
 */
object LeitorFala {
    val APPS = listOf("Uber", "99", "inDrive", "Cabify", "iFood", "Lalamove", "Loggi", "Rappi", "Táxi", "Particular")

    private val NOMES = listOf(
        "Uber" to Regex("""\bu+ber\b|\bguber\b"""),
        // "99" é app só depois de "da", "na", "pela"… ou no começo; "noventa e nove" por extenso é sempre o app.
        "99" to Regex("""(^|\b(?:da|na|no|do|pela|pelo|a|app|aplicativo|corridas?|viagens?)\s+)99\b|noventa e nove|\bnove nove\b"""),
        "inDrive" to Regex("""\bin ?drive\b|\bindriver?\b"""),
        "Cabify" to Regex("""\bcabify\b|\bcabfy\b"""),
        "iFood" to Regex("""\bi ?food\b"""),
        "Lalamove" to Regex("""\blalamove\b"""),
        "Loggi" to Regex("""\blog+i\b"""),
        "Rappi" to Regex("""\brap+i\b"""),
        "Táxi" to Regex("""\btaxi\b|\btaximetro\b|\bbandeirada\b"""),
        "Particular" to Regex("""\bparticular\b|\bpor fora\b"""),
    )
    private val GASTO = Regex("""\b(gastei|gasto|paguei|pagar|abasteci|abastecer|comprei|custou|despesa)""")
    private val GANHO = Regex("""\b(ganhei|ganho|ganhos|fatur\w*|recebi|receb\w*|corridas?|viagens?|entrou|lucrei|rendeu|fiz|tirei|bati|fechei|fechou)\b""")
    private val TOTAL = Regex("""\b(total|faturei|faturamento|fechei|fechou|no dia|do dia|dia todo|ate agora|hoje deu)\b""")
    private val SEM_TOTAL_NA_TELA = listOf("Táxi", "Particular")
    private val CORRIDA = Regex("""\b(corridas?|viagens?|entrega)\b""")
    private val REAIS_E_CENTAVOS = Regex("""(\d+)\s*(?:reais\s*)?e\s*(\d{1,2})\s*(?:centavos)?\b""")
    private val NUMERO = Regex("""\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:[.,]\d+)?""")
    private val DEPOIS_CONTAGEM = Regex("""^\s*(corridas?|viagens?|horas?|h\b|min|km|quilometros?)""")
    private val MARCADO = Regex("""^\s*(reais|real|conto|contos|pila)\b""")

    fun ehGanho(fala: String): Boolean {
        val t = normal(fala)
        if (GASTO.containsMatchIn(t) || LeitorGasto.TIPOS.any { it.second.containsMatchIn(t) }) return false
        return app(t) != null || GANHO.containsMatchIn(t)
    }

    /** Ex.: "faturei 240 na Uber", "ganhei 346 na 99" (total do dia), "corrida da 99, 23 e 50" (soma uma corrida). */
    fun lerGanho(fala: String): Ganho {
        val t = normal(fala)
        val app = app(t)
        // O "99" que é o nome do app não é o valor.
        val semApp = if (app == "99") NOMES.first { it.first == "99" }.second.replace(t) { " ${it.groupValues[1].trim()} app " } else t
        // Com o nome do app, o motorista fala o que a tela dele mostra, que já é a soma do dia: "ganhei 146
        // na 99" de manhã e "ganhei 346 na 99" à tarde dão 346, não 492. Soma quando diz "corrida"/"viagem",
        // quando não diz o app (passageiro de rua) e no táxi e na particular, que não têm tela com o total.
        val total = app != null && app !in SEM_TOTAL_NA_TELA && (TOTAL.containsMatchIn(t) || !CORRIDA.containsMatchIn(t))
        return Ganho(app, valor(semApp), total)
    }

    private fun app(t: String) = NOMES.firstOrNull { it.second.containsMatchIn(t) }?.first

    /** O valor de uma fala qualquer ("recibo de 23 e 50…"). */
    fun valorDaFala(fala: String) = valor(normal(fala))

    private fun valor(t: String): Double? {
        REAIS_E_CENTAVOS.find(t)?.let { m -> return m.groupValues[1].toDouble() + m.groupValues[2].padEnd(2, '0').toDouble() / 100 }
        val numeros = NUMERO.findAll(t).filter { !DEPOIS_CONTAGEM.containsMatchIn(t.substring(it.range.last + 1)) }.toList()
        val m = numeros.firstOrNull { MARCADO.containsMatchIn(t.substring(it.range.last + 1)) || t.substring(0, it.range.first).trimEnd().endsWith("r$") }
            ?: numeros.firstOrNull() ?: return null
        return LeitorGasto.numero(m.value)
    }

    private fun normal(fala: String) = LeitorGasto.semAcento(fala.lowercase()).replace(' ', ' ')
}
