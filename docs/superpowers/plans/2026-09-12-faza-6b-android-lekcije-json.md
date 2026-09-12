# Faza 6b — Android: sadržaj lekcija iz JSON-a

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android lekcije čitaju isti `Content/lessons/*.json` koji iOS već isporučuje, umesto sadržaja zakucanog u Kotlinu — čime pada i ostatak spec-a 4.5.

**Architecture:** Ista podela kao na iOS-u: šema i parser su čist Kotlin bez ijednog `android.*` uvoza (pa se testiraju običnim JVM testovima), repozitorijum čita iz `assets` sa lancem jezika, a renderer je jedino mesto koje zna kako se blok crta. Postojeće komponente (`LPara`, `LBullet`, `LBox`, `LSectionHeader`, `LNumberedRule`, `OpeningExerciseCard`, `MateExerciseCard`, `BoardView`) se **ponovo koriste** — ovaj plan ne prepisuje izgled, nego zamenjuje izvor teksta.

**Tech Stack:** Kotlin, Jetpack Compose, **`org.json`** (ugrađen u Android — `kotlinx.serialization` bi bila nova zavisnost i zabranjena je), JUnit4, instrumentisani testovi.

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` — sekcije **5.2 Sadržaj van koda**, **4.5 Android meša jezike**, **Faza 6**.

## Global Constraints

- **`create_xcode_project.py` se NIKAD ne pokreće.**
- **`Chessko/` (iOS) se NE dira.** Ovaj plan menja samo `ChesskoAndroid/` i `CLAUDE.md`.
- **Nijedna nova Gradle zavisnost.** JSON ide kroz `org.json`.
- **JSON fajlovi su bajt-identični iOS verzijama.** Ne prevode se, ne preformatiraju, ne skraćuju.
- **Izvorni jezik je srpski.** Tekst iz JSON-a stiže **već preveden** i **NE ide kroz `loc()`** — kroz `loc()` ide samo hrom (dugmad, podrazumevane poruke vežbi).
- `minSdk = 26`, `targetSdk = 36`.
- Gradle traži `export ANDROID_HOME=~/Library/Android/sdk` pre svake komande.

## Okruženje — ne sme otimati fokus korisniku

- Emulator **isključivo bez prozora**:
  ```bash
  export ANDROID_HOME=~/Library/Android/sdk
  $ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim &
  $ANDROID_HOME/platform-tools/adb wait-for-device
  ```
  Na kraju `adb emu kill`. **Nikad Android Studio, nikad `open -a`.**
- Sintetički tapovi **rade** (`adb shell input tap`), koordinate iz `uiautomator dump`.
- **`connectedDebugAndroidTest` ume da kaže `BUILD SUCCESSFUL` a da ne pokrene nijedan test**
  (npr. pun disk). Rezultat se čita iz `app/build/outputs/androidTest-results/connected/debug/*.xml`,
  nikad iz izlaznog koda.
- Pomoćni fajlovi idu **apsolutnom putanjom** u
  `/private/tmp/claude-501/-Users-veljkoodobasic-Documents-Projects-Chess/8c4918e4-1a29-42d9-8d21-a8c5b122d5c5/scratchpad`.

---

## Zatečeno stanje

`ChesskoAndroid/.../ui/LearnView.kt` je **1322 linije**, od čega su `Lesson1Content` … `Lesson4Content`
(linije ~576–975) sadržaj **zakucan u Kotlinu** — **46 srpskih stringova** prosleđenih kao *pozicioni*
argumenti. Zato ih pretraga po `text = "…"` ne vidi, i zato su promašeni u Fazi 6a.

Šta **već postoji** i ponovo se koristi (ne pisati iznova):

| Komponenta | Linija | Za koji blok |
|---|---|---|
| `LBox(icon, title, text, color)` | 488 | `box` |
| `LPara(text)` | 510 | `paragraph` |
| `LBullet(icon, title, text, color)` | 521 | `bullets` |
| `LSectionHeader(icon, title, color)` | 538 | `heading` |
| `LNumberedRule(number, title, text, color)` | 550 | `numberedRule` |
| `OpeningExerciseCard(line: OpeningLine)` | 976 | `exercise` sa `kind: "scripted"` |
| `MateExerciseCard(...)` | 1073 | `exercise` sa `kind: "vsEngine"` |
| `BoardView(...)` | — | `board`, `explorer` |

Šta **ne postoji** i mora se napisati: prikaz reda figure (`pieceRow`) i tabele vrednosti
(`pieceValueTable`). Lekcija 3 danas tu ima običan `LPara` sa tekstom.

---

## Struktura fajlova

| Fajl | Odgovornost |
|---|---|
| `ChesskoAndroid/app/src/main/assets/lessons/*.json` **(novo, 36 fajlova)** | Kopija iOS sadržaja, bajt-identična. |
| `…/models/LessonContent.kt` **(nov, bez `android.*` uvoza)** | Šema + `org.json` parser. JVM-testabilan. |
| `…/logic/LessonRepository.kt` **(nov)** | Čitanje iz `assets`, keš, lanac jezika. |
| `…/ui/LessonRenderer.kt` **(nov)** | Jedino mesto koje zna kako se blok crta. |
| `…/ui/LearnView.kt` (izmena) | Lista lekcija iz repozitorijuma; briše se ~400 linija zakucanog sadržaja. |
| `app/src/test/.../LessonContentTest.kt` **(nov)** | JVM testovi parsera. |
| `app/src/androidTest/.../LessonRepositoryTest.kt` **(nov)** | Instrumentisani testovi nad stvarnim `assets`. |

---

## Task 1: Šema i parser

**Files:**
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/models/LessonContent.kt`
- Create: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/LessonContentTest.kt`

**Interfaces:**
- Produces (Taskovi 2–4 zavise od ovoga):
  - `data class LessonDocument(id, language, title, subtitle, icon, blocks: List<LessonBlock>)`
  - `sealed class LessonBlock` sa podklasama: `Heading(text, icon)`, `Paragraph(text)`,
    `Bullets(items: List<BulletItem>)`, `Box(style: BoxStyle, icon, title, text)`,
    `Quote(text, author)`, `PieceRow(piece, name, count)`,
    `NumberedRule(number: Int, title, text)`, `PieceValueTable(rows: List<PieceValueRow>)`,
    `Board(fen, caption, interactive: Boolean)`, `Explorer`, `Exercise(spec: ExerciseSpec)`, `Divider`
  - `data class BulletItem(icon, title, text, style: BoxStyle?)`
  - `data class PieceValueRow(piece, name, value: String, valueLabel: String?)`
  - `enum class BoxStyle { RULE, WARNING, INFO }`
  - `enum class ExerciseKind { SCRIPTED, VS_ENGINE }`
  - `data class ExerciseSpec(kind, title, hint, icon, uciMoves: List<String>?, startFEN: String?, solvedMessage: String?, wrongMessage: String?, playingPrompt: String?, mateIn: Int?)`
  - `object LessonParser { fun parse(json: String): LessonDocument }` — **baca** `IllegalArgumentException` na nepoznat tip bloka

**Zašto parser BACA na nepoznat tip.** Sadržaj je od ove faze van dometa kompajlera. Ako bi se
nepoznat blok tiho preskočio, pokvaren JSON bi dao lekciju sa rupom koju niko ne primeti. iOS ima
istu nameru (`LessonBlock` dekoder baca), i to je jedina stvar koja može da vikne.

**`value` u `pieceValueTable` je `String`, ne `Int`** — kralj ima vrednost `"∞"`.

- [ ] **Step 1: Napisati JVM testove koji padaju**

`ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/LessonContentTest.kt`:

```kotlin
package com.veljkoni.chessko

import com.veljkoni.chessko.models.BoxStyle
import com.veljkoni.chessko.models.ExerciseKind
import com.veljkoni.chessko.models.LessonBlock
import com.veljkoni.chessko.models.LessonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LessonContentTest {

    private fun doc(blocks: String) = """
        {"id":"t","language":"sr","title":"N","subtitle":"P","icon":"book.fill","blocks":[$blocks]}
    """.trimIndent()

    @Test
    fun parsesDocumentHeader() {
        val d = LessonParser.parse(doc("""{"type":"paragraph","text":"zdravo"}"""))
        assertEquals("t", d.id)
        assertEquals("sr", d.language)
        assertEquals("N", d.title)
        assertEquals("P", d.subtitle)
        assertEquals("book.fill", d.icon)
        assertEquals(1, d.blocks.size)
    }

    @Test
    fun parsesEveryBlockTypeTheShippedContentUses() {
        // 11 tipova koje isporuceni JSON stvarno koristi, plus `quote` koji sema
        // definise a sadrzaj jos ne koristi — podrzan je da lekcija koja ga
        // doda sutra ne pukne.
        val d = LessonParser.parse(doc("""
            {"type":"heading","text":"H","icon":"i"},
            {"type":"paragraph","text":"P"},
            {"type":"bullets","items":[{"icon":"a","title":"b","text":"c"}]},
            {"type":"box","style":"rule","icon":"i","title":"T","text":"X"},
            {"type":"quote","text":"Q","author":"A"},
            {"type":"pieceRow","piece":"pawn","name":"Pesak","count":"8"},
            {"type":"numberedRule","number":2,"title":"T","text":"X"},
            {"type":"pieceValueTable","rows":[{"piece":"king","name":"Kralj","value":"∞"}]},
            {"type":"board","fen":"8/8/8/8/8/8/8/8 w - - 0 1","caption":"C","interactive":false},
            {"type":"explorer"},
            {"type":"exercise","kind":"scripted","title":"T","hint":"H","icon":"i","uciMoves":["e2e4"]},
            {"type":"divider"}
        """.trimIndent()))
        assertEquals(12, d.blocks.size)
        assertTrue(d.blocks[0] is LessonBlock.Heading)
        assertTrue(d.blocks[4] is LessonBlock.Quote)
        assertTrue(d.blocks[9] is LessonBlock.Explorer)
        assertTrue(d.blocks[11] is LessonBlock.Divider)
    }

    @Test
    fun kingValueStaysAStringBecauseItIsInfinity() {
        // Da je `value` Int, kralj bi pukao pri parsiranju i cela lekcija 3 bi
        // nestala — a kralj je jedini red koji to obara.
        val d = LessonParser.parse(doc(
            """{"type":"pieceValueTable","rows":[{"piece":"king","name":"Kralj","value":"∞"}]}"""))
        val t = d.blocks[0] as LessonBlock.PieceValueTable
        assertEquals("∞", t.rows[0].value)
    }

    @Test
    fun boxStyleMapsAllThreeValues() {
        for ((raw, expected) in listOf("rule" to BoxStyle.RULE,
                                       "warning" to BoxStyle.WARNING,
                                       "info" to BoxStyle.INFO)) {
            val d = LessonParser.parse(doc(
                """{"type":"box","style":"$raw","icon":"i","title":"T","text":"X"}"""))
            assertEquals(expected, (d.blocks[0] as LessonBlock.Box).style)
        }
    }

    @Test
    fun exerciseKeepsOptionalFieldsAsNullWhenAbsent() {
        val d = LessonParser.parse(doc(
            """{"type":"exercise","kind":"vsEngine","title":"T","hint":"H","icon":"i","startFEN":"8/8/8/8/8/8/8/8 w - - 0 1"}"""))
        val e = (d.blocks[0] as LessonBlock.Exercise).spec
        assertEquals(ExerciseKind.VS_ENGINE, e.kind)
        assertEquals(null, e.uciMoves)
        assertEquals(null, e.mateIn)
        assertEquals(null, e.solvedMessage)
    }

    @Test
    fun unknownBlockTypeThrowsInsteadOfBeingSkipped() {
        // Tih preskok bi dao lekciju sa rupom koju niko ne primeti. Sadrzaj je
        // od ove faze van dometa kompajlera, pa je ovo jedino mesto koje moze
        // da vikne.
        try {
            LessonParser.parse(doc("""{"type":"teleporter","text":"X"}"""))
            fail("nepoznat tip bloka mora da baci")
        } catch (e: IllegalArgumentException) {
            assertTrue("poruka mora da imenuje tip: ${e.message}",
                       e.message!!.contains("teleporter"))
        }
    }

    @Test
    fun unknownBoxStyleThrows() {
        try {
            LessonParser.parse(doc("""{"type":"box","style":"neon","icon":"i","title":"T","text":"X"}"""))
            fail("nepoznat stil kutije mora da baci")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("neon"))
        }
    }

    @Test
    fun bulletStyleIsOptional() {
        val d = LessonParser.parse(doc(
            """{"type":"bullets","items":[{"icon":"a","title":"b","text":"c"},{"icon":"d","title":"e","text":"f","style":"warning"}]}"""))
        val b = d.blocks[0] as LessonBlock.Bullets
        assertEquals(null, b.items[0].style)
        assertEquals(BoxStyle.WARNING, b.items[1].style)
    }
}
```

- [ ] **Step 2: Pokrenuti i videti da padaju**

```bash
export ANDROID_HOME=~/Library/Android/sdk
cd ChesskoAndroid && ./gradlew testDebugUnitTest --tests '*LessonContentTest*'
```
Expected: FAIL, `Unresolved reference: LessonParser`.
**Proveri BROJ pokrenutih testova** — filter koji ne pogađa ništa prijavljuje „passed".

- [ ] **Step 3: Napisati `LessonContent.kt`**

```kotlin
package com.veljkoni.chessko.models

import org.json.JSONArray
import org.json.JSONObject

// MARK: - Sadrzaj lekcija
//
// Sema + parser. NAMERNO bez ijednog `android.*` uvoza: tako se ceo ovaj fajl
// testira obicnim JVM testom, bez emulatora. `org.json` je ugradjen u Android,
// ali ga `testDebugUnitTest` obezbedjuje kroz stub — zato testovi koji ga
// koriste MORAJU da rade sa pravim implementacijama (vidi Task 1, Step 5).

enum class BoxStyle { RULE, WARNING, INFO }

enum class ExerciseKind { SCRIPTED, VS_ENGINE }

data class BulletItem(
    val icon: String,
    val title: String,
    val text: String,
    /// `null` = koristi akcent lekcije. Postoji da bi pojedinacna stavka mogla
    /// da nosi upozorenje bez posebnog tipa bloka.
    val style: BoxStyle? = null
)

data class PieceValueRow(
    val piece: String,
    val name: String,
    /// STRING, ne Int: kralj nosi „∞". Da je broj, ceo blok bi pukao na kralju
    /// i lekcija bi nestala.
    val value: String,
    val valueLabel: String? = null
)

data class ExerciseSpec(
    val kind: ExerciseKind,
    val title: String,
    val hint: String,
    val icon: String,
    val uciMoves: List<String>? = null,
    val startFEN: String? = null,
    val solvedMessage: String? = null,
    val wrongMessage: String? = null,
    val playingPrompt: String? = null,
    val mateIn: Int? = null
)

sealed class LessonBlock {
    data class Heading(val text: String, val icon: String) : LessonBlock()
    data class Paragraph(val text: String) : LessonBlock()
    data class Bullets(val items: List<BulletItem>) : LessonBlock()
    data class Box(val style: BoxStyle, val icon: String, val title: String, val text: String) : LessonBlock()
    data class Quote(val text: String, val author: String) : LessonBlock()
    data class PieceRow(val piece: String, val name: String, val count: String) : LessonBlock()
    data class NumberedRule(val number: Int, val title: String, val text: String) : LessonBlock()
    data class PieceValueTable(val rows: List<PieceValueRow>) : LessonBlock()
    data class Board(val fen: String, val caption: String, val interactive: Boolean) : LessonBlock()
    data object Explorer : LessonBlock()
    data class Exercise(val spec: ExerciseSpec) : LessonBlock()
    data object Divider : LessonBlock()
}

data class LessonDocument(
    val id: String,
    val language: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val blocks: List<LessonBlock>
)

object LessonParser {

    fun parse(json: String): LessonDocument {
        val root = JSONObject(json)
        val arr = root.getJSONArray("blocks")
        val blocks = ArrayList<LessonBlock>(arr.length())
        for (i in 0 until arr.length()) blocks.add(block(arr.getJSONObject(i)))
        return LessonDocument(
            id = root.getString("id"),
            language = root.getString("language"),
            title = root.getString("title"),
            subtitle = root.getString("subtitle"),
            icon = root.getString("icon"),
            blocks = blocks
        )
    }

    private fun block(o: JSONObject): LessonBlock = when (val t = o.getString("type")) {
        "heading" -> LessonBlock.Heading(o.getString("text"), o.getString("icon"))
        "paragraph" -> LessonBlock.Paragraph(o.getString("text"))
        "bullets" -> LessonBlock.Bullets(o.getJSONArray("items").map {
            BulletItem(it.getString("icon"), it.getString("title"), it.getString("text"),
                       it.optStyle("style"))
        })
        "box" -> LessonBlock.Box(style(o.getString("style")), o.getString("icon"),
                                 o.getString("title"), o.getString("text"))
        "quote" -> LessonBlock.Quote(o.getString("text"), o.getString("author"))
        "pieceRow" -> LessonBlock.PieceRow(o.getString("piece"), o.getString("name"),
                                           o.getString("count"))
        "numberedRule" -> LessonBlock.NumberedRule(o.getInt("number"), o.getString("title"),
                                                   o.getString("text"))
        "pieceValueTable" -> LessonBlock.PieceValueTable(o.getJSONArray("rows").map {
            PieceValueRow(it.getString("piece"), it.getString("name"), it.getString("value"),
                          it.optStringOrNull("valueLabel"))
        })
        "board" -> LessonBlock.Board(o.getString("fen"), o.getString("caption"),
                                     o.optBoolean("interactive", false))
        "explorer" -> LessonBlock.Explorer
        "exercise" -> LessonBlock.Exercise(exercise(o))
        "divider" -> LessonBlock.Divider
        // Tih preskok bi dao lekciju sa rupom koju niko ne primeti.
        else -> throw IllegalArgumentException("Nepoznat tip bloka u lekciji: \"$t\"")
    }

    private fun exercise(o: JSONObject) = ExerciseSpec(
        kind = when (val k = o.getString("kind")) {
            "scripted" -> ExerciseKind.SCRIPTED
            "vsEngine" -> ExerciseKind.VS_ENGINE
            else -> throw IllegalArgumentException("Nepoznata vrsta vezbe: \"$k\"")
        },
        title = o.getString("title"),
        hint = o.getString("hint"),
        icon = o.getString("icon"),
        uciMoves = o.optJSONArray("uciMoves")?.let { a -> (0 until a.length()).map { a.getString(it) } },
        startFEN = o.optStringOrNull("startFEN"),
        solvedMessage = o.optStringOrNull("solvedMessage"),
        wrongMessage = o.optStringOrNull("wrongMessage"),
        playingPrompt = o.optStringOrNull("playingPrompt"),
        mateIn = if (o.isNull("mateIn")) null else o.optInt("mateIn").takeIf { o.has("mateIn") }
    )

    private fun style(raw: String) = when (raw) {
        "rule" -> BoxStyle.RULE
        "warning" -> BoxStyle.WARNING
        "info" -> BoxStyle.INFO
        else -> throw IllegalArgumentException("Nepoznat stil kutije: \"$raw\"")
    }

    /// `optString` vraca PRAZAN STRING kad kljuca nema, ne `null` — zbog toga
    /// bi opciona polja postala `""` i renderer bi crtao prazne redove umesto
    /// da ih preskoci.
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optStyle(key: String): BoxStyle? =
        optStringOrNull(key)?.let { style(it) }

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
```

- [ ] **Step 4: Pokrenuti testove**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest --tests '*LessonContentTest*'
```
Expected: **8 testova prolazi.**

- [ ] **Step 5: `org.json` u JVM testu NE RADI — testovi idu u `androidTest`**

**Ovo se ostvarilo i odlučeno je.** `testDebugUnitTest` pada sa
`RuntimeException: Method getJSONArray in org.json.JSONObject not mocked` — Android JVM testovi
nose *stub* `org.json`-a. `isReturnDefaultValues = true` to **ne rešava**; stub i dalje baca.

Odluka: `LessonContentTest.kt` ide u **`app/src/androidTest/`** sa `@RunWith(AndroidJUnit4::class)`.

Razlog nije samo zabrana zavisnosti. Test-only zavisnost (`testImplementation("org.json:json")`)
testirala bi parser protiv **druge implementacije** `org.json`-a od one koja se isporučuje na
uređaju — pa bi parser mogao da prolazi testove a da se na telefonu ponaša drugačije. To je ista
klasa greške kao test koji deli pogrešnu pretpostavku sa kodom. Instrumentisani test radi protiv
**pravog** `org.json`-a.

Cena, zapisana pošteno: `LessonContent.kt` ostaje bez ijednog `android.*` uvoza (pa je prenosiv),
ali se **njegovi testovi ne mogu pokrenuti bez emulatora**. Projektni cilj „JVM-testabilan parser"
time nije ispunjen — ispunjen je samo „parser bez Android zavisnosti".

- [ ] **Step 5b (istorijski zapis): zašto ne ide u JVM testove**

Android-ov `org.json` je u `testDebugUnitTest` podrazumevano *stub* koji baca
`RuntimeException("Stub!")`. Ako testovi padnu tom porukom, u `app/build.gradle.kts` dodati:

```kotlin
android {
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}
```

To **nije dovoljno** za `org.json` (stub i dalje baca), pa je ispravno rešenje da testovi ne
zavise od Android stub-a: `testImplementation` već povlači JUnit, a `org.json` postoji i u JDK-u
kroz `json` artefakt — **ali to bi bila nova zavisnost i zabranjena je.**

Ako se stub pokaže kao prepreka, **STANI i prijavi**: izbor je ili (a) preseliti ove testove u
`androidTest` (rade na emulatoru, pravi `org.json`), ili (b) napisati parser nad ručnim
raščlanjivanjem umesto `org.json`. **Ne uvoditi zavisnost.** Odluku donosi kontroler.

- [ ] **Step 6: Ceo skup testova i build**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest && ./gradlew assembleDebug
```
Expected: JVM ostaje **22** (testovi ovog taska su instrumentisani), instrumentisanih **19** (11 zatečenih + 8 novih), `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/models/LessonContent.kt \
        ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/LessonContentTest.kt
git commit -m "Faza 6b, Task 1: sema i parser sadrzaja lekcija"
```

---

## Task 2: Sadržaj u `assets` i repozitorijum

**Files:**
- Create: `ChesskoAndroid/app/src/main/assets/lessons/` (36 JSON fajlova)
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/LessonRepository.kt`
- Create: `ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/LessonRepositoryTest.kt`

**Interfaces:**
- Consumes: `LessonParser.parse`, `LessonDocument` (Task 1).
- Produces:
  - `class LessonRepository(context: Context)`
  - `fun lesson(id: String, language: String): LessonDocument?`
  - `fun discoveredLessonIds(): List<String>`
  - `companion object { val LESSON_ORDER: List<String> }`

### ZAMKA KOJA BI TIHO SLOMILA KINESKI

`Loc.getLanguage()` **ne vraća kod kojim se fajl zove**:

```kotlin
fun setLanguage(code: String) { currentLanguage = code; if (code == "zh") currentLanguage = "zh-Hans" }
fun getLanguage(): String = if (currentLanguage == "zh-Hans") "zh" else currentLanguage
```

Dakle za kineski `getLanguage()` daje `"zh"`, a fajlovi se zovu `board-and-pieces.zh-Hans.json`.
Repozitorijum koji za traženje fajla koristi `getLanguage()` **tiho bi vratio engleski svakom
kineskom korisniku** — bez ijedne poruke. Zato repozitorijum prima jezik kao **parametar**, a
pozivalac (Task 4) mora da prosledi kod u obliku kojim se fajl zove.

- [ ] **Step 1: Prekopirati sadržaj i dokazati da je identičan**

```bash
cd /Users/veljkoodobasic/Documents/Projects/Chess
mkdir -p ChesskoAndroid/app/src/main/assets/lessons
cp Chessko/Content/lessons/*.json ChesskoAndroid/app/src/main/assets/lessons/
ls ChesskoAndroid/app/src/main/assets/lessons/ | wc -l
diff -r Chessko/Content/lessons ChesskoAndroid/app/src/main/assets/lessons && echo IDENTICNO
```
Expected: **36**, pa `IDENTICNO`. Ako `diff` prijavi razliku, stani — sadržaj se ne transformiše.

- [ ] **Step 2: Napisati instrumentisane testove koji padaju**

`ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/LessonRepositoryTest.kt`:

```kotlin
package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veljkoni.chessko.logic.LessonRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LessonRepositoryTest {

    private fun repo() = LessonRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun everyShippedLessonParsesInEveryShippedLanguage() {
        // Prolazi SVE fajlove, ne uzorak. Pokvaren JSON u jednom jeziku inace
        // izadje na videlo tek kad ga korisnik tog jezika otvori.
        val r = repo()
        var parsed = 0
        for (id in r.discoveredLessonIds()) {
            for (lang in listOf("sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi")) {
                val d = r.lesson(id, lang) ?: continue
                assertTrue("prazna lekcija: $id.$lang", d.blocks.isNotEmpty())
                parsed++
            }
        }
        // 4 originalne lekcije x 8 jezika + 2 nove x 2 jezika = 36
        assertEquals(36, parsed)
    }

    @Test
    fun discoversAllSixLessons() {
        assertEquals(
            listOf("board-and-pieces", "notation", "openings", "tactics", "middlegame", "endgame"),
            repo().discoveredLessonIds()
        )
    }

    @Test
    fun chineseResolvesToItsOwnFileNotEnglish() {
        // Kljucni test ove faze: `zh-Hans` se NE sme tiho svesti na engleski.
        val d = repo().lesson("board-and-pieces", "zh-Hans")
        assertNotNull(d)
        assertEquals("zh-Hans", d!!.language)
    }

    @Test
    fun missingLanguageFallsBackToEnglishThenSerbian() {
        // Nove lekcije idu samo na sr+en. Trazen francuski mora dati engleski,
        // ne prazan ekran.
        val d = repo().lesson("tactics", "fr")
        assertNotNull(d)
        assertEquals("en", d!!.language)
    }

    @Test
    fun unknownLessonReturnsNull() {
        assertEquals(null, repo().lesson("nepostojeca", "sr"))
    }

    @Test
    fun knownLessonsComeInPrescribedOrderAndUnknownGoLast() {
        val ids = repo().discoveredLessonIds()
        assertEquals("board-and-pieces", ids.first())
        assertEquals("endgame", ids.last())
    }
}
```

- [ ] **Step 3: Pokrenuti i videti da padaju**

```bash
export ANDROID_HOME=~/Library/Android/sdk
$ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim &
$ANDROID_HOME/platform-tools/adb wait-for-device
cd ChesskoAndroid && ./gradlew connectedDebugAndroidTest
```
Expected: FAIL, `Unresolved reference: LessonRepository`.
**Rezultat čitaj iz XML-a**, ne iz izlaznog koda.

- [ ] **Step 4: Napisati `LessonRepository.kt`**

```kotlin
package com.veljkoni.chessko.logic

import android.content.Context
import com.veljkoni.chessko.models.LessonDocument
import com.veljkoni.chessko.models.LessonParser

// MARK: - Lesson Repository
//
// Cita `assets/lessons/<id>.<jezik>.json`. Za razliku od `puzzles.sqlite`,
// ovde NEMA kopiranja u `filesDir`: `assets.open()` vraca strim i radi i nad
// kompresovanim asset-om, a JSON se cita u celosti odjednom.
class LessonRepository(private val context: Context) {

    private val cache = HashMap<String, LessonDocument>()

    /// Lekcija na trazenom jeziku, sa lancem `jezik -> en -> sr`.
    ///
    /// **`language` je kod kojim se ZOVE FAJL**, ne ono sto vraca
    /// `Loc.getLanguage()`. Za kineski su to razlicite stvari: `getLanguage()`
    /// daje „zh", a fajl se zove `.zh-Hans.json`. Pozivalac mora da prosledi
    /// oblik iz imena fajla, inace kineski korisnik tiho dobija engleski.
    fun lesson(id: String, language: String): LessonDocument? {
        for (candidate in listOf(language, "en", "sr")) {
            val key = "$id.$candidate"
            cache[key]?.let { return it }
            load(key)?.let { cache[key] = it; return it }
        }
        return null
    }

    /// Razdvaja DVA slucaja koja se lako slepe u jedan `runCatching`:
    /// fajla nema (ocekivano — nove lekcije idu samo na sr+en, pa se jezik
    /// uredno preskace), i fajl POSTOJI ali se ne parsira (greska koja ne sme
    /// da se izgubi; `LessonParser` namerno baca).
    private fun load(key: String): LessonDocument? {
        val text = try {
            context.assets.open("lessons/$key.json").bufferedReader().use { it.readText() }
        } catch (_: java.io.FileNotFoundException) {
            return null
        }
        return try {
            LessonParser.parse(text)
        } catch (e: Exception) {
            // Glasno, ali bez rusenja aplikacije: jedna pokvarena lekcija ne sme
            // da obori ceo ekran Ucenja.
            android.util.Log.e("Chessko", "lessons/$key.json postoji ali se ne parsira: $e")
            null
        }
    }

    /// Id-jevi lekcija koje stvarno postoje u `assets`, otkriveni iz imena
    /// fajlova. Nova lekcija = nov JSON, bez izmene koda.
    fun discoveredLessonIds(): List<String> {
        val files = context.assets.list("lessons")?.toList() ?: emptyList()
        val ids = files.mapNotNull { name ->
            val stem = name.removeSuffix(".json")
            val dot = stem.lastIndexOf('.')
            if (dot <= 0) return@mapNotNull null
            // Poslednji deo MORA biti podrzan jezik: bez te provere bi zalutali
            // `openings.sr.backup.json` dao fantomsku lekciju „openings.sr".
            if (stem.substring(dot + 1) !in LANGUAGES) null else stem.substring(0, dot)
        }.distinct()
        val known = LESSON_ORDER.filter { it in ids }
        val extra = (ids - LESSON_ORDER.toSet()).sorted()
        return known + extra
    }

    companion object {
        /// Redosled na ekranu. NIJE spisak postojecih lekcija — lekcija koja
        /// nije ovde i dalje se prikazuje, na kraju liste. Inace bi nova
        /// lekcija bila NEVIDLJIVA bez ijedne poruke.
        val LESSON_ORDER = listOf(
            "board-and-pieces", "notation", "openings", "tactics", "middlegame", "endgame"
        )
        private val LANGUAGES = setOf("sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi")
    }
}
```

- [ ] **Step 5: Pokrenuti instrumentisane testove**

```bash
cd ChesskoAndroid && ./gradlew connectedDebugAndroidTest
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t = f = 0
for p in glob.glob("app/build/outputs/androidTest-results/connected/debug/*.xml"):
    r = ET.parse(p).getroot(); t += int(r.get("tests")); f += int(r.get("failures"))
print(f"instrumentisanih: {t}, padova: {f}")
EOF
```
Expected: **25 testova** (19 posle Task-a 1 + 6 novih), **0 padova**.

- [ ] **Step 6: Dokazati da lekcije stvarno ulaze u APK**

```bash
cd ChesskoAndroid && ./gradlew assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -c "assets/lessons/"
```
Expected: **36**. Ako je 0, asseti nisu upakovani i testovi gore čitaju nešto drugo.

- [ ] **Step 7: Commit**

```bash
git add ChesskoAndroid/app/src/main/assets/lessons \
        ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/LessonRepository.kt \
        ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/LessonRepositoryTest.kt
git commit -m "Faza 6b, Task 2: lekcijski JSON u assets i LessonRepository"
```

---

## Task 3: Renderer blokova

**Files:**
- Create: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/LessonRenderer.kt`

**Interfaces:**
- Consumes: `LessonBlock` i podklase (Task 1), postojeće `LPara`/`LBullet`/`LBox`/`LSectionHeader`/`LNumberedRule`/`OpeningExerciseCard`/`MateExerciseCard`/`BoardView`/`OpeningLine` iz `LearnView.kt`.
- Produces:
  - `@Composable fun LessonBlocks(blocks: List<LessonBlock>, accent: Color, explorerViewModel: LearnViewModel)`
  - `@Composable fun LPieceRow(piece: String, name: String, count: String, accent: Color)`
  - `@Composable fun LPieceValueTable(rows: List<PieceValueRow>, accent: Color)`

**Pravilo koje se ne sme prekršiti.** Tekst iz JSON-a stiže **već preveden** i **NE ide kroz
`loc()`**. Kroz `loc()` ide samo hrom koji renderer sam dodaje (podrazumevane poruke vežbi).
Ako se sadržaj provuče kroz `loc()`, `Loc.get` na nepoznat ključ vraća sam ključ — dakle isti
tekst — pa se greška **ne vidi**, ali svaki string postaje promašen pretragom i rečnik naraste
za stotine mrtvih unosa.

- [ ] **Step 1: Napisati `LessonRenderer.kt`**

```kotlin
package com.veljkoni.chessko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.models.BoxStyle
import com.veljkoni.chessko.models.ExerciseKind
import com.veljkoni.chessko.models.LessonBlock
import com.veljkoni.chessko.models.PieceValueRow
import com.veljkoni.chessko.viewmodels.LearnViewModel

// MARK: - Lesson Renderer
//
// JEDINO mesto koje zna kako se blok crta. Nov tip bloka = jedna grana ovde i
// jedan `case` u `LessonBlock`.
//
// Tekst iz JSON-a stize VEC PREVEDEN i NE ide kroz `loc()`. Kroz `loc()` ide
// samo hrom koji renderer sam dodaje.

@Composable
fun LessonBlocks(
    blocks: List<LessonBlock>,
    accent: Color,
    explorerViewModel: LearnViewModel
) {
    for (b in blocks) {
        when (b) {
            is LessonBlock.Heading -> LSectionHeader(b.icon, b.text, accent)
            is LessonBlock.Paragraph -> LPara(b.text)
            is LessonBlock.Bullets -> for (it in b.items) {
                LBullet(it.icon, it.title, it.text, colorFor(it.style, accent))
            }
            is LessonBlock.Box -> LBox(b.icon, b.title, b.text, colorFor(b.style, accent))
            is LessonBlock.Quote -> LQuote(b.text, b.author)
            is LessonBlock.PieceRow -> LPieceRow(b.piece, b.name, b.count, accent)
            is LessonBlock.NumberedRule -> LNumberedRule(b.number, b.title, b.text, accent)
            is LessonBlock.PieceValueTable -> LPieceValueTable(b.rows, accent)
            is LessonBlock.Board -> LStaticBoard(b.fen, b.caption, b.interactive)
            is LessonBlock.Explorer -> LExplorer(explorerViewModel)
            is LessonBlock.Exercise -> LExercise(b.spec, accent)
            is LessonBlock.Divider -> HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}

/// `null` znaci „koristi akcent lekcije". `WARNING` je jedini koji namerno
/// izlazi iz akcenta — nosi znacenje, nije ukras.
private fun colorFor(style: BoxStyle?, accent: Color): Color = when (style) {
    null -> accent
    BoxStyle.INFO -> accent
    BoxStyle.RULE -> Color(0xFFE0B252)
    BoxStyle.WARNING -> Color(0xFFF2857A)
}

@Composable
private fun LQuote(text: String, author: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = text, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))
        Spacer(Modifier.height(4.dp))
        Text(text = "— $author", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
fun LPieceRow(piece: String, name: String, count: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // `name` i `count` dolaze iz JSON-a vec prevedeni — NE kroz `loc()`.
        Text(text = name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = accent)
        Spacer(Modifier.weight(1f))
        Text(text = count, fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
fun LPieceValueTable(rows: List<PieceValueRow>, accent: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        for (r in rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = r.name, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.weight(1f))
                // `valueLabel` ako postoji, inace gola vrednost. NE sklapa se
                // „$value bodova" u kodu: srpska mnozina se ne poklapa ni sa
                // jednim drugim jezikom, a `value` je string jer kralj nosi „∞".
                Text(
                    text = r.valueLabel ?: r.value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
        }
    }
}
```

Grane `LStaticBoard`, `LExplorer` i `LExercise` pišu se u Step-u 2 — one su jedine koje
zavise od postojećih komponenti u `LearnView.kt`.

- [ ] **Step 2: Povezati blokove sa postojećim komponentama**

Dodati u isti fajl:

```kotlin
@Composable
private fun LStaticBoard(fen: String, caption: String, interactive: Boolean) {
    val state = remember(fen) { com.veljkoni.chessko.models.GameState.fromFEN(fen) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (state == null) {
            // Pokvaren FEN u sadrzaju mora da se VIDI, ne da ostavi prazninu.
            Text(text = "⚠︎ $fen", fontSize = 13.sp, color = Color(0xFFF2857A))
        } else {
            // POTPIS JE PROVEREN uz stvarni `BoardView` (`ui/BoardView.kt`):
            // prima `board`, ne `gameState`, i `onTap`, ne `onSquareClick`.
            // `BoardTheme.CLASSIC`/`PieceStyle.CLASSIC` su isto ono sto
            // `LearnView` vec prosledjuje na sva tri mesta (linije 632, 1029, 1132) —
            // lekcijska tabla namerno NE prati korisnikovu temu.
            BoardView(
                board = state.board,
                isFlipped = false,
                selectedPosition = null,
                legalMoves = emptyList(),
                lastMove = null,
                boardTheme = BoardTheme.CLASSIC,
                pieceStyle = PieceStyle.CLASSIC,
                onTap = {}
            )
        }
        if (caption.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(text = caption, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        if (interactive) {
            // Interaktivna tabla u lekciji jos ne postoji ni na iOS-u. Umesto
            // tihe razlike izmedju platformi, kaze se sta fali.
            Text(text = loc("Interaktivna tabla još nije dostupna."),
                 fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun LExplorer(viewModel: LearnViewModel) {
    // PROVERENO: `PieceExplorer` kao zasebna funkcija NE POSTOJI — istrazivac
    // figura je ugradjen u `Lesson1Content` (`LearnView.kt` ~576-690: birac
    // figura, `BoardView` sa `viewModel.board`, i kartica sa `infoTitle`).
    // Step 3 ovog istog taska ga izdvaja u
    // `@Composable fun PieceExplorer(viewModel: LearnViewModel)` BEZ menjanja
    // izgleda. Do tada se ovaj poziv ne kompajlira, pa je izdvajanje uslov da
    // Task 3 uopste prodje svoj build korak.
    PieceExplorer(viewModel)
}

@Composable
private fun LExercise(spec: com.veljkoni.chessko.models.ExerciseSpec, accent: Color) {
    when (spec.kind) {
        ExerciseKind.SCRIPTED -> OpeningExerciseCard(
            line = OpeningLine(
                name = spec.title,
                uciMoves = spec.uciMoves ?: emptyList(),
                hint = spec.hint,
                icon = spec.icon,
                accentColor = accent,
                // Podrazumevane poruke IDU kroz `loc()` — to je hrom, ne sadrzaj.
                solvedMessage = spec.solvedMessage ?: loc("Bravo! Otvaranje savladano! ✓"),
                wrongMessage = spec.wrongMessage ?: loc("Pogrešan potez — pokušaj ponovo."),
                playingPrompt = spec.playingPrompt,
                startFEN = spec.startFEN
            )
        )
        // POTPIS PROVEREN: `MateExerciseCard(fen, title, hint, icon, color)` —
        // `fen` je PRVI parametar i zove se `fen`, ne `startFEN`; boja je `color`.
        ExerciseKind.VS_ENGINE -> MateExerciseCard(
            fen = spec.startFEN ?: "",
            title = spec.title,
            hint = spec.hint,
            icon = spec.icon,
            color = accent
        )
    }
}
```

> **Potpis `MateExerciseCard` i postojanje `PieceExplorer` PROVERI pre pisanja.** `LearnView.kt`
> ih ima (`MateExerciseCard` na liniji 1073), ali se parametri mogu razlikovati od gornjih.
> Ako se razlikuju, prilagodi **poziv**, ne komponentu — izgled se u ovoj fazi ne menja.
> Ako `PieceExplorer` ne postoji kao izdvojena funkcija nego je ugrađen u `Lesson1Content`,
> izdvoj ga u zasebnu `@Composable` bez menjanja izgleda i to navedi u izveštaju.

- [ ] **Step 3: Izdvojiti istraživač figura iz `Lesson1Content`**

`LExplorer` iznad zove `PieceExplorer(viewModel)`, a ta funkcija **ne postoji** — istraživač je
ugrađen u `Lesson1Content` (`LearnView.kt`, otprilike 576–690: birač figura, `BoardView` sa
`viewModel.board`, i kartica sa `infoTitle`). Bez izdvajanja Task 3 **ne može da se kompajlira**,
pa izdvajanje pripada ovde, ne kasnije.

U `LearnView.kt` izdvoj u:

```kotlin
@Composable
fun PieceExplorer(viewModel: LearnViewModel) { /* premesten sadrzaj, BEZ izmena izgleda */ }
```

i pozovi je iz `Lesson1Content` na mestu odakle je izvađena. Premeštanje je doslovno: isti
pozivi, isti parametri (`BoardTheme.CLASSIC`, `PieceStyle.CLASSIC`), ista kartica. Posle ovoga
`Lesson1Content` i dalje izgleda isto — to je jedini prihvatljiv ishod. Ako se izgled promeni,
to je regresija, ne poboljšanje.

- [ ] **Step 4: Build**

```bash
export ANDROID_HOME=~/Library/Android/sdk
cd ChesskoAndroid && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`. Renderer se još nigde ne poziva — ovo je samo provera da se
kompajlira uz stvarne potpise postojećih komponenti.

- [ ] **Step 5: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/LessonRenderer.kt
git commit -m "Faza 6b, Task 3: renderer lekcijskih blokova"
```

