package com.veljkoni.chessko.models

data class Position(val row: Int, val col: Int) {
    val isValid: Boolean
        get() = row in 0..7 && col in 0..7

    fun offset(dr: Int, dc: Int): Position = Position(row + dr, col + dc)

    val algebraic: String
        get() {
            val fileChar = ('a' + col)
            return "$fileChar${8 - row}"
        }
}
