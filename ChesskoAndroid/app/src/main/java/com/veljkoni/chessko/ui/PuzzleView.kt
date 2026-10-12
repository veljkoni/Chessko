package com.veljkoni.chessko.ui

import com.veljkoni.chessko.ui.theme.ButtonShape
import com.veljkoni.chessko.ui.theme.dsPrimaryButtonColors
import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.loc

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.PuzzlePhase
import com.veljkoni.chessko.viewmodels.PuzzleViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// spec § 7 (dizajnerski pregled): iOS raspored na obe platforme — zaglavlje sa
// „‹ Danas ›", red tagova, okvir uputstva, tabla, jedna radnja. Sadrzaj od vrha:
// nista centrirano po visini, nista prikovano za dno. „Pokušaj ponovo" je uklonjen
// iz rešavanja; ostaje samo na ekranu greske pri ucitavanju (jedini izlaz iz tog
// stanja).
@Composable
fun PuzzleView(
    viewModel: PuzzleViewModel,
    modifier: Modifier = Modifier
) {
    // Ekran se ponovo prikazuje (povratak na tab, zatvaranje podesavanja), a
    // `PuzzleViewModel` prezivljava kroz `remember` u `MainActivity` — pa kes
    // napretka moze biti zastareo ako je u medjuvremenu pritisnuto „Resetuj
    // statistiku". Osvezava se SAMO kes, ne i zadatak: `loadDailyPuzzle()` bi
    // restartovao zadatak u toku, sto je vec jednom bio bug.
    LaunchedEffect(Unit) { viewModel.refreshPersistedProgress() }

    val busy = viewModel.phase == PuzzlePhase.LOADING || viewModel.phase == PuzzlePhase.NETWORK_ERROR

    val isTablet = isTabletWindow()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val side = puzzleBoardSide(maxWidth, maxHeight, isTablet, isLandscape)

        if (isLandscape) {
            // Tablet (spec § 13): tabla + panel 400 centrirani kao par, vrhovi
            // poravnati, umetak `Space.xl`; telefon zatecen (`Space.l`, panel do kraja).
            val inset = if (isTablet) DS.Space.xl else DS.Space.l
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = inset),
                horizontalArrangement = Arrangement.spacedBy(DS.Space.xl, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = inset)
                        .size(side),
                    contentAlignment = Alignment.Center
                ) {
                    if (busy) PuzzleLoadState(viewModel) else PuzzleBoard(viewModel, Modifier.fillMaxSize())
                }
                Column(
                    modifier = (if (isTablet) Modifier.width(TabletPanelWidth) else Modifier.weight(1f))
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = inset),
                    verticalArrangement = Arrangement.spacedBy(DS.Space.m)
                ) {
                    PuzzleHeaderRow(viewModel)
                    if (!busy) {
                        PuzzleTagsRow(viewModel)
                        PuzzleInstruction(viewModel)
                        Spacer(modifier = Modifier.height(DS.Space.l - DS.Space.m))
                        PuzzleActionsRow(viewModel)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DS.Space.l)
            ) {
                Box(modifier = Modifier.padding(vertical = DS.Space.s)) { PuzzleHeaderRow(viewModel) }
                if (busy) {
                    // Stanje celog ekrana (ucitavanje / greska) — tu centriranje
                    // jeste namera: nema sadrzaja koji bi se citao od vrha.
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) { PuzzleLoadState(viewModel) }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = DS.Space.s, bottom = DS.Space.l),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // spec § 13: kolona siroka kao tabla, centrirana (na
                        // telefonu je to ionako cela sirina).
                        Column(
                            modifier = Modifier.widthIn(max = side).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(DS.Space.m),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PuzzleTagsRow(viewModel)
                            PuzzleInstruction(viewModel)
                            PuzzleBoard(viewModel, Modifier.size(side))
                            Spacer(modifier = Modifier.height(DS.Space.l - DS.Space.m))
                            PuzzleActionsRow(viewModel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Strana table — zajednicka funkcija `boardSide` (spec § 13). Telefon: portret
 * sirina, pejzaz visina − 2·`Space.l` (zateceno), do 560; tablet do 800.
 */
internal fun puzzleBoardSide(width: Dp, height: Dp, isTablet: Boolean, isLandscape: Boolean): Dp =
    boardSide(width, height, isTablet, isLandscape,
        phoneHeightReserve = if (isLandscape) DS.Space.l * 2 else 0.dp)

@Composable
private fun PuzzleBoard(viewModel: PuzzleViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val activeTheme = remember(settings.boardTheme) {
        BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
    }
    val activeStyle = remember(settings.pieceStyle) {
        PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
    }
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
        modifier = modifier
    )
}

/** Ucitavanje ili greska pri ucitavanju — jedino mesto gde ostaje „Pokušaj ponovo". */
@Composable
private fun PuzzleLoadState(viewModel: PuzzleViewModel) {
    if (viewModel.phase == PuzzlePhase.LOADING) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DS.accent)
            Spacer(modifier = Modifier.height(DS.Space.m))
            Text(text = loc("Učitavam zadatak..."), color = DS.inkMuted, style = Type.body)
        }
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(DS.Space.xl)
    ) {
        // Dekorativna: naslov greske stoji odmah ispod ikone.
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = DS.warning,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(DS.Space.s))
        Text(
            text = loc("Greška pri učitavanju zadatka"),
            color = DS.ink,
            style = Type.heading,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = viewModel.networkErrorMessage,
            color = DS.inkMuted,
            style = Type.body,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(DS.Space.l))
        Button(
            shape = ButtonShape, // spec § 3: Radius.m, nigde „stadion"
            onClick = { viewModel.loadDailyPuzzle() },
            colors = dsPrimaryButtonColors()
        ) {
            Text(text = loc("Pokušaj ponovo"))
        }
    }
}

