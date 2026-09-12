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
import com.veljkoni.chessko.logic.PuzzleRating
import com.veljkoni.chessko.logic.PuzzleRepository
import com.veljkoni.chessko.logic.SoundManager
import com.veljkoni.chessko.logic.StatsManager
import com.veljkoni.chessko.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class PuzzlePhase {
    LOADING, NETWORK_ERROR, PLAYING, WRONG_MOVE, SOLVED, SHOWING_SOLUTION
}

class PuzzleViewModel(application: Application) : AndroidViewModel(application) {

    private val soundManager = SoundManager(application)
    private val hapticManager = HapticManager(application)
    private val sharedPrefs = application.getSharedPreferences(StatsManager.PUZZLE_PREFS_NAME, Context.MODE_PRIVATE)
    private val statsManager = StatsManager.getInstance(application)
    private var puzzleHadError = false

    // `by lazy`: prva upotreba kopira 7 MB iz `assets` u `filesDir`, pa se to
    // ne radi u konstruktoru ViewModel-a (glavna nit pri otvaranju taba).
    private val repository by lazy { PuzzleRepository(getApplication()) }

    // Skup id-jeva vec resenih zadataka (nezavisno od kalendara - vidi
    // `solvedDates`). Ucitava se JEDNOM iz `SharedPreferences`; upisuje se tek
    // kad je zadatak STVARNO resen (prikaz resenja ga ne upisuje), u oba
    // rezima (DAILY i PRACTICE) - koristi se za iskljucivanje iz `nextPuzzle()`.
    private val solvedPuzzleIds: MutableSet<String> =
        sharedPrefs.getStringSet(SOLVED_PUZZLE_IDS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun persistSolvedPuzzleIds() {
        sharedPrefs.edit().putStringSet(SOLVED_PUZZLE_IDS_KEY, solvedPuzzleIds).apply()
    }

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

    enum class PuzzleMode { DAILY, PRACTICE }

    var mode by mutableStateOf(PuzzleMode.DAILY)
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
        sharedPrefs.edit().putStringSet(StatsManager.SOLVED_DATES_KEY, updated).apply()
    }

    /// Ponovo cita napredak sa diska u kes.
    ///
    /// `solvedPuzzleIds` i `solvedDates` se ucitavaju JEDNOM, pri stvaranju
    /// ViewModel-a, a `MainActivity` ga drzi kroz `remember` — instanca
    /// prezivljava prelaske izmedju tabova i otvaranje podesavanja. Kad
    /// „Resetuj statistiku" obrise `chessko_puzzle_prefs` NA DISKU, kes toga ne
    /// zna: kvacica pored datuma ostaje, a vec reseni zadaci ostaju iskljuceni
    /// iz vezbanja — sve do ubijanja procesa.
    ///
    /// Zato se zove na SVAKOM ulasku u nov zadatak, u oba rezima. Isti obrazac
    /// iOS ima kao `reloadPersistedProgress()`; Android ga pri prenosu nije
    /// poneo, pa je popravka reseta bila nepotpuna u zivoj sesiji (dokazano na
    /// uredjaju: disk cist, ekran i dalje pokazuje kvacicu).
    private fun reloadPersistedProgress() {
        solvedPuzzleIds.clear()
        sharedPrefs.getStringSet(SOLVED_PUZZLE_IDS_KEY, emptySet())?.let { solvedPuzzleIds.addAll(it) }
        loadSolvedDates()
    }

    private fun loadSolvedDates() {
        solvedDates = sharedPrefs.getStringSet(StatsManager.SOLVED_DATES_KEY, emptySet()) ?: emptySet()
    }

