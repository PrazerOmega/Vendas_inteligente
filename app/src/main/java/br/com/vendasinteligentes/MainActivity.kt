package br.com.vendasinteligentes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.vendasinteligentes.ui.navigation.DestinoApp
import br.com.vendasinteligentes.ui.screens.ClientesScreen
import br.com.vendasinteligentes.ui.screens.CompraNotaScreen
import br.com.vendasinteligentes.ui.screens.HistoricoScreen
import br.com.vendasinteligentes.ui.screens.LivroCaixaScreen
import br.com.vendasinteligentes.ui.screens.ProdutosScreen
import br.com.vendasinteligentes.ui.theme.VendasInteligentesTheme
import br.com.vendasinteligentes.ui.viewmodel.ClientesViewModel
import br.com.vendasinteligentes.ui.viewmodel.CompraNotaViewModel
import br.com.vendasinteligentes.ui.viewmodel.HistoricoViewModel
import br.com.vendasinteligentes.ui.viewmodel.LivroCaixaViewModel
import br.com.vendasinteligentes.ui.viewmodel.ProdutosViewModel

class MainActivity : ComponentActivity() {

    private val livroCaixaViewModel by viewModels<LivroCaixaViewModel> {
        LivroCaixaViewModel.Factory((application as VendasApplication).repository)
    }

    private val produtosViewModel by viewModels<ProdutosViewModel> {
        ProdutosViewModel.Factory((application as VendasApplication).repository)
    }

    private val clientesViewModel by viewModels<ClientesViewModel> {
        ClientesViewModel.Factory((application as VendasApplication).repository)
    }

    private val historicoViewModel by viewModels<HistoricoViewModel> {
        HistoricoViewModel.Factory((application as VendasApplication).repository)
    }

    private val compraNotaViewModel by viewModels<CompraNotaViewModel> {
        CompraNotaViewModel.Factory((application as VendasApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VendasInteligentesTheme {
                MainApp(
                    livroCaixaViewModel = livroCaixaViewModel,
                    produtosViewModel = produtosViewModel,
                    clientesViewModel = clientesViewModel,
                    historicoViewModel = historicoViewModel,
                    compraNotaViewModel = compraNotaViewModel
                )
            }
        }
    }
}

@Composable
fun MainApp(
    livroCaixaViewModel: LivroCaixaViewModel,
    produtosViewModel: ProdutosViewModel,
    clientesViewModel: ClientesViewModel,
    historicoViewModel: HistoricoViewModel,
    compraNotaViewModel: CompraNotaViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val rotaAtual = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                DestinoApp.values().forEach { destino ->
                    val selecionado = rotaAtual == destino.rota
                    NavigationBarItem(
                        selected = selecionado,
                        onClick = {
                            if (rotaAtual != destino.rota) {
                                navController.navigate(destino.rota) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destino.icone, contentDescription = destino.titulo) },
                        label = { Text(destino.titulo) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DestinoApp.LIVRO_CAIXA.rota,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(DestinoApp.LIVRO_CAIXA.rota) {
                LivroCaixaScreen(viewModel = livroCaixaViewModel)
            }
            composable(DestinoApp.PRODUTOS.rota) {
                ProdutosScreen(viewModel = produtosViewModel)
            }
            composable(DestinoApp.COMPRAS.rota) {
                CompraNotaScreen(viewModel = compraNotaViewModel)
            }
            composable(DestinoApp.CLIENTES.rota) {
                ClientesScreen(viewModel = clientesViewModel)
            }
            composable(DestinoApp.HISTORICO.rota) {
                HistoricoScreen(viewModel = historicoViewModel)
            }
        }
    }
}
