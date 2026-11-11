package br.com.vendasinteligentes.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.vendasinteligentes.util.Formatters

@Entity(
    tableName = "clientes",
    indices = [
        Index(value = ["nomeNormalizado"], unique = true)
    ]
)
data class Cliente(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val nome: String,
    val nomeNormalizado: String = Formatters.normalizarNomeCliente(nome)
)
