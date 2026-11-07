package app.corridaverde

import app.corridaverde.LeitorOferta.Cartao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Textos do OCR copiados dos diagnósticos de 30/10/2026 (versão da Play, oferta da Uber lida do print). */
class PrintDaUberTest {
    private fun ler(texto: String) = LeitorOferta.lerUberDoPrint(texto.split(" | "))

    @Test
    fun pontoQueSumiuNaViagemVoltaPeloTotal() {
        // 17:38, cartão parado e nítido: "7.2 km" saiu "72 km" em todas as leituras (o aviso deu 10% e 1 parada).
        val o = ler("NEIROS | JARDIM | AMÉRICA | 270 | Táxi Promo | R$ 34,90 | 38 min • 8.9 km | e 10 min 1.7 km | " +
            "R. Ribeiro de Barros, Vila Anglo | Brasileira, Säo Paulo | 4,95 (3287) | Verificado | 28 minutos• 72 km | " +
            "Av. Doutora Ruth Cardoso, São Paulo | Tenho interesse | Bur | CioCARA | FLORA")!!
        assertEquals(34.90, o.valor, 0.001)
        assertEquals(1.7, o.buscaKm, 0.001)
        assertEquals(10, o.buscaMin)
        assertEquals(7.2, o.viagemKm, 0.001)
        assertEquals(28, o.viagemMin)
        assertEquals(0, o.paradas)
        assertEquals("Av. Doutora Ruth Cardoso, São Paulo", o.destino)
    }

    @Test
    fun pontoQueSumiuNoTotalNaoViraBusca() {
        // 17:05:37: o total "7.2 km" saiu "72 km" e virou a busca (com sobras do mapa entre o valor e o total).
        val o = ler("BELA VISTA | JA | M 2 Táxi Promo | IM | R$ 25,98 | PA | 40 min •72 km | 8 16 min 2.5 km | " +
            "R. Santa Efigênia, Santa | Efigênia, São Paulo | ★ 4,86 (790) | 24 minutos • 4.7 km | " +
            "R. Bela Cintra, 1958, Consolação, São | Paulo | Tenho interesse | Casa de Tote")!!
        assertEquals(2.5, o.buscaKm, 0.001)
        assertEquals(16, o.buscaMin)
        assertEquals(4.7, o.viagemKm, 0.001)
        assertEquals(24, o.viagemMin)
        assertEquals(0, o.paradas)
        // 18:56 (do print do aviso): "7.8 km" do total saiu "78 km".
        val promo = ler("Táxi Promo | R$ 35,19 | 38 min • 78 km | 5 min • 0.9 km | Shopping Iguatemi, Ponto 1 - Av. | " +
            "Brig. Faria Lima, 2.232, Jardim Europa | 4,88 (273) | 33 minutos • 6.9 km | Cobertura do Mac Usp, 1301, Moema, | " +
            "São Paulo | Tenho interesse")!!
        assertEquals(0.9, promo.buscaKm, 0.001)
        assertEquals(6.9, promo.viagemKm, 0.001)
        assertEquals(33, promo.viagemMin)
        assertEquals(0, promo.paradas)
    }

