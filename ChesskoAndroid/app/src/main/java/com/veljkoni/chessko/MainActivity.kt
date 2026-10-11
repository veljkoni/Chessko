package com.veljkoni.chessko

import com.veljkoni.chessko.ui.theme.DialogShape
import com.veljkoni.chessko.ui.theme.ButtonShape
import com.veljkoni.chessko.ui.theme.DSRowDivider
import com.veljkoni.chessko.ui.theme.dsCard
import com.veljkoni.chessko.ui.theme.dsPrimaryButtonColors
import com.veljkoni.chessko.ui.theme.dsSecondaryButtonColors
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtMost
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.veljkoni.chessko.logic.StockfishLevel
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

        // spec § 15: jezik „Sistem" se razresava pri svakom (re)kreiranju aktivnosti.
        SettingsManager.getInstance(applicationContext).reapplyLanguage()

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
                // spec § 4: Analiza, Podesavanja i Sat se otvaraju PREKO celog prozora
                // (`ui/FullScreen.kt`), iznad donje trake -- ne kao dijalog ispod nje.
                FullScreenHostLayout {
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
                // Radnja koja ceka potvrdu „Napustiti partiju?" (Nova partija, promena protivnika).
                var pendingNewGame by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                    FullScreenDestination(onDismiss = { showSettings = false }) {
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
                            shape = DialogShape,
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

                                // spec § 5: opcije kao redovi u `surface` dijalogu, razdvojeni `line`;
                                // opisi `inkMuted` na surface (5,86 / 5,43). `fill` nije kartica (§ 3).
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    NewGameOptionRow(
                                        icon = Icons.Default.Computer,
                                        title = loc("Igraj protiv računara"),
                                        description = loc("Igraj protiv veštačke inteligencije sa izborom boje")
                                    ) {
                                        showNewGameMenu = false
                                        showColorPicker = true
                                    }
                                    DSRowDivider()
                                    NewGameOptionRow(
                                        icon = Icons.Default.People,
                                        title = loc("Igraj sa prijateljem"),
                                        description = loc("Lokalna igra na istom telefonu sa rotiranjem table")
                                    ) {
                                        showNewGameMenu = false
                                        gameViewModel.newGame(GameMode.LOCAL_FRIEND, PieceColor.WHITE)
                                    }
                                    DSRowDivider()
                                    // Bez reci „Premium" — nema kupovina (spec § 5, § 16).
                                    NewGameOptionRow(
                                        icon = Icons.Default.Timer,
                                        title = loc("Šahovski sat"),
                                        description = loc("Šahovski sat sa vremenskim kontrolama")
                                    ) {
                                        showNewGameMenu = false
                                        showChessClock = true
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
                            shape = DialogShape,
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
                                            // spec § 5 (54): nema podrazumevanog izbora, pa sve tri
                                            // plocice imaju ISTU ispunu (`fill` + `line`). Krug
                                            // ispod i dalje nosi stranu (fiksno crn, svetla ivica).
                                            .background(DS.fill)
                                            .border(1.dp, DS.line, RoundedCornerShape(DS.Radius.m))
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
                    // Koren sata je crn (polovine, § 0) -- ista podloga ispod sistemskih
                    // traka, i svetle ikone statusne trake i u svetloj temi.
                    FullScreenDestination(
                        onDismiss = { showChessClock = false },
                        background = Color.Black
                    ) {
                        ChessClockView(onDismiss = { showChessClock = false })
                    }
                }

                if (showAnalysis) {
                    FullScreenDestination(onDismiss = { showAnalysis = false }) {
                        AnalysisView(viewModel = gameViewModel, onClose = { showAnalysis = false })
                    }
                }

                if (showResetConfirm) {
                    val dismiss = {
                        showResetConfirm = false
                        pendingNewGame = null
                    }
                    AlertDialog(
                        onDismissRequest = dismiss,
                        // Isti tekst kao iOS (obracanje „ti", spec § 3/§ 16).
                        title = { Text(text = loc("Napustiti partiju?"), color = DS.ink, fontWeight = FontWeight.Bold) },
                        text = { Text(text = loc("Partija je u toku. Sigurno želiš da počneš iznova?"), color = DS.ink) },
                        confirmButton = {
                            TextButton(onClick = {
                                val action = pendingNewGame ?: { gameViewModel.newGame(gameViewModel.gameMode, gameViewModel.playerColor) }
                                dismiss()
                                action()
                            }) {
                                // Destruktivna potvrda: tekst-dugme u `DS.danger` (presuda kontrolora).
                                Text(text = loc("Napusti"), color = DS.danger, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = dismiss) {
                                Text(text = loc("Nastavi"), color = DS.accent)
                            }
                        },
                        containerColor = DS.surface,
                        shape = DialogShape
                    )
                }

                if (showResignConfirm) {
                    AlertDialog(
                        onDismissRequest = { showResignConfirm = false },
                        // spec § 5: „Predati partiju?" · „Nastavi" (accent) · „Predaj" (danger, 6,54 / 6,69).
                        title = { Text(text = loc("Predati partiju?"), color = DS.ink, fontWeight = FontWeight.Bold) },
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
                                Text(text = loc("Nastavi"), color = DS.accent)
                            }
                        },
                        containerColor = DS.surface,
                        shape = DialogShape
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
                            // spec § 3: dijalog = `surface` + `Radius.l` (bilo 20.dp).
                            shape = DialogShape,
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

                                // spec § 5: primarno „Nova igra", sporedno „Analiziraj partiju"
                                // (jedan naziv za radnju — nema vise „Pregledaj partiju"),
                                // „Zatvori" kao tekst-dugme u `accent`.
                                Button(
                                    onClick = {
                                        dismissGameOverOverlay = true
                                        showNewGameMenu = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = ButtonShape,
                                    colors = dsPrimaryButtonColors()
                                ) {
                                    Text(text = loc("Nova igra"), style = Type.body, fontWeight = FontWeight.Bold)
                                }

                                if (canAnalyzeGame(gameViewModel)) {
                                    Button(
                                        onClick = {
                                            dismissGameOverOverlay = true
                                            showAnalysis = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = ButtonShape,
                                        colors = dsSecondaryButtonColors()
                                    ) {
                                        Text(text = loc("Analiziraj partiju"), style = Type.body, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                TextButton(
                                    onClick = { dismissGameOverOverlay = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = loc("Zatvori"), color = DS.accent, style = Type.body, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // spec § 4: donja traka ispod 840 dp, `NavigationRail` od 840 dp.
                val useRail = useNavigationRail()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!useRail) {
                            ChesskoNavigationBar(
                                selected = ChesskoTab.entries[activeTab],
                                onSelect = { activeTab = it.ordinal }
                            )
                        }
                    }
                ) { innerPadding ->
                    // Pozadina ekrana -- ravna DS.ground umesto fiksnog tamnog gradijenta
                    // (brief Task-a 2: "pozadina ekrana -> DS.ground"), da bi svetla tema
                    // stvarno posvetlila i ovaj deo ekrana.
                    val layoutDirection = LocalLayoutDirection.current
                    Row(modifier = Modifier.fillMaxSize().background(DS.ground)) {
                    if (useRail) {
                        // Rail sam nosi insete svoje (pocetne) ivice i vertikale.
                        ChesskoNavigationRail(
                            selected = ChesskoTab.entries[activeTab],
                            onSelect = { activeTab = it.ordinal }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                if (useRail) PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = innerPadding.calculateBottomPadding(),
                                    end = innerPadding.calculateEndPadding(layoutDirection)
                                ) else innerPadding
                            )
                    ) {
                        when (activeTab) {
                            0 -> {
                                val configuration = LocalConfiguration.current
                                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                // spec § 5: ispod 360 dp sirine ikona iznad labele u akcionoj traci.
                                val compactActions = configuration.screenWidthDp < 360

                                val activeTheme = remember(settingsManager.boardTheme) {
                                    BoardTheme.values().find { it.rawValue == settingsManager.boardTheme } ?: BoardTheme.CLASSIC
                                }
                                val activeStyle = remember(settingsManager.pieceStyle) {
                                    PieceStyle.values().find { it.rawValue == settingsManager.pieceStyle } ?: PieceStyle.CLASSIC
                                }

                                val topColor = if (gameViewModel.isFlipped) PieceColor.WHITE else PieceColor.BLACK
                                val bottomColor = if (gameViewModel.isFlipped) PieceColor.BLACK else PieceColor.WHITE

                                // „Nova partija" / promena protivnika: uz potvrdu ako je partija u toku
                                // (isti uslov kao iOS `handleNewGameSelection`).
                                val requestNewGame: (() -> Unit) -> Unit = { action ->
                                    val inProgress = !gameViewModel.isGameOver && gameViewModel.gameState.moveNotations.isNotEmpty()
                                    if (inProgress) {
                                        pendingNewGame = action
                                        showResetConfirm = true
                                    } else {
                                        action()
                                    }
                                }

                                val opponentCard: @Composable () -> Unit = {
                                    GameOpponentCard(
                                        viewModel = gameViewModel,
                                        color = topColor,
                                        boardTheme = activeTheme,
                                        pieceStyle = activeStyle,
                                        onPlayFriend = {
                                            requestNewGame { gameViewModel.newGame(GameMode.LOCAL_FRIEND, PieceColor.WHITE) }
                                        },
                                        onPlayComputer = {
                                            requestNewGame { showColorPicker = true }
                                        }
                                    )
                                }
                                val playerCard: @Composable () -> Unit = {
                                    GamePlayerCard(
                                        viewModel = gameViewModel,
                                        color = bottomColor,
                                        boardTheme = activeTheme,
                                        pieceStyle = activeStyle
                                    )
                                }
                                val actionBar: @Composable (Boolean) -> Unit = { compact ->
                                    GameActionBar(
                                        canUndo = gameViewModel.canUndo,
                                        canResign = gameViewModel.canResign,
                                        onUndo = { gameViewModel.undo() },
                                        onResign = { showResignConfirm = true },
                                        compact = compact,
                                        // „Više": Podeli, Nova partija, Analiza (spec § 5). Analiza se
                                        // otvara kroz `FullScreenDestination` (Task 3) kao i ranije.
                                        moreItems = listOf(
                                            MoreMenuItem(
                                                label = loc("Podeli"),
                                                icon = Icons.Default.Share,
                                                enabled = gameViewModel.gameState.moveNotations.isNotEmpty()
                                            ) {
                                                val pgn = gameViewModel.generatePGN()
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, pgn)
                                                    putExtra(Intent.EXTRA_SUBJECT, loc("Chessko partija"))
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, loc("Podeli partiju")))
                                            },
                                            MoreMenuItem(label = loc("Nova partija"), icon = Icons.Default.Refresh) {
                                                // Isto kao iOS `.restart`: `newGame` (ne `resetGame`), jer samo
                                                // on pokrece racunar kad igrac igra crnima.
                                                requestNewGame { gameViewModel.newGame(gameViewModel.gameMode, gameViewModel.playerColor) }
                                            },
                                            MoreMenuItem(
                                                label = loc("Analiza"),
                                                icon = Icons.Default.Assessment,
                                                enabled = canAnalyzeGame(gameViewModel)
                                            ) { showAnalysis = true }
                                        )
                                    )
                                }
                                val board: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { boardSize ->
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        if (gameViewModel.showEvalBar) {
                                            EvalBar(
                                                evaluation = gameViewModel.evaluationScore,
                                                mateIn = gameViewModel.evaluationMateIn,
                                                isFlipped = gameViewModel.isFlipped,
                                                modifier = Modifier.size(width = GameEvalBarWidth, height = boardSize)
                                            )
                                            Spacer(modifier = Modifier.width(GameEvalBarSpacing))
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
                                            modifier = Modifier.size(boardSize)
                                        )
                                    }
                                }
                                val moveList: @Composable (Modifier) -> Unit = { mod ->
                                    MoveHistoryView(
                                        notations = gameViewModel.gameState.moveNotations,
                                        selectedMoveIndex = gameViewModel.viewingMoveIndex,
                                        onSelectMove = { gameViewModel.goToMove(it) },
                                        modifier = mod
                                    )
                                }
                                val header: @Composable () -> Unit = {
                                    GameHeaderRow(
                                        onNewGame = { showNewGameMenu = true },
                                        onSettings = { showSettings = true }
                                    )
                                }

                                if (isLandscape) {
                                    // Telefon pejzaz (spec § 5): tabla i panel 400 kao par, VRHOVI
                                    // poravnati; lista poteza ide do dna table.
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(DS.Space.s)
                                    ) {
                                        val boardSize = gameBoardSide(maxWidth, maxHeight, isLandscape = true, showEvalBar = gameViewModel.showEvalBar)
                                        val evalWidth = if (gameViewModel.showEvalBar) GameEvalBarWidth + GameEvalBarSpacing else 0.dp
                                        val panelWidth = (maxWidth - boardSize - evalWidth - DS.Space.l)
                                            .coerceAtMost(GameLandscapePanelWidth)
                                            .coerceAtLeast(0.dp)
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.spacedBy(DS.Space.l, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            board(boardSize)
                                            // Lista dobija ostatak visine panela (do dna table); kad
                                            // gornji deo ne staje (nizak telefon, traka pregleda),
                                            // panel skroluje umesto da sece.
                                            val density = LocalDensity.current
                                            var panelFixedPx by remember { mutableIntStateOf(0) }
                                            val panelFixed = with(density) { panelFixedPx.toDp() }
                                            val panelListHeight = (boardSize - panelFixed - DS.Space.s)
                                                .coerceAtLeast(GameMinMoveListHeight)
                                            Column(
                                                modifier = Modifier
                                                    .width(panelWidth)
                                                    .height(boardSize)
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(DS.Space.s)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .onSizeChanged { panelFixedPx = it.height },
                                                    verticalArrangement = Arrangement.spacedBy(DS.Space.s)
                                                ) {
                                                    header()
                                                    opponentCard()
                                                    playerCard()
                                                    actionBar(false)
                                                    if (gameViewModel.totalReviewMoves > 0) {
                                                        ReviewControlsRow(viewModel = gameViewModel, modifier = Modifier.fillMaxWidth())
                                                    }
                                                }
                                                moveList(Modifier.height(panelListHeight))
                                            }
                                        }
                                    }
                                } else {
                                    // Portret: od vrha — zaglavlje, kartica protivnika, tabla, kartica
                                    // igraca, akcije, (pregled), lista poteza koja zauzima OSTATAK visine
                                    // (spec § 5). Nista nije centrirano po visini.
                                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                        val viewportHeight = maxHeight
                                        val density = LocalDensity.current
                                        var fixedHeightPx by remember { mutableIntStateOf(0) }
                                        val evalPadding = if (gameViewModel.showEvalBar) DS.Space.s * 2 else 0.dp
                                        val boardSize = gameBoardSide(maxWidth - evalPadding, maxHeight, isLandscape = false, showEvalBar = gameViewModel.showEvalBar)
                                        val fixedHeight = with(density) { fixedHeightPx.toDp() }
                                        val listHeight = (viewportHeight - fixedHeight - DS.Space.m * 2)
                                            .coerceAtLeast(GameMinMoveListHeight)

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState()),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onSizeChanged { fixedHeightPx = it.height }
                                                    .padding(top = DS.Space.m),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(DS.Space.m)
                                            ) {
                                                Box(modifier = Modifier.padding(horizontal = DS.Space.l)) { header() }
                                                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Space.m)) { opponentCard() }
                                                board(boardSize)
                                                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Space.m)) { playerCard() }
                                                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Space.m)) { actionBar(compactActions) }
                                                if (gameViewModel.totalReviewMoves > 0) {
                                                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Space.m)) {
                                                        ReviewControlsRow(viewModel = gameViewModel, modifier = Modifier.fillMaxWidth())
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(DS.Space.m))
                                            moveList(
                                                Modifier
                                                    .padding(horizontal = DS.Space.m)
                                                    .height(listHeight)
                                            )
                                            Spacer(modifier = Modifier.height(DS.Space.m))
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
                    } // Row (rail + sadrzaj)
                }
                } // FullScreenHostLayout
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
        // spec § 3: `fill` nije kartica — traka pregleda je kartica (`surface` + `line`).
        modifier = modifier
            .fillMaxWidth()
            .dsCard()
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

