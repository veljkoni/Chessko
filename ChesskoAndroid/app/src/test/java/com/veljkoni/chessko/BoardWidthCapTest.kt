package com.veljkoni.chessko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Igracka tabla mora imati gornju granicu sirine, inace na tabletu proguta ceo ekran.
 *
 * Od Task-a 11 (spec § 13) granicu nosi jedna funkcija — `logic/BoardSizing`
 * (telefon 560 = `DS.maxBoardSide`, tablet 800), ista kao iOS `Models/BoardSizing.swift`.
 * Android ima cetiri fajla sa igrackom tablom jer su portret i pejzaz tab-a Igra
 * oba u MainActivity.kt.
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

    /** Poziv zajednicke funkcije `boardSide(` — ne `gameBoardSide(`/`stepBoardSide(`. */
    private val sharedCall = Regex("(?<![A-Za-z_])boardSide\\(")

    /**
     * Task 11 (dizajnerski pregled, spec § 13): JEDNA funkcija strane table za sve
     * ekrane. Svesno izmenjeno — ranije je svaki ekran morao da nosi `DS.maxBoardSide`
     * (560); sada granicu (telefon 560, tablet 800) nosi samo `logic/BoardSizing`, a
     * ekran mora da je dobije iz nje. `DS.maxBoardSide` na ekranu je zabranjen: to bi
     * bio drugi izvor istine (tablet bi opet stao na 560). Zastita nije oslabljena —
     * premestena je na jedno mesto koje pokriva `BoardSizingTest`.
     */
    @Test
    fun everyPlayerBoardUsesTheSharedBoardSide() {
        for (path in playerScreens) {
            val src = read(path)
            assertTrue(
                "$path crta tablu, pa mora da zove zajednicku boardSide( (StepGameView sme kroz stepBoardSide)",
                sharedCall.containsMatchIn(src) ||
                    (path.endsWith("/StepGameView.kt") && "stepBoardSide(" in src)
            )
            assertFalse(
                "$path ne sme da nosi sopstvenu granicu DS.maxBoardSide — granica je u BoardSizing",
                "DS.maxBoardSide" in src
            )
        }
        val stepHelper = read("src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt")
            .substringAfter("fun stepBoardSide(", "")
        assertTrue("stepBoardSide mora da zove zajednicku boardSide(", sharedCall.containsMatchIn(stepHelper.substringBefore("\n\n")))

        val wrapper = read("src/main/java/com/veljkoni/chessko/ui/BoardLayout.kt")
        assertTrue("ui/BoardLayout.kt boardSide mora da delegira na BoardSizing.boardSide", "BoardSizing.boardSide(" in wrapper)

        val sizing = read("src/main/java/com/veljkoni/chessko/logic/BoardSizing.kt")
        assertTrue("BoardSizing mora da ogranici telefon", "coerceIn(0f, PHONE_MAX_SIDE)" in sizing)
        assertTrue("BoardSizing mora da ogranici tablet", "coerceIn(0f, TABLET_MAX_SIDE)" in sizing)
    }

    /**
     * Lekcija (spec § 9/§ 13). Do Task-a 11 je ovde stajao
     * `lessonBoardsUseTheTemporaryCap` (privremena konstanta = `DS.maxBoardSide`);
     * pre njega `lessonBoardsAreNotCapped`, oboren snimcima 87/95/110/119 (tabla do
     * 1336 pt na tabletu). Sada granicu racuna `LessonDetailView` zajednickom
     * funkcijom i spusta je kroz `LocalLessonBoardMaxSide`; svaka lekcijska tabla je
     * nosi.
     */
    @Test
    fun lessonBoardsUseTheSharedBoardSide() {
        val detail = read("src/main/java/com/veljkoni/chessko/ui/LessonDetailView.kt")
        assertTrue("LessonDetailView mora da racuna granicu iz boardSide(", sharedCall.containsMatchIn(detail))
        assertTrue(
            "LessonDetailView mora da spusti granicu kroz LocalLessonBoardMaxSide",
            "LocalLessonBoardMaxSide provides" in detail
        )
        for (path in listOf(
            "src/main/java/com/veljkoni/chessko/ui/LearnView.kt",
            "src/main/java/com/veljkoni/chessko/ui/LessonRenderer.kt"
        )) {
            val src = read(path)
            assertTrue(
                "$path crta lekcijsku tablu, pa mora da je ogranici sa LocalLessonBoardMaxSide",
                "widthIn(max = LocalLessonBoardMaxSide.current)" in src
            )
        }
    }
}