    @Test
    fun corridasDe30DeOutubroLidasCerto() {
        val c2 = ler("Táxi Promo | R$ 25,66 | 28 min • 6.3 km | 8 9 min •1.3 km | Pç. Mal. Deodoro, Santa Cecilia, Sã | " +
            "Paulo | ★ 4,86 (2805) | Verificado | 19 minutos• 5.0 km | Rua Aimbere, 279, Pompéia, São | Paulo | Iniciar navegação")!!
        assertEquals(1.3, c2.buscaKm, 0.001)
        assertEquals(5.0, c2.viagemKm, 0.001)
        assertEquals(19, c2.viagemMin)
        val c4 = ler("EUROPA | Táxi | R$ 24- R$ 34 | 23 min • 4.7 km | a 2 min • 0.3 km | R. Colômbia, Jardim Paulista, São | " +
            "Paulo | * 4,84 (1195) | Verificado | R | 21 minutos• 4.4 km | R. Cardeal Arcoverde, 3060, | Pinheiros, São Paulo | " +
            "Tenho interesse | MONÇOES | INDIAOPOLIS | VILAIoRDEIR")!!
        assertEquals(24.0, c4.valor, 0.001)
        assertEquals(34.0, c4.valorMax!!, 0.001)
        assertEquals(0.3, c4.buscaKm, 0.001)
        assertEquals(4.4, c4.viagemKm, 0.001)
        assertEquals(21, c4.viagemMin)
        // Corrida 1 (do print do aviso): 1 parada, viagem de mais de uma hora.
        val c1 = ler("Táxi Promo | R$ 58,19 | 1 h e 9 min • 12.4 km | 2 min • 0.6 km | Rua José Alencar Castelo Branco, | " +
            "Lapa, São Paulo | 4,77 (1721) | Verificado | 1 parada | 1 h e 7 min • 11.8 km | R. Barão de Tatuí, 183, Vila Buarque, | " +
            "São Paulo | Viagem longa (mais de 45 min) | Tenho interesse")!!
        assertEquals(0.6, c1.buscaKm, 0.001)
        assertEquals(11.8, c1.viagemKm, 0.001)
        assertEquals(67, c1.viagemMin)
        assertEquals(1, c1.paradas)
    }

    @Test
    fun somaQueNaoFechaNaoViraAviso() {
        // Corrida 4, 18:25:36: a viagem saiu "2 minutos" (o aviso foi para busca longa e 1 parada).
        assertNull(ler("EUROPA | Táxi | R$ 24- R$ 34 | 23 min • 4.7 km | a 2 min • 0.3 km | R. Colômbia, Jardim Paulista, São | " +
            "Paulo | * 4,84 (1195) | Verificado | R | 2 minutos• 4.4 km | R. Cardeal Arcoverde, 3060, | Pinheiros, São Paulo"))
        // Corrida 3, 17:56:31, cartão esmaecido: a busca não foi lida e a viagem saiu "58 km".
        assertNull(ler("JARD\" | AMÉCA | 2 Táxi Promo | R$ 28,24 | 24 min6.3 km | 4min-05ly | Rua Joao nea Sao | Paulo | " +
            "Combinação em andamento | 20 minutos 58 km | R. Honduras, 1400, Jardim América, | Sao Paulo | Tenho interesse"))
    }

    @Test
    fun taxiSemALinhaDaViagemNaoViraAviso() {
        // 18:23, cartão escuro: a linha "1 h e 19 min • 19.4 km" não foi lida, o total virou busca e o aviso deu 624%.
        assertNull(ler("Táxi Promo | R$ 87,46 | 1 h e 24 min • 20.5 km | 5 min • 1.1 km | Rua Sarandi, Jardim Paulista, São | " +
            "Paulo | 4,94 (1236) | Verificado | R. Prof. Bourhan Helou, 263, Jardim | Ana Rosa, São Paulo | " +
            "Viagem longa (mais de 45 min) | Tenho interesse"))
        val inteiro = ler("Táxi Promo | R$ 87,46 | 1 h e 24 min • 20.5 km | 5 min • 1.1 km | Rua Sarandi, Jardim Paulista, São | " +
            "Paulo | 4,94 (1236) | Verificado | 1 h e 19 min • 19.4 km | R. Prof. Bourhan Helou, 263, Jardim | Ana Rosa, São Paulo | " +
            "Viagem longa (mais de 45 min) | Tenho interesse")!!
        assertEquals(1.1, inteiro.buscaKm, 0.001)
        assertEquals(19.4, inteiro.viagemKm, 0.001)
        assertEquals(79, inteiro.viagemMin)
    }

