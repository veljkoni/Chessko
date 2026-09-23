package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.PuzzlePhase
import com.veljkoni.chessko.viewmodels.PuzzleViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(DS.Space.s),
            horizontalArrangement = Arrangement.spacedBy(DS.Space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Board
            Box(
                modifier = Modifier
                    // Strana se racuna iz VISINE (fillMaxHeight + aspectRatio), pa
                    // granica ide na obe ose — vidi MainActivity.kt pejzaz.
                    .sizeIn(maxWidth = DS.maxBoardSide, maxHeight = DS.maxBoardSide)
                    .fillMaxHeight()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                when (viewModel.phase) {
                    PuzzlePhase.LOADING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = DS.accent)
                            Spacer(modifier = Modifier.height(DS.Space.m))
                            Text(
                                text = loc("Učitavam zadatak..."),
                                color = DS.inkMuted,
                                // iOS: `.dsBody` (`Chessko/Views/PuzzleView.swift:457`).
                                style = Type.body
                            )
                        }
                    }
                    PuzzlePhase.NETWORK_ERROR -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(DS.Space.l)
                        ) {
                            // Faza 8, Task 3: emoji -> Icon. Podloga je `DS.ground`
                            // (isti ekran, obe orijentacije — vidi ContrastTest).
                            // DEKORATIVNA: tekst ODMAH ispod vec kaze „Greška pri
                            // učitavanju zadatka", pa bi opis naterao citac ekrana
                            // da istu stvar kaze dvaput. Prvi prelaz je ovde imao
                            // kljuc "Greška" (zadat unapred, iz izgleda ikone); taj
                            // kljuc vise ne postoji.
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = DS.warning,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = loc("Greška pri učitavanju zadatka"),
                                color = DS.ink,
                                // Isti string, dve velicine u istom fajlu (pejzaz 14,
                                // portret 16) -- tacno drift koji Task 2 opisuje.
                                // Obe grane sada idu na `heading`.
                                style = Type.heading,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(DS.Space.m))
                            Button(
                                onClick = { viewModel.loadDailyPuzzle() },
                                colors = ButtonDefaults.buttonColors(containerColor = DS.accent)
                            ) {
                                Text(text = loc("Pokušaj ponovo"), color = DS.onAccent)
                            }
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Right Side: Toolbar, Metadata & Controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = DS.Space.s),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Vezi se za KONKRETAN datum — u vezbovnom rezimu bi tvrdila
                // neistinu (zadatak nije zadatak dana za prikazani datum).
                if (viewModel.mode == PuzzleViewModel.PuzzleMode.DAILY) {
                    DateNavigationRow(viewModel)
                }

                if (viewModel.phase != PuzzlePhase.LOADING && viewModel.phase != PuzzlePhase.NETWORK_ERROR) {
                    PuzzleMetadataHeader(viewModel)
                    PuzzleStatusBanner(phase = viewModel.phase, message = viewModel.statusMessage)
                    Spacer(modifier = Modifier.height(DS.Space.s))
                    PuzzleActionsRow(viewModel)
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(DS.Space.l),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Date Navigation Toolbar — vezi se za KONKRETAN datum, pa se
            // gasi u vezbovnom rezimu (ne tvrdi neistinu o zadatku dana).
            if (viewModel.mode == PuzzleViewModel.PuzzleMode.DAILY) {
                DateNavigationRow(viewModel)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Puzzle Metadata Header
            if (viewModel.phase != PuzzlePhase.LOADING && viewModel.phase != PuzzlePhase.NETWORK_ERROR) {
                PuzzleMetadataHeader(viewModel)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Main Content (Chessboard or Loading/Error)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (viewModel.phase) {
                    PuzzlePhase.LOADING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = DS.accent)
                            Spacer(modifier = Modifier.height(DS.Space.m))
                            Text(
                                text = loc("Učitavam zadatak..."),
                                color = DS.inkMuted,
                                // iOS: `.dsBody` (`Chessko/Views/PuzzleView.swift:457`).
                                style = Type.body
                            )
                        }
                    }
                    PuzzlePhase.NETWORK_ERROR -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(DS.Space.xl)
                        ) {
                            // Faza 8, Task 3: isti obrazac kao pejzazna grana iznad
                            // (ista ikona, ista podloga `DS.ground`, isto
                            // dekorativna — naslov greske stoji odmah ispod nje).
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
                                // Sama poruka o gresci -- iOS: `.dsBody`
                                // (`Chessko/Views/PuzzleView.swift:468`).
                                style = Type.body,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(DS.Space.l))
                            Button(
                                onClick = { viewModel.loadDailyPuzzle() },
                                colors = ButtonDefaults.buttonColors(containerColor = DS.accent)
                            ) {
                                Text(text = loc("Pokušaj ponovo"), color = DS.onAccent)
                            }
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
                }
            }

            Spacer(modifier = Modifier.height(DS.Space.m))

            // 4. Status banner
            if (viewModel.phase != PuzzlePhase.LOADING && viewModel.phase != PuzzlePhase.NETWORK_ERROR) {
                PuzzleStatusBanner(phase = viewModel.phase, message = viewModel.statusMessage)
            }

            Spacer(modifier = Modifier.height(DS.Space.l))

            // 5. Actions row
            if (viewModel.phase != PuzzlePhase.LOADING && viewModel.phase != PuzzlePhase.NETWORK_ERROR) {
                PuzzleActionsRow(viewModel)
            }
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

