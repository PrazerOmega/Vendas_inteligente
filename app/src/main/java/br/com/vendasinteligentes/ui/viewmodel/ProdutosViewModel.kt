package br.com.vendasinteligentes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.data.repository.VendasRepository
import br.com.vendasinteligentes.domain.parser.LivroCaixaParser
import br.com.vendasinteligentes.util.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProdutosUiState(
    val produtos: List<Produto> = emptyList(),
    val termoBusca: String = "",
    val produtoEmEdicao: Produto? = null,
    val mostrarDialogo: Boolean = false,
    val erro: String? = null
)

class ProdutosViewModel(private val repository: VendasRepository) : ViewModel() {

    private val _termoBusca = MutableStateFlow("")
    private val _produtoEmEdicao = MutableStateFlow<Produto?>(null)
    private val _mostrarDialogo = MutableStateFlow(false)
    private val _erro = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProdutosUiState> = combine(
        repository.produtos,
        _termoBusca,
        _produtoEmEdicao,
        _mostrarDialogo,
        _erro
    ) { lista, busca, emEdicao, dialogo, erroMsg ->
        val filtrados = if (busca.isBlank()) {
            lista
        } else {
            val q = Formatters.normalizarTexto(busca)
            lista.filter {
                Formatters.normalizarTexto(it.nome).contains(q) ||
                (it.variante != null && Formatters.normalizarTexto(it.variante).contains(q))
            }
        }
        ProdutosUiState(
            produtos = filtrados,
            termoBusca = busca,
            produtoEmEdicao = emEdicao,
            mostrarDialogo = dialogo,
            erro = erroMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProdutosUiState()
    )

    fun onBuscaAlterada(novaBusca: String) {
        _termoBusca.value = novaBusca
    }

    fun abrirDialogoNovoProduto() {
        _produtoEmEdicao.value = null
        _erro.value = null
        _mostrarDialogo.value = true
    }

    fun abrirDialogoEdicao(produto: Produto) {
        _produtoEmEdicao.value = produto
        _erro.value = null
        _mostrarDialogo.value = true
    }

    fun fecharDialogo() {
        _mostrarDialogo.value = false
        _produtoEmEdicao.value = null
        _erro.value = null
    }

    fun salvarProduto(
        nome: String,
        variante: String?,
        custoTexto: String,
        precoTexto: String,
        estoqueTexto: String
    ) {
        if (nome.isBlank()) {
            _erro.value = "O nome do produto é obrigatório."
            return
        }

        val custoCentavos = LivroCaixaParser.parseValorCentavos(custoTexto)
        if (custoCentavos == null || custoCentavos < 0) {
            _erro.value = "Custo de aquisição inválido."
            return
        }

        val precoCentavos = LivroCaixaParser.parseValorCentavos(precoTexto)
        if (precoCentavos == null || precoCentavos < 0) {
            _erro.value = "Preço de venda inválido."
            return
        }

        val estoque = estoqueTexto.trim().toIntOrNull()
        if (estoque == null || estoque < 0) {
            _erro.value = "Estoque inválido (deve ser um número inteiro >= 0)."
            return
        }

        viewModelScope.launch {
            try {
                val atual = _produtoEmEdicao.value
                val produtoParaSalvar = (atual ?: Produto(
                    nome = nome,
                    variante = variante,
                    custoAquisicaoCentavos = custoCentavos,
                    precoVendaCentavos = precoCentavos,
                    estoqueAtual = estoque
                )).copy(
                    nome = nome,
                    variante = variante,
                    custoAquisicaoCentavos = custoCentavos,
                    precoVendaCentavos = precoCentavos,
                    estoqueAtual = estoque
                )

                repository.salvarProduto(produtoParaSalvar)
                fecharDialogo()
            } catch (e: Exception) {
                _erro.value = e.message ?: "Erro ao salvar produto."
            }
        }
    }

    fun excluirProduto(produto: Produto) {
        viewModelScope.launch {
            repository.excluirProduto(produto)
        }
    }

    class Factory(private val repository: VendasRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProdutosViewModel(repository) as T
        }
    }
}