    fun loadDailyPuzzle() {
        reloadPersistedProgress()
        // Reset na DAILY: bez ovoga bi vezbanje (PRACTICE) ostalo "zaglavljeno"
        // posle povratka na zadatak dana (retry dugme, promena datuma), pa bi
        // UI (traka za datum, poruke) i dalje gejtovao na pogresan rezim.
        mode = PuzzleMode.DAILY
        phase = PuzzlePhase.LOADING
        currentPuzzle = null
        puzzleHadError = false

        val epochStart = LocalDate.of(1970, 1, 1)
        val dayIndex = ChronoUnit.DAYS.between(epochStart, selectedDate)

        viewModelScope.launch(Dispatchers.IO) {
            // Citanje iz lokalne baze je brzo, ali ostaje na IO niti: prvo
            // pokretanje kopira 7 MB iz `assets` u `filesDir`.
            val puzzle = repository.dailyPuzzle(dayIndex)
            withContext(Dispatchers.Main) {
                if (puzzle != null) setupPuzzle(puzzle) else showUnavailable()
            }
        }
    }

    /// Vezbanje bez kraja: bira zadatak po rejtingu igraca, iskljucujuci vec
    /// resene. Prozor se progresivno siri — inace bi korisnik koji je resio sve
    /// u svom opsegu dobio prazan ekran bez objasnjenja.
    fun nextPuzzle() {
        reloadPersistedProgress()
        mode = PuzzleMode.PRACTICE
        // Bez ovoga bi `puzzleHadError` iz PRETHODNOG zadatka (dnevnog ili
        // vezbovnog) ostao `true` i tiho progutao snimanje Elo rejtinga i
        // streaka za OVAJ, potpuno nov zadatak (i tacan i pogresan potez u
        // `attempt()` proveravaju bas ovaj flag).
        puzzleHadError = false
        viewModelScope.launch(Dispatchers.IO) {
            val r = statsManager.puzzleRating
            val windows = listOf(
                PuzzleRating.practiceWindow(r),
                maxOf(PuzzleRating.MIN, r - 400)..minOf(PuzzleRating.MAX, r + 400),
                PuzzleRating.MIN..PuzzleRating.MAX
            )
            var found = windows.firstNotNullOfOrNull {
                repository.randomPuzzle(it, solvedPuzzleIds)
            }
            // Poslednje pribeziste: korisnik je resio sve. Bolje ponovljen
            // zadatak nego prazan ekran.
            if (found == null) found = repository.randomPuzzle(PuzzleRating.MIN..PuzzleRating.MAX, emptySet())
            withContext(Dispatchers.Main) {
                if (found != null) setupPuzzle(found) else showUnavailable()
            }
        }
    }

    /// Repozitorijum ne vraca zadatak (isporucena baza od 20.000 redova ovo
    /// prakticno cini nedostizivim, ali `randomPuzzle`/`dailyPuzzle` su
    /// nullable pa se mora pokriti). Ista poruka i faza koje je ranije
    /// koristio mrezni put za "nema rezultata".
    private fun showUnavailable() {
        networkErrorMessage = "Nema dostupnih zadataka."
        phase = PuzzlePhase.NETWORK_ERROR
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
                statsManager.applyPuzzleResult(currentPuzzle?.rating ?: return, solved = false)
            }
            return
        }

        hapticManager.mediumImpact()
        applyMove(move)
        movePointer++

        if (movePointer >= rawMoves.size) {
            phase = PuzzlePhase.SOLVED
            hapticManager.success()
            currentPuzzle?.let { puzzle ->
                if (solvedPuzzleIds.add(puzzle.puzzleId)) persistSolvedPuzzleIds()
            }
            // Kalendarski dan se oznacava resenim SAMO u DAILY rezimu —
            // vezbovni zadatak nije vezan ni za jedan datum.
            if (mode == PuzzleMode.DAILY) {
                markCurrentSolved()
            }
            if (!puzzleHadError) {
                statsManager.recordPuzzleSolved()
                statsManager.applyPuzzleResult(currentPuzzle?.rating ?: return, solved = true)
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
            currentPuzzle?.let { statsManager.applyPuzzleResult(it.rating, solved = false) }
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

    companion object {
        // Kljucevi zive na `StatsManager`-u jer ih i `resetStats()` mora znati.
        // Dupliran literal bi znacio da izmena na jednom mestu tiho ugasi reset.
        private const val SOLVED_PUZZLE_IDS_KEY = StatsManager.SOLVED_PUZZLE_IDS_KEY
    }
}
