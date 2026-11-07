package app.corridaverde

import java.text.Normalizer

/** Bandeirada, km na bandeira 1 e 2 e hora parada de uma categoria de táxi. */
data class Valores(val bandeirada: Double, val km1: Double, val km2: Double, val hora: Double) {
    val completo get() = bandeirada > 0 && km1 > 0 && km2 > 0 && hora > 0
}

/**
 * A tabela do taxímetro de uma cidade: os valores da prefeitura e quando vale a bandeira 2.
 * Domingo e feriado são bandeira 2 o dia todo em todas as cidades da base.
 */
data class TabelaTaxi(
    val comum: Valores,
    /** A segunda categoria da cidade (Luxo em SP, Executivo no Rio, Especial no Recife...), ou null se não tem. */
    val especial: Valores? = null,
    val nomeEspecial: String = "Luxo",
    /** Bandeira 2 à noite: a partir de [b2De] horas até antes de [b2Ate] horas da manhã seguinte. */
    val b2De: Int = 20,
    val b2Ate: Int = 6,
    /** Sábado: [SABADO_NORMAL] (só a noite), 0 (o dia todo) ou a hora em que a bandeira 2 começa (13, 14...). */
    val b2Sabado: Int = SABADO_NORMAL,
) {
    companion object {
        const val SABADO_NORMAL = -1
    }
}

/**
 * Uma cidade da base. [conferir] = a pesquisa não achou o decreto mais recente ou faltou algum valor
 * (a hora parada foi estimada em 10 vezes o km, a média das capitais): o app pede para o taxista conferir.
 */
data class Cidade(
    val nome: String,
    val uf: String,
    val tabela: TabelaTaxi,
    /** Desde quando vale a tabela, como a pesquisa achou (mês/ano). */
    val vigencia: String,
    val conferir: Boolean = false,
    /** Feriados da cidade e do estado (mês to dia), além dos nacionais. */
    val feriados: Set<Pair<Int, Int>> = emptySet(),
    /** Terça de Carnaval em bandeira 2 o dia todo. */
    val carnaval: Boolean = false,
) {
    /** "São Paulo/SP": o que fica guardado no [Config.cidade]. */
    val chave get() = "$nome/$uf"
}

/**
 * As tabelas de táxi das capitais e das cidades grandes, pesquisadas em 07/11/2026 nos decretos e nas notícias
 * de cada prefeitura. Cada prefeitura tem a sua tabela, e as vizinhas mudam bastante (Niterói cobra o km 17% mais
 * caro que o Rio; Guarulhos, 7% mais barato que São Paulo), por isso não dá para usar a da capital na região toda.
 * Cidade que não está aqui entra pela tabela colada no táxi ("Minha cidade não está na lista").
 */
object Cidades {
    private fun v(bandeirada: Double, km1: Double, km2: Double, hora: Double) = Valores(bandeirada, km1, km2, hora)

    /** SP: Prefeitura, vigente desde 11/08/2025. Bandeira 2 = km 30% mais caro, a hora parada não muda. */
    val SAO_PAULO = Cidade(
        "São Paulo", "SP",
        TabelaTaxi(v(6.55, 4.80, 6.24, 55.50), v(9.83, 7.20, 9.36, 83.25), "Luxo"),
        "ago/2025", feriados = setOf(1 to 25, 7 to 9),
    )