// ── Igra (spec § 5) — delovi ekrana ───────────────────────────────────────────

/** Sirina i razmak eval trake pored table (zateceno). */
val GameEvalBarWidth = 6.dp
val GameEvalBarSpacing = 6.dp
/** Panel u pejzazu (spec § 5 „Telefon pejzaz", § 13). */
val GameLandscapePanelWidth = 400.dp
/** Najmanja visina liste poteza: tri reda + unutrasnji razmak. */
val GameMinMoveListHeight = MoveRowHeight * 3 + 8.dp

/**
 * Strana table na ekranu Igra. JEDNO mesto za vrednost — Task 11 (spec § 13) ovde
 * menja telo zajednickom funkcijom `boardSide`; raspored cita samo ovaj broj.
 * Logika je zatecena: portret iz sirine, pejzaz iz visine, granica 560.
 */
fun gameBoardSide(availableWidth: Dp, availableHeight: Dp, isLandscape: Boolean, showEvalBar: Boolean): Dp {
    if (isLandscape) return availableHeight.coerceAtMost(DS.maxBoardSide)
    val evalWidth = if (showEvalBar) GameEvalBarWidth + GameEvalBarSpacing else 0.dp
    return (availableWidth - evalWidth).coerceAtMost(DS.maxBoardSide).coerceAtLeast(0.dp)
}

