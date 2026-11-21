package com.veljkoni.chessko.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.models.GameStatus
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.online.OnlineCloseConfirm
import com.veljkoni.chessko.online.OnlineLink
import com.veljkoni.chessko.online.OnlineNotice
import com.veljkoni.chessko.online.OnlineResult
import com.veljkoni.chessko.online.OnlineState
import com.veljkoni.chessko.online.OnlineViewModel
import com.veljkoni.chessko.ui.theme.ButtonShape
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.DialogShape
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.ui.theme.dsCard
import com.veljkoni.chessko.ui.theme.dsPrimaryButtonColors
import com.veljkoni.chessko.ui.theme.dsSecondaryButtonColors
import com.veljkoni.chessko.viewmodels.GameViewModel

// Igra preko interneta (Android strana iOS `OnlineGameView`): JEDNO celoekransko
// odrediste za ceo tok -- lobi (`OnlineLobbyView`) dok nema partije, tabla dok
// traje, rezultat posle kraja. Otvaranje zove `open()`, zatvaranje `close()`.
// Masina i veza zive u `OnlineViewModel`; ovde se samo crta. Ovaj ekran je jedino
// mesto (uz `MainActivity` kad postoji sacuvana partija) koje sme da napravi
// `OnlineViewModel`.

/**
 * Odrediste. Pozivalac drzi `if (show) OnlineGameDestination(onDismiss = { show = false })`.
 * Zatvaranje usred partije pita „Predati partiju?" (ili, bez veze, kaze da partija ceka).
 */
@Composable
fun OnlineGameDestination(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm = remember { OnlineViewModel.get(app) }
    val game = vm.game

    var showCloseConfirm by remember { mutableStateOf(false) }
    // Varijanta potvrde zatvaranja, zamrznuta pri otvaranju dijaloga.
    var closeConfirm by remember { mutableStateOf(OnlineCloseConfirm.GAME_WAITS) }
    var showResignConfirm by remember { mutableStateOf(false) }
    var showAnalysis by remember { mutableStateOf(false) }

    DisposableEffect(vm) {
        vm.destinationShowing = true
        vm.open()
        // Samo oznaka da ekran nestaje; veza se NE gasi (rotacija u lobiju ne sme da
        // izgubi poziv na cekanju) -- gasi je `close()` kroz izricito zatvaranje.
        onDispose { vm.destinationShowing = false }
    }

    // Obavestenje se prikazuje ~4 s pa se brise (isto kao iOS).
    LaunchedEffect(vm.notice) {
        if (vm.notice != null) {
            kotlinx.coroutines.delay(4000)
            vm.notice = null
        }
    }

    val state = vm.state
    val isPlaying = state is OnlineState.Playing
    val showsBoard = when (state) {
        is OnlineState.Playing, is OnlineState.Ended -> true
        OnlineState.Idle, OnlineState.Connecting -> vm.hasActiveGame
        else -> false
    }

    val closeNow = {
        vm.close()
        onDismiss()
    }
    val requestClose = {
        if (isPlaying) {
            closeConfirm = OnlineCloseConfirm.of(game.onlineCanMove)
            showCloseConfirm = true
        } else {
            closeNow()
        }
    }

    FullScreenDestination(onDismiss = requestClose) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = DS.Space.l)) {
            StepHeader(title = loc("Igraj preko interneta"), onClose = requestClose)
            if (showsBoard) {
                OnlineGameContent(
                    vm = vm,
                    game = game,
                    onResign = { showResignConfirm = true },
                    onAnalyze = { showAnalysis = true },
                    onClose = closeNow
                )
            } else {
                OnlineLobbyView(vm = vm, modifier = Modifier.weight(1f))
            }
        }
        if (game.showPromotion && showsBoard) PromotionOverlay(game)

        if (showAnalysis) {
            FullScreenDestination(onDismiss = { showAnalysis = false }) {
                AnalysisView(viewModel = game, onClose = { showAnalysis = false })
            }
        }
    }

    // Zatvaranje usred partije: predaja se nudi samo dok je veza ziva; inace bi
    // `resign` bio tihi no-op a ekran bi se zatvorio. Bez veze partija ceka
    // (sacuvana je; prijatelj vidi „izgubio vezu"). Varijanta je zamrznuta pri otvaranju:
    // veza koja se vrati dok je dijalog otvoren ne sme pretvoriti „Zatvori" u „Predaj".
    if (showCloseConfirm) {
        val offersResign = closeConfirm == OnlineCloseConfirm.RESIGN
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = {
                Text(
                    text = if (offersResign) loc("Predati partiju?") else loc("Partija će te čekati."),
                    color = DS.ink,
                    fontWeight = FontWeight.Bold
                )
            },
            confirmButton = {
                if (offersResign) {
                    TextButton(onClick = {
                        showCloseConfirm = false
                        // Veza je u medjuvremenu pala: bez predaje, partija ceka.
                        if (closeConfirm.resignsOnConfirm(game.onlineCanMove)) vm.resign()
                        closeNow()
                    }) { Text(text = loc("Predaj"), color = DS.danger, fontWeight = FontWeight.Bold) }
                } else {
                    TextButton(onClick = {
                        showCloseConfirm = false
                        closeNow()
                    }) { Text(text = loc("Zatvori"), color = DS.accent, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text(text = loc("Nastavi"), color = DS.accent)
                }
            },
            containerColor = DS.surface,
            shape = DialogShape
        )
    }
    if (showResignConfirm) {
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text(text = loc("Predati partiju?"), color = DS.ink, fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    showResignConfirm = false
                    vm.resign()
                }) { Text(text = loc("Predaj"), color = DS.danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) {
                    Text(text = loc("Nastavi"), color = DS.accent)
                }
            },
            containerColor = DS.surface,
            shape = DialogShape
        )
    }
}

