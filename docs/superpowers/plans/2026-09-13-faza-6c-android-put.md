# Faza 6c — Android: Put (kurikulum i napredak)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Treći tab Android aplikacije postaje **Put** umesto Učenja — kurikulum iz deljenog `curriculum.json`, napredak u JSON fajlu koji preživljava gašenje, i sva četiri tipa koraka (`lesson`, `practice`, `game`, `test`).

**Architecture:** Prenos iOS Faze 4a na Kotlin/Compose. `curriculum.json` se deli bajt-identično, kao već `puzzles.sqlite` (6a) i 36 lekcijskih JSON-a (6b). Rizična logika (otključavanje, dnevni cilj, streak) izdvaja se u `PathProgress` — čiste funkcije bez ijednog `android.*` i bez `org.json`, pa je **JVM-testabilna**; perzistencija i parsiranje idu u `androidTest`, jer Android JVM testovi nose *stub* `org.json`-a koji baca (utvrđeno u Fazi 6b). `StatsManager` postaje fasada nad novim `ProgressStore`-om i zadržava svaki postojeći potpis, pa se njegova pozivna mesta ne diraju.

**Tech Stack:** Kotlin, Jetpack Compose, `org.json` (ugrađen u Android), `java.time`, `android.database.sqlite`. **Nijedna nova Gradle zavisnost.**

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` (sekcije 5.1 Put, 5.4 Napredak, Faza 4, Faza 6)

---

## Global Constraints

Ova sekcija važi za **svaki** task; zahtevi svakog taska je implicitno uključuju.

- **`create_xcode_project.py` se NIKAD ne pokreće.** Regeneriše `project.pbxproj` iz nule, ne zna za `chesskit-engine` SPM zavisnost, i obara Stockfish.
- **`Chessko/` se NE dira.** Ovo je Android faza. Jedini dozvoljen dodir iOS stabla je **čitanje** radi poređenja. Na kraju faze `git diff --stat <base>..HEAD -- Chessko Chessko.xcodeproj` mora biti **prazan**.
- **Nijedna nova Gradle zavisnost.** Posebno: **nema `androidx.navigation`** — projekat je nema i neće je dobiti. Ekrani koraka se guraju kao **obično Compose stanje** (`var route by remember { mutableStateOf<StepRoute?>(null) }`), ne kroz `NavHost`.
- **`curriculum.json` je bajt-identičan sa iOS-om.** Dokaz je `diff Chessko/Content/curriculum.json ChesskoAndroid/app/src/main/assets/curriculum.json` — prazan izlaz.
- **UI tekst je na srpskom i ide kroz `loc()`.** Sadržaj koji stiže iz JSON-a (naslovi poglavlja, tekst lekcija) **NE** ide kroz `loc()` — on je već preveden.
- **Svaki `loc()` poziv mora imati ključ u rečniku.** Čuva ga `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`; ključeve pisati kao **literale unutar `loc()`**, nikad sklopljene iz promenljive — sklopljen ključ je testu nevidljiv.
- **Polazno stanje testova: 24 JVM + 26 instrumentisanih.** Svaki task navodi svoj novi zbir.
- **`connectedDebugAndroidTest` ume da kaže `BUILD SUCCESSFUL` a da ne pokrene nijedan test.** Rezultat se čita iz `app/build/outputs/androidTest-results/connected/debug/*.xml`, **ne** iz izlaznog koda. Isto i za `testDebugUnitTest` → `app/build/test-results/testDebugUnitTest/*.xml`.
- **Emulator — obavezan recept i životni vek:**
  ```bash
  export ANDROID_HOME=~/Library/Android/sdk
  nice -n 10 $ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 \
    -no-window -no-audio -no-boot-anim -gpu host -cores 2 -memory 2048 &
  $ANDROID_HOME/platform-tools/adb wait-for-device
  $ANDROID_HOME/platform-tools/adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
  ```
  **`-gpu host`, NIKAD `-gpu off`** (izmereno: `-gpu off` daje 745% prema 216%, jer procesor softverski rasterizuje 2,6 miliona piksela po kadru). Emulator se diže **samo za korak koji ga traži**, vizuelne provere se grupišu u **jedan prolaz**, i odmah po njemu ide `adb emu kill` + `./gradlew --stop` + provera `pgrep -f qemu-system`. Nikad Android Studio, nikad prozor.
- **Skrolovanje u Compose se ne postiže sa `adb shell input swipe`** — ne šalje dovoljno međudogađaja. Koristiti `input motionevent DOWN` → 12× `MOVE` → `UP`.
- **Novi izvorni fajl mora da se dokaže u build-u**: ubaciti sintaksnu grešku i videti da `assembleDebug` padne. Fajl koji se ne kompajlira u aplikaciju prošao je i `swift test` i `xcodebuild` u Fazi 2, a aplikacija ga nikad nije videla.
- **Svaki nov test mora da se dokaže mutacijom** — izmeniti kod tako da test padne, pa vratiti. Test koji ne može da padne nije test.

---

## File Structure

| Fajl | Odgovornost |
|---|---|
| `app/src/main/assets/curriculum.json` | **novo** — bajt-identična kopija iOS kurikuluma |
| `.../models/Curriculum.kt` | **novo** — `Curriculum`/`Chapter`/`CurriculumStep`/`StepKind` + `org.json` parser koji BACA na nepoznat tip |
| `.../logic/PathProgress.kt` | **novo** — čiste funkcije: otključavanje, dnevni cilj, streak. Bez `android.*`, bez `org.json` → JVM-testabilno |
| `.../logic/ProgressStore.kt` | **novo** — `ProgressSnapshot` + skladište u `filesDir/progress.json`, migracija iz `SharedPreferences` |
| `.../logic/StatsManager.kt` | izmena — postaje fasada nad `ProgressStore`-om, svi potpisi ostaju |
| `.../logic/PuzzleRepository.kt` | izmena — `+ puzzlesForStep(...)`, `+ stepRatingWindow(...)` |
| `.../viewmodels/PuzzleViewModel.kt` | izmena — režim koraka: red zadataka, napredovanje, pad testa |
| `.../viewmodels/GameViewModel.kt` | izmena — parametrizovan ključ za čuvanje partije |
| `.../ui/LessonDetailView.kt` | **novo** — detalj lekcije izdvojen iz `LearnView.kt` da ga Put može otvoriti |
| `.../ui/PromotionOverlay.kt` | **novo** — birač figure pri promociji (**popravlja živ bug**, vidi Task 7) |
| `.../ui/PathView.kt` | **novo** — ekran Puta: nastavak, streak, poglavlja, koraci, rutiranje |
| `.../ui/StepPracticeView.kt` | **novo** — koraci `practice` i `test` |
| `.../ui/StepGameView.kt` | **novo** — korak `game` |
| `.../MainActivity.kt` | izmena — tab 2 „Učenje" → „Put" |
| `.../logic/Loc.kt` | izmena — novi UI ključevi × 8 jezika |

---

## Zamke prenosa — pročitati pre Task-a 1

Ovo su mesta gde se Kotlin **ponaša drugačije od Swift-a**, pa doslovan prevod daje tihu grešku:

1. **`IntRange` sa donjom granicom većom od gornje NE puca.** Swift `ClosedRange` ruši proces pri kreiranju, i iOS se na to oslanja kao na proveru. Kotlin `6oo..1` je samo **prazan opseg** — korak bi tiho ostao bez ijednog zadatka. Zato provera `lo <= hi` u parseru mora biti **eksplicitna**, i mora postojati test koji dokazuje da baca.
2. **`org.json` `opt*` metode već rade ono što je iOS morao ručno.** iOS je morao da napiše dekoder sa `decodeIfPresent` jer sintetisani `Decodable` baca `keyNotFound` i time briše napredak svakog korisnika pri prvom dodatom polju. `JSONObject.optInt(key, default)` to radi sam. **Ne zameniti ga sa `getInt`** — `getInt` baca, i vraća tačno onaj bug od kog se iOS branio.
3. **`PuzzleViewModel` je `AndroidViewModel`.** Uzet preko `viewModel()` biva **deljen** — ulazak u korak bi pregazio zadatak dana. Ekran koraka mora da napravi **sopstvenu instancu** (`remember { PuzzleViewModel(app) }`).
4. **`GameViewModel` čuva partiju pod zakucanim `"chessko_save"`/`"saved_game"`.** Drugi primerak bi učitao korisnikovu partiju sa taba Igra i prvim potezom je pregazio — model snima celu partiju posle SVAKOG poteza.
5. **Nema `NavHost`-a.** Rutiranje je obično stanje. iOS je ovde imao bug koji je koštao ceo task (`@ViewBuilder` funkcija sa opcionim povratkom nikad ne vraća `nil`) — Kotlin `when` nema taj problem, ali odluka o ruti mora da bude **na jednom mestu** (`fun routeFor(step): StepRoute?`), ne razmazana po UI-ju.
6. **`LearnView.kt` drži listu i detalj u ISTOM composable-u**, prebacuje se na `activeLessonId`. Put mora da otvori detalj bez liste, pa se detalj izdvaja.

---

## Task 1: Kurikulum — asset i model

**Files:**
- Create: `ChesskoAndroid/app/src/main/assets/curriculum.json`
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/models/Curriculum.kt`
- Test: `ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/CurriculumTest.kt`

**Interfaces:**
- Consumes: `PuzzleRepository(context)` — `count()`, `allPuzzlesOrderedById()`; `LessonRepository(context)` — `discoveredLessonIds()`
- Produces:
  - `data class Curriculum(val version: Int, val chapters: List<Chapter>)` sa `val allStepIds: List<String>` i `fun step(id: String): CurriculumStep?`
  - `data class Chapter(val id: String, val title: Map<String, String>, val steps: List<CurriculumStep>)`
  - `data class CurriculumStep(val id: String, val kind: StepKind)`
  - `sealed class StepKind` sa `Lesson(lessonId)`, `Practice(themes, count, ratingRange)`, `Game(difficulty, startFEN)`, `Test(themes, count, ratingRange)`
  - `object CurriculumParser { val KNOWN_DIFFICULTIES: Set<String>; fun parse(json: String): Curriculum }`
  - `fun loadCurriculum(context: Context): Curriculum` — čita iz assets

- [ ] **Step 1: Kopiraj kurikulum i dokaži da je identičan**

```bash
cd /Users/veljkoodobasic/Documents/Projects/Chess
cp Chessko/Content/curriculum.json ChesskoAndroid/app/src/main/assets/curriculum.json
diff Chessko/Content/curriculum.json ChesskoAndroid/app/src/main/assets/curriculum.json && echo "IDENTICNO"
```
Očekivano: `IDENTICNO`, bez ijedne linije razlike.

- [ ] **Step 2: Napiši test koji pada**

`app/src/androidTest/java/com/veljkoni/chessko/CurriculumTest.kt`:

```kotlin
package com.veljkoni.chessko

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veljkoni.chessko.logic.LessonRepository
import com.veljkoni.chessko.logic.PuzzleRepository
import com.veljkoni.chessko.models.CurriculumParser
import com.veljkoni.chessko.models.StepKind
import com.veljkoni.chessko.models.loadCurriculum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurriculumTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedCurriculumParsesWithExpectedShape() {
        val c = loadCurriculum(context)
        assertEquals(1, c.version)
        assertEquals(6, c.chapters.size)
        assertEquals(17, c.allStepIds.size)
        assertEquals(
            listOf("basics", "notation", "opening", "tactics", "middlegame", "endgame"),
            c.chapters.map { it.id }
        )
    }

    /**
     * Ne proverava samo TIP bloka nego i SADRZAJ polja. Test koji tvrdi samo
     * "ovo je Practice" prolazi i kad parser zameni `themes` i `count`.
     */
    @Test
    fun stepFieldsCarryTheirOwnValues() {
        val c = loadCurriculum(context)
        val practice = c.step("tactics-practice")
        assertNotNull(practice)
        val kind = practice!!.kind
        assertTrue(kind is StepKind.Practice)
        kind as StepKind.Practice
        assertEquals(listOf("fork", "pin", "skewer"), kind.themes)
        assertEquals(5, kind.count)
        assertEquals(600..1200, kind.ratingRange)

        val lesson = c.step("basics-lesson")!!.kind
        assertTrue(lesson is StepKind.Lesson)
        assertEquals("board-and-pieces", (lesson as StepKind.Lesson).lessonId)

        val game = c.step("basics-game")!!.kind
        assertTrue(game is StepKind.Game)
        assertEquals("beginner", (game as StepKind.Game).difficulty)
        assertEquals(null, game.startFEN)
    }

    @Test
    fun unknownStepTypeThrows() {
        val json = """{"version":1,"chapters":[{"id":"c","title":{"sr":"C"},
            "steps":[{"id":"s","type":"teleport"}]}]}"""
        try {
            CurriculumParser.parse(json)
            fail("Nepoznat tip koraka mora da baci, ne da se tiho preskoci")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("teleport"))
        }
    }

    @Test
    fun unknownDifficultyThrows() {
        val json = """{"version":1,"chapters":[{"id":"c","title":{"sr":"C"},
            "steps":[{"id":"s","type":"game","difficulty":"begginer"}]}]}"""
        try {
            CurriculumParser.parse(json)
            fail("Tipfeler u tezini mora da baci — inace korak zauvek stoji kao neaktivan")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("begginer"))
        }
    }

    /**
     * Kotlin `IntRange` sa donjom granicom vecom od gornje NE puca kao Swift
     * `ClosedRange` — samo je prazan. Bez ove provere korak bi tiho ostao bez
     * ijednog zadatka i ne bi se mogao zavrsiti.
     */
    @Test
    fun invertedRatingRangeThrows() {
        val json = """{"version":1,"chapters":[{"id":"c","title":{"sr":"C"},
            "steps":[{"id":"s","type":"practice","themes":["fork"],"count":3,
            "ratingRange":[1200,600]}]}]}"""
        try {
            CurriculumParser.parse(json)
            fail("Obrnut ratingRange mora da baci")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("ratingRange"))
        }
    }

    /**
     * Kurikulum ne sme da laze: svaka lekcija koju pominje mora da postoji kao
     * fajl, i svaka tema mora da ima dovoljno zadataka u trazenom opsegu.
     * Bez ovoga tipfeler daje korak koji se NIKAD ne moze zavrsiti, i to bez
     * ijedne poruke na ekranu.
     */
    @Test
    fun curriculumDoesNotLieAboutLessonsOrPuzzles() {
        val c = loadCurriculum(context)
        val lessons = LessonRepository(context).discoveredLessonIds().toSet()
        val all = PuzzleRepository(context).allPuzzlesOrderedById()

        for (chapter in c.chapters) {
            for (step in chapter.steps) {
                when (val k = step.kind) {
                    is StepKind.Lesson ->
                        assertTrue("Korak ${step.id} trazi lekciju '${k.lessonId}' koje nema",
                            lessons.contains(k.lessonId))
                    is StepKind.Practice -> assertEnough(all, step.id, k.themes, k.count, k.ratingRange)
                    is StepKind.Test -> assertEnough(all, step.id, k.themes, k.count, k.ratingRange)
                    is StepKind.Game -> Unit
                }
            }
        }
    }

    private fun assertEnough(
        all: List<com.veljkoni.chessko.models.ChessPuzzle>,
        stepId: String, themes: List<String>, count: Int, range: IntRange
    ) {
        // `ChessPuzzle.themes` je STRING razdvojen razmacima, ne lista —
        // `p.themes.any { }` bi iteriralo po znakovima i ne bi se ni kompajliralo.
        val n = all.count { p -> p.rating in range && p.themes.split(" ").any { it in themes } }
        assertTrue("Korak $stepId trazi $count zadataka za teme $themes u $range, a ima ih $n",
            n >= count)
    }
}
```

- [ ] **Step 3: Pokreni test i vidi da pada**

```bash
cd ChesskoAndroid && ./gradlew compileDebugAndroidTestKotlin
```
Očekivano: FAIL — `Unresolved reference: CurriculumParser` / `loadCurriculum`.

- [ ] **Step 4: Napiši model i parser**

`app/src/main/java/com/veljkoni/chessko/models/Curriculum.kt`:

```kotlin
package com.veljkoni.chessko.models

