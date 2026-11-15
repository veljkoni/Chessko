package com.veljkoni.chessko.online

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.viewmodels.GameMode
import com.veljkoni.chessko.viewmodels.GameViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

// Pokrece cistu `OnlineMachine` nad pravom vezom i vozi tablu (`GameViewModel`).
// Masina odlucuje, ovde se samo izvrsavaju efekti. Kreira se lazy (`get`) --
// nijedan postojeci put koda ga ne sme dodirnuti pri pokretanju (mreza, motor).
// Sve se zove sa glavne niti (OkHttp callback-ovi se prebacuju u OnlineConnection).
// Verno prenosi iOS `OnlineViewModel`.

class OnlineViewModel private constructor(private val app: Application) {
    companion object {
        const val PREFS = "chessko_online"
        const val CODE_KEY = "online.code"
        const val ACTIVE_GAME_KEY = "online.activeGame"

        @Volatile
        private var instance: OnlineViewModel? = null

        /** Jedini put do primerka; pravi ga tek prvi poziv. */
        fun get(app: Application): OnlineViewModel =
            instance ?: synchronized(this) { instance ?: OnlineViewModel(app).also { instance = it } }

        /** Postojeci primerak ili null; NIKAD ga ne pravi (zivotni ciklus aktivnosti). */
        fun existing(): OnlineViewModel? = instance

        /** Cita sacuvanu partiju BEZ pravljenja primerka (to bi podiglo GameViewModel i motor). */
        fun hasStoredGame(context: Context): Boolean =
            readStored(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)) != null

        internal fun readStored(prefs: android.content.SharedPreferences): ActiveGame? {
            val raw = prefs.getString(ACTIVE_GAME_KEY, null) ?: return null
            return try {
                val o = JSONObject(raw)
                val color = when (o.getString("color")) {
                    "white" -> PieceColor.WHITE
                    "black" -> PieceColor.BLACK
                    else -> return null
                }
                ActiveGame(o.getString("game"), o.getString("token"), color, o.getString("opponent"))
            } catch (_: Exception) {
                null
            }
        }

