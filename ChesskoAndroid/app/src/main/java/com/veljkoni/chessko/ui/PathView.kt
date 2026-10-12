package com.veljkoni.chessko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.logic.LessonRepository
import com.veljkoni.chessko.logic.PathProgress
import com.veljkoni.chessko.logic.ProgressStore
import com.veljkoni.chessko.logic.StepState
import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locInteger
import com.veljkoni.chessko.logic.locPlural
import com.veljkoni.chessko.logic.locF
import com.veljkoni.chessko.models.CurriculumStep
import com.veljkoni.chessko.models.StepKind
import com.veljkoni.chessko.models.loadCurriculum
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.ui.theme.dsCard

/**
 * Odluka o tome gde korak vodi stoji na JEDNOM mestu. Razmazana po UI-ju, ona
 * se razilazi sa prikazom: iOS je tu imao bug u kome su vezba, test i partija
 * izgledali aktivno i vodili na PRAZAN EKRAN.
 *
 * `null` znaci da korak nije podrzan -- kartica tada pise „Uskoro" i NEMA
 * strelicu, umesto da se otvori u prazno.
 */
sealed class StepRoute {
    data class Lesson(val lessonId: String, val stepId: String) : StepRoute()
    data class Practice(val step: CurriculumStep) : StepRoute()
    data class Game(val difficulty: String, val startFEN: String?, val stepId: String) : StepRoute()
}

fun routeFor(step: CurriculumStep): StepRoute? = when (val k = step.kind) {
    is StepKind.Lesson -> StepRoute.Lesson(k.lessonId, step.id)
    // `test` se od `practice`-a razlikuje SAMO po toleranciji na gresku, koju
    // `StepPracticeView`/`PuzzleViewModel` citaju direktno iz `step.kind` — ista
    // ruta za oba.
    is StepKind.Practice -> StepRoute.Practice(step)
    is StepKind.Test -> StepRoute.Practice(step)
    // `routeFor` od Task-a 7 vise ni za jedan tip koraka ne vraca `null` sa
    // isporucenim kurikulumom — poglavlja sa `game` korakom su prohodna.
    is StepKind.Game -> StepRoute.Game(k.difficulty, k.startFEN, step.id)
}

@Composable
fun PathView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ProgressStore.getInstance(context) }
    val curriculum = remember { loadCurriculum(context) }
    // Isti repozitorijum koji `LessonDetailView` koristi da otvori lekciju --
    // ovde samo cita naslov, da kartica koraka ne pise golo „Lekcija" za svih
    // sest lekcija u sest poglavlja.
    val lessonRepo = remember { LessonRepository(context) }

    // Naslov poglavlja stize iz JSON-a vec preveden -- NE kroz loc().
    val lang = Loc.fileLanguageCode().substringBefore('-')
    // Naslov LEKCIJE trazi PUN kod fajla (npr. "zh-Hans"), ne skraceni oblik
    // iznad -- isti razlog kao u `LessonDetailView`: skraceno "zh" bi dalo
    // engleski naslov kineskom korisniku.
    val fileLang = Loc.fileLanguageCode()

    // Cita `store.snapshot`, pa se lista sama prekrsti kad se korak zavrsi na
    // drugom ekranu i korisnik se vrati.
    val states = store.stepStates(curriculum)

    var route by remember { mutableStateOf<StepRoute?>(null) }

    // Nema NavHost-a (projekat nema androidx.navigation i nece je dobiti).
    // Ruta je obicno stanje, a ekran koraka zamenjuje listu.
    when (val r = route) {
        is StepRoute.Lesson -> {
            LessonDetailView(
                lessonId = r.lessonId,
                onClose = { route = null },
                onComplete = { store.completeStep(r.stepId); route = null }
            )
            return
        }
        is StepRoute.Practice -> {
            StepPracticeView(step = r.step, onClose = { route = null })
            return
        }
        is StepRoute.Game -> {
            StepGameView(
                difficulty = r.difficulty,
                startFEN = r.startFEN,
                stepId = r.stepId,
                onClose = { route = null }
            )
            return
        }
        null -> Unit
    }

    // Raspored preuzet sa iOS-a (spec § 8): naslov „Put", karta „Nastavi",
    // kartica niza i dnevnog cilja, pa poglavlja. Kolona do 720 dp, centrirana
    // (tablet, § 13); na telefonu je ekran uzi pa se nista ne menja.
    Box(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = PathMaxColumnWidth)
                .fillMaxWidth()
                .padding(horizontal = DS.Space.l, vertical = DS.Space.m),
            verticalArrangement = Arrangement.spacedBy(DS.Space.l)
        ) {
            Text(
                text = loc("Put"),
                color = DS.ink,
                style = Type.title,
                modifier = Modifier.padding(top = DS.Space.xs)
            )

            // Nastavak: prvi korak koji nije zavrsen (isti izbor kao iOS `nextStep`).
            val next = curriculum.allStepIds.firstOrNull { states[it] == StepState.AVAILABLE }
            val nextStep = next?.let { curriculum.step(it) }
            if (nextStep != null) {
                val nextIndex = curriculum.allStepIds.indexOf(nextStep.id) + 1
                val chapterOfNext = curriculum.chapters.firstOrNull { c -> c.steps.any { it.id == nextStep.id } }
                ContinueCard(
                    stepTitle = stepCardTitle(nextStep, lessonRepo, fileLang),
                    chapterTitle = chapterOfNext?.let { it.title[lang] ?: it.title["en"] ?: it.id },
                    icon = stepKindIcon(nextStep),
                    accessibility = locF("Nastavi, korak %d, %s", nextIndex, stepCardTitle(nextStep, lessonRepo, fileLang)),
                    runnable = routeFor(nextStep) != null,
                    onClick = { routeFor(nextStep)?.let { route = it } }
                )
            } else {
                PathCompleteCard()
            }

            StreakAndGoalCard(
                streak = store.currentStreak,
                goal = store.dailyGoal,
                goalMet = store.goalMetToday
            )

            for (chapter in curriculum.chapters) {
                val done = chapter.steps.count { states[it.id] == StepState.COMPLETED }
                val chapterTitle = chapter.title[lang] ?: chapter.title["en"] ?: chapter.id
                val allLocked = chapter.steps.isNotEmpty() &&
                    chapter.steps.all { (states[it.id] ?: StepState.LOCKED) == StepState.LOCKED }
                if (allLocked) {
                    LockedChapterRow(title = chapterTitle, stepCount = chapter.steps.size)
                } else {
                    ChapterSection(
                        title = chapterTitle,
                        progress = done to chapter.steps.size,
                        steps = chapter.steps,
                        states = states,
                        lessonRepo = lessonRepo,
                        fileLang = fileLang,
                        onOpen = { step -> routeFor(step)?.let { route = it } }
                    )
                }
            }
        }
    }
}