---

## Task 4: `LearnView` čita iz repozitorijuma

**Files:**
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/LearnView.kt`

**Interfaces:**
- Consumes: `LessonRepository` (Task 2), `LessonBlocks` (Task 3).

Ovo je task u kome nestaje ~400 linija zakucanog sadržaja: `Lesson1Content` … `Lesson4Content`
i sve `LessonInfo` definicije sa srpskim naslovima.

- [ ] **Step 1: Razrešiti jezik u oblik kojim se zove fajl**

`Loc.getLanguage()` vraća `"zh"` za kineski, a fajl se zove `.zh-Hans.json`. Dodati u `Loc.kt`:

```kotlin
    /// Kod u obliku u kome se pojavljuje u IMENIMA FAJLOVA
    /// (`board-and-pieces.zh-Hans.json`). Razlikuje se od `getLanguage()`, koji
    /// za kineski vraca „zh" radi UI birača. Lekcije se traze OVIM kodom —
    /// inace kineski korisnik tiho dobija engleski.
    fun fileLanguageCode(): String = currentLanguage
```

- [ ] **Step 2: Lista lekcija iz repozitorijuma**

`LessonInfo` više ne nosi tekst; `id` postaje `String`:

```kotlin
data class LessonInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val accentColor: Color
)
```

U `LearnView` zameniti zakucanu listu:

```kotlin
    val context = LocalContext.current
    val repo = remember { LessonRepository(context) }
    val lang = Loc.fileLanguageCode()
    val lessons = remember(lang) {
        repo.discoveredLessonIds().mapNotNull { id ->
            repo.lesson(id, lang)?.let { d ->
                LessonInfo(d.id, d.title, d.subtitle, d.icon, accentFor(d.id))
            }
        }
    }
