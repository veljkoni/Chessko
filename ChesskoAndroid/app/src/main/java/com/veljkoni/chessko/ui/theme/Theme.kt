package com.veljkoni.chessko.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * `dynamicColor` je UKLONJEN, namerno.
 *
 * Zatecena verzija je bila Android Studio sablon sa `dynamicColor = true`, koji
 * na Androidu 12+ vuce boje sa KORISNIKOVE TAPETE. Za aplikaciju ciji spec
 * (5.6) trazi jedan fiksan akcent to nije funkcija nego greska — akcent bi se
 * menjao sa pozadinom telefona.
 *
 * `MaterialTheme.colorScheme` se i dalje popunjava, jer ga koriste zatecene
 * Material komponente (`Switch`, `Slider`, `Card`); bez toga bi one ostale
 * ljubicaste iz sablona.
 */
@Composable
fun ChesskoTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            error = colors.danger, outline = colors.line
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            error = colors.danger, outline = colors.line
        )
    }

    CompositionLocalProvider(LocalChesskoColors provides colors) {
        MaterialTheme(colorScheme = material, typography = Typography, content = content)
    }
}
