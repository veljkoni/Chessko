# Faza 6d-1 — Android: dizajn sistem (tokeni i glavni ekrani)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android dobija dizajn tokene iz spec-a 5.6 i **svetlu temu koja stvarno radi** — na tabu Igra, tabu Zadaci, tabu Put i u Podešavanjima.

**Architecture:** Prenos iOS Faze 1 na Compose. Paleta živi u `ChesskoColors` (data klasa) koju `ChesskoTheme` bira po temi i objavljuje kroz `CompositionLocal`; pozivna mesta je čitaju kao `DS.accent`, isto kao iOS. Razmaci, radijusi i tipografska skala su obične konstante. Kontrast se ne procenjuje nego **računa u testu**.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.runtime.CompositionLocal`. **Nijedna nova Gradle zavisnost.**

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` (sekcija 5.6, Faza 1, Faza 6)

---

## Zašto je ovo dve kriške, i šta je u prvoj

iOS Faza 1 je bila **preusmeravanje** — boje su tamo već bile adaptivne (`Color.adaptive(light:dark:)`), pa je faza samo uvela tokene i provukla ih kroz njih.

Na Androidu to nije tako. Izmereno pre pisanja ovog plana:

- **466 zakucanih boja u 19 fajlova** (193 heks literala + 273 `Color.White`/`Color.Black`)
- **Nijedan ekran ne čita temu.** `isDark` se izračuna u `MainActivity.kt:90` i prosledi `ChesskoTheme`-u — i tu se zaustavlja. Jedini drugi pogodak za `isDarkTheme` je `BoardView.kt:568`, i on se tiče **tema table**, ne svetlog/tamnog režima.
- Zato podešavanje **„Svetla / Tamna / Sistem" danas ne menja ništa vidljivo.** Stoji u `SettingsView.kt:480` i laže.
- `ChesskoTheme` je **netaknut Android Studio šablon**: `Purple80`/`Pink40` paleta i `dynamicColor = true`, koji boje vuče sa korisnikove tapete — za aplikaciju čiji spec traži **jedan fiksan akcent** to je greška, ne funkcija.

Dakle ova faza mora i da **uvede svetlu temu po prvi put**. Podeljena je na dve kriške da svaka bude isporučiva:

| | Fajlovi | Zakucanih boja |
|---|---|---|
| **6d-1 (ovaj plan)** | `DesignSystem.kt`, `Theme.kt`, `Color.kt`, `Type.kt`, `MainActivity.kt`, `UiComponents.kt`, `CapturedPiecesView.kt`, `MoveHistoryView.kt`, `EvalBar.kt`, `PromotionOverlay.kt`, `PuzzleView.kt`, `PathView.kt`, `StepPracticeView.kt`, `StepGameView.kt`, `SettingsView.kt` | **~280** |
| 6d-2 (sledeći plan) | `ChessClockView.kt`, `LearnView.kt`, `BoardView.kt`, `BoardTheme.kt`, `LessonDetailView.kt`, `LessonRenderer.kt` | ~173 |

**Međustanje je vidljivo neujednačeno** i to je prihvaćeno: posle 6d-1 tri taba i podešavanja prate temu, a sat i lekcije su i dalje tamni. Zato 6d-1 **ne dira** podešavanje „Svetla/Tamna" — ono postaje iskreno tek kad 6d-2 završi.

---

## Global Constraints

Važi za **svaki** task.

- **`create_xcode_project.py` se NIKAD ne pokreće.** `Chessko/` se **NE dira** (samo čitanje radi poređenja). Na kraju faze `git diff --stat 117a26c..HEAD -- Chessko Chessko.xcodeproj` mora biti prazan.
- **Nijedna nova Gradle zavisnost.**
- **Akcent je FIKSAN i ne menja se sa temom table.** Osam tema table bira korisnik; tabla ostaje jedini šaroliki element (spec 5.6).
- **UI tekst ide kroz `loc()`**, ključevi kao literali, svih 8 jezika. Ova faza ne bi trebalo da dodaje nove ključeve; ako ipak doda, `LocTest` je čuvar.
- **Polazno stanje testova: JVM 36, instrumentisani 46.** Svaki task navodi svoj zbir.
- **Rezultat testova se čita iz XML-a**, ne iz izlaznog koda: `app/build/test-results/testDebugUnitTest/*.xml` i `app/build/outputs/androidTest-results/connected/debug/*.xml`.
- **Emulator — obavezan recept i životni vek:**
  ```bash
  export ANDROID_HOME=~/Library/Android/sdk
  nice -n 10 $ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 \
    -no-window -no-audio -no-boot-anim -gpu host -cores 2 -memory 2048 &
  $ANDROID_HOME/platform-tools/adb wait-for-device
  $ANDROID_HOME/platform-tools/adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
  ```
  **`-gpu host`, NIKAD `-gpu off`** (izmereno: 745% prema 216%). Diže se **samo za korak koji ga traži**, jednom, i gasi **odmah**: `adb emu kill`, `./gradlew --stop`, `pgrep -f qemu-system` prazno. Ako se rad prekine na pola — **prvo se gasi emulator**.
- **Skrol u Compose se ne postiže sa `adb shell input swipe`** — treba `input motionevent DOWN` → 12× `MOVE` → `UP`.
- **Promena teme se na uređaju postiže** preko `run-as` i `chessko_settings` (`colorScheme` = `light`/`dark`/`system`), ili sistemski: `adb shell "cmd uimode night yes|no"`.
- **Svaki nov test mora da se dokaže mutacijom.**

---

## File Structure

