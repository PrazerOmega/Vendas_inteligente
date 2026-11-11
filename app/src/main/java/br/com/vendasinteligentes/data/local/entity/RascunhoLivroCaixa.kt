package br.com.vendasinteligentes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rascunho_livro_caixa")
data class RascunhoLivroCaixa(
    @PrimaryKey
    val id: Int = 1,
    val texto: String,
    val dataHoraInicioRascunho: Long,
    val dataHoraUltimaModificacao: Long
)
