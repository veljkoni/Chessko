package com.veljkoni.chessko.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - Dizajn tokeni
//
// Jedan izvor istine za boje, razmake i radijuse. Pravac „Tiho i precizno"
// (spec 5.6): neutralne podloge, jedan uzdrzan akcent, tabla je jedina
// zasicena stvar na ekranu.
//
// Akcent je FIKSAN — ne menja se sa temom table. Boje vezane za tablu (polja,
// poslednji potez, legalni potezi, sah) i dalje dolaze iz `BoardTheme`.
//
// Vrednosti su ISTE kao iOS (`Chessko/Views/DesignSystem.swift`). Kurikulum,
// lekcije i baza zadataka se vec dele; paleta je cetvrta zajednicka stvar.

data class ChesskoColors(
    val accent: Color,
    val ground: Color,
    val surface: Color,
    val navBar: Color,
    val fill: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    /** Semanticke boje — nose znacenje, nisu ukras. */
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Zatamnjenje ispod modalnih preklopa. Namerno isto u obe teme. */
    val scrim: Color,
    /** Tekst i ikone NA scrim-u. Namerno bela u obe teme. */
    val onScrim: Color,
    /** Taman tekst koji se NE invertuje — za povrsine koje same ne prate temu. */
    val inkFixed: Color,
    /**
     * Tekst i ikone NA `accent` podlozi. MORA biti adaptivan: `accent` menja
     * svetlinu izmedju tema, pa bela na TAMNOJ varijanti (svetloplavi
     * `#7EA0E8`) daje 2,6:1 — ispod AA (CLAUDE.md, Faza 2 changelog).
     * Cuva ga `ContrastTest.plainWhiteWouldFailOnTheDarkAccent`.
     */
    val onAccent: Color
)

val LightColors = ChesskoColors(
    accent   = Color(0xFF2E4A8A),
    ground   = Color(0xFFF2F3F7),
    surface  = Color(0xFFFFFFFF),
    navBar   = Color(0xFFFFFFFF),
    fill     = Color(0xFFE7EAF1),
    line     = Color(0xFFDFE3EC),
    ink      = Color(0xFF161A22),
    inkMuted = Color(0xFF6B7280),
    success  = Color(0xFF2F7D4F),
    warning  = Color(0xFFB07D2B),
    danger   = Color(0xFFB3261E),
    scrim    = Color(0x8C000000),
    onScrim  = Color(0xFFFFFFFF),
    inkFixed = Color(0xFF161A22),
    onAccent = Color(0xFFFFFFFF)
)

val DarkColors = ChesskoColors(
    accent   = Color(0xFF7EA0E8),
    ground   = Color(0xFF0E1428),
    surface  = Color(0xFF161D33),
    navBar   = Color(0xFF131A2E),
    fill     = Color(0xFF1E2740),
    line     = Color(0xFF232C46),
    ink      = Color(0xFFEEF1F7),
    inkMuted = Color(0xFF8B93A7),
    success  = Color(0xFF6FCF97),
    warning  = Color(0xFFE0B252),
    danger   = Color(0xFFF2857A),
    scrim    = Color(0x8C000000),
    onScrim  = Color(0xFFFFFFFF),
    inkFixed = Color(0xFF161A22),
    onAccent = Color(0xFF161A22)
)

/**
 * `staticCompositionLocalOf`, ne `compositionLocalOf`: paleta se menja retko
 * (promena teme), a citaju je stotine mesta. Staticna varijanta ne prati
 * citaoce pojedinacno nego rekomponuje ceo podstablo — jeftinije za ovaj odnos.
 */
val LocalChesskoColors = staticCompositionLocalOf { DarkColors }

/**
 * Pozivna mesta citaju `DS.accent`, isto kao na iOS-u. Geteri su
 * `@ReadOnlyComposable` da ne prave sopstvenu grupu u kompoziciji.
 */
object DS {
    val accent: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.accent
    val ground: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.ground
    val surface: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.surface
    val navBar: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.navBar
    val fill: Color     @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.fill
    val line: Color     @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.line
    val ink: Color      @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.ink
    val inkMuted: Color @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.inkMuted
    val success: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.success
    val warning: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.warning
    val danger: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.danger
    val scrim: Color    @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.scrim
    val onScrim: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.onScrim
    val inkFixed: Color @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.inkFixed
    val onAccent: Color @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.onAccent

    object Space {
        val xs: Dp = 4.dp
        val s: Dp = 8.dp
        val m: Dp = 12.dp
        val l: Dp = 16.dp
        val xl: Dp = 24.dp
    }

    object Radius {
        val s: Dp = 8.dp
        val m: Dp = 12.dp
        val l: Dp = 16.dp
    }

    /**
     * Najveca stranica table. Na telefonu se nikad ne dostigne; na tabletu
     * sprecava da tabla proguta ceo ekran.
     */
    val maxBoardSide: Dp = 560.dp
}
