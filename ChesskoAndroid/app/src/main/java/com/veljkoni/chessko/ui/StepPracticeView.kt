package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.models.CurriculumStep
import com.veljkoni.chessko.models.StepKind
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

    // `step.id` kao kljuc: ako se ikad otvori drugi korak dok je ovaj ekran
    // ziv (nije slucaj danas), red se ponovo puni za novi korak.
    LaunchedEffect(step.id) { viewModel.startStepPractice(step) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stepTitle(step),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White.copy(alpha = 0.7f)
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
                CircularProgressIndicator(color = Color(0xFF00D2FF))
            }
            PuzzlePhase.NETWORK_ERROR -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "⚠️", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = viewModel.networkErrorMessage,
                        color = Color.White,
                        fontSize = 14.sp,
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
                        onTap = { pos -> viewModel.tap(pos) }
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
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        // Po jedna pilula za svaki zadatak u redu.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            for (index in 0 until total) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (index < progress.first) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.12f))
                )
            }
        }

        if (requiresFlawless) {
            Text(
                text = loc("Test mora biti rešen bez greške"),
                color = Color(0xFFF59E0B),
                fontSize = 12.sp
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
                modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34D399))
            )
            Text(text = loc("Korak je završen"), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
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
