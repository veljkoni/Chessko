package com.veljkoni.chessko.ui

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veljkoni.chessko.logic.HapticManager
import com.veljkoni.chessko.logic.SoundManager
import com.veljkoni.chessko.ui.theme.DarkColors
import com.veljkoni.chessko.ui.theme.DS
import kotlinx.coroutines.delay

// MARK: - Fiksne boje kontrolne trake
//
// Sat je u `CLAUDE.md` naveden kao izuzet od tokena U CELOSTI, i traka izmedju dve
// njegove polovine je deo tog izuzetka, ne hrom: ona dodiruje crnu polovinu odozgo i
// belu odozdo, pa je njena podloga vezana za STRANU U IGRI, ne za sistemsku temu.
// `DS.surface` je tu u svetloj temi bio bukvalno ista boja kao bela polovina (1,000:1).
//
// Sadrzaj trake zato cita `DarkColors` — fiksnu paletu, ne `DS.*`. To NIJE mesanje:
// vrednost je konstanta kao i podloga, pa je par merljiv i isti u obe teme. Dijalozi
// sata (`PresetChooserDialog`, `InfoDialog`) su druga prica — oni lebde nad scrim-om
// i legitimno prate temu, pa i dalje koriste `DS.*`.
//
// Cuva ih `ContrastTest.clockControlBarIsReadableOverFixedHalves`.

/** Podloga trake. Zatecena vrednost, vracena posle regresije opisane u `ControlBar`. */
val ClockBarBackground = Color(0xFF1E293B)

/**
 * Udubljenje ispod „zatvori" i „resetuj" dugmadi. Cisto dekorativno — nosilac radnje je
 * ikonica u sredini, ne krug (od Faze 8, Task 1 vektorska; do tada emoji glif, ali
 * zakljucak vazi identicno za oboje — krug samo omedjuje dodirnu povrsinu).
 * Zatecenih `White@6%` daje 1,198 prema traci; `12%` daje 1,458. Nijedna vrednost u
 * ovom registru ne stize do 3:1 a da krug ne pocne da vice glasnije od ikonice, pa je
 * uzeta veca od dve izmerene.
 */
val ClockBarWell = Color.White.copy(alpha = 0.12f)

/** Akcent NA fiksno tamnoj traci: 5,629 prema podlozi (svetla varijanta bi dala 1,716). */
val ClockBarAccent = DarkColors.accent

data class TimeControlPreset(
    val name: String,
    val category: String,
    val baseMinutes: Int,
    val incrementSeconds: Int,
    val hasSecondPhase: Boolean = false,
    val secondPhaseTriggerMove: Int = 0,
    val secondPhaseAddedMinutes: Int = 0,
    val isOfficial: Boolean = false,
    val subtitle: String = ""
)

