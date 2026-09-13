package com.veljkoni.chessko.logic

import android.content.Context
import android.content.SharedPreferences

/**
 * Od Faze 6c ovo je FASADA nad `ProgressStore`-om, ne skladiste.
 *
 * Zadrzava svaki potpis koji je imala, pa njena pozivna mesta nisu dirana.
 * Sme da se ukloni, ali to znaci dirati svako od njih.
 */
class StatsManager private constructor(context: Context) {

    private val store = ProgressStore.getInstance(context)

    /**
     * Napredak na zadacima istorijski zivi u DRUGOM fajlu, koji
     * `PuzzleViewModel` otvara pod istim imenom. Drzi se ovde da bi
     * `resetStats()` mogao da ga ocisti.
     */
    private val puzzlePrefs: SharedPreferences =
        context.getSharedPreferences(PUZZLE_PREFS_NAME, Context.MODE_PRIVATE)

    val gamesPlayed: Int get() = store.snapshot.gamesPlayed
    val gamesWon: Int get() = store.snapshot.gamesWon
    val gamesLost: Int get() = store.snapshot.gamesLost
    val gamesDrawn: Int get() = store.snapshot.gamesDrawn
    val currentWinStreak: Int get() = store.snapshot.currentWinStreak
    val bestWinStreak: Int get() = store.snapshot.bestWinStreak
    val winsBeginner: Int get() = store.snapshot.winsBeginner
    val winsEasy: Int get() = store.snapshot.winsEasy
    val winsMedium: Int get() = store.snapshot.winsMedium
    val winsHard: Int get() = store.snapshot.winsHard
    val winsStockfish: Int get() = store.snapshot.winsStockfish
    val puzzlesSolved: Int get() = store.snapshot.puzzlesSolved
    val currentPuzzleStreak: Int get() = store.snapshot.currentPuzzleStreak
    val bestPuzzleStreak: Int get() = store.snapshot.bestPuzzleStreak
    val puzzleRating: Int get() = store.snapshot.puzzleRating

    val winRate: Int
        get() = if (gamesPlayed > 0) ((gamesWon.toDouble() / gamesPlayed) * 100).toInt() else 0

    fun applyPuzzleResult(puzzleElo: Int, solved: Boolean) {
        val next = PuzzleRating.newRating(puzzleRating, puzzleElo, solved)
        store.updateStats { it.copy(puzzleRating = next) }
    }

    fun recordGameWon(difficulty: GameDifficulty) = store.updateStats {
        val streak = it.currentWinStreak + 1
        it.copy(
            gamesPlayed = it.gamesPlayed + 1,
            gamesWon = it.gamesWon + 1,
            currentWinStreak = streak,
            bestWinStreak = maxOf(it.bestWinStreak, streak),
            winsBeginner = it.winsBeginner + if (difficulty == GameDifficulty.BEGINNER) 1 else 0,
            winsEasy = it.winsEasy + if (difficulty == GameDifficulty.EASY) 1 else 0,
            winsMedium = it.winsMedium + if (difficulty == GameDifficulty.MEDIUM) 1 else 0,
            winsHard = it.winsHard + if (difficulty == GameDifficulty.HARD) 1 else 0,
            winsStockfish = it.winsStockfish + if (difficulty == GameDifficulty.STOCKFISH) 1 else 0
        )
    }

    fun recordGameLost() = store.updateStats {
        it.copy(gamesPlayed = it.gamesPlayed + 1, gamesLost = it.gamesLost + 1, currentWinStreak = 0)
    }

    fun recordGameDrawn() = store.updateStats {
        it.copy(gamesPlayed = it.gamesPlayed + 1, gamesDrawn = it.gamesDrawn + 1)
    }

    /**
     * Uz brojace upisuje i DNEVNI CILJ: tri resena zadatka ispunjavaju cilj
     * isto kao jedan zavrsen korak (spec 5.4). Bez ovog reda bi streak rastao
     * samo onima koji prolaze Put.
     */
    fun recordPuzzleSolved() {
        store.updateStats {
            val streak = it.currentPuzzleStreak + 1
            it.copy(
                puzzlesSolved = it.puzzlesSolved + 1,
                currentPuzzleStreak = streak,
                bestPuzzleStreak = maxOf(it.bestPuzzleStreak, streak)
            )
        }
        store.recordPuzzleSolvedToday()
    }

    fun recordPuzzleFailed() = store.updateStats { it.copy(currentPuzzleStreak = 0) }

    /**
     * „Resetuj statistiku" NE dira Put: `completedSteps`,
     * `stepCompletionDates` i `stepsCompletedByDay` ostaju. Napredak nije
     * statistika (spec 5.4, isto kao iOS).
     */
    fun resetStats() {
        store.updateStats {
            it.copy(
                gamesPlayed = 0, gamesWon = 0, gamesLost = 0, gamesDrawn = 0,
                currentWinStreak = 0, bestWinStreak = 0,
                winsBeginner = 0, winsEasy = 0, winsMedium = 0, winsHard = 0, winsStockfish = 0,
                puzzlesSolved = 0, currentPuzzleStreak = 0, bestPuzzleStreak = 0,
                puzzleRating = PuzzleRating.START,
                puzzlesSolvedByDay = emptyMap()
            )
        }
        puzzlePrefs.edit()
            .remove(SOLVED_PUZZLE_IDS_KEY)
            .remove(SOLVED_DATES_KEY)
            .apply()
    }

    companion object {
        internal const val PUZZLE_PREFS_NAME = "chessko_puzzle_prefs"
        internal const val SOLVED_PUZZLE_IDS_KEY = "solvedPuzzleIds"
        internal const val SOLVED_DATES_KEY = "solved_dates_key"

        @Volatile private var instance: StatsManager? = null

        fun getInstance(context: Context): StatsManager =
            instance ?: synchronized(this) {
                instance ?: StatsManager(context.applicationContext).also { instance = it }
            }
    }
}
