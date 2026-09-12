# Faza 6a — Android: offline zadaci i popravka mešanja jezika

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android tab „Zadaci" prestaje da zove mrežu i čita istu `puzzles.sqlite` bazu koju iOS već isporučuje, uz Elo rejting igrača i neograničeno rešavanje; usput se uklanja mešanje jezika iz spec-a 4.5.

**Architecture:** Baza se deli sa iOS-om **bajt-identično** i ide u `assets/`. Android ne može da otvori bazu direktno iz APK-a (asseti su spakovani), pa se pri prvoj upotrebi kopira u `filesDir` — isti obrazac koji `StockfishEngine` već koristi za `.nnue` mreže. Čitanje ide kroz `android.database.sqlite` (sistemski, **bez ijedne nove zavisnosti**), u režimu samo-za-čitanje. Sva računica koja ne dodiruje bazu (Elo, prozor rejtinga) živi u zasebnom fajlu bez Android uvoza, pa se testira običnim JVM testovima; samo integritet baze traži instrumentisani test.

**Tech Stack:** Kotlin, Jetpack Compose, `android.database.sqlite`, JUnit4 (`testDebugUnitTest`), instrumentisani testovi (`connectedDebugAndroidTest`), Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` — sekcija **5.3 Offline baza zadataka**, **4.5 Android meša jezike**, **Faza 6**.

## Global Constraints

- **`create_xcode_project.py` se NIKAD ne pokreće** (važi za ceo repozitorijum, i kad se radi na Androidu).
- **Nijedna nova Gradle zavisnost.** SQLite je u Android SDK-u; `testImplementation(libs.junit)` i postojeće `androidTest` zavisnosti su dovoljne.
- **`Chessko/` (iOS) se NE dira.** Ovaj plan menja samo `ChesskoAndroid/` i `CLAUDE.md`.
- **`puzzles.sqlite` mora biti bajt-identična iOS verziji** — `sha256` počinje sa `3cd00a83…`. Ne regeneriše se, ne filtrira, ne smanjuje.
- **Izvorni jezik je srpski.** Svaki korisnički vidljiv string ide kroz `loc("…")`; ključ je srpski tekst.
- **`minSdk = 26`**, `targetSdk = 36`.
- Gradle traži `ANDROID_HOME`: `export ANDROID_HOME=~/Library/Android/sdk` pre svake komande.

## Okruženje — ne sme otimati fokus korisniku

Korisnik radi druge poslove na istom računaru. Ovo je tvrdo ograničenje.

- Emulator se pokreće **isključivo bez prozora**:
  ```bash
  export ANDROID_HOME=~/Library/Android/sdk
  $ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim &
  $ANDROID_HOME/platform-tools/adb wait-for-device
  ```
  Postojeći AVD je `Medium_Phone_API_36.1`. **Nikad `-no-window` ne izostavljati.**
- Screenshot bez prozora: `adb exec-out screencap -p > <putanja>`.
- Pomoćni fajlovi idu **apsolutnom putanjom** u scratchpad, nikad relativnom u repozitorijum.
- Emulator ugasiti kad više ne treba: `$ANDROID_HOME/platform-tools/adb emu kill`.

---

## Struktura fajlova

| Fajl | Odgovornost |
|---|---|
| `ChesskoAndroid/app/src/main/assets/puzzles.sqlite` **(nov)** | Kopija iOS baze, bajt-identična. |
| `…/logic/PuzzleRepository.kt` **(nov)** | Kopiranje iz `assets` u `filesDir` i čitanje baze. Jedino mesto koje zna za SQL. |
| `…/logic/PuzzleRating.kt` **(nov, bez Android uvoza)** | Elo formula i prozor rejtinga. Čista računica, JVM-testabilna. |
| `…/logic/StatsManager.kt` (izmena) | Dodaje `puzzleRating` i `applyPuzzleResult`. |
| `…/viewmodels/PuzzleViewModel.kt` (izmena) | Briše mrežni poziv, čita repozitorijum, dodaje „Sledeći zadatak". |
| `…/ui/PuzzleView.kt` (izmena) | Dugme „Sledeći zadatak"; zakucani stringovi kroz `loc()`. |
| `…/logic/Loc.kt` (izmena) | Novi ključevi za stringove koji su do sada bili zakucani. |
| `…/ui/{LearnView,MoveHistoryView,SettingsView,UiComponents}.kt` (izmena) | Preostalih 10 zakucanih stringova kroz `loc()`. |
| `app/src/test/java/com/veljkoni/chessko/PuzzleRatingTest.kt` **(nov)** | JVM testovi Elo formule i prozora. |
| `app/src/test/java/com/veljkoni/chessko/LocTest.kt` **(nov)** | JVM test da svaki unos rečnika ima svih 8 jezika. |
| `app/src/androidTest/java/com/veljkoni/chessko/PuzzleRepositoryTest.kt` **(nov)** | Instrumentisani testovi integriteta baze. |

---

## Task 1: Baza u `assets` i `PuzzleRepository`

**Files:**
- Create: `ChesskoAndroid/app/src/main/assets/puzzles.sqlite`
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/PuzzleRepository.kt`
- Create: `ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/PuzzleRepositoryTest.kt`

