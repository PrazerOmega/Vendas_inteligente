package br.com.vendasinteligentes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import br.com.vendasinteligentes.data.local.entity.ItemVenda
import br.com.vendasinteligentes.data.local.entity.Venda
import br.com.vendasinteligentes.data.local.model.VendaComItens
import kotlinx.coroutines.flow.Flow

@Dao
interface VendaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVenda(venda: Venda): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItens(itens: List<ItemVenda>)

    @Transaction
    @Query("SELECT * FROM vendas ORDER BY dataHoraOcorrencia DESC, id DESC")
    fun getAllVendasComItens(): Flow<List<VendaComItens>>

    @Transaction
    @Query("SELECT * FROM vendas WHERE id = :vendaId LIMIT 1")
    suspend fun getVendaComItensById(vendaId: Long): VendaComItens?
}
