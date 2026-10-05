package app.corridaverde

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Os vídeos das corridas ficam em Movies/Corrida Verde, onde a galeria os encontra.
 * Passando de [LIMITE_BYTES], os mais antigos são apagados, como numa câmera veicular.
 * Os vídeos que o motorista guardou ficam fora da conta e nunca são apagados.
 */
object Videos {
    const val PASTA = "Corrida Verde"
    const val LIMITE_BYTES = 5L * 1024 * 1024 * 1024

    /** [chave] é o endereço na galeria (content://) ou o caminho do arquivo. */
    data class Video(val chave: String, val bytes: Long, val criado: Long)

    /** Arquivo aberto para o gravador escrever. */
    class Destino(val fd: ParcelFileDescriptor, val uri: Uri?, val arquivo: File?) {
        val chave get() = uri?.toString() ?: arquivo!!.path
    }

    /** Os mais antigos saem primeiro, até o total caber no limite. Os [guardados] não entram na conta. */
    fun quaisApagar(videos: List<Video>, limite: Long = LIMITE_BYTES, guardados: Set<String> = emptySet()): List<Video> {
        val livres = videos.filter { it.chave !in guardados }
        var total = livres.sumOf { it.bytes }
        val apagar = mutableListOf<Video>()
        for (v in livres.sortedBy { it.criado }) {
            if (total <= limite) break
            apagar += v
            total -= v.bytes
        }
        return apagar
    }

    /** O menor tamanho com o lado maior de pelo menos 640 (480p); se a câmera não chegar a isso, o maior que ela tiver. */
    fun escolherTamanho(tamanhos: List<Pair<Int, Int>>): Pair<Int, Int> =
        tamanhos.filter { maxOf(it.first, it.second) >= 640 }.minByOrNull { it.first * it.second }
            ?: tamanhos.maxBy { it.first * it.second }

    fun novo(ctx: Context): Destino {
        val nome = "corrida_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val valores = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, nome)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$PASTA")
                // Escondido da galeria até o pedaço terminar de gravar.
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valores)!!
            return Destino(ctx.contentResolver.openFileDescriptor(uri, "w")!!, uri, null)
        }
        val f = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), nome)
        return Destino(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE), null, f)
    }

    /** Fecha o arquivo: se a gravação deu certo, mostra na galeria; se não, apaga. */
    fun concluir(ctx: Context, d: Destino, ok: Boolean) {
        runCatching { d.fd.close() }
        if (!ok) {
            d.uri?.let { runCatching { ctx.contentResolver.delete(it, null, null) } }
            d.arquivo?.delete()
            return
        }
        if (Build.VERSION.SDK_INT >= 29) d.uri?.let { ctx.contentResolver.update(it, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }
    }

    fun limpar(ctx: Context) {
        val todos = todos(ctx)
        val guardados = guardados(ctx)
        // O motorista apagou na galeria: esquece.
        val existem = todos.map { it.chave }.toSet()
        if (guardados.any { it !in existem }) salvarGuardados(ctx, guardados.filter { it in existem }.toSet())
        quaisApagar(todos, guardados = guardados).forEach {
            if (it.chave.startsWith("content:")) ctx.contentResolver.delete(Uri.parse(it.chave), null, null) else File(it.chave).delete()
        }
    }

    fun guardar(ctx: Context, chaves: Collection<String>) = salvarGuardados(ctx, guardados(ctx) + chaves)

    private fun guardados(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(GUARDADOS, emptySet())!!.toSet()

    private fun salvarGuardados(ctx: Context, chaves: Set<String>) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(GUARDADOS, chaves).apply()

    private const val PREFS = "videos"
    private const val GUARDADOS = "guardados"

    private fun todos(ctx: Context): List<Video> {
        if (Build.VERSION.SDK_INT < 29) {
            return ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.listFiles().orEmpty()
                .map { Video(it.path, it.length(), it.lastModified()) }
        }
        val lista = mutableListOf<Video>()
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ctx.contentResolver.query(
            base,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED),
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("${Environment.DIRECTORY_MOVIES}/$PASTA%"),
            null,
        )?.use { c ->
            while (c.moveToNext()) lista += Video(ContentUris.withAppendedId(base, c.getLong(0)).toString(), c.getLong(1), c.getLong(2))
        }
        return lista
    }
}
