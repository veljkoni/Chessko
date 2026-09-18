package com.veljkoni.chessko

import com.veljkoni.chessko.ui.localeForDateFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * `ofPattern("d. MMMM yyyy.")` bez `Locale`-a uzima SISTEMSKI jezik uredjaja,
 * ne jezik izabran u aplikaciji (`Loc.getLanguage()`) — korisnik sa srpskim u
 * aplikaciji i engleskim sistemom je video „September" umesto „septembar".
 * `localeForDateFormatting` je popravka; ovaj test dokazuje da radi za svih
 * 8 jezika aplikacije, ne samo za sr/en.
 *
 * **Sta se NE tvrdi (S-3, talas ispravki):** doslovni nazivi meseci za svih 8
 * jezika (ranije npr. ruski genitiv „сентября") su uklonjeni. Ti nizovi su
 * podatak CLDR baze ugradjene u JDK, ne ponasanje aplikacije — nadogradnja
 * JDK-a je mogla da obori build iz razloga koji sa Chessko-om nema veze.
 * Ostaje ono sto je i bio ceo dokaz, izrazeno kroz PISMO umesto kroz slova:
 * srpski ne sme biti cirilica, a `zh`/`hi` ne smeju pasti na engleski.
 * Pismo je stabilno kroz CLDR verzije; tacan oblik imena meseca nije.
 */
class PuzzleDateFormatTest {

    // 17. septembar 2026. — namerno isti datum kao "danasnji dan" u ovoj fazi,
    // da se lakse poredi sa rucnom proverom.
    private val date = LocalDate.of(2026, 9, 17)

    // Kodovi koje vraca `Loc.getLanguage()` (dakle goli "zh", ne "zh-Hans" —
    // to je kod FAJLA lekcija, vidi CLAUDE.md).
    private val appLanguages = listOf("sr", "en", "fr", "de", "it", "ru", "zh", "hi")

    private fun format(languageCode: String): String =
        date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy.", localeForDateFormatting(languageCode)))

    private val cirilica = Regex("\\p{IsCyrillic}")
    private val han = Regex("\\p{IsHan}")
    private val devanagari = Regex("\\p{IsDevanagari}")

    @Test fun serbianUsesLatinScriptNotCyrillic() {
        val result = format("sr")
        // `Locale.forLanguageTag("sr")` bez ovog fix-a daje cirilicno
        // "септембар" — CLDR podrazumeva cirilicu za goli "sr". Ceo srpski
        // sadrzaj aplikacije je latinica (vidi `Loc.kt`), pa cirilica ovde
        // ne bi bila "drugi jezik" nego "isti jezik, pogresno pismo".
        assertFalse("srpski datum ne sme biti cirilica, dobijeno: $result", cirilica.containsMatchIn(result))
        assertEquals("sr", localeForDateFormatting("sr").language)
        assertEquals("Latn", localeForDateFormatting("sr").script)
    }

    @Test fun russianUsesCyrillicScript() {
        // Kontrola za test iznad: `sr` bez cirilice vredi samo ako mehanizam
        // uopste ume da isporuci cirilicu kad treba.
        val result = format("ru")
        assertTrue("ruski datum mora biti cirilica, dobijeno: $result", cirilica.containsMatchIn(result))
    }

    @Test fun chineseDoesNotFallBackToEnglish() {
        val result = format("zh")
        assertFalse(result.contains("September"))
        assertTrue("ocekivan kineski zapis meseca, dobijeno: $result", han.containsMatchIn(result))
    }

    @Test fun hindiDoesNotFallBackToEnglish() {
        val result = format("hi")
        assertFalse(result.contains("September"))
        assertTrue("ocekivan devanagari zapis meseca, dobijeno: $result", devanagari.containsMatchIn(result))
    }

    @Test fun frenchAndItalianDoNotFallBackToEnglish() {
        // Nemacki je namerno izostavljen: CLDR i za `de` daje bas "September",
        // pa poredjenje sa engleskim za njega ne znaci nista.
        val english = format("en")
        for (code in listOf("fr", "it")) {
            assertFalse("`$code` je vratio engleski naziv meseca", format(code) == english)
        }
    }

    @Test fun everyAppLanguageResolvesToItsOwnLocale() {
        // Ovo je jedina tvrdnja o samoj funkciji: svaki kod se mapira u locale
        // ISTOG jezika (a `sr` jos i u latinicno pismo). Bez toga bi tih
        // `Locale.forLanguageTag` na nepoznat kod vratio `ROOT` i sve palo na
        // engleski, bez ijedne greske.
        for (code in appLanguages) {
            assertEquals("`$code` se ne mapira u svoj jezik", code, localeForDateFormatting(code).language)
        }
    }

    @Test fun everyAppLanguageKeepsThePatternShape() {
        // Dan i godina su brojevi iz obrasca, ne iz CLDR-a — oni se smeju
        // tvrditi doslovno. Naziv meseca se namerno ne gleda.
        val oblik = Regex("^17\\. .+ 2026\\.$")
        for (code in appLanguages) {
            val result = format(code)
            assertTrue("`$code` je razbio obrazac d. MMMM yyyy. -> $result", oblik.matches(result))
        }
    }
}