**Interfaces:**
- Consumes: `ChessPuzzle(puzzleId, fen, moves, rating, themes)` — postojeći model, **ne menja se**.
- Produces (Taskovi 2–3 zavise od ovoga):
  - `class PuzzleRepository(context: Context)`
  - `fun count(): Int`
  - `fun puzzle(id: String): ChessPuzzle?`
  - `fun dailyPuzzle(dayIndex: Long): ChessPuzzle?`
  - `fun randomPuzzle(ratingRange: IntRange, excluding: Set<String>): ChessPuzzle?`
  - `fun allPuzzlesOrderedById(): List<ChessPuzzle>`

### Zašto se baza kopira, a ne čita iz `assets`

`android.database.sqlite` traži pravu putanju na disku. Asseti su unutar APK-a (i mogu biti kompresovani), pa se ne mogu otvoriti kao baza. Isti problem je već rešen u `StockfishEngine.kt:71-73` za `.nnue` mreže — kopiranje u `context.filesDir` pri prvoj upotrebi. Ovaj task koristi **isti obrazac**, sa jednom dopunom: kopija se preskače ako fajl već postoji **i** ima očekivanu veličinu, da polovična kopija (prekinuta instalacija, pun disk) ne ostane zauvek.

- [ ] **Step 1: Prekopirati bazu iz iOS-a i dokazati da je identična**

```bash
cd /Users/veljkoodobasic/Documents/Projects/Chess
cp Chessko/puzzles.sqlite ChesskoAndroid/app/src/main/assets/puzzles.sqlite
shasum -a 256 Chessko/puzzles.sqlite ChesskoAndroid/app/src/main/assets/puzzles.sqlite
```
Expected: **oba heša identična**, počinju sa `3cd00a83`. Ako se razlikuju, stani — baza se ne sme transformisati.

- [ ] **Step 2: Napisati instrumentisani test koji pada**

`ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/PuzzleRepositoryTest.kt`:

```kotlin
package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.PuzzleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PuzzleRepositoryTest {

    private fun repo(): PuzzleRepository =
        PuzzleRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun databaseShipsWithExactlyTwentyThousandPuzzles() {
        assertEquals(20000, repo().count())
    }

    @Test
    fun everyPuzzleHasNonEmptyFenAndAtLeastOneMove() {
        // Prolazi CELU bazu, ne uzorak. Uzorak od 500 redova hvata pokvaren
        // red u ~2,5% pokretanja, sto znaci prakticno nikad.
        var checked = 0
        for (p in repo().allPuzzlesOrderedById()) {
            assertTrue("prazan FEN: ${p.puzzleId}", p.fen.isNotBlank())
            assertTrue("bez poteza: ${p.puzzleId}", p.uciMoves.isNotEmpty())
            assertTrue("rejting van opsega: ${p.puzzleId}", p.rating in 600..2200)
            checked++
        }
        assertEquals(20000, checked)
    }

    @Test
    fun dailyPuzzleIsDeterministicForTheSameDay() {
        val r = repo()
        val a = r.dailyPuzzle(20000)
        val b = r.dailyPuzzle(20000)
        assertNotNull(a)
        assertEquals(a!!.puzzleId, b!!.puzzleId)
    }

    @Test
    fun differentDaysGiveDifferentPuzzles() {
        val r = repo()
        assertTrue(r.dailyPuzzle(20000)!!.puzzleId != r.dailyPuzzle(20001)!!.puzzleId)
    }

    @Test
    fun randomPuzzleRespectsRatingRange() {
        val p = repo().randomPuzzle(800..1000, emptySet())
        assertNotNull(p)
        assertTrue(p!!.rating in 800..1000)
    }

    @Test
    fun randomPuzzleRespectsExclusionSet() {
        val r = repo()
        val first = r.randomPuzzle(800..1000, emptySet())!!
        val second = r.randomPuzzle(800..1000, setOf(first.puzzleId))
        assertNotNull(second)
        assertTrue(second!!.puzzleId != first.puzzleId)
    }

    @Test
    fun emptyRangeReturnsNullInsteadOfThrowing() {
        // Prazan presek se u aplikaciji desava (rejting igraca ume da odluta),
        // i mora da vrati null, ne da srusi ekran.
        assertEquals(null, repo().randomPuzzle(2300..2400, emptySet()))
    }

    @Test
    fun puzzleByIdRoundTrips() {
        val r = repo()
        val any = r.dailyPuzzle(12345)!!
        assertEquals(any.puzzleId, r.puzzle(any.puzzleId)!!.puzzleId)
    }
}
```


- [ ] **Step 3: Pokrenuti test i videti da pada**

```bash
export ANDROID_HOME=~/Library/Android/sdk
$ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim &
$ANDROID_HOME/platform-tools/adb wait-for-device
cd ChesskoAndroid && ./gradlew connectedDebugAndroidTest
```
Expected: FAIL sa greškom kompajliranja `Unresolved reference: PuzzleRepository`.

- [ ] **Step 4: Napisati `PuzzleRepository.kt`**

