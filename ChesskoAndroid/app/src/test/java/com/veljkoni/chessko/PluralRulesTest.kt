package com.veljkoni.chessko

import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.LocalNumber
import com.veljkoni.chessko.logic.PluralCategory
import com.veljkoni.chessko.logic.PluralRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mnozine i lokalni format brojeva (dizajnerski pregled, spec § 3 i § 16).
 * Pravila su ista kao iOS `LocalFormatTests.swift` — ako se jedna strana
 * promeni, druga mora isto.
 */
class PluralRulesTest {

    @After fun resetLanguage() = Loc.setLanguage("sr")

    @Test
    fun serbianCategories() {
        val expected = mapOf(
            0 to PluralCategory.OTHER, 1 to PluralCategory.ONE, 2 to PluralCategory.FEW,
            4 to PluralCategory.FEW, 5 to PluralCategory.OTHER, 11 to PluralCategory.OTHER,
            12 to PluralCategory.OTHER, 14 to PluralCategory.OTHER, 21 to PluralCategory.ONE,
            22 to PluralCategory.FEW, 25 to PluralCategory.OTHER, 101 to PluralCategory.ONE,
            111 to PluralCategory.OTHER, 112 to PluralCategory.OTHER
        )
        for ((n, c) in expected) assertEquals("sr $n", c, PluralRules.category("sr", n))
    }

    @Test
    fun russianUsesManyWhereSerbianUsesOther() {
        assertEquals(PluralCategory.ONE, PluralRules.category("ru", 1))
        assertEquals(PluralCategory.FEW, PluralRules.category("ru", 3))
        assertEquals(PluralCategory.MANY, PluralRules.category("ru", 5))
        assertEquals(PluralCategory.MANY, PluralRules.category("ru", 11))
        assertEquals(PluralCategory.MANY, PluralRules.category("ru", 0))
    }

    @Test
    fun oneOtherLanguages() {
        for (l in listOf("en", "de", "it")) {
            assertEquals(PluralCategory.ONE, PluralRules.category(l, 1))
            assertEquals(PluralCategory.OTHER, PluralRules.category(l, 0))
            assertEquals(PluralCategory.OTHER, PluralRules.category(l, 2))
        }
        for (l in listOf("fr", "hi")) {
            assertEquals(PluralCategory.ONE, PluralRules.category(l, 0))
            assertEquals(PluralCategory.ONE, PluralRules.category(l, 1))
            assertEquals(PluralCategory.OTHER, PluralRules.category(l, 2))
        }
        assertEquals(PluralCategory.OTHER, PluralRules.category("zh-Hans", 1))
    }

    /** „1 Dana" (spec § 3) je tacno ono sto ova funkcija postoji da spreci. */
    @Test
    fun streakLabelAgreesWithTheNumber() {
        Loc.setLanguage("sr")
        assertEquals("dan zaredom", Loc.plural("dana zaredom", 1))
        assertEquals("dana zaredom", Loc.plural("dana zaredom", 3))
        assertEquals("dan zaredom", Loc.plural("dana zaredom", 21))
        Loc.setLanguage("en")
        assertEquals("day in a row", Loc.plural("dana zaredom", 1))
        assertEquals("days in a row", Loc.plural("dana zaredom", 2))
        Loc.setLanguage("ru")
        assertEquals("дней подряд", Loc.plural("dana zaredom", 5))
        Loc.setLanguage("sr")
        assertEquals("2 moguća poteza", Loc.plural("%d mogućih poteza", 2))
        assertEquals("5 mogućih poteza", Loc.plural("%d mogućih poteza", 5))
    }

    /**
     * Svaki unos ima svih osam jezika, a svaki jezik ima formu za SVAKU kategoriju
     * koju njegovo pravilo moze da vrati — inace bi broj tiho pao na `OTHER`.
     */
    @Test
    fun everyPluralEntryCoversEveryCategoryItsLanguageUses() {
        val langs = listOf("sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi")
        for ((key, byLang) in Loc.pluralDictionaryForTests) {
            for (l in langs) {
                val forms = byLang[l]
                assertTrue("$key nema jezik $l", forms != null)
                val used = (0..200).map { PluralRules.category(l, it) }.toSet()
                for (c in used) assertTrue("$key/$l nema $c", forms!!.containsKey(c))
            }
        }
    }

    /** Svaki `locPlural("…")` u izvoru ima unos — isti obrazac kao `LocTest`. */
    @Test
    fun everyLocPluralCallInTheSourceHasAnEntry() {
        val root = File("src/main/java")
        val call = Regex("""\blocPlural\(\s*"((?:[^"\\]|\\.)*)"""")
        val missing = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            call.findAll(f.readText()).map { it.groupValues[1] }
        }.filter { it !in Loc.pluralDictionaryForTests }.toList()
        assertTrue("locPlural bez unosa: $missing", missing.isEmpty())
    }

    @Test
    fun numbersFollowAppLanguage() {
        assertEquals("-2.3", LocalNumber.decimal(-2.3, languageCode = "en"))
        assertTrue(LocalNumber.decimal(-2.3, languageCode = "sr").contains(","))
        assertEquals("+2.3", LocalNumber.decimal(2.3, signed = true, languageCode = "en"))
        assertTrue(LocalNumber.percent(0.0, languageCode = "sr").startsWith("0,0"))
        assertEquals("41.9%", LocalNumber.percent(41.9, languageCode = "en"))
        assertTrue(LocalNumber.percent(41.9, languageCode = "de").contains("41,9"))
    }
}