| Fajl | Odgovornost |
|---|---|
| `.../ui/theme/DesignSystem.kt` | **novo** — `ChesskoColors`, obe palete, `LocalChesskoColors`, `object DS` (boje + `Space` + `Radius` + `maxBoardSide`) |
| `.../ui/theme/Theme.kt` | prepisuje se — bez `dynamicColor`, bira paletu, objavljuje je, i preslikava u `MaterialTheme.colorScheme` |
| `.../ui/theme/Color.kt` | **briše se** — `Purple80`/`Pink40` je šablon koji niko ne koristi |
| `.../ui/theme/Type.kt` | tipografska skala `DS.Type.*` |
| `.../MainActivity.kt` | migracija (91 mesto) |
| `.../ui/UiComponents.kt`, `CapturedPiecesView.kt`, `MoveHistoryView.kt`, `EvalBar.kt`, `PromotionOverlay.kt` | migracija (49) |
| `.../ui/PuzzleView.kt` | migracija (27) |
| `.../ui/PathView.kt`, `StepPracticeView.kt`, `StepGameView.kt` | migracija (43) |
| `.../ui/SettingsView.kt` | migracija (70) |
| `app/src/test/.../ContrastTest.kt` | **novo** — WCAG kontrast, čista matematika, JVM |

---

## Tabela preslikavanja — jedan izvor istine za sve taskove migracije

Izmereno prebrojavanjem po celom Android izvoru. **Svaki task migracije koristi OVU tabelu**, ne sopstvenu procenu.

| Zatečeno | Postaje | Napomena |
|---|---|---|
| `Color(0xFF00D2FF)` (52×) | `DS.accent` | de-facto akcent aplikacije |
| `Color(0xFF3B82F6)` (5×) | `DS.accent` | drugi ton iste uloge |
| `Color(0xFFEF4444)` (17×) | `DS.danger` | **osim u `BoardView`** — tamo je oznaka šaha, ostaje |
| `Color(0xFF10B981)` (7×) | `DS.success` | |
| `Color(0xFFF59E0B)` (8×) | `DS.warning` | |
| `Color(0xFF0F172A)` (4×) | `DS.ground` | podloga ekrana |
| `Color(0xFF1E293B)` (12×) | `DS.surface` | površina kartice |
| `Color.White` kao **tekst** | `DS.ink` | |
| `Color.White.copy(alpha = 0.5f…0.75f)` | `DS.inkMuted` | prigušen tekst |
| `Color.White.copy(alpha = 0.03f…0.12f)` | `DS.fill` | **ispuna, ne tekst** — providnost nestaje, boja je puna |
| `Color.White.copy(alpha ≈ 0.2f)` kao **ivica** | `DS.line` | |
| `Color.Black.copy(alpha = 0.4f…0.6f)` kao **preklop** | `DS.scrim` | |
| tekst/ikona **na** `DS.accent` | `DS.onAccent` | **nikad `Color.White`** — vidi zamku 2 |
| tekst/ikona **na** `DS.scrim` | `DS.onScrim` | fiksna bela, namerno |

### Šta se NAMERNO ne prevodi u tokene