/// `Locale` za naziv meseca u traci datuma — MORA pratiti jezik IZABRAN u
/// aplikaciji (`Loc.getLanguage()`), ne sistemski jezik uredjaja. `ofPattern`
/// bez `Locale`-a uzima sistemski, pa je korisnik sa srpskim u aplikaciji i
/// engleskim sistemom video „September" umesto „septembar".
///
/// `Loc.getLanguage()` (ne `Loc.fileLanguageCode()`) je namerno: potonji vraca
/// kod FAJLA lekcija ("zh-Hans"), a ovde treba goli jezicki kod za `Locale`
/// (vidi CLAUDE.md, „Poznata ogranicenja" — ta dva se ne smeju pomesati).
/// `Locale.forLanguageTag(code)` je za sve jezike osim srpskog dovoljan.
///
/// Za "sr" NIJE dovoljan: bez skripte, i Java i Android ICU podrazumevaju
/// CIRILICU za srpski (`sr` == `sr-Cyrl` po CLDR-u), a ceo srpski sadrzaj
/// aplikacije je LATINICA (vidi recnik u `Loc.kt`). Bez ovog izuzetka bi naziv
/// meseca bio jedini cirilicni tekst na celom ekranu, na srpskom UI-ju.
/// Izmereno (JVM, `DateTimeFormatter`): `Locale.forLanguageTag("sr")` daje
/// "септембар" (Cyrl); `Locale.forLanguageTag("sr-Latn")` daje "septembar" (Latn).
internal fun localeForDateFormatting(languageCode: String): Locale =
    when (languageCode) {
        "sr" -> Locale.forLanguageTag("sr-Latn")
        else -> Locale.forLanguageTag(languageCode)
    }

/** Naslov u traci datuma: „Danas" / „Juče" / kratak datum u jeziku aplikacije (kao iOS `dateTitle`). */
private fun puzzleDateTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> loc("Danas")
        today.minusDays(1) -> loc("Juče")
        else -> {
            val locale = localeForDateFormatting(Loc.getLanguage())
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM")
            date.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
    }
}

/**
 * Zaglavlje (spec § 7): naslov „Zadaci" levo, „‹ Danas ›" desno. Strelice su
 * ikone-dugmad 44 × 44 u `accent` (7,69 / 7,03 na `ground`); nedostupna strelica
 * (danas → desno) ostaje na mestu sa 38 %. U vezbovnom rezimu se traka datuma ne
 * prikazuje — tvrdila bi neistinu (zadatak nije zadatak dana za prikazani datum).
 */
