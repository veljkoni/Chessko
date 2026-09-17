package com.veljkoni.chessko.logic

import com.veljkoni.chessko.models.EngineScore

// MARK: - Parser UCI ocene (Android)
//
// Namerno BEZ ijednog `android.*` uvoza -- isto pravilo kao `models/MoveAnalysis.kt`,
// `logic/PathProgress.kt` i `logic/PuzzleRating.kt`: cita se JVM testom bez emulatora.
//
// FORMAT NIJE PRETPOSTAVLJEN NEGO IZMEREN. iOS je isti parser prvo napisao za
// `<score> <cp> 34.0` (ChessKitEngine-ov tagovan oblik) i pao je na SVAKOJ
// stvarnoj liniji jer je pravi oblik bio drugaciji (vidi `Chessko/Logic/UCIScoreParser.swift`).
// Android ne koristi ChessKitEngine -- `logic/StockfishEngine.kt` je JNI most na
// PRAVI Stockfish (`cpp/stockfish/`), pa je izlaz obican UCI protokol, bez ijedne
// zagrade i bez decimale:
//
//     info depth 1 seldepth 3 multipv 1 score cp 445 nodes 52 ... pv d5c4
//     info depth 1 seldepth 4 multipv 1 score cp -18 nodes 58 ... pv f1d3
//     info depth 1 seldepth 3 multipv 1 score mate 1 nodes 31 ... pv d8h4
//     info depth 1 seldepth 3 multipv 1 score mate -1 nodes 2 ... pv a8d8 d1d8
//
// Uhvaceno preko `adb logcat` na emulatoru dok je aplikacija igrala stvarnu
// partiju na tezini "Stockfish Majstor" (pozitivan cp i pozitivan mat) i dok je
// motor pretrazivao poziciju iz vec isporucene `puzzles.sqlite` baze (negativan
// mat) -- vidi izvestaj Task-a 2. Potvrdjeno i iz izvora koji se kompajlira u
// ovu apk (`cpp/stockfish/uci.cpp:536`, `format_score`):
//
//     [](Score::InternalUnits units) { return "cp " + std::to_string(units.value); }
//     [](Score::Mate mate) { return "mate " + std::to_string(m); }
//
// -- prost `std::to_string` bez ikakvog posebnog tretmana znaka, pa je "-18"/"-1"
// obican predznak u broju, ne poseban tag.
//
// `lowerbound`/`upperbound` (bez vrednosti, odmah iza ocene -- `search.cpp:2166-2169`)
// oznacavaju NEKONACNU ocenu (aspiration window fail-high/fail-low usred iterative
// deepening-a) -- takva linija se namerno odbacuje, isto kao na iOS-u, jer bi
// analiza inace uzela ocenu koja ce se u sledecoj liniji iste dubine promeniti.
object UCIScoreParser {

    /**
     * Cita ocenu iz jedne UCI `info` linije native Stockfish-a.
     *
     * Trazi token "score" pa cita naredna dva tokena ("cp"/"mate" + broj), umesto
     * da se oslanja na fiksnu poziciju u liniji -- redosled polja iza ocene
     * (`nodes`, `nps`, `pv`, ...) nije nas posao, a `depth`/`seldepth`/`multipv`
     * ispred ocene ne menjaju gde "score" pocinje.
     */
    fun parse(line: String): EngineScore? {
        val tokens = line.trim().split(" ").filter { it.isNotEmpty() }
        val scoreIdx = tokens.indexOf("score")
        if (scoreIdx == -1 || scoreIdx + 2 >= tokens.size) return null

        val kind = tokens[scoreIdx + 1]
        val value = tokens[scoreIdx + 2].toIntOrNull() ?: return null

        // Nekonacna ocena (fail-high/fail-low usred pretrage) -- odbaci.
        val next = tokens.getOrNull(scoreIdx + 3)
        if (next == "lowerbound" || next == "upperbound") return null

        return when (kind) {
            "cp" -> EngineScore.Cp(value)
            "mate" -> EngineScore.Mate(value)
            else -> null
        }
    }
}
