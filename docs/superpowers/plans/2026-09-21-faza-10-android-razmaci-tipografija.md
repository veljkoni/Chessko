# Faza 10 — Android: dovršiti dizajn sistem (razmaci, radijusi, tipografija)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zatvoriti poslednji deo dizajn sistema koji su Faze 6d-1 i 6d-2 svesno odložile —
`DS.Space`, `DS.Radius`, `DS.maxBoardSide` i tipografska skala dobijaju pozivna mesta, a tamo gde
skala ne opisuje ekrane donosi se **izričita presuda** umesto tihe zamene.

**Architecture:** Tri odvojene odluke, ne jedan sweep. (1) `DS.maxBoardSide` je jedini token čiji
izostanak ima posledicu za korisnika danas — tabla na Androidu nema gornju granicu, iOS je ima na
pet mesta. (2) Tipografska skala **ne opisuje ekrane** (30% poklapanja), pa se prvo presuđuje da
li se menja skala ili ekrani. (3) Razmaci i radijusi se dele na **dokazivo bezbedan** deo
(vrednost već pada na skalu → zamena ne menja nijedan piksel) i **sporan** deo (ne pada → zamena
menja ekran, traži presudu). Svaka presuda se upisuje u kod, ne samo u izveštaj.

**Tech Stack:** Kotlin 2.x, Jetpack Compose (Material 3), Gradle KTS. Bez ijedne nove zavisnosti.
Testovi: JUnit4 na JVM (`testDebugUnitTest`), instrumentisani samo gde JVM ne dopire.

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md`, sekcija 5.6 („Tokeni: boje,
razmaci, radijusi, tipografska skala") i „Faza 1 — Dizajn sistem".

## Global Constraints

- **`Chessko/` se ne dira** — ni jedan iOS fajl, uključujući `Chessko/Content/lessons/`
  (lekcijski JSON je deljen bajt-identično; menja se samo na jednom mestu).
  Provera: `git diff --stat main..HEAD -- Chessko Chessko.xcodeproj` mora biti prazan.
- **Nijedna nova Gradle zavisnost** (`app/build.gradle.kts`, `gradle/libs.versions.toml`).
- **`BoardView.kt` i `BoardTheme.kt` moraju imati nula `DS.` pogodaka** — tabla je sadržaj, ne
  hrom. Ovo važi i posle ove faze: razmaci unutar table se **ne** tokenizuju.
- **Jedanaest boja sata prate STRANU u igri, ne temu** (`ChessClockView.kt`) — ne dirati.
- **Emulator: `-gpu host`, NIKAD `-gpu off`** (izmereno 745%/503% naspram 216%/73%). Diže se
  **jednom**, u Task-u 5, i gasi odmah: `adb emu kill` + `./gradlew --stop` + `pgrep -f
  qemu-system` prazan. **Ako te bilo šta prekine — prvo ugasi emulator.**
- **Broj testova se čita iz XML-a**, ne iz izlaznog koda. JVM:
  `app/build/test-results/testDebugUnitTest/*.xml`. Instrumentisani:
  `app/build/outputs/androidTest-results/connected/debug/*.xml` — `BUILD SUCCESSFUL` ume da znači
  **nula** pokrenutih testova.
- **Polazno stanje: JVM 110, instrumentisani 54**, oba 0 padova.
- **Ime `loc()` ključa u komentaru piše se BEZ `loc(` ispred** — `LocTest` čita izvor kao tekst i
  videće živog pozivaoca. Već je jednom oborilo build.
- **Komituje se imenovano**, bez `git add -A`.
- **„isto kao X" / „isti obrazac kao Y" — otvori X i Y pre nego što to napišeš.**

---

## Izmereno polazno stanje

Sve brojke ispod su izmerene 2026-09-21 na `main` (`dd51f04`), nad
`ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/`, **isključujući `ui/theme/`**.
Ponovi merenje pre nego što se pozoveš na njih — fajlovi se menjaju.

**Razmaci** (`grep -rhoE '[0-9]+\.dp'`, 550 pogodaka):

```
na skali {4,8,12,16,24}: 275  (50%)
van skale:               275  — 10 (56×), 6 (52×), 14 (31×), 1 (22×), 2 (19×),
                                32 (13×), 20 (11×), 18 (9×), 44 (7×), 5 (7×),
                                3 (6×), 48 (5×), 28 (5×), 26 (4×), 0 (4×)
```

**Radijusi** (`RoundedCornerShape(N.dp)`, 105 pogodaka; skala je `{8,12,16}`):

```
na skali: 65  (62%)   — 12 (36×), 8 (19×), 16 (10×)
van skale: 40         — 10 (17×), 6 (12×), 4 (3×), 3 (3×), 20 (2×), 14 (2×), 50 (1×)
```

**Tipografija** (`fontSize = N.sp`, 169 pogodaka; skala je title 22 / heading 17 / body 15 /
caption 12, plus mono 13):

```
na skali (bez mono):  50  (30%)
van skale:           119  — 13 (32×), 11 (32×), 14 (29×), 16 (10×), 18 (7×),
                             9 (3×), 24 (2×), 20 (2×), 72 (1×), 10 (1×)
```

**Pozivna mesta tokena danas:** `DS.Space` 1, `DS.Radius` 0, `DS.maxBoardSide` 0,
tipografska skala 1 (samo `Type.mono`).

**Najveći nosioci `.dp`:** `MainActivity.kt` 141, `ui/LearnView.kt` 77, `ui/SettingsView.kt` 62,
`ui/PuzzleView.kt` 49, `ui/ChessClockView.kt` 44, `ui/UiComponents.kt` 33, `ui/AnalysisView.kt` 25,
`ui/MoveHistoryView.kt` 19.

> **Dve klase vrednosti NISU razmaci i ne tokenizuju se:** `1.dp`/`2.dp`/`3.dp` su debljine ivica
> i hairline-a, a `44.dp`/`48.dp` su Material touch-targeti (minimum 48dp). Tokenizovati ih znači
> tvrditi da su razmak, što nisu. To su **62 od 275** vrednosti van skale.

---

## File Structure

| Fajl | Odgovornost | Task |
|---|---|---|
| `ui/theme/DesignSystem.kt` | `DS.Space`, `DS.Radius`, `DS.maxBoardSide` — vrednosti tokena | 1, 4 |
| `ui/theme/Type.kt` | tipografska skala — `title`/`heading`/`body`/`caption`/`mono` | 2 |
| `MainActivity.kt` | tab Igra (portret + pejzaž), najveći nosilac `.dp` | 1, 3, 4 |
| `ui/PuzzleView.kt`, `ui/StepGameView.kt`, `ui/StepPracticeView.kt` | ekrani sa tablom | 1, 3, 4 |
| `ui/SettingsView.kt`, `ui/AnalysisView.kt`, `ui/UiComponents.kt`, `ui/MoveHistoryView.kt`, `ui/PathView.kt`, `ui/LearnView.kt`, `ui/LessonRenderer.kt`, `ui/LessonDetailView.kt` | ostatak hroma | 3, 4 |
| `app/src/test/…/BoardWidthCapTest.kt` | **novi** — svako igračko pozivno mesto table ima granicu | 1 |
| `app/src/test/…/TypographyScaleTest.kt` | **novi** — nijedan zakucan `fontSize` van `ui/theme/` | 3 |
| `CLAUDE.md` | zapis presuda i izmerenih brojeva | 5 |

**Šta se NE dira:** `ui/BoardView.kt` i `ui/BoardTheme.kt` iznutra (tabla je sadržaj),
jedanaest boja sata, `Chessko/` u celini.

---

## Task 1: `DS.maxBoardSide` dobija pozivna mesta

**Files:**
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/MainActivity.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PuzzleView.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepGameView.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt`
- Test: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/BoardWidthCapTest.kt` (novi)

**Interfaces:**
- Consumes: `DS.maxBoardSide: Dp = 560.dp` (`ui/theme/DesignSystem.kt:132`) — postoji, nekorišćen.
- Produces: ništa što kasniji taskovi zovu. Test iz ovog taska ostaje kao zaštita.

**Zašto ovo postoji.** `BoardView` je `BoxWithConstraints` sa `.aspectRatio(1f)`
(`ui/BoardView.kt:154`) i `val squareSize = maxWidth / 8` (`:158`) — dakle uzima **svu** širinu
koju mu modifier da. Nijedno od **11** pozivnih mesta ne ograničava širinu (provereno:
`grep -B3 "BoardView("` ne daje nijedan `widthIn`/`requiredWidth`/`.width(`). iOS istu granicu
primenjuje na **pet** mesta — `GameView.swift:35` i `:105`, `PuzzleView.swift:296`,
`StepGameView.swift:174`, `StepPracticeView.swift:84` — i to baš zato da tabla ne proguta ceo
ekran tableta.

- [ ] **Korak 1: Napiši test koji pada**

`BoardWidthCapTest.kt` čita izvor **kao tekst** — isti obrazac koji projekat već koristi u
`MainActivitySoundWiringTest` i `LessonBoardsOptOutOfSwipeTest`, jer se do kompozicije iz JVM
testa ne može doći. Test tvrdi **obe strane**, jer bi tvrdnja samo o brojanju prolazila i nad
fajlom u kom je granica zalepljena bilo gde:

```kotlin
package com.veljkoni.chessko

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Igracka tabla mora imati gornju granicu sirine, inace na tabletu proguta ceo ekran.
 *
 * iOS istu granicu (`DS.maxBoardSide`, 560) primenjuje na pet mesta:
 * GameView.swift:35 i :105, PuzzleView.swift:296, StepGameView.swift:174,
 * StepPracticeView.swift:84. Android ih ima cetiri fajla jer su portret i pejzaz
 * tab-a Igra oba u MainActivity.kt.
 *
 * Do kompozicije se iz JVM testa ne moze doci (nema `ViewModelStore`, nema uredjaja),
 * pa se cita izvor — isti obrazac kao MainActivitySoundWiringTest.
 */