    val TODAS: List<Cidade> = listOf(
        SAO_PAULO,
        // Decreto de jan/2026 (+14,3%). Horário da bandeira 2 não achado: igual ao de SP.
        Cidade("Guarulhos", "SP", TabelaTaxi(v(6.29, 4.47, 5.94, 44.87)), "jan/2026", conferir = true,
            feriados = setOf(7 to 9, 12 to 8)),
        // Emdec, decretos 23.766 e 23.770/2025.
        Cidade("Campinas", "SP", TabelaTaxi(v(5.49, 3.94, 5.12, 48.68), v(7.14, 5.12, 6.66, 63.28), "Executivo", b2Sabado = 14),
            "fev/2025", feriados = setOf(7 to 9, 12 to 8)),
        // Decreto 15.048 de 16/03/2026 (bandeira 2 das 20h às 6h, domingo e feriado). Pesquisado em 07/11/2026.
        Cidade("Osasco", "SP", TabelaTaxi(v(6.55, 4.80, 6.72, 55.00)), "mar/2026",
            feriados = setOf(2 to 19, 7 to 9)),
        // Decreto 9.608 de 08/07/2022, o mais novo achado (bandeira 2 das 20h às 6h, domingo e feriado).
        Cidade("Barueri", "SP", TabelaTaxi(v(5.40, 4.60, 4.70, 45.00)), "jul/2022", conferir = true,
            feriados = setOf(3 to 26, 7 to 9)),
        Cidade("Santos", "SP", TabelaTaxi(v(6.50, 4.60, 5.52, 36.00)), "2025", conferir = true,
            feriados = setOf(1 to 26, 7 to 9, 9 to 8)),
        // Resolução SMTR 3.893 de 29/12/2025. Executivo: km na bandeira 2 estimado (+20%).
        Cidade("Rio de Janeiro", "RJ", TabelaTaxi(v(6.30, 3.85, 4.62, 48.51), v(8.50, 6.55, 7.86, 82.53), "Executivo", b2De = 21),
            "jan/2026", feriados = setOf(1 to 20, 4 to 23), carnaval = true),
        Cidade("Niterói", "RJ", TabelaTaxi(v(5.95, 4.50, 5.08, 44.36), b2De = 22, b2Sabado = 12),
            "abr/2026", feriados = setOf(4 to 23, 6 to 24, 11 to 22), carnaval = true),
        // BHTrans, 08/08/2026. A prefeitura libera a bandeira 2 em dezembro todo ano.
        Cidade("Belo Horizonte", "MG", TabelaTaxi(v(5.40, 4.50, 5.40, 39.00), b2De = 22, b2Sabado = 14),
            "ago/2026", feriados = setOf(8 to 15, 12 to 8), carnaval = true),
        Cidade("Juiz de Fora", "MG", TabelaTaxi(v(7.92, 3.98, 4.76, 39.80)), "jan/2026", conferir = true,
            feriados = setOf(5 to 31)),
        // Decreto 22.457, 18/01/2026.
        Cidade("Uberlândia", "MG", TabelaTaxi(v(5.26, 3.62, 4.50, 36.20)), "jan/2026", conferir = true,
            feriados = setOf(8 to 31)),
        // Decreto 2.652/2025 (URBS). Hora parada do comum estimada pelo reajuste do executivo.
        Cidade("Curitiba", "PR", TabelaTaxi(v(5.40, 4.06, 5.00, 33.40), v(7.15, 5.41, 6.66, 43.87), "Executivo", b2De = 22, b2Sabado = 13),
            "jan/2026", conferir = true, feriados = setOf(9 to 8)),
        // EPTC, decreto 23.670, 19/02/2026.
        Cidade("Porto Alegre", "RS", TabelaTaxi(v(7.24, 3.62, 4.71, 36.20), b2Sabado = 15), "fev/2026", conferir = true,
            feriados = setOf(2 to 2, 9 to 20), carnaval = true),
        // Decreto 28.637. Horário da bandeira 2 não achado.
        Cidade("Florianópolis", "SC", TabelaTaxi(v(7.00, 4.30, 5.10, 45.00), v(10.50, 4.80, 5.85, 45.00), "Aeroporto"),
            "out/2025", conferir = true, feriados = setOf(3 to 23)),
        // Decreto 46.615, 06/12/2024: sábado, domingo e feriado o dia todo.
        Cidade("Brasília", "DF", TabelaTaxi(v(6.59, 3.59, 4.61, 39.91), b2Sabado = 0), "dez/2024",
            feriados = setOf(11 to 30)),
        Cidade("Goiânia", "GO", TabelaTaxi(v(5.37, 3.23, 4.04, 26.92)), "jul/2024", conferir = true,
            feriados = setOf(5 to 24, 10 to 24)),
        Cidade("Campo Grande", "MS", TabelaTaxi(v(5.00, 3.86, 4.41, 38.60)), "set/2025", conferir = true,
            feriados = setOf(8 to 26, 10 to 11)),
        Cidade("Cuiabá", "MT", TabelaTaxi(v(6.00, 4.30, 6.00, 25.00)), "jul/2026", conferir = true,
            feriados = setOf(4 to 8, 11 to 20)),
        // Semob: fim de semana e dezembro inteiros em bandeira 2.
        Cidade("Salvador", "BA", TabelaTaxi(v(6.47, 3.23, 4.52, 32.33), b2De = 21, b2Sabado = 0),
            "2026", conferir = true, feriados = setOf(6 to 24, 7 to 2, 12 to 8), carnaval = true),
        // CTTU, 12/02/2026. O decreto ampliou o horário da bandeira 2: confira.
        Cidade("Recife", "PE", TabelaTaxi(v(5.12, 3.35, 4.05, 26.67), v(6.21, 4.07, 4.85, 26.67), "Especial"),
            "fev/2026", conferir = true, feriados = setOf(3 to 6, 6 to 24, 7 to 16, 12 to 8), carnaval = true),
        // Etufor, decreto 16.574, 19/01/2026. Bandeira 2 = km 30% mais caro.
        Cidade("Fortaleza", "CE", TabelaTaxi(v(6.82, 4.06, 5.28, 40.59), b2Sabado = 13), "jan/2026",
            feriados = setOf(3 to 19, 3 to 25, 8 to 15)),
        Cidade("Maceió", "AL", TabelaTaxi(v(4.79, 3.34, 4.00, 15.82), b2De = 22), "2025", conferir = true,
            feriados = setOf(6 to 24, 8 to 27, 9 to 16, 12 to 8)),
        Cidade("Aracaju", "SE", TabelaTaxi(v(5.30, 3.50, 4.20, 20.00)), "2025", conferir = true,
            feriados = setOf(3 to 17, 6 to 24, 7 to 8, 12 to 8)),
        Cidade("João Pessoa", "PB", TabelaTaxi(v(5.50, 3.50, 4.50, 35.00), b2Sabado = 13), "2026", conferir = true,
            feriados = setOf(8 to 5)),
        Cidade("Natal", "RN", TabelaTaxi(v(4.85, 3.00, 4.18, 30.55), b2De = 22, b2Ate = 5), "jun/2023", conferir = true,
            feriados = setOf(1 to 6, 10 to 3, 11 to 21)),
        Cidade("São Luís", "MA", TabelaTaxi(v(4.50, 2.80, 3.30, 28.00), b2Sabado = 12), "abr/2016", conferir = true,
            feriados = setOf(7 to 28, 9 to 8, 12 to 8)),
        Cidade("Belém", "PA", TabelaTaxi(v(5.61, 4.03, 4.84, 40.30)), "ago/2025", conferir = true,
            feriados = setOf(1 to 12, 8 to 15, 12 to 8)),
        Cidade("Manaus", "AM", TabelaTaxi(v(5.00, 3.12, 4.14, 48.00)), "jun/2022", conferir = true,
            feriados = setOf(9 to 5, 10 to 24, 12 to 8)),
        Cidade("Vitória", "ES", TabelaTaxi(v(6.20, 4.00, 4.80, 21.00), b2Sabado = 14), "2022", conferir = true,
            feriados = setOf(9 to 8)),
        Cidade("Rio Branco", "AC", TabelaTaxi(v(6.50, 5.51, 6.61, 36.74)), "dez/2025", conferir = true,
            feriados = setOf(1 to 23, 6 to 15, 8 to 6, 9 to 5, 11 to 17, 12 to 28)),
        Cidade("Macapá", "AP", TabelaTaxi(v(4.15, 3.00, 3.60, 32.00)), "antiga", conferir = true,
            feriados = setOf(2 to 4, 3 to 19, 9 to 13)),
    ).sortedBy { semAcento(it.nome) }

    fun achar(chave: String): Cidade? = TODAS.firstOrNull { it.chave == chave }

    /** Cidades que contêm o que foi digitado, sem ligar para acento e maiúscula ("sao" acha São Paulo e São Luís). */
    fun procurar(texto: String): List<Cidade> {
        val t = semAcento(texto).trim()
        if (t.isEmpty()) return TODAS
        return TODAS.filter { semAcento("${it.nome} ${it.uf}").contains(t) }
            .sortedBy { if (semAcento(it.nome).startsWith(t)) 0 else 1 }
    }

    /** A cidade da base com o nome que o mapa deu para onde o celular está, ou null. */
    fun pelaLocalidade(nome: String?): Cidade? {
        val n = semAcento(nome ?: return null).trim()
        return TODAS.firstOrNull { semAcento(it.nome) == n }
    }

    fun semAcento(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
}
