package com.veljkoni.chessko.logic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class StatsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("chessko_stats", Context.MODE_PRIVATE)

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

        prefs.edit().clear().apply()
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
        @Volatile
        private var instance: StatsManager? = null

        fun getInstance(context: Context): StatsManager {
            return instance ?: synchronized(this) {
                instance ?: StatsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
