package app.corridaverde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LeitorOfertaTest {
    // Textos da oferta do print de 24/09/2026 (endereços trocados).
    private val print = listOf(
        "Business Comfort", "Exclusivo", "R$ 62,10", "R\$9,86/km aprox.", "4,95 (426)", "Verificado",
        "8 min (1.4 km)", "Rua A, São Paulo", "35 minutos (4.9 km)", "Rua B, 308, Itaim Bibi, São Paulo", "Aceitar",
    )

    @Test
    fun leOfertaDoPrint() {
        val o = LeitorOferta.ler(print)!!
        assertEquals(62.10, o.valor, 0.001)
        assertEquals(1.4, o.buscaKm, 0.001)
        assertEquals(8, o.buscaMin)
        assertEquals(4.9, o.viagemKm, 0.001)
        assertEquals(35, o.viagemMin)
        assertEquals(4.95, o.nota!!, 0.001)
        assertEquals("Rua B, 308, Itaim Bibi, São Paulo", o.destino)
    }

    @Test
    fun avaliaOfertaDoPrint() {
        val quintaDeManha = LocalDateTime.of(2026, 9, 24, 8, 46)
        val r = Avaliador.avaliar(LeitorOferta.ler(print)!!, Config(), quintaDeManha)
        // 4,9 km em 35 min: ~23 min de trânsito cobrados pela hora parada.
        assertEquals(51.57, r.taximetro, 0.005)
        assertEquals(23, r.minParado)
        assertEquals(120, r.pct)
        assertEquals(Cor.VERDE, r.cor)
        assertEquals(9.86, r.rsKm, 0.005)
        assertEquals(86.65, r.rsHora!!, 0.005)
        assertFalse(r.bandeira2)
    }

    @Test
    fun leOfertaUberXComSelecionar() {
        // Oferta do print de 24/09/2026, 21:23 (botão "Selecionar" em vez de "Aceitar").
        val o = LeitorOferta.ler(listOf(
            "UberX", "R$ 14,36", "R\$2,02/km aprox.", "4,87 (153)", "Verificado",
            "10 min (3.0 km)", "Rua A, Brasilandia, São Paulo", "13 minutos (4.1 km)", "Rua B, 6, Brasilandia, São Paulo",
            "Selecionar",
        ))!!
        assertEquals(14.36, o.valor, 0.001)
        assertEquals(3.0, o.buscaKm, 0.001)
        assertEquals(4.1, o.viagemKm, 0.001)
        assertEquals(13, o.viagemMin)
    }

    @Test
    fun leFaixaDaCategoriaTaxi() {
        // Print de 01/10/2026, 17:28: o valor final fica entre o mínimo e o máximo.
        val o = LeitorOferta.ler(listOf(
            "Táxi", "R$ 31 - R$ 46", "4,97 (2106)", "Verificado",
            "2 min (0.1 km)", "Rua A, Itaim Bibi, São Paulo", "30 minutos (6.1 km)", "Rua B, Vila Mariana, São Paulo", "Selecionar",
        ))!!
        assertEquals(31.0, o.valor, 0.001)
        assertEquals(46.0, o.valorMax!!, 0.001)
        assertEquals(6.1, o.viagemKm, 0.001)
        assertEquals("Rua B, Vila Mariana, São Paulo", o.destino)
        val r = Avaliador.avaliar(o, Config(), LocalDateTime.of(2026, 10, 1, 17, 28))
        // uberTAXI paga o taxímetro (~R$ 50,04) até o máximo (R$ 46): recebe 92% dele.
        assertEquals(92, r.pct)
        assertEquals(Cor.AMARELO, r.cor)
        assertEquals(46.0 / 6.2, r.rsKm, 0.01)
        // Máximo acima do taxímetro: recebe o taxímetro inteiro, 100%.
        val folgado = Avaliador.avaliar(o.copy(valorMax = 70.0), Config(), LocalDateTime.of(2026, 10, 1, 17, 28))
        assertEquals(100, folgado.pct)
        assertEquals(Cor.VERDE, folgado.cor)
    }

    @Test
    fun ignoraGanhosDoDiaAntesDaOferta() {
        val o = LeitorOferta.ler(listOf("R$ 1.234,56") + print)!!
        assertEquals(62.10, o.valor, 0.001)
    }

    @Test
    fun leHoraComE() {
        val o = LeitorOferta.ler(listOf("Black", "R$ 173,39", "R$3,09/km aprox.", "4,79 (840)", "Verificado", "5 min (1.2 km)",
            "Av Parada Pinto, Cachoeirinha, São Paulo", "1 parada", "1 h e 29 min (54.9 km)", "Rua Imbituba, 66", "Selecionar"))!!
        assertEquals(173.39, o.valor, 0.001)
        assertEquals(89, o.viagemMin)
        assertEquals(54.9, o.viagemKm, 0.001)
    }

    @Test
    fun blackComAcrescimoIncluidoLeOValorDaCorrida() {
        // Print de 13/10/2026: o "+R$ 3,75 incluído" fica entre o valor e a busca e era lido como o valor.
        val o = LeitorOferta.ler(listOf("Black", "R$ 34,19", "R$4,07/km aprox.", "4,95 (597)", "Verificado", "+R$ 3,75 incluído",
            "7 min (1.6 km)", "Avenida Imperatriz Leopoldina, Vila Leopoldina, São Paulo", "16 minutos (6.8 km)",
            "Natura NASP, Natura NASP, Jaguará", "Selecionar"))!!
        assertEquals(34.19, o.valor, 0.001)
        assertEquals(6.8, o.viagemKm, 0.001)
        assertEquals(Grupo.PREMIUM, o.grupo)
        // Mesmo se o "+" vier num texto e o valor no outro.
        assertEquals(34.19, LeitorOferta.ler(listOf("Black", "R$ 34,19", "+", "R$ 3,75", "incluído", "7 min (1.6 km)", "16 minutos (6.8 km)"))!!.valor, 0.001)
    }

    @Test
    fun leHorasMetrosEParadas() {
        val o = LeitorOferta.ler(listOf("R$ 1.180,00", "3 min (800 m)", "1 h 5 min (45.2 km)", "10 min (3.1 km)"))!!
        assertEquals(1180.0, o.valor, 0.001)
        assertEquals(0.8, o.buscaKm, 0.001)
        assertEquals(48.3, o.viagemKm, 0.001)
        assertEquals(75, o.viagemMin)
        assertEquals(1, o.paradas)
    }

    @Test
    fun ofertaExclusivaComParadaAvisaNoPopup() {
        // Textos do print Chamada Uber Black Exclusiva de 27/09/2026 (endereços trocados).
        val o = LeitorOferta.ler(listOf(
            "Black", "Exclusivo", "R$ 85,13", "R\$6,81/km aprox.", "4,94 (1511)", "Verificado",
            "3 min (0.6 km)", "Rua A, São Paulo", "1 parada", "37 minutos (11.9 km)", "Rua B, Liberdade, São Paulo", "Aceitar",
        ))!!
        assertEquals(85.13, o.valor, 0.001)
        assertEquals(11.9, o.viagemKm, 0.001)
        assertEquals(1, o.paradas)
        val r = Avaliador.avaliar(o, Config(luxo = true), LocalDateTime.of(2026, 9, 27, 23, 35))
        assertEquals(listOf("1 parada"), r.avisos)
    }

    @Test
    fun semPegadinhaNaoTemAviso() {
        val r = Avaliador.avaliar(LeitorOferta.ler(print)!!, Config(), LocalDateTime.of(2026, 9, 24, 8, 46))
        assertEquals(0, r.oferta.paradas)
        assertTrue(r.avisos.isEmpty())
    }

    @Test
    fun avisosJuntosNumaLinha() {
        val o = Oferta(valor = 150.0, buscaKm = 3.5, buscaMin = 12, viagemKm = 30.0, viagemMin = 50, nota = 4.6, paradas = 2)
        val r = Avaliador.avaliar(o, Config(), LocalDateTime.of(2026, 9, 24, 8, 46))
        assertEquals(listOf("2 paradas", "busca longa", "nota 4,60", "viagem longa"), r.avisos)
    }

    @Test
    fun semOfertaDevolveNull() {
        assertNull(LeitorOferta.ler(listOf("Você está online", "R$ 150,00")))
    }

    @Test
    fun buscaLongaRebaixaUmNivel() {
        val o = Oferta(valor = 62.10, buscaKm = 3.5, buscaMin = 12, viagemKm = 4.9, viagemMin = 35, nota = null)
        val r = Avaliador.avaliar(o, Config(), LocalDateTime.of(2026, 9, 24, 8, 46))
        assertTrue(r.buscaLonga)
        assertEquals(Cor.AMARELO, r.cor)
    }

    @Test
    fun corridasReaisDe25DeSetembro() {
        val sextaDeManha = LocalDateTime.of(2026, 9, 25, 8, 37)
        // Táxi Promo R$ 32,24, 20 minutos (6.7 km): o taxímetro deu R$ 40,85.
        val semTransito = Avaliador.avaliar(Oferta(32.24, 0.4, 3, 6.7, 20, null), Config(), sextaDeManha)
        assertEquals(40.85, semTransito.taximetro, 40.85 * 0.05)
        assertEquals(Cor.VERMELHO, semTransito.cor)
        // R$ 36,71, 7,24 km que levaram 32 min: o taxímetro deu R$ 57,65.
        val comTransito = Avaliador.avaliar(Oferta(36.71, 1.0, 4, 7.24, 32, null), Config(), sextaDeManha)
        assertEquals(57.65, comTransito.taximetro, 57.65 * 0.05)
        assertEquals(Cor.VERMELHO, comTransito.cor)
    }

    @Test
    fun bandeira2() {
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 9, 24, 21, 0)))  // quinta à noite
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 9, 27, 12, 0)))  // domingo
        assertTrue(Tarifa.bandeira2(LocalDateTime.of(2026, 11, 20, 12, 0))) // Consciência Negra
        assertFalse(Tarifa.bandeira2(LocalDateTime.of(2026, 9, 26, 12, 0))) // sábado de dia
        assertEquals(LocalDate.of(2026, 4, 5), Feriados.pascoa(2026))
        assertEquals(9.83 + 10 * 7.20 * 1.3, Tarifa.taximetro(10.0, 24, luxo = true, bandeira2 = true), 0.001)
    }

    @Test
    fun leNumeroDaTagDaRelease() {
        assertEquals(12, Atualizador.numeroDaTag("""{"url":"x","tag_name": "v1.12","name":"Corrida Verde 1.12"}"""))
        assertNull(Atualizador.numeroDaTag("""{"message":"Not Found"}"""))
    }

    @Test
    fun leOfertaDa99() {
        val o = LeitorOferta.ler(listOf("Táxi", "R\$13,01", "1,0x", "4,97", "125 corridas", "Perfil Premium",
            "5 min (705 m)", "Rua Dr. Araújo Castro, 194, Jardim Peri", "4 min (1,1 km)", "Rua Várzea Grande, 153, Jardim Peri"))!!
        assertEquals(13.01, o.valor, 0.001)
        assertEquals(0.705, o.buscaKm, 0.001)
        assertEquals(1.1, o.viagemKm, 0.001)
    }

    @Test
    fun leTaxiDa99PeloOcr() {
        // Print da oferta da 99 de 04/10/2026, 11:57, como o OCR devolve (com sobras do mapa nas bordas).
        val o = LeitorOferta.ler99(listOf(
            "Táxi", "3", "NE", "R$14,05", "01,0x", "lo", "4,92 • 127 corridas •", "Perfil Premium",
            "7 min (1,6 km)", "EMEF Osvaldo Quirino Simões, Rua", "Camilo Peçanha, 10 - 10",
            "4 min (1,2 km)", "TriMais Supermercado - Peri, Avenida", "Peri Ronchetti, 870",
        ))!!
        assertEquals(14.05, o.valor, 0.001)
        assertEquals(1.6, o.buscaKm, 0.001)
        assertEquals(7, o.buscaMin)
        assertEquals(1.2, o.viagemKm, 0.001)
        assertEquals(4, o.viagemMin)
        assertEquals(4.92, o.nota!!, 0.001)
        assertEquals(0, o.paradas)
        assertEquals("TriMais Supermercado - Peri, Avenida Peri Ronchetti, 870", o.destino)
    }

    @Test
    fun leUberXPeloPrint() {
        // Print de 30/10/2026, 07:02, na versão da Play (o texto do cartão não chega): como o OCR devolve,
        // com sobras do mapa na borda e o "•" lido como ponto.
        val o = LeitorOferta.lerUberDoPrint(listOf(
            "UberX", "R$ 35,85", "48 min • 23.9 km • Est. de R$1,50/km", "6 min . 1.4 km", "RI",
            "Rua José Francisco, Brasilândia,", "São Paulo", "4,94 (338)", "42 minutos • 22.5 km",
            "Av. Sylvio Honório Álvares Penteado,", "370, Alphaville, Barueri", "Tenho interesse",
        ))!!
        assertEquals(35.85, o.valor, 0.001)
        assertEquals(1.4, o.buscaKm, 0.001)
        assertEquals(6, o.buscaMin)
        assertEquals(22.5, o.viagemKm, 0.001)
        assertEquals(42, o.viagemMin)
        assertEquals(4.94, o.nota!!, 0.001)
        assertEquals(0, o.paradas)
        assertEquals(Grupo.ECONOMICO, o.grupo)
    }

    @Test
    fun leBlackPeloPrintComRSemCifraoESemPonto() {
        // Print de 30/10/2026, 06:57 (cartão escuro por cima da 99): o "$" lido como S e o "•" que sumiu.
        val o = LeitorOferta.lerUberDoPrint(listOf(
            "Black", "Exclusivo", "RS 27,76", "34 min • 10.1 km • Est. de R$2,75/km", "9 min 3.0 km",
            "Rua Serra do Paru, Mandaqui, São", "Paulo", "4,96 (407)", "Verificado", "25 minutos • 7.1 km",
            "Av. Leôncio de Magalhães, 1.000,", "Jardim São Paulo, São Paulo", "Aceitar",
        ))!!
        assertEquals(27.76, o.valor, 0.001)
        assertEquals(3.0, o.buscaKm, 0.001)
        assertEquals(9, o.buscaMin)
        assertEquals(7.1, o.viagemKm, 0.001)
        assertEquals(25, o.viagemMin)
        assertEquals(Grupo.PREMIUM, o.grupo)
    }

    @Test
    fun telaInicialDaUberPeloPrintNaoEOferta() {
        assertNull(LeitorOferta.lerUberDoPrint(listOf("Procurando viagens", "R$ 0,00", "HOJE", "Osasco", "015", "348")))
    }

    @Test
    fun le99PgtoNoAppSemPegarOAMaisPorCorrida() {
        val o = LeitorOferta.ler99(listOf(
            "Pgto. no app", "1", "R$14,70", "1,4x", "R$3,78/km", "R$4,02 a mais por corrida", "5,00 • 31 corridas • Cartão verif.",
            "6 min (1,5 km)", "Rua A, 80", "6 min (2,3 km)", "Rua B, Avenida C",
        ))!!
        assertEquals(14.70, o.valor, 0.001)
        assertEquals(5.0, o.nota!!, 0.001)
        assertEquals(2.3, o.viagemKm, 0.001)
    }

    @Test
    fun le99NegociaSemPegarAsContrapropostas() {
        val o = LeitorOferta.ler99(listOf(
            "Negocia • Dinheiro", "RS23,68", "R$2,08/km", "4,90 • 214 corridas • Perfil Premium",
            "5 min (1 km)", "Rua A, 725", "31 min (10,4 km)", "Shopping D, Avenida Cruzeiro do Sul,", "1100 - Canindé",
            "Aceitar por R$23,68", "R$24,86", "R$25,57", "R$26,05",
        ))!!
        assertEquals(23.68, o.valor, 0.001)
        assertEquals(1.0, o.buscaKm, 0.001)
        assertEquals(10.4, o.viagemKm, 0.001)
        assertEquals(31, o.viagemMin)
        // O "Aceitar por" não entra no endereço.
        assertEquals("Shopping D, Avenida Cruzeiro do Sul, 1100 - Canindé", o.destino)
    }

    @Test
    fun destinoCortadoDa99PerdeAPalavraPelaMetade() {
        // Print de 04/10/2026, 11:13: o fim do endereço vem cortado com reticências.
        val o = LeitorOferta.ler99(listOf(
            "Táxi", "R$11,96", "1,0x", "4,89 • 225 corridas • Cartão verif.",
            "7 min (1,1 km)", "Rua São Gonçalo do Abaeté, 236,", "Parque Tiete",
            "3 min (890 m)", "EE Profª Luiza Salette Junca de", "Almeida, Avenida Dep. Cantídio Samp...",
        ))!!
        assertEquals(0.89, o.viagemKm, 0.001)
        assertEquals("EE Profª Luiza Salette Junca de Almeida, Avenida Dep. Cantídio", o.destino)
    }

    @Test
    fun telaDa99SemOfertaNaoViraOferta() {
        assertNull(LeitorOferta.ler99(listOf("Atualização nas configurações", "Para ajudar você a conseguir mais corridas")))
    }

    @Test
    fun leTaxiDa99EmMetrosComoOOcrDevolveu() {
        // Leitura real do OCR de 04/10/2026, 12:41 (só as linhas do cartão; a nota vem com um "t" da estrela).
        val o = LeitorOferta.ler99(listOf(
            "Táxi", "3", "R$9,53 O1,0x", "t 4,98 270 corridas", "Perfil Premium",
            "4 min (701 m)", "Rua José Maria Peixoto, 158, Jardim", "Peri",
            "2 min (541 m)", "Rua Cariacica, 78, Jardim Santa Cruz",
        ))!!
        assertEquals(9.53, o.valor, 0.001)
        assertEquals(0.701, o.buscaKm, 0.001)
        assertEquals(0.541, o.viagemKm, 0.001)
        assertEquals(4.98, o.nota!!, 0.001)
        assertEquals("Rua Cariacica, 78, Jardim Santa Cruz", o.destino)
    }

    @Test
    fun leTrechoComPontoNoLugarDosParenteses() {
        // Diagnóstico de 26/10/2026: a Uber passou a escrever "7 min • 2.6 km".
        val o = LeitorOferta.ler(listOf(
            "UberX", "Exclusivo", "R$ 56,36", "Est. de R\$2,26/km", "7 min • 2.6 km", "Rua A, Cachoeirinha, São Paulo",
            "4,92 (1007)", "Verificado", "1 h e 3 min • 22.3 km", "Cidade de Deus, Osasco", "Viagem longa (mais de 45 min)", "Aceitar",
        ))!!
        assertEquals(56.36, o.valor, 0.001)
        assertEquals(2.6, o.buscaKm, 0.001)
        assertEquals(7, o.buscaMin)
        assertEquals(22.3, o.viagemKm, 0.001)
        assertEquals(63, o.viagemMin)
        assertEquals(4.92, o.nota!!, 0.001)
        assertEquals("Cidade de Deus, Osasco", o.destino)
    }

    @Test
    fun leTaxiPromoComValorExtraNoFormatoNovo() {
        // Print de 26/10/2026, 07:30: "Inclui um valor extra de R$ 4" fica entre o valor e a busca.
        val o = LeitorOferta.ler(listOf(
            "Táxi Promo", "R$ 62,42", "Inclui um valor extra de R$ 4", "8 min • 1.8 km", "Rua A, Santana",
            "4,68 (1313)", "Verificado", "31 minutos • 12.2 km", "R. Padre Adelino, 901, Belém, São Paulo", "Viagem",
        ))!!
        assertEquals(62.42, o.valor, 0.001)
        assertEquals(1.8, o.buscaKm, 0.001)
        assertEquals(12.2, o.viagemKm, 0.001)
        assertEquals(31, o.viagemMin)
        assertEquals("R. Padre Adelino, 901, Belém, São Paulo", o.destino)
    }

    @Test
    fun leFaixaDoTaxiNoFormatoNovo() {
        // Print de 26/10/2026, 07:38.
        val o = LeitorOferta.ler(listOf(
            "Táxi", "R$ 28 - R$ 40", "9 min • 0.7 km", "Rua A, Santana", "4,61 (1073)",
            "21 minutos • 5.5 km", "R. Voluntários da Pátria, 3693, Santana, São Paulo", "Viagem",
        ))!!
        assertEquals(28.0, o.valor, 0.001)
        assertEquals(40.0, o.valorMax!!, 0.001)
        assertEquals(0.7, o.buscaKm, 0.001)
        assertEquals(5.5, o.viagemKm, 0.001)
    }

    @Test
    fun totalDoTaxiEmbaixoDoValorNaoViraBuscaNemParada() {
        // Prints de 28/10/2026: "8 min • 1.7 km" embaixo da faixa é a busca mais a viagem.
        val o = LeitorOferta.ler(listOf(
            "Táxi", "R$ 12 - R$ 16", "8 min • 1.7 km", "1 min • 0.2 km", "Rua João Cachoeira, Itaim Bibi, São Paulo",
            "4,84 (732)", "Verificado", "7 minutos • 1.5 km", "Avenida Brigadeiro Faria Lima, 4440, Itaim Bibi, São Paulo",
            "Tenho interesse",
        ))!!
        assertEquals(12.0, o.valor, 0.001)
        assertEquals(16.0, o.valorMax!!, 0.001)
        assertEquals(0.2, o.buscaKm, 0.001)
        assertEquals(1, o.buscaMin)
        assertEquals(1.5, o.viagemKm, 0.001)
        assertEquals(7, o.viagemMin)
        assertEquals(0, o.paradas)
        assertEquals("Avenida Brigadeiro Faria Lima, 4440, Itaim Bibi, São Paulo", o.destino)
    }

    @Test
    fun totalDoTaxiComValorExtraEDoTaxiPromo() {
        val extra = LeitorOferta.ler(listOf(
            "Táxi", "R$ 29 - R$ 42", "27 min • 4.3 km", "Inclui um valor extra de R$ 9", "3 min • 0.8 km",
            "Avenida Lavandisca, Moema, São Paulo", "4,86 (1435)", "Verificado", "24 minutos • 3.5 km",
            "Google | Ed. Pátio Victor Malzoni, Saída Rua Iguatemi/Fazano, Itaim Bibi", "Tenho interesse",
        ))!!
        assertEquals(29.0, extra.valor, 0.001)
        assertEquals(0.8, extra.buscaKm, 0.001)
        assertEquals(3.5, extra.viagemKm, 0.001)
        assertEquals(24, extra.viagemMin)
        assertEquals(0, extra.paradas)

        val promo = LeitorOferta.ler(listOf(
            "Táxi Promo", "R$ 19,34", "24 min • 3.9 km", "4 min • 0.9 km", "R. Quintana, Cidade Monções, São Paulo",
            "4,80 (1899)", "Verificado", "20 minutos • 3.0 km", "R. Comendador Miguel Calfat, 307, Vila Olímpia, São Paulo",
        ))!!
        assertEquals(19.34, promo.valor, 0.001)
        assertEquals(0.9, promo.buscaKm, 0.001)
        assertEquals(3.0, promo.viagemKm, 0.001)
        assertEquals(20, promo.viagemMin)
        assertEquals(0, promo.paradas)
    }

    @Test
    fun totalComParadaContaSoAParadaDeVerdade() {
        val o = LeitorOferta.ler(listOf(
            "Táxi", "R$ 30 - R$ 44", "35 min • 9.0 km", "5 min • 1.0 km", "Rua A, São Paulo", "1 parada",
            "10 minutos • 3.0 km", "Rua B, São Paulo", "20 minutos • 5.0 km", "Rua C, São Paulo",
        ))!!
        assertEquals(1.0, o.buscaKm, 0.001)
        assertEquals(8.0, o.viagemKm, 0.001)
        assertEquals(30, o.viagemMin)
        assertEquals(1, o.paradas)
    }
}
