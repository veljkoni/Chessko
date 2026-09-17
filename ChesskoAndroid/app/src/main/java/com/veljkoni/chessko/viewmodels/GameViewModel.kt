package com.veljkoni.chessko.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veljkoni.chessko.logic.*
import com.veljkoni.chessko.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GameMode {
    VS_COMPUTER, LOCAL_FRIEND
}

/**
 * `saveKey` odredjuje slot u SharedPreferences u koji OVAJ primerak cuva i
 * cita partiju. Podrazumevano [FREE_PLAY_SAVE_KEY] -- tab Igra.
 *
 * Korak `game` Puta pravi sopstveni primerak sa [stepSaveKey]. Sa jednim
 * zajednickim kljucem bi taj primerak pri kreiranju ucitao partiju koju
 * korisnik ima u toku na tabu Igra i prvim potezom je pregazio -- `save()`
 * upisuje celu partiju posle SVAKOG poteza.
 */
class GameViewModel(
    application: Application,
    private val saveKey: String = FREE_PLAY_SAVE_KEY
) : AndroidViewModel(application) {

    companion object {
        const val FREE_PLAY_SAVE_KEY = "saved_game"

        /**
         * Korak dobija SOPSTVENI slot. Sa jednim kljucem bi drugi primerak
         * modela ucitao partiju koju korisnik ima u toku na tabu Igra i prvim
         * potezom je pregazio -- model snima celu partiju posle SVAKOG poteza.
         */
        fun stepSaveKey(stepId: String) = "saved_game.step.$stepId"

        /**
         * Brise sacuvanu partiju KORAKA sa diska, bez potrebe za zivim
         * primerkom modela. Zavrsen `game` korak ostaje klikabilan
         * (`PathView`: `clickable = state != LOCKED`, a `COMPLETED != LOCKED`),
         * pa drugi ulazak u isti korak pravi NOV primerak (`remember(stepId)`)
         * ciji `init` sinhrono ucita staru ZAVRSENU partiju sa diska -- tek
         * asinhroni `LaunchedEffect(stepId)` je posle toga resetuje, pa
         * korisnik nakratko vidi staru gotovu tablu. iOS ovo resava brisanjem
         * ODMAH po zavrsetku koraka (`GameViewModel.clearStepSave()`,
         * `Chessko/ViewModels/GameViewModel.swift:814`, zvano iz
         * `StepGameView.swift:133`) -- isti obrazac ovde, pozvano odmah posle
         * `progressStore.completeStep(stepId)`.
         */
        fun clearStepSave(context: Context, stepId: String) {
            val prefs = context.getSharedPreferences("chessko_save", Context.MODE_PRIVATE)
            prefs.edit().remove(stepSaveKey(stepId)).apply()
        }
    }

    /// Izvedeno iz `saveKey`, ne zasebno stanje -- nema dva izvora istine o
    /// tome da li je ovaj primerak partija koraka Puta ili slobodna partija.
    val isStepGame: Boolean
        get() = saveKey != FREE_PLAY_SAVE_KEY

    /// Tezina koju propisuje `game` korak Puta (postavlja je `startStepGame`).
    /// Kad je postavljena, ima prednost nad globalnim podesavanjem korisnika
    /// -- bez ovoga bi `startStepGame` morao da pise u `SettingsManager`, a to
    /// bi pregazilo tezinu koju je korisnik izabrao za slobodnu partiju na
    /// tabu Igra (i obrnuto, sledeca slobodna partija bi nasledila tezinu
    /// koraka).
    // `mutableStateOf`, NE obican `var`: `startStepGame` ga postavlja u ISTOM
    // pozivu u kom cesto postavlja i `gameState` na NOVU ALI STRUKTURNO JEDNAKU
    // vrednost (fresh `game` korak bez `startFEN`-a resetuje na `GameState.initial()`,
    // isto kao pocetna vrednost) -- Compose-ov `mutableStateOf` za `gameState`
    // koristi strukturnu jednakost i NE prijavljuje promenu kad je nova vrednost
    // `equals()` staroj, pa se ekran ne bi ponovo iscrtao ni zbog cega. Da je i
    // ovo obican `var`, kartica protivnika bi ostala zaglavljena na globalnoj
    // (pogresnoj) tezini sve dok neka DRUGA, stvarno razlicita promena stanja ne
    // izazove recompose iz nekog drugog razloga.
    private var stepDifficultyOverride: GameDifficulty? by mutableStateOf<GameDifficulty?>(null)

    private val soundManager = SoundManager(application)
    private val hapticManager = HapticManager(application)
    private val settings = SettingsManager.getInstance(application)

    // Game state (Observable via Compose state delegation)
    var gameState by mutableStateOf(GameState.initial())
        private set

    var selectedPosition by mutableStateOf<Position?>(null)
        private set

    var legalMovesForSelected by mutableStateOf<List<ChessMove>>(emptyList())
        private set

    var isThinking by mutableStateOf(false)
        private set

    var lastMove by mutableStateOf<ChessMove?>(null)
        private set

    var difficulty: GameDifficulty
        get() = stepDifficultyOverride ?: settings.difficulty
        set(value) {
            settings.updateDifficulty(value)
        }

    var stockfishLevel: StockfishLevel
        get() = settings.stockfishLevel
        set(value) {
            settings.updateStockfishLevel(value)
        }

    var gameMode by mutableStateOf(GameMode.VS_COMPUTER)
    var playerColor by mutableStateOf(PieceColor.WHITE)

    var showPromotion by mutableStateOf(false)
    var promotionMove by mutableStateOf<ChessMove?>(null)

    // Undo history: pair of (gameState, lastMove) before the move was applied
    private val history = mutableStateListOf<Pair<GameState, ChessMove?>>()

    // Game review (move-by-move navigation)
    var viewingMoveIndex by mutableStateOf<Int?>(null)
        private set

    // Evaluation bar
    var evaluationScore by mutableDoubleStateOf(0.0)
        private set
    var evaluationMateIn by mutableStateOf<Int?>(null)
        private set

    val showEvalBar: Boolean
        get() = settings.showEvalBar

    init {
        load()
        updateEvaluation()
    }

    val allHistoryStates: List<Pair<GameState, ChessMove?>>
        get() {
            val list = mutableListOf<Pair<GameState, ChessMove?>>()
            if (history.isNotEmpty()) {
                list.add(Pair(history.first().first, null))
                for (i in 1 until history.size) {
                    val prevMove = history.getOrNull(i)?.second
                    list.add(Pair(history[i].first, prevMove))
                }
                list.add(Pair(gameState, lastMove))
            } else {
                list.add(Pair(gameState, lastMove))
            }
            return list
        }

    val displayState: GameState
        get() {
            val idx = viewingMoveIndex ?: return gameState
            val states = allHistoryStates
            return if (idx in states.indices) states[idx].first else gameState
        }

    val displayLastMove: ChessMove?
        get() {
            val idx = viewingMoveIndex ?: return lastMove
            val states = allHistoryStates
            return if (idx in states.indices) states[idx].second else lastMove
        }

    val isReviewing: Boolean
        get() = viewingMoveIndex != null && viewingMoveIndex != (allHistoryStates.size - 1)

    val currentReviewMoveIndex: Int
        get() = viewingMoveIndex ?: (allHistoryStates.size - 1)

    val totalReviewMoves: Int
        get() = maxOf(0, allHistoryStates.size - 1)

    val canStepBackward: Boolean
        get() = currentReviewMoveIndex > 0

    val canStepForward: Boolean
        get() = viewingMoveIndex != null && viewingMoveIndex!! < (allHistoryStates.size - 1)

    fun goToStart() {
        if (allHistoryStates.isNotEmpty()) {
            viewingMoveIndex = 0
            updateEvaluation()
        }
    }

    fun stepBackward() {
        val current = viewingMoveIndex ?: (allHistoryStates.size - 1)
        if (current > 0) {
            viewingMoveIndex = current - 1
            updateEvaluation()
        }
    }

    fun stepForward() {
        val current = viewingMoveIndex ?: (allHistoryStates.size - 1)
        if (current < allHistoryStates.size - 1) {
            val next = current + 1
            viewingMoveIndex = if (next == allHistoryStates.size - 1) null else next
            updateEvaluation()
        }
    }

    fun goToEnd() {
        viewingMoveIndex = null
        updateEvaluation()
    }

    fun goToMove(moveNumber: Int) {
        val states = allHistoryStates
        if (moveNumber in states.indices) {
            viewingMoveIndex = if (moveNumber == states.size - 1) null else moveNumber
            updateEvaluation()
        }
    }

    fun updateEvaluation() {
        val targetState = displayState
        viewModelScope.launch(Dispatchers.Default) {
            val (score, mate) = ChessAI.evaluatePosition(targetState)
            withContext(Dispatchers.Main) {
                evaluationScore = score
                evaluationMateIn = mate
            }
        }
    }

    val activePlayerColor: PieceColor
        get() = if (gameMode == GameMode.LOCAL_FRIEND) gameState.currentTurn else playerColor

    val isPlayerTurn: Boolean
        get() = if (gameMode == GameMode.LOCAL_FRIEND) {
            !isGameOver && !isThinking && !isReviewing
        } else {
            gameState.currentTurn == playerColor && !isThinking && !isReviewing
        }

    val isGameOver: Boolean
        get() = when (gameState.status) {
            is GameStatus.Checkmate, is GameStatus.Draw, is GameStatus.Resigned -> true
            else -> false
        }

    val canUndo: Boolean
        get() = history.isNotEmpty() && !isThinking && !isReviewing

    val canResign: Boolean
        get() = !isGameOver && !isThinking && (history.isNotEmpty() || gameState.moveNotations.isNotEmpty())

    val isFlipped: Boolean
        get() = if (gameMode == GameMode.LOCAL_FRIEND) {
            if (SettingsManager.getInstance(getApplication()).rotateBoardInLocalPlay) {
                gameState.currentTurn == PieceColor.BLACK
            } else {
                playerColor == PieceColor.BLACK
            }
        } else {
            playerColor == PieceColor.BLACK
        }

    fun resign() {
        if (!canResign) return
        val loser = activePlayerColor
        gameState = gameState.copy(status = GameStatus.Resigned(loser))
        viewingMoveIndex = null
        save()
        soundManager.playCheckmate()
        hapticManager.warning()
        updateEvaluation()

        // Predaja u koraku Puta se NE upisuje u statistiku -- to je jedini
        // predvidjen izlaz iz koraka koji se ne moze dobiti. Odigrana partija
        // u koraku (mat/remi, ispod u `triggerAudioAndHapticFeedback`) se broji
        // normalno, kao i svaka druga partija.
        if (gameMode == GameMode.VS_COMPUTER && !hasRecordedGameEnd && !isStepGame) {
            hasRecordedGameEnd = true
            val stats = StatsManager.getInstance(getApplication())
            if (loser == playerColor) {
                stats.recordGameLost()
            } else {
                stats.recordGameWon(difficulty)
            }
        }
    }

    fun tap(position: Position) {
        if (isReviewing) {
            // Auto-return to live game on tap
            goToEnd()
            return
        }
        if (!isPlayerTurn || isGameOver) return

        val selected = selectedPosition
        if (selected != null) {
            val move = legalMovesForSelected.firstOrNull { it.to == position }
            if (move != null) {
                handleMove(move)
                return
            }
        }

        val piece = gameState.board[position.row][position.col]
        if (piece != null && piece.color == activePlayerColor) {
            if (selectedPosition != position) {
                hapticManager.selection()
            }
            selectedPosition = position
            legalMovesForSelected = MoveGenerator.legalMoves(activePlayerColor, gameState)
                .filter { it.from == position }
        } else {
            selectedPosition = null
            legalMovesForSelected = emptyList()
        }
    }

    private fun handleMove(move: ChessMove) {
        val settings = SettingsManager.getInstance(getApplication())
        if (move.flag is MoveFlag.Promotion) {
            if (settings.autoPromoteToQueen) {
                execute(ChessMove(move.from, move.to, MoveFlag.Promotion(PieceType.QUEEN)))
            } else {
                // Wait for player to choose a piece
                promotionMove = move
                showPromotion = true
            }
        } else {
            execute(move)
        }
    }

    fun confirmPromotion(type: PieceType) {
        val move = promotionMove ?: return
        promotionMove = null
        showPromotion = false
        execute(ChessMove(move.from, move.to, MoveFlag.Promotion(type)))
    }

    fun cancelPromotion() {
        promotionMove = null
        showPromotion = false
        selectedPosition = null
        legalMovesForSelected = emptyList()
    }

    private fun execute(move: ChessMove, addToHistory: Boolean = true) {
        // Capture details BEFORE state change
        val capturedPiece = when (move.flag) {
            is MoveFlag.EnPassant -> {
                val dir = if (gameState.currentTurn == PieceColor.WHITE) 1 else -1
                gameState.board[move.to.row + dir][move.to.col]
            }
            else -> gameState.board[move.to.row][move.to.col]
        }
        val isCapture = capturedPiece != null

        if (addToHistory) {
            history.add(Pair(gameState, lastMove))
        }

        val newState = gameState.applying(move)
        gameState = newState
        lastMove = move
        viewingMoveIndex = null
        selectedPosition = null
        legalMovesForSelected = emptyList()

        // Play feedback
        triggerAudioAndHapticFeedback(newState, isCapture)

        save()
        updateEvaluation()

        if (!isGameOver) {
            triggerAI()
        }
    }

    fun undo() {
        if (!canUndo) return
        viewingMoveIndex = null

        if (gameMode == GameMode.LOCAL_FRIEND) {
            val last = history.removeLastOrNull()
            if (last != null) {
                gameState = last.first
                lastMove = last.second
                selectedPosition = null
                legalMovesForSelected = emptyList()
                soundManager.playMove()
                hapticManager.lightImpact()
                save()
                updateEvaluation()
            }
            return
        }

        // VS Computer: pop until we reach the player's turn
        while (history.isNotEmpty()) {
            val last = history.removeLast()
            if (last.first.currentTurn == playerColor) {
                gameState = last.first
                lastMove = last.second
                selectedPosition = null
                legalMovesForSelected = emptyList()
                soundManager.playMove()
                hapticManager.lightImpact()
                save()
                updateEvaluation()
                return
            }
        }
    }

    fun resetGame() {
        gameState = GameState.initial()
        selectedPosition = null
        legalMovesForSelected = emptyList()
        lastMove = null
        history.clear()
        viewingMoveIndex = null
        isThinking = false
        hasRecordedGameEnd = false
        clearSave()
        updateEvaluation()
    }

    fun generatePGN(): String {
        val sb = StringBuilder()
        sb.appendLine("[Event \"Chessko Game\"]")
        sb.appendLine("[Site \"Chessko App\"]")
        sb.appendLine("[Date \"${java.time.LocalDate.now()}\"]")
        sb.appendLine("[White \"${if (gameMode == GameMode.LOCAL_FRIEND) "Player 1" else if (playerColor == PieceColor.WHITE) "Player" else "Chessko AI"}\"]")
        sb.appendLine("[Black \"${if (gameMode == GameMode.LOCAL_FRIEND) "Player 2" else if (playerColor == PieceColor.BLACK) "Player" else "Chessko AI"}\"]")

        val result = when (val s = gameState.status) {
            is GameStatus.Checkmate -> if (s.color == PieceColor.WHITE) "0-1" else "1-0"
            is GameStatus.Resigned -> if (s.color == PieceColor.WHITE) "0-1" else "1-0"
            is GameStatus.Draw -> "1/2-1/2"
            else -> "*"
        }
        sb.appendLine("[Result \"$result\"]")
        sb.appendLine()

        val notations = gameState.moveNotations
        for (i in notations.indices) {
            if (i % 2 == 0) {
                sb.append("${i / 2 + 1}. ")
            }
            sb.append("${notations[i]} ")
        }
        if (notations.isNotEmpty()) {
            sb.append(result)
        }

        return sb.toString().trim()
    }

    fun newGame(mode: GameMode, color: PieceColor) {
        gameMode = mode
        playerColor = color
        resetGame()
        if (mode == GameMode.VS_COMPUTER && playerColor == PieceColor.BLACK) {
            triggerAI()
        }
    }

    /**
     * Priprema partiju za `game` korak Puta.
     *
     * NAMERNO ne zove `newGame()`: on tezinu upisuje kroz `SettingsManager`
     * (globalno podesavanje), pa bi njome pregazio tezinu koju bira korisnik
     * za slobodnu partiju na tabu Igra. Ovaj primerak umesto toga postavlja
     * [stepDifficultyOverride] -- slobodna partija ga nikad ne vidi.
     *
     * Zapoceta partija IZ ISTOG koraka (isti `saveKey`) se nastavlja -- `load()`
     * u `init`-u je vec ucitao stanje sa diska. Zavrsena se NE nastavlja:
     * ulazak u vec odigran korak bi inace zavrsio na gotovoj tabli bez ijednog
     * poteza koji se moze odigrati -- korak se moze odigrati ponovo.
     */
    fun startStepGame(difficulty: GameDifficulty, startFEN: String?) {
        stepDifficultyOverride = difficulty
        gameMode = GameMode.VS_COMPUTER

        if (gameState.moveNotations.isNotEmpty() && !isGameOver) {
            return // nastavak vec ucitane, nezavrsene partije koraka
        }

        hasRecordedGameEnd = false
        viewingMoveIndex = null
        history.clear()
        selectedPosition = null
        legalMovesForSelected = emptyList()
        showPromotion = false
        promotionMove = null
        lastMove = null
        isThinking = false

        // Neispravan FEN ne sme da ostavi korak bez table.
        val start = startFEN?.let { GameState.fromFEN(it) } ?: GameState.initial()
        // Igrac vodi stranu koja je na potezu u startnoj poziciji -- bez ovoga
        // bi korak sa startFEN-om u kome je crni na potezu odmah cekao potez
        // igraca koji tu stranu uopste ne igra. Danas nedostizno (nijedan
        // `game` korak u curriculum.json ne nosi startFEN), ali cena je nula.
        playerColor = start.currentTurn
        gameState = start

        save() // slot koraka postoji od prvog trenutka, ne tek od prvog poteza
        updateEvaluation()

        if (gameMode == GameMode.VS_COMPUTER && gameState.currentTurn != playerColor) {
            triggerAI()
        }
    }

    private fun triggerAI() {
        if (gameMode != GameMode.VS_COMPUTER) return
        if (gameState.currentTurn == playerColor) return
        if (isThinking) return

        isThinking = true

        val capturedState = gameState
        val capturedColor = gameState.currentTurn
        val currentDifficulty = difficulty
        val currentStockfishLevel = stockfishLevel

        viewModelScope.launch {
            delay(300) // slight delay to look natural
            
            // Calculate best move on background thread
            val move = withContext(Dispatchers.Default) {
                if (currentDifficulty == GameDifficulty.STOCKFISH) {
                    if (StockfishEngine.engineStarted) {
                        val uciMove = StockfishEngine.getBestMove(capturedState.fen, currentStockfishLevel.depth)
                        if (uciMove != null) {
                            parseUCIMove(uciMove, capturedState)
                        } else {
                            ChessAI(ChessAI.Difficulty.MEDIUM).bestMove(capturedColor, capturedState)
                        }
                    } else {
                        ChessAI(ChessAI.Difficulty.MEDIUM).bestMove(capturedColor, capturedState)
                    }
                } else {
                    val aiDiff = when (currentDifficulty) {
                        GameDifficulty.BEGINNER -> ChessAI.Difficulty.BEGINNER
                        GameDifficulty.EASY -> ChessAI.Difficulty.EASY
                        GameDifficulty.MEDIUM -> ChessAI.Difficulty.MEDIUM
                        GameDifficulty.HARD -> ChessAI.Difficulty.HARD
                        else -> ChessAI.Difficulty.MEDIUM
                    }
                    ChessAI(aiDiff).bestMove(capturedColor, capturedState)
                }
            }

            isThinking = false

            if (move != null && capturedState == gameState) {
                execute(move)
            }
        }
    }

    private fun parseUCIMove(uci: String, state: GameState): ChessMove? {
        if (uci.length < 4) return null
        val fromCol = uci[0] - 'a'
        val fromRow = 7 - (uci[1] - '1')
        val toCol = uci[2] - 'a'
        val toRow = 7 - (uci[3] - '1')

        if (fromCol !in 0..7 || fromRow !in 0..7 || toCol !in 0..7 || toRow !in 0..7) return null

        val from = Position(fromRow, fromCol)
        val to = Position(toRow, toCol)
        val legalMoves = MoveGenerator.legalMoves(state.currentTurn, state)

        if (uci.length >= 5) {
            val promType = when (uci[4]) {
                'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP
                'n' -> PieceType.KNIGHT
                else -> PieceType.QUEEN
            }
            return legalMoves.firstOrNull { 
                it.from == from && it.to == to && it.flag is MoveFlag.Promotion && (it.flag as MoveFlag.Promotion).pieceType == promType 
            }
        }

        return legalMoves.firstOrNull { it.from == from && it.to == to }
    }

    private val statsManager = StatsManager.getInstance(application)
    private var hasRecordedGameEnd = false

    private fun triggerAudioAndHapticFeedback(state: GameState, isCapture: Boolean) {
        when (val status = state.status) {
            is GameStatus.Checkmate -> {
                soundManager.playCheckmate()
                hapticManager.success()
                if (!hasRecordedGameEnd && gameMode == GameMode.VS_COMPUTER) {
                    hasRecordedGameEnd = true
                    if (status.color != playerColor) {
                        statsManager.recordGameWon(difficulty)
                    } else {
                        statsManager.recordGameLost()
                    }
                }
            }
            is GameStatus.Draw -> {
                soundManager.playMove()
                hapticManager.warning()
                if (!hasRecordedGameEnd && gameMode == GameMode.VS_COMPUTER) {
                    hasRecordedGameEnd = true
                    statsManager.recordGameDrawn()
                }
            }
            is GameStatus.Check -> {
                soundManager.playCheck()
                hapticManager.warning()
            }
            is GameStatus.Playing -> {
                if (isCapture) {
                    soundManager.playCapture()
                    hapticManager.mediumImpact()
                } else {
                    soundManager.playMove()
                    hapticManager.lightImpact()
                }
            }
            is GameStatus.Resigned -> {
                soundManager.playCheckmate()
                hapticManager.warning()
            }
        }
    }

    /**
     * `fen` NEMA polje za status (mat/pat/remi/predaja) -- `GameState.fromFEN`
     * uvek vraca `GameStatus.Playing`, cak i za zavrsenu partiju. Bez ovoga
     * dugme "Analiziraj partiju" (`canAnalyzeGame`, `UiComponents.kt`) posle
     * rekreacije `Activity`-ja (rotacija, promena sistemske teme) nestaje --
     * `isGameOver` cita bas ovo polje. Status se zato serijalizuje ODVOJENO
     * od FEN-a, ne izvodi iz njega.
     */
    private fun statusToJson(status: GameStatus): JSONObject {
        val json = JSONObject()
        when (status) {
            is GameStatus.Playing -> json.put("type", "PLAYING")
            is GameStatus.Check -> {
                json.put("type", "CHECK")
                json.put("color", status.color.name)
            }
            is GameStatus.Checkmate -> {
                json.put("type", "CHECKMATE")
                json.put("color", status.color.name)
            }
            is GameStatus.Draw -> {
                json.put("type", "DRAW")
                json.put("reason", drawReasonToName(status.reason))
            }
            is GameStatus.Resigned -> {
                json.put("type", "RESIGNED")
                json.put("color", status.color.name)
            }
        }
        return json
    }

    private fun drawReasonToName(reason: DrawReason): String = when (reason) {
        is DrawReason.Stalemate -> "STALEMATE"
        is DrawReason.FiftyMoves -> "FIFTY_MOVES"
        is DrawReason.Repetition -> "REPETITION"
        is DrawReason.InsufficientMaterial -> "INSUFFICIENT_MATERIAL"
    }

    private fun drawReasonFromName(name: String): DrawReason? = when (name) {
        "STALEMATE" -> DrawReason.Stalemate
        "FIFTY_MOVES" -> DrawReason.FiftyMoves
        "REPETITION" -> DrawReason.Repetition
        "INSUFFICIENT_MATERIAL" -> DrawReason.InsufficientMaterial
        else -> null
    }

    /**
     * `null` kad polje `status` u zapisu ne postoji (save napravljen PRE ove
     * izmene) ili je oblik neocekivan -- pozivalac (`load()`) tada pada na
     * `GameState.statusFromPosition`.
     */
    private fun statusFromJson(json: JSONObject?): GameStatus? {
        if (json == null) return null
        return try {
            when (json.getString("type")) {
                "PLAYING" -> GameStatus.Playing
                "CHECK" -> GameStatus.Check(PieceColor.valueOf(json.getString("color")))
                "CHECKMATE" -> GameStatus.Checkmate(PieceColor.valueOf(json.getString("color")))
                "DRAW" -> drawReasonFromName(json.getString("reason"))?.let { GameStatus.Draw(it) }
                "RESIGNED" -> GameStatus.Resigned(PieceColor.valueOf(json.getString("color")))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun save() {
        try {
            val json = JSONObject()
            json.put("fen", gameState.fen)
            json.put("status", statusToJson(gameState.status))
            json.put("gameMode", gameMode.name)
            json.put("playerColor", playerColor.name)

            lastMove?.let {
                val moveJson = JSONObject()
                moveJson.put("fromRow", it.from.row)
                moveJson.put("fromCol", it.from.col)
                moveJson.put("toRow", it.to.row)
                moveJson.put("toCol", it.to.col)
                json.put("lastMove", moveJson)
            }
            
            val historyArray = JSONArray()
            for (entry in history) {
                val entryJson = JSONObject()
                entryJson.put("fen", entry.first.fen)
                entry.second?.let {
                    val mJson = JSONObject()
                    mJson.put("fromRow", it.from.row)
                    mJson.put("fromCol", it.from.col)
                    mJson.put("toRow", it.to.row)
                    mJson.put("toCol", it.to.col)
                    entryJson.put("lastMove", mJson)
                }
                historyArray.put(entryJson)
            }
            json.put("history", historyArray)

            val notationsArray = JSONArray()
            for (notation in gameState.moveNotations) {
                notationsArray.put(notation)
            }
            json.put("moveNotations", notationsArray)
            
            val prefs = getApplication<Application>().getSharedPreferences("chessko_save", Context.MODE_PRIVATE)
            prefs.edit().putString(saveKey, json.toString()).apply()
            Log.d("GameViewModel", "Game saved successfully!")
        } catch (e: Exception) {
            Log.e("GameViewModel", "Failed to save game state", e)
        }
    }

    private fun load() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("chessko_save", Context.MODE_PRIVATE)
            val savedString = prefs.getString(saveKey, null) ?: return
            val json = JSONObject(savedString)
            
            val savedFen = json.getString("fen")
            val savedState = GameState.fromFEN(savedFen) ?: return
            
            val savedMode = GameMode.valueOf(json.getString("gameMode"))
            val savedColor = PieceColor.valueOf(json.getString("playerColor"))
            
            val historyArray = json.getJSONArray("history")
            val tempHistory = mutableListOf<Pair<GameState, ChessMove?>>()
            
            for (i in 0 until historyArray.length()) {
                val entryJson = historyArray.getJSONObject(i)
                val entryFen = entryJson.getString("fen")
                val entryState = GameState.fromFEN(entryFen) ?: continue
                
                var entryMove: ChessMove? = null
                if (entryJson.has("lastMove")) {
                    val mJson = entryJson.getJSONObject("lastMove")
                    val from = Position(mJson.getInt("fromRow"), mJson.getInt("fromCol"))
                    val to = Position(mJson.getInt("toRow"), mJson.getInt("toCol"))
                    val legalMoves = MoveGenerator.legalMoves(entryState.currentTurn, entryState)
                    entryMove = legalMoves.firstOrNull { it.from == from && it.to == to }
                }
                tempHistory.add(Pair(entryState, entryMove))
            }
            
            var savedLastMove: ChessMove? = null
            if (json.has("lastMove")) {
                val lmJson = json.getJSONObject("lastMove")
                val from = Position(lmJson.getInt("fromRow"), lmJson.getInt("fromCol"))
                val to = Position(lmJson.getInt("toRow"), lmJson.getInt("toCol"))
                
                val prevState = if (tempHistory.isNotEmpty()) tempHistory.last().first else null
                if (prevState != null) {
                    val legalMoves = MoveGenerator.legalMoves(prevState.currentTurn, prevState)
                    savedLastMove = legalMoves.firstOrNull { it.from == from && it.to == to }
                }
            }

            val savedMoveNotations = mutableListOf<String>()
            if (json.has("moveNotations")) {
                val notationsArray = json.getJSONArray("moveNotations")
                for (i in 0 until notationsArray.length()) {
                    savedMoveNotations.add(notationsArray.getString(i))
                }
            }
            
            // Fallback SAMO za zapise sacuvane PRE ove izmene, koji nemaju
            // polje "status". `GameState.statusFromPosition` prepoznaje mat i
            // pat, ali NE i `Draw(FiftyMoves)`/`Draw(Repetition)` (fen getter
            // uvek pise polutez "0", `GameState.kt:109`, zateceno -- van
            // obima ove izmene -- a fromFEN/load() ionako ne cuvaju punu
            // istoriju pozicija), ni `Draw(InsufficientMaterial)` (pozicija
            // MOZE imati legalne poteze, npr. kralj+kralj, pa
            // `legalMoves.isEmpty()` ne pogadja) ni `Resigned` (predaja nema
            // trag na tabli). Takav stari zapis ostaje na tim ishodima
            // neprepoznat -- isto ponasanje kao PRE ove izmene, nije
            // regresija. Nov zapis uvek nosi eksplicitan "status", pa ovaj
            // fallback pogadja iskljucivo partije sacuvane starijom verzijom
            // aplikacije -- opadajuci skup.
            val savedStatus = statusFromJson(json.optJSONObject("status"))
                ?: GameState.statusFromPosition(savedState)

            gameState = savedState.copy(status = savedStatus, moveNotations = savedMoveNotations)
            gameMode = savedMode
            playerColor = savedColor
            lastMove = savedLastMove
            history.clear()
            history.addAll(tempHistory)
            
            Log.d("GameViewModel", "Game loaded successfully!")
            
            // Trigger AI if it's their turn
            if (gameMode == GameMode.VS_COMPUTER && gameState.currentTurn != playerColor && !isGameOver) {
                triggerAI()
            }
        } catch (e: Exception) {
            Log.e("GameViewModel", "Failed to load game state", e)
        }
    }

    private fun clearSave() {
        val prefs = getApplication<Application>().getSharedPreferences("chessko_save", Context.MODE_PRIVATE)
        prefs.edit().remove(saveKey).apply()
    }

    /**
     * Oslobadja `SoundPool`. Postoji kao javna metoda (ne samo `onCleared()`)
     * zato sto `StepGameView` pravi ovaj model kroz `remember`, NE kroz
     * `ViewModelStore` -- `onCleared()` mu se zato nikad ne izvrsi, a
     * `SoundManager` nije singleton (svaki `GameViewModel` pravi SVOJ
     * `SoundPool` koji niko drugi ne deli). Ekran ga zove iz
     * `DisposableEffect(Unit) { onDispose { ... } }` pri izlasku; `onCleared()`
     * ispod zove ISTO mesto da se `soundManager.release()` ne duplira.
     */
    fun releaseSounds() {
        soundManager.release()
    }

    override fun onCleared() {
        super.onCleared()
        releaseSounds()
    }
}
