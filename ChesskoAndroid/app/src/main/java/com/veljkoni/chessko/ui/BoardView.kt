package com.veljkoni.chessko.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.logic.HapticManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.veljkoni.chessko.models.*

/**
 * Jedno mesto koje primenjuje prečicu prevlačenjem — zove se iz OBE putanje
 * (obično prevlačenje i naoružan dug pritisak). Bez izdvajanja bi dve kopije
 * istih pragova ćutke otišle u različitim pravcima.
 */
private fun applyBoardSwipe(
    settings: SettingsManager,
    hapticManager: HapticManager,
    swipeX: Float,
    swipeY: Float
) {
    if (kotlin.math.abs(swipeX) > kotlin.math.abs(swipeY)) {
        if (settings.swipeToChangeBoardTheme && kotlin.math.abs(swipeX) > 100f) {
            val themes = BoardTheme.values()
            val currentIdx = themes.indexOfFirst { it.rawValue == settings.boardTheme }
            val nextIdx = if (swipeX < 0) {
                (currentIdx + 1) % themes.size
            } else {
                (currentIdx - 1 + themes.size) % themes.size
            }
            settings.updateBoardTheme(themes[nextIdx].rawValue)
            hapticManager.mediumImpact()
        }
    } else {
        if (settings.swipeToChangePieceStyle && kotlin.math.abs(swipeY) > 100f) {
            val styles = PieceStyle.values()
            val currentIdx = styles.indexOfFirst { it.rawValue == settings.pieceStyle }
            val nextIdx = if (swipeY < 0) {
                (currentIdx + 1) % styles.size
            } else {
                (currentIdx - 1 + styles.size) % styles.size
            }
            settings.updatePieceStyle(styles[nextIdx].rawValue)
            hapticManager.mediumImpact()
        }
    }
}

