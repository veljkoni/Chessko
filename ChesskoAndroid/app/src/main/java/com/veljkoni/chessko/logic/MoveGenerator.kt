package com.veljkoni.chessko.logic

import com.veljkoni.chessko.models.*

object MoveGenerator {

    fun legalMoves(color: PieceColor, state: GameState): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = state.board[row][col]
                if (piece != null && piece.color == color) {
                    val pos = Position(row, col)
                    for (move in pseudoLegalMoves(piece, pos, state)) {
                        val next = state.applyingForSearch(move)
                        if (!isInCheck(color, next)) {
                            moves.add(move)
                        }
                    }
                }
            }
        }
        return moves
    }

    fun pseudoLegalMoves(piece: ChessPiece, pos: Position, state: GameState): List<ChessMove> {
        return when (piece.type) {
            PieceType.PAWN -> pawnMoves(piece.color, pos, state)
            PieceType.KNIGHT -> knightMoves(piece.color, pos, state)
            PieceType.BISHOP -> slidingMoves(piece.color, pos, state, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
            PieceType.ROOK -> slidingMoves(piece.color, pos, state, listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
            PieceType.QUEEN -> slidingMoves(piece.color, pos, state, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1))
            PieceType.KING -> kingMoves(piece.color, pos, state)
        }
    }

    private fun pawnMoves(color: PieceColor, pos: Position, state: GameState): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        val dir = if (color == PieceColor.WHITE) -1 else 1
        val startRow = if (color == PieceColor.WHITE) 6 else 1
        val promRow = if (color == PieceColor.WHITE) 0 else 7

        // Forward one
        val fwd = pos.offset(dir, 0)
        if (fwd.isValid && state.board[fwd.row][fwd.col] == null) {
            addPawnMove(pos, fwd, promRow, moves)

            // Forward two from start
            if (pos.row == startRow) {
                val fwd2 = pos.offset(dir * 2, 0)
                if (state.board[fwd2.row][fwd2.col] == null) {
                    moves.add(ChessMove(pos, fwd2))
                }
            }
        }

        // Diagonal captures
        for (dc in listOf(-1, 1)) {
            val cap = pos.offset(dir, dc)
            if (cap.isValid) {
                val target = state.board[cap.row][cap.col]
                if (target != null && target.color != color) {
                    addPawnMove(pos, cap, promRow, moves)
                }
                val ep = state.enPassantTarget
                if (ep != null && ep == cap) {
                    moves.add(ChessMove(pos, cap, MoveFlag.EnPassant))
                }
            }
        }

        return moves
    }

    private fun addPawnMove(from: Position, to: Position, promRow: Int, moves: MutableList<ChessMove>) {
        if (to.row == promRow) {
            for (pt in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                moves.add(ChessMove(from, to, MoveFlag.Promotion(pt)))
            }
        } else {
            moves.add(ChessMove(from, to))
        }
    }

    private fun knightMoves(color: PieceColor, pos: Position, state: GameState): List<ChessMove> {
        val offsets = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        val moves = mutableListOf<ChessMove>()
        for ((dr, dc) in offsets) {
            val t = pos.offset(dr, dc)
            if (t.isValid && state.board[t.row][t.col]?.color != color) {
                moves.add(ChessMove(pos, t))
            }
        }
        return moves
    }

    private fun slidingMoves(color: PieceColor, pos: Position, state: GameState, dirs: List<Pair<Int, Int>>): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for ((dr, dc) in dirs) {
            var cur = pos.offset(dr, dc)
            while (cur.isValid) {
                val blocker = state.board[cur.row][cur.col]
                if (blocker != null) {
                    if (blocker.color != color) {
                        moves.add(ChessMove(pos, cur))
                    }
                    break
                }
                moves.add(ChessMove(pos, cur))
                cur = cur.offset(dr, dc)
            }
        }
        return moves
    }

    private fun kingMoves(color: PieceColor, pos: Position, state: GameState): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        val offsets = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
        for ((dr, dc) in offsets) {
            val t = pos.offset(dr, dc)
            if (t.isValid && state.board[t.row][t.col]?.color != color) {
                moves.add(ChessMove(pos, t))
            }
        }

        // Castling - king must not currently be in check
        if (isInCheck(color, state)) return moves

        val row = if (color == PieceColor.WHITE) 7 else 0
        val canKS = if (color == PieceColor.WHITE) state.whiteCanCastleKingside else state.blackCanCastleKingside
        val canQS = if (color == PieceColor.WHITE) state.whiteCanCastleQueenside else state.blackCanCastleQueenside

        // Kingside: f & g must be empty, f must not be attacked
        if (canKS && state.board[row][5] == null && state.board[row][6] == null) {
            if (!isAttacked(Position(row, 5), color.opposite, state)) {
                moves.add(ChessMove(pos, Position(row, 6), MoveFlag.CastleKingside))
            }
        }

        // Queenside: b, c, d must be empty, d must not be attacked
        if (canQS && state.board[row][3] == null && state.board[row][2] == null && state.board[row][1] == null) {
            if (!isAttacked(Position(row, 3), color.opposite, state)) {
                moves.add(ChessMove(pos, Position(row, 2), MoveFlag.CastleQueenside))
            }
        }

        return moves
    }

    fun isInCheck(color: PieceColor, state: GameState): Boolean {
        val kingPos = state.kingPosition(color) ?: return false
        return isAttacked(kingPos, color.opposite, state)
    }

    fun isAttacked(square: Position, attacker: PieceColor, state: GameState): Boolean {
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = state.board[row][col]
                if (piece != null && piece.color == attacker) {
                    if (attacks(piece, Position(row, col), square, state)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun attacks(piece: ChessPiece, pos: Position, target: Position, state: GameState): Boolean {
        return when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (piece.color == PieceColor.WHITE) -1 else 1
                target == pos.offset(dir, -1) || target == pos.offset(dir, 1)
            }
            PieceType.KNIGHT -> {
                val offsets = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                offsets.any { pos.offset(it.first, it.second) == target }
            }
            PieceType.BISHOP -> diagonallyReaches(pos, target, state)
            PieceType.ROOK -> straightlyReaches(pos, target, state)
            PieceType.QUEEN -> diagonallyReaches(pos, target, state) || straightlyReaches(pos, target, state)
            PieceType.KING -> {
                val dr = kotlin.math.abs(target.row - pos.row)
                val dc = kotlin.math.abs(target.col - pos.col)
                dr <= 1 && dc <= 1 && (dr + dc > 0)
            }
        }
    }

    private fun diagonallyReaches(from: Position, to: Position, state: GameState): Boolean {
        val dr = to.row - from.row
        val dc = to.col - from.col
        if (kotlin.math.abs(dr) != kotlin.math.abs(dc) || dr == 0) return false
        val stepR = if (dr > 0) 1 else -1
        val stepC = if (dc > 0) 1 else -1
        var cur = from.offset(stepR, stepC)
        while (cur != to) {
            if (state.board[cur.row][cur.col] != null) return false
            cur = cur.offset(stepR, stepC)
        }
        return true
    }

    private fun straightlyReaches(from: Position, to: Position, state: GameState): Boolean {
        val dr = to.row - from.row
        val dc = to.col - from.col
        if ((dr == 0) == (dc == 0)) return false
        val stepR = if (dr == 0) 0 else if (dr > 0) 1 else -1
        val stepC = if (dc == 0) 0 else if (dc > 0) 1 else -1
        var cur = from.offset(stepR, stepC)
        while (cur != to) {
            if (state.board[cur.row][cur.col] != null) return false
            cur = cur.offset(stepR, stepC)
        }
        return true
    }
}
