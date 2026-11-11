package br.com.vendasinteligentes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.vendasinteligentes.data.local.AppDatabase
import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.data.local.entity.TipoMovimentacao
import br.com.vendasinteligentes.data.repository.VendasRepository
import br.com.vendasinteligentes.domain.parser.ItemInterpretado
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VendasRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: VendasRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VendasRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun salvarProduto_persisteEstoqueEValoresCorretamente() = runTest {
        val produto = Produto(
            nome = "Amendoim",
            variante = "90g",
            custoAquisicaoCentavos = 150L,
            precoVendaCentavos = 300L,
            estoqueAtual = 20
        )
        repository.salvarProduto(produto)

        val lista = repository.produtos.first()
        assertEquals(1, lista.size)
        val salvo = lista.first()
        assertEquals("Amendoim", salvo.nome)
        assertEquals("90g", salvo.variante)
        assertEquals(20, salvo.estoqueAtual)
        assertEquals(150L, salvo.custoAquisicaoCentavos)
        assertEquals(300L, salvo.precoVendaCentavos)
        assertEquals(150L, salvo.margemBrutaCentavos)
    }

    @Test
    fun salvarTextoRascunho_preservaTimestampInicialEntreModificacoes() = runTest {
        repository.salvarTextoRascunho("Primeira linha de rascunho")
        val rascunho1 = repository.rascunho.first()
        assertNotNull(rascunho1)
        val timestampInicial = rascunho1!!.dataHoraInicioRascunho

        // Modifica o texto do rascunho
        repository.salvarTextoRascunho("Primeira linha de rascunho\nSegunda linha")
        val rascunho2 = repository.rascunho.first()
        assertNotNull(rascunho2)
        // O timestamp inicial da ocorrência DEVE ser preservado
        assertEquals(timestampInicial, rascunho2!!.dataHoraInicioRascunho)
        assertEquals("Primeira linha de rascunho\nSegunda linha", rascunho2.texto)
    }

    @Test
    fun confirmarOperacoes_compra_atualizaEstoqueERegistraMovimentacaoEDebito() = runTest {
        // Cadastra produto com 10 unidades
        repository.salvarProduto(
            Produto(
                nome = "Amendoim",
                variante = "90g",
                custoAquisicaoCentavos = 150L,
                precoVendaCentavos = 300L,
                estoqueAtual = 10
            )
        )
        val produtoSalvo = repository.produtos.first().first()

        // Simula rascunho existente
        repository.salvarTextoRascunho("Wellington comprou 2 amendoins de 90 g")
        val rascunhoTimestamp = repository.rascunho.first()!!.dataHoraInicioRascunho

        val itemCompra = ItemInterpretado.Compra(
            linhaNumero = 1,
            textoOriginal = "Wellington comprou 2 amendoins de 90 g",
            nomeCliente = "Wellington",
            quantidade = 2,
            produto = produtoSalvo,
            precoUnitarioCentavos = 300L,
            valorTotalCentavos = 600L
        )

        val resultado = repository.confirmarOperacoes(listOf(itemCompra))
        assertTrue(resultado.isSuccess)

        // 1. Estoque reduzido de 10 para 8
        val produtoAposCompra = repository.produtos.first().first()
        assertEquals(8, produtoAposCompra.estoqueAtual)

        // 2. Cliente criado com saldo devedor (-600 centavos)
        val clientes = repository.clientesComSaldo.first()
        assertEquals(1, clientes.size)
        val clienteWellington = clientes.first()
        assertEquals("Wellington", clienteWellington.cliente.nome)
        assertEquals(-600L, clienteWellington.saldoCentavos)

        // 3. Venda e ItemVenda registrados com snapshot imutável
        val vendas = repository.todasVendasComItens.first()
        assertEquals(1, vendas.size)
        val venda = vendas.first()
        assertEquals(600L, venda.venda.valorTotalCentavos)
        assertEquals(rascunhoTimestamp, venda.venda.dataHoraOcorrencia)
        assertEquals(1, venda.itens.size)
        assertEquals("Amendoim", venda.itens.first().nomeProdutoSnapshot)
        assertEquals("90g", venda.itens.first().varianteSnapshot)
        assertEquals(300L, venda.itens.first().precoUnitarioCentavos)

        // 4. Movimentação da conta registrada com ocorrência do rascunho
        val movs = repository.todasMovimentacoesComCliente.first()
        assertEquals(1, movs.size)
        val mov = movs.first().movimentacao
        assertEquals(TipoMovimentacao.COMPRA, mov.tipo)
        assertEquals(600L, mov.valorCentavos)
        assertEquals(-600L, mov.valorAssinadoCentavos)
        assertEquals(rascunhoTimestamp, mov.dataHoraOcorrencia)

        // 5. Rascunho preservado após sucesso
        val rascunhoFinal = repository.rascunho.first()
        assertNotNull(rascunhoFinal)
        assertEquals("Wellington comprou 2 amendoins de 90 g", rascunhoFinal!!.texto)
    }

    @Test
    fun confirmarOperacoes_estoqueInsuficiente_reverteTransacaoSemAlterarDados() = runTest {
        repository.salvarProduto(
            Produto(
                nome = "Amendoim",
                variante = "90g",
                custoAquisicaoCentavos = 150L,
                precoVendaCentavos = 300L,
                estoqueAtual = 1 // Apenas 1 disponível
            )
        )
        val produtoSalvo = repository.produtos.first().first()
        repository.salvarTextoRascunho("Wellington comprou 5 amendoins de 90 g")

        val itemCompra = ItemInterpretado.Compra(
            linhaNumero = 1,
            textoOriginal = "Wellington comprou 5 amendoins de 90 g",
            nomeCliente = "Wellington",
            quantidade = 5, // Solicita 5
            produto = produtoSalvo,
            precoUnitarioCentavos = 300L,
            valorTotalCentavos = 1500L
        )

        val resultado = repository.confirmarOperacoes(listOf(itemCompra))
        assertTrue(resultado.isFailure)
        assertTrue(resultado.exceptionOrNull()!!.message!!.contains("Estoque insuficiente"))

        // O estoque permanece intacto (1)
        val produtoAposFalha = repository.produtos.first().first()
        assertEquals(1, produtoAposFalha.estoqueAtual)

        // Nenhuma venda foi registrada
        val vendas = repository.todasVendasComItens.first()
        assertTrue(vendas.isEmpty())

        // Rascunho foi preservado para permitir correção pelo usuário
        val rascunhoPreservado = repository.rascunho.first()
        assertNotNull(rascunhoPreservado)
    }

    @Test
    fun confirmarOperacoes_pagamento_geraCreditoParaCliente() = runTest {
        val itemPagamento = ItemInterpretado.Pagamento(
            linhaNumero = 1,
            textoOriginal = "Wellington me pagou 10 reais",
            nomeCliente = "Wellington",
            valorCentavos = 1000L
        )

        val resultado = repository.confirmarOperacoes(listOf(itemPagamento))
        assertTrue(resultado.isSuccess)

        val clientes = repository.clientesComSaldo.first()
        assertEquals(1, clientes.size)
        val cliente = clientes.first()
        assertEquals("Wellington", cliente.cliente.nome)
        assertEquals(1000L, cliente.saldoCentavos) // Saldo positivo (crédito)
    }

    @Test(expected = IllegalArgumentException::class)
    fun salvarCliente_comNomeDuplicadoEmOutroCase_lancaExcecao() = runTest {
        repository.salvarCliente(Cliente(nome = "Fulano"))
        // Tentar salvar "fulano" (minúsculo) deve falhar por duplicidade
        repository.salvarCliente(Cliente(nome = "fulano"))
    }
}
