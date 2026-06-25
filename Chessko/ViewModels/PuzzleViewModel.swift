import SwiftUI

// MARK: - Puzzle Phase

enum PuzzlePhase: Equatable {
    case loading
    case networkError(String)
    case playing          // waiting for player's move
    case wrongMove        // player just tried the wrong move
    case solved           // all moves found correctly
    case showingSolution  // auto-playing remaining moves
}

// MARK: - Puzzle View Model

@Observable
@MainActor
final class PuzzleViewModel {

    // MARK: - Published State

    var gameState: GameState = .initial()
    var playerColor: PieceColor = .white
    var selectedPosition: Position?
    var legalMovesForSelected: [ChessMove] = []
    var lastMove: ChessMove?
    var animatingPiece: AnimatingPiece?
    var flyingCapture: FlyingCapture?

    var currentPuzzle: ChessPuzzle?
    var phase: PuzzlePhase = .loading

    // MARK: - Date Navigation

    var selectedDate: Date = Calendar.current.startOfDay(for: Date())
    private(set) var solvedDates: Set<String> = []

    private static let cal = Calendar.current
    private let solvedKey = "chessko.solvedDates"

    var canGoPrevious: Bool {
        selectedDate > minSelectableDate
    }

    var canGoNext: Bool {
        selectedDate < maxSelectableDate
    }

    var minSelectableDate: Date {
        Self.cal.date(byAdding: .month, value: -2,
                      to: Self.cal.startOfDay(for: Date())) ?? Date()
    }

    var maxSelectableDate: Date {
        Self.cal.startOfDay(for: Date())
    }

    func goToPrevious() {
        guard canGoPrevious,
              let d = Self.cal.date(byAdding: .day, value: -1, to: selectedDate)
        else { return }
        load(date: d)
    }

    func goToNext() {
        guard canGoNext,
              let d = Self.cal.date(byAdding: .day, value: 1, to: selectedDate)
        else { return }
        load(date: d)
    }

    func load(date: Date) {
        selectedDate = Self.cal.startOfDay(for: date)
        Task { await fetchPuzzle() }
    }

    func dateKey(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }

    func isSolved(_ date: Date) -> Bool {
        solvedDates.contains(dateKey(date))
    }

    private func markCurrentSolved() {
        solvedDates.insert(dateKey(selectedDate))
        UserDefaults.standard.set(Array(solvedDates), forKey: solvedKey)
    }

    private func loadSolvedDates() {
        let arr = UserDefaults.standard.stringArray(forKey: solvedKey) ?? []
        solvedDates = Set(arr)
    }

    // MARK: - Private

    private var rawMoves: [String] = []  // all UCI moves from the puzzle
    private var movePointer: Int = 0     // index of the next move to apply/find

    // MARK: - Computed

    var isFlipped: Bool { playerColor == .black }

    var isPlayerTurn: Bool {
        phase == .playing || phase == .wrongMove
    }

    var statusMessage: String {
        switch phase {
        case .loading:         return "Učitavam zadatak..."
        case .networkError:    return "Greška pri učitavanju."
        case .playing:
            let side = playerColor == .white ? "bele" : "crne"
            return "Pronađi pravi potez za \(side)"
        case .wrongMove:       return "Pogrešno. Pokušaj ponovo."
        case .solved:          return "Odlično! Zadatak rešen! 🎉"
        case .showingSolution: return "Rešenje..."
        }
    }

    // MARK: - Load

    func loadDailyPuzzle() async {
        loadSolvedDates()
        await fetchPuzzle()
    }