    @Test
    fun uberXSemTotalConsertaOPontoPelaVelocidade() {
        // Sem a linha do total (a do "Est. de" sai antes) não há soma para conferir: 72 km em 30 min seriam 144 km/h.
        val o = ler("UberX | R$ 20,00 | 40 min • 9.5 km • Est. de R$2,10/km | 10 min • 2.3 km | Rua A, São Paulo | " +
            "4,90 (100) | 30 minutos • 72 km | Rua B, São Paulo | Tenho interesse")!!
        assertEquals(2.3, o.buscaKm, 0.001)
        assertEquals(7.2, o.viagemKm, 0.001)
    }

    @Test
    fun cartaoEsmaecidoEOfertaQueAcabou() {
        assertEquals(Cartao.EM_TRANSICAO, LeitorOferta.cartaoDoPrint(listOf("2 Táxi Promo", "R$ 25,66", "Combinação em andamento")))
        assertEquals(Cartao.ENCERRADO, LeitorOferta.cartaoDoPrint(listOf("Táxi Promo", "Encontramos outro motorista", "ceiro para essa solicitação")))
        assertEquals(Cartao.ENCERRADO, LeitorOferta.cartaoDoPrint(listOf("R$ 29,79", "Esta solicitação não está mais", "disponível")))
        assertEquals(Cartao.NORMAL, LeitorOferta.cartaoDoPrint(listOf("Táxi Promo", "R$ 25,66", "Viagem encontrada")))
    }

    @Test
    fun valoresDoPrint() {
        assertEquals(listOf(24.0, 34.0), LeitorOferta.valoresDoPrint(listOf("Táxi", "R$ 24- R$ 34")))
        assertEquals(listOf(34.9), LeitorOferta.valoresDoPrint(listOf("RS 34,90", "38 min • 8.9 km")))
    }

    @Test
    fun barraDaCorridaDaUber() {
        // Diagnóstico da corrida 1 (30/10/2026): a barra chega no evento, sem print.
        assertEquals(12, LeitorViagem.minutosAteOFim(listOf("Não é possível ficar offline", "Preferências", "12 min", "2,4 km", "A caminho da última parada", "Conteúdo")))
        assertEquals(Int.MAX_VALUE, LeitorViagem.minutosAteOFim(listOf("36 min", "8,7 km", "A caminho da primeira parada")))
        assertEquals(0, LeitorViagem.minutosAteOFim(listOf("Não é possível ficar offline", "Preferências", "Procurando viagens", "Conteúdo")))
        assertNull(LeitorViagem.minutosAteOFim(listOf("30", "LIMITE", "Recursos de segurança", "Reporte um problema no mapa")))
        assertNull(LeitorViagem.minutosAteOFim(listOf("8 min", "1,3 km", "Encontro com Daniele")))
    }

    // ---------- Ofertas de 06/11/2026 em que o aviso não apareceu (diagnóstico da 1.82) ----------

