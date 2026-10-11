package com.veljkoni.chessko.ui

import com.veljkoni.chessko.ui.theme.ButtonShape
import com.veljkoni.chessko.ui.theme.dsPrimaryButtonColors
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.LessonRepository
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.LearnViewModel

// MARK: - Detalj lekcije
//
// Izdvojeno iz `LearnView.kt` (Faza 6c, Task 5): Put mora da otvori lekciju
// BEZ spiska koji je stajao ispred nje u `LearnView`. Sadrzaj je SELJEN, ne
// prepisan — isti header, ista traka nazad, isti `LessonBlocks` poziv.
//
// `onComplete` je jedina razlika u odnosu na staro ponasanje: dugme potvrde
// na dnu koje se vidi SAMO kad je lekcija otvorena kao korak Puta. `null`
// znaci da je lekcija otvorena iz obicnog spiska (`LearnView`), gde nema sta
// da se "zavrsi".

// `accentFor(id)` (sest boja, jedna po lekciji) je OBRISANA u Fazi 6d-2, Task 2.
// iOS je tacno ovo uklonio u Fazi 1: „per-lekcijske boje svedene na jedan
// akcent, a boja zadrzana samo tamo gde nosi znacenje" — spec 5.6 trazi jedan
// uzdrzan akcent, ne sest. Jedino pozivno mesto sada cita `DS.accent` direktno.

