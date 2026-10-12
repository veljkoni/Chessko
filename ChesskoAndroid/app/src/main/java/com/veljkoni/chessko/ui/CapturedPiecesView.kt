package com.veljkoni.chessko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veljkoni.chessko.models.ChessPiece

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
        // § 5 / § 19: nigde „–" kao prazno stanje. Pozivalac (`PlayerCard`)
        // red ionako ne crta kad nema uzetih figura; ostaje prazan red visine 28.
        if (sortedPieces.isNotEmpty()) {
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
