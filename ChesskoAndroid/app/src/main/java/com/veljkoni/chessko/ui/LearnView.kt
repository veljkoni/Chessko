package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veljkoni.chessko.models.*
import com.veljkoni.chessko.logic.SoundManager
import com.veljkoni.chessko.logic.HapticManager
import com.veljkoni.chessko.logic.ChessAI
import com.veljkoni.chessko.logic.MoveGenerator
import com.veljkoni.chessko.viewmodels.LearnScenario
import com.veljkoni.chessko.viewmodels.LearnViewModel
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.ui.theme.dsCard
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
    val icon: LessonGlyph,
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

/**
 * Kutija (pravilo, upozorenje, info, citat) — spec § 9: boja kutije 10 % preko
 * `surface` (ne preko `ground`), `Radius.m`, BEZ ivice; naslov `Type.body` bold
 * `ink` (bio 12 u boji kutije), ikona u boji kutije (pravilo: `warning`
 * 4,99 / 7,05), telo `ink`.
 *
 * Tekst sa „ · " razdvojenim uslovima (kutija „Uslovi za rokadu", svih 8 jezika)
 * crta se kao lista sa tackama — spec § 9 „Uslovi (rokada) kao lista sa tackama".
 * Sadrzaj (JSON) se ne menja; renderer cita isti separator na obe platforme.
 */
@Composable
fun LBox(icon: LessonGlyph, title: String, text: String, color: Color) {
    val items = text.split(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.Radius.m))
            .background(DS.surface)
            .background(color.copy(alpha = 0.1f))
            .padding(DS.Space.m)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
        ) {
            LessonGlyphView(icon, fontSize = Type.body.fontSize, tint = color)
            Text(text = title, color = DS.ink, style = Type.body, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(DS.Space.s))
        if (items.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
                for (item in items) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DS.Space.s)) {
                        Text(text = "•", color = DS.ink, style = LessonBodyStyle)
                        Text(text = mdBold(item.trim()), color = DS.ink, style = LessonBodyStyle, modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            Text(text = mdBold(text), color = DS.ink, style = LessonBodyStyle)
        }
    }
}

@Composable
fun LPara(text: String) {
    Text(
        // Podebljanje iz JSON-a (`**ovako**`) — vidi `mdBold` u LessonRenderer.kt.
        text = mdBold(text),
        // Telo lekcije (spec § 9): `Type.body` 15, prored 1,5, `ink` (15,72 / 16,15).
        color = DS.ink,
        style = LessonBodyStyle,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Stavka nabrajanja: naslov je POD-BLOK (spec § 9 „Kretanje": `Type.body` medium
 * `ink`), telo je telo lekcije (`ink`, ne `inkMuted` — dugi tekst, spec § 3).
 * Ikona: neutralna `inkMuted`, a pravilo/upozorenje nose svoju boju.
 */
@Composable
fun LBullet(icon: LessonGlyph, title: String, text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m),
        verticalAlignment = Alignment.Top
    ) {
        LessonGlyphView(icon, fontSize = Type.body.fontSize, tint = color, modifier = Modifier.padding(top = 3.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = DS.ink, style = Type.body, fontWeight = FontWeight.Medium)
            Text(text = mdBold(text), color = DS.ink, style = LessonBodyStyle)
        }
    }
}

/**
 * Naslov sekcije (spec § 9): `Type.heading` 17 bold `ink` + ikona/glif `inkMuted`
 * (5,28 / 5,94). NIJE `accent` — u akcentu su naslovi izgledali kao linkovi.
 */
@Composable
fun LSectionHeader(icon: LessonGlyph, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
    ) {
        LessonGlyphView(icon, fontSize = Type.heading.fontSize, tint = DS.inkMuted)
        Text(text = title, color = DS.ink, style = Type.heading, fontWeight = FontWeight.Bold)
    }
}

/** Numerisano pravilo: broj na `fill` plocici u `ink` (statican broj nije `accent`, spec § 3). */
@Composable
fun LNumberedRule(number: Int, title: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(DS.Radius.s))
                .background(DS.fill),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number.toString(), color = DS.ink, style = Type.body, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = DS.ink, style = Type.body, fontWeight = FontWeight.Medium)
            Text(text = mdBold(text), color = DS.ink, style = LessonBodyStyle)
        }
    }
}