```kotlin
package com.veljkoni.chessko.logic

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.veljkoni.chessko.models.ChessPuzzle
import java.io.File

// MARK: - Puzzle Repository
//
// Cita `puzzles.sqlite` — 20.000 Lichess zadataka, licenca CC0, ISTA baza koju
// isporucuje i iOS (bajt-identicna). Aplikacija od ove faze nema nijedan mrezni
// poziv za zadatke i radi u avionskom rezimu.
//
// Baza se KOPIRA iz `assets` u `filesDir` pri prvoj upotrebi: `SQLiteDatabase`
// trazi pravu putanju na disku, a asseti su unutar APK-a. Isti obrazac koji
// `StockfishEngine` vec koristi za `.nnue` mreze.
class PuzzleRepository(context: Context) {

    private val db: SQLiteDatabase

    init {
        val target = File(context.filesDir, DB_NAME)
        val expected = context.assets.openFd(DB_NAME).length
        // Kopira se samo ako fajla nema ILI je nepotpun. Bez provere velicine
        // bi prekinuta prva kopija (pun disk, ubijen proces) ostala zauvek kao
        // pokvarena baza koju niko vise ne bi prepisao.
        if (!target.exists() || target.length() != expected) {
            context.assets.open(DB_NAME).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        db = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun count(): Int =
        db.rawQuery("SELECT COUNT(*) FROM puzzles", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun puzzle(id: String): ChessPuzzle? =
        db.rawQuery("$SELECT_COLS WHERE id = ?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) c.toPuzzle() else null
        }

    /// Zadatak dana. `dayIndex` je broj dana od epohe — isti ulaz daje isti
    /// zadatak, bez cuvanja stanja. Deterministicno preko `ORDER BY id`, pa se
    /// ne oslanja na redosled umetanja.
    fun dailyPuzzle(dayIndex: Long): ChessPuzzle? {
        val n = count()
        if (n == 0) return null
        val offset = ((dayIndex % n) + n) % n   // i za negativan dayIndex
        return db.rawQuery("$SELECT_COLS ORDER BY id LIMIT 1 OFFSET ?",
                           arrayOf(offset.toString())).use { c ->
            if (c.moveToFirst()) c.toPuzzle() else null
        }
    }

    /// Nasumican zadatak u opsegu rejtinga, bez onih koji su vec resavani.
    ///
    /// `excluding` se vezuje kao JEDAN parametar po id-ju. Na tavanici od
    /// 20.000 resenih to je 20k vezivanja po dodiru, protiv SQLite granice od
    /// 999 parametara na starijim verzijama — zato se skup ovde SECE na
    /// `MAX_EXCLUDED` najskorijih. Ponovljen zadatak je bolji od pada.
    fun randomPuzzle(ratingRange: IntRange, excluding: Set<String>): ChessPuzzle? {
        val excl = excluding.take(MAX_EXCLUDED)
        val holes = if (excl.isEmpty()) "" else
            " AND id NOT IN (${excl.joinToString(",") { "?" }})"
        val args = (listOf(ratingRange.first.toString(), ratingRange.last.toString()) + excl)
            .toTypedArray()
        return db.rawQuery(
            "$SELECT_COLS WHERE rating BETWEEN ? AND ?$holes ORDER BY RANDOM() LIMIT 1", args
        ).use { c -> if (c.moveToFirst()) c.toPuzzle() else null }
    }

    /// Svi zadaci, deterministicno po `id`. Postoji zbog testa integriteta koji
    /// prolazi CELU bazu; aplikacija ga ne zove.
    fun allPuzzlesOrderedById(): List<ChessPuzzle> {
        val out = ArrayList<ChessPuzzle>(20000)
        db.rawQuery("$SELECT_COLS ORDER BY id", null).use { c ->
            while (c.moveToNext()) out.add(c.toPuzzle())
        }
        return out
    }

    private fun android.database.Cursor.toPuzzle() = ChessPuzzle(
        puzzleId = getString(0),
        fen = getString(1),
        moves = getString(2),
        rating = getInt(3),
        themes = getString(4)
    )

    companion object {
        private const val DB_NAME = "puzzles.sqlite"
        private const val SELECT_COLS = "SELECT id, fen, moves, rating, themes FROM puzzles"
        /// Granica broja iskljucenih id-jeva, vidi `randomPuzzle`.
        const val MAX_EXCLUDED = 900
    }
}
```

- [ ] **Step 5: Pokrenuti instrumentisane testove**

```bash
cd ChesskoAndroid && ./gradlew connectedDebugAndroidTest
```
Expected: **8 testova prolazi.**

- [ ] **Step 6: Potvrditi da baza stvarno stiže u APK**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep puzzles.sqlite
```
Expected: jedan red, veličina ~7 MB. Ako ga nema, asset nije upakovan i testovi gore lažu (čitali bi staru kopiju iz `filesDir`).

- [ ] **Step 7: Commit**

```bash
git add ChesskoAndroid/app/src/main/assets/puzzles.sqlite \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/PuzzleRepository.kt \
        ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/PuzzleRepositoryTest.kt
git commit -m "Faza 6a, Task 1: puzzles.sqlite u assets i PuzzleRepository"
```

---

## Task 2: Elo rejting igrača

**Files:**
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/PuzzleRating.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/StatsManager.kt`
- Create: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/PuzzleRatingTest.kt`

**Interfaces:**
- Produces:
  - `object PuzzleRating { const val MIN = 600; const val MAX = 2200; const val START = 800 }`
  - `fun PuzzleRating.newRating(current: Int, puzzleRating: Int, solved: Boolean): Int`
  - `fun PuzzleRating.practiceWindow(playerRating: Int): IntRange`
  - `StatsManager.puzzleRating: Int` i `StatsManager.applyPuzzleResult(puzzleRating: Int, solved: Boolean)`

**`PuzzleRating.kt` NE sme da uvozi ništa iz `android.*`** — to je jedini razlog zašto je odvojen od `StatsManager`-a, koji uvozi `Context` i `SharedPreferences` i zato nije JVM-testabilan.

- [ ] **Step 1: Napisati JVM testove koji padaju**

`ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/PuzzleRatingTest.kt`:

```kotlin
package com.veljkoni.chessko

