package app.corridaverde

/**
 * Tamanho da logo no topo do recibo. Quadrada, fica como sempre foi; comprida (nome da empresa ao lado do símbolo)
 * ganha largura, senão sai com a altura de uma linha de texto. Sem Android aqui, para rodar nos testes.
 */
object LogoRecibo {
    private const val LARGURA_MAXIMA = 130f

    /** A partir daqui a logo conta como comprida e perde a margem em branco. */
    const val PROPORCAO_COMPRIDA = 1.5f

    fun comprida(largura: Int, altura: Int) = altura > 0 && largura >= altura * PROPORCAO_COMPRIDA

    /** Largura do espaço da logo: nunca menos que a de sempre ([minima]) e nunca por cima do texto ([livre]). */
    fun largura(larguraImagem: Int, alturaImagem: Int, alturaCaixa: Float, minima: Float, livre: Float): Float {
        val natural = larguraImagem.toFloat() / alturaImagem * alturaCaixa
        return natural.coerceAtMost(LARGURA_MAXIMA).coerceAtMost(livre).coerceAtLeast(minima)
    }

    /** O retângulo com desenho (esquerda, topo, direita e baixo, sem incluir os dois últimos); null se é tudo branco. */
    fun recorte(px: IntArray, largura: Int, altura: Int): IntArray? {
        var esquerda = largura
        var topo = altura
        var direita = -1
        var baixo = -1
        for (y in 0 until altura) for (x in 0 until largura) {
            if (fundo(px[y * largura + x])) continue
            if (x < esquerda) esquerda = x
            if (x > direita) direita = x
            if (y < topo) topo = y
            if (y > baixo) baixo = y
        }
        return if (direita < 0) null else intArrayOf(esquerda, topo, direita + 1, baixo + 1)
    }

    /** Transparente ou quase branco (o JPEG nunca deixa o branco perfeito). */
    private fun fundo(p: Int): Boolean {
        val menor = minOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        return (p ushr 24) < 16 || menor >= 235
    }
}
