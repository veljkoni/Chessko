package com.veljkoni.chessko.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.logic.LessonRepository
import com.veljkoni.chessko.logic.ProgressStore
import com.veljkoni.chessko.logic.StepState
import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF
import com.veljkoni.chessko.models.CurriculumStep
import com.veljkoni.chessko.models.StepKind
import com.veljkoni.chessko.models.loadCurriculum

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

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Nastavak: prvi korak koji nije zavrsen.
        val next = curriculum.allStepIds.firstOrNull { states[it] == StepState.AVAILABLE }
        val nextIndex = next?.let { curriculum.allStepIds.indexOf(it) + 1 }
        PathHeader(
            streak = store.currentStreak,
            goalMet = store.goalMetToday,
            continueLabel = nextIndex?.let { locF("Korak %d", it) },
            onContinue = { next?.let { id -> curriculum.step(id)?.let { route = routeFor(it) } } }
        )

        for (chapter in curriculum.chapters) {
            val done = chapter.steps.count { states[it.id] == StepState.COMPLETED }
            ChapterSection(
                title = chapter.title[lang] ?: chapter.title["en"] ?: chapter.id,
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

/**
 * Zaglavlje ekrana: streak, dnevni cilj i kartica „Nastavi" ka prvom
 * nezavrsenom koraku sa rutom. Spec 5.1: pocetni ekran Puta NE prikazuje
 * spisak lekcija nego NASTAVAK.
 *
 * `continueLabel == null` znaci da nema sledeceg koraka koji se moze
 * otvoriti (ceo Put predjen, ili je sledeci korak jos bez rute) — kartica
 * "Nastavi" se tad ne prikazuje.
 */
@Composable
private fun PathHeader(
    streak: Int,
    goalMet: Boolean,
    continueLabel: String?,
    onContinue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2138)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = locF("Niz: %d dana", streak),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (goalMet) loc("Cilj za danas je ispunjen") else loc("Cilj za danas nije ispunjen"),
                color = if (goalMet) Color(0xFF34D399) else Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )

            if (continueLabel != null) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = loc("Nastavi"), fontWeight = FontWeight.Bold)
                        Text(text = continueLabel, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Jedno poglavlje: naslov + napredak (X/N) + spisak koraka. Svaki red
 * prikazuje tip koraka i stanje; strelica postoji SAMO ako je korak
 * dostupan/zavrsen I ima rutu (`routeFor(step) != null`).
 *
 * NAMERNO bez rednog broja po koraku unutar poglavlja: `notation-lesson` je
 * globalno cetvrti korak (`curriculum.allStepIds`), ali u svom poglavlju
 * prvi -- ista kartica bi cim korisnik predje granicu poglavlja nosila dva
 * razlicita broja istovremeno (jedan ovde, drugi na kartici "Nastavi"). Redni
 * broj postoji SAMO na kartici "Nastavi" (`PathHeader`, globalni indeks iz
 * `curriculum.allStepIds`) -- isto mesto gde ga iOS prikazuje
 * (`Chessko/Views/PathView.swift:258`, jedino mesto sa "Korak %lld" na celoj
 * platformi) i tacno gde ga spec 5.1 trazi.
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${progress.first}/${progress.second}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }

            for (step in steps) {
                val state = states[step.id] ?: StepState.LOCKED
                val hasRoute = routeFor(step) != null
                StepRow(
                    state = state,
                    hasRoute = hasRoute,
                    typeLabel = stepCardTitle(step, lessonRepo, fileLang),
                    onClick = { if (state != StepState.LOCKED && hasRoute) onOpen(step) }
                )
            }
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

@Composable
private fun StepRow(
    state: StepState,
    hasRoute: Boolean,
    typeLabel: String,
    onClick: () -> Unit
) {
    // Strelica SAMO ako korak nije zakljucan I ima rutu — vidi doc iznad
    // `routeFor`. Od Task-a 7 sva cetiri tipa koraka imaju rutu; provera
    // ostaje jer je `hasRoute` jedino mesto koje bi uhvatilo buduci tip
    // koraka bez ekrana, umesto da tiho vodi u prazno.
    val clickable = state != StepState.LOCKED && hasRoute

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = typeLabel,
                color = if (state == StepState.LOCKED) Color.White.copy(alpha = 0.35f) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when (state) {
                    StepState.LOCKED -> loc("Zaključano")
                    StepState.COMPLETED -> loc("Završeno")
                    StepState.AVAILABLE -> if (hasRoute) loc("Dostupno") else loc("Uskoro")
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        if (clickable) {
            Text(text = "›", color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp)
        }
    }
}