import com.veljkoni.chessko.logic.PuzzleRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleRatingTest {

    // Vrednosti su iste one koje iOS vec ima u RatingTests.swift.
    // E = 1/(1 + 10^((Rp-R)/400)), R' = R + 32*(S-E)
    @Test fun solvedEqualRatingGains16() =
        assertEquals(816, PuzzleRating.newRating(800, 800, true))

    @Test fun failedEqualRatingLoses16() =
        assertEquals(784, PuzzleRating.newRating(800, 800, false))

    @Test fun solvedHarderPuzzleGainsMore() =
        assertEquals(832, PuzzleRating.newRating(800, 1600, true))

    @Test fun failedEasierPuzzleLosesMore() =
        assertEquals(771, PuzzleRating.newRating(800, 400, false))

    @Test
    fun gainStaysPositiveAndNeverGrowsAcrossManySolves() {
        // Svojstvo koje Elo garantuje: prirast je pozitivan i NE raste.
        // `<=`, ne `<` — celobrojno zaokruzivanje pravi platoe (16, 16, 15...),
        // pa bi strogo opadanje palo iako je formula ispravna.
        var r = 800
        var prev = Int.MAX_VALUE
        repeat(100) {
            val next = PuzzleRating.newRating(r, 800, true)
            val gain = next - r
            assertTrue("prirast mora biti pozitivan, bio je $gain", gain > 0)
            assertTrue("prirast ne sme da raste", gain <= prev)
            prev = gain
            r = next
        }
        assertTrue("posle 100 resenih rejting je $r", r in 1200..1400)
    }

    @Test
    fun practiceWindowNeverInvertsForLowRatings() {
        // Rejting igraca nije ogranicen odozdo; dug niz neuspeha ga vodi ka ~80.
        // Naivan prozor bi tada bio -120..180 (baza pocinje od 600), a klampovanje
        // SAMO donje granice dalo bi 600..180 — obrnut opseg.
        val w = PuzzleRating.practiceWindow(80)
        assertTrue("opseg ne sme biti obrnut: $w", w.first <= w.last)
        assertEquals(PuzzleRating.MIN, w.first)
    }

    @Test
    fun practiceWindowForRatingFarAboveCeilingDoesNotInvertAndStaysAboveFloor() {
        // Naivno: (3000-200)..(3000+100) = 2800..3100 — iznad baze u celosti.
        // `lo` (2800) je vec iznad MAX (2200) pre nego sto se `hi` klampuje, pa
        // formula vraca 2800..2800: validan opseg koji jednostavno ne pogadja
        // nijedan red. Na to se oslanja progresivno prosirenje u `nextPuzzle()`.
        //
        // NE tvrdi `w.last <= MAX` — to bi protivrecilo samoj formuli. Isti test
        // na iOS-u (`practiceRatingWindowForRatingFarAboveCeiling…`) takodje
        // namerno izostavlja tu tvrdnju.
        val w = PuzzleRating.practiceWindow(3000)
        assertTrue(w.first <= w.last)
        assertTrue(w.first >= PuzzleRating.MIN)
    }

    @Test
    fun practiceWindowIsCenteredBelowThePlayer() {
        // -200 / +100: zadaci malo ispod rejtinga se cesce pogadjaju, pa je
        // vezbanje prijatnije nego kad je prozor centriran.
        assertEquals(1000..1300, PuzzleRating.practiceWindow(1200))
    }
}
```

- [ ] **Step 2: Pokrenuti i videti da padaju**

```bash
export ANDROID_HOME=~/Library/Android/sdk
cd ChesskoAndroid && ./gradlew testDebugUnitTest --tests '*PuzzleRatingTest*'
```
Expected: FAIL, `Unresolved reference: PuzzleRating`.

- [ ] **Step 3: Napisati `PuzzleRating.kt`**

```kotlin
package com.veljkoni.chessko.logic

import kotlin.math.pow
import kotlin.math.roundToInt

// MARK: - Elo rejting za zadatke
//
// Namerno BEZ ijednog `android.*` uvoza: `StatsManager` uvozi `Context` i
// `SharedPreferences` pa se ne moze testirati obicnim JVM testom. Ova
// racunica moze, i mora — ona odredjuje koje zadatke korisnik uopste vidi.
object PuzzleRating {

    /// Granice isporucene baze (`puzzles.sqlite`).
    const val MIN = 600
    const val MAX = 2200
    const val START = 800
    private const val K = 32

    /// `E = 1/(1 + 10^((Rp-R)/400))`, `R' = R + K*(S-E)`.
    fun newRating(current: Int, puzzleRating: Int, solved: Boolean): Int {
        val expected = 1.0 / (1.0 + 10.0.pow((puzzleRating - current) / 400.0))
        val score = if (solved) 1.0 else 0.0
        return current + (K * (score - expected)).roundToInt()
    }

