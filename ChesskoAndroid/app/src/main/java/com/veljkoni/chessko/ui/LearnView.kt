package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veljkoni.chessko.models.*
import com.veljkoni.chessko.logic.SoundManager
import com.veljkoni.chessko.logic.HapticManager
import com.veljkoni.chessko.logic.ChessAI
import com.veljkoni.chessko.logic.MoveGenerator
import com.veljkoni.chessko.viewmodels.LearnScenario
import com.veljkoni.chessko.viewmodels.LearnViewModel
import com.veljkoni.chessko.ui.theme.DS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - Sta je ovaj fajl posle Faze 6c (Task 8)
//
// Do Task-a 5 ove faze ovaj fajl je bio i EKRAN (spisak lekcija, `LearnView`
// composable + `LessonCard`/`LessonInfo`) i skladiste deljenih komponenti za
// renderovanje sadrzaja lekcije. Otkad je treci tab postao Put (`PathView`),
// `LearnView(` se odnigde ne poziva — isto stanje u kom je iOS `LearnView.swift`
// u Fazi 4a OBRISAN u celosti.
//
// Android ovaj fajl NIJE obrisao: `LBox`/`LPara`/`LBullet`/`LSectionHeader`/
// `LNumberedRule`/`PieceExplorer`/`OpeningExerciseCard`/`MateExerciseCard`/
// `MatePuzzleCard`/`OpeningLine`/`OpeningPhase`/`OpeningExerciseState`/
// `MateExerciseState` su i dalje jedini nosioci renderovanja sadrzaja lekcije
// i `LessonRenderer.kt` ih zove direktno (isti paket, bez importa) — to je
// vecina sadrzaja ovog fajla. Preseljavanje bi značilo premestiti skoro citav
// fajl u `LessonRenderer.kt`/`LessonDetailView.kt` radi brisanja par stotina
// mrtvih linija; odluka je da OSTANE pod ovim imenom, uz brisanje SAMO onoga
// sto je stvarno mrtvo: `LearnView()` composable, `LessonCard()` i
// `LessonInfo` (spisak lekcija i njegova kartica — zamenio ih je Put), plus
// neiskoriscen `learnViewModel` u `MainActivity.kt`. `accentFor(id)` je bio u
// `LessonDetailView.kt` od Faze 6c, Task 5 — Faza 6d-2, Task 2 ga je OBRISAO u
// celosti (jedino pozivno mesto sada cita `DS.accent` direktno, vidi komentar
// iznad `LessonDetailView` u tom fajlu).

enum class OpeningPhase {
    PLAYING, WRONG_MOVE, SOLVED
}

data class OpeningLine(
    val name: String,
    val uciMoves: List<String>,
    val hint: String,
    val icon: String,
    val accentColor: Color,
    val solvedMessage: String = loc("Bravo! Otvaranje savladano! ✓"),
    val wrongMessage: String = loc("Pogrešan potez — pokušaj ponovo."),
    val playingPrompt: String? = null,
    val startFEN: String? = null
)

class OpeningExerciseState(val line: OpeningLine) {
    var gameState by mutableStateOf(line.startFEN?.let { GameState.fromFEN(it) } ?: GameState.initial())
    var selectedPosition by mutableStateOf<Position?>(null)
    var legalMovesForSelected by mutableStateOf<List<ChessMove>>(emptyList())
    var lastMove by mutableStateOf<ChessMove?>(null)
    var phase by mutableStateOf(OpeningPhase.PLAYING)
    var movePointer by mutableStateOf(0)

    val isPlayerTurn: Boolean
        get() = phase != OpeningPhase.SOLVED && gameState.currentTurn == PieceColor.WHITE

