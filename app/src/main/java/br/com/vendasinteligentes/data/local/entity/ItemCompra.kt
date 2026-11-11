package br.com.vendasinteligentes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Item de uma nota fiscal de compra. Guarda um snapshot do nome/variante para que o
 * histórico permaneça legível mesmo se o produto for renomeado ou excluído
 * (nesse caso, [produtoId] passa a ser nulo).
 */
@Entity(
    tableName = "itens_compra",
    foreignKeys = [
        ForeignKey(
            entity = CompraNota::class,
            parentColumns = ["id"],
            childColumns = ["compraId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Produto::class,
            parentColumns = ["id"],
            childColumns = ["produtoId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["compraId"]),
        Index(value = ["produtoId"])
    ]
)
data class ItemCompra(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val compraId: Long,
    val produtoId: Long?,
    val nomeProdutoSnapshot: String,
    val varianteSnapshot: String?,
    val quantidade: Int,
    val custoUnitarioCentavos: Long,
    val valorTotalItemCentavos: Long
) {
    val nomeExibicao: String
        get() = if (varianteSnapshot.isNullOrBlank()) nomeProdutoSnapshot else "$nomeProdutoSnapshot ($varianteSnapshot)"
}
