package br.com.vendasinteligentes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.vendasinteligentes.data.repository.VendasRepository
import br.com.vendasinteligentes.domain.parser.LivroCaixaParser
import br.com.vendasinteligentes.domain.parser.ResultadoInterpretacao
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LivroCaixaUiState(
    val textoRascunho: String = "",
    val dataHoraInicioRascunho: Long? = null,
    val resultado: ResultadoInterpretacao? = null,
    val emExecucao: Boolean = false,
    val mensagemSucesso: String? = null,
    val mensagemErro: String? = null
)

class LivroCaixaViewModel(private val repository: VendasRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LivroCaixaUiState())
    val uiState: StateFlow<LivroCaixaUiState> = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null

    init {
        carregarRascunho()
    }

    private fun carregarRascunho() {
        viewModelScope.launch {
            val rascunho = repository.rascunho.firstOrNull()
            if (rascunho != null) {
                _uiState.update {
                    it.copy(
                        textoRascunho = rascunho.texto,
                        dataHoraInicioRascunho = rascunho.dataHoraInicioRascunho
                    )
                }
                if (rascunho.texto.isNotBlank()) {
                    interpretarTexto(rascunho.texto)
                }
            }
        }
    }

    fun onTextoAlterado(novoTexto: String) {
        _uiState.update {
            it.copy(
                textoRascunho = novoTexto,
                mensagemSucesso = null,
                mensagemErro = null
            )
        }

        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(400)
            repository.salvarTextoRascunho(novoTexto)
            interpretarTexto(novoTexto)
        }
    }

    fun interpretarTexto(texto: String = _uiState.value.textoRascunho) {
        if (texto.isBlank()) {
            _uiState.update { it.copy(resultado = null) }
            return
        }

        viewModelScope.launch {
            val produtos = repository.getProdutosSync()
            val clientes = repository.getClientesSync()
            val resultado = LivroCaixaParser.interpretar(texto, produtos, clientes)
            _uiState.update { it.copy(resultado = resultado) }
        }
    }

    fun confirmarLote() {
        val resultado = _uiState.value.resultado ?: return
        if (!resultado.valido) return

        viewModelScope.launch {
            _uiState.update { it.copy(emExecucao = true, mensagemErro = null, mensagemSucesso = null) }
            val res = repository.confirmarOperacoes(resultado.itens)
            res.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            emExecucao = false,
                            mensagemSucesso = "Operações confirmadas e registradas com sucesso!"
                        )
                    }
                },
                onFailure = { erro ->
                    _uiState.update {
                        it.copy(
                            emExecucao = false,
                            mensagemErro = erro.message ?: "Erro ao registrar operações."
                        )
                    }
                }
            )
        }
    }

    fun limparMensagens() {
        _uiState.update { it.copy(mensagemSucesso = null, mensagemErro = null) }
    }

    class Factory(private val repository: VendasRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LivroCaixaViewModel(repository) as T
        }
    }
}