Isto kao iOS (`CLAUDE.md`, sekcija „Dizajn sistem"):

- **boje table** — polja, pozadina table, oznaka šaha, poslednji potez, legalni potezi. Bira ih tema table, ne sistemska tema. (`BoardView`, `BoardTheme` — ionako su u 6d-2.)
- **crna i bela polovina šahovskog sata** — prate STRANU sata, ne temu. (`ChessClockView`, u 6d-2.)
- **prikaz tema table i stilova figura u Podešavanjima** — to je **sadržaj**, ne hrom. Kvadratići koji prikazuju kako tema izgleda moraju ostati u svojim bojama.
- **`PieceColor.WHITE`/`BLACK`** — strane u igri, ne boje.

---

## Zamke — pročitati pre Task-a 1

1. **`dynamicColor = true` je aktivan i vuče boje sa tapete.** `Theme.kt:40`. Za aplikaciju čiji spec traži jedan fiksan akcent to je greška. Uklanja se u Task-u 1 — ne „po mogućstvu", nego obavezno.
2. **`DS.onAccent` MORA biti adaptivan.** iOS je ovo platio: `accent` menja svetlinu između tema (`#2E4A8A` svetla / `#7EA0E8` tamna), pa bela na svetloj varijanti daje **2,6:1** — ispod AA. `DS.onScrim` (fiksna bela) sme **samo** na `DS.scrim`.
3. **Providnost nestaje pri prevođenju.** `Color.White.copy(alpha = 0.04f)` na tamnoj podlozi daje vrlo tamnu sivu; `DS.fill` je **puna** boja koja to zamenjuje. Ne pisati `DS.fill.copy(alpha = 0.04f)` — to bi dalo skoro nevidljivu ispunu.
4. **`object DS` sa `@Composable` geterima ne može van kompozicije.** Sve migracije su u composable funkcijama, pa je to u redu — ali ako neki `remember { }` blok ili obična funkcija zatraži boju, ona mora da je primi kao parametar.
5. **Tri para tokena su ispod WCAG AA u svetloj temi, i to je nasleđeno iz spec-a.** Izmereno pre pisanja plana (vidi Task 1). Ne rešava se spuštanjem praga u testu.

---

## Task 1: Tokeni, tema i test kontrasta

**Files:**
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/theme/DesignSystem.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/theme/Theme.kt` (prepisuje se)
- Delete: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/theme/Color.kt`
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/theme/Type.kt`
- Test: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/ContrastTest.kt`

**Interfaces:**
- Produces:
  - `data class ChesskoColors(accent, ground, surface, navBar, fill, line, ink, inkMuted, success, warning, danger, scrim, onScrim, inkFixed, onAccent)` — svi `androidx.compose.ui.graphics.Color`
  - `val LightColors: ChesskoColors`, `val DarkColors: ChesskoColors`
  - `val LocalChesskoColors: ProvidableCompositionLocal<ChesskoColors>`
  - `object DS` sa `@Composable @ReadOnlyComposable` geterima za svaki token, plus `object Space { xs, s, m, l, xl }`, `object Radius { s, m, l }`, `val maxBoardSide: Dp`
  - `object Type` (u `Type.kt`) sa `title`, `heading`, `body`, `caption`, `mono`
  - `@Composable fun ChesskoTheme(darkTheme: Boolean, content: @Composable () -> Unit)` — **bez `dynamicColor` parametra**

- [ ] **Step 1: Napiši test kontrasta koji pada**

`app/src/test/java/com/veljkoni/chessko/ContrastTest.kt`:

```kotlin
package com.veljkoni.chessko

import androidx.compose.ui.graphics.Color
import com.veljkoni.chessko.ui.theme.ChesskoColors
import com.veljkoni.chessko.ui.theme.DarkColors
import com.veljkoni.chessko.ui.theme.LightColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Kontrast se RACUNA, ne procenjuje.
 *
 * iOS je ovu lekciju platio: bela na `accent`-u daje 8,5:1 u tamnoj temi ali
 * samo 2,6:1 u svetloj, pa je morao da uvede poseban `onAccent` token. Ovaj
 * test postoji da se takva greska vise ne otkrije na ekranu.
 */
class ContrastTest {

    /** WCAG 2.1 relativna luminansa. */
    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    /** WCAG odnos kontrasta, uvek >= 1.0. */
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, p: ChesskoColors, fg: (ChesskoColors) -> Color,
                      bg: (ChesskoColors) -> Color, min: Double) {
        val r = contrast(fg(p), bg(p))
        assertTrue("$name: %.2f, a trazi se >= %.2f".format(r, min), r >= min)
    }

    @Test
    fun textOnBackgroundsMeetsAA() {
        for ((label, p) in listOf("svetla" to LightColors, "tamna" to DarkColors)) {
            check("$label ink/ground", p, { it.ink }, { it.ground }, 4.5)
            check("$label ink/surface", p, { it.ink }, { it.surface }, 4.5)
            check("$label inkMuted/surface", p, { it.inkMuted }, { it.surface }, 4.5)
            check("$label success/surface", p, { it.success }, { it.surface }, 4.5)
            check("$label danger/surface", p, { it.danger }, { it.surface }, 4.5)
        }
    }

    /**
     * Najvazniji par u celom sistemu. `accent` menja svetlinu izmedju tema, pa
     * nijedna FIKSNA boja teksta ne radi u obe — zato `onAccent` postoji.
     */
    @Test
    fun onAccentMeetsAAInBothThemes() {
        check("svetla onAccent/accent", LightColors, { it.onAccent }, { it.accent }, 4.5)
        check("tamna onAccent/accent", DarkColors, { it.onAccent }, { it.accent }, 4.5)
    }

    /**
     * Dokaz da je `onAccent` NEOPHODAN: fiksna bela pada u svetloj temi.
     * Ako ovaj test ikad prestane da vazi, `onAccent` se sme ukloniti — do tada ne.
     */
    @Test
    fun plainWhiteWouldFailOnTheLightAccent() {
        val r = contrast(Color.White, LightColors.accent)
        assertTrue("bela na svetlom akcentu daje %.2f — da je >= 4.5, onAccent ne bi trebao".format(r),
            r < 4.5)
    }

    /**
     * TRI PARA SU ISPOD AA U SVETLOJ TEMI, i to je NASLEDJENO iz spec tabele
     * (iOS ima iste vrednosti). Izmereno pre pisanja ovog testa:
     *
     *   inkMuted / ground : 4,36  (svetla)   5,94 (tamna)
     *   inkMuted / fill   : 4,01  (svetla)   4,81 (tamna)
     *   warning  / surface: 3,61  (svetla)   8,48 (tamna)
     *
     * Prag se NE spusta da bi test prosao. Umesto toga se tvrdi da se stanje ne
     * POGORSAVA: ako neko promeni token i obori kontrast ispod izmerenog, test
     * pada i tera na razgovor. Popravka bi znacila razlaz sa iOS paletom, pa je
     * svesno odlozena i zapisana u „Poznata ogranicenja".
     */
    @Test
    fun knownSubAAPairsDoNotGetWorse() {
        check("svetla inkMuted/ground", LightColors, { it.inkMuted }, { it.ground }, 4.36)
        check("svetla inkMuted/fill", LightColors, { it.inkMuted }, { it.fill }, 4.01)
        check("svetla warning/surface", LightColors, { it.warning }, { it.surface }, 3.61)
    }

    @Test
    fun everyAdaptiveTokenActuallyDiffersBetweenThemes() {
        val pairs = listOf<Pair<String, (ChesskoColors) -> Color>>(
            "accent" to { it.accent }, "ground" to { it.ground }, "surface" to { it.surface },
            "navBar" to { it.navBar }, "fill" to { it.fill }, "line" to { it.line },
            "ink" to { it.ink }, "inkMuted" to { it.inkMuted },
            "success" to { it.success }, "warning" to { it.warning },
            "danger" to { it.danger }, "onAccent" to { it.onAccent }
        )
        for ((name, get) in pairs) {
            assertTrue("$name je isti u obe teme — ili nije adaptivan, ili je greska",
                get(LightColors) != get(DarkColors))
        }
        // `scrim`, `onScrim` i `inkFixed` su NAMERNO isti u obe teme.
        assertTrue("scrim mora biti isti", LightColors.scrim == DarkColors.scrim)
        assertTrue("onScrim mora biti isti", LightColors.onScrim == DarkColors.onScrim)
        assertTrue("inkFixed mora biti isti", LightColors.inkFixed == DarkColors.inkFixed)
    }
}
```

- [ ] **Step 2: Pokreni i vidi da pada**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest
```
Očekivano: FAIL — `Unresolved reference: ChesskoColors`.

> **Ako `androidx.compose.ui.graphics.Color` ne radi u JVM testu**, premesti `ContrastTest.kt` u `androidTest` i to zapiši u izveštaju. Ne dodaj zavisnost da bi radilo na JVM-u.

- [ ] **Step 3: Napiši tokene**

`app/src/main/java/com/veljkoni/chessko/ui/theme/DesignSystem.kt`:

```kotlin
package com.veljkoni.chessko.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - Dizajn tokeni
//
// Jedan izvor istine za boje, razmake i radijuse. Pravac „Tiho i precizno"
// (spec 5.6): neutralne podloge, jedan uzdrzan akcent, tabla je jedina
// zasicena stvar na ekranu.
//
// Akcent je FIKSAN — ne menja se sa temom table. Boje vezane za tablu (polja,
// poslednji potez, legalni potezi, sah) i dalje dolaze iz `BoardTheme`.
//
// Vrednosti su ISTE kao iOS (`Chessko/Views/DesignSystem.swift`). Kurikulum,
// lekcije i baza zadataka se vec dele; paleta je cetvrta zajednicka stvar.

data class ChesskoColors(
    val accent: Color,
    val ground: Color,
    val surface: Color,
    val navBar: Color,
    val fill: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    /** Semanticke boje — nose znacenje, nisu ukras. */
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Zatamnjenje ispod modalnih preklopa. Namerno isto u obe teme. */
    val scrim: Color,
    /** Tekst i ikone NA scrim-u. Namerno bela u obe teme. */
    val onScrim: Color,
    /** Taman tekst koji se NE invertuje — za povrsine koje same ne prate temu. */
    val inkFixed: Color,
    /**
     * Tekst i ikone NA `accent` podlozi. MORA biti adaptivan: `accent` menja
     * svetlinu izmedju tema, pa bela na svetloj varijanti daje 2,6:1 — ispod AA.
     * Cuva ga `ContrastTest.plainWhiteWouldFailOnTheLightAccent`.
     */
    val onAccent: Color
)

val LightColors = ChesskoColors(
    accent   = Color(0xFF2E4A8A),
    ground   = Color(0xFFF2F3F7),
    surface  = Color(0xFFFFFFFF),
    navBar   = Color(0xFFFFFFFF),
    fill     = Color(0xFFE7EAF1),
    line     = Color(0xFFDFE3EC),
    ink      = Color(0xFF161A22),
    inkMuted = Color(0xFF6B7280),
    success  = Color(0xFF2F7D4F),
    warning  = Color(0xFFB07D2B),
    danger   = Color(0xFFB3261E),
    scrim    = Color(0x8C000000),
    onScrim  = Color(0xFFFFFFFF),
    inkFixed = Color(0xFF161A22),
    onAccent = Color(0xFFFFFFFF)
)

val DarkColors = ChesskoColors(
    accent   = Color(0xFF7EA0E8),
    ground   = Color(0xFF0E1428),
    surface  = Color(0xFF161D33),
    navBar   = Color(0xFF131A2E),
    fill     = Color(0xFF1E2740),
    line     = Color(0xFF232C46),
    ink      = Color(0xFFEEF1F7),
    inkMuted = Color(0xFF8B93A7),
    success  = Color(0xFF6FCF97),
    warning  = Color(0xFFE0B252),
    danger   = Color(0xFFF2857A),
    scrim    = Color(0x8C000000),
    onScrim  = Color(0xFFFFFFFF),
    inkFixed = Color(0xFF161A22),
    onAccent = Color(0xFF161A22)
)

/**
 * `staticCompositionLocalOf`, ne `compositionLocalOf`: paleta se menja retko
 * (promena teme), a citaju je stotine mesta. Staticna varijanta ne prati
 * citaoce pojedinacno nego rekomponuje ceo podstablo — jeftinije za ovaj odnos.
 */
val LocalChesskoColors = staticCompositionLocalOf { DarkColors }

/**
 * Pozivna mesta citaju `DS.accent`, isto kao na iOS-u. Geteri su
 * `@ReadOnlyComposable` da ne prave sopstvenu grupu u kompoziciji.
 */
object DS {
    val accent: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.accent
    val ground: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.ground
    val surface: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.surface
    val navBar: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.navBar
    val fill: Color     @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.fill
    val line: Color     @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.line
    val ink: Color      @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.ink
    val inkMuted: Color @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.inkMuted
    val success: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.success
    val warning: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.warning
    val danger: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.danger
    val scrim: Color    @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.scrim
    val onScrim: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.onScrim
    val inkFixed: Color @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.inkFixed
    val onAccent: Color @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.onAccent

    object Space {
        val xs: Dp = 4.dp
        val s: Dp = 8.dp
        val m: Dp = 12.dp
        val l: Dp = 16.dp
        val xl: Dp = 24.dp
    }

    object Radius {
        val s: Dp = 8.dp
        val m: Dp = 12.dp
        val l: Dp = 16.dp
    }

    /**
     * Najveca stranica table. Na telefonu se nikad ne dostigne; na tabletu
     * sprecava da tabla proguta ceo ekran.
     */
    val maxBoardSide: Dp = 560.dp
}
```

- [ ] **Step 4: Prepiši `Theme.kt` i obriši `Color.kt`**

```kotlin
package com.veljkoni.chessko.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * `dynamicColor` je UKLONJEN, namerno.
 *
 * Zatecena verzija je bila Android Studio sablon sa `dynamicColor = true`, koji
 * na Androidu 12+ vuce boje sa KORISNIKOVE TAPETE. Za aplikaciju ciji spec
 * (5.6) trazi jedan fiksan akcent to nije funkcija nego greska — akcent bi se
 * menjao sa pozadinom telefona.
 *
 * `MaterialTheme.colorScheme` se i dalje popunjava, jer ga koriste zatecene
 * Material komponente (`Switch`, `Slider`, `Card`); bez toga bi one ostale
 * ljubicaste iz sablona.
 */
@Composable
fun ChesskoTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            error = colors.danger, outline = colors.line
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            error = colors.danger, outline = colors.line
        )
    }

    CompositionLocalProvider(LocalChesskoColors provides colors) {
        MaterialTheme(colorScheme = material, typography = Typography, content = content)
    }
}
```

Zatim: `git rm ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/theme/Color.kt` — `Purple80`/`Pink40` su šablon koji posle ovoga niko ne koristi. **Prvo proveri** `grep -rn "Purple80\|PurpleGrey80\|Pink80\|Purple40\|PurpleGrey40\|Pink40"` i obriši tek kad je 0 pogodaka van tog fajla.

- [ ] **Step 5: Tipografska skala u `Type.kt`**

Dodaj `object Type` sa `title`, `heading`, `body`, `caption`, `mono` (`FontFamily.Monospace`), po ugledu na iOS `Font.dsTitle/dsHeading/dsBody/dsCaption/dsMono`. Zadrži postojeći `Typography` koji `MaterialTheme` traži.

- [ ] **Step 6: Pokreni testove i pročitaj iz XML-a**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=0
for p in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    r=ET.parse(p).getroot(); t+=int(r.get("tests") or 0); f+=int(r.get("failures") or 0); e+=int(r.get("errors") or 0)
print(f"JVM: {t} testova, {f} padova, {e} gresaka")
PY
```
Očekivano: **41 test** (36 + 5 novih), 0 padova.