import android.content.Context
import org.json.JSONObject

// MARK: - Kurikulum (Put)
//
// Okosnica v2: niz koraka koje igrac prolazi redom. Zivi u
// `assets/curriculum.json`, van koda, i BAJT JE IDENTICAN iOS kopiji u
// `Chessko/Content/curriculum.json` — kurikulum se menja na jednom mestu.
//
// Kao i kod `LessonBlock`-a, nepoznat `type` koraka BACA umesto da se tiho
// preskoci: preskocen korak bi napravio rupu u putu koju niko ne vidi.

sealed class StepKind {
    /** Teorija; zavrsava se kad korisnik dodje do kraja i potvrdi. */
    data class Lesson(val lessonId: String) : StepKind()

    /** N zadataka filtriranih po temi; zavrsava se kad su svi reseni. */
    data class Practice(
        val themes: List<String>, val count: Int, val ratingRange: IntRange
    ) : StepKind()

    /** Partija protiv racunara; zavrsava se kad partija dodje do kraja. */
    data class Game(val difficulty: String, val startFEN: String?) : StepKind()

    /** Kao `Practice`, ali se zavrsava SAMO bez ijedne greske — zakljucava poglavlje. */
    data class Test(
        val themes: List<String>, val count: Int, val ratingRange: IntRange
    ) : StepKind()
}

data class CurriculumStep(val id: String, val kind: StepKind)

data class Chapter(
    val id: String,
    /**
     * Naslov po jeziku. Kurikulum je mali, pa naslovi stoje ovde umesto u
     * odvojenim fajlovima po jeziku kao kod lekcija. NE ide kroz `loc()` —
     * stize vec preveden.
     */
    val title: Map<String, String>,
    val steps: List<CurriculumStep>
)

data class Curriculum(val version: Int, val chapters: List<Chapter>) {
    /** Redosled svih koraka kroz sva poglavlja — osnova za otkljucavanje. */
    val allStepIds: List<String> get() = chapters.flatMap { c -> c.steps.map { it.id } }

    fun step(id: String): CurriculumStep? =
        chapters.firstNotNullOfOrNull { c -> c.steps.find { it.id == id } }
}

object CurriculumParser {

    /**
     * Sirove vrednosti `GameDifficulty`-ja. Stoje kao literali, a ne kao
     * referenca na sam enum, jer ovaj fajl mora da ostane bez `ui`/`logic`
     * zavisnosti.
     *
     * Postoji zato sto bi tipfeler ("begginer") inace prosao svaku proveru i
     * isplivao tek kao korak koji se ne moze odigrati.
     */
    val KNOWN_DIFFICULTIES: Set<String> =
        setOf("beginner", "easy", "medium", "hard", "stockfish")

    fun parse(json: String): Curriculum {
        val root = JSONObject(json)
        val chaptersJson = root.getJSONArray("chapters")
        val chapters = (0 until chaptersJson.length()).map { i ->
            val c = chaptersJson.getJSONObject(i)
            val titleJson = c.getJSONObject("title")
            val title = titleJson.keys().asSequence().associateWith { titleJson.getString(it) }
            val stepsJson = c.getJSONArray("steps")
            Chapter(
                id = c.getString("id"),
                title = title,
                steps = (0 until stepsJson.length()).map { j -> parseStep(stepsJson.getJSONObject(j)) }
            )
        }
        return Curriculum(version = root.optInt("version", 1), chapters = chapters)
    }

    private fun parseStep(o: JSONObject): CurriculumStep {
        val id = o.getString("id")
        val kind = when (val type = o.getString("type")) {
            "lesson" -> StepKind.Lesson(o.getString("lessonId"))
            "practice" -> StepKind.Practice(themes(o), o.getInt("count"), range(o))
            "test" -> StepKind.Test(themes(o), o.getInt("count"), range(o))
            "game" -> {
                val difficulty = o.getString("difficulty")
                require(difficulty in KNOWN_DIFFICULTIES) {
                    "Nepoznata tezina '$difficulty'. Dozvoljeno: " +
                        KNOWN_DIFFICULTIES.sorted().joinToString(", ") + "."
                }
                StepKind.Game(difficulty, if (o.has("startFEN")) o.getString("startFEN") else null)
            }
            else -> throw IllegalArgumentException(
                "Nepoznat tip koraka '$type'. Dodaj ga u StepKind ili ispravi JSON."
            )
        }
        return CurriculumStep(id, kind)
    }

    private fun themes(o: JSONObject): List<String> {
        val a = o.getJSONArray("themes")
        return (0 until a.length()).map { a.getString(it) }
    }

    /**
     * Kotlin `IntRange` sa `lo > hi` NE puca — samo je prazan, pa bi korak
     * tiho ostao bez ijednog zadatka. Swift `ClosedRange` bi ovde srusio
     * proces, i iOS se na to oslanja. Zato je provera ovde EKSPLICITNA.
     */
    private fun range(o: JSONObject): IntRange {
        val a = o.getJSONArray("ratingRange")
        require(a.length() == 2) { "ratingRange mora imati tacno dva broja" }
        val lo = a.getInt(0)
        val hi = a.getInt(1)
        require(lo <= hi) { "ratingRange mora biti [donja, gornja] sa donja <= gornja, dobijeno [$lo, $hi]" }
        return lo..hi
    }
}

/** Ucitava isporuceni kurikulum iz `assets/curriculum.json`. */
fun loadCurriculum(context: Context): Curriculum =
    CurriculumParser.parse(
        context.assets.open("curriculum.json").bufferedReader().use { it.readText() }
    )
```

- [ ] **Step 5: Dokaži da se novi fajl stvarno kompajlira u aplikaciju**

```bash
cd ChesskoAndroid
printf '\nfun __probe(): Int = "nije broj"\n' >> app/src/main/java/com/veljkoni/chessko/models/Curriculum.kt
./gradlew assembleDebug 2>&1 | tail -5    # MORA da padne
git checkout app/src/main/java/com/veljkoni/chessko/models/Curriculum.kt
```
Očekivano: prvi `assembleDebug` FAIL sa type-mismatch. Ako prođe, fajl nije u build-u.

- [ ] **Step 6: Podigni emulator, pokreni testove, pa ga ODMAH ugasi**

```bash
export ANDROID_HOME=~/Library/Android/sdk
nice -n 10 $ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 \
  -no-window -no-audio -no-boot-anim -gpu host -cores 2 -memory 2048 &
