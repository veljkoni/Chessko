package com.veljkoni.chessko

import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.online.ActiveGame
import com.veljkoni.chessko.online.ClientMessage
import com.veljkoni.chessko.online.ClockReading
import com.veljkoni.chessko.online.ClockSpec
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
import com.veljkoni.chessko.online.WireClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port iOS OnlineMachineTests, ista namera, ista redosled. */
class OnlineMachineTest {
    private val game = ActiveGame("g1", "tok", PieceColor.WHITE, "ABCDEF")

    private fun playing(
        link: OnlineLink = OnlineLink.CONNECTED, away: Boolean = false,
        dBy: Boolean = false, dMe: Boolean = false,
    ) = OnlineState.Playing(game, link, away, dBy, dMe, clock = null)

    private fun machine(state: OnlineState, stored: ActiveGame? = null): OnlineMachine {
        val m = OnlineMachine("K7M4QX", stored)
        m.state = state
        return m
    }

    /** Dolazna poruka bez važnog vremena prijema (partije bez sata). */
    private fun rx(m: ServerMessage) = OnlineEvent.Received(m, 0.0)
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
        assertEquals(OnlineState.Invited("ABCDEF", null), m.state)
    }

    @Test fun inviteWhileInvitedSendsNothing() {
        val m = machine(OnlineState.Invited("ABCDEF", null))
        assertEquals(noFx, m.handle(OnlineEvent.Invite("XYZ234", null)))
        assertEquals(OnlineState.Invited("ABCDEF", null), m.state)
    }

    @Test fun inviteInLobbySendsInvite() {
        val m = machine(OnlineState.Lobby)
        assertEquals(listOf(send(ClientMessage.Invite("ABCDEF"))), m.handle(OnlineEvent.Invite("ABCDEF", null)))
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
        assertEquals(listOf(send(ClientMessage.Move(4, "e2e4"))), m.handle(OnlineEvent.LocalMove(4, "e2e4", false)))
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

    // Partija na vreme: bez predaje dijalog kaže da sat i dalje teče; sa vezom se i dalje nudi predaja.
    @Test fun closeConfirmInTimedGameSaysTheClockRuns() {
        assertEquals(OnlineCloseConfirm.CLOCK_RUNS, OnlineCloseConfirm.of(false, timed = true))
        assertEquals(OnlineCloseConfirm.RESIGN, OnlineCloseConfirm.of(true, timed = true))
        assertEquals(OnlineCloseConfirm.GAME_WAITS, OnlineCloseConfirm.of(false, timed = false))
        assertFalse(OnlineCloseConfirm.CLOCK_RUNS.resignsOnConfirm(true))
        assertFalse(OnlineCloseConfirm.CLOCK_RUNS.resignsOnConfirm(false))
    }

    @Test fun actionsAreRefusedWhileReconnecting() {
        val events = listOf(
            OnlineEvent.LocalMove(0, "e2e4", false), OnlineEvent.Resign, OnlineEvent.OfferDraw,
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

    // Pozadina: eksplicitan prekid veze (isto kao iOS)
    @Test fun backgroundThenActiveReconnectsFromPlaying() {
        val m = machine(playing(), game)
        // pozadina: veza pada, mašina traži reconnect (VM ga ne zakazuje dok je u pozadini)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.ScheduleReconnect(1)), m.handle(OnlineEvent.SocketClosed))
        assertEquals(playing(OnlineLink.RECONNECTING), m.state)
        // povratak: odmah connect, bez čekanja backoff-a
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Connect), m.handle(OnlineEvent.AppBecameActive))
    }

    // ── Partija na vreme (protokol v2) ──────────────────────────────────

    private val timedGame = ActiveGame("g2", "tok2", PieceColor.WHITE, "ABCDEF", ClockSpec.BLITZ)

    private fun wire(
        w: Int = 180_000, b: Int = 180_000, run: PieceColor? = null, first: Int? = 30_000,
    ) = WireClock(ClockSpec.BLITZ, w, b, run, first)

    private fun timedPlaying(
        link: OnlineLink = OnlineLink.CONNECTED, away: Boolean = false,
        clock: ClockReading? = ClockReading(wire(), 100.0),
    ) = OnlineState.Playing(timedGame, link, away, drawOfferedByOpponent = false, drawOfferedByMe = false, clock = clock)

    private fun readingOf(s: OnlineState): ClockReading? = (s as? OnlineState.Playing)?.clock

    @Test fun invitedCarriesClockFromMessage() {
        val m = machine(OnlineState.Lobby)
        assertEquals(noFx, m.handle(rx(ServerMessage.Invited("ABCDEF", ClockSpec.RAPID))))
        assertEquals(OnlineState.Invited("ABCDEF", ClockSpec.RAPID), m.state)
    }

    @Test fun acceptSendsTheSameClockAsTheInvite() {
        val timed = machine(OnlineState.Invited("ABCDEF", ClockSpec.BLITZ))
        assertEquals(listOf(send(ClientMessage.Accept("ABCDEF", ClockSpec.BLITZ))), timed.handle(OnlineEvent.Accept))
        val untimed = machine(OnlineState.Invited("ABCDEF", null))
        assertEquals(listOf(send(ClientMessage.Accept("ABCDEF", null))), untimed.handle(OnlineEvent.Accept))
    }

    @Test fun inviteWithClockSendsClock() {
        val m = machine(OnlineState.Lobby)
        assertEquals(
            listOf(send(ClientMessage.Invite("ABCDEF", ClockSpec.RAPID))),
            m.handle(OnlineEvent.Invite("ABCDEF", ClockSpec.RAPID)),
        )
        assertEquals(OnlineState.Inviting("ABCDEF"), m.state)
    }

    @Test fun startWithClockSetsReadingAndStoresSpec() {
        val m = machine(OnlineState.Inviting("ABCDEF"))
        val w = wire()
        val fx = m.handle(OnlineEvent.Received(ServerMessage.Start("g2", PieceColor.WHITE, "tok2", "ABCDEF", w), 42.5))
        assertEquals(listOf(OnlineEffect.Persist(timedGame), OnlineEffect.StartGame(timedGame)), fx)
        assertEquals(timedGame, m.stored)
        assertEquals(ClockSpec.BLITZ, m.stored?.clock)
        assertEquals(timedPlaying(clock = ClockReading(w, 42.5)), m.state)
    }

    @Test fun startWithoutClockIsUntimed() {
        val m = machine(OnlineState.Invited("ABCDEF", null))
        m.handle(rx(ServerMessage.Start("g1", PieceColor.WHITE, "tok", "ABCDEF", null)))
        assertNull(m.stored?.clock)
        assertNull(readingOf(m.state))
    }

    @Test fun ackMoveSyncAndResumedRefreshTheReading() {
        val a = wire(w = 170_000, run = PieceColor.BLACK, first = null)
        val m1 = machine(timedPlaying(), timedGame)
        assertEquals(noFx, m1.handle(OnlineEvent.Received(ServerMessage.Ack(2, a), 200.0)))
        assertEquals(ClockReading(a, 200.0), readingOf(m1.state))

        val b = wire(b = 160_000, run = PieceColor.WHITE, first = null)
        val m2 = machine(timedPlaying(), timedGame)
        assertEquals(
            listOf<OnlineEffect>(OnlineEffect.ApplyRemoteMove(3, "e7e5")),
            m2.handle(OnlineEvent.Received(ServerMessage.Move(3, "e7e5", false, b), 300.0)),
        )
        assertEquals(ClockReading(b, 300.0), readingOf(m2.state))

        val c = wire(w = 150_000, run = PieceColor.WHITE, first = null)
        val m3 = machine(timedPlaying(), timedGame)
        assertEquals(
            listOf<OnlineEffect>(OnlineEffect.ReplaceMoves(listOf("e2e4", "e7e5"))),
            m3.handle(OnlineEvent.Received(ServerMessage.Sync(listOf("e2e4", "e7e5"), c), 400.0)),
        )
        assertEquals(ClockReading(c, 400.0), readingOf(m3.state))

        val d = wire(w = 90_000, b = 120_000, run = PieceColor.WHITE, first = null)
        val m4 = machine(timedPlaying(OnlineLink.RECONNECTING), timedGame)
        assertEquals(
            listOf<OnlineEffect>(OnlineEffect.ReplaceMoves(listOf("e2e4", "e7e5"))),
            m4.handle(OnlineEvent.Received(
                ServerMessage.Resumed("g2", PieceColor.WHITE, listOf("e2e4", "e7e5"), true, d), 500.0,
            )),
        )
        assertEquals(timedPlaying(clock = ClockReading(d, 500.0)), m4.state)
    }

    // Poruka bez `clock` (stara ili pokvarena) ne briše poslednje stanje sata.
    @Test fun messageWithoutClockKeepsTheLastReading() {
        val r = ClockReading(wire(run = PieceColor.WHITE, first = null), 7.0)
        val m = machine(timedPlaying(clock = r), timedGame)
        m.handle(OnlineEvent.Received(ServerMessage.Ack(2, null), 50.0))
        m.handle(OnlineEvent.Received(ServerMessage.Move(3, "e7e5", false, null), 60.0))
        m.handle(OnlineEvent.Received(ServerMessage.Sync(listOf("e2e4", "e7e5"), null), 70.0))
        assertEquals(r, readingOf(m.state))
    }

    @Test fun otherPlayingEventsKeepTheReading() {
        val r = ClockReading(wire(run = PieceColor.WHITE, first = null), 7.0)
        val m = machine(timedPlaying(clock = r), timedGame)
        m.handle(rx(ServerMessage.Opponent(false)))
        m.handle(rx(ServerMessage.DrawOffer))
        m.handle(OnlineEvent.SocketClosed)
        assertEquals(r, readingOf(m.state))
    }

    @Test fun flagEndsWithTheRightSide() {
        val mine = machine(timedPlaying(), timedGame)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), mine.handle(rx(ServerMessage.Flag(PieceColor.WHITE))))
        assertEquals(OnlineState.Ended(OnlineResult.I_FLAGGED), mine.state)
        assertNull(mine.stored)
        val theirs = machine(timedPlaying(), timedGame)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), theirs.handle(rx(ServerMessage.Flag(PieceColor.BLACK))))
        assertEquals(OnlineState.Ended(OnlineResult.OPPONENT_FLAGGED), theirs.state)
    }

    @Test fun flagRightAfterResumedEnds() {
        val m = machine(timedPlaying(OnlineLink.RECONNECTING), timedGame)
        m.handle(OnlineEvent.Received(
            ServerMessage.Resumed("g2", PieceColor.WHITE, listOf("e2e4"), false, wire(w = 0, run = PieceColor.WHITE, first = null)),
            1.0,
        ))
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(rx(ServerMessage.Flag(PieceColor.WHITE))))
        assertEquals(OnlineState.Ended(OnlineResult.I_FLAGGED), m.state)
    }

    @Test fun firstMoveExpiredEndsWithoutResult() {
        val m = machine(timedPlaying(), timedGame)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(rx(ServerMessage.FirstMoveExpired)))
        assertEquals(OnlineState.Ended(OnlineResult.FIRST_MOVE_EXPIRED), m.state)
        assertNull(m.stored)
    }

    @Test fun flagAndFirstMoveExpiredOutsidePlayingAreIgnored() {
        val l = machine(OnlineState.Lobby)
        assertEquals(noFx, l.handle(rx(ServerMessage.Flag(PieceColor.WHITE))))
        assertEquals(noFx, l.handle(rx(ServerMessage.FirstMoveExpired)))
        assertEquals(OnlineState.Lobby, l.state)
    }

    @Test fun needsUpdateWhileInvitingGoesBackToLobbyWithNotice() {
        val m = machine(OnlineState.Inviting("ABCDEF"))
        assertEquals(
            listOf<OnlineEffect>(OnlineEffect.ShowMessage(OnlineNotice.NEEDS_UPDATE)),
            m.handle(rx(ServerMessage.Error(OnlineError.NEEDS_UPDATE))),
        )
        assertEquals(OnlineState.Lobby, m.state)
        // Tiče se prijateljeve aplikacije, ne ove: nije kraj sesije.
        assertFalse(m.updateRequired)
    }

    @Test fun badClockWhileInvitingGoesBackToLobbySilently() {
        val m = machine(OnlineState.Inviting("ABCDEF"))
        assertEquals(noFx, m.handle(rx(ServerMessage.Error(OnlineError.BAD_CLOCK))))
        assertEquals(OnlineState.Lobby, m.state)
    }

    @Test fun timedEndingLocalMoveSendsEndAndWaitsForAck() {
        val m = machine(timedPlaying(), timedGame)
        assertEquals(
            listOf(send(ClientMessage.Move(6, "d8h4", end = true))),
            m.handle(OnlineEvent.LocalMove(6, "d8h4", ends = true)),
        )
        // VM odmah posle poteza javlja BoardFinished: u partiji sa satom bez `finished`, čeka se ack.
        assertEquals(noFx, m.handle(OnlineEvent.BoardFinished))
        assertNotNull(readingOf(m.state))
        assertTrue("treba da ostane u Playing do ack-a", m.state is OnlineState.Playing)
        assertFalse(m.canAct) // ništa se ne šalje za partiju koja se upravo završava
        val a = wire(w = 150_000, run = null, first = null)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(OnlineEvent.Received(ServerMessage.Ack(6, a), 9.0)))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
        assertNull(m.stored)
    }

    @Test fun timedAckForAnotherPlyDoesNotEnd() {
        val m = machine(timedPlaying(), timedGame)
        m.handle(OnlineEvent.LocalMove(6, "d8h4", ends = true))
        assertEquals(noFx, m.handle(OnlineEvent.Received(ServerMessage.Ack(4, wire()), 9.0)))
        assertTrue("ack drugog poteza ne završava partiju", m.state is OnlineState.Playing)
    }

    @Test fun timedNonEndingLocalMoveSendsEndFalse() {
        val m = machine(timedPlaying(), timedGame)
        assertEquals(
            listOf(send(ClientMessage.Move(2, "g1f3", end = false))),
            m.handle(OnlineEvent.LocalMove(2, "g1f3", ends = false)),
        )
        assertTrue(m.canAct)
    }

    @Test fun untimedEndingLocalMoveKeepsTodaysFlow() {
        val m = machine(playing(), game)
        assertEquals(
            listOf(send(ClientMessage.Move(6, "d8h4", end = false))),
            m.handle(OnlineEvent.LocalMove(6, "d8h4", ends = true)),
        )
        assertEquals(listOf(send(ClientMessage.Finished), OnlineEffect.Persist(null)), m.handle(OnlineEvent.BoardFinished))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
    }

    // Ruling 2: primalac završnog poteza ne šalje `finished` (server je partiju već završio).
    @Test fun timedRemoteEndingMoveEndsBoardWithoutFinished() {
        val m = machine(timedPlaying(), timedGame)
        val w = wire(w = 100_000, b = 90_000, run = null, first = null)
        val fx = m.handle(OnlineEvent.Received(ServerMessage.Move(7, "d8h4", true, w), 3.0))
        assertEquals(listOf(OnlineEffect.ApplyRemoteMove(7, "d8h4"), OnlineEffect.Persist(null)), fx)
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
        // VM posle primene poteza javlja BoardFinished: ignoriše se, ništa se ne šalje.
        assertEquals(noFx, m.handle(OnlineEvent.BoardFinished))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
    }

    @Test fun untimedRemoteMoveIgnoresEnd() {
        val m = machine(playing(), game)
        assertEquals(
            listOf<OnlineEffect>(OnlineEffect.ApplyRemoteMove(7, "d8h4")),
            m.handle(rx(ServerMessage.Move(7, "d8h4", true, null))),
        )
        assertEquals(playing(), m.state)
        assertEquals(listOf(send(ClientMessage.Finished), OnlineEffect.Persist(null)), m.handle(OnlineEvent.BoardFinished))
    }

    // `resumed` za partiju završenu dok smo bili odsutni: kraj se vidi samo na tabli. `finished` se
    // ipak šalje (veza je živa); server odgovara `game_gone`, koji se u `Ended` zanemaruje.
    @Test fun timedBoardFinishedAfterResumedSendsFinishedAndIgnoresGameGone() {
        val m = machine(timedPlaying(OnlineLink.RECONNECTING), timedGame)
        m.handle(OnlineEvent.Received(
            ServerMessage.Resumed(
                "g2", PieceColor.WHITE, listOf("f2f3", "e7e5", "g2g4", "d8h4"), false,
                wire(run = PieceColor.WHITE, first = null),
            ),
            1.0,
        ))
        assertEquals(listOf(send(ClientMessage.Finished), OnlineEffect.Persist(null)), m.handle(OnlineEvent.BoardFinished))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
        assertEquals(noFx, m.handle(rx(ServerMessage.Error(OnlineError.GAME_GONE))))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
    }

    // Protivnikov potez je stigao bez `end: true`, a tabla je kod nas gotova (npr. dva telefona se ne
    // slažu oko remija po pravilu): sa satom se šalje `finished`, da server ne drži partiju i sat.
    @Test fun timedBoardFinishedWithoutPendingEndSendsFinished() {
        val m = machine(timedPlaying(), timedGame)
        assertEquals(
            listOf<OnlineEffect>(OnlineEffect.ApplyRemoteMove(7, "d8h4")),
            m.handle(OnlineEvent.Received(ServerMessage.Move(7, "d8h4", false, wire(run = PieceColor.WHITE, first = null)), 3.0)),
        )
        assertEquals(listOf(send(ClientMessage.Finished), OnlineEffect.Persist(null)), m.handle(OnlineEvent.BoardFinished))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
        assertNull(m.stored)
    }

    // Sa satom i bez veze: kraj lokalno, ništa se ne šalje (isto kao bez sata).
    @Test fun timedBoardFinishedWhileReconnectingEndsWithoutSend() {
        val m = machine(timedPlaying(OnlineLink.RECONNECTING), timedGame)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(OnlineEvent.BoardFinished))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
    }

    // Završni potez otišao, veza pala pre ack-a: strana koja je završila dobija game_gone.
    @Test fun timedGameGoneWhileAwaitingEndAckEndsBoard() {
        val m = machine(timedPlaying(), timedGame)
        m.handle(OnlineEvent.LocalMove(6, "d8h4", ends = true))
        m.handle(OnlineEvent.SocketClosed)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(rx(ServerMessage.Error(OnlineError.GAME_GONE))))
        assertEquals(OnlineState.Ended(OnlineResult.BOARD), m.state)
    }

    // Završni potez nije stigao do servera: `resumed` ga ne sadrži, partija se nastavlja.
    @Test fun timedResumedWithoutTheEndingMoveClearsThePendingEnd() {
        val m = machine(timedPlaying(), timedGame)
        m.handle(OnlineEvent.LocalMove(6, "d8h4", ends = true))
        m.handle(OnlineEvent.SocketClosed)
        m.handle(OnlineEvent.Received(ServerMessage.Resumed("g2", PieceColor.WHITE, listOf("e2e4"), true, wire()), 2.0))
        assertTrue(m.canAct)
        assertEquals(listOf<OnlineEffect>(OnlineEffect.Persist(null)), m.handle(rx(ServerMessage.Error(OnlineError.GAME_GONE))))
        assertEquals(OnlineState.Ended(OnlineResult.ABORTED), m.state)
    }

    @Test fun timedSyncClearsThePendingEnd() {
        val m = machine(timedPlaying(), timedGame)
        m.handle(OnlineEvent.LocalMove(6, "d8h4", ends = true))
        m.handle(OnlineEvent.Received(ServerMessage.Sync(listOf("e2e4"), wire()), 2.0))
        assertTrue(m.canAct)
    }
}
