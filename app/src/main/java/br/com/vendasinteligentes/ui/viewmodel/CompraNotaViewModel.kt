package br.com.vendasinteligentes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.data.local.model.CompraComItens
import br.com.vendasinteligentes.data.repository.ItemCompraEntrada
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Item da nota ainda em digitação (antes de salvar). */
data class ItemNotaFormulario(
    val chave: Long,
    val produtoId: Long?,
    val nome: String,
    val variante: String?,
    val quantidade: Int,
    val valorTotalCentavos: Long,
    val precoVendaNovoProdutoCentavos: Long = 0
) {
    val nomeExibicao: String
        get() = if (variante.isNullOrBlank()) nome else "$nome ($variante)"

    val custoUnitarioCentavos: Long
        get() = if (quantidade > 0) (valorTotalCentavos + quantidade / 2) / quantidade else 0
}

data class FormularioNota(
    val fornecedor: String = "",
    val numeroNota: String = "",
    val dataTexto: String = "",
    val itens: List<ItemNotaFormulario> = emptyList(),
    val erro: String? = null,
    val salvando: Boolean = false,
    /** null = diálogo fechado; -1 = novo item; >= 0 = chave do item em edição. */
    val itemDialogoChave: Long? = null,
    val erroItem: String? = null
) {
    val valorTotalCentavos: Long get() = itens.sumOf { it.valorTotalCentavos }
    val quantidadeTotal: Int get() = itens.sumOf { it.quantidade }
}

data class CompraNotaUiState(
    val compras: List<CompraComItens> = emptyList(),
    val produtos: List<Produto> = emptyList(),
    val totalGastoMesCentavos: Long = 0,
    val totalGastoGeralCentavos: Long = 0,
    val formulario: FormularioNota? = null,
    val compraParaExcluir: CompraComItens? = null,
    val mensagem: String? = null
)

