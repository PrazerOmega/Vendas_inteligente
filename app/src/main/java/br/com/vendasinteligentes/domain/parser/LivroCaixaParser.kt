package br.com.vendasinteligentes.domain.parser

import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.entity.Produto
import br.com.vendasinteligentes.util.Formatters

sealed class ItemInterpretado {
    abstract val linhaNumero: Int
    abstract val textoOriginal: String
    abstract val nomeCliente: String
    abstract val clienteExistente: Cliente?
    abstract val sugestoesClientesSemelhantes: List<Cliente>

    data class Compra(
        override val linhaNumero: Int,
        override val textoOriginal: String,
        override val nomeCliente: String,
        val quantidade: Int,
        val produto: Produto,
        val precoUnitarioCentavos: Long,
        val valorTotalCentavos: Long,
        override val clienteExistente: Cliente? = null,
        override val sugestoesClientesSemelhantes: List<Cliente> = emptyList()
    ) : ItemInterpretado()

    data class Pagamento(
        override val linhaNumero: Int,
        override val textoOriginal: String,
        override val nomeCliente: String,
        val valorCentavos: Long,
        override val clienteExistente: Cliente? = null,
        override val sugestoesClientesSemelhantes: List<Cliente> = emptyList()
    ) : ItemInterpretado()
}

data class ErroInterpretacao(
    val linhaNumero: Int,
    val textoLinha: String,
    val mensagem: String
)

data class ResultadoInterpretacao(
    val itens: List<ItemInterpretado>,
    val erros: List<ErroInterpretacao>
) {
    val valido: Boolean get() = erros.isEmpty() && itens.isNotEmpty()
    val totalCentavosCompras: Long get() = itens.filterIsInstance<ItemInterpretado.Compra>().sumOf { it.valorTotalCentavos }
    val totalCentavosPagamentos: Long get() = itens.filterIsInstance<ItemInterpretado.Pagamento>().sumOf { it.valorCentavos }
}

object LivroCaixaParser {

    private val regexCompra = Regex("""^(.*?)\s+comprou\s+(.*)$""", RegexOption.IGNORE_CASE)
    private val regexPagamento = Regex("""^(.*?)\s+(?:me\s+)?pagou\s+(.*)$""", RegexOption.IGNORE_CASE)

