import Foundation

// MARK: - Move Generator

enum MoveGenerator {

    // MARK: Legal Moves (filters out moves leaving king in check)

    static func legalMoves(for color: PieceColor, in state: GameState) -> [ChessMove] {
        var moves: [ChessMove] = []
        for row in 0..<8 {
            for col in 0..<8 {
                guard let piece = state.board[row][col], piece.color == color else { continue }
                let pos = Position(row: row, col: col)
                for move in pseudoLegalMoves(for: piece, at: pos, in: state) {
                    // Use applyingForSearch to avoid infinite recursion
                    // (applying() would call legalMoves() again)
                    let next = state.applyingForSearch(move)
                    if !isInCheck(color: color, in: next) {
                        moves.append(move)
                    }
                }
            }
        }
        return moves
    }

    // MARK: Pseudo-Legal Moves (ignores check)

    static func pseudoLegalMoves(for piece: ChessPiece, at pos: Position, in state: GameState) -> [ChessMove] {
        switch piece.type {
        case .pawn:   return pawnMoves(color: piece.color, at: pos, in: state)
        case .knight: return knightMoves(color: piece.color, at: pos, in: state)
        case .bishop: return slidingMoves(color: piece.color, at: pos, in: state, dirs: [(-1,-1),(-1,1),(1,-1),(1,1)])
        case .rook:   return slidingMoves(color: piece.color, at: pos, in: state, dirs: [(-1,0),(1,0),(0,-1),(0,1)])
        case .queen:  return slidingMoves(color: piece.color, at: pos, in: state, dirs: [(-1,-1),(-1,1),(1,-1),(1,1),(-1,0),(1,0),(0,-1),(0,1)])
        case .king:   return kingMoves(color: piece.color, at: pos, in: state)
        }
    }

    // MARK: - Piece Move Generators

    private static func pawnMoves(color: PieceColor, at pos: Position, in state: GameState) -> [ChessMove] {
        var moves: [ChessMove] = []
        let dir      = color == .white ? -1 : 1
        let startRow = color == .white ? 6 : 1
        let promRow  = color == .white ? 0 : 7

        // Forward one
        let fwd = pos.offset(dr: dir, dc: 0)
        if fwd.isValid && state.board[fwd.row][fwd.col] == nil {
            addPawnMove(from: pos, to: fwd, promRow: promRow, into: &moves)

            // Forward two from start
            if pos.row == startRow {
                let fwd2 = pos.offset(dr: dir * 2, dc: 0)
                if state.board[fwd2.row][fwd2.col] == nil {
                    moves.append(ChessMove(from: pos, to: fwd2))
                }
            }
        }

        // Diagonal captures
        for dc in [-1, 1] {
            let cap = pos.offset(dr: dir, dc: dc)
            if cap.isValid {
                if let target = state.board[cap.row][cap.col], target.color != color {
                    addPawnMove(from: pos, to: cap, promRow: promRow, into: &moves)
                }
                if let ep = state.enPassantTarget, ep == cap {
                    moves.append(ChessMove(from: pos, to: cap, flag: .enPassant))
                }
            }
        }

        return moves
    }

    private static func addPawnMove(from: Position, to: Position, promRow: Int, into moves: inout [ChessMove]) {
        if to.row == promRow {
            for pt in [PieceType.queen, .rook, .bishop, .knight] {
                moves.append(ChessMove(from: from, to: to, flag: .promotion(pt)))
            }
        } else {
            moves.append(ChessMove(from: from, to: to))
        }
    }

    private static func knightMoves(color: PieceColor, at pos: Position, in state: GameState) -> [ChessMove] {
        let offsets = [(-2,-1),(-2,1),(-1,-2),(-1,2),(1,-2),(1,2),(2,-1),(2,1)]
        return offsets.compactMap { (dr, dc) in
            let t = pos.offset(dr: dr, dc: dc)
            guard t.isValid, state.board[t.row][t.col]?.color != color else { return nil }
            return ChessMove(from: pos, to: t)
        }
    }

    private static func slidingMoves(color: PieceColor, at pos: Position, in state: GameState, dirs: [(Int,Int)]) -> [ChessMove] {
        var moves: [ChessMove] = []
        for (dr, dc) in dirs {
            var cur = pos.offset(dr: dr, dc: dc)
            while cur.isValid {
                if let blocker = state.board[cur.row][cur.col] {
                    if blocker.color != color { moves.append(ChessMove(from: pos, to: cur)) }
                    break
                }
                moves.append(ChessMove(from: pos, to: cur))
                cur = cur.offset(dr: dr, dc: dc)
            }
        }
        return moves
    }