class BoardWidthCapTest {

    /**
     * Komentari se skidaju PRE poredjenja — pogodak u komentaru je u Fazi 9 dvaput
     * pomerio broj. Isti helper nosi LessonBoardsOptOutOfSwipeTest.
     */
    private fun withoutComments(src: String): String =
        src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private fun read(path: String): String {
        val file = File(path)
        assertTrue("izvor nije nadjen na ${file.absolutePath}", file.isFile)
        return withoutComments(file.readText())
    }

    private val playerScreens = listOf(
        "src/main/java/com/veljkoni/chessko/MainActivity.kt",
        "src/main/java/com/veljkoni/chessko/ui/PuzzleView.kt",
        "src/main/java/com/veljkoni/chessko/ui/StepGameView.kt",
        "src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt"
    )

    @Test
    fun everyPlayerBoardIsWidthCapped() {
        for (path in playerScreens) {
            val src = read(path)
            assertTrue(
                "$path crta tablu, pa mora da koristi DS.maxBoardSide",
                "DS.maxBoardSide" in src
            )
        }
    }

    @Test
    fun lessonBoardsAreNotCapped() {
        // Table u lekciji zive u skrolujucem stupcu i vec su ogranicene sirinom stranice;
        // granica od 560 bi im samo dodala mrtav prostor. Ako neko ovo promeni, neka to
        // bude odluka, ne previd.
        for (path in listOf(
            "src/main/java/com/veljkoni/chessko/ui/LearnView.kt",
            "src/main/java/com/veljkoni/chessko/ui/LessonRenderer.kt"
        )) {
            val src = read(path)
            assertTrue(
                "$path je lekcijski — ne sme da nosi DS.maxBoardSide",
                "DS.maxBoardSide" !in src
            )
        }
    }
}
```

- [ ] **Korak 2: Pokreni test i vidi da PRVI pada**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest --tests '*BoardWidthCapTest*'
```
Očekivano: `everyPlayerBoardIsWidthCapped` pada na prvom fajlu („MainActivity.kt crta tablu, pa
mora da koristi DS.maxBoardSide"); `lessonBoardsAreNotCapped` prolazi odmah (i to je u redu —
on čuva od preterivanja, ne od izostanka).

