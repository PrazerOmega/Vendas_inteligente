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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendasinteligentes.data.local.entity.TipoMovimentacao
import br.com.vendasinteligentes.ui.theme.ErroVermelho
import br.com.vendasinteligentes.ui.theme.PrimariaVerde
import br.com.vendasinteligentes.ui.viewmodel.FiltroHistorico
import br.com.vendasinteligentes.ui.viewmodel.HistoricoViewModel
import br.com.vendasinteligentes.util.Formatters

import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoScreen(viewModel: HistoricoViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Histórico de Movimentações",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Filtros
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.filtro == FiltroHistorico.TODOS,
                onClick = { viewModel.onFiltroSelecionado(FiltroHistorico.TODOS) },
                label = { Text("Todas") }
            )
            FilterChip(
                selected = state.filtro == FiltroHistorico.COMPRAS,
                onClick = { viewModel.onFiltroSelecionado(FiltroHistorico.COMPRAS) },
                label = { Text("Compras") }
            )
            FilterChip(
                selected = state.filtro == FiltroHistorico.PAGAMENTOS,
                onClick = { viewModel.onFiltroSelecionado(FiltroHistorico.PAGAMENTOS) },
                label = { Text("Pagamentos") }
            )
        }

        if (state.movimentacoes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma movimentação registrada no histórico.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.movimentacoes, key = { it.movimentacao.id }) { item ->
                    val isCompra = item.movimentacao.tipo == TipoMovimentacao.COMPRA
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
                                Text(
                                    text = item.cliente.nome,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isCompra) "- ${Formatters.formatarMoeda(item.movimentacao.valorCentavos)}"
                                           else "+ ${Formatters.formatarMoeda(item.movimentacao.valorCentavos)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCompra) ErroVermelho else PrimariaVerde
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.movimentacao.descricao,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Ocorrência: ${Formatters.formatarDataHora(item.movimentacao.dataHoraOcorrencia)} | Registro: ${Formatters.formatarDataCurta(item.movimentacao.dataHoraRegistro)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