@Composable
fun ChessClockView(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val hapticManager = remember { HapticManager(context) }

    // Presets
    val presets = remember {
        listOf(
            TimeControlPreset("3 + 2", loc("Blic"), 3, 2, isOfficial = true, subtitle = loc("Zvanični svetski blic")),
            TimeControlPreset("5 + 0", loc("Blic"), 5, 0, subtitle = loc("Stara škola blica")),
            TimeControlPreset("5 + 3", loc("Blic"), 5, 3, subtitle = loc("Turnirski blic")),
            
            TimeControlPreset("15 + 10", loc("Ubrzani šah"), 15, 10, isOfficial = true, subtitle = loc("Zvanični svetski rapid")),
            TimeControlPreset("10 + 5", loc("Ubrzani šah"), 10, 5, subtitle = loc("Popularni online rapid")),
            TimeControlPreset("25 + 10", loc("Ubrzani šah"), 25, 10, subtitle = loc("Lokalni turniri")),
            
            TimeControlPreset("90 + 30 + 30", loc("Klasični šah"), 90, 30, hasSecondPhase = true, secondPhaseTriggerMove = 40, secondPhaseAddedMinutes = 30, isOfficial = true, subtitle = loc("Turniri kandidata / FIDE")),
            TimeControlPreset("90 + 30", loc("Klasični šah"), 90, 30, subtitle = loc("Domaća liga"))
        )
    }

    var selectedPreset by remember { mutableStateOf(presets[0]) }

    // Timer states
    var p1Time by remember { mutableStateOf(180.0) } // Player 1 (White, bottom)
    var p2Time by remember { mutableStateOf(180.0) } // Player 2 (Black, top)
    var p1Moves by remember { mutableStateOf(0) }
    var p2Moves by remember { mutableStateOf(0) }

    // Game state
    var activePlayer by remember { mutableStateOf<Int?>(null) } // null = paused or not started
    var isPaused by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }

    var showPresetDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Reset clock action
    fun resetClock() {
        p1Time = selectedPreset.baseMinutes * 60.0
        p2Time = selectedPreset.baseMinutes * 60.0
        p1Moves = 0
        p2Moves = 0
        activePlayer = null
        isPaused = false
        hasStarted = false
    }

    // Effect for reset clock on preset change
    LaunchedEffect(selectedPreset) {
        resetClock()
    }

    // Timer ticking
    LaunchedEffect(activePlayer, isPaused, hasStarted) {
        if (hasStarted && !isPaused && activePlayer != null) {
            while (true) {
                delay(100)
                if (activePlayer == 1) {
                    p1Time = (p1Time - 0.1).coerceAtLeast(0.0)
                    if (p1Time <= 0.0) {
                        activePlayer = null
                        hapticManager.warning()
                        break
                    }
                } else if (activePlayer == 2) {
                    p2Time = (p2Time - 0.1).coerceAtLeast(0.0)
                    if (p2Time <= 0.0) {
                        activePlayer = null
                        hapticManager.warning()
                        break
                    }
                }
            }
        }
    }

    // Clean up sound pool
    DisposableEffect(Unit) {
        onDispose {
            soundManager.release()
        }
    }

    // Tap Handling
    val handleTap: (Int) -> Unit = { playerNumber ->
        if (p1Time > 0 && p2Time > 0) {
            if (!hasStarted) {
                hasStarted = true
                isPaused = false
                activePlayer = if (playerNumber == 1) 2 else 1
                soundManager.playMove()
                hapticManager.mediumImpact()
            } else if (!isPaused) {
                if (playerNumber == 1 && activePlayer == 1) {
                    p1Moves++
                    p1Time += selectedPreset.incrementSeconds
                    if (selectedPreset.hasSecondPhase && p1Moves == selectedPreset.secondPhaseTriggerMove) {
                        p1Time += selectedPreset.secondPhaseAddedMinutes * 60.0
                    }
                    activePlayer = 2
                    soundManager.playMove()
                    hapticManager.mediumImpact()
                } else if (playerNumber == 2 && activePlayer == 2) {
                    p2Moves++
                    p2Time += selectedPreset.incrementSeconds
                    if (selectedPreset.hasSecondPhase && p2Moves == selectedPreset.secondPhaseTriggerMove) {
                        p2Time += selectedPreset.secondPhaseAddedMinutes * 60.0
                    }
                    activePlayer = 1
                    soundManager.playMove()
                    hapticManager.mediumImpact()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Player 2 (Black, top half, rotated 180 degrees)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable { handleTap(2) }
        ) {
            PlayerArea(
                playerNumber = 2,
                timeLeft = p2Time,
                moves = p2Moves,
                isActive = activePlayer == 2,
                hasStarted = hasStarted,
                subtitle = selectedPreset.subtitle,
                isRotated = true
            )
        }

        // Center Control Bar
        ControlBar(
            hasStarted = hasStarted,
            isPaused = isPaused,
            p1Time = p1Time,
            baseSeconds = selectedPreset.baseMinutes * 60.0,
            selectedPresetName = selectedPreset.name,
            onClose = onDismiss,
            onPresetClick = { showPresetDialog = true },
            onPlayPauseClick = {
                hapticManager.mediumImpact()
                if (isPaused) {
                    isPaused = false
                    if (activePlayer == null) activePlayer = 1
                } else {
                    isPaused = true
                }
            },
            onResetClick = {
                hapticManager.mediumImpact()
                resetClock()
            },
            onInfoClick = { showInfoDialog = true }
        )

        // Player 1 (White, bottom half)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable { handleTap(1) }
        ) {
            PlayerArea(
                playerNumber = 1,
                timeLeft = p1Time,
                moves = p1Moves,
                isActive = activePlayer == 1,
                hasStarted = hasStarted,
                subtitle = selectedPreset.subtitle,
                isRotated = false
            )
        }
    }

    // 1. Preset Choice Dialog
    if (showPresetDialog) {
        PresetChooserDialog(
            presets = presets,
            currentPreset = selectedPreset,
            onPresetSelected = {
                selectedPreset = it
                showPresetDialog = false
            },
            onDismiss = { showPresetDialog = false }
        )
    }

    // 2. Info Dialog
    if (showInfoDialog) {
        InfoDialog(onDismiss = { showInfoDialog = false })
    }
}

