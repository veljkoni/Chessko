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
