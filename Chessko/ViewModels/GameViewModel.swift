import SwiftUI
import UIKit

// MARK: - Haptics

enum Haptics {
    @MainActor
    static func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        UIImpactFeedbackGenerator(style: style).impactOccurred()
    }
    @MainActor
    static func notification(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        UINotificationFeedbackGenerator().notificationOccurred(type)
    }
    @MainActor
    static func selection() {
        UISelectionFeedbackGenerator().selectionChanged()
    }
}

// MARK: - Difficulty

enum GameDifficulty: String, CaseIterable, Sendable, Codable {
    case easy      = "easy"
    case medium    = "medium"
    case hard      = "hard"
    case stockfish = "stockfish"

    var label: String {
        switch self {
        case .easy:      return "Lak"
        case .medium:    return "Srednji"
        case .hard:      return "Težak"
        case .stockfish: return "Stockfish"
        }
    }

    var icon: String {
        switch self {
        case .easy:      return "🟢"
        case .medium:    return "🟡"
        case .hard:      return "🔴"
        case .stockfish: return "⚡"
        }
    }

    var chessAIDifficulty: ChessAI.Difficulty {
        switch self {
        case .easy:             return .easy
        case .medium:           return .medium
        case .hard, .stockfish: return .hard
        }
    }
}

// MARK: - Animating Piece

struct AnimatingPiece: Sendable {
    let piece: ChessPiece
    let from: Position
    let to: Position
}

// MARK: - Flying Capture
// Tracks a piece that was just captured and is animating from board to captured section.

struct FlyingCapture: Sendable {
    let piece: ChessPiece
    let fromPosition: Position
}

// MARK: - Game View Model

@Observable
@MainActor
final class GameViewModel {

    // MARK: State

    var gameState: GameState = .initial()
    var selectedPosition: Position?
    var legalMovesForSelected: [ChessMove] = []
    var isThinking = false
    var lastMove: ChessMove?
    var difficulty: GameDifficulty = .medium
    var promotionMove: ChessMove?
    var showPromotion = false
    var animatingPiece: AnimatingPiece?
    var flyingCapture: FlyingCapture?

    var playerColor: PieceColor = .white

    private let stockfish = StockfishBridge()
    private var gameGeneration = 0

    // Undo history: (state before move, lastMove before move)
    private var history: [(state: GameState, lastMove: ChessMove?)] = []

    // MARK: Init

    init() {
        Task { await stockfish.start() }
        load()   // restore game from previous session (no-op if nothing saved)
    }

    // MARK: Computed

    var isPlayerTurn: Bool { gameState.currentTurn == playerColor && !isThinking }

    var isGameOver: Bool {
        switch gameState.status {
        case .checkmate, .draw: return true
        default: return false
        }
    }

    var isStockfishAvailable: Bool { stockfish.isAvailable }

    var canUndo: Bool { !history.isEmpty && !isThinking && !isGameOver }

    var hasSavedGame: Bool { UserDefaults.standard.data(forKey: savedGameKey) != nil }

    /// True when the board should be displayed from black's perspective (rows and cols reversed).
    var isFlipped: Bool { playerColor == .black }

    var statusMessage: String {
        switch gameState.status {
        case .playing:
            return gameState.currentTurn == playerColor ? "Tvoj potez" : "Računar razmišlja..."
        case .check(let c):
            return c == playerColor ? "Šah! Tvoj kralj je napadnut." : "Šah! Napadaš kralja."
        case .checkmate(let c):
            return c == playerColor ? "Mat! Izgubio si." : "Mat! Pobedio si! 🎉"
        case .draw(let reason):
            switch reason {
            case .stalemate:            return "Pat – remi!"
            case .fiftyMoves:           return "Remi – pravilo 50 poteza."
            case .repetition:           return "Remi – ponavljanje pozicije."
            case .insufficientMaterial: return "Remi – nedovoljan materijal."
            }
        }
    }

    // MARK: - User Interaction

    func tap(position: Position) {
        guard isPlayerTurn, !isGameOver else { return }

        if selectedPosition != nil,
           let move = legalMovesForSelected.first(where: { $0.to == position }) {
            handleMove(move)
            return
        }

        if let piece = gameState.board[position.row][position.col], piece.color == playerColor {
            if selectedPosition != position {
                Haptics.selection()
            }
            selectedPosition = position
            legalMovesForSelected = MoveGenerator.legalMoves(for: playerColor, in: gameState)
                .filter { $0.from == position }
        } else {
            selectedPosition = nil
            legalMovesForSelected = []
        }
    }

    private func handleMove(_ move: ChessMove) {
        if case .promotion = move.flag {
            // Show promotion picker — wait for player to choose
            promotionMove = move
            showPromotion = true
        } else {
            execute(move)
        }
    }

    /// Called by the UI when the player picks a promotion piece.
    func confirmPromotion(_ type: PieceType) {
        guard let move = promotionMove else { return }
        promotionMove = nil
        showPromotion = false
        execute(ChessMove(from: move.from, to: move.to, flag: .promotion(type)))
    }

