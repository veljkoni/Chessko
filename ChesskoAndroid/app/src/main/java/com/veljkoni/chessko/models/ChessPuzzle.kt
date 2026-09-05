package com.veljkoni.chessko.models

data class ChessPuzzle(
    val puzzleId: String,
    val fen: String,
    val moves: String,     // space-separated UCI moves
    val rating: Int,
    val themes: String     // space-separated themes
) {
    val uciMoves: List<String>
        get() = moves.split(" ").filter { it.isNotEmpty() }

    val themeList: List<String>
        get() = themes.split(" ").filter { it.isNotEmpty() }

    val difficultyLabel: String
        get() = when {
            rating < 1200 -> "Lako"
            rating in 1200..1599 -> "Srednje"
            else -> "Teško"
        }

    val difficultyColor: String
        get() = when {
            rating < 1200 -> "green"
            rating in 1200..1599 -> "yellow"
            else -> "red"
        }
}