@Composable
private fun NewGameOptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DS.Space.m),
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = DS.accent, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = DS.ink, style = Type.body, fontWeight = FontWeight.SemiBold)
            Text(text = description, color = DS.inkMuted, style = Type.caption)
        }
    }
}

/** Zaglavlje ekrana Igra: nova igra · „Chessko" · podesavanja. */
@Composable
private fun GameHeaderRow(onNewGame: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNewGame, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = Icons.Default.Add, contentDescription = loc("Nova igra"), tint = DS.ink, modifier = Modifier.size(24.dp))
        }
        Text(
            text = loc("Chessko"),
            color = DS.ink,
            // spec § 4 (P3): `Type.heading` semibold na obe platforme.
            style = Type.heading,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = Icons.Default.Menu, contentDescription = loc("Podešavanja"), tint = DS.ink, modifier = Modifier.size(24.dp))
        }
    }
}

private fun isComputerSide(viewModel: GameViewModel, color: PieceColor): Boolean =
    viewModel.gameMode == GameMode.VS_COMPUTER && color != viewModel.playerColor

/** Strana `color` je u sahu (spec § 5: sah se kaze u kartici, ne u posebnom redu). */
private fun isSideInCheck(viewModel: GameViewModel, color: PieceColor): Boolean =
    (viewModel.gameState.status as? GameStatus.Check)?.color == color

