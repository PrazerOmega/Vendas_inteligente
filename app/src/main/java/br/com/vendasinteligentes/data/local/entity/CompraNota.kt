package br.com.vendasinteligentes.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cabeçalho de uma nota fiscal de compra (entrada de mercadoria).
 * O valor total é a soma dos itens no momento do registro.
 */
@Entity(
    tableName = "compras_nota",
    indices = [
        Index(value = ["dataHoraCompra"])
    ]
)
data class CompraNota(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fornecedor: String? = null,
    val numeroNota: String? = null,
    val dataHoraCompra: Long,
    val dataHoraRegistro: Long,
    val valorTotalCentavos: Long,
    val observacao: String? = null
)
