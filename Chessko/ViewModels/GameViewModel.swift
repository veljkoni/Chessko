import SwiftUI
import UIKit

// MARK: - Haptics

enum Haptics {
    @MainActor
    static func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        guard UserDefaults.standard.bool(forKey: "hapticsEnabled") else { return }
        UIImpactFeedbackGenerator(style: style).impactOccurred()
    }
    @MainActor
    static func notification(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        guard UserDefaults.standard.bool(forKey: "hapticsEnabled") else { return }
        UINotificationFeedbackGenerator().notificationOccurred(type)
    }
    @MainActor
    static func selection() {
        guard UserDefaults.standard.bool(forKey: "hapticsEnabled") else { return }
        UISelectionFeedbackGenerator().selectionChanged()
    }
}

// MARK: - Difficulty

enum GameDifficulty: String, CaseIterable, Sendable, Codable {
    case beginner  = "beginner"
    case easy      = "easy"
    case medium    = "medium"
    case hard      = "hard"
    case stockfish = "stockfish"

    var title: String {
        switch self {
        case .beginner:  return Loc("Početnik (~500 ELO)")
        case .easy:      return Loc("Lako (~900 ELO)")
        case .medium:    return Loc("Srednje (~1300 ELO)")
        case .hard:      return Loc("Teško (~1700 ELO)")
        case .stockfish: return Loc("Stockfish Majstor (2200+ ELO)")
        }
    }

    var subtitle: String {
        switch self {
        case .beginner:  return Loc("Za one koji uče pravila i osnove")
        case .easy:      return Loc("Opuštena i prijatna partija")
        case .medium:    return Loc("Dobar balans za redovne igrače")
        case .hard:      return Loc("Snažna igra bez previda")
        case .stockfish: return Loc("Maksimalna snaga šahovskog motora")
        }
    }

    var label: String {
        switch self {
        case .beginner:  return Loc("Početnik")
        case .easy:      return Loc("Lako")
        case .medium:    return Loc("Srednje")
        case .hard:      return Loc("Teško")
        case .stockfish: return "Stockfish"
        }
    }

    var chessAIDifficulty: ChessAI.Difficulty {
        switch self {
        case .beginner:         return .beginner
        case .easy:             return .easy
        case .medium:           return .medium
        case .hard, .stockfish: return .hard
        }
    }
}

// MARK: - Stockfish Level
//
// Strength of the Stockfish engine, controlled purely by search depth.
// (UCI_LimitStrength/UCI_Elo wedged the engine on the 2nd move, so we weaken
//  it by limiting `go depth` instead — the same flow that always worked.)

enum StockfishLevel: String, CaseIterable, Sendable, Codable {
    case beginner
    case casual
    case intermediate
    case advanced
    case expert
    case maximum

    /// Search depth passed to `go depth`. Higher = stronger.
    var searchDepth: Int {
        switch self {
        case .beginner:     return 1
        case .casual:       return 3
        case .intermediate: return 5
        case .advanced:     return 8
        case .expert:       return 11
        case .maximum:      return 15
        }
    }

    var label: String {
        switch self {
        case .beginner:     return Loc("Stockfish: 1300 ELO")
        case .casual:       return Loc("Stockfish: 1600 ELO")
        case .intermediate: return Loc("Stockfish: 1900 ELO")
        case .advanced:     return Loc("Stockfish: 2200 ELO")
        case .expert:       return Loc("Stockfish: 2600 ELO")
        case .maximum:      return Loc("Stockfish: Maksimalno")
        }
    }

