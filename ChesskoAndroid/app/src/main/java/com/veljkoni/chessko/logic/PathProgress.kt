package com.veljkoni.chessko.logic

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Stanje koraka na Putu. */
enum class StepState { LOCKED, AVAILABLE, COMPLETED }

// MARK: - Cista logika Puta
//
// Sve sto se moze pogresiti oko otkljucavanja, dnevnog cilja i streak-a stoji
// OVDE, kao ciste funkcije bez fajlova i bez sata. Dan ulazi kao string
// ("yyyy-MM-dd") da test ne zavisi od vremenske zone ni od trenutka pokretanja.
//
// Nijedan `android.*` ni `org.json` uvoz — zato se ovo testira na JVM-u, gde
// je povratna sprega u sekundama, a ne na emulatoru.

object PathProgress {

    /**
     * Korak je dostupan ako je PRVI nezavrsen u redosledu; svi posle su
     * zakljucani. Prazan skup zavrsenih znaci da je dostupan samo prvi.
     */
    fun stepStates(stepIds: List<String>, completed: Set<String>): Map<String, StepState> {
        val result = LinkedHashMap<String, StepState>(stepIds.size)
        var foundAvailable = false
        for (id in stepIds) {
            result[id] = when {
                completed.contains(id) -> StepState.COMPLETED
                !foundAvailable -> { foundAvailable = true; StepState.AVAILABLE }
                else -> StepState.LOCKED
            }
        }
        return result
    }

    /** Dnevni cilj: jedan zavrsen korak Puta ILI tri resena zadatka (spec 5.4). */
    fun goalMet(steps: Int, puzzles: Int): Boolean = steps >= 1 || puzzles >= 3

    /**
     * Dani zaredom sa ispunjenim ciljem, zakljucno sa danas.
     *
     * Ako cilj DANAS jos nije ispunjen, brojanje krece od juce — dan jos
     * traje, pa niz ne sme da se prekine. Prekida ga tek propusten dan.
     */
    fun currentStreak(goalDays: Set<String>, today: String): Int {
        var day = try {
            LocalDate.parse(today)
        } catch (e: DateTimeParseException) {
            return 0
        }
        if (!goalDays.contains(today)) day = day.minusDays(1)

        var count = 0
        while (goalDays.contains(day.toString())) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Kljuc dana u ISO obliku `yyyy-MM-dd`; `LocalDate.toString()` ga vec daje. */
    fun dayKey(date: LocalDate = LocalDate.now()): String = date.toString()
}
