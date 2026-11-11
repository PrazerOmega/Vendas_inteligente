package br.com.vendasinteligentes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.ui.theme.ErroVermelho
import br.com.vendasinteligentes.ui.theme.PrimariaVerde
import br.com.vendasinteligentes.ui.viewmodel.ProdutosViewModel
import br.com.vendasinteligentes.util.Formatters

@Composable
fun ProdutosScreen(viewModel: ProdutosViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.abrirDialogoNovoProduto() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Novo Produto")
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
                text = "Produtos & Estoque",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Barra de busca
            OutlinedTextField(
                value = state.termoBusca,
                onValueChange = { viewModel.onBuscaAlterada(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por nome ou variante...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (state.produtos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.termoBusca.isBlank())
                            "Nenhum produto cadastrado.\nToque no botão + para adicionar."
                        else "Nenhum produto encontrado para \"${state.termoBusca}\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.produtos, key = { it.id }) { produto ->
                        ProdutoItemCard(
                            produto = produto,
                            onEditar = { viewModel.abrirDialogoEdicao(produto) },
                            onExcluir = { viewModel.excluirProduto(produto) }
                        )
                    }
                }
            }
        }

        if (state.mostrarDialogo) {
            ProdutoDialog(
                produto = state.produtoEmEdicao,
                erro = state.erro,
                onSalvar = { nome, variante, custo, preco, estoque ->
                    viewModel.salvarProduto(nome, variante, custo, preco, estoque)
                },
                onCancelar = { viewModel.fecharDialogo() }
            )
        }
    }
}

@Composable
fun ProdutoItemCard(
    produto: Produto,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = produto.nomeExibicao,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Estoque: ${produto.estoqueAtual} unid.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (produto.estoqueAtual > 0) PrimariaVerde else ErroVermelho
                    )
                }

                Row {
                    IconButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onExcluir) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Preço de Venda", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.formatarMoeda(produto.precoVendaCentavos), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Custo de Aquisição", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.formatarMoeda(produto.custoAquisicaoCentavos), fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("Margem Unitária", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.formatarMoeda(produto.margemBrutaCentavos), fontWeight = FontWeight.Bold, color = PrimariaVerde)
                }
            }
        }
    }
}

@Composable
fun ProdutoDialog(
    produto: Produto?,
    erro: String?,
    onSalvar: (nome: String, variante: String?, custo: String, preco: String, estoque: String) -> Unit,
    onCancelar: () -> Unit
) {
    var nome by remember { mutableStateOf(produto?.nome ?: "") }
    var variante by remember { mutableStateOf(produto?.variante ?: "") }
    var custo by remember {
        mutableStateOf(if (produto != null) (produto.custoAquisicaoCentavos / 100.0).toString() else "")
    }
    var preco by remember {
        mutableStateOf(if (produto != null) (produto.precoVendaCentavos / 100.0).toString() else "")
    }
    var estoque by remember {
        mutableStateOf(produto?.estoqueAtual?.toString() ?: "0")
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (produto == null) "Novo Produto" else "Editar Produto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (erro != null) {
                    Text(erro, color = ErroVermelho, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome do Produto *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = variante,
                    onValueChange = { variante = it },
                    label = { Text("Variante (ex: 90g, Lata, Preto)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = custo,
                        onValueChange = { custo = it },
                        label = { Text("Custo (R$)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = preco,
                        onValueChange = { preco = it },
                        label = { Text("Preço (R$)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = estoque,
                    onValueChange = { estoque = it },
                    label = { Text("Estoque Atual (unidades)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSalvar(nome, variante, custo, preco, estoque)
            }) {
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
