package com.veljkoni.chessko

// NAPOMENA (Faza 9, Task 3): ovaj test namerno zivi u `androidTest`, ne u
// `test` — iz ISTOG razloga kao `LessonContentTest` (vidi njegov uvodni
// komentar): cita se pravi lekcijski JSON, a `org.json` je u
// `testDebugUnitTest` stub koji baca „not mocked" na svaki poziv. Uz to,
// `lessonGlyph` gradi `Icons.Filled.*` vektore, sto je Compose UI kod.
//
// Test tvrdi INVARIJANTU, ne vrednosti: ne pominje nijedan emoji, nijedno ime
// Material ikone i nijedan broj iz mape. Zato prezivljava svaku sledecu fazu
// koja neki par promeni — a pada tacno onda kad mapa prestane da pokriva
// isporuceni sadrzaj. Oba ishoda tog raskoraka su pokrivena, jer ih ima dva a
// ne jedan: glasan (`Unknown` — crveni trougao i sirovo SF ime) i TIH (ime bez
// tacke procuri kroz granu „vec gotov glif" i iscrta se kao obicna rec).

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.ui.LessonGlyph
import com.veljkoni.chessko.ui.lessonGlyph
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LessonGlyphMapTest {

    private val assets
        get() = InstrumentationRegistry.getInstrumentation().targetContext.assets

    /// Skuplja SVAKO `icon` polje, na bilo kojoj dubini.
    ///
    /// Obilazak je namerno slep za semu (`heading`/`bullets`/`box`/`exercise`
    /// i doc-level polje svi nose `icon` na razlicitim mestima): tip bloka koji
    /// neka sledeca faza doda nosice svoj `icon` bez izmene ovog testa. Zato se
    /// ne ide kroz `LessonParser` nego kroz sirov `JSONObject`.
    private fun collectIcons(node: Any?, out: MutableList<String>) {
        when (node) {
            is JSONObject -> for (key in node.keys()) {
                val value = node.opt(key)
                if (key == "icon" && value is String) out.add(value)
                collectIcons(value, out)
            }
            is JSONArray -> for (i in 0 until node.length()) collectIcons(node.opt(i), out)
        }
    }

    @Test
    fun everyIconInShippedLessonsResolvesToAKnownGlyph() {
        val files = assets.list("lessons")?.filter { it.endsWith(".json") }.orEmpty()
        assertTrue("u assets/lessons nema nijednog JSON-a", files.isNotEmpty())

        val distinct = sortedSetOf<String>()
        for (name in files) {
            val text = assets.open("lessons/$name").bufferedReader().use { it.readText() }
            val icons = mutableListOf<String>()
            collectIcons(JSONObject(text), icons)
            // Osigurac od tautologije: bez njega bi pokvaren obilazak (ili
            // promenjeno ime polja u semi) dao prazan skup i test bi prolazio
            // tvrdeci nista. Svaki isporuceni fajl nosi bar doc-level `icon`.
            assertTrue(
                "lessons/$name nema nijedno `icon` polje — obilazak je pokvaren, ne sadrzaj",
                icons.isNotEmpty()
            )
            distinct.addAll(icons)
        }

        val unresolved = distinct.filter { lessonGlyph(it) is LessonGlyph.Unknown }
        assertEquals(
            "SF simboli iz isporucenog sadrzaja koje mapa ne poznaje " +
                "(korisnik bi na tom mestu video crveni trougao i sirovo ime): $unresolved",
            emptyList<String>(),
            unresolved
        )

        // Druga polovina iste tvrdnje, i NIJE visak — nasla ju je mutacija ovog
        // taska. `Unknown` hvata samo imena sa tackom; tri isporucena SF imena
        // je nemaju (`globe`, `link`, `tuningfork`), pa bi izbacivanje bas njih
        // iz mape proslo kroz granu „vec gotov glif" i nacrtalo BUKVALNU rec
        // „tuningfork" u lekciji — tiho, sto je tacno ono sto `Unknown` postoji
        // da spreci. Invarijanta bez vrednosti: isporuceni `icon` koji je cist
        // ASCII je SF IME, nikad glif, pa ga mora razresiti mapa — a prolaz kroz
        // rezervnu granu se prepoznaje po tome sto nacrtani tekst bude jednak
        // samom imenu. Ne-ASCII `icon` (rucno pisana lekcija koja upise emoji
        // direktno) ovim ostaje dozvoljen; danas ga nijedan fajl ne koristi.
        val drawnAsItsOwnName = distinct.filter { symbol ->
            val glyph = lessonGlyph(symbol)
            symbol.all { it.code < 128 } && glyph is LessonGlyph.Emoji && glyph.text == symbol
        }
        assertEquals(
            "SF imena koja bi se iscrtala kao sopstveni tekst umesto glifa: $drawnAsItsOwnName",
            emptyList<String>(),
            drawnAsItsOwnName
        )
    }

    @Test
    fun symbolOutsideTheMapIsLoudNotSilent() {
        // Bez ove tvrdnje bi prva prolazila i nad mapom koja PROGUTA sve — npr.
        // ako bi `lessonGlyph` nepoznat simbol vratio kao neutralnu tacku
        // (zatecen fallback pre Task-a 0) ili prazan `Emoji`.
        val fake = lessonGlyph("nema.me.fill")
        assertTrue("izmisljen SF simbol mora dati Unknown, dao je $fake", fake is LessonGlyph.Unknown)
        assertEquals("nema.me.fill", (fake as LessonGlyph.Unknown).symbol)

        // Prazno ime je ista klasa greske (krnj JSON), i mora da vikne isto.
        assertTrue(lessonGlyph("") is LessonGlyph.Unknown)

        // A niz BEZ tacke nije SF ime nego vec gotov glif iz rucno pisane
        // lekcije — on se propusta nepromenjen i to NIJE greska. Granica izmedju
        // ta dva slucaja je jedino sto deli „vikni" od „nacrtaj".
        val passthrough = lessonGlyph("🔥")
        assertTrue(passthrough is LessonGlyph.Emoji)
        assertEquals("🔥", (passthrough as LessonGlyph.Emoji).text)
    }
}
