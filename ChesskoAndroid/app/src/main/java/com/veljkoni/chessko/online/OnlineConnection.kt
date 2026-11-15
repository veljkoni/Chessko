package com.veljkoni.chessko.online

import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

// Jedan WebSocket ka serveru za igru preko interneta. Jednokratan: kad se zatvori
// ili padne, pravi se nov primerak (OnlineViewModel numerise sokete). Jedino mesto
// u aplikaciji koje koristi mrezu. OkHttp callback-ovi stizu na pozadinskim
// nitima; svi se prebacuju na glavnu nit pre nego sto dodirnu stanje, pa su
// onOpen/onMessage/onClose i sva polja ovde jednonitni (kao @MainActor na iOS-u).
// Verno prenosi iOS `OnlineConnection`.

class OnlineConnection {
    companion object {
        const val URL = "wss://play.chessko.app/ws"
        const val PING_SECONDS = 25L
        const val CONNECT_TIMEOUT_SECONDS = 15L
        /** Najduze cekanje da se poslate poruke isprazne pre gasenja (npr. "resign"). */
        const val FLUSH_CAP_MS = 2000L

        /** Deljen klijent: ping na 25 s drzi vezu zivom i otkriva mrtvu (onFailure). */
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }

    var onOpen: (() -> Unit)? = null
    var onMessage: ((ServerMessage) -> Unit)? = null
    /** Tacno jednom po soketu, osim posle `close()` (tiho gasenje). */
    var onClose: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var finished = false

    fun open() {
        if (socket != null || finished) return
        val request = Request.Builder().url(URL).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                main.post { handleOpened() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { handle(text) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                main.post { handle(bytes.utf8()) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Odgovor je uvek 1000 (normalno zatvaranje), ne eho serverovog koda.
                webSocket.close(1000, null)
                main.post { fail() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { fail() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { fail() }
            }
        })
    }

    fun send(message: ClientMessage) {
        val ws = socket ?: return
        if (finished) return
        // `send` vraca false kad je red pun ili je soket vec zatvaran: veza je tada neupotrebljiva.
        if (!ws.send(OnlineCodec.encode(message))) fail()
    }

    /**
     * Tiho gasenje: nijedan callback vise ne stize. OkHttp `close(1000)` stavlja
     * close-frame u ISTI red kao poruke, iza onih vec poslatih, pa poslednji
     * `resign`/`move` stize do servera pre zatvaranja. Cekanje je ograniceno na
     * `FLUSH_CAP_MS`, pa `cancel()`.
     */
    fun close() {
        finished = true
        onClose = null
        onOpen = null
        onMessage = null
        val ws = socket ?: return
        socket = null
        ws.close(1000, null)
        main.postDelayed({ ws.cancel() }, FLUSH_CAP_MS)
    }

    private fun handleOpened() {
        if (finished) return
        onOpen?.invoke()
    }

    private fun handle(text: String) {
        if (finished) return
        // Nepoznata poruka dekodira u Unknown; kodek BACA na ne-objekat, to je ovde ignorisano.
        val decoded = try {
            OnlineCodec.decode(text)
        } catch (_: Exception) {
            ServerMessage.Unknown
        }
        if (decoded == ServerMessage.Unknown) return
        onMessage?.invoke(decoded)
    }

    /** Svaka greska/zatvaranje zavrsava ovde; `finished` garantuje jedan `onClose`. */
    private fun fail() {
        if (finished) return
        finished = true
        val ws = socket
        socket = null
        ws?.cancel()
        val callback = onClose
        onClose = null
        onOpen = null
        onMessage = null
        callback?.invoke()
    }
}
