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

/**
 * Um gasto registrado. [km] é a quilometragem do painel na hora do abastecimento. [descricao] é o nome que o
 * motorista deu a um gasto "Outro" ("Supermercado", "Dentista"), que vira um tipo dele ([TiposGasto]).
 */
data class Gasto(
    val quando: LocalDateTime,
    val tipo: Tipo,
    val valor: Double,
    val litros: Double? = null,
    val km: Int? = null,
    val tanqueCheio: Boolean = false,
    val descricao: String = "",
) {
    /** Como o gasto aparece nas telas: o nome que o motorista deu, ou o do tipo. */
    val nome get() = descricao.takeIf { tipo == Tipo.OUTRO && it.isNotBlank() } ?: tipo.nome
}

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

    /** Troca o nome de um tipo próprio em todos os gastos dele. */
    fun renomear(ctx: Context, antigo: String, novo: String) {
        val f = File(ctx.filesDir, ARQUIVO).takeIf { it.exists() } ?: return
        val c = TiposGasto.chave(antigo)
        f.writeText(f.readLines().joinToString("") { l ->
            val g = deLinha(l)
            (if (g != null && g.tipo == Tipo.OUTRO && TiposGasto.chave(g.descricao) == c) paraLinha(g.copy(descricao = novo)) else l) + "\n"
        })
    }

    fun paraLinha(g: Gasto) =
        listOf(g.quando, g.tipo.name, g.valor, g.litros ?: "", g.km ?: "", if (g.tanqueCheio) 1 else 0,
            g.descricao.replace(Regex("""[\t\r\n]+"""), " "))
            .joinToString("\t")

    fun deLinha(s: String): Gasto? = runCatching {
        val c = s.split('\t')
        Gasto(
            quando = LocalDateTime.parse(c[0]),
            tipo = Tipo.valueOf(c[1]),
            valor = c[2].toDouble(),
            litros = c[3].toDoubleOrNull(),
            km = c[4].toIntOrNull(),
            tanqueCheio = c[5] == "1",
            // Os gastos de antes dos tipos próprios têm 6 colunas.
            descricao = c.getOrNull(6).orEmpty(),
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

/** Um tipo de gasto criado pelo motorista. [pessoal] fica fora do "Sobrou do trabalho"; [escondido] some da lista. */
data class TipoProprio(val nome: String, val pessoal: Boolean = false, val escondido: Boolean = false)

/**
 * Os tipos de gasto de cada motorista ("Supermercado", "Escola dos filhos"...): nascem quando ele escreve o nome de
 * um gasto "Outro" e, dali em diante, aparecem na lista e são reconhecidos na fala. Ficam só no celular dele.
 */
object TiposGasto {
    private const val ARQUIVO = "tipos_gasto.tsv"

    /** Para comparar nomes: sem acento, minúsculo, sem espaço sobrando ("Supermercado " = "supermercado"). */
    fun chave(nome: String) = LeitorGasto.semAcento(nome).lowercase().replace(Regex("""\s+"""), " ").trim()

    /** O nome como vai no arquivo: sem tabulação nem quebra de linha, com a primeira letra maiúscula. */
    fun limpo(nome: String) = nome.replace(Regex("""[\t\r\n]+"""), " ").replace(Regex("""\s+"""), " ").trim()
        .replaceFirstChar { it.uppercase() }.take(40)

    fun todos(ctx: Context): List<TipoProprio> = juntar(lerArquivo(ctx), Gastos.todos(ctx))

    /** Os do arquivo e os nomes que só aparecem nos gastos (o arquivo pode ter ficado para trás numa cópia antiga). */
    fun juntar(doArquivo: List<TipoProprio>, gastos: List<Gasto>): List<TipoProprio> {
        val vistos = doArquivo.map { chave(it.nome) }.toMutableSet()
        val soNosGastos = gastos.filter { it.tipo == Tipo.OUTRO && it.descricao.isNotBlank() }
            .filter { vistos.add(chave(it.descricao)) }
            .map { TipoProprio(limpo(it.descricao), pessoal = parecePessoal(it.descricao)) }
        return doArquivo + soNosGastos
    }

    /** Os que aparecem na lista do formulário: os mais usados primeiro. */
    fun naLista(tipos: List<TipoProprio>, gastos: List<Gasto>): List<TipoProprio> {
        val usos = gastos.filter { it.tipo == Tipo.OUTRO }.groupingBy { chave(it.descricao) }.eachCount()
        return tipos.filter { !it.escondido }.sortedWith(compareByDescending<TipoProprio> { usos[chave(it.nome)] ?: 0 }.thenBy { chave(it.nome) })
    }

    fun achar(tipos: List<TipoProprio>, nome: String): TipoProprio? = chave(nome).let { c -> tipos.firstOrNull { chave(it.nome) == c } }

    /** As chaves dos tipos pessoais, para o [Dinheiro.balanco] separar do trabalho. */
    fun pessoais(ctx: Context): Set<String> = todos(ctx).filter { it.pessoal }.map { chave(it.nome) }.toSet()

    private val PALAVRAS_PESSOAIS = listOf(
        "mercado", "supermercado", "hortifruti", "sacolao", "feira", "acougue", "dentista", "medico", "medica",
        "consulta", "exame", "hospital", "farmacia", "remedio", "plano de saude", "convenio", "escola", "faculdade",
        "curso", "material escolar", "creche", "aluguel", "condominio", "luz", "energia", "agua", "gas de cozinha",
        "botijao", "internet", "tv", "netflix", "academia", "cabeleireiro", "barbeiro", "roupa", "presente", "pet",
        "veterinario", "racao", "casa", "familia", "filho", "filhos", "filha", "esposa", "marido", "mae", "pai",
        "cartao de credito", "fatura", "emprestimo", "igreja", "dizimo", "lazer", "cinema",
    ).map { " $it " }

    /**
     * Nome de gasto que é de casa ("Mercado", "Dentista", "Escola dos filhos"): já vem marcado como pessoal.
     * Antes a caixinha vinha sempre desmarcada e o Paulo viu o mercado e o dentista no Sobrou do trabalho (07/11/2026).
     */
    fun parecePessoal(nome: String): Boolean {
        val t = " " + chave(nome).replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim() + " "
        return PALAVRAS_PESSOAIS.any { it in t }
    }

    fun ehPessoal(g: Gasto, pessoais: Set<String>) = g.tipo == Tipo.OUTRO && g.descricao.isNotBlank() && chave(g.descricao) in pessoais

    /** Cria o tipo, ou atualiza se pessoal ou não (e mostra de novo, se estava escondido). */
    fun registrar(ctx: Context, nome: String, pessoal: Boolean) {
        val n = limpo(nome)
        if (n.isEmpty()) return
        val tipos = todos(ctx)
        val existe = achar(tipos, n)
        salvar(ctx, if (existe == null) tipos + TipoProprio(n, pessoal)
            else tipos.map { if (it === existe) it.copy(pessoal = pessoal, escondido = false) else it })
    }

    fun atualizar(ctx: Context, antigo: TipoProprio, novo: TipoProprio) {
        val tipos = todos(ctx)
        val nome = limpo(novo.nome)
        if (nome.isEmpty()) return
        val outro = achar(tipos, nome)?.takeIf { chave(it.nome) != chave(antigo.nome) }
        // Renomeou para um nome que já existe: junta os dois.
        val lista = tipos.filter { chave(it.nome) != chave(antigo.nome) && it !== outro } + novo.copy(nome = outro?.nome ?: nome)
        salvar(ctx, lista)
        if (nome != antigo.nome) Gastos.renomear(ctx, antigo.nome, outro?.nome ?: nome)
    }

    /**
     * O tipo próprio dito na fala ("gastei 45 no dentista" acha "Dentista"). O nome mais comprido ganha
     * ("café da manhã" antes de "café"). Só palavras inteiras.
     */
    fun naFala(tipos: List<TipoProprio>, fala: String): TipoProprio? {
        val t = " " + chave(fala).replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ") + " "
        return tipos.filter { chave(it.nome).isNotEmpty() && " ${chave(it.nome)} " in t }.maxByOrNull { it.nome.length }
    }

    private val DEPOIS_DE = Regex("""\b(?:no|na|nos|nas|com|em|de|do|da|pro|pra|para)\s+([a-zà-ÿ]{3,}(?:\s+(?:d[oae]s?|e)\s+[a-zà-ÿ]{3,})?)""")
    private val NAO_E_NOME = Regex("""^(reais|real|conto|contos|pila|hoje|ontem|dinheiro|cartao|credito|debito|pix|uber|gasto|valor|novo|nova)\b""")

    /** Um nome para sugerir num gasto novo: "gastei 45 reais no dentista" sugere "Dentista". */
    fun sugerirNome(fala: String): String? = DEPOIS_DE.findAll(fala.lowercase())
        .map { it.groupValues[1] }
        .firstOrNull { !NAO_E_NOME.containsMatchIn(LeitorGasto.semAcento(it)) }
        ?.let { limpo(it) }

    private fun lerArquivo(ctx: Context): List<TipoProprio> {
        val f = File(ctx.filesDir, ARQUIVO)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { l ->
            val c = l.split('\t')
            c.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { nome ->
                // Sem a 4ª coluna: gravado até a 1.89, quando a caixinha "pessoal" vinha sempre desmarcada. Os nomes de
                // casa passam a pessoal uma vez; daí em diante vale o que o motorista escolher.
                val antigo = c.size < 4
                TipoProprio(nome, c.getOrNull(1) == "1" || (antigo && parecePessoal(nome)), c.getOrNull(2) == "1")
            }
        }
    }

    private fun salvar(ctx: Context, tipos: List<TipoProprio>) {
        File(ctx.filesDir, ARQUIVO).writeText(tipos.joinToString("") { "${limpo(it.nome)}\t${if (it.pessoal) 1 else 0}\t${if (it.escondido) 1 else 0}\t1\n" })
    }
}
