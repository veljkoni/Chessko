import Testing
import Foundation
@testable import ChesskoEngine

private let game = ActiveGame(game: "g1", token: "tok", color: .white, opponent: "ABCDEF")

private func playing(_ link: OnlineLink = .connected, away: Bool = false,
                     dBy: Bool = false, dMe: Bool = false) -> OnlineState {
    .playing(game, link: link, opponentAway: away, drawOfferedByOpponent: dBy, drawOfferedByMe: dMe, clock: nil)
}

/// Dolazna poruka bez važnog vremena prijema (partije bez sata).
private func rx(_ m: ServerMessage) -> OnlineEvent { .received(m, at: 0) }

private func machine(_ state: OnlineState, stored: ActiveGame? = nil) -> OnlineMachine {
    var m = OnlineMachine(myCode: "K7M4QX", stored: stored)
    m.state = state
    return m
}

@Test func socketOpenedSendsHello() {
    var m = machine(.idle)
    #expect(m.handle(.socketOpened) == [.send(.hello(code: "K7M4QX"))])
    #expect(m.state == .connecting)
}

@Test func socketOpenedWithStoredSendsHelloThenResume() {
    var m = machine(.idle, stored: game)
    #expect(m.handle(.socketOpened) == [.send(.hello(code: "K7M4QX")), .send(.resume(game: "g1", token: "tok"))])
}

@Test func socketOpenedAcceptedWhileReconnecting() {
    var m = machine(playing(.reconnecting), stored: game)
    #expect(m.handle(.socketOpened).count == 2)
}

@Test func welcomeWithoutStoredGoesToLobby() {
    var m = machine(.connecting)
    #expect(m.handle(rx(.welcome(code: "K7M4QX"))) == [])
    #expect(m.state == .lobby)
}

// Ruling 2
@Test func welcomeWithStoredStaysConnectingUntilResumed() {
    var m = machine(.connecting, stored: game)
    _ = m.handle(rx(.welcome(code: "K7M4QX")))
    #expect(m.state == .connecting)
}

@Test func resumedGoesToPlayingAndReplacesMoves() {
    var m = machine(.connecting, stored: game)
    let fx = m.handle(rx(.resumed(game: "g1", color: .white, moves: ["e2e4"], opponentOnline: false, clock: nil)))
    #expect(fx == [.replaceMoves(["e2e4"])])
    #expect(m.state == playing(.connected, away: true))
}

@Test func resumeAfterLongAbsenceAppliesMissingMoves() {
    var m = machine(playing(.reconnecting), stored: game)
    let fx = m.handle(rx(.resumed(game: "g1", color: .white, moves: ["e2e4", "e7e5", "g1f3"], opponentOnline: true, clock: nil)))
    #expect(fx == [.replaceMoves(["e2e4", "e7e5", "g1f3"])])
}

@Test func gameGoneWithStoredPersistsNilAndEndsAborted() {
    var m = machine(.connecting, stored: game)
    #expect(m.handle(rx(.error(.gameGone))) == [.persist(nil)])
    #expect(m.state == .ended(.aborted))
    #expect(m.stored == nil)
}

@Test func socketClosedInPlayingReconnectsWithBackoff() {
    var m = machine(playing(), stored: game)
    var secs: [Int] = []
    for _ in 0..<7 {
        if case .scheduleReconnect(let s)? = m.handle(.socketClosed).first { secs.append(s) }
    }
    #expect(secs == [1, 2, 4, 8, 15, 15, 15])
    #expect(m.state == playing(.reconnecting))
}

// Ruling 3
@Test func backoffResetsAfterResumed() {
    var m = machine(playing(), stored: game)
    _ = m.handle(.socketClosed); _ = m.handle(.socketClosed)
    _ = m.handle(rx(.resumed(game: "g1", color: .white, moves: [], opponentOnline: true, clock: nil)))
    #expect(m.handle(.socketClosed) == [.scheduleReconnect(seconds: 1)])
}

