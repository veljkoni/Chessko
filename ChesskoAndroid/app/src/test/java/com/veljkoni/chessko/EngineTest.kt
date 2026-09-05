package com.veljkoni.chessko

import com.veljkoni.chessko.logic.MoveGenerator
import com.veljkoni.chessko.models.GameState
import com.veljkoni.chessko.models.MoveFlag
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.models.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Perft i bihejvioralni testovi za MoveGenerator/GameState — port sa iOS strane.
 * Pokrivaju bag sa rokadom posle pojedenog topa (topa "iz vazduha").
 */
class EngineTest {

    private fun perft(state: GameState, depth: Int): Int {
        if (depth == 0) return 1
        val moves = MoveGenerator.legalMoves(state.currentTurn, state)
        if (depth == 1) return moves.size
        return moves.sumOf { perft(state.applyingForSearch(it), depth - 1) }
    }

    @Test
    fun perft_start() {
        val state = GameState.initial()
        assertEquals(20, perft(state, 1))
        assertEquals(400, perft(state, 2))
        assertEquals(8902, perft(state, 3))
        assertEquals(197281, perft(state, 4))
    }

    @Test
    fun perft_kiwipete() {
        val state = GameState.fromFEN("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")!!
        assertEquals(48, perft(state, 1))
        assertEquals(2039, perft(state, 2))
        assertEquals(97862, perft(state, 3))
    }

    @Test
    fun perft_pos3() {
        val state = GameState.fromFEN("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")!!
        assertEquals(14, perft(state, 1))
        assertEquals(191, perft(state, 2))
        assertEquals(2812, perft(state, 3))
        assertEquals(43238, perft(state, 4))
    }

    @Test
    fun perft_pos4() {
        val state = GameState.fromFEN("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")!!
        assertEquals(6, perft(state, 1))
        assertEquals(264, perft(state, 2))
        assertEquals(9467, perft(state, 3))
    }

    @Test
    fun perft_pos5() {
        val state = GameState.fromFEN("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")!!
        assertEquals(44, perft(state, 1))
        assertEquals(1486, perft(state, 2))
        assertEquals(62379, perft(state, 3))
    }

    @Test
    fun perft_pos6() {
        val state = GameState.fromFEN("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10")!!
        assertEquals(46, perft(state, 1))
        assertEquals(2079, perft(state, 2))
        assertEquals(89890, perft(state, 3))
    }

    @Test
    fun rookCapturedOnHomeSquareRevokesCastlingRight() {
        // Crni skakač sa f2 uzima topa na h1 — belom mora nestati pravo na malu rokadu.
        val state = GameState.fromFEN("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R b KQ - 1 8")!!
        val move = com.veljkoni.chessko.models.ChessMove(Position(6, 5), Position(7, 7))
        val after = state.applying(move)

        assertFalse(after.whiteCanCastleKingside)
        val whiteMoves = MoveGenerator.legalMoves(PieceColor.WHITE, after)
        assertTrue(whiteMoves.none { it.flag is MoveFlag.CastleKingside })
    }

    @Test
    fun noRookOnCornerMeansNoCastleEvenIfFlagStale() {
        // Stara sačuvana partija može da nosi zastarelo pravo rokade bez topa u uglu.
        val state = GameState.fromFEN("4k3/8/8/8/8/8/8/4K3 w K - 0 1")!!
        val moves = MoveGenerator.legalMoves(PieceColor.WHITE, state)
        assertTrue(moves.none { it.flag is MoveFlag.CastleKingside })
    }

    @Test
    fun allFourCornersRevokeOnCapture() {
        run {
            val state = GameState.fromFEN("r3k3/8/8/8/8/8/8/R3K3 b Qq - 0 1")!!
            val move = com.veljkoni.chessko.models.ChessMove(Position(0, 0), Position(7, 0))
            val after = state.applying(move)
            assertFalse(after.whiteCanCastleQueenside)
        }
        run {
            val state = GameState.fromFEN("4k2r/8/8/8/8/8/8/4K2R w Kk - 0 1")!!
            val move = com.veljkoni.chessko.models.ChessMove(Position(7, 7), Position(0, 7))
            val after = state.applying(move)
            assertFalse(after.blackCanCastleKingside)
        }
        run {
            val state = GameState.fromFEN("r3k3/8/8/8/8/8/8/R3K3 w Qq - 0 1")!!
            val move = com.veljkoni.chessko.models.ChessMove(Position(7, 0), Position(0, 0))
            val after = state.applying(move)
            assertFalse(after.blackCanCastleQueenside)
        }
    }
}
