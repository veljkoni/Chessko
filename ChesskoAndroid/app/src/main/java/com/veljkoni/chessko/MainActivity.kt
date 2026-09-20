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
                            shape = RoundedCornerShape(16.dp),
                            color = DS.surface,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = loc("Nova igra"),
                                    color = DS.ink,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Option 1: VS COMPUTER
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DS.fill)
                                        .border(1.dp, DS.line, RoundedCornerShape(12.dp))
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
                                        Text(text = loc("Igraj protiv računara"), color = DS.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = loc("Igraj protiv veštačke inteligencije sa izborom boje"), color = DS.inkMuted, fontSize = 11.sp)
                                    }
                                }

                                // Option 2: LOCAL_FRIEND
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DS.fill)
                                        .border(1.dp, DS.line, RoundedCornerShape(12.dp))
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
                                        Text(text = loc("Igraj sa prijateljem"), color = DS.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = loc("Lokalna igra na istom telefonu sa rotiranjem table"), color = DS.inkMuted, fontSize = 11.sp)
                                    }
                                }

                                // Option 3: CHESS_CLOCK
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DS.fill)
                                        .border(1.dp, DS.line, RoundedCornerShape(12.dp))
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
                                        Text(text = loc("Šahovski sat"), color = DS.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = loc("Premium šahovski sat sa vremenskim kontrolama"), color = DS.inkMuted, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
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
                            shape = RoundedCornerShape(16.dp),
                            color = DS.surface,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = loc("Izaberi boju figura"),
                                    color = DS.ink,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // White Color Card
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DS.fill)
                                            .border(1.dp, DS.line, RoundedCornerShape(12.dp))
                                            .clickable {
                                                showColorPicker = false
                                                gameViewModel.newGame(GameMode.VS_COMPUTER, PieceColor.WHITE)
                                            }
                                            .padding(vertical = 16.dp),
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
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = loc("Beli"), color = DS.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Random Color Card
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DS.fill)
                                            .border(1.dp, DS.line, RoundedCornerShape(12.dp))
                                            .clickable {
                                                showColorPicker = false
                                                val selectedColor = if (Math.random() < 0.5) PieceColor.WHITE else PieceColor.BLACK
                                                gameViewModel.newGame(GameMode.VS_COMPUTER, selectedColor)
                                            }
                                            .padding(vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shuffle,
                                            contentDescription = null,
                                            tint = DS.accent,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = loc("Nasumično"), color = DS.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.4f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                showColorPicker = false
                                                gameViewModel.newGame(GameMode.VS_COMPUTER, PieceColor.BLACK)
                                            }
                                            .padding(vertical = 16.dp),
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
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = loc("Crni"), color = DS.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                        shape = RoundedCornerShape(16.dp)
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
                        shape = RoundedCornerShape(16.dp)
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
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp
                                )

                                Text(
                                    text = getStatusMessage(gameViewModel),
                                    color = DS.ink,
                                    fontSize = 18.sp,
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
                                    shape = RoundedCornerShape(12.dp),
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
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = loc("Pregledaj partiju"),
                                        fontSize = 15.sp,
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
                                    shape = RoundedCornerShape(12.dp),
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
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = loc("Nova igra"),
                                        fontSize = 15.sp,
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
                                .padding(vertical = 4.dp),
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
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left Side: Board & EvalBar
                                        BoxWithConstraints(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .padding(vertical = 4.dp),
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
                                                    modifier = Modifier.size(boardSize)
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
                                                .padding(end = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                    fontSize = 20.sp,
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
                                                    fontSize = 13.sp,
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
                                                .padding(vertical = 12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // 0. Top Header
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 2.dp),
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
                                                    fontSize = 22.sp,
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
                                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
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
                                                    .padding(horizontal = if (gameViewModel.showEvalBar) 8.dp else 0.dp),
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
                                                        modifier = Modifier.size(boardSize)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // 3. Bottom Player Card
                                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
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
                                                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                                    ReviewControlsRow(
                                                        viewModel = gameViewModel,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                            }

                                            // 4. Move history, Status & Actions
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
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
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                }

                                                if (gameViewModel.gameState.moveNotations.isNotEmpty()) {
                                                    Text(
                                                        text = loc("Potezi"),
                                                        color = DS.inkMuted,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                                    )
                                                    MoveHistoryView(
                                                        notations = gameViewModel.gameState.moveNotations,
                                                        selectedMoveIndex = gameViewModel.viewingMoveIndex,
                                                        onSelectMove = { gameViewModel.goToMove(it) },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                }

                                                StatusBanner(message = getStatusMessage(gameViewModel))

                                                Spacer(modifier = Modifier.height(12.dp))

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
            .clip(RoundedCornerShape(12.dp))
            .background(DS.fill)
            .border(1.dp, DS.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
            fontSize = 13.sp,
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = if (isVsComputer) Icons.Default.Computer else Icons.Default.People,
                    // vidi progress.md); za "Prijatelj" nema dodeljenog kljuca u tabeli, a
                    // vidljivi Text odmah desno vec nosi znacenje -- ikona je tu dekorativna.
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = if (isVsComputer) loc("Računar") else loc("Prijatelj"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = loc("Vrati potez"),
                    modifier = Modifier.size(14.dp)
                )
                Text(text = loc("Vrati"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = loc("Predaj partiju"),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(text = loc("Predaj"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = loc("Podeli partiju"),
                    modifier = Modifier.size(14.dp)
                )
                Text(text = loc("Podeli"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = loc("Pokušaj ponovo"),
                    modifier = Modifier.size(14.dp)
                )
                Text(text = loc("Reset"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}