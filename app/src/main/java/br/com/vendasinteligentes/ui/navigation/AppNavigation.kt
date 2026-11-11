package br.com.vendasinteligentes.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

enum class DestinoApp(
    val rota: String,
    val titulo: String,
    val icone: ImageVector
) {
    LIVRO_CAIXA("livro_caixa", "Livro-caixa", Icons.Default.Edit),
    PRODUTOS("produtos", "Produtos", Icons.Default.ShoppingCart),
    COMPRAS("compras", "Compras", Icons.AutoMirrored.Filled.List),
    CLIENTES("clientes", "Clientes", Icons.Default.Person),
    HISTORICO("historico", "Histórico", Icons.Default.DateRange)
}
