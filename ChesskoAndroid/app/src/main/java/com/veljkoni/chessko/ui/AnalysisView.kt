package com.veljkoni.chessko.ui

import com.veljkoni.chessko.ui.theme.dsCard
import com.veljkoni.chessko.logic.locPercent
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locPlural
import com.veljkoni.chessko.logic.EvaluationLabel
import com.veljkoni.chessko.models.BeginnerExplanation
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.models.AnalyzedMove
import com.veljkoni.chessko.models.GameAnalysis
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.AnalysisError
import com.veljkoni.chessko.viewmodels.AnalysisViewModel
import com.veljkoni.chessko.viewmodels.GameViewModel
import kotlin.math.max

// MARK: - Ekran analize partije (Faza 6e, Task 5)
//
// Prenosi `Chessko/Views/AnalysisView.swift`. Cist prikaz gotovog
// `GameAnalysis`-a — ovaj fajl ne racuna nista, sve brojke stizu vec gotove
// iz `AnalysisViewModel`/`GameAnalysis` (Task 1 i 4, oba testirana bez
// ijednog `android.*` uvoza). Kako klasa poteza izgleda (simbol + ispuna)
// zna `MoveClassBadge` (`GameComponents.kt`); ovaj fajl samo slaze ekran.
//
// **Sopstveni `AnalysisViewModel`, ne deljen.** Pravi se preko `remember`,
// NE preko `viewModel()`/`ViewModelStoreOwner` — ovaj ekran se otvara kao
// celoekransko odrediste (`FullScreenDestination`, `FullScreen.kt`; nema
// NavHost-a u projektu), pa bi `viewModel()` vezao zivotni vek za Activity, a
// ne za sam ekran:
// `onCleared()` se tada ne bi izvrsio dok korisnik ne zatvori CELU
// aplikaciju. `DisposableEffect` ispod zove `cancel()` eksplicitno pri
// zatvaranju ekrana — isti razlog zasto `AnalysisViewModel`-ova doc-komentar
// eksplicitno kaze da se `onCleared()` NE sme koristiti kao jedina zastita.
// Bez ovoga bi korisnik koji zatvori analizu i odmah zapocne novu partiju
// cekao da se svih ~N pretraga zavrsi pre nego sto AI odigra prvi potez —
// `StockfishEngine` je `object` sa JEDNIM `searchMutex`-om.
//
// **Dizajnerski pregled § 6.** Klasa poteza je simbol + ispuna
// (`MoveClassBadge`, `GameComponents.kt`; boja = porodica: success / inkMuted /
// warning / danger) — `DS.accent` se za klasu NE koristi, jer u istoj listi znaci
// „izabran potez". Lista poteza je ista komponenta kao u Igri (`MoveHistoryView`,
// broj · beli · crni). Prelomni potez nosi `warning` ikonu, gubitak u pionima
// („−2,3", `EvaluationLabel.loss`) i recenicu za pocetnika. Tacnost ispod
// `GameAnalysis.MIN_MOVES_FOR_ACCURACY` sopstvenih poteza je „—" uz napomenu.
// Svi parovi boja su postojeci (§ 2 tabela: success/warning/danger/inkMuted na
// surface, inkMuted na ground) i pokriveni `ContrastTest`-om. iOS:
// `Chessko/Views/AnalysisView.swift`, ista pravila.

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
                .padding(horizontal = DS.Space.l, vertical = DS.Space.m),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = loc("Analiza partije"),
                color = DS.ink,
                // Zaglavlje ekrana -> `heading`, isto kao koraci Puta.
                style = Type.heading,
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
                .padding(horizontal = DS.Space.l)
                .padding(bottom = DS.Space.xl),
            verticalArrangement = Arrangement.spacedBy(DS.Space.l)
        ) {
            when {
                error != null -> ErrorMessage(error)
                analysis != null -> {
                    AccuracySection(analysis)
                    analysis.turningPoint?.let { tp ->
                        TurningPointCard(move = tp) { open(viewModel, tp, onClose) }
                    }
                    AnalysisMoveList(
                        analysis = analysis,
                        selectedMoveIndex = viewModel.viewingMoveIndex,
                        onSelectMove = { index -> // broj odigranih poluteza (ply + 1)
                            viewModel.goToMove(index)
                            onClose()
                        }
                    )
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
        // iOS: `.dsCaption` i `.dsBody` (`Chessko/Views/AnalysisView.swift:66`, `:69`).
        Text(text = "$done / $total", color = DS.inkMuted, style = Type.caption)
        Text(text = loc("Analiziram…"), color = DS.ink, style = Type.body)
    }
}

// MARK: - Greske

@Composable
private fun ErrorMessage(error: AnalysisError) {
    Text(
        text = errorText(error),
        color = DS.inkMuted,
        // iOS: `.dsBody` (`Chessko/Views/AnalysisView.swift:26`).
        style = Type.body,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp)
    )
}

