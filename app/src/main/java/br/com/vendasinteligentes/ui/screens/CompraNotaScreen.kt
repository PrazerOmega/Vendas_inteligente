package br.com.vendasinteligentes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.data.local.model.CompraComItens
import br.com.vendasinteligentes.domain.parser.LivroCaixaParser
import br.com.vendasinteligentes.ui.theme.ErroVermelho
import br.com.vendasinteligentes.ui.theme.PrimariaVerde
import br.com.vendasinteligentes.ui.viewmodel.CompraNotaUiState
import br.com.vendasinteligentes.ui.viewmodel.CompraNotaViewModel
import br.com.vendasinteligentes.ui.viewmodel.FormularioNota
import br.com.vendasinteligentes.ui.viewmodel.ItemNotaFormulario
import br.com.vendasinteligentes.util.Formatters

@Composable
fun CompraNotaScreen(viewModel: CompraNotaViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.mensagem) {
        state.mensagem?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.mensagemExibida()
        }
    }

    val formulario = state.formulario
    BackHandler(enabled = formulario != null) { viewModel.cancelarNota() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (formulario == null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.novaNota() },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nova nota") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (formulario == null) {
                ListaCompras(state = state, onExcluir = viewModel::solicitarExclusao)
            } else {
                FormularioNotaContent(form = formulario, viewModel = viewModel)

                formulario.itemDialogoChave?.let { chave ->
                    ItemNotaDialog(
                        itemExistente = formulario.itens.firstOrNull { it.chave == chave },
                        produtos = state.produtos,
                        erro = formulario.erroItem,
                        onSalvar = viewModel::salvarItem,
                        onCancelar = viewModel::fecharDialogoItem
                    )
                }
            }
        }

        state.compraParaExcluir?.let { compra ->
            AlertDialog(
                onDismissRequest = viewModel::cancelarExclusao,
                title = { Text("Excluir nota?") },
                text = {
                    Text(
                        "A nota de ${Formatters.formatarMoeda(compra.compra.valorTotalCentavos)} será removida " +
                            "e as quantidades lançadas serão retiradas do estoque."
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmarExclusao) {
                        Text("Excluir", color = ErroVermelho)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelarExclusao) { Text("Cancelar") }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Lista de notas registradas
// ---------------------------------------------------------------------------

@Composable
private fun ListaCompras(state: CompraNotaUiState, onExcluir: (CompraComItens) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Compras (Notas Fiscais)",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        ResumoGastosCard(
            totalMes = state.totalGastoMesCentavos,
            totalGeral = state.totalGastoGeralCentavos,
            quantidadeNotas = state.compras.size
        )
        Spacer(Modifier.height(12.dp))

        if (state.compras.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma nota registrada.\nToque em \"Nova nota\" para lançar uma compra.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(state.compras, key = { it.compra.id }) { compra ->
                    CompraCard(compra = compra, onExcluir = { onExcluir(compra) })
                }
            }
        }
    }
}

