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
            // Task 5 (dizajnerski pregled): koraci Puta dele JEDNU funkciju strane
            // table (`stepBoardSide`, definisana u `StepPracticeView.kt`), da je
            // Task 11 menja na jednom mestu. `StepGameView` je zato poziva umesto
            // da sam nosi `DS.maxBoardSide`; sama funkcija mora da je nosi (ispod).
            assertTrue(
                "$path crta tablu, pa mora da koristi DS.maxBoardSide (samo StepGameView sme kroz deljeni stepBoardSide)",
                "DS.maxBoardSide" in src ||
                    (path.endsWith("/StepGameView.kt") && "stepBoardSide(" in src)
            )
        }
        val helper = read("src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt")
            .substringAfter("fun stepBoardSide(", "")
            .substringBefore("\n}")
        assertTrue("stepBoardSide mora da ogranici tablu sa DS.maxBoardSide", "DS.maxBoardSide" in helper)
    }

    /**
     * Svesno obrnuto u dizajnerskom pregledu (Task 8, spec § 9/§ 13). Ovde je stajao
     * `lessonBoardsAreNotCapped` — tvrdnja da lekcijskim tablama granica ne treba jer
     * su „vec ogranicene sirinom stranice". Na tabletu to nije bilo tacno (tabla do
     * 1336 pt, snimci 87/95/110/119), pa spec trazi granicu i za lekciju.
     *
     * Do Task-a 11 (jedna funkcija strane table za sve ekrane) granica je privremena
     * konstanta `LessonBoardMaxSide` u `LessonRenderer.kt` (= `DS.maxBoardSide`), a
     * svaka lekcijska tabla je mora nositi. Test tvrdi oba: da konstanta postoji i
     * izvodi se iz `DS.maxBoardSide`, i da je svaki fajl sa lekcijskom tablom koristi.
     */
    @Test
    fun lessonBoardsUseTheTemporaryCap() {
        val renderer = read("src/main/java/com/veljkoni/chessko/ui/LessonRenderer.kt")
        assertTrue(
            "LessonRenderer.kt mora da definise LessonBoardMaxSide iz DS.maxBoardSide",
            Regex("val\\s+LessonBoardMaxSide\\s*=\\s*DS\\.maxBoardSide").containsMatchIn(renderer)
        )
        for (path in listOf(
            "src/main/java/com/veljkoni/chessko/ui/LearnView.kt",
            "src/main/java/com/veljkoni/chessko/ui/LessonRenderer.kt"
        )) {
            val src = read(path)
            assertTrue(
                "$path crta lekcijsku tablu, pa mora da je ogranici sa LessonBoardMaxSide",
                "widthIn(max = LessonBoardMaxSide)" in src
            )
        }
    }
}