private fun isActiveSide(viewModel: GameViewModel, color: PieceColor): Boolean =
    viewModel.gameState.currentTurn == color && !viewModel.isGameOver

/** Priblizan Elo za drugi red kartice protivnika (isti brojevi kao iOS). `null` = ne prikazuje se. */
private fun opponentElo(difficulty: GameDifficulty, level: StockfishLevel): String? = when (difficulty) {
    GameDifficulty.BEGINNER -> "~500"
    GameDifficulty.EASY -> "~900"
    GameDifficulty.MEDIUM -> "~1300"
    GameDifficulty.HARD -> "~1700"
    GameDifficulty.STOCKFISH -> when (level) {
        StockfishLevel.BEGINNER -> "~1320"
        StockfishLevel.CASUAL -> "~1600"
        StockfishLevel.INTERMEDIATE -> "~1900"
        StockfishLevel.ADVANCED -> "~2200"
        StockfishLevel.EXPERT -> "~2600"
        StockfishLevel.MAXIMUM -> null
    }
}

/**
 * Kartica protivnika (gore, spec § 5): ime + drugi red „Srednje · ~1300 · igra
 * crnima" + strelica; tap otvara izbor protivnika i nivoa („Racunar" i „Srednje"
 * iz stare akcione trake prelaze ovde). Promena nivoa vazi odmah; promena
 * protivnika pokrece novu partiju (uz potvrdu ako je partija u toku).
 */
