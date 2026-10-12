package com.veljkoni.chessko.ui

import com.veljkoni.chessko.ui.theme.ButtonShape
import com.veljkoni.chessko.logic.locDecimal
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.veljkoni.chessko.models.*
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.GameMode
import com.veljkoni.chessko.viewmodels.GameViewModel
import kotlin.math.max

// `PlayerHeaderCard` je zamenjen zajednickom `PlayerCard` (`ui/GameComponents.kt`,
// dizajnerski pregled § 5): status „Na potezu" i kraj partije u kartici, procena
// samo u kartici igraca, aktivna ivica 2 dp.

@Composable
fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "dots")

    @Composable
    fun dotAlpha(delayMillis: Int): Float {
        val alpha by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(delayMillis)
            ),
            label = "alpha"
        )
        return alpha
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(DS.ink.copy(alpha = dotAlpha(0))))
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(DS.ink.copy(alpha = dotAlpha(200))))
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(DS.ink.copy(alpha = dotAlpha(400))))
    }
}

// `BottomNavItem` je zamenjen M3 `NavigationBar`/`NavigationRail` (`AppNavigation.kt`, spec § 4).

fun getPlayerName(viewModel: GameViewModel, color: PieceColor): String {
    return if (viewModel.gameMode == GameMode.LOCAL_FRIEND) {
        if (color == PieceColor.WHITE) loc("Beli") else loc("Crni")
    } else {
        if (color == viewModel.playerColor) loc("Ti") else loc("Računar")
    }
}

fun getMaterialAdvantage(gameState: GameState, color: PieceColor): Int {
    val whiteVal = gameState.capturedByWhite.sumOf { it.type.materialValue / 100 }
    val blackVal = gameState.capturedByBlack.sumOf { it.type.materialValue / 100 }
    return if (color == PieceColor.WHITE) {
        max(0, whiteVal - blackVal)
    } else {
        max(0, blackVal - whiteVal)
    }
}

// Faza 6e, Task 5: dugme "Analiziraj partiju".
//
// Ovde (ne u `MainActivity.kt`) zato sto ga koriste OBA mesta koja pokrecu
// partiju protiv racunara -- slobodna partija na tabu Igra (`MainActivity.kt`,
// paket `com.veljkoni.chessko`) i korak `game` Puta (`StepGameView.kt`, ISTI
// paket `com.veljkoni.chessko.ui` kao ovaj fajl) -- isti obrazac kao
// `getStatusMessage`/`getMaterialAdvantage` iznad.
//
// Vidi se samo na GOTOVOJ partiji koja ima bar jedan potez -- analiza
// pretrazuje pozicije redom, pa nad partijom koja jos traje ne bi ni imala
// sta da kaze. Uslov je isti kao iOS (`GameView.swift`, `analysisButton`):
// `viewModel.isGameOver && !moveNotations.isEmpty`.
//
// Na tabu Igra mora stajati u OBE grane rasporeda (pejzazna i portretna,
// `MainActivity.kt`) -- grane ne dele telo, pa bi dodavanje na jedno mesto
// ostavilo dugme nevidljivim u drugoj orijentaciji (iOS je tu gresku vec
// jednom napravio, vidi CLAUDE.md).
fun canAnalyzeGame(viewModel: GameViewModel): Boolean =
    viewModel.isGameOver && viewModel.gameState.moveNotations.isNotEmpty()

@Composable
fun AnalysisButton(viewModel: GameViewModel, onClick: () -> Unit) {
    if (!canAnalyzeGame(viewModel)) return

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(DS.Radius.m),
        colors = ButtonDefaults.buttonColors(
            containerColor = DS.accent,
            contentColor = DS.onAccent
        )
    ) {
        Icon(
            imageVector = Icons.Default.Assessment,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(DS.Space.s))
        Text(
            text = loc("Analiziraj partiju"),
            // iOS: `.dsBody.weight(.semibold)` (`Chessko/Views/GameView.swift:280`).
            style = Type.body,
            fontWeight = FontWeight.Bold
        )
    }
}

fun getStatusMessage(viewModel: GameViewModel): String {
    val status = viewModel.gameState.status
    val color = viewModel.playerColor
    return when (status) {
        is GameStatus.Playing -> {
            if (viewModel.gameMode == GameMode.LOCAL_FRIEND) {
                if (viewModel.gameState.currentTurn == PieceColor.WHITE) loc("Beli igra") else loc("Crni igra")
            } else {
                if (viewModel.gameState.currentTurn == color) loc("Tvoj potez") else loc("Računar razmišlja...")
            }
        }
        is GameStatus.Check -> {
            val targetColor = status.color
            if (viewModel.gameMode == GameMode.LOCAL_FRIEND) {
                if (targetColor == PieceColor.WHITE) loc("Šah! Beli kralj je napadnut.") else loc("Šah! Crni kralj je napadnut.")
            } else {
                if (targetColor == color) loc("Šah! Tvoj kralj je napadnut.") else loc("Šah! Napadaš kralja.")
            }
        }
        // Ishod je rodno neutralan (spec § 5, § 16): „Partija je predata · pobeda
        // crnih", ne „Izgubio si". 🎉 ostaje tamo gde je i bio (emoji u porukama se
        // ne diraju, § 0). Isto kao iOS `GameViewModel.statusMessage`.
        is GameStatus.Checkmate -> {
            val loserColor = status.color
            val base = if (loserColor == PieceColor.WHITE) loc("Mat · pobeda crnih") else loc("Mat · pobeda belih")
            val celebrate = viewModel.gameMode == GameMode.LOCAL_FRIEND || loserColor != color
            if (celebrate) "$base 🎉" else base
        }
        is GameStatus.Resigned -> {
            val loserColor = status.color
            val base = if (loserColor == PieceColor.WHITE) loc("Partija je predata · pobeda crnih") else loc("Partija je predata · pobeda belih")
            val celebrate = viewModel.gameMode != GameMode.LOCAL_FRIEND && loserColor != color
            if (celebrate) "$base 🎉" else base
        }
        is GameStatus.Draw -> {
            when (status.reason) {
                is DrawReason.Stalemate -> loc("Pat – remi!")
                is DrawReason.FiftyMoves -> loc("Remi – pravilo 50 poteza.")
                is DrawReason.Repetition -> loc("Remi – ponavljanje pozicije.")
                is DrawReason.InsufficientMaterial -> loc("Remi – nedovoljan materijal.")
            }
        }
    }
}
