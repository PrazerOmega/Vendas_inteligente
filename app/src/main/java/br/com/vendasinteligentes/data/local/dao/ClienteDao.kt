package br.com.vendasinteligentes.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.model.ClienteComSaldo
import kotlinx.coroutines.flow.Flow

@Dao
interface ClienteDao {
    @Query("SELECT * FROM clientes ORDER BY nome COLLATE NOCASE ASC")
    fun getAll(): Flow<List<Cliente>>

    @Query("SELECT * FROM clientes ORDER BY nome COLLATE NOCASE ASC")
    suspend fun getAllSync(): List<Cliente>

    @Query("SELECT * FROM clientes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Cliente?

    @Query("SELECT * FROM clientes WHERE LOWER(TRIM(nome)) = LOWER(TRIM(:nome)) LIMIT 1")
    suspend fun findByNome(nome: String): Cliente?

    @Query("SELECT * FROM clientes WHERE nomeNormalizado = :nomeNormalizado LIMIT 1")
    suspend fun findByNomeNormalizado(nomeNormalizado: String): Cliente?

    @Query("""
        SELECT c.id, c.nome, c.nomeNormalizado,
               COALESCE(SUM(CASE WHEN m.tipo = 'PAGAMENTO' THEN m.valorCentavos ELSE -m.valorCentavos END), 0) AS saldoCentavos
        FROM clientes c
        LEFT JOIN movimentacoes_conta m ON m.clienteId = c.id
        GROUP BY c.id
        ORDER BY c.nome COLLATE NOCASE ASC
    """)
    fun getAllComSaldo(): Flow<List<ClienteComSaldo>>

    @Query("""
        SELECT c.id, c.nome, c.nomeNormalizado,
               COALESCE(SUM(CASE WHEN m.tipo = 'PAGAMENTO' THEN m.valorCentavos ELSE -m.valorCentavos END), 0) AS saldoCentavos
        FROM clientes c
        LEFT JOIN movimentacoes_conta m ON m.clienteId = c.id
        WHERE c.id = :clienteId
        GROUP BY c.id
        LIMIT 1
    """)
    fun getClienteComSaldoById(clienteId: Long): Flow<ClienteComSaldo?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(cliente: Cliente): Long

    @Update
    suspend fun update(cliente: Cliente)

    @Delete
    suspend fun delete(cliente: Cliente)
}