/** Najveca sirina kolone Puta (spec § 8 / § 13: tablet, kolona do 720, centrirana). */
private val PathMaxColumnWidth = 720.dp

/** Velicina ikone statusa koraka (spec § 8: „status koraka ikonom 24"). */
private val StepStatusIconSize = 24.dp

/** Plocica ikone vrste koraka — ista mera kao iOS (`PathView.swift`, 28 pt). */
private val StepKindTileSize = 28.dp

/** Ikona vrste koraka — isti simboli kao iOS `icon(of:)` (book / puzzlepiece / seal / cpu). */
private fun stepKindIcon(step: CurriculumStep): ImageVector = when (step.kind) {
    is StepKind.Lesson -> Icons.Filled.MenuBook
    is StepKind.Practice -> Icons.Filled.Extension
    is StepKind.Test -> Icons.Filled.Verified
    is StepKind.Game -> Icons.Filled.Computer
}

/**
 * Dugme „Nastavi" (spec § 8): „Nastavi: Vežba · 5" u jednom redu, poglavlje
 * ispod kao `Type.caption`; `onAccent` na `accent` (8,53 / 6,71), `Radius.m`.
 * Jedina primarna akcija na ekranu — nikad `Color.White` (vidi `DS.onAccent`).
 * `runnable == false` je rezerva za korak bez rute: izgleda neaktivno (`fill`).
 */
@Composable
private fun ContinueCard(
    stepTitle: String,
    chapterTitle: String?,
    icon: ImageVector,
    accessibility: String,
    runnable: Boolean,
    onClick: () -> Unit
) {
    val ink = if (runnable) DS.onAccent else DS.inkMuted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.Radius.m))
            .background(if (runnable) DS.accent else DS.fill)
            .then(if (runnable) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = accessibility }
            .padding(DS.Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(StepKindTileSize))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
            Text(
                text = if (runnable) locF("Nastavi: %s", stepTitle) else "${loc("Uskoro")} · $stepTitle",
                color = if (runnable) DS.onAccent else DS.ink,
                style = Type.heading,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (chapterTitle != null) {
                Text(text = chapterTitle, color = ink, style = Type.caption)
            }
        }
        if (runnable) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DS.onAccent
            )
        }
    }
}

