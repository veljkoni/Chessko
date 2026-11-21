package com.veljkoni.chessko.online

import com.veljkoni.chessko.models.PieceColor

// Mašina stanja igre preko interneta: čista funkcija (događaj → novo stanje +
// efekti). Bez mreže, tajmera i org.json — efekte izvršava OnlineViewModel.
// Verno prenosi iOS `OnlineMachine`.

data class ActiveGame(val game: String, val token: String, val color: PieceColor, val opponent: String)

enum class OnlineLink { CONNECTED, RECONNECTING }

enum class OnlineResult { I_RESIGNED, OPPONENT_RESIGNED, DRAW_AGREED, ABORTED, BOARD }

enum class OnlineNotice {
    OFFLINE, BUSY, SELF_CODE, RATE_LIMITED, BAD_CODE, DECLINED,
    /** U interfejsu, ali ga mašina ne emituje: `code_taken` se tiho rešava. */
    CODE_TAKEN,
    INVITE_CANCELLED, DRAW_DECLINED,
}

sealed interface OnlineState {
    data object Idle : OnlineState
    data object Connecting : OnlineState
    data object Lobby : OnlineState
    data class Inviting(val to: String) : OnlineState
    data class Invited(val from: String) : OnlineState
    data class Playing(
        val game: ActiveGame, val link: OnlineLink, val opponentAway: Boolean,
        val drawOfferedByOpponent: Boolean, val drawOfferedByMe: Boolean,
    ) : OnlineState
    data class Ended(val result: OnlineResult) : OnlineState
}

sealed interface OnlineEvent {
    data object SocketOpened : OnlineEvent
    data object SocketClosed : OnlineEvent
    data class Received(val message: ServerMessage) : OnlineEvent
    data class Invite(val code: String) : OnlineEvent
    data object CancelInvite : OnlineEvent
    data object Accept : OnlineEvent
    data object Decline : OnlineEvent
    data class LocalMove(val ply: Int, val uci: String) : OnlineEvent
    data object Resign : OnlineEvent
    data object OfferDraw : OnlineEvent
    data class AnswerDraw(val accept: Boolean) : OnlineEvent
    data object BoardFinished : OnlineEvent
    data object AppBecameActive : OnlineEvent
    /** Iz `Ended` nazad u lobi (isti socket, bez novog hello-a ako je živ). */
    data object BackToLobby : OnlineEvent
}

sealed interface OnlineEffect {
    data class Send(val message: ClientMessage) : OnlineEffect
    data object Connect : OnlineEffect
    data class StartGame(val game: ActiveGame) : OnlineEffect
    data class ApplyRemoteMove(val ply: Int, val uci: String) : OnlineEffect
    data class ReplaceMoves(val moves: List<String>) : OnlineEffect
    data class Persist(val game: ActiveGame?) : OnlineEffect
    data class ShowMessage(val notice: OnlineNotice) : OnlineEffect
    data class ScheduleReconnect(val seconds: Int) : OnlineEffect
}

class OnlineMachine(val myCode: String, var stored: ActiveGame? = null) {
    var state: OnlineState = OnlineState.Idle

    private var reconnectAttempt = 0
    /** Da li je socket trenutno otvoren (za `BackToLobby` iz `Ended`). */
    private var socketAlive = false
    /** Da li je TRENUTNI socket vezan za `myCode` (welcome/resumed stigli). */
    private var socketBound = false
    /**
     * Server je odbio verziju protokola (`error version`). Kraj za ovu sesiju:
     * mašina više ne traži vezu i ignoriše sve događaje; UI trajno traži ažuriranje.
     */
    var updateRequired = false
        private set

    /** Igrač sme da igra/predaje/nudi remi samo dok je veza živa. */
    val canAct: Boolean
        get() = !updateRequired && (state as? OnlineState.Playing)?.link == OnlineLink.CONNECTED

