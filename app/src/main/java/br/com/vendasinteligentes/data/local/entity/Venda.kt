package br.com.vendasinteligentes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vendas",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["clienteId"]),
        Index(value = ["dataHoraOcorrencia"])
    ]
)
data class Venda(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clienteId: Long,
    val dataHoraOcorrencia: Long,
    val dataHoraRegistro: Long,
    val valorTotalCentavos: Long,
    val observacao: String? = null
)