    fun interpretar(
        texto: String,
        produtosCadastrados: List<Produto>,
        clientesCadastrados: List<Cliente> = emptyList()
    ): ResultadoInterpretacao {
        val linhas = texto.lines()
        val itens = mutableListOf<ItemInterpretado>()
        val erros = mutableListOf<ErroInterpretacao>()

        for ((index, linhaRaw) in linhas.withIndex()) {
            val linhaNumero = index + 1
            val linha = linhaRaw.trim()
            if (linha.isBlank()) {
                continue
            }

            val matchCompra = regexCompra.find(linha)
            val matchPagamento = regexPagamento.find(linha)

            if (matchCompra != null && matchPagamento != null) {
                erros.add(
                    ErroInterpretacao(
                        linhaNumero = linhaNumero,
                        textoLinha = linhaRaw,
                        mensagem = "Linha ambígua contendo ambas as palavras-chave 'comprou' e 'me pagou'."
                    )
                )
                continue
            }

            if (matchCompra != null) {
                val clienteRaw = matchCompra.groupValues[1].trim()
                val resto = matchCompra.groupValues[2].trim()

                val erroCliente = validarNomeCliente(clienteRaw)
                if (erroCliente != null) {
                    erros.add(ErroInterpretacao(linhaNumero, linhaRaw, erroCliente))
                    continue
                }

                val matchQtd = Regex("""^(\d+)\s+(.*)$""").find(resto)
                if (matchQtd == null) {
                    erros.add(
                        ErroInterpretacao(
                            linhaNumero,
                            linhaRaw,
                            "Quantidade inválida ou ausente antes do produto. Exemplo: 'fulano comprou 2 amendoins'."
                        )
                    )
                    continue
                }

                val qtdStr = matchQtd.groupValues[1]
                val produtoTexto = matchQtd.groupValues[2].trim()

                val quantidade = qtdStr.toIntOrNull()
                if (quantidade == null || quantidade <= 0) {
                    erros.add(
                        ErroInterpretacao(
                            linhaNumero,
                            linhaRaw,
                            "A quantidade deve ser um número inteiro maior que zero (recebido: '$qtdStr')."
                        )
                    )
                    continue
                }

                if (produtoTexto.isBlank()) {
                    erros.add(
                        ErroInterpretacao(
                            linhaNumero,
                            linhaRaw,
                            "Nome do produto não informado após a quantidade."
                        )
                    )
                    continue
                }

                val infoCliente = resolverCliente(clienteRaw, clientesCadastrados)
                val resultadoProduto = encontrarProduto(produtoTexto, produtosCadastrados)
                when (resultadoProduto) {
                    is MatchProdutoResult.Encontrado -> {
                        val prod = resultadoProduto.produto
                        val totalCentavos = prod.precoVendaCentavos * quantidade
                        itens.add(
                            ItemInterpretado.Compra(
                                linhaNumero = linhaNumero,
                                textoOriginal = linhaRaw,
                                nomeCliente = infoCliente.nomeFormatado,
                                quantidade = quantidade,
                                produto = prod,
                                precoUnitarioCentavos = prod.precoVendaCentavos,
                                valorTotalCentavos = totalCentavos,
                                clienteExistente = infoCliente.clienteExistente,
                                sugestoesClientesSemelhantes = infoCliente.clientesSemelhantes
                            )
                        )
                    }
                    is MatchProdutoResult.NaoEncontrado -> {
                        erros.add(
                            ErroInterpretacao(
                                linhaNumero,
                                linhaRaw,
                                "Produto não cadastrado ou não encontrado: '$produtoTexto'."
                            )
                        )
                    }
                    is MatchProdutoResult.Ambiguo -> {
                        val opcoes = resultadoProduto.candidatos.joinToString { it.nomeExibicao }
                        erros.add(
                            ErroInterpretacao(
                                linhaNumero,
                                linhaRaw,
                                "Produto ambíguo: '$produtoTexto' corresponde a múltiplos produtos cadastrados: $opcoes."
                            )
                        )
                    }
                }
            } else if (matchPagamento != null) {
                val clienteRaw = matchPagamento.groupValues[1].trim()
                val valorTexto = matchPagamento.groupValues[2].trim()

                val erroCliente = validarNomeCliente(clienteRaw)
                if (erroCliente != null) {
                    erros.add(ErroInterpretacao(linhaNumero, linhaRaw, erroCliente))
                    continue
                }

                val valorCentavos = parseValorCentavos(valorTexto)
                if (valorCentavos == null || valorCentavos <= 0) {
                    erros.add(
                        ErroInterpretacao(
                            linhaNumero,
                            linhaRaw,
                            "Valor monetário inválido ou não reconhecido: '$valorTexto'. Exemplo: '10 reais', '10,50' ou 'R$ 10,00'."
                        )
                    )
                    continue
                }

                val infoCliente = resolverCliente(clienteRaw, clientesCadastrados)
                itens.add(
                    ItemInterpretado.Pagamento(
                        linhaNumero = linhaNumero,
                        textoOriginal = linhaRaw,
                        nomeCliente = infoCliente.nomeFormatado,
                        valorCentavos = valorCentavos,
                        clienteExistente = infoCliente.clienteExistente,
                        sugestoesClientesSemelhantes = infoCliente.clientesSemelhantes
                    )
                )
            } else {
                erros.add(
                    ErroInterpretacao(
                        linhaNumero = linhaNumero,
                        textoLinha = linhaRaw,
                        mensagem = "Formato não reconhecido. Use '<Cliente> comprou <Qtd> <Produto>' ou '<Cliente> me pagou <Valor>'."
                    )
                )
            }
        }

        return ResultadoInterpretacao(itens = itens, erros = erros)
    }

    private fun validarNomeCliente(nome: String): String? {
        val trimmed = nome.trim()
        if (trimmed.isBlank()) {
            return "Nome do cliente não informado."
        }
        val normalizado = Formatters.normalizarNomeCliente(trimmed)
        if (normalizado.length < 2) {
            return "Nome do cliente muito curto: '$trimmed'."
        }
        val contemLetras = normalizado.any { it.isLetter() }
        if (!contemLetras) {
            return "Nome do cliente inválido (deve conter letras): '$trimmed'."
        }
        return null
    }

    data class InfoClienteResolvido(
        val nomeFormatado: String,
        val clienteExistente: Cliente?,
        val clientesSemelhantes: List<Cliente>
    )