```

`accentFor(id)` zadržava postojeće boje po lekciji; za nepoznat id vraća podrazumevani akcent:

```kotlin
private fun accentFor(id: String): Color = when (id) {
    "board-and-pieces" -> Color(0xFF7EA0E8)
    "openings" -> Color(0xFF6FCF97)
    "middlegame" -> Color(0xFFE0B252)
    "endgame" -> Color(0xFFF2857A)
    else -> Color(0xFF7EA0E8)
}
```

- [ ] **Step 3: Telo lekcije kroz renderer**

Zameniti `when (info.id) { 1 -> Lesson1Content(...) … }` sa:

```kotlin
    val doc = remember(info.id, lang) { repo.lesson(info.id, lang) }
    if (doc == null) {
        Text(text = loc("Lekcija nije dostupna."), color = Color.White.copy(alpha = 0.6f))
    } else {
        LessonBlocks(doc.blocks, info.accentColor, learnViewModel)
    }
```

Ključ `"Lekcija nije dostupna."` dodati u `Loc.kt` na svih 8 jezika.

- [ ] **Step 4: Obrisati zakucan sadržaj**

Obrisati `Lesson1Content`, `Lesson2Content`, `Lesson3Content`, `Lesson4Content` i sve
`OpeningLine(...)` / `MateExerciseCard(...)` pozive koji su bili unutar njih. **Ne brisati**
same komponente (`OpeningExerciseCard`, `MateExerciseCard`, `MatePuzzleCard`, `LBox`, `LPara`,
`LBullet`, `LSectionHeader`, `LNumberedRule`) — renderer ih koristi.

- [ ] **Step 5: Dokazati da zakucanih srpskih stringova više nema**

```bash
cd ChesskoAndroid
grep -cE 'LPara\("|LBullet\("|LSectionHeader\("|LNumberedRule\(' \
  app/src/main/java/com/veljkoni/chessko/ui/LearnView.kt
