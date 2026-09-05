package com.veljkoni.chessko.models

import com.veljkoni.chessko.logic.MoveGenerator

sealed interface DrawReason {
    object Stalemate : DrawReason
    object FiftyMoves : DrawReason
    object Repetition : DrawReason
    object InsufficientMaterial : DrawReason
}

sealed interface GameStatus {
    object Playing : GameStatus
    data class Check(val color: PieceColor) : GameStatus
    data class Checkmate(val color: PieceColor) : GameStatus
    data class Draw(val reason: DrawReason) : GameStatus
    data class Resigned(val color: PieceColor) : GameStatus
}

data class GameState(
    val board: List<List<ChessPiece?>>,
    val currentTurn: PieceColor,
    val whiteCanCastleKingside: Boolean,
    val whiteCanCastleQueenside: Boolean,
    val blackCanCastleKingside: Boolean,
    val blackCanCastleQueenside: Boolean,
    val enPassantTarget: Position?,
    val moveHistory: List<ChessMove>,
    val capturedByWhite: List<ChessPiece>,
    val capturedByBlack: List<ChessPiece>,
    val status: GameStatus = GameStatus.Playing,
    val halfmoveClock: Int = 0,
    val positionHistory: Map<String, Int> = emptyMap(),
    val moveNotations: List<String> = emptyList()
) {

    val positionKey: String
        get() {
            val key = StringBuilder(134)
            for (row in 0..7) {
                for (col in 0..7) {
                    val p = board[row][col]
                    if (p != null) {
                        key.append(if (p.color == PieceColor.WHITE) "W" else "B")
                        key.append(p.type.ordinal)
                    } else {
                        key.append("--")
                    }
                }
            }
            key.append(if (currentTurn == PieceColor.WHITE) "W" else "B")
            key.append(if (whiteCanCastleKingside) "1" else "0")
            key.append(if (whiteCanCastleQueenside) "1" else "0")
            key.append(if (blackCanCastleKingside) "1" else "0")
            key.append(if (blackCanCastleQueenside) "1" else "0")
            if (enPassantTarget != null) {
                key.append(enPassantTarget.col)
            } else {
                key.append("-")
            }
            return key.toString()
        }

    val fen: String
        get() {
            val rows = mutableListOf<String>()
            for (row in 0..7) {
                val rowStr = StringBuilder()
                var empty = 0
                for (col in 0..7) {
                    val piece = board[row][col]
                    if (piece != null) {
                        if (empty > 0) {
                            rowStr.append(empty)
                            empty = 0
                        }
                        val letter = when (piece.type) {
                            PieceType.KING -> "k"
                            PieceType.QUEEN -> "q"
                            PieceType.ROOK -> "r"
                            PieceType.BISHOP -> "b"
                            PieceType.KNIGHT -> "n"
                            PieceType.PAWN -> "p"
                        }
                        rowStr.append(if (piece.color == PieceColor.WHITE) letter.uppercase() else letter)
                    } else {
                        empty++
                    }
                }
                if (empty > 0) {
                    rowStr.append(empty)
                }
                rows.add(rowStr.toString())
            }

            val active = if (currentTurn == PieceColor.WHITE) "w" else "b"

            val castling = StringBuilder()
            if (whiteCanCastleKingside) castling.append("K")
            if (whiteCanCastleQueenside) castling.append("Q")
            if (blackCanCastleKingside) castling.append("k")
            if (blackCanCastleQueenside) castling.append("q")
            val castlingStr = if (castling.isEmpty()) "-" else castling.toString()

            val ep = enPassantTarget?.algebraic ?: "-"

            return "${rows.joinToString("/")} $active $castlingStr $ep 0 1"
        }

    fun applying(move: ChessMove): GameState {
        val baseNotation = getBaseNotation(move, this)
        var s = applyingForSearch(move)

        val key = s.positionKey
        val newHistory = s.positionHistory.toMutableMap()
        newHistory[key] = (newHistory[key] ?: 0) + 1
        s = s.copy(positionHistory = newHistory)

        if (s.halfmoveClock >= 100) {
            return s.copy(
                status = GameStatus.Draw(DrawReason.FiftyMoves),
                moveNotations = s.moveNotations + baseNotation
            )
        }
        if ((s.positionHistory[key] ?: 0) >= 3) {
            return s.copy(
                status = GameStatus.Draw(DrawReason.Repetition),
                moveNotations = s.moveNotations + baseNotation
            )
        }
        if (isInsufficientMaterial(s)) {
            return s.copy(
                status = GameStatus.Draw(DrawReason.InsufficientMaterial),
                moveNotations = s.moveNotations + baseNotation
            )
        }

        val opponentMoves = MoveGenerator.legalMoves(s.currentTurn, s)
        var newStatus: GameStatus = GameStatus.Playing
        if (opponentMoves.isEmpty()) {
            if (MoveGenerator.isInCheck(s.currentTurn, s)) {
                newStatus = GameStatus.Checkmate(s.currentTurn)
            } else {
                newStatus = GameStatus.Draw(DrawReason.Stalemate)
            }
        } else if (MoveGenerator.isInCheck(s.currentTurn, s)) {
            newStatus = GameStatus.Check(s.currentTurn)
        }

        var notation = baseNotation
        when (newStatus) {
            is GameStatus.Checkmate -> notation += "#"
            is GameStatus.Check -> notation += "+"
            else -> {}
        }

        return s.copy(
            status = newStatus,
            moveNotations = s.moveNotations + notation
        )
    }

    fun applyingForSearch(move: ChessMove): GameState {
        val mutableBoard = board.map { it.toMutableList() }.toMutableList()
        val piece = mutableBoard[move.from.row][move.from.col] ?: return this

        val isCapturePre = mutableBoard[move.to.row][move.to.col] != null || move.flag is MoveFlag.EnPassant
        val nextHalfmove = if (piece.type == PieceType.PAWN || isCapturePre) 0 else halfmoveClock + 1

        var nextEnPassantTarget: Position? = null
        val nextCapturedByWhite = capturedByWhite.toMutableList()
        val nextCapturedByBlack = capturedByBlack.toMutableList()

        when (val flag = move.flag) {
            is MoveFlag.Normal -> {
                if (piece.type == PieceType.PAWN && kotlin.math.abs(move.to.row - move.from.row) == 2) {
                    val epRow = (move.from.row + move.to.row) / 2
                    nextEnPassantTarget = Position(epRow, move.from.col)
                }
                val captured = mutableBoard[move.to.row][move.to.col]
                if (captured != null) {
                    if (piece.color == PieceColor.WHITE) nextCapturedByWhite.add(captured)
                    else nextCapturedByBlack.add(captured)
                }
                mutableBoard[move.to.row][move.to.col] = piece
                mutableBoard[move.from.row][move.from.col] = null
            }
            is MoveFlag.CastleKingside -> {
                val row = move.from.row
                mutableBoard[row][6] = ChessPiece(PieceType.KING, piece.color)
                mutableBoard[row][5] = ChessPiece(PieceType.ROOK, piece.color)
                mutableBoard[row][4] = null
                mutableBoard[row][7] = null
            }
            is MoveFlag.CastleQueenside -> {
                val row = move.from.row
                mutableBoard[row][2] = ChessPiece(PieceType.KING, piece.color)
                mutableBoard[row][3] = ChessPiece(PieceType.ROOK, piece.color)
                mutableBoard[row][4] = null
                mutableBoard[row][0] = null
            }
            is MoveFlag.EnPassant -> {
                val capturedPawn = ChessPiece(PieceType.PAWN, piece.color.opposite)
                if (piece.color == PieceColor.WHITE) nextCapturedByWhite.add(capturedPawn)
                else nextCapturedByBlack.add(capturedPawn)
                mutableBoard[move.from.row][move.to.col] = null
                mutableBoard[move.to.row][move.to.col] = piece
                mutableBoard[move.from.row][move.from.col] = null
            }
            is MoveFlag.Promotion -> {
                val captured = mutableBoard[move.to.row][move.to.col]
                if (captured != null) {
                    if (piece.color == PieceColor.WHITE) nextCapturedByWhite.add(captured)
                    else nextCapturedByBlack.add(captured)
                }
                mutableBoard[move.to.row][move.to.col] = ChessPiece(flag.pieceType, piece.color)
                mutableBoard[move.from.row][move.from.col] = null
            }
        }

        var nextWCK = whiteCanCastleKingside
        var nextWCQ = whiteCanCastleQueenside
        var nextBCK = blackCanCastleKingside
        var nextBCQ = blackCanCastleQueenside

        if (piece.type == PieceType.KING) {
            if (piece.color == PieceColor.WHITE) {
                nextWCK = false
                nextWCQ = false
            } else {
                nextBCK = false
                nextBCQ = false
            }
        }
        if (piece.type == PieceType.ROOK) {
            when (move.from) {
                Position(7, 7) -> nextWCK = false
                Position(7, 0) -> nextWCQ = false
                Position(0, 7) -> nextBCK = false
                Position(0, 0) -> nextBCQ = false
            }
        }

        return GameState(
            board = mutableBoard.map { it.toList() }.toList(),
            currentTurn = currentTurn.opposite,
            whiteCanCastleKingside = nextWCK,
            whiteCanCastleQueenside = nextWCQ,
            blackCanCastleKingside = nextBCK,
            blackCanCastleQueenside = nextBCQ,
            enPassantTarget = nextEnPassantTarget,
            moveHistory = moveHistory + move,
            capturedByWhite = nextCapturedByWhite,
            capturedByBlack = nextCapturedByBlack,
            status = status,
            halfmoveClock = nextHalfmove,
            positionHistory = positionHistory,
            moveNotations = moveNotations
        )
    }

    fun kingPosition(color: PieceColor): Position? {
        for (row in 0..7) {
            for (col in 0..7) {
                val p = board[row][col]
                if (p != null && p.type == PieceType.KING && p.color == color) {
                    return Position(row, col)
                }
            }
        }
        return null
    }

    companion object {
        fun fromFEN(fen: String): GameState? {
            val parts = fen.split(" ").filter { it.isNotEmpty() }
            if (parts.size < 2) return null

            // 1. Piece placement
            val board = MutableList(8) { MutableList<ChessPiece?>(8) { null } }
            val ranks = parts[0].split("/")
            if (ranks.size != 8) return null

            for (rowIndex in 0..7) {
                val rank = ranks[rowIndex]
                var colIndex = 0
                for (ch in rank) {
                    if (ch.isDigit()) {
                        val num = ch.digitToInt()
                        colIndex += num
                    } else {
                        val color = if (ch.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                        val type = when (ch.lowercaseChar()) {
                            'k' -> PieceType.KING
                            'q' -> PieceType.QUEEN
                            'r' -> PieceType.ROOK
                            'b' -> PieceType.BISHOP
                            'n' -> PieceType.KNIGHT
                            'p' -> PieceType.PAWN
                            else -> null
                        } ?: return null
                        if (colIndex < 8) {
                            board[rowIndex][colIndex] = ChessPiece(type, color)
                        }
                        colIndex++
                    }
                }
            }

            // 2. Active color
            val turn = if (parts[1] == "b") PieceColor.BLACK else PieceColor.WHITE

            // 3. Castling
            val castling = parts.getOrNull(2) ?: "-"
            val wck = castling.contains("K")
            val wcq = castling.contains("Q")
            val bck = castling.contains("k")
            val bcq = castling.contains("q")

            // 4. En passant
            var ep: Position? = null
            val epStr = parts.getOrNull(3) ?: "-"
            if (epStr != "-") {
                if (epStr.length >= 2) {
                    val col = epStr[0].code - 97 // 'a' = 97
                    val rankDigit = epStr[1].digitToIntOrNull()
                    if (rankDigit != null) {
                        val row = 8 - rankDigit
                        if (col in 0..7 && row in 0..7) {
                            ep = Position(row, col)
                        }
                    }
                }
            }

            // 5. Halfmove clock
            val halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0

            val state = GameState(
                board = board.map { it.toList() }.toList(),
                currentTurn = turn,
                whiteCanCastleKingside = wck,
                whiteCanCastleQueenside = wcq,
                blackCanCastleKingside = bck,
                blackCanCastleQueenside = bcq,
                enPassantTarget = ep,
                moveHistory = emptyList(),
                capturedByWhite = emptyList(),
                capturedByBlack = emptyList(),
                status = GameStatus.Playing,
                halfmoveClock = halfmove
            )
            val initialHistory = mapOf(state.positionKey to 1)
            return state.copy(positionHistory = initialHistory)
        }

        fun initial(): GameState {
            val board = MutableList(8) { MutableList<ChessPiece?>(8) { null } }
            val backRankTypes = listOf(
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
            )
            for (col in 0..7) {
                board[0][col] = ChessPiece(backRankTypes[col], PieceColor.BLACK)
                board[1][col] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
                board[6][col] = ChessPiece(PieceType.PAWN, PieceColor.WHITE)
                board[7][col] = ChessPiece(backRankTypes[col], PieceColor.WHITE)
            }

            val startingBoard = board.map { it.toList() }.toList()
            val state = GameState(
                board = startingBoard,
                currentTurn = PieceColor.WHITE,
                whiteCanCastleKingside = true,
                whiteCanCastleQueenside = true,
                blackCanCastleKingside = true,
                blackCanCastleQueenside = true,
                enPassantTarget = null,
                moveHistory = emptyList(),
                capturedByWhite = emptyList(),
                capturedByBlack = emptyList()
            )
            val initialHistory = mapOf(state.positionKey to 1)
            return state.copy(positionHistory = initialHistory)
        }

        private fun getBaseNotation(move: ChessMove, state: GameState): String {
            val piece = state.board[move.from.row][move.from.col] ?: return move.to.algebraic

            when (move.flag) {
                is MoveFlag.CastleKingside -> return "O-O"
                is MoveFlag.CastleQueenside -> return "O-O-O"
                else -> {}
            }

            val isCapture = state.board[move.to.row][move.to.col] != null || move.flag is MoveFlag.EnPassant
            val dest = move.to.algebraic

            if (piece.type == PieceType.PAWN) {
                var s = if (isCapture) "${move.from.algebraic[0]}x$dest" else dest
                if (move.flag is MoveFlag.Promotion) {
                    s += "=${move.flag.pieceType.srbNotationLetter}"
                }
                return s
            }

            val letter = piece.type.srbNotationLetter
            return if (isCapture) "${letter}x$dest" else "$letter$dest"
        }

        private fun isInsufficientMaterial(s: GameState): Boolean {
            val whitePieces = mutableListOf<PieceType>()
            val blackPieces = mutableListOf<PieceType>()
            var whiteBishopOnLight: Boolean? = null
            var blackBishopOnLight: Boolean? = null

            for (row in 0..7) {
                for (col in 0..7) {
                    val p = s.board[row][col]
                    if (p == null || p.type == PieceType.KING) continue
                    if (p.color == PieceColor.WHITE) {
                        whitePieces.add(p.type)
                        if (p.type == PieceType.BISHOP) {
                            whiteBishopOnLight = (row + col) % 2 == 0
                        }
                    } else {
                        blackPieces.add(p.type)
                        if (p.type == PieceType.BISHOP) {
                            blackBishopOnLight = (row + col) % 2 == 0
                        }
                    }
                }
            }

            val isMinor = { t: PieceType -> t == PieceType.BISHOP || t == PieceType.KNIGHT }

            if (whitePieces.isEmpty() && blackPieces.isEmpty()) return true

            if (whitePieces.isEmpty() && blackPieces.size == 1 && isMinor(blackPieces[0])) return true
            if (blackPieces.isEmpty() && whitePieces.size == 1 && isMinor(whitePieces[0])) return true

            if (whitePieces == listOf(PieceType.BISHOP) && blackPieces == listOf(PieceType.BISHOP) &&
                whiteBishopOnLight != null && blackBishopOnLight != null &&
                whiteBishopOnLight == blackBishopOnLight
            ) {
                return true
            }

            return false
        }
    }
}
