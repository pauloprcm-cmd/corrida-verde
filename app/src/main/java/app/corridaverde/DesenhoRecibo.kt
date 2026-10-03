package app.corridaverde

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Desenha o recibo numa folha A5 (420 × 595 pontos, meia folha A4), num dos cinco modelos. Na tela do
 * celular o PDF abre na largura da tela, então a folha menor, cheia, deixa a letra maior para o passageiro. O mesmo desenho vira a
 * prévia na tela (para conferir e para personalizar) e o PDF que vai para o passageiro.
 */
object DesenhoRecibo {
    const val LARGURA = 420
    const val ALTURA = 595
    private val CLARO = Color.rgb(0xF4, 0xF4, 0xF4)
    private val LINHA = Color.rgb(0xB8, 0xB8, 0xB8)
    private const val MARGEM = 36f
    private val PRETO = Color.rgb(0x1A, 0x1A, 0x1A)
    private val CINZA = Color.rgb(0x6B, 0x70, 0x75)
    private val DATA_LONGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

    /** Tudo o que o desenho precisa, junto. */
    class Folha(val r: Recibo, val m: Motorista, val e: EstiloRecibo, val logo: Bitmap?, val assinatura: Bitmap?)

    fun folha(ctx: Context, r: Recibo, m: Motorista, e: EstiloRecibo = EstiloRecibo.carregar(ctx)) =
        Folha(r, m, e, EstiloRecibo.logo(ctx), EstiloRecibo.assinatura(ctx))

    fun desenhar(c: Canvas, f: Folha) {
        c.drawColor(Color.WHITE)
        marcaDagua(c, f)
        when (f.e.modelo) {
            Modelo.TABELA -> return desenharTabela(c, f)
            Modelo.COMPLETO -> return desenharCompleto(c, f)
            else -> {}
        }
        val topo = when (f.e.modelo) {
            Modelo.CLASSICO -> cabecalhoClassico(c, f)
            Modelo.MODERNO -> cabecalhoModerno(c, f)
            else -> cabecalhoSimples(c, f)
        }
        val simples = f.e.modelo == Modelo.SIMPLES
        val largura = LARGURA - 2 * MARGEM
        var y = topo
        if (!simples) {
            // Caixa com o valor, como nos bloquinhos.
            val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = f.e.cor }
            c.drawRoundRect(MARGEM, y, LARGURA - MARGEM, y + 44f, 6f, 6f, borda)
            c.drawText("Valor", MARGEM + 12f, y + 27f, pincel(12f, cor = CINZA))
            c.drawText("R$ ${Popup.br(f.r.valor)}", LARGURA - MARGEM - 12f, y + 30f,
                pincel(22f, negrito = true, cor = f.e.cor).apply { textAlign = Paint.Align.RIGHT })
            y += 62f
        } else {
            c.drawText("Valor: R$ ${Popup.br(f.r.valor)}", MARGEM, y + 18f, pincel(18f, negrito = true))
            y += 34f
        }
        val corpo = if (simples) 14f else 13f
        y = paragrafo(c, Recibos.frase(f.r), pincel(corpo), y, largura) + 10f
        if (f.r.pagamento.isNotBlank()) y = paragrafo(c, "Forma de pagamento: ${f.r.pagamento}.", pincel(corpo), y, largura) + 6f
        f.r.substitui?.let {
            y = paragrafo(c, "Este recibo substitui o nº ${String.format(Locale.ROOT, "%04d", it)}.", pincel(11f, cor = CINZA), y, largura) + 6f
        }
        y += 6f
        c.drawText("${f.m.cidade.ifBlank { "São Paulo" }}, ${f.r.quando.toLocalDate().format(DATA_LONGA)}.", MARGEM, y + 13f, pincel(corpo))

