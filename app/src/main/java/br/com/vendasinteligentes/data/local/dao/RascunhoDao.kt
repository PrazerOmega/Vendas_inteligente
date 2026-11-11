package br.com.vendasinteligentes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.vendasinteligentes.data.local.entity.RascunhoLivroCaixa
import kotlinx.coroutines.flow.Flow

@Dao
interface RascunhoDao {
    @Query("SELECT * FROM rascunho_livro_caixa WHERE id = 1 LIMIT 1")
    fun getRascunho(): Flow<RascunhoLivroCaixa?>

    @Query("SELECT * FROM rascunho_livro_caixa WHERE id = 1 LIMIT 1")
    suspend fun getRascunhoSync(): RascunhoLivroCaixa?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarRascunho(rascunho: RascunhoLivroCaixa)

    @Query("DELETE FROM rascunho_livro_caixa WHERE id = 1")
    suspend fun limparRascunho()
}
