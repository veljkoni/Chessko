package com.veljkoni.chessko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Faza 9, Task 4: dokaz da lekcijske table NE nose precicu prevlacenjem, i da
 * je i dalje nose sve ostale.
 *
 * Zasto izvor a ne ponasanje: `allowsStyleSwipe` je parametar `@Composable`
 * funkcije koja se poziva iz kompozicije, ne iz koda do kog test moze da dodje
 * refleksijom; jedini nacin da se dokaze bez emulatora je da se procita pravi
 * fajl. Isti obrazac kao `MainActivitySoundWiringTest` i
 * `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`.
 *
 * Sta cuva: iOS je granicu povukao ranije — `BoardView.allowsStyleSwipe`
 * (`Chessko/Views/BoardView.swift:21`) je `false` na tacno pet mesta u
 * `Chessko/Views/LessonRenderer.swift` (:194 staticka tabla, :243 istrazivac
 * figura, :645/:755/:886 tri kartice vezbi). Android ima istih pet, u dva
 * fajla. Sesta lekcijska tabla dodata bez te zastavice ne bi nista srusila —
 * tiho bi dozvolila da lekcija promeni globalno podesavanje.
 *
 * Druga tvrdnja nije ukras: bez nje bi jedan `allowsStyleSwipe = false`
 * zalutao u `MainActivity`/`PuzzleView`/korake Puta ugasio precicu na svim
 * ekranima gde radi, a prvi test bi i dalje bio zelen.
 */
class LessonBoardsOptOutOfSwipeTest {

    /** Uklanja `//` i `/* */` komentare — grepovana lista se cita, ne broji. */
    private fun withoutComments(src: String): String =
        src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private fun read(path: String): String {
        val file = File(path)
        assertTrue("izvor nije nadjen na ${file.absolutePath}", file.isFile)
        return withoutComments(file.readText())
    }

    @Test
    fun everyLessonEmbeddedBoardOptsOutOfTheSwipeShortcut() {
        val lessonFiles = mapOf(
            // istrazivac figura + tri kartice vezbi
            "src/main/java/com/veljkoni/chessko/ui/LearnView.kt" to 4,
            // `LStaticBoard` — `board` blok lekcije
            "src/main/java/com/veljkoni/chessko/ui/LessonRenderer.kt" to 1
        )

        var ukupno = 0
        for ((path, ocekivano) in lessonFiles) {
            val src = read(path)
            val tabli = Regex("BoardView\\(").findAll(src).count()
            val iskljucenih = Regex("allowsStyleSwipe\\s*=\\s*false").findAll(src).count()
            assertEquals(
                "$path: broj `BoardView(` poziva se promenio",
                ocekivano, tabli
            )
            assertEquals(
                "$path: lekcijska tabla bez `allowsStyleSwipe = false` — prevlacenje po " +
                    "njoj menja globalnu temu/stil, sto iOS izricito ne dozvoljava",
                tabli, iskljucenih
            )
            ukupno += iskljucenih
        }
        assertEquals("iOS ima tacno pet takvih tabli; Android mora imati isto", 5, ukupno)
    }

    @Test
    fun boardsOutsideLessonsKeepTheSwipeShortcut() {
        val igracke = listOf(
            "src/main/java/com/veljkoni/chessko/MainActivity.kt",
            "src/main/java/com/veljkoni/chessko/ui/PuzzleView.kt",
            "src/main/java/com/veljkoni/chessko/ui/StepGameView.kt",
            "src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt"
        )
        for (path in igracke) {
            val src = read(path)
            assertTrue("$path: nema nijedan `BoardView(` poziv", src.contains("BoardView("))
            assertEquals(
                "$path: tabla van lekcije je iskljucila precicu — nestala bi i tamo gde radi",
                0, Regex("allowsStyleSwipe\\s*=\\s*false").findAll(src).count()
            )
        }
    }
}