```
Expected: **0**. (Renderer ih zove sa promenljivama, ne sa literalima.)

- [ ] **Step 6: Vizuelni dokaz na engleskom**

Na emulatoru **bez prozora**, jezik na engleski, otvoriti **svih šest** lekcija i snimiti.
Nijedna ne sme da prikaže srpski pasus. Posebno Lekciju 4 — baš ona je u Fazi 6a pokazala
ceo srpski tekst na engleskom UI-ju.

Snimiti i srpsku varijantu radi poređenja, i **jednu lekciju koja postoji samo na sr+en**
(`tactics`) na francuskom — mora pokazati engleski, ne prazno.

- [ ] **Step 7: Testovi i build**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest && ./gradlew assembleDebug
```
Expected: JVM **22**, `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/
git commit -m "Faza 6b, Task 4: lekcije se citaju iz JSON-a umesto iz koda"
```

---

## Task 5: Dokumentacija i zatvaranje faze

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Dopuniti „Sadržaj lekcija"**

Dodati da od Faze 6b isti JSON čita i Android, bajt-identično, iz `assets/lessons/`; da parser
ide kroz `org.json` jer bi `kotlinx.serialization` bila nova zavisnost; i da parser **baca** na
nepoznat tip bloka iz istog razloga kao iOS.