    val statusMessage: String
        get() = when (phase) {
            OpeningPhase.SOLVED -> line.solvedMessage
            OpeningPhase.WRONG_MOVE -> line.wrongMessage
            OpeningPhase.PLAYING -> {
                val moveNum = (movePointer / 2) + 1
                // Kljuc POSTOJI u `Loc.kt` na svih 8 jezika i postojao je i pre
                // ove faze — samo ga niko nije zvao, pa je vezba otvaranja na
                // svakom jeziku pisala srpski. Vidi se na svakoj vezbi bez
                // `playingPrompt` (sve tri u lekciji `openings`).
                locF("Potez %d — pronađi pravi potez za bele!", moveNum)
            }
        }

    fun tap(position: Position, soundManager: SoundManager, hapticManager: HapticManager, scope: kotlinx.coroutines.CoroutineScope) {
        if (!isPlayerTurn) return

        val selected = selectedPosition
        if (selected != null) {
            val move = legalMovesForSelected.firstOrNull { it.to == position }
            if (move != null) {
                attempt(move, soundManager, hapticManager, scope)
                return
            }
        }

        val piece = gameState.board[position.row][position.col]
        if (piece != null && piece.color == PieceColor.WHITE) {
            hapticManager.selection()
            selectedPosition = position
            legalMovesForSelected = MoveGenerator.legalMoves(PieceColor.WHITE, gameState)
                .filter { it.from == position }
            if (phase == OpeningPhase.WRONG_MOVE) phase = OpeningPhase.PLAYING
        } else {
            selectedPosition = null
            legalMovesForSelected = emptyList()
        }
    }

    private fun attempt(move: ChessMove, soundManager: SoundManager, hapticManager: HapticManager, scope: kotlinx.coroutines.CoroutineScope) {
        if (movePointer >= line.uciMoves.size) return
        val expected = ChessMove.fromUCI(line.uciMoves[movePointer], gameState) ?: return

        if (move.from != expected.from || move.to != expected.to) {
            hapticManager.warning()
            phase = OpeningPhase.WRONG_MOVE
            selectedPosition = null
            legalMovesForSelected = emptyList()
            return
        }

        hapticManager.mediumImpact()
        applyMove(move, soundManager)
        movePointer++

        if (movePointer >= line.uciMoves.size) {
            phase = OpeningPhase.SOLVED
            hapticManager.success()
            return
        }

        phase = OpeningPhase.PLAYING
        scope.launch {
            delay(500)
            playBlack(soundManager, hapticManager)
        }
    }

    private fun playBlack(soundManager: SoundManager, hapticManager: HapticManager) {
        if (movePointer >= line.uciMoves.size) return
        val move = ChessMove.fromUCI(line.uciMoves[movePointer], gameState) ?: return

        applyMove(move, soundManager)
        movePointer++

        if (movePointer >= line.uciMoves.size) {
            phase = OpeningPhase.SOLVED
            hapticManager.success()
        }
    }

    private fun applyMove(move: ChessMove, soundManager: SoundManager) {
        val captured = gameState.board[move.to.row][move.to.col] != null || move.flag is MoveFlag.EnPassant
        if (captured) {
            soundManager.playCapture()
        } else {
            soundManager.playMove()
        }
        gameState = gameState.applying(move)
        lastMove = move
        selectedPosition = null
        legalMovesForSelected = emptyList()
    }

    fun reset() {
        gameState = line.startFEN?.let { GameState.fromFEN(it) } ?: GameState.initial()
        selectedPosition = null
        legalMovesForSelected = emptyList()
        lastMove = null
        phase = OpeningPhase.PLAYING
        movePointer = 0
    }
}