@Composable
fun LessonDetailView(
    lessonId: String,
    onClose: () -> Unit,
    onComplete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repo = remember { LessonRepository(context) }
    // Sopstveni model po ekranu, NE isti obrazac kao iOS: tamo jedan
    // `LearnViewModel` zivi u `ContentView`-u i ubacuje se kroz `@Environment`
    // (`Chessko/Views/PathView.swift:20-23`, `Chessko/Views/LessonRenderer.swift:222-228`).
    // Lokalna instanca je ovde svesno drugaciji izbor (jednostavnije, bez
    // ekvivalenta @Environment-u u ovom Compose kodu) — ostaje kako jeste, ali
    // komentar ne sme da tvrdi paritet koji ne postoji.
    val explorerViewModel = remember { LearnViewModel() }

    // NIJE `Loc.getLanguage()`: on za kineski vraca „zh", a fajl se zove
    // `.zh-Hans.json`. Sa pogresnim kodom bi kineski korisnik tiho dobio
    // engleski — bez pada i bez poruke.
    val lang = Loc.fileLanguageCode()

    val doc = remember(lessonId, lang) { repo.lesson(lessonId, lang) }
    val number = remember(lessonId) {
        repo.discoveredLessonIds().indexOf(lessonId).let { if (it >= 0) it + 1 else null }
    }

    val scrollState = rememberScrollState()
    val contentScrolled by remember { derivedStateOf { scrollState.value > 0 } }
    // Sistemsko „nazad" vodi na Put, isto kao dugme u app baru (ranije je
    // izlazilo iz aplikacije, jer ruta Puta nije imala `BackHandler`).
    BackHandler(onBack = onClose)

    // Naslov u app baru: iz koraka Puta naslov lekcije (Put broji korake, ne
    // lekcije), inace „Lekcija N" — isto pravilo kao iOS `LessonDetailView.navigationTitle`.
    val barTitle = if (onComplete != null || number == null) (doc?.title ?: "") else locF("Lekcija %d", number)

    Column(modifier = Modifier.fillMaxSize()) {
        // App bar lekcije (spec § 4): `navBar` podloga, nazad + „Put" u `accent`
        // (8,53 / 6,65), naslov `Type.body` medium, `line` ispod tek kad sadrzaj
        // klizi. Zamenjuje plutajuce „← Put" na `fill` plocici.
        val navBar = DS.navBar
        val line = DS.line
        val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
        LessonAppBar(
            title = barTitle,
            onBack = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .drawBehind {
                    // Traka statusa iznad app bara je u `navBar` boji, kao na iOS-u.
                    // Ekran je unutar `Scaffold`-ovog paddinga (vrh = statusna traka),
                    // pa se ta traka crta IZNAD sopstvenih granica: Compose ne
                    // isece crtanje van granica bez `clip`, a nijedan roditelj do
                    // `Scaffold`-a ne isece (a `Scaffold` pokriva ceo prozor).
                    drawRect(navBar, topLeft = Offset(0f, -statusBarTop), size = Size(size.width, size.height + statusBarTop))
                    // Linija ispod se CRTA, ne slaze kao element: pojava linije ne
                    // menja visinu trake, pa skrol ne poskoci.
                    if (contentScrolled) {
                        val stroke = 1.dp.toPx()
                        drawRect(line, topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
                    }
                }
                .padding(horizontal = DS.Space.xs)
        )

        val isTablet = isTabletWindow()
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Spec § 13: tablet = prozor >= 600 x 480 dp (`isTabletWindow`, isto kao
            // Igra/Zadaci). Dve kolone (spec § 9) samo u pejzazu I uz dovoljno visine:
            // telefon u pejzazu je >= 600 sirok, ali nizak — tu vazi jedna kolona.
            val twoColumn = doc != null && isTablet && maxWidth > maxHeight &&
                maxHeight >= 480.dp && doc.blocks.any { it.carriesBoard() }

            if (!twoColumn) {
                // Lekcija je dokument koji se cita uspravno, pa se tabla racuna po
                // portretnom pravilu (telefon do 560, tablet do 800); kolona teksta
                // (640) je na tabletu ionako uza.
                val boardCap = boardSide(maxWidth, maxHeight, isTablet, isLandscape = false)
                CompositionLocalProvider(LocalLessonBoardMaxSide provides boardCap) {
                // Jedna kolona: tekst do 640 (spec § 9 „Tablet: tekst u koloni do
                // 640"), centrirana; na telefonu granica nije dostizna.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = DS.Space.l),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = LessonTextMaxWidth).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(DS.Space.l)
                    ) {
                        LessonContent(doc, explorerViewModel, LessonBoardMode.Inline, onComplete)
                    }
                }
                }
            } else {
                // Tablet pejzaz (spec § 9): tekst 480–520 levo, tabla desno i
                // lepljiva dok tekst skroluje. Desno stoji tabla bloka do kog je
                // citalac stigao — poslednja oznaka iznad sredine vidnog polja.
                // Kolona table: zajednicka funkcija `boardSide` (tablet, pejzaz), uz
                // granice ovog rasporeda — tekst bar 480, prostor za dugmad ispod.
                val gutter = DS.Space.xl
                val shared = boardSide(maxWidth, maxHeight, isTablet = true, isLandscape = true)
                val boardCol = minOf(maxHeight - 160.dp, maxWidth - 480.dp - gutter * 3, shared)
                    .coerceAtLeast(280.dp)
                val textCol = (maxWidth - boardCol - gutter * 3).coerceIn(480.dp, 520.dp)
                val boardIndices = doc!!.blocks.indices.filter { doc.blocks[it].carriesBoard() }
                val markerY = remember(doc) { mutableStateMapOf<Int, Float>() }
                var viewportTop by remember { mutableFloatStateOf(0f) }
                var viewportHeight by remember { mutableFloatStateOf(0f) }
                val activeIndex = boardIndices
                    .filter { (markerY[it] ?: Float.MAX_VALUE) <= viewportTop + viewportHeight / 2 }
                    .maxOrNull() ?: boardIndices.first()

                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(gutter, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .width(textCol)
                            .fillMaxHeight()
                            .onGloballyPositioned {
                                viewportTop = it.positionInRoot().y
                                viewportHeight = it.size.height.toFloat()
                            }
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(DS.Space.l)
                    ) {
                        LessonContent(
                            doc, explorerViewModel,
                            LessonBoardMode.Markers(activeIndex) { i, y -> markerY[i] = y },
                            onComplete
                        )
                    }
                    Column(
                        modifier = Modifier
                            .width(boardCol)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = DS.Space.l)
                    ) {
                        // Sve table ostaju u kompoziciji (napredak u vezbi ne sme da
                        // nestane kad citalac skroluje dalje i vrati se); neaktivne
                        // se mere na 0 i ne postavljaju — ne crtaju se i ne primaju
                        // dodir.
                        for (i in boardIndices) {
                            val active = i == activeIndex
                            Box(
                                modifier = if (active) Modifier.fillMaxWidth() else Modifier
                                    .clearAndSetSemantics {}
                                    .layout { measurable, _ ->
                                        measurable.measure(Constraints.fixed(0, 0))
                                        layout(0, 0) {}
                                    }
                            ) {
                                CompositionLocalProvider(LocalLessonBoardMaxSide provides boardCol) {
                                    LessonBoardBlock(doc.blocks[i], explorerViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Spec § 9: tekst lekcije na tabletu u koloni do 640. */
private val LessonTextMaxWidth = 640.dp

/** Zaglavlje + blokovi + dugme koraka — isto u jednoj i u dve kolone. */
@Composable
private fun LessonContent(
    doc: com.veljkoni.chessko.models.LessonDocument?,
    explorerViewModel: LearnViewModel,
    boardMode: LessonBoardMode,
    onComplete: (() -> Unit)?
) {
    if (doc == null) {
        // Kartica postoji samo ako se lekcija vec jednom ucitala, pa je ovo
        // prakticno nedostizno — ali tiha praznina bi bila gora od recenice.
        Text(text = loc("Lekcija nije dostupna."), color = DS.inkMuted, style = Type.body)
        return
    }
    // Zaglavlje lekcije (spec § 9): naslov `Type.title` 22 bold `ink`, podnaslov
    // meta `inkMuted`. Plocica ikone `fill` + `ink` (bila `accent` tinta — accent
    // je samo za ono sto se tapa, spec § 3). Redni broj „Lekcija N" je vec
    // naslov app bara, pa iznad naslova vise ne stoji (iOS ga tu nikad nije imao).
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = DS.Space.l),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(DS.Radius.m))
                .background(DS.fill),
            contentAlignment = Alignment.Center
        ) {
            LessonGlyphView(lessonGlyph(doc.icon), fontSize = Type.title.fontSize, tint = DS.ink)
        }
        Spacer(modifier = Modifier.width(DS.Space.m))
        Column {
            Text(text = doc.title, color = DS.ink, style = Type.title)
            Text(text = doc.subtitle, color = DS.inkMuted, style = Type.body)
        }
    }

    LessonBlocks(doc.blocks, explorerViewModel, boardMode)

    // Vidi se SAMO kad je lekcija otvorena kao korak Puta: korak se zavrsava tek
    // kad korisnik dodje do kraja lekcije i POTVRDI (spec 5.1).
    if (onComplete != null) {
        Column(
            modifier = Modifier.padding(top = DS.Space.s, bottom = DS.Space.xl),
            verticalArrangement = Arrangement.spacedBy(DS.Space.l)
        ) {
            HorizontalDivider(color = DS.line)
            Button(
                shape = ButtonShape, // spec § 3/§ 9: Radius.m, nigde „stadion"
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = dsPrimaryButtonColors()
            ) {
                // Tekst NA `accent` podlozi -> `DS.onAccent` (u `dsPrimaryButtonColors`).
                Text(text = loc("Završi korak"), style = Type.body, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        Spacer(Modifier.height(DS.Space.l))
    }
}

/**
 * App bar lekcije: „← Put" na pocetku, naslov centriran kad ima mesta.
 *
 * Naslov se NIKAD ne preklapa sa dugmetom, ni na jednom od 8 jezika (dugme je
 * „← Parcours"/„← Percorso" sire od fiksne margine koja je ranije stajala ovde):
 * ako stane izmedju dve sirine dugmeta — centriran po celoj traci; inace stoji
 * odmah iza dugmeta i skracuje se trotackom.
 */
@Composable
private fun LessonAppBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier,
        content = {
            TextButton(onClick = onBack) {
                // Ikona je DEKORATIVNA — „Put" odmah do nje nosi celo znacenje.
                // `AutoMirrored` jer je strelica smerna (RTL).
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = DS.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(DS.Space.xs))
                Text(text = loc("Put"), color = DS.accent, style = Type.body, maxLines = 1)
            }
            Text(
                text = title,
                color = DS.ink,
                style = Type.body,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val back = measurables[0].measure(loose)
        val gap = DS.Space.s.roundToPx()
        val side = back.width + gap
        val titleMeasurable = measurables[1]
        val centeredRoom = (width - 2 * side).coerceAtLeast(0)
        val centered = titleMeasurable.maxIntrinsicWidth(back.height) <= centeredRoom
        val titleMax = if (centered) centeredRoom else (width - side).coerceAtLeast(0)
        val titlePlaceable = titleMeasurable.measure(loose.copy(maxWidth = titleMax))
        val height = maxOf(back.height, titlePlaceable.height, constraints.minHeight)
        layout(width, height) {
            back.placeRelative(0, (height - back.height) / 2)
            val x = if (centered) (width - titlePlaceable.width) / 2 else side
            titlePlaceable.placeRelative(x, (height - titlePlaceable.height) / 2)
        }
    }
}