@Test func backoffResetsAfterWelcome() {
    var m = machine(.lobby)
    _ = m.handle(.socketClosed); _ = m.handle(.socketClosed)
    _ = m.handle(rx(.welcome(code: "K7M4QX")))
    #expect(m.handle(.socketClosed) == [.scheduleReconnect(seconds: 1)])
}

@Test func appBecameActiveWhileReconnectingConnects() {
    var m = machine(playing(.reconnecting), stored: game)
    #expect(m.handle(.appBecameActive) == [.connect])
}

// Ruling 6
@Test func appBecameActiveInLobbyAndEndedDoesNothing() {
    var a = machine(.lobby); #expect(a.handle(.appBecameActive) == [])
    var b = machine(.ended(.board)); #expect(b.handle(.appBecameActive) == [])
}

@Test func socketClosedInLobbyGoesConnecting() {
    var m = machine(.lobby)
    #expect(m.handle(.socketClosed) == [.scheduleReconnect(seconds: 1)])
    #expect(m.state == .connecting)
}

// Ruling 1
@Test func codeTakenWithStoredIsIgnored() {
    var m = machine(.connecting, stored: game)
    #expect(m.handle(rx(.error(.codeTaken))) == [])
    #expect(m.state == .connecting)
}

@Test func codeTakenWithoutStoredSchedulesReconnect() {
    var m = machine(.connecting)
    #expect(m.handle(rx(.error(.codeTaken))) == [.scheduleReconnect(seconds: 1)])
    #expect(m.handle(rx(.error(.codeTaken))) == [.scheduleReconnect(seconds: 2)])
    #expect(m.state == .connecting)
}

@Test func startPersistsAndStartsGame() {
    var m = machine(.inviting(to: "ABCDEF"))
    let fx = m.handle(rx(.start(game: "g1", color: .white, token: "tok", opponent: "ABCDEF", clock: nil)))
    #expect(fx == [.persist(game), .startGame(game)])
    #expect(m.stored == game)
}

@Test func endingsPersistNilAndEnd() {
    var r = machine(playing(), stored: game)
    #expect(r.handle(rx(.resigned)) == [.persist(nil)]); #expect(r.state == .ended(.opponentResigned))
    var d = machine(playing(), stored: game)
    #expect(d.handle(rx(.drawReply(accept: true))) == [.persist(nil)]); #expect(d.state == .ended(.drawAgreed))
    var a = machine(playing(), stored: game)
    #expect(a.handle(rx(.aborted)) == [.persist(nil)]); #expect(a.state == .ended(.aborted))
    var b = machine(playing(), stored: game)
    #expect(b.handle(.boardFinished) == [.send(.finished), .persist(nil)]); #expect(b.state == .ended(.board))
    var s = machine(playing(), stored: game)
    #expect(s.handle(.resign) == [.send(.resign), .persist(nil)]); #expect(s.state == .ended(.iResigned))
    #expect(s.stored == nil)
}

@Test func incomingInviteInLobbyGoesToInvited() {
    var m = machine(.lobby)
    #expect(m.handle(rx(.invited(from: "ABCDEF", clock: nil))) == [])
    #expect(m.state == .invited(from: "ABCDEF", clock: nil))
}

@Test func inviteWhileInvitedSendsNothing() {
    var m = machine(.invited(from: "ABCDEF", clock: nil))
    #expect(m.handle(.invite(code: "XYZ234", clock: nil)) == [])
    #expect(m.state == .invited(from: "ABCDEF", clock: nil))
}

@Test func inviteInLobbySendsInvite() {
    var m = machine(.lobby)
    #expect(m.handle(.invite(code: "ABCDEF", clock: nil)) == [.send(.invite(to: "ABCDEF", clock: nil))])
    #expect(m.state == .inviting(to: "ABCDEF"))
}