    /// Called if the player dismisses the promotion sheet without choosing.
    func cancelPromotion() {
        promotionMove = nil
        showPromotion = false
        selectedPosition = nil
        legalMovesForSelected = []
    }

    private func execute(_ move: ChessMove, addToHistory: Bool = true) {
        // ── Capture info BEFORE state change ────────────────────────────────
        let capturedPiece: ChessPiece?
        let captureFromPos: Position
        if move.flag == .enPassant {
            // En passant: captured pawn is one row in front of the destination
            let dir = gameState.currentTurn == .white ? 1 : -1
            let epPos = Position(row: move.to.row + dir, col: move.to.col)
            capturedPiece = gameState.board[epPos.row][epPos.col]
            captureFromPos = epPos
        } else {
            capturedPiece = gameState.board[move.to.row][move.to.col]
            captureFromPos = move.to
        }
        let isCapture = capturedPiece != nil

        // Exclude captured piece from display immediately (BEFORE state change)
        if let captured = capturedPiece {
            flyingCapture = FlyingCapture(piece: captured, fromPosition: captureFromPos)
        }

        if addToHistory {
            history.append((state: gameState, lastMove: lastMove))
        }

        // Set up piece animation BEFORE state change (same render cycle = no flicker)
        if let piece = gameState.board[move.from.row][move.from.col] {
            animatingPiece = AnimatingPiece(piece: piece, from: move.from, to: move.to)
        }

        let newState = gameState.applying(move)

        // Apply board state without animation (piece is handled by AnimatingPieceView)
        gameState = newState
        save()   // persist after every move

        // Animate UI elements (highlights, selection dots)
        withAnimation(.easeInOut(duration: 0.2)) {
            lastMove = move
            selectedPosition = nil
            legalMovesForSelected = []
        }

        // Sound + haptics based on outcome
        switch newState.status {
        case .checkmate(let c):
            SoundManager.shared.playMove()
            Haptics.notification(c == playerColor ? .error : .success)
        case .draw:
            SoundManager.shared.playMove()
            Haptics.notification(.warning)
        case .check:
            SoundManager.shared.playMove()
            Haptics.notification(.warning)
        case .playing:
            if isCapture { SoundManager.shared.playCapture() } else { SoundManager.shared.playMove() }
            Haptics.impact(isCapture ? .medium : .light)
        }

        // Clear slide animation then spring the captured piece into its section
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(280))
            animatingPiece = nil
            if self.flyingCapture != nil {
                withAnimation(.spring(duration: 0.4, bounce: 0.3)) {
                    self.flyingCapture = nil
                }
            }
        }

        if !isGameOver { triggerAI() }
    }

    // MARK: - Undo

    func undo() {
        guard canUndo else { return }
        flyingCapture = nil
        // Pop states until we're back at the player's turn
        while let last = history.last {
            history.removeLast()
            if last.state.currentTurn == playerColor {
                withAnimation(.easeInOut(duration: 0.2)) {
                    gameState = last.state
                    lastMove = last.lastMove
                    selectedPosition = nil
                    legalMovesForSelected = []
                }
                SoundManager.shared.playMove()
                Haptics.impact(.rigid)
                save()
                return
            }
        }
    }

    // MARK: - Difficulty

    func setDifficulty(_ newDifficulty: GameDifficulty) {
        difficulty = newDifficulty
    }

    // MARK: - AI

    private func triggerAI() {
        guard gameState.currentTurn != playerColor else { return }
        isThinking = true

        let capturedState = gameState
        let capturedColor = gameState.currentTurn
        let capturedDifficulty = difficulty
        let capturedGeneration = gameGeneration
        let stockfishRef = stockfish

        Task {
            try? await Task.sleep(for: .milliseconds(300))

            let move: ChessMove?

            if capturedDifficulty == .stockfish && stockfishRef.isAvailable {
                move = await stockfishRef.bestMove(for: capturedState)
            } else {
                let aiDiff = capturedDifficulty.chessAIDifficulty
                move = await Task.detached(priority: .userInitiated) {
                    ChessAI(difficulty: aiDiff).bestMove(for: capturedColor, in: capturedState)
                }.value
            }

            guard self.gameGeneration == capturedGeneration else {
                self.isThinking = false
                return
            }

            if let move {
                // Capture info BEFORE state change
                let capturedPiece: ChessPiece?
                let captureFromPos: Position
                if move.flag == .enPassant {
                    let dir = self.gameState.currentTurn == .white ? 1 : -1
                    let epPos = Position(row: move.to.row + dir, col: move.to.col)
                    capturedPiece = self.gameState.board[epPos.row][epPos.col]
                    captureFromPos = epPos
                } else {
                    capturedPiece = self.gameState.board[move.to.row][move.to.col]
                    captureFromPos = move.to
                }
                let isCapture = capturedPiece != nil

                if let captured = capturedPiece {
                    self.flyingCapture = FlyingCapture(piece: captured, fromPosition: captureFromPos)
                }

                self.history.append((state: self.gameState, lastMove: self.lastMove))

                if let piece = self.gameState.board[move.from.row][move.from.col] {
                    self.animatingPiece = AnimatingPiece(piece: piece, from: move.from, to: move.to)
                }

                let newState = self.gameState.applying(move)
                self.gameState = newState
                self.save()   // persist after AI move

                withAnimation(.easeInOut(duration: 0.2)) {
                    self.lastMove = move
                }

                switch newState.status {
                case .checkmate(let c):
                    SoundManager.shared.playMove()
                    Haptics.notification(c == self.playerColor ? .error : .success)
                case .draw:
                    SoundManager.shared.playMove()
                    Haptics.notification(.warning)
                case .check:
                    SoundManager.shared.playMove()
                    Haptics.notification(.warning)
                case .playing:
                    if isCapture { SoundManager.shared.playCapture() } else { SoundManager.shared.playMove() }
                    Haptics.impact(isCapture ? .medium : .light)
                }

                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(280))
                    self.animatingPiece = nil
                    if self.flyingCapture != nil {
                        withAnimation(.spring(duration: 0.4, bounce: 0.3)) {
                            self.flyingCapture = nil
                        }
                    }
                }
            }
            self.isThinking = false
        }
    }

    // MARK: - Persistence

    /// Codable snapshot of everything needed to resume a game.
    private struct SavedGame: Codable {
        struct HistoryEntry: Codable {
            let state: GameState
            let lastMove: ChessMove?
        }
        let gameState: GameState
        let history: [HistoryEntry]
        let lastMove: ChessMove?
        let difficulty: GameDifficulty
        let playerColor: PieceColor

        // Backward-compatible decode: old saves without playerColor default to .white
        private enum CodingKeys: String, CodingKey {
            case gameState, history, lastMove, difficulty, playerColor
        }

        init(gameState: GameState, history: [HistoryEntry], lastMove: ChessMove?,
             difficulty: GameDifficulty, playerColor: PieceColor) {
            self.gameState   = gameState
            self.history     = history
            self.lastMove    = lastMove
            self.difficulty  = difficulty
            self.playerColor = playerColor
        }

        init(from decoder: Decoder) throws {
            let c       = try decoder.container(keyedBy: CodingKeys.self)
            gameState   = try c.decode(GameState.self,        forKey: .gameState)
            history     = try c.decode([HistoryEntry].self,   forKey: .history)
            lastMove    = try c.decodeIfPresent(ChessMove.self, forKey: .lastMove)
            difficulty  = try c.decode(GameDifficulty.self,   forKey: .difficulty)
            playerColor = (try? c.decode(PieceColor.self,     forKey: .playerColor)) ?? .white
        }
    }

    private let savedGameKey = "chessko.savedGame"

    /// Serialise current state to UserDefaults.
    private func save() {
        let snap = SavedGame(
            gameState:   gameState,
            history:     history.map { SavedGame.HistoryEntry(state: $0.state, lastMove: $0.lastMove) },
            lastMove:    lastMove,
            difficulty:  difficulty,
            playerColor: playerColor
        )
        if let data = try? JSONEncoder().encode(snap) {
            UserDefaults.standard.set(data, forKey: savedGameKey)
        }
    }

    /// Restore state from UserDefaults; no-op if nothing is saved or data is corrupt.
    private func load() {
        guard
            let data  = UserDefaults.standard.data(forKey: savedGameKey),
            let saved = try? JSONDecoder().decode(SavedGame.self, from: data)
        else { return }

        gameState   = saved.gameState
        history     = saved.history.map { ($0.state, $0.lastMove) }
        lastMove    = saved.lastMove
        difficulty  = saved.difficulty
        playerColor = saved.playerColor

        // If it's the AI's turn at load time, trigger it
        if gameState.currentTurn != playerColor && !isGameOver {
            triggerAI()
        }
    }

    private func clearSave() {
        UserDefaults.standard.removeObject(forKey: savedGameKey)
    }

    // MARK: - New Game

    func newGame(playerColor: PieceColor = .white) {
        self.playerColor = playerColor
        gameGeneration += 1
        flyingCapture = nil
        clearSave()
        history.removeAll()
        withAnimation {
            gameState = .initial()
            selectedPosition = nil
            legalMovesForSelected = []
            lastMove = nil
            isThinking = false
        }
        // When player picks black, AI (white) moves first
        if playerColor == .black {
            triggerAI()
        }
    }

    func loadDebugPromotion() {
        gameGeneration += 1
        clearSave()
        history.removeAll()
        withAnimation {
            gameState = .debugPromotion()
            selectedPosition = nil
            legalMovesForSelected = []
            lastMove = nil
            isThinking = false
        }
    }
}
