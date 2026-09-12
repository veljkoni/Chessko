package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.LessonRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LessonRepositoryTest {

    private fun repo() = LessonRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun everyShippedLessonParsesInEveryShippedLanguage() {
        // Prolazi SVE fajlove, ne uzorak. Pokvaren JSON u jednom jeziku inace
        // izadje na videlo tek kad ga korisnik tog jezika otvori.
        val r = repo()
        var parsed = 0
        for (id in r.discoveredLessonIds()) {
            for (lang in listOf("sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi")) {
                val d = r.lesson(id, lang) ?: continue
                assertTrue("prazna lekcija: $id.$lang", d.blocks.isNotEmpty())
                // Broji se SAMO kad je dokument stvarno na trazenom jeziku.
                // `lesson()` pada na `en` pa `sr`, pa bi golo brojanje dalo
                // 6 lekcija x 8 jezika = 48, a fajlova ima 36: nove dve lekcije
                // postoje samo na sr+en. Bez ove provere test broji uspehe
                // rezervnog lanca kao da su prevodi.
                if (d.language == lang) parsed++
            }
        }
        // 4 originalne lekcije x 8 jezika + 2 nove x 2 jezika = 36
        assertEquals(36, parsed)
    }

    @Test
    fun discoversAllSixLessons() {
        assertEquals(
            listOf("board-and-pieces", "notation", "openings", "tactics", "middlegame", "endgame"),
            repo().discoveredLessonIds()
        )
    }

    @Test
    fun chineseResolvesToItsOwnFileNotEnglish() {
        // Kljucni test ove faze: `zh-Hans` se NE sme tiho svesti na engleski.
        val d = repo().lesson("board-and-pieces", "zh-Hans")
        assertNotNull(d)
        assertEquals("zh-Hans", d!!.language)
    }

    @Test
    fun missingLanguageFallsBackToEnglishThenSerbian() {
        // Nove lekcije idu samo na sr+en. Trazen francuski mora dati engleski,
        // ne prazan ekran.
        val d = repo().lesson("tactics", "fr")
        assertNotNull(d)
        assertEquals("en", d!!.language)
    }

    @Test
    fun unknownLessonReturnsNull() {
        assertEquals(null, repo().lesson("nepostojeca", "sr"))
    }

    @Test
    fun knownLessonsComeInPrescribedOrderAndUnknownGoLast() {
        val ids = repo().discoveredLessonIds()
        assertEquals("board-and-pieces", ids.first())
        assertEquals("endgame", ids.last())
    }
}
