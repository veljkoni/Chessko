package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.models.AnalyzedMove
import com.veljkoni.chessko.models.GameAnalysis
import com.veljkoni.chessko.models.MoveClass
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.viewmodels.AnalysisError
import com.veljkoni.chessko.viewmodels.AnalysisViewModel
import com.veljkoni.chessko.viewmodels.GameViewModel
import kotlin.math.max

// MARK: - Ekran analize partije (Faza 6e, Task 5)
//
// Prenosi `Chessko/Views/AnalysisView.swift`. Cist prikaz gotovog
// `GameAnalysis`-a — ovaj fajl ne racuna nista, sve brojke stizu vec gotove
// iz `AnalysisViewModel`/`GameAnalysis` (Task 1 i 4, oba testirana bez
// ijednog `android.*` uvoza). Jedino sto ovaj fajl zna, a model ne, jeste
// kako klasa poteza izgleda: boja i naziv.
//
// **Sopstveni `AnalysisViewModel`, ne deljen.** Pravi se preko `remember`,
// NE preko `viewModel()`/`ViewModelStoreOwner` — ovaj ekran se otvara kao
// puni `Dialog` (nema NavHost-a u projektu, vidi `PathView.kt`), pa bi
// `viewModel()` vezao zivotni vek za Activity, a ne za sam dijalog:
// `onCleared()` se tada ne bi izvrsio dok korisnik ne zatvori CELU
// aplikaciju. `DisposableEffect` ispod zove `cancel()` eksplicitno pri
// zatvaranju ekrana — isti razlog zasto `AnalysisViewModel`-ova doc-komentar
// eksplicitno kaze da se `onCleared()` NE sme koristiti kao jedina zastita.
// Bez ovoga bi korisnik koji zatvori analizu i odmah zapocne novu partiju
// cekao da se svih ~N pretraga zavrsi pre nego sto AI odigra prvi potez —
// `StockfishEngine` je `object` sa JEDNIM `searchMutex`-om.
//
// **Preslikavanje klase u boju — TRI boje, ne cetiri kao iOS.** iOS ima
// `best -> DS.accent` odvojeno od `excellent/good -> DS.success` (setovi
// (accent, success, warning, danger) = 4 boje za 6 klasa). Ovaj ekran, po
// eksplicitnom uputstvu za ovaj task, spaja `BEST` u `DS.success` zajedno sa
// `EXCELLENT`/`GOOD` — (success, warning, danger) = 3 boje za 6 klasa.
// Ovo JESTE razlika od iOS izvora (provereno citanjem `AnalysisView.swift`,
// ne pretpostavljeno) i zabelezena je kao takva u izvestaju ovog taska, ne
// prokrijumcarena. Naziv klase ostaje u `contentDescription` na svakom
// potezu i u prelomnoj kartici, pa VoiceOver/TalkBack ekvivalent i dalje
// razlikuje svih 6 — samo boja ne.
//
// **Kontrast — nijedan nov par.** Svi parovi koje ovaj ekran crta su vec
// pokriveni postojecim `ContrastTest`-om: `ink/ground` i `ink/surface`
// (naslovi, vrednosti tacnosti), `inkMuted/ground` i `inkMuted/surface`
// (natpisi), `success/surface` i `danger/surface` (tacke klasa i ikonica
// prelomnog poteza, `textOnBackgroundsMeetsAA`), `accent/ground` i
// `accent/surface` (dugme „Zatvori", `accentTextOnSurfaceAndGroundMeetsAA`),
// `accent/fill` (traka napretka, `textOnFillMeetsAA`). `warning/surface`
// (tacka klasa NETACNOST/GRESKA) NIJE nov potrosac — isti par vec crta
// `StatItem` u `SettingsView.kt` (kartica statistike, `DS.warning` tekst na
// `DS.surface`) i vec je upisan u `ContrastTest.knownSubAAPairsDoNotGetWorse`
// (3,61 u svetloj temi, ispod AA za TEKST — ovde je upotreba jos blaza,
// obojena tacka od 8dp, ne tekst, pa vazi laksi ne-tekstualni prag 3:1 koji
// prolazi u obe teme).

@Composable
fun AnalysisView(viewModel: GameViewModel, onClose: () -> Unit) {
    val analysisViewModel = remember { AnalysisViewModel() }

    // Analiza od desetina pozicija ne sme da nastavi da melje kad ekran
    // nestane — vidi doc-komentar klase iznad.
    DisposableEffect(Unit) {
        onDispose { analysisViewModel.cancel() }
    }

    LaunchedEffect(Unit) {
        analysisViewModel.start(
            states = viewModel.allHistoryStates,
            notations = viewModel.gameState.moveNotations
        )
    }

    // Broj pozicija za prikaz napretka „N / M" — snimljen jednom pri otvaranju
    // ekrana (partija se ne menja dok je analiza otvorena).
    val total = remember { max(1, viewModel.allHistoryStates.size) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DS.ground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = loc("Analiza partije"),
                color = DS.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onClose) {
                Text(text = loc("Zatvori"), color = DS.accent, fontWeight = FontWeight.SemiBold)
            }
        }

        val analysis = analysisViewModel.analysis
        val error = analysisViewModel.error

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                error != null -> ErrorMessage(error)
                analysis != null -> {
                    AccuracyRow(analysis)
                    analysis.turningPoint?.let { tp ->
                        TurningPointCard(move = tp) { open(viewModel, tp, onClose) }
                    }
                    MoveStrip(analysis) { move -> open(viewModel, move, onClose) }
                }
                else -> {
                    val done = (analysisViewModel.progress * total).toInt().coerceIn(0, total)
                    RunningView(progress = analysisViewModel.progress, done = done, total = total)
                }
            }
        }
    }
}

