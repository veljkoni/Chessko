package com.veljkoni.chessko.ui

import com.veljkoni.chessko.ui.theme.DialogShape
import com.veljkoni.chessko.ui.theme.DSCardGroup
import com.veljkoni.chessko.ui.theme.DSRowDivider
import com.veljkoni.chessko.ui.theme.DSSectionHeader
import com.veljkoni.chessko.ui.theme.dsCard

import com.veljkoni.chessko.logic.locPercent
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import com.veljkoni.chessko.logic.Loc
import kotlin.math.max
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
import com.veljkoni.chessko.ui.theme.Type

/**
 * Jezici u listi, ISTIM redom kao iOS (`AppLanguage.all` u
 * `Chessko/Logic/LocalizationManager.swift`, spec § 15): posle „Sistem" latinicni
 * endonimi abecedno, pa Русский, हिन्दी, 中文. Fiksno, ne sortirano po lokalu.
 * Kod je onaj koji se cuva u `SettingsManager.languageCode` (kineski kao „zh",
 * zatecen kljuc; `Loc.setLanguage` ga prevodi u „zh-Hans").
 */
internal val APP_LANGUAGES = listOf(
    "de" to "Deutsch",
    "en" to "English",
    "fr" to "Français",
    "it" to "Italiano",
    "sr" to "Srpski",
    "ru" to "Русский",
    "hi" to "हिन्दी",
    "zh" to "中文 (简体)"
)

/** Endonim za kod iz `Loc` („zh-Hans" ili „zh" → 中文). */
private fun endonymFor(code: String): String {
    val normalized = if (code == "zh-Hans") "zh" else code
    return APP_LANGUAGES.firstOrNull { it.first == normalized }?.second ?: normalized
}