    /// Prozor iz kog se biraju zadaci za vezbanje: malo ispod rejtinga igraca.
    ///
    /// Klampuju se OBE granice, i to donja pa gornja. Rejting igraca nije
    /// ogranicen — dug niz neuspeha ga vodi ka ~80 — pa bi naivan prozor bio
    /// `-120..180`, a klampovanje samo donje granice dalo bi `600..180`.
    /// Obrnut `IntRange` ne puca kao `ClosedRange` u Swift-u, ali tiho ne
    /// vraca nijedan red, sto je gore: ekran ostane prazan bez poruke.
    fun practiceWindow(playerRating: Int): IntRange {
        val lo = maxOf(MIN, playerRating - 200)
        val hi = maxOf(lo, minOf(MAX, playerRating + 100))
        return lo..hi
    }
}
```

- [ ] **Step 4: Pokrenuti testove**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest --tests '*PuzzleRatingTest*'
```
Expected: **8 testova prolazi.**

- [ ] **Step 5: Dodati rejting u `StatsManager`**

U `StatsManager.kt`, uz postojeće `puzzlesSolved` (oko linije 37):

```kotlin
    // Elo rejting igraca za zadatke. Pocetna vrednost je PuzzleRating.START,
    // ne 0 — bez toga bi nov korisnik dobijao samo najlakse zadatke dok se ne
    // popne, a `resetStats()` bi ga vratio na nulu umesto na pocetak.
    var puzzleRating by mutableIntStateOf(prefs.getInt("puzzleRating", PuzzleRating.START))
        private set

    fun applyPuzzleResult(puzzleElo: Int, solved: Boolean) {
        val next = PuzzleRating.newRating(this.puzzleRating, puzzleElo, solved)
        this.puzzleRating = next
        prefs.edit().putInt("puzzleRating", next).apply()
    }
```


U `resetStats()` (ako postoji) dodati `puzzleRating = PuzzleRating.START` i upis u `prefs`.

- [ ] **Step 6: Build i ceo skup JVM testova**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest && ./gradlew assembleDebug
```
Expected: svi testovi prolaze (**10 zatečenih** — 9 u `EngineTest.kt` + 1 u `ExampleUnitTest.kt` — **+ 8 novih = 18**), `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/PuzzleRating.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/StatsManager.kt \
        ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/PuzzleRatingTest.kt
git commit -m "Faza 6a, Task 2: Elo rejting igraca za zadatke"
```

---

## Task 3: `PuzzleViewModel` bez mreže

**Files:**
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/viewmodels/PuzzleViewModel.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PuzzleView.kt`

**Interfaces:**
- Consumes: `PuzzleRepository` (Task 1), `PuzzleRating` i `StatsManager.applyPuzzleResult` (Task 2).
- Produces: `PuzzleViewModel.nextPuzzle()`, `PuzzleViewModel.mode` (`DAILY` / `PRACTICE`).

- [ ] **Step 1: Dodati repozitorijum u ViewModel**

`PuzzleViewModel` je `AndroidViewModel(application)` (linija 33), pa `Context` već ima. Uz
postojeći `private val statsManager = StatsManager.getInstance(application)` (linija 38) dodati:

```kotlin
    // `by lazy`: prva upotreba kopira 7 MB iz `assets` u `filesDir`, pa se to
    // ne radi u konstruktoru ViewModel-a (glavna nit pri otvaranju taba).
    private val repository by lazy { PuzzleRepository(getApplication()) }
```
uz `import com.veljkoni.chessko.logic.PuzzleRepository` i `import com.veljkoni.chessko.logic.PuzzleRating`.

- [ ] **Step 2: Obrisati mrežni poziv**

U `PuzzleViewModel.kt` (oko linije 135–160) ceo blok koji gradi
`URL("https://chess-puzzles-api.vercel.app/…")`, otvara `HttpURLConnection` i parsira `JSONArray`
zameniti čitanjem iz repozitorijuma:

```kotlin
        val epochStart = LocalDate.of(1970, 1, 1)
        val dayIndex = ChronoUnit.DAYS.between(epochStart, selectedDate)

        viewModelScope.launch(Dispatchers.IO) {
            // Citanje iz lokalne baze je brzo, ali ostaje na IO niti: prvo
            // pokretanje kopira 7 MB iz `assets` u `filesDir`.
            val puzzle = repository.dailyPuzzle(dayIndex)
            withContext(Dispatchers.Main) {
                if (puzzle != null) setupPuzzle(puzzle) else showUnavailable()
            }
        }
```

Ukloniti i uvoze koji su ostali bez korisnika (`java.net.URL`, `java.net.HttpURLConnection`,
`org.json.JSONArray`, `java.io.BufferedReader`, `java.io.InputStreamReader`).

- [ ] **Step 3: Dodati režim i „Sledeći zadatak"**

