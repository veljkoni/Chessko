package com.veljkoni.chessko.ui

import com.veljkoni.chessko.ui.theme.ButtonShape
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
    val accent = DS.accent
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

        // Scrollable Lesson Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = DS.Space.l),
            verticalArrangement = Arrangement.spacedBy(DS.Space.l)
        ) {
            if (doc == null) {
                // Kartica postoji samo ako se lekcija vec jednom ucitala,
                // pa je ovo prakticno nedostizno — ali tiha praznina bi
                // bila gora od recenice.
                Text(
                    text = loc("Lekcija nije dostupna."),
                    // Direktno na `DS.ground` (skrol nema Card ni Surface iza ovog
                    // teksta) — isti par kao `inkMuted`/`ground` u `PathView`.
                    color = DS.inkMuted,
                    // Recenica, ne oznaka -> `body`.
                    style = Type.body
                )
            } else {
                // Lesson Header — BEZ kartice ispod naslova. iOS (`LessonDetailView.swift:128-150`,
                // `lessonHeader`) nema pozadinu/ivicu na celom header-u; tint akcenta stoji SAMO
                // iza kvadrata sa ikonom. Kriska 6d-2, krug ispravki 1: prethodna verzija je
                // tintovala CEO red (`accent@12%` preko `DS.ground`), pa je podnaslov
                // (`DS.inkMuted`, tada 12sp, nije bold) pao na 3,62:1 u svetloj temi -- ispod AA i
                // NOV sub-AA par kog iOS nema, jer iOS ovde nema kompozitnu podlogu uopste.
                // Uklanjanjem tinta sa reda podnaslov pada na goli `DS.ground`, gde je
                // `inkMuted`/`ground` = 4,36 (svetla) / 5,94 (tamna) -- vec poznat, vec pinovan
                // par (`ContrastTest.knownSubAAPairsDoNotGetWorse`), i tacno ono sto iOS ima.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Naslovna ikona lekcije: iOS je crta na `.dsTitle.weight(.medium)`
                        // (`Chessko/Views/LessonDetailView.swift:135`), dakle bas velicinom
                        // `title`. Glif nije tekst, pa uzima samo velicinu iz skale.
                        LessonGlyphView(lessonGlyph(doc.icon), fontSize = Type.title.fontSize, tint = accent)
                    }
                    Spacer(modifier = Modifier.width(DS.Space.m))
                    Column {
                        if (number != null) {
                            Text(
                                text = locF("Lekcija %d", number),
                                color = accent,
                                // Sitna oznaka iznad naslova -> `label`. iOS ovaj red
                                // nema (`lessonHeader` ne pise redni broj).
                                style = Type.label,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = doc.title,
                            color = DS.ink,
                            // iOS: `.dsHeading.weight(.bold)`
                            // (`Chessko/Views/LessonDetailView.swift:140`).
                            style = Type.heading,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = doc.subtitle,
                            color = DS.inkMuted,
                            // iOS: `.dsBody` (`Chessko/Views/LessonDetailView.swift:142`).
                            // 12 je ovde bilo razilazenje naniže, ne odluka.
                            style = Type.body
                        )
                    }
                }

                LessonBlocks(doc.blocks, accent, explorerViewModel)

                // Vidi se SAMO kad je lekcija otvorena kao korak Puta.
                // `onComplete` (iz `PathView`) i zavrsava korak i vraca listu —
                // spec 5.1 trazi da se korak zavrsi tek kad korisnik dodje do
                // kraja lekcije i POTVRDI, ne samim otvaranjem.
                if (onComplete != null) {
                    Column(
                        modifier = Modifier.padding(top = DS.Space.xs, bottom = DS.Space.xl),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(color = DS.line)
                        Button(
                            shape = ButtonShape, // spec § 3: Radius.m, nigde „stadion"
                            onClick = onComplete,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            Text(
                                text = loc("Završi korak"),
                                fontWeight = FontWeight.SemiBold,
                                // Tekst NA `accent` podlozi -> `DS.onAccent`, ne
                                // `DS.ink`/bela. `accent` menja svetlinu izmedju
                                // tema (`DS.onAccent` doc-komentar u
                                // `DesignSystem.kt`); bela bi u tamnoj temi pala
                                // na 2,6:1 (ista greska koju je iOS vec pravio).
                                color = DS.onAccent
                            )
                        }
                    }
                }
            }
        }
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
