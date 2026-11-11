package br.com.vendasinteligentes.data.local

import androidx.room.TypeConverter
import br.com.vendasinteligentes.data.local.entity.TipoMovimentacao

class Converters {
    @TypeConverter
    fun fromTipoMovimentacao(value: TipoMovimentacao): String = value.name

    @TypeConverter
    fun toTipoMovimentacao(value: String): TipoMovimentacao = TipoMovimentacao.valueOf(value)
}
