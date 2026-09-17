package com.veljkoni.chessko.logic

import android.content.Context
import android.util.Log
import com.veljkoni.chessko.models.EngineScore
import com.veljkoni.chessko.models.GameState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ocena jedne pozicije + potez koji bi motor odigrao (`bestMove`, UCI string ili
 * `null` za terminalnu poziciju). `bestMove` je ovde potreban da bi se posle, u
 * analizi partije, moglo utvrditi da li je IGRAC odigrao bas potez motora
 * (`engineBestMatched` u `GameAnalysis.build`).
 */
data class PositionEval(val score: EngineScore, val bestMove: String?)

object StockfishEngine {
    private val inputChannel = Channel<String>(Channel.UNLIMITED)
    private val outputChannel = Channel<String>(Channel.UNLIMITED)
    var engineStarted = false
        private set

    private val searchMutex = Mutex()

    // Gornje granice za `for (line in outputChannel)` petlje ispod. Bez njih,
    // ako native motor umre ili "go"/"position" bude progutana, petlja visi
    // ZAUVEK -- unutar `searchMutex.withLock`. `StockfishEngine` je `object`
    // singleton sa JEDNIM mutexom, pa jedna zaglavljena pretraga trajno
    // blokira SVAKI naredni poziv (`getBestMove` -- dakle i zivu partiju
    // protiv racunara -- i `evaluate`), do restarta procesa. Ovaj task
    // uvodi `evaluate` koji ce u analizi partije (Task 4) biti pozivan ~81
    // puta zaredom kroz isti mutex -- izlozenost istoj klasi greske raste za
    // dva reda velicine u odnosu na jedan poziv `getBestMove` po potezu.
    //
    // Vrednosti su PROCENA sa realnom donjom granicom, ne merenje ove tacne
    // stvari (koliko traje TIMEOUT), nego merenje koliko traje NORMALNA
    // pretraga -- iz stvarno uhvacenog izlaza na ovom istom emulatoru
    // (Task 2 ove faze, "P3(deep)" dijagnostika):
    //   `go depth 20` -> "info depth 20 ... time 29315" (~29,3s)
    //   `go depth 22` -> "info depth 22 ... time 43818" (~43,8s)
    // `StockfishLevel.MAXIMUM` (jedini nivo koji `getBestMove` dobija od
    // korisnika) trazi dubinu 20, dakle ~29s je gornja granica NORMALNOG toka
    // za koju imamo merenje. 120s je >4x ta vrednost -- dovoljna rezerva za
    // sporiju poziciju ili sporiji uredjaj, a i dalje konacna. `evaluate` se
    // poziva na fiksnoj dubini 12, gde je isti capture izmerio ~1,0s
    // ("info depth 12 ... time 1040"); 30s je >25x ta vrednost. Marginu (4x,
    // 25x) biram procenom, ne merenjem -- Task 6 (jedino preostalo dizanje
    // emulatora u fazi) je proverava na zivom uredjaju; ako se pokaze da
    // normalna pretraga ikad priblizi ovim brojevima, vrednosti treba
    // povecati, ne obrnuto.
    private const val BEST_MOVE_TIMEOUT_MS = 120_000L
    private const val EVALUATE_TIMEOUT_MS = 30_000L

    private external fun startEngine()
    private external fun sendCommand(cmd: String)
    private external fun readOutput(): String

