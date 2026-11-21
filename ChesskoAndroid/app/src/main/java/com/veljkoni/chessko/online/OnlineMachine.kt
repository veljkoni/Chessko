package com.veljkoni.chessko.online

import com.veljkoni.chessko.models.PieceColor

// Mašina stanja igre preko interneta: čista funkcija (događaj → novo stanje +
// efekti). Bez mreže, tajmera i org.json — efekte izvršava OnlineViewModel.
// Vreme ulazi samo kroz `Received(_, at)` (za prikaz sata), mašina ga ne meri.
// Verno prenosi iOS `OnlineMachine`.

/**
 * `clock`: vremenska kontrola; `null` = partija bez sata. Stari zapisi (bez polja) se
 * čitaju kao `null` — svaka partija pre protokola v2 je bez sata.
 */
data class ActiveGame(
    val game: String, val token: String, val color: PieceColor, val opponent: String,
    val clock: ClockSpec? = null,
)

/**
 * Poslednje stanje sata sa servera i trenutak kad je primljeno (`receivedAt`, iz
 * događaja — mašina ne zna za vreme). Prikaz odbrojava od toga; server je istina.
 */
data class ClockReading(val wire: WireClock, val receivedAt: Double)

enum class OnlineLink { CONNECTED, RECONNECTING }

enum class OnlineResult {
    I_RESIGNED, OPPONENT_RESIGNED, DRAW_AGREED, ABORTED, BOARD,
    /** Partija na vreme: istekao mi je sat / protivniku je istekao sat. */
    I_FLAGGED, OPPONENT_FLAGGED,
    /** Partija na vreme: prvi potez nije odigran u roku; bez rezultata. */
    FIRST_MOVE_EXPIRED,
}

enum class OnlineNotice {
    OFFLINE, BUSY, SELF_CODE, RATE_LIMITED, BAD_CODE, DECLINED,
    /** U interfejsu, ali ga mašina ne emituje: `code_taken` se tiho rešava. */
    CODE_TAKEN,
    INVITE_CANCELLED, DRAW_DECLINED,
    /** Poziv sa satom stigao je do starije verzije aplikacije (`needs_update`). */
    NEEDS_UPDATE,
}

sealed interface OnlineState {
    data object Idle : OnlineState
    data object Connecting : OnlineState
    data object Lobby : OnlineState
    data class Inviting(val to: String) : OnlineState
    /** `clock == null`: poziv bez sata. */
    data class Invited(val from: String, val clock: ClockSpec?) : OnlineState
    /** `clock == null`: partija bez sata (ili sat još nije stigao). */
    data class Playing(
        val game: ActiveGame, val link: OnlineLink, val opponentAway: Boolean,
        val drawOfferedByOpponent: Boolean, val drawOfferedByMe: Boolean,
        val clock: ClockReading? = null,
    ) : OnlineState
    data class Ended(val result: OnlineResult) : OnlineState
}

sealed interface OnlineEvent {
    data object SocketOpened : OnlineEvent
    data object SocketClosed : OnlineEvent
    /** `at`: trenutak prijema u sekundama (monotono, npr. `SystemClock.elapsedRealtime() / 1000.0`). */
    data class Received(val message: ServerMessage, val at: Double) : OnlineEvent
    data class Invite(val code: String, val clock: ClockSpec?) : OnlineEvent
    data object CancelInvite : OnlineEvent
    data object Accept : OnlineEvent
    data object Decline : OnlineEvent
    /** `ends`: potez po pravilima završava partiju (mat, pat, remi po pravilu). */
    data class LocalMove(val ply: Int, val uci: String, val ends: Boolean) : OnlineEvent
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
    /**
     * Partija na vreme: poslat je sopstveni završni potez (`end: true`), kraj se
     * proglašava tek na njegov `ack` (server je istina — potez je mogao stići kasno).
     */
    private var pendingEndPly: Int? = null

