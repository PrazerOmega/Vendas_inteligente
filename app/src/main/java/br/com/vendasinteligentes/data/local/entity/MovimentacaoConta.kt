package br.com.vendasinteligentes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movimentacoes_conta",
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
data class MovimentacaoConta(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clienteId: Long,
    val tipo: TipoMovimentacao,
    val valorCentavos: Long, // Magnitude positiva em centavos
    val dataHoraOcorrencia: Long, // Data/hora do início do rascunho
    val dataHoraRegistro: Long,   // Data/hora da confirmação do lote
    val descricao: String,
    val vendaId: Long? = null
) {
    /**
     * Saldo = soma dos créditos + soma dos débitos (débitos negativos, créditos positivos).
     */
    val valorAssinadoCentavos: Long
        get() = if (tipo == TipoMovimentacao.COMPRA) -valorCentavos else valorCentavos
}
