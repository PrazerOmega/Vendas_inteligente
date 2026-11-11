package br.com.vendasinteligentes.data.local.model

import androidx.room.Embedded
import br.com.vendasinteligentes.data.local.entity.Cliente

enum class StatusSaldo {
    DEVEDOR, // Saldo negativo (cliente deve)
    QUITADO, // Saldo zero (conta quitada)
    CREDOR   // Saldo positivo (cliente possui crédito)
}

data class ClienteComSaldo(
    @Embedded
    val cliente: Cliente,
    val saldoCentavos: Long = 0L
) {
    val statusSaldo: StatusSaldo
        get() = when {
            saldoCentavos < 0 -> StatusSaldo.DEVEDOR
            saldoCentavos > 0 -> StatusSaldo.CREDOR
            else -> StatusSaldo.QUITADO
        }
}
