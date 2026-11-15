package com.veljkoni.chessko.models

import com.veljkoni.chessko.logic.MoveGenerator

sealed interface MoveFlag {
    object Normal : MoveFlag
    object CastleKingside : MoveFlag
    object CastleQueenside : MoveFlag
    object EnPassant : MoveFlag
    data class Promotion(val pieceType: PieceType) : MoveFlag
}

data class ChessMove(
    val from: Position,
    val to: Position,
    val flag: MoveFlag = MoveFlag.Normal
) {
    val notation: String
        get() = "${from.algebraic}${to.algebraic}"

    /** UCI zapis ("e2e4", "e7e8q" — slovo promocije malo). */
    val uci: String
        get() {
            val flag = flag
            if (flag is MoveFlag.Promotion) {
                val letter = when (flag.pieceType) {
                    PieceType.ROOK -> "r"
                    PieceType.BISHOP -> "b"
                    PieceType.KNIGHT -> "n"
                    else -> "q"
                }
                return notation + letter
            }
            return notation
        }

    companion object {
        fun fromUCI(uci: String, state: GameState): ChessMove? {
            val lower = uci.lowercase()
            if (lower.length < 4) return null
            
            val fc = lower[0]
            val fr = lower[1].digitToIntOrNull() ?: return null
            val tc = lower[2]
            val tr = lower[3].digitToIntOrNull() ?: return null

            val fromCol = fc.code - 97
            val fromRow = 8 - fr
            val toCol = tc.code - 97
            val toRow = 8 - tr

            if (fromCol !in 0..7 || fromRow !in 0..7 || toCol !in 0..7 || toRow !in 0..7) return null

            val legal = MoveGenerator.legalMoves(state.currentTurn, state)

            if (lower.length >= 5) {
                val promPiece = when (lower[4]) {
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> PieceType.QUEEN
                }
                return legal.firstOrNull {
                    it.from.row == fromRow && it.from.col == fromCol &&
                            it.to.row == toRow && it.to.col == toCol &&
                            it.flag is MoveFlag.Promotion && it.flag.pieceType == promPiece
                }
            }

            return legal.firstOrNull {
                it.from.row == fromRow && it.from.col == fromCol &&
                        it.to.row == toRow && it.to.col == toCol
            }
        }
    }
}