@Composable
private fun PathCompleteCard() {
    Row(
        modifier = Modifier.fillMaxWidth().dsCard(DS.Radius.l).padding(DS.Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = DS.success)
        Text(text = loc("Ceo put je pređen!"), color = DS.ink, style = Type.heading)
    }
}

/**
 * Niz dana i dnevni cilj (spec § 8): „1 dan zaredom / 2 dana / 5 dana" i
 * „Danas: 1 / 1 korak" sa trakom — oba kroz mnozine (`locPlural`). Isto kao
 * iOS `streakCard`. Broj niza je veliki i odvojen od oznake (bazna linija).
 */
@Composable
private fun StreakAndGoalCard(streak: Int, goal: PathProgress.DailyGoal, goalMet: Boolean) {
    val goalText = if (goal.countsPuzzles)
        locPlural("Danas: %d / %d zadatak", goal.goal, goal.done, goal.goal)
    else
        locPlural("Danas: %d / %d korak", goal.goal, goal.done, goal.goal)
    Column(
        modifier = Modifier.fillMaxWidth().dsCard(DS.Radius.l).padding(DS.Space.l),
        verticalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        // Poravnanje ide kroz `alignByBaseline()` na oba deteta, ne kroz
        // `verticalAlignment` na redu -- kad su oba prisutna, bazna linija pobedjuje.
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
        ) {
            Text(
                text = locInteger(streak),
                color = DS.ink,
                style = Type.title,
                modifier = Modifier.alignByBaseline()
            )
            Text(
                text = locPlural("dana zaredom", streak),
                color = DS.inkMuted,
                style = Type.body,
                modifier = Modifier.alignByBaseline()
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(DS.Space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = goalText, color = DS.ink, style = Type.body, modifier = Modifier.weight(1f))
                if (goalMet) {
                    // Stoji sam (bez teksta istog znacenja) -- zato ima opis.
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = loc("Cilj za danas je ispunjen"),
                        tint = DS.success,
                        modifier = Modifier.size(StepStatusIconSize)
                    )
                }
            }
            PathProgressBar(goal.fraction, if (goalMet) DS.success else DS.accent)
        }
    }
}

/** Traka 4 dp, `tint` na `fill` (spec § 8: `accent` na `fill` 7,08 / 5,69). */
@Composable
private fun PathProgressBar(fraction: Float, tint: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PathProgressBarHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(DS.fill)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(percent = 50))
                .background(tint)
        )
    }
}

private val PathProgressBarHeight = 4.dp

/**
 * P3 (spec § 8): poglavlje u kom je sve zakljucano je jedan red — naslov ·
 * „3 koraka" · katanac. Isto kao iOS `lockedChapter`.
 */
@Composable
private fun LockedChapterRow(title: String, stepCount: Int) {
    val count = locPlural("%d koraka", stepCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dsCard(DS.Radius.l)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $count, ${loc("Zaključano")}" }
            .padding(DS.Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
    ) {
        Text(text = title, color = DS.ink, style = Type.heading)
        Text(text = "· $count", color = DS.inkMuted, style = Type.caption, modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = DS.inkMuted)
    }
}

/**
 * Jedno poglavlje: naslov + „1/3" + traka 4 dp + spisak koraka (spec § 8).
 *
 * NAMERNO bez rednog broja po koraku unutar poglavlja: `notation-lesson` je
 * globalno cetvrti korak (`curriculum.allStepIds`), ali u svom poglavlju
 * prvi -- ista kartica bi nosila dva razlicita broja istovremeno. Redni broj
 * postoji SAMO u opisu karte „Nastavi" za citac ekrana, kao na iOS-u.
 */
@Composable
private fun ChapterSection(
    title: String,
    progress: Pair<Int, Int>,
    steps: List<CurriculumStep>,
    states: Map<String, StepState>,
    lessonRepo: LessonRepository,
    fileLang: String,
    onOpen: (CurriculumStep) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().dsCard(DS.Radius.l).padding(bottom = DS.Space.s)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DS.Space.l, end = DS.Space.l, top = DS.Space.l, bottom = DS.Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = DS.ink, style = Type.heading, modifier = Modifier.weight(1f))
            Text(text = "${progress.first}/${progress.second}", color = DS.inkMuted, style = Type.caption)
        }
        Box(modifier = Modifier.padding(start = DS.Space.l, end = DS.Space.l, bottom = DS.Space.s)) {
            PathProgressBar(
                if (progress.second == 0) 0f else progress.first.toFloat() / progress.second,
                DS.accent
            )
        }

        steps.forEachIndexed { index, step ->
            val state = states[step.id] ?: StepState.LOCKED
            if (index > 0) {
                // Razdelnik se ne crta uz istaknut (trenutni) red — `fill` ploca je sama granica.
                val prev = states[steps[index - 1].id] ?: StepState.LOCKED
                val nearCurrent = state == StepState.AVAILABLE || prev == StepState.AVAILABLE
                Box(
                    modifier = Modifier
                        .padding(start = DS.Space.l + StepKindTileSize + DS.Space.m)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(if (nearCurrent) Color.Transparent else DS.line)
                )
            }
            val hasRoute = routeFor(step) != null
            StepRow(
                state = state,
                hasRoute = hasRoute,
                title = stepCardTitle(step, lessonRepo, fileLang),
                kindIcon = stepKindIcon(step),
                onClick = { if (state != StepState.LOCKED && hasRoute) onOpen(step) }
            )
        }
    }
}

