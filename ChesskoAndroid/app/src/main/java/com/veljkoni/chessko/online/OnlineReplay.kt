package com.veljkoni.chessko.online

import com.veljkoni.chessko.models.ChessMove
import com.veljkoni.chessko.models.GameState

/** Rezultat tihog ponavljanja UCI poteza iz pocetne pozicije (bez ijednog `android.*` uvoza). */
data class OnlineReplay(
    val state: GameState,
    val lastMove: ChessMove?,
    /** Par (stanje pre poteza, prethodni potez) -- isti oblik kao `GameViewModel.history`. */
    val history: List<Pair<GameState, ChessMove?>>,
    /** Potezi koji su se stvarno razresili; staje na prvom nelegalnom/necitljivom. */
    val applied: List<String>,
)

fun replayUci(moves: List<String>): OnlineReplay {
    var state = GameState.initial()
    var last: ChessMove? = null
    val history = mutableListOf<Pair<GameState, ChessMove?>>()
    val applied = mutableListOf<String>()
    for (uci in moves) {
        val move = ChessMove.fromUCI(uci, state) ?: break
        history.add(Pair(state, last))
        state = state.applying(move)
        last = move
        applied.add(move.uci)
    }
    return OnlineReplay(state, last, history, applied)
}
