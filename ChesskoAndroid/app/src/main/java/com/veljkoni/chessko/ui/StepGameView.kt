package com.veljkoni.chessko.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veljkoni.chessko.logic.GameDifficulty
import com.veljkoni.chessko.logic.ProgressStore
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.GameViewModel

// MARK: - Pokretac koraka `game` (Faza 6c, Task 7)
//
// Partija protiv racunara sa tezinom (i opciono startnom pozicijom) iz
// koraka. Ceo tok partije -- tap, legalnost, AI, zvuk, haptika, promocija,
// kraj partije -- vec zivi u `GameViewModel`-u i deli se sa tabom Igra; ovaj
// ekran dodaje samo ono sto korak ima a slobodna partija nema: zavrsetak
// KORAKA.
//
// Tri odluke koje ekran nosi:
// 1. SOPSTVENI `GameViewModel`, sa sopstvenim slotom za cuvanje
//    (`GameViewModel.stepSaveKey(stepId)`). Deljeni primerak (ili zajednicki
//    kljuc) bi ulaskom u korak ucitao/pregazio partiju koju korisnik ima u
//    toku na tabu Igra -- isti razlog zbog kog `StepPracticeView` drzi
//    sopstveni `PuzzleViewModel`.
// 2. Korak se zavrsava kad partija dodje do KRAJA, BEZ OBZIRA NA ISHOD --
//    spec trazi "partija odigrana do kraja", ne pobedu. Predaja je takodje
//    kraj.
// 3. Upis koraka je gejtovan na `pendingStepId`, da se ne bi desio dvaput za
//    isti ulazak (npr. ako se status pozicije ponovo izracuna dok je partija
//    vec gotova).
@Composable
fun StepGameView(difficulty: String, startFEN: String?, stepId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val progressStore = remember { ProgressStore.getInstance(app) }

    // Sirov string iz kurikuluma ("beginner", "easy", ...) -- `Curriculum.parse`
    // ga vec proverava protiv `KNOWN_DIFFICULTIES`, pa je ovo mapiranje totalno
    // u praksi; fallback na `MEDIUM` postoji samo da ekran nikad ne padne.
    val gameDifficulty = remember(difficulty) {
        GameDifficulty.values().find { it.name.equals(difficulty, ignoreCase = true) }
            ?: GameDifficulty.MEDIUM
    }

    // Sopstveni model po `stepId`: novi korak dobija nov primerak, a s njim i
    // nov slot za cuvanje (`stepSaveKey`).
    val viewModel = remember(stepId) { GameViewModel(app, GameViewModel.stepSaveKey(stepId)) }

    // `viewModel` je napravljen kroz `remember`, NE kroz `ViewModelStore`, pa mu se
    // `onCleared()` NIKAD ne izvrsi -- `SoundManager` (nije singleton, svaki
    // `GameViewModel` pravi SVOJ `SoundPool`) bi bez ovoga procureo na svaki izlazak
    // iz koraka. Kljuc je `stepId`, isto kao `remember` iznad: ako se `stepId`
    // promeni dok je ekran ziv, STARI model se oslobadja pre nego sto se efekat
    // za novi instalira, ne tek kad ceo ekran nestane. Isti obrazac kao
    // `ChessClockView.kt` (`DisposableEffect` + `release()`).
    DisposableEffect(stepId) {
        onDispose { viewModel.releaseSounds() }
    }

    // `null` znaci "vec upisan" -- zavrsetak se okida TACNO jednom po ulasku,
    // ne na svako ponovno izracunavanje statusa partije.
    var pendingStepId by remember(stepId) { mutableStateOf<String?>(stepId) }

    var showResignConfirm by remember { mutableStateOf(false) }
    var showAnalysis by remember { mutableStateOf(false) }

    LaunchedEffect(stepId) {
        viewModel.startStepGame(gameDifficulty, startFEN)
        // Branik za slucaj da je partija gotova pre nego sto donji efekat ima
        // sta da uporedi. `startStepGame` zavrsenu partiju ne nastavlja (resetuje
        // je), pa je ovo danas nedostizno -- ali uslov zavrsetka stoji na oba
        // puta do njega, isto kao iOS `start()`/`completeStepOnce()`.
        if (viewModel.isGameOver && pendingStepId == stepId) {
            pendingStepId = null
            progressStore.completeStep(stepId)
            GameViewModel.clearStepSave(app, stepId)
        }
    }

    // Reaguje na SVAKU promenu statusa partije (potez, mat, remi, predaja).
    LaunchedEffect(viewModel.gameState.status) {
        if (viewModel.isGameOver && pendingStepId == stepId) {
            pendingStepId = null
            progressStore.completeStep(stepId)
            GameViewModel.clearStepSave(app, stepId)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = loc("Partija"),
                color = DS.ink,
                // Zaglavlje ekrana koraka -> `heading`, isto kao `StepPracticeView`.
                style = Type.heading,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { viewModel.undo() },
                    enabled = viewModel.canUndo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = DS.ink,
                        // `disabledContainerColor` se NE sme izostaviti: M3
                        // podrazumevano daje `onSurface` @12%, pa afordansa cita
                        // naopako — onemoguceno dugme postane popunjena siva pilula,
                        // a omoguceno goli glif.
                        disabledContainerColor = Color.Transparent,
                        // Ranije je i ovo bilo `DS.inkMuted`, identicno sa
                        // `contentColor` — dugme je izgledalo isto u oba stanja.
                        // Ostatak grane razlikuje stanja bas ovim parom
                        // (`MainActivity.kt:1239-1241`, `:1275-1277`,
                        // `PuzzleView.kt:454-457`): `DS.ink` ukljuceno, `DS.inkMuted`
                        // iskljuceno. Podloga je `DS.ground`; izmereno
                        // `ink`/`ground` = 15,72 (svetla) / 16,15 (tamna),
                        // `inkMuted`/`ground` = 4,36 / 5,94.
                        disabledContentColor = DS.inkMuted
                    )
                ) {
                    // Faza 8, talas ispravki: `Text("↩︎")` -> `Icon`. Glif je
                    // prezivEO celu fazu jer je provera obima grepovala samo
                    // emoji blokove (`1F300–1FAFF`, `2600–27BF`, `2B00–2BFF`),
                    // a `U+21A9` je u bloku strelica. Dugme nema tekst — ikona
                    // stoji SAMA, pa dobija opis; isti kljuc i ista radnja kao
                    // `Undo` u `MainActivity.kt` (gde je dekorativna, jer je
                    // tamo uz vidljiv `Text`). `AutoMirrored` je bitan: glif
                    // se u RTL rasporedu mora okrenuti, sto goli `Text` nije
                    // radio. Boja se nasledjuje iz `ButtonDefaults` iznad
                    // (`DS.ink` / `DS.inkMuted`), par vec izmeren u komentaru.
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = loc("Vrati potez"),
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (viewModel.canResign) {
                    Button(
                        onClick = { showResignConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = DS.danger
                        )
                    ) {
                        // Faza 8, Task 3: emoji -> Icon. Dugme nema tekst pored
                        // sebe (za razliku od "Zatvori" odmah desno) — opis je
                        // neophodan. Isti kljuc kao `Flag` u `MainActivity.kt`
                        // (Faza 8, Task 2), ista radnja.
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = loc("Predaj partiju"),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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
        }

        OpponentCard(viewModel)
        StatusCard(message = getStatusMessage(viewModel))

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

        StepGameFooter(
            viewModel = viewModel,
            isGameOver = viewModel.isGameOver,
            onClose = onClose,
            onAnalyze = { showAnalysis = true }
        )
    }

    if (viewModel.showPromotion) {
        PromotionOverlay(viewModel)
    }

    // Korak se upisuje kao zavrsen PRE ovoga (vidi LaunchedEffect-e iznad,
    // oba gejtovana na `isGameOver && pendingStepId == stepId`), pa analiza
    // NIKAD ne stoji na putu zavrsetka koraka -- ako motor nije dostupan,
    // korak je vec zavrsen, samo dugme ostaje bez efekta.
    if (showAnalysis) {
        Dialog(
            onDismissRequest = { showAnalysis = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AnalysisView(viewModel = viewModel, onClose = { showAnalysis = false })
        }
    }

    if (showResignConfirm) {
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text(text = loc("Predaja partije"), color = DS.ink, fontWeight = FontWeight.Bold) },
            // 0.8f je van tacnog opsega inkMuted (0.5-0.75f) iz tabele, ali je
            // ovo bas telo/supporting tekst dijaloga -- isti M3 slot
            // (`DialogTokens.SupportingTextColor`) koji `Theme.kt` vec mapira na
            // `onSurfaceVariant` = `inkMuted` po podrazumevanoj vrednosti.
            text = { Text(text = loc("Da li ste sigurni da želite da predate partiju?"), color = DS.inkMuted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resign()
                    showResignConfirm = false
                }) {
                    Text(text = loc("Predaj"), color = DS.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) {
                    Text(text = loc("Nastavi igru"), color = DS.inkMuted)
                }
            },
            containerColor = DS.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun OpponentCard(viewModel: GameViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DS.fill)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Faza 8, Task 3: emoji -> Icon. Ikona stoji NEPOSREDNO uz vidljiv
        // `Text(loc("Računar"))` -- isti obrazac kao `Computer`/`People` u
        // `MainActivity.kt` (Faza 8, Task 2, ispravljeno u progress.md nakon
        // sto je prvobitni kljuc "Protivnik računar" bio dodeljen ovom istom
        // slucaju): dekorativna, BEZ kljuca (ne samo `null` opis vec i BEZ
        // dodatog `Loc.kt` unosa) -- ovaj korak, za razliku od slobodne
        // partije na tabu Igra, nema prekidac Racunar/Prijatelj, pa se ikona
        // ovde nikad ne menja u `People`.
        Icon(
            imageVector = Icons.Default.Computer,
            contentDescription = null,
            tint = DS.ink,
            modifier = Modifier.size(16.dp)
        )
        // Kartica protivnika je paritet sa iOS-om
        // (`Chessko/Views/StepGameView.swift:199-205`): naziv `.dsBody`, tezina
        // `.dsCaption`. `fontWeight = Medium` ostaje -- iOS ga nema, ali brisanje
        // bi bila promena izgleda koju ovaj task ne trazi.
        Text(text = loc("Računar"), color = DS.ink, style = Type.body, fontWeight = FontWeight.Medium)
        Text(text = viewModel.difficulty.label, color = DS.inkMuted, style = Type.caption)
        Spacer(modifier = Modifier.weight(1f))
        if (viewModel.isThinking) {
            Text(text = loc("Računar razmišlja..."), color = DS.inkMuted, style = Type.caption)
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DS.fill)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // iOS: `.dsBody` (`Chessko/Views/StepGameView.swift:229`).
        Text(text = message, color = DS.ink, style = Type.body)
    }
}

@Composable
private fun StepGameFooter(
    viewModel: GameViewModel,
    isGameOver: Boolean,
    onClose: () -> Unit,
    onAnalyze: () -> Unit
) {
    if (isGameOver) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(DS.success))
                Text(text = loc("Korak je završen"), color = DS.ink, fontWeight = FontWeight.SemiBold)
            }
            // Korak je vec upisan kao zavrsen (vidi komentar na mestu poziva) --
            // ovo dugme je cisto dodatna informacija, ne uslov za nastavak Puta.
            AnalysisButton(viewModel = viewModel, onClick = onAnalyze)
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DS.accent, contentColor = DS.onAccent)
            ) {
                Text(text = loc("Nazad na Put"), fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Text(
            text = loc("Korak se završava kad partija dođe do kraja."),
            color = DS.inkMuted,
            // iOS: `.dsCaption` (`Chessko/Views/StepGameView.swift:290`).
            style = Type.caption,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