    private static func kingMoves(color: PieceColor, at pos: Position, in state: GameState) -> [ChessMove] {
        var moves: [ChessMove] = []
        let offsets = [(-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)]
        for (dr, dc) in offsets {
            let t = pos.offset(dr: dr, dc: dc)
            if t.isValid && state.board[t.row][t.col]?.color != color {
                moves.append(ChessMove(from: pos, to: t))
            }
        }

        // Castling – king must not currently be in check
        guard !isInCheck(color: color, in: state) else { return moves }

        let row = color == .white ? 7 : 0
        let canKS = color == .white ? state.whiteCanCastleKingside  : state.blackCanCastleKingside
        let canQS = color == .white ? state.whiteCanCastleQueenside : state.blackCanCastleQueenside

        // Kingside: f & g must be empty, f must not be attacked
        if canKS && state.board[row][5] == nil && state.board[row][6] == nil {
            if !isAttacked(square: Position(row: row, col: 5), by: color.opposite, in: state) {
                moves.append(ChessMove(from: pos, to: Position(row: row, col: 6), flag: .castleKingside))
            }
        }

        // Queenside: b, c, d must be empty, d must not be attacked
        if canQS && state.board[row][3] == nil && state.board[row][2] == nil && state.board[row][1] == nil {
            if !isAttacked(square: Position(row: row, col: 3), by: color.opposite, in: state) {
                moves.append(ChessMove(from: pos, to: Position(row: row, col: 2), flag: .castleQueenside))
            }
        }

        return moves
    }

    // MARK: - Check Detection

    static func isInCheck(color: PieceColor, in state: GameState) -> Bool {
        guard let kingPos = state.kingPosition(for: color) else { return false }
        return isAttacked(square: kingPos, by: color.opposite, in: state)
    }

    static func isAttacked(square: Position, by attacker: PieceColor, in state: GameState) -> Bool {
        for row in 0..<8 {
            for col in 0..<8 {
                guard let piece = state.board[row][col], piece.color == attacker else { continue }
                if attacks(piece: piece, from: Position(row: row, col: col), target: square, in: state) {
                    return true
                }
            }
        }
        return false
    }

    private static func attacks(piece: ChessPiece, from pos: Position, target: Position, in state: GameState) -> Bool {
        switch piece.type {
        case .pawn:
            let dir = piece.color == .white ? -1 : 1
            return target == pos.offset(dr: dir, dc: -1) || target == pos.offset(dr: dir, dc: 1)
        case .knight:
            let offsets = [(-2,-1),(-2,1),(-1,-2),(-1,2),(1,-2),(1,2),(2,-1),(2,1)]
            return offsets.contains { pos.offset(dr: $0.0, dc: $0.1) == target }
        case .bishop:
            return diagonallyReaches(from: pos, to: target, in: state)
        case .rook:
            return straightlyReaches(from: pos, to: target, in: state)
        case .queen:
            return diagonallyReaches(from: pos, to: target, in: state) || straightlyReaches(from: pos, to: target, in: state)
        case .king:
            let dr = abs(target.row - pos.row)
            let dc = abs(target.col - pos.col)
            return dr <= 1 && dc <= 1 && (dr + dc > 0)
        }
    }

    private static func diagonallyReaches(from: Position, to: Position, in state: GameState) -> Bool {
        let dr = to.row - from.row; let dc = to.col - from.col
        guard abs(dr) == abs(dc), dr != 0 else { return false }
        let stepR = dr > 0 ? 1 : -1; let stepC = dc > 0 ? 1 : -1
        var cur = from.offset(dr: stepR, dc: stepC)
        while cur != to {
            if state.board[cur.row][cur.col] != nil { return false }
            cur = cur.offset(dr: stepR, dc: stepC)
        }
        return true
    }

    private static func straightlyReaches(from: Position, to: Position, in state: GameState) -> Bool {
        let dr = to.row - from.row; let dc = to.col - from.col
        guard (dr == 0) != (dc == 0) else { return false }
        let stepR = dr == 0 ? 0 : (dr > 0 ? 1 : -1)
        let stepC = dc == 0 ? 0 : (dc > 0 ? 1 : -1)
        var cur = from.offset(dr: stepR, dc: stepC)
        while cur != to {
            if state.board[cur.row][cur.col] != nil { return false }
            cur = cur.offset(dr: stepR, dc: stepC)
        }
        return true
    }
}
