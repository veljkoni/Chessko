package com.veljkoni.chessko.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.R
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type

// MARK: - Glavna navigacija (spec § 4, § 10)
//
// Tri taba, dva oblika: donja traka (`NavigationBar`) ispod 840 dp sirine
// prozora, `NavigationRail` od 840 dp (M3 „expanded"). Isti spisak tabova i iste
// boje za oba — jedno mesto istine.
//
// Ikone (§ 10): Igra = sopstveni skakac (`res/drawable/ic_tab_igra*.xml`, iz
// materijala dizajnera), Zadaci = `extension`, Put = `route`. Oba Material
// simbola postoje u `material-icons-extended` koji je vec na classpath-u
// (`Icons.Rounded.*` = pun oblik, `Icons.Outlined.*` = obris), pa vektor `route`
// nije trebalo uvoziti rucno.
// Stanja: izabran = pun oblik `accent` na `fill` indikatoru (7,08 / 5,69),
// neizabran = obris `inkMuted`.

/** Prag za `NavigationRail` (M3 window size class *expanded*). */
const val NAVIGATION_RAIL_MIN_WIDTH_DP = 840

/** Da li je prozor dovoljno sirok za `NavigationRail` umesto donje trake. */
@Composable
fun useNavigationRail(): Boolean =
    LocalConfiguration.current.screenWidthDp >= NAVIGATION_RAIL_MIN_WIDTH_DP

enum class ChesskoTab { GAME, PUZZLES, PATH }

@Composable
private fun ChesskoTab.label(): String = when (this) {
    ChesskoTab.GAME -> loc("Igra")
    ChesskoTab.PUZZLES -> loc("Zadaci")
    ChesskoTab.PATH -> loc("Put")
}

@Composable
private fun ChesskoTab.icon(selected: Boolean): Painter = when (this) {
    ChesskoTab.GAME -> painterResource(if (selected) R.drawable.ic_tab_igra_fill else R.drawable.ic_tab_igra)
    ChesskoTab.PUZZLES -> rememberVectorPainter(if (selected) Icons.Rounded.Extension else Icons.Outlined.Extension)
    ChesskoTab.PATH -> rememberVectorPainter(if (selected) Icons.Rounded.Route else Icons.Outlined.Route)
}

/** Donja traka. Inseti navigacione trake (rucka za gestove) ulaze u visinu trake — spec § 4. */
@Composable
fun ChesskoNavigationBar(selected: ChesskoTab, onSelect: (ChesskoTab) -> Unit) {
    NavigationBar(
        containerColor = DS.navBar,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        ChesskoTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                // Labela ispod nosi znacenje — ikona je dekorativna.
                icon = { Icon(painter = tab.icon(isSelected), contentDescription = null) },
                label = {
                    Text(
                        text = tab.label(),
                        style = Type.label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DS.accent,
                    selectedTextColor = DS.accent,
                    indicatorColor = DS.fill,
                    unselectedIconColor = DS.inkMuted,
                    unselectedTextColor = DS.inkMuted
                )
            )
        }
    }
}

/** Bocna traka za prozore >= 840 dp (spec § 4). */
@Composable
fun ChesskoNavigationRail(selected: ChesskoTab, onSelect: (ChesskoTab) -> Unit) {
    NavigationRail(containerColor = DS.navBar) {
        ChesskoTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(painter = tab.icon(isSelected), contentDescription = null) },
                label = {
                    Text(
                        text = tab.label(),
                        style = Type.label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = DS.accent,
                    selectedTextColor = DS.accent,
                    indicatorColor = DS.fill,
                    unselectedIconColor = DS.inkMuted,
                    unselectedTextColor = DS.inkMuted
                )
            )
        }
    }
}
