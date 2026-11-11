package br.com.vendasinteligentes.data.repository

import androidx.room.withTransaction
import br.com.vendasinteligentes.data.local.AppDatabase
import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.entity.ItemVenda
import br.com.vendasinteligentes.data.local.entity.MovimentacaoConta
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.data.local.entity.RascunhoLivroCaixa
import br.com.vendasinteligentes.data.local.entity.TipoMovimentacao
import br.com.vendasinteligentes.data.local.entity.Venda
import br.com.vendasinteligentes.data.local.model.ClienteComSaldo
import br.com.vendasinteligentes.data.local.model.MovimentacaoComCliente
import br.com.vendasinteligentes.data.local.model.VendaComItens
import br.com.vendasinteligentes.domain.parser.ItemInterpretado
import br.com.vendasinteligentes.data.local.entity.CompraNota
import br.com.vendasinteligentes.data.local.entity.ItemCompra
import br.com.vendasinteligentes.data.local.model.CompraComItens
import br.com.vendasinteligentes.util.Formatters
import kotlinx.coroutines.flow.Flow

/**
 * Item digitado a partir de uma nota fiscal de compra.
 * Se [produtoId] for nulo, o produto é localizado por nome/variante ou criado automaticamente.
 */
data class ItemCompraEntrada(
    val produtoId: Long?,
    val nome: String,
    val variante: String?,
    val quantidade: Int,
    val valorTotalCentavos: Long,
    /** Usado apenas quando um produto novo precisa ser criado. */
    val precoVendaNovoProdutoCentavos: Long = 0
) {
    /** Custo unitário arredondado ao centavo mais próximo. */
    val custoUnitarioCentavos: Long
        get() = if (quantidade > 0) (valorTotalCentavos + quantidade / 2) / quantidade else 0
}

class VendasRepository(private val database: AppDatabase) {

    private val produtoDao = database.produtoDao()
    private val clienteDao = database.clienteDao()
    private val movimentacaoDao = database.movimentacaoContaDao()
    private val vendaDao = database.vendaDao()
    private val rascunhoDao = database.rascunhoDao()
    private val compraDao = database.compraDao()

    // --- PRODUTOS ---

    val produtos: Flow<List<Produto>> = produtoDao.getAll()

    suspend fun getProdutosSync(): List<Produto> = produtoDao.getAllSync()

    suspend fun salvarProduto(produto: Produto) {
        require(produto.nome.isNotBlank()) { "O nome do produto é obrigatório." }
        require(produto.custoAquisicaoCentavos >= 0) { "O custo de aquisição não pode ser negativo." }
        require(produto.precoVendaCentavos >= 0) { "O preço de venda não pode ser negativo." }
        require(produto.estoqueAtual >= 0) { "A quantidade em estoque não pode ser negativa." }

        val formatado = produto.copy(
            nome = produto.nome.trim(),
            variante = produto.variante?.trim()?.ifBlank { null }
        )

        if (formatado.id == 0L) {
            produtoDao.insert(formatado)
        } else {
            produtoDao.update(formatado)
        }
    }

    suspend fun excluirProduto(produto: Produto) {
        produtoDao.delete(produto)
    }

    // --- CLIENTES ---

    val clientesComSaldo: Flow<List<ClienteComSaldo>> = clienteDao.getAllComSaldo()

    fun getClienteComSaldo(clienteId: Long): Flow<ClienteComSaldo?> =
        clienteDao.getClienteComSaldoById(clienteId)

    fun getMovimentacoesCliente(clienteId: Long): Flow<List<MovimentacaoConta>> =
        movimentacaoDao.getByClienteId(clienteId)

    suspend fun getClientesSync(): List<Cliente> = clienteDao.getAllSync()

    suspend fun getOrCreateCliente(nome: String): Cliente {
        val trimmed = nome.trim()
        require(trimmed.isNotBlank()) { "O nome do cliente é obrigatório." }
        val norm = Formatters.normalizarNomeCliente(trimmed)
        require(norm.length >= 2) { "O nome do cliente deve ter ao menos 2 caracteres válidos." }

        val existente = clienteDao.findByNomeNormalizado(norm)
        if (existente != null) {
            return existente
        }

        // Tenta inserir; se houver colisão de concorrência devido ao índice UNIQUE, recupera o cliente existente
        return try {
            val novoId = clienteDao.insert(
                Cliente(
                    nome = trimmed,
                    nomeNormalizado = norm
                )
            )
            clienteDao.getById(novoId) ?: error("Erro ao carregar cliente recém-criado '$trimmed'.")
        } catch (e: Exception) {
            clienteDao.findByNomeNormalizado(norm)
                ?: throw e
        }
    }