    private fun nextBackoff(): OnlineEffect {
        val s = BACKOFF[minOf(reconnectAttempt, BACKOFF.size - 1)]
        reconnectAttempt += 1
        return OnlineEffect.ScheduleReconnect(s)
    }

    private fun end(r: OnlineResult): List<OnlineEffect> {
        stored = null
        state = OnlineState.Ended(r)
        return listOf(OnlineEffect.Persist(null))
    }

    fun handle(e: OnlineEvent): List<OnlineEffect> {
        // Socket živost se prati u svakom stanju, i u `Ended`.
        when (e) {
            OnlineEvent.SocketOpened -> { socketAlive = true; socketBound = false }
            OnlineEvent.SocketClosed -> { socketAlive = false; socketBound = false }
            is OnlineEvent.Received ->
                if (e.message is ServerMessage.Welcome || e.message is ServerMessage.Resumed) socketBound = true
            else -> {}
        }
        if (updateRequired) return emptyList()
        if (state is OnlineState.Ended) {
            return when (e) {
                OnlineEvent.BackToLobby -> {
                    if (socketAlive && socketBound) {
                        state = OnlineState.Lobby
                        emptyList()
                    } else {
                        state = OnlineState.Connecting
                        // Živ a nevezan socket je već poslao hello; njegov `welcome` vodi u lobi.
                        if (socketAlive) emptyList() else listOf(OnlineEffect.Connect)
                    }
                }
                // Nikad resume: stored je ovde null.
                OnlineEvent.SocketOpened -> listOf(OnlineEffect.Send(ClientMessage.Hello(myCode)))
                else -> emptyList()
            }
        }
        val st = state
        when (e) {
            OnlineEvent.BackToLobby -> return emptyList()
            OnlineEvent.SocketOpened -> {
                when {
                    st is OnlineState.Idle || st is OnlineState.Connecting -> state = OnlineState.Connecting
                    st is OnlineState.Playing && st.link == OnlineLink.RECONNECTING -> {}
                    else -> return emptyList()
                }
                val fx = mutableListOf<OnlineEffect>(OnlineEffect.Send(ClientMessage.Hello(myCode)))
                stored?.let { fx.add(OnlineEffect.Send(ClientMessage.Resume(it.game, it.token))) }
                return fx
            }
            OnlineEvent.SocketClosed -> return when (st) {
                OnlineState.Idle, OnlineState.Connecting, OnlineState.Lobby,
                is OnlineState.Inviting, is OnlineState.Invited -> {
                    state = OnlineState.Connecting
                    listOf(nextBackoff())
                }
                is OnlineState.Playing -> {
                    state = st.copy(link = OnlineLink.RECONNECTING)
                    listOf(nextBackoff())
                }
                is OnlineState.Ended -> emptyList()
            }
            OnlineEvent.AppBecameActive -> return when {
                st is OnlineState.Playing && st.link == OnlineLink.RECONNECTING -> listOf(OnlineEffect.Connect)
                st is OnlineState.Connecting -> listOf(OnlineEffect.Connect)
                else -> emptyList()
            }
            is OnlineEvent.Invite -> {
                if (st !is OnlineState.Lobby) return emptyList()
                state = OnlineState.Inviting(e.code)
                return listOf(OnlineEffect.Send(ClientMessage.Invite(e.code)))
            }
            OnlineEvent.CancelInvite -> {
                if (st !is OnlineState.Inviting) return emptyList()
                state = OnlineState.Lobby
                return listOf(OnlineEffect.Send(ClientMessage.Cancel))
            }
            OnlineEvent.Accept -> {
                if (st !is OnlineState.Invited) return emptyList()
                return listOf(OnlineEffect.Send(ClientMessage.Accept(st.from)))
            }
            OnlineEvent.Decline -> {
                if (st !is OnlineState.Invited) return emptyList()
                state = OnlineState.Lobby
                return listOf(OnlineEffect.Send(ClientMessage.Decline(st.from)))
            }

            // Dok je veza prekinuta (RECONNECTING) akcije se odbijaju (canAct).
            // Potez koji je utrčao u umirući socket ispravlja `ReplaceMoves` posle
            // `Resumed` — server je istina.
            is OnlineEvent.LocalMove -> {
                if (!canAct) return emptyList()
                return listOf(OnlineEffect.Send(ClientMessage.Move(e.ply, e.uci)))
            }
            OnlineEvent.Resign -> {
                if (!canAct) return emptyList()
                return listOf<OnlineEffect>(OnlineEffect.Send(ClientMessage.Resign)) + end(OnlineResult.I_RESIGNED)
            }
            OnlineEvent.OfferDraw -> {
                if (!canAct || st !is OnlineState.Playing) return emptyList()
                state = st.copy(drawOfferedByMe = true)
                return listOf(OnlineEffect.Send(ClientMessage.DrawOffer))
            }
            is OnlineEvent.AnswerDraw -> {
                if (!canAct || st !is OnlineState.Playing) return emptyList()
                if (e.accept) {
                    return listOf<OnlineEffect>(OnlineEffect.Send(ClientMessage.DrawReply(true))) +
                        end(OnlineResult.DRAW_AGREED)
                }
                state = st.copy(drawOfferedByOpponent = false)
                return listOf(OnlineEffect.Send(ClientMessage.DrawReply(false)))
            }
            OnlineEvent.BoardFinished -> {
                if (st !is OnlineState.Playing) return emptyList()
                // Tabla je gotova pa se završava lokalno čak i bez veze; `finished`
                // se šalje samo ako je veza živa. Inače partiju čisti protivnikov
                // `finished` ili serverov prekid posle 30 minuta.
                val send: List<OnlineEffect> =
                    if (canAct) listOf(OnlineEffect.Send(ClientMessage.Finished)) else emptyList()
                return send + end(OnlineResult.BOARD)
            }
            is OnlineEvent.Received -> return receive(e.message)
        }
    }