@Composable
private fun GameOpponentCard(
    viewModel: GameViewModel,
    color: PieceColor,
    boardTheme: BoardTheme,
    pieceStyle: PieceStyle,
    onPlayFriend: () -> Unit,
    onPlayComputer: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val computer = isComputerSide(viewModel, color)
    val active = isActiveSide(viewModel, color)
    val status: PlayerCardStatus? = when {
        !active -> null
        computer -> if (viewModel.isThinking) PlayerCardStatus.Thinking else null
        else -> PlayerCardStatus.ToMove(inCheck = isSideInCheck(viewModel, color))
    }
    val subtitle = if (computer) {
        listOfNotNull(
            viewModel.difficulty.label,
            opponentElo(viewModel.difficulty, viewModel.stockfishLevel),
            if (color == PieceColor.BLACK) loc("igra crnima") else loc("igra belima")
        ).joinToString(" · ")
    } else null

    Box {
        PlayerCard(
            color = color,
            name = getPlayerName(viewModel, color),
            boardTheme = boardTheme,
            pieceStyle = pieceStyle,
            subtitle = subtitle,
            status = status,
            isActive = active,
            materialAdvantage = getMaterialAdvantage(viewModel.gameState, color),
            capturedPieces = if (color == PieceColor.WHITE) viewModel.gameState.capturedByWhite else viewModel.gameState.capturedByBlack,
            showsChevron = true,
            onClick = { menuOpen = true }
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = DS.surface,
            shape = DialogShape
        ) {
            val itemColors = MenuDefaults.itemColors(
                textColor = DS.ink,
                leadingIconColor = DS.ink,
                trailingIconColor = DS.accent
            )
            if (viewModel.gameMode == GameMode.VS_COMPUTER) {
                GameDifficulty.values().forEach { diff ->
                    val selected = viewModel.difficulty == diff
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = diff.title,
                                style = Type.body,
                                color = if (selected) DS.accent else DS.ink,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        trailingIcon = if (selected) {
                            { Icon(imageVector = Icons.Default.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            viewModel.difficulty = diff
                            menuOpen = false
                        },
                        colors = itemColors
                    )
                }
                // Stockfish nivo, kad je izabran Stockfish (paritet sa iOS menijem).
                if (viewModel.difficulty == GameDifficulty.STOCKFISH) {
                    DSRowDivider()
                    StockfishLevel.values().forEach { level ->
                        val selected = viewModel.stockfishLevel == level
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = loc(level.label),
                                    style = Type.body,
                                    color = if (selected) DS.accent else DS.ink,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            trailingIcon = if (selected) {
                                { Icon(imageVector = Icons.Default.Check, contentDescription = null) }
                            } else null,
                            onClick = {
                                viewModel.stockfishLevel = level
                                menuOpen = false
                            },
                            colors = itemColors
                        )
                    }
                }
                DSRowDivider()
                DropdownMenuItem(
                    text = { Text(text = loc("Igraj sa prijateljem"), style = Type.body) },
                    leadingIcon = { Icon(imageVector = Icons.Default.People, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onPlayFriend()
                    },
                    colors = itemColors
                )
            } else {
                DropdownMenuItem(
                    text = { Text(text = loc("Igraj protiv računara"), style = Type.body) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Computer, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onPlayComputer()
                    },
                    colors = itemColors
                )
            }
        }
    }
}

/**
 * Kartica igraca (dole, spec § 5): „Na potezu" + tacka `success`, kraj partije u
 * istoj kartici kao `Type.body`, procena desno (kad je eval ukljucen) — jedno
 * stalno mesto; poseban red „Tvoj potez" je uklonjen.
 */
@Composable
private fun GamePlayerCard(
    viewModel: GameViewModel,
    color: PieceColor,
    boardTheme: BoardTheme,
    pieceStyle: PieceStyle
) {
    val computer = isComputerSide(viewModel, color)
    val active = isActiveSide(viewModel, color)
    val status: PlayerCardStatus? = when {
        viewModel.isGameOver -> PlayerCardStatus.Result(getStatusMessage(viewModel))
        !active -> null
        computer -> if (viewModel.isThinking) PlayerCardStatus.Thinking else null
        else -> PlayerCardStatus.ToMove(inCheck = isSideInCheck(viewModel, color))
    }
    PlayerCard(
        color = color,
        name = getPlayerName(viewModel, color),
        boardTheme = boardTheme,
        pieceStyle = pieceStyle,
        status = status,
        isActive = active,
        materialAdvantage = getMaterialAdvantage(viewModel.gameState, color),
        capturedPieces = if (color == PieceColor.WHITE) viewModel.gameState.capturedByWhite else viewModel.gameState.capturedByBlack,
        evaluation = if (viewModel.showEvalBar) evaluationOf(viewModel.evaluationScore, viewModel.evaluationMateIn) else null
    )
}
