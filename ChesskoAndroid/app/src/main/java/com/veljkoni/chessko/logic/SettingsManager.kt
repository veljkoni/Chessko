package com.veljkoni.chessko.logic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("chessko_settings", Context.MODE_PRIVATE)

    var languageCode by mutableStateOf(prefs.getString("languageCode", "sr") ?: "sr")
        private set

    init {
        Loc.setLanguage(languageCode)
    }

    var soundEnabled by mutableStateOf(prefs.getBoolean("soundEnabled", true))
        private set
    var hapticsEnabled by mutableStateOf(prefs.getBoolean("hapticsEnabled", true))
        private set
    var colorScheme by mutableStateOf(prefs.getString("colorScheme", "system") ?: "system")
        private set
    var boardTheme by mutableStateOf(prefs.getString("boardTheme", "classic") ?: "classic")
        private set
    var showCoordinates by mutableStateOf(prefs.getBoolean("showCoordinates", true))
        private set
    var showLastMoveHighlight by mutableStateOf(prefs.getBoolean("showLastMoveHighlight", true))
        private set
    var showLegalMoves by mutableStateOf(prefs.getBoolean("showLegalMoves", true))
        private set
    var autoPromoteToQueen by mutableStateOf(prefs.getBoolean("autoPromoteToQueen", false))
        private set
    var pieceStyle by mutableStateOf(prefs.getString("pieceStyle", "classic") ?: "classic")
        private set
    var rotateBoardInLocalPlay by mutableStateOf(prefs.getBoolean("rotateBoardInLocalPlay", true))
        private set
    var swipeToChangeBoardTheme by mutableStateOf(prefs.getBoolean("swipeToChangeBoardTheme", true))
        private set
    var swipeToChangePieceStyle by mutableStateOf(prefs.getBoolean("swipeToChangePieceStyle", true))
        private set
    var showEvalBar by mutableStateOf(prefs.getBoolean("showEvalBar", true))
        private set
    var difficulty by mutableStateOf(GameDifficulty.valueOf(prefs.getString("difficulty", "MEDIUM") ?: "MEDIUM"))
        private set
    var stockfishLevel by mutableStateOf(StockfishLevel.valueOf(prefs.getString("stockfishLevel", "INTERMEDIATE") ?: "INTERMEDIATE"))
        private set

    fun updateLanguageCode(value: String) {
        languageCode = value
        prefs.edit().putString("languageCode", value).apply()
        Loc.setLanguage(value)
    }

    fun updateSoundEnabled(value: Boolean) {
        soundEnabled = value
        prefs.edit().putBoolean("soundEnabled", value).apply()
    }

    fun updateHapticsEnabled(value: Boolean) {
        hapticsEnabled = value
        prefs.edit().putBoolean("hapticsEnabled", value).apply()
    }

    fun updateColorScheme(value: String) {
        colorScheme = value
        prefs.edit().putString("colorScheme", value).apply()
    }

    fun updateBoardTheme(value: String) {
        boardTheme = value
        prefs.edit().putString("boardTheme", value).apply()
    }

    fun updateShowCoordinates(value: Boolean) {
        showCoordinates = value
        prefs.edit().putBoolean("showCoordinates", value).apply()
    }

    fun updateShowLastMoveHighlight(value: Boolean) {
        showLastMoveHighlight = value
        prefs.edit().putBoolean("showLastMoveHighlight", value).apply()
    }

    fun updateShowLegalMoves(value: Boolean) {
        showLegalMoves = value
        prefs.edit().putBoolean("showLegalMoves", value).apply()
    }

    fun updateAutoPromoteToQueen(value: Boolean) {
        autoPromoteToQueen = value
        prefs.edit().putBoolean("autoPromoteToQueen", value).apply()
    }

    fun updatePieceStyle(value: String) {
        pieceStyle = value
        prefs.edit().putString("pieceStyle", value).apply()
    }

    fun updateRotateBoardInLocalPlay(value: Boolean) {
        rotateBoardInLocalPlay = value
        prefs.edit().putBoolean("rotateBoardInLocalPlay", value).apply()
    }

    fun updateSwipeToChangeBoardTheme(value: Boolean) {
        swipeToChangeBoardTheme = value
        prefs.edit().putBoolean("swipeToChangeBoardTheme", value).apply()
    }

    fun updateSwipeToChangePieceStyle(value: Boolean) {
        swipeToChangePieceStyle = value
        prefs.edit().putBoolean("swipeToChangePieceStyle", value).apply()
    }

    fun updateShowEvalBar(value: Boolean) {
        showEvalBar = value
        prefs.edit().putBoolean("showEvalBar", value).apply()
    }

    fun updateDifficulty(value: GameDifficulty) {
        difficulty = value
        prefs.edit().putString("difficulty", value.name).apply()
    }

    fun updateStockfishLevel(value: StockfishLevel) {
        stockfishLevel = value
        prefs.edit().putString("stockfishLevel", value.name).apply()
    }

    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

enum class GameDifficulty {
    BEGINNER,
    EASY,
    MEDIUM,
    HARD,
    STOCKFISH;

    val label: String
        get() = when (this) {
            BEGINNER -> loc("Početnik")
            EASY -> loc("Lako")
            MEDIUM -> loc("Srednje")
            HARD -> loc("Teško")
            STOCKFISH -> "Stockfish"
        }

    val title: String
        get() = when (this) {
            BEGINNER -> loc("Početnik (~500 ELO)")
            EASY -> loc("Lako (~900 ELO)")
            MEDIUM -> loc("Srednje (~1300 ELO)")
            HARD -> loc("Teško (~1700 ELO)")
            STOCKFISH -> loc("Stockfish Majstor (2200+ ELO)")
        }

    val subtitle: String
        get() = when (this) {
            BEGINNER -> loc("Za one koji uče pravila i osnove")
            EASY -> loc("Opuštena i prijatna partija")
            MEDIUM -> loc("Dobar balans za redovne igrače")
            HARD -> loc("Snažna igra bez previda")
            STOCKFISH -> loc("Maksimalna snaga šahovskog motora")
        }
}

enum class StockfishLevel(val depth: Int, val label: String) {
    BEGINNER(1, "Stockfish: 1300 ELO"),
    CASUAL(3, "Stockfish: 1600 ELO"),
    INTERMEDIATE(6, "Stockfish: 1900 ELO"),
    ADVANCED(10, "Stockfish: 2200 ELO"),
    EXPERT(14, "Stockfish: 2600 ELO"),
    MAXIMUM(20, "Stockfish: Maksimalno");
}