$ANDROID_HOME/platform-tools/adb wait-for-device
$ANDROID_HOME/platform-tools/adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
cd ChesskoAndroid && ./gradlew connectedDebugAndroidTest
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=0
for p in glob.glob("app/build/outputs/androidTest-results/connected/debug/*.xml"):
    r=ET.parse(p).getroot(); t+=int(r.get("tests")); f+=int(r.get("failures")); e+=int(r.get("errors"))
print(f"INSTRUMENTISANI: {t} testova, {f} padova, {e} gresaka")
PY
$ANDROID_HOME/platform-tools/adb emu kill; ./gradlew --stop; pgrep -f qemu-system || echo "qemu: nema"
```
Očekivano: **32 testa** (26 zatečenih + 6 novih), 0 padova. Emulator ugašen.

- [ ] **Step 7: Dokaži mutacijom da novi testovi mogu da padnu**

```bash
cd ChesskoAndroid
# zameni `themes` i `count` u Practice grani parsera pa vidi da test padne
sed -i '' 's/StepKind.Practice(themes(o), o.getInt("count"), range(o))/StepKind.Practice(themes(o), 99, range(o))/' \
  app/src/main/java/com/veljkoni/chessko/models/Curriculum.kt
# (emulator dici samo za ovaj prolaz, pa gasiti — vidi Step 6)
git checkout app/src/main/java/com/veljkoni/chessko/models/Curriculum.kt
```
Očekivano: `stepFieldsCarryTheirOwnValues` FAIL sa `expected:<5> but was:<99>`.

- [ ] **Step 8: Commit**

```bash
git add ChesskoAndroid/app/src/main/assets/curriculum.json \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/models/Curriculum.kt \
        ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/CurriculumTest.kt
git commit -F - <<'EOF'
Faza 6c, Task 1: kurikulum kao deljeni asset i model

curriculum.json je bajt-identican iOS kopiji (diff prazan), kao vec
puzzles.sqlite i 36 lekcijskih JSON-a. Parser ide kroz org.json i BACA na
nepoznat tip koraka, nepoznatu tezinu i obrnut ratingRange.

Obrnut opseg se proverava EKSPLICITNO jer se Kotlin ovde ponasa drugacije od
Swift-a: ClosedRange(1200...600) rusi proces pri kreiranju, a IntRange
1200..600 je samo prazan -- korak bi tiho ostao bez ijednog zadatka.

Test curriculumDoesNotLieAboutLessonsOrPuzzles tvrdi da svaka pomenuta
lekcija postoji kao fajl i da svaka tema ima dovoljno zadataka u opsegu.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 2: `PathProgress` — čista logika Puta

**Files:**
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/PathProgress.kt`
- Test: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/PathProgressTest.kt`

**Interfaces:**
- Consumes: ništa (namerno — ovaj fajl nema nijedan `android.*` ni `org.json` uvoz)
- Produces:
  - `enum class StepState { LOCKED, AVAILABLE, COMPLETED }`
  - `object PathProgress` sa:
    - `fun stepStates(stepIds: List<String>, completed: Set<String>): Map<String, StepState>`
    - `fun goalMet(steps: Int, puzzles: Int): Boolean`
    - `fun currentStreak(goalDays: Set<String>, today: String): Int`
    - `fun dayKey(date: LocalDate = LocalDate.now()): String`

- [ ] **Step 1: Napiši testove koji padaju**

`app/src/test/java/com/veljkoni/chessko/PathProgressTest.kt`:

```kotlin
package com.veljkoni.chessko

import com.veljkoni.chessko.logic.PathProgress
import com.veljkoni.chessko.logic.StepState
import org.junit.Assert.assertEquals
import org.junit.Test

class PathProgressTest {

    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun emptyProgressUnlocksOnlyTheFirstStep() {
        val s = PathProgress.stepStates(ids, emptySet())
        assertEquals(StepState.AVAILABLE, s["a"])
        assertEquals(StepState.LOCKED, s["b"])
        assertEquals(StepState.LOCKED, s["c"])
        assertEquals(StepState.LOCKED, s["d"])
    }

    @Test
    fun completingAStepUnlocksExactlyTheNextOne() {
        val s = PathProgress.stepStates(ids, setOf("a"))
        assertEquals(StepState.COMPLETED, s["a"])
        assertEquals(StepState.AVAILABLE, s["b"])
        assertEquals(StepState.LOCKED, s["c"])
    }

    /**
     * Dostupan je PRVI NEZAVRSEN u redosledu, ne "onaj posle poslednjeg
     * zavrsenog". Sa rupom (a i c zavrseni, b nije) dostupan mora biti b.
     */
    @Test
    fun aGapInProgressMakesTheGapAvailableNotTheStepAfterTheLastCompleted() {
        val s = PathProgress.stepStates(ids, setOf("a", "c"))
        assertEquals(StepState.COMPLETED, s["a"])
        assertEquals(StepState.AVAILABLE, s["b"])
        assertEquals(StepState.COMPLETED, s["c"])
        assertEquals(StepState.LOCKED, s["d"])
    }

    @Test
    fun goalIsOneStepOrThreePuzzles() {
        assertEquals(false, PathProgress.goalMet(steps = 0, puzzles = 0))
        assertEquals(false, PathProgress.goalMet(steps = 0, puzzles = 2))
        assertEquals(true, PathProgress.goalMet(steps = 0, puzzles = 3))
        assertEquals(true, PathProgress.goalMet(steps = 1, puzzles = 0))
    }

    @Test
    fun streakCountsConsecutiveDaysEndingToday() {
        val days = setOf("2026-09-11", "2026-09-12", "2026-09-13")
        assertEquals(3, PathProgress.currentStreak(days, today = "2026-09-13"))
    }

    /**
     * NAJVAZNIJI test ove faze. Ako cilj DANAS jos nije ispunjen, niz se broji
     * od juce — dan jos traje. Bez ovoga bi korisniku streak nestajao svako
     * jutro, pre nego sto uopste stigne da odigra nesto.
     */
    @Test
    fun streakSurvivesADayThatHasNotEndedYet() {
        val days = setOf("2026-09-11", "2026-09-12")
        assertEquals(2, PathProgress.currentStreak(days, today = "2026-09-13"))
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        val days = setOf("2026-09-10", "2026-09-11")
        assertEquals(0, PathProgress.currentStreak(days, today = "2026-09-13"))
    }

    @Test
    fun noHistoryMeansNoStreak() {
        assertEquals(0, PathProgress.currentStreak(emptySet(), today = "2026-09-13"))
    }

    /**
     * Dan ulazi kao string bas zato da test ne zavisi ni od vremenske zone ni
     * od trenutka pokretanja. Neispravan datum ne sme da srusi ekran Puta.
     */
    @Test
    fun malformedTodayYieldsZeroInsteadOfCrashing() {
        assertEquals(0, PathProgress.currentStreak(setOf("2026-09-13"), today = "juce"))
    }
}
```

- [ ] **Step 2: Pokreni i vidi da padaju**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest
```
Očekivano: FAIL — `Unresolved reference: PathProgress`.

- [ ] **Step 3: Napiši čistu logiku**

`app/src/main/java/com/veljkoni/chessko/logic/PathProgress.kt`:

```kotlin
package com.veljkoni.chessko.logic

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Stanje koraka na Putu. */
enum class StepState { LOCKED, AVAILABLE, COMPLETED }

// MARK: - Cista logika Puta
//
// Sve sto se moze pogresiti oko otkljucavanja, dnevnog cilja i streak-a stoji
// OVDE, kao ciste funkcije bez fajlova i bez sata. Dan ulazi kao string
// ("yyyy-MM-dd") da test ne zavisi od vremenske zone ni od trenutka pokretanja.
//
// Nijedan `android.*` ni `org.json` uvoz — zato se ovo testira na JVM-u, gde
// je povratna sprega u sekundama, a ne na emulatoru.

object PathProgress {

    /**
     * Korak je dostupan ako je PRVI nezavrsen u redosledu; svi posle su
     * zakljucani. Prazan skup zavrsenih znaci da je dostupan samo prvi.
     */
    fun stepStates(stepIds: List<String>, completed: Set<String>): Map<String, StepState> {
        val result = LinkedHashMap<String, StepState>(stepIds.size)
        var foundAvailable = false
        for (id in stepIds) {
            result[id] = when {
                completed.contains(id) -> StepState.COMPLETED
                !foundAvailable -> { foundAvailable = true; StepState.AVAILABLE }
                else -> StepState.LOCKED
            }
        }
        return result
    }

    /** Dnevni cilj: jedan zavrsen korak Puta ILI tri resena zadatka (spec 5.4). */
    fun goalMet(steps: Int, puzzles: Int): Boolean = steps >= 1 || puzzles >= 3

    /**
     * Dani zaredom sa ispunjenim ciljem, zakljucno sa danas.
     *
     * Ako cilj DANAS jos nije ispunjen, brojanje krece od juce — dan jos
     * traje, pa niz ne sme da se prekine. Prekida ga tek propusten dan.
     */
    fun currentStreak(goalDays: Set<String>, today: String): Int {
        var day = try {
            LocalDate.parse(today)
        } catch (e: DateTimeParseException) {
            return 0
        }
        if (!goalDays.contains(today)) day = day.minusDays(1)

        var count = 0
        while (goalDays.contains(day.toString())) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Kljuc dana u ISO obliku `yyyy-MM-dd`; `LocalDate.toString()` ga vec daje. */
    fun dayKey(date: LocalDate = LocalDate.now()): String = date.toString()
}
```

- [ ] **Step 4: Pokreni testove i pročitaj rezultat iz XML-a**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=0
for p in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    r=ET.parse(p).getroot(); t+=int(r.get("tests")); f+=int(r.get("failures")); e+=int(r.get("errors"))
print(f"JVM: {t} testova, {f} padova, {e} gresaka")
PY
```
Očekivano: **33 testa** (24 zatečena + 9 novih), 0 padova.

- [ ] **Step 5: Dokaži mutacijom da test streak-a stvarno brani**

```bash
cd ChesskoAndroid
sed -i '' 's/if (!goalDays.contains(today)) day = day.minusDays(1)/\/\/ mutacija/' \
  app/src/main/java/com/veljkoni/chessko/logic/PathProgress.kt
./gradlew testDebugUnitTest 2>&1 | grep -E "streakSurvives|tests completed"
git checkout app/src/main/java/com/veljkoni/chessko/logic/PathProgress.kt
```
Očekivano: `streakSurvivesADayThatHasNotEndedYet FAILED`, `expected:<2> but was:<0>`.

- [ ] **Step 6: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/PathProgress.kt \
        ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/PathProgressTest.kt
git commit -F - <<'EOF'
Faza 6c, Task 2: cista logika Puta (otkljucavanje, cilj, streak)

PathProgress nema nijedan android.* ni org.json uvoz, pa se cela rizicna
logika testira na JVM-u umesto na emulatoru. Dan ulazi kao string, pa test ne
zavisi ni od vremenske zone ni od trenutka pokretanja.

Kljucno svojstvo, dokazano mutacijom: streak PREZIVLJAVA dan koji jos traje.
Ako cilj danas nije ispunjen, broji se od juce -- inace bi korisniku niz
nestajao svako jutro. Uklanjanje te grane obara test (2 -> 0).

JVM testovi 24 -> 33.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 3: `ProgressStore` — snimak na disku i migracija

**Files:**
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/ProgressStore.kt`
- Test: `ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/ProgressStoreTest.kt`

**Interfaces:**
- Consumes: `PathProgress`, `StepState`, `Curriculum.allStepIds`, `PuzzleRating.START`
- Produces:
  - `data class ProgressSnapshot(...)` sa `fun toJson(): String` i `companion object { fun fromJson(s: String): ProgressSnapshot }`
  - `class ProgressStore(context: Context, file: File = default, prefs: SharedPreferences = default)` sa:
    - `val snapshot: ProgressSnapshot` (Compose-observable)
    - `fun stepStates(curriculum: Curriculum): Map<String, StepState>`
    - `fun completeStep(id: String)`
    - `fun recordPuzzleSolvedToday()`
    - `val goalMetToday: Boolean`
    - `val currentStreak: Int`
    - `fun updateStats(change: (ProgressSnapshot) -> ProgressSnapshot)`
    - `companion object { fun getInstance(context: Context): ProgressStore }`

- [ ] **Step 1: Napiši testove koji padaju**

`app/src/androidTest/java/com/veljkoni/chessko/ProgressStoreTest.kt`:

```kotlin
package com.veljkoni.chessko

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.ProgressSnapshot
import com.veljkoni.chessko.logic.ProgressStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProgressStoreTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var file: File

