package com.veljkoni.chessko.ui

/**
 * Redosled = redosled u mreži Podešavanja i u prečici prevlačenjem (spec § 12 + odluka vlasnika:
 * Neonski/Drvene/Metalne/Ravne/Tanke obrisani, Reljef i Rez dodati na kraj). `assetSuffix` je
 * sufiks SVG-a u `assets/pieces/` (`piece_white_king_<sufiks>.svg`); klasični ga nema.
 * Iste vrednosti kao iOS `PieceStyle` (`Chessko/Views/PieceImageView.swift`).
 */
enum class PieceStyle(
    val rawValue: String,
    val label: String,
    val assetSuffix: String?
) {
    CLASSIC("classic", "Klasični", null),
    GAMEROOM("gameroom", "Igraonica", "gameroom"),
    GLASS("glass", "Staklene", "glass"),
    RELJEF("reljef", "Reljef", "reljef"),
    REZ("rez", "Rez", "rez");

    companion object {
        /** Obrisan (`neon`, `wood`, `metal`, `flat`, `simpleThin`) ili nepoznat stil → `classic`. */
        fun sanitizedRaw(raw: String?): String =
            entries.firstOrNull { it.rawValue == raw }?.rawValue ?: CLASSIC.rawValue
    }
}
