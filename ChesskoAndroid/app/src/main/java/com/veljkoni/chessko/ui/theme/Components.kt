package com.veljkoni.chessko.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Globalna pravila upotrebe (dizajnerski pregled, spec § 3). Jedno mesto za
// karticu, naslov sekcije i oblike dijaloga — iOS ima iste u
// `Chessko/Views/DesignSystem.swift` (`.dsCard()`, `DSSectionHeader`).

/**
 * Kartica = `DS.surface` + ivica 1 dp `DS.line`, u obe teme (spec § 3).
 *
 * `line` nad `ground` je 1,16 / 1,32 — ivica je DEKORATIVNA (spec to kaze
 * izricito: kartica se prepoznaje po sadrzaju). Raniji komentari u
 * `SettingsView.kt` su ivicu uklonili zbog istog broja; spec je tu presudu
 * obrnuo, pa ivica postoji i nije zamisljena da prelazi 3:1.
 *
 * `DS.fill` NIJE kartica — samo cipovi, polja, dugmad i redovi unutar kartice.
 */
@Composable
fun Modifier.dsCard(radius: Dp = DS.Radius.m): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(DS.surface)
        .border(1.dp, DS.line, shape)
}

/** Grupa redova u kartici sa `line` razdelnikom izmedju redova (spec § 3 „Grupe"). */
@Composable
fun DSCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().dsCard(), content = content)
}

/** Razdelnik izmedju redova unutar `DSCardGroup`. */
@Composable
fun DSRowDivider() = HorizontalDivider(color = DS.line)

/**
 * Naslov sekcije: `Type.heading` bold, `DS.ink` (15,72 / 16,15) — spec § 3.
 * Ne `accent`: accent je samo za ono sto se tapa ili je izabrano.
 */
@Composable
fun DSSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text = text, color = DS.ink, style = Type.heading, fontWeight = FontWeight.Bold, modifier = modifier)
}

/** Oblik dijaloga (spec § 3): `Radius.l`. */
val DialogShape: Shape @Composable get() = RoundedCornerShape(DS.Radius.l)

/** Oblik dugmeta (spec § 3): `Radius.m`, nigde „stadion". */
val ButtonShape: Shape @Composable get() = RoundedCornerShape(DS.Radius.m)

/** Primarno dugme dijaloga: `accent` + `onAccent`. */
@Composable
fun dsPrimaryButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(containerColor = DS.accent, contentColor = DS.onAccent)

/** Sporedno dugme sa ispunom: `fill` + `ink` u obe teme. */
@Composable
fun dsSecondaryButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(containerColor = DS.fill, contentColor = DS.ink)