- [ ] **Korak 3: Primeni granicu na sva četiri ekrana**

Na svakom mestu gde se `BoardView` crta za igrača, obmotaj modifier tako da širina ima gornju
granicu a tabla ostaje centrirana. Obrazac je isti svuda:

```kotlin
BoardView(
    // … postojeći parametri, nedirnuti …
    modifier = Modifier
        .widthIn(max = DS.maxBoardSide)
        .align(Alignment.CenterHorizontally)
)
```

Ako pozivno mesto već prosleđuje `modifier`, dodaj `.widthIn(max = DS.maxBoardSide)` **pre**
postojećih ograničenja veličine, da ne pregaziš `aspectRatio`. U pejzažu (`MainActivity.kt`,
grana `isLandscape`) strana table se računa iz **visine**, pa granica ide na obe ose:
`.sizeIn(maxWidth = DS.maxBoardSide, maxHeight = DS.maxBoardSide)`.

> **Ne diraj `ui/BoardView.kt`.** Granica pripada pozivaocu, kao i na iOS-u — tabla ne zna koliko
> smje da bude velika, ekran zna. Uz to bi izmena u `BoardView.kt` pala na ograničenje „nula
> `DS.` pogodaka" iz Global Constraints.

- [ ] **Korak 4: Pokreni test i vidi da prolazi**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest --tests '*BoardWidthCapTest*'
```
Očekivano: oba PASS.

- [ ] **Korak 5: Dokaz mutacijom**

Ukloni `DS.maxBoardSide` iz **jednog** fajla, pokreni test, **zalepi izlaz** (mora da padne baš
na tom fajlu), pa vrati. Zatim **pokreni ceo skup ponovo** — inače na disku ostaje crven XML iz
namerno pokvarenog prolaza, koji je tačan ali ne opisuje stablo (zamka iz Faze 7).

- [ ] **Korak 6: Build, testovi, commit**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/MainActivity.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PuzzleView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepGameView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt \
        ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/BoardWidthCapTest.kt
git commit -m "Faza 10, Task 1: tabla dobija gornju granicu sirine (DS.maxBoardSide)"
```
JVM 110 → **112**.

