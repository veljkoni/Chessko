package com.veljkoni.chessko

import com.veljkoni.chessko.logic.PuzzleRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleRatingTest {

    // Vrednosti su iste one koje iOS vec ima u RatingTests.swift.
    // E = 1/(1 + 10^((Rp-R)/400)), R' = R + 32*(S-E)
    @Test fun solvedEqualRatingGains16() =
        assertEquals(816, PuzzleRating.newRating(800, 800, true))

    @Test fun failedEqualRatingLoses16() =
        assertEquals(784, PuzzleRating.newRating(800, 800, false))

    @Test fun solvedHarderPuzzleGainsMore() =
        assertEquals(832, PuzzleRating.newRating(800, 1600, true))

    @Test fun failedEasierPuzzleLosesMore() =
        assertEquals(771, PuzzleRating.newRating(800, 400, false))

    @Test
    fun gainStaysPositiveAndNeverGrowsAcrossManySolves() {
        // Svojstvo koje Elo garantuje: prirast je pozitivan i NE raste.
        // `<=`, ne `<` — celobrojno zaokruzivanje pravi platoe (16, 16, 15...),
        // pa bi strogo opadanje palo iako je formula ispravna.
        var r = 800
        var prev = Int.MAX_VALUE
        repeat(100) {
            val next = PuzzleRating.newRating(r, 800, true)
            val gain = next - r
            assertTrue("prirast mora biti pozitivan, bio je $gain", gain > 0)
            assertTrue("prirast ne sme da raste", gain <= prev)
            prev = gain
            r = next
        }
        assertTrue("posle 100 resenih rejting je $r", r in 1200..1400)
    }

    @Test
    fun practiceWindowNeverInvertsForLowRatings() {
        // Rejting igraca nije ogranicen odozdo; dug niz neuspeha ga vodi ka ~80.
        // Naivan prozor bi tada bio -120..180 (baza pocinje od 600), a klampovanje
        // SAMO donje granice dalo bi 600..180 — obrnut opseg.
        val w = PuzzleRating.practiceWindow(80)
        assertTrue("opseg ne sme biti obrnut: $w", w.first <= w.last)
        assertEquals(PuzzleRating.MIN, w.first)
    }

    @Test
    fun practiceWindowForRatingFarAboveCeilingDoesNotInvertAndStaysAboveFloor() {
        // Naivno: (3000-200)..(3000+100) = 2800..3100 — iznad baze u celosti.
        // `lo` (2800) je vec iznad MAX (2200) PRE nego sto se `hi` uopste
        // klampuje, pa formula ispravno vraca 2800..2800 (validan, ne-prazan
        // IntRange koji jednostavno ne pogadja nijedan red u bazi — na to se
        // oslanja progresivno prosirenje prozora u pozivaocu, isto kao na iOS-u:
        // vidi `practiceRatingWindowForRatingFarAboveCeilingDoesNotTrapAndStaysValid`
        // u `Tests/ChesskoEngineTests/PuzzleRepositoryTests.swift`).
        // Bitno je SAMO da ne pukne/obrne se pri kreiranju — NE da ostane <= MAX;
        // originalna verzija ovog testa je to tvrdila i pogresno padala.
        val w = PuzzleRating.practiceWindow(3000)
        assertTrue(w.first <= w.last)
        assertTrue(w.first >= PuzzleRating.MIN)
    }

    @Test
    fun practiceWindowIsCenteredBelowThePlayer() {
        // -200 / +100: zadaci malo ispod rejtinga se cesce pogadjaju, pa je
        // vezbanje prijatnije nego kad je prozor centriran.
        assertEquals(1000..1300, PuzzleRating.practiceWindow(1200))
    }
}
