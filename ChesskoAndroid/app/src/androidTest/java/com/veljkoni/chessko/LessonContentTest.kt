package com.veljkoni.chessko

// NAPOMENA (Faza 6b, Task 1): ovaj test namerno zivi u `androidTest`, ne u
// `test`. `LessonContent.kt` ne uvozi nista iz `android.*` i logicki je cist
// JVM kod, ali `org.json` je u `testDebugUnitTest` (JVM jedinicni testovi)
// podrazumevano STUB koji baca "not mocked" RuntimeException na svaki poziv
// (npr. `getJSONArray`) — dokazano pri implementaciji ovog taska, vidi
// task-1-report.md. `isReturnDefaultValues = true` ne resava ovo. Bez uvodjenja
// nove zavisnosti (kotlinx.serialization ili org.json:json su zabranjeni),
// jedini nacin da se testira sa PRAVIM `org.json` je `androidTest` (radi na
// emulatoru/uredjaju, gde je pravi Android runtime). Ovo je bila odluka
// kontrolera (opcija a od dve ponudjene), potvrdjena posle prijave stuba.

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veljkoni.chessko.models.BoxStyle
import com.veljkoni.chessko.models.ExerciseKind
import com.veljkoni.chessko.models.LessonBlock
import com.veljkoni.chessko.models.LessonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LessonContentTest {

    private fun doc(blocks: String) = """
        {"id":"t","language":"sr","title":"N","subtitle":"P","icon":"book.fill","blocks":[$blocks]}
    """.trimIndent()

    @Test
    fun parsesDocumentHeader() {
        val d = LessonParser.parse(doc("""{"type":"paragraph","text":"zdravo"}"""))
        assertEquals("t", d.id)
        assertEquals("sr", d.language)
        assertEquals("N", d.title)
        assertEquals("P", d.subtitle)
        assertEquals("book.fill", d.icon)
        assertEquals(1, d.blocks.size)
    }

    @Test
    fun parsesEveryBlockTypeTheShippedContentUses() {
        // 11 tipova koje isporuceni JSON stvarno koristi, plus `quote` koji sema
        // definise a sadrzaj jos ne koristi — podrzan je da lekcija koja ga
        // doda sutra ne pukne.
        val d = LessonParser.parse(doc("""
            {"type":"heading","text":"H","icon":"i"},
            {"type":"paragraph","text":"P"},
            {"type":"bullets","items":[{"icon":"a","title":"b","text":"c"}]},
            {"type":"box","style":"rule","icon":"i","title":"T","text":"X"},
            {"type":"quote","text":"Q","author":"A"},
            {"type":"pieceRow","piece":"pawn","name":"Pesak","count":"8"},
            {"type":"numberedRule","number":2,"title":"T","text":"X"},
            {"type":"pieceValueTable","rows":[{"piece":"king","name":"Kralj","value":"∞"}]},
            {"type":"board","fen":"8/8/8/8/8/8/8/8 w - - 0 1","caption":"C","interactive":false},
            {"type":"explorer"},
            {"type":"exercise","kind":"scripted","title":"T","hint":"H","icon":"i","uciMoves":["e2e4"]},
            {"type":"divider"}
        """.trimIndent()))
        assertEquals(12, d.blocks.size)
        assertTrue(d.blocks[0] is LessonBlock.Heading)
        assertTrue(d.blocks[4] is LessonBlock.Quote)
        assertTrue(d.blocks[9] is LessonBlock.Explorer)
        assertTrue(d.blocks[11] is LessonBlock.Divider)
    }

    @Test
    fun everyFieldLandsInItsOwnPropertyNotTheNeighbouring() {
        // Ovaj test postoji zbog konkretne rupe koju je pregled nasao sabotazom:
        // zamena `title` i `text` u `Bullets` PROLAZILA je kroz ceo test-set,
        // jer su testovi tvrdili samo TIP bloka, nikad sadrzaj.
        //
        // Kod rucnog `org.json` parsiranja kompajler ne vidi imena kljuceva, pa
        // je zamena dva polja istog tipa (`String`) najverovatnija greska koja
        // se moze napraviti — i jedina koju testovi moraju da hvataju.
        // Zato svaka vrednost ispod ima RAZLICIT sadrzaj: da zamena bilo koja
        // dva polja obori bar jednu tvrdnju.
        val d = LessonParser.parse(doc("""
            {"type":"heading","text":"naslov-tekst","icon":"naslov-ikona"},
            {"type":"bullets","items":[{"icon":"b-ikona","title":"b-naslov","text":"b-tekst"}]},
            {"type":"box","style":"warning","icon":"k-ikona","title":"k-naslov","text":"k-tekst"},
            {"type":"quote","text":"c-tekst","author":"c-autor"},
            {"type":"pieceRow","piece":"knight","name":"Skakac","count":"2"},
            {"type":"numberedRule","number":7,"title":"p-naslov","text":"p-tekst"},
            {"type":"pieceValueTable","rows":[{"piece":"rook","name":"Top","value":"5","valueLabel":"5 poena"}]},
            {"type":"board","fen":"8/8/8/8/8/8/8/8 w - - 0 1","caption":"opis-table","interactive":true},
            {"type":"exercise","kind":"scripted","title":"v-naslov","hint":"v-hint","icon":"v-ikona",
             "uciMoves":["e2e4","e7e5"],"startFEN":"8/8/8/8/8/8/8/8 w - - 0 1",
             "solvedMessage":"v-resen","wrongMessage":"v-pogresan","playingPrompt":"v-prompt","mateIn":3}
        """.trimIndent()))

        val h = d.blocks[0] as LessonBlock.Heading
        assertEquals("naslov-tekst", h.text); assertEquals("naslov-ikona", h.icon)

        val b = (d.blocks[1] as LessonBlock.Bullets).items[0]
        assertEquals("b-ikona", b.icon); assertEquals("b-naslov", b.title); assertEquals("b-tekst", b.text)

        val k = d.blocks[2] as LessonBlock.Box
        assertEquals(BoxStyle.WARNING, k.style); assertEquals("k-ikona", k.icon)
        assertEquals("k-naslov", k.title); assertEquals("k-tekst", k.text)

        val c = d.blocks[3] as LessonBlock.Quote
        assertEquals("c-tekst", c.text); assertEquals("c-autor", c.author)

        val pr = d.blocks[4] as LessonBlock.PieceRow
        assertEquals("knight", pr.piece); assertEquals("Skakac", pr.name); assertEquals("2", pr.count)

        val nr = d.blocks[5] as LessonBlock.NumberedRule
        assertEquals(7, nr.number); assertEquals("p-naslov", nr.title); assertEquals("p-tekst", nr.text)

        val row = (d.blocks[6] as LessonBlock.PieceValueTable).rows[0]
        assertEquals("rook", row.piece); assertEquals("Top", row.name)
        assertEquals("5", row.value); assertEquals("5 poena", row.valueLabel)

        val bd = d.blocks[7] as LessonBlock.Board
        assertEquals("8/8/8/8/8/8/8/8 w - - 0 1", bd.fen)
        assertEquals("opis-table", bd.caption); assertEquals(true, bd.interactive)

        val e = (d.blocks[8] as LessonBlock.Exercise).spec
        assertEquals("v-naslov", e.title); assertEquals("v-hint", e.hint); assertEquals("v-ikona", e.icon)
        assertEquals(listOf("e2e4", "e7e5"), e.uciMoves)
        assertEquals("8/8/8/8/8/8/8/8 w - - 0 1", e.startFEN)
        assertEquals("v-resen", e.solvedMessage); assertEquals("v-pogresan", e.wrongMessage)
        assertEquals("v-prompt", e.playingPrompt); assertEquals(3, e.mateIn)
    }

    @Test
    fun kingValueStaysAStringBecauseItIsInfinity() {
        // Da je `value` Int, kralj bi pukao pri parsiranju i cela lekcija 3 bi
        // nestala — a kralj je jedini red koji to obara.
        val d = LessonParser.parse(doc(
            """{"type":"pieceValueTable","rows":[{"piece":"king","name":"Kralj","value":"∞"}]}"""))
        val t = d.blocks[0] as LessonBlock.PieceValueTable
        assertEquals("∞", t.rows[0].value)
    }

    @Test
    fun boxStyleMapsAllThreeValues() {
        for ((raw, expected) in listOf("rule" to BoxStyle.RULE,
                                       "warning" to BoxStyle.WARNING,
                                       "info" to BoxStyle.INFO)) {
            val d = LessonParser.parse(doc(
                """{"type":"box","style":"$raw","icon":"i","title":"T","text":"X"}"""))
            assertEquals(expected, (d.blocks[0] as LessonBlock.Box).style)
        }
    }

    @Test
    fun exerciseKeepsOptionalFieldsAsNullWhenAbsent() {
        val d = LessonParser.parse(doc(
            """{"type":"exercise","kind":"vsEngine","title":"T","hint":"H","icon":"i","startFEN":"8/8/8/8/8/8/8/8 w - - 0 1"}"""))
        val e = (d.blocks[0] as LessonBlock.Exercise).spec
        assertEquals(ExerciseKind.VS_ENGINE, e.kind)
        assertEquals(null, e.uciMoves)
        assertEquals(null, e.mateIn)
        assertEquals(null, e.solvedMessage)
    }

    @Test
    fun unknownBlockTypeThrowsInsteadOfBeingSkipped() {
        // Tih preskok bi dao lekciju sa rupom koju niko ne primeti. Sadrzaj je
        // od ove faze van dometa kompajlera, pa je ovo jedino mesto koje moze
        // da vikne.
        try {
            LessonParser.parse(doc("""{"type":"teleporter","text":"X"}"""))
            fail("nepoznat tip bloka mora da baci")
        } catch (e: IllegalArgumentException) {
            assertTrue("poruka mora da imenuje tip: ${e.message}",
                       e.message!!.contains("teleporter"))
        }
    }

    @Test
    fun unknownBoxStyleThrows() {
        try {
            LessonParser.parse(doc("""{"type":"box","style":"neon","icon":"i","title":"T","text":"X"}"""))
            fail("nepoznat stil kutije mora da baci")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("neon"))
        }
    }

    @Test
    fun bulletStyleIsOptional() {
        val d = LessonParser.parse(doc(
            """{"type":"bullets","items":[{"icon":"a","title":"b","text":"c"},{"icon":"d","title":"e","text":"f","style":"warning"}]}"""))
        val b = d.blocks[0] as LessonBlock.Bullets
        assertEquals(null, b.items[0].style)
        assertEquals(BoxStyle.WARNING, b.items[1].style)
    }
}
