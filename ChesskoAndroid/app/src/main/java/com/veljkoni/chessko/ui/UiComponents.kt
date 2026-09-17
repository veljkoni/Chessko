package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.veljkoni.chessko.models.*
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.viewmodels.GameMode
import com.veljkoni.chessko.viewmodels.GameViewModel
import kotlin.math.max

@Composable
fun PlayerHeaderCard(
    color: PieceColor,
    name: String,
    isActive: Boolean,
    isThinking: Boolean,
    capturedPieces: List<ChessPiece>,
    materialAdvantage: Int,
    boardTheme: BoardTheme,
    pieceStyle: PieceStyle,
    evaluationScore: Double = 0.0,
    evaluationMateIn: Int? = null,
    showEvalBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBackground = DS.surface

    val borderColor = if (isActive) {
        DS.accent
    } else {
        DS.line
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBackground)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // King Avatar
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DS.fill),
            contentAlignment = Alignment.Center
        ) {
            val kingAsset = getAssetName(ChessPiece(PieceType.KING, color), pieceStyle, boardTheme)
            AsyncImage(
                model = "file:///android_asset/pieces/$kingAsset.svg",
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Name + Material Adv + Captured list
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = name,
                    color = DS.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (materialAdvantage > 0) {
                    Text(
                        text = "+$materialAdvantage",
                        color = DS.accent.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            CapturedPiecesView(
                pieces = capturedPieces,
                pieceStyle = pieceStyle,
                boardTheme = boardTheme
            )
        }

        // Evaluation Score Pill (when eval bar is enabled)
        if (showEvalBar) {
            val evalText: String? = when {
                evaluationMateIn != null -> {
                    if (color == PieceColor.WHITE && evaluationMateIn > 0) "M$evaluationMateIn"
                    else if (color == PieceColor.BLACK && evaluationMateIn < 0) "M${kotlin.math.abs(evaluationMateIn)}"
                    else null
                }
                color == PieceColor.WHITE -> {
                    if (evaluationScore >= 0.1) String.format(java.util.Locale.US, "+%.1f", evaluationScore)
                    else if (kotlin.math.abs(evaluationScore) < 0.1) "0.0"
                    else null
                }
                else -> {
                    if (evaluationScore <= -0.1) String.format(java.util.Locale.US, "+%.1f", kotlin.math.abs(evaluationScore))
                    else null
                }
            }

            val isMate = (color == PieceColor.WHITE && (evaluationMateIn ?: 0) > 0) ||
                         (color == PieceColor.BLACK && (evaluationMateIn ?: 0) < 0)

            if (evalText != null) {
                // Zlatna oznaka mata namerno ostaje fiksna -- nije u tabeli preslikavanja,
                // ista klasa izuzetka kao oznaka saha u BoardView. Krug ispravki 1/5: ta boja
                // je NEVIDLJIVA (1,16:1) na `DS.fill` svetle teme, jer je `DS.fill` od ovog
                // taska tema-zavisan a marker nije -- isto nacelo koje je Task 2 platio,
                // izokrenuto (fiksna boja nad podlogom koja je postala token). Podloga ISPOD
                // markera zato mora da bude isto tako fiksna, po analogiji sa oznakom saha
                // koja stoji na tabli -- tabla nikad ne prati temu.
                val mateBadgeBackground = Color(0xFF1E293B)
                val mateBadgeBorder = Color.White.copy(alpha = 0.18f)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isMate) mateBadgeBackground else DS.fill)
                        .border(1.dp, if (isMate) mateBadgeBorder else DS.line, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = evalText,
                        color = if (isMate) Color(0xFFFFD700) else DS.ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        // Active State Indicator
        if (isActive) {
            if (isThinking) {
                ThinkingDots()
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DS.success)
                )
            }
        }
    }
}

@Composable
fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "dots")

    @Composable
    fun dotAlpha(delayMillis: Int): Float {
        val alpha by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(delayMillis)
            ),
            label = "alpha"
        )
        return alpha
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(DS.ink.copy(alpha = dotAlpha(0))))
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(DS.ink.copy(alpha = dotAlpha(200))))
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(DS.ink.copy(alpha = dotAlpha(400))))
    }
}

@Composable
fun StatusBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DS.fill)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = DS.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RowScope.BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) DS.accent else DS.inkMuted

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor
        ),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}


fun getPlayerName(viewModel: GameViewModel, color: PieceColor): String {
    return if (viewModel.gameMode == GameMode.LOCAL_FRIEND) {
        if (color == PieceColor.WHITE) loc("Beli") else loc("Crni")
    } else {
        if (color == viewModel.playerColor) loc("Ti") else loc("Računar")
    }
}

