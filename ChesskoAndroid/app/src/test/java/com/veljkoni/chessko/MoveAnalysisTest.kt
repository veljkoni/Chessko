package com.veljkoni.chessko

import com.veljkoni.chessko.models.AnalyzedMove
import com.veljkoni.chessko.models.EngineScore
import com.veljkoni.chessko.models.GameAnalysis
import com.veljkoni.chessko.models.MoveClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

// Prenosi iOS `Tests/ChesskoEngineTests/MoveAnalysisTests.swift` (bez UCI parser
// dela, koji nije deo ovog taska). Iste tvrdnje, isti razlozi.

class MoveAnalysisTest {

    // MARK: - EngineScore

    @Test
    fun centipawnScorePassesThrough() {
        assertEquals(0, EngineScore.Cp(0).centipawns)
        assertEquals(250, EngineScore.Cp(250).centipawns)
        assertEquals(-80, EngineScore.Cp(-80).centipawns)
    }

    @Test
    fun mateScoreMapsFarAboveAnyCentipawnScore() {
        // Mat u 1 mora biti bolji od mata u 2, a oba daleko iznad bilo koje
        // realne centipionske ocene (koja se u praksi drzi unutar +-5000).
        assertTrue(EngineScore.Mate(1).centipawns > EngineScore.Mate(2).centipawns)
        assertTrue(EngineScore.Mate(2).centipawns > 5000)
        assertTrue(EngineScore.Mate(-1).centipawns < EngineScore.Mate(-2).centipawns)
        assertTrue(EngineScore.Mate(-2).centipawns < -5000)
    }

    @Test fun mateInOneIs9999() = assertEquals(9999, EngineScore.Mate(1).centipawns)
    @Test fun mateInTwoIs9998() = assertEquals(9998, EngineScore.Mate(2).centipawns)
    @Test fun mateAgainstInOneIsMinus9999() = assertEquals(-9999, EngineScore.Mate(-1).centipawns)

    @Test
    fun mateDistanceIsClampedAtNinetyNine() {
        // Mat dalji od 99 poteza se ne preliva u drugu stranu.
        assertEquals(10_000 - 99, EngineScore.Mate(150).centipawns)
    }

    // MARK: - cpLoss

    @Test
    fun cpLossIsBeforePlusAfterBecausePerspectiveFlips() {
        // `before` je iz ugla igraca koji vuce potez; `after` je iz ugla PROTIVNIKA,
        // jer je posle poteza on na potezu. Zato se sabiraju, ne oduzimaju.
        // Bio +50 za mene, posle poteza +50 za protivnika => izgubio sam 100.
        assertEquals(100, GameAnalysis.cpLoss(EngineScore.Cp(50), EngineScore.Cp(50)))
        // Bio +50 za mene, posle poteza -50 za protivnika (= +50 za mene) => nista.
        assertEquals(0, GameAnalysis.cpLoss(EngineScore.Cp(50), EngineScore.Cp(-50)))
    }

    @Test
    fun cpLossNeverGoesNegative() {
        // Pretraga na fiksnoj dubini ume da oceni poziciju POSLE poteza bolje nego
        // onu pre nje. To nije "negativan gubitak" nego nula.
        assertEquals(0, GameAnalysis.cpLoss(EngineScore.Cp(10), EngineScore.Cp(-200)))
        assertEquals(0, GameAnalysis.cpLoss(EngineScore.Cp(30), EngineScore.Cp(-30)))
    }

    @Test
    fun cpLossIsCappedSoOneMissedMateCannotEatTheWholeGame() {
        // Propusten mat daje razliku od ~20.000 centipiona. Bez granice bi jedan
        // takav potez sam odredio prosek cele partije.
        val loss = GameAnalysis.cpLoss(EngineScore.Mate(1), EngineScore.Cp(0))
        assertEquals(1000, loss)
        assertTrue(loss > 300) // i dalje uredno iznad praga za promasaj
    }

    @Test
    fun cpLossOfTwoMatesIsCappedAtOneThousand() {
        assertEquals(1000, GameAnalysis.cpLoss(EngineScore.Mate(1), EngineScore.Mate(1)))
    }

    // MARK: - Klasifikacija (pragovi doslovno iz spec-a 5.5)