@Composable
fun PlayerArea(
    playerNumber: Int,
    timeLeft: Double,
    moves: Int,
    isActive: Boolean,
    hasStarted: Boolean,
    subtitle: String,
    isRotated: Boolean,
    modifier: Modifier = Modifier
) {
    val isTimeOut = timeLeft <= 0

    val bgColor by animateColorAsState(
        targetValue = when {
            isTimeOut -> if (playerNumber == 2) Color(0xFF8C2525) else Color(0xFFFADAD8)
            isActive -> if (playerNumber == 2) Color(0xFF121212) else Color(0xFFFFFFFF)
            else -> if (playerNumber == 2) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
        },
        animationSpec = tween(durationMillis = 200),
        label = "bgColorAnimation"
    )

    val textColor = when {
        isTimeOut -> if (playerNumber == 2) Color.White else Color(0xFFEF4444)
        isActive -> if (playerNumber == 2) Color.White else Color.Black
        else -> if (playerNumber == 2) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
    }

    val subtextColor = when {
        isTimeOut -> if (playerNumber == 2) Color.White.copy(alpha = 0.7f) else Color(0xFFEF4444).copy(alpha = 0.7f)
        isActive -> if (playerNumber == 2) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
        else -> if (playerNumber == 2) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.rotate(if (isRotated) 180f else 0f)
        ) {
            if (isTimeOut) {
                Box(
                    modifier = Modifier
                        .clip(CapsuleShape)
                        .background(textColor.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = loc("Vreme je isteklo!"),
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text(
                    text = if (!hasStarted) subtitle else "Poteza: $moves",
                    color = subtextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = formatTime(timeLeft),
                color = textColor,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ControlBar(
    hasStarted: Boolean,
    isPaused: Boolean,
    p1Time: Double,
    baseSeconds: Double,
    selectedPresetName: String,
    onClose: () -> Unit,
    onPresetClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onResetClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // TRAKA IZMEDJU DVE NAMERNO FIKSNE POLOVINE JE I SAMA FIKSNA.
    //
    // Prvi prelaz ove faze poslao je podlogu trake na `DS.surface` a polovine ostavio
    // fiksnim — ispravno svako za sebe, ali par se nikad nije izmerio. U svetloj temi
    // `DS.surface` je `#FFFFFF`, bas boja bele polovine ispod: sav je pao sa **14,63
    // na 1,000**. Traka NIJE `clickable`, pa korisnik koji cilja vrh „svoje" bele
    // polovine pogadja inertnu traku i sat se ne prebacuje — bez vidljivog sava ne zna
    // gde polovina pocinje.
    //
    // Zato se ovde vraca zatecena fiksna `#1E293B` i CEO sadrzaj trake cita `DarkColors`
    // (fiksna paleta, jer je ovo trajno tamna povrsina — ne `DS.*`, koji bi u svetloj
    // temi dao `DS.ink` `#161A22` na `#1E293B`, tj. 1,19:1). Mereno, ista formula kao
    // `ContrastTest`, isto u obe teme:
    //   traka prema beloj polovini: 14,629 (aktivna) / 11,652 (mirna) / 11,205 (istek)
    //   `DarkColors.ink` na traci 12,929; `DarkColors.accent` 5,629
    // Sav prema CRNOJ polovini iznad ostaje nevidljiv (1,281 / 1,050) — to nije uveo
    // ovaj prelaz, isto je bilo i pre grane; upisano u „Poznata ogranicenja".
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ClockBarBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Close Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(44.dp)
                .background(ClockBarWell, CircleShape)
        ) {
            // `DarkColors.ink` na `ClockBarWell` (belo@12% preko trake), fiksan par
            // — 8,87:1, deljen sa dugmetom za resetovanje ispod (isti krug, ista
            // podloga). Vidi `ContrastTest.clockRoundButtonIconsAreReadable`.
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = loc("Zatvori"),
                tint = DarkColors.ink,
                modifier = Modifier.size(18.dp)
            )
        }

        // Mid section: either preset chooser or play/pause button
        if (!hasStarted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(CapsuleShape)
                        // 0,12 a ne 0,15: nad fiksnom trakom akcent na sopstvenom
                        // @15% tintu daje 4,339 (ispod AA), na @12% daje 4,583.
                        // 0,12 je uz to i iOS vrednost (`ChessClockView.swift:278`).
                        .background(ClockBarAccent.copy(alpha = 0.12f))
                        .clickable { onPresetClick() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // `ClockBarAccent` (=`DarkColors.accent`) na sopstvenom @12% cipu:
                    // isti par kao tekst odmah desno, vec izmeren i pokriven u
                    // `ContrastTest.clockControlBarIsReadableOverFixedHalves`
                    // (4,583 — "akcent trake na sopstvenom @12% cipu").
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = loc("Vremenska kontrola"),
                        tint = ClockBarAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedPresetName,
                        color = ClockBarAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onInfoClick) {
                    // `DarkColors.ink` direktno na traci (`ClockBarBackground`, bez
                    // kruga iza sebe) — isti par kao „DarkColors.ink na traci" u
                    // `ContrastTest.clockControlBarIsReadableOverFixedHalves` (12,929).
                    // Pre ove izmene je `ℹ️` bio pun-kolor emoji glif; Skia za takve
                    // glifove IGNORISE `color=` teksta, pa je zatecen `color = DS.accent`
                    // izgledao kao da mehanizam radi — nije radio nikad. Sa pravom
                    // ikonom `tint` sad stvarno boji glif.
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = loc("Objašnjenje pravila"),
                        tint = DarkColors.ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(52.dp)
                    .background(ClockBarAccent, CircleShape)
            ) {
                // `DarkColors.onAccent` (fiksno, NE `DS.onAccent` — taj prati temu
                // preko `DS.accent`, dok je krug ovde uvek `ClockBarAccent` =
                // `DarkColors.accent`) na `ClockBarAccent`: 6,71:1. Bela bi ovde
                // pala na 2,60:1 — `DarkColors.accent` je svetloplav, ne taman.
                // Isto kao `ℹ️` pre ove izmene: `▶️`/`⏸️` su bili pun-kolor emoji
                // glifovi, pa je zatecen `color = DS.onAccent` bio mrtav — Skia
                // ignorise `color=` za takve glifove. Nov par, vidi
                // `ContrastTest.clockPlayPauseIconIsReadableOnItsAccentCircle`.
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = loc(if (isPaused) "Pusti sat" else "Pauziraj sat"),
                    tint = DarkColors.onAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Reset Button
        val canReset = hasStarted || p1Time != baseSeconds
        // ONEMOGUCENO STANJE: `tint`, ne `Modifier.alpha` na celom dugmetu.
        //
        // Pre ove izmene glif je bio pun-kolor emoji (🔄), koji Skia
        // IGNORISE za `color=`, pa je zatecen kod razlikovao stanja jedino preko
        // `Modifier.alpha` na celom dugmetu (jedini sloj koji deluje i na emoji) —
        // ali je to dimovalo i krug ISPOD glifa. Sa pravim vektorom `tint` sad
        // stvarno boji glif, pa je pitanje ponovo otvoreno: ostaje li M3-standardni
        // `alpha(0.38f)` ili se prelazi na par tinta (`DarkColors.ink` /
        // `DarkColors.inkMuted`), isto kao ostala dugmad na grani (`DS.ink`/
        // `DS.inkMuted`, npr. traka pregleda partije u `MainActivity.kt`)?
        //
        // IZMERENO (formula identicna `ContrastTest`-u), oba para nad STVARNOM
        // podlogom (`ClockBarWell` — belo@12% preko `ClockBarBackground`):
        //   `alpha(0.38f)` na celom dugmetu: krug se sam utopi u traku (1,15:1 —
        //     nevidljiv kao krug), a glif nad TIM utopljenim krugom pada na 2,75:1 —
        //     ispod WCAG-ovog ne-tekstualnog praga od 3:1 (onemoguceno dugme je
        //     tehnicki izuzeto od tog praga, ali cilj je da SE VIDI da dugme postoji).
        //   `tint = DarkColors.inkMuted`, krug NETAKNUT (i dalje pun `ClockBarWell`):
        //     3,26:1 — iznad praga, i krug ostaje jasno ocrtan umesto da nestane.
        // Odabran je `tint`: jedini od ova dva koji stvarno prelazi 3:1, i jedini
        // koji ne gasi i sam krug — dugme se vidi kao POSTOJECE ali neaktivno,
        // ne kao da je nestalo. `Modifier.alpha` je uklonjen u celosti (import
        // `androidx.compose.ui.draw.alpha` vise nije potreban nigde u fajlu).
        // Vidi `ContrastTest.clockRoundButtonIconsAreReadable` (omoguceno,
        // deljeno sa dugmetom „Zatvori") i `clockResetIconDisabledStateIsReadable`
        // (onemoguceno, nov par).
        IconButton(
            onClick = onResetClick,
            enabled = canReset,
            modifier = Modifier
                .size(44.dp)
                .background(ClockBarWell, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = loc("Resetuj sat"),
                tint = if (canReset) DarkColors.ink else DarkColors.inkMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PresetChooserDialog(
    presets: List<TimeControlPreset>,
    currentPreset: TimeControlPreset,
    onPresetSelected: (TimeControlPreset) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Blic") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DS.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = loc("Vremenska kontrola"),
                    color = DS.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // Category selection Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(loc("Blic"), loc("Ubrzani šah"), loc("Klasični šah")).forEach { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) DS.accent else DS.fill)
                                .clickable { selectedCategory = category }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) DS.onAccent else DS.ink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Preset List
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.filter { it.category == selectedCategory }.forEach { preset ->
                        val isSelected = currentPreset == preset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) DS.fill else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) DS.accent else DS.line,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onPresetSelected(preset) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = preset.name,
                                        color = DS.ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (preset.isOfficial) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        // `DS.accent` — dijalog lebdi nad scrim-om i
                                        // prati temu (vidi komentar iznad `ControlBar`),
                                        // pa ovde, za razliku od trake, ide token, ne
                                        // fiksna boja. Par je vec pokriven: `accent/fill`
                                        // (`ContrastTest.textOnFillMeetsAA`) i
                                        // `accent/surface`
                                        // (`accentTextOnSurfaceAndGroundMeetsAA`) — ovaj
                                        // red sedi na `DS.fill` kad je izabran, inace na
                                        // providnoj podlozi nad `DS.surface`. Oba >= 4.5,
                                        // iznad i WCAG-ovog ne-tekstualnog praga od 3:1
                                        // koji ovde stvarno vazi (ikonica je dekorativna).
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            // Dekorativna: zvezdica oznacava `preset.isOfficial`, a Text
                                            // odmah ispod vec kaze „Zvanicni svetski blic" /
                                            // „Turniri kandidata / FIDE". contentDescription bi
                                            // citac ekrana naterao da istu stvar kaze dvaput.
                                            contentDescription = null,
                                            tint = DS.accent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = preset.subtitle,
                                    color = DS.inkMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = loc("Izaberi"),
                                color = if (isSelected) DS.accent else DS.inkMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = loc("Zatvori"), color = DS.accent)
                }
            }
        }
    }
}

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DS.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = loc("Objašnjenje vremenskih kontrola"),
                    color = DS.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoCategorySection(
                        title = loc("Blic"),
                        items = listOf(
                            "3 + 2" to loc("3 minuta + 2 sekunde inkrementa. Zvanični format na Svetskim prvenstvima u blicu."),
                            "5 + 0" to loc("Čistih 5 minuta bez inkrementa. Stara škola blica."),
                            "5 + 3" to loc("5 minuta + 3 sekunde inkrementa. Koristi se na jačim online turnirima.")
                        )
                    )

                    InfoCategorySection(
                        title = loc("Ubrzani šah"),
                        items = listOf(
                            "15 + 10" to loc("15 minuta + 10 sekundi inkrementa. Zvanični FIDE format za Svetska prvenstva."),
                            "10 + 5" to loc("10 minuta + 5 sekundi inkrementa. Popularan online format, ozbiljniji od blica."),
                            "25 + 10" to loc("25 minuta + 10 sekundi inkrementa. Čest format na lokalnim turnirima.")
                        )
                    )

                    InfoCategorySection(
                        title = loc("Klasični šah"),
                        items = listOf(
                            "90 + 30 + 30" to loc("90 minuta za 40 poteza, potom +30 minuta, uz 30s inkrementa. Format za svetske šampionate."),
                            "90 + 30" to loc("90 minuta za celu partiju uz dodavanje od 30 sekundi po potezu (format domaćih liga).")
                        )
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DS.accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = loc("Zatvori"), color = DS.onAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InfoCategorySection(
    title: String,
    items: List<Pair<String, String>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = DS.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (name, description) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DS.fill)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = name,
                        color = DS.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )

                    Text(
                        text = description,
                        color = DS.inkMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

private val CapsuleShape = RoundedCornerShape(50)

private fun formatTime(seconds: Double): String {
    if (seconds <= 0) return "00:00"
    val totalMins = seconds.toInt() / 60
    val secs = seconds.toInt() % 60
    
    return when {
        totalMins >= 60 -> {
            val hours = totalMins / 60
            val mins = totalMins % 60
            String.format("%d:%02d:%02d", hours, mins, secs)
        }
        seconds < 10 -> {
            // Show tenths of a second if under 10 seconds for dramatic precision
            val tenths = ((seconds - seconds.toInt()) * 10).toInt()
            String.format("%02d:%02d.%d", totalMins, secs, tenths)
        }
        else -> {
            String.format("%02d:%02d", totalMins, secs)
        }
    }
}