    @Before fun setUp() {
        dir = File(context.cacheDir, "progress-test-${System.nanoTime()}").apply { mkdirs() }
        file = File(dir, "progress.json")
    }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun prefs(name: String) =
        context.getSharedPreferences("test_$name-${System.nanoTime()}", Context.MODE_PRIVATE)

    @Test
    fun progressSurvivesANewInstance() {
        val p = prefs("survive")
        ProgressStore(context, file, p).completeStep("basics-lesson")
        val reopened = ProgressStore(context, file, p)
        assertTrue(reopened.snapshot.completedSteps.contains("basics-lesson"))
    }

    @Test
    fun completingTheSameStepTwiceCountsOnce() {
        val p = prefs("twice")
        val s = ProgressStore(context, file, p)
        s.completeStep("x"); s.completeStep("x")
        assertEquals(1, s.snapshot.completedSteps.size)
        assertEquals(1, s.snapshot.stepsCompletedByDay.values.sum())
    }

    /**
     * Prvo pokretanje posle nadogradnje preuzima statistiku iz SharedPreferences
     * i OSTAVLJA je tamo netaknutu — povratak na stariju verziju mora da radi.
     */
    @Test
    fun oldStatsAreMigratedAndLeftInPlace() {
        val p = prefs("migrate")
        p.edit().putInt("gamesPlayed", 7).putInt("gamesWon", 5)
            .putInt("puzzlesSolved", 42).putInt("puzzleRating", 1234)
            .putInt("winsHard", 3).apply()

        val s = ProgressStore(context, file, p)
        assertEquals(7, s.snapshot.gamesPlayed)
        assertEquals(5, s.snapshot.gamesWon)
        assertEquals(42, s.snapshot.puzzlesSolved)
        assertEquals(1234, s.snapshot.puzzleRating)
        assertEquals(3, s.snapshot.winsHard)

        assertEquals(7, p.getInt("gamesPlayed", -1))
        assertEquals(1234, p.getInt("puzzleRating", -1))
    }

    @Test
    fun aFirstRunWithNoOldStatsStartsRatingAt800() {
        assertEquals(800, ProgressStore(context, file, prefs("fresh")).snapshot.puzzleRating)
    }

    /**
     * Shema sme da raste. Stariji fajl bez novih kljuceva mora da se procita, a
     * nova polja da dobiju podrazumevanu vrednost. Sa `getInt` umesto `optInt`
     * ovo bi bacilo, fajl bi bio proglasen pokvarenim i CEO napredak bi nestao.
     */
    @Test
    fun anOlderFileMissingNewKeysStillLoads() {
        file.writeText("""{"version":1,"completedSteps":["basics-lesson"]}""")
        val s = ProgressStore(context, file, prefs("older"))
        assertTrue(s.snapshot.completedSteps.contains("basics-lesson"))
        assertEquals(800, s.snapshot.puzzleRating)
        assertEquals(0, s.snapshot.gamesPlayed)
    }

    /**
     * Pokvaren fajl se ODLAZE u stranu, ne gazi. To je korisnikov fajl na
     * uredjaju i sme da se osteti prekinutim upisom — aplikacija mora da se
     * oporavi, a delimicno ostecen napredak da ostane dostupan za rucno
     * spasavanje.
     */
    @Test
    fun aCorruptFileIsSetAsideNotOverwritten() {
        file.writeText("{ ovo nije json")
        ProgressStore(context, file, prefs("corrupt"))
        assertTrue(File(file.path + ".corrupt").exists())
        assertEquals("{ ovo nije json", File(file.path + ".corrupt").readText())
    }

    @Test
    fun goalAndStreakReadFromTheSnapshot() {
        val p = prefs("goal")
        val s = ProgressStore(context, file, p)
        assertFalse(s.goalMetToday)
        s.completeStep("basics-lesson")
        assertTrue(s.goalMetToday)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun threeSolvedPuzzlesMeetTheDailyGoal() {
        val s = ProgressStore(context, file, prefs("puzzles"))
        s.recordPuzzleSolvedToday(); s.recordPuzzleSolvedToday()
        assertFalse(s.goalMetToday)
        s.recordPuzzleSolvedToday()
        assertTrue(s.goalMetToday)
    }

    @Test
    fun snapshotRoundTripsThroughJson() {
        val a = ProgressSnapshot(
            completedSteps = setOf("a", "b"),
            stepCompletionDates = mapOf("a" to "2026-09-13"),
            stepsCompletedByDay = mapOf("2026-09-13" to 2),
            puzzlesSolvedByDay = mapOf("2026-09-13" to 5),
            gamesPlayed = 3, gamesWon = 2, gamesLost = 1, gamesDrawn = 0,
            currentWinStreak = 2, bestWinStreak = 4,
            winsBeginner = 1, winsEasy = 1, winsMedium = 0, winsHard = 0, winsStockfish = 0,
            puzzlesSolved = 9, currentPuzzleStreak = 3, bestPuzzleStreak = 6,
            puzzleRating = 912
        )
        assertEquals(a, ProgressSnapshot.fromJson(a.toJson()))
    }
}
```

- [ ] **Step 2: Pokreni i vidi da pada**

```bash
cd ChesskoAndroid && ./gradlew compileDebugAndroidTestKotlin
```
Očekivano: FAIL — `Unresolved reference: ProgressStore`.

- [ ] **Step 3: Napiši skladište**

`app/src/main/java/com/veljkoni/chessko/logic/ProgressStore.kt`:

```kotlin
package com.veljkoni.chessko.logic

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.veljkoni.chessko.models.Curriculum
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// MARK: - Snimak napretka na disku
//
// JSON fajl u `filesDir`, ne `SharedPreferences`: napredak je struktura, ne
// sacica skalara (spec 5.4). Stari `SharedPreferences` kljucevi se NAMERNO ne
// brisu posle migracije -- tako povratak na stariju verziju i dalje radi.

data class ProgressSnapshot(
    val version: Int = 1,

    // Put
    val completedSteps: Set<String> = emptySet(),
    val stepCompletionDates: Map<String, String> = emptyMap(),   // stepId -> "yyyy-MM-dd"

    // Dnevni cilj i streak
    val stepsCompletedByDay: Map<String, Int> = emptyMap(),
    val puzzlesSolvedByDay: Map<String, Int> = emptyMap(),

    // Preneto iz `SharedPreferences` pri prvom pokretanju
    val gamesPlayed: Int = 0, val gamesWon: Int = 0,
    val gamesLost: Int = 0, val gamesDrawn: Int = 0,
    val currentWinStreak: Int = 0, val bestWinStreak: Int = 0,
    // Pobede po tezini postoje SAMO na Androidu (iOS ProgressSnapshot ih nema).
    // Bez njih bi fasada `StatsManager` izgubila podatke koje danas prikazuje.
    val winsBeginner: Int = 0, val winsEasy: Int = 0, val winsMedium: Int = 0,
    val winsHard: Int = 0, val winsStockfish: Int = 0,
    val puzzlesSolved: Int = 0,
    val currentPuzzleStreak: Int = 0, val bestPuzzleStreak: Int = 0,
    val puzzleRating: Int = PuzzleRating.START
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("version", version)
        o.put("completedSteps", JSONArray(completedSteps.toList()))
        o.put("stepCompletionDates", JSONObject(stepCompletionDates as Map<*, *>))
        o.put("stepsCompletedByDay", JSONObject(stepsCompletedByDay as Map<*, *>))
        o.put("puzzlesSolvedByDay", JSONObject(puzzlesSolvedByDay as Map<*, *>))
        o.put("gamesPlayed", gamesPlayed); o.put("gamesWon", gamesWon)
        o.put("gamesLost", gamesLost); o.put("gamesDrawn", gamesDrawn)
        o.put("currentWinStreak", currentWinStreak); o.put("bestWinStreak", bestWinStreak)
        o.put("winsBeginner", winsBeginner); o.put("winsEasy", winsEasy)
        o.put("winsMedium", winsMedium); o.put("winsHard", winsHard)
        o.put("winsStockfish", winsStockfish)
        o.put("puzzlesSolved", puzzlesSolved)
        o.put("currentPuzzleStreak", currentPuzzleStreak)
        o.put("bestPuzzleStreak", bestPuzzleStreak)
        o.put("puzzleRating", puzzleRating)
        return o.toString()
    }

    companion object {
        /**
         * SVE se cita kroz `opt*`, nikad `get*`.
         *
         * `getInt` baca za kljuc kog nema u fajlu. Da je ovde, prvo sledece
         * polje dodato u ovu strukturu razbilo bi `progress.json` svakog
         * postojeceg korisnika: citanje pukne -> fajl se proglasi pokvarenim ->
         * krene migracija -> ceo napredak na Putu i cela istorija streak-a
         * nestanu bez ijedne poruke. Sa `opt*` stariji fajl se cita, a nova
         * polja dobiju podrazumevanu vrednost.
         */
        fun fromJson(s: String): ProgressSnapshot {
            val o = JSONObject(s)
            return ProgressSnapshot(
                version = o.optInt("version", 1),
                completedSteps = o.optJSONArray("completedSteps").toStringSet(),
                stepCompletionDates = o.optJSONObject("stepCompletionDates").toStringMap(),
                stepsCompletedByDay = o.optJSONObject("stepsCompletedByDay").toIntMap(),
                puzzlesSolvedByDay = o.optJSONObject("puzzlesSolvedByDay").toIntMap(),
                gamesPlayed = o.optInt("gamesPlayed", 0),
                gamesWon = o.optInt("gamesWon", 0),
                gamesLost = o.optInt("gamesLost", 0),
                gamesDrawn = o.optInt("gamesDrawn", 0),
                currentWinStreak = o.optInt("currentWinStreak", 0),
                bestWinStreak = o.optInt("bestWinStreak", 0),
                winsBeginner = o.optInt("winsBeginner", 0),
                winsEasy = o.optInt("winsEasy", 0),
                winsMedium = o.optInt("winsMedium", 0),
                winsHard = o.optInt("winsHard", 0),
                winsStockfish = o.optInt("winsStockfish", 0),
                puzzlesSolved = o.optInt("puzzlesSolved", 0),
                currentPuzzleStreak = o.optInt("currentPuzzleStreak", 0),
                bestPuzzleStreak = o.optInt("bestPuzzleStreak", 0),
                puzzleRating = o.optInt("puzzleRating", PuzzleRating.START)
            )
        }

        private fun JSONArray?.toStringSet(): Set<String> =
            if (this == null) emptySet() else (0 until length()).map { getString(it) }.toSet()

        private fun JSONObject?.toStringMap(): Map<String, String> =
            if (this == null) emptyMap()
            else keys().asSequence().associateWith { getString(it) }

        private fun JSONObject?.toIntMap(): Map<String, Int> =
            if (this == null) emptyMap()
            else keys().asSequence().associateWith { getInt(it) }
    }
}