- [ ] **Step 7: Dokaži mutacijom da test kontrasta brani**

```bash
cd ChesskoAndroid
F=app/src/main/java/com/veljkoni/chessko/ui/theme/DesignSystem.kt
cp $F /tmp/ds.bak
# `onAccent` u svetloj temi na belu — tacno greska od koje test cuva
sed -i '' 's/    onAccent = Color(0xFFFFFFFF)\n)/XX/' $F 2>/dev/null || true
python3 - <<'PY'
import io
p="app/src/main/java/com/veljkoni/chessko/ui/theme/DesignSystem.kt"
s=io.open(p,encoding="utf-8").read()
i=s.index("val DarkColors")
head=s[:i].replace("onAccent = Color(0xFFFFFFFF)","onAccent = Color(0xFFFFFFFF) // ok")
# u DARK paleti onAccent na belu -> pada onAccentMeetsAAInBothThemes
s=head+s[i:].replace("onAccent = Color(0xFF161A22)","onAccent = Color(0xFFFFFFFF)")
io.open(p,"w",encoding="utf-8").write(s)
PY
./gradlew testDebugUnitTest 2>&1 | grep -E "onAccentMeetsAA|tests completed"
cp /tmp/ds.bak $F && rm /tmp/ds.bak
diff <(git show HEAD:$F 2>/dev/null || cat $F) $F >/dev/null && echo "fajl vracen" || echo "PROVERI vracanje"
```
Očekivano: `onAccentMeetsAAInBothThemes FAILED` sa izmerenom vrednošću ispod 4,5.

