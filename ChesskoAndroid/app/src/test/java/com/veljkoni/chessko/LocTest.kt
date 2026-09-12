package com.veljkoni.chessko

import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.loc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocTest {

    private val languages = setOf("sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi")

    @Test
    fun everyEntryHasAllEightLanguages() {
        // `Loc.get` na nepoznat kljuc vraca SAM KLJUC, dakle srpski tekst, i to
        // tiho. Krnj unos se zato ne vidi kao greska nego kao "mesanje jezika" —
        // tacno kvar iz spec-a 4.5. Ovaj test je jedino mesto koje moze da vikne.
        val missing = Loc.dictionaryForTests
            .filterValues { !it.keys.containsAll(languages) }
            .map { (key, langs) -> "$key -> nedostaje ${languages - langs.keys}" }
        assertTrue("krnji unosi:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun difficultyLabelsAreTranslated() {
        Loc.setLanguage("en")
        assertEquals("Easy", loc("Lako"))
        Loc.setLanguage("sr")
        assertEquals("Lako", loc("Lako"))
    }

    @Test
    fun puzzleScreenChromeIsTranslated() {
        // Bas stringovi iz spec-a 4.5.
        Loc.setLanguage("en")
        for (key in listOf("Rejting: ", "Prikaži rešenje", "Pokušaj ponovo",
                           "Greška pri učitavanju zadatka", "Sledeći zadatak")) {
            assertTrue("kljuc nije preveden na engleski: $key", loc(key) != key)
        }
        Loc.setLanguage("sr")
    }
}