> **Vizuelna potvrda na tabletu ide u Task 5**, u jedinom prolazu emulatora. Ovaj task dokazuje
> da granica postoji u kodu; da izgleda ispravno dokazuje Task 5.

---

## Task 2: Presuda o tipografskoj skali

**Files:**
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/theme/Type.kt`

**Interfaces:**
- Consumes: ništa iz Task-a 1.
- Produces: konačan skup imena i veličina koje Task 3 primenjuje. **Task 3 ne sme da izmišlja
  nova imena** — koja god imena izađu iz ovog taska, to su sva imena koja postoje.

**Zašto ovo postoji, i zašto nije mehanički.** Skala danas nudi `title 22 / heading 17 / body 15
/ caption 12 / mono 13`. Ekrani koriste `13 (32×), 12 (32×), 11 (32×), 14 (29×)` kao četiri
najčešće veličine — **30% poklapanja sa skalom**. Mehanička zamena bi promenila veličinu teksta
na 119 mesta, na ekranima koji su vizuelno provereni kroz Faze 6d-1, 6d-2, 8 i 9.

- [ ] **Korak 1: Izmeri raspodelu po ULOZI, ne samo po veličini**

Broj sam po sebi ne kaže da li je `11.sp` podnaslov ili oznaka. Za svaku od pet najčešćih
veličina (13, 12, 11, 14, 15) izvuci **po tri stvarna pozivna mesta** sa okolnim kodom i zapiši
**šta taj tekst jeste** (naslov kartice, telo, oznaka ispod ikone, brojač…). Bez toga presuda je
o brojevima, ne o ekranu.

```bash
grep -rn 'fontSize = 11.sp' --include='*.kt' \
  ChesskoAndroid/app/src/main/java/com/veljkoni/chessko | head -3
```

- [ ] **Korak 2: Presudi — tri puta, svaki sa cenom**

**(a) Skala se prilagođava ekranima.** Vrednosti se pomeraju ka onome što se stvarno koristi
(npr. `caption` 12 → 11, dodaje se jedan korak za 13/14). Cena: razlaz sa iOS skalom, koja je
izvedena iz `Font.appFont`; dve platforme prestaju da izgledaju isto.

**(b) Ekrani se prilagođavaju skali.** 119 mesta menja veličinu. Cena: promena izgleda na
proverenim ekranima, i **obavezna vizuelna provera svih ekrana** u Task-u 5, ne uzorka.

**(c) Skala se proširuje.** Dodaju se koraci koji pokrivaju stvarne veličine (npr. `bodySmall`
13, `captionSmall` 11), pa poklapanje skoči bez pomeranja postojećih. Cena: skala od 5 raste na
7–8 koraka i prestaje da bude „jedna tipografska skala" iz spec-a 5.6.

**Izmeri i presudi, ne biraj po ukusu.** Pre nego što presudiš, **otvori iOS
`Chessko/Views/DesignSystem.swift`** i vidi koje veličine `Font.ds*` stvarno daje — ako se iOS
skala već razlikuje od Android brojeva, opcija (a) je manji razlaz nego što izgleda. **Ne tvrdi
paritet bez otvaranja fajla.**

- [ ] **Korak 3: Upiši presudu u `Type.kt`, sa razlogom**

Presuda ide **iznad skale**, kao komentar koji kaže šta je mereno, šta je odlučeno i šta se time
gubi — istim tonom kojim `DesignSystem.kt` objašnjava zašto `dynamicColor` nije tu. Sledeći
čitalac mora moći da sazna zašto `caption` ima baš tu vrednost, bez čitanja ovog plana.

- [ ] **Korak 4: Build i commit**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/theme/Type.kt
git commit -m "Faza 10, Task 2: presuda o tipografskoj skali"
```
Broj testova nepromenjen (**112**) — ovaj task menja definiciju, ne pozivna mesta.

---

## Task 3: Primena tipografije

**Files:**
- Modify: svih 8 fajlova iz „File Structure" koji nose `fontSize` (`MainActivity.kt`,
  `ui/PuzzleView.kt`, `ui/SettingsView.kt`, `ui/AnalysisView.kt`, `ui/UiComponents.kt`,
  `ui/MoveHistoryView.kt`, `ui/PathView.kt`, `ui/LearnView.kt`, `ui/LessonRenderer.kt`,
  `ui/LessonDetailView.kt`, `ui/StepGameView.kt`, `ui/StepPracticeView.kt`,
  `ui/ChessClockView.kt`)
