package com.veljkoni.chessko

import com.veljkoni.chessko.models.DrawReason
import com.veljkoni.chessko.models.GameState
import com.veljkoni.chessko.models.GameStatus
import com.veljkoni.chessko.models.PieceColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 4, Faza 7: `GameState.statusFromPosition` je preracunavanje statusa iz
 * PRAVILA (mat/pat/sah preko `MoveGenerator.legalMoves` + `isInCheck`), isti
 * obrazac kao `StockfishEngine.terminalEval`. Koristi ga
 * `GameViewModel.load()` kao fallback za partije sacuvane PRE nego sto je
 * `status` pocelo da se serijalizuje eksplicitno -- bez njega
 * `GameState.fromFEN` uvek vraca `GameStatus.Playing`, i za mat, pa dugme
 * "Analiziraj partiju" (`canAnalyzeGame`) posle rekreacije `Activity`-ja
 * nestaje (defekt iz Faze 6e).
 *
 * Namerno bez `android.*` zavisnosti (`GameState`/`MoveGenerator` ih nemaju),
 * pa se ovo dokazuje JVM testom bez emulatora.
 */
class GameStateStatusFromPositionTest {

    // "Fool's Mate" -- 1.f3 e5 2.g4 Qh4#. Poznata, verifikovana FEN pozicija:
    // beli je na potezu i mat je.
    @Test
    fun recognizesCheckmate() {
        val state = GameState.fromFEN("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 3")!!
        val status = GameState.statusFromPosition(state)
        assertEquals(GameStatus.Checkmate(PieceColor.WHITE), status)
    }

    // Klasicna "kraljica-pat": crni kralj h8 nije u sahu, ali su mu sva tri
    // suseda (g8, g7, h7) pod napadom bele dame g6 (uz belog kralja f6).
    @Test
    fun recognizesStalemate() {
        val state = GameState.fromFEN("7k/8/5KQ1/8/8/8/8/8 b - - 0 1")!!
        val status = GameState.statusFromPosition(state)
        assertEquals(GameStatus.Draw(DrawReason.Stalemate), status)
    }

    // Beli kralj e1 u sahu od crnog skakaca g2, ali ima kuda da pobegne
    // (d1/d2/e2 nisu napadnuti) -- Check, ne Checkmate.
    @Test
    fun recognizesCheckWithEscape() {
        val state = GameState.fromFEN("4k3/8/8/8/8/8/6n1/4K3 w - - 0 1")!!
        val status = GameState.statusFromPosition(state)
        assertEquals(GameStatus.Check(PieceColor.WHITE), status)
    }

    @Test
    fun startingPositionIsPlaying() {
        val status = GameState.statusFromPosition(GameState.initial())
        assertEquals(GameStatus.Playing, status)
    }

    // Dokumentovano ogranicenje: pozicija kralj+kralj JESTE nedovoljan
    // materijal (Draw), ali ima legalne poteze -- `statusFromPosition` je ne
    // prepoznaje kao zavrsnu, jer to zavisi od pravila van gole pozicije
    // (isto pravilo primenjuje i `applying()`, odvojenom proverom
    // `isInsufficientMaterial` PRE poziva ovoj funkciji). Ovaj test cuva tu
    // granicu -- ako se ikad promeni, neka se promeni namerno.
    @Test
    fun doesNotRecognizeInsufficientMaterialAsTerminal() {
        val state = GameState.fromFEN("4k3/8/8/8/8/8/8/4K3 w - - 0 1")!!
        val status = GameState.statusFromPosition(state)
        assertTrue("king-vs-king ima legalne poteze, status ostaje Playing", status is GameStatus.Playing)
    }
}