    @Test
    fun iconeDaBuscaGrudadoNoMinuto() {
        // 19:27:13: o ícone da busca virou "8" grudado no "6 min" e o "km" saiu "kmn".
        val a = ler("B | Ver todas as solicitações 1 | Certa | Care- I | Táxi Promo | R$ 23,69 | 22 min 4.6 km | " +
            "2 Inclui um valor extra de R$ 4 | 86 min •1.2 kmn | Rua Tabapuā, Itaim Bibi, São Paulo | * 4,84 (743) | " +
            "16 minutos•3.4 km | Rua Fernão Dias, 690, São Paulo | Tenho interesse")!!
        assertEquals(23.69, a.valor, 0.001)
        assertEquals(6, a.buscaMin)
        assertEquals(1.2, a.buscaKm, 0.001)
        assertEquals(16, a.viagemMin)
        assertEquals(3.4, a.viagemKm, 0.001)
        // 19:28:38: "87 min •13 km" é 7 min e 1.3 km (o total 17 min e 3.0 km confirma).
        val b = ler("Butantấ | 2 Táxi Promo | R$ 14,48 | 17 min •3.0 km | Inclui um valor extra de R$2 | 87 min •13 km | " +
            "Av. Brig. Faria Lima, Itaim Bibi, São | Paulo | * 4,91 (935) | Verificado | 10 minutos •1,7 km | " +
            "Rua Casa do Ator, 90, Itaim Bibi, São | Paulo | Tenho interesse")!!
        assertEquals(14.48, b.valor, 0.001)
        assertEquals(7, b.buscaMin)
        assertEquals(1.3, b.buscaKm, 0.001)
        assertEquals(1.7, b.viagemKm, 0.001)
        // 19:29:13: a busca "87 min" e a viagem "74 km" (7.4) ao mesmo tempo.
        val c = ler("2 Táxi Promo | R$ 39,18 | 49 min•9.2 km | 87 min •1.8 km | Rua Fidêncio Ramos, Itaim Bibi, São | " +
            "Paulo | 4,93 (743) | Verificado | l parada | 42 minutos •74 km | R. José Maria Lisboa, 20, Jardim | " +
            "Paulista, São Paulo | Tenho interesse | Gravação: nenhuma graVação ainda")!!
        assertEquals(7, c.buscaMin)
        assertEquals(7.4, c.viagemKm, 0.001)
        assertEquals(42, c.viagemMin)
        assertEquals(1, c.paradas) // "l parada" é "1 parada"
    }

    @Test
    fun umMinutoEscritoComL() {
        // 19:29:30: "1 min" saiu "l min".
        val o = ler("Ragar ae vldgens | 2 Táxi Promo | R$ 15,23 | 13 min. 2.4 km | e l min • 0.1 km | " +
            "São Paulo Corporate Towers, Torre | Sul, Itaim Bibi | 4,93 (1198) | Verificado | 12 minutos• 2.3 km | " +
            "Rua Jesuíno Arruda, 666, Itaim Bibi, | São Paulo | Tenho interesse | RROKIIN")!!
        assertEquals(1, o.buscaMin)
        assertEquals(0.1, o.buscaKm, 0.001)
        assertEquals(12, o.viagemMin)
        assertEquals(2.3, o.viagemKm, 0.001)
    }

    @Test
    fun valorSemAVirgula() {
        // 19:29:35: "R$ 37,99" saiu "R$ 3799".
        val o = ler("ver todas as SOiCitaçoes | AVANÇADO | Táxi Promo | R$ 3799 | 38 min•9.0 km | 6 min •1.2 km | " +
            "Google |Ed. Pátio Victor Malzoni, | Torre A, Itaim Bibi | * 4,99 (3316) | 32 minutos•7.8 km | " +
            "R. Paraguacu, 316, Perdizes, São | Paulo | Tenho interesse")!!
        assertEquals(37.99, o.valor, 0.001)
        assertEquals(6, o.buscaMin)
        assertEquals(7.8, o.viagemKm, 0.001)
        // A faixa do Táxi continua sem centavos.
        val faixa = ler("Táxi | R$ 100 - R$ 146 | 50 min • 21.0 km | 5 min • 1.0 km | Rua A, São Paulo | " +
            "45 minutos • 20.0 km | Rua B, 10, São Paulo | Tenho interesse")!!
        assertEquals(100.0, faixa.valor, 0.001)
        assertEquals(146.0, faixa.valorMax!!, 0.001)
    }

    @Test
    fun buscaQueNaoFechaComOTotalContinuaSemAviso() {
        // Se nem tirando o ícone a soma fecha, é melhor não avisar do que avisar errado.
        assertNull(ler("Táxi Promo | R$ 23,69 | 22 min 4.6 km | 89 min •1.2 km | Rua X, São Paulo | 16 minutos•3.4 km | " +
            "Rua Y, São Paulo | Tenho interesse"))
    }
}
