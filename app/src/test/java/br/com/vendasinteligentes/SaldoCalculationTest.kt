package br.com.vendasinteligentes

import br.com.vendasinteligentes.data.local.entity.Cliente
import br.com.vendasinteligentes.data.local.entity.MovimentacaoConta
import br.com.vendasinteligentes.data.local.entity.TipoMovimentacao
import br.com.vendasinteligentes.data.local.model.ClienteComSaldo
import br.com.vendasinteligentes.data.local.model.StatusSaldo
import org.junit.Assert.assertEquals
import org.junit.Test

class SaldoCalculationTest {

    @Test
    fun calcularSaldo_cenarioPadrao_conformeEspecificacao() {
        val cliente = Cliente(id = 1L, nome = "Wellington")

        // 1. Saldo inicial: R$ 0,00
        var movimentacoes = listOf<MovimentacaoConta>()
        var saldo = calcularSaldo(movimentacoes)
        assertEquals(0L, saldo)
        assertEquals(StatusSaldo.QUITADO, ClienteComSaldo(cliente, saldo).statusSaldo)

        // 2. Compra de R$ 5,00 (500 centavos): saldo devedor de R$ 5,00 (-500 centavos)
        movimentacoes = movimentacoes + MovimentacaoConta(
            id = 1L,
            clienteId = cliente.id,
            tipo = TipoMovimentacao.COMPRA,
            valorCentavos = 500L,
            dataHoraOcorrencia = 1000L,
            dataHoraRegistro = 1000L,
            descricao = "Compra"
        )
        saldo = calcularSaldo(movimentacoes)
        assertEquals(-500L, saldo)
        assertEquals(StatusSaldo.DEVEDOR, ClienteComSaldo(cliente, saldo).statusSaldo)

        // 3. Pagamento de R$ 10,00 (1000 centavos): saldo credor de R$ 5,00 (+500 centavos)
        movimentacoes = movimentacoes + MovimentacaoConta(
            id = 2L,
            clienteId = cliente.id,
            tipo = TipoMovimentacao.PAGAMENTO,
            valorCentavos = 1000L,
            dataHoraOcorrencia = 2000L,
            dataHoraRegistro = 2000L,
            descricao = "Pagamento"
        )
        saldo = calcularSaldo(movimentacoes)
        assertEquals(500L, saldo)
        assertEquals(StatusSaldo.CREDOR, ClienteComSaldo(cliente, saldo).statusSaldo)

        // 4. Nova compra de R$ 5,00: consome crédito e quita a conta (0 centavos)
        movimentacoes = movimentacoes + MovimentacaoConta(
            id = 3L,
            clienteId = cliente.id,
            tipo = TipoMovimentacao.COMPRA,
            valorCentavos = 500L,
            dataHoraOcorrencia = 3000L,
            dataHoraRegistro = 3000L,
            descricao = "Compra"
        )
        saldo = calcularSaldo(movimentacoes)
        assertEquals(0L, saldo)
        assertEquals(StatusSaldo.QUITADO, ClienteComSaldo(cliente, saldo).statusSaldo)
    }

    private fun calcularSaldo(movimentacoes: List<MovimentacaoConta>): Long {
        return movimentacoes.sumOf { it.valorAssinadoCentavos }
    }
}