// spec § 15: grupisane `surface` liste sa `line` razdelnicima, jedan naslov po grupi,
// redosled grupa kao na iOS-u (`Chessko/Views/SettingsSheet.swift`): Statistika,
// Tezina, Izgled table, Stil figura, Igra i pravila, Jezik (jedan red, bez naslova),
// Izgled, Ostalo, O aplikaciji.
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
    var showLanguageList by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Lista jezika je sledece celoekransko odrediste (iOS: `NavigationLink`); sistemsko
    // „nazad" zatvara samo nju (`FullScreenDestination` zatvara gornje odrediste).
    if (showLanguageList) {
        FullScreenDestination(onDismiss = { showLanguageList = false }) {
            LanguageListScreen(
                settings = settings,
                onBack = { showLanguageList = false },
                onLanguageChanged = onLanguageChanged
            )
        }
    }

    if (showResetStatsConfirm) {
        AlertDialog(
            onDismissRequest = { showResetStatsConfirm = false },
            title = { Text(text = loc("Potvrda"), color = DS.ink, fontWeight = FontWeight.Bold) },
            text = { Text(text = loc("Da li želite da resetujete sve statistike?"), color = DS.ink) },
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
                    // spec § 3: sporedna radnja u `accent`, nikad `inkMuted`.
                    Text(text = loc("Otkaži"), color = DS.accent)
                }
            },
            containerColor = DS.surface,
            shape = DialogShape
        )
    }

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
                    // Zaglavlje ekrana -> `heading`, isto kao ostala zaglavlja grane.
                    style = Type.heading,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {},
            actions = {
                TextButton(onClick = onDismiss) {
                    Text(text = loc("Gotovo"), color = DS.accent, fontWeight = FontWeight.SemiBold, style = Type.body)
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
                .padding(DS.Space.l),
            // spec § 3: `Space.xl` izmedju grupa.
            verticalArrangement = Arrangement.spacedBy(DS.Space.xl)
        ) {
            // 1. Statistika
            SettingsSection(title = loc("Statistika igranja")) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dsCard() // spec § 3: kartica = surface + 1 dp line
                        .padding(DS.Space.l),
                    verticalArrangement = Arrangement.spacedBy(DS.Space.m)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = loc("Odigrano"), value = "${stats.gamesPlayed}", color = DS.ink)
                        StatItem(label = loc("Pobede"), value = "${stats.gamesWon}", color = DS.success)
                        StatItem(label = loc("Porazi"), value = "${stats.gamesLost}", color = DS.danger)
                        // spec § 15: „Remi" nije upozorenje — `ink`.
                        StatItem(label = loc("Remi"), value = "${stats.gamesDrawn}", color = DS.ink)
                    }

                    HorizontalDivider(color = DS.line)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = loc("Uspešnost"), value = locPercent(stats.winRate.toDouble(), fractionDigits = 0), color = DS.ink)
                        StatItem(label = loc("Najbolji niz"), value = "${stats.bestWinStreak} 🔥", color = DS.warning)
                        StatItem(label = loc("Rešeno zadataka"), value = "${stats.puzzlesSolved} 🧩", color = DS.ink)
                        // Rejting igraca BIRA zadatke (`PuzzleRating.practiceWindow`),
                        // pa mora da se vidi. iOS ovo ima na istom mestu, u istom redu.
                        StatItem(label = loc("Rejting zadataka"), value = "${stats.puzzleRating}", color = DS.ink)
                    }

                    if (stats.gamesPlayed > 0 || stats.puzzlesSolved > 0) {
                        HorizontalDivider(color = DS.line)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showResetStatsConfirm = true },
                                contentPadding = PaddingValues(horizontal = DS.Space.s, vertical = 2.dp)
                            ) {
                                Text(
                                    text = loc("Resetuj statistiku"),
                                    color = DS.danger,
                                    style = Type.caption,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 2. Tezina
            SettingsSection(title = loc("Težina protivnika (AI)")) {
                DSCardGroup {
                    val difficulties = GameDifficulty.values().toList()
                    difficulties.forEachIndexed { index, diff ->
                        if (index > 0) DSRowDivider()
                        DifficultyOptionRow(
                            label = diff.title,
                            subtitle = diff.subtitle,
                            isSelected = gameViewModel.difficulty == diff,
                            onClick = { gameViewModel.difficulty = diff }
                        )
                    }
                }
            }

            // 3. Izgled table — spec § 15: jedan naslov (bez unutrasnjeg „Tema table"),
            // mreza tema u istoj `surface` grupi kao prekidaci.
            SettingsSection(title = loc("Izgled table")) {
                DSCardGroup {
                    // Podaci iz `BoardTheme.values()` — brisanje/dodavanje teme je izmena enum-a.
                    AdaptiveTileGrid(
                        items = BoardTheme.values().toList(),
                        modifier = Modifier.padding(DS.Space.l)
                    ) { theme ->
                        val isSelected = settings.boardTheme == theme.rawValue
                        SettingsTile(
                            label = loc(theme.label),
                            isSelected = isSelected,
                            onClick = { settings.updateBoardTheme(theme.rawValue) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(DS.Radius.s))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) DS.accent else DS.line,
                                        shape = RoundedCornerShape(DS.Radius.s)
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
                        }
                    }
                    DSRowDivider()
                    SettingsToggle(
                        label = loc("Prikaži koordinate"),
                        icon = Icons.Default.GridOn,
                        checked = settings.showCoordinates,
                        onCheckedChange = { settings.updateShowCoordinates(it) }
                    )
                    DSRowDivider()
                    SettingsToggle(
                        label = loc("Prikaži poslednji potez"),
                        icon = Icons.Default.History,
                        checked = settings.showLastMoveHighlight,
                        onCheckedChange = { settings.updateShowLastMoveHighlight(it) }
                    )
                    DSRowDivider()
                    SettingsToggle(
                        label = loc("Prikaži moguća polja"),
                        icon = Icons.Default.RadioButtonChecked,
                        checked = settings.showLegalMoves,
                        onCheckedChange = { settings.updateShowLegalMoves(it) }
                    )
                    DSRowDivider()
                    SettingsToggle(
                        label = loc("Prevlačenje levo/desno (menja temu)"),
                        icon = Icons.Default.SwapHoriz,
                        checked = settings.swipeToChangeBoardTheme,
                        onCheckedChange = { settings.updateSwipeToChangeBoardTheme(it) }
                    )
                }
            }

            // 4. Stil figura — spec § 15: bez unutrasnjeg „Stil", ista adaptivna mreza.
            SettingsSection(title = loc("Stil figura")) {
                DSCardGroup {
                    // Podaci iz `PieceStyle.values()` — brisanje/dodavanje stila je izmena enum-a.
                    AdaptiveTileGrid(
                        items = PieceStyle.values().toList(),
                        modifier = Modifier.padding(DS.Space.l)
                    ) { style ->
                        val isSelected = settings.pieceStyle == style.rawValue
                        SettingsTile(
                            label = loc(style.label),
                            isSelected = isSelected,
                            onClick = { settings.updatePieceStyle(style.rawValue) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(DS.Radius.s))
                                    .background(DS.fill)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) DS.accent else DS.line,
                                        shape = RoundedCornerShape(DS.Radius.s)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val pieceAsset = getAssetName(
                                    ChessPiece(PieceType.KNIGHT, PieceColor.WHITE),
                                    style
                                )
                                AsyncImage(
                                    model = "file:///android_asset/pieces/$pieceAsset.svg",
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                    DSRowDivider()
                    SettingsToggle(
                        label = loc("Zadrži pa prevuci gore/dole (menja stil)"),
                        icon = Icons.Default.SwapVert,
                        checked = settings.swipeToChangePieceStyle,
                        onCheckedChange = { settings.updateSwipeToChangePieceStyle(it) }
                    )
                }
            }

            // 5. Igra i pravila
            SettingsSection(title = loc("Igra i pravila")) {
                DSCardGroup {
                    SettingsToggle(
                        label = loc("Automatska promocija u damu"),
                        icon = Icons.Default.Upgrade,
                        checked = settings.autoPromoteToQueen,
                        onCheckedChange = { settings.updateAutoPromoteToQueen(it) }
                    )
                    DSRowDivider()
                    SettingsToggle(
                        label = loc("Rotiraj tablu u lokalnoj igri"),
                        icon = Icons.Default.ScreenRotation,
                        checked = settings.rotateBoardInLocalPlay,
                        onCheckedChange = { settings.updateRotateBoardInLocalPlay(it) }
                    )
                    DSRowDivider()
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

            // 6. Jezik — spec § 15: JEDAN red „Jezik · Srpski ›" sa globusom, bez naslova
            // grupe (naslov „Jezik" iznad reda „Jezik" bio bi dupli).
            DSCardGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageList = true }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = DS.Space.l, vertical = DS.Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = DS.inkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = loc("Jezik"),
                        color = DS.ink,
                        style = Type.body,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (settings.languageCode == Loc.SYSTEM) loc("Sistem") else endonymFor(settings.languageCode),
                        color = DS.inkMuted,
                        style = Type.body,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = DS.inkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 7. Izgled — iOS ima redove sa kvacicom (bilo: tri `fill` dugmeta na ground).
            SettingsSection(title = loc("Izgled")) {
                DSCardGroup {
                    val themes = listOf(
                        Triple("system", loc("Sistem"), Icons.Default.BrightnessMedium),
                        Triple("light", loc("Svetla"), Icons.Default.LightMode),
                        Triple("dark", loc("Tamna"), Icons.Default.DarkMode)
                    )
                    themes.forEachIndexed { index, (value, label, icon) ->
                        if (index > 0) DSRowDivider()
                        SelectableRow(
                            label = label,
                            icon = icon,
                            isSelected = settings.colorScheme == value,
                            onClick = { settings.updateColorScheme(value) }
                        )
                    }
                }
            }

            // 8. Ostalo (zvuk i vibracija)
            SettingsSection(title = loc("Ostalo")) {
                DSCardGroup {
                    SettingsToggle(
                        label = loc("Zvučni efekti"),
                        icon = if (settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        checked = settings.soundEnabled,
                        onCheckedChange = { settings.updateSoundEnabled(it) }
                    )
                    DSRowDivider()
                    SettingsToggle(
                        label = loc("Vibracija (Taktilni odziv)"),
                        icon = Icons.Default.Vibration,
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { settings.updateHapticsEnabled(it) }
                    )
                }
            }

            // 9. O aplikaciji
            SettingsSection(title = loc("O aplikaciji")) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dsCard() // spec § 3: kartica = surface + 1 dp line
                        .padding(DS.Space.l),
                    verticalArrangement = Arrangement.spacedBy(DS.Space.s)
                ) {
                    Text(text = loc("Chessko"), color = DS.ink, style = Type.heading, fontWeight = FontWeight.Bold)
                    Text(text = loc("Verzija 1.0.0"), color = DS.inkMuted, style = Type.body)

                    HorizontalDivider(color = DS.line)

                    Text(
                        text = loc("Ova aplikacija je otvorenog koda, koristi Stockfish šahovski pokretač pod GPLv3 licencom i preuzima šahovske zadatke iz slobodne Lichess baze."),
                        color = DS.inkMuted,
                        style = Type.caption
                    )

                    Spacer(modifier = Modifier.height(DS.Space.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
                    ) {
                        LinkButton(label = loc("Stockfish"), url = "https://stockfishchess.org", modifier = Modifier.weight(1f))
                        LinkButton(label = loc("Lichess"), url = "https://lichess.org", modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Dugme-link u kartici „O aplikaciji": `fill` + `ink` (sporedno dugme sa ispunom, spec § 3). */
@Composable
private fun LinkButton(label: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Button(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        colors = ButtonDefaults.buttonColors(containerColor = DS.fill, contentColor = DS.ink),
        shape = RoundedCornerShape(DS.Radius.s),
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        // Dekorativna ikona — tekst dugmeta nosi ceo vidljiv sadrzaj (Faza 8).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = DS.ink,
                modifier = Modifier.size(14.dp)
            )
            Text(text = label, color = DS.ink, style = Type.caption, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Lista jezika (spec § 15): Sistem, pa `APP_LANGUAGES` istim redom kao iOS. Ispod
 * „Sistem" sitno (`inkMuted`) jezik u koji se „Sistem" na ovom uredjaju razresava
 * (`Loc.resolveSystemLanguage`, isto pravilo kao iOS `systemFallback`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageListScreen(
    settings: SettingsManager,
    onBack: () -> Unit,
    onLanguageChanged: (String) -> Unit
) {
    fun select(code: String) {
        settings.updateLanguageCode(code)
        onLanguageChanged(code)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DS.ground)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(text = loc("Jezik"), color = DS.ink, style = Type.heading, fontWeight = FontWeight.Bold)
            },
            navigationIcon = {
                TextButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = DS.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(DS.Space.xs))
                    Text(text = loc("Podešavanja"), color = DS.accent, style = Type.body, maxLines = 1)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DS.ground)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(DS.Space.l)
        ) {
            DSCardGroup {
                LanguageRow(
                    name = loc("Sistem"),
                    detail = endonymFor(Loc.resolveSystemLanguage(Loc.systemLanguageTags())),
                    isSelected = settings.languageCode == Loc.SYSTEM,
                    onClick = { select(Loc.SYSTEM) }
                )
                APP_LANGUAGES.forEach { (code, endonym) ->
                    DSRowDivider()
                    LanguageRow(
                        name = endonym,
                        detail = null,
                        // „zh-Hans" je mogao biti sacuvan i u tom obliku (Loc ga prima).
                        isSelected = settings.languageCode == code ||
                            (code == "zh" && settings.languageCode == "zh-Hans"),
                        onClick = { select(code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(name: String, detail: String?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = DS.Space.l, vertical = DS.Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = if (isSelected) DS.accent else DS.ink,
                style = Type.body,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (detail != null) {
                Text(text = detail, color = DS.inkMuted, style = Type.caption)
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

/** Red sa ikonom i kvacicom za izabranu vrednost (Izgled: Sistem/Svetla/Tamna). */
@Composable
private fun SelectableRow(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = DS.Space.l, vertical = DS.Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = DS.inkMuted, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            color = DS.ink,
            style = Type.body,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = DS.accent, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Adaptivna mreza (spec § 15): kolona najmanje [minCellWidth] (88), broj kolona iz
 * sirine. Ekvivalent `LazyVerticalGrid(GridCells.Adaptive(88.dp))` — ali NE lenja:
 * mreza stoji u `verticalScroll` koloni, a lenja mreza u istosmernom skroleru dobija
 * beskonacnu visinu i pada. Za 8 (posle Task-a 12: 6/5) plocica lenjost ne donosi nista.
 */
@Composable
private fun <T> AdaptiveTileGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    minCellWidth: Dp = 88.dp,
    spacing: Dp = DS.Space.s,
    tile: @Composable (T) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = max(1, ((maxWidth + spacing) / (minCellWidth + spacing)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(DS.Space.m)) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            tile(item)
                        }
                    }
                    repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Plocica teme/stila: uzorak + labela; ceo stub je meta dodira. */
@Composable
private fun SettingsTile(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.Radius.s))
            .clickable(onClick = onClick)
            .semantics { selected = isSelected }
    ) {
        preview()
        Spacer(modifier = Modifier.height(6.dp))
        SettingsTileLabel(text = label, isSelected = isSelected)
    }
}

/**
 * Labela ispod plocice (spec § 15): `Type.caption`, najvise 2 reda, rezervisana visina
 * dva reda (`caption` lineHeight × 2) da se mreza ne pomera. Bez loma usred reci:
 * `Hyphens.None` + `LineBreak.Heading`; ako NAJDUZA rec na `caption` ne staje u
 * sirinu kolone („Классическая", „Игровая"), cela labela prelazi na `Type.label`.
 */
@Composable
private fun SettingsTileLabel(text: String, isSelected: Boolean) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val weight = if (isSelected) FontWeight.Bold else FontWeight.Medium
    val captionStyle = Type.caption.copy(
        fontWeight = weight,
        textAlign = TextAlign.Center,
        hyphens = Hyphens.None,
        lineBreak = LineBreak.Heading
    )
    val reservedHeight = with(density) { (Type.caption.lineHeight * 2).toDp() }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth
        val wordsFit = remember(text, maxPx, weight) {
            !constraints.hasBoundedWidth ||
                text.split(' ', ' ').filter { it.isNotEmpty() }.all { word ->
                    measurer.measure(word, captionStyle, maxLines = 1, softWrap = false).size.width <= maxPx
                }
        }
        val style = if (wordsFit) captionStyle else captionStyle.copy(
            fontSize = Type.label.fontSize,
            lineHeight = Type.label.lineHeight
        )
        Text(
            text = text,
            color = if (isSelected) DS.accent else DS.ink,
            style = style,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = reservedHeight)
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DS.Space.s)
    ) {
        // spec § 3: naslov sekcije je `Type.heading` bold u `DS.ink`, ne `accent`
        // (accent je samo za ono sto se tapa ili je izabrano).
        DSSectionHeader(text = title)
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
            // spec § 3: `fill` nije kartica — red sedi u `DSCardGroup` (`surface` + `line`).
            .padding(horizontal = DS.Space.m, vertical = 6.dp),
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
                // spec § 15: ikone u redovima su `inkMuted` (5,86 / 5,43), ne `accent`.
                tint = DS.inkMuted,
                modifier = Modifier.size(20.dp)
            )
            Text(text = label, color = DS.ink, style = Type.body, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            // ISKLJUCENO stanje se NE sme prepustiti M3 podrazumevanim vrednostima:
            // `SwitchTokens` vode palac i ivicu na `Outline` (= `DS.line`), a traku na
            // `SurfaceContainerHighest` (= `DS.fill`) — `line/fill` je 1,067 / 1,071,
            // nevidljiv prekidac (Faza 6d-1; par cuva `ContrastTest`).
            //
            // Od dizajnerskog pregleda (spec § 3) red vise NIJE `DS.fill` nego sedi u
            // `DSCardGroup` na `DS.surface`. Iskljucena traka je takodje `DS.surface`,
            // dakle traka == red i sama traka se ne razlikuje od podloge. Zato ivica
            // ostaje `inkMuted`: ona (i palac iste boje) crta oblik prekidaca nad redom.
            // Izmereno sa novim `inkMuted` (`#5E6573`, WCAG 2.1 kao `ContrastTest`):
            //   palac / ivica nad trakom i redom  `inkMuted` / `surface` = 5,86 (svetla) / 5,43 (tamna)
            // Iznad praga 3:1 za ne-tekstualni kontrast u obe teme.
            //
            // UKLJUCENO stanje ostaje na podrazumevanom (`accent` traka, `onAccent`
            // palac = 8,53 / 6,71).
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
            .padding(horizontal = DS.Space.l, vertical = DS.Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = label,
                color = if (isSelected) DS.accent else DS.ink,
                // iOS: `.subheadline` (`Chessko/Views/SettingsSheet.swift:151`).
                style = Type.body,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = DS.inkMuted,
                    // Uloga 1 uz `Type.label`: prigusen podnaslov ispod naslova reda.
                    // iOS crta bas `caption2` (`Chessko/Views/SettingsSheet.swift:156`).
                    style = Type.label
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
            // iOS: `.headline` (`Chessko/Views/SettingsSheet.swift:407`).
            style = Type.heading,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = DS.inkMuted,
            // Uloga 2 uz `Type.label`: oznaka ispod brojcane vrednosti. iOS crta
            // bas `caption2` (`Chessko/Views/SettingsSheet.swift:411`).
            style = Type.label,
            fontWeight = FontWeight.Medium
        )
    }
}
