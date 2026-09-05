package com.veljkoni.chessko.models

enum class PieceType {
    KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN;

    val materialValue: Int
        get() = when (this) {
            KING -> 20000
            QUEEN -> 900
            ROOK -> 500
            BISHOP -> 330
            KNIGHT -> 320
            PAWN -> 100
        }

    val srbNotationLetter: String
        get() = when (this) {
            KING -> "K"
            QUEEN -> "D"
            ROOK -> "T"
            BISHOP -> "L"
            KNIGHT -> "S"
            PAWN -> ""
        }

    val srbName: String
        get() = when (this) {
            KING -> "Kralj"
            QUEEN -> "Dama"
            ROOK -> "Top"
            BISHOP -> "Lovac"
            KNIGHT -> "Skakač"
            PAWN -> "Pešak"
        }
}

enum class PieceColor {
    WHITE, BLACK;

    val opposite: PieceColor
        get() = if (this == WHITE) BLACK else WHITE
}

data class ChessPiece(val type: PieceType, val color: PieceColor) {
    val symbol: String
        get() = when (color) {
            PieceColor.WHITE -> when (type) {
                PieceType.KING -> "♔"
                PieceType.QUEEN -> "♕"
                PieceType.ROOK -> "♖"
                PieceType.BISHOP -> "♗"
                PieceType.KNIGHT -> "♘"
                PieceType.PAWN -> "♙"
            }
            PieceColor.BLACK -> when (type) {
                PieceType.KING -> "♚"
                PieceType.QUEEN -> "♛"
                PieceType.ROOK -> "♜"
                PieceType.BISHOP -> "♝"
                PieceType.KNIGHT -> "♞"
                PieceType.PAWN -> "♟"
            }
        }
}
