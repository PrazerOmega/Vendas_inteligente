package br.com.vendasinteligentes.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import br.com.vendasinteligentes.data.local.entity.CompraNota
import br.com.vendasinteligentes.data.local.entity.ItemCompra

data class CompraComItens(
    @Embedded
    val compra: CompraNota,
    @Relation(
        parentColumn = "id",
        entityColumn = "compraId"
    )
    val itens: List<ItemCompra>
)
