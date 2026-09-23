package com.veljkoni.chessko

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Igracka tabla mora imati gornju granicu sirine, inace na tabletu proguta ceo ekran.
 *
 * iOS istu granicu (`DS.maxBoardSide`, 560) primenjuje na pet mesta:
 * GameView.swift:35 i :105, PuzzleView.swift:296, StepGameView.swift:174,
 * StepPracticeView.swift:84. Android ih ima cetiri fajla jer su portret i pejzaz
 * tab-a Igra oba u MainActivity.kt.
 *
 * Do kompozicije se iz JVM testa ne moze doci (nema `ViewModelStore`, nema uredjaja),
 * pa se cita izvor — isti obrazac kao MainActivitySoundWiringTest.
 */
class BoardWidthCapTest {

    /**
     * Komentari se skidaju PRE poredjenja — pogodak u komentaru je u Fazi 9 dvaput
     * pomerio broj. Isti helper nosi LessonBoardsOptOutOfSwipeTest.
     */
    private fun withoutComments(src: String): String =
        src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private fun read(path: String): String {
        val file = File(path)
        assertTrue("izvor nije nadjen na ${file.absolutePath}", file.isFile)
        return withoutComments(file.readText())
    }

    private val playerScreens = listOf(
        "src/main/java/com/veljkoni/chessko/MainActivity.kt",
        "src/main/java/com/veljkoni/chessko/ui/PuzzleView.kt",
        "src/main/java/com/veljkoni/chessko/ui/StepGameView.kt",
        "src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt"
    )

    @Test
    fun everyPlayerBoardIsWidthCapped() {
        for (path in playerScreens) {
            val src = read(path)
            assertTrue(
                "$path crta tablu, pa mora da koristi DS.maxBoardSide",
                "DS.maxBoardSide" in src
            )
        }
    }

    @Test
    fun lessonBoardsAreNotCapped() {
        // Table u lekciji zive u skrolujucem stupcu i vec su ogranicene sirinom stranice;
        // granica od 560 bi im samo dodala mrtav prostor. Ako neko ovo promeni, neka to
        // bude odluka, ne previd.
        for (path in listOf(
            "src/main/java/com/veljkoni/chessko/ui/LearnView.kt",
            "src/main/java/com/veljkoni/chessko/ui/LessonRenderer.kt"
        )) {
            val src = read(path)
            assertTrue(
                "$path je lekcijski — ne sme da nosi DS.maxBoardSide",
                "DS.maxBoardSide" !in src
            )
        }
    }
}