@Composable
private fun OnlineGameContent(
    vm: OnlineViewModel,
    game: GameViewModel,
    onResign: () -> Unit,
    onAnalyze: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val activeTheme = remember(settings.boardTheme) {
        BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
    }
    val activeStyle = remember(settings.pieceStyle) {
        PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
    }
    val isTablet = isTabletWindow()
    val interactive = game.onlineCanMove && game.onlineResult == null && !game.isGameOver

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val side = stepBoardSide(maxWidth, maxHeight, isTablet, isLandscape)
        val board: @Composable () -> Unit = {
            BoardView(
                board = game.gameState.board,
                isFlipped = game.isFlipped,
                selectedPosition = game.selectedPosition,
                legalMoves = game.legalMovesForSelected,
                lastMove = game.lastMove,
                gameStatus = game.gameState.status,
                boardTheme = activeTheme,
                pieceStyle = activeStyle,
                showCoordinates = settings.showCoordinates,
                showLastMoveHighlight = settings.showLastMoveHighlight,
                showLegalMoves = settings.showLegalMoves,
                onTap = { pos -> if (interactive) game.tap(pos) },
                modifier = Modifier
                    .size(side)
                    .alpha(if (interactive || game.isGameOver) 1f else 0.6f)
                    .testTag("online.board")
            )
        }
        val footer: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(DS.Space.s)) {
                OnlineStatusArea(vm, game, onAnalyze, onClose)
                OnlineActions(vm, game, onResign)
            }
        }
        if (isTablet && isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(DS.Space.xl, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.padding(vertical = DS.Space.xl)) { board() }
                Column(
                    modifier = Modifier
                        .width(TabletPanelWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = DS.Space.xl),
                    verticalArrangement = Arrangement.spacedBy(DS.Space.m)
                ) {
                    FriendCard(game, activeStyle)
                    MyCard(vm, game, activeStyle)
                    footer()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = DS.Space.l),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = side).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DS.Space.m),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FriendCard(game, activeStyle)
                    board()
                    MyCard(vm, game, activeStyle)
                    footer()
                }
            }
        }
    }
}

// ── Kartice ───────────────────────────────────────────────────────────────────

private fun isActive(game: GameViewModel, color: PieceColor): Boolean =
    game.gameState.currentTurn == color && !game.isGameOver && game.onlineResult == null

private fun isInCheck(game: GameViewModel, color: PieceColor): Boolean =
    (game.gameState.status as? GameStatus.Check)?.color == color

private fun captured(game: GameViewModel, color: PieceColor) =
    if (color == PieceColor.WHITE) game.gameState.capturedByWhite else game.gameState.capturedByBlack

@Composable
private fun FriendCard(game: GameViewModel, pieceStyle: PieceStyle) {
    val color = if (game.playerColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
    val active = isActive(game, color)
    PlayerCard(
        color = color,
        name = loc("Prijatelj"),
        pieceStyle = pieceStyle,
        status = if (active) PlayerCardStatus.ToMove(inCheck = isInCheck(game, color)) else null,
        isActive = active,
        materialAdvantage = getMaterialAdvantage(game.gameState, color),
        capturedPieces = captured(game, color)
    )
}

@Composable
private fun MyCard(vm: OnlineViewModel, game: GameViewModel, pieceStyle: PieceStyle) {
    val color = game.playerColor
    val active = isActive(game, color)
    val result = (vm.state as? OnlineState.Ended)?.result
    val status: PlayerCardStatus? = when {
        result != null -> PlayerCardStatus.Result(
            when (result) {
                OnlineResult.I_RESIGNED, OnlineResult.OPPONENT_RESIGNED, OnlineResult.BOARD -> getStatusMessage(game)
                OnlineResult.DRAW_AGREED -> loc("Remi – dogovor.")
                OnlineResult.ABORTED -> loc("Partija je prekinuta.")
                // Privremeno do Task-a 10 (pravi tekstovi za kraj na vreme).
                OnlineResult.I_FLAGGED, OnlineResult.OPPONENT_FLAGGED -> getStatusMessage(game)
                OnlineResult.FIRST_MOVE_EXPIRED -> loc("Partija je prekinuta.")
            }
        )
        active -> PlayerCardStatus.ToMove(inCheck = isInCheck(game, color))
        else -> null
    }
    PlayerCard(
        color = color,
        name = loc("Ti"),
        pieceStyle = pieceStyle,
        status = status,
        isActive = active,
        materialAdvantage = getMaterialAdvantage(game.gameState, color),
        capturedPieces = captured(game, color)
    )
}

// ── Traka stanja ──────────────────────────────────────────────────────────────

@Composable
private fun StatusLine(text: String, showsProgress: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DS.Space.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showsProgress) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DS.inkMuted)
        Text(
            text = text,
            color = DS.inkMuted,
            style = Type.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("online.status")
        )
    }
}

