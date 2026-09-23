package com.veljkoni.chessko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Faza 10, Task 4: dokaz da razmak i radijus koji PADAJU NA SKALU stoje kao
 * token, a ne kao broj.
 *
 * ## Zasto ovaj test postoji iako je task tvrdio da ga ne moze biti
 *
 * Presuda iznad `DS.Space` je isprva pisala da test nije moguc, jer bi test
 * koji zabranjuje `.dp` literal zabranio i ivice i touch-targete. To je tacno
 * — ali opisuje SIRI test od onog koji cuva ono sto je uradjeno. Invarijanta
 * nije „nema `.dp`" nego:
 *
 *     on-scale vrednost u OBLIKU POZIVA razmaka mora biti token
 *
 * i taj oblik je uzak. `.border(1.dp, …)` i `size(48.dp)` mu **ne odgovaraju
 * uopste**, pa im izuzetak nije ni potreban — nisu izuzeti, nego nisu ni
 * obuhvaceni. To je razlika izmedju testa koji ima spisak izuzetaka (i koji
 * prvi izuzetak cini beskorisnim) i testa koji ima uzak domen.
 *
 * ## Zasto SINTAKSNI, a ne po ulozi
 *
 * Task 4 je razvrstavao literale po ULOZI (razmak / radijus / dimenzija /
 * ivica), i to je bilo ispravno za JEDNOKRATNO merenje. Za test je pogresno:
 * klasifikator uloge je heuristika, a heuristika u testu sutra daje lazan pad
 * na kodu koji nema nikakve veze sa skalom. Zato se ovde poklapaju samo
 * **oblici poziva**, koji su jednoznacni:
 *
 *   `padding(...)`, `PaddingValues(...)`, `spacedBy(...)`  — razmak
 *   `Spacer(...)`                                          — razmak kroz visinu/sirinu
 *   `RoundedCornerShape(...)`                              — radijus
 *
 * Ako neko sutra napise razmak u obliku koji ovde nije naveden, test ce cutati.
 * To je svesna cena uskog domena: bolje test koji hvata cestu gresku i nikad
 * ne laze, nego test koji hvata sve i povremeno laze.
 *
 * ## Sta cuva
 *
 * Vizuelni prolaz Task-a 5 moze da potvrdi da danas nista nije pomereno. Ne
 * moze da spreci da sutra neko napise `padding(12.dp)` pored `DS.Space.m` i
 * tiho razvodni skalu. Ovo je jedina automatska odbrana tog taska.
 *
 * Isti obrazac citanja izvora koji projekat vec nosi tri puta:
 * `MainActivitySoundWiringTest`, `LessonBoardsOptOutOfSwipeTest`,
 * `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`.
 *
 * ## Dokazan mutacijom
 *
 * Sa `MainActivity.kt` vracenim na `d844b16` (stanje pre Task-a 4) test pada i
 * ispise **62** mesta; sa danasnjim stablom prolazi sa 0. Taj fajl je u Task-u
 * 4 imao **63** zamene — razlika je jedna jedina linija (`:1114`,
 * `padding(start = …, bottom = …)`) koja nosi ISTI broj dvaput, a prijava je
 * `Set` po (fajl, linija, oblik, vrednost). Test, dakle, broji **mesta**, ne
 * literale; za poruku o gresci je to i korisnije.
 */
class SpacingTokensTest {

    /** `DS.Space`: 4 / 8 / 12 / 16 / 24. */
    private val spaceScale = setOf("4", "8", "12", "16", "24")

    /** `DS.Radius`: 8 / 12 / 16. */
    private val radiusScale = setOf("8", "12", "16")

    /**
     * Tabla je sadrzaj, ne hrom: `BoardView.kt` i `BoardTheme.kt` su izvan
     * dometa cele Faze 10 i moraju imati NULA `DS.` pogodaka. Da su u domenu,
     * test bi jednog dana trazio token bas tamo gde ga drugo pravilo zabranjuje.
     * `theme/` je izuzet jer su tamo same definicije ovih tokena.
     */
    private val outOfScope = listOf("ui/BoardView.kt", "ui/BoardTheme.kt", "ui/theme/")

    private fun withoutComments(src: String): String =
        src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private fun sources(): List<File> {
        val root = File("src/main/java/com/veljkoni/chessko")
        assertTrue("izvor nije nadjen na ${root.absolutePath}", root.isDirectory)
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f -> outOfScope.none { f.invariantSeparatorsPath.contains(it) } }
            .sortedBy { it.path }
            .toList()
    }

    /**
     * Tekst argumenata svakog poziva `callee(...)`, sa indeksom pocetka.
     * Zagrade se broje, pa ugnjezden poziv ne prekida argument prerano, a
     * ono sto dolazi POSLE zatvorene zagrade nije uhvaceno — zato
     * `Modifier.padding(4.dp).size(16.dp)` prijavljuje samo prvi broj.
     */
    private fun callArguments(src: String, callee: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var from = 0
        while (true) {
            val at = src.indexOf("$callee(", from)
            if (at < 0) return out
            from = at + callee.length
            // Granica reci: `absolutePadding(` ne sme da se poklopi sa `padding(`.
            val before = if (at == 0) ' ' else src[at - 1]
            if (before.isLetterOrDigit() || before == '_') continue
            var depth = 0
            var i = at + callee.length
            val start = i + 1
            while (i < src.length) {
                when (src[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                i++
            }
            if (i < src.length) out.add(at to src.substring(start, i))
        }
    }

    private fun lineOf(src: String, index: Int): Int =
        src.substring(0, index).count { it == '\n' } + 1

    private val literal = Regex("(\\d+(?:\\.\\d+)?)\\.dp\\b")

    @Test
    fun onScaleSpacingAndRadiusValuesAreWrittenAsTokens() {
        // oblik poziva -> skala koju taj oblik mora da postuje
        val shapes = listOf(
            "padding" to spaceScale,
            "PaddingValues" to spaceScale,
            "spacedBy" to spaceScale,
            "Spacer" to spaceScale,
            "RoundedCornerShape" to radiusScale
        )

        val violations = sortedSetOf<String>()
        var scanned = 0

        for (file in sources()) {
            val src = withoutComments(file.readText())
            scanned++
            for ((callee, scale) in shapes) {
                for ((at, args) in callArguments(src, callee)) {
                    for (m in literal.findAll(args)) {
                        val value = m.groupValues[1]
                        if (value !in scale) continue
                        val token = if (scale === spaceScale) {
                            "DS.Space." + mapOf(
                                "4" to "xs", "8" to "s", "12" to "m", "16" to "l", "24" to "xl"
                            )[value]
                        } else {
                            "DS.Radius." + mapOf("8" to "s", "12" to "m", "16" to "l")[value]
                        }
                        violations.add(
                            "${file.name}:${lineOf(src, at)}  $callee(… $value.dp …)  ->  $token"
                        )
                    }
                }
            }
        }

        assertTrue("nijedan izvorni fajl nije procitan", scanned > 10)
        assertEquals(
            "Vrednost koja vec pada na skalu zapisana je kao broj, ne kao token — " +
                "skala se time tiho razvodnjava. Zameni tokenom (ista vrednost, nijedan " +
                "piksel razlike). Ako broj tu NE sme biti token, onda nije u ulozi razmaka " +
                "ni radijusa, pa ne bi ni bio u ovom obliku poziva — proveri poziv, ne test." +
                "\n\n" + violations.joinToString("\n"),
            emptyList<String>(), violations.toList()
        )
    }
}
