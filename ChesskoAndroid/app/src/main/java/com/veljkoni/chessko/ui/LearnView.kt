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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LessonInfo(
    val id: Int,
    val title: String,
    val subtitle: String,
    val icon: String,
    val accentColor: Color
) {
    companion object {
        val all = listOf(
            LessonInfo(1, loc("Tabla, figure i kretanje"), loc("Osnove šaha za početnike"), "♟️", Color(0xFF3B82F6)),
            LessonInfo(2, loc("Početak igre (Otvaranja)"), loc("Zlatna pravila i poznata otvaranja"), "🏁", Color(0xFF10B981)),
            LessonInfo(3, loc("Središnjica"), loc("Taktika i srce bitke"), "⚡", Color(0xFFF59E0B)),
            LessonInfo(4, loc("Završnica"), loc("Šah-mat, pat i remi"), "🏆", Color(0xFFEF4444))
        )
    }
}

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
                "Potez $moveNum — pronađi pravi potez za bele!"
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
                if (status.color == PieceColor.BLACK) loc("Šah! Nastavi...") else "Šah — moraš da se braniš!"
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
fun LearnView(
    viewModel: LearnViewModel,
    modifier: Modifier = Modifier
) {
    var activeLessonId by remember { mutableStateOf<Int?>(null) }

    val activeLesson = activeLessonId?.let { id -> LessonInfo.all.find { it.id == id } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (activeLesson == null) {
            // Main list of lessons
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = loc("Nauči šah"),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = loc("4 lekcije od osnova do završnice"),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }

                // Lesson Cards list
                LessonInfo.all.forEach { lesson ->
                    LessonCard(
                        info = lesson,
                        onClick = { activeLessonId = lesson.id }
                    )
                }
            }
        } else {
            // Detailed lesson view
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Back Button Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { activeLessonId = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "◀ Lekcije", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Lesson Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Lesson Main Title Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(activeLesson.accentColor.copy(alpha = 0.12f))
                            .border(1.dp, activeLesson.accentColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(activeLesson.accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = activeLesson.icon, fontSize = 24.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Lekcija ${activeLesson.id}",
                                color = activeLesson.accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeLesson.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeLesson.subtitle,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Render Lesson body depending on ID
                    when (activeLesson.id) {
                        1 -> Lesson1Content(viewModel, activeLesson)
                        2 -> Lesson2Content(activeLesson)
                        3 -> Lesson3Content(activeLesson)
                        4 -> Lesson4Content(activeLesson)
                    }
                }
            }
        }
    }
}

