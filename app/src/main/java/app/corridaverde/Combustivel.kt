package app.corridaverde

import java.time.LocalDate
import java.util.Locale

/** Um trecho entre dois tanques cheios com quilometragem. [combustivel] null = misturado (nenhum chegou a 80% dos litros). */
data class Trecho(val ate: Gasto, val kmRodados: Int, val litros: Double, val valor: Double, val combustivel: Tipo?) {
    val kmPorLitro get() = kmRodados / litros
    val custoKm get() = valor / kmRodados
}

/** O consumo de um combustível somando os trechos dele: km/l e, o que decide qual compensa, R$ por km. */
data class ConsumoDoCombustivel(val tipo: Tipo, val kmRodados: Int, val litros: Double, val valor: Double, val tanques: Int) {
    val kmPorLitro get() = kmRodados / litros
    val custoKm get() = valor / kmRodados
}

/** O que dá para tirar dos abastecimentos além do consumo de um tanque: comparar combustíveis, a média do mês e avisos. */
object Abastecimentos {
    /** Um trecho conta para um combustível quando ele é pelo menos 80% dos litros colocados. */
    const val PREDOMINANTE = 0.80
    /** Consumo de um tanque 15% abaixo da média do mesmo combustível: algo pode estar errado no carro. */
    const val QUEDA = 0.15
    /** A média do período só aparece com pelo menos 300 km: antes disso o nível do tanque pesa demais. */
    const val KM_MINIMO_MEDIA = 300
    /** Regra geral: o etanol compensa até 70% do preço da gasolina. */
    const val REGRA_GERAL = 0.70

    private fun abastecimentos(gastos: List<Gasto>) = gastos.filter { it.tipo.combustivel }.sortedBy { it.quando }

    /** Os trechos de tanque cheio a tanque cheio, do mais antigo ao mais novo, como no [Gastos.consumo]. */
    fun trechos(gastos: List<Gasto>): List<Trecho> {
        val abast = abastecimentos(gastos)
        val cheios = abast.indices.filter { abast[it].tanqueCheio && abast[it].km != null }
        return cheios.zipWithNext().mapNotNull { (i, j) ->
            val depois = abast.subList(i + 1, j + 1)
            if (depois.any { it.litros == null || it.litros <= 0 }) return@mapNotNull null
            val rodados = abast[j].km!! - abast[i].km!!
            if (rodados <= 0) return@mapNotNull null
            val litros = depois.sumOf { it.litros!! }
            val principal = depois.groupBy { it.tipo }.mapValues { (_, l) -> l.sumOf { it.litros!! } }.maxByOrNull { it.value }
            val tipo = principal?.takeIf { it.value / litros >= PREDOMINANTE }?.key
            Trecho(abast[j], rodados, litros, depois.sumOf { it.valor }, tipo)
        }
    }

    /** Consumo de cada combustível nos trechos que acabaram a partir de [desde], do mais barato por km ao mais caro. */
    fun porCombustivel(gastos: List<Gasto>, desde: LocalDate): List<ConsumoDoCombustivel> =
        trechos(gastos).filter { it.combustivel != null && !it.ate.quando.toLocalDate().isBefore(desde) }
            .groupBy { it.combustivel!! }
            .map { (tipo, t) -> ConsumoDoCombustivel(tipo, t.sumOf { it.kmRodados }, t.sumOf { it.litros }, t.sumOf { it.valor }, t.size) }
            .sortedBy { it.custoKm }

    /**
     * Média de um período sem precisar encher o tanque: km do painel entre o primeiro e o último abastecimento
     * com quilometragem, dividido pelos litros colocados depois do primeiro. O nível do tanque no começo e no fim
     * entra como erro, que vai diluindo com os km. Null com menos de [KM_MINIMO_MEDIA] km ou faltando litros.
     */
    fun mediaDoPeriodo(gastos: List<Gasto>, de: LocalDate, ate: LocalDate): Consumo? {
        val abast = abastecimentos(gastos).filter { it.quando.toLocalDate() in de..ate }
        val comKm = abast.indices.filter { abast[it].km != null }
        if (comKm.size < 2) return null
        val i = comKm.first()
        val j = comKm.last()
        val depois = abast.subList(i + 1, j + 1)
        if (depois.any { it.litros == null || it.litros <= 0 }) return null
        val rodados = abast[j].km!! - abast[i].km!!
        if (rodados < KM_MINIMO_MEDIA) return null
        val unidade = depois.map { it.tipo.unidade }.distinct().singleOrNull() ?: "L"
        return Consumo(rodados, depois.sumOf { it.litros!! }, depois.sumOf { it.valor }, unidade)
    }

