package br.com.vendasinteligentes

import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.domain.parser.ItemInterpretado
import br.com.vendasinteligentes.domain.parser.LivroCaixaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivroCaixaParserTest {

    private val produtoAmendoim = Produto(
        id = 1L,
        nome = "Amendoim",
        variante = "90g",
        custoAquisicaoCentavos = 150L,
        precoVendaCentavos = 300L,
        estoqueAtual = 50
    )

    private val produtoCocaCola = Produto(
        id = 2L,
        nome = "Coca-Cola",
        variante = "Lata 350ml",
        custoAquisicaoCentavos = 250L,
        precoVendaCentavos = 500L,
        estoqueAtual = 30
    )

    private val produtos = listOf(produtoAmendoim, produtoCocaCola)

    @Test
    fun interpretar_compraValida_retornaItemCompraComValoresCorretos() {
        val entrada = "Wellington comprou 2 amendoins de 90 g"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertTrue(resultado.valido)
        assertEquals(0, resultado.erros.size)
        assertEquals(1, resultado.itens.size)

        val item = resultado.itens.first() as ItemInterpretado.Compra
        assertEquals("Wellington", item.nomeCliente)
        assertEquals(2, item.quantidade)
        assertEquals(1L, item.produto.id)
        assertEquals(300L, item.precoUnitarioCentavos)
        assertEquals(600L, item.valorTotalCentavos)
        assertEquals(1, item.linhaNumero)
    }

    @Test
    fun interpretar_pagamentoValido_retornaItemPagamentoComValorEmCentavos() {
        val entrada = "Wellington me pagou 10 reais"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertTrue(resultado.valido)
        assertEquals(0, resultado.erros.size)
        assertEquals(1, resultado.itens.size)

        val item = resultado.itens.first() as ItemInterpretado.Pagamento
        assertEquals("Wellington", item.nomeCliente)
        assertEquals(1000L, item.valorCentavos)
    }

    @Test
    fun interpretar_variacoesDeFormatoDeMoeda_parseCorreto() {
        assertEquals(1000L, LivroCaixaParser.parseValorCentavos("10 reais"))
        assertEquals(1050L, LivroCaixaParser.parseValorCentavos("10,50"))
        assertEquals(1050L, LivroCaixaParser.parseValorCentavos("10.50"))
        assertEquals(1000L, LivroCaixaParser.parseValorCentavos("R$ 10,00"))
        assertEquals(1000L, LivroCaixaParser.parseValorCentavos("10"))
        assertEquals(1550L, LivroCaixaParser.parseValorCentavos("15 reais e 50 centavos"))
        assertEquals(50L, LivroCaixaParser.parseValorCentavos("50 centavos"))
        assertEquals(100L, LivroCaixaParser.parseValorCentavos("1 real"))
    }

    @Test
    fun interpretar_espacosExtrasECaixaAlta_normalizaCorretamente() {
        val entrada = "   WELLINGTON   comprou   2    amendoins  de  90  g   "
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertTrue(resultado.valido)
        val item = resultado.itens.first() as ItemInterpretado.Compra
        assertEquals("Wellington", item.nomeCliente)
        assertEquals(2, item.quantidade)
        assertEquals(1L, item.produto.id)
    }

    @Test
    fun interpretar_quantidadeInvalida_retornaErro() {
        val entrada = "Wellington comprou zero amendoins de 90 g"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertFalse(resultado.valido)
        assertEquals(1, resultado.erros.size)
        assertTrue(resultado.erros.first().mensagem.contains("Quantidade"))
    }

    @Test
    fun interpretar_produtoNaoCadastrado_retornaErroEspecifico() {
        val entrada = "Wellington comprou 1 salgadinho fandangos"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertFalse(resultado.valido)
        assertEquals(1, resultado.erros.size)
        assertTrue(resultado.erros.first().mensagem.contains("não cadastrado"))
    }

    @Test
    fun interpretar_formatoNaoReconhecido_retornaErroComInstrucao() {
        val entrada = "Wellington pegou 2 coca colas"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertFalse(resultado.valido)
        assertEquals(1, resultado.erros.size)
        assertTrue(resultado.erros.first().mensagem.contains("Formato não reconhecido"))
    }

    @Test
    fun interpretar_linhaAmbigua_retornaErro() {
        val entrada = "Wellington comprou 2 amendoins e me pagou 10 reais"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertFalse(resultado.valido)
        assertEquals(1, resultado.erros.size)
        assertTrue(resultado.erros.first().mensagem.contains("ambígua"))
    }

    @Test
    fun interpretar_loteMistoComErros_identificaLinhasCorretasEErrosEspecificos() {
        val entrada = """
            Wellington comprou 2 amendoins de 90 g
            
            Carlos me pagou 50 reais
            Maria pegou 1 agua
            Wellington me pagou 10,50
        """.trimIndent()

        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertFalse(resultado.valido)
        assertEquals(3, resultado.itens.size)
        assertEquals(1, resultado.erros.size)

        val erro = resultado.erros.first()
        assertEquals(4, erro.linhaNumero)
        assertEquals("Maria pegou 1 agua", erro.textoLinha)
    }

    @Test
    fun interpretar_clienteCadastradoMinusculoEDigitadoMaiusculo_preservaNomeCadastrado() {
        val clientes = listOf(Cliente(id = 1L, nome = "fulano"))
        val entrada = "FULANO COMPROU 2 AMENDOINS DE 90 G"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos, clientes)

        assertTrue(resultado.valido)
        val item = resultado.itens.first() as ItemInterpretado.Compra
        assertEquals("fulano", item.nomeCliente)
        assertEquals(2, item.quantidade)
    }

    @Test
    fun interpretar_pagamentoSemMeEMaiusculo_funcionaCorretamente() {
        val entrada = "FULANO PAGOU 10 REAIS"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertTrue(resultado.valido)
        val item = resultado.itens.first() as ItemInterpretado.Pagamento
        assertEquals("Fulano", item.nomeCliente)
        assertEquals(1000L, item.valorCentavos)
    }

    @Test
    fun interpretar_apenasNomeDoProdutoSemVariante_encontraProdutoCorretamente() {
        val entrada = "fulano comprou 2 amendoins"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertTrue(resultado.valido)
        val item = resultado.itens.first() as ItemInterpretado.Compra
        assertEquals("Fulano", item.nomeCliente)
        assertEquals(2, item.quantidade)
        assertEquals(produtoAmendoim.id, item.produto.id)
    }

    @Test
    fun interpretar_bebidaSemInformarSabor_encontraProdutoCorretamente() {
        val entrada = "fulano comprou 1 coca cola"
        val resultado = LivroCaixaParser.interpretar(entrada, produtos)

        assertTrue(resultado.valido)
        val item = resultado.itens.first() as ItemInterpretado.Compra
        assertEquals(1, item.quantidade)
        assertEquals(produtoCocaCola.id, item.produto.id)
    }

    @Test
    fun interpretar_multiplosSaboresMesmoNome_encontraSemErroDeAmbiguidade() {
        val sucoLaranja = Produto(id = 10L, nome = "Suco", variante = "Laranja", custoAquisicaoCentavos = 200, precoVendaCentavos = 400, estoqueAtual = 10)
        val sucoUva = Produto(id = 11L, nome = "Suco", variante = "Uva", custoAquisicaoCentavos = 200, precoVendaCentavos = 400, estoqueAtual = 5)
        val lista = listOf(sucoLaranja, sucoUva)

        val entrada = "fulano comprou 2 sucos"
        val resultado = LivroCaixaParser.interpretar(entrada, lista)

        assertTrue(resultado.valido)
        val item = resultado.itens.first() as ItemInterpretado.Compra
        assertEquals(2, item.quantidade)
        assertEquals("Suco", item.produto.nome)
    }
}
