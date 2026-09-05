package com.veljkoni.chessko.logic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
}