- Test: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/TypographyScaleTest.kt` (novi)

**Interfaces:**
- Consumes: **konačan spisak imena iz Task-a 2** — `Type.title` 22 / `Type.heading` 17 /
  `Type.body` 15 / `Type.caption` 12 / `Type.label` 11 (**nov**) / `Type.mono` 13.
  **To su sva imena koja postoje. Ne izmišljaj nova i ne dodaj korak.**
- Uloge, kako ih je Task 2 presudio: `label` je podnaslov ispod naslova, tekst uz ikonu na
  zbijenom dugmetu, i oznaka ispod brojčane vrednosti. `body` je osnovni tekst **i telo lekcije**.

> **Veličina 14 NAMERNO nema ime.** Jedina je česta veličina koje nema u Apple-ovoj lestvici
> (Material drift). Njenih **29** mesta se razvrstava **po ulozi** na `body` ili `caption` —
> **ne zaokružuje se** na najbliži korak. Isto važi za 16 (9×) i 18 (7×): razvrstaj po ulozi na
> `heading`/`body`; ako nekom mestu 17 stvarno ne odgovara, to je **nalaz za izveštaj**, ne
> povod da se doda korak.

> **Pet od tih 29 mesta na 14 nisu tekst** nego veličina lekcijskog glifa (`LessonGlyphView`).
> Ako ostaju doslovni, moraju nositi komentar zašto.

> **`ui/LessonRenderer.kt:174` nosi USLOVNU veličinu** — `fontSize = if (symbol != null) 22.sp
> else 15.sp` — koju šablon `fontSize = N.sp` **uopšte ne vidi**. Nije u brojci od 161 i mora se
> obraditi ručno.
- Produces: nijedan zakucan `fontSize` van `ui/theme/` i van imenovanih izuzetaka.

- [ ] **Korak 1: Napiši test koji pada**

```kotlin
package com.veljkoni.chessko

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Nijedan ekran ne sme sam da bira velicinu fonta — to radi Type.*.
 *
 * Izuzeci su imenovani pojedinacno, sa razlogom, i **moraju ostati kratki**:
 * spisak koji raste je znak da skala ne opisuje ekrane, a to je presuda
 * Task-a 2, ne stvar koju resava dopisivanje izuzetka.
 */
class TypographyScaleTest {

    /**
     * Jedan izuzetak, i to po VREDNOSTI a ne po fajlu: cifre sata (`72.sp`) nisu tekst
     * nego prikaz vremena i imaju sopstveni registar — iOS ih drzi na 86pt iz istog
     * razloga. Ostali tekst u `ChessClockView.kt` (14/13/16/11) JESTE hrom i tokenizuje
     * se normalno, pa izuzimanje celog fajla ne bi bilo tacno.
     */
    private val allowed = setOf("ChessClockView.kt:fontSize = 72.sp")

    @Test
    fun noScreenHardcodesFontSize() {
        val root = File("src/main/java/com/veljkoni/chessko")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("/theme/") }
            .flatMap { f ->
                // Komentar koji pominje fontSize nije zakucan fontSize — skini ih prvo.
                val src = f.readText()
                    .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                    .lines().joinToString("\n") { it.substringBefore("//") }
                Regex("""fontSize\s*=\s*\d+\.sp""").findAll(src)
                    .map { "${f.name}:${it.value}" }
            }
            .filterNot { it in allowed }
            .toList()

        assertEquals("zakucan fontSize van Type.*: $offenders", emptyList<String>(), offenders)
    }
}
```

- [ ] **Korak 2: Pokreni i vidi da pada sa spiskom**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest --tests '*TypographyScaleTest*'
```
Očekivano: FAIL, sa spiskom od ~160 unosa. **Zalepi prvih deset** — to je tvoja radna lista.

> **Tačan broj je 161, ne 169** (izmereno posle Task-a 2, potvrđeno nezavisno). Naivan grep
> prijavi 170 jer broji i **devet pogodaka u samom `Type.kt`**, od kojih su dva unutar `/* */`
> bloka — pa ih filter koji gleda samo linije što *počinju* sa `//`/`*` ne skida. Implementer
> Task-a 2 je u tu zamku pao pa se sam ispravio; ne ponavljaj je.

- [ ] **Korak 3: Zameni, fajl po fajlu, po presudi Task-a 2**

Idi od najmanjeg fajla ka najvećem (`ui/MoveHistoryView.kt` 19 → `MainActivity.kt` 141), i
**komituj posle svakog fajla**. Razlog nije urednost nego mogućnost da se jedan fajl vrati ako
vizuelna provera u Task-u 5 pokaže da je nešto ispalo pogrešno.

Obrazac zamene:

```kotlin
// pre
Text(text = loc("Rejting zadataka"), fontSize = 12.sp, color = DS.inkMuted)
// posle
Text(text = loc("Rejting zadataka"), style = Type.caption, color = DS.inkMuted)
```