@Composable
private fun ResumoGastosCard(totalMes: Long, totalGeral: Long, quantidadeNotas: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Gasto neste mês",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    Formatters.formatarMoeda(totalMes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Total ($quantidadeNotas ${if (quantidadeNotas == 1) "nota" else "notas"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    Formatters.formatarMoeda(totalGeral),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun CompraCard(compra: CompraComItens, onExcluir: () -> Unit) {
    var expandido by rememberSaveable(compra.compra.id) { mutableStateOf(false) }
    val totalUnidades = compra.itens.sumOf { it.quantidade }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expandido = !expandido },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = compra.compra.fornecedor ?: "Fornecedor não informado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val nota = compra.compra.numeroNota?.let { "NF $it • " } ?: ""
                    Text(
                        text = nota + Formatters.formatarDataHora(compra.compra.dataHoraCompra),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Formatters.formatarMoeda(compra.compra.valorTotalCentavos),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${compra.itens.size} itens • $totalUnidades unid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expandido) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expandido) "Recolher" else "Expandir",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            AnimatedVisibility(visible = expandido) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    compra.itens.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.nomeExibicao, fontWeight = FontWeight.Medium)
                                Text(
                                    "${item.quantidade} × ${Formatters.formatarMoeda(item.custoUnitarioCentavos)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                Formatters.formatarMoeda(item.valorTotalItemCentavos),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onExcluir,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ErroVermelho)
                        Spacer(Modifier.width(4.dp))
                        Text("Excluir nota", color = ErroVermelho)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formulário de nova nota
// ---------------------------------------------------------------------------

@Composable
private fun FormularioNotaContent(form: FormularioNota, viewModel: CompraNotaViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::cancelarNota) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                "Nova nota fiscal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = form.fornecedor,
                onValueChange = viewModel::onFornecedorAlterado,
                label = { Text("Fornecedor / Loja") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.numeroNota,
                    onValueChange = viewModel::onNumeroNotaAlterado,
                    label = { Text("Nº da nota") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = form.dataTexto,
                    onValueChange = viewModel::onDataAlterada,
                    label = { Text("Data (dd/mm/aaaa)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Itens da nota",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = viewModel::abrirNovoItem) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Adicionar item")
                }
            }

            if (form.itens.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::abrirNovoItem),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        "Nenhum item ainda. Digite cada produto da nota com a quantidade e o valor pago.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                form.itens.forEach { item ->
                    ItemNotaCard(
                        item = item,
                        onEditar = { viewModel.editarItem(item.chave) },
                        onRemover = { viewModel.removerItem(item.chave) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        RodapeTotalNota(form = form, onSalvar = viewModel::salvarNota)
    }
}

@Composable
private fun ItemNotaCard(item: ItemNotaFormulario, onEditar: () -> Unit, onRemover: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditar),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.nomeExibicao,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.produtoId == null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = PrimariaVerde) {
                            Text(
                                "NOVO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    "${item.quantidade} × ${Formatters.formatarMoeda(item.custoUnitarioCentavos)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                Formatters.formatarMoeda(item.valorTotalCentavos),
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onEditar) {
                Icon(Icons.Default.Edit, contentDescription = "Editar item", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRemover) {
                Icon(Icons.Default.Delete, contentDescription = "Remover item", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RodapeTotalNota(form: FormularioNota, onSalvar: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (form.erro != null) {
                Text(form.erro, color = ErroVermelho, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${form.itens.size} itens • ${form.quantidadeTotal} unid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        Formatters.formatarMoeda(form.valorTotalCentavos),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(onClick = onSalvar, enabled = !form.salvando && form.itens.isNotEmpty()) {
                    if (form.salvando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Salvar nota")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Diálogo de item (busca de produto existente ou cadastro de novo)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemNotaDialog(
    itemExistente: ItemNotaFormulario?,
    produtos: List<Produto>,
    erro: String?,
    onSalvar: (Produto?, String, String, String, String, String) -> Unit,
    onCancelar: () -> Unit
) {
    var produtoSelecionado by remember {
        mutableStateOf(itemExistente?.produtoId?.let { id -> produtos.firstOrNull { it.id == id } })
    }
    var textoProduto by remember {
        mutableStateOf(produtoSelecionado?.nomeExibicao ?: itemExistente?.nome ?: "")
    }
    var variante by remember { mutableStateOf(if (produtoSelecionado == null) itemExistente?.variante ?: "" else "") }
    var quantidade by remember { mutableStateOf(itemExistente?.quantidade?.toString() ?: "") }
    var valorTotal by remember {
        mutableStateOf(itemExistente?.let { "%.2f".format(it.valorTotalCentavos / 100.0).replace('.', ',') } ?: "")
    }
    var precoVenda by remember {
        mutableStateOf(
            itemExistente?.precoVendaNovoProdutoCentavos?.takeIf { it > 0 }
                ?.let { "%.2f".format(it / 100.0).replace('.', ',') } ?: ""
        )
    }
    var menuAberto by remember { mutableStateOf(false) }

    val sugestoes = remember(textoProduto, produtos, produtoSelecionado) {
        if (produtoSelecionado != null || textoProduto.isBlank()) {
            emptyList()
        } else {
            val q = Formatters.normalizarTexto(textoProduto)
            produtos.filter { Formatters.normalizarTexto(it.nomeExibicao).contains(q) }.take(6)
        }
    }

    // Prévia do custo unitário enquanto digita
    val qtdInt = quantidade.trim().toIntOrNull()
    val totalCentavos = LivroCaixaParser.parseValorCentavos(valorTotal)
    val custoUnitario = if (qtdInt != null && qtdInt > 0 && totalCentavos != null) {
        (totalCentavos + qtdInt / 2) / qtdInt
    } else null

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (itemExistente == null) "Adicionar item" else "Editar item") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (erro != null) {
                    Text(erro, color = ErroVermelho, style = MaterialTheme.typography.bodySmall)
                }

                ExposedDropdownMenuBox(
                    expanded = menuAberto && sugestoes.isNotEmpty(),
                    onExpandedChange = { menuAberto = it }
                ) {
                    OutlinedTextField(
                        value = textoProduto,
                        onValueChange = {
                            textoProduto = it
                            produtoSelecionado = null
                            menuAberto = true
                        },
                        label = { Text("Produto *") },
                        placeholder = { Text("Busque ou digite um novo") },
                        singleLine = true,
                        trailingIcon = {
                            if (produtoSelecionado != null) {
                                Icon(Icons.Default.Check, contentDescription = "Produto vinculado", tint = PrimariaVerde)
                            } else if (sugestoes.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuAberto)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = menuAberto && sugestoes.isNotEmpty(),
                        onDismissRequest = { menuAberto = false }
                    ) {
                        sugestoes.forEach { produto ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(produto.nomeExibicao, fontWeight = FontWeight.Medium)
                                        Text(
                                            "Estoque: ${produto.estoqueAtual} • Custo: " +
                                                Formatters.formatarMoeda(produto.custoAquisicaoCentavos),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    produtoSelecionado = produto
                                    textoProduto = produto.nomeExibicao
                                    menuAberto = false
                                }
                            )
                        }
                    }
                }

                val selecionado = produtoSelecionado
                if (selecionado != null) {
                    Text(
                        "Estoque atual: ${selecionado.estoqueAtual} unid. • Custo atual: " +
                            Formatters.formatarMoeda(selecionado.custoAquisicaoCentavos),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (textoProduto.isNotBlank()) {
                    Text(
                        "Produto novo — será cadastrado ao salvar a nota.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimariaVerde
                    )
                    OutlinedTextField(
                        value = variante,
                        onValueChange = { variante = it },
                        label = { Text("Variante (ex: 90g, Lata)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = precoVenda,
                        onValueChange = { precoVenda = it },
                        label = { Text("Preço de venda (R$, opcional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantidade,
                        onValueChange = { novo -> quantidade = novo.filter { it.isDigit() } },
                        label = { Text("Quantidade *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = valorTotal,
                        onValueChange = { valorTotal = it },
                        label = { Text("Valor total (R$) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (custoUnitario != null) {
                    Text(
                        "Custo unitário: ${Formatters.formatarMoeda(custoUnitario)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSalvar(produtoSelecionado, textoProduto, variante, quantidade, valorTotal, precoVenda)
            }) { Text(if (itemExistente == null) "Adicionar" else "Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}
