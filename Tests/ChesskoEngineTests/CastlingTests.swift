import Testing
@testable import ChesskoEngine

// Crni skakac na f2 moze da pojede belog topa na h1. f1 i g1 su prazni,
// beli jos ima pravo na obe rokade. Posle Sxh1 beli NE SME da rokira.
private let rookCaptureFEN = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R b KQ - 1 8"

@Test func capturingRookRevokesCastlingRight() {
    let state = try! #require(GameState.fromFEN(rookCaptureFEN))
    let knightTakesRook = ChessMove(from: Position(row: 6, col: 5),
                                    to:   Position(row: 7, col: 7))
    let after = state.applying(knightTakesRook)

    #expect(after.whiteCanCastleKingside == false)

    let castles = MoveGenerator.legalMoves(for: .white, in: after)
        .filter { $0.flag == .castleKingside }
    #expect(castles.isEmpty)
}

@Test func castlingMovesTheRealRookInsteadOfCreatingOne() {
    let state = GameState.initial()
    // 1.e4 e5 2.Sf3 Sc6 3.Lc4 Lc5 — oslobadja f1 i g1 za belog.
    let opening = [
        (Position(row: 6, col: 4), Position(row: 4, col: 4)),
        (Position(row: 1, col: 4), Position(row: 3, col: 4)),
        (Position(row: 7, col: 6), Position(row: 5, col: 5)),
        (Position(row: 0, col: 1), Position(row: 2, col: 2)),
        (Position(row: 7, col: 5), Position(row: 4, col: 2)),
        (Position(row: 0, col: 5), Position(row: 3, col: 2)),
    ]
    var s = state
    for (from, to) in opening {
        s = s.applying(ChessMove(from: from, to: to))
    }

    let castle = try! #require(
        MoveGenerator.legalMoves(for: .white, in: s)
            .first { $0.flag == .castleKingside }
    )
    let after = s.applying(castle)

    #expect(after.board[7][6]?.type == .king)
    #expect(after.board[7][5]?.type == .rook)
    #expect(after.board[7][7] == nil)
    #expect(countRooks(in: after, color: .white) == 2)
}

private func countRooks(in state: GameState, color: PieceColor) -> Int {
    var count = 0
    for row in 0..<8 {
        for col in 0..<8 {
            if let p = state.board[row][col], p.color == color, p.type == .rook {
                count += 1
            }
        }
    }
    return count
}

// Sva cetiri ugla moraju da oduzmu pravo kad se top na njima pojede.
// Task 4 je testirao samo h1; ostala tri ugla su bila nepokrivena.
@Test func capturingRookOnEveryCornerRevokesRight() {
    // Crni Ra8 jede belog Ra1 -> belo damino pravo pada (a crno takodje, jer top napusta a8).
    let a1 = try! #require(GameState.fromFEN("r3k3/8/8/8/8/8/8/R3K3 b Qq - 0 1"))
    let rxa1 = try! #require(
        MoveGenerator.legalMoves(for: .black, in: a1)
            .first { $0.from == Position(row: 0, col: 0) && $0.to == Position(row: 7, col: 0) }
    )
    #expect(a1.applying(rxa1).whiteCanCastleQueenside == false)

    // Beli Rh1 jede crnog Rh8 -> crno kraljevo pravo pada.
    let h8 = try! #require(GameState.fromFEN("4k2r/8/8/8/8/8/8/4K2R w Kk - 0 1"))
    let rxh8 = try! #require(
        MoveGenerator.legalMoves(for: .white, in: h8)
            .first { $0.from == Position(row: 7, col: 7) && $0.to == Position(row: 0, col: 7) }
    )
    #expect(h8.applying(rxh8).blackCanCastleKingside == false)

    // Beli Ra1 jede crnog Ra8 -> crno damino pravo pada.
    let a8 = try! #require(GameState.fromFEN("r3k3/8/8/8/8/8/8/R3K3 w Qq - 0 1"))
    let rxa8 = try! #require(
        MoveGenerator.legalMoves(for: .white, in: a8)
            .first { $0.from == Position(row: 7, col: 0) && $0.to == Position(row: 0, col: 0) }
    )
    #expect(a8.applying(rxa8).blackCanCastleQueenside == false)
}

// Partija sacuvana starijom verzijom moze da nosi pravo rokade bez topa na uglu.
// Motor to mora sam da odbije, umesto da se osloni na zastavicu.
@Test func staleCastlingRightWithoutRookOffersNoCastle() {
    let state = try! #require(GameState.fromFEN("4k3/8/8/8/8/8/8/4K3 w K - 0 1"))
    #expect(state.whiteCanCastleKingside == true)   // zastavica je zaista postavljena
    let castles = MoveGenerator.legalMoves(for: .white, in: state)
        .filter { $0.flag == .castleKingside }
    #expect(castles.isEmpty)
}