@Test func remoteMoveIsAppliedOnlyInPlaying() {
    var p = machine(playing(), stored: game)
    #expect(p.handle(rx(.move(ply: 1, uci: "e7e5", end: false, clock: nil))) == [.applyRemoteMove(ply: 1, uci: "e7e5")])
    var l = machine(.lobby)
    #expect(l.handle(rx(.move(ply: 1, uci: "e7e5", end: false, clock: nil))) == [])
}

@Test func localMoveSendsMoveWithCallerPly() {
    var m = machine(playing(), stored: game)
    #expect(m.handle(.localMove(ply: 4, uci: "e2e4", ends: false)) == [.send(.move(ply: 4, uci: "e2e4", end: false))])
}

@Test func syncReplacesMoves() {
    var m = machine(playing(), stored: game)
    #expect(m.handle(rx(.sync(moves: ["e2e4"], clock: nil))) == [.replaceMoves(["e2e4"])])
}

@Test func drawOfferThenDeclineClearsFlagAndShowsNotice() {
    var m = machine(playing(), stored: game)
    #expect(m.handle(.offerDraw) == [.send(.drawOffer)])
    #expect(m.state == playing(dMe: true))
    #expect(m.handle(rx(.drawReply(accept: false))) == [.showMessage(.drawDeclined)])
    #expect(m.state == playing())
}

// I3: greška verzije je kraj za sesiju — bez ponovnog povezivanja, trajno „ažuriraj".
@Test func versionErrorRequiresUpdateAndStopsTheMachine() {
    var m = machine(.connecting)
    #expect(!m.updateRequired)
    #expect(m.handle(rx(.error(.version))) == [])
    #expect(m.updateRequired)
    #expect(m.handle(.socketClosed) == [])            // nema scheduleReconnect
    #expect(m.handle(.appBecameActive) == [])         // nema connect
    #expect(m.handle(.socketOpened) == [])            // nema hello
    #expect(m.handle(rx(.welcome(code: "K7M4QX"))) == [])
    #expect(m.state == .connecting)
    #expect(m.updateRequired)
}

@Test func versionErrorWithStoredGameIgnoresResumedAndBlocksActions() {
    var m = machine(playing(.reconnecting), stored: game)
    #expect(m.handle(rx(.error(.version))) == [])
    #expect(m.handle(rx(.resumed(game: "g1", color: .white, moves: ["e2e4"], opponentOnline: true, clock: nil))) == [])
    #expect(!m.canAct)
    #expect(m.handle(.resign) == [])
    #expect(m.handle(.backToLobby) == [])
    #expect(m.stored == game)
}

// I1: kraj koji je server sačuvao dok je ova strana bila odsutna stiže odmah posle `resumed`.
@Test func resignedRightAfterResumedEndsOpponentResigned() {
    var m = machine(playing(.reconnecting), stored: game)
    #expect(m.handle(rx(.resumed(game: "g1", color: .white, moves: ["e2e4"], opponentOnline: false, clock: nil))) == [.replaceMoves(["e2e4"])])
    #expect(m.handle(rx(.resigned)) == [.persist(nil)])
    #expect(m.state == .ended(.opponentResigned))
    #expect(m.stored == nil)
}

@Test func drawAcceptRightAfterResumedEndsDrawEvenThoughOfferFlagWasReset() {
    // Ova strana je ponudila remi pa izgubila vezu; `resumed` briše zastavicu ponude.
    var m = machine(playing(.reconnecting, dMe: true), stored: game)
    _ = m.handle(rx(.resumed(game: "g1", color: .white, moves: [], opponentOnline: false, clock: nil)))
    #expect(m.state == playing(.connected, away: true))
    #expect(m.handle(rx(.drawReply(accept: true))) == [.persist(nil)])
    #expect(m.state == .ended(.drawAgreed))
}