private fun errorText(error: AnalysisError): String = when (error) {
    AnalysisError.NOT_ENOUGH_MOVES -> loc("Nema dovoljno poteza za analizu.")
    AnalysisError.ENGINE_UNAVAILABLE -> loc("Analiza nije dostupna — motor nije pronađen.")
    AnalysisError.ENGINE_NOT_READY -> loc("Motor se još priprema. Pokušaj ponovo za koji trenutak.")
    AnalysisError.SEARCH_FAILED -> loc("Analiza nije uspela.")
}

// MARK: - Tacnost

/** § 6 / § 17: ispod praga poteza strana dobija „—" i napomenu (`inkMuted` na `ground`). */
@Composable
private fun AccuracySection(result: GameAnalysis) {
    val white = result.reliableAccuracy(white = true)
    val black = result.reliableAccuracy(white = false)
    Column(verticalArrangement = Arrangement.spacedBy(DS.Space.s)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DS.Space.l)
        ) {
            AccuracyBox(
                title = loc("beli").replaceFirstChar { it.uppercase() },
                value = white,
                modifier = Modifier.weight(1f)
            )
            AccuracyBox(
                title = loc("crni").replaceFirstChar { it.uppercase() },
                value = black,
                modifier = Modifier.weight(1f)
            )
        }
        if (white == null || black == null) {
            Text(
                text = loc("Premalo poteza za procenu tačnosti."),
                color = DS.inkMuted,
                style = Type.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AccuracyBox(title: String, value: Double?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .dsCard() // spec § 3: surface + 1 dp line (iOS `.dsCard()`)
            .padding(DS.Space.m)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = title, color = DS.inkMuted, style = Type.caption, fontWeight = FontWeight.Medium)
        Text(
            // Lokalni format JEZIKA APLIKACIJE (spec § 3: „0,0 %" na srpskom);
            // ispod praga poteza „—" (§ 6).
            text = value?.let { locPercent(it) } ?: "\u2014",
            color = DS.ink,
            style = Type.title,
            fontWeight = FontWeight.Bold
        )
        Text(text = loc("Tačnost"), color = DS.inkMuted, style = Type.caption, fontWeight = FontWeight.Medium)
    }
}

// MARK: - Prelomni potez

/** Recenica za pocetnika (§ 6). Strana je ona koja je odigrala potez. */
private fun explanation(move: AnalyzedMove): String = when (val e = move.beginnerExplanation) {
    is BeginnerExplanation.PawnsLost ->
        if (move.byWhite) locPlural("Beli gubi oko %d piona prednosti.", e.pawns)
        else locPlural("Crni gubi oko %d piona prednosti.", e.pawns)
    BeginnerExplanation.MissedMate -> if (move.byWhite) loc("Beli propušta mat.") else loc("Crni propušta mat.")
    BeginnerExplanation.AllowedMate -> if (move.byWhite) loc("Beli dozvoljava mat.") else loc("Crni dozvoljava mat.")
}

/** § 6: ikona `warning`, „2.g4  −2,3" i recenica umesto „2.g4 (−233)". */
@Composable
private fun TurningPointCard(move: AnalyzedMove, onClick: () -> Unit) {
    val loss = EvaluationLabel.loss(move.cpLoss)
    val sentence = explanation(move)
    val description = "${loc("Prelomni potez")}, ${move.moveClass.accessibilityName}, " +
        "${move.displayNotation}, $loss. $sentence"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dsCard() // spec § 3: surface + 1 dp line (iOS `.dsCard()`)
            .clickable(onClick = onClick)
            .padding(DS.Space.m)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = DS.warning,
            modifier = Modifier.size(22.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
            Text(text = loc("Prelomni potez"), color = DS.inkMuted, style = Type.caption, fontWeight = FontWeight.Medium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
            ) {
                MoveClassBadge(move.moveClass)
                Text(text = move.displayNotation, color = DS.ink, style = Type.heading, fontWeight = FontWeight.Bold)
                Text(text = loss, color = DS.ink, style = Type.heading, fontWeight = FontWeight.Bold)
            }
            Text(text = sentence, color = DS.ink, style = Type.body)
        }
    }
}

// MARK: - Lista poteza
//
// § 6: ista lista kao u Igri (broj · beli · crni), oznaka klase ispred poteza,
// izabran red na accent tinti. Izabran je potez koji je korisnik vec otvorio u
// pregledu partije (isti `viewingMoveIndex` kao u Igri); tap otvara potez na tabli.
// Ekran vec skroluje, pa lista dobija punu visinu (svi redovi, bez unutrasnjeg skrola).

@Composable
private fun AnalysisMoveList(
    analysis: GameAnalysis,
    selectedMoveIndex: Int?,
    onSelectMove: (Int) -> Unit
) {
    val classes = remember(analysis) { analysis.moves.associate { it.ply to it.moveClass } }
    val rows = (analysis.moves.size + 1) / 2
    MoveHistoryView(
        notations = analysis.moves.map { it.notation },
        modifier = Modifier.height(MoveRowHeight * rows + DS.Space.xs * 2),
        selectedMoveIndex = selectedMoveIndex,
        highlightsLastMoveWhenLive = false,
        moveClass = { classes[it] },
        onSelectMove = onSelectMove
    )
}