// Istrazivac figura: birac figura + sandbox tabla + info kartica + scenario
// dugmad (rokada/en passant/promocija).
//
// Spec § 9 „Cipovi figura": izabrani `accent` + `onAccent` (8,53 / 6,71), ostali
// `fill` + `ink` (14,47 / 13,08). Kartica cipova je obicna kartica (`surface` +
// `line`) sa `Space.m` unutra — ranije `LazyVerticalGrid` sa zakucanom visinom
// 110 dp, koja je ostavljala prazno dno.
@Composable
fun PieceExplorer(viewModel: LearnViewModel) {
    val piecesList = listOf(PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN, PieceType.KING)
    Column(verticalArrangement = Arrangement.spacedBy(DS.Space.m)) {
        Column(
            modifier = Modifier.fillMaxWidth().dsCard().padding(DS.Space.m),
            verticalArrangement = Arrangement.spacedBy(DS.Space.s)
        ) {
            for (rowPieces in piecesList.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(DS.Space.s)) {
                    for (piece in rowPieces) {
                        val isSelected = viewModel.selectedPieceType == piece
                        val bg = if (isSelected) DS.accent else DS.fill
                        val fg = if (isSelected) DS.onAccent else DS.ink
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(DS.Radius.s))
                                .background(bg)
                                .clickable { viewModel.select(piece) }
                                .padding(vertical = DS.Space.s, horizontal = DS.Space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val pieceAsset = getAssetName(ChessPiece(piece, PieceColor.WHITE), PieceStyle.CLASSIC)
                            AsyncImage(
                                model = "file:///android_asset/pieces/$pieceAsset.svg",
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // `srbName` je bukvalno srpski string u modelu; kljucevi
                            // postoje u `Loc.kt` na svih 8 jezika.
                            Text(text = loc(piece.srbName), color = fg, style = Type.caption, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BoardView(
                board = viewModel.board,
                isFlipped = false,
                selectedPosition = viewModel.selectedPosition,
                legalMoves = viewModel.legalMoves,
                lastMove = null,
                boardTheme = BoardTheme.CLASSIC,
                pieceStyle = PieceStyle.CLASSIC,
                // Lekcijska tabla ne menja globalno podešavanje — isti izuzetak i
                // istih pet mesta kao iOS (`allowsStyleSwipe: false`).
                allowsStyleSwipe = false,
                onTap = { viewModel.tap(it) },
                modifier = Modifier.widthIn(max = LocalLessonBoardMaxSide.current).fillMaxWidth()
            )
        }

        // Info kartica: naslov `ink` bold (statican tekst nije `accent`, spec § 3),
        // opis `ink`, brojac poteza meta `inkMuted`.
        Column(
            modifier = Modifier.fillMaxWidth().dsCard().padding(DS.Space.m)
        ) {
            Text(text = viewModel.infoTitle, color = DS.ink, style = Type.body, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(DS.Space.xs))
            Text(text = viewModel.infoText, color = DS.ink, style = Type.body)
            Spacer(modifier = Modifier.height(DS.Space.s))
            Text(text = viewModel.movesCountLabel, color = DS.inkMuted, style = Type.caption)
        }

        val scenarios = viewModel.availableScenarios
        if (scenarios.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
            ) {
                scenarios.forEach { sc ->
                    val active = viewModel.activeScenario == sc
                    Button(
                        onClick = { viewModel.toggleScenario(sc) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) DS.accent else DS.fill,
                            contentColor = if (active) DS.onAccent else DS.ink
                        ),
                        shape = RoundedCornerShape(DS.Radius.s),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = sc.label, style = Type.caption, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

// Telo lekcija je od Faze 6b u `assets/lessons/*.json`; crta ga
// `LessonRenderer.LessonBlocks`.

// ── Kartice vezbi (spec § 9, § 3) ────────────────────────────────────────────
//
// Kartica = `surface` + 1 dp ivica, `Radius.m`, `Space.m` unutra. Ivica je
// `line` dok se igra, `success`/`danger` posle resenja/greske (stanje, ne ukras).
// Plocica ikone `fill` + `ink` glif (bila `accent` tinta — accent je samo za ono
// sto se tapa). Status „u toku" (npr. „Na potezu si!") je `ink` (spec § 9).

@Composable
private fun Modifier.exerciseCard(border: Color): Modifier {
    val shape = RoundedCornerShape(DS.Radius.m)
    return this
        .fillMaxWidth()
        .clip(shape)
        .background(DS.surface)
        .border(1.dp, border, shape)
        .padding(DS.Space.m)
}

@Composable
private fun ExerciseHeader(
    icon: LessonGlyph,
    title: String,
    hint: String,
    solved: Boolean,
    badge: String? = null,
    solvedIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.CheckCircle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(DS.Radius.s))
                .background(DS.fill),
            contentAlignment = Alignment.Center
        ) {
            LessonGlyphView(icon, fontSize = Type.body.fontSize, tint = DS.ink)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    color = DS.ink,
                    style = Type.body,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badge != null) {
                    // Oznaka „Mat u N": meta cip `fill` + `ink` (ne `accent`).
                    Text(
                        text = badge,
                        color = DS.ink,
                        style = Type.label,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(DS.Radius.s))
                            .background(DS.fill)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(text = hint, color = DS.inkMuted, style = Type.caption)
        }
        // Bedz resenosti stoji SAM na kraju reda (status tekst je odvojen celom
        // tablom), pa nosi opis — nije dekorativan (Faza 8, Task 4).
        if (solved) {
            Icon(
                imageVector = solvedIcon,
                contentDescription = loc("Rešeno"),
                tint = DS.success,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Lekcijska tabla u kartici: granica iz `LocalLessonBoardMaxSide` (spec § 13), centrirana. */
@Composable
private fun exerciseBoardModifier(): Modifier = Modifier
    .widthIn(max = LocalLessonBoardMaxSide.current)
    .fillMaxWidth()
    .aspectRatio(1f)
    .clip(RoundedCornerShape(DS.Radius.s))

@Composable
private fun ExerciseFooter(status: String, statusColor: Color, onReset: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
    ) {
        Text(
            text = status,
            color = statusColor,
            style = Type.caption,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(containerColor = DS.fill, contentColor = DS.ink),
            contentPadding = PaddingValues(horizontal = DS.Space.m, vertical = DS.Space.xs),
            shape = RoundedCornerShape(DS.Radius.s)
        ) {
            // iOS na sve tri kartice pise „Ponovo"; Android je na dve pisao „Reset".
            Text(text = loc("Ponovo"), style = Type.caption)
        }
    }
}

@Composable
fun OpeningExerciseCard(line: OpeningLine) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context.applicationContext as android.app.Application) }
    val hapticManager = remember { HapticManager(context.applicationContext as android.app.Application) }
    val scope = rememberCoroutineScope()
    val state = remember { OpeningExerciseState(line) }

    val border = when (state.phase) {
        OpeningPhase.SOLVED -> DS.success
        OpeningPhase.WRONG_MOVE -> DS.danger
        OpeningPhase.PLAYING -> DS.line
    }
    Column(
        modifier = Modifier.exerciseCard(border),
        verticalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        ExerciseHeader(line.icon, line.name, line.hint, solved = state.phase == OpeningPhase.SOLVED)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BoardView(
                board = state.gameState.board,
                isFlipped = false,
                selectedPosition = state.selectedPosition,
                legalMoves = state.legalMovesForSelected,
                lastMove = state.lastMove,
                boardTheme = BoardTheme.CLASSIC,
                pieceStyle = PieceStyle.CLASSIC,
                // Lekcijska tabla ne menja globalno podešavanje — isti izuzetak i
                // istih pet mesta kao iOS (`allowsStyleSwipe: false`).
                allowsStyleSwipe = false,
                onTap = { state.tap(it, soundManager, hapticManager, scope) },
                modifier = exerciseBoardModifier()
            )
        }
        ExerciseFooter(
            // `playingPrompt` je uputstvo koje JSON pise uz vezbu; bez njega
            // genericko „Potez N — pronadji pravi potez za bele!".
            status = if (state.phase == OpeningPhase.PLAYING && line.playingPrompt != null)
                line.playingPrompt
            else
                state.statusMessage,
            statusColor = when (state.phase) {
                OpeningPhase.SOLVED -> DS.success
                OpeningPhase.WRONG_MOVE -> DS.danger
                OpeningPhase.PLAYING -> DS.ink
            },
            onReset = { state.reset() }
        )
    }
}

@Composable
fun MateExerciseCard(
    fen: String,
    title: String,
    hint: String,
    icon: LessonGlyph
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context.applicationContext as android.app.Application) }
    val hapticManager = remember { HapticManager(context.applicationContext as android.app.Application) }
    val scope = rememberCoroutineScope()
    val state = remember { MateExerciseState(fen, title, hint) }

    val border = when {
        state.isSolved -> DS.success
        state.isOver -> DS.danger
        else -> DS.line
    }
    Column(
        modifier = Modifier.exerciseCard(border),
        verticalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        ExerciseHeader(icon, title, hint, solved = state.isSolved)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BoardView(
                board = state.gameState.board,
                isFlipped = false,
                selectedPosition = state.selectedPosition,
                legalMoves = state.legalMovesForSelected,
                lastMove = state.lastMove,
                boardTheme = BoardTheme.CLASSIC,
                pieceStyle = PieceStyle.CLASSIC,
                // Lekcijska tabla ne menja globalno podešavanje — isti izuzetak i
                // istih pet mesta kao iOS (`allowsStyleSwipe: false`).
                allowsStyleSwipe = false,
                onTap = { state.tap(it, soundManager, hapticManager, scope) },
                modifier = exerciseBoardModifier()
            )
        }
        ExerciseFooter(
            status = state.statusMessage,
            statusColor = when {
                state.isSolved -> DS.success
                state.isOver -> DS.danger
                // „Na potezu si!" je `ink` (spec § 9).
                else -> DS.ink
            },
            onReset = { state.reset() }
        )
    }
}

@Composable
fun MatePuzzleCard(
    fen: String,
    moves: List<String>,
    title: String,
    hint: String,
    icon: LessonGlyph,
    mateIn: Int,
    // Poruke iz JSON-a. `null` = koristi podrazumevanu. Bez ovih parametara
    // izmena sadrzaja ne bi imala efekta za mat-zadatke (iOS ih prosledjuje).
    solvedMessage: String? = null,
    wrongMessage: String? = null,
    playingPrompt: String? = null
) {
    val line = remember(fen, moves, title, hint, icon, solvedMessage, wrongMessage, playingPrompt) {
        OpeningLine(
            name = title,
            uciMoves = moves,
            hint = hint,
            icon = icon,
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

    val border = when (state.phase) {
        OpeningPhase.SOLVED -> DS.success
        OpeningPhase.WRONG_MOVE -> DS.danger
        OpeningPhase.PLAYING -> DS.line
    }
    Column(
        modifier = Modifier.exerciseCard(border),
        verticalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        ExerciseHeader(
            icon, title, hint,
            solved = state.phase == OpeningPhase.SOLVED,
            badge = locF("Mat u %d", mateIn),
            solvedIcon = Icons.Default.EmojiEvents
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BoardView(
                board = state.gameState.board,
                isFlipped = false,
                selectedPosition = state.selectedPosition,
                legalMoves = state.legalMovesForSelected,
                lastMove = state.lastMove,
                boardTheme = BoardTheme.CLASSIC,
                pieceStyle = PieceStyle.CLASSIC,
                // Lekcijska tabla ne menja globalno podešavanje — isti izuzetak i
                // istih pet mesta kao iOS (`allowsStyleSwipe: false`).
                allowsStyleSwipe = false,
                onTap = { state.tap(it, soundManager, hapticManager, scope) },
                modifier = exerciseBoardModifier()
            )
        }
        ExerciseFooter(
            status = if (state.phase == OpeningPhase.PLAYING && line.playingPrompt != null) line.playingPrompt else state.statusMessage,
            statusColor = when (state.phase) {
                OpeningPhase.SOLVED -> DS.success
                OpeningPhase.WRONG_MOVE -> DS.danger
                OpeningPhase.PLAYING -> DS.ink
            },
            onReset = { state.reset() }
        )
    }
}
