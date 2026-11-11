package br.com.vendasinteligentes.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import br.com.vendasinteligentes.data.local.entity.Produto
import kotlinx.coroutines.flow.Flow

@Dao
interface ProdutoDao {
    @Query("SELECT * FROM produtos ORDER BY nome ASC, variante ASC")
    fun getAll(): Flow<List<Produto>>

    @Query("SELECT * FROM produtos ORDER BY nome ASC, variante ASC")
    suspend fun getAllSync(): List<Produto>

    @Query("SELECT * FROM produtos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Produto?

    @Query("SELECT * FROM produtos WHERE LOWER(TRIM(nome)) = LOWER(TRIM(:nome)) AND (:variante IS NULL OR LOWER(TRIM(variante)) = LOWER(TRIM(:variante))) LIMIT 1")
    suspend fun findByNomeEVariante(nome: String, variante: String?): Produto?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(produto: Produto): Long

    @Update
    suspend fun update(produto: Produto)

    @Delete
    suspend fun delete(produto: Produto)

    @Query("UPDATE produtos SET estoqueAtual = :novoEstoque WHERE id = :id")
    suspend fun atualizarEstoque(id: Long, novoEstoque: Int)
}