@Composable
fun BoardView(
    board: List<List<ChessPiece?>>,
    isFlipped: Boolean,
    selectedPosition: Position?,
    legalMoves: List<ChessMove>,
    lastMove: ChessMove?,
    boardTheme: BoardTheme,
    pieceStyle: PieceStyle,
    gameStatus: GameStatus = GameStatus.Playing,
    showCoordinates: Boolean = true,
    showLastMoveHighlight: Boolean = true,
    showLegalMoves: Boolean = true,
    /**
     * Precica prevlacenjem (tema table / stil figura) pripada IGRACKOJ tabli.
     * Table ugradjene u lekciju je ne dobijaju — isto ime i isto znacenje kao
     * iOS-ov `BoardView.allowsStyleSwipe` (`Chessko/Views/BoardView.swift:21`,
     * komentar: „Boards embedded in a scrolling lesson must not swallow vertical
     * drags — the page has to scroll"), i istih **pet** pozivnih mesta: staticka
     * tabla bloka, istrazivac figura i tri kartice vezbi.
     *
     * Na Androidu razlog nije gutanje skrola (od Faze 7 prazno polje ne trosi
     * nista) nego to sto lekcija ne sme da menja globalno podesavanje — iOS je
     * tu granicu povukao ranije, Android je nije imao.
     */
    allowsStyleSwipe: Boolean = true,
    onTap: (Position) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticManager = remember { HapticManager(context.applicationContext) }

    // Identify king in check position
    val checkKingPosition = remember(gameStatus, board) {
        val inCheckColor = when (gameStatus) {
            is GameStatus.Check -> gameStatus.color
            is GameStatus.Checkmate -> gameStatus.color
            else -> null
        }
        if (inCheckColor != null) {
            var found: Position? = null
            for (r in 0..7) {
                for (c in 0..7) {
                    val p = board[r][c]
                    if (p?.type == PieceType.KING && p.color == inCheckColor) {
                        found = Position(r, c)
                        break
                    }
                }
            }
            found
        } else null
    }

    var draggedPiece by remember { mutableStateOf<ChessPiece?>(null) }
    var dragStartPos by remember { mutableStateOf<Position?>(null) }
    var dragTouchOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF17234F))
    ) {
        val squareSize = maxWidth / 8
        val squareSizePx = with(LocalDensity.current) { squareSize.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // NE koristi `detectDragGestures`. Taj detektor troši pokazivač
                // ČIM se pređe touch slop, bez obzira na to da li gest uopšte
                // ima šta da radi — pa tabla proguta vertikalni skrol roditelja
                // i lekcija sa dve table prestane da se skroluje (zatečeno pre
                // Faze 6c; ta faza ga je samo učinila vidljivim).
                //
                // Ovde se pokazivač troši u TAČNO dva slučaja: kad gest počinje
                // na polju sa figurom (jedini koji stvarno vuče figuru), i kad
                // je prečica naoružana dugim pritiskom (v. Fazu 1 ispod). Inače
                // se nijedan `change` ne troši, pa gest propada roditeljskom
                // `scroll`-u; prečica prevlačenjem (tema table / stil figura) se
                // i dalje prati, ali se odustaje čim skrol preuzme pokret — vidi
                // `PointerEventPass.Final` u petlji ispod za to ZAŠTO se
                // odustajanje mora čitati na drugom prolazu.
                .pointerInput(board, isFlipped, squareSizePx) {
                    awaitEachGesture {
                        // `requireUnconsumed = false`: `clickable` na samom polju
                        // troši `down` (tako Compose radi tap), a taj tap i dalje
                        // mora da radi — isto kao sa `detectDragGestures`.
                        val down = awaitFirstDown(requireUnconsumed = false)

                        val startDisplayCol = (down.position.x / squareSizePx).toInt().coerceIn(0, 7)
                        val startDisplayRow = (down.position.y / squareSizePx).toInt().coerceIn(0, 7)
                        val startRow = if (isFlipped) 7 - startDisplayRow else startDisplayRow
                        val startCol = if (isFlipped) 7 - startDisplayCol else startDisplayCol
                        val startPos = Position(startRow, startCol)
                        val pieceOnSquare = board[startRow][startCol]

                        if (pieceOnSquare == null) {
                            // --- Nema figure: ne troši ništa (dok se prečica ne naoruža). ---
                            if (!allowsStyleSwipe) return@awaitEachGesture

                            val settings = SettingsManager.getInstance(context)
                            if (!settings.swipeToChangeBoardTheme && !settings.swipeToChangePieceStyle) {
                                return@awaitEachGesture
                            }

                            var swipeX = 0f
                            var swipeY = 0f

                            // FAZA 1 — dug pritisak BEZ pokreta naoružava prečicu.
                            //
                            // Zašto uopšte postoji: obično prevlačenje po praznom
                            // polju ĆUTI svuda gde je tabla u `verticalScroll`-u
                            // (roditelj potroši pokret na svom touch slop-u ~20 px,
                            // a prečici treba 100 px). Izmereno na emulatoru, ne
                            // izvedeno: ekran Igra u portretu — uspravno prevlačenje
                            // ne menja `pieceStyle`, vodoravno menja `boardTheme`.
                            // Dok čekamo hold ne trošimo NIŠTA, pa skrol radi
                            // normalno; čim se prst pomeri preko touch slop-a
                            // odustaje se od naoružavanja i ide zatečena putanja.
                            //
                            // Gejtovano na `swipeToChangePieceStyle`, ne na „bilo
                            // koji od dva prekidača": naoružavanje postoji ISKLJUČIVO
                            // zbog uspravne ose — vodoravnu nijedan roditelj ne traži,
                            // pa joj hold ne donosi ništa. Sa uključenom samo temom
                            // gejt na „bilo koji" bi na uspravan pokret vibrirao,
                            // ukrao skrol i ne bi uradio ništa.
                            val slop = viewConfiguration.touchSlop
                            var armed = false
                            if (settings.swipeToChangePieceStyle) {
                                // `null` = isteklo (naoružano); `true` = prst podignut
                                // pre isteka (gest je GOTOV); `false` = krenuo pokret
                                // (nastavlja se zatečenom putanjom).
                                val liftedBeforeHold =
                                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val ch = ev.changes.firstOrNull { it.id == down.id }
                                                ?: return@withTimeoutOrNull true
                                            if (!ch.pressed) return@withTimeoutOrNull true
                                            // Potrošeno na `Initial` prolazu. Roditeljski
                                            // skrol se ovde NE vidi (na `Main` prolazu dete
                                            // ide pre roditelja) — zato zatečena petlja
                                            // ispod isti događaj čita još jednom na
                                            // `Final`. Faza 1 to ne mora: odustaje na prvi
                                            // pokret preko touch slop-a, a roditelj ne može
                                            // da potroši pre nego što i sam pređe isti slop.
                                            if (ch.isConsumed) return@withTimeoutOrNull false
                                            val d = ch.positionChange()
                                            swipeX += d.x
                                            swipeY += d.y
                                            if (kotlin.math.abs(swipeX) > slop ||
                                                kotlin.math.abs(swipeY) > slop
                                            ) {
                                                return@withTimeoutOrNull false
                                            }
                                        }
                                    }

                                // Prst podignut pre nego što se navršio dug pritisak:
                                // gest je gotov i MORA se izaći iz `awaitEachGesture`.
                                // Pad u zatečenu petlju ispod bi značio čekanje novog
                                // događaja za pokazivač koji više ne postoji, pa bi
                                // `block()` ostao u toku; `awaitEachGesture` posle
                                // `block()` čeka da SVI pokazivači budu gore
                                // (`awaitAllPointersUp`), pa bi SLEDEĆI gest ovaj čvor
                                // preskočio u celosti. Izmereno pre popravke: tap na
                                // prazno polje pa prevlačenje figure — potez se ne
                                // odigra (brojač ostane isti), a prevlačenje procuri
                                // roditeljskom skrolu i strana se pomeri.
                                if (liftedBeforeHold == true) return@awaitEachGesture
                                armed = liftedBeforeHold == null
                            }

                            if (armed) {
                                // Naoružano: od sada se troši SVAKI pokret, pa
                                // roditeljski skrol više ne može da preuzme gest —
                                // zato prečica radi i tamo gde obično prevlačenje ćuti.
                                hapticManager.lightImpact()
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    // REDOSLED JE BITAN, isto kao u grani prevlačenja
                                    // figure ispod: `positionChange()` vraća
                                    // `Offset.Zero` za već potrošen `change`.
                                    val d = ch.positionChange()
                                    ch.consume()
                                    swipeX += d.x
                                    swipeY += d.y
                                }
                                applyBoardSwipe(settings, hapticManager, swipeX, swipeY)
                                return@awaitEachGesture
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@awaitEachGesture
                                // Prst podignut — proveri PRE `isConsumed`, jer
                                // `clickable` troši baš taj `up` kad je gest bio tap.
                                if (!change.pressed) break
                                // Neko je potrošio još na `Initial` prolazu.
                                if (change.isConsumed) return@awaitEachGesture
                                val delta = change.positionChange()
                                swipeX += delta.x
                                swipeY += delta.y

                                // ISTI dogadjaj jos jednom, na `Final` prolazu.
                                // Na `Main` prolazu dete UVEK ide PRE roditelja, pa
                                // roditeljski `verticalScroll` jos nije ni stigao da
                                // potrosi — provera `isConsumed` gore ga zato nikad ne
                                // vidi. Bez ovog drugog citanja jedan isti pokret i
                                // skroluje ekran I menja stil figura: izmereno na
                                // emulatoru (lekcija „Tabla, figure i kretanje",
                                // prevlacenje po praznom polju: `pieceStyle` metal →
                                // flat, a lekcija se u istom potezu odskrolovala).
                                // `Final` prolaz ide OBRNUTIM redom (roditelj pa dete),
                                // pa je tu potrosnja skrola vidljiva.
                                val finalChange = awaitPointerEvent(PointerEventPass.Final)
                                    .changes.firstOrNull { it.id == down.id }
                                    ?: return@awaitEachGesture
                                if (finalChange.isConsumed) return@awaitEachGesture
                            }

                            applyBoardSwipe(settings, hapticManager, swipeX, swipeY)
                            return@awaitEachGesture
                        }

                        // --- Figura postoji: ponašanje kao i pre. ---
                        // Slop se čeka da običan TAP ne bi startovao prevlačenje
                        // (tap obrađuje `clickable` na polju; bez ovoga bi se
                        // `onTap` okinuo dvaput i figura bi se odmah odselektovala).
                        var overSlop = Offset.Zero
                        var drag: PointerInputChange?
                        do {
                            drag = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                overSlop = over
                            }
                        } while (drag != null && !drag.isConsumed)
                        if (drag == null) return@awaitEachGesture

                        draggedPiece = pieceOnSquare
                        dragStartPos = startPos
                        dragTouchOffset = down.position + overSlop
                        onTap(startPos)

                        val completed = drag(drag.id) { change ->
                            // REDOSLED JE BITAN: `positionChange()` vraca
                            // `Offset.Zero` za vec potrosen `change`, pa se
                            // pomeraj mora procitati PRE `consume()`. Obrnuto
                            // figura ostaje zalepljena za polazno polje i potez
                            // se nikad ne odigra (izmereno na emulatoru).
                            // `detectDragGestures` je interno radio isti
                            // redosled.
                            val delta = change.positionChange()
                            change.consume()
                            dragTouchOffset += delta
                        }

                        if (completed) {
                            val endDisplayCol = (dragTouchOffset.x / squareSizePx).toInt().coerceIn(0, 7)
                            val endDisplayRow = (dragTouchOffset.y / squareSizePx).toInt().coerceIn(0, 7)
                            val endRow = if (isFlipped) 7 - endDisplayRow else endDisplayRow
                            val endCol = if (isFlipped) 7 - endDisplayCol else endDisplayCol
                            val targetPos = Position(endRow, endCol)
                            if (targetPos != startPos) {
                                onTap(targetPos)
                            }
                        }
                        draggedPiece = null
                        dragStartPos = null
                    }
                }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (displayRow in 0..7) {
                // If board is flipped, we reverse the row index
                val boardRow = if (isFlipped) 7 - displayRow else displayRow
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (displayCol in 0..7) {
                        val boardCol = if (isFlipped) 7 - displayCol else displayCol
                        val pos = Position(boardRow, boardCol)
                        val piece = board[boardRow][boardCol]
                        val isSelected = selectedPosition == pos
                        val isLegal = legalMoves.any { it.to == pos }
                        val isLast = lastMove != null && (lastMove.from == pos || lastMove.to == pos)
                        val isInCheck = (pos == checkKingPosition)
                        val isBeingDragged = (dragStartPos == pos && draggedPiece != null)

                        SquareView(
                            position = pos,
                            piece = piece,
                            isSelected = isSelected,
                            isLegalMove = isLegal,
                            isLastMove = isLast,
                            isInCheck = isInCheck,
                            hidePiece = isBeingDragged,
                            isLight = (boardRow + boardCol) % 2 == 0,
                            isBottomEdge = if (isFlipped) displayRow == 0 else displayRow == 7,
                            isLeftEdge = if (isFlipped) displayCol == 7 else displayCol == 0,
                            isFlipped = isFlipped,
                            boardTheme = boardTheme,
                            pieceStyle = pieceStyle,
                            showCoordinates = showCoordinates,
                            showLastMoveHighlight = showLastMoveHighlight,
                            showLegalMoves = showLegalMoves,
                            lastMove = lastMove,
                            squareSize = squareSize,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onTap(pos) }
                        )
                    }
                }
            }
        }

        // Floating Piece Overlay during Drag & Drop
        if (draggedPiece != null) {
            val p = draggedPiece!!
            val assetName = getAssetName(p, pieceStyle, boardTheme)
            val metalBrush = if (pieceStyle == PieceStyle.METAL) {
                if (p.color == PieceColor.WHITE) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFE0E2E5),
                            Color(0xFFB9BFC5),
                            Color(0xFFF1F3F5),
                            Color(0xFF8B939C)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4A4D54),
                            Color(0xFF2B2D32),
                            Color(0xFF1A1B1E),
                            Color(0xFF383A3E),
                            Color(0xFF111214)
                        )
                    )
                }
            } else null

            AsyncImage(
                model = "file:///android_asset/pieces/$assetName.svg",
                contentDescription = null,
                modifier = Modifier
                    .size(squareSize)
                    .offset {
                        IntOffset(
                            (dragTouchOffset.x - squareSizePx / 2).roundToInt(),
                            (dragTouchOffset.y - squareSizePx / 2).roundToInt()
                        )
                    }
                    .scale(1.18f)
                    .graphicsLayer {
                        shadowElevation = 8.dp.toPx()
                        if (metalBrush != null) {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                    }
                    .then(
                        if (metalBrush != null) {
                            Modifier.drawWithContent {
                                drawContent()
                                drawRect(brush = metalBrush, blendMode = BlendMode.SrcIn)
                            }
                        } else Modifier
                    )
            )
        }

        // Victory Confetti Overlay on Checkmate
        ConfettiOverlay(isTriggered = gameStatus is GameStatus.Checkmate)
        }
    }
}

