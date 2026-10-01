package app.corridaverde

/** Um radar avisado pelo navegador: a distância até ele e, se aparecer, o limite da via. */
data class Radar(val metros: Int, val limite: Int?, val tipo: String)

object LeitorRadar {
    // "Radar de velocidade em 210 m" (Waze), "Radar de semáforo e velocidade a 286 m" (99).
    private val RADAR = Regex("""radar( de [\p{L} ]{3,40}?)?\s+(?:em|a|à)\s+(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    // "50 Limite" (99 e Uber) ou "Limite de velocidade 60".
    private val LIMITE = Regex("""\b(\d{2,3})\s*(?:km/h\s*)?limite\b|\blimite(?: de velocidade)?:?\s*(\d{2,3})\b""", RegexOption.IGNORE_CASE)

    fun ler(textos: List<String>): Radar? {
        // O botão "Radar de Viagens" da Uber não é radar de trânsito.
        val tela = textos.joinToString(" ") { it.replace('\n', ' ') }.replace(Regex("Radar de Viagens", RegexOption.IGNORE_CASE), " ")
        val m = RADAR.find(tela) ?: return null
        val numero = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        val metros = if (m.groupValues[3].equals("km", ignoreCase = true)) (numero * 1000).toInt() else numero.toInt()
        if (metros > 3000) return null
        val limite = LIMITE.find(tela)?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() }
            ?.takeIf { it in 20..130 && it % 10 == 0 }
        return Radar(metros, limite, m.groupValues[1].trim().removePrefix("de ").trim().ifEmpty { "velocidade" })
    }
}

/** Decide quando o radar é outro (toca o som de novo) e quando é o mesmo chegando mais perto. */
class AcompanhaRadar {
    private var ultimo: Radar? = null
    private var hora = 0L

    /** Devolve true se é um radar novo. */
    fun visto(r: Radar, agoraMs: Long): Boolean {
        val antes = ultimo
        val novo = antes == null || agoraMs - hora > 15_000 || r.metros > antes.metros + 150
        ultimo = r
        hora = agoraMs
        return novo
    }
}

/**
 * No Google Maps o aviso de radar é confiável só de longe: perto do radar o número pula
 * (550 m → 80 m em 7 s, com o radar ainda a ~400 m) e depois o aviso some antes de passar.
 * Por isso a distância segue numa conta própria pela velocidade: leitura que cai mais rápido
 * do que o carro anda é ignorada, e sem aviso o alerta continua até a conta passar do radar.
 */
class EstimaRadar {
    private var radar: Radar? = null
    private var metros = 0.0
    private var hora = 0L
    private var ultimaLeitura = 0L
    private var kmh = 0

    /** [lido] é o aviso na tela agora (ou null); devolve o radar a mostrar, ou null se já passou. */
    fun atualizar(lido: Radar?, velocidade: Int?, agoraMs: Long): Radar? {
        val atual = radar
        if (atual != null) metros -= kmh / 3.6 * (agoraMs - hora) / 1000.0
        hora = agoraMs
        if (velocidade != null) kmh = velocidade
        if (lido != null) {
            if (atual == null || lido.metros > metros + OUTRO_RADAR || lido.metros >= metros - PULO) {
                radar = lido
                metros = lido.metros.toDouble()
            } else {
                // Pulou para perto demais: fica a conta, mas guarda o limite se veio.
                radar = atual.copy(limite = lido.limite ?: atual.limite)
            }
            ultimaLeitura = agoraMs
        }
        val r = radar ?: return null
        if (metros < -PASSOU || agoraMs - ultimaLeitura > MAXIMO_SEM_AVISO) {
            radar = null
            return null
        }
        return r.copy(metros = metros.coerceAtLeast(0.0).toInt())
    }

    private companion object {
        const val OUTRO_RADAR = 500
        const val PULO = 150
        /** Folga depois da conta chegar a zero: a velocidade lida e o GPS não são exatos. */
        const val PASSOU = 40
        const val MAXIMO_SEM_AVISO = 90_000L
    }
}

object LeitorVelocidade {
    // "Velocidade atual 44 quilômetros por hora" (Google Maps).
    private val VELOCIDADE = Regex("""velocidade atual (\d{1,3}) quil""", RegexOption.IGNORE_CASE)

    fun ler(textos: List<String>): Int? = textos.firstNotNullOfOrNull { VELOCIDADE.find(it)?.groupValues?.get(1)?.toIntOrNull() }
}
