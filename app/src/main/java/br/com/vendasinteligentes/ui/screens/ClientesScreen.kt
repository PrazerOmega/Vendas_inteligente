package br.com.vendasinteligentes.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendasinteligentes.data.local.entity.TipoMovimentacao
import br.com.vendasinteligentes.data.local.model.ClienteComSaldo
import br.com.vendasinteligentes.data.local.model.StatusSaldo
import br.com.vendasinteligentes.ui.theme.ErroVermelho
import br.com.vendasinteligentes.ui.theme.PrimariaVerde
import br.com.vendasinteligentes.ui.theme.SaldoQuitado
import br.com.vendasinteligentes.ui.viewmodel.ClientesViewModel
import br.com.vendasinteligentes.util.Formatters

@Composable
fun ClientesScreen(viewModel: ClientesViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.abrirDialogoNovoCliente() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Novo Cliente")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Clientes & Contas",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = state.termoBusca,
                onValueChange = { viewModel.onBuscaAlterada(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar cliente por nome...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (state.clientesComSaldo.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.termoBusca.isBlank())
                            "Nenhum cliente cadastrado ainda.\nEles são criados automaticamente pelo Livro-caixa ou pelo botão +."
                        else "Nenhum cliente encontrado para \"${state.termoBusca}\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.clientesComSaldo, key = { it.cliente.id }) { item ->
                        ClienteCard(
                            clienteComSaldo = item,
                            onClick = { viewModel.selecionarCliente(item) }
                        )
                    }
                }
            }
        }

        // Diálogo de Detalhes / Extrato da Conta Corrente do Cliente
        state.clienteSelecionado?.let { selecionado ->
            AlertDialog(
                onDismissRequest = { viewModel.selecionarCliente(null) },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selecionado.cliente.nome, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { viewModel.selecionarCliente(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Resumo do saldo atual
                        val corSaldo = when (selecionado.statusSaldo) {
                            StatusSaldo.DEVEDOR -> ErroVermelho
                            StatusSaldo.CREDOR -> PrimariaVerde
                            StatusSaldo.QUITADO -> SaldoQuitado
                        }
                        val textoSaldo = when (selecionado.statusSaldo) {
                            StatusSaldo.DEVEDOR -> "Saldo Devedor: ${Formatters.formatarMoeda(-selecionado.saldoCentavos)}"
                            StatusSaldo.CREDOR -> "Crédito Disponível: ${Formatters.formatarMoeda(selecionado.saldoCentavos)}"
                            StatusSaldo.QUITADO -> "Conta Quitada (R$ 0,00)"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = corSaldo.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = textoSaldo,
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Bold,
                                color = corSaldo
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Histórico de Movimentações:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))

                        if (state.extratoSelecionado.isEmpty()) {
                            Text(
                                text = "Nenhuma movimentação registrada.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(state.extratoSelecionado, key = { it.id }) { mov ->
                                    val isCompra = mov.tipo == TipoMovimentacao.COMPRA
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mov.descricao,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = Formatters.formatarDataCurta(mov.dataHoraOcorrencia),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = if (isCompra) "- ${Formatters.formatarMoeda(mov.valorCentavos)}"
                                                   else "+ ${Formatters.formatarMoeda(mov.valorCentavos)}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isCompra) ErroVermelho else PrimariaVerde
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.selecionarCliente(null) }) {
                        Text("Fechar")
                    }
                }
            )
        }

        // Diálogo para Novo Cliente
        if (state.mostrarDialogoNovoCliente) {
            NovoClienteDialog(
                erro = state.erro,
                onSalvar = { nome -> viewModel.salvarCliente(nome) },
                onCancelar = { viewModel.fecharDialogo() }
            )
        }
    }
}

@Composable
fun ClienteCard(
    clienteComSaldo: ClienteComSaldo,
    onClick: () -> Unit
) {
    val corSaldo = when (clienteComSaldo.statusSaldo) {
        StatusSaldo.DEVEDOR -> ErroVermelho
        StatusSaldo.CREDOR -> PrimariaVerde
        StatusSaldo.QUITADO -> SaldoQuitado
    }

    val labelSaldo = when (clienteComSaldo.statusSaldo) {
        StatusSaldo.DEVEDOR -> "Deve ${Formatters.formatarMoeda(-clienteComSaldo.saldoCentavos)}"
        StatusSaldo.CREDOR -> "Crédito ${Formatters.formatarMoeda(clienteComSaldo.saldoCentavos)}"
        StatusSaldo.QUITADO -> "Conta quitada"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clienteComSaldo.cliente.nome,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = labelSaldo,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = corSaldo
            )
        }
    }
}

@Composable
fun NovoClienteDialog(
    erro: String?,
    onSalvar: (nome: String) -> Unit,
    onCancelar: () -> Unit
) {
    var nome by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Novo Cliente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (erro != null) {
                    Text(erro, color = ErroVermelho, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome do Cliente") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSalvar(nome) }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Cancelar")
            }
        }
    )
}
