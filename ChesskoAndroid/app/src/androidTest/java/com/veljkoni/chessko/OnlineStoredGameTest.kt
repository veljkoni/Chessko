package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.online.ActiveGame
import com.veljkoni.chessko.online.ClockSpec
import com.veljkoni.chessko.online.OnlineViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sačuvana online partija (`online.activeGame`). Isto kao iOS
 * `activeGameDecodesOldSaveWithoutClock`. Instrumentisan jer JVM testovi nose samo stub `org.json`.
 */
@RunWith(AndroidJUnit4::class)
class OnlineStoredGameTest {
    private val game = ActiveGame("g1", "tok", PieceColor.WHITE, "ABCDEF")
    private val timedGame = ActiveGame("g2", "tok2", PieceColor.WHITE, "ABCDEF", ClockSpec.BLITZ)

    @Test fun activeGameDecodesOldSaveWithoutClock() {
        // Tačan oblik koji je pisala verzija pre protokola v2.
        val old = """{"game":"g1","token":"tok","color":"white","opponent":"ABCDEF"}"""
        val g = OnlineViewModel.decodeStored(old)
        assertEquals(game, g)
        assertNull(g?.clock)
        assertEquals(timedGame, OnlineViewModel.decodeStored(OnlineViewModel.encodeStored(timedGame)))
        assertEquals(game, OnlineViewModel.decodeStored(OnlineViewModel.encodeStored(game)))
    }

    @Test fun brokenClockDropsTheSave() {
        val broken = """{"game":"g2","token":"tok2","color":"white","opponent":"ABCDEF","clock":{"base":180}}"""
        assertNull(OnlineViewModel.decodeStored(broken))
    }
}
