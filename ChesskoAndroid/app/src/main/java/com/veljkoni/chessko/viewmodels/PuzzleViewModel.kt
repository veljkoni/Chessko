package com.veljkoni.chessko.viewmodels

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veljkoni.chessko.logic.HapticManager
import com.veljkoni.chessko.logic.MoveGenerator
import com.veljkoni.chessko.logic.SoundManager
import com.veljkoni.chessko.logic.StatsManager
import com.veljkoni.chessko.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class PuzzlePhase {
    LOADING, NETWORK_ERROR, PLAYING, WRONG_MOVE, SOLVED, SHOWING_SOLUTION
}

class PuzzleViewModel(application: Application) : AndroidViewModel(application) {

    private val soundManager = SoundManager(application)
    private val hapticManager = HapticManager(application)
    private val sharedPrefs = application.getSharedPreferences("chessko_puzzle_prefs", Context.MODE_PRIVATE)
    private val statsManager = StatsManager.getInstance(application)
    private var puzzleHadError = false

    // Observable states
    var gameState by mutableStateOf(GameState.initial())
        private set
    var playerColor by mutableStateOf(PieceColor.WHITE)
        private set
    var selectedPosition by mutableStateOf<Position?>(null)
        private set
    var legalMovesForSelected by mutableStateOf<List<ChessMove>>(emptyList())
        private set
    var lastMove by mutableStateOf<ChessMove?>(null)
        private set

    var currentPuzzle by mutableStateOf<ChessPuzzle?>(null)
        private set
    var phase by mutableStateOf(PuzzlePhase.LOADING)
        private set
    var networkErrorMessage by mutableStateOf("")
        private set

    var selectedDate by mutableStateOf(LocalDate.now())
        private set
    var solvedDates by mutableStateOf(setOf<String>())
        private set

    private var rawMoves = listOf<String>()
    private var movePointer = 0

    val isFlipped: Boolean
        get() = playerColor == PieceColor.BLACK

    val isPlayerTurn: Boolean
        get() = phase == PuzzlePhase.PLAYING || phase == PuzzlePhase.WRONG_MOVE

    val canGoPrevious: Boolean
        get() = true // Limit dates if desired

    val canGoNext: Boolean
        get() = selectedDate.isBefore(LocalDate.now())

    val statusMessage: String
        get() = when (phase) {
            PuzzlePhase.LOADING -> loc("Učitavam zadatak...")
            PuzzlePhase.NETWORK_ERROR -> "Greška pri učitavanju: $networkErrorMessage"
            PuzzlePhase.PLAYING -> if (playerColor == PieceColor.WHITE) loc("Pronađi pravi potez za bele") else loc("Pronađi pravi potez za crne")
            PuzzlePhase.WRONG_MOVE -> loc("Pogrešno. Pokušaj ponovo.")
            PuzzlePhase.SOLVED -> loc("Odlično! Zadatak rešen! 🎉")
            PuzzlePhase.SHOWING_SOLUTION -> loc("Rešenje...")
        }

    init {
        loadSolvedDates()
        loadDailyPuzzle()
    }

    fun goToPrevious() {
        val prevDate = selectedDate.minusDays(1)
        loadDate(prevDate)
    }

    fun goToNext() {
        if (canGoNext) {
            val nextDate = selectedDate.plusDays(1)
            loadDate(nextDate)
        }
    }

    private fun loadDate(date: LocalDate) {
        selectedDate = date
        loadDailyPuzzle()
    }

