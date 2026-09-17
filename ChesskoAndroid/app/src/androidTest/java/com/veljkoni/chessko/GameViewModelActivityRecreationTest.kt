package com.veljkoni.chessko

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.models.GameStatus
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.models.Position
import com.veljkoni.chessko.ui.canAnalyzeGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Faza 7, Task 4: dokaz da zavrsena partija (ovde: predaja) prezivljava
 * rekreaciju `Activity`-ja -- rotaciju ili promenu sistemske teme.
 *
 * Zasto ovo dokazuje bas TO: `MainActivity.kt` ne pravi `gameViewModel` kroz
 * `ViewModelStore` (koji bi ga sam preziveo), nego kroz `remember { GameViewModel(...) }`
 * UNUTAR `setContent` (vidi `MainActivitySoundLifecycleTest.kt`, isti nalaz
 * dokumentovan tamo za drugi bug). Rekreacija `Activity`-ja ponovo izvrsava
 * `onCreate`/`setContent`, sto pravi SASVIM NOV `GameViewModel` primerak cija
 * `init { load() }` cita partiju iskljucivo sa diska. Ovaj test zato ne mount-uje
 * UI (nema stabilnih test tagova za "Predaj se" u meniju, isti razlog kao u
 * pomenutom testu) nego reprodukuje TACNO taj mehanizam: napravi PRVI
 * `GameViewModel`, odigra jedan potez i preda partiju (`resign()`, koji interno
 * zove `save()`), pa napravi DRUGI, SASVIM NOV primerak sa ISTIM `saveKey`-jem
 * -- ekvivalent onome sto rekreacija `Activity`-ja stvarno radi.
 *
 * Zasto bas predaja, ne mat: `resign()` je deterministican (mat bi trazio
 * odigravanje cele partije do kraja), a brief eksplicitno navodi predaju kao
 * jedan od tri prijavljena puta u bug reportu ("posle mata, predaje ili remija").
 *
 * `GameMode.LOCAL_FRIEND` (ne `VS_COMPUTER`) namerno -- iskljucuje AI potez
 * (`triggerAI()` odmah izlazi van `VS_COMPUTER`), pa test ne zavisi od
 * asinhronog `viewModelScope.launch { delay(300) ... }`.
 *
 * Sopstveni `saveKey` (ne `FREE_PLAY_SAVE_KEY`) -- ne sme da dirne
 * stvarnu slobodnu partiju ili partiju nekog `game` koraka na istom uredjaju/
 * emulatoru (vidi "Zamka" u CLAUDE.md o parametrizovanom kljucu).
 *
 * NAPOMENA: ovaj test je u ovom prolazu SAMO KOMPAJLIRAN, NIJE IZVRSEN --
 * izvrsavanje trazi emulator, koji je rezervisan za Task 5 cele faze (vidi
 * brief). Niko ga ovde nije pokrenuo na uredjaju/emulatoru.
 */
@RunWith(AndroidJUnit4::class)
class GameViewModelActivityRecreationTest {

    private val testSaveKey = "test.faza7.task4.activityRecreation"

    private fun clearTestSave(context: Context) {
        context.getSharedPreferences("chessko_save", Context.MODE_PRIVATE)
            .edit()
            .remove(testSaveKey)
            .apply()
    }

    @Test
    fun resignedGameSurvivesFreshViewModelInstance() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        clearTestSave(app)

        try {
            // "Prva Activity" -- partija u toku, pa predaja.
            val first = com.veljkoni.chessko.viewmodels.GameViewModel(app, testSaveKey)
            first.newGame(com.veljkoni.chessko.viewmodels.GameMode.LOCAL_FRIEND, PieceColor.WHITE)
            first.tap(Position(6, 4)) // selektuj belog piona e2
            first.tap(Position(4, 4)) // e2-e4 -- history/moveNotations vise nisu prazni
            assertTrue("partija mora imati bar jedan potez pre predaje", first.gameState.moveNotations.isNotEmpty())

            first.resign()
            assertTrue("resign() mora zavrsiti partiju", first.isGameOver)
            assertTrue(
                "dugme 'Analiziraj partiju' mora biti vidljivo ODMAH posle predaje",
                canAnalyzeGame(first)
            )

            // "Rekreacija Activity-ja" -- sasvim NOV primerak, isti saveKey,
            // ucitava iskljucivo sa diska (isto sto MainActivity.onCreate radi
            // posle rotacije/promene teme).
            val second = com.veljkoni.chessko.viewmodels.GameViewModel(app, testSaveKey)

            assertEquals(
                "ucitan status mora biti TACNO ono sto je sacuvano, ne GameStatus.Playing",
                GameStatus.Resigned(PieceColor.BLACK),
                second.gameState.status
            )
            assertTrue("ucitana partija mora ostati oznacena kao zavrsena", second.isGameOver)
            assertTrue(
                "dugme 'Analiziraj partiju' NE SME nestati posle rekreacije Activity-ja -- ovo je bag iz Faze 6e",
                canAnalyzeGame(second)
            )
        } finally {
            clearTestSave(app)
        }
    }
}