@Test func resumedAfterRelaunchThenResignedEnds() {
    var m = machine(.idle, stored: game)
    _ = m.handle(.socketOpened)
    _ = m.handle(rx(.welcome(code: "K7M4QX")))
    _ = m.handle(rx(.resumed(game: "g1", color: .white, moves: ["e2e4"], opponentOnline: false, clock: nil)))
    #expect(m.handle(rx(.resigned)) == [.persist(nil)])
    #expect(m.state == .ended(.opponentResigned))
}

@Test func boardFinishedAfterResumedSendsFinishedAndEnds() {
    // Mat/pat iz liste poteza: VM posle `replaceMoves` javlja boardFinished.
    var m = machine(playing(.reconnecting), stored: game)
    _ = m.handle(rx(.resumed(game: "g1", color: .white, moves: ["f2f3", "e7e5", "g2g4", "d8h4"], opponentOnline: false, clock: nil)))
    #expect(m.handle(.boardFinished) == [.send(.finished), .persist(nil)])
    #expect(m.state == .ended(.board))
    // server je partiju već obrisao: game_gone posle kraja ne menja ništa
    #expect(m.handle(rx(.error(.gameGone))) == [])
    #expect(m.state == .ended(.board))
}

// I4: varijanta potvrde zatvaranja je zamrznuta pri otvaranju.
@Test func closeConfirmVariantIsCapturedAndResignNeedsLiveLink() {
    #expect(OnlineCloseConfirm(canAct: true) == .resign)
    #expect(OnlineCloseConfirm(canAct: false) == .gameWaits)
    #expect(OnlineCloseConfirm.resign.resignsOnConfirm(canActNow: true))
    #expect(!OnlineCloseConfirm.resign.resignsOnConfirm(canActNow: false))
    #expect(!OnlineCloseConfirm.gameWaits.resignsOnConfirm(canActNow: true))
    #expect(!OnlineCloseConfirm.gameWaits.resignsOnConfirm(canActNow: false))
}

// Fix round 1: bez akcija tokom obnove veze
@Test func actionsAreRefusedWhileReconnecting() {
    let events: [OnlineEvent] = [.localMove(ply: 0, uci: "e2e4", ends: false), .resign, .offerDraw, .answerDraw(true), .answerDraw(false)]
    for e in events {
        var m = machine(playing(.reconnecting, dBy: true), stored: game)
        #expect(m.handle(e) == [])
        #expect(m.state == playing(.reconnecting, dBy: true))
        #expect(m.stored == game)
    }
}

@Test func canActOnlyWhenConnectedPlaying() {
    #expect(machine(playing(.connected)).canAct)
    #expect(!machine(playing(.reconnecting)).canAct)
    #expect(!machine(.lobby).canAct)
}

@Test func boardFinishedWhileReconnectingEndsWithoutSend() {
    var m = machine(playing(.reconnecting), stored: game)
    #expect(m.handle(.boardFinished) == [.persist(nil)])
    #expect(m.state == .ended(.board))
}

@Test func backToLobbyFromEndedWithLiveSocketGoesToLobby() {
    var m = machine(.idle)
    _ = m.handle(.socketOpened)
    _ = m.handle(rx(.welcome(code: "K7M4QX")))
    m.state = .ended(.board)
    #expect(m.handle(.backToLobby) == [])
    #expect(m.state == .lobby)
}

@Test func backToLobbyAfterSocketClosedInEndedReconnects() {
    var m = machine(.idle)
    _ = m.handle(.socketOpened)
    m.state = .ended(.board)
    #expect(m.handle(.socketClosed) == [])
    #expect(m.state == .ended(.board))
    #expect(m.handle(.backToLobby) == [.connect])
    #expect(m.state == .connecting)
}

@Test func backToLobbyIgnoredOutsideEnded() {
    var m = machine(playing(), stored: game)
    #expect(m.handle(.backToLobby) == [])
    #expect(m.state == playing())
}