```kotlin
    enum class PuzzleMode { DAILY, PRACTICE }

    var mode by mutableStateOf(PuzzleMode.DAILY)
        private set

    /// Vezbanje bez kraja: bira zadatak po rejtingu igraca, iskljucujuci vec
    /// resene. Prozor se progresivno siri — inace bi korisnik koji je resio sve
    /// u svom opsegu dobio prazan ekran bez objasnjenja.
    fun nextPuzzle() {
        mode = PuzzleMode.PRACTICE
        viewModelScope.launch(Dispatchers.IO) {
            val r = statsManager.puzzleRating
            val windows = listOf(
                PuzzleRating.practiceWindow(r),
                maxOf(PuzzleRating.MIN, r - 400)..minOf(PuzzleRating.MAX, r + 400),
                PuzzleRating.MIN..PuzzleRating.MAX
            )
            var found = windows.firstNotNullOfOrNull {
                repository.randomPuzzle(it, solvedPuzzleIds)
            }
            // Poslednje pribeziste: korisnik je resio sve. Bolje ponovljen
            // zadatak nego prazan ekran.
            if (found == null) found = repository.randomPuzzle(PuzzleRating.MIN..PuzzleRating.MAX, emptySet())
            withContext(Dispatchers.Main) {
                if (found != null) setupPuzzle(found) else showUnavailable()
            }
        }
    }
```

`solvedPuzzleIds` je `MutableSet<String>` učitan iz `SharedPreferences` (ključ `solvedPuzzleIds`),
u koji se upisuje **tek kad je zadatak stvarno rešen**, u oba režima. Prikaz rešenja ga **ne** upisuje.

Na mestu gde se zadatak označi kao rešen dodati i:
```kotlin
        statsManager.applyPuzzleResult(currentPuzzle?.rating ?: return, solved = true)
```
i simetrično `solved = false` na mestu pogrešnog poteza i prikaza rešenja.

- [ ] **Step 4: Dugme u `PuzzleView.kt`**

U grani koja se prikazuje kad je zadatak rešen, **iznad** postojećih kontrola:

```kotlin
        Button(
            onClick = { viewModel.nextPuzzle() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = loc("Sledeći zadatak"), fontWeight = FontWeight.SemiBold)
        }
```

Kontrole za promenu datuma i poruku „Završio si zadatak za danas!" gejtovati na
`viewModel.mode == PuzzleViewModel.PuzzleMode.DAILY` — u režimu vežbanja one tvrde neistinu.

- [ ] **Step 5: Dokazati da mreže više nema**

