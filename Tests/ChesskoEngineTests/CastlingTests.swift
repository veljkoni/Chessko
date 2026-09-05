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
