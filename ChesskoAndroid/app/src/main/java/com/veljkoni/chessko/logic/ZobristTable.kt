@file:OptIn(ExperimentalUnsignedTypes::class)
package com.veljkoni.chessko.logic

import com.veljkoni.chessko.models.*

enum class TTFlag {
    EXACT, LOWER_BOUND, UPPER_BOUND
}

data class TTEntry(
    var hash: ULong = 0uL,
    var move: UInt = 0u,   // encoded move; 0 = none stored
    var score: Int = 0,
    var depth: Int = 0,
    var flag: TTFlag = TTFlag.EXACT
)

class TranspositionTable {
    companion object {
        const val SIZE = 1 shl 18
        const val MASK = SIZE - 1
    }

    val entries = Array(SIZE) { TTEntry() }

    fun probe(hash: ULong, depth: Int, alpha: IntArray, beta: IntArray): ProbeResult? {
        val idx = (hash and MASK.toULong()).toInt()
        val e = entries[idx]
        if (e.hash != hash) return null
        val bestMove = decodeMove(e.move)
        if (e.depth < depth) return null
        val s = e.score
        when (e.flag) {
            TTFlag.EXACT -> return ProbeResult(s, bestMove)
            TTFlag.LOWER_BOUND -> alpha[0] = maxOf(alpha[0], s)
            TTFlag.UPPER_BOUND -> beta[0] = minOf(beta[0], s)
        }
        if (alpha[0] >= beta[0]) return ProbeResult(s, bestMove)
        return null
    }

    fun bestMove(hash: ULong): ChessMove? {
        val idx = (hash and MASK.toULong()).toInt()
        val e = entries[idx]
        if (e.hash != hash) return null
        return decodeMove(e.move)
    }

    fun store(hash: ULong, depth: Int, score: Int, flag: TTFlag, move: ChessMove?) {
        val idx = (hash and MASK.toULong()).toInt()
        entries[idx] = TTEntry(
            hash = hash,
            move = encodeMove(move),
            score = score,
            depth = depth,
            flag = flag
        )
    }

    private fun encodeMove(move: ChessMove?): UInt {
        if (move == null) return 0u
        val f = when (val flag = move.flag) {
            is MoveFlag.Normal -> 0u
            is MoveFlag.CastleKingside -> 1u
            is MoveFlag.CastleQueenside -> 2u
            is MoveFlag.EnPassant -> 3u
            is MoveFlag.Promotion -> when (flag.pieceType) {
                PieceType.KNIGHT -> 4u
                PieceType.BISHOP -> 5u
                PieceType.ROOK -> 6u
                else -> 7u
            }
        }
        return (move.from.row.toUInt() and 0x7u) or
                ((move.from.col.toUInt() and 0x7u) shl 3) or
                ((move.to.row.toUInt() and 0x7u) shl 6) or
                ((move.to.col.toUInt() and 0x7u) shl 9) or
                ((f and 0x7u) shl 12)
    }

    private fun decodeMove(encoded: UInt): ChessMove? {
        if (encoded == 0u) return null
        val fromRow = (encoded.toInt() shr 0) and 0x7
        val fromCol = (encoded.toInt() shr 3) and 0x7
        val toRow = (encoded.toInt() shr 6) and 0x7
        val toCol = (encoded.toInt() shr 9) and 0x7
        val flagBits = (encoded.toInt() shr 12) and 0x7
        val flag = when (flagBits) {
            1 -> MoveFlag.CastleKingside
            2 -> MoveFlag.CastleQueenside
            3 -> MoveFlag.EnPassant
            4 -> MoveFlag.Promotion(PieceType.KNIGHT)
            5 -> MoveFlag.Promotion(PieceType.BISHOP)
            6 -> MoveFlag.Promotion(PieceType.ROOK)
            7 -> MoveFlag.Promotion(PieceType.QUEEN)
            else -> MoveFlag.Normal
        }
        return ChessMove(Position(fromRow, fromCol), Position(toRow, toCol), flag)
    }
}

data class ProbeResult(val score: Int, val move: ChessMove?)

class SearchContext(val timeLimitSeconds: Double) {
    val tt = TranspositionTable()
    val startTime = System.currentTimeMillis()
    var nodeCount = 0
    var shouldStop = false

    fun tick() {
        nodeCount++
        if (nodeCount % 2048 == 0) {
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            shouldStop = elapsed >= timeLimitSeconds
        }
    }
}

class XorShift64(private var state: ULong) {
    fun next(): ULong {
        state = state xor (state shl 13)
        state = state xor (state shr 7)
        state = state xor (state shl 17)
        return state
    }
}

class ZobristTable private constructor() {
    val pieces: Array<Array<Array<ULongArray>>>
    val sideToMove: ULong
    val castling: ULongArray
    val enPassant: ULongArray

    init {
        val rng = XorShift64(0x9E3779B97F4A7C15uL)
        pieces = Array(2) {
            Array(6) {
                Array(8) {
                    ULongArray(8) { rng.next() }
                }
            }
        }
        sideToMove = rng.next()
        castling = ULongArray(4) { rng.next() }
        enPassant = ULongArray(8) { rng.next() }
    }