class CompraNotaViewModel(private val repository: VendasRepository) : ViewModel() {

    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }

    private val _formulario = MutableStateFlow<FormularioNota?>(null)
    private val _compraParaExcluir = MutableStateFlow<CompraComItens?>(null)
    private val _mensagem = MutableStateFlow<String?>(null)
    private var proximaChave = 0L

    val uiState: StateFlow<CompraNotaUiState> = combine(
        repository.comprasComItens,
        repository.produtos,
        _formulario,
        _compraParaExcluir,
        _mensagem
    ) { compras, produtos, form, excluir, msg ->
        val inicioMes = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        CompraNotaUiState(
            compras = compras,
            produtos = produtos,
            totalGastoMesCentavos = compras
                .filter { it.compra.dataHoraCompra >= inicioMes }
                .sumOf { it.compra.valorTotalCentavos },
            totalGastoGeralCentavos = compras.sumOf { it.compra.valorTotalCentavos },
            formulario = form,
            compraParaExcluir = excluir,
            mensagem = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CompraNotaUiState()
    )

    // --- Formulário da nota ---

    fun novaNota() {
        _formulario.value = FormularioNota(dataTexto = formatoData.format(Date()))
    }

    fun cancelarNota() {
        _formulario.value = null
    }

    fun onFornecedorAlterado(valor: String) = _formulario.update { it?.copy(fornecedor = valor, erro = null) }

    fun onNumeroNotaAlterado(valor: String) = _formulario.update { it?.copy(numeroNota = valor, erro = null) }

    fun onDataAlterada(valor: String) = _formulario.update { it?.copy(dataTexto = valor, erro = null) }

    fun removerItem(chave: Long) = _formulario.update { form ->
        form?.copy(itens = form.itens.filterNot { it.chave == chave })
    }

    // --- Diálogo de item ---

    fun abrirNovoItem() = _formulario.update { it?.copy(itemDialogoChave = -1L, erroItem = null) }

    fun editarItem(chave: Long) = _formulario.update { it?.copy(itemDialogoChave = chave, erroItem = null) }

    fun fecharDialogoItem() = _formulario.update { it?.copy(itemDialogoChave = null, erroItem = null) }

    fun salvarItem(
        produtoInformado: Produto?,
        nome: String,
        variante: String,
        quantidadeTexto: String,
        valorTotalTexto: String,
        precoVendaTexto: String
    ) {
        val form = _formulario.value ?: return

        // Vincula automaticamente se o nome/variante digitado coincidir com um produto já cadastrado.
        val produtoSelecionado = produtoInformado ?: uiState.value.produtos.firstOrNull {
            Formatters.normalizarTexto(it.nome) == Formatters.normalizarTexto(nome) &&
                Formatters.normalizarTexto(it.variante ?: "") == Formatters.normalizarTexto(variante)
        }

        val nomeFinal = produtoSelecionado?.nome ?: nome.trim()
        if (nomeFinal.isBlank()) {
            _formulario.value = form.copy(erroItem = "Informe o produto.")
            return
        }

        val quantidade = quantidadeTexto.trim().toIntOrNull()
        if (quantidade == null || quantidade <= 0) {
            _formulario.value = form.copy(erroItem = "Quantidade inválida (número inteiro maior que zero).")
            return
        }

        val valorTotal = LivroCaixaParser.parseValorCentavos(valorTotalTexto)
        if (valorTotal == null || valorTotal < 0) {
            _formulario.value = form.copy(erroItem = "Valor total do item inválido.")
            return
        }

        val precoVenda = if (produtoSelecionado == null && precoVendaTexto.isNotBlank()) {
            LivroCaixaParser.parseValorCentavos(precoVendaTexto)?.takeIf { it >= 0 } ?: run {
                _formulario.value = form.copy(erroItem = "Preço de venda inválido.")
                return
            }
        } else 0L

        val chaveEdicao = form.itemDialogoChave
        val item = ItemNotaFormulario(
            chave = if (chaveEdicao != null && chaveEdicao >= 0) chaveEdicao else proximaChave++,
            produtoId = produtoSelecionado?.id,
            nome = nomeFinal,
            variante = produtoSelecionado?.variante ?: variante.trim().ifBlank { null },
            quantidade = quantidade,
            valorTotalCentavos = valorTotal,
            precoVendaNovoProdutoCentavos = precoVenda
        )

        val novosItens = if (chaveEdicao != null && chaveEdicao >= 0) {
            form.itens.map { if (it.chave == chaveEdicao) item else it }
        } else {
            form.itens + item
        }

        _formulario.value = form.copy(itens = novosItens, itemDialogoChave = null, erroItem = null, erro = null)
    }

    // --- Persistência ---

    fun salvarNota() {
        val form = _formulario.value ?: return
        if (form.salvando) return

        if (form.itens.isEmpty()) {
            _formulario.value = form.copy(erro = "Adicione ao menos um item da nota.")
            return
        }

        val data = parseData(form.dataTexto)
        if (data == null) {
            _formulario.value = form.copy(erro = "Data inválida. Use o formato dd/mm/aaaa.")
            return
        }

        _formulario.value = form.copy(salvando = true, erro = null)

        viewModelScope.launch {
            val resultado = repository.registrarCompra(
                fornecedor = form.fornecedor,
                numeroNota = form.numeroNota,
                dataHoraCompra = data,
                itens = form.itens.map {
                    ItemCompraEntrada(
                        produtoId = it.produtoId,
                        nome = it.nome,
                        variante = it.variante,
                        quantidade = it.quantidade,
                        valorTotalCentavos = it.valorTotalCentavos,
                        precoVendaNovoProdutoCentavos = it.precoVendaNovoProdutoCentavos
                    )
                }
            )

            resultado
                .onSuccess {
                    _formulario.value = null
                    _mensagem.value = "Nota registrada! Estoque e custos atualizados."
                }
                .onFailure { e ->
                    _formulario.update {
                        it?.copy(salvando = false, erro = e.message ?: "Erro ao salvar a nota.")
                    }
                }
        }
    }

    /** Converte "dd/MM/yyyy" para timestamp; se for hoje, usa o horário atual. */
    private fun parseData(texto: String): Long? {
        val data = runCatching { formatoData.parse(texto.trim()) }.getOrNull() ?: return null
        val hoje = formatoData.format(Date())
        return if (formatoData.format(data) == hoje) System.currentTimeMillis()
        else Calendar.getInstance().apply {
            time = data
            set(Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis
    }

    // --- Exclusão ---

    fun solicitarExclusao(compra: CompraComItens) {
        _compraParaExcluir.value = compra
    }

    fun cancelarExclusao() {
        _compraParaExcluir.value = null
    }

    fun confirmarExclusao() {
        val compra = _compraParaExcluir.value ?: return
        _compraParaExcluir.value = null
        viewModelScope.launch {
            repository.excluirCompra(compra.compra.id)
                .onSuccess { _mensagem.value = "Nota excluída e estoque estornado." }
                .onFailure { _mensagem.value = it.message ?: "Erro ao excluir a nota." }
        }
    }

    fun mensagemExibida() {
        _mensagem.value = null
    }

    class Factory(private val repository: VendasRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CompraNotaViewModel(repository) as T
        }
    }
}