```bash
cd ChesskoAndroid
grep -rn "HttpURLConnection\|chess-puzzles-api\|URL(" app/src/main/java --include='*.kt'
```
Expected: **nijedan pogodak u `viewmodels/`**; jedini preostali su dva `Uri.parse("https://…")`
u `SettingsView.kt` (linkovi ka Stockfish-u i Lichess-u u sekciji „O aplikaciji", GPLv3 obaveza).

- [ ] **Step 6: Build i testovi**

```bash
./gradlew testDebugUnitTest && ./gradlew assembleDebug
```
Expected: 18 testova prolazi, `BUILD SUCCESSFUL`.

- [ ] **Step 7: Provera u pokrenutoj aplikaciji, bez prozora**

```bash
export ANDROID_HOME=~/Library/Android/sdk
$ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim &
$ANDROID_HOME/platform-tools/adb wait-for-device
./gradlew installDebug
# avionski rezim: dokaz da zadaci rade bez mreze
$ANDROID_HOME/platform-tools/adb shell svc wifi disable
$ANDROID_HOME/platform-tools/adb shell svc data disable
$ANDROID_HOME/platform-tools/adb shell am start -n com.veljkoni.chessko/.MainActivity
$ANDROID_HOME/platform-tools/adb exec-out screencap -p > /private/tmp/claude-501/-Users-veljkoodobasic-Documents-Projects-Chess/8c4918e4-1a29-42d9-8d21-a8c5b122d5c5/scratchpad/zadaci-offline.png
```
Snimiti ekran Zadataka **sa učitanim zadatkom dok je mreža isključena**. To je dokaz koji se traži;
screenshot sa uključenom mrežom ne dokazuje ništa. Na kraju vratiti mrežu
(`svc wifi enable`, `svc data enable`) i ugasiti emulator (`adb emu kill`).

- [ ] **Step 8: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/viewmodels/PuzzleViewModel.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PuzzleView.kt
git commit -m "Faza 6a, Task 3: Zadaci citaju lokalnu bazu umesto mreze"
```

---

## Task 4: Spec 4.5 — mešanje jezika

**Files:**
- Modify: `…/ui/PuzzleView.kt`, `…/ui/LearnView.kt`, `…/ui/MoveHistoryView.kt`, `…/ui/SettingsView.kt`, `…/ui/UiComponents.kt`
- Modify: `…/models/ChessPuzzle.kt`
- Modify: `…/logic/Loc.kt`
- Create: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/LocTest.kt`

### Šta je stvarni uzrok — provereno, ne pretpostavljeno

Spec 4.5 kaže: „Na ekranu Zadaci istovremeno stoje „Rejting: 1494", „Prikaži rešenje" i
„Find the right move for Black"."

Uzrok **nije** mrežni API. `statusMessage` **jeste** lokalizovan — ključ
„Pronađi pravi potez za crne" postoji u `Loc.kt` sa svih 8 jezika. Ono što nije lokalizovano je
**hrom oko njega**: u celom UI-ju ima **15 zakucanih srpskih stringova** koji zaobilaze `loc()`.
Na engleskom uređaju status se prevede, a „Rejting: " i „Prikaži rešenje" ostanu srpski — tačno
slika iz spec-a.

Raspodela (`text = "…"` bez `loc()`): `LearnView.kt` 5, `PuzzleView.kt` 5, `SettingsView.kt` 2,
`UiComponents.kt` 2, `MoveHistoryView.kt` 1.

Uz to, `ChessPuzzle.difficultyLabel` (`ChessPuzzle.kt:16-21`) vraća **zakucan srpski**
(„Lako"/„Srednje"/„Teško") i koristi se u `PuzzleView.kt:359`. iOS isti kod ima kroz `Loc(...)`.
Sva tri ključa **već postoje** u `Loc.kt`, pa je popravka samo omotavanje.

- [ ] **Step 1: Test koji tvrdi da rečnik nije krnj**

`ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/LocTest.kt`:

```kotlin
package com.veljkoni.chessko

import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.loc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocTest {

    private val languages = setOf("sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi")

    @Test
    fun everyEntryHasAllEightLanguages() {
        // `Loc.get` na nepoznat kljuc vraca SAM KLJUC, dakle srpski tekst, i to
        // tiho. Krnj unos se zato ne vidi kao greska nego kao "mesanje jezika" —
        // tacno kvar iz spec-a 4.5. Ovaj test je jedino mesto koje moze da vikne.
        val missing = Loc.dictionaryForTests
            .filterValues { !it.keys.containsAll(languages) }
            .map { (key, langs) -> "$key -> nedostaje ${languages - langs.keys}" }
        assertTrue("krnji unosi:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun difficultyLabelsAreTranslated() {
        Loc.setLanguage("en")
        assertEquals("Easy", loc("Lako"))
        Loc.setLanguage("sr")
        assertEquals("Lako", loc("Lako"))
    }

    @Test
    fun puzzleScreenChromeIsTranslated() {
        // Bas stringovi iz spec-a 4.5.
        Loc.setLanguage("en")
        for (key in listOf("Rejting: ", "Prikaži rešenje", "Pokušaj ponovo",
                           "Greška pri učitavanju zadatka", "Sledeći zadatak")) {
            assertTrue("kljuc nije preveden na engleski: $key", loc(key) != key)
        }
        Loc.setLanguage("sr")
    }
}
```

`Loc.kt` mora da izloži rečnik testu — dodati uz `private val dictionary`:

```kotlin
    /// Samo za testove: `dictionary` je private, a test mora da proveri da
    /// nijedan unos nije krnj. Bez ovoga bi test morao da zna svaki kljuc
    /// unapred, pa bi propustio bas one koji se naknadno dodaju.
    internal val dictionaryForTests: Map<String, Map<String, String>> get() = dictionary
```

- [ ] **Step 2: Pokrenuti i videti da pada**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest --tests '*LocTest*'
```
Expected: FAIL — `puzzleScreenChromeIsTranslated` pada jer ključevi „Rejting: ",
„Pokušaj ponovo" i „Sledeći zadatak" još ne postoje (`loc(key) == key`).

- [ ] **Step 3: Dodati ključeve koji nedostaju u `Loc.kt`**

U `dictionary`, uz ostale unose:

```kotlin
        "Rejting: " to mapOf("sr" to "Rejting: ", "en" to "Rating: ", "fr" to "Classement : ", "de" to "Wertung: ", "it" to "Punteggio: ", "ru" to "Рейтинг: ", "zh-Hans" to "等级分：", "hi" to "रेटिंग: "),
        "Pokušaj ponovo" to mapOf("sr" to "Pokušaj ponovo", "en" to "Try again", "fr" to "Réessayer", "de" to "Erneut versuchen", "it" to "Riprova", "ru" to "Попробовать снова", "zh-Hans" to "再试一次", "hi" to "फिर कोशिश करें"),
        "Sledeći zadatak" to mapOf("sr" to "Sledeći zadatak", "en" to "Next puzzle", "fr" to "Problème suivant", "de" to "Nächste Aufgabe", "it" to "Prossimo problema", "ru" to "Следующая задача", "zh-Hans" to "下一题", "hi" to "अगली पहेली"),
```

Ključeve „Prikaži rešenje" i „Greška pri učitavanju zadatka" **prvo proveri** — možda već postoje:
```bash
for k in "Prikaži rešenje" "Greška pri učitavanju zadatka"; do
  echo "$k: $(grep -c "\"$k\" to mapOf" app/src/main/java/com/veljkoni/chessko/logic/Loc.kt)"
done
```
Ako je `0`, dodaj ih po istom obrascu; ako je `1`, ne dupliraj — dupliran ključ u `mapOf` tiho
pobeđuje poslednjim unosom.

- [ ] **Step 4: Omotati svih 15 zakucanih stringova**

Naći ih i zameniti `text = "…"` sa `text = loc("…")`:

```bash
cd ChesskoAndroid
grep -rn 'text = "' app/src/main/java/com/veljkoni/chessko/ui/*.kt | grep -vE 'text = "[^A-Za-zČĆŽŠĐčćžšđ]*"'
```

Emoji-prefikse **izdvojiti iz ključa**: `"💡 Prikaži rešenje"` postaje
`"💡 " + loc("Prikaži rešenje")`, jer je ključ u katalogu čist tekst bez emojija. Stringovi koji
su sami po sebi samo simbol (`"⚠️"`, `"◀️"`, `"✅"`, `"▶️"`) se **ne** prevode i ostaju kakvi jesu.

- [ ] **Step 5: `difficultyLabel` kroz `loc()`**

U `ChessPuzzle.kt`:

```kotlin
    val difficultyLabel: String
        get() = when {
            rating < 1200 -> loc("Lako")
            rating in 1200..1599 -> loc("Srednje")
            else -> loc("Teško")
        }
```
uz `import com.veljkoni.chessko.logic.loc`.

- [ ] **Step 6: Testovi i dokaz da zakucanih stringova više nema**

```bash
./gradlew testDebugUnitTest
grep -rn 'text = "' app/src/main/java/com/veljkoni/chessko/ui/*.kt | grep -vE 'text = "[^A-Za-zČĆŽŠĐčćžšđ]*"'
```
Expected: **21 test prolazi** (18 + 3 nova), a `grep` ne vraća nijedan red.

- [ ] **Step 7: Vizuelni dokaz da 4.5 više ne važi**

Na emulatoru **bez prozora**, prebaciti jezik na engleski i snimiti ekran Zadataka. Na slici
ne sme biti nijedne srpske reči. Snimiti i srpsku varijantu radi poređenja.

```bash
$ANDROID_HOME/platform-tools/adb exec-out screencap -p > /private/tmp/claude-501/-Users-veljkoodobasic-Documents-Projects-Chess/8c4918e4-1a29-42d9-8d21-a8c5b122d5c5/scratchpad/zadaci-en.png
```

- [ ] **Step 8: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/
git commit -m "Faza 6a, Task 4: popravljeno mesanje jezika iz spec-a 4.5"
```

---

## Task 5: Dokumentacija i zatvaranje faze

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Dopuniti sekciju „Baza zadataka"**

Dodati da je ista baza od Faze 6a i na Androidu, bajt-identična (`sha256` `3cd00a83…`), da se kopira
iz `assets` u `filesDir` pri prvoj upotrebi jer `SQLiteDatabase` traži putanju na disku, i da
Android čita kroz `android.database.sqlite` — **bez ijedne nove Gradle zavisnosti**.

- [ ] **Step 2: Ažurirati „Poznata ograničenja"**

Ukloniti unos o mešanju jezika ako postoji, ili ga zameniti opisom šta je **stvarno** bilo:
`statusMessage` je bio lokalizovan, a hrom oko njega nije — 15 zakucanih srpskih stringova.
Zapisati i da `Loc.get` na nepoznat ključ **tiho vraća sam ključ**, pa krnj unos izgleda kao
mešanje jezika, i da to sada hvata `LocTest.everyEntryHasAllEightLanguages`.

- [ ] **Step 3: Zapisati stanje Android porta**

Tabela šta je preneto a šta nije: Faza 0 ✅ (rokada + 9 testova), 6a ✅ (offline zadaci, rejting,
4.5), a Faze 1 (dizajn sistem), 3 (lekcije u JSON), 4 (Put) i 5 (analiza) **nisu**. Bez ovoga
sledeći čitalac ne zna gde je Android u odnosu na iOS.

- [ ] **Step 4: Changelog unos** na **dno** sekcije (fajl ide rastuće po datumu).

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "Faza 6a: dokumentacija offline zadataka na Androidu"
```

---

## Završna provera faze

- [ ] `./gradlew testDebugUnitTest` prolazi **21/21**
- [ ] `./gradlew connectedDebugAndroidTest` prolazi **8/8** (emulator, bez prozora)
- [ ] `./gradlew assembleDebug` → `BUILD SUCCESSFUL`
- [ ] `puzzles.sqlite` je u APK-u i bajt-identična iOS verziji
- [ ] `grep` za `HttpURLConnection` u `viewmodels/` ne vraća ništa
- [ ] Zadaci rade sa **isključenom mrežom** — snimljeno
- [ ] Ekran Zadataka na engleskom nema nijednu srpsku reč — snimljeno
- [ ] `git status --short` prazan
- [ ] `Chessko/` (iOS) nije diran: `git diff --stat main..HEAD -- Chessko/` je prazan

## Rizici

- **7 MB u `assets` znači 7 MB veći APK.** To je cena rada bez mreže i ista je koju iOS već plaća.
  Ako ikad zasmeta, baza se ne sme filtrirati bez iste izmene na iOS-u — deljenje bajt-identične
  baze je ono što drži dve platforme na istim zadacima.
- **Prva upotreba kopira 7 MB.** Na sporom uređaju to je vidljiv zastoj; zato je čitanje na
  `Dispatchers.IO`. Ako se pokaže kao problem, kopiranje se pomera u pozadinski posao pri prvom
  pokretanju aplikacije — ali tek pošto se izmeri, ne pretpostavi.
- **Instrumentisani testovi traže emulator.** Bez njega se integritet baze ne može proveriti;
  JVM testovi pokrivaju samo čistu računicu. Ako emulator nije dostupan, to se **prijavljuje kao
  neprovereno**, ne zaobilazi.
- **`Loc.get` tiho vraća ključ.** Svaki nov string bez unosa u rečnik ponavlja kvar 4.5 i ne vidi
  se ni u jednom build-u. `LocTest` je jedina zaštita; ne sme se isključiti.
