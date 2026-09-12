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
    fun dailyPuzzleSurvivesNegativeDayIndex() {
        // `dayIndex` je broj dana od epohe i za datume PRE 1970. je negativan —
        // korisnik do njega stize strelicom „prethodni dan". Kotlinov `%` cuva
        // znak, pa bi naivan `dayIndex % n` dao NEGATIVAN offset.
        //
        // Ne sme se proveravati samo `!= null`: SQLite negativan `OFFSET`
        // tretira kao nulu (provereno), pa bi takav zadatak i dalje stizao —
        // samo bi SVI datumi pre epohe delili isti, prvi zadatak. Zato se
        // trazi tacno preslikavanje: dan -1 mora dati POSLEDNJI zadatak, a ne
        // isti kao dan 0.
        val r = repo()
        val last = (r.count() - 1).toLong()
        assertEquals(r.dailyPuzzle(last)!!.puzzleId, r.dailyPuzzle(-1)!!.puzzleId)
        assertTrue(r.dailyPuzzle(-1)!!.puzzleId != r.dailyPuzzle(0)!!.puzzleId)
        // Jedina vrednost kod koje bi unarni minus prelio; mora da prodje.
        assertNotNull(r.dailyPuzzle(Long.MIN_VALUE))
    }

    @Test
    fun hugeExclusionSetDoesNotThrow() {
        // `excluding` se vezuje kao JEDAN SQL parametar po id-ju. Skup od 1500
        // resenih zadataka je dostizan posle par meseci vezbanja, pa upit mora
        // da vrati zadatak umesto da pukne.
        //
        // Na OVOM emulatoru (API 36, SQLite 3.50) granica je 32.766 pa ni 1500
        // parametara ne puca — provereno. Na `minSdk 26` uredjajima granica je
        // 999, i tamo je pad stvaran. Zato test nosi i tvrdnju nad samom
        // granicom: ona vazi svuda, ne samo na uredjaju na kome se pokrece.
        assertTrue(
            "MAX_EXCLUDED mora ostati ispod SQLite granice od 999 parametara",
            PuzzleRepository.MAX_EXCLUDED <= 999
        )
        val many = (1..1500).map { "x$it" }.toSet()
        assertTrue(many.size > PuzzleRepository.MAX_EXCLUDED)
        assertNotNull(repo().randomPuzzle(600..2200, many))
    }

    @Test
    fun puzzleByIdRoundTrips() {
        val r = repo()
        val any = r.dailyPuzzle(12345)!!
        assertEquals(any.puzzleId, r.puzzle(any.puzzleId)!!.puzzleId)
    }
}