    private fun resolverCliente(nomeRaw: String, clientesCadastrados: List<Cliente>): InfoClienteResolvido {
        val trimmed = nomeRaw.trim()
        val normalizado = Formatters.normalizarNomeCliente(trimmed)

        // 1. Equivalência exata pela normalização canônica
        val existente = clientesCadastrados.firstOrNull {
            it.nomeNormalizado == normalizado || Formatters.normalizarNomeCliente(it.nome) == normalizado
        }
        if (existente != null) {
            return InfoClienteResolvido(
                nomeFormatado = existente.nome,
                clienteExistente = existente,
                clientesSemelhantes = emptyList()
            )
        }

        // 2. Se for cliente novo, verificar se existem clientes com escrita parecida para sugerir/alertar
        val semelhantes = if (normalizado.length >= 4) {
            clientesCadastrados.filter { c ->
                val cNorm = c.nomeNormalizado.ifBlank { Formatters.normalizarNomeCliente(c.nome) }
                cNorm != normalizado && calcularDistanciaLevenshtein(normalizado, cNorm) <= 1
            }
        } else {
            emptyList()
        }

        // Formatação Title Case para novos cadastros
        val nomeFormatado = trimmed.split(Regex("""\s+""")).joinToString(" ") { palavra ->
            palavra.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        return InfoClienteResolvido(
            nomeFormatado = nomeFormatado,
            clienteExistente = null,
            clientesSemelhantes = semelhantes
        )
    }

    private fun calcularDistanciaLevenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        var v0 = IntArray(s2.length + 1) { it }
        var v1 = IntArray(s2.length + 1)

        for (i in s1.indices) {
            v1[0] = i + 1
            for (j in s2.indices) {
                val cost = if (s1[i] == s2[j]) 0 else 1
                v1[j + 1] = minOf(v1[j] + 1, v0[j + 1] + 1, v0[j] + cost)
            }
            val temp = v0
            v0 = v1
            v1 = temp
        }

        return v0[s2.length]
    }

    sealed class MatchProdutoResult {
        data class Encontrado(val produto: Produto) : MatchProdutoResult()
        data class Ambiguo(val candidatos: List<Produto>) : MatchProdutoResult()
        object NaoEncontrado : MatchProdutoResult()
    }

    private fun encontrarProduto(
        termoBusca: String,
        produtos: List<Produto>
    ): MatchProdutoResult {
        if (produtos.isEmpty()) {
            return MatchProdutoResult.NaoEncontrado
        }

        val buscaNorm = normalizarParaTokens(termoBusca)
        if (buscaNorm.isEmpty()) {
            return MatchProdutoResult.NaoEncontrado
        }

        // 1. Busca por correspondência exata de nome normalizado (apenas o nome do produto)
        val exatosPorNome = produtos.filter { p ->
            val tokensNome = normalizarParaTokens(p.nome)
            tokensNome.size == buscaNorm.size &&
            tokensNome.indices.all { i -> tokensSaoEquivalentes(tokensNome[i], buscaNorm[i]) }
        }
        if (exatosPorNome.size == 1) {
            return MatchProdutoResult.Encontrado(exatosPorNome.first())
        } else if (exatosPorNome.size > 1) {
            // Mais de uma variante/sabor para o mesmo nome: seleciona o primeiro com estoque ou o primeiro
            val escolhido = exatosPorNome.firstOrNull { it.estoqueAtual > 0 } ?: exatosPorNome.first()
            return MatchProdutoResult.Encontrado(escolhido)
        }

        // 2. Busca por correspondência exata de nome completo (nome + variante/sabor) caso tenha sido digitado
        val exatosCompleto = produtos.filter { p ->
            val nomeCompleto = if (p.variante.isNullOrBlank()) p.nome else "${p.nome} ${p.variante}"
            val tokensCompleto = normalizarParaTokens(nomeCompleto)
            tokensCompleto.size == buscaNorm.size &&
            tokensCompleto.indices.all { i -> tokensSaoEquivalentes(tokensCompleto[i], buscaNorm[i]) }
        }
        if (exatosCompleto.size == 1) {
            return MatchProdutoResult.Encontrado(exatosCompleto.first())
        }

        // 3. Pontuação por tokens
        val candidatosPontuados = produtos.mapNotNull { prod ->
            val tokensNome = normalizarParaTokens(prod.nome)
            val tokensVariante = prod.variante?.let { normalizarParaTokens(it) } ?: emptyList()

            // O nome do produto deve ser contemplado na busca
            val contemTodosTokensNome = tokensNome.isNotEmpty() && tokensNome.all { tokenNome ->
                buscaNorm.any { tokenBusca -> tokensSaoEquivalentes(tokenNome, tokenBusca) }
            }

            if (!contemTodosTokensNome) {
                null
            } else {
                val tokensNomeCoincidentes = tokensNome.count { tNome ->
                    buscaNorm.any { tBusca -> tokensSaoEquivalentes(tNome, tBusca) }
                }
                val tokensVarianteCoincidentes = tokensVariante.count { tVar ->
                    buscaNorm.any { tBusca -> tokensSaoEquivalentes(tVar, tBusca) }
                }

                // Considera tamanho relevante (tokens do nome + tokens de variante se o usuário digitou algum)
                val tokensRelevantes = tokensNome.size + if (tokensVarianteCoincidentes > 0) tokensVariante.size else 0
                val diferencaTamanho = Math.abs(buscaNorm.size - tokensRelevantes)

                val score = (tokensNomeCoincidentes * 10) + (tokensVarianteCoincidentes * 5) - diferencaTamanho
                Pair(prod, score)
            }
        }

        if (candidatosPontuados.isEmpty()) {
            return MatchProdutoResult.NaoEncontrado
        }

        val maxScore = candidatosPontuados.maxOf { it.second }
        val melhores = candidatosPontuados.filter { it.second == maxScore }.map { it.first }

        return when {
            melhores.size == 1 -> MatchProdutoResult.Encontrado(melhores.first())
            else -> {
                // Se múltiplos produtos empataram com o mesmo nome (ex: sabores/variantes diferentes),
                // seleciona o primeiro com estoque ou o primeiro da lista
                val todosMesmoNome = melhores.map { normalizarParaTokens(it.nome) }.distinct().size == 1
                if (todosMesmoNome) {
                    val escolhido = melhores.firstOrNull { it.estoqueAtual > 0 } ?: melhores.first()
                    MatchProdutoResult.Encontrado(escolhido)
                } else {
                    MatchProdutoResult.Ambiguo(melhores)
                }
            }
        }
    }

