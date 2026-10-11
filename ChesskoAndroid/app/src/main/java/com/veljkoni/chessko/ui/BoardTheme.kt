package com.veljkoni.chessko.ui

import androidx.compose.ui.graphics.Color

/**
 * Redosled = redosled u mreži Podešavanja i u prečici prevlačenjem (spec § 11 + odluka vlasnika:
 * Polarna/Smaragd/Pesak/Sajber obrisane, Žalfija i Glina dodate na kraj, ravna boja). Iste
 * vrednosti kao iOS `BoardTheme` (`Chessko/Views/SettingsSheet.swift`).
 */
enum class BoardTheme(
    val rawValue: String,
    val label: String,
    val lightColor: Color,
    val darkColor: Color
) {
    CLASSIC("classic", "Klasična", Color(0xFFE9EBDE), Color(0xFF8592AF)),
    FOREST("forest", "Šumska", Color(0xFFECECD7), Color(0xFF739552)),
    WOOD("wood", "Drvo", Color(0xFFF0D9B5), Color(0xFFB58863)),
    CHARCOAL("charcoal", "Ugalj", Color(0xFFE8E8E8), Color(0xFF646464)),
    SAGE("sage", "Žalfija", Color(0xFFE7EAE5), Color(0xFF87927F)),
    CLAY("clay", "Glina", Color(0xFFEEEAE8), Color(0xFF9A8A87));

    companion object {
        /**
         * Sačuvana vrednost koja više ne postoji (obrisana tema, npr. `midnight_aurora`) ili
         * nedostaje → `classic`. Bez ovoga crtanje i dalje pada na `CLASSIC` (`?: CLASSIC`), ali
         * mreža u Podešavanjima poredi sirov string pa nijedna pločica nije izabrana.
         */
        fun sanitizedRaw(raw: String?): String =
            entries.firstOrNull { it.rawValue == raw }?.rawValue ?: CLASSIC.rawValue
    }
}
