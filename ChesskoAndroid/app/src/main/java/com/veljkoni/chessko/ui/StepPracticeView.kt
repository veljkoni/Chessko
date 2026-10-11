package com.veljkoni.chessko.ui

import com.veljkoni.chessko.ui.theme.ButtonShape
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.models.CurriculumStep
import com.veljkoni.chessko.models.StepKind
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.PuzzlePhase
import com.veljkoni.chessko.viewmodels.PuzzleViewModel

// MARK: - Pokretac koraka `practice`/`test` (Faza 6c, Task 6)
//
// Ekran je NAMERNO tanak. Ceo tok resavanja (tap, provera poteza,
// protivnikov odgovor, rejting, dnevni cilj) vec zivi u `PuzzleViewModel`-u i
// deli se sa tabom Zadaci; ovde se dodaje samo ono sto korak ima a
// pojedinacan zadatak nema — red zadataka, traka napretka i zavrsetak koraka.
//
// Dve stvari koje ekran svesno NE nudi:
// 1. "Prikazi resenje" — na tabu Zadaci je izlaz iz zaglavljenog zadatka; u
//    koraku Puta bi bio izlaz iz same PROVERE.
// 2. "Sledeci zadatak" — red je fiksan i napunjen unapred; sledeci stize sam
//    kad se tekuci resi.
//
// Sopstveni `PuzzleViewModel` (NE `viewModel()`/deljena instanca) je bitan:
// deljeni model bi ulaskom u korak pregazio zadatak dana koji korisnik ima
// zapocet na tabu Zadaci, i obrnuto.
@Composable
fun StepPracticeView(step: CurriculumStep, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    // `PuzzleViewModel` vise NEMA autoload u `init`-u (Faza 6c, Task 7) -- pravljenje
    // instance ovde vise ne pokrece ucitavanje dnevnog zadatka koje bi se trkalo sa
    // `startStepPractice()` ispod.
    val viewModel = remember { PuzzleViewModel(app) }

    // `viewModel` je napravljen kroz `remember`, NE kroz `ViewModelStore`, pa mu se
    // `onCleared()` NIKAD ne izvrsi -- `SoundManager` (nije singleton, svaki
    // `PuzzleViewModel` pravi SVOJ `SoundPool`) bi bez ovoga procureo na svaki izlazak
    // iz koraka. Isti obrazac kao `ChessClockView.kt` (`DisposableEffect` + `release()`).
    DisposableEffect(Unit) {
        onDispose { viewModel.releaseSounds() }
    }

    // `step.id` kao kljuc: ako se ikad otvori drugi korak dok je ovaj ekran
    // ziv (nije slucaj danas), red se ponovo puni za novi korak.
    LaunchedEffect(step.id) { viewModel.startStepPractice(step) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val side = stepBoardSide(maxWidth, maxHeight)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = DS.Space.l)) {
            StepHeader(title = stepTitle(step), onClose = onClose)

            when (viewModel.phase) {
                PuzzlePhase.LOADING -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DS.accent)
                }
                PuzzlePhase.NETWORK_ERROR -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Dekorativna: poruka o gresci stoji odmah ispod ikone.
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DS.warning,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(DS.Space.s))
                        Text(
                            text = viewModel.networkErrorMessage,
                            color = DS.ink,
                            style = Type.body,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    val settings = remember { SettingsManager.getInstance(context) }
                    val activeTheme = remember(settings.boardTheme) {
                        BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
                    }
                    val activeStyle = remember(settings.pieceStyle) {
                        PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
                    }
                    // spec § 7: sadrzaj od vrha (`Space.l` ispod zaglavlja) — ranije
                    // je tabla bila centrirana u `weight(1f)` pa je iznad nje ostajalo
                    // ~300 dp praznog.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = DS.Space.l, bottom = DS.Space.l),
                        verticalArrangement = Arrangement.spacedBy(DS.Space.m),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProgressHeader(
                            progress = viewModel.stepProgress,
                            requiresFlawless = viewModel.stepRequiresFlawless,
                            finished = isStepFinished(viewModel.stepProgress, viewModel.stepFailed)
                        )

                        // Isti uputni okvir kao Zadaci (spec § 7).
                        PuzzleInstruction(viewModel)

                        BoardView(
                            board = viewModel.gameState.board,
                            isFlipped = viewModel.isFlipped,
                            selectedPosition = viewModel.selectedPosition,
                            legalMoves = viewModel.legalMovesForSelected,
                            lastMove = viewModel.lastMove,
                            gameStatus = viewModel.gameState.status,
                            boardTheme = activeTheme,
                            pieceStyle = activeStyle,
                            showCoordinates = settings.showCoordinates,
                            showLastMoveHighlight = settings.showLastMoveHighlight,
                            showLegalMoves = settings.showLegalMoves,
                            onTap = { pos -> viewModel.tap(pos) },
                            modifier = Modifier.size(side)
                        )

                        StepFooter(progress = viewModel.stepProgress, stepFailed = viewModel.stepFailed, onClose = onClose)
                    }
                }
            }
        }
    }
}