/**
 * `mutableStateOf` da EKRAN Puta sam primeti izmenu: kad se korak zavrsi na
 * drugom ekranu, lista koraka mora sama da se prekrsti kad se korisnik vrati.
 */
class ProgressStore(
    context: Context,
    private val file: File = defaultFile(context),
    private val prefs: SharedPreferences =
        context.getSharedPreferences("chessko_stats", Context.MODE_PRIVATE)
) {
    var snapshot by mutableStateOf(ProgressSnapshot())
        private set

    init {
        val existing = if (file.exists()) file.readText() else null
        var loaded: ProgressSnapshot? = null
        if (existing != null) {
            try {
                loaded = ProgressSnapshot.fromJson(existing)
            } catch (e: Exception) {
                // Fajl POSTOJI ali se ne cita. Migracija bi ga odmah pregazila i
                // trajno unistila napredak koji je mozda samo delimicno ostecen.
                val backup = File(file.path + ".corrupt")
                backup.delete()
                file.renameTo(backup)
                Log.e("Chessko", "progress.json se ne cita (${e.message}); odlozen u ${backup.name}")
            }
        }
        if (loaded != null) {
            snapshot = loaded
        } else {
            snapshot = migratedFromPrefs()
            save()
        }
    }

    /** Prvo pokretanje posle nadogradnje: statistika se preuzima i OSTAVLJA. */
    private fun migratedFromPrefs() = ProgressSnapshot(
        gamesPlayed = prefs.getInt("gamesPlayed", 0),
        gamesWon = prefs.getInt("gamesWon", 0),
        gamesLost = prefs.getInt("gamesLost", 0),
        gamesDrawn = prefs.getInt("gamesDrawn", 0),
        currentWinStreak = prefs.getInt("currentWinStreak", 0),
        bestWinStreak = prefs.getInt("bestWinStreak", 0),
        winsBeginner = prefs.getInt("winsBeginner", 0),
        winsEasy = prefs.getInt("winsEasy", 0),
        winsMedium = prefs.getInt("winsMedium", 0),
        winsHard = prefs.getInt("winsHard", 0),
        winsStockfish = prefs.getInt("winsStockfish", 0),
        puzzlesSolved = prefs.getInt("puzzlesSolved", 0),
        currentPuzzleStreak = prefs.getInt("currentPuzzleStreak", 0),
        bestPuzzleStreak = prefs.getInt("bestPuzzleStreak", 0),
        puzzleRating = prefs.getInt("puzzleRating", PuzzleRating.START)
    )

    /**
     * Upis nikad ne baca dalje — napredak ne sme da obori aplikaciju — ali ne
     * sme ni da cuti. Gubitak je uzak jer je snimak u memoriji CEO: sledeca
     * uspesna izmena upisuje i ono sto je ranije palo. Zato se upisuje po
     * SVAKOJ izmeni; grupno snimanje bi taj oporavak ukinulo.
     */
    fun save() {
        try {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(snapshot.toJson())
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        } catch (e: Exception) {
            Log.e("Chessko", "napredak nije sacuvan: ${e.message}")
        }
    }

    private fun mutate(change: (ProgressSnapshot) -> ProgressSnapshot) {
        snapshot = change(snapshot)
        save()
    }

    // MARK: Put

    fun stepStates(curriculum: Curriculum): Map<String, StepState> =
        PathProgress.stepStates(curriculum.allStepIds, snapshot.completedSteps)

    fun completeStep(id: String) {
        if (snapshot.completedSteps.contains(id)) return
        val day = PathProgress.dayKey()
        mutate {
            it.copy(
                completedSteps = it.completedSteps + id,
                stepCompletionDates = it.stepCompletionDates + (id to day),
                stepsCompletedByDay = it.stepsCompletedByDay +
                    (day to (it.stepsCompletedByDay[day] ?: 0) + 1)
            )
        }
    }

    fun recordPuzzleSolvedToday() {
        val day = PathProgress.dayKey()
        mutate {
            it.copy(puzzlesSolvedByDay = it.puzzlesSolvedByDay +
                (day to (it.puzzlesSolvedByDay[day] ?: 0) + 1))
        }
    }

    // MARK: Cilj i streak

    val goalMetToday: Boolean
        get() {
            val day = PathProgress.dayKey()
            return PathProgress.goalMet(
                snapshot.stepsCompletedByDay[day] ?: 0,
                snapshot.puzzlesSolvedByDay[day] ?: 0
            )
        }

    val currentStreak: Int
        get() {
            val days = (snapshot.stepsCompletedByDay.keys + snapshot.puzzlesSolvedByDay.keys)
                .filter {
                    PathProgress.goalMet(
                        snapshot.stepsCompletedByDay[it] ?: 0,
                        snapshot.puzzlesSolvedByDay[it] ?: 0
                    )
                }.toSet()
            return PathProgress.currentStreak(days, PathProgress.dayKey())
        }

    // MARK: Pristup statistici (koristi fasada `StatsManager`)

    fun updateStats(change: (ProgressSnapshot) -> ProgressSnapshot) = mutate(change)

    companion object {
        fun defaultFile(context: Context) = File(context.filesDir, "progress.json")

        @Volatile private var instance: ProgressStore? = null

        fun getInstance(context: Context): ProgressStore =
            instance ?: synchronized(this) {
                instance ?: ProgressStore(context.applicationContext).also { instance = it }
            }
    }
}
```

- [ ] **Step 4: Pokreni instrumentisane testove (jedan prolaz emulatora, pa gasi)**

Recept iz Global Constraints. Očekivano: **41 test** (32 posle Task-a 1 + 9 novih), 0 padova.

- [ ] **Step 5: Dokaži mutacijom da `optInt` štiti napredak**

```bash
cd ChesskoAndroid
sed -i '' 's/puzzleRating = o.optInt("puzzleRating", PuzzleRating.START)/puzzleRating = o.getInt("puzzleRating")/' \
  app/src/main/java/com/veljkoni/chessko/logic/ProgressStore.kt
# pokreni androidTest u istom prolazu emulatora
git checkout app/src/main/java/com/veljkoni/chessko/logic/ProgressStore.kt
```
Očekivano: `anOlderFileMissingNewKeysStillLoads` FAIL — dokaz da bi `getInt` obrisao napredak pri sledećem dodatom polju.

- [ ] **Step 6: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/ProgressStore.kt \
        ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/ProgressStoreTest.kt
git commit -F - <<'EOF'
Faza 6c, Task 3: napredak u JSON fajlu, uz migraciju iz SharedPreferences

progress.json u filesDir umesto SharedPreferences -- napredak je struktura,
ne sacica skalara (spec 5.4). Stari kljucevi se posle migracije NE brisu, pa
povratak na stariju verziju aplikacije i dalje radi.

Snimak nosi i winsBeginner..winsStockfish, kojih iOS ProgressSnapshot nema:
bez njih bi fasada StatsManager izgubila podatke koje ekran vec prikazuje.

Sve se cita kroz opt*, nikad get*. Mutacija u getInt obara test
anOlderFileMissingNewKeysStillLoads -- to je tacno onaj bug od kog se iOS
branio rucno pisanim dekoderom: prvo sledece dodato polje razbilo bi fajl
svakog korisnika i obrisalo ceo napredak bez poruke.

Pokvaren fajl se odlaze u .corrupt, ne gazi -- to je korisnikov fajl.

Instrumentisani testovi 32 -> 41.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 4: `StatsManager` postaje fasada

**Files:**
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/StatsManager.kt`
- Test: `ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/StatsFacadeTest.kt`

**Interfaces:**
- Consumes: `ProgressStore.getInstance(context)`, `ProgressSnapshot`, `PuzzleRating`
- Produces: **isti javni potpisi kao danas** — `gamesPlayed`, `gamesWon`, `gamesLost`, `gamesDrawn`, `currentWinStreak`, `bestWinStreak`, `winsBeginner`, `winsEasy`, `winsMedium`, `winsHard`, `winsStockfish`, `puzzlesSolved`, `currentPuzzleStreak`, `bestPuzzleStreak`, `puzzleRating`, `winRate`, `recordGameWon(GameDifficulty)`, `recordGameLost()`, `recordGameDrawn()`, `recordPuzzleSolved()`, `recordPuzzleFailed()`, `applyPuzzleResult(Int, Boolean)`, `resetStats()`, `getInstance(Context)`, i `companion` konstante `PUZZLE_PREFS_NAME` / `SOLVED_PUZZLE_IDS_KEY` / `SOLVED_DATES_KEY`

**Ključno:** nijedno pozivno mesto se ne dira. Dokaz je `git diff --stat` koji van `StatsManager.kt` i testova ne pokazuje ništa.

