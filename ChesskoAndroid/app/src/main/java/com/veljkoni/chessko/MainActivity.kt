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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.shape.CircleShape
import com.veljkoni.chessko.ui.theme.ChesskoTheme
import com.veljkoni.chessko.viewmodels.GameMode
import com.veljkoni.chessko.viewmodels.GameViewModel
import com.veljkoni.chessko.viewmodels.PuzzleViewModel
import com.veljkoni.chessko.viewmodels.LearnViewModel

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
                    val learnViewModel = remember { LearnViewModel() }
                
                var showSettings by remember { mutableStateOf(false) }
                var showNewGameMenu by remember { mutableStateOf(false) }
                var showColorPicker by remember { mutableStateOf(false) }
                var showChessClock by remember { mutableStateOf(false) }
                var showResetConfirm by remember { mutableStateOf(false) }
                var showResignConfirm by remember { mutableStateOf(false) }
                var dismissGameOverOverlay by remember { mutableStateOf(false) }

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
                            color = Color(0xFF1E293B),
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
                                    color = Color.White,
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
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
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
                                        tint = Color(0xFF00D2FF),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Column {
                                        Text(text = loc("Igraj protiv računara"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = loc("Igraj protiv veštačke inteligencije sa izborom boje"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }
                                }

                                // Option 2: LOCAL_FRIEND
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
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
                                        tint = Color(0xFF00D2FF),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Column {
                                        Text(text = loc("Igraj sa prijateljem"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = loc("Lokalna igra na istom telefonu sa rotiranjem table"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }
                                }

                                // Option 3: CHESS_CLOCK
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
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
                                        tint = Color(0xFF00D2FF),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Column {
                                        Text(text = loc("Šahovski sat"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = loc("Premium šahovski sat sa vremenskim kontrolama"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = { showNewGameMenu = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = loc("Zatvori"), color = Color(0xFF00D2FF), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (showColorPicker) {
                    Dialog(onDismissRequest = { showColorPicker = false }) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B),
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
                                    color = Color.White,
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
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                showColorPicker = false
                                                gameViewModel.newGame(GameMode.VS_COMPUTER, PieceColor.WHITE)
                                            }
                                            .padding(vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Circle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = loc("Beli"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Random Color Card
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
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
                                            tint = Color(0xFF00D2FF),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = loc("Nasumično"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Black Color Card
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
                                        Text(text = loc("Crni"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = { showColorPicker = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = loc("Zatvori"), color = Color(0xFF00D2FF), fontWeight = FontWeight.Bold)
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

                if (showResetConfirm) {
                    AlertDialog(
                        onDismissRequest = { showResetConfirm = false },
                        title = { Text(text = loc("Potvrda"), color = Color.White, fontWeight = FontWeight.Bold) },
                        text = { Text(text = loc("Da li ste sigurni? Trenutna partija će biti izgubljena."), color = Color.White.copy(alpha = 0.8f)) },
                        confirmButton = {
                            TextButton(onClick = {
                                gameViewModel.resetGame()
                                showResetConfirm = false
                            }) {
                                Text(text = loc("Potvrdi"), color = Color(0xFF00D2FF), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetConfirm = false }) {
                                Text(text = loc("Otkaži"), color = Color.White.copy(alpha = 0.6f))
                            }
                        },
                        containerColor = Color(0xFF1E293B),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                if (showResignConfirm) {
                    AlertDialog(
                        onDismissRequest = { showResignConfirm = false },
                        title = { Text(text = loc("Predaja partije"), color = Color.White, fontWeight = FontWeight.Bold) },
                        text = { Text(text = loc("Da li ste sigurni da želite da predate partiju?"), color = Color.White.copy(alpha = 0.8f)) },
                        confirmButton = {
                            TextButton(onClick = {
                                gameViewModel.resign()
                                showResignConfirm = false
                            }) {
                                Text(text = loc("Predaj"), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResignConfirm = false }) {
                                Text(text = loc("Nastavi igru"), color = Color.White.copy(alpha = 0.6f))
                            }
                        },
                        containerColor = Color(0xFF1E293B),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                if (gameViewModel.isGameOver && !dismissGameOverOverlay) {
                    Dialog(
                        onDismissRequest = { dismissGameOverOverlay = true }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1E293B),
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
                                val emoji = when (val s = gameViewModel.gameState.status) {
                                    is GameStatus.Checkmate -> {
                                        if (gameViewModel.gameMode == GameMode.LOCAL_FRIEND) {
                                            "🏆"
                                        } else if (s.color == gameViewModel.playerColor) {
                                            "😔"
                                        } else {
                                            "🏆"
                                        }
                                    }
                                    is GameStatus.Resigned -> {
                                        if (gameViewModel.gameMode == GameMode.LOCAL_FRIEND) {
                                            "🏳️"
                                        } else if (s.color == gameViewModel.playerColor) {
                                            "🏳️"
                                        } else {
                                            "🏆"
                                        }
                                    }
                                    is GameStatus.Draw -> "🤝"
                                    else -> "♟️"
                                }

                                Text(
                                    text = emoji,
                                    fontSize = 48.sp
                                )

                                Text(
                                    text = loc("Kraj partije"),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp
                                )

                                Text(
                                    text = getStatusMessage(gameViewModel),
                                    color = Color.White,
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF334155),
                                        contentColor = Color.White
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
                                        containerColor = Color(0xFF00D2FF),
                                        contentColor = Color(0xFF0B132B)
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
                                .background(Color(0xFF0B0F19))
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
                    // Deep Indigo Gradient Background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0F172A),
                                        Color(0xFF1E1B4B),
                                        Color(0xFF0F172A)
                                    )
                                )
                            )
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
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                Text(
                                                    text = loc("Chessko"),
                                                    color = Color.White,
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
                                                        tint = Color.White,
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

                                            // Move History
                                            if (gameViewModel.gameState.moveNotations.isNotEmpty()) {
                                                Text(
                                                    text = loc("Potezi"),
                                                    color = Color.White.copy(alpha = 0.6f),
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
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                Text(
                                                    text = loc("Chessko"),
                                                    color = Color.White,
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
                                                        tint = Color.White,
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
                                                if (gameViewModel.gameState.moveNotations.isNotEmpty()) {
                                                    Text(
                                                        text = loc("Potezi"),
                                                        color = Color.White.copy(alpha = 0.6f),
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
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
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
                tint = if (viewModel.canStepBackward) Color.White else Color.White.copy(alpha = 0.2f),
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
                tint = if (viewModel.canStepBackward) Color.White else Color.White.copy(alpha = 0.2f),
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
            color = if (viewModel.isReviewing) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.95f),
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
                tint = if (viewModel.canStepForward) Color.White else Color.White.copy(alpha = 0.2f),
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
                tint = if (viewModel.canStepForward) Color.White else Color.White.copy(alpha = 0.2f),
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
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            val label = if (viewModel.gameMode == GameMode.VS_COMPUTER) "🤖 " + loc("Računar") else "👥 " + loc("Prijatelj")
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White
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
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.02f),
                disabledContentColor = Color.White.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(0.7f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(text = "↩️ " + loc("Vrati"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        // Resign Button
        if (viewModel.canResign) {
            Button(
                onClick = { onResign() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                    contentColor = Color(0xFFFCA5A5)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(0.7f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(text = "🏳️ " + loc("Predaj"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Share Button
        Button(
            onClick = { onShare() },
            enabled = viewModel.gameState.moveNotations.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.02f),
                disabledContentColor = Color.White.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(0.7f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(text = "📤 " + loc("Podeli"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        // Reset Button
        Button(
            onClick = { onReset() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(0.7f),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(text = "🔄 " + loc("Reset"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}