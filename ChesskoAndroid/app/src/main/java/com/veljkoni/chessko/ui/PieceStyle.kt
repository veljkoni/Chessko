package com.veljkoni.chessko.ui

enum class PieceStyle(
    val rawValue: String,
    val label: String
) {
    CLASSIC("classic", "Klasični"),
    NEON("neon", "Neonski"),
    WOOD("wood", "Drvene"),
    METAL("metal", "Metalne"),
    FLAT("flat", "Ravne"),
    SIMPLE_THIN("simpleThin", "Tanke"),
    GAMEROOM("gameroom", "Igraonica"),
    GLASS("glass", "Staklene")
}
