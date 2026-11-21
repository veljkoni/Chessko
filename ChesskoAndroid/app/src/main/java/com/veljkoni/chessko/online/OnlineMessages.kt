package com.veljkoni.chessko.online

import com.veljkoni.chessko.models.PieceColor

// Poruke protokola igre preko interneta (docs/online-protocol.md,
// server/play/src/protocol.ts). Isti slučajevi kao iOS `ClientMessage`/`ServerMessage`.
// Bez org.json — kodek je u OnlineCodec.kt.

enum class OnlineError {
    VERSION, BAD_CODE, CODE_TAKEN, OFFLINE, BUSY, SELF, RATE_LIMITED, GAME_GONE, NEEDS_UPDATE, BAD_CLOCK;

    /** Vrednost polja `reason` na žici. */
    val wire: String
        get() = name.lowercase()

    companion object {
        fun fromWire(s: String): OnlineError? = entries.firstOrNull { it.wire == s }
    }
}

/** Vremenska kontrola partije (sekunde). Sa servera i u pozivu je gola `{base, inc}`. */
data class ClockSpec(val base: Int, val inc: Int) {
    companion object {
        val BLITZ = ClockSpec(180, 2)
        val RAPID = ClockSpec(600, 5)
    }
}

/** Stanje sata kako ga šalje server (`{base, inc, w, b, run, first}`; w/b/first u ms). */
data class WireClock(
    val spec: ClockSpec,
    val whiteMs: Int,
    val blackMs: Int,
    val running: PieceColor?,
    val firstMoveMs: Int?,
)

sealed interface ClientMessage {
    data class Hello(val code: String) : ClientMessage
    data class Resume(val game: String, val token: String) : ClientMessage
    data class Invite(val to: String, val clock: ClockSpec? = null) : ClientMessage
    data object Cancel : ClientMessage
    data class Accept(val from: String, val clock: ClockSpec? = null) : ClientMessage
    data class Decline(val from: String) : ClientMessage
    data class Move(val ply: Int, val uci: String, val end: Boolean = false) : ClientMessage
    data object Resign : ClientMessage
    data object DrawOffer : ClientMessage
    data class DrawReply(val accept: Boolean) : ClientMessage
    data object Finished : ClientMessage

    companion object {
        const val PROTOCOL_VERSION = 2
    }
}

sealed interface ServerMessage {
    data class Welcome(val code: String) : ServerMessage
    data class Resumed(
        val game: String, val color: PieceColor, val moves: List<String>, val opponentOnline: Boolean,
        val clock: WireClock? = null,
    ) : ServerMessage
    data class InviteSent(val to: String) : ServerMessage
    data class Invited(val from: String, val clock: ClockSpec? = null) : ServerMessage
    data class InviteCancelled(val from: String) : ServerMessage
    data class Declined(val by: String) : ServerMessage
    data class Start(val game: String, val color: PieceColor, val token: String, val opponent: String,
        val clock: WireClock? = null,
    ) : ServerMessage
    data class Move(
        val ply: Int, val uci: String, val end: Boolean = false, val clock: WireClock? = null,
    ) : ServerMessage
    data class Ack(val ply: Int, val clock: WireClock? = null) : ServerMessage
    data class Sync(val moves: List<String>, val clock: WireClock? = null) : ServerMessage
    data object Resigned : ServerMessage
    data object DrawOffer : ServerMessage
    data class DrawReply(val accept: Boolean) : ServerMessage
    data class Opponent(val online: Boolean) : ServerMessage
    data object Aborted : ServerMessage
    data class Error(val error: OnlineError) : ServerMessage
    data class Flag(val loser: PieceColor) : ServerMessage
    data object FirstMoveExpired : ServerMessage
    data object Unknown : ServerMessage
}