    private fun tokensSaoEquivalentes(tokenA: String, tokenB: String): Boolean {
        if (tokenA == tokenB) return true
        val stemA = stemPlural(tokenA)
        val stemB = stemPlural(tokenB)
        return stemA == stemB || stemA == tokenB || tokenA == stemB
    }

    private fun stemPlural(token: String): String {
        return when {
            token.endsWith("ns") && token.length > 3 -> token.dropLast(2) + "m"
            token.endsWith("es") && token.length > 3 -> token.dropLast(2)
            token.endsWith("s") && token.length > 3 -> token.dropLast(1)
            else -> token
        }
    }

    private fun normalizarParaTokens(texto: String): List<String> {
        val semAcento = Formatters.normalizarTexto(texto)
        // Substituir números com unidades grudadas como "90g" em "90 g"
        val comEspacoUnidades = semAcento.replace(Regex("""(\d+)([a-zA-Z]+)"""), "$1 $2")
        val apenasLetrasENumeros = comEspacoUnidades.replace(Regex("""[^a-z0-9]+"""), " ")
        val stopWords = setOf("de", "do", "da", "dos", "das", "e", "em", "um", "uma")
        return apenasLetrasENumeros.split(Regex("""\s+"""))
            .filter { it.isNotBlank() && it !in stopWords }
    }

    fun parseValorCentavos(texto: String): Long? {
        val limpo = texto.trim()
            .lowercase()
            .replace("r$", "")
            .trim()

        if (limpo.isBlank()) return null

        // Caso 1: "15 reais e 50 centavos" ou "1 real e 50 centavos"
        val matchReaisECentavos = Regex("""^(\d+)\s*rea(?:is|l)\s*e\s*(\d+)\s*centavos?$""").find(limpo)
        if (matchReaisECentavos != null) {
            val reais = matchReaisECentavos.groupValues[1].toLongOrNull() ?: return null
            val centavos = matchReaisECentavos.groupValues[2].toLongOrNull() ?: return null
            return reais * 100 + centavos
        }

        // Caso 2: "50 centavos"
        val matchApenasCentavos = Regex("""^(\d+)\s*centavos?$""").find(limpo)
        if (matchApenasCentavos != null) {
            return matchApenasCentavos.groupValues[1].toLongOrNull()
        }

        // Caso 3: número seguido de "reais" ou "real", ex: "10 reais", "10,50 reais"
        val matchComReais = Regex("""^(.*?)\s*rea(?:is|l)$""").find(limpo)
        val numStr = if (matchComReais != null) {
            matchComReais.groupValues[1].trim()
        } else {
            limpo
        }

        // Caso 4: Formato monetário com vírgula ou ponto (ex: "10,50", "10.50", "10,5", "10.5")
        val matchDecimal = Regex("""^(\d+)[,.](\d{1,2})$""").find(numStr)
        if (matchDecimal != null) {
            val inteiros = matchDecimal.groupValues[1].toLongOrNull() ?: return null
            val fracStr = matchDecimal.groupValues[2]
            val fracao = if (fracStr.length == 1) {
                (fracStr.toLongOrNull() ?: return null) * 10
            } else {
                fracStr.toLongOrNull() ?: return null
            }
            return inteiros * 100 + fracao
        }

        // Caso 5: Número inteiro simples (ex: "10" -> R$ 10,00)
        val matchInteiro = Regex("""^(\d+)$""").find(numStr)
        if (matchInteiro != null) {
            val inteiros = matchInteiro.groupValues[1].toLongOrNull() ?: return null
            return inteiros * 100
        }

        return null
    }
}
