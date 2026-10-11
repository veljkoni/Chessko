package com.veljkoni.chessko

import androidx.compose.ui.graphics.toArgb
import com.veljkoni.chessko.ui.BoardTheme
import com.veljkoni.chessko.ui.PieceStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Task 12 (spec § 11/§ 12 + odluka vlasnika): spisak tema i stilova, redosled (= mreža u
 * Podešavanjima i prečica prevlačenjem) i pad obrisane sačuvane vrednosti na `classic`.
 */
class AppearanceCatalogTest {

    @Test
    fun themesAreTheSixKeptInOrder() {
        assertEquals(
            listOf("classic", "forest", "wood", "charcoal", "sage", "clay"),
            BoardTheme.entries.map { it.rawValue }
        )
    }

    @Test
    fun stylesAreTheFiveKeptInOrder() {
        assertEquals(
            listOf("classic", "gameroom", "glass", "reljef", "rez"),
            PieceStyle.entries.map { it.rawValue }
        )
    }

    @Test
    fun removedOrUnknownSavedValueFallsBackToClassic() {
        for (raw in listOf("midnight_aurora", "sage_emerald", "warm_terracotta", "cyber_lavender", "", null)) {
            assertEquals("classic", BoardTheme.sanitizedRaw(raw))
        }
        // `wood` je i dalje TEMA (Drvo) — ne sme da padne.
        assertEquals("wood", BoardTheme.sanitizedRaw("wood"))
        for (raw in listOf("neon", "wood", "metal", "flat", "simpleThin", "", null)) {
            assertEquals("classic", PieceStyle.sanitizedRaw(raw))
        }
        for (s in PieceStyle.entries) assertEquals(s.rawValue, PieceStyle.sanitizedRaw(s.rawValue))
        for (t in BoardTheme.entries) assertEquals(t.rawValue, BoardTheme.sanitizedRaw(t.rawValue))
    }

    @Test
    fun newThemeColorsMatchSpec() {
        assertEquals(0xFFE7EAE5.toInt(), BoardTheme.SAGE.lightColor.toArgb())
        assertEquals(0xFF87927F.toInt(), BoardTheme.SAGE.darkColor.toArgb())
        assertEquals(0xFFEEEAE8.toInt(), BoardTheme.CLAY.lightColor.toArgb())
        assertEquals(0xFF9A8A87.toInt(), BoardTheme.CLAY.darkColor.toArgb())
    }

    /** Svaki stil ima svih 12 SVG-ova u `assets/pieces/` po šemi koju čita `getAssetName`. */
    @Test
    fun everyStyleHasAllTwelvePieceAssets() {
        val dir = listOf("src/main/assets/pieces", "app/src/main/assets/pieces").map(::File).first { it.isDirectory }
        val types = listOf("king", "queen", "rook", "bishop", "knight", "pawn")
        val expected = PieceStyle.entries.flatMap { s ->
            listOf("white", "black").flatMap { c -> types.map { t -> s.assetSuffix?.let { "piece_${c}_${t}_$it.svg" } ?: "piece_${c}_${t}.svg" } }
        }.toSet()
        val actual = dir.listFiles()!!.map { it.name }.filter { it.endsWith(".svg") }.toSet()
        assertEquals(expected, actual)
    }
}
