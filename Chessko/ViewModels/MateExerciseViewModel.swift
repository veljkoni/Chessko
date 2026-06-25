import SwiftUI

// MARK: - Mate Exercise ViewModel
//
// Lightweight game controller for the three elementary-mate mini-tasks
// embedded in Lesson 1. White = player, Black = easy AI.

@Observable
@MainActor
final class MateExerciseViewModel {

    // MARK: State
    var gameState: GameState
    var selectedPosition: Position?
    var legalMovesForSelected: [ChessMove] = []
    var lastMove: ChessMove?
    var animatingPiece: AnimatingPiece?
    var isThinking = false

    // MARK: Config
    let title: String
    let hint: String
    private let startFEN: String

    // MARK: Init
    init(fen: String, title: String, hint: String) {
        self.startFEN = fen
        self.title    = title
        self.hint     = hint
        self.gameState = GameState.fromFEN(fen) ?? .initial()
    }

    // MARK: Computed

    var isPlayerTurn: Bool {
        gameState.currentTurn == .white && !isThinking && !isOver
    }

    var isOver: Bool {
        switch gameState.status {
        case .checkmate, .draw: return true
        default: return false
        }
    }

    var isSolved: Bool {
        if case .checkmate(let c) = gameState.status { return c == .black }
        return false
    }

    var statusMessage: String {
        switch gameState.status {
        case .checkmate(let c):
            return c == .black ? "Bravo! Mat! 🎉" : "Poraz — pokušaj ponovo."
        case .draw:
            return "Remi — pazi na pat! Pokušaj ponovo."
        case .check(let c):
            return c == .black ? "Šah! Nastavi..." : "Šah — mora da se braniš!"
        case .playing:
            return isThinking ? "Crni razmišlja..." : "Na potezu si!"
        }
    }

    // MARK: - Tap

    func tap(position: Position) {
        guard isPlayerTurn else { return }

        if let move = legalMovesForSelected.first(where: { $0.to == position }) {
            execute(move)
            return
        }

        let piece = gameState.board[position.row][position.col]
        if let piece, piece.color == .white {
            if selectedPosition != position { Haptics.selection() }
            selectedPosition = position
            legalMovesForSelected = MoveGenerator.legalMoves(for: .white, in: gameState)
                .filter { $0.from == position }
        } else {
            selectedPosition = nil
            legalMovesForSelected = []
        }
    }

    // MARK: - Execute player move

    private func execute(_ move: ChessMove) {
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
            try? await Task.sleep(for: .milliseconds(250))
            self.animatingPiece = nil
        }

        switch gameState.status {
        case .checkmate: Haptics.notification(.success)
        case .draw:      Haptics.notification(.warning)
        default:         triggerAI()
        }
    }

    // MARK: - AI (easy, plays Black)

    private func triggerAI() {
        guard gameState.currentTurn == .black else { return }
        isThinking = true
        let snap = gameState

        Task {
            try? await Task.sleep(for: .milliseconds(450))

            let move = await Task.detached(priority: .userInitiated) {
                ChessAI(difficulty: .easy).bestMove(for: .black, in: snap)
            }.value

            guard let move else { self.isThinking = false; return }

            if let piece = self.gameState.board[move.from.row][move.from.col] {
                self.animatingPiece = AnimatingPiece(piece: piece, from: move.from, to: move.to)
            }

            self.gameState = self.gameState.applying(move)
            self.lastMove  = move
            SoundManager.shared.playMove()
            Haptics.impact(.light)

            Task {
                try? await Task.sleep(for: .milliseconds(250))
                self.animatingPiece = nil
            }

            self.isThinking = false
        }
    }

    // MARK: - Reset

    func reset() {
        isThinking            = false
        selectedPosition      = nil
        legalMovesForSelected = []
        lastMove              = nil
        animatingPiece        = nil
        gameState = GameState.fromFEN(startFEN) ?? .initial()
    }
}
