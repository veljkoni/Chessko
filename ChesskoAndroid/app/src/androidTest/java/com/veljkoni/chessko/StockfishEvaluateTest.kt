package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.StockfishEngine
import com.veljkoni.chessko.models.EngineScore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

// `evaluate` trazi ziv motor -- isti razlog zbog kog `CurriculumTest` i
// `LessonContentTest` vec zive u `androidTest`, ne u `test`.
//
// VAZNO za Task 6 (ili bilo koga ko ovo pokrene): ovaj test se NIJE izvrsavao
// kao deo Taska 3 -- samo je potvrdjeno da se kompajlira
// (`./gradlew assembleDebugAndroidTest`). Instrumentisano izvrsavanje je
// eksplicitno ostavljeno za Task 6 (plan die-a emulator samo dvaput u ovoj
// fazi: Task 2, vec obavljeno, i Task 6, zavrsna provera).
@RunWith(AndroidJUnit4::class)
class StockfishEvaluateTest {

    companion object {
        // Isti fajlovi i isti poziv kao `MainActivity.onCreate` -- motor se
        // pokrece TACNO jednom za ceo proces instrumentacije (`StockfishEngine`
        // je `object`, singleton), pa `@BeforeClass` umesto `@Before`.
        @BeforeClass
        @JvmStatic
        fun startEngine() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            StockfishEngine.start(context, "nn-1c0000000000.nnue", "nn-37f18f62d772.nnue")
        }
    }

    /**
     * `StockfishEngine.start` kopira dve NNUE mreze (do ~140MB ukupno) na
     * disk PA TEK ONDA salje `uci`/`setoption`/`isready` -- sve to na
     * pozadinskim korutinama, bez ijedne sinhrone tacke koju testu moze da
     * saceka. Fiksna pauza je jedini alat koji `StockfishEngine` danas nudi;
     * 5s je pet puta duze od bilo kog merenja u izvestajima Task-a 1/2 na
     * istom emulatoru.
     */
    private suspend fun waitForEngineReady() {
        var waited = 0
        while (!StockfishEngine.engineStarted && waited < 5000) {
            delay(100)
            waited += 100
        }
        assertTrue("motor se nije pokrenuo", StockfishEngine.engineStarted)
        delay(5000)
    }

    @Test
    fun startingPositionEvaluatesCloseToZero() = runBlocking {
        waitForEngineReady()
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val result = StockfishEngine.evaluate(fen, depth = 12)
        assertNotNull("evaluate je vratio null za pocetnu poziciju", result)
        val score = result!!.score
        assertTrue("ocekivan Cp, dobijeno $score", score is EngineScore.Cp)
        assertTrue(
            "pocetna pozicija predaleko od nule: $score",
            kotlin.math.abs((score as EngineScore.Cp).value) < 100
        )
    }

    @Test
    fun mateInOneGivesPositiveMate() = runBlocking {
        waitForEngineReady()
        // Isti zadatak kao "Mat u 1 -- Dama zadnja linija" iz Lekcije 4
        // (verifikovano python-chess-om, vidi CLAUDE.md): belom na potezu,
        // Dd2-d8 daje mat.
        val fen = "6k1/5ppp/8/8/8/8/3Q4/4R1K1 w - - 0 1"
        val result = StockfishEngine.evaluate(fen, depth = 12)
        assertNotNull("evaluate je vratio null za mat u 1", result)
        val score = result!!.score
        assertTrue("ocekivan Mate, dobijeno $score", score is EngineScore.Mate)
        assertTrue(
            "mat u 1 za stranu na potezu mora biti pozitivan: $score",
            (score as EngineScore.Mate).n > 0
        )
    }

    @Test
    fun terminalPositionReturnsMateZeroWithoutAskingEngine() = runBlocking {
        // NAMERNO se ne zove `waitForEngineReady()` -- ova provera je bas o
        // tome da terminalna pozicija ne dodiruje motor uopste, pa ne treba
        // ni da mu se sacekaju NNUE/isready. Ako bi implementacija ikad
        // pokusala da pita motor, dobar deo poziva bi visio ili pao pre nego
        // sto stigne do asserta ispod.
        //
        // Pozicija je posledica poteza Dd2-d8# iz "Mat u 1 -- Dama zadnja
        // linija": crni kralj na g8 u satu bez ijednog legalnog poteza
        // (blokada nemoguca, kralj nema polje, dama se ne moze uzeti).
        val fen = "3Q2k1/5ppp/8/8/8/8/8/4R1K1 b - - 1 1"
        val result = StockfishEngine.evaluate(fen, depth = 12)
        assertNotNull("terminalna pozicija ne sme da vrati null", result)
        assertNull("terminalna pozicija ne sme da nosi predlog poteza", result!!.bestMove)
        assertTrue(
            "terminalna pozicija sa satom mora biti Mate(0): ${result.score}",
            result.score == EngineScore.Mate(0)
        )
    }
}
