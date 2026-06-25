import Foundation
import ChessKitEngine

// MARK: - Stockfish Bridge
//
// Wraps ChessKitEngine's Stockfish 17 UCI engine.
//
// Requirements (add to Xcode project as bundle resources):
//   • nn-37f18f62d772.nnue  (~15 MB, small network — required)
//   • nn-1111cefa1111.nnue  (~79 MB, big network — optional, stronger)
//
// Download from: https://tests.stockfishchess.org/nns
//
// Add SPM package in Xcode → File → Add Package Dependencies:
//   https://github.com/chesskit-app/chesskit-engine

actor StockfishBridge {

    private var engine: Engine?
    private var pendingContinuation: CheckedContinuation<ChessMove?, Never>?
    private var pendingState: GameState?

    // MARK: - Availability

    nonisolated var isAvailable: Bool {
        Bundle.main.url(forResource: "nn-37f18f62d772", withExtension: "nnue") != nil
            || Bundle.main.url(forResource: "nn-1111cefa1111", withExtension: "nnue") != nil
    }

    // MARK: - Lifecycle

    func start() async {
        guard engine == nil else { return }

        let nnueBig   = Bundle.main.url(forResource: "nn-1111cefa1111",  withExtension: "nnue")
        let nnueSmall = Bundle.main.url(forResource: "nn-37f18f62d772",  withExtension: "nnue")

        let eng = Engine(type: .stockfish, loggingEnabled: false)
        engine = eng

        await eng.start()

        // Wait for isRunning
        var waited = 0
        while !(await eng.isRunning), waited < 50 {
            try? await Task.sleep(for: .milliseconds(100))
            waited += 1
        }

        // Set NNUE paths. EvalFile (big) is required — fall back to small if missing.
        let evalFile      = nnueBig ?? nnueSmall
        let evalFileSmall = nnueSmall ?? nnueBig
        if let url = evalFile      { await eng.send(command: .setoption(id: "EvalFile",      value: url.path())) }
        if let url = evalFileSmall { await eng.send(command: .setoption(id: "EvalFileSmall", value: url.path())) }

        // Subscribe to response stream after start() — stream is nil before start().
        // rawValue uses tagged format: "<bestmove> d7d5 <ponder> e2e4"
        if let stream = await eng.responseStream {
            Task { [weak self] in
                for await response in stream {
                    await self?.handle(response: response)
                }
            }
        }
    }

    // MARK: - Best Move

    /// Ask Stockfish for the best move in the given position.
    /// Returns nil if engine is not running or no legal move found.
    func bestMove(for state: GameState, depth: Int = 12) async -> ChessMove? {
        guard let engine else { return nil }

        // Wait up to 5 s for engine to finish startup
        var waited = 0
        while !(await engine.isRunning), waited < 50 {
            try? await Task.sleep(for: .milliseconds(100))
            waited += 1
        }
        guard await engine.isRunning else { return nil }

        pendingState = state
        await engine.send(command: .stop)
        await engine.send(command: .position(.fen(state.fen)))
        await engine.send(command: .go(depth: depth))

        return await withCheckedContinuation { continuation in
            pendingContinuation = continuation
        }
    }

    // MARK: - Response Handler

    private func handle(response: EngineResponse) {
        // rawValue uses tagged format: "<bestmove> d7d5 <ponder> e2e4"
        let raw = response.rawValue
        guard raw.hasPrefix("<bestmove>") else { return }
        guard let state = pendingState, let cont = pendingContinuation else { return }

        // tokens: ["<bestmove>", "d7d5", "<ponder>", "e2e4"]
        let tokens = raw.split(separator: " ")
        guard tokens.count >= 2 else { return }
        let uciMove = String(tokens[1])

        pendingContinuation = nil
        pendingState = nil
        cont.resume(returning: parseUCI(uciMove, in: state))
    }

    // MARK: - UCI Move Parser

    /// Converts UCI move string (e.g. "e2e4", "e7e8q") to ChessMove
    /// by matching against legal moves so flags (castling, en passant) are set correctly.
    private func parseUCI(_ uci: String, in state: GameState) -> ChessMove? {
        let chars = Array(uci)
        guard chars.count >= 4,
              let fc = chars[0].asciiValue, let fr = chars[1].asciiValue,
              let tc = chars[2].asciiValue, let tr = chars[3].asciiValue else { return nil }

        let fromCol = Int(fc) - 97          // 'a'=0 … 'h'=7
        let fromRow = 7 - (Int(fr) - 49)   // '1'=row7 … '8'=row0
        let toCol   = Int(tc) - 97
        let toRow   = 7 - (Int(tr) - 49)

        guard (0...7).contains(fromCol), (0...7).contains(fromRow),
              (0...7).contains(toCol),   (0...7).contains(toRow) else { return nil }

        let from = Position(row: fromRow, col: fromCol)
        let to   = Position(row: toRow,   col: toCol)
        let legal = MoveGenerator.legalMoves(for: state.currentTurn, in: state)

        if chars.count >= 5 {
            // Promotion suffix: q r b n
            let promType: PieceType = switch chars[4] {
            case "r": .rook
            case "b": .bishop
            case "n": .knight
            default:  .queen
            }
            return legal.first { $0.from == from && $0.to == to && $0.flag == .promotion(promType) }
        }

        // Matches castling, en passant, and normal moves by from/to coordinates
        return legal.first { $0.from == from && $0.to == to }
    }
}