/**
 * Jedna vrednost strane table za ekrane koraka (Task 11 menja samo telo, spec § 13).
 * Sirina do `DS.maxBoardSide`, uz granicu po visini da tabla u pejzazu ne proguta
 * ceo ekran (isti racun kao iOS `boardSide(in:)`, rezerva 220).
 */
internal fun stepBoardSide(width: Dp, height: Dp): Dp {
    val byWidth = width - DS.Space.l * 2
    val byHeight = height - 220.dp
    return max(200.dp, min(min(byWidth, byHeight), DS.maxBoardSide))
}

/**
 * Zaglavlje ekrana koraka: naslov + „Zatvori" kao tekst-dugme u `accent`
 * (spec § 7, § 3 — nikad `inkMuted`). Deli ga i `StepGameView`.
 */
@Composable
internal fun StepHeader(title: String, onClose: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Text(
            text = title,
            color = DS.ink,
            style = Type.heading,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        actions()
        TextButton(onClick = onClose, shape = ButtonShape) {
            Text(text = loc("Zatvori"), color = DS.accent, style = Type.body, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun isStepFinished(progress: Pair<Int, Int>, stepFailed: Boolean): Boolean =
    !stepFailed && progress.second > 0 && progress.first >= progress.second

@Composable
private fun ProgressHeader(progress: Pair<Int, Int>, requiresFlawless: Boolean, finished: Boolean) {
    // Pre nego sto se red napuni total je 0; brojac tada ne sme da kaze
    // "Zadatak 1 od 0".
    val total = maxOf(progress.second, 1)
    val current = minOf(progress.first + 1, total)

    Column(verticalArrangement = Arrangement.spacedBy(DS.Space.s), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = locF("Zadatak %d od %d", current, total),
            // spec § 7: `inkMuted` (5,28 / 5,94 na `ground`).
            color = DS.inkMuted,
            style = Type.caption,
            fontWeight = FontWeight.SemiBold
        )

        // spec § 7: reseni segmenti `accent` (7,69 / 7,03), tekuci `accent` 40 %,
        // nereseni `fill` — isto kao iOS. (Ranije je reseno bilo `success`.)
        Row(horizontalArrangement = Arrangement.spacedBy(DS.Space.xs), modifier = Modifier.fillMaxWidth()) {
            for (index in 0 until total) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                index < progress.first -> DS.accent
                                index == progress.first && !finished -> DS.accent.copy(alpha = 0.4f)
                                else -> DS.fill
                            }
                        )
                )
            }
        }

        if (requiresFlawless && !finished) {
            // spec § 7: tekst testa u `warning` direktno na `ground` — posle
            // promene tokena (§ 2) 5,16 / 9,27, preko AA, pa tonirana podloga
            // vise nije potrebna. Isto kao iOS.
            Text(
                text = loc("Test mora biti rešen bez greške"),
                color = DS.warning,
                style = Type.caption
            )
        }
    }
}

/**
 * Korak je gotov kad je resen SVAKI zadatak iz reda. Za `test` ovo je
 * dostizno samo bez greske — prva greska ga vrati na pocetak sa novim
 * zadacima, pa `stepSolved` krene od nule.
 */
@Composable
private fun StepFooter(progress: Pair<Int, Int>, stepFailed: Boolean, onClose: () -> Unit) {
    // `!stepFailed` je drugi branik za isti slucaj koji `isPlayerTurn` vec
    // gasi na izvoru: pao test NIJE zavrsen korak, ma koliko zadataka bilo
    // reseno pre restarta. Bez ovoga bi ekran napisao "Korak je zavrsen" dok
    // Put isti korak jos vodi kao nezavrsen.
    if (!isStepFinished(progress, stepFailed)) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).background(DS.success)
            )
            Text(text = loc("Korak je završen"), color = DS.ink, fontWeight = FontWeight.SemiBold)
        }
        Button(
            shape = ButtonShape, // spec § 3: Radius.m, nigde „stadion"
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = DS.accent, contentColor = DS.onAccent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(text = loc("Nazad na Put"), style = Type.body, fontWeight = FontWeight.SemiBold)
        }
    }
}

/// Isti tekst koji red nosi na ekranu Puta — korisnik dolazi sa tog ekrana i
/// mora da prepozna gde je usao.
private fun stepTitle(step: CurriculumStep): String = when (val k = step.kind) {
    is StepKind.Practice -> locF("Vežba · %d", k.count)
    is StepKind.Test -> locF("Test · %d", k.count)
    else -> loc("Put")
}