- [ ] **Step 8: Dokaži da se novi fajl kompajlira u aplikaciju**

```bash
cd ChesskoAndroid
printf '\nfun __probe(): Int = "nije broj"\n' >> app/src/main/java/com/veljkoni/chessko/ui/theme/DesignSystem.kt
./gradlew assembleDebug 2>&1 | tail -5   # MORA da padne
# vrati rucno (fajl jos nije commit-ovan): obrisi te dve linije i potvrdi `tail`
```

- [ ] **Step 9: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/theme/ \
        ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/ContrastTest.kt
git commit -F - <<'EOF'
Faza 6d-1, Task 1: dizajn tokeni, tema bez tapete, test kontrasta

Paleta je ista kao iOS (DesignSystem.swift) i zivi u ChesskoColors koju
ChesskoTheme bira po temi i objavljuje kroz CompositionLocal; pozivna mesta je
citaju kao DS.accent, isto kao iOS.

dynamicColor je UKLONJEN. Zateceni Android Studio sablon ga je imao ukljucenog,
pa je akcent dolazio sa KORISNIKOVE TAPETE -- za aplikaciju ciji spec trazi
jedan fiksan akcent to nije funkcija nego greska. Purple/Pink sablon obrisan.

Kontrast se racuna, ne procenjuje. Test cuva par onAccent/accent, koji je iOS
platio na ekranu: bela na svetlom akcentu daje 2,6:1.

Tri para su ispod AA u svetloj temi i to je NASLEDJENO iz spec tabele
(inkMuted/ground 4,36; inkMuted/fill 4,01; warning/surface 3,61). Prag NIJE
spusten -- test tvrdi da se stanje ne pogorsava, a razlika ide u dokumentaciju.

JVM testovi 36 -> 41.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 2: Tab Igra — `MainActivity.kt`

**Files:**
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/MainActivity.kt` (91 mesta)

**Interfaces:**
- Consumes: `DS.*`, `ChesskoTheme(darkTheme:)` iz Task-a 1

- [ ] **Step 1: Izbroj polaznu tačku**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
grep -c "Color(0x\|Color\.White\|Color\.Black" MainActivity.kt
```
Zapiši broj; na kraju taska mora biti **0**, osim izuzetaka koje izričito navedeš u izveštaju sa razlogom.

- [ ] **Step 2: Ukloni `dynamicColor` sa poziva teme**

`MainActivity.kt:96` sada zove `ChesskoTheme(darkTheme = isDark)`. Posle Task-a 1 taj potpis više nema `dynamicColor` — proveri da poziv i dalje odgovara.

- [ ] **Step 3: Migriraj po tabeli preslikavanja**

Koristi tabelu iz zaglavlja ovog plana. Za svako mesto gde tabela ne daje jednoznačan odgovor — **zapiši dilemu u izveštaj** umesto da izmišljaš token.

Posebno u ovom fajlu:
- pozadina ekrana → `DS.ground`
- kartice igrača → `DS.surface`, ivica `DS.line`
- aktivna kartica igrača → ivica `DS.accent`
- `Color.White.copy(alpha = 0.04f)` ispune → `DS.fill`
- status mata/šaha → `DS.danger`, remi → `DS.inkMuted`
- tab bar → `DS.navBar`, izabran tab `DS.accent`, neizabran `DS.inkMuted`