@Composable
fun DateNavigationRow(viewModel: PuzzleViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DS.fill)
            .padding(horizontal = DS.Space.s, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prev button
        Button(
            onClick = { viewModel.goToPrevious() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = DS.ink
            ),
            contentPadding = PaddingValues(horizontal = DS.Space.s)
        ) {
            // Faza 8, Task 3: emoji -> Icon. Dugme nema tekst pored sebe —
            // strelica STOJI SAMA, pa joj treba opis (za razliku od ikona koje
            // stoje uz vidljiv tekst istog znacenja). Boja se nasledjuje iz
            // `ButtonDefaults` iznad (`DS.ink`) — isti par kao ostatak dugmadi
            // ove trake, vec pokriven `textOnFillMeetsAA`.
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = loc("Prethodni dan"),
                modifier = Modifier.size(16.dp)
            )
        }

        // Date Display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val dateStr = viewModel.selectedDate.format(
                DateTimeFormatter.ofPattern("d. MMMM yyyy.", localeForDateFormatting(Loc.getLanguage()))
            )
            Text(
                text = dateStr,
                color = DS.ink,
                // Oznaka u traci datuma -> `body`. iOS tu crta
                // `.system(size: 16, weight: .bold)` (`Chessko/Views/PuzzleView.swift:106`,
                // `:139`), dakle i on je na tom mestu van svoje skale.
                style = Type.body,
                fontWeight = FontWeight.SemiBold
            )
            // Kvacica resenosti: Faza 8, Task 3 zamenila je emoji (✅) sa
            // `Icons.Default.CheckCircle`. Raniji oblik ovog komentara je
            // objasnjavao da emoji glif nosi SOPSTVENU boju, pa boje nije ni
            // bilo u kodu — to vise nije tacno: ikona treba tint i on se BIRA,
            // `DS.success`. Ovo NIJE dekorativna ikona (za razliku od
            // strelica koje stoje u istom redu): datum levo od nje ne kaze
            // "resen", pa ikona nosi informaciju koju tekst ne ponavlja —
            // otud pravi `contentDescription`. Podloga je `DS.fill` (cela
            // `DateNavigationRow`); par `success/fill` je izmeren u
            // `ContrastTest.puzzleSolvedCheckmarkIsReadableOnDateRow`.
            if (viewModel.isSolved(viewModel.selectedDate)) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = loc("Rešeno"),
                    tint = DS.success,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Next button
        Button(
            onClick = { viewModel.goToNext() },
            enabled = viewModel.canGoNext,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = DS.ink,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = DS.inkMuted
            ),
            contentPadding = PaddingValues(horizontal = DS.Space.s)
        ) {
            // Isti obrazac kao "Prethodni dan" iznad — sama, bez teksta pored
            // sebe. `disabledContentColor = DS.inkMuted` kad `!canGoNext`;
            // par vec pokriven `nonTextPairsOverFillAreDistinguishable`.
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = loc("Sledeći dan"),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun PuzzleMetadataHeader(viewModel: PuzzleViewModel) {
    val puzzle = viewModel.currentPuzzle ?: return
    
    val diffColor = when (puzzle.difficultyColor) {
        "green" -> DS.success
        "yellow" -> DS.warning
        else -> DS.danger
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DS.Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Rating indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = loc("Rejting: "),
                color = DS.inkMuted,
                // iOS: `.dsCaption` (`Chessko/Views/PuzzleView.swift:314`); vrednost
                // pored vec stoji na `Type.mono`, kao iOS `.dsMono.weight(.semibold)`.
                style = Type.caption
            )
            Text(
                text = puzzle.rating.toString(),
                style = Type.mono,
                color = DS.ink,
                fontWeight = FontWeight.Bold
            )
        }

        // Right: Difficulty Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(diffColor.copy(alpha = 0.15f))
                .border(1.dp, diffColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = DS.Space.s, vertical = 3.dp)
        ) {
            Text(
                text = puzzle.difficultyLabel,
                color = diffColor,
                // Bedz, ne recenica -> `label`. iOS pilulu tezine nema uopste
                // (`grep -rn difficultyLabel Chessko/Views` je prazan).
                style = Type.label,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Statusna kartica specificna za tab Zadaci — menja podlogu po ishodu
 * (reseno/pogresan potez/u toku), za razliku od generickog `StatusBanner`-a
 * (`UiComponents.kt`) koji ostaje neutralan i koriste ga tab Igra i
 * `StepPracticeView`. Tekst je uvek `DS.onAccent`: `accent`/`success`/`danger`
 * dele isti obrazac tamno<->svetlo izmedju tema (tamna nijansa u svetloj temi,
 * pastelna u tamnoj), pa `onAccent` (bela u svetloj, `inkFixed` u tamnoj) daje
 * >=4.5:1 kontrast na sve tri podloge u obe teme -- provereno racunski, vidi
 * task-4-report.md.
 */
@Composable
fun PuzzleStatusBanner(phase: PuzzlePhase, message: String) {
    val backgroundColor = when (phase) {
        PuzzlePhase.SOLVED -> DS.success
        PuzzlePhase.WRONG_MOVE -> DS.danger
        else -> DS.accent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.Radius.s))
            .background(backgroundColor)
            .padding(vertical = 10.dp, horizontal = DS.Space.m),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = DS.onAccent,
            // iOS status kartica: `.dsBody` (`Chessko/Views/PuzzleView.swift:345`).
            style = Type.body,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PuzzleActionsRow(viewModel: PuzzleViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DS.Space.s)
    ) {
        // Vezbanje bez kraja: uvek dostupno kad je zadatak resen, u OBA rezima
        // (dnevni i vezbovni) — iznad ostalih kontrola, kao primarna akcija.
        if (viewModel.phase == PuzzlePhase.SOLVED) {
            Button(
                onClick = { viewModel.nextPuzzle() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = loc("Sledeći zadatak"), fontWeight = FontWeight.SemiBold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DS.Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Solution Button
            Button(
                onClick = { viewModel.showSolution() },
                // `isPlayerTurn` je NUZAN deo uslova: `showSolution()` je iznutra
                // gejtovan na `!awaitingOpponent`, pa bi bez ovoga dugme tokom
                // 600 ms cekanja na protivnicki odgovor izgledalo pritisno a ne
                // bi radilo nista — dodir se tiho proguta.
                enabled = viewModel.isPlayerTurn &&
                    (viewModel.phase == PuzzlePhase.PLAYING || viewModel.phase == PuzzlePhase.WRONG_MOVE),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DS.fill,
                    contentColor = DS.ink,
                    disabledContainerColor = DS.fill,
                    disabledContentColor = DS.inkMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                // Faza 8, Task 3: emoji -> Icon. DEKORATIVNA, i to najociglednije
                // na grani: tekst pored ikone je RECJU ZA REC ista fraza koju je
                // prvi prelaz stavio u `contentDescription` — citac ekrana bi
                // izgovorio „Prikaži rešenje, Prikaži rešenje, dugme". Boja se
                // nasledjuje iz `ButtonDefaults` (`DS.ink`/`DS.inkMuted`), isti
                // par kao ostatak dugmadi ovog reda.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    // Tekst uz ikonu na zbijenom dugmetu (dva dugmeta dele red) ->
                    // `Type.label`, ista uloga kao `ActionsRow` na ekranu Igra.
                    Text(text = loc("Prikaži rešenje"), style = Type.label, fontWeight = FontWeight.SemiBold)
                }
            }

            // Restart / Retry Button
            Button(
                onClick = { viewModel.loadDailyPuzzle() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DS.fill,
                    contentColor = DS.ink
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                // Isti slucaj kao dugme iznad: vidljiv tekst je identican frazi
                // koju je prvi prelaz stavio u `contentDescription` („Pokušaj
                // ponovo"), pa je ikona dekorativna. Kljuc ostaje u recniku —
                // ovde je i dalje VIDLJIV tekst dugmeta, samo vise nije opis.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(text = loc("Pokušaj ponovo"), style = Type.label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