    suspend fun salvarCliente(cliente: Cliente): Long {
        require(cliente.nome.isNotBlank()) { "O nome do cliente é obrigatório." }
        val trimmed = cliente.nome.trim()
        val norm = Formatters.normalizarNomeCliente(trimmed)
        require(norm.length >= 2) { "O nome do cliente deve ter ao menos 2 caracteres válidos." }

        val formatado = cliente.copy(
            nome = trimmed,
            nomeNormalizado = norm
        )

        val existente = clienteDao.findByNomeNormalizado(norm)
        if (formatado.id == 0L && existente != null) {
            throw IllegalArgumentException("Já existe um cliente cadastrado equivalente a '${existente.nome}'.")
        }
        if (formatado.id != 0L && existente != null && existente.id != formatado.id) {
            throw IllegalArgumentException("Já existe outro cliente cadastrado com o nome '${existente.nome}'.")
        }

        return if (formatado.id == 0L) {
            clienteDao.insert(formatado)
        } else {
            clienteDao.update(formatado)
            formatado.id
        }
    }

    suspend fun excluirCliente(cliente: Cliente) {
        clienteDao.delete(cliente)
    }

    // --- RASCUNHO LIVRO-CAIXA ---

    val rascunho: Flow<RascunhoLivroCaixa?> = rascunhoDao.getRascunho()

    suspend fun salvarTextoRascunho(texto: String) {
        val rascunhoAtual = rascunhoDao.getRascunhoSync()
        val agora = System.currentTimeMillis()

        if (texto.isBlank()) {
            rascunhoDao.limparRascunho()
            return
        }

        val inicioRascunho = if (rascunhoAtual != null && rascunhoAtual.texto.isNotBlank()) {
            // Preserva a data/hora inicial em que o rascunho começou a ser digitado
            rascunhoAtual.dataHoraInicioRascunho
        } else {
            agora
        }

        rascunhoDao.salvarRascunho(
            RascunhoLivroCaixa(
                id = 1,
                texto = texto,
                dataHoraInicioRascunho = inicioRascunho,
                dataHoraUltimaModificacao = agora
            )
        )
    }

    suspend fun limparRascunho() {
        rascunhoDao.limparRascunho()
    }

    // --- CONFIRMAÇÃO TRANSACIONAL ---