    @Test
    fun classificationFollowsSpecThresholds() {
        assertEquals(MoveClass.BEST, MoveClass.classify(0, true))
        assertEquals(MoveClass.EXCELLENT, MoveClass.classify(0, false))
        assertEquals(MoveClass.EXCELLENT, MoveClass.classify(19, false))
        assertEquals(MoveClass.GOOD, MoveClass.classify(20, false))
        assertEquals(MoveClass.GOOD, MoveClass.classify(49, false))
        assertEquals(MoveClass.INACCURACY, MoveClass.classify(50, false))
        assertEquals(MoveClass.INACCURACY, MoveClass.classify(99, false))
        assertEquals(MoveClass.MISTAKE, MoveClass.classify(100, false))
        assertEquals(MoveClass.MISTAKE, MoveClass.classify(299, false))
        assertEquals(MoveClass.BLUNDER, MoveClass.classify(300, false))
        assertEquals(MoveClass.BLUNDER, MoveClass.classify(999, false))
    }

    @Test
    fun engineBestMoveWinsOverThresholdsEvenWithSmallLoss() {
        // Potez motora je po definiciji najbolji, i kad merenje da mali gubitak
        // (razlicite dubine daju razlicite ocene iste pozicije).
        assertEquals(MoveClass.BEST, MoveClass.classify(15, true))
        assertEquals(MoveClass.BEST, MoveClass.classify(900, true))
    }

    // MARK: - Procenat tacnosti (formula doslovno iz spec-a)

    @Test
    fun accuracyMatchesSpecFormula() {
        // 103.1668 * exp(-0.04354 * x) - 3.1669
        assertTrue(abs(GameAnalysis.accuracy(0.0) - 99.9999) < 0.01)
        assertTrue(abs(GameAnalysis.accuracy(10.0) - 63.5) < 0.5)
        assertTrue(abs(GameAnalysis.accuracy(50.0) - 8.5) < 0.5)
    }

    @Test
    fun accuracyIsClampedToZeroAndHundred() {
        // Formula za veliko x ide ispod nule (-3.1669 u limitu).
        assertEquals(0.0, GameAnalysis.accuracy(1000.0), 0.0)
        assertTrue(GameAnalysis.accuracy(0.0) <= 100.0)
        assertEquals(100.0, GameAnalysis.accuracy(-5.0), 0.0)
    }

    @Test
    fun accuracyFallsAsAverageLossRises() {
        val a = GameAnalysis.accuracy(5.0)
        val b = GameAnalysis.accuracy(25.0)
        val c = GameAnalysis.accuracy(80.0)
        assertTrue(a > b)
        assertTrue(b > c)
    }

    // MARK: - Sklapanje cele analize

    @Test
    fun buildPairsScoresIntoMovesAndSplitsBySide() {
        // 3 poteza => 4 ocene. Potezi 0 i 2 su beli, potez 1 je crni.
        val analysis = GameAnalysis.build(
            notations = listOf("e4", "e5", "Sf3"),
            scores = listOf(
                EngineScore.Cp(20), EngineScore.Cp(-20),
                EngineScore.Cp(20), EngineScore.Cp(-20)
            ),
            engineBestMatched = listOf(true, true, true)
        )
        assertEquals(3, analysis.moves.size)
        assertTrue(analysis.moves[0].byWhite)
        assertTrue(!analysis.moves[1].byWhite)
        assertTrue(analysis.moves[2].byWhite)
        assertEquals("e4", analysis.moves[0].notation)
        assertTrue(analysis.moves.all { it.cpLoss == 0 })
        // Savrsena igra NE daje tacno 100: formula iz spec-a u nuli daje 99.9999
        // (103.1668 - 3.1669). To nije greska nego oblik same formule, i korisnik
        // ionako vidi "100.0%" jer se prikazuje na jednu decimalu. Zato tolerancija,
        // a ne `== 100` — inace bi test terao da se formula tiho odvoji od spec-a.
        assertTrue(analysis.whiteAccuracy > 99.99)
        assertTrue(analysis.blackAccuracy > 99.99)
    }

    @Test
    fun buildComputesEachSideAccuracyFromOnlyThatSideMoves() {
        // Beli igra savrseno, crni gubi po 200 centipiona po potezu.
        //
        // Ocene se moraju izabrati tako da SVAKI beli potez ima gubitak 0, ne samo
        // prvi: gubitak poteza `i` je `scores[i] + scores[i+1]`, pa jedna ocena
        // ulazi u DVA susedna poteza. Niz [0, 0, 200, -200, 400] daje belom 0 i 0,
        // a crnom 200 i 200.
        val analysis = GameAnalysis.build(
            notations = listOf("e4", "a5", "Sf3", "b5"),
            scores = listOf(
                EngineScore.Cp(0), EngineScore.Cp(0), EngineScore.Cp(200),
                EngineScore.Cp(-200), EngineScore.Cp(400)
            ),
            engineBestMatched = listOf(false, false, false, false)
        )
        assertEquals(0, analysis.moves[0].cpLoss) // beli
        assertEquals(200, analysis.moves[1].cpLoss) // crni
        assertEquals(0, analysis.moves[2].cpLoss) // beli
        assertEquals(200, analysis.moves[3].cpLoss) // crni
        assertTrue(analysis.whiteAccuracy > 99.99)
        assertTrue(analysis.whiteAccuracy > analysis.blackAccuracy)
    }