> **`style =` ne poništava `fontWeight` koji je već zadat na pozivnom mestu** — ako red nosi
> `fontWeight = FontWeight.Bold`, a `Type.body` je `Normal`, rezultat je **Bold**, jer eksplicitni
> parametar pobeđuje stil. To je dobro; ali znači da zamena `fontSize` **ne sme usput da obriše**
> postojeći `fontWeight`, osim ako se poklapa sa stilom. **Proveri svaki red, ne radi
> find-replace.**

- [ ] **Korak 4: Pokreni test i vidi da prolazi**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest --tests '*TypographyScaleTest*'
```
Očekivano: PASS.

- [ ] **Korak 5: Dokaz mutacijom, pa ponovni prolaz**

Vrati **jedan** `fontSize = 14.sp` u bilo koji ekran, pokreni test, **zalepi izlaz** (mora da
imenuje baš taj fajl), pa vrati. Zatim **pokreni ceo skup ponovo**, da XML opisuje stablo.

- [ ] **Korak 6: Build i završni commit**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
```
JVM 112 → **113**.

---

## Task 4: Razmaci i radijusi — dokazivo bezbedan deo, pa presuda o ostatku

**Files:**
- Modify: isti skup fajlova kao Task 3, plus `ui/theme/DesignSystem.kt` ako presuda traži nov korak

**Interfaces:**
- Consumes: `DS.Space.xs/s/m/l/xl` = `4/8/12/16/24.dp`, `DS.Radius.s/m/l` = `8/12/16.dp`
  (`ui/theme/DesignSystem.kt`).
- Produces: ništa što kasniji taskovi zovu.

- [ ] **Korak 1: Zameni SAMO vrednosti koje već padaju na skalu**

To je **275 od 550** razmaka i **65 od 105** radijusa. Zamena `12.dp` → `DS.Space.m` menja
**nula piksela** — i to je dokazivo, ne tvrdnja:

```bash
# pre izmene
cd ChesskoAndroid && ./gradlew assembleDebug && \
  unzip -p app/build/outputs/apk/debug/app-debug.apk classes.dex | sha256sum
```

> **Ovaj heš se NEĆE poklopiti posle izmene** i to je očekivano — konstanta iz `object Space`
> nije ista instrukcija kao literal. Ne koristi ga kao dokaz. **Pravi dokaz je vizuelni prolaz u
> Task-u 5**, a ovaj korak samo drži izmenu u klasi „ista vrednost, drugo ime".

Idi fajl po fajlu, **komituj posle svakog**. Ne diraj `ui/BoardView.kt` ni `ui/BoardTheme.kt`.

- [ ] **Korak 2: Razvrstaj ostatak, pa presudi po grupama**

275 razmaka van skale nisu jedna grupa. Razvrstaj ih i presudi **po grupi**, ne pojedinačno:

| grupa | vrednosti | šta jeste |
|---|---|---|
| ivice i hairline | 1, 2, 3 (47×) | debljina linije, **nije razmak — ne tokenizuje se** |
| touch-targeti | 44, 48 (12×) | Material minimum 48dp, **nije razmak — ne tokenizuje se** |
| blizu skale | 6, 10, 14, 18, 20 (129×) | razmak koji promašuje korak za 2dp |
| krupno | 26, 28, 32 (22×) | veliki odmaci, kandidat za nov korak `xxl` |
| ostatak | 0, 5, i pojedinačne | čitaj pojedinačno |

Za **„blizu skale"** presudi jedno od dvoje: zaokružiti na najbliži korak (menja ekran za ≤2dp
po mestu, 129 mesta) ili ostaviti zakucano uz komentar zašto. Za **„krupno"** presudi da li
`DS.Space` dobija `xxl = 32.dp`.

**Zapiši presudu u kod**, iznad `object Space`, sa izmerenim brojevima.

- [ ] **Korak 3: Radijusi — isto, ali kraće**

40 vrednosti van skale, od kojih je `10.dp` (17×) daleko najčešća. Presudi da li `10` ide na
`DS.Radius.s` (8) ili `m` (12), ili `DS.Radius` dobija korak. `50.dp` (1×) je verovatno pilula,
ne radijus kartice — **otvori to mesto i pogledaj** pre nego što ga svrstaš.

- [ ] **Korak 4: Testovi, build, commit**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
```
Broj testova nepromenjen (**113**) — razmaci nemaju test koji ih čuva, i to je namerno: test koji
zabranjuje svaki `.dp` literal bi zabranio i ivice i touch-targete, pa bi ga prvi izuzetak
učinio beskorisnim. Čuva ih vizuelni prolaz u Task-u 5.

---

## Task 5: Jedini prolaz emulatora i zatvaranje faze

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Korak 1: Oba skupa testova, brojevi iz XML-a**

Očekivano **113 JVM / 54 instrumentisanih**, 0 padova. Ako se ne poklapa — reci, ne prepravljaj
očekivanje.

- [ ] **Korak 2: JEDAN prolaz emulatora**

```bash
export ANDROID_HOME=~/Library/Android/sdk   # nije postavljen u okruzenju
nice -n 10 $ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 \
  -no-window -no-audio -no-boot-anim -gpu host -cores 2 -memory 2048 &