    suspend fun confirmarOperacoes(itens: List<ItemInterpretado>): Result<Unit> {
        if (itens.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nenhuma operação para confirmar."))
        }

        return runCatching {
            database.withTransaction {
                val rascunhoAtual = rascunhoDao.getRascunhoSync()
                val dataHoraOcorrencia = rascunhoAtual?.dataHoraInicioRascunho ?: System.currentTimeMillis()
                val dataHoraRegistro = System.currentTimeMillis()

                for (item in itens) {
                    // Localiza cliente pelo identificador normalizado ou cadastra de forma segura
                    val cliente = getOrCreateCliente(item.nomeCliente)

                    when (item) {
                        is ItemInterpretado.Compra -> {
                            // Verifica estoque atualizado com o banco
                            val produtoAtual = produtoDao.getById(item.produto.id)
                                ?: error("Produto '${item.produto.nomeExibicao}' não foi encontrado no banco.")

                            if (produtoAtual.estoqueAtual < item.quantidade) {
                                error(
                                    "Estoque insuficiente para o produto '${produtoAtual.nomeExibicao}'. " +
                                    "Disponível: ${produtoAtual.estoqueAtual}, Solicitado: ${item.quantidade}."
                                )
                            }

                            // 1. Atualiza estoque
                            val novoEstoque = produtoAtual.estoqueAtual - item.quantidade
                            produtoDao.atualizarEstoque(produtoAtual.id, novoEstoque)

                            // 2. Registra Venda
                            val vendaId = vendaDao.insertVenda(
                                Venda(
                                    clienteId = cliente.id,
                                    dataHoraOcorrencia = dataHoraOcorrencia,
                                    dataHoraRegistro = dataHoraRegistro,
                                    valorTotalCentavos = item.valorTotalCentavos,
                                    observacao = item.textoOriginal
                                )
                            )

                            // 3. Registra ItemVenda com histórico imutável de preço e custo
                            vendaDao.insertItens(
                                listOf(
                                    ItemVenda(
                                        vendaId = vendaId,
                                        produtoId = produtoAtual.id,
                                        nomeProdutoSnapshot = produtoAtual.nome,
                                        varianteSnapshot = produtoAtual.variante,
                                        quantidade = item.quantidade,
                                        precoUnitarioCentavos = produtoAtual.precoVendaCentavos,
                                        custoUnitarioCentavos = produtoAtual.custoAquisicaoCentavos,
                                        valorTotalItemCentavos = item.valorTotalCentavos
                                    )
                                )
                            )

                            // 4. Registra Débito na conta do cliente
                            movimentacaoDao.insert(
                                MovimentacaoConta(
                                    clienteId = cliente.id,
                                    tipo = TipoMovimentacao.COMPRA,
                                    valorCentavos = item.valorTotalCentavos,
                                    dataHoraOcorrencia = dataHoraOcorrencia,
                                    dataHoraRegistro = dataHoraRegistro,
                                    descricao = "Compra: ${item.quantidade}x ${produtoAtual.nomeExibicao}",
                                    vendaId = vendaId
                                )
                            )
                        }

                        is ItemInterpretado.Pagamento -> {
                            // Registra Crédito na conta do cliente
                            movimentacaoDao.insert(
                                MovimentacaoConta(
                                    clienteId = cliente.id,
                                    tipo = TipoMovimentacao.PAGAMENTO,
                                    valorCentavos = item.valorCentavos,
                                    dataHoraOcorrencia = dataHoraOcorrencia,
                                    dataHoraRegistro = dataHoraRegistro,
                                    descricao = "Pagamento recebido"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // --- HISTÓRICO ---

    val todasMovimentacoesComCliente: Flow<List<MovimentacaoComCliente>> =
        movimentacaoDao.getAllWithCliente()

    val todasVendasComItens: Flow<List<VendaComItens>> =
        vendaDao.getAllVendasComItens()

    // --- COMPRAS (NOTAS FISCAIS DE ENTRADA) ---

    val comprasComItens: Flow<List<CompraComItens>> = compraDao.getAllComprasComItens()

    /**
     * Registra a nota fiscal de compra em uma única transação:
     * vincula/cria cada produto, soma a quantidade ao estoque e atualiza o custo de aquisição.
     */
    suspend fun registrarCompra(
        fornecedor: String?,
        numeroNota: String?,
        dataHoraCompra: Long,
        itens: List<ItemCompraEntrada>,
        observacao: String? = null
    ): Result<Long> {
        if (itens.isEmpty()) {
            return Result.failure(IllegalArgumentException("Adicione ao menos um item à nota."))
        }

        return runCatching {
            itens.forEach { item ->
                require(item.nome.isNotBlank()) { "Todos os itens precisam de um nome de produto." }
                require(item.quantidade > 0) { "A quantidade de '${item.nome}' deve ser maior que zero." }
                require(item.valorTotalCentavos >= 0) { "O valor de '${item.nome}' não pode ser negativo." }
            }

            database.withTransaction {
                val compraId = compraDao.insertCompra(
                    CompraNota(
                        fornecedor = fornecedor?.trim()?.ifBlank { null },
                        numeroNota = numeroNota?.trim()?.ifBlank { null },
                        dataHoraCompra = dataHoraCompra,
                        dataHoraRegistro = System.currentTimeMillis(),
                        valorTotalCentavos = itens.sumOf { it.valorTotalCentavos },
                        observacao = observacao?.trim()?.ifBlank { null }
                    )
                )

                val itensCompra = itens.map { item ->
                    val nome = item.nome.trim()
                    val variante = item.variante?.trim()?.ifBlank { null }

                    val produto = item.produtoId?.let { produtoDao.getById(it) }
                        ?: produtoDao.findByNomeEVariante(nome, variante)
                            ?.takeIf { (it.variante ?: "").equals(variante ?: "", ignoreCase = true) }
                        ?: run {
                            val novoId = produtoDao.insert(
                                Produto(
                                    nome = nome,
                                    variante = variante,
                                    custoAquisicaoCentavos = item.custoUnitarioCentavos,
                                    precoVendaCentavos = item.precoVendaNovoProdutoCentavos.coerceAtLeast(0),
                                    estoqueAtual = 0
                                )
                            )
                            produtoDao.getById(novoId) ?: error("Erro ao cadastrar o produto '$nome'.")
                        }

                    produtoDao.update(
                        produto.copy(
                            estoqueAtual = produto.estoqueAtual + item.quantidade,
                            custoAquisicaoCentavos = item.custoUnitarioCentavos
                        )
                    )

                    ItemCompra(
                        compraId = compraId,
                        produtoId = produto.id,
                        nomeProdutoSnapshot = produto.nome,
                        varianteSnapshot = produto.variante,
                        quantidade = item.quantidade,
                        custoUnitarioCentavos = item.custoUnitarioCentavos,
                        valorTotalItemCentavos = item.valorTotalCentavos
                    )
                }

                compraDao.insertItens(itensCompra)
                compraId
            }
        }
    }

    /**
     * Exclui a nota e estorna do estoque as quantidades lançadas (sem ficar negativo).
     * O custo de aquisição não é revertido, pois pode ter sido alterado depois.
     */
    suspend fun excluirCompra(compraId: Long): Result<Unit> = runCatching {
        database.withTransaction {
            val compra = compraDao.getCompraComItensById(compraId)
                ?: error("Nota não encontrada.")
            compra.itens.forEach { item ->
                val produtoId = item.produtoId ?: return@forEach
                val produto = produtoDao.getById(produtoId) ?: return@forEach
                produtoDao.atualizarEstoque(
                    produto.id,
                    (produto.estoqueAtual - item.quantidade).coerceAtLeast(0)
                )
            }
            compraDao.deleteCompra(compra.compra)
        }
    }
}
