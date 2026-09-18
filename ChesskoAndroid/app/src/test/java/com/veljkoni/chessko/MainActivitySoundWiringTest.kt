package com.veljkoni.chessko

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Faza 7, talas ispravki (S-2): dokaz da `MainActivity.kt` STVARNO nosi
 * `DisposableEffect(languageKey)` koji oslobadja oba `SoundManager`-a.
 *
 * Zasto ovakav test uopste postoji: `SoundReleaseOnLanguageKeyChangeTest`
 * (instrumentisani) dokazuje da OBRAZAC radi, ali ga rekonstruise u sopstvenom
 * `setContent`-u — pa bi prosao i da neko obrise ceo `DisposableEffect` iz
 * `MainActivity.kt`. Modeli se tamo prave kroz `remember { … }` unutar
 * kompozicije, ne kroz `ViewModelStore`, pa se do njih iz testa ne moze doci
 * ni refleksijom ni test tagom; jedino sto se moze proveriti nad PRAVIM
 * fajlom je da je veza i dalje tu.
 *
 * Isti obrazac kao `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`:
 * JVM test cita izvor iz radnog direktorijuma modula.
 *
 * Ovo je namerno provera IZVORA, ne ponasanja — i tako se i zove. Ako se
 * `MainActivity` ikad prepise tako da se modeli drze kroz `ViewModelStore`,
 * ovaj test treba zameniti pravim testom ponasanja, ne olabaviti.
 */
class MainActivitySoundWiringTest {

    @Test
    fun mainActivityStillReleasesBothSoundManagersOnLanguageChange() {
        val file = File("src/main/java/com/veljkoni/chessko/MainActivity.kt")
        assertTrue("izvor nije nadjen na ${file.absolutePath}", file.isFile)
        val src = file.readText()

        val keyIdx = src.indexOf("key(languageKey)")
        assertTrue("`key(languageKey)` blok vise ne postoji u MainActivity.kt", keyIdx >= 0)

        val effectIdx = src.indexOf("DisposableEffect(languageKey)", startIndex = keyIdx)
        assertTrue(
            "`DisposableEffect(languageKey)` unutar `key(languageKey)` bloka ne postoji — " +
                "`SoundPool` oba modela ponovo curi pri svakoj promeni jezika",
            effectIdx >= 0
        )

        // Telo efekta: dovoljno da se vidi `onDispose` i oba oslobadjanja.
        val body = src.substring(effectIdx, minOf(effectIdx + 400, src.length))
        for (ocekivano in listOf(
            "onDispose",
            "gameViewModel.releaseSounds()",
            "puzzleViewModel.releaseSounds()"
        )) {
            assertTrue(
                "`DisposableEffect(languageKey)` u MainActivity.kt vise ne sadrzi `$ocekivano`",
                body.contains(ocekivano)
            )
        }
    }
}
