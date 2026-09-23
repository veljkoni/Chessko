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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.models.AnalyzedMove
import com.veljkoni.chessko.models.GameAnalysis
import com.veljkoni.chessko.models.MoveClass
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
// **Preslikavanje klase u boju — sest klasa u CETIRI boje, isto kao iOS**
// (`Chessko/Views/AnalysisView.swift:153-160`; CLAUDE.md, „Analiza partije":
// „Sest klasa poteza preslikava se u cetiri boje"). `BEST` dobija sopstveni
// `DS.accent`, ODVOJENO od `EXCELLENT`/`GOOD` (`DS.success`); `INACCURACY` i
// `MISTAKE` dele `DS.warning`; `BLUNDER` je sam na `DS.danger`. Samo DVA para
// klasa deli boju (excellent+good, inaccuracy+mistake) — to je zapisano
// ogranicenje iOS-a, ne previd, i ovaj ekran ga prenosi doslovno, ne
// pojednostavljuje ga dalje. `BEST` znaci „odigran je potez motora" i mora
// ostati vizuelno razlicit od „solidno" (`good`): to je jedina klasa koja
// nosi drugaciju vrstu dobre vesti, i jedina zbog koje igrac uopste gleda
// traku dvaput. `DS.accent` je vec boja `turningPoint` kartice — ali
// `turningPoint` ISKLJUCUJE `BEST` (vidi `GameAnalysis.turningPoint`), pa se
// akcent u praksi nikad ne pojavljuje na obe stvari istovremeno; sudara nema.
// Naziv klase ostaje i u `contentDescription` na svakom potezu i u prelomnoj
// kartici, pa VoiceOver/TalkBack ekvivalent razlikuje svih 6 i kad se boja
// od dve deli.
//
// **Kontrast — nijedan nov par.** Svi parovi koje ovaj ekran crta su vec
// pokriveni postojecim `ContrastTest`-om: `ink/ground` i `ink/surface`
// (naslovi, vrednosti tacnosti), `inkMuted/ground` i `inkMuted/surface`
// (natpisi), `success/surface` i `danger/surface` (tacke klasa i ikonica
// prelomnog poteza, `textOnBackgroundsMeetsAA`), `accent/ground` i
// `accent/surface` (dugme „Zatvori" I tacka klase `BEST`, koja sedi na
// `DS.surface` u `MoveChip` — isti par, `accentTextOnSurfaceAndGroundMeetsAA`,
// izmereno 8,53 svetla / 6,43 tamna, daleko iznad AA praga 4,5 u obe teme —
// ne samo iznad laksem ne-tekstualnom pragu 3:1 koji bi tacki od 8dp i onako
// bio dovoljan), `accent/fill` (traka napretka, `textOnFillMeetsAA`).
// `warning/surface` (tacka klasa NETACNOST/GRESKA) NIJE nov potrosac — isti
// par vec crta `StatItem` u `SettingsView.kt` (kartica statistike, `DS.warning`
// tekst na `DS.surface`) i vec je upisan u `ContrastTest.knownSubAAPairsDoNotGetWorse`
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

@Composable
private fun AccuracyRow(result: GameAnalysis) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DS.Space.l)
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
            .clip(RoundedCornerShape(DS.Radius.m))
            .background(DS.surface)
            .padding(DS.Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Cela kartica je paritet: naslov i oznaka `.dsCaption`, vrednost `.dsTitle`
        // (`Chessko/Views/AnalysisView.swift:87`, `:90`, `:93`).
        Text(text = title, color = DS.inkMuted, style = Type.caption, fontWeight = FontWeight.Medium)
        Text(
            // `Locale.US` NIJE kozmetika: `String.format` bez njega uzima
            // SISTEMSKI jezik, pa bi na nemackom ili ruskom uredjaju pisalo
            // „41,9%" iako je jezik aplikacije biran u njenim podesavanjima
            // (`Loc`, ne sistem). iOS uvek daje tacku. Isti obrazac vec koristi
            // traka ocene (`UiComponents.kt:131`).
            text = String.format(java.util.Locale.US, "%.1f%%", value),
            color = DS.ink,
            style = Type.title,
            fontWeight = FontWeight.Bold
        )
        Text(text = loc("Tačnost"), color = DS.inkMuted, style = Type.caption, fontWeight = FontWeight.Medium)
    }
}

// MARK: - Prelomni potez

@Composable
private fun TurningPointCard(move: AnalyzedMove, onClick: () -> Unit) {
    val description = "${loc("Prelomni potez")}: ${move.displayNotation}, −${move.cpLoss}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.Radius.m))
            .background(DS.surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = colorFor(move.moveClass),
            modifier = Modifier.size(22.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // iOS: `.dsCaption` pa `.dsHeading`
            // (`Chessko/Views/AnalysisView.swift:109`, `:112`).
            Text(text = loc("Prelomni potez"), color = DS.inkMuted, style = Type.caption, fontWeight = FontWeight.Medium)
            Text(
                text = "${move.displayNotation}  (−${move.cpLoss})",
                color = DS.ink,
                style = Type.heading,
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
    // Imenovan razmak, ne dimenzija: ide pravo u `Arrangement.spacedBy` dva
    // reda nize. Zato token, iako nije zapisan unutar `spacedBy(...)`.
    val spacing = DS.Space.s
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
            .clip(RoundedCornerShape(DS.Radius.s))
            .background(DS.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = DS.Space.s, vertical = 6.dp)
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
            // iOS: `.dsMono` (`Chessko/Views/AnalysisView.swift:137`). `fontFamily`
            // je obrisan jer ga `Type.mono` vec nosi -- brisanje je bez posledica
            // samo zato sto se poklapa sa stilom.
            style = Type.mono
        )
    }
}

// MARK: - Klase

/**
 * Sest klasa u cetiri boje, isto kao iOS (vidi doc-komentar fajla). `BEST`
 * je ODVOJEN od `EXCELLENT`/`GOOD` — jedini spoj je excellent+good i
 * inaccuracy+mistake.
 */
@Composable
private fun colorFor(c: MoveClass): Color = when (c) {
    MoveClass.BEST -> DS.accent
    MoveClass.EXCELLENT, MoveClass.GOOD -> DS.success
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
