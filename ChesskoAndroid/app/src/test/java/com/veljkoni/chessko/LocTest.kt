package com.veljkoni.chessko

import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.loc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocTest {

    private val languages = setOf("sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi")

    @Test
    fun everyLocCallInTheSourceHasAKeyInTheDictionary() {
        // `everyEntryHasAllEightLanguages` hvata samo KRNJE unose — kljuc koji
        // uopste NE postoji u recniku je za njega nevidljiv. A bas to je kvar:
        // `Loc.get` na nepoznat kljuc tiho vraca sam kljuc, pa se srpski tekst
        // prikaze na svakom stranom jeziku, bez pada i bez traga u logu.
        //
        // Tako je u ovoj fazi prosao `LearnViewModel.PROMOTION`: literal je bio
        // bez „(redovi 8)", recnik i iOS su imali sa njim, i ceo pasus je na
        // engleskom ostajao srpski. Ovaj test je jedina zastita koja to vidi.
        val root = File("src/main/java/com/veljkoni/chessko")
        assertTrue("izvor nije nadjen na ${root.absolutePath}", root.isDirectory)

        // `loc("…")` i `locF("…")` sa LITERALOM. Pozivi sa promenljivom
        // (`loc(theme.label)`) se ne mogu staticki proveriti i preskacu se.
        val poziv = Regex("""\blocF?\(\s*"((?:[^"\\]|\\.)*)"""")
        val recnik = Loc.dictionaryForTests

        // `Chessko` je ime aplikacije i namerno nije u recniku.
        val dozvoljeniIzuzeci = setOf("Chessko")

        val nedostaju = sortedMapOf<String, MutableList<String>>()
        root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { i, red ->
                for (m in poziv.findAll(red)) {
                    val k = m.groupValues[1].replace("\\\"", "\"")
                    if (k !in recnik && k !in dozvoljeniIzuzeci) {
                        nedostaju.getOrPut(k) { mutableListOf() }.add("${f.name}:${i + 1}")
                    }
                }
            }
        }
        assertTrue(
            "loc() pozivi bez unosa u recniku:\n" +
                nedostaju.entries.joinToString("\n") { (k, gde) -> "  ${k.take(70)} -> $gde" },
            nedostaju.isEmpty()
        )
    }

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
    fun fileLanguageCodeKeepsScriptForChinese() {
        // Lekcije se traze po IMENU FAJLA (`board-and-pieces.zh-Hans.json`), a
        // `getLanguage()` za kineski vraca „zh" radi UI biraca. Da `LearnView`
        // upotrebi `getLanguage()`, kineski korisnik bi tiho dobio engleski —
        // bez pada i bez poruke. Ovaj test je jedino mesto koje to hvata.
        Loc.setLanguage("zh")
        assertEquals("zh", Loc.getLanguage())
        assertEquals("zh-Hans", Loc.fileLanguageCode())

        Loc.setLanguage("fr")
        assertEquals("fr", Loc.fileLanguageCode())
        Loc.setLanguage("sr")
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
