package com.veljkoni.chessko.viewmodels

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import androidx.compose.runtime.*
import com.veljkoni.chessko.logic.MoveGenerator
import com.veljkoni.chessko.models.*

enum class LearnScenario(val label: String) {
    CASTLING(loc("Rokada")),
    EN_PASSANT("En passant"),
    PROMOTION(loc("Promocija"))
}

class LearnViewModel {
    var selectedPieceType by mutableStateOf(PieceType.QUEEN)
    var piecePosition by mutableStateOf(Position(4, 3)) // d4 default
    var activeScenario by mutableStateOf<LearnScenario?>(null)

    var board by mutableStateOf<List<List<ChessPiece?>>>(emptyBoard())
        private set
    var legalMoves by mutableStateOf<List<ChessMove>>(emptyList())
        private set

    val selectedPosition: Position?
        get() = piecePosition

    val isPlayerTurn: Boolean
        get() = true

    init {
        refresh()
    }

    fun select(piece: PieceType) {
        selectedPieceType = piece
        activeScenario = null
        piecePosition = defaultPosition(piece)
        refresh()
    }

    val availableScenarios: List<LearnScenario>
        get() = when (selectedPieceType) {
            PieceType.KING -> listOf(LearnScenario.CASTLING)
            PieceType.PAWN -> listOf(LearnScenario.EN_PASSANT, LearnScenario.PROMOTION)
            else -> emptyList()
        }

    fun toggleScenario(scenario: LearnScenario) {
        if (activeScenario == scenario) {
            activeScenario = null
            piecePosition = defaultPosition(selectedPieceType)
        } else {
            activeScenario = scenario
            piecePosition = scenarioStartPosition(scenario)
        }
        refresh()
    }

    fun tap(position: Position) {
        if (position == piecePosition) return
        if (activeScenario != null) {
            activeScenario = null
        }
        piecePosition = position
        refresh()
    }

    val infoTitle: String
        get() = when (activeScenario) {
            null -> selectedPieceType.srbName
            LearnScenario.CASTLING -> loc("Rokada")
            LearnScenario.EN_PASSANT -> "En passant"
            LearnScenario.PROMOTION -> loc("Promocija")
        }

    val infoText: String
        get() = when (activeScenario) {
            null -> when (selectedPieceType) {
                PieceType.PAWN -> loc("Kreće se jedno polje napred. Uzima figuru dijagonalno ispred sebe. Sa startne pozicije može da skoči i dva polja odjednom.")
                PieceType.ROOK -> loc("Kreće se horizontalno ili vertikalno, koliko god polja želi. Ne može preskakati figure.")
                PieceType.KNIGHT -> loc("Kreće se u obliku slova L: dva polja u jednom pravcu pa jedno bočno. Jedina figura koja može preskočiti druge.")
                PieceType.BISHOP -> loc("Kreće se dijagonalno, koliko god polja želi. Uvek ostaje na istoj boji polja.")
                PieceType.QUEEN -> loc("Najjača figura na tabli. Kombinuje kretanje topa i lovca — horizontalno, vertikalno i dijagonalno.")
                PieceType.KING -> loc("Kreće se jedno polje u bilo kom smeru. Ne sme stati na polje koje napada protivnik. Zaštiti ga!")
            }
            LearnScenario.CASTLING -> loc("Poseban potez: ako kralj i top nisu se još pomerali i između njih nema figura, kralj skoči dva polja ka topu, a top preskoči kralja. Tapni g1 (kratka rokada) ili c1 (duga rokada).")
            LearnScenario.EN_PASSANT -> loc("Posebno uzimanje pešakom: ako protivnički pešak skoči dva polja i nađe se pored tvojeg pešaka, možeš ga uzeti 'u prolazu' — kao da se pomerio samo jedno polje. Tapni d6.")
            LearnScenario.PROMOTION -> "Kad beli pešak stigne do osmog reda, može se pretvoriti u bilo koju figuru — gotovo uvek u damu. Tapni e8."
        }

    val movesCountLabel: String
        get() {
            val n = legalMoves.size
            return when (n) {
                0 -> loc("Nema mogućih poteza")
                1 -> loc("1 mogući potez")
                else -> locF("%d mogućih poteza", n)
            }
        }

    private fun refresh() {
        val (b, state) = buildBoardAndState()
        board = b
        legalMoves = MoveGenerator.legalMoves(PieceColor.WHITE, state)
            .filter { it.from == piecePosition }
    }

    private fun buildBoardAndState(): Pair<List<List<ChessPiece?>>, GameState> {
        val b = MutableList(8) { MutableList<ChessPiece?>(8) { null } }
        var castleKS = false
        var castleQS = false
        var epTarget: Position? = null

        when (activeScenario) {
            null -> {
                b[piecePosition.row][piecePosition.col] = ChessPiece(selectedPieceType, PieceColor.WHITE)
                if (selectedPieceType == PieceType.PAWN) {
                    val capRow = piecePosition.row - 1
                    if (capRow >= 0) {
                        if (piecePosition.col > 0) {
                            b[capRow][piecePosition.col - 1] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
                        }
                        if (piecePosition.col < 7) {
                            b[capRow][piecePosition.col + 1] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
                        }
                    }
                }
            }
            LearnScenario.CASTLING -> {
                b[7][4] = ChessPiece(PieceType.KING, PieceColor.WHITE)
                b[7][0] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)
                b[7][7] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)
                castleKS = true
                castleQS = true
            }
            LearnScenario.EN_PASSANT -> {
                b[3][4] = ChessPiece(PieceType.PAWN, PieceColor.WHITE)
                b[3][3] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
                epTarget = Position(2, 3)
            }
            LearnScenario.PROMOTION -> {
                b[1][4] = ChessPiece(PieceType.PAWN, PieceColor.WHITE)
            }
        }

        val state = GameState(
            board = b.map { it.toList() }.toList(),
            currentTurn = PieceColor.WHITE,
            whiteCanCastleKingside = castleKS,
            whiteCanCastleQueenside = castleQS,
            blackCanCastleKingside = false,
            blackCanCastleQueenside = false,
            enPassantTarget = epTarget,
            moveHistory = emptyList(),
            capturedByWhite = emptyList(),
            capturedByBlack = emptyList(),
            status = GameStatus.Playing
        )
        return Pair(state.board, state)
    }

    private fun defaultPosition(piece: PieceType): Position {
        return when (piece) {
            PieceType.PAWN -> Position(6, 4) // e2
            PieceType.KING -> Position(4, 4) // e4
            else -> Position(4, 3) // d4
        }
    }

    private fun scenarioStartPosition(scenario: LearnScenario): Position {
        return when (scenario) {
            LearnScenario.CASTLING -> Position(7, 4) // e1
            LearnScenario.EN_PASSANT -> Position(3, 4) // e5
            LearnScenario.PROMOTION -> Position(1, 4) // e7
        }
    }

    companion object {
        fun emptyBoard(): List<List<ChessPiece?>> {
            return List(8) { List<ChessPiece?>(8) { null } }
        }
    }
}
