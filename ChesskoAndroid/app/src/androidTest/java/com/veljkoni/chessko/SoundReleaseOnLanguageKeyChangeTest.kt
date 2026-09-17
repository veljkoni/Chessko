package com.veljkoni.chessko

import android.app.Application
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.SoundManager
import com.veljkoni.chessko.viewmodels.GameViewModel
import com.veljkoni.chessko.viewmodels.PuzzleViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Faza 7, Task 2: dokaz da OBRAZAC `key(languageKey) { remember{…}; DisposableEffect{…} }`
 * stvarno oslobadja `SoundManager` oba modela (Igra, Zadaci) kad se kljuc promeni.
 *
 * **Sta ovaj test NE dokazuje (S-2, talas ispravki):** on ne dodiruje `MainActivity`.
 * Obrazac je rekonstruisan u sopstvenom `setContent`-u, pa bi test prosao i da neko
 * obrise `DisposableEffect` iz `MainActivity.kt`. Zato je preimenovan iz
 * `MainActivitySoundLifecycleTest` — staro ime je obecavalo zastitu koju ne pruza.
 * Da `MainActivity` i dalje nosi tu vezu cuva `MainActivitySoundWiringTest`
 * (JVM, provera izvora); tek to dvoje zajedno pokrivaju i mehanizam i njegovu upotrebu.
 *
 * Zasto ne mount-uje celu `MainActivity`: modeli se tamo prave kroz `remember { … }`
 * unutar kompozicije, ne kroz `ViewModelStore`, pa do njih iz testa nema puta — ni
 * refleksijom ni test tagom; uz to hamburger meni i `SettingsView` nemaju stabilne test
 * tagove za navigaciju do promene jezika. Zato reprodukuje BUKVALNO isti obrazac koji
 * `MainActivity.kt` koristi (uporedi sa `MainActivity.kt:99-115`):
 *
 *   key(languageKey) {
 *       val gameViewModel = remember { GameViewModel(...) }
 *       val puzzleViewModel = remember { PuzzleViewModel(...) }
 *       DisposableEffect(languageKey) {
 *           onDispose { gameViewModel.releaseSounds(); puzzleViewModel.releaseSounds() }
 *       }
 *   }
 *
 * sa PRAVIM `GameViewModel`/`PuzzleViewModel` (ne dvojnicima) -- nema mock biblioteke u
 * projektu (nijedna nova Gradle zavisnost dozvoljena), pa se do privatnog `soundManager`
 * polja dolazi refleksijom. Dokaz da je `SoundPool` STVARNO oslobodjen (ne samo da je
 * `release()` pozvan) je `SoundManager.isReleased`, koje cita `soundPool == null` --
 * ista provera koju `SoundManager.release()` sam postavlja.
 */
@RunWith(AndroidJUnit4::class)
class SoundReleaseOnLanguageKeyChangeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun soundManagerOf(viewModel: Any): SoundManager {
        val field = viewModel.javaClass.getDeclaredField("soundManager")
        field.isAccessible = true
        return field.get(viewModel) as SoundManager
    }

    @Test
    fun changingLanguageKeyReleasesOldSoundManagers() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application

        var gameVm: GameViewModel? = null
        var puzzleVm: PuzzleViewModel? = null
        lateinit var changeKey: (String) -> Unit

        composeRule.setContent {
            var languageKey by remember { mutableStateOf("sr") }
            changeKey = { languageKey = it }

            key(languageKey) {
                val g = remember { GameViewModel(app) }
                val p = remember { PuzzleViewModel(app) }
                gameVm = g
                puzzleVm = p

                // Ista zastita kao u MainActivity.kt: oslobodi TACNO one primerke
                // koje je ovaj opseg napravio, kad `languageKey` promeni vrednost.
                DisposableEffect(languageKey) {
                    onDispose {
                        g.releaseSounds()
                        p.releaseSounds()
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val firstGame = requireNotNull(gameVm)
        val firstPuzzle = requireNotNull(puzzleVm)
        val firstGameSound = soundManagerOf(firstGame)
        val firstPuzzleSound = soundManagerOf(firstPuzzle)

        assertFalse("SoundManager ne sme biti vec oslobodjen pre promene jezika", firstGameSound.isReleased)
        assertFalse("SoundManager ne sme biti vec oslobodjen pre promene jezika", firstPuzzleSound.isReleased)

        // Simulira ono sto `SettingsView`-ov `onLanguageChanged` radi u pravoj aplikaciji:
        // `languageKey` dobija novu vrednost, `key(...)` odbacuje ceo blok i pravi NOVE modele.
        changeKey("en")
        composeRule.waitForIdle()

        val secondGame = requireNotNull(gameVm)
        val secondPuzzle = requireNotNull(puzzleVm)
        assertNotSame("key(languageKey) mora praviti NOV GameViewModel", firstGame, secondGame)
        assertNotSame("key(languageKey) mora praviti NOV PuzzleViewModel", firstPuzzle, secondPuzzle)

        assertTrue("Stari GameViewModel-ov SoundManager mora biti oslobodjen posle promene jezika", firstGameSound.isReleased)
        assertTrue("Stari PuzzleViewModel-ov SoundManager mora biti oslobodjen posle promene jezika", firstPuzzleSound.isReleased)

        // Novi primerci moraju ostati upotrebljivi -- popravka ne sme osloboditi model
        // koji je ekran jos koristi.
        assertFalse("Novi GameViewModel-ov SoundManager ne sme biti oslobodjen", soundManagerOf(secondGame).isReleased)
        assertFalse("Novi PuzzleViewModel-ov SoundManager ne sme biti oslobodjen", soundManagerOf(secondPuzzle).isReleased)
    }
}
