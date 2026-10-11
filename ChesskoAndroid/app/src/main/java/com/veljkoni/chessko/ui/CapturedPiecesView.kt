package com.veljkoni.chessko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veljkoni.chessko.models.ChessPiece
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type

@Composable
fun CapturedPiecesView(
    pieces: List<ChessPiece>,
    pieceStyle: PieceStyle,
    modifier: Modifier = Modifier
) {
    val sortedPieces = remember(pieces) {
        pieces.sortedByDescending { it.type.materialValue }
    }

    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        if (sortedPieces.isEmpty()) {
            Text(
                text = "–",
                // iOS crta bas caption2 (`Chessko/Views/CapturedPiecesView.swift:34`),
                // dakle Type.label -- 12 je ovde bilo razilazenje, ne odluka.
                style = Type.label,
                // Hrom (prazno stanje), ne sadrzaj stila figure -- ovo mora da prati temu.
                color = DS.inkMuted
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-6).dp)
            ) {
                sortedPieces.forEach { piece ->
                    val assetName = getAssetName(piece, pieceStyle)

                    val imageModifier = Modifier.size(28.dp)

                    AsyncImage(
                        model = "file:///android_asset/pieces/$assetName.svg",
                        contentDescription = null,
                        modifier = imageModifier
                    )
                }
            }
        }
    }
}
