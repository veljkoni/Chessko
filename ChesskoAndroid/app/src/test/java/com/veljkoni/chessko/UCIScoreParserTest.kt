package com.veljkoni.chessko

import com.veljkoni.chessko.logic.UCIScoreParser
import com.veljkoni.chessko.models.EngineScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Linije ispod su DOSLOVNO prekopirane iz stvarnog izlaza native Stockfish-a
// (JNI most, `cpp/stockfish`), uhvacene preko `adb logcat` na emulatoru dok je
// aplikacija igrala partiju na tezini "Stockfish Majstor". Nista nije
// pretpostavljeno: iOS je isti parser napisao za `<score> <cp> 34.0` format
// (ChessKitEngine-ov tagovan oblik) i pao je na svakoj stvarnoj liniji jer je
// pravi oblik bio drugaciji. Android koristi PRAVI Stockfish (ne ChessKitEngine),
// pa je izlaz obican UCI protokol: "score cp <int>" / "score mate <int>", bez
// ijedne zagrade i bez decimale. Potvrdjeno i iz izvora (`uci.cpp:536`,
// `format_score`): `"cp " + std::to_string(units.value)` / `"mate " + std::to_string(m)`.
//
// Linije za "mate -1" i "score cp -18" hvatane su tako sto je STVARNI native
// Stockfish pozvan sa nekoliko FEN-ova (jedan iz vec isporucene `puzzles.sqlite`
// baze, `mateIn2` zadatak `TU9vn` -- vidi izvestaj zadatka), ne izmisljeno:
// motor je zaista pretrazivao te pozicije i zaista vratio te ocene.
class UCIScoreParserTest {

    @Test
    fun parsesOrdinaryPositiveCp() {
        val line = "info depth 1 seldepth 3 multipv 1 score cp 445 nodes 52 nps 10400 hashfull 0 tbhits 0 time 5 pv d5c4"
        assertEquals(EngineScore.Cp(445), UCIScoreParser.parse(line))
    }

    @Test
    fun parsesNegativeCp() {
        val line = "info depth 1 seldepth 4 multipv 1 score cp -18 nodes 58 nps 9666 hashfull 0 tbhits 0 time 6 pv f1d3"
        assertEquals(EngineScore.Cp(-18), UCIScoreParser.parse(line))
    }

    @Test
    fun parsesCpAtHighDepthWithLongPv() {
        // Uhvaceno na depth 22, 1.132.173 cvorova -- proverava da parser ne
        // zavisi od kratke `pv` liste niti od pozicije `score` tokena u liniji.
        val line = "info depth 22 seldepth 31 multipv 1 score cp -3 nodes 1132173 nps 25838 hashfull 416 tbhits 0 time 43818 pv f3e5 f6d7 f1d3 d7e5 d4e5 f7f5 h2h4 a8b8 d2f3 b7b5 f3g5 e7g5 h4g5 g7g6 e1g1 b5b4 c3c4"
        assertEquals(EngineScore.Cp(-3), UCIScoreParser.parse(line))
    }

    @Test
    fun parsesPositiveMate() {
        // Fool's mate: 1.f3 e5 2.g4 -- Stockfish (crni na potezu) nalazi Qh4#.
        val line = "info depth 1 seldepth 3 multipv 1 score mate 1 nodes 31 nps 7750 hashfull 0 tbhits 0 time 4 pv d8h4"
        assertEquals(EngineScore.Mate(1), UCIScoreParser.parse(line))
    }

    @Test
    fun parsesNegativeMate() {
        // Puzzle `TU9vn` iz puzzles.sqlite (mateIn2), pozicija posle beskog
        // sahovanja topom -- crni ima TACNO jedan legalan potez (a8d8), a
        // motor (na potezu je crni) vec vidi da sledi mat -- otud "mate -1".
        val line = "info depth 1 seldepth 3 multipv 1 score mate -1 nodes 2 nps 2000 hashfull 0 tbhits 0 time 1 pv a8d8 d1d8"
        assertEquals(EngineScore.Mate(-1), UCIScoreParser.parse(line))
    }

    @Test
    fun linesWithoutScoreReturnNull() {
        // "info string ..." linije nemaju ocenu -- ovakve linije prate uci
        // handshake (verzija, NNUE fajlovi) i ne smeju da izazovu pad parsera.
        assertNull(UCIScoreParser.parse("info string Available processors: 0-1"))
        assertNull(UCIScoreParser.parse("info string Using 1 thread"))
        assertNull(
            UCIScoreParser.parse(
                "info string NNUE evaluation using /data/user/0/com.veljkoni.chessko/files/nn-1c0000000000.nnue (133MiB, (22528, 3072, 15, 32, 1))"
            )
        )
    }

    @Test
    fun bestmoveLinesReturnNull() {
        // `bestmove` nije `info` linija i nema token "score" -- ne sme se
        // pomesati sa ocenom pozicije.
        assertNull(UCIScoreParser.parse("bestmove d5c4 ponder d2d3"))
        assertNull(UCIScoreParser.parse("bestmove d8h4"))
    }

    @Test
    fun blankAndUnrelatedLinesReturnNull() {
        assertNull(UCIScoreParser.parse(""))
        assertNull(UCIScoreParser.parse("uciok"))
        assertNull(UCIScoreParser.parse("readyok"))
        assertNull(UCIScoreParser.parse("id name Stockfish 17.1"))
    }

    @Test
    fun boundSuffixesMakeScoreIncomplete() {
        // `lowerbound`/`upperbound` nisu uhvaceni uzivo (nasa pretraga nikad
        // nije pogodila aspiration-window fail-high/low u prozoru merenja),
        // ali su POTVRDJENI u samom izvoru koji je kompajliran u ovu apk:
        // `search.cpp:2166-2169` gradi bas te doslovne stringove odmah iza
        // ocene, pre "nodes" -- linija ispod je sastavljena po tom tacnom
        // rasporedu polja, ne pretpostavljena.
        val lowerbound = "info depth 12 seldepth 18 multipv 1 score cp 34 lowerbound nodes 12345 nps 500000 hashfull 10 tbhits 0 time 20 pv e2e4"
        val upperbound = "info depth 12 seldepth 18 multipv 1 score mate 5 upperbound nodes 12345 nps 500000 hashfull 10 tbhits 0 time 20 pv e2e4"
        assertNull(UCIScoreParser.parse(lowerbound))
        assertNull(UCIScoreParser.parse(upperbound))
    }
}
