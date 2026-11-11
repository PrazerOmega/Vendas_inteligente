package br.com.vendasinteligentes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.entity.MovimentacaoConta
import br.com.vendasinteligentes.data.local.model.ClienteComSaldo
import br.com.vendasinteligentes.data.repository.VendasRepository
import br.com.vendasinteligentes.util.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClientesUiState(
    val clientesComSaldo: List<ClienteComSaldo> = emptyList(),
    val termoBusca: String = "",
    val clienteSelecionado: ClienteComSaldo? = null,
    val extratoSelecionado: List<MovimentacaoConta> = emptyList(),
    val mostrarDialogoNovoCliente: Boolean = false,
    val erro: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ClientesViewModel(private val repository: VendasRepository) : ViewModel() {

    private val _termoBusca = MutableStateFlow("")
    private val _clienteSelecionado = MutableStateFlow<ClienteComSaldo?>(null)
    private val _mostrarDialogo = MutableStateFlow(false)
    private val _erro = MutableStateFlow<String?>(null)

    private val _extrato = _clienteSelecionado.flatMapLatest { cliente ->
        if (cliente == null) flowOf(emptyList())
        else repository.getMovimentacoesCliente(cliente.cliente.id)
    }

    private val _dados = combine(
        repository.clientesComSaldo,
        _termoBusca,
        _clienteSelecionado,
        _extrato
    ) { lista, busca, selecionado, extratoLista ->
        val filtrados = if (busca.isBlank()) {
            lista
        } else {
            val q = Formatters.normalizarTexto(busca)
            lista.filter { Formatters.normalizarTexto(it.cliente.nome).contains(q) }
        }
        Triple(filtrados, selecionado, extratoLista)
    }

    val uiState: StateFlow<ClientesUiState> = combine(
        _dados,
        _mostrarDialogo,
        _erro,
        _termoBusca
    ) { dados, dialogo, erroMsg, busca ->
        ClientesUiState(
            clientesComSaldo = dados.first,
            termoBusca = busca,
            clienteSelecionado = dados.second,
            extratoSelecionado = dados.third,
            mostrarDialogoNovoCliente = dialogo,
            erro = erroMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ClientesUiState()
    )

    fun onBuscaAlterada(novaBusca: String) {
        _termoBusca.value = novaBusca
    }

    fun selecionarCliente(cliente: ClienteComSaldo?) {
        _clienteSelecionado.value = cliente
    }

    fun abrirDialogoNovoCliente() {
        _erro.value = null
        _mostrarDialogo.value = true
    }

    fun fecharDialogo() {
        _mostrarDialogo.value = false
        _erro.value = null
    }

    fun salvarCliente(nome: String) {
        if (nome.isBlank() || nome.trim().length < 2) {
            _erro.value = "O nome do cliente deve ter ao menos 2 caracteres."
            return
        }

        viewModelScope.launch {
            try {
                repository.salvarCliente(
                    Cliente(
                        nome = nome
                    )
                )
                fecharDialogo()
            } catch (e: Exception) {
                _erro.value = e.message ?: "Erro ao salvar cliente."
            }
        }
    }

    class Factory(private val repository: VendasRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ClientesViewModel(repository) as T
        }
    }
}
