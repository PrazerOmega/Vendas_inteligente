package br.com.vendasinteligentes.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import br.com.vendasinteligentes.data.local.entity.ItemVenda
import br.com.vendasinteligentes.data.local.entity.Venda

data class VendaComItens(
    @Embedded
    val venda: Venda,
    @Relation(
        parentColumn = "id",
        entityColumn = "vendaId"
    )
    val itens: List<ItemVenda>
)
