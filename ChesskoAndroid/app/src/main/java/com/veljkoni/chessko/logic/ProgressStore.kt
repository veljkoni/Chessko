package com.veljkoni.chessko.logic

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.veljkoni.chessko.models.Curriculum
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// MARK: - Snimak napretka na disku
//
// JSON fajl u `filesDir`, ne `SharedPreferences`: napredak je struktura, ne
// sacica skalara (spec 5.4). Stari `SharedPreferences` kljucevi se NAMERNO ne
// brisu posle migracije -- tako povratak na stariju verziju i dalje radi.

data class ProgressSnapshot(
    val version: Int = 1,

    // Put
    val completedSteps: Set<String> = emptySet(),
    val stepCompletionDates: Map<String, String> = emptyMap(),   // stepId -> "yyyy-MM-dd"

    // Dnevni cilj i streak
    val stepsCompletedByDay: Map<String, Int> = emptyMap(),
    val puzzlesSolvedByDay: Map<String, Int> = emptyMap(),

    // Preneto iz `SharedPreferences` pri prvom pokretanju
    val gamesPlayed: Int = 0, val gamesWon: Int = 0,
    val gamesLost: Int = 0, val gamesDrawn: Int = 0,
    val currentWinStreak: Int = 0, val bestWinStreak: Int = 0,
    // Pobede po tezini postoje SAMO na Androidu (iOS ProgressSnapshot ih nema).
    // Bez njih bi fasada `StatsManager` izgubila podatke koje danas prikazuje.
    val winsBeginner: Int = 0, val winsEasy: Int = 0, val winsMedium: Int = 0,
    val winsHard: Int = 0, val winsStockfish: Int = 0,
    val puzzlesSolved: Int = 0,
    val currentPuzzleStreak: Int = 0, val bestPuzzleStreak: Int = 0,
    val puzzleRating: Int = PuzzleRating.START
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("version", version)
        o.put("completedSteps", JSONArray(completedSteps.toList()))
        o.put("stepCompletionDates", JSONObject(stepCompletionDates as Map<*, *>))
        o.put("stepsCompletedByDay", JSONObject(stepsCompletedByDay as Map<*, *>))
        o.put("puzzlesSolvedByDay", JSONObject(puzzlesSolvedByDay as Map<*, *>))
        o.put("gamesPlayed", gamesPlayed); o.put("gamesWon", gamesWon)
        o.put("gamesLost", gamesLost); o.put("gamesDrawn", gamesDrawn)
        o.put("currentWinStreak", currentWinStreak); o.put("bestWinStreak", bestWinStreak)
        o.put("winsBeginner", winsBeginner); o.put("winsEasy", winsEasy)
        o.put("winsMedium", winsMedium); o.put("winsHard", winsHard)
        o.put("winsStockfish", winsStockfish)
        o.put("puzzlesSolved", puzzlesSolved)
        o.put("currentPuzzleStreak", currentPuzzleStreak)
        o.put("bestPuzzleStreak", bestPuzzleStreak)
        o.put("puzzleRating", puzzleRating)
        return o.toString()
    }

    companion object {
        /**
         * SVE se cita defanzivno -- i polja najviseg nivoa, i UNOSI unutar
         * mapa/niza jedan nivo dublje.
         *
         * Polja najviseg nivoa idu kroz `opt*` (`optInt`, `optJSONObject`, ...),
         * nikad `get*`: `getInt` baca za kljuc kog nema u fajlu. Da je ovde,
         * prvo sledece polje dodato u ovu strukturu razbilo bi `progress.json`
         * svakog postojeceg korisnika: citanje pukne -> fajl se proglasi
         * pokvarenim -> krene migracija -> ceo napredak nestane bez poruke.
         *
         * Isto pravilo vazi i JEDAN NIVO DUBLJE: `toStringSet`/`toStringMap`/
         * `toIntMap` citaju svaki UNOS preko `opt(key)` i PRESKACU unos ciji
         * tip ne odgovara, umesto da pozovu `getString`/`getInt` koji bi bacili
         * na prvi neispravan unos i oborili citanje cele mape/niza. Ovo nije
         * kozmetika: `completedSteps`, `stepCompletionDates` i
         * `stepsCompletedByDay` (napredak na Putu) ne postoje u
         * `SharedPreferences` i NE MOGU se migrirati nazad -- jedan pokvaren
         * unos za jedan dan bi bez ove zastite trajno obrisao ceo Put, iako je
         * ostatak fajla savrseno citljiv.
         */
        fun fromJson(s: String): ProgressSnapshot {
            val o = JSONObject(s)
            return ProgressSnapshot(
                version = o.optInt("version", 1),
                completedSteps = o.optJSONArray("completedSteps").toStringSet(),
                stepCompletionDates = o.optJSONObject("stepCompletionDates").toStringMap(),
                stepsCompletedByDay = o.optJSONObject("stepsCompletedByDay").toIntMap(),
                puzzlesSolvedByDay = o.optJSONObject("puzzlesSolvedByDay").toIntMap(),
                gamesPlayed = o.optInt("gamesPlayed", 0),
                gamesWon = o.optInt("gamesWon", 0),
                gamesLost = o.optInt("gamesLost", 0),
                gamesDrawn = o.optInt("gamesDrawn", 0),
                currentWinStreak = o.optInt("currentWinStreak", 0),
                bestWinStreak = o.optInt("bestWinStreak", 0),
                winsBeginner = o.optInt("winsBeginner", 0),
                winsEasy = o.optInt("winsEasy", 0),
                winsMedium = o.optInt("winsMedium", 0),
                winsHard = o.optInt("winsHard", 0),
                winsStockfish = o.optInt("winsStockfish", 0),
                puzzlesSolved = o.optInt("puzzlesSolved", 0),
                currentPuzzleStreak = o.optInt("currentPuzzleStreak", 0),
                bestPuzzleStreak = o.optInt("bestPuzzleStreak", 0),
                puzzleRating = o.optInt("puzzleRating", PuzzleRating.START)
            )
        }

        /** Preskace unos ciji tip nije `String` umesto da baci -- vidi doc iznad `fromJson`. */
        private fun JSONArray?.toStringSet(): Set<String> =
            if (this == null) emptySet()
            else (0 until length()).mapNotNull { opt(it) as? String }.toSet()

        /** Preskace unos ciji tip nije `String` umesto da baci -- vidi doc iznad `fromJson`. */
        private fun JSONObject?.toStringMap(): Map<String, String> =
            if (this == null) emptyMap()
            else keys().asSequence()
                .mapNotNull { key -> (opt(key) as? String)?.let { key to it } }
                .toMap()

        /** Preskace unos ciji tip nije broj umesto da baci -- vidi doc iznad `fromJson`. */
        private fun JSONObject?.toIntMap(): Map<String, Int> =
            if (this == null) emptyMap()
            else keys().asSequence()
                .mapNotNull { key -> (opt(key) as? Number)?.let { key to it.toInt() } }
                .toMap()
    }
}

