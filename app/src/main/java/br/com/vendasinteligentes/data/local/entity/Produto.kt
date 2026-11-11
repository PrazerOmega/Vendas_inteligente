package br.com.vendasinteligentes.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "produtos",
    indices = [
        Index(value = ["nome", "variante"], unique = true)
    ]
)
data class Produto(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val nome: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val variante: String? = null,
    val custoAquisicaoCentavos: Long,
    val precoVendaCentavos: Long,
    val estoqueAtual: Int
) {
    val margemBrutaCentavos: Long
        get() = precoVendaCentavos - custoAquisicaoCentavos

    val nomeExibicao: String
        get() = if (variante.isNullOrBlank()) nome else "$nome ($variante)"
}
