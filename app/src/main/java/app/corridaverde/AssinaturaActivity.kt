package app.corridaverde

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Grava a assinatura do motorista, que depois sai em todo recibo. Dois jeitos: assinar com o dedo,
 * com o celular deitado para sobrar espaço, ou fotografar uma assinatura feita com caneta no papel
 * (o app recorta e tira o fundo do papel).
 */
class AssinaturaActivity : Activity() {
    private var foto: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_FOTO, false)) telaFoto() else telaDedo()
    }

    private fun telaDedo() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val quadro = QuadroAssinatura(this)
        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(Color.rgb(0xEE, 0xEE, 0xEE))
            fitsSystemWindows = true
        }
        tela.addView(texto("Assine sobre a linha com o dedo, como no papel.", 16f))
        tela.addView(quadro, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(6) })
        val botoes = LinearLayout(this)
        botoes.addView(botao("Cancelar") { finish() }, peso())
        botoes.addView(botao("Apagar e refazer") { quadro.limpar() }, peso())
        botoes.addView(botao("Salvar") {
            val b = quadro.imagem()
            if (b == null) Toast.makeText(this, "Assine no quadro primeiro", Toast.LENGTH_SHORT).show() else salvar(b)
        }, peso())
        tela.addView(botoes)
        setContentView(tela)
    }

    private fun telaFoto() {
        val tela = coluna()
        tela.addView(texto("Foto da assinatura no papel", 22f, negrito = true))
        tela.addView(texto(
            "1. Assine com caneta azul ou preta numa folha branca, sem linhas.\n" +
                "2. Fotografe de perto, com boa luz e sem sombra do celular em cima.\n" +
                "3. Deixe só a assinatura na foto, sem a mesa em volta.\n\n" +
                "O app recorta e tira o branco do papel. Você confere antes de salvar.",
            17f,
        ).apply { setPadding(0, dp(12), 0, dp(12)) })
        tela.addView(botao("📷 Tirar a foto agora") { tirarFoto() })
        tela.addView(botao("🖼️ Escolher uma foto que já tirei") {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), GALERIA)
        })
        tela.addView(botao("Cancelar") { finish() })
        setContentView(tela)
    }

    /** O resultado da foto, já limpo, para o motorista conferir. */
    private fun telaConferir(b: Bitmap) {
        foto = b
        val tela = coluna()
        tela.addView(texto("Ficou boa?", 22f, negrito = true))
        tela.addView(texto("É assim que a assinatura vai sair no recibo.", 16f).apply { setPadding(0, dp(4), 0, dp(10)) })
        tela.addView(ImageView(this).apply {
            setImageBitmap(b)
            adjustViewBounds = true
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(180)))
        tela.addView(botao("Salvar esta assinatura") { foto?.let { salvar(it) } })
        tela.addView(botao("Tentar outra foto") { telaFoto() })
        setContentView(tela)
    }

    private fun tirarFoto() {
        // Como o app declara a câmera (gravação da corrida), o Android exige a permissão até para abrir a câmera dele.
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA)
            return
        }
        val destino = arquivoFoto().apply { delete() }
        val uri = ArquivosProvider.uri(destino)
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivityForResult(i, FOTO) }
            .onFailure { Toast.makeText(this, "Não achei o app da câmera", Toast.LENGTH_LONG).show() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) tirarFoto()
        else Toast.makeText(this, "Sem a câmera, escolha uma foto que você já tirou", Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = when (requestCode) {
            FOTO -> Uri.fromFile(arquivoFoto())
            GALERIA -> data?.data ?: return
            else -> return
        }
        val b = runCatching { abrir(uri) }.getOrNull()
        if (b == null) {
            Toast.makeText(this, "Não deu para abrir a foto", Toast.LENGTH_LONG).show()
            return
        }
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        val r = LimpaAssinatura.limpar(px, b.width, b.height)
        if (r == null) {
            Toast.makeText(this, "Não achei a assinatura na foto. Tente de mais perto, com mais luz.", Toast.LENGTH_LONG).show()
            return
        }
        telaConferir(Bitmap.createBitmap(r.pixels, r.largura, r.altura, Bitmap.Config.ARGB_8888))
    }

    /** Abre a foto já reduzida (até ~1600 px) e de pé, conforme o celular estava na hora da foto. */
    private fun abrir(uri: Uri): Bitmap? {
        val tamanho = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, tamanho) }
        var amostra = 1
        while (maxOf(tamanho.outWidth, tamanho.outHeight) / (amostra * 2) >= 1600) amostra *= 2
        val b = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: return null
        val giro = contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (giro == 0f) return b
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(giro) }, true)
    }

    private fun salvar(b: Bitmap) {
        EstiloRecibo.guardar(EstiloRecibo.arquivoAssinatura(this), b)
        arquivoFoto().delete()
        Toast.makeText(this, "Assinatura salva: os recibos já saem assinados", Toast.LENGTH_LONG).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun arquivoFoto() = File(ArquivosProvider.pasta(this), ArquivosProvider.FOTO_ASSINATURA)

    private fun coluna() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        setBackgroundColor(Color.WHITE)
        fitsSystemWindows = true
    }

    private fun botao(t: String, acao: () -> Unit) = Button(this).apply {
        text = t
        textSize = 17f
        minHeight = dp(56)
        setOnClickListener { acao() }
    }

    private fun peso() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun texto(t: String, tamanho: Float, negrito: Boolean = false) = TextView(this).apply {
        text = t
        textSize = tamanho
        gravity = Gravity.START
        setTextColor(Color.rgb(0x1A, 0x1A, 0x1A))
        if (negrito) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_FOTO = "foto"
        private const val FOTO = 1
        private const val GALERIA = 2
        private const val CAMERA = 3
    }
}