@Composable
fun SquareView(
    position: Position,
    piece: ChessPiece?,
    isSelected: Boolean,
    isLegalMove: Boolean,
    isLastMove: Boolean,
    isInCheck: Boolean = false,
    hidePiece: Boolean = false,
    isLight: Boolean,
    isBottomEdge: Boolean,
    isLeftEdge: Boolean,
    isFlipped: Boolean,
    boardTheme: BoardTheme,
    pieceStyle: PieceStyle,
    showCoordinates: Boolean,
    showLastMoveHighlight: Boolean,
    showLegalMoves: Boolean,
    lastMove: ChessMove?,
    squareSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // Determine square base background
    val baseModifier = when {
        !isLight && (boardTheme == BoardTheme.MIDNIGHT_AURORA || boardTheme == BoardTheme.CYBER_LAVENDER) -> {
            val centerColor = if (boardTheme == BoardTheme.MIDNIGHT_AURORA) Color(0xFF3A475C) else Color(0xFF4F3B8C)
            Modifier.background(
                Brush.radialGradient(
                    colors = listOf(centerColor, boardTheme.darkColor)
                )
            )
        }
        else -> {
            Modifier.background(if (isLight) boardTheme.lightColor else boardTheme.darkColor)
        }
    }

    val animPercent = remember { Animatable(0f) }
    LaunchedEffect(lastMove) {
        if (lastMove != null && lastMove.to == position) {
            animPercent.snapTo(0f)
            animPercent.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
        } else {
            animPercent.snapTo(1f)
        }
    }

    // Selection pulse animation
    val selectionTransition = rememberInfiniteTransition(label = "selectionPulse")
    val selectionScale by selectionTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "selectionScale"
    )

    // Check pulse animation
    val checkTransition = rememberInfiniteTransition(label = "checkPulse")
    val checkAlpha by checkTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "checkAlpha"
    )

    Box(
        modifier = modifier
            .then(baseModifier)
            .aspectRatio(1f)
    ) {
        // King in Check Red Aura
        if (isInCheck) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFEF4444).copy(alpha = checkAlpha),
                                Color(0xFFDC2626).copy(alpha = checkAlpha * 0.5f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(2.dp, Color(0xFFEF4444).copy(alpha = checkAlpha))
            )
        }

        // Last-move highlight
        if (isLastMove && showLastMoveHighlight && !isInCheck) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Yellow.copy(alpha = 0.40f))
            )
        }

        // Selection highlight
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF00D2FF).copy(alpha = 0.28f))
                    .border(1.5.dp, Color(0xFF00D2FF).copy(alpha = 0.75f))
            )
        }

        // Animated Legal Move Indicators
        val dotAlpha by animateFloatAsState(
            targetValue = if (isLegalMove && showLegalMoves) 1f else 0f,
            animationSpec = tween(180),
            label = "dotAlpha"
        )
        val dotScale by animateFloatAsState(
            targetValue = if (isLegalMove && showLegalMoves) 1f else 0.4f,
            animationSpec = tween(180),
            label = "dotScale"
        )

        if (dotAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = dotAlpha
                        scaleX = dotScale
                        scaleY = dotScale
                    }
            ) {
                if (piece != null) {
                    // Capture ring
                    Canvas(modifier = Modifier.fillMaxSize().padding(1.dp)) {
                        val strokeWidth = size.width * 0.08f
                        drawRect(
                            color = Color(0xFF00D2FF).copy(alpha = 0.65f),
                            style = Stroke(width = strokeWidth)
                        )
                    }
                } else {
                    // Empty-square dot
                    // 0,55 je iOS vrednost (`SquareView.swift:182`). Android je nosio 0,45 —
                    // isti pomak od -0,10 koji je imao i prsten iznad. Plan faze 6d-2 je oba
                    // broja zamenio mestima i zato je trazio popravku samo jednog; drugi je
                    // ispravljen posto je izvrsilac razliku prijavio umesto da je precuti.
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.28f)
                            .clip(CircleShape)
                            .background(Color(0xFF00D2FF).copy(alpha = 0.55f))
                            .align(Alignment.Center)
                    )
                }
            }
        }

        // Render piece
        if (piece != null && !hidePiece) {
            val assetName = getAssetName(piece, pieceStyle, boardTheme)
            val metalBrush = if (pieceStyle == PieceStyle.METAL) {
                if (piece.color == PieceColor.WHITE) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFE0E2E5),
                            Color(0xFFB9BFC5),
                            Color(0xFFF1F3F5),
                            Color(0xFF8B939C)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4A4D54),
                            Color(0xFF2B2D32),
                            Color(0xFF1A1B1E),
                            Color(0xFF383A3E),
                            Color(0xFF111214)
                        )
                    )
                }
            } else null

            val translationX: androidx.compose.ui.unit.Dp
            val translationY: androidx.compose.ui.unit.Dp

            if (lastMove != null && lastMove.to == position && animPercent.value < 1f) {
                val dx = if (isFlipped) {
                    (lastMove.to.col - lastMove.from.col) * squareSize.value
                } else {
                    (lastMove.from.col - lastMove.to.col) * squareSize.value
                }
                val dy = if (isFlipped) {
                    (lastMove.to.row - lastMove.from.row) * squareSize.value
                } else {
                    (lastMove.from.row - lastMove.to.row) * squareSize.value
                }
                translationX = (dx * (1f - animPercent.value)).dp
                translationY = (dy * (1f - animPercent.value)).dp
            } else {
                translationX = 0.dp
                translationY = 0.dp
            }

            val imageModifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .offset(x = translationX, y = translationY)
                .scale(if (isSelected) selectionScale else 1.0f)
                .then(
                    if (metalBrush != null) {
                        Modifier
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = metalBrush, blendMode = BlendMode.SrcIn)
                            }
                    } else Modifier
                )

            AsyncImage(
                model = "file:///android_asset/pieces/$assetName.svg",
                contentDescription = null,
                modifier = imageModifier
            )
        }

        // Coordinates
        if (showCoordinates) {
            val labelColor = if (isLight) boardTheme.darkColor else boardTheme.lightColor
            
            // File letter (a-h)
            if (isBottomEdge) {
                val fileChar = ('a' + position.col)
                Text(
                    text = fileChar.toString(),
                    color = labelColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                )
            }

            // Rank number (1-8)
            if (isLeftEdge) {
                val rankNum = (8 - position.row)
                Text(
                    text = rankNum.toString(),
                    color = labelColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                )
            }
        }
    }
}