- [ ] **Step 4: Build i JVM testovi**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
```
Očekivano: BUILD SUCCESSFUL, **JVM 41**, 0 padova.

- [ ] **Step 5: Vizuelna provera OBE teme, jedan prolaz emulatora**

Emulator po receptu iz Global Constraints. Za svaku temu snimi tab Igra:
```bash
adb shell "run-as com.veljkoni.chessko sh -c \"sed -i 's|>dark<|>light<|' shared_prefs/chessko_settings.xml\""
adb shell am force-stop com.veljkoni.chessko && adb shell am start -n com.veljkoni.chessko/.MainActivity
```
**Traži konkretno:** nijedan beo tekst na beloj podlozi, nijedan tamni tekst na tamnoj, tabla nepromenjena u obe teme (ona ne prati sistemsku temu), i da je akcent isti bez obzira na izabranu temu table.

- [ ] **Step 6: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/MainActivity.kt
git commit -F - <<'EOF'
Faza 6d-1, Task 2: tab Igra prelazi na dizajn tokene

91 zakucana boja zamenjena tokenima po tabeli preslikavanja. Tab Igra je prvi
ekran na Androidu koji stvarno prati svetlu/tamnu temu -- do sada je podesavanje
postojalo a nije menjalo nista.

Boje table nisu dirane: bira ih tema table, ne sistemska tema.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 3: Zajedničke komponente

**Files:**
- Modify: `ui/UiComponents.kt` (20), `ui/CapturedPiecesView.kt` (10), `ui/MoveHistoryView.kt` (12), `ui/EvalBar.kt` (4), `ui/PromotionOverlay.kt` (3) — ukupno **49 mesta**

**Interfaces:**
- Consumes: `DS.*`, `DS.Space.*`, `DS.Radius.*` iz Task-a 1

- [ ] **Step 1: Izbroj polaznu tacku**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
for f in ui/UiComponents.kt ui/CapturedPiecesView.kt ui/MoveHistoryView.kt ui/EvalBar.kt ui/PromotionOverlay.kt; do
  printf "  %-30s %s\n" "$f" "$(grep -c 'Color(0x\|Color\.White\|Color\.Black' $f)"
done
```
Zbir mora na kraju biti **0**, osim izuzetaka koje izricito navedes u izvestaju sa razlogom.

- [ ] **Step 2: Migriraj po tabeli preslikavanja**

Koristi tabelu „Tabela preslikavanja" iz zaglavlja ovog plana — ne sopstvenu procenu. Za svako mesto gde tabela ne daje jednoznacan odgovor, **zapisi dilemu u izvestaj** umesto da izmislis token.

Dve posebne napomene za ove fajlove:
- **`EvalBar`** prikazuje prednost bele/crne — to su **strane u igri**, ne tema. Njegove dve boje ostaju fiksne; token ide samo na okvir i pozadinu.
- **`PromotionOverlay`** stoji na `DS.scrim`, pa tekst i figure na njemu idu na `DS.onScrim`, **nikad `DS.ink`** — u tamnoj temi bi se `ink` izgubio na tamnom preklopu.

- [ ] **Step 3: Build i JVM testovi**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
```
Ocekivano: BUILD SUCCESSFUL, **JVM 41**, 0 padova (citano iz XML-a).

- [ ] **Step 4: Vizuelna provera OBE teme, jedan prolaz emulatora**

Emulator po receptu iz Global Constraints — dize se jednom, gasi odmah. Promena teme:
```bash
adb shell "run-as com.veljkoni.chessko sh -c \"sed -i 's|>dark<|>light<|' shared_prefs/chessko_settings.xml\""
adb shell am force-stop com.veljkoni.chessko && adb shell am start -n com.veljkoni.chessko/.MainActivity
```
**Trazi konkretno:** uzete figure i istorija poteza citljivi u obe teme; preklop promocije citljiv u obe; eval traka i dalje razlikuje belu od crne.

- [ ] **Step 5: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/UiComponents.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/CapturedPiecesView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/MoveHistoryView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/EvalBar.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PromotionOverlay.kt
git commit -F - <<'EOF'
Faza 6d-1, Task 3: zajednicke komponente prelaze na tokene

49 zakucanih boja u pet fajlova koje koriste svi ekrani.

Dva izuzetka su zadrzana namerno: EvalBar razlikuje BELU od CRNE strane u
igri, pa te dve boje nisu tema nego sadrzaj; PromotionOverlay stoji na scrim-u
pa mu tekst ide na onScrim, ne na ink -- ink bi se u tamnoj temi izgubio.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 4: Tab Zadaci — `PuzzleView.kt`

**Files:**
- Modify: `ui/PuzzleView.kt` (27 mesta)

**Interfaces:**
- Consumes: `DS.*`, `DS.Space.*`, `DS.Radius.*`, `DS.Type.*` iz Task-a 1

- [ ] **Step 1: Izbroj polaznu tacku**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
grep -c "Color(0x\|Color\.White\|Color\.Black" ui/PuzzleView.kt
```
Zbir mora na kraju biti **0**, osim izuzetaka koje izricito navedes u izvestaju sa razlogom.

- [ ] **Step 2: Migriraj po tabeli preslikavanja**

Koristi tabelu preslikavanja iz zaglavlja ovog plana — ne sopstvenu procenu. Za svako mesto gde tabela ne daje jednoznacan odgovor, **zapisi dilemu u izvestaj** umesto da izmislis token.

- **Rejting bedz** koristi monospaced cifre → `DS.Type.mono`.
- **Cipovi tema zadatka** → `DS.fill` podloga + `DS.inkMuted` tekst.
- **Statusna kartica** menja boju po ishodu: reseno → `DS.success`, pogresan potez → `DS.danger`, u toku → `DS.accent`. Tekst NA njima ide na `DS.onAccent` ili `DS.inkFixed` po podlozi — **nikad `Color.White`** (zamka 2).
- **Kvacica resenosti** u traci datuma → `DS.success`.

