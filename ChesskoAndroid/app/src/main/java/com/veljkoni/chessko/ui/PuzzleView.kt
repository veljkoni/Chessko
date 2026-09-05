package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.viewmodels.PuzzlePhase
import com.veljkoni.chessko.viewmodels.PuzzleViewModel
import java.time.format.DateTimeFormatter

@Composable
fun PuzzleView(
    viewModel: PuzzleViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Board
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                when (viewModel.phase) {
                    PuzzlePhase.LOADING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00D2FF))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = loc("Učitavam zadatak..."),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                    PuzzlePhase.NETWORK_ERROR -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(text = "⚠️", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Greška pri učitavanju zadatka",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadDailyPuzzle() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF))
                            ) {
                                Text(text = loc("Pokušaj ponovo"), color = Color.Black)
                            }
                        }
                    }
                    else -> {
                        val settings = remember { SettingsManager.getInstance(context) }
                        val activeTheme = remember(settings.boardTheme) {
                            BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
                        }
                        val activeStyle = remember(settings.pieceStyle) {
                            PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
                        }

                        BoardView(
                            board = viewModel.gameState.board,
                            isFlipped = viewModel.isFlipped,
                            selectedPosition = viewModel.selectedPosition,
                            legalMoves = viewModel.legalMovesForSelected,
                            lastMove = viewModel.lastMove,
                            gameStatus = viewModel.gameState.status,
                            boardTheme = activeTheme,
                            pieceStyle = activeStyle,
                            showCoordinates = settings.showCoordinates,
                            showLastMoveHighlight = settings.showLastMoveHighlight,
                            showLegalMoves = settings.showLegalMoves,
                            onTap = { pos -> viewModel.tap(pos) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Right Side: Toolbar, Metadata & Controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DateNavigationRow(viewModel)

                if (viewModel.phase != PuzzlePhase.LOADING && viewModel.phase != PuzzlePhase.NETWORK_ERROR) {
                    PuzzleMetadataHeader(viewModel)
                    StatusBanner(message = viewModel.statusMessage)
                    Spacer(modifier = Modifier.height(8.dp))
                    PuzzleActionsRow(viewModel)
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Date Navigation Toolbar
            DateNavigationRow(viewModel)

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Puzzle Metadata Header
            if (viewModel.phase != PuzzlePhase.LOADING && viewModel.phase != PuzzlePhase.NETWORK_ERROR) {
                PuzzleMetadataHeader(viewModel)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Main Content (Chessboard or Loading/Error)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (viewModel.phase) {
                    PuzzlePhase.LOADING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00D2FF))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = loc("Učitavam zadatak..."),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                    PuzzlePhase.NETWORK_ERROR -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Greška pri učitavanju zadatka",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = viewModel.networkErrorMessage,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadDailyPuzzle() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF))
                            ) {
                                Text(text = loc("Pokušaj ponovo"), color = Color.Black)
                            }
                        }
                    }
                    else -> {
                        val settings = remember { SettingsManager.getInstance(context) }
                        val activeTheme = remember(settings.boardTheme) {
                            BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
                        }
                        val activeStyle = remember(settings.pieceStyle) {
                            PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
                        }

                        BoardView(
                            board = viewModel.gameState.board,
                            isFlipped = viewModel.isFlipped,
                            selectedPosition = viewModel.selectedPosition,
                            legalMoves = viewModel.legalMovesForSelected,
                            lastMove = viewModel.lastMove,
                            gameStatus = viewModel.gameState.status,
                            boardTheme = activeTheme,
                            pieceStyle = activeStyle,
                            showCoordinates = settings.showCoordinates,
                            showLastMoveHighlight = settings.showLastMoveHighlight,
                            showLegalMoves = settings.showLegalMoves,
                            onTap = { pos -> viewModel.tap(pos) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Status banner
            if (viewModel.phase != PuzzlePhase.LOADING && viewModel.phase != PuzzlePhase.NETWORK_ERROR) {
                StatusBanner(message = viewModel.statusMessage)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Actions row
            if (viewModel.phase != PuzzlePhase.LOADING && viewModel.phase != PuzzlePhase.NETWORK_ERROR) {
                PuzzleActionsRow(viewModel)
            }
        }
    }
}

@Composable
fun DateNavigationRow(viewModel: PuzzleViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prev button
        Button(
            onClick = { viewModel.goToPrevious() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(text = "◀️", fontSize = 14.sp)
        }

        // Date Display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val dateStr = viewModel.selectedDate.format(DateTimeFormatter.ofPattern("d. MMMM yyyy."))
            Text(
                text = dateStr,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (viewModel.isSolved(viewModel.selectedDate)) {
                Text(text = "✅", fontSize = 14.sp)
            }
        }

        // Next button
        Button(
            onClick = { viewModel.goToNext() },
            enabled = viewModel.canGoNext,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.White.copy(alpha = 0.2f)
            ),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(text = "▶️", fontSize = 14.sp)
        }
    }
}

@Composable
fun PuzzleMetadataHeader(viewModel: PuzzleViewModel) {
    val puzzle = viewModel.currentPuzzle ?: return
    
    val diffColor = when (puzzle.difficultyColor) {
        "green" -> Color(0xFF10B981)
        "yellow" -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Rating indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Rejting: ",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
            Text(
                text = puzzle.rating.toString(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Right: Difficulty Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(diffColor.copy(alpha = 0.15f))
                .border(1.dp, diffColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = puzzle.difficultyLabel,
                color = diffColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PuzzleActionsRow(viewModel: PuzzleViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Solution Button
        Button(
            onClick = { viewModel.showSolution() },
            enabled = viewModel.phase == PuzzlePhase.PLAYING || viewModel.phase == PuzzlePhase.WRONG_MOVE,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.02f),
                disabledContentColor = Color.White.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(text = "💡 Prikaži rešenje", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        // Restart / Retry Button
        Button(
            onClick = { viewModel.loadDailyPuzzle() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(text = "🔄 Pokušaj ponovo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