    /**
     * Igrač sme da igra/predaje/nudi remi samo dok je veza živa. UI onemogućava tablu i
     * dugmad kad je `false`. Dok se čeka `ack` završnog poteza nema akcija.
     */
    val canAct: Boolean
        get() = !updateRequired && pendingEndPly == null &&
            (state as? OnlineState.Playing)?.link == OnlineLink.CONNECTED

    private val isTimed: Boolean
        get() = (state as? OnlineState.Playing)?.game?.clock != null

    private fun nextBackoff(): OnlineEffect {
        val s = BACKOFF[minOf(reconnectAttempt, BACKOFF.size - 1)]
        reconnectAttempt += 1
        return OnlineEffect.ScheduleReconnect(s)
    }

    private fun end(r: OnlineResult): List<OnlineEffect> {
        stored = null
        pendingEndPly = null
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
                return listOf(OnlineEffect.Send(ClientMessage.Invite(e.code, e.clock)))
            }
            OnlineEvent.CancelInvite -> {
                if (st !is OnlineState.Inviting) return emptyList()
                state = OnlineState.Lobby
                return listOf(OnlineEffect.Send(ClientMessage.Cancel))
            }
            OnlineEvent.Accept -> {
                // Isti sat koji je pozvani video (server ignoriše `accept` sa drugim).
                if (st !is OnlineState.Invited) return emptyList()
                return listOf(OnlineEffect.Send(ClientMessage.Accept(st.from, st.clock)))
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
                // Bez sata `end` se ne šalje (kraj ide kroz `finished`, kao u v1).
                if (!e.ends || !isTimed) return listOf(OnlineEffect.Send(ClientMessage.Move(e.ply, e.uci, end = false)))
                pendingEndPly = e.ply
                return listOf(OnlineEffect.Send(ClientMessage.Move(e.ply, e.uci, end = true)))
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
                // Sa satom i sopstvenim završnim potezom na putu (`end: true`) kraj stiže
                // sa njegovim `ack`-om — ovde se ništa ne šalje i ne završava.
                if (isTimed && pendingEndPly != null) return emptyList()
                // Tabla je gotova pa se završava lokalno čak i bez veze; `finished`
                // se šalje samo ako je veza živa. Bez sata partiju inače čisti protivnikov
                // `finished` ili serverov prekid posle 30 minuta. Sa satom se `finished`
                // šalje isto: ako se dva telefona ikad ne slože oko remija po pravilu
                // (protivnikov potez je stigao bez `end: true`), server bi bez njega držao
                // partiju i sat bi i dalje tekao, pa bi igrač kasnije izgubio na vreme.
                // Kad je partija na serveru već završena (npr. `resumed` posle protivnikovog
                // `end: true` dok smo bili odsutni), server odgovara `game_gone`, koji se u
                // `Ended` zanemaruje.
                val send: List<OnlineEffect> =
                    if (canAct) listOf(OnlineEffect.Send(ClientMessage.Finished)) else emptyList()
                return send + end(OnlineResult.BOARD)
            }
            is OnlineEvent.Received -> return receive(e.message, e.at)
        }
    }

    /** Novo stanje sata (`null` u poruci ne briše poslednje poznato). */
    private fun refreshClock(wire: WireClock?, at: Double) {
        val st = state
        if (wire == null || st !is OnlineState.Playing) return
        state = st.copy(clock = ClockReading(wire, at))
    }

    private fun receive(m: ServerMessage, at: Double): List<OnlineEffect> {
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
                // Završni potez koji nije stigao do servera nije u `moves`; partija ide dalje.
                pendingEndPly = null
                // Za već završenu partiju sat je zamrznut, a poruka kraja (ili kraj na
                // tabli) stiže odmah posle — ovo stanje sata traje samo do nje.
                state = OnlineState.Playing(s, OnlineLink.CONNECTED, !m.opponentOnline,
                    drawOfferedByOpponent = false, drawOfferedByMe = false,
                    clock = m.clock?.let { ClockReading(it, at) })
                return listOf(OnlineEffect.ReplaceMoves(m.moves))
            }
            is ServerMessage.Error -> return when (m.error) {
                OnlineError.GAME_GONE -> when {
                    stored == null -> emptyList()
                    // `game_gone` dobija strana koja je završila partiju: njen završni potez
                    // (`end: true`) je prihvaćen, samo `ack` nije stigao pre prekida veze.
                    pendingEndPly != null -> end(OnlineResult.BOARD)
                    else -> end(OnlineResult.ABORTED)
                }
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
                // Tiče se PRIJATELJEVE aplikacije (stara verzija ne zna za sat): nije
                // `updateRequired`, samo nazad u lobi sa obaveštenjem.
                OnlineError.NEEDS_UPDATE -> backToLobby(OnlineNotice.NEEDS_UPDATE)
                OnlineError.BAD_CLOCK -> {
                    // Ispravan klijent je ne izaziva (šalje samo BLITZ/RAPID): nazad u lobi.
                    if (st is OnlineState.Inviting || st is OnlineState.Invited) state = OnlineState.Lobby
                    emptyList()
                }
            }
            is ServerMessage.InviteSent -> {
                if (st is OnlineState.Inviting) state = OnlineState.Inviting(m.to)
                return emptyList()
            }
            is ServerMessage.Invited -> {
                if (st !is OnlineState.Lobby) return emptyList()
                // Bez `clock` (ili neispravan) = poziv bez sata; sat se ne izmišlja.
                state = OnlineState.Invited(m.from, m.clock)
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
                val g = ActiveGame(m.game, m.token, m.color, m.opponent, m.clock?.spec)
                stored = g
                pendingEndPly = null
                state = OnlineState.Playing(g, OnlineLink.CONNECTED, false,
                    drawOfferedByOpponent = false, drawOfferedByMe = false,
                    clock = m.clock?.let { ClockReading(it, at) })
                return listOf(OnlineEffect.Persist(g), OnlineEffect.StartGame(g))
            }
            is ServerMessage.Move -> {
                if (st !is OnlineState.Playing) return emptyList()
                refreshClock(m.clock, at)
                val apply = listOf<OnlineEffect>(OnlineEffect.ApplyRemoteMove(m.ply, m.uci))
                // Sa satom server je partiju već završio (Ruling 2): bez `finished`.
                // Bez sata `end` nema značenje — kraj ide kroz `BoardFinished` kao do sada.
                if (m.end && isTimed) return apply + end(OnlineResult.BOARD)
                return apply
            }
            is ServerMessage.Sync -> {
                if (st !is OnlineState.Playing) return emptyList()
                refreshClock(m.clock, at)
                // Server nije prihvatio naš potez (pa ni završni).
                pendingEndPly = null
                return listOf(OnlineEffect.ReplaceMoves(m.moves))
            }
            is ServerMessage.Ack -> {
                if (st !is OnlineState.Playing) return emptyList()
                refreshClock(m.clock, at)
                if (pendingEndPly != null && pendingEndPly == m.ply) return end(OnlineResult.BOARD)
                return emptyList()
            }
            is ServerMessage.Flag -> {
                if (st !is OnlineState.Playing) return emptyList()
                return end(if (m.loser == st.game.color) OnlineResult.I_FLAGGED else OnlineResult.OPPONENT_FLAGGED)
            }
            ServerMessage.FirstMoveExpired -> {
                if (st !is OnlineState.Playing) return emptyList()
                return end(OnlineResult.FIRST_MOVE_EXPIRED)
            }
            ServerMessage.Unknown -> return emptyList()
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
    /** „Partija će te čekati." [Zatvori] — partija bez sata. */
    GAME_WAITS,
    /** „Sat i dalje teče." [Zatvori] — partija na vreme: odsustvo ne zaustavlja sat. */
    CLOCK_RUNS;

    /**
     * Potvrda predaje samo ako je ponuđena predaja I mašina u trenutku dodira još sme
     * da igra; inače se ekran zatvara a partija čeka.
     */
    fun resignsOnConfirm(canActNow: Boolean): Boolean = this == RESIGN && canActNow

    companion object {
        fun of(canAct: Boolean, timed: Boolean = false): OnlineCloseConfirm =
            if (canAct) RESIGN else if (timed) CLOCK_RUNS else GAME_WAITS
    }
}
