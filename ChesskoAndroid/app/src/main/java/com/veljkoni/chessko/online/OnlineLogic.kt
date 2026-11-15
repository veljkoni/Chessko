package com.veljkoni.chessko.online

import com.veljkoni.chessko.models.PieceColor

// Cista logika OnlineViewModel-a izdvojena da bude JVM-testabilna (sam model
// drzi GameViewModel, OkHttp i SharedPreferences).

object OnlineLogic {
    /** Da li zakazani reconnect jos treba da otvori socket (iOS `needsConnection`). */
    fun needsConnection(state: OnlineState, uiOpen: Boolean, hasStoredGame: Boolean): Boolean = when (state) {
        OnlineState.Connecting -> uiOpen || hasStoredGame
        is OnlineState.Playing -> state.link == OnlineLink.RECONNECTING
        else -> false
    }

    /**
     * `ReplaceMoves`: tabla se pravi iznova kad nije online partija ili je druge boje,
     * inace se samo zamenjuju potezi.
     */
    fun replaceNeedsFreshBoard(boardIsOnline: Boolean, boardColor: PieceColor, gameColor: PieceColor): Boolean =
        !boardIsOnline || boardColor != gameColor

    /** `open()` otvara socket samo iz `Idle` i kad nema socketa ni zakazanog otvaranja. */
    fun shouldOpenOnOpen(state: OnlineState, hasSocket: Boolean): Boolean = state == OnlineState.Idle && !hasSocket

    /** `newCode()` samo bez sacuvane partije i u stanjima pre partije. */
    fun canGenerateNewCode(state: OnlineState, hasStoredGame: Boolean): Boolean =
        !hasStoredGame && (state == OnlineState.Idle || state == OnlineState.Connecting || state == OnlineState.Lobby)
}
