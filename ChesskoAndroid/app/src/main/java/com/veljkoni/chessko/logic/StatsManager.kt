package com.veljkoni.chessko.logic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class StatsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("chessko_stats", Context.MODE_PRIVATE)

    /// Napredak na zadacima istorijski zivi u DRUGOM fajlu, koji `PuzzleViewModel`
    /// otvara pod istim imenom. Drzi se ovde da bi `resetStats()` mogao da ga
    /// ocisti; `context` je samo konstruktorski parametar i nije dostupan kasnije.
    private val puzzlePrefs: SharedPreferences =
        context.getSharedPreferences(PUZZLE_PREFS_NAME, Context.MODE_PRIVATE)

    var gamesPlayed by mutableIntStateOf(prefs.getInt("gamesPlayed", 0))
        private set
    var gamesWon by mutableIntStateOf(prefs.getInt("gamesWon", 0))
        private set
    var gamesLost by mutableIntStateOf(prefs.getInt("gamesLost", 0))
        private set
    var gamesDrawn by mutableIntStateOf(prefs.getInt("gamesDrawn", 0))
        private set

    var currentWinStreak by mutableIntStateOf(prefs.getInt("currentWinStreak", 0))
        private set
    var bestWinStreak by mutableIntStateOf(prefs.getInt("bestWinStreak", 0))
        private set

    var winsBeginner by mutableIntStateOf(prefs.getInt("winsBeginner", 0))
        private set
    var winsEasy by mutableIntStateOf(prefs.getInt("winsEasy", 0))
        private set
    var winsMedium by mutableIntStateOf(prefs.getInt("winsMedium", 0))
        private set
    var winsHard by mutableIntStateOf(prefs.getInt("winsHard", 0))
        private set
    var winsStockfish by mutableIntStateOf(prefs.getInt("winsStockfish", 0))
        private set

    var puzzlesSolved by mutableIntStateOf(prefs.getInt("puzzlesSolved", 0))
        private set
    var currentPuzzleStreak by mutableIntStateOf(prefs.getInt("currentPuzzleStreak", 0))
        private set
    var bestPuzzleStreak by mutableIntStateOf(prefs.getInt("bestPuzzleStreak", 0))
        private set

    // Elo rejting igraca za zadatke. Pocetna vrednost je PuzzleRating.START,
    // ne 0 — bez toga bi nov korisnik dobijao samo najlakse zadatke dok se ne
    // popne, a `resetStats()` bi ga vratio na nulu umesto na pocetak.
    var puzzleRating by mutableIntStateOf(prefs.getInt("puzzleRating", PuzzleRating.START))
        private set

    fun applyPuzzleResult(puzzleElo: Int, solved: Boolean) {
        val next = PuzzleRating.newRating(this.puzzleRating, puzzleElo, solved)
        this.puzzleRating = next
        prefs.edit().putInt("puzzleRating", next).apply()
    }

    val winRate: Int
        get() = if (gamesPlayed > 0) ((gamesWon.toDouble() / gamesPlayed) * 100).toInt() else 0

    fun recordGameWon(difficulty: GameDifficulty) {
        gamesPlayed++
        gamesWon++
        currentWinStreak++
        if (currentWinStreak > bestWinStreak) {
            bestWinStreak = currentWinStreak
        }
        when (difficulty) {
            GameDifficulty.BEGINNER -> winsBeginner++
            GameDifficulty.EASY -> winsEasy++
            GameDifficulty.MEDIUM -> winsMedium++
            GameDifficulty.HARD -> winsHard++
            GameDifficulty.STOCKFISH -> winsStockfish++
        }
        saveGameStats()
    }

    fun recordGameLost() {
        gamesPlayed++
        gamesLost++
        currentWinStreak = 0
        saveGameStats()
    }

    fun recordGameDrawn() {
        gamesPlayed++
        gamesDrawn++
        saveGameStats()
    }

    fun recordPuzzleSolved() {
        puzzlesSolved++
        currentPuzzleStreak++
        if (currentPuzzleStreak > bestPuzzleStreak) {
            bestPuzzleStreak = currentPuzzleStreak
        }
        savePuzzleStats()
    }

    fun recordPuzzleFailed() {
        currentPuzzleStreak = 0
        savePuzzleStats()
    }

    fun resetStats() {
        gamesPlayed = 0
        gamesWon = 0
        gamesLost = 0
        gamesDrawn = 0
        currentWinStreak = 0
        bestWinStreak = 0
        winsBeginner = 0
        winsEasy = 0
        winsMedium = 0
        winsHard = 0
        winsStockfish = 0
        puzzlesSolved = 0
        currentPuzzleStreak = 0
        bestPuzzleStreak = 0
        puzzleRating = PuzzleRating.START

        prefs.edit().clear().apply()

        // `clear()` iznad cisti SAMO `chessko_stats`. Napredak na zadacima je u
        // `chessko_puzzle_prefs`, pa bi bez ovoga „Resetuj statistiku" obrisala
        // brojace ali ostavila sve resene zadatke iskljucene iz vezbanja —
        // korisnik bi posle reseta dobijao samo zadatke koje jos nije video.
        // iOS isto radi (`StatsManager.resetStats`, `removeObject` za oba kljuca).
        puzzlePrefs.edit()
            .remove(SOLVED_PUZZLE_IDS_KEY)
            .remove(SOLVED_DATES_KEY)
            .apply()
    }

    private fun saveGameStats() {
        prefs.edit()
            .putInt("gamesPlayed", gamesPlayed)
            .putInt("gamesWon", gamesWon)
            .putInt("gamesLost", gamesLost)
            .putInt("gamesDrawn", gamesDrawn)
            .putInt("currentWinStreak", currentWinStreak)
            .putInt("bestWinStreak", bestWinStreak)
            .putInt("winsBeginner", winsBeginner)
            .putInt("winsEasy", winsEasy)
            .putInt("winsMedium", winsMedium)
            .putInt("winsHard", winsHard)
            .putInt("winsStockfish", winsStockfish)
            .apply()
    }

    private fun savePuzzleStats() {
        prefs.edit()
            .putInt("puzzlesSolved", puzzlesSolved)
            .putInt("currentPuzzleStreak", currentPuzzleStreak)
            .putInt("bestPuzzleStreak", bestPuzzleStreak)
            .apply()
    }

    companion object {
        /// Fajl u kome `PuzzleViewModel` cuva napredak na zadacima. Odvojen od
        /// `chessko_stats` istorijski, pa ga `resetStats()` mora cistiti posebno.
        internal const val PUZZLE_PREFS_NAME = "chessko_puzzle_prefs"
        internal const val SOLVED_PUZZLE_IDS_KEY = "solvedPuzzleIds"
        internal const val SOLVED_DATES_KEY = "solved_dates_key"

        @Volatile
        private var instance: StatsManager? = null

        fun getInstance(context: Context): StatsManager {
            return instance ?: synchronized(this) {
                instance ?: StatsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
