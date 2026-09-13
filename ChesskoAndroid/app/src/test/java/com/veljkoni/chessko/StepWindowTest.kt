package com.veljkoni.chessko

import com.veljkoni.chessko.logic.PuzzleRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepWindowTest {

    @Test
    fun theStepRangeIntersectsThePlayerWindow() {
        // igrac 900 -> prozor 700..1000; korak 600..1200 -> presek 700..1000
        assertEquals(700..1000, PuzzleRating.stepWindow(900, 600..1200))
    }

    /**
     * Kad je presek prazan, prednost ima OPSEG KORAKA -- kurikulum zna sta se
     * uci, rejting je samo podesavanje (spec 5.4). Bez ovoga bi jak igrac dobio
     * prazan red i korak koji se ne moze zavrsiti.
     */
    @Test
    fun anEmptyIntersectionFallsBackToTheStepRange() {
        assertEquals(600..800, PuzzleRating.stepWindow(2000, 600..800))
    }

    @Test
    fun theResultIsNeverInverted() {
        for (r in listOf(80, 600, 900, 1500, 3000)) {
            for (range in listOf(600..800, 600..1200, 1100..2200)) {
                val w = PuzzleRating.stepWindow(r, range)
                assertEquals(true, w.first <= w.last)
            }
        }
    }
}