// Fix round 2: veza otvorena u .ended
@Test func socketOpenedInEndedSendsHelloOnly() {
    var m = machine(.ended(.board))
    #expect(m.handle(.socketOpened) == [.send(.hello(code: "K7M4QX"))])
    #expect(m.state == .ended(.board))
}

@Test func endedSocketOpenedWelcomeBackToLobby() {
    var m = machine(.ended(.board))
    _ = m.handle(.socketOpened)
    #expect(m.handle(rx(.welcome(code: "K7M4QX"))) == [])
    #expect(m.state == .ended(.board))
    #expect(m.handle(.backToLobby) == [])
    #expect(m.state == .lobby)
}

@Test func endedSocketOpenedBeforeWelcomeBackToLobbyWaitsForWelcomeWithoutSecondHello() {
    var m = machine(.ended(.board))
    #expect(m.handle(.socketOpened) == [.send(.hello(code: "K7M4QX"))])
    #expect(m.handle(.backToLobby) == [])
    #expect(m.state == .connecting)
    #expect(m.handle(rx(.welcome(code: "K7M4QX"))) == [])
    #expect(m.state == .lobby)
}

// Pozadina: eksplicitan prekid veze (isto kao Android)
@Test func scenePolicyDropsOnBackgroundOnlyWithUiOrStoredGame() {
    #expect(OnlineScenePolicy.shouldDropOnBackground(uiOpen: true, hasStoredGame: false))
    #expect(OnlineScenePolicy.shouldDropOnBackground(uiOpen: false, hasStoredGame: true))
    #expect(!OnlineScenePolicy.shouldDropOnBackground(uiOpen: false, hasStoredGame: false))
}

@Test func scenePolicyBlocksConnectsWhileBackgrounded() {
    #expect(!OnlineScenePolicy.mayConnect(backgrounded: true))
    #expect(OnlineScenePolicy.mayConnect(backgrounded: false))
}

@Test func backgroundThenActiveReconnectsFromPlaying() {
    var m = machine(playing(), stored: game)
    // pozadina: veza pada, mašina traži reconnect (VM ga ne zakazuje dok je u pozadini)
    #expect(m.handle(.socketClosed) == [.scheduleReconnect(seconds: 1)])
    #expect(m.state == playing(.reconnecting))
    // povratak: odmah connect, bez čekanja backoff-a
    #expect(m.handle(.appBecameActive) == [.connect])
}

// MARK: - Partija na vreme (protokol v2)

private let timedGame = ActiveGame(game: "g2", token: "tok2", color: .white, opponent: "ABCDEF", clock: .blitz)

private func wire(w: Int = 180_000, b: Int = 180_000, run: PieceColor? = nil, first: Int? = 30_000) -> WireClock {
    WireClock(spec: .blitz, whiteMs: w, blackMs: b, running: run, firstMoveMs: first)
}

private func timedPlaying(_ link: OnlineLink = .connected, away: Bool = false,
                          clock: ClockReading? = ClockReading(wire: wire(), receivedAt: 100)) -> OnlineState {
    .playing(timedGame, link: link, opponentAway: away, drawOfferedByOpponent: false, drawOfferedByMe: false, clock: clock)
}

private func readingOf(_ s: OnlineState) -> ClockReading? {
    if case .playing(_, _, _, _, _, let c) = s { return c }
    return nil
}

@Test func invitedCarriesClockFromMessage() {
    var m = machine(.lobby)
    #expect(m.handle(rx(.invited(from: "ABCDEF", clock: .rapid))) == [])
    #expect(m.state == .invited(from: "ABCDEF", clock: .rapid))
}

@Test func acceptSendsTheSameClockAsTheInvite() {
    var timed = machine(.invited(from: "ABCDEF", clock: .blitz))
    #expect(timed.handle(.accept) == [.send(.accept(from: "ABCDEF", clock: .blitz))])
    var untimed = machine(.invited(from: "ABCDEF", clock: nil))
    #expect(untimed.handle(.accept) == [.send(.accept(from: "ABCDEF", clock: nil))])
}

