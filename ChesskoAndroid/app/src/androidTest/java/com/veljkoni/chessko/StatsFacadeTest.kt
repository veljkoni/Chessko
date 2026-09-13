package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.GameDifficulty
import com.veljkoni.chessko.logic.ProgressStore
import com.veljkoni.chessko.logic.StatsManager
import org.junit.Assert.assertEquals
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

    private val PuzzleRatingStart get() = com.veljkoni.chessko.logic.PuzzleRating.START
}