    fun isSolved(date: LocalDate): Boolean {
        return solvedDates.contains(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    private fun markCurrentSolved() {
        val dateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val updated = solvedDates.toMutableSet().apply { add(dateKey) }
        solvedDates = updated
        sharedPrefs.edit().putStringSet("solved_dates_key", updated).apply()
    }

    private fun loadSolvedDates() {
        solvedDates = sharedPrefs.getStringSet("solved_dates_key", emptySet()) ?: emptySet()
    }

    fun loadDailyPuzzle() {
        phase = PuzzlePhase.LOADING
        currentPuzzle = null
        puzzleHadError = false

        val epochStart = LocalDate.of(1970, 1, 1)
        val dayIndex = ChronoUnit.DAYS.between(epochStart, selectedDate)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Modulo daily index matching iOS day cycle (start = dayIndex % 10000)
                val startParam = dayIndex % 10000
                val url = URL("https://chess-puzzles-api.vercel.app/puzzles?start=$startParam&limit=1")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.use { it.readText() }
                    val jsonArray = JSONArray(response)
                    if (jsonArray.length() > 0) {
                        val jsonObject = jsonArray.getJSONObject(0)
                        val puzzle = ChessPuzzle(
                            puzzleId = jsonObject.getString("PuzzleId"),
                            fen = jsonObject.getString("FEN"),
                            moves = jsonObject.getString("Moves"),
                            rating = jsonObject.getInt("Rating"),
                            themes = jsonObject.getString("Themes")
                        )
                        withContext(Dispatchers.Main) {
                            setupPuzzle(puzzle)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            networkErrorMessage = "Nema dostupnih zadataka."
                            phase = PuzzlePhase.NETWORK_ERROR
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        networkErrorMessage = "HTTP greška ${conn.responseCode}."
                        phase = PuzzlePhase.NETWORK_ERROR
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    networkErrorMessage = e.localizedMessage ?: "Greška na mreži."
                    phase = PuzzlePhase.NETWORK_ERROR
                }
            }
        }
    }

    private fun setupPuzzle(puzzle: ChessPuzzle) {
        val state = GameState.fromFEN(puzzle.fen)
        if (state == null) {
            networkErrorMessage = "Neispravan FEN u zadatku."
            phase = PuzzlePhase.NETWORK_ERROR
            return
        }

        currentPuzzle = puzzle
        rawMoves = puzzle.uciMoves
        movePointer = 0
        selectedPosition = null
        legalMovesForSelected = emptyList()
        lastMove = null

        playerColor = state.currentTurn.opposite
        gameState = state

        viewModelScope.launch {
            delay(400)
            applyNextComputerMove()
        }
    }

    fun tap(position: Position) {
        if (!isPlayerTurn) return

        val selected = selectedPosition
        if (selected != null) {
            val move = legalMovesForSelected.firstOrNull { it.to == position }
            if (move != null) {
                attempt(move)
                return
            }
        }

        val piece = gameState.board[position.row][position.col]
        if (piece != null && piece.color == playerColor) {
            hapticManager.selection()
            selectedPosition = position
            legalMovesForSelected = MoveGenerator.legalMoves(playerColor, gameState)
                .filter { it.from == position }
            if (phase == PuzzlePhase.WRONG_MOVE) {
                phase = PuzzlePhase.PLAYING
            }
        } else {
            selectedPosition = null
            legalMovesForSelected = emptyList()
        }
    }

    private fun attempt(move: ChessMove) {
        if (movePointer >= rawMoves.size) return
        val expected = ChessMove.fromUCI(rawMoves[movePointer], gameState) ?: return

        if (move.from != expected.from || move.to != expected.to) {
            hapticManager.warning()
            phase = PuzzlePhase.WRONG_MOVE
            selectedPosition = null
            legalMovesForSelected = emptyList()
            if (!puzzleHadError) {
                puzzleHadError = true
                statsManager.recordPuzzleFailed()
            }
            return
        }

        hapticManager.mediumImpact()
        applyMove(move)
        movePointer++

        if (movePointer >= rawMoves.size) {
            phase = PuzzlePhase.SOLVED
            hapticManager.success()
            markCurrentSolved()
            if (!puzzleHadError) {
                statsManager.recordPuzzleSolved()
            }
            return
        }

        phase = PuzzlePhase.PLAYING
        viewModelScope.launch {
            delay(600)
            applyNextComputerMove()
        }
    }

    private fun applyNextComputerMove() {
        if (movePointer >= rawMoves.size) return
        val move = ChessMove.fromUCI(rawMoves[movePointer], gameState) ?: return

        applyMove(move)
        movePointer++
        phase = PuzzlePhase.PLAYING
    }

    fun showSolution() {
        if (phase != PuzzlePhase.PLAYING && phase != PuzzlePhase.WRONG_MOVE) return
        phase = PuzzlePhase.SHOWING_SOLUTION
        if (!puzzleHadError) {
            puzzleHadError = true
            statsManager.recordPuzzleFailed()
        }

        viewModelScope.launch {
            while (movePointer < rawMoves.size) {
                val move = ChessMove.fromUCI(rawMoves[movePointer], gameState) ?: break
                applyMove(move)
                movePointer++
                delay(700)
            }
            phase = PuzzlePhase.SOLVED
        }
    }

    private fun applyMove(move: ChessMove) {
        val captured = gameState.board[move.to.row][move.to.col] != null || move.flag is MoveFlag.EnPassant
        if (captured) {
            soundManager.playCapture()
        } else {
            soundManager.playMove()
        }

        gameState = gameState.applyingForSearch(move)
        lastMove = move
        selectedPosition = null
        legalMovesForSelected = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
    }
}
