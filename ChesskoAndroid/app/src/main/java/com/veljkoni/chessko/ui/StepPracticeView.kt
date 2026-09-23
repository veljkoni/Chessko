package com.veljkoni.chessko.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DS.Space.l),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stepTitle(step),
                color = DS.ink,
                // Zaglavlje ekrana koraka -> `Type.heading`. Ne `title`: 22 nosi
                // naslov aplikacije (`MainActivity`), a ovaj red je nivo ispod.
                style = Type.heading,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = DS.inkMuted
                )
            ) {
                Text(text = loc("Zatvori"))
            }
        }

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
                    // Faza 8, Task 3: isti obrazac kao `PuzzleView.kt` (ista
                    // ikona, ista podloga `DS.ground` — ekran koraka sedi u
                    // istom `Box` iz `MainActivity.kt`), i isto dekorativna:
                    // poruka o gresci stoji odmah ispod ikone.
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
                        // Poruka u recenici, ne oznaka -> `body`.
                        style = Type.body,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                ProgressHeader(
                    progress = viewModel.stepProgress,
                    requiresFlawless = viewModel.stepRequiresFlawless
                )

                StatusBanner(message = viewModel.statusMessage)

                val settings = remember { SettingsManager.getInstance(context) }
                val activeTheme = remember(settings.boardTheme) {
                    BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
                }
                val activeStyle = remember(settings.pieceStyle) {
                    PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                        modifier = Modifier.widthIn(max = DS.maxBoardSide)
                    )
                }

                StepFooter(progress = viewModel.stepProgress, stepFailed = viewModel.stepFailed, onClose = onClose)
            }
        }
    }
}

@Composable
private fun ProgressHeader(progress: Pair<Int, Int>, requiresFlawless: Boolean) {
    // Pre nego sto se red napuni total je 0; brojac tada ne sme da kaze
    // "Zadatak 1 od 0".
    val total = maxOf(progress.second, 1)
    val current = minOf(progress.first + 1, total)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = locF("Zadatak %d od %d", current, total),
            color = DS.inkMuted,
            // iOS crta bas `.dsCaption.weight(.semibold)`
            // (`Chessko/Views/StepPracticeView.swift:116`).
            style = Type.caption,
            fontWeight = FontWeight.SemiBold
        )

        // Po jedna pilula za svaki zadatak u redu: reseno -> success, tekuce
        // (index == progress.first, upravo se resava) -> accent, jos
        // neodigrano -> fill. Ranije su "reseno" i "tekuce" delili istu
        // (accent) boju -- tri stanja sad se stvarno razlikuju.
        Row(horizontalArrangement = Arrangement.spacedBy(DS.Space.xs), modifier = Modifier.fillMaxWidth()) {
            for (index in 0 until total) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                index < progress.first -> DS.success
                                index == progress.first -> DS.accent
                                else -> DS.fill
                            }
                        )
                )
            }
        }

        if (requiresFlawless) {
            // `DS.warning` tekst direktno na `DS.ground` (ovaj red nema Card ni
            // background omotac) pada na 3,26:1 u svetloj temi -- gore od vec
            // poznatog `warning/surface` para (3,61) i van svih ContrastTest
            // provera. Standardan obrazac trake upozorenja: tonirana `DS.warning`
            // podloga (providnost je ovde NAMERNA, deo tog obrasca -- ne pokusaj
            // da se nadoknadi providnost izgubljena pri prevodjenju) + `DS.ink`
            // tekst. Izracunato: DS.ink na (DS.warning @15% preko DS.ground) =
            // 13,47 svetla / 12,30 tamna -- daleko iznad AA u obe teme.
            Text(
                text = loc("Test mora biti rešen bez greške"),
                color = DS.ink,
                // iOS: `.dsCaption` (`Chessko/Views/StepPracticeView.swift:134`).
                style = Type.caption,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DS.warning.copy(alpha = 0.15f))
                    .padding(horizontal = DS.Space.s, vertical = DS.Space.xs)
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
    val finished = !stepFailed && progress.second > 0 && progress.first >= progress.second
    if (!finished) return

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
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DS.accent, contentColor = DS.onAccent)
        ) {
            Text(text = loc("Nazad na Put"), fontWeight = FontWeight.Bold)
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