    private func fetchPuzzle() async {
        phase = .loading
        currentPuzzle = nil

        let dayIndex = Self.cal.ordinality(of: .day, in: .era, for: selectedDate) ?? 1

        guard let url = URL(string:
            "https://chess-puzzles-api.vercel.app/puzzles?start=\(dayIndex % 10_000)&limit=1")
        else { phase = .networkError("Neispravan URL"); return }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let puzzles   = try JSONDecoder().decode([ChessPuzzle].self, from: data)
            guard let puzzle = puzzles.first else {
                phase = .networkError("Nema dostupnih zadataka"); return
            }
            setup(puzzle: puzzle)
        } catch {
            phase = .networkError("Greška mreže: \(error.localizedDescription)")
        }
    }

    // MARK: - Setup

    private func setup(puzzle: ChessPuzzle) {
        guard let state = GameState.fromFEN(puzzle.fen) else {
            phase = .networkError("Neispravan FEN"); return
        }

        currentPuzzle = puzzle
        rawMoves      = puzzle.uciMoves
        movePointer   = 0
        selectedPosition     = nil
        legalMovesForSelected = []
        lastMove      = nil
        animatingPiece = nil
        flyingCapture  = nil

        playerColor = state.currentTurn.opposite
        gameState   = state

        Task {
            try? await Task.sleep(for: .milliseconds(400))
            applyNextComputerMove()
        }
    }

    // MARK: - Square Tap

    func tap(position: Position) {
        guard isPlayerTurn else { return }

        if let move = legalMovesForSelected.first(where: { $0.to == position }) {
            attempt(move: move)
            return
        }

        let piece = gameState.board[position.row][position.col]

        if let piece, piece.color == playerColor {
            selectedPosition      = position
            legalMovesForSelected = MoveGenerator.legalMoves(for: playerColor, in: gameState)
                .filter { $0.from == position }
            if phase == .wrongMove { phase = .playing }
        } else {
            selectedPosition      = nil
            legalMovesForSelected = []
        }
    }

    // MARK: - Attempt Player Move

    private func attempt(move: ChessMove) {
        guard movePointer < rawMoves.count,
              let expected = ChessMove.fromUCI(rawMoves[movePointer], in: gameState)
        else { return }

        guard move.from == expected.from && move.to == expected.to else {
            Haptics.notification(.error)
            phase = .wrongMove
            selectedPosition      = nil
            legalMovesForSelected = []
            return
        }

        Haptics.impact(.medium)
        apply(move: move, isPlayerMove: true)
        movePointer += 1

        if movePointer >= rawMoves.count {
            phase = .solved
            Haptics.notification(.success)
            markCurrentSolved()
            return
        }

        phase = .playing
        Task {
            try? await Task.sleep(for: .milliseconds(600))
            applyNextComputerMove()
        }
    }

    // MARK: - Computer Move

    private func applyNextComputerMove() {
        guard movePointer < rawMoves.count,
              let move = ChessMove.fromUCI(rawMoves[movePointer], in: gameState)
        else { return }

        apply(move: move, isPlayerMove: false)
        movePointer += 1
        phase = .playing
    }

    // MARK: - Show Solution

    func showSolution() {
        guard phase == .playing || phase == .wrongMove else { return }
        phase = .showingSolution

        Task {
            while movePointer < rawMoves.count {
                guard let move = ChessMove.fromUCI(rawMoves[movePointer], in: gameState)
                else { break }
                apply(move: move, isPlayerMove: movePointer % 2 == 1)
                movePointer += 1
                try? await Task.sleep(for: .milliseconds(700))
            }
            phase = .solved
            // Ne označavamo kao rešeno kad se prikaže rešenje
        }
    }

    // MARK: - Apply Move (with animation)

    @MainActor func apply(move: ChessMove, isPlayerMove: Bool) {
        guard let piece = gameState.board[move.from.row][move.from.col] else { return }

        animatingPiece = AnimatingPiece(piece: piece, from: move.from, to: move.to)

        let captured = gameState.board[move.to.row][move.to.col]
        if let cap = captured {
            SoundManager.shared.playCapture()
            withAnimation(.easeOut(duration: 0.15)) {
                flyingCapture = FlyingCapture(piece: cap, fromPosition: move.to)
            }
        } else {
            SoundManager.shared.playMove()
        }

        gameState = gameState.applyingForSearch(move)
        lastMove  = move
        selectedPosition      = nil
        legalMovesForSelected = []

        Task {
            try? await Task.sleep(for: .milliseconds(280))
            withAnimation(.spring(duration: 0.25)) { flyingCapture = nil }
            try? await Task.sleep(for: .milliseconds(100))
            animatingPiece = nil
        }
    }
}

// MARK: - Preview

#Preview {
    PuzzleView(viewModel: PuzzleViewModel())
}
