package br.com.vendasinteligentes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import br.com.vendasinteligentes.data.local.entity.MovimentacaoConta
import br.com.vendasinteligentes.data.local.model.MovimentacaoComCliente
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimentacaoContaDao {
    @Query("SELECT * FROM movimentacoes_conta ORDER BY dataHoraOcorrencia DESC, id DESC")
    fun getAll(): Flow<List<MovimentacaoConta>>

    @Query("SELECT * FROM movimentacoes_conta WHERE clienteId = :clienteId ORDER BY dataHoraOcorrencia DESC, id DESC")
    fun getByClienteId(clienteId: Long): Flow<List<MovimentacaoConta>>

    @Transaction
    @Query("SELECT * FROM movimentacoes_conta ORDER BY dataHoraOcorrencia DESC, id DESC")
    fun getAllWithCliente(): Flow<List<MovimentacaoComCliente>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movimentacao: MovimentacaoConta): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(movimentacoes: List<MovimentacaoConta>)

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN tipo = 'PAGAMENTO' THEN valorCentavos ELSE -valorCentavos END), 0)
        FROM movimentacoes_conta
        WHERE clienteId = :clienteId
    """)
    suspend fun calcularSaldoCliente(clienteId: Long): Long
}