    fun hash(state: GameState): ULong {
        var h = 0uL
        for (row in 0..7) {
            for (col in 0..7) {
                val p = state.board[row][col]
                if (p != null) {
                    h = h xor pieces[p.color.ordinal][p.type.ordinal][row][col]
                }
            }
        }
        if (state.currentTurn == PieceColor.BLACK) {
            h = h xor sideToMove
        }
        if (state.whiteCanCastleKingside) h = h xor castling[0]
        if (state.whiteCanCastleQueenside) h = h xor castling[1]
        if (state.blackCanCastleKingside) h = h xor castling[2]
        if (state.blackCanCastleQueenside) h = h xor castling[3]
        
        val ep = state.enPassantTarget
        if (ep != null) {
            h = h xor enPassant[ep.col]
        }
        return h
    }

    fun hash(move: ChessMove, state: GameState, oldHash: ULong): ULong {
        var h = oldHash
        val mover = state.board[move.from.row][move.from.col] ?: return h

        // Toggle side to move
        h = h xor sideToMove

        // XOR out old en passant
        val oldEp = state.enPassantTarget
        if (oldEp != null) {
            h = h xor enPassant[oldEp.col]
        }

        // XOR out old castling rights
        if (state.whiteCanCastleKingside) h = h xor castling[0]
        if (state.whiteCanCastleQueenside) h = h xor castling[1]
        if (state.blackCanCastleKingside) h = h xor castling[2]
        if (state.blackCanCastleQueenside) h = h xor castling[3]

        // Apply piece movements
        val mc = mover.color.ordinal
        val mt = mover.type.ordinal

        when (move.flag) {
            is MoveFlag.Normal -> {
                h = h xor pieces[mc][mt][move.from.row][move.from.col]
                val cap = state.board[move.to.row][move.to.col]
                if (cap != null) {
                    h = h xor pieces[cap.color.ordinal][cap.type.ordinal][move.to.row][move.to.col]
                }
                h = h xor pieces[mc][mt][move.to.row][move.to.col]
                // New en passant square?
                if (mover.type == PieceType.PAWN && kotlin.math.abs(move.to.row - move.from.row) == 2) {
                    h = h xor enPassant[move.from.col]
                }
            }
            is MoveFlag.CastleKingside -> {
                val row = move.from.row
                val rr = PieceType.ROOK.ordinal
                val kr = PieceType.KING.ordinal
                h = h xor pieces[mc][kr][row][4]
                h = h xor pieces[mc][rr][row][7]
                h = h xor pieces[mc][kr][row][6]
                h = h xor pieces[mc][rr][row][5]
            }
            is MoveFlag.CastleQueenside -> {
                val row = move.from.row
                val rr = PieceType.ROOK.ordinal
                val kr = PieceType.KING.ordinal
                h = h xor pieces[mc][kr][row][4]
                h = h xor pieces[mc][rr][row][0]
                h = h xor pieces[mc][kr][row][2]
                h = h xor pieces[mc][rr][row][3]
            }
            is MoveFlag.EnPassant -> {
                h = h xor pieces[mc][mt][move.from.row][move.from.col]
                val capColor = mover.color.opposite.ordinal
                val capType = PieceType.PAWN.ordinal
                h = h xor pieces[capColor][capType][move.from.row][move.to.col]
                h = h xor pieces[mc][mt][move.to.row][move.to.col]
            }
            is MoveFlag.Promotion -> {
                h = h xor pieces[mc][mt][move.from.row][move.from.col]
                val cap = state.board[move.to.row][move.to.col]
                if (cap != null) {
                    h = h xor pieces[cap.color.ordinal][cap.type.ordinal][move.to.row][move.to.col]
                }
                val promType = move.flag.pieceType.ordinal
                h = h xor pieces[mc][promType][move.to.row][move.to.col]
            }
        }

        // Compute new castling rights (mirrors applyingForSearch)
        var wck = state.whiteCanCastleKingside
        var wcq = state.whiteCanCastleQueenside
        var bck = state.blackCanCastleKingside
        var bcq = state.blackCanCastleQueenside

        if (mover.type == PieceType.KING) {
            if (mover.color == PieceColor.WHITE) {
                wck = false
                wcq = false
            } else {
                bck = false
                bcq = false
            }
        }
        if (mover.type == PieceType.ROOK) {
            when (move.from) {
                Position(7, 7) -> wck = false
                Position(7, 0) -> wcq = false
                Position(0, 7) -> bck = false
                Position(0, 0) -> bcq = false
            }
        }
        // Top pojeden na startnom polju takodje oduzima pravo rokade
        // vlasniku tog topa (mora pratiti applyingForSearch tacno).
        when (move.to) {
            Position(7, 7) -> wck = false
            Position(7, 0) -> wcq = false
            Position(0, 7) -> bck = false
            Position(0, 0) -> bcq = false
            else -> {}
        }

        // XOR in new castling rights
        if (wck) h = h xor castling[0]
        if (wcq) h = h xor castling[1]
        if (bck) h = h xor castling[2]
        if (bcq) h = h xor castling[3]

        return h
    }

    companion object {
        val shared = ZobristTable()
    }
}
