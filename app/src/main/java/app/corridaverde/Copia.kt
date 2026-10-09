package app.corridaverde

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Cópia dos dados do motorista num arquivo .zip que ele guarda onde quiser (Drive, WhatsApp, e-mail):
 * ganhos, gastos, recibos, logo, assinatura e ajustes. Serve para trocar de celular e para passar da
 * versão do GitHub para a da Play, que não atualiza por cima. O backup automático do Google faz o mesmo
 * sozinho, mas só uma vez por dia e só se estiver ligado no celular.
 */
object Copia {
    /** Só estes arquivos entram e saem da cópia; nada de diagnóstico nem vídeo. */
    private val ARQUIVOS = listOf(
        "corridas.tsv", "totais.tsv", "gastos.tsv", "recibos.tsv", "clientes.tsv", "destinos.tsv", "recibo_logo.png", "recibo_assinatura.png",
    )
    private val PREFERENCIAS = listOf("config", "motorista", "recibo_estilo", "pix")
    private const val MARCA = "corrida-verde-copia.json"

    /** O que a cópia tem, para o motorista conferir antes de restaurar. */
    class Resumo(val feitaEm: String, val lancamentos: Int, val recibos: Int)

    fun nomeDoArquivo() = "CorridaVerde-copia-${LocalDateTime.now().toLocalDate()}.zip"

    fun gerar(ctx: Context, saida: OutputStream) {
        ZipOutputStream(saida).use { zip ->
            val prefs = JSONObject()
            PREFERENCIAS.forEach { nome ->
                val valores = JSONObject()
                ctx.getSharedPreferences(nome, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                    // Guarda o tipo junto, para devolver do mesmo jeito (Int não pode virar Long).
                    val (tipo, valor) = when (v) {
                        is String -> "s" to v
                        is Int -> "i" to v
                        is Long -> "l" to v
                        is Float -> "f" to v.toDouble()
                        is Boolean -> "b" to v
                        is Set<*> -> "c" to JSONArray(v.map { it.toString() })
                        else -> return@forEach
                    }
                    valores.put(k, JSONObject().put("t", tipo).put("v", valor))
                }
                prefs.put(nome, valores)
            }
            val marca = JSONObject()
                .put("app", "Corrida Verde")
                .put("versao", Atualizador.versaoAtual)
                .put("feitaEm", LocalDateTime.now().withNano(0).toString())
                .put("lancamentos", contarLinhas(ctx, "corridas.tsv") + contarLinhas(ctx, "totais.tsv") + contarLinhas(ctx, "gastos.tsv"))
                .put("recibos", contarLinhas(ctx, "recibos.tsv"))
                .put("preferencias", prefs)
            zip.putNextEntry(ZipEntry(MARCA))
            zip.write(marca.toString(1).toByteArray())
            zip.closeEntry()
            ARQUIVOS.map { File(ctx.filesDir, it) }.filter { it.isFile }.forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Lê a cópia sem mudar nada: null se o arquivo não é uma cópia do Corrida Verde. */
    fun conferir(entrada: InputStream): Resumo? = ler(entrada)?.first?.let {
        Resumo(it.optString("feitaEm").replace('T', ' ').take(16), it.optInt("lancamentos"), it.optInt("recibos"))
    }

    /** Troca os dados deste celular pelos da cópia. Devolve false se o arquivo não é uma cópia válida. */
    fun restaurar(ctx: Context, entrada: InputStream): Boolean {
        val (marca, arquivos) = ler(entrada) ?: return false
        // Arquivos: os que não vieram na cópia são apagados, para não misturar dados dos dois celulares.
        ARQUIVOS.forEach { nome ->
            val f = File(ctx.filesDir, nome)
            val conteudo = arquivos[nome]
            if (conteudo == null) f.delete() else f.writeBytes(conteudo)
        }
        val prefs = marca.optJSONObject("preferencias") ?: JSONObject()
        val primeiroUso = Config.primeiroUso(ctx)
        PREFERENCIAS.forEach { nome ->
            val e = ctx.getSharedPreferences(nome, Context.MODE_PRIVATE).edit().clear()
            prefs.optJSONObject(nome)?.let { valores ->
                valores.keys().forEach { k ->
                    val item = valores.getJSONObject(k)
                    when (item.getString("t")) {
                        "s" -> e.putString(k, item.getString("v"))
                        "i" -> e.putInt(k, item.getInt("v"))
                        "l" -> e.putLong(k, item.getLong("v"))
                        "f" -> e.putFloat(k, item.getDouble("v").toFloat())
                        "b" -> e.putBoolean(k, item.getBoolean("v"))
                        "c" -> e.putStringSet(k, item.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                    }
                }
            }
            e.commit()
        }
        // Uma cópia antiga ou sem a data não pode recomeçar os 30 dias grátis.
        Config.manterPrimeiroUso(ctx, primeiroUso)
        return true
    }

    /** A marca e os arquivos conhecidos de dentro do zip; qualquer outra coisa é ignorada. */
    private fun ler(entrada: InputStream): Pair<JSONObject, Map<String, ByteArray>>? = runCatching {
        var marca: JSONObject? = null
        val arquivos = mutableMapOf<String, ByteArray>()
        ZipInputStream(entrada).use { zip ->
            while (true) {
                val item = zip.nextEntry ?: break
                when (item.name) {
                    MARCA -> marca = JSONObject(zip.readBytes().decodeToString())
                    in ARQUIVOS -> arquivos[item.name] = zip.readBytes()
                }
            }
        }
        marca?.takeIf { it.optString("app") == "Corrida Verde" }?.let { it to arquivos }
    }.getOrNull()

    private fun contarLinhas(ctx: Context, nome: String) =
        File(ctx.filesDir, nome).takeIf { it.isFile }?.useLines { l -> l.count { it.isNotBlank() } } ?: 0
}