- [ ] **Step 3: Build i JVM testovi**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
```
Ocekivano: BUILD SUCCESSFUL, **JVM 41**, 0 padova (citano iz XML-a).

- [ ] **Step 4: Vizuelna provera OBE teme, jedan prolaz emulatora**

Emulator po receptu iz Global Constraints — dize se jednom, gasi odmah. Promena teme:
```bash
adb shell "run-as com.veljkoni.chessko sh -c \"sed -i 's|>dark<|>light<|' shared_prefs/chessko_settings.xml\""
adb shell am force-stop com.veljkoni.chessko && adb shell am start -n com.veljkoni.chessko/.MainActivity
```

**Trazi konkretno:** rejting bedz i cipovi citljivi u obe teme; statusna kartica citljiva u sva tri stanja (u toku, resen, pogresan potez); tabla nepromenjena.

- [ ] **Step 5: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PuzzleView.kt
git commit -F - <<'EOF'
Faza 6d-1, Task 4: tab Zadaci prelazi na tokene

27 zakucanih boja zamenjeno tokenima.

Statusna kartica menja podlogu po ishodu, pa joj tekst ide na onAccent/inkFixed
umesto na fiksnu belu -- accent menja svetlinu izmedju tema, i bela na svetloj
varijanti daje 2,6:1.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 5: Tab Put — `PathView.kt`, `StepPracticeView.kt`, `StepGameView.kt`

**Files:**
- Modify: `ui/PathView.kt` (12), `ui/StepPracticeView.kt` (11), `ui/StepGameView.kt` (20) — ukupno **43 mesta**

**Interfaces:**
- Consumes: `DS.*`, `DS.Space.*`, `DS.Radius.*`, `DS.Type.*` iz Task-a 1

- [ ] **Step 1: Izbroj polaznu tacku**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
for f in ui/PathView.kt ui/StepPracticeView.kt ui/StepGameView.kt; do
  printf "  %-28s %s\n" "$f" "$(grep -c 'Color(0x\|Color\.White\|Color\.Black' $f)"
done
```
Zbir mora na kraju biti **0**, osim izuzetaka koje izricito navedes u izvestaju sa razlogom.

- [ ] **Step 2: Migriraj po tabeli preslikavanja**

Koristi tabelu preslikavanja iz zaglavlja ovog plana — ne sopstvenu procenu. Za svako mesto gde tabela ne daje jednoznacan odgovor, **zapisi dilemu u izvestaj** umesto da izmislis token.

- **Kartica nastavka** je jedina primarna akcija na ekranu → `DS.accent` podloga + `DS.onAccent` tekst.
- **Stanja koraka:** zavrseno → `DS.success`, dostupno → `DS.ink`, zakljucano → `DS.inkMuted`.
- **Traka pilula** u vezbi: reseno → `DS.success`, tekuce → `DS.accent`, neodigrano → `DS.fill`.
- **Poruka o dnevnom cilju:** ispunjen → `DS.success`; neispunjen → `DS.inkMuted`.

- [ ] **Step 3: Build i JVM testovi**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
```
Ocekivano: BUILD SUCCESSFUL, **JVM 41**, 0 padova (citano iz XML-a).

- [ ] **Step 4: Vizuelna provera OBE teme, jedan prolaz emulatora**

Emulator po receptu iz Global Constraints — dize se jednom, gasi odmah. Promena teme:
```bash
adb shell "run-as com.veljkoni.chessko sh -c \"sed -i 's|>dark<|>light<|' shared_prefs/chessko_settings.xml\""
adb shell am force-stop com.veljkoni.chessko && adb shell am start -n com.veljkoni.chessko/.MainActivity
```

**Trazi konkretno:** kartica nastavka citljiva u obe teme; tri stanja koraka se razlikuju i u svetloj; traka pilula razaznatljiva; kartica protivnika u koraku partije citljiva.

- [ ] **Step 5: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/PathView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepPracticeView.kt \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/StepGameView.kt
git commit -F - <<'EOF'
Faza 6d-1, Task 5: tab Put prelazi na tokene

43 zakucane boje u tri fajla Puta.

Kartica nastavka je jedina primarna akcija na ekranu, pa nosi accent podlogu i
onAccent tekst. Tri stanja koraka (zavrseno/dostupno/zakljucano) moraju da se
razlikuju i u svetloj temi, ne samo u tamnoj.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 6: Podesavanja — `SettingsView.kt`

**Files:**
- Modify: `ui/SettingsView.kt` (70 mesta) — najveci pojedinacni fajl u ovoj krisci

**Interfaces:**
- Consumes: `DS.*`, `DS.Space.*`, `DS.Radius.*`, `DS.Type.*` iz Task-a 1

- [ ] **Step 1: Izbroj polaznu tacku**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
grep -c "Color(0x\|Color\.White\|Color\.Black" ui/SettingsView.kt
```
Zbir mora na kraju biti **0**, osim izuzetaka koje izricito navedes u izvestaju sa razlogom.

- [ ] **Step 2: Migriraj po tabeli preslikavanja**

Koristi tabelu preslikavanja iz zaglavlja ovog plana — ne sopstvenu procenu. Za svako mesto gde tabela ne daje jednoznacan odgovor, **zapisi dilemu u izvestaj** umesto da izmislis token.

> **IZUZETAK koji se lako pogresi: prikaz tema table i stilova figura NE prelazi na tokene.**
> Ti kvadratici pokazuju kako tema table izgleda — to je **sadrzaj**, ne hrom. Ako se oboje
> tokenima, korisnik vise ne vidi sta bira. Doslovno isti izuzetak koji `CLAUDE.md` navodi za iOS.

Ostalo:
- Sekcije liste → `DS.surface`, razdvojnici → `DS.line`
- Statistika (`StatItem`) → brojka `DS.ink`, oznaka `DS.inkMuted`
- Dugme za resetovanje statistike → `DS.danger`
- Prekidaci i klizaci → `MaterialTheme` ih od Task-a 1 dobija iz `colorScheme`, pa ih **ne boj rucno**

