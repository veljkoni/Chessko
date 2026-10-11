package com.veljkoni.chessko.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.logic.BoardSizing

// Compose strana zajednicke funkcije strane table (spec § 13). Racun je u
// `logic/BoardSizing` (cist, JVM test); ovde su samo citanje prozora i Dp omotac.

/**
 * Tablet = prozor >= 600 dp sirok i >= 480 dp visok (vidi `BoardSizing.isTabletWindow`).
 * Isto citanje prozora kao prag rail-a (`useNavigationRail`) — bez nove zavisnosti.
 */
@Composable
fun isTabletWindow(): Boolean {
    val configuration = LocalConfiguration.current
    return BoardSizing.isTabletWindow(configuration.screenWidthDp, configuration.screenHeightDp)
}

/**
 * `BoardSizing.boardSide` u Dp. `width`/`height` = prostor ekrana (bez sistemskog hroma);
 * `chromeTop` = sopstveno zaglavlje ekrana koje stoji iznad table u istom prostoru.
 */
fun boardSide(
    width: Dp,
    height: Dp,
    isTablet: Boolean,
    isLandscape: Boolean,
    chromeTop: Dp = 0.dp,
    phoneHeightReserve: Dp = 0.dp
): Dp = BoardSizing.boardSide(
    width = width.value,
    height = height.value,
    isTablet = isTablet,
    isLandscape = isLandscape,
    chromeTop = chromeTop.value,
    phoneHeightReserve = phoneHeightReserve.value
).dp

/** Panel uz tablu u pejzazu na tabletu (Igra, Zadaci, koraci). */
val TabletPanelWidth: Dp = BoardSizing.TABLET_PANEL_WIDTH.dp