fun getAssetName(piece: ChessPiece, style: PieceStyle, theme: BoardTheme): String {
    val color = if (piece.color == PieceColor.WHITE) "white" else "black"
    val type = piece.type.name.lowercase()
    val isDarkTheme = theme == BoardTheme.MIDNIGHT_AURORA || theme == BoardTheme.CYBER_LAVENDER

    return when (style) {
        PieceStyle.FLAT -> {
            if (color == "black" && isDarkTheme) {
                "piece_black_${type}_flat_outlined"
            } else {
                "piece_${color}_${type}_flat"
            }
        }
        PieceStyle.SIMPLE_THIN -> "piece_${color}_${type}_simple_thin"
        PieceStyle.NEON -> "piece_${color}_${type}_neo"
        PieceStyle.WOOD -> "piece_${color}_${type}_wood"
        PieceStyle.GLASS -> "piece_${color}_${type}_glass"
        PieceStyle.GAMEROOM -> "piece_${color}_${type}_gameroom"
        else -> "piece_${color}_${type}"
    }
}

@Composable
fun ConfettiOverlay(isTriggered: Boolean) {
    if (!isTriggered) return

    val particles = remember {
        List(50) {
            ConfettiParticle(
                x = (0..1000).random() / 1000f,
                y = -(0..500).random() / 1000f,
                size = (10..22).random().toFloat(),
                speedY = (0.005f + (0..100).random() / 10000f),
                speedX = (-0.004f + (0..80).random() / 10000f),
                rotation = (0..360).random().toFloat(),
                rotationSpeed = (-6f + (0..120).random() / 10f),
                color = listOf(
                    Color(0xFFFFD700), // Gold
                    Color(0xFF00D2FF), // Cyan
                    Color(0xFFFF4081), // Pink
                    Color(0xFF10B981), // Emerald
                    Color(0xFFA855F7), // Purple
                    Color.White
                ).random()
            )
        }
    }

    var frameTime by remember { mutableStateOf(0L) }
    LaunchedEffect(isTriggered) {
        while (isTriggered) {
            withFrameNanos { frameTime = it }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
        val w = size.width
        val h = size.height

        for (p in particles) {
            p.y += p.speedY
            p.x += p.speedX
            p.rotation += p.rotationSpeed

            if (p.y > 1.1f) {
                p.y = -0.1f
                p.x = (0..1000).random() / 1000f
            }

            val px = p.x * w
            val py = p.y * h

            withTransform({
                translate(left = px, top = py)
                rotate(degrees = p.rotation)
            }) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(-p.size / 2, -p.size / 2),
                    size = Size(p.size, p.size * 0.55f)
                )
            }
        }
    }
}

private class ConfettiParticle(
    var x: Float,
    var y: Float,
    val size: Float,
    val speedY: Float,
    val speedX: Float,
    var rotation: Float,
    val rotationSpeed: Float,
    val color: Color
)

