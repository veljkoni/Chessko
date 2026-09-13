package com.veljkoni.chessko

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.ProgressSnapshot
import com.veljkoni.chessko.logic.ProgressStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProgressStoreTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var file: File

    @Before fun setUp() {
        dir = File(context.cacheDir, "progress-test-${System.nanoTime()}").apply { mkdirs() }
        file = File(dir, "progress.json")
    }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun prefs(name: String) =
        context.getSharedPreferences("test_$name-${System.nanoTime()}", Context.MODE_PRIVATE)

    @Test
    fun progressSurvivesANewInstance() {
        val p = prefs("survive")
        ProgressStore(context, file, p).completeStep("basics-lesson")
        val reopened = ProgressStore(context, file, p)
        assertTrue(reopened.snapshot.completedSteps.contains("basics-lesson"))
    }

    @Test
    fun completingTheSameStepTwiceCountsOnce() {
        val p = prefs("twice")
        val s = ProgressStore(context, file, p)
        s.completeStep("x"); s.completeStep("x")
        assertEquals(1, s.snapshot.completedSteps.size)
        assertEquals(1, s.snapshot.stepsCompletedByDay.values.sum())
    }

    /**
     * Prvo pokretanje posle nadogradnje preuzima statistiku iz SharedPreferences
     * i OSTAVLJA je tamo netaknutu — povratak na stariju verziju mora da radi.
     */
    @Test
    fun oldStatsAreMigratedAndLeftInPlace() {
        val p = prefs("migrate")
        p.edit().putInt("gamesPlayed", 7).putInt("gamesWon", 5)
            .putInt("puzzlesSolved", 42).putInt("puzzleRating", 1234)
            .putInt("winsHard", 3).apply()

        val s = ProgressStore(context, file, p)
        assertEquals(7, s.snapshot.gamesPlayed)
        assertEquals(5, s.snapshot.gamesWon)
        assertEquals(42, s.snapshot.puzzlesSolved)
        assertEquals(1234, s.snapshot.puzzleRating)
        assertEquals(3, s.snapshot.winsHard)

        assertEquals(7, p.getInt("gamesPlayed", -1))
        assertEquals(1234, p.getInt("puzzleRating", -1))
    }

    @Test
    fun aFirstRunWithNoOldStatsStartsRatingAt800() {
        assertEquals(800, ProgressStore(context, file, prefs("fresh")).snapshot.puzzleRating)
    }

    /**
     * Shema sme da raste. Stariji fajl bez novih kljuceva mora da se procita, a
     * nova polja da dobiju podrazumevanu vrednost. Sa `getInt` umesto `optInt`
     * ovo bi bacilo, fajl bi bio proglasen pokvarenim i CEO napredak bi nestao.
     */
    @Test
    fun anOlderFileMissingNewKeysStillLoads() {
        file.writeText("""{"version":1,"completedSteps":["basics-lesson"]}""")
        val s = ProgressStore(context, file, prefs("older"))
        assertTrue(s.snapshot.completedSteps.contains("basics-lesson"))
        assertEquals(800, s.snapshot.puzzleRating)
        assertEquals(0, s.snapshot.gamesPlayed)
    }

    /**
     * Pokvaren fajl se ODLAZE u stranu, ne gazi. To je korisnikov fajl na
     * uredjaju i sme da se osteti prekinutim upisom — aplikacija mora da se
     * oporavi, a delimicno ostecen napredak da ostane dostupan za rucno
     * spasavanje.
     */
    @Test
    fun aCorruptFileIsSetAsideNotOverwritten() {
        file.writeText("{ ovo nije json")
        ProgressStore(context, file, prefs("corrupt"))
        assertTrue(File(file.path + ".corrupt").exists())
        assertEquals("{ ovo nije json", File(file.path + ".corrupt").readText())
    }

    @Test
    fun goalAndStreakReadFromTheSnapshot() {
        val p = prefs("goal")
        val s = ProgressStore(context, file, p)
        assertFalse(s.goalMetToday)
        s.completeStep("basics-lesson")
        assertTrue(s.goalMetToday)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun threeSolvedPuzzlesMeetTheDailyGoal() {
        val s = ProgressStore(context, file, prefs("puzzles"))
        s.recordPuzzleSolvedToday(); s.recordPuzzleSolvedToday()
        assertFalse(s.goalMetToday)
        s.recordPuzzleSolvedToday()
        assertTrue(s.goalMetToday)
    }

    @Test
    fun snapshotRoundTripsThroughJson() {
        val a = ProgressSnapshot(
            completedSteps = setOf("a", "b"),
            stepCompletionDates = mapOf("a" to "2026-09-13"),
            stepsCompletedByDay = mapOf("2026-09-13" to 2),
            puzzlesSolvedByDay = mapOf("2026-09-13" to 5),
            gamesPlayed = 3, gamesWon = 2, gamesLost = 1, gamesDrawn = 0,
            currentWinStreak = 2, bestWinStreak = 4,
            winsBeginner = 1, winsEasy = 1, winsMedium = 0, winsHard = 0, winsStockfish = 0,
            puzzlesSolved = 9, currentPuzzleStreak = 3, bestPuzzleStreak = 6,
            puzzleRating = 912
        )
        assertEquals(a, ProgressSnapshot.fromJson(a.toJson()))
    }
}
