package app.corridaverde

import android.content.Context
import java.util.Locale

enum class TipoChave(val nome: String) { CELULAR("Celular"), CPF("CPF"), CNPJ("CNPJ"), EMAIL("E-mail"), ALEATORIA("Chave aleatória") }

/** A chave Pix do motorista, cadastrada uma vez. O nome e a cidade vão dentro do QR Code. */
data class ContaPix(val tipo: TipoChave = TipoChave.CELULAR, val chave: String = "", val nome: String = "", val cidade: String = "São Paulo") {
    val completa get() = chaveFormatada() != null && nome.isNotBlank()

    /**
     * A chave do jeito que o Banco Central pede dentro do QR: celular com +55, CPF e CNPJ só com
     * números, e-mail em minúsculas. Null se não parece uma chave daquele tipo.
     */
    fun chaveFormatada(): String? {
        val t = chave.trim()
        val digitos = t.filter(Char::isDigit)
        return when (tipo) {
            TipoChave.CELULAR -> when {
                digitos.length in 10..11 -> "+55$digitos"
                digitos.length in 12..13 && digitos.startsWith("55") -> "+$digitos"
                else -> null
            }
            TipoChave.CPF -> digitos.takeIf { it.length == 11 }
            TipoChave.CNPJ -> digitos.takeIf { it.length == 14 }
            TipoChave.EMAIL -> t.lowercase(Locale.ROOT).takeIf { '@' in it && '.' in it.substringAfter('@') && ' ' !in it }
            TipoChave.ALEATORIA -> t.lowercase(Locale.ROOT).takeIf { ALEATORIA.matches(it) }
        }
    }

    fun salvar(ctx: Context) {
        ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putString("tipo", tipo.name).putString("chave", chave).putString("nome", nome).putString("cidade", cidade)
            .apply()
    }

    companion object {
        private const val ARQUIVO = "pix"
        private val ALEATORIA = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""")

        fun carregar(ctx: Context): ContaPix {
            val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            val tipo = runCatching { TipoChave.valueOf(p.getString("tipo", null)!!) }.getOrDefault(TipoChave.CELULAR)
            return ContaPix(tipo, p.getString("chave", "") ?: "", p.getString("nome", "") ?: "", p.getString("cidade", "São Paulo") ?: "São Paulo")
        }
    }
}

/**
 * O "Pix Copia e Cola" (BR Code do Banco Central): o texto que vira o QR Code. Estático, com o valor
 * já preenchido; qualquer app de banco lê. Não passa por banco nenhum: o app só monta o texto.
 */
object BrCode {
    fun payload(chave: String, nome: String, cidade: String, valor: Double?, txid: String = "***"): String {
        val conta = campo("00", "br.gov.bcb.pix") + campo("01", chave)
        val semCrc = buildString {
            append(campo("00", "01"))
            append(campo("26", conta))
            append(campo("52", "0000"))
            append(campo("53", "986"))
            if (valor != null && valor > 0) append(campo("54", String.format(Locale.ROOT, "%.2f", valor)))
            append(campo("58", "BR"))
            append(campo("59", nome))
            append(campo("60", cidade))
            append(campo("62", campo("05", txid)))
            append("6304")
        }
        return semCrc + crc16(semCrc)
    }

    /** O que o QR leva da conta do motorista: nome e cidade sem acento, em maiúsculas e no tamanho máximo. */
    fun daConta(c: ContaPix, valor: Double?): String? {
        val chave = c.chaveFormatada() ?: return null
        return payload(chave, limpo(c.nome, 25), limpo(c.cidade.ifBlank { "São Paulo" }, 15), valor)
    }

    private fun limpo(s: String, max: Int) =
        LeitorGasto.semAcento(s).uppercase(Locale.ROOT).replace(Regex("""[^A-Z0-9 ]"""), "").replace(Regex("""\s+"""), " ").trim().take(max).trim()

    private fun campo(id: String, valor: String) = id + String.format(Locale.ROOT, "%02d", valor.length) + valor

    /** CRC16-CCITT (polinômio 1021, início FFFF), como manda o manual do BR Code. */
    fun crc16(s: String): String {
        var crc = 0xFFFF
        for (b in s.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return String.format(Locale.ROOT, "%04X", crc)
    }
}

/** "Gera um Pix de 70 reais", "QR Code de 70", "cria chave pix de 70", "cobrar 70 no pix". */
object LeitorPix {
    private val QR = Regex("""\bq\s?r\b|\bqr ?code\b|\bqrcode\b|\bcuar ?code\b|\bcu erre\b""")
    /** Pedidos, não relatos: "cobrar 70 no Pix" gera o QR; "cobrei 70 no Pix" é ganho e não gera. */
    private val PEDIDO = Regex("""\b(gera|gerar|gere|cria|criar|crie|faz|fazer|faca|mostra|mostrar|mostre|cobra|cobrar|cobranca|manda|mandar|passa|passar|monta|montar)\b""")
    private val PIX = Regex("""\bpix\b|\bpics\b|\bpicks\b""")
    private val COMECA_PIX = Regex("""^\s*(?:um\s+)?pix\s+de\s+\d""")

    fun ehPix(fala: String): Boolean {
        val t = LeitorGasto.semAcento(fala.lowercase(Locale.ROOT)).replace(' ', ' ')
        return QR.containsMatchIn(t) || (PIX.containsMatchIn(t) && PEDIDO.containsMatchIn(t)) || COMECA_PIX.containsMatchIn(t)
    }

    fun valor(fala: String): Double? = LeitorFala.valorDaFala(fala)
}
