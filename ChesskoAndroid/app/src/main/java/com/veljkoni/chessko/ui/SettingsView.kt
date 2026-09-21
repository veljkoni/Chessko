package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veljkoni.chessko.models.ChessPiece
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.models.PieceType
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.logic.ChessAI
import com.veljkoni.chessko.viewmodels.GameViewModel
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import com.veljkoni.chessko.logic.GameDifficulty
import com.veljkoni.chessko.logic.StockfishLevel
import com.veljkoni.chessko.logic.StatsManager
import com.veljkoni.chessko.ui.theme.DS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    gameViewModel: GameViewModel,
    onDismiss: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val stats = remember { StatsManager.getInstance(context) }
    var showResetStatsConfirm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DS.ground)
    ) {
        // Toolbar header
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = loc("Podešavanja"),
                    color = DS.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {},
            actions = {
                TextButton(onClick = onDismiss) {
                    Text(text = loc("Gotovo"), color = DS.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = DS.ground
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Language Selection Section
            SettingsSection(title = loc("Jezik")) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        // Nijedan par u paleti ne daje vidljivu ivicu (`line/fill` 1,067,
                        // `line/surface` 1,285 — vidi `ContrastTest.nonTextPairsOverFillAreDistinguishable`).
                        // Kartica se od `DS.ground` odvaja SAMO bojom podloge, kao iOS-ova
                        // nativna grouped lista (vidi `settingsCardsSeparateFromGround`).
                        .background(DS.surface)
                ) {
                    val languages = listOf(
                        "sr" to "Srpski",
                        "en" to "English",
                        "fr" to "Français",
                        "de" to "Deutsch",
                        "it" to "Italiano",
                        "ru" to "Русский",
                        "zh-Hans" to "中文 (简体)",
                        "hi" to "हिन्दी"
                    )
                    
                    languages.forEachIndexed { index, (code, endonym) ->
                        val isSelected = settings.languageCode == code || (code == "zh-Hans" && settings.languageCode == "zh")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    val newCode = if (code == "zh-Hans") "zh" else code
                                    settings.updateLanguageCode(newCode)
                                    onLanguageChanged(newCode)
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = endonym,
                                color = if (isSelected) DS.accent else DS.ink,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = DS.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (index < languages.size - 1) {
                            HorizontalDivider(color = DS.line)
                        }
                    }
                }
            }

            // 0. Statistics Section
            if (showResetStatsConfirm) {
                AlertDialog(
                    onDismissRequest = { showResetStatsConfirm = false },
                    title = { Text(text = loc("Potvrda"), color = DS.ink, fontWeight = FontWeight.Bold) },
                    text = { Text(text = loc("Da li želite da resetujete sve statistike?"), color = DS.inkMuted) },
                    confirmButton = {
                        TextButton(onClick = {
                            stats.resetStats()
                            showResetStatsConfirm = false
                        }) {
                            Text(text = loc("Potvrdi"), color = DS.danger, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetStatsConfirm = false }) {
                            Text(text = loc("Otkaži"), color = DS.inkMuted)
                        }
                    },
                    containerColor = DS.surface,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // JEDINA kartica na ovom ekranu koja nosi SEMANTICKI obojen tekst
            // (`success`/`danger`/`warning`/`accent` brojeve), pa je jedina koja
            // ide na `DS.surface` umesto na `DS.fill` kao ostale.
            //
            // Nad `DS.fill` je izmereno: `warning` 3,00 i `success` 4,18 u svetloj
            // temi — oba ispod AA (17sp Bold NIJE „large text" po WCAG-u, prag je
            // 4,5). Nad `DS.surface`: `success` 5,04 i `inkMuted` labela 4,83 —
            // oboje preko praga; `warning` ostaje 3,61, sto je TACNO isti par koji
            // iOS `StatBox` ima na istom mestu (`SettingsSheet.swift:399-415`, broj
            // u boji na `systemBackground`) i koji je vec upisan u „Poznata
            // ogranicenja". Popravka same boje bi znacila razlaz sa iOS paletom;
            // popravka podloge ne znaci nista osim da kartica koristi token koji
            // tabela preslikavanja ionako propisuje za ispunu kartice.
            SettingsSection(title = loc("Statistika igranja")) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        // Ivica uklonjena — `line/surface` daje 1,285, nevidljivo (vidi
                        // komentar uz karticu jezika iznad).
                        .background(DS.surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Row 1: Main Counters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = loc("Odigrano"), value = "${stats.gamesPlayed}", color = DS.ink)
                        StatItem(label = loc("Pobede"), value = "${stats.gamesWon}", color = DS.success)
                        StatItem(label = loc("Porazi"), value = "${stats.gamesLost}", color = DS.danger)
                        StatItem(label = loc("Remi"), value = "${stats.gamesDrawn}", color = DS.warning)
                    }

                    HorizontalDivider(color = DS.line)

                    // Row 2: Win Rate & Streaks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = loc("Uspešnost"), value = "${stats.winRate}%", color = DS.accent)
                        StatItem(label = loc("Najbolji niz"), value = "${stats.bestWinStreak} 🔥", color = DS.warning)
                        StatItem(label = loc("Rešeno zadataka"), value = "${stats.puzzlesSolved} 🧩", color = DS.accent)
                        // Rejting igraca BIRA zadatke (`PuzzleRating.practiceWindow`),
                        // pa mora da se vidi — inace se tesina vezbanja menja bez
                        // ijednog vidljivog razloga. („Rejting" u zaglavlju
                        // Zadataka je rejting ZADATKA, ne igraca.) iOS ovo ima
                        // na istom mestu, u istom redu.
                        StatItem(label = loc("Rejting zadataka"), value = "${stats.puzzleRating}", color = DS.accent)
                    }

                    if (stats.gamesPlayed > 0 || stats.puzzlesSolved > 0) {
                        HorizontalDivider(color = DS.line)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showResetStatsConfirm = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = loc("Resetuj statistiku"),
                                    color = DS.danger,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 1. Difficulty Section
            SettingsSection(title = loc("Težina protivnika (AI)")) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        // Ivica uklonjena — vidi komentar uz karticu jezika iznad.
                        .background(DS.surface)
                ) {
                    val difficulties = listOf(
                        GameDifficulty.BEGINNER,
                        GameDifficulty.EASY,
                        GameDifficulty.MEDIUM,
                        GameDifficulty.HARD,
                        GameDifficulty.STOCKFISH
                    )

                    difficulties.forEachIndexed { index, diff ->
                        DifficultyOptionRow(
                            label = diff.title,
                            subtitle = diff.subtitle,
                            isSelected = gameViewModel.difficulty == diff,
                            onClick = { gameViewModel.difficulty = diff }
                        )
                        if (index < difficulties.size - 1) {
                            HorizontalDivider(color = DS.line)
                        }
                    }
                }
            }

            // 2. Board Themes Customization
            SettingsSection(title = loc("Izgled table")) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = loc("Tema table"),
                        color = DS.inkMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // LazyRow for Board Themes
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(BoardTheme.values()) { theme ->
                            val isSelected = settings.boardTheme == theme.rawValue
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { settings.updateBoardTheme(theme.rawValue) }
                                    .width(65.dp)
                            ) {
                                // 2x2 board preview
                                Column(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) DS.accent else DS.line,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Row(modifier = Modifier.weight(1f)) {
                                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(theme.lightColor))
                                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(theme.darkColor))
                                    }
                                    Row(modifier = Modifier.weight(1f)) {
                                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(theme.darkColor))
                                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(theme.lightColor))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = loc(theme.label),
                                    color = if (isSelected) DS.accent else DS.inkMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    SettingsToggle(
                        label = loc("Prikaži koordinate"),
                        icon = Icons.Default.GridOn,
                        checked = settings.showCoordinates,
                        onCheckedChange = { settings.updateShowCoordinates(it) }
                    )

                    SettingsToggle(
                        label = loc("Prikaži poslednji potez"),
                        icon = Icons.Default.History,
                        checked = settings.showLastMoveHighlight,
                        onCheckedChange = { settings.updateShowLastMoveHighlight(it) }
                    )

                    SettingsToggle(
                        label = loc("Prikaži moguća polja"),
                        icon = Icons.Default.RadioButtonChecked,
                        checked = settings.showLegalMoves,
                        onCheckedChange = { settings.updateShowLegalMoves(it) }
                    )

                    SettingsToggle(
                        label = loc("Prevlačenje levo/desno (menja temu)"),
                        icon = Icons.Default.SwapHoriz,
                        checked = settings.swipeToChangeBoardTheme,
                        onCheckedChange = { settings.updateSwipeToChangeBoardTheme(it) }
                    )
                }
            }

            // 3. Piece Styles Customization
            SettingsSection(title = loc("Stil figura")) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = loc("Stil"),
                        color = DS.inkMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Grid layout of 4 columns for Piece Styles
                    val styles = PieceStyle.values()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in styles.indices step 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (j in 0 until 4) {
                                    val index = i + j
                                    if (index < styles.size) {
                                        val style = styles[index]
                                        val isSelected = settings.pieceStyle == style.rawValue
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { settings.updatePieceStyle(style.rawValue) }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(DS.fill)
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) DS.accent else DS.line,
                                                        shape = RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val pieceAsset = getAssetName(
                                                    ChessPiece(PieceType.KNIGHT, PieceColor.WHITE),
                                                    style,
                                                    BoardTheme.CLASSIC
                                                )
                                                AsyncImage(
                                                    model = "file:///android_asset/pieces/$pieceAsset.svg",
                                                    contentDescription = null,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = loc(style.label),
                                                color = if (isSelected) DS.accent else DS.inkMuted,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    SettingsToggle(
                        label = loc("Zadrži pa prevuci gore/dole (menja stil)"),
                        icon = Icons.Default.SwapVert,
                        checked = settings.swipeToChangePieceStyle,
                        onCheckedChange = { settings.updateSwipeToChangePieceStyle(it) }
                    )
                }
            }

            // 4. Gameplay & Rules
            SettingsSection(title = loc("Igra i pravila")) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsToggle(
                        label = loc("Automatska promocija u damu"),
                        icon = Icons.Default.Upgrade,
                        checked = settings.autoPromoteToQueen,
                        onCheckedChange = { settings.updateAutoPromoteToQueen(it) }
                    )

                    SettingsToggle(
                        label = loc("Rotiraj tablu u lokalnoj igri"),
                        icon = Icons.Default.ScreenRotation,
                        checked = settings.rotateBoardInLocalPlay,
                        onCheckedChange = { settings.updateRotateBoardInLocalPlay(it) }
                    )

                    SettingsToggle(
                        label = loc("Evaluaciona traka (Eval Bar)"),
                        icon = Icons.Default.SwapVert,
                        checked = settings.showEvalBar,
                        onCheckedChange = {
                            settings.updateShowEvalBar(it)
                            gameViewModel.updateEvaluation()
                        }
                    )
                }
            }

            // 5. Appearance (Theme)
            SettingsSection(title = loc("Izgled aplikacije")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf(
                        "system" to loc("Sistem"),
                        "light" to loc("Svetla"),
                        "dark" to loc("Tamna")
                    )

                    themes.forEach { (value, label) ->
                        val isSelected = settings.colorScheme == value
                        Button(
                            onClick = { settings.updateColorScheme(value) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) DS.accent else DS.fill,
                                contentColor = if (isSelected) DS.onAccent else DS.ink
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 6. Sound & Haptics
            SettingsSection(title = loc("Efekti")) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsToggle(
                        label = loc("Zvučni efekti"),
                        icon = if (settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        checked = settings.soundEnabled,
                        onCheckedChange = { settings.updateSoundEnabled(it) }
                    )

                    SettingsToggle(
                        label = loc("Vibracija (Taktilni odziv)"),
                        icon = Icons.Default.Vibration,
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { settings.updateHapticsEnabled(it) }
                    )
                }
            }

            // 7. About Application
            SettingsSection(title = loc("O aplikaciji")) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        // Ivica uklonjena — vidi komentar uz karticu jezika iznad.
                        .background(DS.surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = loc("Chessko"), color = DS.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = loc("Verzija 1.0.0"), color = DS.inkMuted, fontSize = 12.sp)

                    HorizontalDivider(color = DS.line)

                    Text(
                        text = loc("Ova aplikacija je otvorenog koda, koristi Stockfish šahovski pokretač pod GPLv3 licencom i preuzima šahovske zadatke iz slobodne Lichess baze."),
                        color = DS.inkMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://stockfishchess.org"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DS.fill,
                                contentColor = DS.ink
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            // Faza 8, Task 4: emoji -> Material ikona. Znak je bio
                            // zalepljen na tekst dugmeta ("🌐 " + loc(...)); ikona je
                            // dekorativna jer je "Stockfish" odmah uz nju CEO vidljiv
                            // sadrzaj dugmeta -- opis bi citaocu ekrana rekao
                            // "spoljni link, Stockfish" umesto samo "Stockfish".
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(text = loc("Stockfish"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lichess.org"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DS.fill,
                                contentColor = DS.ink
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            // Isti obrazac kao dugme "Stockfish" iznad -- dekorativna
                            // ikona, tekst dugmeta nosi ceo vidljiv sadrzaj.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(text = loc("Lichess"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = DS.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
fun SettingsToggle(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DS.fill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            // `weight(1f)` nije kozmetika: bez njega duga oznaka (nemački
            // „Halten, dann hoch/runter ziehen…") gura `Switch` van reda umesto
            // da se prelomi — `SpaceBetween` ne skuplja dete koje nema tezinu.
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DS.accent,
                modifier = Modifier.size(20.dp)
            )
            Text(text = label, color = DS.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            // ISKLJUCENO stanje se NE sme prepustiti M3 podrazumevanim vrednostima.
            // `SwitchTokens` vodi palac i ivicu na `Outline`, a traku na
            // `SurfaceContainerHighest`; `Theme.kt` to mapira na `DS.line` odnosno
            // `DS.fill`, a taj par meri **1,067 (svetla) / 1,071 (tamna)** — palac je
            // bukvalno iste boje kao traka. Red ispod (`.background(DS.fill)`) je jos
            // jednom ista boja, pa se ceo iskljucen prekidac gubi u sopstvenoj podlozi:
            // ne vidi se ni gde da se tapne.
            //
            // Pouka koja je to propustila: i komentar u `Theme.kt` i pregled Task-a 6
            // proverili su da su uloge MAPIRANE — i to tacno — ali nijedno nije
            // proverilo da su mapirane vrednosti MEDJUSOBNO razlicite. Analiziran je
            // token, ne par. Od ove ispravke par `line`/`fill` cuva `ContrastTest`
            // (blok `nonTextPairsOverFillAreDistinguishable`).
            //
            // Izmereno posle popravke (WCAG 2.1, ista formula kao `ContrastTest`):
            //   palac / traka  `inkMuted` / `surface` = 4,83 (svetla) / 5,43 (tamna)
            //   palac / red    `inkMuted` / `fill`    = 4,01 (svetla) / 4,81 (tamna)
            //   ivica / red    isti par               = 4,01 / 4,81
            // Sve preko WCAG praga 3:1 za ne-tekstualni kontrast, u obe teme.
            //
            // UKLJUCENO stanje ostaje na podrazumevanom (`accent` traka, `onAccent`
            // palac = 8,53 / 6,71) — ono je od pocetka bilo tacno.
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = DS.inkMuted,
                uncheckedTrackColor = DS.surface,
                uncheckedBorderColor = DS.inkMuted
            )
        )
    }
}

@Composable
fun DifficultyOptionRow(
    label: String,
    subtitle: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = label,
                color = if (isSelected) DS.accent else DS.ink,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = DS.inkMuted,
                    fontSize = 11.sp
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = DS.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    color: Color = DS.ink
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = DS.inkMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
