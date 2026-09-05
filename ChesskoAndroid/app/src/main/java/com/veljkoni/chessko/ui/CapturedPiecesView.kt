package com.veljkoni.chessko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veljkoni.chessko.models.ChessPiece
import com.veljkoni.chessko.models.PieceColor

@Composable
fun CapturedPiecesView(
    pieces: List<ChessPiece>,
    pieceStyle: PieceStyle,
    boardTheme: BoardTheme,
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
                fontSize = 12.sp,
                color = Color.Gray.copy(alpha = 0.5f)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-6).dp)
            ) {
                sortedPieces.forEach { piece ->
                    val assetName = getAssetName(piece, pieceStyle, boardTheme)
                    val metalBrush = if (pieceStyle == PieceStyle.METAL) {
                        if (piece.color == PieceColor.WHITE) {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFE0E2E5),
                                    Color(0xFFB9BFC5),
                                    Color(0xFFF1F3F5),
                                    Color(0xFF8B939C)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4A4D54),
                                    Color(0xFF2B2D32),
                                    Color(0xFF1A1B1E),
                                    Color(0xFF383A3E),
                                    Color(0xFF111214)
                                )
                            )
                        }
                    } else null

                    val imageModifier = Modifier
                        .size(28.dp)
                        .then(
                            if (metalBrush != null) {
                                Modifier
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(brush = metalBrush, blendMode = BlendMode.SrcIn)
                                    }
                            } else Modifier
                        )

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
