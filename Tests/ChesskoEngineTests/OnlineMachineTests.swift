import Testing
import Foundation
@testable import ChesskoEngine

private let game = ActiveGame(game: "g1", token: "tok", color: .white, opponent: "ABCDEF")

private func playing(_ link: OnlineLink = .connected, away: Bool = false,
                     dBy: Bool = false, dMe: Bool = false) -> OnlineState {
    .playing(game, link: link, opponentAway: away, drawOfferedByOpponent: dBy, drawOfferedByMe: dMe)
}

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
    #expect(m.handle(.received(.welcome(code: "K7M4QX"))) == [])
    #expect(m.state == .lobby)
}

// Ruling 2
@Test func welcomeWithStoredStaysConnectingUntilResumed() {
    var m = machine(.connecting, stored: game)
    _ = m.handle(.received(.welcome(code: "K7M4QX")))
    #expect(m.state == .connecting)
}

@Test func resumedGoesToPlayingAndReplacesMoves() {
    var m = machine(.connecting, stored: game)
    let fx = m.handle(.received(.resumed(game: "g1", color: .white, moves: ["e2e4"], opponentOnline: false)))
    #expect(fx == [.replaceMoves(["e2e4"])])
    #expect(m.state == playing(.connected, away: true))
}

@Test func resumeAfterLongAbsenceAppliesMissingMoves() {
    var m = machine(playing(.reconnecting), stored: game)
    let fx = m.handle(.received(.resumed(game: "g1", color: .white, moves: ["e2e4", "e7e5", "g1f3"], opponentOnline: true)))
    #expect(fx == [.replaceMoves(["e2e4", "e7e5", "g1f3"])])
}

@Test func gameGoneWithStoredPersistsNilAndEndsAborted() {
    var m = machine(.connecting, stored: game)
    #expect(m.handle(.received(.error(.gameGone))) == [.persist(nil)])
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
    _ = m.handle(.received(.resumed(game: "g1", color: .white, moves: [], opponentOnline: true)))
    #expect(m.handle(.socketClosed) == [.scheduleReconnect(seconds: 1)])
}

@Test func backoffResetsAfterWelcome() {
    var m = machine(.lobby)
    _ = m.handle(.socketClosed); _ = m.handle(.socketClosed)
    _ = m.handle(.received(.welcome(code: "K7M4QX")))
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
    #expect(m.handle(.received(.error(.codeTaken))) == [])
    #expect(m.state == .connecting)
}

@Test func codeTakenWithoutStoredSchedulesReconnect() {
    var m = machine(.connecting)
    #expect(m.handle(.received(.error(.codeTaken))) == [.scheduleReconnect(seconds: 1)])
    #expect(m.handle(.received(.error(.codeTaken))) == [.scheduleReconnect(seconds: 2)])
    #expect(m.state == .connecting)
}

@Test func startPersistsAndStartsGame() {
    var m = machine(.inviting(to: "ABCDEF"))
    let fx = m.handle(.received(.start(game: "g1", color: .white, token: "tok", opponent: "ABCDEF")))
    #expect(fx == [.persist(game), .startGame(game)])
    #expect(m.stored == game)
}

@Test func endingsPersistNilAndEnd() {
    var r = machine(playing(), stored: game)
    #expect(r.handle(.received(.resigned)) == [.persist(nil)]); #expect(r.state == .ended(.opponentResigned))
    var d = machine(playing(), stored: game)
    #expect(d.handle(.received(.drawReply(accept: true))) == [.persist(nil)]); #expect(d.state == .ended(.drawAgreed))
    var a = machine(playing(), stored: game)
    #expect(a.handle(.received(.aborted)) == [.persist(nil)]); #expect(a.state == .ended(.aborted))
    var b = machine(playing(), stored: game)
    #expect(b.handle(.boardFinished) == [.send(.finished), .persist(nil)]); #expect(b.state == .ended(.board))
    var s = machine(playing(), stored: game)
    #expect(s.handle(.resign) == [.send(.resign), .persist(nil)]); #expect(s.state == .ended(.iResigned))
    #expect(s.stored == nil)
}

@Test func incomingInviteInLobbyGoesToInvited() {
    var m = machine(.lobby)
    #expect(m.handle(.received(.invited(from: "ABCDEF"))) == [])
    #expect(m.state == .invited(from: "ABCDEF"))
}

@Test func inviteWhileInvitedSendsNothing() {
    var m = machine(.invited(from: "ABCDEF"))
    #expect(m.handle(.invite(code: "XYZ234")) == [])
    #expect(m.state == .invited(from: "ABCDEF"))
}

@Test func inviteInLobbySendsInvite() {
    var m = machine(.lobby)
    #expect(m.handle(.invite(code: "ABCDEF")) == [.send(.invite(to: "ABCDEF"))])
    #expect(m.state == .inviting(to: "ABCDEF"))
}

@Test func remoteMoveIsAppliedOnlyInPlaying() {
    var p = machine(playing(), stored: game)
    #expect(p.handle(.received(.move(ply: 1, uci: "e7e5"))) == [.applyRemoteMove(ply: 1, uci: "e7e5")])
    var l = machine(.lobby)
    #expect(l.handle(.received(.move(ply: 1, uci: "e7e5"))) == [])
}

@Test func localMoveSendsMoveWithCallerPly() {
    var m = machine(playing(), stored: game)
    #expect(m.handle(.localMove(ply: 4, uci: "e2e4")) == [.send(.move(ply: 4, uci: "e2e4"))])
}

@Test func syncReplacesMoves() {
    var m = machine(playing(), stored: game)
    #expect(m.handle(.received(.sync(moves: ["e2e4"]))) == [.replaceMoves(["e2e4"])])
}

@Test func drawOfferThenDeclineClearsFlagAndShowsNotice() {
    var m = machine(playing(), stored: game)
    #expect(m.handle(.offerDraw) == [.send(.drawOffer)])
    #expect(m.state == playing(dMe: true))
    #expect(m.handle(.received(.drawReply(accept: false))) == [.showMessage(.drawDeclined)])
    #expect(m.state == playing())
}

@Test func versionErrorShowsUpdateApp() {
    var m = machine(.connecting)
    #expect(m.handle(.received(.error(.version))) == [.showMessage(.updateApp)])
}

// Fix round 1: bez akcija tokom obnove veze
@Test func actionsAreRefusedWhileReconnecting() {
    let events: [OnlineEvent] = [.localMove(ply: 0, uci: "e2e4"), .resign, .offerDraw, .answerDraw(true), .answerDraw(false)]
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
    _ = m.handle(.received(.welcome(code: "K7M4QX")))
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
    #expect(m.handle(.received(.welcome(code: "K7M4QX"))) == [])
    #expect(m.state == .ended(.board))
    #expect(m.handle(.backToLobby) == [])
    #expect(m.state == .lobby)
}

@Test func endedSocketOpenedBeforeWelcomeBackToLobbyWaitsForWelcomeWithoutSecondHello() {
    var m = machine(.ended(.board))
    #expect(m.handle(.socketOpened) == [.send(.hello(code: "K7M4QX"))])
    #expect(m.handle(.backToLobby) == [])
    #expect(m.state == .connecting)
    #expect(m.handle(.received(.welcome(code: "K7M4QX"))) == [])
    #expect(m.state == .lobby)
}
