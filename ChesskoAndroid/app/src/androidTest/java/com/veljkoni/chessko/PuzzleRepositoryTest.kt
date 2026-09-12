package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.PuzzleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PuzzleRepositoryTest {

    private fun repo(): PuzzleRepository =
        PuzzleRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun databaseShipsWithExactlyTwentyThousandPuzzles() {
        assertEquals(20000, repo().count())
    }

    @Test
    fun everyPuzzleHasNonEmptyFenAndAtLeastOneMove() {
        // Prolazi CELU bazu, ne uzorak. Uzorak od 500 redova hvata pokvaren
        // red u ~2,5% pokretanja, sto znaci prakticno nikad.
        var checked = 0
        for (p in repo().allPuzzlesOrderedById()) {
            assertTrue("prazan FEN: ${p.puzzleId}", p.fen.isNotBlank())
            assertTrue("bez poteza: ${p.puzzleId}", p.uciMoves.isNotEmpty())
            assertTrue("rejting van opsega: ${p.puzzleId}", p.rating in 600..2200)
            checked++
        }
        assertEquals(20000, checked)
    }

    @Test
    fun dailyPuzzleIsDeterministicForTheSameDay() {
        val r = repo()
        val a = r.dailyPuzzle(20000)
        val b = r.dailyPuzzle(20000)
        assertNotNull(a)
        assertEquals(a!!.puzzleId, b!!.puzzleId)
    }

    @Test
    fun differentDaysGiveDifferentPuzzles() {
        val r = repo()
        assertTrue(r.dailyPuzzle(20000)!!.puzzleId != r.dailyPuzzle(20001)!!.puzzleId)
    }

    @Test
    fun randomPuzzleRespectsRatingRange() {
        val p = repo().randomPuzzle(800..1000, emptySet())
        assertNotNull(p)
        assertTrue(p!!.rating in 800..1000)
    }

    @Test
    fun randomPuzzleRespectsExclusionSet() {
        val r = repo()
        val first = r.randomPuzzle(800..1000, emptySet())!!
        val second = r.randomPuzzle(800..1000, setOf(first.puzzleId))
        assertNotNull(second)
        assertTrue(second!!.puzzleId != first.puzzleId)
    }

    @Test
    fun emptyRangeReturnsNullInsteadOfThrowing() {
        // Prazan presek se u aplikaciji desava (rejting igraca ume da odluta),
        // i mora da vrati null, ne da srusi ekran.
        assertEquals(null, repo().randomPuzzle(2300..2400, emptySet()))
    }

    @Test
    fun puzzleByIdRoundTrips() {
        val r = repo()
        val any = r.dailyPuzzle(12345)!!
        assertEquals(any.puzzleId, r.puzzle(any.puzzleId)!!.puzzleId)
    }
}
