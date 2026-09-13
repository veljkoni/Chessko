package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.ui.theme.DS

data class MovePair(
    val number: Int,
    val white: String,
    val black: String?
)

@Composable
fun MoveHistoryView(
    notations: List<String>,
    selectedMoveIndex: Int? = null,
    onSelectMove: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pairs = remember(notations) {
        val list = mutableListOf<MovePair>()
        for (i in notations.indices step 2) {
            val num = (i / 2) + 1
            val white = notations[i]
            val black = if (i + 1 < notations.size) notations[i + 1] else null
            list.add(MovePair(num, white, black))
        }
        list
    }

    val pairRows = remember(pairs) {
        val list = mutableListOf<Pair<MovePair, MovePair?>>()
        for (i in pairs.indices step 2) {
            val left = pairs[i]
            val right = if (i + 1 < pairs.size) pairs[i + 1] else null
            list.add(Pair(left, right))
        }
        list
    }

    val lazyListState = rememberLazyListState()

    // Auto-scroll to the end or selected item
    LaunchedEffect(notations.size, selectedMoveIndex) {
        if (pairRows.isNotEmpty()) {
            if (selectedMoveIndex != null && selectedMoveIndex > 0) {
                val pairIndex = (selectedMoveIndex - 1) / 4
                if (pairIndex in pairRows.indices) {
                    lazyListState.animateScrollToItem(pairIndex)
                }
            } else {
                lazyListState.animateScrollToItem(pairRows.size - 1)
            }
        }
    }

    Box(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DS.fill)
            .border(1.dp, DS.line, RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (notations.isEmpty()) {
            Text(
                text = loc("Nema odigranih poteza"),
                color = DS.inkMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(pairRows) { index, row ->
                    val bg = if (index % 2 == 0) Color.Transparent else DS.fill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(vertical = 4.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Pair
                        val leftWhiteIdx = row.first.number * 2 - 1
                        val leftBlackIdx = row.first.number * 2
                        MovePairCell(
                            pair = row.first,
                            isLastWhite = if (selectedMoveIndex != null) selectedMoveIndex == leftWhiteIdx else leftWhiteIdx == notations.size,
                            isLastBlack = if (selectedMoveIndex != null) selectedMoveIndex == leftBlackIdx else leftBlackIdx == notations.size,
                            onWhiteClick = { onSelectMove(leftWhiteIdx) },
                            onBlackClick = { if (row.first.black != null) onSelectMove(leftBlackIdx) },
                            modifier = Modifier.weight(1f)
                        )

                        // Divider if right pair exists
                        if (row.second != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(16.dp)
                                    .background(DS.line)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Right Pair
                            val rightWhiteIdx = row.second!!.number * 2 - 1
                            val rightBlackIdx = row.second!!.number * 2
                            MovePairCell(
                                pair = row.second!!,
                                isLastWhite = if (selectedMoveIndex != null) selectedMoveIndex == rightWhiteIdx else rightWhiteIdx == notations.size,
                                isLastBlack = if (selectedMoveIndex != null) selectedMoveIndex == rightBlackIdx else rightBlackIdx == notations.size,
                                onWhiteClick = { onSelectMove(rightWhiteIdx) },
                                onBlackClick = { if (row.second!!.black != null) onSelectMove(rightBlackIdx) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.MovePairCell(
    pair: MovePair,
    isLastWhite: Boolean,
    isLastBlack: Boolean,
    onWhiteClick: () -> Unit,
    onBlackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${pair.number}.",
            color = DS.inkMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(26.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isLastWhite) DS.accent.copy(alpha = 0.2f) else Color.Transparent)
                .clickable(onClick = onWhiteClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = pair.white,
                color = if (isLastWhite) DS.accent else DS.ink,
                fontSize = 12.sp,
                fontWeight = if (isLastWhite) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isLastBlack) DS.accent.copy(alpha = 0.2f) else Color.Transparent)
                .clickable(enabled = pair.black != null, onClick = onBlackClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = pair.black ?: "",
                color = if (isLastBlack) DS.accent else DS.ink,
                fontSize = 12.sp,
                fontWeight = if (isLastBlack) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