@Test func inviteWithClockSendsClock() {
    var m = machine(.lobby)
    #expect(m.handle(.invite(code: "ABCDEF", clock: .rapid)) == [.send(.invite(to: "ABCDEF", clock: .rapid))])
    #expect(m.state == .inviting(to: "ABCDEF"))
}

@Test func startWithClockSetsReadingAndStoresSpec() {
    var m = machine(.inviting(to: "ABCDEF"))
    let w = wire()
    let fx = m.handle(.received(.start(game: "g2", color: .white, token: "tok2", opponent: "ABCDEF", clock: w), at: 42.5))
    #expect(fx == [.persist(timedGame), .startGame(timedGame)])
    #expect(m.stored == timedGame)
    #expect(m.stored?.clock == .blitz)
    #expect(m.state == timedPlaying(clock: ClockReading(wire: w, receivedAt: 42.5)))
}

@Test func startWithoutClockIsUntimed() {
    var m = machine(.invited(from: "ABCDEF", clock: nil))
    _ = m.handle(rx(.start(game: "g1", color: .white, token: "tok", opponent: "ABCDEF", clock: nil)))
    #expect(m.stored?.clock == nil)
    #expect(readingOf(m.state) == nil)
}

@Test func ackMoveSyncAndResumedRefreshTheReading() {
    let a = wire(w: 170_000, run: .black, first: nil)
    var m1 = machine(timedPlaying(), stored: timedGame)
    #expect(m1.handle(.received(.ack(ply: 2, clock: a), at: 200)) == [])
    #expect(readingOf(m1.state) == ClockReading(wire: a, receivedAt: 200))

    let b = wire(b: 160_000, run: .white, first: nil)
    var m2 = machine(timedPlaying(), stored: timedGame)
    #expect(m2.handle(.received(.move(ply: 3, uci: "e7e5", end: false, clock: b), at: 300)) == [.applyRemoteMove(ply: 3, uci: "e7e5")])
    #expect(readingOf(m2.state) == ClockReading(wire: b, receivedAt: 300))

    let c = wire(w: 150_000, run: .white, first: nil)
    var m3 = machine(timedPlaying(), stored: timedGame)
    #expect(m3.handle(.received(.sync(moves: ["e2e4", "e7e5"], clock: c), at: 400)) == [.replaceMoves(["e2e4", "e7e5"])])
    #expect(readingOf(m3.state) == ClockReading(wire: c, receivedAt: 400))

    let d = wire(w: 90_000, b: 120_000, run: .white, first: nil)
    var m4 = machine(timedPlaying(.reconnecting), stored: timedGame)
    #expect(m4.handle(.received(.resumed(game: "g2", color: .white, moves: ["e2e4", "e7e5"], opponentOnline: true, clock: d), at: 500)) == [.replaceMoves(["e2e4", "e7e5"])])
    #expect(m4.state == timedPlaying(clock: ClockReading(wire: d, receivedAt: 500)))
}

@Test func otherPlayingEventsKeepTheReading() {
    let r = ClockReading(wire: wire(run: .white, first: nil), receivedAt: 7)
    var m = machine(timedPlaying(clock: r), stored: timedGame)
    _ = m.handle(rx(.opponent(online: false)))
    _ = m.handle(rx(.drawOffer))
    _ = m.handle(.socketClosed)
    #expect(readingOf(m.state) == r)
}

@Test func flagEndsWithTheRightSide() {
    var mine = machine(timedPlaying(), stored: timedGame)
    #expect(mine.handle(rx(.flag(loser: .white))) == [.persist(nil)])
    #expect(mine.state == .ended(.iFlagged))
    #expect(mine.stored == nil)
    var theirs = machine(timedPlaying(), stored: timedGame)
    #expect(theirs.handle(rx(.flag(loser: .black))) == [.persist(nil)])
    #expect(theirs.state == .ended(.opponentFlagged))
}

