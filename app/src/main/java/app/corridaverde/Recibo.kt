package app.corridaverde

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dados do motorista que vão no recibo. */
data class Motorista(
    val nome: String = "",
    val placa: String = "",
    val alvara: String = "",
    val documento: String = "",
    val telefone: String = "",
    val cidade: String = "São Paulo",
    /** Nome do táxi como aparece no topo do recibo ("Táxi do João"); vazio = usa o nome. */
    val fantasia: String = "",
    val email: String = "",
    val instagram: String = "",
) {
    /** O nome que vai em destaque no recibo. */
    val marca get() = fantasia.ifBlank { nome }

    val completo get() = nome.isNotBlank() && placa.isNotBlank()

    fun salvar(ctx: Context) {
        ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putString("nome", nome).putString("placa", placa).putString("alvara", alvara)
            .putString("documento", documento).putString("telefone", telefone).putString("cidade", cidade)
            .putString("fantasia", fantasia).putString("email", email).putString("instagram", instagram)
            .apply()
    }

    companion object {
        private const val ARQUIVO = "motorista"

        fun carregar(ctx: Context): Motorista {
            val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            fun s(k: String, d: String = "") = p.getString(k, d) ?: d
            return Motorista(
                s("nome"), s("placa"), s("alvara"), s("documento"), s("telefone"), s("cidade", "São Paulo"),
                s("fantasia"), s("email"), s("instagram"),
            )
        }
    }
}

/** Recibo de uma corrida fora da plataforma (rua, ponto, particular). [numero] 0 = ainda não emitido. */
data class Recibo(
    val numero: Int,
    val quando: LocalDateTime,
    val valor: Double,
    val passageiro: String = "",
    val de: String = "",
    val ate: String = "",
    val pagamento: String = "",
    val telefone: String = "",
    val email: String = "",
    /** Número do recibo que este corrige: recibo emitido não se edita, sai outro no lugar. */
    val substitui: Int? = null,
    /** CPF ou CNPJ de quem pagou (cliente fixo); vazio = não sai no recibo. */
    val documento: String = "",
    /** Recibo de cliente fixo: as corridas pagas de uma vez. Vazio = recibo de uma corrida só (de, até, quando). */
    val corridas: List<CorridaRecibo> = emptyList(),
) {
    val numeroTexto get() = String.format(Locale.ROOT, "%04d", numero)

    /** Recibo de cliente fixo, com a tabela de corridas. */
    val varias get() = corridas.isNotEmpty()

    companion object {
        /** O que cabe na folha A5 sem apertar a letra. */
        const val MAX_CORRIDAS = 8
    }
}

/** Um trajeto que o cliente fixo costuma fazer, com o preço combinado. Não tem data: ela entra no recibo. */
data class Trajeto(val tipo: String, val de: String, val ate: String, val valor: Double, val obs: String = "") {
    /** "(Ida) Residência → Aeroporto de Congonhas", como no recibo. */
    val descricao get() = buildString {
        if (tipo.isNotBlank()) append("($tipo) ")
        append(listOf(de, ate).filter { it.isNotBlank() }.joinToString(" → "))
    }.trim()

    companion object {
        val TIPOS = listOf("Ida", "Volta", "Ida e volta")
    }
}

/** Uma corrida do recibo de cliente fixo: o trajeto e o dia em que foi feito. */
data class CorridaRecibo(val dia: LocalDate, val trajeto: Trajeto)

/**
 * Listas dentro de uma coluna do .tsv: os campos se separam por U+001F e os itens por U+001E, caracteres que
 * ninguém digita. Assim o arquivo continua com uma linha por recibo ou por cliente.
 */
object Lista {
    private const val CAMPO = '\u001F'
    private const val ITEM = '\u001E'

    fun limpo(s: String) = s.filter { it != CAMPO && it != ITEM }.replace(Regex("""[\t\n\r]"""), " ")

    fun juntar(itens: List<List<String>>) = itens.joinToString(ITEM.toString()) { campos -> campos.joinToString(CAMPO.toString()) { limpo(it) } }

    fun separar(texto: String): List<List<String>> = if (texto.isEmpty()) emptyList() else texto.split(ITEM).map { it.split(CAMPO) }