/// Otvara poziciju POSLE odigranog poteza u zatecenom review modu.
/// `allHistoryStates` indeks je `ply + 1` jer je na indeksu 0 pocetna
/// pozicija, pre ijednog poteza — isti obrazac kao iOS `open(_:)`.
private fun open(viewModel: GameViewModel, move: AnalyzedMove, onClose: () -> Unit) {
    viewModel.goToMove(move.ply + 1)
    onClose()
}

// MARK: - Napredak

@Composable
private fun RunningView(progress: Float, done: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
            color = DS.accent,
            trackColor = DS.fill
        )
        // Bez `loc()`: goli brojevi se ne prevode, isti obrazac kao iOS
        // (`Text(verbatim: "\(done) / \(total)")`).
        Text(text = "$done / $total", color = DS.inkMuted, fontSize = 12.sp)
        Text(text = loc("Analiziram…"), color = DS.ink, fontSize = 14.sp)
    }
}

// MARK: - Greske

@Composable
private fun ErrorMessage(error: AnalysisError) {
    Text(
        text = errorText(error),
        color = DS.inkMuted,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp)
    )
}

private fun errorText(error: AnalysisError): String = when (error) {
    AnalysisError.NOT_ENOUGH_MOVES -> loc("Nema dovoljno poteza za analizu.")
    AnalysisError.ENGINE_UNAVAILABLE -> loc("Analiza nije dostupna — motor nije pronađen.")
    AnalysisError.SEARCH_FAILED -> loc("Analiza nije uspela.")
}

// MARK: - Tacnost

@Composable
private fun AccuracyRow(result: GameAnalysis) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AccuracyBox(
            title = loc("beli").replaceFirstChar { it.uppercase() },
            value = result.whiteAccuracy,
            modifier = Modifier.weight(1f)
        )
        AccuracyBox(
            title = loc("crni").replaceFirstChar { it.uppercase() },
            value = result.blackAccuracy,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AccuracyBox(title: String, value: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DS.surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = title, color = DS.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            text = String.format("%.1f%%", value),
            color = DS.ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = loc("Tačnost"), color = DS.inkMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// MARK: - Prelomni potez

@Composable
private fun TurningPointCard(move: AnalyzedMove, onClick: () -> Unit) {
    val description = "${loc("Prelomni potez")}: ${move.displayNotation}, −${move.cpLoss}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DS.surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = colorFor(move.moveClass),
            modifier = Modifier.size(22.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = loc("Prelomni potez"), color = DS.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "${move.displayNotation}  (−${move.cpLoss})",
                color = DS.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// MARK: - Traka poteza
//
// Manuelna mreza (chunked po redovima), NE `LazyVerticalGrid`: ekran je vec
// unutar `verticalScroll`-a, a lazy mreza bez fiksne visine unutar drugog
// skrola puca (beskonacna visina). Broj poteza u partiji je u praksi mali
// (par desetina), pa obicna, ne-lenja mreza ne kosta nista vidljivo.

@Composable
private fun MoveStrip(result: GameAnalysis, onClick: (AnalyzedMove) -> Unit) {
    val itemMinWidth = 96.dp
    val spacing = 8.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = max(1, ((maxWidth + spacing) / (itemMinWidth + spacing)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            result.moves.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    row.forEach { move ->
                        MoveChip(move = move, modifier = Modifier.weight(1f)) { onClick(move) }
                    }
                    // Poravnanje poslednjeg, nepopunjenog reda.
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveChip(move: AnalyzedMove, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val description = "${move.displayNotation}, ${classLabel(move.moveClass)}"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DS.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colorFor(move.moveClass))
        )
        Text(
            text = move.displayNotation,
            color = DS.ink,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// MARK: - Klase

/**
 * Sest klasa u tri boje (vidi doc-komentar fajla za razliku u odnosu na
 * iOS-ovih cetiri boje — namerna odluka ovog taska, ne previd).
 */
@Composable
private fun colorFor(c: MoveClass): Color = when (c) {
    MoveClass.BEST, MoveClass.EXCELLENT, MoveClass.GOOD -> DS.success
    MoveClass.INACCURACY, MoveClass.MISTAKE -> DS.warning
    MoveClass.BLUNDER -> DS.danger
}

private fun classLabel(c: MoveClass): String = when (c) {
    MoveClass.BEST -> loc("najbolji")
    MoveClass.EXCELLENT -> loc("odličan")
    MoveClass.GOOD -> loc("dobar")
    MoveClass.INACCURACY -> loc("netačnost")
    MoveClass.MISTAKE -> loc("greška")
    MoveClass.BLUNDER -> loc("promašaj")
}
