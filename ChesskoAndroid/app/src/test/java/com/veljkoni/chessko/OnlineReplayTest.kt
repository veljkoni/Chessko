package com.veljkoni.chessko

import com.veljkoni.chessko.online.replayUci
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineReplayTest {
    private val withPromotion = listOf(
        "h2h4", "g7g5", "h4g5", "h7h6", "g5h6", "g8f6", "h6h7", "h8g8", "h7g8q",
    )

    @Test
    fun replaysPromotionAndKeepsFullHistory() {
        val r = replayUci(withPromotion)
        assertEquals(9, r.applied.size)
        assertEquals(9, r.state.moveHistory.size)
        assertEquals(9, r.history.size)
        assertEquals("h7g8q", r.applied.last())
    }

    @Test
    fun stopsAtIllegalMoveWithoutCrashing() {
        val r = replayUci(listOf("e2e4", "e7e5", "e2e4", "zzzz", "d2d4"))
        assertEquals(listOf("e2e4", "e7e5"), r.applied)
        assertEquals(2, r.state.moveHistory.size)
    }
}