@Test func flagRightAfterResumedEnds() {
    var m = machine(timedPlaying(.reconnecting), stored: timedGame)
    _ = m.handle(.received(.resumed(game: "g2", color: .white, moves: ["e2e4"], opponentOnline: false, clock: wire(w: 0, run: .white, first: nil)), at: 1))
    #expect(m.handle(rx(.flag(loser: .white))) == [.persist(nil)])
    #expect(m.state == .ended(.iFlagged))
}

@Test func firstMoveExpiredEndsWithoutResult() {
    var m = machine(timedPlaying(), stored: timedGame)
    #expect(m.handle(rx(.firstMoveExpired)) == [.persist(nil)])
    #expect(m.state == .ended(.firstMoveExpired))
    #expect(m.stored == nil)
}

@Test func flagAndFirstMoveExpiredOutsidePlayingAreIgnored() {
    var l = machine(.lobby)
    #expect(l.handle(rx(.flag(loser: .white))) == [])
    #expect(l.handle(rx(.firstMoveExpired)) == [])
    #expect(l.state == .lobby)
}

@Test func needsUpdateWhileInvitingGoesBackToLobbyWithNotice() {
    var m = machine(.inviting(to: "ABCDEF"))
    #expect(m.handle(rx(.error(.needsUpdate))) == [.showMessage(.needsUpdate)])
    #expect(m.state == .lobby)
}

@Test func badClockWhileInvitingGoesBackToLobbySilently() {
    var m = machine(.inviting(to: "ABCDEF"))
    #expect(m.handle(rx(.error(.badClock))) == [])
    #expect(m.state == .lobby)
}

@Test func timedEndingLocalMoveSendsEndAndWaitsForAck() {
    var m = machine(timedPlaying(), stored: timedGame)
    #expect(m.handle(.localMove(ply: 6, uci: "d8h4", ends: true)) == [.send(.move(ply: 6, uci: "d8h4", end: true))])
    // VM odmah posle poteza javlja boardFinished: u partiji sa satom bez `finished`, čeka se ack.
    #expect(m.handle(.boardFinished) == [])
    #expect(readingOf(m.state) != nil)
    if case .playing = m.state {} else { Issue.record("treba da ostane u .playing do ack-a") }
    #expect(!m.canAct)   // ništa se ne šalje za partiju koja se upravo završava
    let a = wire(w: 150_000, run: nil, first: nil)
    #expect(m.handle(.received(.ack(ply: 6, clock: a), at: 9)) == [.persist(nil)])
    #expect(m.state == .ended(.board))
    #expect(m.stored == nil)
}

@Test func timedAckForAnotherPlyDoesNotEnd() {
    var m = machine(timedPlaying(), stored: timedGame)
    _ = m.handle(.localMove(ply: 6, uci: "d8h4", ends: true))
    #expect(m.handle(.received(.ack(ply: 4, clock: wire()), at: 9)) == [])
    if case .playing = m.state {} else { Issue.record("ack drugog poteza ne završava partiju") }
}

@Test func timedNonEndingLocalMoveSendsEndFalse() {
    var m = machine(timedPlaying(), stored: timedGame)
    #expect(m.handle(.localMove(ply: 2, uci: "g1f3", ends: false)) == [.send(.move(ply: 2, uci: "g1f3", end: false))])
    #expect(m.canAct)
}

@Test func untimedEndingLocalMoveKeepsTodaysFlow() {
    var m = machine(playing(), stored: game)
    #expect(m.handle(.localMove(ply: 6, uci: "d8h4", ends: true)) == [.send(.move(ply: 6, uci: "d8h4", end: false))])
    #expect(m.handle(.boardFinished) == [.send(.finished), .persist(nil)])
    #expect(m.state == .ended(.board))
}