$ANDROID_HOME/platform-tools/adb wait-for-device
$ANDROID_HOME/platform-tools/adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
```

U tom prolazu, **obe teme**:

1. **Tablet** — `DS.maxBoardSide` se stvarno vidi. Proveri **svih šest** igračkih tabli
   (Igra portret i pejzaž, Zadaci portret i pejzaž, korak-partija, korak-vežba) — Task 1 ih je
   ograničio na šest mesta u četiri fajla. **Igra u portretu je poseban slučaj:** tamo već
   postoji `widthIn(max = 500.dp)` na obuhvatnoj koloni, pa je granica od 560 **nedostižna** i
   tabla se neće promeniti — to nije greška nego sloj iznad.
   **I jedna stvar koju Task 1 nije mogao da dokaže bez uređaja:** redosled modifiera (granica
   **pre** `.size()`/`.fillMaxHeight()`) izveden je iz semantike `Constraints.constrain()`.
   **Ako neka tabla ispadne nekvadratna na tabletu, prvo gledaj taj redosled** — naročito
   `ui/PuzzleView.kt`, gde granica stoji na **spoljnom `Box`-u**, ne na `BoardView` pozivu. Ako nemaš tablet AVD, koristi
   `adb shell wm size 2560x1600` i `wm density 240` na postojećem, i **reci u izveštaju da je
   geometrija simulirana, ne uređaj** (isti obrazac kao pejzaž u Fazi 9).
   **Pre i posle:** snimi tablu i sa uklonjenom granicom, da se vidi da granica nešto radi.
2. **Igra** (portret i pejzaž), **Zadaci**, **Put**, **korak Puta**, **lekcija**, **Podešavanja**,
   **sat** (uključujući dijalog izbora vremenske kontrole i info dijalog), **ekran analize** —
   dakle **svaki ekran**, ne uzorak.

   > **Zašto svaki, a ne uzorak:** presuda Task-a 2 ne pomera nijednu vrednost skale, ali
   > primena u Task-u 3 menja veličinu na **~60 pozivnih mesta** — najvidljivije **telo lekcije
   > 13 → 15**. To je promena na ekranima koji su vizuelno provereni kroz Faze 6d-1, 6d-2, 8 i 9,
   > pa uzorak ovde ne dokazuje ništa. Implementer Task-a 2 je to sam tražio.

   > **Dve stvari koje Task 2 izričito nije mogao da proveri statički, pa se proveravaju ovde:**
   > (a) `LSectionHeader` (15, bold) i telo (15) se **izjednačavaju po veličini** i razlikovaće
   > se samo težinom — na iOS-u je već tako, ali Android gubi jedan vizuelni stepen; ako zasmeta,
   > popravka je **težina ili razmak, ne nova veličina**;
   > (b) tvrdnja da je 13 → 15 povratak paritetu je izvedena **iz izvora, ne iz dve lekcije jednu
   > pored druge** — ako Android na 15 prelije karticu koju iOS ne prelije, uzrok je razmak ili
   > širina, pa **proveri, ne pretpostavljaj**.
3. **Sat** — jedanaest boja prati stranu; potvrdi da su crna i bela polovina i dalje crna i bela.
4. **Spisak mesta sa najvecim rizikom** — izmeren u Task-u 3 i zaostren u njegovom pregledu.
   Ovo nije „pogledaj okolo" nego **radna lista**; svako mesto ili potvrdi ili prijavi:

   | mesto | promena | šta tačno gledaš |
   |---|---|---|
   | oznake ispod uzoraka **stila figura**, mreža od 4 kolone (`SettingsView`) | 10/11 → 12 | **najveći rizik.** iOS ima `lineLimit(1)` + `minimumScaleFactor(0.8)` (`SettingsSheet.swift:209-210`, `:269-270`); `SettingsView.kt` nema **nijedan** `maxLines` ni `TextOverflow` u celom fajlu — pa režim otkaza **nije odsecanje nego PRELOM U DVA REDA**, dakle neujednačena visina ćelija i razbijen red |
   | telo lekcije | 13 → 15 | preliva svaku lekciju; proveri na **oba** jezika |
   | naslov `LBox` kutije naspram njenog tela | naslov 13 → **12**, telo 13 → **15** | naslov postaje **manji od sopstvenog tela**. To **jeste** iOS paritet (`LessonRenderer.swift:438` caption / `:446` body, naslov je bold) — ali pogađa **44 `RULE`/`WARNING` kutije** u isporučenom sadržaju, pa se gleda, ne pretpostavlja |
   | naslov greške u **pejzažnoj** grani `PuzzleView` | 14 → 17 | +3 u **užoj** koloni |
   | poruka o ishodu partije | 18 → 22 | jedini +4 u grani |
   | „Tema table" / „Stil" | 12 → 15 | |
   | čip „Mat u %d" | 9 → 11 | zaglavlje kartica vežbi |
   | naslov dijaloga sata i naslovi sekcija u njemu | 16 → 17 i 14 → 17 | sada su **iste veličine**, pre 16 vs 14 — razlikuju se još samo bojom |
   | naslov aplikacije u pejzažu | 20 → 22 | |

5. **Tipografija** — na bar tri ekrana uporedi sa snimcima iz Faze 9
   (`.superpowers/sdd/2026-09-20-faza-9-lekcijske-ikone-i-precica/screenshots/`) i reci **šta se
   promenilo**. Ako se ništa nije promenilo a Task 2 je izabrao opciju (b), nešto nije primenjeno.

Snimci u `.superpowers/sdd/<plan>/screenshots/`, **ne u sesijski `/tmp`** — odatle su u ranijoj
fazi nestali pre finalnog pregleda.

- [ ] **Korak 3: Gašenje, i dokaz da je ugašeno**

```bash
$ANDROID_HOME/platform-tools/adb emu kill
(cd ChesskoAndroid && ./gradlew --stop)
pgrep -f qemu-system || echo "qemu: nema"
```
**Zalepi izlaz.**

- [ ] **Korak 4: Prebroj šta je ostalo**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
grep -rhoE '[0-9]+\.dp' --include='*.kt' . | grep -v '/theme/' | wc -l
grep -rhoE 'fontSize *= *[0-9]+\.sp' --include='*.kt' . | grep -v '/theme/' | wc -l
```
**Svaki pogodak pročitaj, ne samo prebroj** — pogodak u komentaru je u Fazi 9 dvaput pomerio
broj. Razloži ostatak po grupama iz Task-a 4 (ivice, touch-targeti, sat) i reci koliko ih je
ostalo **namerno**.