    /** Abastecimentos do período sem quilometragem: sem ela a média não sai. */
    fun semKm(gastos: List<Gasto>, de: LocalDate, ate: LocalDate) =
        abastecimentos(gastos).count { it.quando.toLocalDate() in de..ate && it.km == null }

    /**
     * Até quantos % do preço da gasolina o etanol compensa NESTE carro: km/l do etanol ÷ km/l da gasolina.
     * Null sem tanque medido dos dois.
     */
    fun limiteEtanol(lista: List<ConsumoDoCombustivel>): Double? {
        val e = lista.firstOrNull { it.tipo == Tipo.ETANOL } ?: return null
        val g = lista.firstOrNull { it.tipo == Tipo.GASOLINA } ?: return null
        return e.kmPorLitro / g.kmPorLitro
    }

    /** Preço do litro do último abastecimento de [tipo] com litros, a partir de [desde]. */
    fun ultimoPreco(gastos: List<Gasto>, tipo: Tipo, desde: LocalDate): Double? =
        abastecimentos(gastos).lastOrNull { it.tipo == tipo && it.litros != null && it.litros > 0 && !it.quando.toLocalDate().isBefore(desde) }
            ?.let { it.valor / it.litros!! }

    /**
     * Quanto o último trecho ficou abaixo da média dos trechos anteriores do mesmo combustível (0,18 = 18% pior),
     * ou null se não piorou [QUEDA] ou mais, ou se ainda há menos de 2 trechos para comparar.
     */
    fun queda(trechos: List<Trecho>): Double? {
        val ultimo = trechos.lastOrNull()?.takeIf { it.combustivel != null } ?: return null
        val antes = trechos.dropLast(1).filter { it.combustivel == ultimo.combustivel }.takeLast(5)
        if (antes.size < 2) return null
        val media = antes.sumOf { it.kmRodados } / antes.sumOf { it.litros }
        return (1 - ultimo.kmPorLitro / media).takeIf { it >= QUEDA }
    }

    /**
     * A conta do etanol contra a gasolina na hora de abastecer um dos dois, com o preço do outro dos últimos 60 dias.
     * Usa o limite medido no carro; sem ele, os 70% da regra geral.
     */
    fun compararPreco(gastos: List<Gasto>, g: Gasto, hoje: LocalDate = g.quando.toLocalDate()): String? {
        if (g.tipo != Tipo.ETANOL && g.tipo != Tipo.GASOLINA) return null
        val litros = g.litros?.takeIf { it > 0 } ?: return null
        val preco = g.valor / litros
        val outro = if (g.tipo == Tipo.ETANOL) Tipo.GASOLINA else Tipo.ETANOL
        val precoOutro = ultimoPreco(gastos.filter { it != g }, outro, hoje.minusDays(60)) ?: return null
        val etanol = if (g.tipo == Tipo.ETANOL) preco else precoOutro
        val gasolina = if (g.tipo == Tipo.GASOLINA) preco else precoOutro
        val medido = limiteEtanol(porCombustivel(gastos, hoje.minusDays(180)))
        val limite = medido ?: REGRA_GERAL
        val razao = etanol / gasolina
        val qual = if (razao <= limite) "o etanol" else "a gasolina"
        val base = if (medido != null) "No seu carro, o etanol compensa até ${pct(limite)} do preço da gasolina."
            else "Pela regra geral, o etanol compensa até 70% do preço da gasolina (com tanques cheios dos dois, o app mede o do seu carro)."
        return "$base O etanol a R$ ${Popup.br(etanol)} está a ${pct(razao)} da gasolina a R$ ${Popup.br(gasolina)}: $qual compensa."
    }

    private fun pct(x: Double) = "${Math.round(x * 100)}%"

    fun kmL(x: Double) = String.format(Locale("pt", "BR"), "%.1f", x)
}
