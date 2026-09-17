package com.veljkoni.chessko

import com.veljkoni.chessko.ui.localeForDateFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * `ofPattern("d. MMMM yyyy.")` bez `Locale`-a uzima SISTEMSKI jezik uredjaja,
 * ne jezik izabran u aplikaciji (`Loc.getLanguage()`) — korisnik sa srpskim u
 * aplikaciji i engleskim sistemom je video „September" umesto „septembar".
 * `localeForDateFormatting` je popravka; ovaj test dokazuje da radi za svih
 * 8 jezika aplikacije, ne samo za sr/en.
 */
class PuzzleDateFormatTest {

    // 17. septembar 2026. — namerno isti datum kao "danasnji dan" u ovoj fazi,
    // da se lakse poredi sa rucnom proverom.
    private val date = LocalDate.of(2026, 9, 17)

    private fun format(languageCode: String): String =
        date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy.", localeForDateFormatting(languageCode)))

    @Test fun serbianUsesLatinScriptNotCyrillic() {
        val result = format("sr")
        assertEquals("17. septembar 2026.", result)
        // `Locale.forLanguageTag("sr")` bez ovog fix-a daje cirilicno
        // "септембар" — CLDR podrazumeva cirilicu za goli "sr". Ceo srpski
        // sadrzaj aplikacije je latinica (vidi `Loc.kt`), pa cirilica ovde
        // ne bi bila "drugi jezik" nego "isti jezik, pogresno pismo".
        assertFalse(result.contains("септембар"))
    }

    @Test fun englishNameOfMonth() =
        assertEquals("17. September 2026.", format("en"))

    @Test fun frenchNameOfMonth() =
        assertEquals("17. septembre 2026.", format("fr"))

    @Test fun germanNameOfMonth() =
        assertEquals("17. September 2026.", format("de"))

    @Test fun italianNameOfMonth() =
        assertEquals("17. settembre 2026.", format("it"))

    @Test fun russianNameOfMonth() =
        assertEquals("17. сентября 2026.", format("ru"))

    // `Loc.getLanguage()` vraca goli "zh" (ne "zh-Hans", to je kod FAJLA
    // lekcija — vidi CLAUDE.md). Provera da goli "zh" ne padne na engleski.
    @Test fun chineseNameOfMonthDoesNotFallBackToEnglish() {
        val result = format("zh")
        assertFalse(result.contains("September"))
        assertEquals("17. 九月 2026.", result)
    }

    @Test fun hindiNameOfMonthDoesNotFallBackToEnglish() {
        val result = format("hi")
        assertFalse(result.contains("September"))
        assertEquals("17. सितंबर 2026.", result)
    }
}
