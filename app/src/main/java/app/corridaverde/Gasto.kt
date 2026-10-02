package app.corridaverde

import android.content.Context
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime

enum class Tipo(val nome: String, val combustivel: Boolean = false) {
    GASOLINA("Gasolina", true),
    ETANOL("Etanol", true),
    DIESEL("Diesel", true),
    GNV("GNV", true),
    LAVAGEM("Lavagem"),
    ESTACIONAMENTO("Estacionamento"),
    PEDAGIO("Pedágio"),
    MANUTENCAO("Manutenção"),
    ALIMENTACAO("Alimentação"),
    OUTRO("Outro");

    /** GNV é vendido em m³. */
    val unidade get() = if (this == GNV) "m³" else "L"
}

/** Um gasto registrado. [km] é a quilometragem do painel na hora do abastecimento. */
data class Gasto(
    val quando: LocalDateTime,
    val tipo: Tipo,
    val valor: Double,
    val litros: Double? = null,
    val km: Int? = null,
    val tanqueCheio: Boolean = false,
)

/** Consumo entre dois abastecimentos de tanque cheio. */
data class Consumo(val kmRodados: Int, val litros: Double, val valor: Double, val unidade: String) {
    val kmPorLitro get() = kmRodados / litros
    val custoKm get() = valor / kmRodados
}

/** O que o app entendeu da fala. Tudo pode faltar: o motorista completa no formulário. */
data class Leitura(
    val tipo: Tipo? = null,
    val valor: Double? = null,
    val litros: Double? = null,
    val km: Int? = null,
    val tanqueCheio: Boolean = false,
)

object LeitorGasto {
    private val NUMERO = Regex("""\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:[.,]\d+)?""")
    private val DEPOIS_LITROS = Regex("""^\s*(litros?|l\b|metros? cubicos?|m3|m³)""")
    private val DEPOIS_KM = Regex("""^\s*(km|quilometros?)\b""")
    private val ANTES_KM = Regex("""(quilometragem|hodometro|odometro|km|painel|marcando)\s*(de|em|:|e)?\s*$""")
    private val DEPOIS_PRECO = Regex("""^\s*(reais\s*)?(o|por|cada)\s*litro""")
    private val ANTES_PRECO = Regex("""litros?\s*(a|de|por|custando)?\s*(r\$)?\s*$""")
    private val DEPOIS_VALOR = Regex("""^\s*(reais|real|conto|contos|pila)\b""")
    private val ANTES_VALOR = Regex("""r\$\s*$""")
    private val CHEIO = Regex("""tanque cheio|enchi|completei|complet(o|ar)|cheio""")

    val TIPOS = listOf(
        Tipo.GASOLINA to Regex("""gasolina"""),
        Tipo.ETANOL to Regex("""etanol|alcool"""),
        Tipo.DIESEL to Regex("""diesel"""),
        Tipo.GNV to Regex("""\bgnv\b|\bgas\b"""),
        Tipo.LAVAGEM to Regex("""lav(agem|ei|ar|a jato|ajato|a-jato)"""),
        Tipo.ESTACIONAMENTO to Regex("""estacion|zona azul"""),
        Tipo.PEDAGIO to Regex("""pedagio"""),
        Tipo.MANUTENCAO to Regex("""manuten|mecanic|oficina|oleo|pneu|revis|borrach|freio|pastilha"""),
        Tipo.ALIMENTACAO to Regex("""almoc|lanche|comida|jant|cafe|alimenta|refeic"""),
    )

    /** Ex.: "abasteci 120 reais de etanol, 22 litros, tanque cheio, quilometragem 45.320". */
    fun ler(fala: String): Leitura {
        val t = semAcento(fala.lowercase()).replace(" ", " ")
        var valor: Double? = null
        var litros: Double? = null
        var km: Int? = null
        var preco: Double? = null
        val soltos = mutableListOf<Double>()

        for (m in NUMERO.findAll(t)) {
            val n = numero(m.value) ?: continue
            val antes = t.substring(0, m.range.first)
            val depois = t.substring(m.range.last + 1)
            when {
                DEPOIS_LITROS.containsMatchIn(depois) -> litros = n
                DEPOIS_PRECO.containsMatchIn(depois) || ANTES_PRECO.containsMatchIn(antes) -> preco = n
                DEPOIS_KM.containsMatchIn(depois) || ANTES_KM.containsMatchIn(antes) -> km = n.toInt()
                DEPOIS_VALOR.containsMatchIn(depois) || ANTES_VALOR.containsMatchIn(antes) -> valor = n
                else -> soltos += n
            }
        }
        if (valor == null) valor = soltos.firstOrNull()
        if (litros == null && preco != null && valor != null && preco > 0) litros = valor / preco

        var tipo = TIPOS.firstOrNull { it.second.containsMatchIn(t) }?.first
        if (tipo == null && "abastec" in t) tipo = Tipo.GASOLINA
        return Leitura(tipo, valor, litros, km, CHEIO.containsMatchIn(t))
    }

