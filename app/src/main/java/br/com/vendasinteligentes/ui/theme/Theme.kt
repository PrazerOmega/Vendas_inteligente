package br.com.vendasinteligentes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimariaVerde = Color(0xFF006C4C)
val SobrePrimaria = Color(0xFFFFFFFF)
val PrimariaContainer = Color(0xFF89F8C7)
val SobrePrimariaContainer = Color(0xFF002114)

val SecundariaTeal = Color(0xFF4C6357)
val TerciariaAzul = Color(0xFF3E6374)

val ErroVermelho = Color(0xFFBA1A1A)
val FundoClaro = Color(0xFFFBFDFA)
val SuperficieClara = Color(0xFFFBFDFA)

val SaldoDevedor = Color(0xFFBA1A1A)
val SaldoCredor = Color(0xFF006C4C)
val SaldoQuitado = Color(0xFF555F5B)

private val LightColorScheme = lightColorScheme(
    primary = PrimariaVerde,
    onPrimary = SobrePrimaria,
    primaryContainer = PrimariaContainer,
    onPrimaryContainer = SobrePrimariaContainer,
    secondary = SecundariaTeal,
    tertiary = TerciariaAzul,
    background = FundoClaro,
    surface = SuperficieClara,
    error = ErroVermelho
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6CDBAC),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB3CCBE),
    tertiary = Color(0xFFA5CDE0),
    error = Color(0xFFFFB4AB)
)

@Composable
fun VendasInteligentesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
