package app.corridaverde

import android.content.Context
import java.time.LocalDate

data class Config(
    /** [TAXI] ou [APP]; vazio enquanto a pessoa não escolheu (primeira abertura). */
    val perfil: String = "",
    val luxo: Boolean = false,
    /**
     * Cidade do taxista ("São Paulo/SP"), vazia enquanto ele não escolheu. Fora da base ([Cidades]) é o nome que
     * ele digitou, com a tabela do táxi dele. Quem já usava o app antes das cidades fica com a de SP até escolher.
     */
    val cidade: String = "",
    /** A tabela que vale: a da cidade, ou a que o taxista corrigiu em Ajustes (a prefeitura mudou e o app não). */
    val tabela: TabelaTaxi = Cidades.SAO_PAULO.tabela,
    /** Padrões do taxista escolhidos pelo Paulo em 30/10/2026 (antes 100, 85 e 3,0 km). */
    val limiteVerde: Int = 80,
    val limiteAmarelo: Int = 70,
    val buscaMaxKm: Double = 2.0,
    val notaMinima: Double = 4.7,
    val viagemLongaKm: Double = 25.0,
    val posicaoY: Int = 60,
    /** Lado da tela do aviso da oferta: [CENTRO] (padrão), [ESQUERDA] ou [DIREITA]. O do radar não muda. */
    val ladoAviso: String = CENTRO,
    val diagnostico: Boolean = false,
    /** Até quando o modo diagnóstico fica ligado (24 h depois de ligar), para ninguém esquecer ele lendo a tela. */
    val diagnosticoAte: Long = 0,
    val gravar: Boolean = false,
    val radar: Boolean = true,
    val radarSom: Boolean = true,
    /** Aviso também nas ofertas da 99 (lidas por imagem). */
    val aviso99: Boolean = true,
    /** Bolinha Falar por cima da Uber e da 99. */
    val bolinha: Boolean = true,
    /**
     * Perfil de motorista de app: R$/km (busca + viagem) a partir do qual a oferta fica verde e amarela, por grupo.
     * Padrões provisórios (pesquisa de 01/10/2026; média do GigU de R$ 1,72/km), a conferir no teste com motoristas.
     */
    val verdeEconomico: Double = 2.0,
    val amareloEconomico: Double = 1.7,
    val verdeConforto: Double = 2.4,
    val amareloConforto: Double = 2.0,
    val verdePremium: Double = 3.2,
    val amareloPremium: Double = 2.7,
    /** Casa do motorista, para o modo "Indo pra casa". */
    val casaEndereco: String = "",
    val casa: Ponto? = null,
    val raioCasaKm: Double = 3.0,
    /** Dia em que o modo "Indo pra casa" foi ligado: ele desliga sozinho no dia seguinte. */
    val indoPraCasaDia: String = "",
) {
    val indoPraCasa get() = casa != null && indoPraCasaDia == LocalDate.now().toString()
    val motoristaDeApp get() = perfil == APP
    /** A cidade da base, para os feriados; sem cidade escolhida, São Paulo (como era antes das cidades). */
    val cidadeDaBase get() = if (cidade.isEmpty()) Cidades.SAO_PAULO else Cidades.achar(cidade)
    val nomeCidade get() = cidade.substringBefore('/')
    /** Falta o taxista dizer a cidade (a escolha aparece ao abrir o app). */
    val faltaCidade get() = !motoristaDeApp && cidade.isEmpty()

    fun verde(g: Grupo) = when (g) {
        Grupo.ECONOMICO -> verdeEconomico
        Grupo.CONFORTO -> verdeConforto
        Grupo.PREMIUM -> verdePremium
    }

    fun amarelo(g: Grupo) = when (g) {
        Grupo.ECONOMICO -> amareloEconomico
        Grupo.CONFORTO -> amareloConforto
        Grupo.PREMIUM -> amareloPremium
    }

    fun salvar(ctx: Context) {
        ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putString("perfil", perfil)
            .putBoolean("luxo", luxo)
            .putString("cidade", cidade)
            .apply { salvarTabela(this, tabela) }
            .putInt("limiteVerde", limiteVerde)
            .putInt("limiteAmarelo", limiteAmarelo)
            .putFloat("buscaMaxKm", buscaMaxKm.toFloat())
            .putFloat("notaMinima", notaMinima.toFloat())
            .putFloat("viagemLongaKm", viagemLongaKm.toFloat())
            .putInt("posicaoY", posicaoY)
            .putString("ladoAviso", ladoAviso)
            .putBoolean("diagnostico", diagnostico)
            .putLong("diagnosticoAte", diagnosticoAte)
            .putBoolean("gravar", gravar)
            .putBoolean("radar", radar)
            .putBoolean("radarSom", radarSom)
            .putBoolean("aviso99", aviso99)
            .putBoolean("bolinha", bolinha)
            .putFloat("verdeEconomico", verdeEconomico.toFloat())
            .putFloat("amareloEconomico", amareloEconomico.toFloat())
            .putFloat("verdeConforto", verdeConforto.toFloat())
            .putFloat("amareloConforto", amareloConforto.toFloat())
            .putFloat("verdePremium", verdePremium.toFloat())
            .putFloat("amareloPremium", amareloPremium.toFloat())
            .putString("casaEndereco", casaEndereco)
            .putString("casa", casa?.let { "${it.lat};${it.lon}" } ?: "")
            .putFloat("raioCasaKm", raioCasaKm.toFloat())
            .putString("indoPraCasaDia", indoPraCasaDia)
            .apply()
    }

    companion object {
        private const val ARQUIVO = "config"
        const val TAXI = "taxi"
        const val APP = "app"
        const val ESQUERDA = "esquerda"
        const val CENTRO = "centro"
        const val DIREITA = "direita"
        const val DURACAO_DIAGNOSTICO = 24 * 60 * 60_000L

        /**
         * Dia da primeira abertura (AAAA-MM-DD), de onde vão contar os 30 dias grátis. Fica no "config", que
         * entra no backup do Google e na cópia: quem desinstala e instala de novo recebe a data de volta e
         * não ganha outros 30 dias. Não faz parte do [Config] para o [salvar] nunca mexer nela.
         */
        private const val PRIMEIRO_USO = "primeiroUso"

        /** Grava hoje como primeira abertura se ainda não houver data, e devolve a data guardada. */
        fun marcarPrimeiroUso(ctx: Context): LocalDate {
            val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            p.getString(PRIMEIRO_USO, null)?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { return it } }
            val hoje = LocalDate.now()
            p.edit().putString(PRIMEIRO_USO, hoje.toString()).apply()
            return hoje
        }

        fun primeiroUso(ctx: Context): String? =
            ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).getString(PRIMEIRO_USO, null)

        /** Depois de restaurar uma cópia, fica a data mais antiga entre a deste celular e a da cópia. */
        fun manterPrimeiroUso(ctx: Context, antes: String?) {
            val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            maisAntiga(antes, p.getString(PRIMEIRO_USO, null))?.let { p.edit().putString(PRIMEIRO_USO, it).commit() }
        }

        /** A data mais antiga das duas (AAAA-MM-DD se compara como texto); ignora o que não for data. */
        fun maisAntiga(a: String?, b: String?): String? =
            listOfNotNull(a, b).filter { runCatching { LocalDate.parse(it) }.isSuccess }.minOrNull()

        private val VALORES = listOf("bandeirada", "km1", "km2", "hora")

        private fun salvarTabela(e: android.content.SharedPreferences.Editor, t: TabelaTaxi) {
            fun valores(prefixo: String, v: Valores?) = listOf(v?.bandeirada, v?.km1, v?.km2, v?.hora).zip(VALORES)
                .forEach { (n, nome) -> e.putString("$prefixo$nome", n?.toString() ?: "") }
            valores("tx_", t.comum)
            valores("txe_", t.especial)
            e.putString("tx_nomeEspecial", t.nomeEspecial)
                .putInt("tx_b2De", t.b2De).putInt("tx_b2Ate", t.b2Ate).putInt("tx_b2Sabado", t.b2Sabado)
                .putBoolean("tx_b2Dezembro", t.b2Dezembro)
        }

        /** Guardada como texto ("4.47"): em Float, 4,47 voltava 4,4699997. Sem tabela guardada, a de SP. */
        private fun carregarTabela(p: android.content.SharedPreferences): TabelaTaxi {
            val sp = Cidades.SAO_PAULO.tabela
            fun valores(prefixo: String): Valores? {
                val n = VALORES.map { p.getString("$prefixo$it", null)?.toDoubleOrNull() }
                return if (n.all { it != null }) Valores(n[0]!!, n[1]!!, n[2]!!, n[3]!!) else null
            }
            val comum = valores("tx_") ?: return sp
            return TabelaTaxi(
                comum = comum,
                especial = valores("txe_"),
                nomeEspecial = p.getString("tx_nomeEspecial", sp.nomeEspecial) ?: sp.nomeEspecial,
                b2De = p.getInt("tx_b2De", sp.b2De),
                b2Ate = p.getInt("tx_b2Ate", sp.b2Ate),
                b2Sabado = p.getInt("tx_b2Sabado", sp.b2Sabado),
                b2Dezembro = p.getBoolean("tx_b2Dezembro", sp.b2Dezembro),
            )
        }

        /** A cidade que o recibo e o Pix usam quando o motorista não digitou outra: a do taxímetro, ou São Paulo. */
        fun cidadePadrao(ctx: Context): String = carregar(ctx).nomeCidade.ifBlank { "São Paulo" }

        /**
         * Trocou a cidade do taxímetro: o recibo e o Pix acompanham, menos quando o motorista escreveu outra cidade
         * neles (a "região" do recibo, ou a cidade da conta no banco). Só troca o que estava vazio ou na cidade antiga.
         */
        fun acompanharCidade(ctx: Context, antiga: String, nova: String) {
            if (nova.isBlank()) return
            listOf("motorista", "pix").forEach { arquivo ->
                val p = ctx.getSharedPreferences(arquivo, Context.MODE_PRIVATE)
                val atual = p.getString("cidade", null)
                if (atual.isNullOrBlank() || Cidades.semAcento(atual.trim()) == Cidades.semAcento(antiga.trim())) {
                    p.edit().putString("cidade", nova).apply()
                }
            }
        }

        fun carregar(ctx: Context): Config {
            val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            val d = Config()
            // Guardado como Float: 4,7 voltava como 4,699999809265137 na tela de Ajustes. Arredonda a 2 casas.
            fun real(chave: String, padrao: Double) = Math.round(p.getFloat(chave, padrao.toFloat()) * 100.0) / 100.0
            return Config(
                perfil = p.getString("perfil", d.perfil) ?: "",
                luxo = p.getBoolean("luxo", d.luxo),
                cidade = p.getString("cidade", "") ?: "",
                tabela = carregarTabela(p),
                limiteVerde = p.getInt("limiteVerde", d.limiteVerde),
                limiteAmarelo = p.getInt("limiteAmarelo", d.limiteAmarelo),
                buscaMaxKm = real("buscaMaxKm", d.buscaMaxKm),
                notaMinima = real("notaMinima", d.notaMinima),
                viagemLongaKm = real("viagemLongaKm", d.viagemLongaKm),
                posicaoY = p.getInt("posicaoY", d.posicaoY),
                ladoAviso = p.getString("ladoAviso", d.ladoAviso) ?: d.ladoAviso,
                diagnostico = p.getBoolean("diagnostico", d.diagnostico) && System.currentTimeMillis() < p.getLong("diagnosticoAte", 0),
                diagnosticoAte = p.getLong("diagnosticoAte", 0),
                gravar = BuildConfig.GRAVACAO && p.getBoolean("gravar", d.gravar),
                radar = p.getBoolean("radar", d.radar),
                radarSom = p.getBoolean("radarSom", d.radarSom),
                aviso99 = p.getBoolean("aviso99", d.aviso99),
                bolinha = p.getBoolean("bolinha", d.bolinha),
                verdeEconomico = real("verdeEconomico", d.verdeEconomico),
                amareloEconomico = real("amareloEconomico", d.amareloEconomico),
                verdeConforto = real("verdeConforto", d.verdeConforto),
                amareloConforto = real("amareloConforto", d.amareloConforto),
                verdePremium = real("verdePremium", d.verdePremium),
                amareloPremium = real("amareloPremium", d.amareloPremium),
                casaEndereco = p.getString("casaEndereco", d.casaEndereco) ?: "",
                casa = p.getString("casa", "")?.split(';')?.takeIf { it.size == 2 }?.let { (a, b) ->
                    val lat = a.toDoubleOrNull()
                    val lon = b.toDoubleOrNull()
                    if (lat != null && lon != null) Ponto(lat, lon) else null
                },
                raioCasaKm = real("raioCasaKm", d.raioCasaKm),
                indoPraCasaDia = p.getString("indoPraCasaDia", "") ?: "",
            )
        }
    }
}
