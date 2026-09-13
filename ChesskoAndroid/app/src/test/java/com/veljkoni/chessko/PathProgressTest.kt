package com.veljkoni.chessko

import com.veljkoni.chessko.logic.PathProgress
import com.veljkoni.chessko.logic.StepState
import org.junit.Assert.assertEquals
import org.junit.Test

class PathProgressTest {

    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun emptyProgressUnlocksOnlyTheFirstStep() {
        val s = PathProgress.stepStates(ids, emptySet())
        assertEquals(StepState.AVAILABLE, s["a"])
        assertEquals(StepState.LOCKED, s["b"])
        assertEquals(StepState.LOCKED, s["c"])
        assertEquals(StepState.LOCKED, s["d"])
    }

    @Test
    fun completingAStepUnlocksExactlyTheNextOne() {
        val s = PathProgress.stepStates(ids, setOf("a"))
        assertEquals(StepState.COMPLETED, s["a"])
        assertEquals(StepState.AVAILABLE, s["b"])
        assertEquals(StepState.LOCKED, s["c"])
    }

    /**
     * Dostupan je PRVI NEZAVRSEN u redosledu, ne "onaj posle poslednjeg
     * zavrsenog". Sa rupom (a i c zavrseni, b nije) dostupan mora biti b.
     */
    @Test
    fun aGapInProgressMakesTheGapAvailableNotTheStepAfterTheLastCompleted() {
        val s = PathProgress.stepStates(ids, setOf("a", "c"))
        assertEquals(StepState.COMPLETED, s["a"])
        assertEquals(StepState.AVAILABLE, s["b"])
        assertEquals(StepState.COMPLETED, s["c"])
        assertEquals(StepState.LOCKED, s["d"])
    }

    @Test
    fun goalIsOneStepOrThreePuzzles() {
        assertEquals(false, PathProgress.goalMet(steps = 0, puzzles = 0))
        assertEquals(false, PathProgress.goalMet(steps = 0, puzzles = 2))
        assertEquals(true, PathProgress.goalMet(steps = 0, puzzles = 3))
        assertEquals(true, PathProgress.goalMet(steps = 1, puzzles = 0))
    }

    @Test
    fun streakCountsConsecutiveDaysEndingToday() {
        val days = setOf("2026-09-11", "2026-09-12", "2026-09-13")
        assertEquals(3, PathProgress.currentStreak(days, today = "2026-09-13"))
    }

    /**
     * NAJVAZNIJI test ove faze. Ako cilj DANAS jos nije ispunjen, niz se broji
     * od juce — dan jos traje. Bez ovoga bi korisniku streak nestajao svako
     * jutro, pre nego sto uopste stigne da odigra nesto.
     */
    @Test
    fun streakSurvivesADayThatHasNotEndedYet() {
        val days = setOf("2026-09-11", "2026-09-12")
        assertEquals(2, PathProgress.currentStreak(days, today = "2026-09-13"))
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        val days = setOf("2026-09-10", "2026-09-11")
        assertEquals(0, PathProgress.currentStreak(days, today = "2026-09-13"))
    }

    @Test
    fun noHistoryMeansNoStreak() {
        assertEquals(0, PathProgress.currentStreak(emptySet(), today = "2026-09-13"))
    }

    /**
     * Dan ulazi kao string bas zato da test ne zavisi ni od vremenske zone ni
     * od trenutka pokretanja. Neispravan datum ne sme da srusi ekran Puta.
     */
    @Test
    fun malformedTodayYieldsZeroInsteadOfCrashing() {
        assertEquals(0, PathProgress.currentStreak(setOf("2026-09-13"), today = "juce"))
    }
}
