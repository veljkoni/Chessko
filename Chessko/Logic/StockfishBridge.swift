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

    // NNUE file URLs cached once at startup.
    private var nnueBig:   URL?
    private var nnueSmall: URL?

    // MARK: - Availability

    nonisolated var isAvailable: Bool {
        Bundle.main.url(forResource: "nn-37f18f62d772", withExtension: "nnue") != nil
            || Bundle.main.url(forResource: "nn-1111cefa1111", withExtension: "nnue") != nil
    }

    // MARK: - Lifecycle

    /// Cache NNUE URLs once at startup. The engine itself is created fresh
    /// for each search so the responseStream is always in a clean state.
    func start() async {
        nnueBig   = Bundle.main.url(forResource: "nn-1111cefa1111", withExtension: "nnue")
        nnueSmall = Bundle.main.url(forResource: "nn-37f18f62d772", withExtension: "nnue")
    }

    // MARK: - Best Move

    /// Creates a fresh Engine for every search so the responseStream is always
    /// in a clean state. The engine is started, used once, then released.
    func bestMove(for state: GameState, depth: Int = 12) async -> ChessMove? {
        // Fresh engine — ensures responseStream is pristine every call.
        let eng = Engine(type: .stockfish, loggingEnabled: false)
        await eng.start()

        // Wait up to 5 s for the engine to become ready.
        var waited = 0
        while !(await eng.isRunning), waited < 50 {
            try? await Task.sleep(for: .milliseconds(100))
            waited += 1
        }
        guard await eng.isRunning else { return nil }

        // Configure NNUE. EvalFile (big) is required — fall back to small if missing.
        let evalFile      = nnueBig ?? nnueSmall
        let evalFileSmall = nnueSmall ?? nnueBig
        if let url = evalFile      { await eng.send(command: .setoption(id: "EvalFile",      value: url.path())) }
        if let url = evalFileSmall { await eng.send(command: .setoption(id: "EvalFileSmall", value: url.path())) }

        // Subscribe to the response stream before sending any search commands.
        guard let stream = await eng.responseStream else { return nil }

        await eng.send(command: .position(.fen(state.fen)))
        await eng.send(command: .go(depth: depth))

        // Iterate until we get a bestmove response.
        for await response in stream {
            let raw = response.rawValue
            guard raw.hasPrefix("<bestmove>") else { continue }
            let tokens = raw.split(separator: " ")
            guard tokens.count >= 2 else { break }
            let uciMove = String(tokens[1])
            guard uciMove != "(none)" else { break }
            return parseUCI(uciMove, in: state)
        }
        return nil
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
