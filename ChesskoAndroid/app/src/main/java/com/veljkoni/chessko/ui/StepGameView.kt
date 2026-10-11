package com.veljkoni.chessko.ui

import com.veljkoni.chessko.ui.theme.DialogShape
import com.veljkoni.chessko.ui.theme.ButtonShape
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.veljkoni.chessko.models.GameStatus
import com.veljkoni.chessko.models.PieceColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.logic.GameDifficulty
import com.veljkoni.chessko.logic.ProgressStore
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
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
    var showAnalysis by remember { mutableStateOf(false) }

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

    val settings = remember { SettingsManager.getInstance(context) }
    val activeTheme = remember(settings.boardTheme) {
        BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
    }
    val activeStyle = remember(settings.pieceStyle) {
        PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val side = stepBoardSide(maxWidth, maxHeight)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = DS.Space.l)) {
            StepHeader(title = loc("Partija"), onClose = onClose) {
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = viewModel.canUndo,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = DS.ink,
                        // `ink` ukljuceno / `inkMuted` iskljuceno na `ground`
                        // (15,72 / 16,15 naspram 5,28 / 5,94).
                        disabledContentColor = DS.inkMuted
                    )
                ) {
                    // Ikona stoji SAMA, pa dobija opis. `AutoMirrored` zbog RTL-a.
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = loc("Vrati potez"),
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (viewModel.canResign) {
                    IconButton(onClick = { showResignConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = loc("Predaj partiju"),
                            tint = DS.danger,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // spec § 7: sadrzaj od vrha, iste komponente kao Igra — kartica
            // protivnika („Računar · Početnik"), tabla, kartica igraca („Na potezu"
            // umesto posebnog reda „Tvoj potez"), pa napomena.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = DS.Space.l, bottom = DS.Space.l),
                verticalArrangement = Arrangement.spacedBy(DS.Space.m),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StepOpponentCard(viewModel, activeTheme, activeStyle)

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
                    modifier = Modifier.size(side)
                )

                StepPlayerCard(viewModel, activeTheme, activeStyle)

                StepGameFooter(
                    viewModel = viewModel,
                    isGameOver = viewModel.isGameOver,
                    onClose = onClose,
                    onAnalyze = { showAnalysis = true }
                )
            }
        }
    }

    if (viewModel.showPromotion) {
        PromotionOverlay(viewModel)
    }

    // Korak se upisuje kao zavrsen PRE ovoga (vidi LaunchedEffect-e iznad,
    // oba gejtovana na `isGameOver && pendingStepId == stepId`), pa analiza
    // NIKAD ne stoji na putu zavrsetka koraka -- ako motor nije dostupan,
    // korak je vec zavrsen, samo dugme ostaje bez efekta.
    if (showAnalysis) {
        // spec § 4: preko celog prozora, iznad donje trake (`FullScreen.kt`).
        FullScreenDestination(onDismiss = { showAnalysis = false }) {
            AnalysisView(viewModel = viewModel, onClose = { showAnalysis = false })
        }
    }

    if (showResignConfirm) {
        // spec § 5: „Predati partiju?" · „Nastavi" (accent) · „Predaj" (danger) — isto
        // kao ekran Igra (Task 4), bez recenice na „Vi".
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text(text = loc("Predati partiju?"), color = DS.ink, fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resign()
                    showResignConfirm = false
                }) {
                    Text(text = loc("Predaj"), color = DS.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) {
                    Text(text = loc("Nastavi"), color = DS.accent)
                }
            },
            containerColor = DS.surface,
            shape = DialogShape
        )
    }
}

private fun stepActive(viewModel: GameViewModel, color: PieceColor): Boolean =
    viewModel.gameState.currentTurn == color && !viewModel.isGameOver

private fun stepCaptured(viewModel: GameViewModel, color: PieceColor) =
    if (color == PieceColor.WHITE) viewModel.gameState.capturedByWhite else viewModel.gameState.capturedByBlack

/** Kartica protivnika „Računar · Početnik" — bez strelice: nivo zadaje korak. */
@Composable
private fun StepOpponentCard(viewModel: GameViewModel, boardTheme: BoardTheme, pieceStyle: PieceStyle) {
    val color = if (viewModel.playerColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
    val active = stepActive(viewModel, color)
    PlayerCard(
        color = color,
        name = loc("Računar"),
        boardTheme = boardTheme,
        pieceStyle = pieceStyle,
        subtitle = viewModel.difficulty.label,
        status = if (active && viewModel.isThinking) PlayerCardStatus.Thinking else null,
        isActive = active,
        materialAdvantage = getMaterialAdvantage(viewModel.gameState, color),
        capturedPieces = stepCaptured(viewModel, color)
    )
}

/** Kartica igraca: „Na potezu" (+ „· Šah"), kraj partije kao `Type.body`. */
@Composable
private fun StepPlayerCard(viewModel: GameViewModel, boardTheme: BoardTheme, pieceStyle: PieceStyle) {
    val color = viewModel.playerColor
    val active = stepActive(viewModel, color)
    val status: PlayerCardStatus? = when {
        viewModel.isGameOver -> PlayerCardStatus.Result(getStatusMessage(viewModel))
        !active -> null
        else -> PlayerCardStatus.ToMove(
            inCheck = (viewModel.gameState.status as? GameStatus.Check)?.color == color
        )
    }
    PlayerCard(
        color = color,
        name = loc("Ti"),
        boardTheme = boardTheme,
        pieceStyle = pieceStyle,
        status = status,
        isActive = active,
        materialAdvantage = getMaterialAdvantage(viewModel.gameState, color),
        capturedPieces = stepCaptured(viewModel, color)
    )
}

@Composable
private fun StepGameFooter(
    viewModel: GameViewModel,
    isGameOver: Boolean,
    onClose: () -> Unit,
    onAnalyze: () -> Unit
) {
    if (isGameOver) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(DS.success))
                Text(text = loc("Korak je završen"), color = DS.ink, fontWeight = FontWeight.SemiBold)
            }
            // Korak je vec upisan kao zavrsen (vidi komentar na mestu poziva) --
            // ovo dugme je cisto dodatna informacija, ne uslov za nastavak Puta.
            AnalysisButton(viewModel = viewModel, onClick = onAnalyze)
            Button(
                shape = ButtonShape, // spec § 3: Radius.m, nigde „stadion"
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DS.accent, contentColor = DS.onAccent)
            ) {
                Text(text = loc("Nazad na Put"), style = Type.body, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        Text(
            text = loc("Korak se završava kad partija dođe do kraja."),
            color = DS.inkMuted,
            // iOS: `.dsCaption` (`Chessko/Views/StepGameView.swift:290`).
            style = Type.caption,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
