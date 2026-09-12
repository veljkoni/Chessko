package com.veljkoni.chessko.logic

import android.content.Context
import com.veljkoni.chessko.models.LessonDocument
import com.veljkoni.chessko.models.LessonParser

// MARK: - Lesson Repository
//
// Cita `assets/lessons/<id>.<jezik>.json`. Za razliku od `puzzles.sqlite`,
// ovde NEMA kopiranja u `filesDir`: `assets.open()` vraca strim i radi i nad
// kompresovanim asset-om, a JSON se cita u celosti odjednom.
class LessonRepository(private val context: Context) {

    private val cache = HashMap<String, LessonDocument>()

    /// Lekcija na trazenom jeziku, sa lancem `jezik -> en -> sr`.
    ///
    /// **`language` je kod kojim se ZOVE FAJL**, ne ono sto vraca
    /// `Loc.getLanguage()`. Za kineski su to razlicite stvari: `getLanguage()`
    /// daje „zh", a fajl se zove `.zh-Hans.json`. Pozivalac mora da prosledi
    /// oblik iz imena fajla, inace kineski korisnik tiho dobija engleski.
    fun lesson(id: String, language: String): LessonDocument? {
        for (candidate in listOf(language, "en", "sr")) {
            val key = "$id.$candidate"
            cache[key]?.let { return it }
            load(key)?.let { cache[key] = it; return it }
        }
        return null
    }

    /// Razdvaja DVA slucaja koja se lako slepe u jedan `runCatching`:
    /// fajla nema (ocekivano — nove lekcije idu samo na sr+en, pa se jezik
    /// uredno preskace), i fajl POSTOJI ali se ne parsira (greska koja ne sme
    /// da se izgubi; `LessonParser` namerno baca).
    private fun load(key: String): LessonDocument? {
        val text = try {
            context.assets.open("lessons/$key.json").bufferedReader().use { it.readText() }
        } catch (_: java.io.FileNotFoundException) {
            return null
        }
        return try {
            LessonParser.parse(text)
        } catch (e: Exception) {
            // Glasno, ali bez rusenja aplikacije: jedna pokvarena lekcija ne sme
            // da obori ceo ekran Ucenja.
            android.util.Log.e("Chessko", "lessons/$key.json postoji ali se ne parsira: $e")
            null
        }
    }

    /// Id-jevi lekcija koje stvarno postoje u `assets`, otkriveni iz imena
    /// fajlova. Nova lekcija = nov JSON, bez izmene koda.
    fun discoveredLessonIds(): List<String> {
        val files = context.assets.list("lessons")?.toList() ?: emptyList()
        val ids = files.mapNotNull { name ->
            val stem = name.removeSuffix(".json")
            val dot = stem.lastIndexOf('.')
            if (dot <= 0) return@mapNotNull null
            // Poslednji deo MORA biti podrzan jezik: bez te provere bi zalutali
            // `openings.sr.backup.json` dao fantomsku lekciju „openings.sr".
            if (stem.substring(dot + 1) !in LANGUAGES) null else stem.substring(0, dot)
        }.distinct()
        val known = LESSON_ORDER.filter { it in ids }
        val extra = (ids - LESSON_ORDER.toSet()).sorted()
        return known + extra
    }

    companion object {
        /// Redosled na ekranu. NIJE spisak postojecih lekcija — lekcija koja
        /// nije ovde i dalje se prikazuje, na kraju liste. Inace bi nova
        /// lekcija bila NEVIDLJIVA bez ijedne poruke.
        val LESSON_ORDER = listOf(
            "board-and-pieces", "notation", "openings", "tactics", "middlegame", "endgame"
        )
        private val LANGUAGES = setOf("sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi")
    }
}
