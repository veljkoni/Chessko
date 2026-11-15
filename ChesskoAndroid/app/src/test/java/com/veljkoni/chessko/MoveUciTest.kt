package com.veljkoni.chessko

import com.veljkoni.chessko.models.ChessMove
import com.veljkoni.chessko.models.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MoveUciTest {
    @Test
    fun moveUciRoundTripIncludingPromotion() {
        val s = GameState.fromFEN("8/4P3/8/8/8/8/8/k6K w - - 0 1")
        assertNotNull(s)
        val m = ChessMove.fromUCI("e7e8q", s!!)
        assertNotNull(m)
        assertEquals("e7e8q", m!!.uci)
        val n = ChessMove.fromUCI("e7e8n", s)
        assertNotNull(n)
        assertEquals("e7e8n", n!!.uci)
        val start = GameState.initial()
        assertEquals("e2e4", ChessMove.fromUCI("e2e4", start)!!.uci)
    }
}
