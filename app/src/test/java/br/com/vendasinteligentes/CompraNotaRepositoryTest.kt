package br.com.vendasinteligentes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.vendasinteligentes.data.local.AppDatabase
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.data.repository.ItemCompraEntrada
import br.com.vendasinteligentes.data.repository.VendasRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CompraNotaRepositoryTest {

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
    fun registrarCompra_somaEstoqueAtualizaCustoECriaProdutoNovo() = runTest {
        repository.salvarProduto(
            Produto(nome = "Amendoim", variante = "90g", custoAquisicaoCentavos = 150, precoVendaCentavos = 300, estoqueAtual = 5)
        )
        val amendoim = repository.produtos.first().single()

        val resultado = repository.registrarCompra(
            fornecedor = "Atacadão",
            numeroNota = "12345",
            dataHoraCompra = 1_700_000_000_000,
            itens = listOf(
                ItemCompraEntrada(amendoim.id, amendoim.nome, amendoim.variante, quantidade = 10, valorTotalCentavos = 1800),
                ItemCompraEntrada(null, "Refrigerante", "Lata", quantidade = 3, valorTotalCentavos = 1000, precoVendaNovoProdutoCentavos = 600)
            )
        )
        assertTrue(resultado.isSuccess)

        val produtos = repository.produtos.first().associateBy { it.nome }
        assertEquals(15, produtos.getValue("Amendoim").estoqueAtual)
        assertEquals(180L, produtos.getValue("Amendoim").custoAquisicaoCentavos)
        assertEquals(3, produtos.getValue("Refrigerante").estoqueAtual)
        assertEquals(333L, produtos.getValue("Refrigerante").custoAquisicaoCentavos)
        assertEquals(600L, produtos.getValue("Refrigerante").precoVendaCentavos)

        val compra = repository.comprasComItens.first().single()
        assertEquals(2800L, compra.compra.valorTotalCentavos)
        assertEquals(2, compra.itens.size)
    }

    @Test
    fun excluirCompra_estornaEstoque() = runTest {
        repository.registrarCompra(
            fornecedor = null,
            numeroNota = null,
            dataHoraCompra = System.currentTimeMillis(),
            itens = listOf(ItemCompraEntrada(null, "Bala", null, quantidade = 50, valorTotalCentavos = 500))
        )
        val compra = repository.comprasComItens.first().single()

        assertTrue(repository.excluirCompra(compra.compra.id).isSuccess)

        assertEquals(0, repository.produtos.first().single().estoqueAtual)
        assertTrue(repository.comprasComItens.first().isEmpty())
    }

    @Test
    fun registrarCompra_quantidadeInvalidaNaoAlteraNada() = runTest {
        val resultado = repository.registrarCompra(
            fornecedor = null,
            numeroNota = null,
            dataHoraCompra = System.currentTimeMillis(),
            itens = listOf(ItemCompraEntrada(null, "Bala", null, quantidade = 0, valorTotalCentavos = 500))
        )
        assertTrue(resultado.isFailure)
        assertTrue(repository.produtos.first().isEmpty())
        assertTrue(repository.comprasComItens.first().isEmpty())
    }
}
