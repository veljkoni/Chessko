package com.veljkoni.chessko

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Nijedan ekran ne sme sam da bira velicinu fonta — to radi Type.*.
 *
 * Izuzeci su imenovani pojedinacno, sa razlogom, i **moraju ostati kratki**:
 * spisak koji raste je znak da skala ne opisuje ekrane, a to je presuda
 * Task-a 2, ne stvar koju resava dopisivanje izuzetka.
 */
class TypographyScaleTest {

    /**
     * Jedan izuzetak, i to po VREDNOSTI a ne po fajlu: cifre sata (`72.sp`) nisu tekst
     * nego prikaz vremena i imaju sopstveni registar — iOS ih drzi na 86pt iz istog
     * razloga. Ostali tekst u `ChessClockView.kt` (14/13/16/11) JESTE hrom i tokenizuje
     * se normalno, pa izuzimanje celog fajla ne bi bilo tacno.
     */
    private val allowed = setOf("ChessClockView.kt:fontSize = 72.sp")

    @Test
    fun noScreenHardcodesFontSize() {
        val root = File("src/main/java/com/veljkoni/chessko")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("/theme/") }
            .flatMap { f ->
                // Komentar koji pominje fontSize nije zakucan fontSize — skini ih prvo.
                val src = f.readText()
                    .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                    .lines().joinToString("\n") { it.substringBefore("//") }
                Regex("""fontSize\s*=\s*\d+\.sp""").findAll(src)
                    .map { "${f.name}:${it.value}" }
            }
            .filterNot { it in allowed }
            .toList()

        assertEquals("zakucan fontSize van Type.*: $offenders", emptyList<String>(), offenders)
    }
}
