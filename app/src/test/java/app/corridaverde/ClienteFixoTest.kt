package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ClienteFixoTest {
    private val ida = Trajeto("Ida", "Residência", "Aeroporto de Congonhas", 130.0)
    private val santaCasa = Trajeto("Ida e volta", "Residência", "Santa Casa, Indaiatuba", 600.0, "Pedágios incluídos")
    private val recibo = Recibo(
        12, LocalDateTime.of(2026, 10, 3, 9, 30), 730.0, "Marta Oliveira", pagamento = "Pix", telefone = "11987654321",
        documento = "123.456.789-00",
        corridas = listOf(CorridaRecibo(LocalDate.of(2026, 9, 29), ida), CorridaRecibo(LocalDate.of(2026, 9, 30), santaCasa)),
    )

    @Test
    fun reciboComCorridasVaiEVoltaDoArquivo() {
        assertEquals(recibo, Recibos.deLinha(Recibos.paraLinha(recibo)))
        assertTrue(recibo.varias)
    }

    @Test
    fun reciboAntigoSemAsColunasNovasContinuaLendo() {
        val antigo = "7\t2026-10-01T21:05\t50.0\tMaria\tPaulista\tVila Mariana\tPix\t\t\t"
        val r = Recibos.deLinha(antigo)!!
        assertEquals("Vila Mariana", r.ate)
        assertEquals("", r.documento)
        assertFalse(r.varias)
    }

    @Test
    fun fraseETextoComAsCorridas() {
        assertEquals(
            "Recebi de Marta Oliveira (CPF/CNPJ 123.456.789-00) a quantia de R$ 730,00 (setecentos e trinta reais), " +
                "em 03/10/2026, referente às seguintes corridas de táxi:",
            Recibos.frase(recibo),
        )
        val t = Recibos.texto(recibo, Motorista("João da Silva", "ABC1D23"))
        assertTrue("• 29/09/2026 – (Ida) Residência → Aeroporto de Congonhas – R$ 130,00" in t)
        assertTrue("• 30/09/2026 – (Ida e volta) Residência → Santa Casa, Indaiatuba – Pedágios incluídos – R$ 600,00" in t)
        assertTrue("Total: R$ 730,00" in t)
    }

    @Test
    fun observacaoDaCorridaEInformacoesAdicionaisVaoEVoltamDoArquivo() {
        val r = recibo.copy(
            corridas = listOf(
                CorridaRecibo(LocalDate.of(2026, 11, 10), ida, "Espera de 20 min"),
                CorridaRecibo(LocalDate.of(2026, 11, 11), santaCasa),
            ),
            info = "Motorista: Diego",
        )
        assertEquals(r, Recibos.deLinha(Recibos.paraLinha(r)))
        val t = Recibos.texto(r, Motorista("Diego", "ABC1D23"))
        assertTrue("• 10/11/2026 – (Ida) Residência → Aeroporto de Congonhas – Espera de 20 min – R$ 130,00" in t)
        assertTrue("Motorista: Diego" in t)
    }

    @Test
    fun observacoesDoTrajetoEDaCorridaSeJuntam() {
        assertEquals("Pedágios incluídos · Espera de 20 min", CorridaRecibo(LocalDate.of(2026, 11, 10), santaCasa, "Espera de 20 min").observacao)
        assertEquals("Pedágios incluídos", CorridaRecibo(LocalDate.of(2026, 11, 10), santaCasa).observacao)
        assertEquals("", CorridaRecibo(LocalDate.of(2026, 11, 10), ida).observacao)
    }

    @Test
    fun reciboDeClienteFixoAntigoSemObservacaoNemInformacoes() {
        // Linha gravada antes de 14/11/2026: corridas com 6 campos (sem a observação) e sem a coluna das informações.
        val corridasAntigas = Lista.juntar(recibo.corridas.map { listOf(it.dia.toString()) + Lista.trajeto(it.trajeto) })
        val colunas = Recibos.paraLinha(recibo).split('\t').take(11) + corridasAntigas
        val r = Recibos.deLinha(colunas.joinToString("\t"))!!
        assertEquals("", r.info)
        assertEquals("", r.corridas[0].obs)
        assertEquals(ida, r.corridas[0].trajeto)
    }

    @Test
    fun descricaoDoTrajeto() {
        assertEquals("(Ida) Residência → Aeroporto de Congonhas", ida.descricao)
        assertEquals("Aeroporto", Trajeto("", "", "Aeroporto", 10.0).descricao)
    }

    @Test
    fun clienteVaiEVoltaDoArquivo() {
        val c = Cliente(1759500000000, "Marta Oliveira", "11987654321", "marta@exemplo.com", "", listOf(ida, santaCasa))
        assertEquals(c, Clientes.deLinha(Clientes.paraLinha(c)))
        val semTrajeto = Cliente(2, "Mário Alves")
        assertEquals(semTrajeto, Clientes.deLinha(Clientes.paraLinha(semTrajeto)))
    }

    @Test
    fun textoDigitadoNaoQuebraOArquivo() {
        val estranho = Trajeto("Ida", "Rua A\tcom tab", "Rua B\ncom quebra", 10.0, "obs\u001Fcom separador")
        val c = Cliente(3, "Ana\tMaria", trajetos = listOf(estranho))
        val lido = Clientes.deLinha(Clientes.paraLinha(c))!!
        assertEquals("Ana Maria", lido.nome)
        assertEquals("Rua A com tab", lido.trajetos[0].de)
        assertEquals("Rua B com quebra", lido.trajetos[0].ate)
        assertEquals("obscom separador", lido.trajetos[0].obs)
    }

    @Test
    fun buscaSemAcentoNemMaiuscula() {
        val todos = listOf(Cliente(1, "Mário Alves"), Cliente(2, "Marta Oliveira"), Cliente(3, "José Lima"))
        assertEquals(listOf("Mário Alves", "Marta Oliveira"), Clientes.buscar(todos, "mar").map { it.nome })
        assertEquals(listOf("Mário Alves"), Clientes.buscar(todos, "MARIO").map { it.nome })
        assertEquals(3, Clientes.buscar(todos, "  ").size)
    }
}