fun getMaterialAdvantage(gameState: GameState, color: PieceColor): Int {
    val whiteVal = gameState.capturedByWhite.sumOf { it.type.materialValue / 100 }
    val blackVal = gameState.capturedByBlack.sumOf { it.type.materialValue / 100 }
    return if (color == PieceColor.WHITE) {
        max(0, whiteVal - blackVal)
    } else {
        max(0, blackVal - whiteVal)
    }
}

// Faza 6e, Task 5: dugme "Analiziraj partiju".
//
// Ovde (ne u `MainActivity.kt`) zato sto ga koriste OBA mesta koja pokrecu
// partiju protiv racunara -- slobodna partija na tabu Igra (`MainActivity.kt`,
// paket `com.veljkoni.chessko`) i korak `game` Puta (`StepGameView.kt`, ISTI
// paket `com.veljkoni.chessko.ui` kao ovaj fajl) -- isti obrazac kao
// `getStatusMessage`/`getMaterialAdvantage` iznad.
//
// Vidi se samo na GOTOVOJ partiji koja ima bar jedan potez -- analiza
// pretrazuje pozicije redom, pa nad partijom koja jos traje ne bi ni imala
// sta da kaze. Uslov je isti kao iOS (`GameView.swift`, `analysisButton`):
// `viewModel.isGameOver && !moveNotations.isEmpty`.
//
// Na tabu Igra mora stajati u OBE grane rasporeda (pejzazna i portretna,
// `MainActivity.kt`) -- grane ne dele telo, pa bi dodavanje na jedno mesto
// ostavilo dugme nevidljivim u drugoj orijentaciji (iOS je tu gresku vec
// jednom napravio, vidi CLAUDE.md).
fun canAnalyzeGame(viewModel: GameViewModel): Boolean =
    viewModel.isGameOver && viewModel.gameState.moveNotations.isNotEmpty()

@Composable
fun AnalysisButton(viewModel: GameViewModel, onClick: () -> Unit) {
    if (!canAnalyzeGame(viewModel)) return

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DS.accent,
            contentColor = DS.onAccent
        )
    ) {
        Icon(
            imageVector = Icons.Default.Assessment,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = loc("Analiziraj partiju"),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun getStatusMessage(viewModel: GameViewModel): String {
    val status = viewModel.gameState.status
    val color = viewModel.playerColor
    return when (status) {
        is GameStatus.Playing -> {
            if (viewModel.gameMode == GameMode.LOCAL_FRIEND) {
                if (viewModel.gameState.currentTurn == PieceColor.WHITE) loc("Beli igra") else loc("Crni igra")
            } else {
                if (viewModel.gameState.currentTurn == color) loc("Tvoj potez") else loc("Računar razmišlja...")
            }
        }
        is GameStatus.Check -> {
            val targetColor = status.color
            if (viewModel.gameMode == GameMode.LOCAL_FRIEND) {
                if (targetColor == PieceColor.WHITE) loc("Šah! Beli kralj je napadnut.") else loc("Šah! Crni kralj je napadnut.")
            } else {
                if (targetColor == color) loc("Šah! Tvoj kralj je napadnut.") else loc("Šah! Napadaš kralja.")
            }
        }
        is GameStatus.Checkmate -> {
            val loserColor = status.color
            if (viewModel.gameMode == GameMode.LOCAL_FRIEND) {
                if (loserColor == PieceColor.WHITE) loc("Mat! Crni je pobedio! 🎉") else loc("Mat! Beli je pobedio! 🎉")
            } else {
                if (loserColor == color) loc("Mat! Izgubio si.") else loc("Mat! Pobedio si! 🎉")
            }
        }
        is GameStatus.Resigned -> {
            val loserColor = status.color
            if (viewModel.gameMode == GameMode.LOCAL_FRIEND) {
                if (loserColor == PieceColor.WHITE) loc("Predaja! Crni je pobedio.") else loc("Predaja! Beli je pobedio.")
            } else {
                if (loserColor == color) loc("Predaja! Izgubio si.") else loc("Predaja! Pobedio si! 🎉")
            }
        }
        is GameStatus.Draw -> {
            when (status.reason) {
                is DrawReason.Stalemate -> loc("Pat – remi!")
                is DrawReason.FiftyMoves -> loc("Remi – pravilo 50 poteza.")
                is DrawReason.Repetition -> loc("Remi – ponavljanje pozicije.")
                is DrawReason.InsufficientMaterial -> loc("Remi – nedovoljan materijal.")
            }
        }
    }
}
