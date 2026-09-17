package com.veljkoni.chessko

import com.veljkoni.chessko.models.ChessMove
import com.veljkoni.chessko.models.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Task 5, Faza 7: `GameState.fen` je do sada pisao polutez (5. polje FEN-a)
 * kao zakucanu nulu. To nije bio kozmeticki propust — taj isti FEN ide:
 *
 *  - Stockfish-u na SVAKI AI potez (`GameViewModel`, `getBestMove`),
 *  - Stockfish-u na svaku poziciju u analizi (`AnalysisViewModel.evaluate`),
 *  - u sacuvanu partiju (`GameViewModel.save()`), odakle ga `fromFEN` cita
 *    nazad — pa se brojac pravila 50 poteza tiho resetovao pri svakom
 *    ponovnom otvaranju aplikacije.
 *
 * iOS isto polje salje tacno od Faze 0 (`Chessko/Models/GameState+FEN.swift`).
 *
 * `GameState` nema nijednu `android.*` zavisnost, pa se ovo dokazuje JVM
 * testom, bez emulatora.
 */
class GameStateFenHalfmoveTest {

    private fun halfmoveField(fen: String): String = fen.split(" ")[4]

    @Test
    fun initialPositionHasZeroHalfmove() {
        assertEquals("0", halfmoveField(GameState.initial().fen))
    }

    /**
     * Sredisnji test: potez koji NIJE ni pionski ni uzimanje uvecava brojac,
     * i FEN to mora da pokaze. Sa zakucanom nulom ovaj test pada.
     * 1.Sf3 Sf6 2.Sc3 — tri "tiha" poteza, brojac 3.
     */
    @Test
    fun quietMovesAreReflectedInTheFenHalfmoveField() {
        var state = GameState.initial()
        for (uci in listOf("g1f3", "g8f6", "b1c3")) {
            val move = ChessMove.fromUCI(uci, state)
            assertNotNull("potez $uci mora biti legalan", move)
            state = state.applying(move!!)
        }
        assertEquals(3, state.halfmoveClock)
        assertEquals("3", halfmoveField(state.fen))
    }

    /** Pionski potez vraca brojac na nulu — i to FEN mora da pokaze. */
    @Test
    fun pawnMoveResetsTheFenHalfmoveField() {
        var state = GameState.initial()
        for (uci in listOf("g1f3", "g8f6")) {
            state = state.applying(ChessMove.fromUCI(uci, state)!!)
        }
        assertEquals("2", halfmoveField(state.fen))

        state = state.applying(ChessMove.fromUCI("e2e4", state)!!)
        assertEquals(0, state.halfmoveClock)
        assertEquals("0", halfmoveField(state.fen))
    }

    /**
     * Round-trip: ono sto `fen` napise, `fromFEN` mora da procita nazad.
     * Bas ovaj put (`save()` -> `load()`) je gubio brojac.
     */
    @Test
    fun halfmoveSurvivesFenRoundTrip() {
        var state = GameState.initial()
        for (uci in listOf("g1f3", "g8f6", "b1c3", "b8c6", "f3g1")) {
            state = state.applying(ChessMove.fromUCI(uci, state)!!)
        }
        assertEquals(5, state.halfmoveClock)

        val reloaded = GameState.fromFEN(state.fen)
        assertNotNull(reloaded)
        assertEquals(state.halfmoveClock, reloaded!!.halfmoveClock)
        assertEquals(state.fen, reloaded.fen)
    }

    /**
     * Visoka vrednost, bez odigravanja 99 poteza: poziciju ucitanu sa brojacem
     * blizu granice FEN mora da opise tacno, jer bas tu Stockfish (i pravilo 50
     * poteza pri nastavku sacuvane partije) treba da zna koliko je blizu remija.
     */
    @Test
    fun highHalfmoveIsPreservedExactly() {
        val fen = "4k3/8/8/8/8/8/8/4K2R w K - 98 60"
        val state = GameState.fromFEN(fen)
        assertNotNull(state)
        assertEquals(98, state!!.halfmoveClock)
        assertEquals("98", halfmoveField(state.fen))

        // Jos jedan tihi potez -> 99, i dalje nije remi.
        val next = state.applying(ChessMove.fromUCI("h1h2", state)!!)
        assertEquals("99", halfmoveField(next.fen))
    }
}