    fun start(context: Context, evalFileName: String, evalFileSmallName: String) {
        if (engineStarted) return
        try {
            System.loadLibrary("stockfishjni")
            startEngine()
            engineStarted = true
            
            // Start reading standard output in a loop
            listenOutput()
            
            // Start sending standard input commands
            CoroutineScope(Dispatchers.IO).launch {
                sendInput()
            }

            // Copy NNUE files from assets to internal storage and set them up
            CoroutineScope(Dispatchers.IO).launch {
                val evalFilePath = copyNNUEFile(context, evalFileName)
                val evalFileSmallPath = copyNNUEFile(context, evalFileSmallName)
                Log.d("StockfishEngine", "NNUE files copied: $evalFilePath, $evalFileSmallPath")
                inputChannel.send("uci")
                inputChannel.send("setoption name EvalFile value $evalFilePath")
                inputChannel.send("setoption name EvalFileSmall value $evalFileSmallPath")
                inputChannel.send("isready")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e("StockfishEngine", "Failed to load stockfishjni library", e)
        } catch (e: Exception) {
            Log.e("StockfishEngine", "Failed to start Stockfish engine", e)
        }
    }

    private fun sendInput() = CoroutineScope(Dispatchers.IO).launch {
        for (cmd in inputChannel) {
            sendCommand(cmd)
        }
    }

    private fun listenOutput() = CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            val output = readOutput()
            if (output.isNotBlank()) {
                Log.d("StockfishEngine", "Stockfish output: $output")
                outputChannel.send(output)
            }
        }
    }

    private fun copyNNUEFile(context: Context, fileName: String): String {
        val outFile = context.filesDir.resolve(fileName)
        if (!outFile.exists()) {
            context.assets.open(fileName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }

    /**
     * Ceka da motor stvarno zavrsi ceo startup pipeline (NNUE kopiranje +
     * `uci`/`setoption`/`isready`), ne samo da native proces krene.
     *
     * `engineStarted` postaje `true` SINHRONO u `start()`, ODMAH posle
     * `startEngine()` -- PRE nego sto kopiranje NNUE fajlova (do ~140MB) i UCI
     * handshake uopste pocnu (oni idu u odvojenoj pozadinskoj korutini, vidi
     * `start()`). Zato `engineStarted` NIJE dokaz spremnosti, samo dokaz da je
     * proces pokrenut -- poziv `getBestMove`/`evaluate` odmah posle `start()`
     * moze da posalje "position"/"go" PRE nego sto motor dobije NNUE mrezu.
     *
     * Ova funkcija cita `outputChannel` dok ne vidi "readyok" -- odgovor na
     * "isready" KOJI VEC SALJE `start()`, na kraju svoje pozadinske korutine,
     * posle oba `setoption`-a. Ne salje sopstveni "isready": drugi bi mogao da
     * stigne PRE internog niza komandi (ako se `start()`-ova NNUE-kopija jos
     * nije zavrsila) i motor bi odgovorio skoro odmah, sto bi ovu funkciju
     * ucinilo bezvrednom -- istom greskom koju ispravlja u odnosu na
     * `engineStarted`.
     *
     * Namenjeno pozivaocima kojima treba stvarna potvrda spremnosti (danas:
     * `StockfishEvaluateTest`). Normalan tok igre je ne zove -- `getBestMove`
     * salje sopstveni "position"/"go" i ceka "bestmove", sto implicitno ceka
     * da motor obradi sve pred njim, ukljucujuci i handshake ako jos traje.
     */
    suspend fun waitUntilReady(timeoutMs: Long = 30_000L): Boolean = searchMutex.withLock {
        if (!engineStarted) return@withLock false
        withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                for (line in outputChannel) {
                    if (line.trim() == "readyok") return@withContext true
                }
                false
            }
        } ?: false
    }

    /**
     * Gets the best move for the given FEN and search depth.
     *
     * Ogranicena sa [BEST_MOVE_TIMEOUT_MS]: bez toga, ako motor umre ili
     * proguta "go", petlja ispod visi zauvek DOK DRZI `searchMutex` --
     * jedina zastita koju `StockfishEngine` (singleton, jedan mutex) inace
     * ima protiv toga je restart procesa. U normalnom toku (depth <= 20,
     * `StockfishLevel.MAXIMUM`) ovo se nikad ne aktivira -- vidi komentar uz
     * konstantu za merenje na kom je zasnovana.
     */
    suspend fun getBestMove(fen: String, depth: Int): String? = searchMutex.withLock {
        if (!engineStarted) return null

        // Drain output channel to remove old stale messages
        while (true) {
            val result = outputChannel.tryReceive()
            if (result.isFailure || result.isClosed) break
        }

        // Send search commands
        inputChannel.send("position fen $fen")
        inputChannel.send("go depth $depth")

        // Wait for the bestmove output
        val move = withTimeoutOrNull(BEST_MOVE_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                for (line in outputChannel) {
                    val parts = line.split(" ")
                    if (parts.isNotEmpty() && parts[0] == "bestmove") {
                        if (parts.size >= 2) {
                            val candidate = parts[1]
                            if (candidate != "(none)") {
                                return@withContext candidate
                            }
                        }
                        break
                    }
                }
                null
            }
        }
        if (move == null) {
            // Ili je istekao TIMEOUT (motor zaglavljen), ili je pretraga
            // legitimno zavrsila bez poteza ("(none)", ili je kanal
            // zatvoren). U oba slucaja je bezbedno poslati "stop": motor
            // koji vec nije u pretrazi ga ignorise, a motor koji jos
            // pretrazuje ce prestati da trosi procesor umesto da svoj
            // zakasneli izlaz ubaci u kanal SLEDECE pretrage (ista klasa
            // greske koju iOS ima zapisanu za analizu partije -- "izlaz
            // starog motora upadne u pipe novog").
            inputChannel.send("stop")
        }
        return move
    }

    /**
     * Ocena jedne pozicije za analizu partije. Za razliku od [getBestMove], koji
     * cita SAMO `bestmove` liniju i baca sve `info` linije, ova funkcija cita
     * `info` linije kroz [UCIScoreParser.parse] da izvuce ocenu.
     *
     * Uzima se POSLEDNJA vidjena ocena PRE `bestmove` -- motor tokom
     * produbljivanja salje ocenu za svaku dubinu (`depth 1`, `depth 2`, ...), a
     * zanima nas ona sa pune dubine, ne prva koju je prijavio.
     *
     * Terminalna pozicija (bez legalnih poteza -- mat ili pat) se NE salje
     * motoru: Stockfish za takvu poziciju ne posalje nijednu `score` liniju, pa
     * bi ova funkcija vratila `null` za SVAKU odigranu partiju -- poslednja
     * pozicija u partiji je uvek mat ili pat. Umesto toga se ocena izvodi iz
     * PRAVILA, preko generisanja poteza -- NE preko `GameState.status`, koji
     * `GameState.fromFEN` ostavlja na `Playing` cak i za mat (konstruktor se
     * poziva bez `status`, podrazumevana vrednost je `Playing` --
     * `models/GameState.kt`). Nema legalnih poteza: sah -> `Mate(0)`, inace ->
     * `Cp(0)`.
     *
     * Pretraga (ne terminalna grana) je ogranicena sa [EVALUATE_TIMEOUT_MS] --
     * isti razlog kao kod [getBestMove], samo izlozeniji: analiza partije
     * (Task 4) zove ovo do ~81 puta zaredom kroz ISTI `searchMutex`, pa bi
     * jedna zaglavljena pozicija bez timeout-a trajno blokirala i analizu i
     * svaku sledecu partiju protiv racunara u istom procesu.
     */
    suspend fun evaluate(fen: String, depth: Int = 12): PositionEval? {
        // Terminalna pozicija se prepoznaje PRE bilo kakvog dodira sa motorom --
        // ni `searchMutex`, ni provera `engineStarted`, ni ijedna UCI komanda.
        // Isti redosled kao iOS `evaluate(state:depth:)`, koji `terminalEval`
        // proverava pre nego sto uopste napravi motor.
        val state = GameState.fromFEN(fen)
        if (state != null) {
            val legalMoves = MoveGenerator.legalMoves(state.currentTurn, state)
            if (legalMoves.isEmpty()) {
                val inCheck = MoveGenerator.isInCheck(state.currentTurn, state)
                return PositionEval(
                    score = if (inCheck) EngineScore.Mate(0) else EngineScore.Cp(0),
                    bestMove = null
                )
            }
        }

        return searchMutex.withLock {
            if (!engineStarted) return@withLock null

            // Drain output channel to remove old stale messages
            while (true) {
                val result = outputChannel.tryReceive()
                if (result.isFailure || result.isClosed) break
            }

            // Send search commands
            inputChannel.send("position fen $fen")
            inputChannel.send("go depth $depth")

            // Wait for the bestmove output, tracking the last seen score along the way.
            val eval = withTimeoutOrNull(EVALUATE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    var lastScore: EngineScore? = null
                    for (line in outputChannel) {
                        val parts = line.split(" ")
                        if (parts.isNotEmpty() && parts[0] == "info") {
                            UCIScoreParser.parse(line)?.let { lastScore = it }
                            continue
                        }
                        if (parts.isNotEmpty() && parts[0] == "bestmove") {
                            val move = if (parts.size >= 2 && parts[1] != "(none)") parts[1] else null
                            // Bez ijedne ocene nema sta da se vrati -- pozicija bez
                            // ocene bi u racunici prosla kao cp(0), sto je tvrdnja
                            // da je izjednaceno.
                            val score = lastScore ?: return@withContext null
                            return@withContext PositionEval(score, move)
                        }
                    }
                    null
                }
            }
            if (eval == null) {
                // Isto obrazlozenje kao u `getBestMove`: bezbedno i kad
                // pretraga nije stvarno istekla (samo legitimno zavrsila bez
                // ocene), obavezno kad JESTE istekla -- motor koji nastavlja
                // da pretrazuje u pozadini bi svoj zakasneli izlaz ubacio u
                // kanal SLEDECE `evaluate`/`getBestMove` pretrage.
                inputChannel.send("stop")
            }
            eval
        }
    }
}
