package app.corridaverde

import android.content.Context
import java.io.File

/** Passageiro fixo do taxista, cadastrado uma vez, com os trajetos que costuma fazer. */
data class Cliente(
    val id: Long,
    val nome: String,
    val telefone: String = "",
    val email: String = "",
    /** CPF ou CNPJ, para o recibo de reembolso; opcional. */
    val documento: String = "",
    val trajetos: List<Trajeto> = emptyList(),
)

/** Os clientes fixos, um por linha em clientes.tsv (entra no backup do Google e na cópia). */
object Clientes {
    private const val ARQUIVO = "clientes.tsv"

    fun todos(ctx: Context): List<Cliente> {
        val f = File(ctx.filesDir, ARQUIVO)
        return if (f.exists()) f.readLines().mapNotNull { deLinha(it) } else emptyList()
    }

    /** Guarda o cliente novo ou troca o que tem o mesmo [Cliente.id]. */
    fun salvar(ctx: Context, c: Cliente) = gravar(ctx, todos(ctx).filter { it.id != c.id } + c)

    fun apagar(ctx: Context, id: Long) = gravar(ctx, todos(ctx).filter { it.id != id })

    private fun gravar(ctx: Context, lista: List<Cliente>) {
        File(ctx.filesDir, ARQUIVO).writeText(lista.joinToString("") { paraLinha(it) + "\n" })
    }

    /** Os que têm [busca] no nome, sem ligar para acento nem maiúscula, em ordem alfabética. */
    fun buscar(lista: List<Cliente>, busca: String): List<Cliente> {
        val b = simples(busca.trim())
        return lista.filter { b.isEmpty() || b in simples(it.nome) }.sortedBy { simples(it.nome) }
    }

    private fun simples(s: String) = LeitorGasto.semAcento(s.lowercase())

    fun paraLinha(c: Cliente) =
        (listOf(c.id.toString(), c.nome, c.telefone, c.email, c.documento).map { Lista.limpo(it) } +
            Lista.juntar(c.trajetos.map { Lista.trajeto(it) })).joinToString("\t")

    fun deLinha(l: String): Cliente? = runCatching {
        val c = l.split('\t')
        Cliente(c[0].toLong(), c[1], c[2], c[3], c[4], Lista.separar(c.getOrElse(5) { "" }).map { Lista.trajeto(it) })
    }.getOrNull()
}
