package br.com.vendasinteligentes.data.local.entity

enum class TipoMovimentacao {
    COMPRA,   // Débito do cliente (reduz o saldo do cliente)
    PAGAMENTO // Crédito do cliente (aumenta o saldo do cliente)
}