    @Test
    fun turningPointIsTheBiggestLossButOnlyIfItActuallyHurts() {
        val clean = GameAnalysis.build(
            notations = listOf("e4", "e5"),
            scores = listOf(EngineScore.Cp(0), EngineScore.Cp(-5), EngineScore.Cp(5)),
            engineBestMatched = listOf(false, false)
        )
        // Najveci gubitak je ~5 centipiona — to nije prelomni potez ni u jednoj partiji.
        assertNull(clean.turningPoint)

        val withBlunder = GameAnalysis.build(
            notations = listOf("e4", "e5", "Lc4"),
            scores = listOf(
                EngineScore.Cp(0), EngineScore.Cp(0), EngineScore.Cp(0), EngineScore.Cp(400)
            ),
            engineBestMatched = listOf(false, false, false)
        )
        assertEquals("Lc4", withBlunder.turningPoint?.notation)
        assertEquals(400, withBlunder.turningPoint?.cpLoss)
    }

    @Test
    fun turningPointNeverPicksAMoveTheEngineItselfWouldPlay() {
        // `classify` daje `.best` cim je odigran potez motora, i kad izmeren
        // gubitak ispadne velik (dve nezavisne pretrage se ne poklope do
        // centipiona). Takav potez ne sme da bude proglasen prelomnim — kartica bi
        // ga prikazala kao najveci gubitak, a obojila kao najbolji potez.
        val analysis = GameAnalysis.build(
            notations = listOf("Sxd7", "Kh8"),
            scores = listOf(EngineScore.Cp(0), EngineScore.Cp(1000), EngineScore.Cp(-800)),
            engineBestMatched = listOf(true, false)
        )
        assertEquals(1000, analysis.moves[0].cpLoss)
        assertEquals(MoveClass.BEST, analysis.moves[0].moveClass)
        assertEquals(200, analysis.moves[1].cpLoss)
        // Prelomni je drugi potez, iako je njegov gubitak PETOSTRUKO manji.
        assertEquals("Kh8", analysis.turningPoint?.notation)
    }

    @Test
    fun turningPointIsNullWhenEveryLosingMoveWasTheEngineMove() {
        val analysis = GameAnalysis.build(
            notations = listOf("Sxd7"),
            scores = listOf(EngineScore.Cp(0), EngineScore.Cp(1000)),
            engineBestMatched = listOf(true)
        )
        assertNull(analysis.turningPoint)
    }

    @Test
    fun buildRejectsMismatchedInputLengthsInsteadOfCrashing() {
        // N poteza trazi tacno N+1 ocena. Neslaganje je greska pozivaoca i mora
        // da vrati praznu analizu, ne da srusi proces indeksiranjem van granica.
        val bad = GameAnalysis.build(
            notations = listOf("e4", "e5"),
            scores = listOf(EngineScore.Cp(0), EngineScore.Cp(0)),
            engineBestMatched = listOf(false, false)
        )
        assertTrue(bad.moves.isEmpty())
        assertNull(bad.turningPoint)
    }

    /** Prazna partija je JEDINI slucaj u kome je tacnost tacno 100. */
    @Test
    fun emptyGameProducesEmptyAnalysisWithFullAccuracy() {
        val empty = GameAnalysis.build(
            notations = emptyList(),
            scores = listOf(EngineScore.Cp(0)),
            engineBestMatched = emptyList()
        )
        assertTrue(empty.moves.isEmpty())
        assertEquals(100.0, empty.whiteAccuracy, 0.0)
        assertEquals(100.0, empty.blackAccuracy, 0.0)
    }

    // MARK: - AnalyzedMove prikaz

    @Test
    fun displayNotationUsesEllipsisForBlackMoves() {
        val white = AnalyzedMove(
            ply = 0, notation = "e4", byWhite = true, cpLoss = 0,
            moveClass = MoveClass.BEST, scoreBefore = EngineScore.Cp(0),
            scoreAfter = EngineScore.Cp(0)
        )
        val black = AnalyzedMove(
            ply = 1, notation = "e5", byWhite = false, cpLoss = 0,
            moveClass = MoveClass.BEST, scoreBefore = EngineScore.Cp(0),
            scoreAfter = EngineScore.Cp(0)
        )
        assertEquals("1.e4", white.displayNotation)
        assertEquals("1…e5", black.displayNotation)
        assertEquals(1, white.moveNumber)
        assertEquals(1, black.moveNumber)
    }
}