        val pe = rodape(c, f)
        assinatura(c, f, pe)
    }

    /** A logo personalizada bem clarinha no meio da folha, por trás de tudo. */
    private fun marcaDagua(c: Canvas, f: Folha) {
        val logo = f.logo ?: return
        if (!f.e.marcaDagua || f.e.modelo == Modelo.SIMPLES) return
        val w = LARGURA.toFloat()
        val h = ALTURA.toFloat()
        val lado = w * 0.62f
        val caixa = RectF((w - lado) / 2, (h - lado) / 2, (w + lado) / 2, (h + lado) / 2)
        desenharImagem(c, logo, caixa, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = 22 })
    }

    /** Modelo Tabela: logo e "Recibo Nº" no topo, dados em linhas, tabela da corrida e as duas assinaturas embaixo. */
    private fun desenharTabela(c: Canvas, f: Folha) {
        val w = LARGURA.toFloat()
        val m = 30f
        var esquerda = m
        f.logo?.let { desenharImagem(c, it, RectF(m, 24f, m + 48f, 72f)); esquerda += 58f }
        c.drawText(f.e.titulo, esquerda, 58f, pincel(23f, negrito = true))
        c.drawText("Nº ${numero(f.r)}", w - m, 58f, pincel(18f, negrito = true).apply { textAlign = Paint.Align.RIGHT })
        c.drawLine(m, 84f, w - m, 84f, Paint().apply { color = LINHA; strokeWidth = 1f })

        var y = 94f
        listOfNotNull(
            "Recibo nº:" to numero(f.r),
            "Data:" to f.r.quando.toLocalDate().format(DATA_LONGA),
            f.r.passageiro.takeIf { it.isNotBlank() }?.let { "Cliente:" to it },
            "Cidade:" to f.m.cidade.ifBlank { "São Paulo" },
        ).forEach { (rotulo, valor) -> y = paragrafo(c, marcado("**$rotulo** $valor"), pincel(11.5f), y, w - 2 * m, m) + 3f }

        // Tabela da corrida: DESCRIÇÃO | VALOR | QTD | TOTAL.
        y += 12f
        val col = floatArrayOf(m, m + (w - 2 * m) * 0.42f, m + (w - 2 * m) * 0.62f, m + (w - 2 * m) * 0.78f, w - m)
        val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = LINHA; strokeWidth = 0.8f }
        val cabeca = 24f
        c.drawRect(m, y, w - m, y + cabeca, Paint().apply { color = CLARO })
        listOf("DESCRIÇÃO", "VALOR", "QTD", "TOTAL").forEachIndexed { i, t ->
            c.drawText(t, col[i] + 6f, y + 16f, pincel(10f, negrito = true, cor = f.e.cor))
        }
        val topoTabela = y
        y += cabeca
        val texto = descricaoComData(f.r)
        val desc = StaticLayout.Builder.obtain(texto, 0, texto.length, pincel(9.5f, negrito = true), (col[1] - col[0] - 10f).toInt())
            .setLineSpacing(1.5f, 1f).build()
        val alturaLinha = maxOf(30f, desc.height + 12f)
        c.save(); c.translate(col[0] + 6f, y + 6f); desc.draw(c); c.restore()
        val meio = y + alturaLinha / 2 + 3.5f
        val valor = "R$ ${Popup.br(f.r.valor)}"
        c.drawText(valor, col[1] + 6f, meio, pincel(10f))
        c.drawText("1 corrida", col[2] + 6f, meio, pincel(10f))
        c.drawText(valor, col[3] + 6f, meio, pincel(10f))
        y += alturaLinha
        c.drawRect(m, topoTabela, w - m, y, borda)
        c.drawLine(m, topoTabela + cabeca, w - m, topoTabela + cabeca, borda)
        for (i in 1..3) c.drawLine(col[i], topoTabela, col[i], y, borda)

        // Embaixo da tabela: confirmação e pagamento à esquerda, total à direita.
        y += 12f
        val total = marcado("**Valor total:** $valor")
        val lTotal = StaticLayout.Builder.obtain(total, 0, total.length, pincel(11.5f), 150)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE).build()
        c.save(); c.translate(w - m - 150f, y); lTotal.draw(c); c.restore()
        val extenso = StaticLayout.Builder.obtain("(${Extenso.reais(f.r.valor)})", 0, "(${Extenso.reais(f.r.valor)})".length, pincel(8.5f, cor = CINZA), 150)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE).build()
        c.save(); c.translate(w - m - 150f, y + lTotal.height + 2f); extenso.draw(c); c.restore()
        val larguraTexto = w - 2 * m - 160f
        y = paragrafo(c, "Confirmamos o recebimento total do serviço descrito neste recibo.", pincel(10f), y, larguraTexto, m) + 5f
        y = paragrafo(c, marcado("**Condições de pagamento:** À vista"), pincel(10f), y, larguraTexto, m) + 1f
        if (f.r.pagamento.isNotBlank()) y = paragrafo(c, marcado("**Forma de pagamento:** ${f.r.pagamento}"), pincel(10f), y, larguraTexto, m) + 1f
        f.r.substitui?.let { paragrafo(c, "Este recibo substitui o nº ${String.format(Locale.ROOT, "%04d", it)}.", pincel(9.5f, cor = CINZA), y + 5f, larguraTexto, m) }

        // Pé: Pix/frase e página; acima, as assinaturas (passageiro à esquerda, motorista à direita).
        c.drawText("Página 1/1", w - m, ALTURA - 18f, pincel(8f, cor = CINZA).apply { textAlign = Paint.Align.RIGHT })
        val pe = rodapeLargo(c, f, ALTURA - 32f, m)
        val dados = listOfNotNull(
            f.m.documento.takeIf { it.isNotBlank() },
            f.m.placa.takeIf { it.isNotBlank() }?.let { "Placa $it" },
            f.m.cidade.takeIf { it.isNotBlank() },
        )
        val linhaAssin = pe - 18f - dados.size * 13f - 16f
        val comPassageiro = f.r.passageiro.isNotBlank()
        val doMotorista = if (comPassageiro) w * 0.71f else w / 2
        val traco = Paint().apply { color = LINHA; strokeWidth = 1f }
        if (comPassageiro) {
            val x = w * 0.29f
            c.drawLine(x - 85f, linhaAssin, x + 85f, linhaAssin, traco)
            c.drawText(f.r.passageiro, x, linhaAssin + 16f, pincel(10.5f, negrito = true).centro())
        }
        f.assinatura?.let { desenharImagem(c, it, RectF(doMotorista - 70f, linhaAssin - 46f, doMotorista + 70f, linhaAssin - 3f)) }
        c.drawLine(doMotorista - 85f, linhaAssin, doMotorista + 85f, linhaAssin, traco)
        c.drawText(f.m.marca.uppercase(), doMotorista, linhaAssin + 16f, pincel(10.5f, negrito = true).centro())
        dados.forEachIndexed { i, t -> c.drawText(t, doMotorista, linhaAssin + 30f + i * 13f, pincel(9.5f).centro()) }
    }

    /**
     * Modelo Completo: dados do motorista no topo, faixa "Recibo" com a data, declaração, tabela de serviços e
     * total. A data e a assinatura ficam presas no pé, para a folha sair cheia.
     */
    private fun desenharCompleto(c: Canvas, f: Folha) {
        val w = LARGURA.toFloat()
        val m = 20f
        // Topo: nome do táxi, nome do motorista, documento, região e contatos; a logo à direita.
        var y = 32f
        f.logo?.let { desenharImagem(c, it, RectF(w - m - 54f, 14f, w - m, 68f)) }
        c.drawText(f.m.marca.uppercase(), m, y, pincel(15f))
        y += 15f
        if (f.m.fantasia.isNotBlank() && f.m.nome.isNotBlank()) { c.drawText(f.m.nome.uppercase(), m, y, pincel(9.5f)); y += 13f }
        f.m.documento.takeIf { it.isNotBlank() }?.let { c.drawText("CPF/CNPJ: $it", m, y, pincel(9.5f)); y += 13f }
        f.m.cidade.takeIf { it.isNotBlank() }?.let { c.drawText(it, m, y, pincel(9.5f)); y += 13f }
        val contatos = listOfNotNull(
            f.m.telefone.takeIf { it.isNotBlank() }?.let { "Tel. $it" },
            f.m.email.takeIf { it.isNotBlank() },
            f.m.instagram.takeIf { it.isNotBlank() },
        )
        if (contatos.isNotEmpty()) {
            y = paragrafo(c, contatos.joinToString("  ·  "), pincel(8.5f, cor = CINZA), y - 8f, w - 2 * m - 60f, m) + 8f
        }
        y = maxOf(y, 72f) + 4f

        // Faixa com o título no meio e a data à direita.
        c.drawRect(m, y, w - m, y + 24f, Paint().apply { color = Color.rgb(0xF6, 0xF6, 0xF6) })
        c.drawText(f.e.titulo, w / 2, y + 17f, pincel(14f, negrito = true).centro())
        c.drawText(Recibos.data(f.r), w - m - 6f, y + 16f, pincel(9.5f).apply { textAlign = Paint.Align.RIGHT })
        y += 36f

        val de = f.r.passageiro.takeIf { it.isNotBlank() }?.let { ", de **$it**" } ?: ""
        val declaracao = "Declaro que recebi na data de **${f.r.quando.toLocalDate().format(DATA_LONGA)}**, o valor de " +
            "**R$ ${Popup.br(f.r.valor)}** (${Extenso.reais(f.r.valor)})$de, referente aos seguintes serviços:"
        y = paragrafo(c, marcado(declaracao), pincel(10.5f), y, w - 2 * m, m) + 12f

        c.drawText("Serviços", w / 2, y + 10f, pincel(12f, negrito = true).centro())
        y += 18f
        // Cabeçalho da tabela na cor escolhida, letras brancas.
        val x = floatArrayOf(m + 6f, w * 0.56f, w * 0.70f, w * 0.81f, w - m - 6f)
        c.drawRoundRect(m, y, w - m, y + 20f, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = f.e.cor })
        c.drawText("Descrição", x[0], y + 14f, pincel(9f, negrito = true, cor = Color.WHITE))
        c.drawText("Preço", x[1], y + 14f, pincel(9f, negrito = true, cor = Color.WHITE).centro())
        c.drawText("Unidade", x[2], y + 14f, pincel(9f, negrito = true, cor = Color.WHITE).centro())
        c.drawText("Quant.", x[3], y + 14f, pincel(9f, negrito = true, cor = Color.WHITE).centro())
        c.drawText("Total", x[4], y + 14f, pincel(9f, negrito = true, cor = Color.WHITE).apply { textAlign = Paint.Align.RIGHT })
        y += 23f
        val descricao = Recibos.descricao(f.r)
        val desc = StaticLayout.Builder.obtain(descricao, 0, descricao.length, pincel(8.5f), (x[1] - x[0] - 28f).toInt()).build()
        val alto = desc.height + 18f
        c.drawRoundRect(m, y, w - m, y + alto, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xEE, 0xEE, 0xEE) })
        c.save(); c.translate(x[0], y + 4f); desc.draw(c); c.restore()
        c.drawText("${Recibos.data(f.r)} ${Recibos.hora(f.r)}", x[0], y + desc.height + 13f, pincel(7.5f))
        val valor = "R$ ${Popup.br(f.r.valor)}"
        val linha = y + 14f
        c.drawText(valor, x[1], linha, pincel(8.5f).centro())
        c.drawText("Corrida", x[2], linha, pincel(8.5f).centro())
        c.drawText("1", x[3], linha, pincel(8.5f).centro())
        c.drawText(valor, x[4], linha, pincel(8.5f).apply { textAlign = Paint.Align.RIGHT })
        y += alto + 18f

        c.drawText("Subtotal serviços", m, y, pincel(10.5f, negrito = true))
        c.drawText(valor, w - m, y, pincel(10.5f, negrito = true).apply { textAlign = Paint.Align.RIGHT })
        y += 12f
        // Caixa do total, na metade direita.
        val caixa = w * 0.46f
        c.drawRect(caixa, y, w - m, y + 22f, Paint().apply { color = Color.rgb(0xF6, 0xF6, 0xF6) })
        c.drawText("Subtotal", caixa + 6f, y + 15f, pincel(10.5f, cor = CINZA))
        c.drawText(valor, w - m - 6f, y + 15f, pincel(10.5f, cor = CINZA).apply { textAlign = Paint.Align.RIGHT })
        y += 24f
        c.drawRoundRect(caixa, y, w - m, y + 26f, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = f.e.cor })
        c.drawText("Total", caixa + 6f, y + 18f, pincel(13f, cor = Color.WHITE))
        c.drawText(valor, w - m - 6f, y + 18f, pincel(13f, cor = Color.WHITE).apply { textAlign = Paint.Align.RIGHT })
        y += 38f

        if (f.r.pagamento.isNotBlank()) {
            c.drawRect(m, y, w - m, y + 22f, Paint().apply { color = Color.rgb(0xF6, 0xF6, 0xF6) })
            c.drawText("Forma de pagamento", m + 6f, y + 15f, pincel(10.5f, negrito = true))
            c.drawText(f.r.pagamento, m + 6f, y + 36f, pincel(9.5f))
            y += 48f
        }
        f.r.substitui?.let { c.drawText("Este recibo substitui o nº ${String.format(Locale.ROOT, "%04d", it)}.", m + 6f, y, pincel(9f, cor = CINZA)) }

        // No pé: Pix/frase; acima, a data, a assinatura e os dados do motorista, no centro.
        val pe = rodapeLargo(c, f, ALTURA - 18f, m)
        val linhas = listOfNotNull(
            f.m.nome.takeIf { f.m.fantasia.isNotBlank() && it.isNotBlank() }?.uppercase(),
            f.m.placa.takeIf { it.isNotBlank() }?.let { "Placa: $it" },
        )
        val linhaAssin = pe - 16f - linhas.size * 12f - 8f
        f.assinatura?.let { desenharImagem(c, it, RectF(w / 2 - 70f, linhaAssin - 42f, w / 2 + 70f, linhaAssin - 3f)) }
        c.drawText(Recibos.data(f.r), w / 2, linhaAssin - 50f, pincel(10f).centro())
        c.drawLine(w / 2 - 85f, linhaAssin, w / 2 + 85f, linhaAssin, Paint().apply { color = PRETO; strokeWidth = 0.7f })
        c.drawText(f.m.marca.uppercase(), w / 2, linhaAssin + 13f, pincel(10.5f).centro())
        linhas.forEachIndexed { i, t -> c.drawText(t, w / 2, linhaAssin + 26f + i * 12f, pincel(if (i == linhas.lastIndex) 8f else 9f).centro()) }
    }

    /** Pix e frase do motorista no pé da folha, terminando em [fim]. Devolve onde o pé começa. */
    private fun rodapeLargo(c: Canvas, f: Folha, fim: Float, margem: Float): Float {
        val linhas = listOfNotNull(f.e.pix.takeIf { it.isNotBlank() }?.let { "Pix: $it" }, f.e.rodape.takeIf { it.isNotBlank() })
        if (linhas.isEmpty()) return fim
        val texto = linhas.joinToString("\n")
        val l = StaticLayout.Builder.obtain(texto, 0, texto.length, pincel(9f, cor = CINZA), (LARGURA - 2 * margem).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(1.5f, 1f).build()
        val y = fim - l.height
        c.save(); c.translate(margem, y); l.draw(c); c.restore()
        return y - 8f
    }

    private fun descricaoComData(r: Recibo) = "${Recibos.descricao(r)}\n${Recibos.data(r)}"

    /** Texto com trechos em negrito marcados entre **asteriscos duplos**. */
    private fun marcado(texto: String): CharSequence {
        val sb = SpannableStringBuilder()
        texto.split("**").forEachIndexed { i, parte ->
            val ini = sb.length
            sb.append(parte)
            if (i % 2 == 1) sb.setSpan(StyleSpan(Typeface.BOLD), ini, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun TextPaint.centro() = apply { textAlign = Paint.Align.CENTER }

    private fun cabecalhoClassico(c: Canvas, f: Folha): Float {
        // Moldura dupla na cor escolhida, como os recibos de papel.
        val moldura = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = f.e.cor }
        moldura.strokeWidth = 2f
        c.drawRect(14f, 14f, LARGURA - 14f, ALTURA - 14f, moldura)
        moldura.strokeWidth = 0.7f
        c.drawRect(19f, 19f, LARGURA - 19f, ALTURA - 19f, moldura)
        var esquerda = MARGEM
        f.logo?.let { desenharImagem(c, it, RectF(MARGEM, MARGEM - 4f, MARGEM + 52f, MARGEM + 48f)); esquerda += 62f }
        c.drawText(f.e.titulo.uppercase(), esquerda, MARGEM + 20f, pincel(19f, negrito = true, cor = f.e.cor))
        c.drawText(subtitulo(f), esquerda, MARGEM + 38f, pincel(11f, cor = CINZA))
        c.drawText("Nº ${numero(f.r)}", LARGURA - MARGEM, MARGEM + 20f, pincel(14f, negrito = true).apply { textAlign = Paint.Align.RIGHT })
        return MARGEM + 62f
    }

    private fun cabecalhoModerno(c: Canvas, f: Folha): Float {
        // Faixa colorida no topo, com o título e o número em branco.
        c.drawRect(0f, 0f, LARGURA.toFloat(), 78f, Paint().apply { color = f.e.cor })
        var esquerda = MARGEM
        f.logo?.let {
            c.drawRoundRect(MARGEM - 4f, 13f, MARGEM + 52f, 65f, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            desenharImagem(c, it, RectF(MARGEM, 17f, MARGEM + 48f, 61f))
            esquerda += 64f
        }
        c.drawText(f.e.titulo, esquerda, 40f, pincel(20f, negrito = true, cor = Color.WHITE))
        c.drawText(subtitulo(f), esquerda, 58f, pincel(11f, cor = Color.argb(220, 255, 255, 255)))
        c.drawText("Nº ${numero(f.r)}", LARGURA - MARGEM, 44f, pincel(15f, negrito = true, cor = Color.WHITE).apply { textAlign = Paint.Align.RIGHT })
        return 100f
    }

    private fun cabecalhoSimples(c: Canvas, f: Folha): Float {
        c.drawText(f.e.titulo, MARGEM, MARGEM + 20f, pincel(20f, negrito = true))
        c.drawText("Nº ${numero(f.r)}", LARGURA - MARGEM, MARGEM + 20f, pincel(14f, negrito = true).apply { textAlign = Paint.Align.RIGHT })
        c.drawLine(MARGEM, MARGEM + 32f, LARGURA - MARGEM, MARGEM + 32f, Paint().apply { color = PRETO; strokeWidth = 1f })
        return MARGEM + 48f
    }

    /** Chave Pix e a frase do motorista, no pé da folha. Devolve onde o pé começa. */
    private fun rodape(c: Canvas, f: Folha): Float {
        val linhas = listOfNotNull(f.e.pix.takeIf { it.isNotBlank() }?.let { "Pix: $it" }, f.e.rodape.takeIf { it.isNotBlank() })
        if (linhas.isEmpty()) return ALTURA - MARGEM
        val p = pincel(10.5f, cor = if (f.e.modelo == Modelo.SIMPLES) CINZA else f.e.cor)
        val l = StaticLayout.Builder.obtain(linhas.joinToString("\n"), 0, linhas.joinToString("\n").length, p, (LARGURA - 2 * MARGEM).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(2f, 1f).build()
        val y = ALTURA - MARGEM - l.height
        if (f.e.modelo == Modelo.MODERNO) c.drawLine(MARGEM, y - 8f, LARGURA - MARGEM, y - 8f, Paint().apply { color = f.e.cor; strokeWidth = 1f })
        c.save()
        c.translate(MARGEM, y)
        l.draw(c)
        c.restore()
        return y - 14f
    }

    /** Assinatura (se o motorista fez uma), a linha e os dados dele, acima do pé. */
    private fun assinatura(c: Canvas, f: Folha, pe: Float) {
        val dados = Recibos.dadosDoMotorista(f.m)
        var base = pe - dados.size * 14f - 8f
        f.assinatura?.let { desenharImagem(c, it, RectF(LARGURA / 2f - 90f, base - 52f, LARGURA / 2f + 90f, base - 4f)) }
        c.drawLine(MARGEM + 40f, base, LARGURA - MARGEM - 40f, base, Paint().apply { color = PRETO; strokeWidth = 1f })
        base += 16f
        val centro = pincel(12f, negrito = true).apply { textAlign = Paint.Align.CENTER }
        val centroCinza = pincel(10.5f, cor = CINZA).apply { textAlign = Paint.Align.CENTER }
        dados.forEachIndexed { i, linha ->
            c.drawText(linha, LARGURA / 2f, base, if (i == 0) centro else centroCinza)
            base += 14f
        }
    }

    /** A prévia: o mesmo desenho, com [larguraPx] pixels de largura. */
    fun imagem(f: Folha, larguraPx: Int = 1050): Bitmap {
        val escala = larguraPx.toFloat() / LARGURA
        val b = Bitmap.createBitmap(larguraPx, (ALTURA * escala).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(escala, escala)
        desenhar(c, f)
        return b
    }

    /** Grava o PDF na pasta de envio do app e devolve o arquivo. */
    fun pdf(ctx: Context, f: Folha): File {
        val doc = PdfDocument()
        val pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, 1).create())
        desenhar(pagina.canvas, f)
        doc.finishPage(pagina)
        val arquivo = File(ArquivosProvider.pasta(ctx), "Recibo-${f.r.numeroTexto}.pdf")
        arquivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        return arquivo
    }

    private fun subtitulo(f: Folha) = if (f.e.titulo.contains("táxi", ignoreCase = true)) "" else "Corrida de táxi"

    private fun numero(r: Recibo) = if (r.numero > 0) r.numeroTexto else "----"

    /** Desenha a imagem dentro de [caixa] sem deformar, centrada. */
    private fun desenharImagem(
        c: Canvas, b: Bitmap, caixa: RectF, p: Paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
    ) {
        val escala = minOf(caixa.width() / b.width, caixa.height() / b.height)
        val w = b.width * escala
        val h = b.height * escala
        val x = caixa.left + (caixa.width() - w) / 2
        val y = caixa.top + (caixa.height() - h) / 2
        c.drawBitmap(b, null, RectF(x, y, x + w, y + h), p)
    }

    private fun paragrafo(c: Canvas, texto: CharSequence, p: TextPaint, y: Float, largura: Float, x: Float = MARGEM): Float {
        val l = StaticLayout.Builder.obtain(texto, 0, texto.length, p, largura.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f, 1f)
            .build()
        c.save()
        c.translate(x, y)
        l.draw(c)
        c.restore()
        return y + l.height
    }

    private fun pincel(tamanho: Float, negrito: Boolean = false, cor: Int = PRETO) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tamanho
        color = cor
        typeface = if (negrito) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
