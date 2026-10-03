package app.corridaverde

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Entrega os PDFs dos recibos (e a cópia dos dados) ao Gmail, ao WhatsApp e a outros apps, só para leitura e só
 * com a permissão dada no envio. A única escrita aceita é a da câmera na foto da assinatura.
 * Faz o papel do FileProvider sem precisar do AndroidX.
 */
class ArquivosProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        if ('w' in mode && uri.lastPathSegment == FOTO_ASSINATURA) {
            val f = File(pasta(ctx), FOTO_ASSINATURA)
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode) or ParcelFileDescriptor.MODE_CREATE)
        }
        return ParcelFileDescriptor.open(arquivo(ctx, uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val f = arquivo(context ?: throw FileNotFoundException(), uri)
        val colunas = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(colunas, 1).apply {
            addRow(colunas.map { if (it == OpenableColumns.SIZE) f.length() else if (it == OpenableColumns.DISPLAY_NAME) f.name else null })
        }
    }

    override fun getType(uri: Uri) = when (uri.lastPathSegment?.substringAfterLast('.')) {
        "jpg" -> "image/jpeg"
        "zip" -> "application/zip"
        else -> "application/pdf"
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        private const val AUTORIDADE = "app.corridaverde.arquivos"
        const val FOTO_ASSINATURA = "assinatura-foto.jpg"

        fun pasta(ctx: Context) = File(ctx.cacheDir, "recibos").apply { mkdirs() }

        fun uri(f: File): Uri = Uri.parse("content://$AUTORIDADE/${Uri.encode(f.name)}")

        /** Só arquivos da pasta dos recibos, pelo nome (sem subpastas). */
        private fun arquivo(ctx: Context, uri: Uri): File {
            val nome = uri.lastPathSegment ?: throw FileNotFoundException()
            if ('/' in nome || nome.startsWith(".")) throw FileNotFoundException()
            return File(pasta(ctx), nome).takeIf { it.isFile } ?: throw FileNotFoundException()
        }
    }
}