class MateExerciseState(
    val fen: String,
    val title: String,
    val hint: String
) {
    var gameState by mutableStateOf(GameState.fromFEN(fen) ?: GameState.initial())
    var selectedPosition by mutableStateOf<Position?>(null)
    var legalMovesForSelected by mutableStateOf<List<ChessMove>>(emptyList())
    var lastMove by mutableStateOf<ChessMove?>(null)
    var isThinking by mutableStateOf(false)

    val isPlayerTurn: Boolean
        get() = gameState.currentTurn == PieceColor.WHITE && !isThinking && !isOver

    val isOver: Boolean
        get() = when (gameState.status) {
            is GameStatus.Checkmate, is GameStatus.Draw -> true
            else -> false
        }

    val isSolved: Boolean
        get() {
            val status = gameState.status
            return status is GameStatus.Checkmate && status.color == PieceColor.BLACK
        }

    val statusMessage: String
        get() = when (val status = gameState.status) {
            is GameStatus.Checkmate -> {
                if (status.color == PieceColor.BLACK) loc("Bravo! Mat! 🎉") else loc("Poraz — pokušaj ponovo.")
            }
            is GameStatus.Draw -> loc("Remi — pazi na pat! Pokušaj ponovo.")
            is GameStatus.Check -> {
                if (status.color == PieceColor.BLACK) loc("Šah! Nastavi...") else loc("Šah — moraš da se braniš!")
            }
            is GameStatus.Playing -> {
                if (isThinking) loc("Crni razmišlja...") else loc("Na potezu si!")
            }
            is GameStatus.Resigned -> loc("Predaja")
        }

    fun tap(position: Position, soundManager: SoundManager, hapticManager: HapticManager, scope: kotlinx.coroutines.CoroutineScope) {
        if (!isPlayerTurn) return

        val selected = selectedPosition
        if (selected != null) {
            val move = legalMovesForSelected.firstOrNull { it.to == position }
            if (move != null) {
                execute(move, soundManager, hapticManager, scope)
                return
            }
        }

        val piece = gameState.board[position.row][position.col]
        if (piece != null && piece.color == PieceColor.WHITE) {
            hapticManager.selection()
            selectedPosition = position
            legalMovesForSelected = MoveGenerator.legalMoves(PieceColor.WHITE, gameState)
                .filter { it.from == position }
        } else {
            selectedPosition = null
            legalMovesForSelected = emptyList()
        }
    }

    private fun execute(move: ChessMove, soundManager: SoundManager, hapticManager: HapticManager, scope: kotlinx.coroutines.CoroutineScope) {
        val captured = gameState.board[move.to.row][move.to.col] != null || move.flag is MoveFlag.EnPassant
        if (captured) {
            soundManager.playCapture()
            hapticManager.mediumImpact()
        } else {
            soundManager.playMove()
            hapticManager.lightImpact()
        }

        gameState = gameState.applying(move)
        lastMove = move
        selectedPosition = null
        legalMovesForSelected = emptyList()

        when (gameState.status) {
            is GameStatus.Checkmate -> hapticManager.success()
            is GameStatus.Draw -> hapticManager.warning()
            else -> triggerAI(soundManager, hapticManager, scope)
        }
    }

    private fun triggerAI(soundManager: SoundManager, hapticManager: HapticManager, scope: kotlinx.coroutines.CoroutineScope) {
        if (gameState.currentTurn != PieceColor.BLACK) return
        isThinking = true
        val snap = gameState

        scope.launch(Dispatchers.Default) {
            delay(450)
            val move = ChessAI(ChessAI.Difficulty.EASY).bestMove(PieceColor.BLACK, snap)

            withContext(Dispatchers.Main) {
                if (move != null) {
                    val captured = gameState.board[move.to.row][move.to.col] != null || move.flag is MoveFlag.EnPassant
                    if (captured) {
                        soundManager.playCapture()
                        hapticManager.mediumImpact()
                    } else {
                        soundManager.playMove()
                        hapticManager.lightImpact()
                    }
                    gameState = gameState.applying(move)
                    lastMove = move
                }
                isThinking = false
            }
        }
    }

    fun reset() {
        gameState = GameState.fromFEN(fen) ?: GameState.initial()
        selectedPosition = null
        legalMovesForSelected = emptyList()
        lastMove = null
        isThinking = false
    }
}

