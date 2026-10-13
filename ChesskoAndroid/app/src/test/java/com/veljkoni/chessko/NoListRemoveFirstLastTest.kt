package com.veljkoni.chessko

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Google Play je odbio izdanje 2.1 (4): Kotlin `removeFirst()`/`removeLast()` se sa
 * `compileSdk` 35+ razrešavaju na Java 21 `List.removeFirst()`/`removeLast()`, koje na
 * Androidu 14 i starijem NE POSTOJE — `NoSuchMethodError` pri pozivu (bilo je u
 * `GameViewModel.undo`). Zamena je `removeAt(0)` / `removeAt(lastIndex)`.
 * `removeFirstOrNull()`/`removeLastOrNull()` su čisto Kotlin funkcije i dozvoljene su.
 *
 * Provera izvora, isti obrazac kao `MainActivitySoundWiringTest`.
 */
class NoListRemoveFirstLastTest {

    @Test
    fun sourceNeverCallsRemoveFirstOrRemoveLast() {
        val root = File("src/main/java")
        assertTrue("izvor nije nađen na ${root.absolutePath}", root.isDirectory)
        val call = Regex("""\.remove(First|Last)\s*\(""")
        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    if (call.containsMatchIn(line)) "${f.path}:${i + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue(
            "removeFirst()/removeLast() ruše aplikaciju na Androidu 14 i starijem — " +
                "koristi removeAt(0) / removeAt(lastIndex):\n" + hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }
}