- [ ] **Step 3: Build i JVM testovi**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest
```
Ocekivano: BUILD SUCCESSFUL, **JVM 41**, 0 padova (citano iz XML-a).

- [ ] **Step 4: Vizuelna provera OBE teme, jedan prolaz emulatora**

Emulator po receptu iz Global Constraints — dize se jednom, gasi odmah. Promena teme:
```bash
adb shell "run-as com.veljkoni.chessko sh -c \"sed -i 's|>dark<|>light<|' shared_prefs/chessko_settings.xml\""
adb shell am force-stop com.veljkoni.chessko && adb shell am start -n com.veljkoni.chessko/.MainActivity
```

**Trazi konkretno:** svih 8 tema table i svi stilovi figura se u SVETLOJ temi vide tacno onako kako izgledaju na tabli; statistika citljiva; dugme za resetovanje i dalje crveno i u svetloj.

- [ ] **Step 5: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/SettingsView.kt
git commit -F - <<'EOF'
Faza 6d-1, Task 6: Podesavanja prelaze na tokene

70 zakucanih boja -- najveci pojedinacni fajl krisce.

Prikaz tema table i stilova figura je NAMERNO ostavljen netaknut: ti kvadratici
su sadrzaj, ne hrom. Da su obojeni tokenima, korisnik vise ne bi video sta bira.
Isti izuzetak postoji i na iOS-u.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
```

---

## Task 7: Zatvaranje kriške

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Prebroj šta je ostalo**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
for f in MainActivity.kt ui/UiComponents.kt ui/CapturedPiecesView.kt ui/MoveHistoryView.kt \
         ui/EvalBar.kt ui/PromotionOverlay.kt ui/PuzzleView.kt ui/PathView.kt \
         ui/StepPracticeView.kt ui/StepGameView.kt ui/SettingsView.kt; do
  printf "  %-30s %s\n" "$f" "$(grep -c 'Color(0x\|Color\.White\|Color\.Black' $f)"
done
```
Svaki broj koji nije 0 mora biti objašnjen u `CLAUDE.md` kao namerni izuzetak.

- [ ] **Step 2: Ažuriraj `CLAUDE.md`**

- Tabela „Stanje Android porta": red `1 — dizajn sistem` iz `ne` u **`delimično (6d-1)`**, uz nov red
- Nova podsekcija **„Android dizajn sistem"** u sekciji „Dizajn sistem": ista paleta kao iOS, `CompositionLocal` mehanizam, i **izričito** koji ekrani prate temu a koji još ne
- **„Poznata ograničenja"** — upiši:
  - **Tri para tokena su ispod WCAG AA u svetloj temi**, nasleđeno iz spec tabele i identično iOS-u: `inkMuted`/`ground` 4,36; `inkMuted`/`fill` 4,01; `warning`/`surface` 3,61. Čuva ih `ContrastTest.knownSubAAPairsDoNotGetWorse` od pogoršanja. Popravka bi značila razlaz sa iOS paletom.
  - **Sat, lekcije i tabla još ne prate temu** — to je 6d-2. Do tada je aplikacija u svetloj temi vidljivo neujednačena.
  - **`dynamicColor` je uklonjen** i zašto (tapeta je menjala akcent).
- Changelog: nov unos sa **izmerenim** brojevima

- [ ] **Step 3: Provere celog stabla**

```bash
cd /Users/veljkoodobasic/Documents/Projects/Chess
D=$(git diff --stat 117a26c..HEAD -- Chessko Chessko.xcodeproj); [ -z "$D" ] && echo "iOS netaknut"
git diff --stat 117a26c..HEAD -- ChesskoAndroid/app/build.gradle.kts ChesskoAndroid/gradle/libs.versions.toml
grep -rn "Purple80\|Pink40\|dynamicColor" ChesskoAndroid/app/src/main/ || echo "sablon uklonjen"
```

- [ ] **Step 4: Oba skupa testova na završnom stablu**

Očekivano: **JVM 41**, **instrumentisani 46**, 0 padova.

- [ ] **Step 5: Commit**

---

## Self-Review

**1. Pokrivenost spec-a (5.6, Faza 1)**

| Zahtev | Task |
|---|---|
| Tokeni: boje, razmaci, radijusi, tipografska skala | 1 |
| Tabela od 8 boja × svetla/tamna | 1 |
| Akcent fiksan, nezavisan od teme table | 1 (vrednost), 2–6 (primena) |
| Jedan akcent umesto više boja po ekranu | 2–6 (tabela preslikavanja: cijan i plava → `DS.accent`) |
| Boje ostaju gde nose značenje | 1 (`success`/`warning`/`danger`), tabela izuzetaka |
| Tabla ostaje jedini šaroliki element | tabela izuzetaka; `BoardView` je u 6d-2 |
| Ikone tabova (puzzle za Zadatke) | **već urađeno** na Androidu — spec to i kaže |
| Emoji u UI → ikone | **6d-2** (`LearnView`, `LessonRenderer`) |
| Mrtav prostor, kartice igrača, eval bar | Faza 4 ih je već rešila na Androidu kroz Put; `EvalBar` u Task-u 3 |
| Duplirani naslovi | ne postoji na Androidu (`PathView` ima jedan naslov) |

Ono što ovaj plan **ne pokriva i to izričito kaže**: sat, lekcije, tabla, i emoji u lekcijama — sve u 6d-2.

**2. Placeholderi:** Taskovi 3–6 namerno ne ponavljaju korake iz Task-a 2 nego navode **samo ono što je specifično za te fajlove** — tabela preslikavanja u zaglavlju je zajednička i obavezna, pa bi prepisivanje istih šest koraka pet puta bilo šum, ne preciznost. Svaki od tih taskova ima svoje posebne napomene i svoju vizuelnu proveru.

**3. Doslednost tipova:** `ChesskoColors` (Task 1) koristi se u `ContrastTest` i u `ChesskoTheme`; `DS.*` geteri u Taskovima 2–6; `LocalChesskoColors` samo u `Theme.kt` i `DesignSystem.kt`. `DS.Type.*` (Task 1, `Type.kt`) koristi se u Taskovima 4 i 6.

**Izmereno pre pisanja plana, da se ne procenjuje kasnije:** kontrast svih devet parova u obe teme (vrednosti u Task-u 1), 466 zakucanih boja po fajlovima, i činjenica da nijedan ekran ne čita temu.
