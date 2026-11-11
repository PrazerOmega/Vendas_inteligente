package br.com.vendasinteligentes.util

import java.text.NumberFormat
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val ptBrLocale = Locale("pt", "BR")
    private val currencyFormat = NumberFormat.getCurrencyInstance(ptBrLocale)

    fun formatarMoeda(centavos: Long): String {
        val valor = centavos / 100.0
        return currencyFormat.format(valor)
    }

    fun formatarDataHora(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", ptBrLocale)
        return sdf.format(Date(timestamp))
    }

    fun formatarDataCurta(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM HH:mm", ptBrLocale)
        return sdf.format(Date(timestamp))
    }

    fun normalizarTexto(texto: String): String {
        val decomposed = Normalizer.normalize(texto.trim().lowercase(ptBrLocale), Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return withoutAccents.trim().replace("\\s+".toRegex(), " ")
    }

    /**
     * Normalização canônica para identificação e deduplicação de nomes de clientes:
     * - Remove espaços excedentes no início e no fim
     * - Converte para minúsculas
     * - Remove acentos diacríticos (NFD)
     * - Substitui pontuações e caracteres não alfanuméricos por espaço
     * - Compacta múltiplos espaços internos em um único espaço
     */
    fun normalizarNomeCliente(nome: String): String {
        val decomposed = Normalizer.normalize(nome.trim().lowercase(ptBrLocale), Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        val alphaNumOnly = withoutAccents.replace("[^a-z0-9]+".toRegex(), " ")
        return alphaNumOnly.trim().replace("\\s+".toRegex(), " ")
    }
}
