import Foundation

// MARK: - FEN String Parsing

extension GameState {

    /// Parses a standard FEN string into a GameState.
    /// Returns nil if the FEN is malformed.
    static func fromFEN(_ fen: String) -> GameState? {
        let parts = fen.split(separator: " ", maxSplits: 5, omittingEmptySubsequences: false)
        guard parts.count >= 2 else { return nil }

        // 1. Piece placement (ranks from 8 down to 1 = rows 0..7)
        var board: [[ChessPiece?]] = Array(repeating: Array(repeating: nil, count: 8), count: 8)
        let ranks = parts[0].split(separator: "/")
        guard ranks.count == 8 else { return nil }

        for (rowIndex, rank) in ranks.enumerated() {
            var colIndex = 0
            for ch in rank {
                if let num = ch.wholeNumberValue {
                    colIndex += num
                } else {
                    let color: PieceColor = ch.isUppercase ? .white : .black
                    let type: PieceType?
                    switch ch.lowercased() {
                    case "k": type = .king
                    case "q": type = .queen
                    case "r": type = .rook
                    case "b": type = .bishop
                    case "n": type = .knight
                    case "p": type = .pawn
                    default:  type = nil
                    }
                    if let t = type, colIndex < 8 {
                        board[rowIndex][colIndex] = ChessPiece(type: t, color: color)
                    }
                    colIndex += 1
                }
            }
        }

        // 2. Active color
        let turn: PieceColor = parts[1] == "b" ? .black : .white

        // 3. Castling availability
        let castling = String(parts.count > 2 ? parts[2] : "-")
        let wck = castling.contains("K")
        let wcq = castling.contains("Q")
        let bck = castling.contains("k")
        let bcq = castling.contains("q")

        // 4. En passant target square
        var ep: Position? = nil
        if parts.count > 3, parts[3] != "-" {
            let epStr = String(parts[3])
            if let fileChar = epStr.first,
               let fileASCII = fileChar.asciiValue,
               let rankDigit = epStr.dropFirst().first?.wholeNumberValue {
                let col = Int(fileASCII) - 97   // 'a'=0
                let row = 8 - rankDigit
                if (0...7).contains(col) && (0...7).contains(row) {
                    ep = Position(row: row, col: col)
                }
            }
        }

        // 5. Halfmove clock
        let halfmove = parts.count > 4 ? (Int(String(parts[4])) ?? 0) : 0

        var state = GameState(
            board: board,
            currentTurn: turn,
            whiteCanCastleKingside:  wck,
            whiteCanCastleQueenside: wcq,
            blackCanCastleKingside:  bck,
            blackCanCastleQueenside: bcq,
            enPassantTarget: ep,
            moveHistory: [],
            capturedByWhite: [],
            capturedByBlack: [],
            status: .playing
        )
        state.halfmoveClock = halfmove
        state.positionHistory[state.positionKey] = 1
        return state
    }
}

// MARK: - FEN String Generation

extension GameState {

    /// Standard FEN representation of the current position.
    /// Example starting position: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    var fen: String {
        // 1. Piece placement (rank 8 → rank 1 = row 0 → row 7)
        var rows: [String] = []
        for row in 0..<8 {
            var rowStr = ""
            var empty = 0
            for col in 0..<8 {
                if let piece = board[row][col] {
                    if empty > 0 { rowStr += "\(empty)"; empty = 0 }
                    rowStr += piece.fenSymbol
                } else {
                    empty += 1
                }
            }
            if empty > 0 { rowStr += "\(empty)" }
            rows.append(rowStr)
        }

        // 2. Active color
        let active = currentTurn == .white ? "w" : "b"

        // 3. Castling availability
        var castling = ""
        if whiteCanCastleKingside  { castling += "K" }
        if whiteCanCastleQueenside { castling += "Q" }
        if blackCanCastleKingside  { castling += "k" }
        if blackCanCastleQueenside { castling += "q" }
        if castling.isEmpty { castling = "-" }

        // 4. En passant target square
        let ep = enPassantTarget?.algebraic ?? "-"

        // 5 & 6. Halfmove clock / fullmove number (not tracked; use safe defaults)
        return "\(rows.joined(separator: "/")) \(active) \(castling) \(ep) 0 1"
    }
}

// MARK: - FEN symbol per piece (private)

private extension ChessPiece {
    var fenSymbol: String {
        let letter: String = switch type {
        case .king:   "k"
        case .queen:  "q"
        case .rook:   "r"
        case .bishop: "b"
        case .knight: "n"
        case .pawn:   "p"
        }
        return color == .white ? letter.uppercased() : letter
    }
}
