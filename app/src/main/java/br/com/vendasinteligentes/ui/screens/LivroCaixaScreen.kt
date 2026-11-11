package br.com.vendasinteligentes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendasinteligentes.domain.parser.ItemInterpretado
import br.com.vendasinteligentes.ui.theme.ErroVermelho
import br.com.vendasinteligentes.ui.theme.PrimariaVerde
import br.com.vendasinteligentes.ui.viewmodel.LivroCaixaViewModel
import br.com.vendasinteligentes.util.Formatters

@Composable
fun LivroCaixaScreen(viewModel: LivroCaixaViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cabeçalho da tela
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Livro-caixa",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state.dataHoraInicioRascunho != null && state.textoRascunho.isNotBlank()) {
                    Text(
                        text = "Rascunho iniciado: ${Formatters.formatarDataHora(state.dataHoraInicioRascunho!!)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.textoRascunho.isNotBlank()) {
                OutlinedButton(
                    onClick = { viewModel.onTextoAlterado("") },
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text("Limpar")
                }
            }
        }

        // Mensagens de Sucesso ou Erro
        state.mensagemSucesso?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = PrimariaVerde.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = PrimariaVerde)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = PrimariaVerde, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { viewModel.limparMensagens() }) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
            }
        }

        state.mensagemErro?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ErroVermelho.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErroVermelho)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = ErroVermelho, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { viewModel.limparMensagens() }) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
            }
        }

        // Campo de entrada de texto (Rascunho)
        OutlinedTextField(
            value = state.textoRascunho,
            onValueChange = { viewModel.onTextoAlterado(it) },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text("Digite as movimentações (uma por linha)") },
            placeholder = {
                Text(
                    "Exemplos:\n" +
                    "fulano comprou 2 amendoins\n" +
                    "fulano me pagou 10 reais"
                )
            },
            shape = RoundedCornerShape(12.dp)
        )

        // Seção de Prévia ou Erros de Validação
        val resultado = state.resultado
        if (resultado != null) {
            if (resultado.erros.isNotEmpty()) {
                // Exibe lista de erros por linha
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ErroVermelho.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ErroVermelho)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Atenção: ${resultado.erros.size} problema(s) encontrado(s)",
                                color = ErroVermelho,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Corrija as linhas abaixo para poder confirmar a gravação:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        resultado.erros.forEach { erro ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        color = ErroVermelho.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Linha ${erro.linhaNumero}: \"${erro.textoLinha}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ErroVermelho
                                )
                                Text(
                                    text = erro.mensagem,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (resultado.itens.isNotEmpty()) {
                // Exibe itens interpretados com sucesso
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Prévia das Operações (${resultado.itens.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Compras: ${Formatters.formatarMoeda(resultado.totalCentavosCompras)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Total Pagamentos: ${Formatters.formatarMoeda(resultado.totalCentavosPagamentos)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimariaVerde
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        resultado.itens.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = "Cliente: ${item.nomeCliente}",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            if (item.clienteExistente != null) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = PrimariaVerde.copy(alpha = 0.12f)),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "CADASTRADO",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = PrimariaVerde,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "NOVO",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        when (item) {
                                            is ItemInterpretado.Compra -> {
                                                Text(
                                                    text = Formatters.formatarMoeda(item.valorTotalCentavos),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            is ItemInterpretado.Pagamento -> {
                                                Text(
                                                    text = "+ ${Formatters.formatarMoeda(item.valorCentavos)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimariaVerde
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(2.dp))

                                    when (item) {
                                        is ItemInterpretado.Compra -> {
                                            Text(
                                                text = "Compra: ${item.quantidade}x ${item.produto.nomeExibicao} " +
                                                        "(${Formatters.formatarMoeda(item.precoUnitarioCentavos)} cada)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        is ItemInterpretado.Pagamento -> {
                                            Text(
                                                text = "Pagamento recebido",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Alerta conservador quando houver clientes já cadastrados com escrita muito parecida
                                    if (item.sugestoesClientesSemelhantes.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        val nomesSemelhantes = item.sugestoesClientesSemelhantes.joinToString { it.nome }
                                        Text(
                                            text = "💡 Você quis dizer $nomesSemelhantes? (Se não for a mesma pessoa, um novo cadastro será criado).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Botão de Confirmação (apenas habilitado se não houver erros e houver itens)
            Button(
                onClick = { viewModel.confirmarLote() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = resultado.valido && !state.emExecucao,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.emExecucao) {
                    Text(
                        text = "Gravando...",
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = if (resultado.valido) "Confirmar e Gravar Movimentações" else "Corrija os erros para confirmar",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