@Composable
fun LBox(icon: String, title: String, text: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = icon, fontSize = 14.sp)
            Text(text = title, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        // TELO kutije, ne sporedan tekst: zateceno `White@0,85`, iOS `L_Box` daje
        // `.primary.opacity(0.85)` (`Chessko/Views/LessonRenderer.swift:447`) — dakle
        // `ink`, ne `inkMuted`. Prvi prelaz je 0,85 preslikao naniže i telo je u svetloj
        // temi palo na 3,73:1 nad sopstvenim tintom (`ink` vraca 13,40–14,19, po stilu
        // kutije) — mereno u `ContrastTest.lessonBoxStylesMeetAA`.
        Text(text = mdBold(text), color = DS.ink, fontSize = 13.sp)
    }
}

@Composable
fun LPara(text: String) {
    Text(
        // Podebljanje iz JSON-a (`**ovako**`) — vidi `mdBold` u LessonRenderer.kt.
        text = mdBold(text),
        // Zateceno `White@0,8`; iOS `L_Para` je `.primary.opacity(0.85)`
        // (`Chessko/Views/LessonRenderer.swift:395`). Ovo je NOSECI tekst lekcije (30
        // blokova u isporucenom sadrzaju), ne podnaslov: `inkMuted` bi ga u svetloj temi
        // drzao na 4,36:1 nad `DS.ground`, `ink` daje 15,72.
        color = DS.ink,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun LBullet(icon: String, title: String, text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = DS.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = mdBold(text), color = DS.inkMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun LSectionHeader(icon: String, title: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(text = icon, fontSize = 18.sp)
        Text(text = title, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LNumberedRule(number: Int, title: String, text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number.toString(), color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = DS.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = mdBold(text), color = DS.inkMuted, fontSize = 13.sp)
        }
    }
}

// Istrazivac figura: birac figura + sandbox tabla + info kartica + scenario
// dugmad (rokada/en passant/promocija). Izdvojen iz `Lesson1Content` (Faza 6b,
// Task 3) da bi `LessonRenderer` mogao da ga pozove za JSON blok tipa
// `explorer`, BEZ izmene izgleda — isti pozivi, isti parametri
// (`BoardTheme.CLASSIC`, `PieceStyle.CLASSIC`), ista kartica.
// `accent` je izdvojen kao parametar (umesto `LessonInfo`, koji ovde ne
// postoji) jer `LessonRenderer.LessonBlocks` vec nosi svoj `accent: Color` za
// ceo blok niz — `info.accentColor` sa poziva iz `Lesson1Content` ostaje
// bit-za-bit ista vrednost.
@Composable
fun PieceExplorer(viewModel: LearnViewModel, accent: Color) {
    // Piece picker grid
    val piecesList = listOf(PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN, PieceType.KING)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DS.surface)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(piecesList) { piece ->
            val isSelected = viewModel.selectedPieceType == piece
            // Isti par kao birac TEME u `SettingsView.kt:506-507` i kategorije u
            // `ChessClockView.kt` (`if (isSelected) DS.accent else DS.fill` +
            // `DS.onAccent`/`DS.ink`) — NE providna bela preko tamne podloge.
            // Birac TEZINE nije isti par i namerno se ne pominje: `DifficultyOptionRow`
            // (`SettingsView.kt:690-721`) nosi izbor `DS.accent` TEKSTOM plus kvacicom,
            // bez akcent-ispune i bez `DS.onAccent` — tvrdnja „isto kao tezina" je
            // stajala ovde neproverena i bila je netacna.
            // Zamka istog oblika kao nalaz 2 iz 6d-1: `Color.White.copy(alpha=0.18f)`
            // je bilo svetlije od `0.04f` SAMO zato sto je podloga bila tamna; u
            // svetloj temi bi se `DS.fill` (svetliji od `DS.surface`) ponasao
            // suprotno. Akcent-cip resava to u oba pravca odjednom.
            val bg = if (isSelected) DS.accent else DS.fill
            val fg = if (isSelected) DS.onAccent else DS.ink
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { viewModel.select(piece) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val pieceAsset = getAssetName(ChessPiece(piece, PieceColor.WHITE), PieceStyle.CLASSIC, BoardTheme.CLASSIC)
                AsyncImage(
                    model = "file:///android_asset/pieces/$pieceAsset.svg",
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // `srbName` je bukvalno srpski string u modelu; kljucevi („Kralj",
                // „Dama"...) postoje u `Loc.kt` na svih 8 jezika. Bez `loc()` je
                // birac figura u lekciji pisao srpski i na engleskom UI-ju.
                Text(text = loc(piece.srbName), color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Sandboxed BoardView
    BoardView(
        board = viewModel.board,
        isFlipped = false,
        selectedPosition = viewModel.selectedPosition,
        legalMoves = viewModel.legalMoves,
        lastMove = null,
        boardTheme = BoardTheme.CLASSIC,
        pieceStyle = PieceStyle.CLASSIC,
        onTap = { viewModel.tap(it) }
    )

    // Display Scenario details or moves count
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DS.surface)
            .padding(12.dp)
    ) {
        Text(text = viewModel.infoTitle, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        // Tri nivoa, tri boje. `infoText` je zatecen na `White@0,8` a
        // `movesCountLabel` na `White@0,4`; prvi prelaz ih je oba poslao na
        // `inkMuted`, pa je hijerarhija nestala — „Najjaca figura…" i „27 mogucih
        // poteza" su se citali kao isti red. iOS ovde nema par za poredjenje
        // (`LessonPieceExplorer` u `Chessko/Views/LessonRenderer.swift` uopste nema
        // info panel), pa vazi pravilo raspona: 0,75–0,9 → `ink`, 0,4–0,7 → `inkMuted`.
        Text(text = viewModel.infoText, color = DS.ink, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = viewModel.movesCountLabel, color = DS.inkMuted, fontSize = 11.sp)
    }

    // Scenario selectors
    val scenarios = viewModel.availableScenarios
    if (scenarios.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scenarios.forEach { sc ->
                val active = viewModel.activeScenario == sc
                Button(
                    onClick = { viewModel.toggleScenario(sc) },
                    colors = ButtonDefaults.buttonColors(
                        // `DS.warning` (narandzasta) je ovde bio UKRAS — aktivno stanje
                        // dugmeta, ne upozorenje — pa ide na `DS.accent`/`DS.onAccent`,
                        // isti par kao selekcija u `SettingsView.kt`/`ChessClockView.kt`.
                        containerColor = if (active) DS.accent else DS.fill,
                        contentColor = if (active) DS.onAccent else DS.ink
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = sc.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Telo lekcija je od Faze 6b u `assets/lessons/*.json`; crta ga
// `LessonRenderer.LessonBlocks`. Ovde su nekad stajale `Lesson1Content`..
// `Lesson4Content` sa ~400 linija zakucanog SRPSKOG teksta koji se na svim
// ostalim jezicima video onakav kakav jeste. Komponente koje su one koristile
// (`LBox`, `LPara`, `LBullet`, `LSectionHeader`, `LNumberedRule`,
// `PieceExplorer`, `OpeningExerciseCard`, `MateExerciseCard`, `MatePuzzleCard`)
// NISU obrisane — renderer ih zove.

@Composable
fun OpeningExerciseCard(line: OpeningLine) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context.applicationContext as android.app.Application) }
    val hapticManager = remember { HapticManager(context.applicationContext as android.app.Application) }
    val scope = rememberCoroutineScope()
    val state = remember { OpeningExerciseState(line) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DS.surface)
            .border(1.dp, line.accentColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(line.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = line.icon, fontSize = 14.sp)
                }
                Column {
                    Text(text = line.name, color = DS.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = line.hint, color = DS.inkMuted, fontSize = 11.sp)
                }
            }
            if (state.phase == OpeningPhase.SOLVED) {
                Text(text = "✅", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chessboard
        BoardView(
            board = state.gameState.board,
            isFlipped = false,
            selectedPosition = state.selectedPosition,
            legalMoves = state.legalMovesForSelected,
            lastMove = state.lastMove,
            boardTheme = BoardTheme.CLASSIC,
            pieceStyle = PieceStyle.CLASSIC,
            onTap = { state.tap(it, soundManager, hapticManager, scope) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Progress + Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                // `playingPrompt` je uputstvo koje JSON pise uz vezbu („Find
                // mate in 1!"). `MatePuzzleCard` ga postuje, ali renderer zove
                // OVU karticu za sve `scripted` vezbe, a ona ga je ignorisala —
                // pa je 13 od 16 vezbi u isporucenom sadrzaju pisalo genericko
                // „Potez N — pronadji pravi potez za bele!" umesto svog teksta.
                text = if (state.phase == OpeningPhase.PLAYING && line.playingPrompt != null)
                    line.playingPrompt
                else
                    state.statusMessage,
                color = when (state.phase) {
                    OpeningPhase.SOLVED -> DS.success
                    OpeningPhase.WRONG_MOVE -> DS.danger
                    // IZUZETAK od pravila „zateceno 0,75–0,9 → `ink`": zateceno jeste
                    // bilo `White@0,8`, ali iOS `OpeningExerciseCard.statusColor` za
                    // `.playing` vraca `.secondary`
                    // (`Chessko/Views/LessonRenderer.swift:685`) — provereno, ne
                    // pretpostavljeno. Ovo je mirno stanje pored dva obojena; da je
                    // `ink`, „u toku" bi vikalo jace od „reseno".
                    else -> DS.inkMuted
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = { state.reset() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DS.fill,
                    contentColor = DS.ink
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(text = loc("Reset"), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun MateExerciseCard(
    fen: String,
    title: String,
    hint: String,
    icon: String,
    color: Color
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context.applicationContext as android.app.Application) }
    val hapticManager = remember { HapticManager(context.applicationContext as android.app.Application) }
    val scope = rememberCoroutineScope()
    val state = remember { MateExerciseState(fen, title, hint) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DS.surface)
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 14.sp)
                }
                Column {
                    Text(text = title, color = DS.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = hint, color = DS.inkMuted, fontSize = 11.sp)
                }
            }
            if (state.isSolved) {
                Text(text = "✅", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chessboard
        BoardView(
            board = state.gameState.board,
            isFlipped = false,
            selectedPosition = state.selectedPosition,
            legalMoves = state.legalMovesForSelected,
            lastMove = state.lastMove,
            boardTheme = BoardTheme.CLASSIC,
            pieceStyle = PieceStyle.CLASSIC,
            onTap = { state.tap(it, soundManager, hapticManager, scope) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Progress + Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = state.statusMessage,
                color = when {
                    state.isSolved -> DS.success
                    state.isOver && !state.isSolved -> DS.danger
                    // Isti izuzetak kao u `OpeningExerciseCard` iznad: zateceno
                    // `White@0,8`, ali iOS `MateExerciseCard.statusColor` za `.playing`
                    // vraca `.secondary` (`Chessko/Views/LessonRenderer.swift:809`).
                    else -> DS.inkMuted
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = { state.reset() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DS.fill,
                    contentColor = DS.ink
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(text = loc("Reset"), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun MatePuzzleCard(
    fen: String,
    moves: List<String>,
    title: String,
    hint: String,
    icon: String,
    accentColor: Color,
    mateIn: Int,
    // Poruke iz JSON-a. `null` = koristi podrazumevanu.
    //
    // Bez ovih parametara `solvedMessage`/`wrongMessage`/`playingPrompt` iz
    // sadrzaja NEMAJU EFEKTA za mat-zadatke, jer ih je kartica sama sklapala.
    // Danas se razlika ne bi videla — vrednosti u JSON-u se slucajno poklapaju
    // sa podrazumevanima — ali bi pao ugovor cele faze: „sadrzaj je u JSON-u,
    // izmena JSON-a menja aplikaciju". iOS ih prosledjuje
    // (`LessonRenderer.swift:106-111`), pa bi Android tiho odstupao.
    solvedMessage: String? = null,
    wrongMessage: String? = null,
    playingPrompt: String? = null
) {
    val line = remember(fen, moves, title, hint, icon, accentColor,
                        solvedMessage, wrongMessage, playingPrompt) {
        OpeningLine(
            name = title,
            uciMoves = moves,
            hint = hint,
            icon = icon,
            accentColor = accentColor,
            solvedMessage = solvedMessage ?: loc("Sjajno! Mat pronađen! 🏆"),
            wrongMessage = wrongMessage ?: loc("Nije to — traži pravi ključni potez!"),
            playingPrompt = playingPrompt
                ?: if (mateIn == 1) loc("Pronađi mat u 1 potezu!") else loc("Pronađi ključni potez!"),
            startFEN = fen
        )
    }

    val context = LocalContext.current
    val soundManager = remember { SoundManager(context.applicationContext as android.app.Application) }
    val hapticManager = remember { HapticManager(context.applicationContext as android.app.Application) }
    val scope = rememberCoroutineScope()
    val state = remember(line) { OpeningExerciseState(line) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DS.surface)
            .border(
                width = 1.5.dp,
                color = when (state.phase) {
                    // Bila je narandzasta (`#F59E0B`) dok ISTA kartica dva reda ispod
                    // (status traka) i sestarske kartice (`OpeningExerciseCard`,
                    // `MateExerciseCard`) koriste zelenu za RESENO — jedina
                    // nedoslednost boje u ovom fajlu. iOS `MatePuzzleCard`
                    // (`Chessko/Views/LessonRenderer.swift:626,691`) potvrdjeno koristi
                    // `DS.success` i za trofej i za ivicu kad je `.solved`; poravnato.
                    OpeningPhase.SOLVED -> DS.success.copy(alpha = 0.6f)
                    OpeningPhase.WRONG_MOVE -> DS.danger.copy(alpha = 0.5f)
                    OpeningPhase.PLAYING -> accentColor.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            color = DS.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = locF("Mat u %d", mateIn),
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(accentColor.copy(alpha = 0.18f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(text = hint, color = DS.inkMuted, fontSize = 11.sp)
                }
            }
            if (state.phase == OpeningPhase.SOLVED) {
                Text(text = "🏆", fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chessboard
        BoardView(
            board = state.gameState.board,
            isFlipped = false,
            selectedPosition = state.selectedPosition,
            legalMoves = state.legalMovesForSelected,
            lastMove = state.lastMove,
            boardTheme = BoardTheme.CLASSIC,
            pieceStyle = PieceStyle.CLASSIC,
            onTap = { state.tap(it, soundManager, hapticManager, scope) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Status bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (state.phase == OpeningPhase.PLAYING && line.playingPrompt != null) line.playingPrompt else state.statusMessage,
                color = when (state.phase) {
                    // Ista odluka kao ivica iznad — poravnato sa iOS-om, `DS.success`
                    // umesto narandzaste, umesto necitljive zelena/narandzasta podele
                    // izmedju ove kartice i sestarskih.
                    OpeningPhase.SOLVED -> DS.success
                    OpeningPhase.WRONG_MOVE -> DS.danger
                    OpeningPhase.PLAYING -> DS.inkMuted
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = { state.reset() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DS.fill,
                    contentColor = DS.ink
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(text = loc("Ponovo"), fontSize = 11.sp)
            }
        }
    }
}
