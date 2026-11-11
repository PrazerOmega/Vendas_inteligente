package br.com.vendasinteligentes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "itens_venda",
    foreignKeys = [
        ForeignKey(
            entity = Venda::class,
            parentColumns = ["id"],
            childColumns = ["vendaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Produto::class,
            parentColumns = ["id"],
            childColumns = ["produtoId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["vendaId"]),
        Index(value = ["produtoId"])
    ]
)
data class ItemVenda(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vendaId: Long,
    val produtoId: Long,
    val nomeProdutoSnapshot: String,
    val varianteSnapshot: String?,
    val quantidade: Int,
    val precoUnitarioCentavos: Long,
    val custoUnitarioCentavos: Long,
    val valorTotalItemCentavos: Long
)