    private fun receive(m: ServerMessage): List<OnlineEffect> {
        val st = state
        when (m) {
            is ServerMessage.Welcome -> {
                if (st is OnlineState.Idle || st is OnlineState.Connecting) {
                    reconnectAttempt = 0
                    // Sa sačuvanom partijom čekamo resumed / game_gone.
                    if (stored == null) state = OnlineState.Lobby
                }
                return emptyList()
            }
            is ServerMessage.Resumed -> {
                val s = stored ?: return emptyList()
                reconnectAttempt = 0
                state = OnlineState.Playing(s, OnlineLink.CONNECTED, !m.opponentOnline,
                    drawOfferedByOpponent = false, drawOfferedByMe = false)
                return listOf(OnlineEffect.ReplaceMoves(m.moves))
            }
            is ServerMessage.Error -> return when (m.error) {
                OnlineError.GAME_GONE -> if (stored == null) emptyList() else end(OnlineResult.ABORTED)
                OnlineError.CODE_TAKEN -> {
                    if (stored != null) emptyList()
                    else {
                        state = OnlineState.Connecting
                        listOf(nextBackoff())
                    }
                }
                OnlineError.VERSION -> {
                    updateRequired = true
                    emptyList()
                }
                OnlineError.BAD_CODE -> backToLobby(OnlineNotice.BAD_CODE)
                OnlineError.OFFLINE -> backToLobby(OnlineNotice.OFFLINE)
                OnlineError.BUSY -> backToLobby(OnlineNotice.BUSY)
                OnlineError.SELF -> backToLobby(OnlineNotice.SELF_CODE)
                OnlineError.RATE_LIMITED -> backToLobby(OnlineNotice.RATE_LIMITED)
                // Ponašanje sata: Task 9.
                OnlineError.NEEDS_UPDATE, OnlineError.BAD_CLOCK -> emptyList()
            }
            is ServerMessage.InviteSent -> {
                if (st is OnlineState.Inviting) state = OnlineState.Inviting(m.to)
                return emptyList()
            }
            is ServerMessage.Invited -> {
                if (st !is OnlineState.Lobby) return emptyList()
                state = OnlineState.Invited(m.from)
                return emptyList()
            }
            is ServerMessage.InviteCancelled -> {
                if (st !is OnlineState.Invited) return emptyList()
                state = OnlineState.Lobby
                return listOf(OnlineEffect.ShowMessage(OnlineNotice.INVITE_CANCELLED))
            }
            is ServerMessage.Declined -> {
                if (st !is OnlineState.Inviting) return emptyList()
                state = OnlineState.Lobby
                return listOf(OnlineEffect.ShowMessage(OnlineNotice.DECLINED))
            }
            is ServerMessage.Start -> {
                if (st !is OnlineState.Lobby && st !is OnlineState.Inviting && st !is OnlineState.Invited) {
                    return emptyList()
                }
                val g = ActiveGame(m.game, m.token, m.color, m.opponent)
                stored = g
                state = OnlineState.Playing(g, OnlineLink.CONNECTED, false,
                    drawOfferedByOpponent = false, drawOfferedByMe = false)
                return listOf(OnlineEffect.Persist(g), OnlineEffect.StartGame(g))
            }
            is ServerMessage.Move -> {
                if (st !is OnlineState.Playing) return emptyList()
                return listOf(OnlineEffect.ApplyRemoteMove(m.ply, m.uci))
            }
            is ServerMessage.Sync -> {
                if (st !is OnlineState.Playing) return emptyList()
                return listOf(OnlineEffect.ReplaceMoves(m.moves))
            }
            is ServerMessage.Ack, ServerMessage.Unknown, is ServerMessage.Flag, ServerMessage.FirstMoveExpired -> return emptyList()
            ServerMessage.Resigned -> {
                if (st !is OnlineState.Playing) return emptyList()
                return end(OnlineResult.OPPONENT_RESIGNED)
            }
            ServerMessage.Aborted -> {
                if (st !is OnlineState.Playing) return emptyList()
                return end(OnlineResult.ABORTED)
            }
            ServerMessage.DrawOffer -> {
                if (st !is OnlineState.Playing) return emptyList()
                state = st.copy(drawOfferedByOpponent = true)
                return emptyList()
            }
            is ServerMessage.DrawReply -> {
                if (st !is OnlineState.Playing) return emptyList()
                if (m.accept) return end(OnlineResult.DRAW_AGREED)
                state = st.copy(drawOfferedByMe = false)
                return listOf(OnlineEffect.ShowMessage(OnlineNotice.DRAW_DECLINED))
            }
            is ServerMessage.Opponent -> {
                if (st !is OnlineState.Playing) return emptyList()
                state = st.copy(opponentAway = !m.online)
                return emptyList()
            }
        }
    }

    private fun backToLobby(n: OnlineNotice): List<OnlineEffect> {
        val st = state
        if (st is OnlineState.Inviting || st is OnlineState.Invited) state = OnlineState.Lobby
        return listOf(OnlineEffect.ShowMessage(n))
    }

    private companion object {
        val BACKOFF = listOf(1, 2, 4, 8, 15)
    }
}

/**
 * Potvrda zatvaranja usred partije. Varijanta se bira kad se dijalog otvori i ne
 * menja se dok je otvoren (veza koja se vrati ne sme pretvoriti „Zatvori" u „Predaj").
 * Verno prenosi iOS `OnlineCloseConfirm`.
 */
enum class OnlineCloseConfirm {
    /** „Predati partiju?" [Predaj] */
    RESIGN,
    /** „Partija će te čekati." [Zatvori] */
    GAME_WAITS;

    /**
     * Potvrda predaje samo ako je ponuđena predaja I mašina u trenutku dodira još sme
     * da igra; inače se ekran zatvara a partija čeka.
     */
    fun resignsOnConfirm(canActNow: Boolean): Boolean = this == RESIGN && canActNow

    companion object {
        fun of(canAct: Boolean): OnlineCloseConfirm = if (canAct) RESIGN else GAME_WAITS
    }
}