@Composable
private fun OnlineStatusArea(vm: OnlineViewModel, game: GameViewModel, onAnalyze: () -> Unit, onClose: () -> Unit) {
    val state = vm.state
    // Server trazi noviju verziju: trajna poruka umesto beskrajnog „Povezujem…".
    if (state !is OnlineState.Ended && vm.updateRequired) {
        StatusLine(loc("Za igru preko interneta potrebna je novija verzija aplikacije."), showsProgress = false)
        return
    }
    when (state) {
        is OnlineState.Ended -> EndedArea(vm, game, onAnalyze, onClose)
        is OnlineState.Playing -> {
            val connection = when {
                state.link == OnlineLink.RECONNECTING -> loc("Ponovo se povezujem…")
                state.opponentAway -> loc("Prijatelj je izgubio vezu, čekam…")
                else -> null
            }
            val notice = vm.notice?.takeIf { it != OnlineNotice.CODE_TAKEN }
            when {
                connection != null -> StatusLine(connection, showsProgress = true)
                state.drawOfferedByOpponent -> DrawOfferBox(vm)
                notice != null -> StatusLine(onlineNoticeText(notice), showsProgress = false)
                else -> StatusLine(getStatusMessage(game), showsProgress = false)
            }
        }
        // Sacuvana partija, veza se jos uspostavlja.
        else -> StatusLine(loc("Povezujem…"), showsProgress = true)
    }
}

@Composable
private fun DrawOfferBox(vm: OnlineViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().dsCard().padding(DS.Space.m),
        verticalArrangement = Arrangement.spacedBy(DS.Space.s),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = loc("Prijatelj nudi remi."), color = DS.ink, style = Type.body, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(DS.Space.s), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { vm.answerDraw(true) },
                shape = ButtonShape,
                colors = dsPrimaryButtonColors(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text(text = loc("Prihvati"), style = Type.body, fontWeight = FontWeight.Bold) }
            Button(
                onClick = { vm.answerDraw(false) },
                shape = ButtonShape,
                colors = dsSecondaryButtonColors(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text(text = loc("Odbij"), style = Type.body, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun EndedArea(vm: OnlineViewModel, game: GameViewModel, onAnalyze: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = DS.Space.s),
        verticalArrangement = Arrangement.spacedBy(DS.Space.s),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { vm.backToLobby() },
            shape = ButtonShape,
            colors = dsPrimaryButtonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(text = loc("Nova partija"), style = Type.body, fontWeight = FontWeight.Bold) }
        if (game.gameState.moveNotations.isNotEmpty()) {
            Button(
                onClick = onAnalyze,
                shape = ButtonShape,
                colors = dsSecondaryButtonColors(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(text = loc("Analiziraj partiju"), style = Type.body, fontWeight = FontWeight.Bold) }
        }
        TextButton(onClick = onClose, shape = ButtonShape) {
            Text(text = loc("Zatvori"), color = DS.accent, style = Type.body, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Akcije ────────────────────────────────────────────────────────────────────

@Composable
private fun OnlineActions(vm: OnlineViewModel, game: GameViewModel, onResign: () -> Unit) {
    val state = vm.state as? OnlineState.Playing ?: return
    val canAct = game.onlineCanMove
    val drawEnabled = canAct && !state.drawOfferedByMe && !state.drawOfferedByOpponent
    Row(horizontalArrangement = Arrangement.spacedBy(DS.Space.s), modifier = Modifier.fillMaxWidth()) {
        ActionBarButton(
            icon = Icons.Default.Flag,
            label = loc("Predaj"),
            onClick = onResign,
            tint = DS.danger,
            enabled = canAct,
            modifier = Modifier.weight(1f).testTag("online.resign")
        )
        ActionBarButton(
            icon = Icons.Default.Handshake,
            label = loc("Ponudi remi"),
            onClick = { vm.offerDraw() },
            enabled = drawEnabled,
            modifier = Modifier.weight(1f).testTag("online.offerDraw")
        )
    }
}