    /** "45.320" é milhar; "120,50" e "5.89" são decimais. */
    fun numero(s: String): Double? =
        if (Regex("""\d{1,3}(\.\d{3})+(,\d+)?""").matches(s)) s.replace(".", "").replace(',', '.').toDoubleOrNull()
        else s.replace(',', '.').toDoubleOrNull()

    fun semAcento(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{Mn}"""), "")
}

object Gastos {
    private const val ARQUIVO = "gastos.tsv"

    fun todos(ctx: Context): List<Gasto> {
        val f = File(ctx.filesDir, ARQUIVO)
        return if (f.exists()) f.readLines().mapNotNull { deLinha(it) } else emptyList()
    }

    fun adicionar(ctx: Context, g: Gasto) = File(ctx.filesDir, ARQUIVO).appendText(paraLinha(g) + "\n")

    /** Apaga um gasto (o primeiro igual, se houver dois iguais). */
    fun apagar(ctx: Context, g: Gasto) {
        val linhas = File(ctx.filesDir, ARQUIVO).takeIf { it.exists() }?.readLines() ?: return
        val i = linhas.indexOfFirst { deLinha(it) == g }
        if (i < 0) return
        File(ctx.filesDir, ARQUIVO).writeText(linhas.filterIndexed { j, _ -> j != i }.joinToString("") { it + "\n" })
    }

    /** Troca um gasto pelo corrigido, no mesmo lugar do arquivo. */
    fun substituir(ctx: Context, antigo: Gasto, novo: Gasto) {
        val linhas = File(ctx.filesDir, ARQUIVO).takeIf { it.exists() }?.readLines() ?: return
        val i = linhas.indexOfFirst { deLinha(it) == antigo }
        if (i < 0) return
        File(ctx.filesDir, ARQUIVO).writeText(linhas.mapIndexed { j, l -> if (j == i) paraLinha(novo) else l }.joinToString("") { it + "\n" })
    }

    fun paraLinha(g: Gasto) =
        listOf(g.quando, g.tipo.name, g.valor, g.litros ?: "", g.km ?: "", if (g.tanqueCheio) 1 else 0).joinToString("\t")

    fun deLinha(s: String): Gasto? = runCatching {
        val c = s.split('\t')
        Gasto(
            quando = LocalDateTime.parse(c[0]),
            tipo = Tipo.valueOf(c[1]),
            valor = c[2].toDouble(),
            litros = c[3].toDoubleOrNull(),
            km = c[4].toIntOrNull(),
            tanqueCheio = c[5] == "1",
        )
    }.getOrNull()

    fun totalDoDia(gastos: List<Gasto>, dia: LocalDate) = gastos.filter { it.quando.toLocalDate() == dia }.sumOf { it.valor }

    /**
     * Consumo do último abastecimento, pelo método tanque cheio a tanque cheio: km entre os dois
     * dividido pelos litros colocados depois do primeiro (inclusive abastecimentos parciais no meio).
     * Null se o último não foi tanque cheio com quilometragem, se não há um anterior assim,
     * ou se falta a quantidade de algum abastecimento no meio.
     */
    fun consumo(gastos: List<Gasto>): Consumo? {
        val abast = gastos.filter { it.tipo.combustivel }.sortedBy { it.quando }
        val ultimo = abast.lastOrNull() ?: return null
        if (!ultimo.tanqueCheio || ultimo.km == null) return null
        val i = abast.indexOfLast { it !== ultimo && it.tanqueCheio && it.km != null }
        if (i < 0) return null
        val depois = abast.subList(i + 1, abast.size)
        if (depois.any { it.litros == null || it.litros <= 0 }) return null
        val rodados = ultimo.km - abast[i].km!!
        if (rodados <= 0) return null
        return Consumo(rodados, depois.sumOf { it.litros!! }, depois.sumOf { it.valor }, ultimo.tipo.unidade)
    }
}