/**
 * `mutableStateOf` da EKRAN Puta sam primeti izmenu: kad se korak zavrsi na
 * drugom ekranu, lista koraka mora sama da se prekrsti kad se korisnik vrati.
 */
class ProgressStore(
    context: Context,
    private val file: File = defaultFile(context),
    private val prefs: SharedPreferences =
        context.getSharedPreferences("chessko_stats", Context.MODE_PRIVATE)
) {
    var snapshot by mutableStateOf(ProgressSnapshot())
        private set

    init {
        val existing = if (file.exists()) file.readText() else null
        var loaded: ProgressSnapshot? = null
        if (existing != null) {
            try {
                loaded = ProgressSnapshot.fromJson(existing)
            } catch (e: Exception) {
                // Fajl POSTOJI ali se ne cita. Migracija bi ga odmah pregazila i
                // trajno unistila napredak koji je mozda samo delimicno ostecen.
                val backup = File(file.path + ".corrupt")
                backup.delete()
                file.renameTo(backup)
                Log.e("Chessko", "progress.json se ne cita (${e.message}); odlozen u ${backup.name}")
            }
        }
        if (loaded != null) {
            snapshot = loaded
        } else {
            snapshot = migratedFromPrefs()
            save()
        }
    }

    /** Prvo pokretanje posle nadogradnje: statistika se preuzima i OSTAVLJA. */
    private fun migratedFromPrefs() = ProgressSnapshot(
        gamesPlayed = prefs.getInt("gamesPlayed", 0),
        gamesWon = prefs.getInt("gamesWon", 0),
        gamesLost = prefs.getInt("gamesLost", 0),
        gamesDrawn = prefs.getInt("gamesDrawn", 0),
        currentWinStreak = prefs.getInt("currentWinStreak", 0),
        bestWinStreak = prefs.getInt("bestWinStreak", 0),
        winsBeginner = prefs.getInt("winsBeginner", 0),
        winsEasy = prefs.getInt("winsEasy", 0),
        winsMedium = prefs.getInt("winsMedium", 0),
        winsHard = prefs.getInt("winsHard", 0),
        winsStockfish = prefs.getInt("winsStockfish", 0),
        puzzlesSolved = prefs.getInt("puzzlesSolved", 0),
        currentPuzzleStreak = prefs.getInt("currentPuzzleStreak", 0),
        bestPuzzleStreak = prefs.getInt("bestPuzzleStreak", 0),
        puzzleRating = prefs.getInt("puzzleRating", PuzzleRating.START)
    )

    /**
     * Upis nikad ne baca dalje — napredak ne sme da obori aplikaciju — ali ne
     * sme ni da cuti. Gubitak je uzak jer je snimak u memoriji CEO: sledeca
     * uspesna izmena upisuje i ono sto je ranije palo. Zato se upisuje po
     * SVAKOJ izmeni; grupno snimanje bi taj oporavak ukinulo.
     */
    fun save() {
        try {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(snapshot.toJson())
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        } catch (e: Exception) {
            Log.e("Chessko", "napredak nije sacuvan: ${e.message}")
        }
    }

    private fun mutate(change: (ProgressSnapshot) -> ProgressSnapshot) {
        snapshot = change(snapshot)
        save()
    }

    // MARK: Put

    fun stepStates(curriculum: Curriculum): Map<String, StepState> =
        PathProgress.stepStates(curriculum.allStepIds, snapshot.completedSteps)

    fun completeStep(id: String) {
        if (snapshot.completedSteps.contains(id)) return
        val day = PathProgress.dayKey()
        mutate {
            it.copy(
                completedSteps = it.completedSteps + id,
                stepCompletionDates = it.stepCompletionDates + (id to day),
                stepsCompletedByDay = it.stepsCompletedByDay +
                    (day to (it.stepsCompletedByDay[day] ?: 0) + 1)
            )
        }
    }

    fun recordPuzzleSolvedToday() {
        val day = PathProgress.dayKey()
        mutate {
            it.copy(puzzlesSolvedByDay = it.puzzlesSolvedByDay +
                (day to (it.puzzlesSolvedByDay[day] ?: 0) + 1))
        }
    }

    // MARK: Cilj i streak

    val goalMetToday: Boolean
        get() {
            val day = PathProgress.dayKey()
            return PathProgress.goalMet(
                snapshot.stepsCompletedByDay[day] ?: 0,
                snapshot.puzzlesSolvedByDay[day] ?: 0
            )
        }

    val currentStreak: Int
        get() {
            val days = (snapshot.stepsCompletedByDay.keys + snapshot.puzzlesSolvedByDay.keys)
                .filter {
                    PathProgress.goalMet(
                        snapshot.stepsCompletedByDay[it] ?: 0,
                        snapshot.puzzlesSolvedByDay[it] ?: 0
                    )
                }.toSet()
            return PathProgress.currentStreak(days, PathProgress.dayKey())
        }

    // MARK: Pristup statistici (koristi fasada `StatsManager`)

    fun updateStats(change: (ProgressSnapshot) -> ProgressSnapshot) = mutate(change)

    companion object {
        fun defaultFile(context: Context) = File(context.filesDir, "progress.json")

        @Volatile private var instance: ProgressStore? = null

        fun getInstance(context: Context): ProgressStore =
            instance ?: synchronized(this) {
                instance ?: ProgressStore(context.applicationContext).also { instance = it }
            }
    }
}