        internal fun encodeStored(g: ActiveGame): String = JSONObject()
            .put("game", g.game).put("token", g.token)
            .put("color", if (g.color == PieceColor.WHITE) "white" else "black")
            .put("opponent", g.opponent).toString()
    }

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var state: OnlineState by mutableStateOf(OnlineState.Idle)
        private set

    /** Poruka za ekran; ekran je brise kad je prikaze. */
    var notice: OnlineNotice? by mutableStateOf(null)

    /**
     * Server je odbio verziju protokola. Vazi do kraja procesa: nijedna veza se vise ne
     * otvara, a ekrani trajno traze azuriranje umesto „Povezujem…".
     */
    var updateRequired: Boolean by mutableStateOf(false)
        private set

    var myCode: String by mutableStateOf("")
        private set

    /** Postoji sacuvana partija koja ceka nastavak (UI do povezivanja vec crta tablu). */
    var hasActiveGame: Boolean by mutableStateOf(false)
        private set

    val game: GameViewModel

    private var machine: OnlineMachine
    private var connection: OnlineConnection? = null
    /** Broj trenutnog soketa; callback-ovi starijih soketa se ignorisu. */
    private var generation = 0
    private var reconnectJob: Job? = null
    private var uiOpen = false
    /** Aktivnost nije vidljiva (ON_STOP): zakazani reconnect-i se ne izvrsavaju. */
    private var backgrounded = false
    // Dogadjaji se serijalizuju: efekat (npr. `ReplaceMoves` -> `onBoardFinished`)
    // sme da proizvede nov dogadjaj, ali se on obradjuje tek posle tekuceg.
    private val queue = ArrayDeque<OnlineEvent>()
    private var draining = false

    init {
        var code = prefs.getString(CODE_KEY, null)
        if (code == null || OnlineCode.normalize(code) != code) {
            code = OnlineCode.generate()
            prefs.edit().putString(CODE_KEY, code).apply()
        }
        val stored = readStored(prefs)
        myCode = code
        machine = OnlineMachine(code, stored)
        game = GameViewModel(app, GameViewModel.ONLINE_SAVE_KEY)
        game.onlineCanMove = machine.canAct
        game.onLocalMove = { ply, uci -> handle(OnlineEvent.LocalMove(ply, uci)) }
        game.onBoardFinished = { handle(OnlineEvent.BoardFinished) }
        hasActiveGame = stored != null
    }

    // ── Javni API ───────────────────────────────────────────────────────

    /**
     * Ekran je stvarno na ekranu (postavlja ga `OnlineGameDestination`; rotacija ga na
     * trenutak spusti pa digne). ON_START/ON_STOP hookovi ga citaju umesto `uiOpen`, da
     * veza nikad ne ostane otvorena bez vidljivog ekrana.
     */
    @Volatile
    var destinationShowing = false

    fun open() {
        uiOpen = true
        val wasBackgrounded = backgrounded
        backgrounded = false
        if (OnlineLogic.shouldOpenOnOpen(machine.state, connection != null || reconnectJob?.isActive == true)) {
            openSocket()
        } else if (wasBackgrounded && connection == null && reconnectJob?.isActive != true) {
            // ON_STOP (rotacija) je ugasio vezu a ON_START je preskocen jer ekran jos nije bio gore.
            handle(OnlineEvent.AppBecameActive)
        }
    }

    /** Dok partija traje veza ostaje pod kontrolom masine (UI prvo pita "Predaj partiju?"). */
    fun close() {
        uiOpen = false
        if (machine.state is OnlineState.Playing) return
        shutDownSocket()
        machine = OnlineMachine(myCode, machine.stored)
        sync()
    }

    fun invite(input: String) {
        val code = OnlineCode.normalize(input)
        if (code == null) {
            notice = OnlineNotice.BAD_CODE
            return
        }
        handle(OnlineEvent.Invite(code))
    }

    fun accept() = handle(OnlineEvent.Accept)
    fun decline() = handle(OnlineEvent.Decline)
    fun cancelInvite() = handle(OnlineEvent.CancelInvite)
    fun resign() = handle(OnlineEvent.Resign)
    fun offerDraw() = handle(OnlineEvent.OfferDraw)
    fun answerDraw(yes: Boolean) = handle(OnlineEvent.AnswerDraw(yes))
    fun backToLobby() = handle(OnlineEvent.BackToLobby)

    /** Novi kod samo kad nema ni zive ni sacuvane partije. */
    fun newCode() {
        if (!OnlineLogic.canGenerateNewCode(machine.state, machine.stored != null)) return
        val code = OnlineCode.generate()
        prefs.edit().putString(CODE_KEY, code).apply()
        myCode = code
        shutDownSocket()
        machine = OnlineMachine(code, null)
        sync()
        if (uiOpen) openSocket()
    }

    /**
     * ON_STOP aktivnosti: veza se gasi i reconnect-i se obustavljaju, inace bi pozadinska
     * aplikacija ostala "online" zauvek -- prijatelj nikad ne bi video "izgubio vezu" i
     * serverov limit od 30 minuta ne bi poceo. Partija ostaje sacuvana; `appBecameActive`
     * je nastavlja.
     */
    fun appWentToBackground() {
        if (!(destinationShowing || machine.stored != null)) return
        backgrounded = true
        shutDownSocket()
        handle(OnlineEvent.SocketClosed)
    }

    /** Android oblik iOS `scenePhaseChanged(.active)`; zove se iz ON_START aktivnosti. */
    fun appBecameActive() {
        backgrounded = false
        if (!(destinationShowing || machine.stored != null)) return
        if (machine.state is OnlineState.Idle) {
            if (connection == null) openSocket()
            return
        }
        handle(OnlineEvent.AppBecameActive)
    }

    // ── Dogadjaji i efekti ──────────────────────────────────────────────

    private fun handle(event: OnlineEvent) {
        queue.addLast(event)
        if (draining) return
        draining = true
        try {
            while (true) process(queue.removeFirstOrNull() ?: break)
        } finally {
            draining = false
        }
    }

    private fun process(event: OnlineEvent) {
        val wasEnded = machine.state is OnlineState.Ended
        val effects = machine.handle(event)
        for (effect in effects) execute(effect)
        if (machine.updateRequired && !updateRequired) {
            updateRequired = true
            shutDownSocket()
        }
        sync()
        val st = machine.state
        if (st is OnlineState.Ended && !wasEnded) {
            game.endOnline(st.result)
            // Ekran zatvoren: kod ne sme ostati registrovan na serveru. Masina
            // dobija SocketClosed da `socketAlive` ostane tacan (BackToLobby ponovo povezuje).
            if (!uiOpen) {
                shutDownSocket()
                handle(OnlineEvent.SocketClosed)
            }
        }
    }

    private fun sync() {
        game.onlineCanMove = machine.canAct
        state = machine.state
        hasActiveGame = machine.stored != null
    }

    private fun execute(effect: OnlineEffect) {
        when (effect) {
            is OnlineEffect.Send -> connection?.send(effect.message)
            OnlineEffect.Connect -> openSocket()
            is OnlineEffect.StartGame -> game.startOnlineGame(effect.game.color, emptyList())
            is OnlineEffect.ReplaceMoves -> {
                val color = machine.stored?.color ?: return
                if (OnlineLogic.replaceNeedsFreshBoard(game.gameMode == GameMode.ONLINE, game.playerColor, color)) {
                    game.startOnlineGame(color, effect.moves)
                    if (game.isGameOver) handle(OnlineEvent.BoardFinished)
                } else {
                    game.replaceMoves(effect.moves)
                }
            }
            is OnlineEffect.ApplyRemoteMove -> if (!game.applyRemoteMove(effect.uci)) forceReconnect()
            is OnlineEffect.Persist -> persist(effect.game)
            is OnlineEffect.ShowMessage -> notice = effect.notice
            is OnlineEffect.ScheduleReconnect -> scheduleReconnect(effect.seconds)
        }
    }

    private fun persist(g: ActiveGame?) {
        val e = prefs.edit()
        if (g != null) e.putString(ACTIVE_GAME_KEY, encodeStored(g)) else e.remove(ACTIVE_GAME_KEY)
        e.apply()
    }

    // ── Veza ────────────────────────────────────────────────────────────

    private fun openSocket() {
        if (updateRequired) return
        reconnectJob?.cancel()
        reconnectJob = null
        connection?.close()
        generation += 1
        val gen = generation
        val c = OnlineConnection()
        c.onOpen = { if (generation == gen) handle(OnlineEvent.SocketOpened) }
        c.onMessage = { m -> if (generation == gen) handle(OnlineEvent.Received(m)) }
        c.onClose = {
            if (generation == gen) {
                connection = null
                handle(OnlineEvent.SocketClosed)
            }
        }
        connection = c
        c.open()
    }

    private fun shutDownSocket() {
        reconnectJob?.cancel()
        reconnectJob = null
        connection?.close()
        connection = null
        generation += 1
    }

    /**
     * Tabla nije u skladu sa serverom: odbaci socket pa ponovo kroz masinu;
     * `resume` na novom vraca serverovu listu poteza.
     */
    private fun forceReconnect() {
        connection?.close()
        connection = null
        generation += 1
        handle(OnlineEvent.SocketClosed)
    }

    private fun scheduleReconnect(seconds: Int) {
        reconnectJob?.cancel()
        reconnectJob = null
        if (backgrounded) return
        reconnectJob = scope.launch {
            delay(seconds * 1000L)
            if (!backgrounded && OnlineLogic.needsConnection(machine.state, uiOpen, machine.stored != null)) openSocket()
        }
    }
}
