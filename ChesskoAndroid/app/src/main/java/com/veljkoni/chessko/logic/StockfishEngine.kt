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
     * Gets the best move for the given FEN and search depth.
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
        return withContext(Dispatchers.IO) {
            for (line in outputChannel) {
                val parts = line.split(" ")
                if (parts.isNotEmpty() && parts[0] == "bestmove") {
                    if (parts.size >= 2) {
                        val move = parts[1]
                        if (move != "(none)") {
                            return@withContext move
                        }
                    }
                    break
                }
            }
            null
        }
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
            return@withLock withContext(Dispatchers.IO) {
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
    }
}
