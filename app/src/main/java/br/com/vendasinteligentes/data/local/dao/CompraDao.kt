package br.com.vendasinteligentes.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import br.com.vendasinteligentes.data.local.entity.CompraNota
import br.com.vendasinteligentes.data.local.entity.ItemCompra
import br.com.vendasinteligentes.data.local.model.CompraComItens
import kotlinx.coroutines.flow.Flow

@Dao
interface CompraDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCompra(compra: CompraNota): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItens(itens: List<ItemCompra>)

    @Delete
    suspend fun deleteCompra(compra: CompraNota)

    @Transaction
    @Query("SELECT * FROM compras_nota ORDER BY dataHoraCompra DESC, id DESC")
    fun getAllComprasComItens(): Flow<List<CompraComItens>>

    @Transaction
    @Query("SELECT * FROM compras_nota WHERE id = :compraId LIMIT 1")
    suspend fun getCompraComItensById(compraId: Long): CompraComItens?
}
