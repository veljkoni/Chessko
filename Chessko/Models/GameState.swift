import Foundation

// MARK: - Draw Reason

enum DrawReason: String, Equatable, Sendable, Codable {
    case stalemate
    case fiftyMoves
    case repetition
    case insufficientMaterial
}

// MARK: - Game Status

enum GameStatus: Equatable, Sendable, Codable {
    case playing
    case check(PieceColor)
    case checkmate(PieceColor)   // this color lost
    case draw(DrawReason)

    private enum CodingKeys: String, CodingKey { case type, color, drawReason }
    private enum StatusType: String, Codable { case playing, check, checkmate, draw, stalemate }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .playing:
            try c.encode(StatusType.playing,   forKey: .type)
        case .check(let color):
            try c.encode(StatusType.check,     forKey: .type)
            try c.encode(color,                forKey: .color)
        case .checkmate(let color):
            try c.encode(StatusType.checkmate, forKey: .type)
            try c.encode(color,                forKey: .color)
        case .draw(let reason):
            try c.encode(StatusType.draw,      forKey: .type)
            try c.encode(reason,               forKey: .drawReason)
        }
    }

    init(from decoder: Decoder) throws {
        let c    = try decoder.container(keyedBy: CodingKeys.self)
        let type = try c.decode(StatusType.self, forKey: .type)
        switch type {
        case .playing:   self = .playing
        case .check:     self = .check(try c.decode(PieceColor.self, forKey: .color))
        case .checkmate: self = .checkmate(try c.decode(PieceColor.self, forKey: .color))
        case .draw:      self = .draw(try c.decode(DrawReason.self, forKey: .drawReason))
        case .stalemate: self = .draw(.stalemate)   // backward compat for old saves
        }
    }
}

// MARK: - Game State

struct GameState: Sendable, Codable {
    var board: [[ChessPiece?]]
    var currentTurn: PieceColor
    var whiteCanCastleKingside: Bool
    var whiteCanCastleQueenside: Bool
    var blackCanCastleKingside: Bool
    var blackCanCastleQueenside: Bool
    var enPassantTarget: Position?
    var moveHistory: [ChessMove]
    var capturedByWhite: [ChessPiece]   // pieces white has captured
    var capturedByBlack: [ChessPiece]   // pieces black has captured
    var status: GameStatus
    /// Halfmove clock: resets to 0 on pawn move or capture; draw at 100 (= 50 full moves).
    var halfmoveClock: Int = 0
    /// Maps positionKey → occurrence count for 3-fold repetition detection.
    var positionHistory: [String: Int] = [:]
    /// Serbian algebraic notation strings, parallel to moveHistory (e.g. "e4", "Sf3", "O-O", "Lxb5+").
    var moveNotations: [String] = []

    // MARK: - Position Key

    /// 134-char compact key: 2 chars/square (piece or "--") + turn + castling (4) + ep.
    /// Used to detect 3-fold repetition.
    var positionKey: String {
        var key = ""
        key.reserveCapacity(134)
        for row in 0..<8 {
            for col in 0..<8 {
                if let p = board[row][col] {
                    key.append(p.color == .white ? "W" : "B")
                    key.append(contentsOf: String(p.type.rawValue))
                } else {
                    key.append("--")
                }
            }
        }
        key.append(currentTurn == .white ? "W" : "B")
        key.append(whiteCanCastleKingside  ? "1" : "0")
        key.append(whiteCanCastleQueenside ? "1" : "0")
        key.append(blackCanCastleKingside  ? "1" : "0")
        key.append(blackCanCastleQueenside ? "1" : "0")
        if let ep = enPassantTarget { key.append(contentsOf: String(ep.col)) } else { key.append("-") }
        return key
    }

    // MARK: - Initial Setup

    static func initial() -> GameState {
        var board: [[ChessPiece?]] = Array(
            repeating: Array(repeating: nil, count: 8),
            count: 8
        )

        let backRankTypes: [PieceType] = [.rook, .knight, .bishop, .queen, .king, .bishop, .knight, .rook]

        for col in 0..<8 {
            board[0][col] = ChessPiece(type: backRankTypes[col], color: .black)
            board[1][col] = ChessPiece(type: .pawn, color: .black)
            board[6][col] = ChessPiece(type: .pawn, color: .white)
            board[7][col] = ChessPiece(type: backRankTypes[col], color: .white)
        }

        var state = GameState(
            board: board,
            currentTurn: .white,
            whiteCanCastleKingside: true,
            whiteCanCastleQueenside: true,
            blackCanCastleKingside: true,
            blackCanCastleQueenside: true,
            enPassantTarget: nil,
            moveHistory: [],
            capturedByWhite: [],
            capturedByBlack: [],
            status: .playing
        )
        // Count the starting position as the first occurrence
        state.positionHistory[state.positionKey] = 1
        return state
    }

