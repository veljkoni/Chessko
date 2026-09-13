package com.veljkoni.chessko.models

import android.content.Context
import org.json.JSONObject

// MARK: - Kurikulum (Put)
//
// Okosnica v2: niz koraka koje igrac prolazi redom. Zivi u
// `assets/curriculum.json`, van koda, i BAJT JE IDENTICAN iOS kopiji u
// `Chessko/Content/curriculum.json` — kurikulum se menja na jednom mestu.
//
// Kao i kod `LessonBlock`-a, nepoznat `type` koraka BACA umesto da se tiho
// preskoci: preskocen korak bi napravio rupu u putu koju niko ne vidi.

sealed class StepKind {
    /** Teorija; zavrsava se kad korisnik dodje do kraja i potvrdi. */
    data class Lesson(val lessonId: String) : StepKind()

    /** N zadataka filtriranih po temi; zavrsava se kad su svi reseni. */
    data class Practice(
        val themes: List<String>, val count: Int, val ratingRange: IntRange
    ) : StepKind()

    /** Partija protiv racunara; zavrsava se kad partija dodje do kraja. */
    data class Game(val difficulty: String, val startFEN: String?) : StepKind()

    /** Kao `Practice`, ali se zavrsava SAMO bez ijedne greske — zakljucava poglavlje. */
    data class Test(
        val themes: List<String>, val count: Int, val ratingRange: IntRange
    ) : StepKind()
}

data class CurriculumStep(val id: String, val kind: StepKind)

data class Chapter(
    val id: String,
    /**
     * Naslov po jeziku. Kurikulum je mali, pa naslovi stoje ovde umesto u
     * odvojenim fajlovima po jeziku kao kod lekcija. NE ide kroz `loc()` —
     * stize vec preveden.
     */
    val title: Map<String, String>,
    val steps: List<CurriculumStep>
)

data class Curriculum(val version: Int, val chapters: List<Chapter>) {
    /** Redosled svih koraka kroz sva poglavlja — osnova za otkljucavanje. */
    val allStepIds: List<String> get() = chapters.flatMap { c -> c.steps.map { it.id } }

    fun step(id: String): CurriculumStep? =
        chapters.firstNotNullOfOrNull { c -> c.steps.find { it.id == id } }
}

object CurriculumParser {

    /**
     * Sirove vrednosti `GameDifficulty`-ja. Stoje kao literali, a ne kao
     * referenca na sam enum, jer ovaj fajl mora da ostane bez `ui`/`logic`
     * zavisnosti.
     *
     * Postoji zato sto bi tipfeler ("begginer") inace prosao svaku proveru i
     * isplivao tek kao korak koji se ne moze odigrati.
     */
    val KNOWN_DIFFICULTIES: Set<String> =
        setOf("beginner", "easy", "medium", "hard", "stockfish")

    fun parse(json: String): Curriculum {
        val root = JSONObject(json)
        val chaptersJson = root.getJSONArray("chapters")
        val chapters = (0 until chaptersJson.length()).map { i ->
            val c = chaptersJson.getJSONObject(i)
            val titleJson = c.getJSONObject("title")
            val title = titleJson.keys().asSequence().associateWith { titleJson.getString(it) }
            val stepsJson = c.getJSONArray("steps")
            Chapter(
                id = c.getString("id"),
                title = title,
                steps = (0 until stepsJson.length()).map { j -> parseStep(stepsJson.getJSONObject(j)) }
            )
        }
        return Curriculum(version = root.optInt("version", 1), chapters = chapters)
    }

    private fun parseStep(o: JSONObject): CurriculumStep {
        val id = o.getString("id")
        val kind = when (val type = o.getString("type")) {
            "lesson" -> StepKind.Lesson(o.getString("lessonId"))
            "practice" -> StepKind.Practice(themes(o), o.getInt("count"), range(o))
            "test" -> StepKind.Test(themes(o), o.getInt("count"), range(o))
            "game" -> {
                val difficulty = o.getString("difficulty")
                require(difficulty in KNOWN_DIFFICULTIES) {
                    "Nepoznata tezina '$difficulty'. Dozvoljeno: " +
                        KNOWN_DIFFICULTIES.sorted().joinToString(", ") + "."
                }
                StepKind.Game(difficulty, if (o.has("startFEN")) o.getString("startFEN") else null)
            }
            else -> throw IllegalArgumentException(
                "Nepoznat tip koraka '$type'. Dodaj ga u StepKind ili ispravi JSON."
            )
        }
        return CurriculumStep(id, kind)
    }

    private fun themes(o: JSONObject): List<String> {
        val a = o.getJSONArray("themes")
        return (0 until a.length()).map { a.getString(it) }
    }

    /**
     * Kotlin `IntRange` sa `lo > hi` NE puca — samo je prazan, pa bi korak
     * tiho ostao bez ijednog zadatka. Swift `ClosedRange` bi ovde srusio
     * proces, i iOS se na to oslanja. Zato je provera ovde EKSPLICITNA.
     */
    private fun range(o: JSONObject): IntRange {
        val a = o.getJSONArray("ratingRange")
        require(a.length() == 2) { "ratingRange mora imati tacno dva broja" }
        val lo = a.getInt(0)
        val hi = a.getInt(1)
        require(lo <= hi) { "ratingRange mora biti [donja, gornja] sa donja <= gornja, dobijeno [$lo, $hi]" }
        return lo..hi
    }
}

/** Ucitava isporuceni kurikulum iz `assets/curriculum.json`. */
fun loadCurriculum(context: Context): Curriculum =
    CurriculumParser.parse(
        context.assets.open("curriculum.json").bufferedReader().use { it.readText() }
    )

