package com.veljkoni.chessko.logic

import kotlin.math.pow
import kotlin.math.roundToInt

// MARK: - Elo rejting za zadatke
//
// Namerno BEZ ijednog `android.*` uvoza: `StatsManager` uvozi `Context` i
// `SharedPreferences` pa se ne moze testirati obicnim JVM testom. Ova
// racunica moze, i mora — ona odredjuje koje zadatke korisnik uopste vidi.
object PuzzleRating {

    /// Granice isporucene baze (`puzzles.sqlite`).
    const val MIN = 600
    const val MAX = 2200
    const val START = 800
    private const val K = 32

    /// `E = 1/(1 + 10^((Rp-R)/400))`, `R' = R + K*(S-E)`.
    fun newRating(current: Int, puzzleRating: Int, solved: Boolean): Int {
        val expected = 1.0 / (1.0 + 10.0.pow((puzzleRating - current) / 400.0))
        val score = if (solved) 1.0 else 0.0
        return current + (K * (score - expected)).roundToInt()
    }

    /// Prozor iz kog se biraju zadaci za vezbanje: malo ispod rejtinga igraca.
    ///
    /// Klampuju se OBE granice, i to donja pa gornja. Rejting igraca nije
    /// ogranicen — dug niz neuspeha ga vodi ka ~80 — pa bi naivan prozor bio
    /// `-120..180`, a klampovanje samo donje granice dalo bi `600..180`.
    /// Obrnut `IntRange` ne puca kao `ClosedRange` u Swift-u, ali tiho ne
    /// vraca nijedan red, sto je gore: ekran ostane prazan bez poruke.
    fun practiceWindow(playerRating: Int): IntRange {
        val lo = maxOf(MIN, playerRating - 200)
        val hi = maxOf(lo, minOf(MAX, playerRating + 100))
        return lo..hi
    }
}