- [ ] **Step 2: Ažurirati „Stanje Android porta"**

Faza 3 prelazi u **da**. Brojevi testova: **22 JVM + 25 instrumentisanih**. Zapisati i ZAŠTO parser nije JVM-testiran (Android `org.json` stub), jer je to netipično i sledeći čitalac bi pomislio da je propust.

- [ ] **Step 3: Zatvoriti unos o spec-u 4.5**

Unos u „Poznata ograničenja" koji kaže da je 4.5 rešen samo za ekran Zadataka **mora se
ispraviti** — posle ove faze pada i ekran Učenja. **Prvo proveri grep-om i na uređaju**, pa tek
onda menjaj tvrdnju; ova faza je već jednom zapisala apsolut koji je grep oborio.

- [ ] **Step 4: Zabeležiti zamku sa kineskim kodom**

`Loc.getLanguage()` vraća `"zh"`, a fajlovi se zovu `.zh-Hans.json`. Zapisati da lekcije koriste
`Loc.fileLanguageCode()` i zašto — inače bi sledeća izmena vratila tihi pad na engleski.

- [ ] **Step 5: Changelog unos** na **dno** sekcije (fajl ide rastuće po datumu).

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md
git commit -m "Faza 6b: dokumentacija lekcija iz JSON-a na Androidu"
```

---

## Završna provera faze

- [ ] `./gradlew testDebugUnitTest` prolazi **22/22** (parser se testira instrumentisano, vidi Task 1 Step 5)
- [ ] `./gradlew connectedDebugAndroidTest` prolazi **25/25** (čitano iz XML-a)
- [ ] `./gradlew assembleDebug` → `BUILD SUCCESSFUL`
- [ ] 36 JSON fajlova u APK-u (`unzip -l | grep -c assets/lessons/`)
- [ ] `diff -r Chessko/Content/lessons ChesskoAndroid/app/src/main/assets/lessons` prazan
- [ ] `grep -cE 'LPara\("|LBullet\("' LearnView.kt` je **0**
- [ ] Svih šest lekcija pregledano na engleskom — nijedna srpska reč
- [ ] `tactics` na francuskom pokazuje engleski, ne prazno
- [ ] `git diff main..HEAD -- Chessko/` je prazan
- [ ] `git status --short` prazan

## Rizici

- **`org.json` u JVM testu je stub.** Ako Task 1 Step 5 pokaže `RuntimeException("Stub!")`,
  odluka je kontrolerova: preseliti testove u `androidTest` ili napisati parser bez `org.json`.
  **Nova zavisnost nije opcija.**
- **Potpisi postojećih komponenti mogu se razlikovati od onih u Task-u 3.** Prilagođava se
  poziv, ne komponenta — izgled se u ovoj fazi ne menja. Ako se izgled ipak promeni, to je
  nalaz koji se prijavljuje, ne tiho prihvata.
- **~400 linija se briše.** Ako se posle brisanja neka lekcija razlikuje od zatečene, to je
  regresija: sadržaj u JSON-u je isti koji iOS prikazuje, ali **Android raspored nije isti kao
  iOS**, pa se poređenje radi sa zatečenim Android ekranom, ne sa iOS-om.
- **Kineski je jedini jezik gde se kod razlikuje od imena fajla.** Test
  `chineseResolvesToItsOwnFileNotEnglish` je jedina zaštita; ne uklanjati ga.
