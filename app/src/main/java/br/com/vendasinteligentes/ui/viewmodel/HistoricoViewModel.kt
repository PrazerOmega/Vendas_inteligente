package br.com.vendasinteligentes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.vendasinteligentes.data.local.entity.TipoMovimentacao
import br.com.vendasinteligentes.data.local.model.MovimentacaoComCliente
import br.com.vendasinteligentes.data.repository.VendasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class FiltroHistorico {
    TODOS,
    COMPRAS,
    PAGAMENTOS
}

data class HistoricoUiState(
    val movimentacoes: List<MovimentacaoComCliente> = emptyList(),
    val filtro: FiltroHistorico = FiltroHistorico.TODOS
)

class HistoricoViewModel(private val repository: VendasRepository) : ViewModel() {

    private val _filtro = MutableStateFlow(FiltroHistorico.TODOS)

    val uiState: StateFlow<HistoricoUiState> = combine(
        repository.todasMovimentacoesComCliente,
        _filtro
    ) { lista, filtroAtual ->
        val filtrados = when (filtroAtual) {
            FiltroHistorico.TODOS -> lista
            FiltroHistorico.COMPRAS -> lista.filter { it.movimentacao.tipo == TipoMovimentacao.COMPRA }
            FiltroHistorico.PAGAMENTOS -> lista.filter { it.movimentacao.tipo == TipoMovimentacao.PAGAMENTO }
        }
        HistoricoUiState(movimentacoes = filtrados, filtro = filtroAtual)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoricoUiState()
    )

    fun onFiltroSelecionado(novoFiltro: FiltroHistorico) {
        _filtro.value = novoFiltro
    }

    class Factory(private val repository: VendasRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoricoViewModel(repository) as T
        }
    }
}
