package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.StockfishEngine
import android.util.Log
import com.veljkoni.chessko.models.EngineScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
// Istorija izvrsavanja (raniji tekst je tvrdio da se test "NIJE izvrsavao" i da
// se emulator die dvaput -- oboje je zastarelo): Task 3 ga je samo kompajlirao
// (`./gradlew assembleDebugAndroidTest`), Task 6 ga je prvi put stvarno pokrenuo
// i time otkrio bug u `StockfishEngine.waitUntilReady()`, a talas ispravki pred
// spajanje ga je pokrenuo jos jednom. Emulator je u fazi dizan CETIRI puta, ne
// dvaput (Task 2, Task 6, popravka `waitUntilReady`, talas ispravki).
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
     * Ceka STVARNU spremnost motora preko `StockfishEngine.waitUntilReady()`
     * (zastavica koju postavlja `listenOutput` kad vidi "readyok"), ne
     * `engineStarted`.
     *
     * `engineStarted` postaje `true` SINHRONO u `start()`, pre nego sto
     * kopiranje NNUE fajlova (do ~140MB) i UCI handshake uopste pocnu (idu u
     * pozadinskoj korutini) -- poll petlja nad `engineStarted` je zato u
     * praksi bez efekta (zavrsi se za ~0ms), sto je i bio nalaz pregleda nad
     * prvom verzijom ovog fajla: komentar je tvrdio da "ceka", a stvarno
     * cekanje je bila samo gola pauza posle petlje. Popravka nije duza pauza
     * nego stvarna provera.
     */
    private suspend fun waitForEngineReady() {
        val ready = StockfishEngine.waitUntilReady()
        assertTrue("motor nije javio readyok na vreme", ready)
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

    /**
     * Otkazana duboka pretraga ne sme da pokvari SLEDECU.
     *
     * Ovo je regresija za nalaz pregleda pred spajanje: "stop" pri otkazivanju
     * (Task 4) zaustavlja motor, ali time sam izaziva `bestmove` liniju
     * (`search.cpp:266-267`). Bez [StockfishEngine] koji tu liniju pojede jos
     * pod svojim `searchMutex`-om, ona sleti u kanal SLEDECE pretrage i bude
     * procitana kao njen odgovor -- ovde konkretno: `evaluate` vidi `bestmove`
     * pre ijedne `info ... score` linije, nema ocenu i vrati `null`, pa cela
     * analiza zavrsi na "Analiza nije uspela.".
     *
     * Bez popravke test pada na `assertNotNull` ispod; sa popravkom prolazi i
     * usput meri koliko traje put "stop" -> procitan `bestmove` (broj iz tog
     * merenja obrazlaze `STOP_DRAIN_TIMEOUT_MS`).
     */
    @Test
    fun cancelledDeepSearchDoesNotPoisonTheNextEvaluation() = runBlocking {
        waitForEngineReady()
        val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        // `depth 30` na ovom emulatoru traje minutima (izmereno u Task-u 2:
        // depth 20 ~29 s, depth 22 ~43,8 s), pa je pretraga sigurno JOS U TOKU
        // kad je otkazemo -- inace bi test merio praznu sobu.
        val job = launch(Dispatchers.Default) {
            StockfishEngine.getBestMove(startFen, depth = 30)
        }
        delay(1_500)
        val stopMs = kotlin.system.measureTimeMillis { job.cancelAndJoin() }
        Log.d("StockfishEvaluateTest", "stop -> bestmove procitan za ${stopMs}ms")

        val result = StockfishEngine.evaluate(startFen, depth = 12)
        assertNotNull(
            "evaluate posle otkazane pretrage je vratio null -- zakasneli bestmove je upao u kanal",
            result
        )
        val score = result!!.score
        assertTrue("ocekivan Cp, dobijeno $score", score is EngineScore.Cp)
        assertTrue(
            "pocetna pozicija predaleko od nule: $score",
            kotlin.math.abs((score as EngineScore.Cp).value) < 100
        )
        assertTrue(
            "zaustavljanje pretrage je trajalo ${stopMs}ms -- duze od STOP_DRAIN_TIMEOUT_MS (2000)",
            stopMs < 2_000
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
        // linija": crni kralj na g8 u sahu bez ijednog legalnog poteza
        // (blokada nemoguca, kralj nema polje, dama se ne moze uzeti).
        val fen = "3Q2k1/5ppp/8/8/8/8/8/4R1K1 b - - 1 1"
        val result = StockfishEngine.evaluate(fen, depth = 12)
        assertNotNull("terminalna pozicija ne sme da vrati null", result)
        assertNull("terminalna pozicija ne sme da nosi predlog poteza", result!!.bestMove)
        assertTrue(
            "terminalna pozicija sa sahom mora biti Mate(0): ${result.score}",
            result.score == EngineScore.Mate(0)
        )
    }
}