- [ ] **Step 1: Prebroj pozivna mesta PRE izmene**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
grep -rno "StatsManager\.\|statsManager\.\|stats\." . | wc -l
```
Zapiši broj — na kraju taska mora biti isti.

- [ ] **Step 2: Napiši test koji pada**

`app/src/androidTest/java/com/veljkoni/chessko/StatsFacadeTest.kt`:

```kotlin
package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.GameDifficulty
import com.veljkoni.chessko.logic.ProgressStore
import com.veljkoni.chessko.logic.StatsManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsFacadeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Fasada i skladiste moraju da gledaju ISTI snimak. Da fasada zadrzi
     * sopstvenu kopiju, ekran statistike i ekran Puta pokazivali bi razlicite
     * brojeve, a jedan od njih bi se pri restartu "vratio unazad".
     */
    @Test
    fun facadeWritesReachTheStore() {
        val stats = StatsManager.getInstance(context)
        val store = ProgressStore.getInstance(context)
        val before = store.snapshot.gamesPlayed
        stats.recordGameWon(GameDifficulty.EASY)
        assertEquals(before + 1, store.snapshot.gamesPlayed)
        assertEquals(store.snapshot.gamesPlayed, stats.gamesPlayed)
    }

    /**
     * Dnevni cilj se puni i sa taba Zadaci, ne samo iz Puta: tri resena
     * zadatka ispunjavaju cilj (spec 5.4). Bez ovoga bi streak rastao samo
     * onima koji prolaze Put.
     */
    @Test
    fun solvingAPuzzleCountsTowardTheDailyGoal() {
        val stats = StatsManager.getInstance(context)
        val store = ProgressStore.getInstance(context)
        val day = com.veljkoni.chessko.logic.PathProgress.dayKey()
        val before = store.snapshot.puzzlesSolvedByDay[day] ?: 0
        stats.recordPuzzleSolved()
        assertEquals(before + 1, store.snapshot.puzzlesSolvedByDay[day])
    }

    /**
     * „Resetuj statistiku" NE dira Put. Brise brojace, ali zavrseni koraci,
     * streak i istorija dnevnog cilja ostaju -- napredak nije statistika.
     */
    @Test
    fun resetClearsCountersButKeepsThePath() {
        val stats = StatsManager.getInstance(context)
        val store = ProgressStore.getInstance(context)
        store.completeStep("basics-lesson")
        stats.recordGameWon(GameDifficulty.HARD)

        stats.resetStats()

        assertEquals(0, stats.gamesPlayed)
        assertEquals(0, stats.winsHard)
        assertEquals(PuzzleRatingStart, stats.puzzleRating)
        assertEquals(true, store.snapshot.completedSteps.contains("basics-lesson"))
    }

    private val PuzzleRatingStart get() = com.veljkoni.chessko.logic.PuzzleRating.START
}
```

- [ ] **Step 3: Pokreni i vidi da pada**

Očekivano: FAIL — fasada još piše u `SharedPreferences`, pa `store.snapshot.gamesPlayed` ostaje nepromenjen.

- [ ] **Step 4: Prepiši `StatsManager` kao fasadu**

Zameni telo klase (konstruktor, sva `by mutableIntStateOf` polja i sve `save*` metode) delegiranjem. Svojstva postaju `get()` nad snimkom, a metode `store.updateStats { ... }`:

```kotlin
package com.veljkoni.chessko.logic

import android.content.Context
import android.content.SharedPreferences

/**
 * Od Faze 6c ovo je FASADA nad `ProgressStore`-om, ne skladiste.
 *
 * Zadrzava svaki potpis koji je imala, pa njena pozivna mesta nisu dirana.
 * Sme da se ukloni, ali to znaci dirati svako od njih.
 */
class StatsManager private constructor(context: Context) {

    private val store = ProgressStore.getInstance(context)

    /**
     * Napredak na zadacima istorijski zivi u DRUGOM fajlu, koji
     * `PuzzleViewModel` otvara pod istim imenom. Drzi se ovde da bi
     * `resetStats()` mogao da ga ocisti.
     */
    private val puzzlePrefs: SharedPreferences =
        context.getSharedPreferences(PUZZLE_PREFS_NAME, Context.MODE_PRIVATE)

    val gamesPlayed: Int get() = store.snapshot.gamesPlayed
    val gamesWon: Int get() = store.snapshot.gamesWon
    val gamesLost: Int get() = store.snapshot.gamesLost
    val gamesDrawn: Int get() = store.snapshot.gamesDrawn
    val currentWinStreak: Int get() = store.snapshot.currentWinStreak
    val bestWinStreak: Int get() = store.snapshot.bestWinStreak
    val winsBeginner: Int get() = store.snapshot.winsBeginner
    val winsEasy: Int get() = store.snapshot.winsEasy
    val winsMedium: Int get() = store.snapshot.winsMedium
    val winsHard: Int get() = store.snapshot.winsHard
    val winsStockfish: Int get() = store.snapshot.winsStockfish
    val puzzlesSolved: Int get() = store.snapshot.puzzlesSolved
    val currentPuzzleStreak: Int get() = store.snapshot.currentPuzzleStreak
    val bestPuzzleStreak: Int get() = store.snapshot.bestPuzzleStreak
    val puzzleRating: Int get() = store.snapshot.puzzleRating

    val winRate: Int
        get() = if (gamesPlayed > 0) ((gamesWon.toDouble() / gamesPlayed) * 100).toInt() else 0

    fun applyPuzzleResult(puzzleElo: Int, solved: Boolean) {
        val next = PuzzleRating.newRating(puzzleRating, puzzleElo, solved)
        store.updateStats { it.copy(puzzleRating = next) }
    }

    fun recordGameWon(difficulty: GameDifficulty) = store.updateStats {
        val streak = it.currentWinStreak + 1
        it.copy(
            gamesPlayed = it.gamesPlayed + 1,
            gamesWon = it.gamesWon + 1,
            currentWinStreak = streak,
            bestWinStreak = maxOf(it.bestWinStreak, streak),
            winsBeginner = it.winsBeginner + if (difficulty == GameDifficulty.BEGINNER) 1 else 0,
            winsEasy = it.winsEasy + if (difficulty == GameDifficulty.EASY) 1 else 0,
            winsMedium = it.winsMedium + if (difficulty == GameDifficulty.MEDIUM) 1 else 0,
            winsHard = it.winsHard + if (difficulty == GameDifficulty.HARD) 1 else 0,
            winsStockfish = it.winsStockfish + if (difficulty == GameDifficulty.STOCKFISH) 1 else 0
        )
    }

    fun recordGameLost() = store.updateStats {
        it.copy(gamesPlayed = it.gamesPlayed + 1, gamesLost = it.gamesLost + 1, currentWinStreak = 0)
    }

    fun recordGameDrawn() = store.updateStats {
        it.copy(gamesPlayed = it.gamesPlayed + 1, gamesDrawn = it.gamesDrawn + 1)
    }

    /**
     * Uz brojace upisuje i DNEVNI CILJ: tri resena zadatka ispunjavaju cilj
     * isto kao jedan zavrsen korak (spec 5.4). Bez ovog reda bi streak rastao
     * samo onima koji prolaze Put.
     */
    fun recordPuzzleSolved() {
        store.updateStats {
            val streak = it.currentPuzzleStreak + 1
            it.copy(
                puzzlesSolved = it.puzzlesSolved + 1,
                currentPuzzleStreak = streak,
                bestPuzzleStreak = maxOf(it.bestPuzzleStreak, streak)
            )
        }
        store.recordPuzzleSolvedToday()
    }

    fun recordPuzzleFailed() = store.updateStats { it.copy(currentPuzzleStreak = 0) }

    /**
     * „Resetuj statistiku" NE dira Put: `completedSteps`,
     * `stepCompletionDates` i `stepsCompletedByDay` ostaju. Napredak nije
     * statistika (spec 5.4, isto kao iOS).
     */
    fun resetStats() {
        store.updateStats {
            it.copy(
                gamesPlayed = 0, gamesWon = 0, gamesLost = 0, gamesDrawn = 0,
                currentWinStreak = 0, bestWinStreak = 0,
                winsBeginner = 0, winsEasy = 0, winsMedium = 0, winsHard = 0, winsStockfish = 0,
                puzzlesSolved = 0, currentPuzzleStreak = 0, bestPuzzleStreak = 0,
                puzzleRating = PuzzleRating.START
                // `puzzlesSolvedByDay` se NE dira. To je istorija dnevnog CILJA, ne
                // brojac — i `currentStreak` je racuna iz unije sa `stepsCompletedByDay`.
                // Brisanjem bi korisnik posle reseta izgubio svaki dan u kome je cilj
                // ispunio iskljucivo zadacima. iOS je isto ne dira
                // (`Chessko/Logic/StatsManager.swift:137-157`).
            )
        }
        puzzlePrefs.edit()
            .remove(SOLVED_PUZZLE_IDS_KEY)
            .remove(SOLVED_DATES_KEY)
            .apply()
    }

    companion object {
        internal const val PUZZLE_PREFS_NAME = "chessko_puzzle_prefs"
        internal const val SOLVED_PUZZLE_IDS_KEY = "solvedPuzzleIds"
        internal const val SOLVED_DATES_KEY = "solved_dates_key"

        @Volatile private var instance: StatsManager? = null

        fun getInstance(context: Context): StatsManager =
            instance ?: synchronized(this) {
                instance ?: StatsManager(context.applicationContext).also { instance = it }
            }
    }
}
```

- [ ] **Step 5: Dokaži da nijedno pozivno mesto nije dirano**

```bash
cd /Users/veljkoodobasic/Documents/Projects/Chess
git diff --stat -- ChesskoAndroid/app/src/main | grep -v StatsManager.kt
```
Očekivano: **prazan izlaz**. Ako se pojavi bilo koji drugi fajl, fasada nije zadržala neki potpis.

- [ ] **Step 6: Pokreni oba skupa testova** (jedan prolaz emulatora, pa gasi)

Očekivano: JVM 33/33, instrumentisani **44** (41 + 3 nova), 0 padova.

- [ ] **Step 7: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/StatsManager.kt \
        ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/StatsFacadeTest.kt
git commit -F - <<'EOF'
Faza 6c, Task 4: StatsManager postaje fasada nad ProgressStore-om

Zadrzava svaki potpis, pa nijedno pozivno mesto nije dirano -- dokaz je
git diff --stat koji van StatsManager.kt ne pokazuje nijedan fajl.

recordPuzzleSolved() sada uz brojace upisuje i dnevni cilj: tri resena
zadatka ispunjavaju cilj isto kao jedan zavrsen korak (spec 5.4). Bez tog
reda bi streak rastao samo onima koji prolaze Put.

"Resetuj statistiku" NE dira Put -- zavrseni koraci, streak i istorija
ostaju. Napredak nije statistika.

Instrumentisani testovi 41 -> 44.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 5: Ekran Puta, tab i koraci tipa `lesson`

**Files:**
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/LessonDetailView.kt`
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PathView.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/LearnView.kt` (detalj se seli)
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/MainActivity.kt:585` (tab) i `:1031` (sadržaj)
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/Loc.kt`

**Interfaces:**
- Consumes: `loadCurriculum(context)`, `ProgressStore.getInstance`, `StepState`, `LessonRepository`, `lessonIcon()`, `LessonBlocks()`
- Produces:
  - `@Composable fun LessonDetailView(lessonId: String, onClose: () -> Unit, onComplete: (() -> Unit)? = null)` — `onComplete` je dugme potvrde na dnu; `null` znači da se lekcija čita van Puta
  - `@Composable fun PathView(modifier: Modifier = Modifier)`
  - `sealed class StepRoute` sa `Lesson(lessonId, stepId)`, `Practice(step)`, `Game(difficulty, startFEN, stepId)`
  - `fun routeFor(step: CurriculumStep): StepRoute?` — **jedno mesto istine** za odluku o ruti

- [ ] **Step 1: Izdvoji detalj lekcije iz `LearnView.kt`**

Premesti granu `activeLesson != null` iz `LearnView` u nov `LessonDetailView.kt`, sa potpisom iznad. `LearnView` posle toga poziva `LessonDetailView(id, onClose = { activeLessonId = null })`. Ništa se ne dodaje i ne oduzima od sadržaja — samo seli.

- [ ] **Step 2: Dodaj UI ključeve u `Loc.kt`**

Dodaj ove ključeve, svaki sa svih 8 jezika (`de`, `en`, `fr`, `hi`, `it`, `ru`, `sr`, `zh-Hans`), po obrascu postojećih unosa:

```
"Put", "Nastavi", "Korak %d", "Zaključano", "Završeno", "Dostupno",
"Niz: %d dana", "Cilj za danas je ispunjen", "Cilj za danas nije ispunjen",
"Završi korak", "Korak je završen", "Vežba", "Test", "Partija", "Lekcija"
```

**Ključevi se pišu kao literali unutar `loc()`**, nikad sklopljeni iz promenljive — `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary` ih inače ne vidi. Za interpolaciju koristiti `locF("Korak %d", n)`, ne `loc("Korak " + n)`.

- [ ] **Step 3: Napiši `PathView.kt`**

Sadržaj ekrana, po spec 5.1 („Početni ekran Puta ne prikazuje spisak lekcija nego nastavak"):

```kotlin
package com.veljkoni.chessko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.veljkoni.chessko.logic.PathProgress
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
    // Task 6 vraca StepRoute.Practice(step) za Practice i Test.
    // Task 7 vraca StepRoute.Game(k.difficulty, k.startFEN, step.id).
    // Do tada NULL: kartica pise „Uskoro" i NEMA strelicu. Nema poluotvorenog
    // stanja u kome korak izgleda aktivno a vodi na prazan ekran.
    is StepKind.Practice -> null
    is StepKind.Test -> null
    is StepKind.Game -> null
}

