package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.GameDifficulty
import com.veljkoni.chessko.logic.PathProgress
import com.veljkoni.chessko.logic.ProgressStore
import com.veljkoni.chessko.logic.StatsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsFacadeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Fasada i skladiste moraju da gledaju ISTI snimak. Da fasada zadrzi
     * sopstvenu kopiju, ekran statistike i ekran Puta pokazivali bi razlicite
     * brojeve, a jedan od njih bi se pri restartu "vratio unazad".
     */
    @Test
    fun facadeWritesReachTheStore() {
        val stats = StatsManager.getInstance(context)
        val store = ProgressStore.getInstance(context)
        val before = store.snapshot.gamesPlayed
        stats.recordGameWon(GameDifficulty.EASY)
        assertEquals(before + 1, store.snapshot.gamesPlayed)
        assertEquals(store.snapshot.gamesPlayed, stats.gamesPlayed)
    }

    /**
     * Dnevni cilj se puni i sa taba Zadaci, ne samo iz Puta: tri resena
     * zadatka ispunjavaju cilj (spec 5.4). Bez ovoga bi streak rastao samo
     * onima koji prolaze Put.
     */
    @Test
    fun solvingAPuzzleCountsTowardTheDailyGoal() {
        val stats = StatsManager.getInstance(context)
        val store = ProgressStore.getInstance(context)
        val day = com.veljkoni.chessko.logic.PathProgress.dayKey()
        val before = store.snapshot.puzzlesSolvedByDay[day] ?: 0
        stats.recordPuzzleSolved()
        assertEquals(before + 1, store.snapshot.puzzlesSolvedByDay[day])
    }

    /**
     * „Resetuj statistiku" NE dira Put. Brise brojace, ali zavrseni koraci,
     * streak i istorija dnevnog cilja ostaju -- napredak nije statistika.
     */
    @Test
    fun resetClearsCountersButKeepsThePath() {
        val stats = StatsManager.getInstance(context)
        val store = ProgressStore.getInstance(context)
        store.completeStep("basics-lesson")
        stats.recordGameWon(GameDifficulty.HARD)

        stats.resetStats()

        assertEquals(0, stats.gamesPlayed)
        assertEquals(0, stats.winsHard)
        assertEquals(PuzzleRatingStart, stats.puzzleRating)
        assertEquals(true, store.snapshot.completedSteps.contains("basics-lesson"))
    }

    /**
     * Dan u kome je cilj ispunjen ISKLJUCIVO zadacima (bez koraka Puta) mora da
     * prezivi „Resetuj statistiku". Streak se racuna iz unije stepsCompletedByDay
     * i puzzlesSolvedByDay, pa bi brisanje druge mape tiho pojelo niz koji
     * CLAUDE.md izricito obecava da ostaje.
     */
    @Test
    fun resetKeepsDailyGoalHistoryEarnedOnlyByPuzzles() {
        val stats = StatsManager.getInstance(context)
        val store = ProgressStore.getInstance(context)
        val day = PathProgress.dayKey()
        repeat(3) { stats.recordPuzzleSolved() }
        assertTrue(store.goalMetToday)

        stats.resetStats()

        assertEquals(0, stats.puzzlesSolved)              // brojac jeste obrisan
        assertTrue(store.goalMetToday)                    // ali cilj za danas stoji
        assertTrue((store.snapshot.puzzlesSolvedByDay[day] ?: 0) >= 3)
    }

    private val PuzzleRatingStart get() = com.veljkoni.chessko.logic.PuzzleRating.START
}