- [ ] **Korak 5: `CLAUDE.md`**

- sekciju „Android dizajn sistem" prepiši: `DS.Space`/`DS.Radius`/`DS.maxBoardSide` i tipografska
  skala **više nemaju nula pozivnih mesta**; navedi koliko ih imaju i koliko je vrednosti ostalo
  namerno zakucano, sa razlogom po grupi
- **ukloni zastarelu tvrdnju** da tokeni „i dalje nemaju nijedno pozivno mesto" — javlja se na
  dva mesta (sekcija „Android dizajn sistem" i „Poznata ograničenja")
- upiši **presudu Task-a 2** (tipografska skala) i **presudu Task-a 4** (razmaci van skale), sa
  izmerenim brojevima i sa onim što se gubi
- ako je Task 1 na tabletu pokazao da tabla stvarno preliva, upiši to kao **popravljen bug**, ne
  kao higijenu tokena
- changelog sa izmerenim brojevima

- [ ] **Korak 6: Provere celog stabla i commit**

```bash
D=$(git diff --stat main..HEAD -- Chessko Chessko.xcodeproj); [ -z "$D" ] && echo "iOS netaknut"
diff -r Chessko/Content/lessons ChesskoAndroid/app/src/main/assets/lessons && echo "lekcije bajt-identicne"
diff Chessko/Content/curriculum.json ChesskoAndroid/app/src/main/assets/curriculum.json && echo "kurikulum bajt-identican"
git diff --stat main..HEAD -- ChesskoAndroid/app/build.gradle.kts ChesskoAndroid/gradle/libs.versions.toml
grep -c "DS\." ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/BoardView.kt
grep -c "DS\." ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/BoardTheme.kt
```
Poslednja dva moraju dati **0**.

---

## Šta ova faza NAMERNO ne radi

- **Ne tokenizuje tablu.** `BoardView.kt`/`BoardTheme.kt` ostaju bez ijednog `DS.` — tabla je
  sadržaj, ne hrom, i to je isti izuzetak koji iOS nosi od Faze 1.
- **Ne dira jedanaest boja sata.**
- **Ne piše Compose UI test za gest** iz Faze 9 (tap na prazno polje pa prevlačenje). Ostaje
  zapisan u `CLAUDE.md` kao poznato ograničenje, sa navedenim oblikom testa; uvodi nov obrazac u
  projekat i traži sopstveni prolaz emulatora.
- **Ne popravlja tri sitna nalaza** iz finalnog pregleda Faze 9 (vibracija na dug pritisak bez
  efekta, `allowsStyleSwipe` van ključeva `pointerInput`-a, KDoc `LessonBoardsOptOutOfSwipeTest`-a
  širi od pokrića).
- **Ne dira iOS.**
