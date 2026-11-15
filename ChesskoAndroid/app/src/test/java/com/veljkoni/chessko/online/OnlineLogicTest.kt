package com.veljkoni.chessko.online

import com.veljkoni.chessko.models.PieceColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLogicTest {
    private val game = ActiveGame("g", "t", PieceColor.WHITE, "ABCDEF")
    private fun playing(link: OnlineLink) = OnlineState.Playing(game, link, false, false, false)

    @Test fun reconnectNeededOnlyWhenStillWaiting() {
        assertTrue(OnlineLogic.needsConnection(OnlineState.Connecting, uiOpen = true, hasStoredGame = false))
        assertTrue(OnlineLogic.needsConnection(OnlineState.Connecting, uiOpen = false, hasStoredGame = true))
        assertFalse(OnlineLogic.needsConnection(OnlineState.Connecting, uiOpen = false, hasStoredGame = false))
        assertTrue(OnlineLogic.needsConnection(playing(OnlineLink.RECONNECTING), false, false))
        assertFalse(OnlineLogic.needsConnection(playing(OnlineLink.CONNECTED), true, true))
        assertFalse(OnlineLogic.needsConnection(OnlineState.Lobby, true, true))
        assertFalse(OnlineLogic.needsConnection(OnlineState.Ended(OnlineResult.BOARD), true, false))
        assertFalse(OnlineLogic.needsConnection(OnlineState.Idle, true, true))
    }

    @Test fun replaceRebuildsBoardUnlessSameOnlineColour() {
        assertTrue(OnlineLogic.replaceNeedsFreshBoard(false, PieceColor.WHITE, PieceColor.WHITE))
        assertTrue(OnlineLogic.replaceNeedsFreshBoard(true, PieceColor.BLACK, PieceColor.WHITE))
        assertFalse(OnlineLogic.replaceNeedsFreshBoard(true, PieceColor.WHITE, PieceColor.WHITE))
    }

    @Test fun openOnlyFromIdleWithoutSocket() {
        assertTrue(OnlineLogic.shouldOpenOnOpen(OnlineState.Idle, hasSocket = false))
        assertFalse(OnlineLogic.shouldOpenOnOpen(OnlineState.Idle, hasSocket = true))
        assertFalse(OnlineLogic.shouldOpenOnOpen(OnlineState.Lobby, hasSocket = false))
        assertFalse(OnlineLogic.shouldOpenOnOpen(playing(OnlineLink.CONNECTED), hasSocket = false))
    }

    @Test fun newCodeOnlyBeforeAnyGame() {
        assertTrue(OnlineLogic.canGenerateNewCode(OnlineState.Lobby, false))
        assertTrue(OnlineLogic.canGenerateNewCode(OnlineState.Idle, false))
        assertFalse(OnlineLogic.canGenerateNewCode(OnlineState.Lobby, true))
        assertFalse(OnlineLogic.canGenerateNewCode(OnlineState.Inviting("X"), false))
        assertFalse(OnlineLogic.canGenerateNewCode(playing(OnlineLink.CONNECTED), false))
        assertEquals(false, OnlineLogic.canGenerateNewCode(OnlineState.Ended(OnlineResult.ABORTED), false))
    }
}
