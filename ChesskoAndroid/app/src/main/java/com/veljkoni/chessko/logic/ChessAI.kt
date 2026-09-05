package com.veljkoni.chessko.logic

import com.veljkoni.chessko.models.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class ChessAI(val difficulty: Difficulty = Difficulty.MEDIUM) {

    enum class Difficulty(
        val maxDepth: Int,
        val timeLimit: Double,
        val quiescenceDepth: Int
    ) {
        BEGINNER(1, 0.20, 0),
        EASY(2, 0.30, 1),
        MEDIUM(6, 0.80, 4),
        HARD(8, 1.50, 5)
    }

    fun bestMove(color: PieceColor, state: GameState): ChessMove? {
        val moves = MoveGenerator.legalMoves(color, state)
        if (moves.isEmpty()) return null

        if (moves.size == 1) return moves[0]

        // Introduce random blunder rate for beginner/easy (Medium & Hard have 0% blunder)
        val roll = Random.nextDouble(0.0, 1.0)
        when (difficulty) {
            Difficulty.BEGINNER -> {
                if (roll < 0.25) {
                    return moves.random()
                }
            }
            Difficulty.EASY -> {
                if (roll < 0.10) {
                    return moves.random()
                }
            }
            Difficulty.MEDIUM -> {}
            Difficulty.HARD -> {}
        }

        val ctx = SearchContext(difficulty.timeLimit)
        val zt = ZobristTable.shared
        val root = zt.hash(state)

        var bestMove: ChessMove? = null
        var bestScore = -Int.MAX_VALUE / 2

        for (depth in 1..difficulty.maxDepth) {
            if (ctx.shouldStop) break

            val rootResult = searchRoot(color, state, root, moves, depth, ctx)
            if (rootResult != null) {
                bestMove = rootResult.move
                bestScore = rootResult.score
            } else {
                // Iteration timed out mid-search; preserve bestMove from previous completed depth
                break
            }

            if (ctx.shouldStop) break

            // Found a forced mate
            if (abs(bestScore) > 90000) break
        }

        return bestMove ?: moves.first()
    }

    private fun searchRoot(
        color: PieceColor,
        state: GameState,
        rootHash: ULong,
        moves: List<ChessMove>,
        depth: Int,
        ctx: SearchContext
    ): SearchResult? {
        val zt = ZobristTable.shared
        var alpha = -Int.MAX_VALUE / 2
        val beta = Int.MAX_VALUE / 2

        val ttHint = ctx.tt.bestMove(rootHash)
        val ordered = orderMoves(moves, state, ttHint)

        var bestMove: ChessMove? = null
        var bestScore = -Int.MAX_VALUE / 2

        for (move in ordered) {
            if (ctx.shouldStop) break

            val newHash = zt.hash(move, state, rootHash)
            val next = state.applyingForSearch(move)
            val score = -negamax(next, newHash, depth - 1, -beta, -alpha, color.opposite, ctx)

            if (score > bestScore || bestMove == null) {
                bestScore = score
                bestMove = move
            }
            alpha = max(alpha, score)
        }

        if (!ctx.shouldStop && bestMove != null) {
            ctx.tt.store(rootHash, depth, bestScore, TTFlag.EXACT, bestMove)
            return SearchResult(bestMove, bestScore)
        }

        return null
    }

    private fun negamax(
        state: GameState,
        hash: ULong,
        depth: Int,
        alpha: Int,
        beta: Int,
        color: PieceColor,
        ctx: SearchContext
    ): Int {
        ctx.tick()
        if (ctx.shouldStop) return 0

        // Terminal position check
        when (val status = state.status) {
            is GameStatus.Checkmate -> {
                return if (status.color == color) -(100000 + depth) else (100000 + depth)
            }
            is GameStatus.Draw -> return 0
            else -> {}
        }

        if (depth == 0) {
            return quiescence(state, hash, alpha, beta, color, difficulty.quiescenceDepth, ctx)
        }

        var currentAlpha = alpha
        var currentBeta = beta
        val alphaArray = intArrayOf(currentAlpha)
        val betaArray = intArrayOf(currentBeta)
        val hit = ctx.tt.probe(hash, depth, alphaArray, betaArray)
        if (hit != null) return hit.score
        currentAlpha = alphaArray[0]
        currentBeta = betaArray[0]

        val ttMove = ctx.tt.bestMove(hash)

        val moves = MoveGenerator.legalMoves(color, state)
        if (moves.isEmpty()) {
            return if (MoveGenerator.isInCheck(color, state)) -(100000 + depth) else 0
        }

        val zt = ZobristTable.shared
        var best = -Int.MAX_VALUE / 2
        var bestMv: ChessMove? = null
        var flag = TTFlag.UPPER_BOUND

        for (move in orderMoves(moves, state, ttMove)) {
            if (ctx.shouldStop) return 0

            val newHash = zt.hash(move, state, hash)
            val next = state.applyingForSearch(move)
            val score = -negamax(next, newHash, depth - 1, -currentBeta, -currentAlpha, color.opposite, ctx)

            if (score > best) {
                best = score
                bestMv = move
            }
            if (score > currentAlpha) {
                currentAlpha = score
                flag = TTFlag.EXACT
            }
            if (score >= currentBeta) {
                ctx.tt.store(hash, depth, score, TTFlag.LOWER_BOUND, move)
                return score
            }
        }

        if (!ctx.shouldStop) {
            ctx.tt.store(hash, depth, best, flag, bestMv)
        }
        return best
    }

    private fun quiescence(
        state: GameState,
        hash: ULong,
        alpha: Int,
        beta: Int,
        color: PieceColor,
        depth: Int,
        ctx: SearchContext
    ): Int {
        ctx.tick()
        if (ctx.shouldStop) return 0

        val standPat = evaluate(state, color)
        if (standPat >= beta) return beta
        var currentAlpha = max(alpha, standPat)

        if (depth <= 0) return currentAlpha

        val zt = ZobristTable.shared
        val allMoves = MoveGenerator.legalMoves(color, state)
        val captures = allMoves.filter { isCapture(it, state) }

        for (move in orderMoves(captures, state, null)) {
            if (ctx.shouldStop) return 0
            val newHash = zt.hash(move, state, hash)
            val next = state.applyingForSearch(move)
            val score = -quiescence(next, newHash, -beta, -currentAlpha, color.opposite, depth - 1, ctx)
            if (score >= beta) return beta
            currentAlpha = max(currentAlpha, score)
        }
        return currentAlpha
    }

    private fun orderMoves(moves: List<ChessMove>, state: GameState, ttMove: ChessMove?): List<ChessMove> {
        return moves.sortedByDescending { mvvLvaScore(it, state, ttMove) }
    }

    private fun mvvLvaScore(move: ChessMove, state: GameState, ttMove: ChessMove?): Int {
        if (ttMove != null && move == ttMove) return Int.MAX_VALUE / 2

        val attacker = state.board[move.from.row][move.from.col] ?: return 0
        val victim = state.board[move.to.row][move.to.col]
        if (victim != null) {
            return 10 * victim.type.materialValue - attacker.type.materialValue
        }
        if (move.flag is MoveFlag.EnPassant) {
            return 10 * PieceType.PAWN.materialValue - attacker.type.materialValue
        }
        if (move.flag is MoveFlag.Promotion) {
            return move.flag.pieceType.materialValue
        }
        return 0
    }

    private fun isCapture(move: ChessMove, state: GameState): Boolean {
        return state.board[move.to.row][move.to.col] != null || move.flag is MoveFlag.EnPassant
    }

    private fun evaluate(state: GameState, color: PieceColor): Int {
        val endgame = isEndgame(state)
        var score = 0
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = state.board[row][col] ?: continue
                val pv = piece.type.materialValue + positionalBonus(piece, row, col, endgame)
                if (piece.color == color) {
                    score += pv
                } else {
                    score -= pv
                }
            }
        }
        return score
    }

    private fun isEndgame(state: GameState): Boolean {
        var queens = 0
        var minors = 0
        for (row in 0..7) {
            for (col in 0..7) {
                val p = state.board[row][col] ?: continue
                if (p.type == PieceType.QUEEN) queens++
                if (p.type == PieceType.BISHOP || p.type == PieceType.KNIGHT) minors++
            }
        }
        return queens == 0 || (queens <= 2 && minors <= 2)
    }

    private fun positionalBonus(piece: ChessPiece, row: Int, col: Int, endgame: Boolean): Int {
        val r = if (piece.color == PieceColor.WHITE) row else (7 - row)
        return when (piece.type) {
            PieceType.PAWN -> pawnPST[r][col]
            PieceType.KNIGHT -> knightPST[r][col]
            PieceType.BISHOP -> bishopPST[r][col]
            PieceType.ROOK -> rookPST[r][col]
            PieceType.QUEEN -> queenPST[r][col]
            PieceType.KING -> if (endgame) kingEndgamePST[r][col] else kingMiddlePST[r][col]
        }
    }

    companion object {
        private val pawnPST = arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
            intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
            intArrayOf(5, 5, 10, 25, 25, 10, 5, 5),
            intArrayOf(0, 0, 0, 20, 20, 0, 0, 0),
            intArrayOf(5, -5, -10, 0, 0, -10, -5, 5),
            intArrayOf(5, 10, 10, -20, -20, 10, 10, 5),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        )
        private val knightPST = arrayOf(
            intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50),
            intArrayOf(-40, -20, 0, 0, 0, 0, -20, -40),
            intArrayOf(-30, 0, 10, 15, 15, 10, 0, -30),
            intArrayOf(-30, 5, 15, 20, 20, 15, 5, -30),
            intArrayOf(-30, 0, 15, 20, 20, 15, 0, -30),
            intArrayOf(-30, 5, 10, 15, 15, 10, 5, -30),
            intArrayOf(-40, -20, 0, 5, 5, 0, -20, -40),
            intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50)
        )
        private val bishopPST = arrayOf(
            intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20),
            intArrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
            intArrayOf(-10, 0, 5, 10, 10, 5, 0, -10),
            intArrayOf(-10, 5, 5, 10, 10, 5, 5, -10),
            intArrayOf(-10, 0, 10, 10, 10, 10, 0, -10),
            intArrayOf(-10, 10, 10, 10, 10, 10, 10, -10),
            intArrayOf(-10, 5, 0, 0, 0, 0, 5, -10),
            intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20)
        )
        private val rookPST = arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(5, 10, 10, 10, 10, 10, 10, 5),
            intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
            intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
            intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
            intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
            intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
            intArrayOf(0, 0, 0, 5, 5, 0, 0, 0)
        )
        private val queenPST = arrayOf(
            intArrayOf(-20, -10, -10, -5, -5, -10, -10, -20),
            intArrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
            intArrayOf(-10, 0, 5, 5, 5, 5, 0, -10),
            intArrayOf(-5, 0, 5, 5, 5, 5, 0, -5),
            intArrayOf(0, 0, 5, 5, 5, 5, 0, -5),
            intArrayOf(-10, 5, 5, 5, 5, 5, 0, -10),
            intArrayOf(-10, 0, 5, 0, 0, 0, 0, -10),
            intArrayOf(-20, -10, -10, -5, -5, -10, -10, -20)
        )
        private val kingMiddlePST = arrayOf(
            intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
            intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
            intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
            intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
            intArrayOf(-20, -30, -30, -40, -40, -30, -30, -20),
            intArrayOf(-10, -20, -20, -20, -20, -20, -20, -10),
            intArrayOf(20, 20, 0, 0, 0, 0, 20, 20),
            intArrayOf(20, 30, 10, 0, 0, 10, 30, 20)
        )
        private val kingEndgamePST = arrayOf(
            intArrayOf(-50, -40, -30, -20, -20, -30, -40, -50),
            intArrayOf(-30, -20, -10, 0, 0, -10, -20, -30),
            intArrayOf(-30, -10, 20, 30, 30, 20, -10, -30),
            intArrayOf(-30, -10, 30, 40, 40, 30, -10, -30),
            intArrayOf(-30, -10, 30, 40, 40, 30, -10, -30),
            intArrayOf(-30, -10, 20, 30, 30, 20, -10, -30),
            intArrayOf(-30, -30, 0, 0, 0, 0, -30, -30),
            intArrayOf(-50, -30, -30, -30, -30, -30, -30, -50)
        )

        fun evaluatePosition(state: GameState): Pair<Double, Int?> {
            when (val s = state.status) {
                is GameStatus.Checkmate -> return if (s.color == PieceColor.WHITE) Pair(-100.0, -1) else Pair(100.0, 1)
                is GameStatus.Draw -> return Pair(0.0, null)
                is GameStatus.Resigned -> return if (s.color == PieceColor.WHITE) Pair(-100.0, -1) else Pair(100.0, 1)
                else -> {}
            }
            val ai = ChessAI(Difficulty.EASY)
            val zt = ZobristTable.shared
            val hash = zt.hash(state)
            val ctx = SearchContext(0.05)
            val cpScore = ai.negamax(state, hash, depth = 2, -20000, 20000, state.currentTurn, ctx)
            val whitePerspectiveCp = if (state.currentTurn == PieceColor.WHITE) cpScore else -cpScore
            val pawns = (whitePerspectiveCp / 100.0).coerceIn(-20.0, 20.0)
            return Pair(pawns, null)
        }
    }
}

data class SearchResult(val move: ChessMove, val score: Int)