    // MARK: - Debug Position

    /// Beli pešak na e7 (jedan potez do promocije), minimalne figure.
    /// Koristi se samo za testiranje promocije — ukloniti pre release-a.
    static func debugPromotion() -> GameState {
        var board: [[ChessPiece?]] = Array(repeating: Array(repeating: nil, count: 8), count: 8)
        board[0][7] = ChessPiece(type: .king,  color: .black)   // Kh8
        board[7][0] = ChessPiece(type: .king,  color: .white)   // Ka1
        board[1][4] = ChessPiece(type: .pawn,  color: .white)   // Pe7
        var state = GameState(
            board: board,
            currentTurn: .white,
            whiteCanCastleKingside: false,
            whiteCanCastleQueenside: false,
            blackCanCastleKingside: false,
            blackCanCastleQueenside: false,
            enPassantTarget: nil,
            moveHistory: [],
            capturedByWhite: [],
            capturedByBlack: [],
            status: .playing
        )
        state.positionHistory[state.positionKey] = 1
        return state
    }

    // MARK: - Apply Move

    /// Full apply: updates board + castling rights + status + draw detection + notation.
    /// Use this for actual game moves (player and AI root).
    func applying(_ move: ChessMove) -> GameState {
        // Compute base notation BEFORE applying (need current board state for piece info)
        let baseNotation = GameState.baseNotation(for: move, in: self)

        var s = applyingForSearch(move)

        // Update position history for repetition detection
        let key = s.positionKey
        s.positionHistory[key, default: 0] += 1

        // Draw checks (cheapest first, before expensive move generation)
        if s.halfmoveClock >= 100 {
            s.status = .draw(.fiftyMoves)
            s.moveNotations.append(baseNotation)
            return s
        }
        if (s.positionHistory[key] ?? 0) >= 3 {
            s.status = .draw(.repetition)
            s.moveNotations.append(baseNotation)
            return s
        }
        if GameState.isInsufficientMaterial(s) {
            s.status = .draw(.insufficientMaterial)
            s.moveNotations.append(baseNotation)
            return s
        }

        // Mate / stalemate (requires move generation — most expensive)
        let opponentMoves = MoveGenerator.legalMoves(for: s.currentTurn, in: s)
        if opponentMoves.isEmpty {
            if MoveGenerator.isInCheck(color: s.currentTurn, in: s) {
                s.status = .checkmate(s.currentTurn)
            } else {
                s.status = .draw(.stalemate)
            }
        } else if MoveGenerator.isInCheck(color: s.currentTurn, in: s) {
            s.status = .check(s.currentTurn)
        } else {
            s.status = .playing
        }

        // Append notation with check/checkmate suffix
        var notation = baseNotation
        switch s.status {
        case .checkmate: notation += "#"
        case .check:     notation += "+"
        default: break
        }
        s.moveNotations.append(notation)

        return s
    }

    /// Builds the notation string (without check/mate suffix) from the pre-move state.
    /// Uses Serbian piece letters: K/D/T/L/S (King/Queen/Rook/Bishop/Knight).
    private static func baseNotation(for move: ChessMove, in state: GameState) -> String {
        guard let piece = state.board[move.from.row][move.from.col] else {
            return move.to.algebraic
        }

        switch move.flag {
        case .castleKingside:  return "O-O"
        case .castleQueenside: return "O-O-O"
        default: break
        }

        let isCapture = state.board[move.to.row][move.to.col] != nil || move.flag == .enPassant
        let dest = move.to.algebraic

        if piece.type == .pawn {
            // Pawn move: "e4" or "exd5", with optional "=D/T/L/S" promotion suffix
            var s = isCapture
                ? "\(move.from.algebraic.first!)x\(dest)"
                : dest
            if case .promotion(let pt) = move.flag {
                s += "=\(pt.srbNotationLetter)"
            }
            return s
        }

        // Piece move: "Sf3" or "Sxf3"
        let letter = piece.type.srbNotationLetter
        return isCapture ? "\(letter)x\(dest)" : "\(letter)\(dest)"
    }