@Composable
fun PathView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ProgressStore.getInstance(context) }
    val curriculum = remember { loadCurriculum(context) }

    // Naslov poglavlja stize iz JSON-a vec preveden -- NE kroz loc().
    val lang = Loc.fileLanguageCode().substringBefore('-')

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
        // Grane za Practice i Game dodaju Task 6 i Task 7, zajedno sa svojim
        // ekranima. Task 5 ih NE pominje — `StepPracticeView`/`StepGameView`
        // jos ne postoje, pa se fajl ne bi ni kompajlirao.
        is StepRoute.Practice -> Unit
        is StepRoute.Game -> Unit
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
                onOpen = { step -> routeFor(step)?.let { route = it } }
            )
        }
    }
}
```

`PathHeader` i `ChapterSection` su privatni composable-i u istom fajlu: zaglavlje prikazuje `locF("Niz: %d dana", streak)`, `loc("Cilj za danas je ispunjen")`/`loc("Cilj za danas nije ispunjen")` i dugme `loc("Nastavi")`; kartica koraka prikazuje redni broj, tip (`loc("Lekcija")`/`loc("Vežba")`/`loc("Test")`/`loc("Partija")`) i stanje, a **strelicu ima samo ako `states[id] != LOCKED` i `routeFor(step) != null`**.

- [ ] **Step 4: Zameni tab u `MainActivity.kt`**

Na liniji 585 zameni `label = loc("Učenje")` sa `label = loc("Put")`, a na 1031 `LearnView(viewModel = learnViewModel)` sa `PathView()`. Ikona taba: `Icons.Filled.Map` iz već prisutnog `material-icons-extended`.

- [ ] **Step 5: Build, JVM testovi, pa vizuelna provera u JEDNOM prolazu emulatora**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
# emulator po receptu iz Global Constraints, pa:
# 1. tab "Put" postoji i otvara listu poglavlja
# 2. samo prvi korak ima strelicu, ostali pisu „Zaključano"
# 3. otvori lekciju, skroluj do dna (input motionevent!), potvrdi „Završi korak"
# 4. vrati se — korak ima kvačicu, drugi je otključan, streak = 1
# 5. ubij aplikaciju (adb shell am force-stop) i pokreni ponovo — napredak stoji
$ANDROID_HOME/platform-tools/adb emu kill; ./gradlew --stop
```
Očekivano: sve pet tačaka prolazi. **Tačka 5 je uslov „gotovo" iz spec-a** — napredak preživi gašenje.

- [ ] **Step 6: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PathView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/LessonDetailView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/LearnView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/MainActivity.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/Loc.kt
git commit -F - <<'EOF'
Faza 6c, Task 5: treci tab je Put, koraci tipa lesson rade

Ekran Puta ne prikazuje spisak lekcija nego NASTAVAK, uz streak i napredak
poglavlja (spec 5.1). Detalj lekcije je izdvojen iz LearnView.kt u sopstveni
fajl, jer je Put otvara bez liste.

Odluka o ruti stoji u jednoj funkciji `routeFor(step)`, ne razmazana po UI-ju.
Korak bez rute nema strelicu i pise „Uskoro" -- iOS je ovde imao bug u kome su
vezba, test i partija izgledali aktivno i vodili na prazan ekran.

Nema NavHost-a: projekat nema androidx.navigation i ne dobija je. Ruta je
obicno Compose stanje.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 6: Koraci `practice` i `test`

**Files:**
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/PuzzleRepository.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/viewmodels/PuzzleViewModel.kt`
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt`
- Test: `ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/StepWindowTest.kt`

**Interfaces:**
- Consumes: `StepKind.Practice`/`Test`, `ProgressStore.completeStep`, `PuzzleRating.practiceWindow`
- Produces:
  - `PuzzleRepository.puzzlesForStep(themes: List<String>, ratingRange: IntRange, excluding: Set<String>, limit: Int): List<ChessPuzzle>`
  - `PuzzleRating.stepWindow(playerRating: Int, stepRange: IntRange): IntRange`
  - `PuzzleViewModel`: `fun startStepPractice(step: CurriculumStep)`, `val stepProgress: Pair<Int, Int>`, `val stepFailed: Boolean`, `val stepRequiresFlawless: Boolean`
  - `@Composable fun StepPracticeView(step: CurriculumStep, onClose: () -> Unit)`

- [ ] **Step 1: Napiši test za prozor rejtinga**

```kotlin
package com.veljkoni.chessko

import com.veljkoni.chessko.logic.PuzzleRating
import org.junit.Assert.assertEquals
import org.junit.Test

class StepWindowTest {

    @Test
    fun theStepRangeIntersectsThePlayerWindow() {
        // igrac 900 -> prozor 700..1000; korak 600..1200 -> presek 700..1000
        assertEquals(700..1000, PuzzleRating.stepWindow(900, 600..1200))
    }

    /**
     * Kad je presek prazan, prednost ima OPSEG KORAKA -- kurikulum zna sta se
     * uci, rejting je samo podesavanje (spec 5.4). Bez ovoga bi jak igrac dobio
     * prazan red i korak koji se ne moze zavrsiti.
     */
    @Test
    fun anEmptyIntersectionFallsBackToTheStepRange() {
        assertEquals(600..800, PuzzleRating.stepWindow(2000, 600..800))
    }

    @Test
    fun theResultIsNeverInverted() {
        for (r in listOf(80, 600, 900, 1500, 3000)) {
            for (range in listOf(600..800, 600..1200, 1100..2200)) {
                val w = PuzzleRating.stepWindow(r, range)
                assertEquals(true, w.first <= w.last)
            }
        }
    }
}
```

Implementacija u `PuzzleRating.kt`:

```kotlin
/**
 * Prozor rejtinga za korak Puta: prozor igraca presecen opsegom koraka.
 * Kad je presek prazan, prednost ima OPSEG KORAKA -- kurikulum zna sta se uci,
 * rejting je samo podesavanje (spec 5.4).
 */
fun stepWindow(playerRating: Int, stepRange: IntRange): IntRange {
    val p = practiceWindow(playerRating)
    val lo = maxOf(p.first, stepRange.first)
    val hi = minOf(p.last, stepRange.last)
    return if (lo <= hi) lo..hi else stepRange
}
```

- [ ] **Step 2: Dodaj upit po temama u `PuzzleRepository.kt`**

Po uzoru na `randomPuzzle`, ali sa `JOIN puzzle_themes` i `IN` listom tema (razložena tabela postoji baš zbog ovoga — `LIKE '%mate%'` bi pogodio i `mateIn1`, `mateIn2` i `smotheredMate`):

```kotlin
fun puzzlesForStep(
    themes: List<String>, ratingRange: IntRange, excluding: Set<String>, limit: Int
): List<ChessPuzzle>
```
Red se puni **jednim** upitom, `ORDER BY RANDOM() LIMIT ?`.

- [ ] **Step 3: Dodaj režim koraka u `PuzzleViewModel.kt`**

Proširi `PuzzleMode` sa `STEP`, dodaj `stepQueue`/`stepSolved`/`stepFailed`/`currentStep`, i:

- `startStepPractice(step)` — puni red **jednom** (bez toga traka napretka nema ukupan broj, a `test` ne može da garantuje da su zadaci NOVI), postavlja `stepFailed = false`, diže `loadGeneration`
- `advanceStepAfterSolve()` — broji; kad je red iscrpljen, zove `ProgressStore.completeStep(step.id)`
- `failStepIfTest()` — prva greška u testu ga obara i posle ~1,4 s pokreće korak iznova sa **novim** zadacima

Tok rešavanja se **ne duplira** — `tap`/`attempt`/`applyMove` su isti kao na tabu Zadaci, pa i `StatsManager.recordPuzzleSolved()` ide postojećim putem. `isPlayerTurn` dobija `&& !stepFailed`.

- [ ] **Step 4: Proširi `routeFor` i `PathView` na vežbu i test**

U `PathView.kt`: `is StepKind.Practice -> StepRoute.Practice(step)` i
`is StepKind.Test -> StepRoute.Practice(step)` umesto `null`; u `when (val r = route)`
grana `is StepRoute.Practice -> { StepPracticeView(step = r.step, onClose = { route = null }); return }`.
Tek sada kartice vežbe i testa dobijaju strelicu.

- [ ] **Step 5: Napiši `StepPracticeView.kt`**

Tanak ekran: traka pilula, brojač `locF("Zadatak %d od %d", solved, total)`, status, `BoardView`. **Sopstvena instanca modela**:

```kotlin
val app = LocalContext.current.applicationContext as Application
val viewModel = remember { PuzzleViewModel(app) }   // NE viewModel() — deljeni bi pregazio zadatak dana
LaunchedEffect(step.id) { viewModel.startStepPractice(step) }
```

Namerno **NE** nudi „Prikaži rešenje" (bio bi izlaz iz provere) ni „Sledeći zadatak" (red je fiksan).

- [ ] **Step 6: Testovi + vizuelna provera u jednom prolazu emulatora**

Očekivano: JVM **36** (33 + 3), instrumentisani 44. Vizuelno: vežba se igra i završava korak; **test pada na prvu grešku i kreće iznova sa drugim zadacima** (uporediti FEN pre i posle).

- [ ] **Step 7: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/PuzzleRating.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/PuzzleRepository.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/viewmodels/PuzzleViewModel.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt \
        ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/StepWindowTest.kt
git commit -F - <<'EOF'
Faza 6c, Task 6: koraci practice i test

Red zadataka se puni JEDNIM upitom po temama nad razlozenom tabelom
puzzle_themes -- LIKE nad tekstom bi pogodio i mateIn1, mateIn2 i
smotheredMate. Punjenje unapred je bitno: bez njega traka napretka nema
ukupan broj, a test koji krece ispocetka ne moze da garantuje NOVE zadatke.

stepWindow presece prozor igraca opsegom koraka; kad je presek prazan,
prednost ima OPSEG KORAKA -- inace bi jak igrac dobio prazan red i korak koji
se ne moze zavrsiti.

