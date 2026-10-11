package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.models.MoveClass
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.ui.theme.dsCard

/** Visina jednog reda liste poteza (spec § 5). */
val MoveRowHeight = 36.dp

/**
 * Lista poteza (spec § 5, deli je i Analiza — § 6): kartica `surface` + `line`,
 * mreza broj · beli · crni, red 36, `Type.mono`. Izabran potez je `accent` tekst
 * na `selectionTint` (accent 12 % / 16 %). Prazno stanje: „Potezi će se pojaviti
 * ovde" (`Type.body`, `inkMuted`).
 *
 * Visinu odredjuje pozivalac (`Modifier.height`/`weight`) — u Igri lista rezervise
 * ostatak visine ispod table. iOS: `Chessko/Views/MoveHistoryView.swift`.
 *
 * @param selectedMoveIndex izabrana pozicija kao broj odigranih poluteza
 *   (1 = posle prvog belog poteza); `null` = ziva partija.
 * @param moveClass opciona klasa poteza po polutezu (od 0) — oznaka ispred notacije.
 */
@Composable
fun MoveHistoryView(
    notations: List<String>,
    modifier: Modifier = Modifier,
    selectedMoveIndex: Int? = null,
    highlightsLastMoveWhenLive: Boolean = true,
    moveClass: ((Int) -> MoveClass?)? = null,
    onSelectMove: (Int) -> Unit = {}
) {
    val rowCount = (notations.size + 1) / 2
    val listState = rememberLazyListState()

    // Auto-skrol do izabranog ili poslednjeg poteza.
    LaunchedEffect(notations.size, selectedMoveIndex) {
        if (rowCount > 0) {
            val ply = if (selectedMoveIndex != null && selectedMoveIndex > 0) selectedMoveIndex - 1 else notations.size - 1
            val row = (ply / 2).coerceIn(0, rowCount - 1)
            listState.animateScrollToItem(row)
        }
    }

    fun isSelected(ply: Int): Boolean =
        if (selectedMoveIndex != null) selectedMoveIndex == ply + 1
        else highlightsLastMoveWhenLive && ply == notations.size - 1

    Box(
        modifier = modifier
            .fillMaxWidth()
            .dsCard(),
        contentAlignment = Alignment.Center
    ) {
        if (notations.isEmpty()) {
            Text(
                text = loc("Potezi će se pojaviti ovde"),
                color = DS.inkMuted,
                style = Type.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(DS.Space.l)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = DS.Space.xs)
            ) {
                itemsIndexed(List(rowCount) { it }) { _, row ->
                    val number = row + 1
                    val whitePly = row * 2
                    val blackPly = whitePly + 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MoveRowHeight)
                            .padding(horizontal = DS.Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)
                    ) {
                        Text(
                            text = "$number.",
                            color = DS.inkMuted,
                            style = Type.mono,
                            modifier = Modifier
                                .width(36.dp)
                                .clearAndSetSemantics { }
                        )
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            MoveCell(
                                notation = notations[whitePly],
                                label = "$number. ${notations[whitePly]}",
                                selected = isSelected(whitePly),
                                moveClass = moveClass?.invoke(whitePly),
                                onClick = { onSelectMove(whitePly + 1) }
                            )
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (blackPly < notations.size) {
                                MoveCell(
                                    notation = notations[blackPly],
                                    label = "$number… ${notations[blackPly]}",
                                    selected = isSelected(blackPly),
                                    moveClass = moveClass?.invoke(blackPly),
                                    onClick = { onSelectMove(blackPly + 1) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveCell(
    notation: String,
    label: String,
    selected: Boolean,
    moveClass: MoveClass?,
    onClick: () -> Unit
) {
    val description = if (moveClass != null) "${moveClass.accessibilityName}, $label" else label
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(DS.Radius.s))
            .background(if (selected) selectionTint else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                this.selected = selected
            }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (moveClass != null) {
            MoveClassBadge(moveClass)
        }
        Text(
            text = notation,
            color = if (selected) DS.accent else DS.ink,
            style = Type.mono,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