/**
 * Naslov kartice koraka. Lekcija dobija SVOJ naslov iz JSON-a (isti
 * `LessonRepository.lesson(...)` obrazac kao `LessonDetailView`) umesto golog
 * „Lekcija" za svih sest lekcija u sest poglavlja; vezba/test nose broj
 * zadataka istim kljucevima koje vec koristi `StepPracticeView`. Naslov
 * lekcije stize vec preveden iz JSON-a i NE ide kroz `loc()`.
 */
private fun stepCardTitle(step: CurriculumStep, lessonRepo: LessonRepository, fileLang: String): String =
    when (val k = step.kind) {
        is StepKind.Lesson -> lessonRepo.lesson(k.lessonId, fileLang)?.title ?: loc("Lekcija")
        is StepKind.Practice -> locF("Vežba · %d", k.count)
        is StepKind.Test -> locF("Test · %d", k.count)
        is StepKind.Game -> loc("Partija")
    }

/**
 * Red koraka (spec § 8, iOS raspored): plocica sa ikonom vrste levo, naslov
 * (`ink`; zakljucan `inkMuted`), STATUS ikonom 24 desno — zavrseno
 * `check_circle` u `success`, trenutni `play_circle` u `accent`, zakljucano
 * `lock` u `inkMuted`. Trenutni korak je istaknut `fill` plocom, `Radius.m`.
 *
 * Klik SAMO ako korak nije zakljucan I ima rutu — vidi doc iznad `routeFor`.
 * Sva cetiri tipa koraka imaju rutu; `hasRoute` je jedino mesto koje bi
 * uhvatilo buduci tip koraka bez ekrana, umesto da tiho vodi u prazno.
 */
@Composable
private fun StepRow(
    state: StepState,
    hasRoute: Boolean,
    title: String,
    kindIcon: ImageVector,
    onClick: () -> Unit
) {
    val clickable = state != StepState.LOCKED && hasRoute
    val isCurrent = state == StepState.AVAILABLE
    val stateLabel = when (state) {
        StepState.LOCKED -> loc("Zaključano")
        StepState.COMPLETED -> loc("Završeno")
        StepState.AVAILABLE -> if (hasRoute) loc("Dostupno") else loc("Uskoro")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DS.Space.s)
            .clip(RoundedCornerShape(DS.Radius.m))
            .background(if (isCurrent) DS.fill else Color.Transparent)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            // Ikona stanja nema sopstveni opis (dekorativna uz opis reda) —
            // stanje se izgovara eksplicitno, kao iOS `accessibilityValue`.
            .semantics(mergeDescendants = true) { stateDescription = stateLabel }
            .padding(horizontal = DS.Space.s, vertical = DS.Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        Box(
            modifier = Modifier
                .size(StepKindTileSize)
                .clip(RoundedCornerShape(DS.Radius.s))
                .background(if (state == StepState.LOCKED) DS.fill else DS.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = kindIcon,
                contentDescription = null,
                tint = if (state == StepState.LOCKED) DS.inkMuted else DS.accent,
                modifier = Modifier.size(StepKindIconSize)
            )
        }
        Text(
            text = title,
            color = if (state == StepState.LOCKED) DS.inkMuted else DS.ink,
            style = Type.body,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        val (statusIcon, statusTint) = when (state) {
            StepState.COMPLETED -> Icons.Filled.CheckCircle to DS.success
            StepState.AVAILABLE -> Icons.Filled.PlayCircle to (if (hasRoute) DS.accent else DS.inkMuted)
            StepState.LOCKED -> Icons.Filled.Lock to DS.inkMuted
        }
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusTint,
            modifier = Modifier.size(StepStatusIconSize)
        )
    }
}

/** Ikona vrste unutar plocice od 28 — iOS `.dsCaption` (~12–14 pt). */
private val StepKindIconSize = 16.dp
