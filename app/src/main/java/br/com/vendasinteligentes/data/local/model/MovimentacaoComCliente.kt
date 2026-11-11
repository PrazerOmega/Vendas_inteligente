package br.com.vendasinteligentes.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.entity.MovimentacaoConta

data class MovimentacaoComCliente(
    @Embedded
    val movimentacao: MovimentacaoConta,
    @Relation(
        parentColumn = "clienteId",
        entityColumn = "id"
    )
    val cliente: Cliente
)
