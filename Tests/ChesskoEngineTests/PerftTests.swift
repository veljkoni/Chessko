import Testing
@testable import ChesskoEngine

/// Broji listove stabla poteza do zadate dubine — standardna provera
/// ispravnosti generatora poteza. Koristi applyingForSearch (lagani apply,
/// bez racunanja statusa) jer applying() poziva legalMoves i bio bi spor.
func perft(_ state: GameState, _ depth: Int) -> Int {
    if depth == 0 { return 1 }
    let moves = MoveGenerator.legalMoves(for: state.currentTurn, in: state)
    if depth == 1 { return moves.count }
    var total = 0
    for move in moves {
        total += perft(state.applyingForSearch(move), depth - 1)
    }
    return total
}

@Test func startingPositionPerft() {
    let state = GameState.initial()
    #expect(perft(state, 1) == 20)
    #expect(perft(state, 2) == 400)
    #expect(perft(state, 3) == 8_902)
    #expect(perft(state, 4) == 197_281)
}

// Kiwipete — hvata rokadu, en passant i vezivanja.
@Test func kiwipetePerft() {
    let fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 48)
    #expect(perft(state, 2) == 2_039)
    #expect(perft(state, 3) == 97_862)
}

// Pozicija 3 — en passant i ivicni slucajevi piona.
@Test func positionThreePerft() {
    let fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 14)
    #expect(perft(state, 2) == 191)
    #expect(perft(state, 3) == 2_812)
    #expect(perft(state, 4) == 43_238)
}

// Pozicija 4 — promocija.
@Test func positionFourPerft() {
    let fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 6)
    #expect(perft(state, 2) == 264)
    #expect(perft(state, 3) == 9_467)
}

// Pozicija 5 — hvata pravo rokade kad se top POJEDE na startnom polju.
// Ovaj test PADA pre Task 4 (motor vrati 62416 umesto 62379).
@Test func positionFivePerft() {
    let fen = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 44)
    #expect(perft(state, 2) == 1_486)
    #expect(perft(state, 3) == 62_379)
}

// Pocetna pozicija, dubina 5 — 4.865.609 cvorova, traje ~90 sekundi
// (provereno na ovom motoru).
// Odvojen test da moze da se preskoci sa --filter kad se radi brzo.
@Test func startingPositionDeepPerft() {
    #expect(perft(GameState.initial(), 5) == 4_865_609)
}

// Pozicija 6 — mirna sredisnjica, kontrolna.
@Test func positionSixPerft() {
    let fen = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 46)
    #expect(perft(state, 2) == 2_079)
    #expect(perft(state, 3) == 89_890)
}