@Composable
fun LessonCard(info: LessonInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, info.accentColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(info.accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = info.icon, fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Lekcija ${info.id}",
                color = info.accentColor.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = info.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = info.subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        Text(text = "▶", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
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
        Text(text = text, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
    }
}

@Composable
fun LPara(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.8f),
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
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = text, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
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
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = text, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

// Interactive Lesson 1 details
@Composable
fun Lesson1Content(viewModel: LearnViewModel, info: LessonInfo) {
    LBox(
        icon = "💬",
        title = loc("Kapablanka piše"),
        text = loc("\"Prva stvar koju učenik treba da uradi jeste da upozna snagu figura. Ovo se najlakše postiže učenjem kako se brzo postiže šah-mat.\""),
        color = info.accentColor
    )

    LPara("Šah se igra na tabli od 64 polja naizmenično svetle i tamne boje. Uvek zapamti: donje desno polje mora biti svetlo. Svaki igrač počinje sa 16 figura.")

    LSectionHeader("📱", loc("Istraži figure interaktivno"), info.accentColor)

    // Piece picker grid
    val piecesList = listOf(PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN, PieceType.KING)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(piecesList) { piece ->
            val isSelected = viewModel.selectedPieceType == piece
            val bg = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f)
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
                Text(text = piece.srbName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp)
    ) {
        Text(text = viewModel.infoTitle, color = info.accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = viewModel.infoText, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = viewModel.movesCountLabel, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
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
                        containerColor = if (active) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.08f),
                        contentColor = if (active) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = sc.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Static piece details
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("ℹ️", "Opis figura i kretanje", info.accentColor)

    LBullet("♟️", "Pion (Pešak) · x 8", "Ide isključivo napred po jedno polje. Na prvom potezu može skočiti dva polja. Jede isključivo dijagonalno napred.", info.accentColor)
    LBullet("🏰", "Top (Kula) · x 2", "Kreće se po pravim linijama (vodoravno i uspravno) koliko god polja želi. Ne može da preskače druge figure.", info.accentColor)
    LBullet("📐", "Lovac · x 2", "Kreće se isključivo dijagonalno. Jedan uvek ostaje na belim, a drugi na crnim poljima partije.", info.accentColor)
    LBullet("🐴", "Skakač (Konj) · x 2", "Kreće se u obliku slova 'L' (2+1 polje). Jedina figura koja može preskakati druge figure na tabli.", info.accentColor)
    LBullet("👑", "Dama (Kraljica) · x 1", "Najjača figura. Kombinuje kretanje topa i lovca u svim pravcima bez limita u poljima.", info.accentColor)
    LBullet("🛡️", "Kralj · x 1", "Najvažnija figura čiji pad završava partiju. Kreće se jedno polje u svim pravcima. Ne sme stati na napadnuto polje.", info.accentColor)

    // Checkmate Exercises at bottom of Lesson 1
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("🧩", "Zadaci — Vežbanje Mata", info.accentColor)
    LPara("Završi ove jednostavne matne vežbe protiv računara. Na potezu si!")

    MateExerciseCard(
        fen = "8/8/4k3/8/4K3/8/8/R7 w - - 0 1",
        title = loc("Vežba 1 — Kralj + Top"),
        hint = loc("Oteraj crnog Kralja na ivicu table. Top i Kralj moraju da sarađuju!"),
        icon = "🏰",
        color = Color(0xFF3B82F6)
    )

    MateExerciseCard(
        fen = "4k3/8/8/8/8/8/8/2B1KB2 w - - 0 1",
        title = loc("Vežba 2 — Kralj + dva Lovca"),
        hint = loc("Oteraj Kralja ne samo na ivicu već i u ugao iste boje kao tvoji lovci."),
        icon = "📐",
        color = Color(0xFF8B5CF6)
    )

    MateExerciseCard(
        fen = "4k3/8/8/8/8/8/8/3QK3 w - - 0 1",
        title = loc("Vežba 3 — Kralj + Dama"),
        hint = loc("Najlakše! Dama odmah sužava prostor. Pazi na pat!"),
        icon = "👑",
        color = Color(0xFFF59E0B)
    )
}

// Lesson 2 detail text layout
@Composable
fun Lesson2Content(info: LessonInfo) {
    LBox(
        icon = "💬",
        title = loc("Kapablanka piše"),
        text = loc("\"Najvažnija stvar u otvaranju je brzo razviti figure. Nijedno parče ne treba pomeriti više od jednom pre nego što je razvoj završen, osim ako je to apsolutno neophodno.\""),
        color = info.accentColor
    )

    LPara("U šahu Beli uvek igra prvi i zbog toga ima blagu inicijalnu prednost. Zadatak oba igrača u otvaranju je isti: što brže dovesti figure u igru i zauzeti kontrolu nad centrom.")

    LSectionHeader("✨", loc("Zlatna pravila otvaranja"), info.accentColor)
    LNumberedRule(1, loc("Razvijaj figure brzo"), "Skakače razvijaj pre lovaca. Ne pomeraj istu figuru dva puta u otvaranju ako nisi primoran. Svaki potez treba da razvija novu figuru.", info.accentColor)
    LNumberedRule(2, loc("Kontroliši centar"), "Četiri centralna polja (e4, d4, e5, d5) su ključ za pobedu. Ko vlada centrom, ima prostor za manevar i slobodan plasman figura.", info.accentColor)
    LNumberedRule(3, "Zaštiti kralja — rokada!", "Uradi rokadu što pre. Kralj u centru je laka meta na otvorenim linijama. Rokada donosi bezbednost kralju i aktivira topa.", info.accentColor)

    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("⚠️", loc("Tipične greške u otvaranju"), info.accentColor)
    LBullet("❌", loc("Prerano izvođenje Dame"), "Dama je jaka, ali ako izađe rano, protivnik je napada lakim figurama i pešacima razvijajući se sa tempom prednosti.", Color.Red)
    LBullet("❌", loc("Pasivna odbrana pionima"), "Previše odbrambenih poteza pešacima zatvara tvoje figure i daje protivniku slobodnu inicijativu u centru.", Color.Red)
    LBullet("❌", loc("Zakasnela rokada"), "Čuvanje kralja u centru kada su linije otvorene je rizik koji najčešće vodi do brzog šaha i gubitka materijala.", Color.Red)

    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("📖", "Vežbanje poznatih otvaranja", info.accentColor)
    LPara(loc("Odigraj svaki potez belih na tabli — crni odgovara automatski po teorijskoj liniji."))

    OpeningExerciseCard(
        line = OpeningLine(
            name = loc("Španska partija (Ruy Lopez)"),
            uciMoves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5"),
            hint = loc("1.e4 e5 2.Sf3 Sc6 3.Lb5 — Kapablankova omiljena"),
            icon = "👑",
            accentColor = info.accentColor
        )
    )

    OpeningExerciseCard(
        line = OpeningLine(
            name = loc("Italijanska partija"),
            uciMoves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4"),
            hint = loc("1.e4 e5 2.Sf3 Sc6 3.Lc4 — lovac nišani tačku f7"),
            icon = "🔥",
            accentColor = Color(0xFFF59E0B)
        )
    )

    OpeningExerciseCard(
        line = OpeningLine(
            name = loc("Sicilijanska odbrana"),
            uciMoves = listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4"),
            hint = loc("1.e4 c5 2.Sf3 d6 3.d4 cxd4 4.Sxd4 — asimetrična borba"),
            icon = "🛡️",
            accentColor = Color(0xFF8B5CF6)
        )
    )
}

// Lesson 3 detail text layout
@Composable
fun Lesson3Content(info: LessonInfo) {
    LBox(
        icon = "💬",
        title = loc("Kapablanka piše"),
        text = loc("\"Idealna središnjica: sve figure su bačene u napad kao masa, koordinirajući se sa mašinskom preciznošću. Cilj svakog majstora je da postigne upravo takvu harmoniju.\""),
        color = info.accentColor
    )

    LPara("Kada su figure razvijene i kraljevi sigurni, počinje središnjica — najkreativniji deo partije gde se grade planovi i sprovodi taktika.")

    LSectionHeader("🚩", loc("Inicijativa"), info.accentColor)
    LPara("Kapablanka objašnjava: Beli ima inicijativu zbog prvog poteza. Igrač sa inicijativom diktira tempo igre, dok protivnik mora da se brani. Inicijativu treba pažljivo čuvati i razvijati.")

    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("⚖️", "Vrednosti figura (Materijal)", info.accentColor)
    LPara("Tabela relativnih vrednosti figura u pešacima:")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            "♟️ Pion (Pešak)" to "1 poen",
            "🐴 Skakač (Konj)" to "3 poena",
            "📐 Lovac" to "3 poena",
            "🏰 Top (Kula)" to "5 poena",
            "👑 Dama" to "9 poena",
            "🛡️ Kralj" to "Beskonačno (kraj igre)"
        ).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = pair.first, color = Color.White, fontSize = 13.sp)
                Text(text = pair.second, color = info.accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("⚡", loc("Osnovni taktički motivi"), info.accentColor)
    LBox("🍴", loc("Viljuška (Rašlje)"), "Kada jedna tvoja figura napadne dve ili više protivničkih istovremeno. Skakači i pioni su idealni za ovaj motiv.", info.accentColor)
    LBox("🔗", loc("Vezivanje (Pin)"), "Kada napadneš figuru koja se ne sme pomeriti jer bi time otkrila vredniju figuru iza sebe (Kralja ili Damu).", info.accentColor)
    LBox("🔄", loc("Otkriveni napad"), "Kada pomeriš jednu figuru i time otvoriš liniju napada za drugu figuru koja stoji iza nje.", info.accentColor)
}

// Lesson 4 detail text layout
@Composable
fun Lesson4Content(info: LessonInfo) {
    LBox(
        icon = "💬",
        title = loc("Kapablanka piše"),
        text = loc("\"Pre nego što se boriš za pobedu u otvaranju ili središnjici, moraš savladati završnicu. Onaj ko ne poznaje završnicu ne može biti jak šahista.\""),
        color = info.accentColor
    )

    LPara(loc("Završnica počinje kada su sa table nestale najvažnije figure i ostanu Kraljevi sa pešacima i možda jednom-dve lake figure."))

    LSectionHeader("👑", loc("Kralj postaje napadač"), info.accentColor)
    LPara("Ovo je najveća promena u završnici. Kralj koji je celu partiju bežao sada mora aktivno da napada.")

    LBullet("👑", loc("Dovedi Kralja u centar odmah"), "Čim osetiš da je završnica blizu, počni da pomičeš Kralja ka centru table. Centralni Kralj dominira nad marginalnim.", info.accentColor)
    LBullet("⛃", loc("Pioni su budući Kraljevi"), loc("Svaki pion koji stigne do poslednjeg reda postaje Dama (ili druga figura). Ovo je glavni cilj u pešačkim završnicama."), info.accentColor)

    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("⛃", loc("Pravilo o promociji piona"), info.accentColor)
    LPara(loc("Kapablanka objašnjava ovo pravilo jasno i precizno:"))

    LBox(
        icon = "✓",
        title = loc("Ključno pravilo"),
        text = "Da bi pešačka završnica bila pobednička, Kralj mora biti ispred svog piona sa barem jednim praznim poljem između njih. Ako je protivnički Kralj direktno ispred piona — igra je remi!",
        color = info.accentColor
    )
    LBullet("⬆", loc("Napreduj Kralja, ne piona"), loc("Kapablanka savetuje: napreduj Kralja koliko je moguće a da ne ugrožavaš piona. Piona pomiči tek kada je neophodno za njegovu zaštitu."), info.accentColor)
    LBullet("📏", loc("Tajno oružje — \"Opozicija\""), "Kada su dva Kralja međusobno licem u lice sa neparnim brojem polja između, igrač koji je prethodno poterao ima prednost. Zove se opozicija — i ključna je za sve pešačke završnice.", info.accentColor)

    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("⚡", loc("Kardinalno načelo"), info.accentColor)
    LBox(
        icon = "⭐",
        title = loc("Jedno drži dvoje — Kapablankovo načelo"),
        text = loc("\"Pion koji drži dva protivnička piona je jedno od glavnih oruđa majstora.\" Ako tvoj pion blokira dva protivnička, ti si faktički figuru ispred — iskoristi tu prednost na drugoj strani table!"),
        color = Color(0xFFF59E0B)
    )

    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("⚖️", loc("Lovac vs. Skakač u završnici"), info.accentColor)
    LBullet("↗", loc("Lovac je jači kada su pioni na obe strane"), loc("Lovac može istovremeno da napada pione na oba krila zahvaljujući dometu. Skakač je spor i ne može da stigne svuda."), info.accentColor)
    LBullet("🎮", loc("Skakač je jači u zatvorenim pozicijama"), loc("Kada su pioni blokirani i pozicija zatvorena, skakač je bolji jer može da preskoče pione i stigne do idealnog polja."), info.accentColor)
    LBox(
        icon = "⚠️",
        title = loc("Slabost lovca — Topov pion"),
        text = loc("Ako tvoj pion ide do h8 (ili a8) i to polje je suprotne boje od tvog lovca, protivnik drži ugao i igra je remi! Kapablanka ovo posebno ističe kao izvor mnogih propuštenih pobeda."),
        color = info.accentColor
    )

    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("🏆", loc("Šah-Mat i Remi"), info.accentColor)
    LBox("⚠️", loc("Šah"), "Situacija kada je Kralj napadnut. Igrač mora da se odbrani — pomeri kralja, pojede napadača, ili postavi štit između.", Color(0xFFF59E0B))
    LBox("❌", loc("Šah-Mat — Kraj igre"), loc("Kralj je napadnut, a nema nijedan legalan način odbrane. Partija se završava ovde — Kralj se nikada zapravo ne jede."), Color(0xFFEF4444))
    LBox("ℹ️", loc("Pat — Noćna mora pobednika!"), "Igrač na potezu nije u šahu, ali nema nijedan legalan potez. Odmah je remi! Ovo je najopasnija greška u završnici — pretvoriti pobedničku poziciju u remi jednim lošim potezom.", info.accentColor)
    LBullet("🔄", loc("Ponavljanje pozicije"), "Ako se ista pozicija ponovi tri puta, može se tražiti remi.", info.accentColor)
    LBullet("➖", loc("Nedovoljno materijala"), loc("Samo Kraljevi, ili Kralj + Lovac/Skakač protiv Kralja — nije moguće dati mat. Automatski remi."), info.accentColor)

    // Mini final test (5 puzzles)
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("🏆", loc("Mini finalni test"), info.accentColor)
    LPara(loc("Primeni sve što si naučio! Reši 5 zadataka — mat u najmanji broj poteza. Svaki koristi drugu kombinaciju figura."))

    MatePuzzleCard(
        fen = "6k1/5ppp/8/8/8/8/3Q4/4R1K1 w - - 0 1",
        moves = listOf("d2d8"),
        title = loc("Zadatak 1 — Dama na zadnjoj liniji"),
        hint = loc("Crni Kralj je zarobljen. Dama ima slobodan put..."),
        icon = "👑",
        accentColor = Color(0xFFF59E0B),
        mateIn = 1
    )

    MatePuzzleCard(
        fen = "6k1/5ppp/8/1R6/8/8/8/6K1 w - - 0 1",
        moves = listOf("b5b8"),
        title = loc("Zadatak 2 — Top na 8. liniji"),
        hint = loc("Pešaci blokiraju sopstvenog Kralja. Top pronalazi put..."),
        icon = "🏰",
        accentColor = Color(0xFF3B82F6),
        mateIn = 1
    )

    MatePuzzleCard(
        fen = "2r3k1/5ppp/8/8/Q7/8/8/4R1K1 w - - 0 1",
        moves = listOf("e1e8", "c8e8", "a4e8"),
        title = loc("Zadatak 3 — Žrtva Topa!"),
        hint = loc("Top ide na e8 i daje šah. Crni Top mora da uzme — a onda Dama?"),
        icon = "🏰",
        accentColor = Color(0xFFF59E0B),
        mateIn = 2
    )

    MatePuzzleCard(
        fen = "5k2/5ppp/8/4B3/8/8/8/4R1K1 w - - 0 1",
        moves = listOf("e5d6", "f8g8", "e1e8"),
        title = loc("Zadatak 4 — Lovac + Top"),
        hint = loc("Lovac daje šah i tera Kralja na g8. Zašto je to pogubno?"),
        icon = "📐",
        accentColor = Color(0xFF10B981),
        mateIn = 2
    )

    MatePuzzleCard(
        fen = "r1bq2r1/b4pk1/p1pp1p2/1p2pP2/1P2P1PB/3P4/1PPQ2P1/R3K2R w KQ - 0 1",
        moves = listOf("d2h6", "g7h6", "h4f6"),
        title = loc("Zadatak 5 (težak) — Žrtva Dame, Lovac mat"),
        hint = loc("Greet – Hanley, Liverpool 2008. Dama se žrtvuje na h6. Zašto Kralj mora da uzme?"),
        icon = "👑",
        accentColor = Color(0xFF8B5CF6),
        mateIn = 2
    )

    // About author
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("👤", loc("O autoru"), info.accentColor)

    LBox(
        icon = "👤",
        title = loc("Hoze Raul Kapablanka (1888–1942)"),
        text = loc("Kubanski šahista, treći zvanični svetski prvak u šahu. Važi za jednog od najvećih šahiskih genija svih vremena — poznat po kristalno čistom stilu igre i intuitivnom razumevanju pozicije."),
        color = info.accentColor
    )

    LPara("Kapablanka je naučio šah sa svega četiri godine gledajući svog oca. Nikada nije pohađao šahovsku školu — sve je naučio sam, igrajući. Već sa 13 godina pobedio je kubanskog prvaka Juana Corzo-a i postao nacionalna senzacija.")
    LPara("U periodu 1916–1924. godine nije izgubio nijednu partiju. Svetsku šampionsku titulu osvojio je 1921. pobedivši legendarnog Emanuela Laskera, koji je bio prvak čitavih 27 godina.")

    LBullet("👁️", loc("Fotografska preciznost"), loc("Pobedio je jednostavnošću i savršenom tehnikom — ne agresijom."), info.accentColor)
    LBullet("📚", loc("Popularizator šaha"), loc("\"Chess Fundamentals\" (1921) je pisao upravo za početnike i amatere."), info.accentColor)

    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    LSectionHeader("📖", loc("Izvor: Project Gutenberg"), info.accentColor)
    LPara("Sav sadržaj lekcija preuzet je iz digitalne verzije knjige dostupne na Project Gutenberg — neprofitnoj biblioteci knjiga u javnom domenu.")

    LBox(
        icon = "🌐",
        title = "gutenberg.org/ebooks/33870",
        text = loc("Možeš je pročitati u celosti besplatno, bez registracije."),
        color = info.accentColor
    )
    LBox(
        icon = "❤️",
        title = loc("Zahvalnost"),
        text = loc("Chessko duguje zahvalnost Kapablanki na bezvremenim principima i Project Gutenberg zajednici volontera koji su digitalizovali ovu i hiljade drugih knjiga."),
        color = info.accentColor
    )
}

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
            .background(Color.White.copy(alpha = 0.04f))
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
                    Text(text = line.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = line.hint, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
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
                text = state.statusMessage,
                color = when (state.phase) {
                    OpeningPhase.SOLVED -> Color(0xFF10B981)
                    OpeningPhase.WRONG_MOVE -> Color(0xFFEF4444)
                    else -> Color.White.copy(alpha = 0.8f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = { state.reset() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White
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
            .background(Color.White.copy(alpha = 0.04f))
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
                    Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = hint, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
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
                    state.isSolved -> Color(0xFF10B981)
                    state.isOver && !state.isSolved -> Color(0xFFEF4444)
                    else -> Color.White.copy(alpha = 0.8f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = { state.reset() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White
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
    mateIn: Int
) {
    val line = remember(fen, moves, title, hint, icon, accentColor) {
        OpeningLine(
            name = title,
            uciMoves = moves,
            hint = hint,
            icon = icon,
            accentColor = accentColor,
            solvedMessage = loc("Sjajno! Mat pronađen! 🏆"),
            wrongMessage = loc("Nije to — traži pravi ključni potez!"),
            playingPrompt = if (mateIn == 1) loc("Pronađi mat u 1 potezu!") else loc("Pronađi ključni potez!"),
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
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.5.dp,
                color = when (state.phase) {
                    OpeningPhase.SOLVED -> Color(0xFFF59E0B).copy(alpha = 0.6f)
                    OpeningPhase.WRONG_MOVE -> Color(0xFFEF4444).copy(alpha = 0.5f)
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
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "Mat u $mateIn",
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(accentColor.copy(alpha = 0.18f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(text = hint, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
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
                    OpeningPhase.SOLVED -> Color(0xFFF59E0B)
                    OpeningPhase.WRONG_MOVE -> Color(0xFFEF4444)
                    OpeningPhase.PLAYING -> Color.White.copy(alpha = 0.65f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = { state.reset() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(text = loc("Ponovo"), fontSize = 11.sp)
            }
        }
    }
}
