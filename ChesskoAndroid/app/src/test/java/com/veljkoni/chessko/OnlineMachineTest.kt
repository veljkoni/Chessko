package com.veljkoni.chessko

import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.online.ActiveGame
import com.veljkoni.chessko.online.ClientMessage
import com.veljkoni.chessko.online.OnlineCloseConfirm
import com.veljkoni.chessko.online.OnlineEffect
import com.veljkoni.chessko.online.OnlineError
import com.veljkoni.chessko.online.OnlineEvent
import com.veljkoni.chessko.online.OnlineLink
import com.veljkoni.chessko.online.OnlineMachine
import com.veljkoni.chessko.online.OnlineNotice
import com.veljkoni.chessko.online.OnlineResult
import com.veljkoni.chessko.online.OnlineState
import com.veljkoni.chessko.online.ServerMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port iOS OnlineMachineTests, ista namera, ista redosled. */
class OnlineMachineTest {
    private val game = ActiveGame("g1", "tok", PieceColor.WHITE, "ABCDEF")

    private fun playing(
        link: OnlineLink = OnlineLink.CONNECTED, away: Boolean = false,
        dBy: Boolean = false, dMe: Boolean = false,
    ) = OnlineState.Playing(game, link, away, dBy, dMe)

    private fun machine(state: OnlineState, stored: ActiveGame? = null): OnlineMachine {
        val m = OnlineMachine("K7M4QX", stored)
        m.state = state
        return m
    }

    private fun rx(m: ServerMessage) = OnlineEvent.Received(m)
    private fun send(m: ClientMessage) = OnlineEffect.Send(m)
    private val noFx = emptyList<OnlineEffect>()

    @Test fun socketOpenedSendsHello() {
        val m = machine(OnlineState.Idle)
        assertEquals(listOf(send(ClientMessage.Hello("K7M4QX"))), m.handle(OnlineEvent.SocketOpened))
        assertEquals(OnlineState.Connecting, m.state)
    }

    @Test fun socketOpenedWithStoredSendsHelloThenResume() {
        val m = machine(OnlineState.Idle, game)
        assertEquals(
            listOf(send(ClientMessage.Hello("K7M4QX")), send(ClientMessage.Resume("g1", "tok"))),
            m.handle(OnlineEvent.SocketOpened),
        )
    }

    @Test fun socketOpenedAcceptedWhileReconnecting() {
        val m = machine(playing(OnlineLink.RECONNECTING), game)
        assertEquals(2, m.handle(OnlineEvent.SocketOpened).size)
    }

    @Test fun welcomeWithoutStoredGoesToLobby() {
        val m = machine(OnlineState.Connecting)
        assertEquals(noFx, m.handle(rx(ServerMessage.Welcome("K7M4QX"))))
        assertEquals(OnlineState.Lobby, m.state)
    }

    @Test fun welcomeWithStoredStaysConnectingUntilResumed() {
        val m = machine(OnlineState.Connecting, game)
        m.handle(rx(ServerMessage.Welcome("K7M4QX")))
        assertEquals(OnlineState.Connecting, m.state)
    }

