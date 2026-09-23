package com.veljkoni.chessko

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.veljkoni.chessko.models.*
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.ui.SettingsView
import com.veljkoni.chessko.ui.ChessClockView
import com.veljkoni.chessko.logic.ChessAI
import com.veljkoni.chessko.logic.StockfishEngine
import com.veljkoni.chessko.logic.GameDifficulty
import com.veljkoni.chessko.ui.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.foundation.shape.CircleShape
import com.veljkoni.chessko.ui.theme.ChesskoTheme
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.GameMode
import com.veljkoni.chessko.viewmodels.GameViewModel
import com.veljkoni.chessko.viewmodels.PuzzleViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure global Coil ImageLoader to support SVGs
        val globalLoader = ImageLoader.Builder(applicationContext)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
        Coil.setImageLoader(globalLoader)

        // Initialize Stockfish engine in the background
        StockfishEngine.start(applicationContext, "nn-1c0000000000.nnue", "nn-37f18f62d772.nnue")

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsManager = remember { SettingsManager.getInstance(context) }
            val isDark = when (settingsManager.colorScheme) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ChesskoTheme(darkTheme = isDark) {
                var languageKey by remember { mutableStateOf(settingsManager.languageCode) }

                key(languageKey) {
                    val gameViewModel = remember { GameViewModel(context.applicationContext as Application) }
                    val puzzleViewModel = remember { PuzzleViewModel(context.applicationContext as Application) }

                    // `gameViewModel`/`puzzleViewModel` su `remember`-ovani ovde, NE kroz
                    // `ViewModelStore` -- `onCleared()` im se zato nikad ne izvrsi. Svaki
                    // pravi sopstveni `SoundManager` (nije singleton), pa promena jezika
                    // (`key(languageKey)` odbacuje ceo blok i pravi NOVE modele) bez ovoga
                    // ostavlja stare `SoundPool`-ove procurele do gasenja procesa. Isti
                    // obrazac kao `ChessClockView.kt`/`StepPracticeView.kt`/`StepGameView.kt`
                    // -- vezano za TACNO onaj opseg (`languageKey`) koji te modele pravi, da
                    // se ne oslobodi primerak koji je ekran jos koristi.
                    DisposableEffect(languageKey) {
                        onDispose {
                            gameViewModel.releaseSounds()
                            puzzleViewModel.releaseSounds()
                        }
                    }

                var showSettings by remember { mutableStateOf(false) }
                var showNewGameMenu by remember { mutableStateOf(false) }
                var showColorPicker by remember { mutableStateOf(false) }
                var showChessClock by remember { mutableStateOf(false) }
                var showResetConfirm by remember { mutableStateOf(false) }
                var showResignConfirm by remember { mutableStateOf(false) }
                var dismissGameOverOverlay by remember { mutableStateOf(false) }
                var showAnalysis by remember { mutableStateOf(false) }

                LaunchedEffect(gameViewModel.gameState.status) {
                    if (gameViewModel.gameState.status is GameStatus.Playing) {
                        dismissGameOverOverlay = false
                    }
                }
                
                // Preload all 12 default piece assets
                LaunchedEffect(Unit) {
                    val pieces = listOf("pawn", "knight", "bishop", "rook", "queen", "king")
                    val colors = listOf("white", "black")
                    for (color in colors) {
                        for (piece in pieces) {
                            val assetPath = "file:///android_asset/pieces/piece_${color}_${piece}.svg"
                            val request = ImageRequest.Builder(context)
                                .data(assetPath)
                                .build()
                            globalLoader.enqueue(request)
                        }
                    }
                }

                // `PuzzleViewModel` vise ne ucitava dnevni zadatak sam iz svog `init`-a
                // (Faza 6c, Task 7 -- zatvara isti rizik koji je Task 6 zaobisao parametrom
                // `autoLoadDaily`). Mora se pokrenuti eksplicitno, TACNO JEDNOM za zivotni
                // vek ovog `puzzleViewModel`-a -- zato je OVDE, van `when (activeTab)`
                // grane: `puzzleViewModel` je `remember`-ovan na ovom istom nivou (unutar
                // `key(languageKey)`), pa se ovaj efekat ne ponavlja pri prebacivanju
                // tabova, samo pri promeni jezika (kad se ceo primerak ionako pravi iznova).
                // `PuzzleView`-ov sopstveni `LaunchedEffect(Unit) { refreshPersistedProgress() }`
                // (vidi `ui/PuzzleView.kt`) I DALJE radi na svaki povratak na tab -- to je
                // namerno odvojeno: on samo osvezava kes (kvacica, iskljuceni zadaci), ne
                // dira zadatak u toku.
                LaunchedEffect(Unit) { puzzleViewModel.loadDailyPuzzle() }

                var activeTab by remember { mutableStateOf(0) }

                if (showSettings) {
                    Dialog(
                        onDismissRequest = { showSettings = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        SettingsView(
                            gameViewModel = gameViewModel,
                            onDismiss = { showSettings = false },
                            onLanguageChanged = { languageKey = it }
                        )
                    }
                }

                if (showNewGameMenu) {
                    Dialog(onDismissRequest = { showNewGameMenu = false }) {
                        Surface(
                            shape = RoundedCornerShape(DS.Radius.l),
                            color = DS.surface,
                            modifier = Modifier.padding(DS.Space.l)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DS.Space.l),
                                verticalArrangement = Arrangement.spacedBy(DS.Space.m)
                            ) {
                                Text(
                                    text = loc("Nova igra"),
                                    color = DS.ink,
                                    // Naslov menija -> `heading`.
                                    style = Type.heading,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Option 1: VS COMPUTER
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(DS.Radius.m))
                                        .background(DS.fill)
                                        .border(1.dp, DS.line, RoundedCornerShape(DS.Radius.m))
                                        .clickable {
                                            showNewGameMenu = false
                                            showColorPicker = true
                                        }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Computer,
                                        contentDescription = null,
                                        tint = DS.accent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Column {
                                        Text(text = loc("Igraj protiv računara"), color = DS.ink, style = Type.body, fontWeight = FontWeight.Bold)
                                        Text(text = loc("Igraj protiv veštačke inteligencije sa izborom boje"), color = DS.inkMuted, style = Type.label)
                                    }
                                }

                                // Option 2: LOCAL_FRIEND
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(DS.Radius.m))
                                        .background(DS.fill)
                                        .border(1.dp, DS.line, RoundedCornerShape(DS.Radius.m))
                                        .clickable {
                                            showNewGameMenu = false
                                            gameViewModel.newGame(GameMode.LOCAL_FRIEND, PieceColor.WHITE)
                                        }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = null,
                                        tint = DS.accent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Column {
                                        Text(text = loc("Igraj sa prijateljem"), color = DS.ink, style = Type.body, fontWeight = FontWeight.Bold)
                                        Text(text = loc("Lokalna igra na istom telefonu sa rotiranjem table"), color = DS.inkMuted, style = Type.label)
                                    }
                                }

                                // Option 3: CHESS_CLOCK
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(DS.Radius.m))
                                        .background(DS.fill)
                                        .border(1.dp, DS.line, RoundedCornerShape(DS.Radius.m))
                                        .clickable {
                                            showNewGameMenu = false
                                            showChessClock = true
                                        }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = DS.accent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Column {
                                        Text(text = loc("Šahovski sat"), color = DS.ink, style = Type.body, fontWeight = FontWeight.Bold)
                                        Text(text = loc("Premium šahovski sat sa vremenskim kontrolama"), color = DS.inkMuted, style = Type.label)
                                    }
                                }

                                Spacer(modifier = Modifier.height(DS.Space.xs))
                                TextButton(
                                    onClick = { showNewGameMenu = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = loc("Zatvori"), color = DS.accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (showColorPicker) {
                    Dialog(onDismissRequest = { showColorPicker = false }) {
                        Surface(
                            shape = RoundedCornerShape(DS.Radius.l),
                            color = DS.surface,
                            modifier = Modifier.padding(DS.Space.l)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DS.Space.l),
                                verticalArrangement = Arrangement.spacedBy(DS.Space.l),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = loc("Izaberi boju figura"),
                                    color = DS.ink,
                                    // Naslov dijaloga -> `heading`.
                                    style = Type.heading,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
                                ) {
                                    // White Color Card
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(DS.Radius.m))
                                            .background(DS.fill)
                                            .border(1.dp, DS.line, RoundedCornerShape(DS.Radius.m))
                                            .clickable {
                                                showColorPicker = false
                                                gameViewModel.newGame(GameMode.VS_COMPUTER, PieceColor.WHITE)
                                            }
                                            .padding(vertical = DS.Space.l),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // PieceColor.WHITE se namerno NE tokenizuje -- ovaj krug
                                        // predstavlja stranu u igri, ne temu (CLAUDE.md, Dizajn sistem).
                                        // Kartica ispod (DS.fill) JESTE tema-zavisna, pa u svetloj temi
                                        // postaje skoro bela -- beli krug bez ivice bi tu imao ~1.20:1
                                        // kontrast (racunato WCAG formulom), gotovo nevidljiv. Fiksna
                                        // tamna ivica (isti obrazac kao fiksna svetla ivica oko crnog
                                        // kruga ispod) drzi krug vidljivim u obe teme, bez obzira sta
                                        // DS.fill trenutno vredi.
                                        Icon(
                                            imageVector = Icons.Default.Circle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.height(DS.Space.s))
                                        Text(text = loc("Beli"), color = DS.ink, style = Type.body, fontWeight = FontWeight.Bold)
                                    }

                                    // Random Color Card
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(DS.Radius.m))
                                            .background(DS.fill)
                                            .border(1.dp, DS.line, RoundedCornerShape(DS.Radius.m))
                                            .clickable {
                                                showColorPicker = false
                                                val selectedColor = if (Math.random() < 0.5) PieceColor.WHITE else PieceColor.BLACK
                                                gameViewModel.newGame(GameMode.VS_COMPUTER, selectedColor)
                                            }
                                            .padding(vertical = DS.Space.l),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shuffle,
                                            contentDescription = null,
                                            tint = DS.accent,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(DS.Space.s))
                                        Text(text = loc("Nasumično"), color = DS.ink, style = Type.body, fontWeight = FontWeight.Bold)
                                    }

                                    // Black Color Card
                                    //
                                    // PieceColor.BLACK se namerno NE tokenizuje -- ova kartica
                                    // prikazuje STRANU U IGRI, ne temu, pa je cela namerno
                                    // tema-nezavisna (isti izuzetak kao "Beli" krug gore i "Crni"
                                    // krug ispod). Zato SVE na njoj -- pozadina, ivica kartice i
                                    // ivica kruga -- ostaje fiksna providna belo/crna, NIKAD DS
                                    // token: tema-zavisan token nad tema-nezavisnom podlogom
                                    // razilazi se cim se tema promeni (ovde je tako jednom vec
                                    // otkriveno -- DS.line tamno #232C46 nad ovom skoro crnom
                                    // podlogom davalo je ~1.36:1, prakticno nevidljivu ivicu;
                                    // fiksna providna bela vraca ~2.88:1 u obe teme).
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(DS.Radius.m))
                                            .background(Color.Black.copy(alpha = 0.4f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(DS.Radius.m))
                                            .clickable {
                                                showColorPicker = false
                                                gameViewModel.newGame(GameMode.VS_COMPUTER, PieceColor.BLACK)
                                            }
                                            .padding(vertical = DS.Space.l),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Circle,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.height(DS.Space.s))
                                        Text(text = loc("Crni"), color = DS.ink, style = Type.body, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = { showColorPicker = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = loc("Zatvori"), color = DS.accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (showChessClock) {
                    Dialog(
                        onDismissRequest = { showChessClock = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        ChessClockView(onDismiss = { showChessClock = false })
                    }
                }

                if (showAnalysis) {
                    Dialog(
                        onDismissRequest = { showAnalysis = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        AnalysisView(viewModel = gameViewModel, onClose = { showAnalysis = false })
                    }
                }

                if (showResetConfirm) {
                    AlertDialog(
                        onDismissRequest = { showResetConfirm = false },
                        title = { Text(text = loc("Potvrda"), color = DS.ink, fontWeight = FontWeight.Bold) },
                        text = { Text(text = loc("Da li ste sigurni? Trenutna partija će biti izgubljena."), color = DS.inkMuted) },
                        confirmButton = {
                            TextButton(onClick = {
                                gameViewModel.resetGame()
                                showResetConfirm = false
                            }) {
                                Text(text = loc("Potvrdi"), color = DS.accent, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetConfirm = false }) {
                                Text(text = loc("Otkaži"), color = DS.inkMuted)
                            }
                        },
                        containerColor = DS.surface,
                        shape = RoundedCornerShape(DS.Radius.l)
                    )
                }

                if (showResignConfirm) {
                    AlertDialog(
                        onDismissRequest = { showResignConfirm = false },
                        title = { Text(text = loc("Predaja partije"), color = DS.ink, fontWeight = FontWeight.Bold) },
                        text = { Text(text = loc("Da li ste sigurni da želite da predate partiju?"), color = DS.inkMuted) },
                        confirmButton = {
                            TextButton(onClick = {
                                gameViewModel.resign()
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
                        shape = RoundedCornerShape(DS.Radius.l)
                    )
                }

                // Ziv bug (Faza 6c, Task 7): GameViewModel je od pocetka postavljao
                // showPromotion = true i cekao izbor figure, a taj izbor nije
                // renderovao NIKO -- promocija pesaka je zauvek blokirala tablu
                // slobodne partije (autoPromoteToQueen je podrazumevano false).
                if (gameViewModel.showPromotion) {
                    PromotionOverlay(gameViewModel)
                }

                if (gameViewModel.isGameOver && !dismissGameOverOverlay) {
                    Dialog(
                        onDismissRequest = { dismissGameOverOverlay = true }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = DS.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .padding(DS.Space.l)
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DS.Space.xl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Faza 8, Task 2: emoji -> Material ikone. Samo pobeda (bivsi
                                // "🏆") ima iOS par (`trophy.fill`); poraz/remi/u toku nemaju,
                                // pa je izbor ikone i boje presudjen ovde:
                                //  - poraz -> SentimentDissatisfied (izraz lica, ne zastava --
                                //    zastava je vec zauzeta predajom)
                                //  - remi -> Handshake (ni pobeda ni poraz -- neutralna boja)
                                //  - "u toku" (nedostizna grana dok je dijalog uslovljen
                                //    `isGameOver`, ali zadrzana odbrambeno) -> BEZ ikone, status
                                //    ispod vec nosi ceo tekst
                                // Boja i dalje nosi znacenje kao ranije (kad su ga nosili
                                // razliciti emoji glifovi): DS.success za pobedu, DS.danger za
                                // poraz, DS.inkMuted za remi. Ikone su ovde DEKORATIVNE
                                // (`contentDescription = null`) -- ceo ishod je ispisan tekstom
                                // odmah ispod (loc("Kraj partije") + getStatusMessage), isti
                                // obrazac kao decorative ikone u meniju "Nova igra" iznad.
                                val (statusIcon, statusIconTint) = when (val s = gameViewModel.gameState.status) {
                                    is GameStatus.Checkmate -> {
                                        if (gameViewModel.gameMode == GameMode.LOCAL_FRIEND) {
                                            Icons.Default.EmojiEvents to DS.success
                                        } else if (s.color == gameViewModel.playerColor) {
                                            Icons.Default.SentimentDissatisfied to DS.danger
                                        } else {
                                            Icons.Default.EmojiEvents to DS.success
                                        }
                                    }
                                    is GameStatus.Resigned -> {
                                        if (gameViewModel.gameMode == GameMode.LOCAL_FRIEND) {
                                            Icons.Default.Flag to DS.danger
                                        } else if (s.color == gameViewModel.playerColor) {
                                            Icons.Default.Flag to DS.danger
                                        } else {
                                            Icons.Default.EmojiEvents to DS.success
                                        }
                                    }
                                    is GameStatus.Draw -> Icons.Default.Handshake to DS.inkMuted
                                    else -> null to DS.inkMuted
                                }

                                if (statusIcon != null) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = statusIconTint,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                Text(
                                    text = loc("Kraj partije"),
                                    color = DS.inkMuted,
                                    style = Type.caption,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp
                                )

                                Text(
                                    text = getStatusMessage(gameViewModel),
                                    color = DS.ink,
                                    // Naslov ishoda partije -> `title`, i to je paritet:
                                    // iOS crta `.title2.weight(.semibold)` = 22
                                    // (`Chessko/Views/GameView.swift:574`).
                                    style = Type.title,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Dugme za pregled / analizu partije
                                Button(
                                    onClick = {
                                        dismissGameOverOverlay = true
                                        gameViewModel.goToStart()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(DS.Radius.m),
                                    // Nema tacnog pogotka u tabeli preslikavanja za solidnu 0xFF334155 --
                                    // ista uloga (sekundarno dugme) kao svi ostali translucentni beli
                                    // fill-ovi u ovom fajlu, pa ide na isti DS.fill (vidi izvestaj).
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DS.fill,
                                        contentColor = DS.ink
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(DS.Space.s))
                                    Text(
                                        text = loc("Pregledaj partiju"),
                                        style = Type.body,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Dugme za novu igru
                                Button(
                                    onClick = {
                                        dismissGameOverOverlay = true
                                        showNewGameMenu = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(DS.Radius.m),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DS.accent,
                                        contentColor = DS.onAccent
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(DS.Space.s))
                                    Text(
                                        text = loc("Nova igra"),
                                        style = Type.body,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .background(DS.navBar)
                                .padding(vertical = DS.Space.xs),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomNavItem(
                                icon = Icons.Default.PlayArrow,
                                label = loc("Igra"),
                                isSelected = activeTab == 0,
                                onClick = { activeTab = 0 }
                            )
                            BottomNavItem(
                                icon = Icons.Default.Extension,
                                label = loc("Zadaci"),
                                isSelected = activeTab == 1,
                                onClick = { activeTab = 1 }
                            )
                            BottomNavItem(
                                icon = Icons.Filled.Map,
                                label = loc("Put"),
                                isSelected = activeTab == 2,
                                onClick = { activeTab = 2 }
                            )
                        }
                    }
                ) { innerPadding ->
                    // Pozadina ekrana -- ravna DS.ground umesto fiksnog tamnog gradijenta
                    // (brief Task-a 2: "pozadina ekrana -> DS.ground"), da bi svetla tema
                    // stvarno posvetlila i ovaj deo ekrana.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DS.ground)
                            .padding(innerPadding)
                    ) {
                        when (activeTab) {
                            0 -> {
                                val configuration = LocalConfiguration.current
                                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                                val activeTheme = remember(settingsManager.boardTheme) {
                                    BoardTheme.values().find { it.rawValue == settingsManager.boardTheme } ?: BoardTheme.CLASSIC
                                }
                                val activeStyle = remember(settingsManager.pieceStyle) {
                                    PieceStyle.values().find { it.rawValue == settingsManager.pieceStyle } ?: PieceStyle.CLASSIC
                                }

                                val topColor = if (gameViewModel.isFlipped) PieceColor.WHITE else PieceColor.BLACK
                                val topCaptured = if (topColor == PieceColor.WHITE) {
                                    gameViewModel.gameState.capturedByWhite
                                } else {
                                    gameViewModel.gameState.capturedByBlack
                                }
                                val topAdv = getMaterialAdvantage(gameViewModel.gameState, topColor)

                                val bottomColor = if (gameViewModel.isFlipped) PieceColor.BLACK else PieceColor.WHITE
                                val bottomCaptured = if (bottomColor == PieceColor.WHITE) {
                                    gameViewModel.gameState.capturedByWhite
                                } else {
                                    gameViewModel.gameState.capturedByBlack
                                }
                                val bottomAdv = getMaterialAdvantage(gameViewModel.gameState, bottomColor)

                                if (isLandscape) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(DS.Space.s),
                                        horizontalArrangement = Arrangement.spacedBy(DS.Space.m),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left Side: Board & EvalBar
                                        BoxWithConstraints(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .padding(vertical = DS.Space.xs),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val boardSize = maxHeight
                                            val evalBarWidth = 6.dp
                                            val spacing = 6.dp

                                            Row(
                                                modifier = Modifier.fillMaxHeight(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (gameViewModel.showEvalBar) {
                                                    EvalBar(
                                                        evaluation = gameViewModel.evaluationScore,
                                                        mateIn = gameViewModel.evaluationMateIn,
                                                        isFlipped = gameViewModel.isFlipped,
                                                        modifier = Modifier
                                                            .size(width = evalBarWidth, height = boardSize)
                                                    )
                                                    Spacer(modifier = Modifier.width(spacing))
                                                }
                                                BoardView(
                                                    board = gameViewModel.displayState.board,
                                                    isFlipped = gameViewModel.isFlipped,
                                                    selectedPosition = if (gameViewModel.isReviewing) null else gameViewModel.selectedPosition,
                                                    legalMoves = if (gameViewModel.isReviewing) emptyList() else gameViewModel.legalMovesForSelected,
                                                    lastMove = gameViewModel.displayLastMove,
                                                    gameStatus = gameViewModel.displayState.status,
                                                    boardTheme = activeTheme,
                                                    pieceStyle = activeStyle,
                                                    showCoordinates = settingsManager.showCoordinates,
                                                    showLastMoveHighlight = settingsManager.showLastMoveHighlight,
                                                    showLegalMoves = settingsManager.showLegalMoves,
                                                    onTap = { pos -> gameViewModel.tap(pos) },
                                                    // Pejzaz: strana table se racuna iz VISINE
                                                    // (boardSize = maxHeight), pa granica ide na
                                                    // obe ose — jedna osa bi ostavila pravougaonik
                                                    // umesto kvadrata na tabletu.
                                                    modifier = Modifier
                                                        .sizeIn(maxWidth = DS.maxBoardSide, maxHeight = DS.maxBoardSide)
                                                        .size(boardSize)
                                                )
                                            }
                                        }

                                        // Right Side: Header, Player Cards, Move History & Controls
                                        val landscapeScrollState = rememberScrollState()
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .verticalScroll(landscapeScrollState)
                                                .padding(end = DS.Space.s),
                                            verticalArrangement = Arrangement.spacedBy(DS.Space.s)
                                        ) {
                                            // Top Header
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { showNewGameMenu = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = loc("Nova igra"),
                                                        tint = DS.ink,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                Text(
                                                    text = loc("Chessko"),
                                                    color = DS.ink,
                                                    // Naslov aplikacije -> `title`. Pejzazna
                                                    // grana je stajala na 20, portretna na 22 --
                                                    // ista uloga, dve vrednosti; sada jedna.
                                                    style = Type.title,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                IconButton(
                                                    onClick = { showSettings = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Menu,
                                                        contentDescription = loc("Podešavanja"),
                                                        tint = DS.ink,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }

                                            // Top Player Card
                                            PlayerHeaderCard(
                                                color = topColor,
                                                name = getPlayerName(gameViewModel, topColor),
                                                isActive = gameViewModel.gameState.currentTurn == topColor && !gameViewModel.isGameOver,
                                                isThinking = topColor != gameViewModel.playerColor && gameViewModel.isThinking,
                                                capturedPieces = topCaptured,
                                                materialAdvantage = topAdv,
                                                boardTheme = activeTheme,
                                                pieceStyle = activeStyle,
                                                evaluationScore = gameViewModel.evaluationScore,
                                                evaluationMateIn = gameViewModel.evaluationMateIn,
                                                showEvalBar = gameViewModel.showEvalBar
                                            )

                                            // Bottom Player Card
                                            PlayerHeaderCard(
                                                color = bottomColor,
                                                name = getPlayerName(gameViewModel, bottomColor),
                                                isActive = gameViewModel.gameState.currentTurn == bottomColor && !gameViewModel.isGameOver,
                                                isThinking = bottomColor != gameViewModel.playerColor && gameViewModel.isThinking,
                                                capturedPieces = bottomCaptured,
                                                materialAdvantage = bottomAdv,
                                                boardTheme = activeTheme,
                                                pieceStyle = activeStyle,
                                                evaluationScore = gameViewModel.evaluationScore,
                                                evaluationMateIn = gameViewModel.evaluationMateIn,
                                                showEvalBar = gameViewModel.showEvalBar
                                            )

                                            // Review Controls (Move Navigation)
                                            if (gameViewModel.totalReviewMoves > 0) {
                                                ReviewControlsRow(
                                                    viewModel = gameViewModel,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            // Dugme "Analiziraj partiju" -- vidljivo samo na gotovoj
                                            // partiji sa bar jednim potezom (vidi AnalysisButton ispod).
                                            AnalysisButton(
                                                viewModel = gameViewModel,
                                                onClick = { showAnalysis = true }
                                            )

                                            // Move History
                                            if (gameViewModel.gameState.moveNotations.isNotEmpty()) {
                                                Text(
                                                    text = loc("Potezi"),
                                                    color = DS.inkMuted,
                                                    // iOS: `.subheadline.weight(.semibold)`
                                                    // (`Chessko/Views/GameView.swift:81`). Pejzaz je
                                                    // bio 13, portret 14 -- ista uloga, dve vrednosti.
                                                    style = Type.body,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                MoveHistoryView(
                                                    notations = gameViewModel.gameState.moveNotations,
                                                    selectedMoveIndex = gameViewModel.viewingMoveIndex,
                                                    onSelectMove = { gameViewModel.goToMove(it) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            // Status Banner
                                            StatusBanner(message = getStatusMessage(gameViewModel))

                                            // Actions Row
                                            ActionsRow(
                                                viewModel = gameViewModel,
                                                onReset = {
                                                    if (gameViewModel.gameState.moveNotations.isNotEmpty()) {
                                                        showResetConfirm = true
                                                    } else {
                                                        gameViewModel.resetGame()
                                                    }
                                                },
                                                onResign = {
                                                    showResignConfirm = true
                                                },
                                                onShare = {
                                                    val pgn = gameViewModel.generatePGN()
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, pgn)
                                                        putExtra(Intent.EXTRA_SUBJECT, loc("Chessko partija"))
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, loc("Podeli partiju")))
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    // Portrait mode (existing Edge-to-Edge full width layout)
                                    val gameScrollState = rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(gameScrollState),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .widthIn(max = 500.dp)
                                                .padding(vertical = DS.Space.m),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // 0. Top Header
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = DS.Space.l, vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { showNewGameMenu = true },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = loc("Nova igra"),
                                                        tint = DS.ink,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                Text(
                                                    text = loc("Chessko"),
                                                    color = DS.ink,
                                                    style = Type.title,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                IconButton(
                                                    onClick = { showSettings = true },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Menu,
                                                        contentDescription = loc("Podešavanja"),
                                                        tint = DS.ink,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            // 1. Top Player Card
                                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Space.m)) {
                                                PlayerHeaderCard(
                                                    color = topColor,
                                                    name = getPlayerName(gameViewModel, topColor),
                                                    isActive = gameViewModel.gameState.currentTurn == topColor && !gameViewModel.isGameOver,
                                                    isThinking = topColor != gameViewModel.playerColor && gameViewModel.isThinking,
                                                    capturedPieces = topCaptured,
                                                    materialAdvantage = topAdv,
                                                    boardTheme = activeTheme,
                                                    pieceStyle = activeStyle,
                                                    evaluationScore = gameViewModel.evaluationScore,
                                                    evaluationMateIn = gameViewModel.evaluationMateIn,
                                                    showEvalBar = gameViewModel.showEvalBar
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // 2. Chessboard & EvalBar
                                            BoxWithConstraints(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = if (gameViewModel.showEvalBar) DS.Space.s else 0.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val totalWidth = maxWidth
                                                val evalBarWidth = 6.dp
                                                val spacing = 6.dp
                                                val boardSize = if (gameViewModel.showEvalBar) (totalWidth - evalBarWidth - spacing) else totalWidth

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (gameViewModel.showEvalBar) {
                                                        EvalBar(
                                                            evaluation = gameViewModel.evaluationScore,
                                                            mateIn = gameViewModel.evaluationMateIn,
                                                            isFlipped = gameViewModel.isFlipped,
                                                            modifier = Modifier
                                                                .size(width = evalBarWidth, height = boardSize)
                                                        )
                                                        Spacer(modifier = Modifier.width(spacing))
                                                    }
                                                    BoardView(
                                                        board = gameViewModel.displayState.board,
                                                        isFlipped = gameViewModel.isFlipped,
                                                        selectedPosition = if (gameViewModel.isReviewing) null else gameViewModel.selectedPosition,
                                                        legalMoves = if (gameViewModel.isReviewing) emptyList() else gameViewModel.legalMovesForSelected,
                                                        lastMove = gameViewModel.displayLastMove,
                                                        gameStatus = gameViewModel.displayState.status,
                                                        boardTheme = activeTheme,
                                                        pieceStyle = activeStyle,
                                                        showCoordinates = settingsManager.showCoordinates,
                                                        showLastMoveHighlight = settingsManager.showLastMoveHighlight,
                                                        showLegalMoves = settingsManager.showLegalMoves,
                                                        onTap = { pos -> gameViewModel.tap(pos) },
                                                        // Portret: strana table se racuna iz SIRINE
                                                        // (boardSize = totalWidth), pa je dovoljna
                                                        // gornja granica sirine.
                                                        modifier = Modifier
                                                            .widthIn(max = DS.maxBoardSide)
                                                            .size(boardSize)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // 3. Bottom Player Card
                                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Space.m)) {
                                                PlayerHeaderCard(
                                                    color = bottomColor,
                                                    name = getPlayerName(gameViewModel, bottomColor),
                                                    isActive = gameViewModel.gameState.currentTurn == bottomColor && !gameViewModel.isGameOver,
                                                    isThinking = bottomColor != gameViewModel.playerColor && gameViewModel.isThinking,
                                                    capturedPieces = bottomCaptured,
                                                    materialAdvantage = bottomAdv,
                                                    boardTheme = activeTheme,
                                                    pieceStyle = activeStyle,
                                                    evaluationScore = gameViewModel.evaluationScore,
                                                    evaluationMateIn = gameViewModel.evaluationMateIn,
                                                    showEvalBar = gameViewModel.showEvalBar
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Review Controls (Move Navigation)
                                            if (gameViewModel.totalReviewMoves > 0) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Space.m)) {
                                                    ReviewControlsRow(
                                                        viewModel = gameViewModel,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                            }

                                            // 4. Move history, Status & Actions
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Space.m)
                                            ) {
                                                // Dugme "Analiziraj partiju" -- vidljivo samo na gotovoj
                                                // partiji sa bar jednim potezom. Ova Column (za razliku od
                                                // pejzazne) nema `spacedBy`, pa razmak ide rucno, isto kao
                                                // za Review Controls iznad.
                                                if (canAnalyzeGame(gameViewModel)) {
                                                    AnalysisButton(
                                                        viewModel = gameViewModel,
                                                        onClick = { showAnalysis = true }
                                                    )
                                                    Spacer(modifier = Modifier.height(DS.Space.m))
                                                }

                                                if (gameViewModel.gameState.moveNotations.isNotEmpty()) {
                                                    Text(
                                                        text = loc("Potezi"),
                                                        color = DS.inkMuted,
                                                        style = Type.body,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(start = DS.Space.xs, bottom = DS.Space.xs)
                                                    )
                                                    MoveHistoryView(
                                                        notations = gameViewModel.gameState.moveNotations,
                                                        selectedMoveIndex = gameViewModel.viewingMoveIndex,
                                                        onSelectMove = { gameViewModel.goToMove(it) },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    Spacer(modifier = Modifier.height(DS.Space.m))
                                                }

                                                StatusBanner(message = getStatusMessage(gameViewModel))

                                                Spacer(modifier = Modifier.height(DS.Space.m))

                                                ActionsRow(
                                                    viewModel = gameViewModel,
                                                    onReset = {
                                                        if (gameViewModel.gameState.moveNotations.isNotEmpty()) {
                                                            showResetConfirm = true
                                                        } else {
                                                            gameViewModel.resetGame()
                                                        }
                                                    },
                                                    onResign = {
                                                        showResignConfirm = true
                                                    },
                                                    onShare = {
                                                        val pgn = gameViewModel.generatePGN()
                                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, pgn)
                                                            putExtra(Intent.EXTRA_SUBJECT, loc("Chessko partija"))
                                                        }
                                                        context.startActivity(Intent.createChooser(sendIntent, loc("Podeli partiju")))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                PuzzleView(viewModel = puzzleViewModel)
                            }
                            2 -> {
                                PathView()
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun ReviewControlsRow(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val currentIndex = viewModel.currentReviewMoveIndex
    val total = viewModel.totalReviewMoves

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.Radius.m))
            .background(DS.fill)
            .border(1.dp, DS.line, RoundedCornerShape(DS.Radius.m))
            .padding(horizontal = DS.Space.s, vertical = DS.Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Go to Start
        IconButton(
            onClick = { viewModel.goToStart() },
            enabled = viewModel.canStepBackward,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FirstPage,
                contentDescription = loc("Početna pozicija"),
                tint = if (viewModel.canStepBackward) DS.ink else DS.inkMuted,
                modifier = Modifier.size(26.dp)
            )
        }

        // Step Backward
        IconButton(
            onClick = { viewModel.stepBackward() },
            enabled = viewModel.canStepBackward,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = loc("Prethodni"),
                tint = if (viewModel.canStepBackward) DS.ink else DS.inkMuted,
                modifier = Modifier.size(28.dp)
            )
        }

        // Move Indicator Text
        val label = when {
            total == 0 -> loc("Početna pozicija")
            currentIndex == 0 -> loc("Početna pozicija")
            else -> locF("Potez %d od %d", currentIndex, total)
        }
        Text(
            text = label,
            color = if (viewModel.isReviewing) DS.accent else DS.ink,
            // Zbijen pokazivac izmedju cetiri dugmeta -> `caption`. NALAZ: i iOS je
            // ovde van sopstvene skale, `.system(size: 13, weight: .semibold)`
            // (`Chessko/Views/GameView.swift:327`), pa pandan ne postoji.
            style = Type.caption,
            fontWeight = FontWeight.Bold
        )

        // Step Forward
        IconButton(
            onClick = { viewModel.stepForward() },
            enabled = viewModel.canStepForward,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = loc("Sledeći"),
                tint = if (viewModel.canStepForward) DS.ink else DS.inkMuted,
                modifier = Modifier.size(28.dp)
            )
        }

        // Go to End
        IconButton(
            onClick = { viewModel.goToEnd() },
            enabled = viewModel.canStepForward,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LastPage,
                contentDescription = loc("Kraj"),
                tint = if (viewModel.canStepForward) DS.ink else DS.inkMuted,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun ActionsRow(
    viewModel: GameViewModel,
    onReset: () -> Unit,
    onResign: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle Game Mode Button
        Button(
            onClick = {
                viewModel.resetGame()
                viewModel.gameMode = if (viewModel.gameMode == GameMode.VS_COMPUTER) {
                    GameMode.LOCAL_FRIEND
                } else {
                    GameMode.VS_COMPUTER
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = DS.fill,
                contentColor = DS.ink
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            val isVsComputer = viewModel.gameMode == GameMode.VS_COMPUTER
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
                Icon(
                    imageVector = if (isVsComputer) Icons.Default.Computer else Icons.Default.People,
                    // vidi progress.md); za "Prijatelj" nema dodeljenog kljuca u tabeli, a
                    // vidljivi Text odmah desno vec nosi znacenje -- ikona je tu dekorativna.
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = if (isVsComputer) loc("Računar") else loc("Prijatelj"), style = Type.label, fontWeight = FontWeight.SemiBold)
            }
        }

        // Toggle Difficulty Button (enabled only in VS COMPUTER mode)
        if (viewModel.gameMode == GameMode.VS_COMPUTER) {
            Button(
                onClick = {
                    val nextDiff = when (viewModel.difficulty) {
                        GameDifficulty.BEGINNER -> GameDifficulty.EASY
                        GameDifficulty.EASY -> GameDifficulty.MEDIUM
                        GameDifficulty.MEDIUM -> GameDifficulty.HARD
                        GameDifficulty.HARD -> GameDifficulty.STOCKFISH
                        GameDifficulty.STOCKFISH -> GameDifficulty.BEGINNER
                    }
                    viewModel.difficulty = nextDiff
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DS.fill,
                    contentColor = DS.ink
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                val label = when (viewModel.difficulty) {
                    GameDifficulty.BEGINNER -> loc("Početnik")
                    GameDifficulty.EASY -> loc("Lako")
                    GameDifficulty.MEDIUM -> loc("Srednje")
                    GameDifficulty.HARD -> loc("Teško")
                    GameDifficulty.STOCKFISH -> "Stockfish"
                }
                Text(text = label, style = Type.label, fontWeight = FontWeight.SemiBold)
            }
        }

        // Undo Button
        Button(
            onClick = { viewModel.undo() },
            enabled = viewModel.canUndo,
            colors = ButtonDefaults.buttonColors(
                containerColor = DS.fill,
                contentColor = DS.ink,
                disabledContainerColor = DS.fill,
                disabledContentColor = DS.inkMuted
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(0.7f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
                // Dekorativna: `Button` spaja semantiku potomaka, pa bi opis uz
                // vidljiv `Text` naterao citac ekrana da radnju kaze dvaput
                // ("Vrati potez, Vrati, dugme"). Isto vazi za preostala tri
                // dugmeta ovog reda. Kljuc "Vrati potez" ostaje u recniku —
                // `StepGameView.kt` ga koristi na dugmetu BEZ teksta.
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = loc("Vrati"), style = Type.label, fontWeight = FontWeight.SemiBold)
            }
        }

        // Resign Button
        if (viewModel.canResign) {
            Button(
                onClick = { onResign() },
                // 0xFFEF4444/0xFFFCA5A5 nisu bukvalni pogodci u tabeli (to je "soft danger"
                // dugme -- providna pozadina + puna boja teksta), ali obe boje su semanticki
                // DS.danger; vidi izvestaj, dilema.
                colors = ButtonDefaults.buttonColors(
                    containerColor = DS.danger.copy(alpha = 0.15f),
                    contentColor = DS.danger
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(0.7f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
                    // Dekorativna (vidi dugme „Vrati" iznad). Kljuc "Predaj partiju"
                    // ostaje — koristi ga `StepGameView.kt`, gde zastava stoji sama.
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(text = loc("Predaj"), style = Type.label, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Share Button
        Button(
            onClick = { onShare() },
            enabled = viewModel.gameState.moveNotations.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = DS.fill,
                contentColor = DS.ink,
                disabledContainerColor = DS.fill,
                disabledContentColor = DS.inkMuted
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(0.7f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
                // Dekorativna (vidi dugme „Vrati" iznad). Kljuc "Podeli partiju"
                // ostaje — naslov je `Intent.createChooser` dijaloga (`:908`, `:1125`).
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = loc("Podeli"), style = Type.label, fontWeight = FontWeight.SemiBold)
            }
        }

        // Reset Button
        Button(
            onClick = { onReset() },
            colors = ButtonDefaults.buttonColors(
                containerColor = DS.fill,
                contentColor = DS.ink
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(0.7f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
                // Dekorativna (vidi dugme „Vrati" iznad), i to je ovde POPRAVKA a
                // ne samo dosledost: raniji opis je bio "Pokušaj ponovo" — fraza sa
                // ekrana Zadataka — dok se dugme zove „Reset" i zove `onReset()`.
                // Citac ekrana je za jednu kontrolu izgovarao DVA razlicita imena.
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = loc("Reset"), style = Type.label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}