StepPracticeView pravi SOPSTVENU instancu PuzzleViewModel-a. Deljena (preko
viewModel()) bi ulaskom u korak pregazila zadatak dana.

JVM testovi 33 -> 36.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 7: Korak `game`, promocija i odvojen slot za partiju

**Files:**
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PromotionOverlay.kt`
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepGameView.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/viewmodels/GameViewModel.kt:571,581,650`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/MainActivity.kt` (overlay na tabu Igra)

**Interfaces:**
- Consumes: `GameViewModel` (`isGameOver`, `resign()`, `showPromotion`, `promotionMove`, `confirmPromotion`, `cancelPromotion`), `ProgressStore.completeStep`
- Produces:
  - `@Composable fun PromotionOverlay(viewModel: GameViewModel)`
  - `@Composable fun StepGameView(difficulty: String, startFEN: String?, stepId: String, onClose: () -> Unit)`
  - `GameViewModel(application, saveKey: String = FREE_PLAY_SAVE_KEY)` + `companion object { const val FREE_PLAY_SAVE_KEY = "saved_game"; fun stepSaveKey(stepId: String) = "saved_game.step.$stepId" }`

> **Ovaj task popravlja živ bug, ne samo prepreku za Put.** `GameViewModel` postavi `showPromotion = true` i čeka izbor figure, a **taj izbor ne renderuje niko** — `grep -rn "showPromotion" ` po celom izvoru pogađa samo `GameViewModel.kt`. Podrazumevano je `autoPromoteToQueen = false`, pa promocija pešaka u **slobodnoj partiji** zauvek blokira tablu. Bez ovoga bi i partija u koraku stala na promociji i korak se ne bi mogao završiti.

- [ ] **Step 1: Dokaži bug pre nego što ga popraviš**

Na emulatoru, u slobodnoj partiji, dovedi belog pešaka do osmog reda (najbrže preko `adb shell` + zasejanog `saved_game`). Očekivano **pre popravke**: tabla se zamrzne, nijedan dodir ne radi, nema dijaloga. Snimi screenshot kao dokaz.

- [ ] **Step 2: Napiši `PromotionOverlay.kt`**

Modalni preklop sa četiri figure (D/T/L/S) koji zove `viewModel.confirmPromotion(type)`; dodir van preklopa zove `cancelPromotion()`. Koristi `PieceImage` obrazac iz `BoardView.kt`.

- [ ] **Step 3: Prikaži ga na tabu Igra i potvrdi da bug nestaje**

U `MainActivity.kt`, u grani taba Igra: `if (gameViewModel.showPromotion) PromotionOverlay(gameViewModel)`. Ponovi Step 1 — sada se bira figura i partija se nastavlja.

- [ ] **Step 4: Parametrizuj ključ za čuvanje partije**

```kotlin
class GameViewModel(
    application: Application,
    private val saveKey: String = FREE_PLAY_SAVE_KEY
) : AndroidViewModel(application) {
    // linije 571, 581, 650: "saved_game" -> saveKey
    companion object {
        const val FREE_PLAY_SAVE_KEY = "saved_game"
        /**
         * Korak dobija SOPSTVENI slot. Sa jednim kljucem bi drugi primerak
         * modela ucitao partiju koju korisnik ima u toku na tabu Igra i prvim
         * potezom je pregazio -- model snima celu partiju posle SVAKOG poteza.
         */
        fun stepSaveKey(stepId: String) = "saved_game.step.$stepId"
    }
}
```

- [ ] **Step 5: Proširi `routeFor` i `PathView` na partiju**

U `PathView.kt`: `is StepKind.Game -> StepRoute.Game(k.difficulty, k.startFEN, step.id)` umesto
`null`, i grana `is StepRoute.Game -> { StepGameView(r.difficulty, r.startFEN, r.stepId,
onClose = { route = null }); return }`. Time `routeFor` više ni za jedan tip koraka ne vraća
`null` sa isporučenim kurikulumom — poglavlja sa `game` korakom postaju prohodna.

- [ ] **Step 6: Napiši `StepGameView.kt`**

Sopstveni `GameViewModel(app, GameViewModel.stepSaveKey(stepId))`, kartica protivnika, status, `BoardView`, `PromotionOverlay`, predaja i undo. Korak se završava kad `isGameOver` postane `true`, **tačno jednom** po ulasku (`var pendingStepId by remember { mutableStateOf<String?>(stepId) }`) i **bez obzira na ishod** — predaja i poraz završavaju korak isto kao pobeda (spec: „partija odigrana do kraja", ne pobeda).

- [ ] **Step 7: Dokaži da tab Igra ostaje netaknut**

```bash
# pre ulaska u korak
adb shell "run-as com.veljkoni.chessko cat shared_prefs/chessko_save.xml" | shasum
# odigraj nekoliko poteza u koraku, pa ponovo:
adb shell "run-as com.veljkoni.chessko cat shared_prefs/chessko_save.xml" | shasum
```
Očekivano: **isti sha** pre i posle — korak piše u svoj slot.

- [ ] **Step 8: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PromotionOverlay.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepGameView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/viewmodels/GameViewModel.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/MainActivity.kt
git commit -F - <<'EOF'
Faza 6c, Task 7: korak game, i popravka zivog buga sa promocijom

GameViewModel je od pocetka postavljao showPromotion = true i cekao izbor
figure, a taj izbor nije renderovao NIKO -- grep po celom izvoru pogadja samo
GameViewModel.kt. Podrazumevano je autoPromoteToQueen = false, pa je promocija
pesaka u slobodnoj partiji zauvek blokirala tablu. Nov PromotionOverlay to
popravlja i na tabu Igra, ne samo u Putu.

GameViewModel vise ne cuva partiju pod jednim globalnim kljucem: korak dobija
sopstveni slot. Sa jednim kljucem bi drugi primerak ucitao korisnikovu partiju
sa taba Igra i prvim potezom je pregazio.

Korak se zavrsava kad partija dodje do kraja, tacno jednom po ulasku i BEZ
OBZIRA NA ISHOD -- spec trazi "partija odigrana do kraja", ne pobedu.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 8: Zatvaranje faze

**Files:**
- Modify: `CLAUDE.md` (sekcije „Stanje Android porta", „Put", „Poznata ograničenja", Changelog)
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/LearnView.kt` (odluka o listi lekcija)

- [ ] **Step 1: Odluči šta biva sa `LearnView` listom**

iOS je `LearnView` **obrisao** kad je treći tab postao Put. Na Androidu lista više nije dostupna ni sa jednog taba. Dve opcije, obe legitimne — izabrati i **zapisati razlog**:
  - obrisati `LearnView` (poravnanje sa iOS-om, ~180 linija manje), ili
  - zadržati je kao mrtav kod sa komentarom zašto.

Ako se briše, `LessonCard` i `accentFor` sele se u `LessonDetailView.kt` ako ih on koristi.

- [ ] **Step 2: Ažuriraj `CLAUDE.md`**

- Tabela „Stanje Android porta": red **`4 — Put`** iz `ne` u **`da`**, uz `6c` red
- Sekcija „Put (kurikulum i napredak)": dopuna da isti `curriculum.json` čita i Android, bajt-identično, sa dokazom `diff`
- „Poznata ograničenja": ukloniti/ispraviti sve što ova faza menja; **dodati** da je promocija na Androidu do 6c bila neupotrebljiva
- Changelog: nov unos sa izmerenim brojevima (testovi, linije), bez zaokruživanja

- [ ] **Step 3: Provere celog stabla**

```bash
cd /Users/veljkoodobasic/Documents/Projects/Chess
diff Chessko/Content/curriculum.json ChesskoAndroid/app/src/main/assets/curriculum.json && echo "kurikulum identican"
diff -r Chessko/Content/lessons ChesskoAndroid/app/src/main/assets/lessons && echo "lekcije identicne"
D=$(git diff --stat 17dd16e..HEAD -- Chessko Chessko.xcodeproj); [ -z "$D" ] && echo "iOS netaknut" || echo "$D"
git diff --stat 17dd16e..HEAD -- ChesskoAndroid/app/build.gradle.kts ChesskoAndroid/gradle/libs.versions.toml
```
Očekivano: kurikulum i lekcije identični, iOS netaknut, **nijedna izmena Gradle fajlova** (nijedna nova zavisnost).

- [ ] **Step 4: Oba skupa testova na završnom stablu**

Očekivano: JVM **36**, instrumentisani **44**, 0 padova — čitano iz XML-a.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/LearnView.kt
git commit -F - <<'EOF'
Faza 6c: dokumentacija Puta na Androidu

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Self-Review

**1. Pokrivenost spec-a**

| Zahtev spec-a | Task |
|---|---|
| 5.1 — četiri tipa koraka | 1 (model), 5 (`lesson`), 6 (`practice`/`test`), 7 (`game`) |
| 5.1 — `locked` → `available` → `completed` | 2 (`PathProgress.stepStates`) |
| 5.1 — početni ekran pokazuje nastavak, ne spisak | 5 (`PathHeader`) |
| 5.1 — tab `Učenje` → `Put`, broj tabova isti | 5 |
| 5.4 — napredak po koracima + datum završetka | 3 (`completedSteps`, `stepCompletionDates`) |
| 5.4 — dnevni cilj = 1 korak ili 3 zadatka | 2 (`goalMet`), 4 (`recordPuzzleSolved`) |
| 5.4 — streak, prekida se propuštenim danom | 2 (`currentStreak`, mutacijom dokazano) |
| 5.4 — rejting bira težinu, presečen sa `ratingRange` koraka | 6 (`stepWindow`) |
| 5.4 — JSON fajl, ne `UserDefaults`; stara statistika migrira i **ostaje** | 3 |
| Faza 4 „gotovo kad" — put se pređe do kraja | 5, 6, 7 |
| Faza 4 „gotovo kad" — napredak preživi gašenje | 5 (Step 5, tačka 5) |
| Faza 4 „gotovo kad" — streak se ispravno prekida | 2 |
| Faza 6 — `curriculum.json` se deli bez izmena | 1 |

Bez rupa.

**2. Placeholderi:** nema „TBD"/„TODO"/„slično kao Task N". Task 5 i 6 opisuju `PathHeader`/`ChapterSection`/`StepPracticeView` rečima uz tačne potpise i tačne `loc()` ključeve, umesto punog Compose tela — to je namerno: raspored je jedina stvar u ovom planu koja se bez ekrana ne može ni napisati ni proveriti, a svi ključevi, imena i uslovi vidljivosti su zadati.

**3. Doslednost tipova:** `StepState` (Task 2) koristi se u Task 3 i 5; `StepKind` (Task 1) u 5 i 6; `ProgressSnapshot` (Task 3) u 4; `stepWindow` (Task 6) prati `practiceWindow` koji već postoji; `GameViewModel.stepSaveKey` (Task 7) nigde se ranije ne pominje pod drugim imenom. `PathProgress.dayKey()` se koristi u Task 3 i u testu Task-a 4 — definisan u Task 2.

**Jedna namerna razlika u odnosu na iOS**, zapisana da se ne pročita kao propust: `ProgressSnapshot` na Androidu nosi `winsBeginner`…`winsStockfish`, kojih iOS nema. Android `StatsManager` ih danas prikazuje, pa bi fasada bez njih tiho izgubila podatke.