// Ruling 2: primalac završnog poteza ne šalje `finished` (server je partiju već završio).
@Test func timedRemoteEndingMoveEndsBoardWithoutFinished() {
    var m = machine(timedPlaying(), stored: timedGame)
    let w = wire(w: 100_000, b: 90_000, run: nil, first: nil)
    let fx = m.handle(.received(.move(ply: 7, uci: "d8h4", end: true, clock: w), at: 3))
    #expect(fx == [.applyRemoteMove(ply: 7, uci: "d8h4"), .persist(nil)])
    #expect(m.state == .ended(.board))
    // VM posle primene poteza javlja boardFinished: ignoriše se, ništa se ne šalje.
    #expect(m.handle(.boardFinished) == [])
    #expect(m.state == .ended(.board))
}

@Test func untimedRemoteMoveIgnoresEnd() {
    var m = machine(playing(), stored: game)
    #expect(m.handle(rx(.move(ply: 7, uci: "d8h4", end: true, clock: nil))) == [.applyRemoteMove(ply: 7, uci: "d8h4")])
    #expect(m.state == playing())
    #expect(m.handle(.boardFinished) == [.send(.finished), .persist(nil)])
}

// `resumed` za partiju završenu `finished`-om dok smo bili odsutni: kraj se vidi samo na tabli.
@Test func timedBoardFinishedAfterResumedEndsWithoutFinished() {
    var m = machine(timedPlaying(.reconnecting), stored: timedGame)
    _ = m.handle(.received(.resumed(game: "g2", color: .white, moves: ["f2f3", "e7e5", "g2g4", "d8h4"], opponentOnline: false, clock: wire(run: .white, first: nil)), at: 1))
    #expect(m.handle(.boardFinished) == [.persist(nil)])
    #expect(m.state == .ended(.board))
}

// Završni potez otišao, veza pala pre ack-a: strana koja je završila dobija game_gone.
@Test func timedGameGoneWhileAwaitingEndAckEndsBoard() {
    var m = machine(timedPlaying(), stored: timedGame)
    _ = m.handle(.localMove(ply: 6, uci: "d8h4", ends: true))
    _ = m.handle(.socketClosed)
    #expect(m.handle(rx(.error(.gameGone))) == [.persist(nil)])
    #expect(m.state == .ended(.board))
}

// Završni potez nije stigao do servera: `resumed` ga ne sadrži, partija se nastavlja.
@Test func timedResumedWithoutTheEndingMoveClearsThePendingEnd() {
    var m = machine(timedPlaying(), stored: timedGame)
    _ = m.handle(.localMove(ply: 6, uci: "d8h4", ends: true))
    _ = m.handle(.socketClosed)
    _ = m.handle(.received(.resumed(game: "g2", color: .white, moves: ["e2e4"], opponentOnline: true, clock: wire()), at: 2))
    #expect(m.canAct)
    #expect(m.handle(rx(.error(.gameGone))) == [.persist(nil)])
    #expect(m.state == .ended(.aborted))
}

@Test func timedSyncClearsThePendingEnd() {
    var m = machine(timedPlaying(), stored: timedGame)
    _ = m.handle(.localMove(ply: 6, uci: "d8h4", ends: true))
    _ = m.handle(.received(.sync(moves: ["e2e4"], clock: wire()), at: 2))
    #expect(m.canAct)
}

@Test func activeGameDecodesOldSaveWithoutClock() throws {
    let old = #"{"game":"g1","token":"tok","color":0,"opponent":"ABCDEF"}"#
    let g = try JSONDecoder().decode(ActiveGame.self, from: Data(old.utf8))
    #expect(g == game)
    #expect(g.clock == nil)
    let roundTrip = try JSONDecoder().decode(ActiveGame.self, from: JSONEncoder().encode(timedGame))
    #expect(roundTrip == timedGame)
}
