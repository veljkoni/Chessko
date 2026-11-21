package com.veljkoni.chessko

import com.veljkoni.chessko.logic.BoardCoordinates
import com.veljkoni.chessko.models.Position
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Izdanje 2.4: koordinate table (slova a–h, brojevi 1–8) moraju stajati na DONJOJ i
 * LEVOJ ivici ekrana i kad je tabla okrenuta (igra crnim).
 *
 * Bug pre popravke: formula je prepisana sa iOS-a, koji okreće tablu tako što je crta
 * normalno pa celu rotira za 180° (`Chessko/Views/BoardView.swift:82-83`, `:130`).
 * Android ne rotira — redove i kolone obilazi obrnutim redom — pa su kod okrenute
 * table slova završavala na GORNJEM redu, a brojevi na DESNOJ koloni.
 *
 * Testovi su u koordinatama TABLE (`Position`), jer to je ono što oznaka i ispisuje.
 */
class BoardCoordinatesTest {

    private fun bottomEdge(flipped: Boolean) =
        allSquares().filter { BoardCoordinates.isBottomEdge(it, flipped) }.toSet()

    private fun leftEdge(flipped: Boolean) =
        allSquares().filter { BoardCoordinates.isLeftEdge(it, flipped) }.toSet()

    private fun allSquares() = (0..7).flatMap { r -> (0..7).map { c -> Position(r, c) } }

    @Test
    fun notFlippedFilesOnRankOneAndRanksOnFileA() {
        // Red 7 = 1. red (beli), kolona 0 = linija a.
        assertEquals((0..7).map { Position(7, it) }.toSet(), bottomEdge(flipped = false))
        assertEquals((0..7).map { Position(it, 0) }.toSet(), leftEdge(flipped = false))
    }

    @Test
    fun flippedFilesOnRankEightAndRanksOnFileH() {
        // Okrenuta tabla: dole je 8. red (red 0), levo je linija h (kolona 7).
        assertEquals((0..7).map { Position(0, it) }.toSet(), bottomEdge(flipped = true))
        assertEquals((0..7).map { Position(it, 7) }.toSet(), leftEdge(flipped = true))
    }

    @Test
    fun bottomLeftCornerCarriesBothLabels() {
        // Ugao dole-levo: a1 kad igra beli, h8 kad igra crni.
        assertEquals(true, BoardCoordinates.isBottomEdge(Position(7, 0), false))
        assertEquals(true, BoardCoordinates.isLeftEdge(Position(7, 0), false))
        assertEquals(true, BoardCoordinates.isBottomEdge(Position(0, 7), true))
        assertEquals(true, BoardCoordinates.isLeftEdge(Position(0, 7), true))
    }

    @Test
    fun displayIndicesMapToTheScreenEdgesIndependentOfFlip() {
        // Isto pravilo izraženo u koordinatama EKRANA: Android crta red po red odozgo,
        // pa je donja ivica uvek prikazni red 7 a leva uvek prikazna kolona 0, bez
        // obzira na okretanje.
        for (flipped in listOf(false, true)) {
            for (displayRow in 0..7) for (displayCol in 0..7) {
                val pos = Position(
                    if (flipped) 7 - displayRow else displayRow,
                    if (flipped) 7 - displayCol else displayCol
                )
                assertEquals("flipped=$flipped $pos", displayRow == 7, BoardCoordinates.isBottomEdge(pos, flipped))
                assertEquals("flipped=$flipped $pos", displayCol == 0, BoardCoordinates.isLeftEdge(pos, flipped))
            }
        }
    }
}
