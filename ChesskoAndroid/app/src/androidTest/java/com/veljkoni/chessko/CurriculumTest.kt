package com.veljkoni.chessko

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veljkoni.chessko.logic.LessonRepository
import com.veljkoni.chessko.logic.PuzzleRepository
import com.veljkoni.chessko.models.CurriculumParser
import com.veljkoni.chessko.models.StepKind
import com.veljkoni.chessko.models.loadCurriculum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurriculumTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedCurriculumParsesWithExpectedShape() {
        val c = loadCurriculum(context)
        assertEquals(1, c.version)
        assertEquals(6, c.chapters.size)
        assertEquals(17, c.allStepIds.size)
        assertEquals(
            listOf("basics", "notation", "opening", "tactics", "middlegame", "endgame"),
            c.chapters.map { it.id }
        )
    }

    /**
     * Ne proverava samo TIP bloka nego i SADRZAJ polja. Test koji tvrdi samo
     * "ovo je Practice" prolazi i kad parser zameni `themes` i `count`.
     */
    @Test
    fun stepFieldsCarryTheirOwnValues() {
        val c = loadCurriculum(context)
        val practice = c.step("tactics-practice")
        assertNotNull(practice)
        val kind = practice!!.kind
        assertTrue(kind is StepKind.Practice)
        kind as StepKind.Practice
        assertEquals(listOf("fork", "pin", "skewer"), kind.themes)
        assertEquals(5, kind.count)
        assertEquals(600..1200, kind.ratingRange)

        val lesson = c.step("basics-lesson")!!.kind
        assertTrue(lesson is StepKind.Lesson)
        assertEquals("board-and-pieces", (lesson as StepKind.Lesson).lessonId)

        val game = c.step("basics-game")!!.kind
        assertTrue(game is StepKind.Game)
        assertEquals("beginner", (game as StepKind.Game).difficulty)
        assertEquals(null, game.startFEN)
    }

    @Test
    fun unknownStepTypeThrows() {
        val json = """{"version":1,"chapters":[{"id":"c","title":{"sr":"C"},
            "steps":[{"id":"s","type":"teleport"}]}]}"""
        try {
            CurriculumParser.parse(json)
            fail("Nepoznat tip koraka mora da baci, ne da se tiho preskoci")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("teleport"))
        }
    }

    @Test
    fun unknownDifficultyThrows() {
        val json = """{"version":1,"chapters":[{"id":"c","title":{"sr":"C"},
            "steps":[{"id":"s","type":"game","difficulty":"begginer"}]}]}"""
        try {
            CurriculumParser.parse(json)
            fail("Tipfeler u tezini mora da baci — inace korak zauvek stoji kao neaktivan")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("begginer"))
        }
    }

    /**
     * Kotlin `IntRange` sa donjom granicom vecom od gornje NE puca kao Swift
     * `ClosedRange` — samo je prazan. Bez ove provere korak bi tiho ostao bez
     * ijednog zadatka i ne bi se mogao zavrsiti.
     */
    @Test
    fun invertedRatingRangeThrows() {
        val json = """{"version":1,"chapters":[{"id":"c","title":{"sr":"C"},
            "steps":[{"id":"s","type":"practice","themes":["fork"],"count":3,
            "ratingRange":[1200,600]}]}]}"""
        try {
            CurriculumParser.parse(json)
            fail("Obrnut ratingRange mora da baci")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("ratingRange"))
        }
    }

    /**
     * Kurikulum ne sme da laze: svaka lekcija koju pominje mora da postoji kao
     * fajl, i svaka tema mora da ima dovoljno zadataka u trazenom opsegu.
     * Bez ovoga tipfeler daje korak koji se NIKAD ne moze zavrsiti, i to bez
     * ijedne poruke na ekranu.
     */
    @Test
    fun curriculumDoesNotLieAboutLessonsOrPuzzles() {
        val c = loadCurriculum(context)
        val lessons = LessonRepository(context).discoveredLessonIds().toSet()
        val all = PuzzleRepository(context).allPuzzlesOrderedById()

        for (chapter in c.chapters) {
            for (step in chapter.steps) {
                when (val k = step.kind) {
                    is StepKind.Lesson ->
                        assertTrue("Korak ${step.id} trazi lekciju '${k.lessonId}' koje nema",
                            lessons.contains(k.lessonId))
                    is StepKind.Practice -> assertEnough(all, step.id, k.themes, k.count, k.ratingRange)
                    is StepKind.Test -> assertEnough(all, step.id, k.themes, k.count, k.ratingRange)
                    is StepKind.Game -> Unit
                }
            }
        }
    }

    private fun assertEnough(
        all: List<com.veljkoni.chessko.models.ChessPuzzle>,
        stepId: String, themes: List<String>, count: Int, range: IntRange
    ) {
        // `ChessPuzzle.themes` je STRING razdvojen razmacima, ne lista —
        // `p.themes.any { }` bi iteriralo po znakovima i ne bi se ni kompajliralo.
        val n = all.count { p -> p.rating in range && p.themes.split(" ").any { it in themes } }
        assertTrue("Korak $stepId trazi $count zadataka za teme $themes u $range, a ima ih $n",
            n >= count)
    }
}