    @Test fun resumedGoesToPlayingAndReplacesMoves() {
        val m = machine(OnlineState.Connecting, game)
        val fx = m.handle(rx(ServerMessage.Resumed("g1", PieceColor.WHITE, listOf("e2e4"), false)))
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ReplaceMoves(listOf("e2e4"))), fx)
        assertEquals(playing(OnlineLink.CONNECTED, away = true), m.state)
    }

    @Test fun resumeAfterLongAbsenceAppliesMissingMoves() {
        val m = machine(playing(OnlineLink.RECONNECTING), game)
        val moves = listOf("e2e4", "e7e5", "g1f3")
        val fx = m.handle(rx(ServerMessage.Resumed("g1", PieceColor.WHITE, moves, true)))
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ReplaceMoves(moves)), fx)
    }

    @Test fun gameGoneWithStoredPersistsNilAndEndsAborted() {
        val m = machine(OnlineState.Connecting, game)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(rx(ServerMessage.Error(OnlineError.GAME_GONE))))
        assertEquals(OnlineState.Ended(OnlineResult.ABORTED), m.state)
        assertNull(m.stored)
    }

    @Test fun socketClosedInPlayingReconnectsWithBackoff() {
        val m = machine(playing(), game)
        val secs = mutableListOf<Int>()
        repeat(7) {
            val first = m.handle(OnlineEvent.SocketClosed).firstOrNull()
            if (first is OnlineEffect.ScheduleReconnect) secs.add(first.seconds)
        }
        assertEquals(listOf(1, 2, 4, 8, 15, 15, 15), secs)
        assertEquals(playing(OnlineLink.RECONNECTING), m.state)
    }

    @Test fun backoffResetsAfterResumed() {
        val m = machine(playing(), game)
        m.handle(OnlineEvent.SocketClosed); m.handle(OnlineEvent.SocketClosed)
        m.handle(rx(ServerMessage.Resumed("g1", PieceColor.WHITE, emptyList(), true)))
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ScheduleReconnect(1)), m.handle(OnlineEvent.SocketClosed))
    }

    @Test fun backoffResetsAfterWelcome() {
        val m = machine(OnlineState.Lobby)
        m.handle(OnlineEvent.SocketClosed); m.handle(OnlineEvent.SocketClosed)
        m.handle(rx(ServerMessage.Welcome("K7M4QX")))
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ScheduleReconnect(1)), m.handle(OnlineEvent.SocketClosed))
    }

    @Test fun appBecameActiveWhileReconnectingConnects() {
        val m = machine(playing(OnlineLink.RECONNECTING), game)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Connect), m.handle(OnlineEvent.AppBecameActive))
    }

    @Test fun appBecameActiveInLobbyAndEndedDoesNothing() {
        val a = machine(OnlineState.Lobby); assertEquals(noFx, a.handle(OnlineEvent.AppBecameActive))
        val b = machine(OnlineState.Ended(OnlineResult.BOARD)); assertEquals(noFx, b.handle(OnlineEvent.AppBecameActive))
    }

    @Test fun socketClosedInLobbyGoesConnecting() {
        val m = machine(OnlineState.Lobby)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ScheduleReconnect(1)), m.handle(OnlineEvent.SocketClosed))
        assertEquals(OnlineState.Connecting, m.state)
    }

    @Test fun codeTakenWithStoredIsIgnored() {
        val m = machine(OnlineState.Connecting, game)
        assertEquals(noFx, m.handle(rx(ServerMessage.Error(OnlineError.CODE_TAKEN))))
        assertEquals(OnlineState.Connecting, m.state)
    }

    @Test fun codeTakenWithoutStoredSchedulesReconnect() {
        val m = machine(OnlineState.Connecting)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ScheduleReconnect(1)), m.handle(rx(ServerMessage.Error(OnlineError.CODE_TAKEN))))
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ScheduleReconnect(2)), m.handle(rx(ServerMessage.Error(OnlineError.CODE_TAKEN))))
        assertEquals(OnlineState.Connecting, m.state)
    }

    @Test fun startPersistsAndStartsGame() {
        val m = machine(OnlineState.Inviting("ABCDEF"))
        val fx = m.handle(rx(ServerMessage.Start("g1", PieceColor.WHITE, "tok", "ABCDEF")))
        assertEquals(listOf(OnlineEffect.Persist(game), OnlineEffect.StartGame(game)), fx)
        assertEquals(game, m.stored)
    }

    @Test fun endingsPersistNilAndEnd() {
        val persistNil = listOf<OnlineEffect>(OnlineEffect.Persist(null))
        val r = machine(playing(), game)
        assertEquals(persistNil, r.handle(rx(ServerMessage.Resigned))); assertEquals(OnlineState.Ended(OnlineResult.OPPONENT_RESIGNED), r.state)
        val d = machine(playing(), game)
        assertEquals(persistNil, d.handle(rx(ServerMessage.DrawReply(true)))); assertEquals(OnlineState.Ended(OnlineResult.DRAW_AGREED), d.state)
        val a = machine(playing(), game)
        assertEquals(persistNil, a.handle(rx(ServerMessage.Aborted))); assertEquals(OnlineState.Ended(OnlineResult.ABORTED), a.state)
        val b = machine(playing(), game)
        assertEquals(listOf(send(ClientMessage.Finished), OnlineEffect.Persist(null)), b.handle(OnlineEvent.BoardFinished))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), b.state)
        val s = machine(playing(), game)
        assertEquals(listOf(send(ClientMessage.Resign), OnlineEffect.Persist(null)), s.handle(OnlineEvent.Resign))
        assertEquals(OnlineState.Ended(OnlineResult.I_RESIGNED), s.state)
        assertNull(s.stored)
    }

    @Test fun incomingInviteInLobbyGoesToInvited() {
        val m = machine(OnlineState.Lobby)
        assertEquals(noFx, m.handle(rx(ServerMessage.Invited("ABCDEF"))))
        assertEquals(OnlineState.Invited("ABCDEF"), m.state)
    }

    @Test fun inviteWhileInvitedSendsNothing() {
        val m = machine(OnlineState.Invited("ABCDEF"))
        assertEquals(noFx, m.handle(OnlineEvent.Invite("XYZ234")))
        assertEquals(OnlineState.Invited("ABCDEF"), m.state)
    }

    @Test fun inviteInLobbySendsInvite() {
        val m = machine(OnlineState.Lobby)
        assertEquals(listOf(send(ClientMessage.Invite("ABCDEF"))), m.handle(OnlineEvent.Invite("ABCDEF")))
        assertEquals(OnlineState.Inviting("ABCDEF"), m.state)
    }

    @Test fun remoteMoveIsAppliedOnlyInPlaying() {
        val p = machine(playing(), game)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ApplyRemoteMove(1, "e7e5")), p.handle(rx(ServerMessage.Move(1, "e7e5"))))
        val l = machine(OnlineState.Lobby)
        assertEquals(noFx, l.handle(rx(ServerMessage.Move(1, "e7e5"))))
    }

    @Test fun localMoveSendsMoveWithCallerPly() {
        val m = machine(playing(), game)
        assertEquals(listOf(send(ClientMessage.Move(4, "e2e4"))), m.handle(OnlineEvent.LocalMove(4, "e2e4")))
    }

    @Test fun syncReplacesMoves() {
        val m = machine(playing(), game)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ReplaceMoves(listOf("e2e4"))), m.handle(rx(ServerMessage.Sync(listOf("e2e4")))))
    }

    @Test fun drawOfferThenDeclineClearsFlagAndShowsNotice() {
        val m = machine(playing(), game)
        assertEquals(listOf(send(ClientMessage.DrawOffer)), m.handle(OnlineEvent.OfferDraw))
        assertEquals(playing(dMe = true), m.state)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ShowMessage(OnlineNotice.DRAW_DECLINED)), m.handle(rx(ServerMessage.DrawReply(false))))
        assertEquals(playing(), m.state)
    }

    // I3: greška verzije je kraj za sesiju — bez ponovnog povezivanja, trajno „ažuriraj".
    @Test fun versionErrorRequiresUpdateAndStopsTheMachine() {
        val m = machine(OnlineState.Connecting)
        assertFalse(m.updateRequired)
        assertEquals(noFx, m.handle(rx(ServerMessage.Error(OnlineError.VERSION))))
        assertTrue(m.updateRequired)
        assertEquals(noFx, m.handle(OnlineEvent.SocketClosed))       // nema ScheduleReconnect
        assertEquals(noFx, m.handle(OnlineEvent.AppBecameActive))    // nema Connect
        assertEquals(noFx, m.handle(OnlineEvent.SocketOpened))       // nema Hello
        assertEquals(noFx, m.handle(rx(ServerMessage.Welcome("K7M4QX"))))
        assertEquals(OnlineState.Connecting, m.state)
        assertTrue(m.updateRequired)
    }

    @Test fun versionErrorWithStoredGameIgnoresResumedAndBlocksActions() {
        val m = machine(playing(OnlineLink.RECONNECTING), game)
        assertEquals(noFx, m.handle(rx(ServerMessage.Error(OnlineError.VERSION))))
        assertEquals(noFx, m.handle(rx(ServerMessage.Resumed("g1", PieceColor.WHITE, listOf("e2e4"), true))))
        assertFalse(m.canAct)
        assertEquals(noFx, m.handle(OnlineEvent.Resign))
        assertEquals(noFx, m.handle(OnlineEvent.BackToLobby))
        assertEquals(game, m.stored)
    }

    // I1: kraj koji je server sačuvao dok je ova strana bila odsutna stiže odmah posle `resumed`.
    @Test fun resignedRightAfterResumedEndsOpponentResigned() {
        val m = machine(playing(OnlineLink.RECONNECTING), game)
        assertEquals(
            listOf<OnlineEffect>(OnlineEffect.ReplaceMoves(listOf("e2e4"))),
            m.handle(rx(ServerMessage.Resumed("g1", PieceColor.WHITE, listOf("e2e4"), false))),
        )
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(rx(ServerMessage.Resigned)))
        assertEquals(OnlineState.Ended(OnlineResult.OPPONENT_RESIGNED), m.state)
        assertNull(m.stored)
    }

    @Test fun drawAcceptRightAfterResumedEndsDrawEvenThoughOfferFlagWasReset() {
        // Ova strana je ponudila remi pa izgubila vezu; `resumed` briše zastavicu ponude.
        val m = machine(playing(OnlineLink.RECONNECTING, dMe = true), game)
        m.handle(rx(ServerMessage.Resumed("g1", PieceColor.WHITE, emptyList(), false)))
        assertEquals(playing(OnlineLink.CONNECTED, away = true), m.state)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(rx(ServerMessage.DrawReply(true))))
        assertEquals(OnlineState.Ended(OnlineResult.DRAW_AGREED), m.state)
    }

    @Test fun resumedAfterRelaunchThenResignedEnds() {
        val m = machine(OnlineState.Idle, game)
        m.handle(OnlineEvent.SocketOpened)
        m.handle(rx(ServerMessage.Welcome("K7M4QX")))
        m.handle(rx(ServerMessage.Resumed("g1", PieceColor.WHITE, listOf("e2e4"), false)))
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(rx(ServerMessage.Resigned)))
        assertEquals(OnlineState.Ended(OnlineResult.OPPONENT_RESIGNED), m.state)
    }

    @Test fun boardFinishedAfterResumedSendsFinishedAndEnds() {
        // Mat/pat iz liste poteza: VM posle `ReplaceMoves` javlja BoardFinished.
        val m = machine(playing(OnlineLink.RECONNECTING), game)
        m.handle(rx(ServerMessage.Resumed("g1", PieceColor.WHITE, listOf("f2f3", "e7e5", "g2g4", "d8h4"), false)))
        assertEquals(listOf(send(ClientMessage.Finished), OnlineEffect.Persist(null)), m.handle(OnlineEvent.BoardFinished))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
        // server je partiju već obrisao: game_gone posle kraja ne menja ništa
        assertEquals(noFx, m.handle(rx(ServerMessage.Error(OnlineError.GAME_GONE))))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
    }

    // I4: varijanta potvrde zatvaranja je zamrznuta pri otvaranju.
    @Test fun closeConfirmVariantIsCapturedAndResignNeedsLiveLink() {
        assertEquals(OnlineCloseConfirm.RESIGN, OnlineCloseConfirm.of(true))
        assertEquals(OnlineCloseConfirm.GAME_WAITS, OnlineCloseConfirm.of(false))
        assertTrue(OnlineCloseConfirm.RESIGN.resignsOnConfirm(true))
        assertFalse(OnlineCloseConfirm.RESIGN.resignsOnConfirm(false))
        assertFalse(OnlineCloseConfirm.GAME_WAITS.resignsOnConfirm(true))
        assertFalse(OnlineCloseConfirm.GAME_WAITS.resignsOnConfirm(false))
    }

    @Test fun actionsAreRefusedWhileReconnecting() {
        val events = listOf(
            OnlineEvent.LocalMove(0, "e2e4"), OnlineEvent.Resign, OnlineEvent.OfferDraw,
            OnlineEvent.AnswerDraw(true), OnlineEvent.AnswerDraw(false),
        )
        for (e in events) {
            val m = machine(playing(OnlineLink.RECONNECTING, dBy = true), game)
            assertEquals(noFx, m.handle(e))
            assertEquals(playing(OnlineLink.RECONNECTING, dBy = true), m.state)
            assertEquals(game, m.stored)
        }
    }

    @Test fun canActOnlyWhenConnectedPlaying() {
        assertTrue(machine(playing(OnlineLink.CONNECTED)).canAct)
        assertFalse(machine(playing(OnlineLink.RECONNECTING)).canAct)
        assertFalse(machine(OnlineState.Lobby).canAct)
    }

    @Test fun boardFinishedWhileReconnectingEndsWithoutSend() {
        val m = machine(playing(OnlineLink.RECONNECTING), game)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(OnlineEvent.BoardFinished))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
    }

    @Test fun backToLobbyFromEndedWithLiveSocketGoesToLobby() {
        val m = machine(OnlineState.Idle)
        m.handle(OnlineEvent.SocketOpened)
        m.handle(rx(ServerMessage.Welcome("K7M4QX")))
        m.state = OnlineState.Ended(OnlineResult.BOARD)
        assertEquals(noFx, m.handle(OnlineEvent.BackToLobby))
        assertEquals(OnlineState.Lobby, m.state)
    }

    @Test fun backToLobbyAfterSocketClosedInEndedReconnects() {
        val m = machine(OnlineState.Idle)
        m.handle(OnlineEvent.SocketOpened)
        m.state = OnlineState.Ended(OnlineResult.BOARD)
        assertEquals(noFx, m.handle(OnlineEvent.SocketClosed))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Connect), m.handle(OnlineEvent.BackToLobby))
        assertEquals(OnlineState.Connecting, m.state)
    }

    @Test fun backToLobbyIgnoredOutsideEnded() {
        val m = machine(playing(), game)
        assertEquals(noFx, m.handle(OnlineEvent.BackToLobby))
        assertEquals(playing(), m.state)
    }

    @Test fun socketOpenedInEndedSendsHelloOnly() {
        val m = machine(OnlineState.Ended(OnlineResult.BOARD))
        assertEquals(listOf(send(ClientMessage.Hello("K7M4QX"))), m.handle(OnlineEvent.SocketOpened))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
    }

    @Test fun endedSocketOpenedWelcomeBackToLobby() {
        val m = machine(OnlineState.Ended(OnlineResult.BOARD))
        m.handle(OnlineEvent.SocketOpened)
        assertEquals(noFx, m.handle(rx(ServerMessage.Welcome("K7M4QX"))))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
        assertEquals(noFx, m.handle(OnlineEvent.BackToLobby))
        assertEquals(OnlineState.Lobby, m.state)
    }

    @Test fun endedSocketOpenedBeforeWelcomeBackToLobbyWaitsForWelcomeWithoutSecondHello() {
        val m = machine(OnlineState.Ended(OnlineResult.BOARD))
        assertEquals(listOf(send(ClientMessage.Hello("K7M4QX"))), m.handle(OnlineEvent.SocketOpened))
        assertEquals(noFx, m.handle(OnlineEvent.BackToLobby))
        assertEquals(OnlineState.Connecting, m.state)
        assertEquals(noFx, m.handle(rx(ServerMessage.Welcome("K7M4QX"))))
        assertEquals(OnlineState.Lobby, m.state)
    }
}
