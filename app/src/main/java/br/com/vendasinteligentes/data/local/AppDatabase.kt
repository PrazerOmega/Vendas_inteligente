package br.com.vendasinteligentes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.vendasinteligentes.data.local.dao.ClienteDao
import br.com.vendasinteligentes.data.local.dao.CompraDao
import br.com.vendasinteligentes.data.local.entity.CompraNota
import br.com.vendasinteligentes.data.local.entity.ItemCompra
import br.com.vendasinteligentes.data.local.dao.MovimentacaoContaDao
import br.com.vendasinteligentes.data.local.dao.ProdutoDao
import br.com.vendasinteligentes.data.local.dao.RascunhoDao
import br.com.vendasinteligentes.data.local.dao.VendaDao
import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.entity.ItemVenda
import br.com.vendasinteligentes.data.local.entity.MovimentacaoConta
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.data.local.entity.RascunhoLivroCaixa
import br.com.vendasinteligentes.data.local.entity.Venda

@Database(
    entities = [
        Produto::class,
        Cliente::class,
        MovimentacaoConta::class,
        Venda::class,
        ItemVenda::class,
        RascunhoLivroCaixa::class,
        CompraNota::class,
        ItemCompra::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun produtoDao(): ProdutoDao
    abstract fun clienteDao(): ClienteDao
    abstract fun movimentacaoContaDao(): MovimentacaoContaDao
    abstract fun vendaDao(): VendaDao
    abstract fun rascunhoDao(): RascunhoDao
    abstract fun compraDao(): CompraDao

    companion object {
        /** Adiciona as tabelas de notas fiscais de compra sem apagar os dados existentes. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `compras_nota` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fornecedor` TEXT, " +
                        "`numeroNota` TEXT, " +
                        "`dataHoraCompra` INTEGER NOT NULL, " +
                        "`dataHoraRegistro` INTEGER NOT NULL, " +
                        "`valorTotalCentavos` INTEGER NOT NULL, " +
                        "`observacao` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_compras_nota_dataHoraCompra` " +
                        "ON `compras_nota` (`dataHoraCompra`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `itens_compra` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`compraId` INTEGER NOT NULL, " +
                        "`produtoId` INTEGER, " +
                        "`nomeProdutoSnapshot` TEXT NOT NULL, " +
                        "`varianteSnapshot` TEXT, " +
                        "`quantidade` INTEGER NOT NULL, " +
                        "`custoUnitarioCentavos` INTEGER NOT NULL, " +
                        "`valorTotalItemCentavos` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`compraId`) REFERENCES `compras_nota`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`produtoId`) REFERENCES `produtos`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_itens_compra_compraId` ON `itens_compra` (`compraId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_itens_compra_produtoId` ON `itens_compra` (`produtoId`)"
                )
            }
        }

        /**
         * Migração 4 -> 5:
         * 1. Lê todos os clientes existentes e calcula seu nome normalizado canônico.
         * 2. Agrupa clientes equivalentes pela normalização.
         * 3. Para cada grupo, elege o cliente com menor ID como registro principal (preservando seu nome e ID).
         * 4. Redireciona todas as movimentações (movimentacoes_conta) e vendas (vendas) dos IDs duplicados para o ID principal.
         * 5. Cria uma nova tabela de clientes com a coluna `nomeNormalizado` NOT NULL e índice UNIQUE em `nomeNormalizado`.
         * 6. Copia apenas os registros principais para a nova tabela e substitui a tabela antiga.
         * 7. Preserva integralmente todas as movimentações, vendas, valores, datas e horas.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Desativa foreign keys temporariamente para manipulação de tabela de clientes
                db.execSQL("PRAGMA foreign_keys = OFF")

                try {
                    // 1. Ler todos os clientes existentes
                    data class ClienteExistente(val id: Long, val nome: String, val nomeNormalizado: String)
                    val clientesExistentes = mutableListOf<ClienteExistente>()

                    val cursor = db.query("SELECT id, nome FROM clientes ORDER BY id ASC")
                    cursor.use { c ->
                        val idIdx = c.getColumnIndexOrThrow("id")
                        val nomeIdx = c.getColumnIndexOrThrow("nome")
                        while (c.moveToNext()) {
                            val id = c.getLong(idIdx)
                            val nome = c.getString(nomeIdx) ?: ""
                            val norm = br.com.vendasinteligentes.util.Formatters.normalizarNomeCliente(nome)
                            clientesExistentes.add(ClienteExistente(id, nome, norm))
                        }
                    }

                    // 2. Agrupar por nomeNormalizado
                    val grupos = clientesExistentes.groupBy { it.nomeNormalizado }
                    val mapaIdSubstituicao = mutableMapOf<Long, Long>() // duplicadoId -> principalId
                    val clientesParaManter = mutableListOf<ClienteExistente>()

                    for ((_, lista) in grupos) {
                        val principal = lista.minByOrNull { it.id } ?: continue
                        clientesParaManter.add(principal)
                        for (cliente in lista) {
                            if (cliente.id != principal.id) {
                                mapaIdSubstituicao[cliente.id] = principal.id
                            }
                        }
                    }

                    // 3. Atualizar movimentacoes_conta e vendas que apontavam para clientes duplicados
                    for ((duplicadoId, principalId) in mapaIdSubstituicao) {
                        db.execSQL(
                            "UPDATE movimentacoes_conta SET clienteId = ? WHERE clienteId = ?",
                            arrayOf(principalId, duplicadoId)
                        )
                        db.execSQL(
                            "UPDATE vendas SET clienteId = ? WHERE clienteId = ?",
                            arrayOf(principalId, duplicadoId)
                        )
                    }

                    // 4. Criar a nova tabela de clientes com a coluna nomeNormalizado
                    db.execSQL(
                        "CREATE TABLE `clientes_novo` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`nome` TEXT NOT NULL COLLATE NOCASE, " +
                            "`nomeNormalizado` TEXT NOT NULL)"
                    )

                    // 5. Inserir os clientes principais consolidados
                    for (c in clientesParaManter) {
                        db.execSQL(
                            "INSERT INTO `clientes_novo` (`id`, `nome`, `nomeNormalizado`) VALUES (?, ?, ?)",
                            arrayOf(c.id, c.nome, c.nomeNormalizado)
                        )
                    }

                    // 6. Substituir a tabela antiga
                    db.execSQL("DROP TABLE clientes")
                    db.execSQL("ALTER TABLE clientes_novo RENAME TO clientes")

                    // 7. Criar o novo índice UNIQUE em nomeNormalizado
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_clientes_nomeNormalizado` " +
                            "ON `clientes` (`nomeNormalizado`)"
                    )
                } finally {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vendas_inteligentes.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
