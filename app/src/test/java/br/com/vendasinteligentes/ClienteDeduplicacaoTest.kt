package br.com.vendasinteligentes

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import br.com.vendasinteligentes.data.local.AppDatabase
import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.data.local.entity.TipoMovimentacao
import br.com.vendasinteligentes.data.repository.VendasRepository
import br.com.vendasinteligentes.domain.parser.ItemInterpretado
import br.com.vendasinteligentes.domain.parser.LivroCaixaParser
import br.com.vendasinteligentes.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ClienteDeduplicacaoTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: VendasRepository

    private val produtoAmendoim = Produto(
        nome = "Amendoim",
        variante = "90g",
        custoAquisicaoCentavos = 150L,
        precoVendaCentavos = 300L,
        estoqueAtual = 100
    )

    private val produtoRefri = Produto(
        nome = "Refrigerante",
        variante = "Lata",
        custoAquisicaoCentavos = 200L,
        precoVendaCentavos = 500L,
        estoqueAtual = 100
    )

    private val produtoChocolate = Produto(
        nome = "Chocolate",
        variante = null,
        custoAquisicaoCentavos = 250L,
        precoVendaCentavos = 400L,
        estoqueAtual = 100
    )

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VendasRepository(database)

        repository.salvarProduto(produtoAmendoim)
        repository.salvarProduto(produtoRefri)
        repository.salvarProduto(produtoChocolate)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun normalizacao_casosEquivalentesExigidosPelaEspecificacao() {
        assertEquals("joao", Formatters.normalizarNomeCliente("João"))
        assertEquals("joao", Formatters.normalizarNomeCliente("JOÃO"))
        assertEquals("joao", Formatters.normalizarNomeCliente("joão"))
        assertEquals("joao", Formatters.normalizarNomeCliente("João  "))
        assertEquals("joao", Formatters.normalizarNomeCliente("  joao "))
        assertEquals("maria silva", Formatters.normalizarNomeCliente("MARIA SILVA"))
        assertEquals("maria silva", Formatters.normalizarNomeCliente("Maria Silva"))
        assertEquals("maria silva", Formatters.normalizarNomeCliente("  Maria   Silva  "))
    }

    /**
     * CENÁRIO A — Variação de escrita
     * "João comprou 2 amendoins"
     * "JOÃO comprou 1 refrigerante"
     * "joão me pagou 10 reais"
     * Resultado esperado: um único cliente e 3 movimentações vinculadas a ele.
     */
    @Test
    fun cenarioA_variacaoDeEscrita_criaUnicoClienteETresMovimentacoes() = runTest {
        val produtos = repository.getProdutosSync()

        val r1 = LivroCaixaParser.interpretar("João comprou 2 amendoins", produtos, repository.getClientesSync())
        assertTrue(r1.valido)
        repository.confirmarOperacoes(r1.itens)

        val r2 = LivroCaixaParser.interpretar("JOÃO comprou 1 refrigerante", produtos, repository.getClientesSync())
        assertTrue(r2.valido)
        repository.confirmarOperacoes(r2.itens)

        val r3 = LivroCaixaParser.interpretar("joão me pagou 10 reais", produtos, repository.getClientesSync())
        assertTrue(r3.valido)
        repository.confirmarOperacoes(r3.itens)

        val clientes = repository.clientesComSaldo.first()
        assertEquals("Deve haver exatamente 1 cliente cadastrado", 1, clientes.size)

        val cliente = clientes.first()
        val movimentacoes = repository.getMovimentacoesCliente(cliente.cliente.id).first()
        assertEquals("Devem existir exatamente 3 movimentações para este cliente", 3, movimentacoes.size)

        // Compra 1: 2 * 300 = 600 (débito)
        // Compra 2: 1 * 500 = 500 (débito)
        // Pagamento: 1000 (crédito)
        // Saldo = 1000 - 1100 = -100 (devedor de R$ 1,00)
        assertEquals(-100L, cliente.saldoCentavos)
    }

    /**
     * CENÁRIO B — Espaços excedentes e múltiplos
     * "Maria comprou 1 chocolate"
     * "Maria  comprou 2 chocolates"
     * "Maria me pagou 5 reais"
     * Resultado esperado: um único cliente.
     */
    @Test
    fun cenarioB_espacosExcedentesEMultiplos_criaUnicoCliente() = runTest {
        val produtos = repository.getProdutosSync()

        val r1 = LivroCaixaParser.interpretar("Maria comprou 1 chocolate", produtos, repository.getClientesSync())
        repository.confirmarOperacoes(r1.itens)

        val r2 = LivroCaixaParser.interpretar("Maria  comprou 2 chocolates", produtos, repository.getClientesSync())
        repository.confirmarOperacoes(r2.itens)

        val r3 = LivroCaixaParser.interpretar("Maria me pagou 5 reais", produtos, repository.getClientesSync())
        repository.confirmarOperacoes(r3.itens)

        val clientes = repository.clientesComSaldo.first()
        assertEquals("Deve haver exatamente 1 cliente cadastrado", 1, clientes.size)
        assertEquals("Maria", clientes.first().cliente.nome)

        val movs = repository.getMovimentacoesCliente(clientes.first().cliente.id).first()
        assertEquals(3, movs.size)
    }

    /**
     * CENÁRIO C — Novo cliente
     * Registrar compra para um nome nunca antes utilizado.
     * Resultado esperado: criação automática de um único cliente com nome normalizado.
     */
    @Test
    fun cenarioC_novoCliente_criacaoAutomaticaDeUmUnicoCliente() = runTest {
        val produtos = repository.getProdutosSync()
        val r = LivroCaixaParser.interpretar("Carlos Souza comprou 1 refrigerante", produtos, repository.getClientesSync())
        assertTrue(r.valido)
        repository.confirmarOperacoes(r.itens)

        val clientes = repository.clientesComSaldo.first()
        assertEquals(1, clientes.size)
        assertEquals("Carlos Souza", clientes.first().cliente.nome)
        assertEquals("carlos souza", clientes.first().cliente.nomeNormalizado)
    }

    /**
     * CENÁRIO D — Clientes diferentes (não confundir com aproximação)
     * "João Silva comprou 1 produto"
     * "João Souza comprou 1 produto"
     * Resultado esperado: dois clientes distintos.
     */
    @Test
    fun cenarioD_clientesDiferentes_criaDoisClientesDistintos() = runTest {
        val produtos = repository.getProdutosSync()

        val r1 = LivroCaixaParser.interpretar("João Silva comprou 1 chocolate", produtos, repository.getClientesSync())
        repository.confirmarOperacoes(r1.itens)

        val r2 = LivroCaixaParser.interpretar("João Souza comprou 1 chocolate", produtos, repository.getClientesSync())
        repository.confirmarOperacoes(r2.itens)

        val clientes = repository.clientesComSaldo.first()
        assertEquals("Devem existir 2 clientes distintos", 2, clientes.size)
        val nomes = clientes.map { it.cliente.nome }.toSet()
        assertTrue(nomes.contains("João Silva"))
        assertTrue(nomes.contains("João Souza"))
    }

    /**
     * CENÁRIO E — Repetição de operações para o mesmo cliente
     * Registrar várias compras e pagamentos ao longo do tempo.
     * Resultado esperado: reutilização do mesmo cadastro sem duplicidade.
     */
    @Test
    fun cenarioE_repeticaoDeOperacoes_reutilizaMesmoCadastroSemDuplicar() = runTest {
        val produtos = repository.getProdutosSync()

        for (i in 1..5) {
            val texto = if (i % 2 == 0) "Ana comprou 1 chocolate" else "Ana me pagou 2 reais"
            val r = LivroCaixaParser.interpretar(texto, produtos, repository.getClientesSync())
            repository.confirmarOperacoes(r.itens)
        }

        val clientes = repository.clientesComSaldo.first()
        assertEquals(1, clientes.size)
        val movs = repository.getMovimentacoesCliente(clientes.first().cliente.id).first()
        assertEquals(5, movs.size)
    }

    /**
     * CENÁRIO F — Migração com histórico anterior e duplicidades existentes
     * Simula a existência de vários cadastros equivalentes (João, JOÃO, joão, João com espaços)
     * contendo movimentações e vendas associadas em versões anteriores da base.
     * Executa a MIGRATION_4_5.
     * Valida:
     * - Consolidação em 1 único cliente principal
     * - Preservação de TODAS as movimentações e vendas
     * - Preservação de valores, datas e horários originais
     * - Saldo consolidado corretamente a partir do histórico
     */
    @Test
    fun cenarioF_migracaoDeHistoricoAnterior_unificaDuplicadosEPreservaMovimentacoes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_4_5.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Cria schema da versão 4
                        db.execSQL("CREATE TABLE IF NOT EXISTS `produtos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nome` TEXT NOT NULL COLLATE NOCASE, `variante` TEXT COLLATE NOCASE, `custoAquisicaoCentavos` INTEGER NOT NULL, `precoVendaCentavos` INTEGER NOT NULL, `estoqueAtual` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `clientes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nome` TEXT NOT NULL COLLATE NOCASE)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clientes_nome` ON `clientes` (`nome`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `movimentacoes_conta` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clienteId` INTEGER NOT NULL, `tipo` TEXT NOT NULL, `valorCentavos` INTEGER NOT NULL, `dataHoraOcorrencia` INTEGER NOT NULL, `dataHoraRegistro` INTEGER NOT NULL, `descricao` TEXT NOT NULL, `vendaId` INTEGER, FOREIGN KEY(`clienteId`) REFERENCES `clientes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `vendas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clienteId` INTEGER NOT NULL, `dataHoraOcorrencia` INTEGER NOT NULL, `dataHoraRegistro` INTEGER NOT NULL, `valorTotalCentavos` INTEGER NOT NULL, `observacao` TEXT, FOREIGN KEY(`clienteId`) REFERENCES `clientes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `itens_venda` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vendaId` INTEGER NOT NULL, `produtoId` INTEGER NOT NULL, `nomeProdutoSnapshot` TEXT NOT NULL, `varianteSnapshot` TEXT, `quantidade` INTEGER NOT NULL, `precoUnitarioCentavos` INTEGER NOT NULL, `custoUnitarioCentavos` INTEGER NOT NULL, `valorTotalItemCentavos` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `rascunho_livro_caixa` (`id` INTEGER NOT NULL, `texto` TEXT NOT NULL, `dataHoraInicioRascunho` INTEGER NOT NULL, `dataHoraUltimaModificacao` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `compras_nota` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fornecedor` TEXT, `numeroNota` TEXT, `dataHoraCompra` INTEGER NOT NULL, `dataHoraRegistro` INTEGER NOT NULL, `valorTotalCentavos` INTEGER NOT NULL, `observacao` TEXT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `itens_compra` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `compraId` INTEGER NOT NULL, `produtoId` INTEGER, `nomeProdutoSnapshot` TEXT NOT NULL, `varianteSnapshot` TEXT, `quantidade` INTEGER NOT NULL, `custoUnitarioCentavos` INTEGER NOT NULL, `valorTotalItemCentavos` INTEGER NOT NULL)")

                        // Insere duplicidades da versão 4 (clientes com IDs 1, 2 e 3 que são a mesma pessoa após normalização)
                        // Note que no SQLite antigo com COLLATE NOCASE, "João" e "joão" podem ter sido inseridos se criados com espaços ou variações diacríticas
                        db.execSQL("INSERT INTO clientes (id, nome) VALUES (1, 'João')")
                        db.execSQL("INSERT INTO clientes (id, nome) VALUES (2, 'Joao')") // sem acento
                        db.execSQL("INSERT INTO clientes (id, nome) VALUES (3, '  JOÃO  ')") // com espaços

                        // Outro cliente independente (ID 4)
                        db.execSQL("INSERT INTO clientes (id, nome) VALUES (4, 'Maria Silva')")

                        // Movimentações para cada duplicado
                        // Cliente 1 (João): Compra de R$ 5,00 (500 centavos) em 1000L
                        db.execSQL("INSERT INTO movimentacoes_conta (id, clienteId, tipo, valorCentavos, dataHoraOcorrencia, dataHoraRegistro, descricao) VALUES (1, 1, 'COMPRA', 500, 1000, 1000, 'Compra 1')")
                        // Cliente 2 (Joao): Compra de R$ 10,00 (1000 centavos) em 2000L
                        db.execSQL("INSERT INTO movimentacoes_conta (id, clienteId, tipo, valorCentavos, dataHoraOcorrencia, dataHoraRegistro, descricao) VALUES (2, 2, 'COMPRA', 1000, 2000, 2000, 'Compra 2')")
                        // Cliente 3 (JOÃO): Pagamento de R$ 8,00 (800 centavos) em 3000L
                        db.execSQL("INSERT INTO movimentacoes_conta (id, clienteId, tipo, valorCentavos, dataHoraOcorrencia, dataHoraRegistro, descricao) VALUES (3, 3, 'PAGAMENTO', 800, 3000, 3000, 'Pagamento 1')")

                        // Vendas vinculadas aos clientes 1 e 2
                        db.execSQL("INSERT INTO vendas (id, clienteId, dataHoraOcorrencia, dataHoraRegistro, valorTotalCentavos) VALUES (1, 1, 1000, 1000, 500)")
                        db.execSQL("INSERT INTO vendas (id, clienteId, dataHoraOcorrencia, dataHoraRegistro, valorTotalCentavos) VALUES (2, 2, 2000, 2000, 1000)")

                        // Movimentação para Maria Silva (ID 4)
                        db.execSQL("INSERT INTO movimentacoes_conta (id, clienteId, tipo, valorCentavos, dataHoraOcorrencia, dataHoraRegistro, descricao) VALUES (4, 4, 'PAGAMENTO', 2000, 4000, 4000, 'Pagamento Maria')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build()
        )

        // Dispara criação inicial no helper v4
        openHelper.writableDatabase.close()

        // Abre banco migrando para v5 com AppDatabase
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        val repo = VendasRepository(migratedDb)

        try {
            runTest {
                val clientes = repo.clientesComSaldo.first()
                assertEquals("Devem restar apenas 2 clientes (João unificado e Maria Silva)", 2, clientes.size)

                val joao = clientes.first { it.cliente.nomeNormalizado == "joao" }
                assertEquals("ID principal mantido deve ser o menor (1)", 1L, joao.cliente.id)
                assertEquals("João", joao.cliente.nome)

                // Saldo esperado: -500 + -1000 + 800 = -700 (saldo devedor de R$ 7,00)
                assertEquals(-700L, joao.saldoCentavos)

                val movsJoao = repo.getMovimentacoesCliente(joao.cliente.id).first()
                assertEquals("Todas as 3 movimentações foram preservadas e migradas", 3, movsJoao.size)

                val valores = movsJoao.map { it.valorCentavos }.toSet()
                assertTrue(valores.contains(500L))
                assertTrue(valores.contains(1000L))
                assertTrue(valores.contains(800L))

                val datas = movsJoao.map { it.dataHoraOcorrencia }.toSet()
                assertTrue("Datas originais preservadas", datas.contains(1000L))
                assertTrue("Datas originais preservadas", datas.contains(2000L))
                assertTrue("Datas originais preservadas", datas.contains(3000L))

                val maria = clientes.first { it.cliente.nomeNormalizado == "maria silva" }
                assertEquals(4L, maria.cliente.id)
                assertEquals(2000L, maria.saldoCentavos)
            }
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    /**
     * CENÁRIO G — Concorrência
     * Simula duas tentativas concorrentes de obter ou criar o mesmo cliente.
     * Resultado esperado: apenas um cliente persistido e ambas as coroutines obtêm o mesmo ID.
     */
    @Test
    fun cenarioG_concorrencia_apenasUmCadastroPersistido() = runTest {
        val d1 = async(Dispatchers.Default) {
            repository.getOrCreateCliente("Lucas Oliveira")
        }
        val d2 = async(Dispatchers.Default) {
            repository.getOrCreateCliente("lucas oliveira")
        }

        val c1 = d1.await()
        val c2 = d2.await()

        assertEquals("Ambas as chamadas devem retornar o mesmo ID", c1.id, c2.id)

        val clientes = repository.clientesComSaldo.first()
        val clientesLucas = clientes.filter { it.cliente.nomeNormalizado == "lucas oliveira" }
        assertEquals("Deve existir apenas um cliente lucas oliveira no banco", 1, clientesLucas.size)
    }

    /**
     * Sugestões de nomes parecidos (Levenshtein conservador)
     */
    @Test
    fun sugestoes_identificaNomesParecidosSemUnificarAutomaticamente() = runTest {
        val produtos = repository.getProdutosSync()
        repository.salvarCliente(Cliente(nome = "Wellington"))

        // Digitar "Welington" (com 1 'l')
        val r = LivroCaixaParser.interpretar("Welington comprou 1 chocolate", produtos, repository.getClientesSync())
        assertTrue(r.valido)

        val item = r.itens.first()
        // Não unifica automaticamente porque são strings distintas
        assertFalse("Não deve marcar como o mesmo cliente automaticamente", item.nomeCliente == "Wellington")
        // Mas deve sugerir Wellington
        assertTrue(item.sugestoesClientesSemelhantes.any { it.nome == "Wellington" })
    }
}