    /// Approximate ELO for display purposes only (not used for engine strength).
    var elo: String? {
        switch self {
        case .beginner:     return "~1320"
        case .casual:       return "~1600"
        case .intermediate: return "~1900"
        case .advanced:     return "~2200"
        case .expert:       return "~2600"
        case .maximum:      return nil
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

// MARK: - Game Mode

enum GameMode: String, Codable {
    case vsComputer
    case localFriend
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
    var difficulty: GameDifficulty = {
        if let raw = UserDefaults.standard.string(forKey: "selectedDifficulty"),
           let diff = GameDifficulty(rawValue: raw) {
            return diff
        }
        return .medium
    }()
    private var hasRecordedGameEnd: Bool = false
    var stockfishLevel: StockfishLevel = .intermediate
    var promotionMove: ChessMove?
    var showPromotion = false
    var animatingPiece: AnimatingPiece?
    var flyingCapture: FlyingCapture?

    var gameMode: GameMode = .vsComputer
    var playerColor: PieceColor = .white

    var activePlayerColor: PieceColor {
        gameMode == .localFriend ? gameState.currentTurn : playerColor
    }

    // Game review (move-by-move navigation)
    var viewingMoveIndex: Int? = nil

    // Evaluation bar
    var evaluationScore: Double = 0.0
    var evaluationMateIn: Int? = nil

    private let stockfish = StockfishBridge()
    private var gameGeneration = 0

    // Undo history: (state before move, lastMove before move)
    private var history: [(state: GameState, lastMove: ChessMove?)] = []

    // MARK: Init

    init() {
        UserDefaults.standard.register(defaults: [
            "soundEnabled": true,
            "hapticsEnabled": true,
            "boardTheme": "classic",
            "showCoordinates": true,
            "showLastMoveHighlight": true,
            "showLegalMoves": true,
            "autoPromoteToQueen": false,
            "pieceStyle": "classic",
            "rotateBoardInLocalPlay": true,
            "showEvalBar": true
        ])
        Task { await stockfish.start() }
        load()   // restore game from previous session (no-op if nothing saved)
        updateEvaluation()
    }

    // MARK: Computed

    var allHistoryStates: [(state: GameState, lastMove: ChessMove?)] {
        var list: [(state: GameState, lastMove: ChessMove?)] = []
        if !history.isEmpty {
            list.append((history[0].state, nil))
            for i in 1..<history.count {
                list.append((history[i].state, history[i].lastMove))
            }
            list.append((gameState, lastMove))
        } else {
            list.append((gameState, lastMove))
        }
        return list
    }

    var displayState: GameState {
        guard let idx = viewingMoveIndex, idx < allHistoryStates.count else { return gameState }
        return allHistoryStates[idx].state
    }

    var displayLastMove: ChessMove? {
        guard let idx = viewingMoveIndex, idx < allHistoryStates.count else { return lastMove }
        return allHistoryStates[idx].lastMove
    }

    var isReviewing: Bool {
        viewingMoveIndex != nil && viewingMoveIndex != (allHistoryStates.count - 1)
    }

    var currentReviewMoveIndex: Int {
        viewingMoveIndex ?? max(0, allHistoryStates.count - 1)
    }

    var totalReviewMoves: Int {
        max(0, allHistoryStates.count - 1)
    }

    var canStepBackward: Bool {
        currentReviewMoveIndex > 0
    }

    var canStepForward: Bool {
        viewingMoveIndex != nil && viewingMoveIndex! < (allHistoryStates.count - 1)
    }

    func goToStart() {
        if !allHistoryStates.isEmpty {
            viewingMoveIndex = 0
            updateEvaluation()
        }
    }

    func stepBackward() {
        let cur = viewingMoveIndex ?? (allHistoryStates.count - 1)
        if cur > 0 {
            viewingMoveIndex = cur - 1
            updateEvaluation()
        }
    }

    func stepForward() {
        let cur = viewingMoveIndex ?? (allHistoryStates.count - 1)
        if cur < allHistoryStates.count - 1 {
            let next = cur + 1
            viewingMoveIndex = (next == allHistoryStates.count - 1) ? nil : next
            updateEvaluation()
        }
    }

    func goToEnd() {
        viewingMoveIndex = nil
        updateEvaluation()
    }

    func goToMove(_ moveNumber: Int) {
        if moveNumber >= 0 && moveNumber < allHistoryStates.count {
            viewingMoveIndex = (moveNumber == allHistoryStates.count - 1) ? nil : moveNumber
            updateEvaluation()
        }
    }

    func updateEvaluation() {
        let state = displayState
        Task.detached {
            let (score, mate) = ChessAI.evaluatePosition(state: state)
            await MainActor.run {
                self.evaluationScore = score
                self.evaluationMateIn = mate
            }
        }
    }

    func resign() {
        guard canResign else { return }
        let loser = activePlayerColor
        gameState.status = .resigned(loser)
        viewingMoveIndex = nil
        save()
        SoundManager.shared.playMove()
        Haptics.notification(.error)
        updateEvaluation()

        if gameMode == .vsComputer && !hasRecordedGameEnd {
            hasRecordedGameEnd = true
            if loser == playerColor {
                StatsManager.shared.recordGameLost()
            } else {
                StatsManager.shared.recordGameWon()
            }
        }
    }

    var isPlayerTurn: Bool {
        if isReviewing { return false }
        if gameMode == .localFriend {
            return !isGameOver && !isThinking
        } else {
            return gameState.currentTurn == playerColor && !isThinking
        }
    }

    var isGameOver: Bool {
        switch gameState.status {
        case .checkmate, .draw, .resigned: return true
        default: return false
        }
    }

    var isStockfishAvailable: Bool { stockfish.isAvailable }

    var canUndo: Bool { !history.isEmpty && !isThinking && !isGameOver && !isReviewing }

    var canResign: Bool { !isGameOver && !isThinking && (!history.isEmpty || !gameState.moveNotations.isEmpty) }

    var hasSavedGame: Bool { UserDefaults.standard.data(forKey: savedGameKey) != nil }

    /// True when the board should be displayed from black's perspective (rows and cols reversed).
    var isFlipped: Bool {
        if gameMode == .localFriend {
            return UserDefaults.standard.bool(forKey: "rotateBoardInLocalPlay") && gameState.currentTurn == .black
        } else {
            return playerColor == .black
        }
    }

    var statusMessage: String {
        switch gameState.status {
        case .playing:
            if gameMode == .localFriend {
                return gameState.currentTurn == .white
                    ? Loc("Beli igra")
                    : Loc("Crni igra")
            } else {
                return gameState.currentTurn == playerColor
                    ? Loc("Tvoj potez")
                    : Loc("Računar razmišlja...")
            }
        case .check(let c):
            if gameMode == .localFriend {
                return c == .white
                    ? Loc("Šah! Beli kralj je napadnut.")
                    : Loc("Šah! Crni kralj je napadnut.")
            } else {
                return c == playerColor
                    ? Loc("Šah! Tvoj kralj je napadnut.")
                    : Loc("Šah! Napadaš kralja.")
            }
        case .checkmate(let c):
            if gameMode == .localFriend {
                return c == .white
                    ? Loc("Mat! Crni je pobedio! 🎉")
                    : Loc("Mat! Beli je pobedio! 🎉")
            } else {
                return c == playerColor
                    ? Loc("Mat! Izgubio si.")
                    : Loc("Mat! Pobedio si! 🎉")
            }
        case .resigned(let c):
            if gameMode == .localFriend {
                return c == .white
                    ? Loc("Predaja! Crni je pobedio.")
                    : Loc("Predaja! Beli je pobedio.")
            } else {
                return c == playerColor
                    ? Loc("Predaja! Izgubio si.")
                    : Loc("Predaja! Pobedio si! 🎉")
            }
        case .draw(let reason):
            switch reason {
            case .stalemate:            return Loc("Pat – remi!")
            case .fiftyMoves:           return Loc("Remi – pravilo 50 poteza.")
            case .repetition:           return Loc("Remi – ponavljanje pozicije.")
            case .insufficientMaterial: return Loc("Remi – nedovoljan materijal.")
            }
        }
    }

    // MARK: - User Interaction

    func tap(position: Position) {
        if isReviewing {
            goToEnd()
            return
        }
        guard isPlayerTurn, !isGameOver else { return }

        if selectedPosition != nil,
           let move = legalMovesForSelected.first(where: { $0.to == position }) {
            handleMove(move)
            return
        }

        if let piece = gameState.board[position.row][position.col], piece.color == activePlayerColor {
            if selectedPosition != position {
                Haptics.selection()
            }
            selectedPosition = position
            legalMovesForSelected = MoveGenerator.legalMoves(for: activePlayerColor, in: gameState)
                .filter { $0.from == position }
        } else {
            selectedPosition = nil
            legalMovesForSelected = []
        }
    }

    private func handleMove(_ move: ChessMove) {
        if case .promotion = move.flag {
            if UserDefaults.standard.bool(forKey: "autoPromoteToQueen") {
                execute(ChessMove(from: move.from, to: move.to, flag: .promotion(.queen)))
            } else {
                // Show promotion picker — wait for player to choose
                promotionMove = move
                showPromotion = true
            }
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
        viewingMoveIndex = nil

        if gameMode == .localFriend {
            withAnimation(.easeInOut(duration: 0.5)) {
                gameState = newState
                lastMove = move
                selectedPosition = nil
                legalMovesForSelected = []
            }
        } else {
            gameState = newState
            withAnimation(.easeInOut(duration: 0.2)) {
                lastMove = move
                selectedPosition = nil
                legalMovesForSelected = []
            }
        }
        save()   // persist after every move
        updateEvaluation()

        // Sound + haptics based on outcome
        switch newState.status {
        case .checkmate(let c):
            SoundManager.shared.playMove()
            Haptics.notification(c == playerColor ? .error : .success)
            if !hasRecordedGameEnd && gameMode == .vsComputer {
                hasRecordedGameEnd = true
                if c != playerColor {
                    StatsManager.shared.recordGameWon()
                } else {
                    StatsManager.shared.recordGameLost()
                }
            }
        case .resigned(let c):
            SoundManager.shared.playMove()
            Haptics.notification(c == playerColor ? .error : .success)
            if !hasRecordedGameEnd && gameMode == .vsComputer {
                hasRecordedGameEnd = true
                if c != playerColor {
                    StatsManager.shared.recordGameWon()
                } else {
                    StatsManager.shared.recordGameLost()
                }
            }
        case .draw:
            SoundManager.shared.playMove()
            Haptics.notification(.warning)
            if !hasRecordedGameEnd && gameMode == .vsComputer {
                hasRecordedGameEnd = true
                StatsManager.shared.recordGameDrawn()
            }
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
        viewingMoveIndex = nil

        if gameMode == .localFriend {
            if let last = history.popLast() {
                withAnimation(.easeInOut(duration: 0.5)) {
                    gameState = last.state
                    lastMove = last.lastMove
                    selectedPosition = nil
                    legalMovesForSelected = []
                }
                SoundManager.shared.playMove()
                Haptics.impact(.rigid)
                save()
                updateEvaluation()
            }
            return
        }

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
                updateEvaluation()
                return
            }
        }
    }

    // MARK: - Difficulty

    func setDifficulty(_ newDifficulty: GameDifficulty) {
        difficulty = newDifficulty
        UserDefaults.standard.set(newDifficulty.rawValue, forKey: "selectedDifficulty")
        save()
    }

    func setStockfishLevel(_ level: StockfishLevel) {
        stockfishLevel = level
        save()
    }

    // MARK: - AI

    private func triggerAI() {
        guard gameMode == .vsComputer else { return }
        guard gameState.currentTurn != playerColor else { return }
        guard !isThinking else { return }
        isThinking = true

        let capturedState = gameState
        let capturedColor = gameState.currentTurn
        let capturedDifficulty = difficulty
        let capturedDepth = stockfishLevel.searchDepth
        let capturedGeneration = gameGeneration
        let stockfishRef = stockfish

        Task {
            try? await Task.sleep(for: .milliseconds(300))

            let move: ChessMove?

            if capturedDifficulty == .stockfish && stockfishRef.isAvailable {
                move = await stockfishRef.bestMove(for: capturedState, depth: capturedDepth)
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
                    if !self.hasRecordedGameEnd && self.gameMode == .vsComputer {
                        self.hasRecordedGameEnd = true
                        if c != self.playerColor {
                            StatsManager.shared.recordGameWon()
                        } else {
                            StatsManager.shared.recordGameLost()
                        }
                    }
                case .resigned(let c):
                    SoundManager.shared.playMove()
                    Haptics.notification(c == self.playerColor ? .error : .success)
                    if !self.hasRecordedGameEnd && self.gameMode == .vsComputer {
                        self.hasRecordedGameEnd = true
                        if c != self.playerColor {
                            StatsManager.shared.recordGameWon()
                        } else {
                            StatsManager.shared.recordGameLost()
                        }
                    }
                case .draw:
                    SoundManager.shared.playMove()
                    Haptics.notification(.warning)
                    if !self.hasRecordedGameEnd && self.gameMode == .vsComputer {
                        self.hasRecordedGameEnd = true
                        StatsManager.shared.recordGameDrawn()
                    }
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
        let stockfishLevel: StockfishLevel
        let gameMode: GameMode

        private enum CodingKeys: String, CodingKey {
            case gameState, history, lastMove, difficulty, playerColor, stockfishLevel, gameMode
        }

        init(gameState: GameState, history: [HistoryEntry], lastMove: ChessMove?,
             difficulty: GameDifficulty, playerColor: PieceColor, stockfishLevel: StockfishLevel, gameMode: GameMode) {
            self.gameState      = gameState
            self.history        = history
            self.lastMove       = lastMove
            self.difficulty     = difficulty
            self.playerColor    = playerColor
            self.stockfishLevel = stockfishLevel
            self.gameMode       = gameMode
        }

        init(from decoder: Decoder) throws {
            let c          = try decoder.container(keyedBy: CodingKeys.self)
            gameState      = try c.decode(GameState.self,        forKey: .gameState)
            history        = try c.decode([HistoryEntry].self,   forKey: .history)
            lastMove       = try c.decodeIfPresent(ChessMove.self, forKey: .lastMove)
            difficulty     = try c.decode(GameDifficulty.self,   forKey: .difficulty)
            playerColor    = (try? c.decode(PieceColor.self,     forKey: .playerColor)) ?? .white
            stockfishLevel = (try? c.decode(StockfishLevel.self, forKey: .stockfishLevel)) ?? .intermediate
            gameMode       = (try? c.decode(GameMode.self,       forKey: .gameMode)) ?? .vsComputer
        }
    }

    private let savedGameKey = "chessko.savedGame"

    /// Serialise current state to UserDefaults.
    private func save() {
        let snap = SavedGame(
            gameState:      gameState,
            history:        history.map { SavedGame.HistoryEntry(state: $0.state, lastMove: $0.lastMove) },
            lastMove:       lastMove,
            difficulty:     difficulty,
            playerColor:    playerColor,
            stockfishLevel: stockfishLevel,
            gameMode:       gameMode
        )
        if let data = try? JSONEncoder().encode(snap) {
            UserDefaults.standard.set(data, forKey: savedGameKey)
        }
    }

    /// Restore state from UserDefaults; no-op if nothing is saved or data is corrupt.
    private func load() {
        if let raw = UserDefaults.standard.string(forKey: "selectedDifficulty"),
           let diff = GameDifficulty(rawValue: raw) {
            difficulty = diff
        }

        guard
            let data  = UserDefaults.standard.data(forKey: savedGameKey),
            let saved = try? JSONDecoder().decode(SavedGame.self, from: data)
        else { return }

        gameState      = saved.gameState
        history        = saved.history.map { ($0.state, $0.lastMove) }
        lastMove       = saved.lastMove
        difficulty     = saved.difficulty
        playerColor    = saved.playerColor
        stockfishLevel = saved.stockfishLevel
        gameMode       = saved.gameMode

        // If it's the AI's turn at load time, trigger it
        if gameMode == .vsComputer && gameState.currentTurn != playerColor && !isGameOver {
            triggerAI()
        }
    }

    private func clearSave() {
        UserDefaults.standard.removeObject(forKey: savedGameKey)
    }

    // MARK: - New Game

    func newGame(gameMode: GameMode = .vsComputer, playerColor: PieceColor = .white) {
        self.gameMode = gameMode
        self.playerColor = playerColor
        self.hasRecordedGameEnd = false
        self.viewingMoveIndex = nil
        if let raw = UserDefaults.standard.string(forKey: "selectedDifficulty"),
           let diff = GameDifficulty(rawValue: raw) {
            self.difficulty = diff
        }
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
        updateEvaluation()
        // When player picks black, AI (white) moves first
        if gameMode == .vsComputer && playerColor == .black {
            triggerAI()
        }
    }

    func generatePGN() -> String {
        var pgn = ""
        pgn += "[Event \"Chessko Game\"]\n"
        pgn += "[Site \"Chessko App\"]\n"
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        pgn += "[Date \"\(formatter.string(from: Date()))\"]\n"
        pgn += "[White \"\(gameMode == .localFriend ? "Player 1" : (playerColor == .white ? "Player" : "Chessko AI"))\"]\n"
        pgn += "[Black \"\(gameMode == .localFriend ? "Player 2" : (playerColor == .black ? "Player" : "Chessko AI"))\"]\n"

        let result: String
        switch gameState.status {
        case .checkmate(let loser), .resigned(let loser):
            result = loser == .white ? "0-1" : "1-0"
        case .draw:
            result = "1/2-1/2"
        default:
            result = "*"
        }
        pgn += "[Result \"\(result)\"]\n\n"

        for (index, notation) in gameState.moveNotations.enumerated() {
            if index % 2 == 0 {
                pgn += "\(index / 2 + 1). "
            }
            pgn += "\(notation) "
        }
        if !gameState.moveNotations.isEmpty {
            pgn += result
        }
        return pgn.trimmingCharacters(in: .whitespacesAndNewlines)
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