    fun trajeto(t: Trajeto) = listOf(t.tipo, t.de, t.ate, t.valor.toString(), t.obs)

    fun trajeto(c: List<String>) = Trajeto(c[0], c[1], c[2], c[3].toDouble(), c.getOrElse(4) { "" })

    fun corridas(cs: List<CorridaRecibo>) = juntar(cs.map { listOf(it.dia.toString()) + trajeto(it.trajeto) })

    fun corridas(texto: String) = separar(texto).map { CorridaRecibo(LocalDate.parse(it[0]), trajeto(it.drop(1))) }
}

/** O que o motorista falou: "recibo de 50 reais, da Avenida Paulista até a Vila Mariana, no Pix". */
data class FalaRecibo(val valor: Double?, val de: String?, val ate: String?, val pagamento: String?, val passageiro: String?)

object LeitorRecibo {
    private val I = RegexOption.IGNORE_CASE
    private val RECIBO = Regex("""\brecibo\b""", I)
    private val PAGAMENTO = Regex("""\b(pix|dinheiro|cart[aã]o de cr[eé]dito|cart[aã]o de d[eé]bito|cr[eé]dito|d[eé]bito|cart[aã]o)\b""", I)
    private val ATE = Regex("""\bat[eé]\s+(?:(?:a|o|as|os|na|no)\s+)?""", I)
    private val PASSAGEIRO = Regex("""\b(?:passageir[oa]|em nome d[eoa]|cliente)\s+""", I)
    private val VALOR = Regex("""(?:r\$\s*)?\d+(?:[.,]\d+)?(?:\s*(?:reais|real|conto|contos|pila))?(?:\s*e\s*\d{1,2}(?:\s*centavos)?)?""", I)
    private val ORIGEM = Regex("""\b(?:(?:saindo|partindo)\s+)?(?:d[aoe]s?|desde)\s+(?!r\$|\d)""", I)
    /** Onde termina um endereço: vírgula, forma de pagamento ou o nome do passageiro. */
    private val FIM_ENDERECO = Regex(""",|\s+(?:n[oa]|em|pel[oa]|via)\s+(?:pix|dinheiro|cart|cr[eé]d|d[eé]b)|\s+(?:pix|dinheiro|cart[aã]o)\b|\s+(?:passageir[oa]|em nome|cliente)\b""", I)
    private val FIM_NOME = Regex(""",|\s+at[eé]\b|\s+(?:d[aoe]\s+)?(?:r\$\s*)?\d|\s+(?:n[oa]|em|pel[oa]|via)\s+(?:pix|dinheiro|cart)|\s+(?:pix|dinheiro|cart[aã]o)\b|\s+(?:saindo|partindo)\b""", I)

    fun ehRecibo(fala: String) = RECIBO.containsMatchIn(fala)

    fun ler(fala: String): FalaRecibo {
        val f = fala.replace(' ', ' ').trim()
        val pagamento = PAGAMENTO.find(f)?.groupValues?.get(1)?.let { nomePagamento(it) }
        val passM = PASSAGEIRO.find(f)
        val passageiro = passM?.let { ate(f.substring(it.range.last + 1), FIM_NOME) }?.takeIf { it.isNotBlank() }
        val ateM = ATE.find(f)
        val ate = ateM?.let { ate(f.substring(it.range.last + 1), FIM_ENDERECO) }?.takeIf { it.any(Char::isLetter) }

        // A origem vem antes do "até", depois do valor e do nome do passageiro: "da Avenida Paulista".
        var origem: String? = null
        if (ateM != null) {
            val trecho = f.substring(0, ateM.range.first)
            var inicio = RECIBO.find(trecho)?.range?.last?.plus(1) ?: 0
            VALOR.find(trecho, inicio)?.let { inicio = maxOf(inicio, it.range.last + 1) }
            if (passM != null && passageiro != null && passM.range.first < trecho.length) {
                inicio = maxOf(inicio, minOf(trecho.length, passM.range.last + 1 + passageiro.length))
            }
            val resto = trecho.substring(minOf(inicio, trecho.length))
            origem = (ORIGEM.find(resto)?.let { resto.substring(it.range.last + 1) } ?: resto)
                .trim().trim(',').trim().takeIf { it.any(Char::isLetter) }
        }

        var semEnderecos = f
        listOfNotNull(origem, ate, passageiro).forEach { semEnderecos = semEnderecos.replace(it, " ") }
        return FalaRecibo(
            valor = LeitorFala.valorDaFala(semEnderecos),
            de = origem?.let(::maiuscula),
            ate = ate?.let(::maiuscula),
            pagamento = pagamento,
            passageiro = passageiro?.let(::maiuscula),
        )
    }