@Composable
fun PuzzleHeaderRow(viewModel: PuzzleViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = loc("Zadaci"),
            color = DS.ink,
            // spec § 4 (P3): naslov zaglavlja `Type.heading` semibold.
            style = Type.heading,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (viewModel.mode == PuzzleViewModel.PuzzleMode.DAILY) {
            val loading = viewModel.phase == PuzzlePhase.LOADING
            val canPrev = viewModel.canGoPrevious && !loading
            val canNext = viewModel.canGoNext && !loading
            IconButton(
                onClick = { viewModel.goToPrevious() },
                enabled = canPrev,
                modifier = Modifier.size(44.dp).alpha(if (canPrev) 1f else 0.38f)
            ) {
                // Strelica stoji SAMA, pa joj treba opis.
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = loc("Prethodni dan"),
                    tint = DS.accent
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = DS.inkMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = puzzleDateTitle(viewModel.selectedDate),
                    color = DS.ink,
                    style = Type.caption,
                    fontWeight = FontWeight.SemiBold
                )
                // Nije dekorativna: datum levo od nje ne kaze „reseno". Par
                // `success/ground` 4,54 / 9,61 (>= 3 : 1 za ikonu).
                if (viewModel.isSolved(viewModel.selectedDate)) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = loc("Rešeno"),
                        tint = DS.success,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            IconButton(
                onClick = { viewModel.goToNext() },
                enabled = canNext,
                modifier = Modifier.size(44.dp).alpha(if (canNext) 1f else 0.38f)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = loc("Sledeći dan"),
                    // Ista boja u oba stanja — nedostupnost nosi 38 % (spec § 7),
                    // ne promena boje.
                    tint = DS.accent
                )
            }
        }
    }
}

/**
 * Red tagova (spec § 7): rejting (zvezdica `warning`, broj `ink`, bez tacke
 * hiljada — „1503"), tezina kao neutralan cip (`fill` + `ink` uz 1–3 crtice;
 * „Teško" nije greska, „Lako" nije uspeh) i do dve teme (`fill` + `inkMuted`).
 */
@Composable
fun PuzzleTagsRow(viewModel: PuzzleViewModel) {
    val puzzle = viewModel.currentPuzzle ?: return
    val themes = puzzle.themeList.map { localizePuzzleTheme(it) }.filter { it.isNotEmpty() }.take(2)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DS.Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PuzzleTag(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = DS.warning,
                modifier = Modifier.size(12.dp)
            )
            Text(
                // `toString()`, ne lokalni format: spec trazi „1503", ne „1.503".
                text = puzzle.rating.toString(),
                color = DS.ink,
                style = Type.mono,
                fontWeight = FontWeight.SemiBold
            )
        }
        val level = when {
            puzzle.rating < 1200 -> 1
            puzzle.rating < 1600 -> 2
            else -> 3
        }
        PuzzleTag(modifier = Modifier.clearAndSetSemantics { contentDescription = puzzle.difficultyLabel }) {
            Text(text = puzzle.difficultyLabel, color = DS.ink, style = Type.caption, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until 3) {
                    Box(
                        modifier = Modifier
                            .size(width = 8.dp, height = 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i < level) DS.ink else DS.ink.copy(alpha = 0.2f))
                    )
                }
            }
        }
        for (label in themes) {
            PuzzleTag {
                Text(text = label, color = DS.inkMuted, style = Type.caption)
            }
        }
    }
}