/**
 * Tira o papel da foto de uma assinatura: o que é bem mais escuro que o papel em volta vira tinta,
 * o resto fica transparente, e a imagem é recortada em volta da tinta. Funciona com luz desigual
 * porque compara cada pedaço da foto com o papel daquele pedaço.
 */
object LimpaAssinatura {
    class Recorte(val pixels: IntArray, val largura: Int, val altura: Int)

    fun limpar(px: IntArray, w: Int, h: Int): Recorte? {
        if (w < 16 || h < 16) return null
        val lum = IntArray(w * h) { i -> val c = px[i]; ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000 }
        val papel = papel(lum, w, h)
        // Escuridão de cada ponto em relação ao papel: 0 = papel, 1 = preto.
        val escuro = FloatArray(w * h) { i -> (1f - lum[i] / maxOf(papel[i], 1f)).coerceIn(0f, 1f) }

        // Ignora uma beiradinha da foto, onde costumam aparecer a mesa e a sombra da folha.
        val bx = w * 3 / 100
        val by = h * 3 / 100
        val linhas = IntArray(h)
        val colunas = IntArray(w)
        var tinta = 0
        var r = 0L; var g = 0L; var b = 0L
        for (y in by until h - by) for (x in bx until w - bx) {
            val i = y * w + x
            if (escuro[i] > LIMIAR) {
                linhas[y]++; colunas[x]++; tinta++
                r += px[i] shr 16 and 255; g += px[i] shr 8 and 255; b += px[i] and 255
            }
        }
        val area = (w - 2 * bx) * (h - 2 * by)
        if (tinta < area / 2000 || tinta > area * 3 / 10) return null

        // Recorte: só linhas e colunas com tinta de verdade (pontinhos soltos não contam).
        val minimo = 2
        val y0 = linhas.indexOfFirst { it >= minimo }
        val y1 = linhas.indexOfLast { it >= minimo }
        val x0 = colunas.indexOfFirst { it >= minimo }
        val x1 = colunas.indexOfLast { it >= minimo }
        if (y0 < 0 || x0 < 0 || y1 <= y0 || x1 <= x0) return null
        val folga = maxOf(4, (x1 - x0) / 40)
        val ex = maxOf(0, x0 - folga); val dx = minOf(w - 1, x1 + folga)
        val ey = maxOf(0, y0 - folga); val dy = minOf(h - 1, y1 + folga)
        val rw = dx - ex + 1
        val rh = dy - ey + 1

        // Cor da tinta: a média dos pontos de tinta, escurecida para sair firme no recibo.
        val cr = (r / tinta).toInt(); val cg = (g / tinta).toInt(); val cb = (b / tinta).toInt()
        val l = maxOf(1, (cr * 299 + cg * 587 + cb * 114) / 1000)
        val f = minOf(1f, 60f / l)
        val cor = ((cr * f).toInt() shl 16) or ((cg * f).toInt() shl 8) or (cb * f).toInt()

        val saida = IntArray(rw * rh)
        for (y in 0 until rh) for (x in 0 until rw) {
            val e = escuro[(ey + y) * w + ex + x]
            val a = ((e - SUAVE) / (LIMIAR + 0.2f - SUAVE)).coerceIn(0f, 1f)
            saida[y * rw + x] = ((a * 255).toInt() shl 24) or cor
        }
        return Recorte(saida, rw, rh)
    }

    /** O brilho do papel em cada ponto: o tom claro típico de cada bloco da foto, suavizado com os vizinhos. */
    private fun papel(lum: IntArray, w: Int, h: Int): FloatArray {
        val lado = maxOf(8, maxOf(w, h) / 16)
        val nx = (w + lado - 1) / lado
        val ny = (h + lado - 1) / lado
        val bloco = FloatArray(nx * ny)
        val hist = IntArray(256)
        for (by in 0 until ny) for (bx in 0 until nx) {
            hist.fill(0)
            var n = 0
            for (y in by * lado until minOf(h, (by + 1) * lado)) for (x in bx * lado until minOf(w, (bx + 1) * lado)) { hist[lum[y * w + x]]++; n++ }
            // 90% dos pontos do bloco são mais escuros que isto: é o papel, mesmo com tinta no meio.
            var acum = 0
            var v = 255
            while (v > 0) { acum += hist[v]; if (acum >= n / 10) break; v-- }
            bloco[by * nx + bx] = v.toFloat()
        }
        val suave = FloatArray(nx * ny) { i ->
            val bx = i % nx; val by = i / nx
            var s = 0f; var n = 0
            for (yy in maxOf(0, by - 1)..minOf(ny - 1, by + 1)) for (xx in maxOf(0, bx - 1)..minOf(nx - 1, bx + 1)) { s += bloco[yy * nx + xx]; n++ }
            maxOf(bloco[i], s / n)
        }
        return FloatArray(w * h) { i -> suave[(i / w / lado) * nx + (i % w) / lado] }
    }

    /** Acima disto é tinta; entre [SUAVE] e [LIMIAR] a borda do traço fica meio transparente. */
    private const val LIMIAR = 0.28f
    private const val SUAVE = 0.12f
}
