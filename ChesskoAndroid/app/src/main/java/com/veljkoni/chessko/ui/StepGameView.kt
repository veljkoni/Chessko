package com.veljkoni.chessko.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.logic.GameDifficulty
import com.veljkoni.chessko.logic.ProgressStore
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.viewmodels.GameViewModel

// MARK: - Pokretac koraka `game` (Faza 6c, Task 7)
//
// Partija protiv racunara sa tezinom (i opciono startnom pozicijom) iz
// koraka. Ceo tok partije -- tap, legalnost, AI, zvuk, haptika, promocija,
// kraj partije -- vec zivi u `GameViewModel`-u i deli se sa tabom Igra; ovaj
// ekran dodaje samo ono sto korak ima a slobodna partija nema: zavrsetak
// KORAKA.
//
// Tri odluke koje ekran nosi:
// 1. SOPSTVENI `GameViewModel`, sa sopstvenim slotom za cuvanje
//    (`GameViewModel.stepSaveKey(stepId)`). Deljeni primerak (ili zajednicki
//    kljuc) bi ulaskom u korak ucitao/pregazio partiju koju korisnik ima u
//    toku na tabu Igra -- isti razlog zbog kog `StepPracticeView` drzi
//    sopstveni `PuzzleViewModel`.
// 2. Korak se zavrsava kad partija dodje do KRAJA, BEZ OBZIRA NA ISHOD --
//    spec trazi "partija odigrana do kraja", ne pobedu. Predaja je takodje
//    kraj.
// 3. Upis koraka je gejtovan na `pendingStepId`, da se ne bi desio dvaput za
//    isti ulazak (npr. ako se status pozicije ponovo izracuna dok je partija
//    vec gotova).
@Composable
fun StepGameView(difficulty: String, startFEN: String?, stepId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val progressStore = remember { ProgressStore.getInstance(app) }

    // Sirov string iz kurikuluma ("beginner", "easy", ...) -- `Curriculum.parse`
    // ga vec proverava protiv `KNOWN_DIFFICULTIES`, pa je ovo mapiranje totalno
    // u praksi; fallback na `MEDIUM` postoji samo da ekran nikad ne padne.
    val gameDifficulty = remember(difficulty) {
        GameDifficulty.values().find { it.name.equals(difficulty, ignoreCase = true) }
            ?: GameDifficulty.MEDIUM
    }

    // Sopstveni model po `stepId`: novi korak dobija nov primerak, a s njim i
    // nov slot za cuvanje (`stepSaveKey`).
    val viewModel = remember(stepId) { GameViewModel(app, GameViewModel.stepSaveKey(stepId)) }

    // `viewModel` je napravljen kroz `remember`, NE kroz `ViewModelStore`, pa mu se
    // `onCleared()` NIKAD ne izvrsi -- `SoundManager` (nije singleton, svaki
    // `GameViewModel` pravi SVOJ `SoundPool`) bi bez ovoga procureo na svaki izlazak
    // iz koraka. Kljuc je `stepId`, isto kao `remember` iznad: ako se `stepId`
    // promeni dok je ekran ziv, STARI model se oslobadja pre nego sto se efekat
    // za novi instalira, ne tek kad ceo ekran nestane. Isti obrazac kao
    // `ChessClockView.kt` (`DisposableEffect` + `release()`).
    DisposableEffect(stepId) {
        onDispose { viewModel.releaseSounds() }
    }

    // `null` znaci "vec upisan" -- zavrsetak se okida TACNO jednom po ulasku,
    // ne na svako ponovno izracunavanje statusa partije.
    var pendingStepId by remember(stepId) { mutableStateOf<String?>(stepId) }

    var showResignConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(stepId) {
        viewModel.startStepGame(gameDifficulty, startFEN)
        // Branik za slucaj da je partija gotova pre nego sto donji efekat ima
        // sta da uporedi. `startStepGame` zavrsenu partiju ne nastavlja (resetuje
        // je), pa je ovo danas nedostizno -- ali uslov zavrsetka stoji na oba
        // puta do njega, isto kao iOS `start()`/`completeStepOnce()`.
        if (viewModel.isGameOver && pendingStepId == stepId) {
            pendingStepId = null
            progressStore.completeStep(stepId)
            GameViewModel.clearStepSave(app, stepId)
        }
    }

    // Reaguje na SVAKU promenu statusa partije (potez, mat, remi, predaja).
    LaunchedEffect(viewModel.gameState.status) {
        if (viewModel.isGameOver && pendingStepId == stepId) {
            pendingStepId = null
            progressStore.completeStep(stepId)
            GameViewModel.clearStepSave(app, stepId)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = loc("Partija"),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { viewModel.undo() },
                    enabled = viewModel.canUndo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White.copy(alpha = 0.7f),
                        disabledContentColor = Color.White.copy(alpha = 0.25f)
                    )
                ) {
                    Text(text = "↩︎")
                }
                if (viewModel.canResign) {
                    Button(
                        onClick = { showResignConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFFEF4444)
                        )
                    ) {
                        Text(text = "🏳")
                    }
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Text(text = loc("Zatvori"))
                }
            }
        }

        OpponentCard(viewModel)
        StatusCard(message = getStatusMessage(viewModel))

        val settings = remember { SettingsManager.getInstance(context) }
        val activeTheme = remember(settings.boardTheme) {
            BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
        }
        val activeStyle = remember(settings.pieceStyle) {
            PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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

        StepGameFooter(isGameOver = viewModel.isGameOver, onClose = onClose)
    }

    if (viewModel.showPromotion) {
        PromotionOverlay(viewModel)
    }

    if (showResignConfirm) {
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text(text = loc("Predaja partije"), color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(text = loc("Da li ste sigurni da želite da predate partiju?"), color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resign()
                    showResignConfirm = false
                }) {
                    Text(text = loc("Predaj"), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) {
                    Text(text = loc("Nastavi igru"), color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun OpponentCard(viewModel: GameViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text = "🤖", fontSize = 16.sp)
        Text(text = loc("Računar"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(text = viewModel.difficulty.label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Spacer(modifier = Modifier.weight(1f))
        if (viewModel.isThinking) {
            Text(text = loc("Računar razmišlja..."), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text = message, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun StepGameFooter(isGameOver: Boolean, onClose: () -> Unit) {
    if (isGameOver) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34D399)))
                Text(text = loc("Korak je završen"), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text(text = loc("Nazad na Put"), fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Text(
            text = loc("Korak se završava kad partija dođe do kraja."),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