@Composable
private fun PuzzleTag(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(DS.Radius.s))
            .background(DS.fill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(DS.Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Teme zadatka — isti 21 kljuc kao iOS `localizeTheme`; nepoznata tema se ne prikazuje. */
private fun localizePuzzleTheme(theme: String): String = when (theme) {
    "opening" -> loc("Otvaranje")
    "middlegame" -> loc("Srednja igra")
    "endgame" -> loc("Završnica")
    "mate" -> loc("Mat")
    "mateIn1" -> loc("Mat u 1")
    "mateIn2" -> loc("Mat u 2")
    "mateIn3" -> loc("Mat u 3")
    "fork" -> loc("Vilica")
    "pin" -> loc("Vezivanje")
    "skewer" -> loc("Nabijanje")
    "sacrifice" -> loc("Žrtva")
    "discoveredAttack" -> loc("Otkriveni napad")
    "deflection" -> loc("Odvlačenje")
    "advantage" -> loc("Prednost")
    "crushing" -> loc("Odlučujuće")
    "short" -> loc("Kratko")
    "long" -> loc("Dugo")
    "oneMove" -> loc("Jedan potez")
    "defensiveMove" -> loc("Odbrana")
    "queensideAttack" -> loc("Napad na damu")
    "kingsideAttack" -> loc("Napad na kralja")
    else -> ""
}

/** Faza zadatka → ton okvira uputstva (deli se sa `StepPracticeView`). */
internal fun PuzzlePhase.instructionTone(): InstructionTone = when (this) {
    PuzzlePhase.WRONG_MOVE -> InstructionTone.WRONG
    PuzzlePhase.SOLVED -> InstructionTone.SOLVED
    PuzzlePhase.SHOWING_SOLUTION -> InstructionTone.SOLUTION
    else -> InstructionTone.HINT
}

internal val PuzzlePhase.isSolving: Boolean
    get() = this == PuzzlePhase.PLAYING || this == PuzzlePhase.WRONG_MOVE

/** spec § 7: uputstvo je okvir (`InstructionBox`), ne dugme — bivse puno `accent` dugme. */
@Composable
fun PuzzleInstruction(viewModel: PuzzleViewModel) {
    InstructionBox(
        text = viewModel.statusMessage,
        sideToMove = if (viewModel.phase.isSolving) viewModel.playerColor else null,
        tone = viewModel.phase.instructionTone()
    )
}

/**
 * Jedna radnja (spec § 7). Tokom resavanja samo „Prikaži rešenje" (sporedno
 * dugme, `fill` + `ink`); „Pokušaj ponovo" je uklonjen. Posle resenja „Sledeći
 * zadatak" (primarno) i, za dnevni zadatak, „Sledeći dan" ili poruka da je
 * danasnji zadatak zavrsen — isto kao iOS.
 */
@Composable
fun PuzzleActionsRow(viewModel: PuzzleViewModel) {
    when (viewModel.phase) {
        PuzzlePhase.PLAYING, PuzzlePhase.WRONG_MOVE -> {
            // `isPlayerTurn` je NUZAN deo uslova: `showSolution()` je iznutra
            // gejtovan na `!awaitingOpponent`, pa bi bez ovoga dugme tokom 600 ms
            // cekanja na protivnicki odgovor izgledalo pritisno a ne bi radilo nista.
            val enabled = viewModel.isPlayerTurn
            Button(
                onClick = { viewModel.showSolution() },
                enabled = enabled,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DS.fill,
                    contentColor = DS.ink,
                    disabledContainerColor = DS.fill,
                    disabledContentColor = DS.ink
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .alpha(if (enabled) 1f else 0.38f)
            ) {
                // Dekorativna — tekst pored kaze isto.
                Icon(imageVector = Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(DS.Space.s))
                Text(text = loc("Prikaži rešenje"), style = Type.body, fontWeight = FontWeight.SemiBold)
            }
        }
        PuzzlePhase.SOLVED -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DS.Space.s)
        ) {
            Button(
                shape = ButtonShape,
                onClick = { viewModel.nextPuzzle() },
                colors = dsPrimaryButtonColors(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(text = loc("Sledeći zadatak"), style = Type.body, fontWeight = FontWeight.SemiBold)
            }
            // Vezano za DNEVNI zadatak: u vezbovnom rezimu bi „Sledeći dan" tiho
            // izbacio korisnika iz vezbanja, a poruka o danasnjem zadatku bi lagala.
            if (viewModel.mode == PuzzleViewModel.PuzzleMode.DAILY) {
                if (viewModel.canGoNext) {
                    Button(
                        shape = ButtonShape,
                        onClick = { viewModel.goToNext() },
                        colors = ButtonDefaults.buttonColors(containerColor = DS.fill, contentColor = DS.ink),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(text = loc("Sledeći dan"), style = Type.body, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = DS.Space.m),
                        horizontalArrangement = Arrangement.spacedBy(DS.Space.s, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = DS.warning, modifier = Modifier.size(18.dp))
                        Text(text = loc("Zadatak za danas je rešen!"), color = DS.ink, style = Type.body, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        else -> Unit
    }
}
