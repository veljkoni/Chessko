import SwiftUI

// MARK: - Opening Line

struct OpeningLine {
    let name: String
    let uciMoves: [String]   // all moves, alternating white/black
    let hint: String
    let icon: String
    let accentColor: Color
    var solvedMessage: String = "Bravo! Otvaranje savladano! ✓"
    var wrongMessage:  String = "Pogrešan potez — pokušaj ponovo."
    var playingPrompt: String? = nil   // nil → uses default move-number prompt
    var startFEN: String? = nil        // nil → standard starting position
}

// MARK: - Opening Phase

enum OpeningPhase: Equatable {
    case playing
    case wrongMove
    case solved
}

// MARK: - Opening Exercise ViewModel
//
// Guides the learner through a fixed opening line.
// White = player (must find correct moves); Black = auto-played from the script.

@Observable
@MainActor
final class OpeningExerciseViewModel {

    // MARK: State
    var gameState: GameState
    var selectedPosition: Position?
    var legalMovesForSelected: [ChessMove] = []
    var lastMove: ChessMove?
    var animatingPiece: AnimatingPiece?
    var phase: OpeningPhase = .playing
    var movePointer: Int = 0

    // MARK: Config
    let line: OpeningLine

    // MARK: Init
    init(line: OpeningLine) {
        self.line = line
        self.gameState = line.startFEN.flatMap { GameState.fromFEN($0) } ?? .initial()
    }

    private func startState() -> GameState {
        line.startFEN.flatMap { GameState.fromFEN($0) } ?? .initial()
    }

    // MARK: Computed

    var isPlayerTurn: Bool {
        phase != .solved && gameState.currentTurn == .white && animatingPiece == nil
    }

    var progressLabel: String {
        let whiteMoves = line.uciMoves.indices.filter { $0 % 2 == 0 }
        let done = whiteMoves.filter { $0 < movePointer }.count
        let total = whiteMoves.count
        return "\(done)/\(total) poteza"
    }

    var statusMessage: String {
        switch phase {
        case .solved:    return line.solvedMessage
        case .wrongMove: return line.wrongMessage
        case .playing:
            if let prompt = line.playingPrompt { return prompt }
            let moveNum = (movePointer / 2) + 1
            return "Potez \(moveNum) — pronađi pravi potez za bele!"
        }
    }

    // MARK: - Tap

    func tap(position: Position) {
        guard isPlayerTurn else { return }

        if let move = legalMovesForSelected.first(where: { $0.to == position }) {
            attempt(move: move)
            return
        }

        let piece = gameState.board[position.row][position.col]
        if let piece, piece.color == .white {
            if selectedPosition != position { Haptics.selection() }
            selectedPosition = position
            legalMovesForSelected = MoveGenerator.legalMoves(for: .white, in: gameState)
                .filter { $0.from == position }
            if phase == .wrongMove { phase = .playing }
        } else {
            selectedPosition = nil
            legalMovesForSelected = []
        }
    }

    // MARK: - Attempt

    private func attempt(move: ChessMove) {
        guard movePointer < line.uciMoves.count,
              let expected = ChessMove.fromUCI(line.uciMoves[movePointer], in: gameState)
        else { return }

        guard move.from == expected.from && move.to == expected.to else {
            Haptics.notification(.error)
            phase = .wrongMove
            selectedPosition = nil
            legalMovesForSelected = []
            return
        }

        apply(move: move)
        movePointer += 1

        if movePointer >= line.uciMoves.count {
            phase = .solved
            Haptics.notification(.success)
            return
        }

        // Auto-play black's response
        Task {
            try? await Task.sleep(for: .milliseconds(500))
            self.playBlack()
        }
    }

    // MARK: - Auto-play black

    private func playBlack() {
        guard movePointer < line.uciMoves.count,
              let move = ChessMove.fromUCI(line.uciMoves[movePointer], in: gameState)
        else { return }

        apply(move: move)
        movePointer += 1

        if movePointer >= line.uciMoves.count {
            phase = .solved
            Haptics.notification(.success)
        }
    }

    // MARK: - Apply

    private func apply(move: ChessMove) {
        if let piece = gameState.board[move.from.row][move.from.col] {
            animatingPiece = AnimatingPiece(piece: piece, from: move.from, to: move.to)
        }

        let isCapture = gameState.board[move.to.row][move.to.col] != nil
        gameState = gameState.applying(move)
        lastMove  = move
        selectedPosition      = nil
        legalMovesForSelected = []

        if isCapture { SoundManager.shared.playCapture() } else { SoundManager.shared.playMove() }
        Haptics.impact(isCapture ? .medium : .light)

        Task {
            try? await Task.sleep(for: .milliseconds(280))
            self.animatingPiece = nil
        }
    }

    // MARK: - Reset

    func reset() {
        gameState             = startState()
        selectedPosition      = nil
        legalMovesForSelected = []
        lastMove              = nil
        animatingPiece        = nil
        movePointer           = 0
        phase                 = .playing
    }
}