    /// Lightweight apply used ONLY inside MoveGenerator and AI search.
    /// Does NOT call legalMoves (avoids infinite recursion) and does NOT update positionHistory.
    func applyingForSearch(_ move: ChessMove) -> GameState {
        var s = self
        guard let piece = s.board[move.from.row][move.from.col] else { return s }

        // Halfmove clock: reset on pawn move or capture, else increment
        let isCapturePre = s.board[move.to.row][move.to.col] != nil || move.flag == .enPassant
        s.halfmoveClock = (piece.type == .pawn || isCapturePre) ? 0 : s.halfmoveClock + 1

        s.enPassantTarget = nil

        switch move.flag {
        case .normal:
            // En passant setup on 2-square pawn advance
            if piece.type == .pawn && abs(move.to.row - move.from.row) == 2 {
                let epRow = (move.from.row + move.to.row) / 2
                s.enPassantTarget = Position(row: epRow, col: move.from.col)
            }
            if let captured = s.board[move.to.row][move.to.col] {
                if piece.color == .white { s.capturedByWhite.append(captured) }
                else { s.capturedByBlack.append(captured) }
            }
            s.board[move.to.row][move.to.col] = piece
            s.board[move.from.row][move.from.col] = nil

        case .castleKingside:
            let row = move.from.row
            s.board[row][6] = ChessPiece(type: .king, color: piece.color)
            s.board[row][5] = ChessPiece(type: .rook, color: piece.color)
            s.board[row][4] = nil
            s.board[row][7] = nil

        case .castleQueenside:
            let row = move.from.row
            s.board[row][2] = ChessPiece(type: .king, color: piece.color)
            s.board[row][3] = ChessPiece(type: .rook, color: piece.color)
            s.board[row][4] = nil
            s.board[row][0] = nil

        case .enPassant:
            let capturedPawn = ChessPiece(type: .pawn, color: piece.color.opposite)
            if piece.color == .white { s.capturedByWhite.append(capturedPawn) }
            else { s.capturedByBlack.append(capturedPawn) }
            s.board[move.from.row][move.to.col] = nil   // remove captured pawn
            s.board[move.to.row][move.to.col] = piece
            s.board[move.from.row][move.from.col] = nil

        case .promotion(let promoteTo):
            if let captured = s.board[move.to.row][move.to.col] {
                if piece.color == .white { s.capturedByWhite.append(captured) }
                else { s.capturedByBlack.append(captured) }
            }
            s.board[move.to.row][move.to.col] = ChessPiece(type: promoteTo, color: piece.color)
            s.board[move.from.row][move.from.col] = nil
        }

        // Update castling rights
        if piece.type == .king {
            if piece.color == .white {
                s.whiteCanCastleKingside = false
                s.whiteCanCastleQueenside = false
            } else {
                s.blackCanCastleKingside = false
                s.blackCanCastleQueenside = false
            }
        }
        if piece.type == .rook {
            switch move.from {
            case Position(row: 7, col: 7): s.whiteCanCastleKingside = false
            case Position(row: 7, col: 0): s.whiteCanCastleQueenside = false
            case Position(row: 0, col: 7): s.blackCanCastleKingside = false
            case Position(row: 0, col: 0): s.blackCanCastleQueenside = false
            default: break
            }
        }

        s.moveHistory.append(move)
        s.currentTurn = currentTurn.opposite
        return s
    }

    // MARK: - Helpers

    func kingPosition(for color: PieceColor) -> Position? {
        for row in 0..<8 {
            for col in 0..<8 {
                if let p = board[row][col], p.type == .king, p.color == color {
                    return Position(row: row, col: col)
                }
            }
        }
        return nil
    }

    /// Returns true when neither side has sufficient material to force checkmate.
    private static func isInsufficientMaterial(_ s: GameState) -> Bool {
        var whitePieces: [PieceType] = []
        var blackPieces: [PieceType] = []
        var whiteBishopOnLight: Bool? = nil
        var blackBishopOnLight: Bool? = nil

        for row in 0..<8 {
            for col in 0..<8 {
                guard let p = s.board[row][col], p.type != .king else { continue }
                if p.color == .white {
                    whitePieces.append(p.type)
                    if p.type == .bishop { whiteBishopOnLight = (row + col) % 2 == 0 }
                } else {
                    blackPieces.append(p.type)
                    if p.type == .bishop { blackBishopOnLight = (row + col) % 2 == 0 }
                }
            }
        }

        let isMinor = { (t: PieceType) in t == .bishop || t == .knight }

        // K vs K
        if whitePieces.isEmpty && blackPieces.isEmpty { return true }

        // K+minor vs K (either side)
        if whitePieces.isEmpty && blackPieces.count == 1 && isMinor(blackPieces[0]) { return true }
        if blackPieces.isEmpty && whitePieces.count == 1 && isMinor(whitePieces[0]) { return true }

        // K+B vs K+B on same color squares
        if whitePieces == [.bishop] && blackPieces == [.bishop],
           let wl = whiteBishopOnLight, let bl = blackBishopOnLight,
           wl == bl { return true }

        return false
    }
}