    private fun ate(s: String, fim: Regex): String = (fim.find(s)?.let { s.substring(0, it.range.first) } ?: s).trim().trim(',').trim()

    private fun maiuscula(s: String) = s.replaceFirstChar { it.uppercase() }

    fun nomePagamento(s: String): String {
        val t = LeitorGasto.semAcento(s.lowercase())
        return when {
            t == "pix" -> "Pix"
            t == "dinheiro" -> "Dinheiro"
            "credito" in t -> "Cartão de crédito"
            "debito" in t -> "Cartão de débito"
            else -> "Cartão"
        }
    }

    val PAGAMENTOS = listOf("Dinheiro", "Pix", "Cartão de débito", "Cartão de crédito")
}

/** Valor por extenso, como em recibo de papel: "cento e vinte e três reais e cinquenta centavos". */
object Extenso {
    private val UNIDADES = listOf(
        "zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez", "onze", "doze", "treze",
        "quatorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove",
    )
    private val DEZENAS = listOf("", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa")
    private val CENTENAS = listOf("", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos", "seiscentos", "setecentos", "oitocentos", "novecentos")

    fun reais(v: Double): String {
        val centavosTotal = Math.round(v * 100)
        val r = centavosTotal / 100
        val c = (centavosTotal % 100).toInt()
        val partes = mutableListOf<String>()
        if (r > 0) partes += inteiro(r) + (if (r == 1L) " real" else " reais")
        if (c > 0) partes += ate999(c) + (if (c == 1) " centavo" else " centavos")
        return if (partes.isEmpty()) "zero real" else partes.joinToString(" e ")
    }

    private fun inteiro(n: Long): String {
        val mil = (n / 1000).toInt()
        val resto = (n % 1000).toInt()
        if (mil == 0) return ate999(resto)
        val milhar = if (mil == 1) "mil" else ate999(mil) + " mil"
        if (resto == 0) return milhar
        // "mil e cem", "mil e cinquenta", mas "mil duzentos e trinta".
        return milhar + (if (resto < 100 || resto % 100 == 0) " e " else " ") + ate999(resto)
    }

    private fun ate999(n: Int): String {
        if (n == 100) return "cem"
        val partes = mutableListOf<String>()
        if (n >= 100) partes += CENTENAS[n / 100]
        val r = n % 100
        if (r > 0 || partes.isEmpty()) partes += if (r < 20) UNIDADES[r] else DEZENAS[r / 10] + if (r % 10 > 0) " e " + UNIDADES[r % 10] else ""
        return partes.joinToString(" e ")
    }
}

object Recibos {
    private const val ARQUIVO = "recibos.tsv"
    private val DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val HORA = DateTimeFormatter.ofPattern("HH:mm")

    fun todos(ctx: Context): List<Recibo> {
        val f = File(ctx.filesDir, ARQUIVO)
        return if (f.exists()) f.readLines().mapNotNull { deLinha(it) } else emptyList()
    }

    fun proximoNumero(ctx: Context) = (todos(ctx).maxOfOrNull { it.numero } ?: 0) + 1

    /** Dá o próximo número ao recibo e guarda. */
    fun emitir(ctx: Context, r: Recibo): Recibo {
        val emitido = r.copy(numero = proximoNumero(ctx))
        File(ctx.filesDir, ARQUIVO).appendText(paraLinha(emitido) + "\n")
        return emitido
    }

    /** Números dos recibos que já foram corrigidos por outro. */
    fun substituidos(recibos: List<Recibo>) = recibos.mapNotNull { it.substitui }.toSet()

    fun paraLinha(r: Recibo) = listOf(
        r.numero, r.quando, r.valor, r.passageiro, r.de, r.ate, r.pagamento, r.telefone, r.email, r.substitui ?: "",
        r.documento, Lista.corridas(r.corridas),
    ).joinToString("\t") { it.toString().replace(Regex("""[\t\n\r]"""), " ") }

    /** As duas últimas colunas (documento e corridas) só existem desde o recibo de cliente fixo. */
    fun deLinha(l: String): Recibo? = runCatching {
        val c = l.split('\t')
        Recibo(
            c[0].toInt(), LocalDateTime.parse(c[1]), c[2].toDouble(), c[3], c[4], c[5], c[6], c[7], c[8], c[9].toIntOrNull(),
            c.getOrElse(10) { "" }, Lista.corridas(c.getOrElse(11) { "" }),
        )
    }.getOrNull()

    fun data(r: Recibo): String = r.quando.format(DATA)

    fun data(c: CorridaRecibo): String = c.dia.format(DATA)

    /** Uma corrida do recibo de cliente fixo em uma linha: "29/09/2026 – (Ida) Casa → Congonhas – R$ 130,00". */
    fun linha(c: CorridaRecibo): String =
        listOf(data(c), c.trajeto.descricao, c.trajeto.obs, "R$ ${Popup.br(c.trajeto.valor)}").filter { it.isNotBlank() }.joinToString(" – ")

    fun hora(r: Recibo): String = r.quando.format(HORA)

    /** Quem pagou, com o CPF ou CNPJ quando o cliente fixo tem: "Marta Oliveira (CPF 123.456.789-00)". */
    fun pagador(r: Recibo): String =
        if (r.documento.isBlank() || r.passageiro.isBlank()) r.passageiro else "${r.passageiro} (CPF/CNPJ ${r.documento})"

    /** A frase principal do recibo, usada no PDF e na mensagem. */
    fun frase(r: Recibo): String = buildString {
        append(if (r.passageiro.isNotBlank()) "Recebi de ${pagador(r)} a quantia de " else "Recebi a quantia de ")
        if (r.varias) {
            append("R$ ${Popup.br(r.valor)} (${Extenso.reais(r.valor)}), em ${data(r)}, referente às seguintes corridas de táxi:")
            return@buildString
        }
        append("R$ ${Popup.br(r.valor)} (${Extenso.reais(r.valor)}), referente a corrida de táxi em ${data(r)} às ${hora(r)}")
        if (r.de.isNotBlank()) append(", de ${r.de}")
        if (r.ate.isNotBlank()) append(if (r.de.isNotBlank()) " até ${r.ate}" else ", até ${r.ate}")
        append('.')
    }

    /**
     * A corrida na tabela dos modelos Tabela e Completo, uma informação por linha, com rótulo, para o
     * "para" não se misturar com o nome da rua: "Corrida de táxi / De: Avenida Paulista / Até: Vila Mariana".
     */
    fun descricao(r: Recibo): String = listOfNotNull(
        "Corrida de táxi",
        r.de.takeIf { it.isNotBlank() }?.let { "De: $it" },
        r.ate.takeIf { it.isNotBlank() }?.let { "Até: $it" },
    ).joinToString("\n")

    fun dadosDoMotorista(m: Motorista): List<String> = listOfNotNull(
        m.nome,
        listOfNotNull(m.placa.takeIf { it.isNotBlank() }?.let { "Táxi placa $it" }, m.alvara.takeIf { it.isNotBlank() }?.let { "Alvará $it" })
            .joinToString(" · ").takeIf { it.isNotBlank() },
        m.documento.takeIf { it.isNotBlank() }?.let { "CPF/CNPJ $it" },
        m.telefone.takeIf { it.isNotBlank() }?.let { "Tel. $it" },
    )

    /** O recibo em texto, para o WhatsApp e o corpo do e-mail. */
    fun texto(r: Recibo, m: Motorista): String = buildString {
        appendLine("RECIBO DE TÁXI Nº ${r.numeroTexto}")
        appendLine()
        appendLine(frase(r))
        r.corridas.forEach { appendLine("• ${linha(it)}") }
        if (r.varias) appendLine("Total: R$ ${Popup.br(r.valor)}")
        if (r.pagamento.isNotBlank()) appendLine("Pagamento: ${r.pagamento}.")
        r.substitui?.let { appendLine("Este recibo substitui o nº ${String.format(Locale.ROOT, "%04d", it)}.") }
        appendLine()
        dadosDoMotorista(m).forEach { appendLine(it) }
    }.trim()
}
