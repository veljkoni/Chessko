package com.veljkoni.chessko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.models.ChessPiece
import com.veljkoni.chessko.models.PieceType
import com.veljkoni.chessko.viewmodels.GameViewModel

/**
 * Modalni preklop za izbor figure pri promociji.
 *
 * `GameViewModel` od pocetka postavlja `showPromotion = true` i ceka izbor
 * (`confirmPromotion`/`cancelPromotion` vec postoje), ali taj izbor do sada
 * NIJE renderovao niko -- `grep -rn "showPromotion"` pogadja samo
 * `GameViewModel.kt`. Sa `autoPromoteToQueen = false` (podrazumevano) to je
 * tablu u slobodnoj partiji zauvek blokiralo na osmom redu: nijedan dodir ne
 * radi jer `isPlayerTurn`/`tap()` cekaju potez koji nikad ne stize.
 *
 * Dodir van kartice (na scrim) otkazuje potez -- `cancelPromotion()` vraca
 * tablu na stanje pre poteza (brise selekciju), isto kao iOS overlay.
 */
@Composable
fun PromotionOverlay(viewModel: GameViewModel) {
    if (viewModel.promotionMove == null) return
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val activeTheme = remember(settings.boardTheme) {
        BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
    }
    val activeStyle = remember(settings.pieceStyle) {
        PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
    }

    // Boja pesaka koji se promoviše: potez jos nije primenjen (showPromotion
    // se postavlja PRE `execute()`), pa je to boja igraca na potezu.
    val color = viewModel.gameState.currentTurn

    // D/T/L/S -- isti redosled kao iOS `promotionOverlay`.
    val choices = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

    Dialog(onDismissRequest = { viewModel.cancelPromotion() }) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E293B))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = loc("Izaberi figuru"),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (type in choices) {
                    val assetName = getAssetName(ChessPiece(type, color), activeStyle, activeTheme)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                viewModel.confirmPromotion(type)
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "file:///android_asset/pieces/$assetName.svg",
                            contentDescription = type.srbName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
