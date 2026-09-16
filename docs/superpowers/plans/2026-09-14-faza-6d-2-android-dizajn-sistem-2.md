# Faza 6d-2 — Android dizajn sistem, deo 2

> **Za agentske izvršioce:** OBAVEZNA POD-VEŠTINA: koristi
> superpowers:subagent-driven-development za izvođenje ovog plana task po task.
> Koraci koriste `- [ ]` sintaksu radi praćenja.

**Cilj:** Preostala tri ekrana Android porta (sat, lekcije, ekran učenja) prelaze na `DS.*`
tokene, a kartice u podešavanjima prestaju da crtaju ivicu koja ne postoji.

**Arhitektura:** Ništa novo se ne uvodi. `ChesskoColors`/`DS`/`ChesskoTheme` postoje od 6d-1;
ovo je primena istog mehanizma na fajlove koje prva kriška nije stigla. Tabela preslikavanja je
ista i obavezujuća.

**Tehnologije:** Kotlin, Jetpack Compose, Material 3. Bez ijedne nove zavisnosti.

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md`, sekcija 5.6
**Prethodna kriška:** `docs/superpowers/plans/2026-09-13-faza-6d-1-android-dizajn-sistem.md`

---

## Globalna ograničenja

- **Ni jedna nova Gradle zavisnost.** Posebno ne `androidx.navigation`.
- **`Chessko/` (iOS) se čita radi poređenja, nikad ne menja.**
- **`create_xcode_project.py` se NIKAD ne pokreće.**
- Svaki `loc()` ključ mora biti **literal unutar `loc()`** i postojati u rečniku
  (`LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`).
- **`ChesskoAndroid/` je jedini direktorijum koji se menja** (uz `CLAUDE.md` i ovaj plan).
- Emulator: `-gpu host`, **NIKAD `-gpu off`**; diže se **jednom, u Task-u 5**, i gasi odmah.

---

## Vodeće načelo — pročitaj pre nego što dodirneš ijednu boju

> **Tema-zavisna boja i tema-izuzeta podloga ne smeju se mešati — ni u jednom smeru.**

Faza 6d-1 je na ovome pukla **četiri puta**, svaki put drugačije:

| # | šta se desilo | odnos |
|---|---|---|
| 1 | fiksna kartica + ivica prebačena na token | 1,36 u tamnoj |
| 2 | fiksni beli krug + podloga prebačena na token | 1,20 u svetloj |
| 3 | fiksni zlatni marker + podloga prebačena na token | 1,16 u svetloj |
| 4 | **oba člana tokeni, ali međusobno nerazlučivi** (`DS.line` palac na `DS.fill` traci) | 1,067 u obe |

Četvrti je najopasniji jer izgleda ispravno: obe strane su „uredno tokenizovane". **Token nije
jedinica provere — par jeste.** Za svaku boju koju upišeš pitaj se šta je tačno iza nje i izmeri.

Sve tri greške su nevidljive dok se tema ne promeni. Zato svaki task koji uvodi **nov par** mora
dodati tvrdnju u `ContrastTest` — to je JVM test, ne traži emulator, i hvata grešku pre nego što
iko pogleda ekran.

> **„Nov par" znači nov, ne novo ime.** Pre pisanja testa pročitaj postojećih sedam i proveri da
> par već nije pokriven — `textOnBackgroundsMeetsAA` u petlji prolazi obe palete, pa jedno ime
> pokriva mnogo parova. Task 1 je po prvom izdanju ovog plana napisao test koji je ponavljao
> četiri već postojeće tvrdnje; uklonjen je, a razlog je upisan u postojeći test. **Test koji ne
> može da padne a da i neki drugi ne padne nije dokaz nego šum.** Ako je par već pokriven, umesto
> novog testa dopiši komentar u postojeći — imenuj novog potrošača para.

---

## Tabela preslikavanja (obavezujuća, ista kao 6d-1)

| zatečeno | token | napomena |
|---|---|---|
| `Color(0xFF00D2FF)` | `DS.accent` | stari akcent aplikacije |
| `Color.White` (tekst) | `DS.ink` | |
| `Color.White.copy(alpha = 0.4…0.7)` | `DS.inkMuted` | sekundarni tekst |
| `Color.White.copy(alpha = 0.02…0.12)` kao **podloga** | `DS.fill` | |
| `Color.White.copy(alpha = 0.02…0.12)` kao **ivica** | `DS.line` | ali vidi Task 4 |
| `Color(0xFF1E293B)` | `DS.surface` | kartica/dijalog |
| `Color(0xFF0F172A)` | `DS.ground` | podloga ekrana |
| `Color(0xFF10B981)` | `DS.success` | nosi značenje |
| `Color(0xFFF59E0B)` | `DS.warning` | nosi značenje |
| `Color(0xFFEF4444)` | `DS.danger` | nosi značenje |
| `Color.Black` **na akcentu** | `DS.onAccent` | nikad `Color.White` na `DS.accent` |

Vrednosti tokena za obe teme — `CLAUDE.md`, sekcija „Dizajn sistem".

---

## Šta ova faza NE radi

Zapisano da se ne bi širila u hodu:

- **Tabla se ne dira.** `BoardView.kt` (36 boja) i `BoardTheme.kt` (8) su **sadržaj u celosti**:
  polja, pozadina table, gradijenti figura, marker šaha `#EF4444`, konfete. Izmereno poređenjem
  sa iOS-om: oznake selekcije i mogućih polja su `#00D2FF` na **obe** platforme, dakle nisu
  zaostatak starog akcenta nego deljena boja oznake. Jedini posao je poravnanje dve alfa
  vrednosti (Task 4).
- **Emoji ostaju emoji.** ~37 u UI hromu i 44 u `lessonIcon()` mapi. Zamena ikonama je odvojen
  posao (izbor ikonografije, ne boja) i odložena je odlukom korisnika.
- **`DS.Space` / `DS.Radius` / `DS.maxBoardSide` / `Type.*` ostaju bez pozivnih mesta.** Primena
  kroz 13 ekrana je veća od svih boja ove faze zajedno; odložena odlukom korisnika.

---

## Izmereno pre pisanja plana

```
ChessClockView.kt     37 boja / 687 linija   →  26 hrom, 11 ostaje fiksno
LearnView.kt          40 boja / 880 linija   →  40 hrom
LessonDetailView.kt   14 boja / 200 linija   →   7 hrom + 7 boja po lekciji
LessonRenderer.kt     11 boja / 375 linija   →  11 hrom
BoardView.kt          36 boja / 661 linija   →   0 (sadržaj)
BoardTheme.kt          8 boja /  19 linija   →   0 (same teme table)
```

Kontrast `DS.line` nad `DS.fill`: **1,067** (svetla) / **1,071** (tamna).
Nad `DS.surface`: **1,285** / **1,209**. `DS.surface` nad `DS.ground`: **1,109** / **1,094**.
**Nijedan par u paleti ne daje vidljivu ivicu kartice** — otuda Task 4.

---

## Struktura fajlova

| fajl | odgovornost | task |
|---|---|---|
| `ui/ChessClockView.kt` | hrom sata (dijalozi, dugmad, birač kontrole) | 1 |
| `ui/LessonRenderer.kt` | blokovi lekcije | 2 |
| `ui/LessonDetailView.kt` | okvir lekcije + akcent po lekciji | 2 |
| `ui/LearnView.kt` | komponente lekcija i vežbi | 3 |
| `ui/SettingsView.kt` | kartice bez mrtve ivice | 4 |
| `ui/BoardView.kt` | dve alfa vrednosti radi iOS pariteta | 4 |
| `app/src/test/.../ContrastTest.kt` | nove tvrdnje po tasku | 1–4 |
| `CLAUDE.md` | dokumentacija + ispravka zastarele tvrdnje | 5 |

---

## Ruling: vizuelna provera je JEDAN grupisan prolaz, u Task-u 5

Faza 6d-1 je dizala emulator po tasku — šest puta — iako `CLAUDE.md` izričito traži grupisanje.
Prolazi su ipak našli greške, ali **nijednu koju screenshot nije mogao da pokaže tek na kraju**:
sva tri kontrastna propusta našla su se **merenjem**, ne gledanjem.

Zato: taskovi 1–4 dokazuju se `ContrastTest`-om, JVM testovima i `assembleDebug`-om. Emulator se
diže **jednom**, u Task-u 5, i pokriva sve ekrane odjednom.

Cena ako grešim: defekt koji samo oko vidi otkriva se na kraju, pa ispravka ide u zaseban commit
umesto u task koji ga je uveo. Prihvatljivo — mereno je da jedan grupisan prolaz košta ~15 min
na ~149%, a šest odvojenih višestruko više.

---

## Task 1: Hrom šahovskog sata

**Fajlovi:**
- Izmeni: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/ChessClockView.kt`
- Izmeni: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/ContrastTest.kt`

**Interfejsi:**
- Koristi: `DS.accent`, `DS.onAccent`, `DS.ink`, `DS.inkMuted`, `DS.surface`, `DS.fill`, `DS.line`
- Proizvodi: ništa novo za druge taskove

### JEDANAEST BOJA KOJE SE NE SMEJU DIRATI

Ovo je **najvažniji deo ovog taska**. Sat deli ekran na crnu i belu polovinu; te boje prate
**STRANU U IGRI**, ne sistemsku temu. Sa tokenom bi u svetloj temi crna polovina postala skoro
bela ispod belog teksta — iOS je tačno tu grešku već napravio i ispravio (`CLAUDE.md`, Faza 1,
talas ispravki, nalaz 1).

Ne diraj nijednu od ovih linija:

```
171  .background(Color.Black)                          // podloga iza oba polutajmera
269  isTimeOut  -> #8C2525 (crna strana) / #FADAD8 (bela strana)
270  isActive   -> #121212 / #FFFFFF
271  else       -> #2C2C2E / #E5E5EA
278  isTimeOut  -> Color.White / #EF4444
279  isActive   -> Color.White / Color.Black
280  else       -> White 50% / Black 50%
284  isTimeOut  -> White 70% / #EF4444 70%
285  isActive   -> White 70% / Black 60%
286  else       -> White 30% / Black 30%
```

- [ ] **Korak 1: Potvrdi da razumeš granicu pre nego što išta promeniš**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
sed -n '265,290p' ui/ChessClockView.kt
```
Očekivano: blok `when` izraza koji bira boju po `playerNumber`, bez ijednog `DS.`.
Ako posle tvog rada ovaj ispis sadrži `DS.`, task je pogrešno urađen.

- [ ] **Korak 2: Migriraj hrom — linije 353 do 655**

Sve ostalo u fajlu je hrom oko sata: dijalog za izbor vremenske kontrole, info dijalog,
dugme za resetovanje, dugmad „Zatvori", zaglavlja. Primeni tabelu preslikavanja doslovno.

Posebno:
- `Color(0xFF00D2FF)` na 377, 386, 393, 401, 473, 501, 531, 543, 611, 629 → `DS.accent`
- `Color.Black` na 406, 480, 614 → **`DS.onAccent`** (to je tekst NA akcentu, ne crna boja)
- `Color(0xFF1E293B)` na 353, 444, 557 → `DS.surface`
- `Color.White.copy(alpha = 0.02…0.1)` podloge (363, 419, 498, 640) → `DS.fill`
- `Color.White.copy(alpha = 0.08)` kao **ivica** na 501 → `DS.line`
- `Color.White` tekst (426, 455, 480, 513, 568, 647) → `DS.ink`
- `Color.White.copy(alpha = 0.25…0.7)` (426, 525, 531, 655) → `DS.inkMuted`

- [ ] **Korak 3: Napiši tvrdnju koja pada pre popravke**

U `ContrastTest.kt` dodaj test da tekst dijaloga sata čita na svojoj podlozi u obe teme:

```kotlin
@Test
fun clockDialogTextMeetsAA() {
    // Dijalozi sata (izbor vremenske kontrole, info) stoje na `surface`, ne na `ground`.
    // Bez ove tvrdnje par `inkMuted`/`surface` nije pokriven ni jednim testom, a upravo
    // ga Task 1 uvodi na tri nova mesta.
    check("ink/surface",      LightColors, { it.ink },      { it.surface }, 4.5)
    check("ink/surface",      DarkColors,  { it.ink },      { it.surface }, 4.5)
    check("inkMuted/surface", LightColors, { it.inkMuted }, { it.surface }, 4.5)
    check("inkMuted/surface", DarkColors,  { it.inkMuted }, { it.surface }, 4.5)
}
```

Prilagodi potpis `check(...)` onome što fajl stvarno ima — pročitaj ga pre pisanja.

- [ ] **Korak 4: Dokaži da test može da padne**

Privremeno promeni `LightColors.inkMuted` u nešto svetlije (npr. `#B0B4BC`), pokreni test,
vidi da pada, pa vrati. **Zalepi oba izlaza u izveštaj.** Test koji nikad nije pao ne dokazuje ništa.

- [ ] **Korak 5: Testovi i build**

```bash
cd ChesskoAndroid
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
Broj testova čitaj iz `app/build/test-results/testDebugUnitTest/*.xml`, **ne iz izlaznog koda**.

- [ ] **Korak 6: Provera da fiksnih 11 nije dirnuto**

```bash
sed -n '165,290p' ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/ChessClockView.kt | grep -c "DS\."
```
Očekivano: **0**.

- [ ] **Korak 7: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/ChessClockView.kt \
        ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/ContrastTest.kt
git commit -m "Faza 6d-2, Task 1: hrom sata prelazi na tokene"
```

---

## Task 2: Blokovi i okvir lekcije

**Fajlovi:**
- Izmeni: `ChesskoAndroid/.../ui/LessonRenderer.kt`
- Izmeni: `ChesskoAndroid/.../ui/LessonDetailView.kt`
- Izmeni: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/ContrastTest.kt`

**Interfejsi:**
- Koristi: sve tokene iz tabele preslikavanja
- Proizvodi: ništa što drugi taskovi troše. `accentFor()` ima **tačno jedno pozivno mesto**
  (`LessonDetailView.kt:75`) i nestaje unutar ovog taska; `LearnView.kt:55` ga samo pominje u
  komentaru.

- [ ] **Korak 1: Sruši sedam boja po lekciji u jedan akcent**

Funkcija se zove **`accentFor(id: String): Color`** (`LessonDetailView.kt:43-52`) i mapira
svaku lekciju u svoju boju:

```kotlin
"board-and-pieces" -> Color(0xFF3B82F6)   // plava
"notation"         -> Color(0xFF8B5CF6)   // ljubičasta
"openings"         -> Color(0xFF10B981)   // zelena
"tactics"          -> Color(0xFF06B6D4)   // cijan
"middlegame"       -> Color(0xFFF59E0B)   // narandžasta
"endgame"          -> Color(0xFFEF4444)   // crvena
else               -> Color(0xFF3B82F6)
```

iOS je tačno ovo uklonio u Fazi 1: *„per-lekcijske boje (plava/zelena/narandžasta/crvena)
svedene na jedan akcent, a boja zadržana samo tamo gde nosi značenje"*. Spec 5.6 traži **jedan
uzdržan akcent**; šest boja po lekciji je suprotno od toga.

**Obriši funkciju i pozovi `DS.accent` na jedinom mestu koje je koristi.**

Provereno pre pisanja plana, ne pretpostavljeno: `accentFor` ima **tačno jedno** pozivno mesto —
`LessonDetailView.kt:75` (`val accent = accentFor(lessonId)`). Drugi pogodak, `LearnView.kt:55`,
je pomen u komentaru, ne poziv.

Zadržavanje potpisa ovde ne bi bilo jeftinije nego brisanje, nego **skuplje**: `DS.accent` je
`@Composable @ReadOnlyComposable` geter, a `accentFor` je obična `internal fun`. Da funkcija
ostane, morala bi i sama postati `@Composable` — što je veća izmena od brisanja jednog poziva.

Na mestu brisanja ostavi komentar zašto boje po lekciji više nema, sa pozivom na iOS presedan
(Faza 1). Doc-komentar iznad funkcije (`LessonDetailView.kt:40-42`) tvrdi da je „boja lekcije
jedina stvar koja je ostala u kodu" — ta rečenica posle ovog taska više nije tačna i mora nestati
sa funkcijom.

- [ ] **Korak 2: Migriraj `LessonDetailView` hrom**

Linije 97-98 (`Color.White.copy(alpha=0.08)` podloga + `Color.White` sadržaj dugmeta),
125, 160, 166, 183 (razdelnik), 192. Po tabeli.

- [ ] **Korak 3: Migriraj `LessonRenderer` — pazi na dva koja NOSE ZNAČENJE**

```
60   Color.White.copy(alpha = 0.08)   razdelnik      → DS.line
71   BoxStyle.RULE    #E0B252 zlatna                 → DS.warning
72   BoxStyle.WARNING #F2857A losos                  → DS.danger
78   White 90%  tekst citata                         → DS.ink
80   White 55%  potpis autora                        → DS.inkMuted
126  White 90% / #E05A5A za nepoznatu figuru         → DS.ink / DS.danger
143  White 75%                                       → DS.inkMuted
156  White 90%                                       → DS.ink
179  #F2857A  „⚠︎ $fen" poruka o grešci               → DS.danger
201  White 60%                                       → DS.inkMuted
207  White 50%                                       → DS.inkMuted
```

`RULE` i `WARNING` su **zlatno pravilo** i **upozorenje** — to je značenje, ne ukras, pa idu na
`DS.warning`/`DS.danger`, ne na `DS.accent`. Crveno `#E05A5A` za nepoznat naziv figure isto:
to je vidljiva poruka o pokvarenom sadržaju (`CLAUDE.md`, „Android čita isti JSON").

- [ ] **Korak 4: Proveri podlogu ispod svakog od tih tekstova**

Blokovi lekcije se crtaju unutar `LessonDetailView` skrola. **Pre nego što upišeš ijedan token,
utvrdi šta je stvarna podloga** — `DS.ground`, `DS.surface` ili `DS.fill` — i tek onda biraj par.
Ovo je tačno mesto na kom je 6d-1 pukla četiri puta. Zabeleži u izveštaju koja je podloga i zašto.

- [ ] **Korak 5: Tvrdnja u `ContrastTest` za dva semantička para**

```kotlin
@Test
fun lessonBoxStylesMeetAA() {
    // `RULE` i `WARNING` kutije u lekcijama nose značenje, pa im tekst mora da se čita.
    // Podloga je <UPISI STVARNU, iz Koraka 4>.
    check("warning/<podloga>", LightColors, { it.warning }, { it.<podloga> }, 4.5)
    check("warning/<podloga>", DarkColors,  { it.warning }, { it.<podloga> }, 4.5)
    check("danger/<podloga>",  LightColors, { it.danger },  { it.<podloga> }, 4.5)
    check("danger/<podloga>",  DarkColors,  { it.danger },  { it.<podloga> }, 4.5)
}
```

**Ako neki par ne prolazi 4,5** — ne menjaj prag da bi test prošao. Ili promeni podlogu (kao što
je 6d-1 uradio sa statističkom karticom), ili ga upiši u `knownSubAAPairsDoNotGetWorse` sa punom
preciznošću i u „Poznata ograničenja", i **zapiši razlog**.

- [ ] **Korak 6: Dokaži mutacijom, pa testovi i build** — isto kao Task 1, koraci 4–5.

- [ ] **Korak 7: Commit**

```bash
git commit -m "Faza 6d-2, Task 2: blokovi i okvir lekcije prelaze na tokene"
```

---

## Task 3: Ekran učenja i kartice vežbi

**Fajlovi:**
- Izmeni: `ChesskoAndroid/.../ui/LearnView.kt` (880 linija, 40 boja)
- Izmeni: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/ContrastTest.kt`

**Interfejsi:**
- Koristi: sve tokene; `lessonAccent()` iz Task-a 2 sada vraća `DS.accent`

Ovo je najveći fajl faze. Nosi `LBox`, `LPara`, `LBullet`, `LSectionHeader`, `LNumberedRule`,
`PieceExplorer`, `OpeningExerciseCard`, `MateExerciseCard`, `MatePuzzleCard` — sve što
`LessonRenderer` zove da nacrta sadržaj lekcije.

- [ ] **Korak 1: Migriraj tekstualne komponente**

Linije 331, 340, 358, 359, 395, 396 — naslovi i tela `LBox`/`LPara` varijanti.
`Color.White` → `DS.ink`; `White 0.6…0.85` → `DS.inkMuted`.

- [ ] **Korak 2: Migriraj `PieceExplorer`**

Linije 420, 427, 447, 469, 474, 476, 491, 492.

**Linija 427 je zamka istog oblika kao nalaz 2 iz 6d-1:**
```kotlin
val bg = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f)
```
Izabrano polje je svetlije od neizabranog **zato što je podloga tamna**. Sa tokenom u svetloj
temi `DS.fill` je svetliji od `DS.surface`, pa se odnos okreće. Izaberi par koji **u obe teme**
čini izabrano polje vidljivo drugačijim, i izmeri oba smera.

Linija 491-492: `Color(0xFFF59E0B)` aktivno dugme scenarija + `Color.Black` sadržaj →
`DS.warning` je ovde **ukras, ne značenje** (aktivno stanje dugmeta, ne upozorenje) → `DS.accent`
+ `DS.onAccent`.

- [ ] **Korak 3: Migriraj tri kartice vežbi**

`OpeningExerciseCard` (524-606), `MateExerciseCard` (635-709), `MatePuzzleCard` (767-871).

Statusne boje **nose značenje i ostaju semantičke**:
```
594, 697        #10B981 rešeno                  → DS.success
595, 698, 772,  #EF4444 pogrešan potez          → DS.danger
859-860
771, 859        #F59E0B rešen mat-zadatak       → DS.success   (vidi ispod)
```

**Odluka koju moraš doneti i zapisati:** linije 771 i 859 koriste narandžastu `#F59E0B` za
**rešen** mat-zadatak, dok iste kartice na drugim mestima koriste zelenu za rešeno. iOS
`MatePuzzleCard` koristi trofej ikonu i zelenu. Poravnaj sa iOS-om (`DS.success`) osim ako
čitanjem koda nađeš razlog zašto je ovde drugačije — u tom slučaju zapiši razlog.

- [ ] **Korak 4: Provera podloge za svaki status**

Statusni tekst stoji na podlozi kartice (`White 0.04` → token). Utvrdi koji token, pa izmeri sva
tri statusa na njemu, u obe teme. **Ovo je isti korak koji je u 6d-1 otkrio da `warning` na
`DS.fill` daje 3,00.**

- [ ] **Korak 5: Tvrdnja u `ContrastTest` za tri statusa**

```kotlin
@Test
fun exerciseStatusColorsMeetAA() {
    // Tri statusa vezbi (reseno / pogresan potez / u toku) stoje na podlozi kartice.
    // Bez ove tvrdnje boja koja NOSI ZNACENJE moze da postane necitljiva u jednoj temi.
    // Podloga je <UPISI STVARNU, iz Koraka 4>.
}
```
Isti postupak kao Task 2, korak 5 — uključujući pravilo da se prag ne pomera.

- [ ] **Korak 6: Dokaži mutacijom, pa testovi i build.**

- [ ] **Korak 7: Commit**

```bash
git commit -m "Faza 6d-2, Task 3: ekran ucenja i kartice vezbi prelaze na tokene"
```

---

## Task 4: Mrtva ivica kartice i iOS paritet table

Dva mala, nezavisna posla u jednom tasku — oba su izmerene ispravke, ne migracije.

**Fajlovi:**
- Izmeni: `ChesskoAndroid/.../ui/SettingsView.kt`
- Izmeni: `ChesskoAndroid/.../ui/BoardView.kt`
- Izmeni: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/ContrastTest.kt`

### Deo A — ivica koja ne crta ništa

Izmereno: `DS.line` nad `DS.fill` = **1,067** (svetla) / **1,071** (tamna). Nad `DS.surface` =
**1,285** / **1,209**. `DS.surface` nad `DS.ground` = **1,109** / **1,094**.

**Nijedan par u paleti ne daje vidljivu ivicu.** iOS taj problem nema jer podešavanja crta
nativna grouped lista — separaciju daje sistem, ne token. Zato se ivica **ne pojačava nego
uklanja**, a kartice se odvajaju podlogom, kao iOS.

- [ ] **Korak 1: Prebaci kartice sa `DS.fill` na `DS.surface` i ukloni mrtvu ivicu**

Četiri kartice: `SettingsView.kt:109-110` (jezik), `:202` (statistika — podloga je već
`DS.surface` od 6d-1, ostaje samo ivica), `:263-264` (težina), `:540-541`.

Ukloni `.border(1.dp, DS.line, …)` sa sva četiri i postavi `.background(DS.surface)`.

**NE diraj** `:409` (podloga pločice stila figure — tu `DS.fill` radi svoj posao, dokazano
screenshotom u 6d-1) ni `:631` (red prekidača — vidi Korak 2).

- [ ] **Korak 2: Proveri da prekidači i dalje rade posle promene podloge**

Talas ispravki 6d-1 je prekidačima vratio eksplicitne boje jer je isključen bio nevidljiv.
Te vrednosti su birane prema `DS.fill` kao podlozi reda. Ako Korak 1 promeni podlogu ispod
njih, **izmeri ponovo** palac prema traci i traku prema redu, u obe teme, i potvrdi ≥ 3:1.

- [ ] **Korak 3: Izmeri i zalepi novo stanje**

Za svaku od četiri kartice: `DS.surface` prema `DS.ground`, i tekst na njoj (`DS.ink`,
`DS.inkMuted`) prema `DS.surface`. Obe teme.

### Deo B — dve alfa vrednosti radi iOS pariteta

Izmereno poređenjem sa `Chessko/Views/SquareView.swift`:

| oznaka | iOS | Android sada | posle |
|---|---|---|---|
| poslednji potez | `Color.yellow` @ **0,40** | @ 0,35 | 0,40 |
| prsten uzimanja | `#00D2FF` @ **0,65** | @ 0,55 | 0,65 |
| tačka praznog polja | `#00D2FF` @ **0,55** | @ 0,45 | 0,55 |

> **Ispravka plana.** Prvo izdanje je tvrdilo da prsten uzimanja stoji na 0,45 i navodilo „samo
> dve vrednosti". Pogrešno: 0,45 je **tačka praznog polja**, a prsten je bio 0,55
> (`BoardView.kt:436-450`, komentari `Capture ring` / `Empty-square dot`). Obe oznake su nosile
> isti pomak od −0,10 prema iOS-u, pa se popravljaju obe. Izvršilac Task-a 4 je razliku prijavio
> umesto da je prećuti i primenio popravku na semantički tačnu liniju — to je i otkrilo grešku.

- [ ] **Korak 4: Poravnaj dve vrednosti**

`BoardView.kt` — poslednji potez (`Color.Yellow.copy(alpha = 0.35f)` → `0.40f`) i prsten
uzimanja (`Color(0xFF00D2FF).copy(alpha = 0.45f)` → `0.65f`).

**Ništa drugo u `BoardView.kt` se ne dira.** Nijedan `DS.` token ne sme ući u taj fajl.

- [ ] **Korak 5: Provera da tabla nije tokenizovana**

```bash
grep -c "DS\." ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/BoardView.kt
```
Očekivano: **0**.

- [ ] **Korak 6: Tvrdnja u `ContrastTest` za novu podlogu kartica**

```kotlin
@Test
fun settingsCardsSeparateFromGround() {
    // Kartice se od podloge ekrana odvajaju SAMO bojom (ivica je uklonjena jer nijedan par
    // u paleti nije davao vidljivu liniju — mereno 1,067 / 1,285). Ako `surface` i `ground`
    // ikad postanu ista boja, kartice nestaju bez ijedne druge posledice.
    // Prag je namerno nizak: ovo je dekorativno odvajanje, ne granica kontrole.
}
```
Upiši stvarne izmerene vrednosti kao donju granicu, punom preciznošću.

- [ ] **Korak 7: Testovi, build, commit**

```bash
git commit -m "Faza 6d-2, Task 4: kartice bez mrtve ivice, tabla poravnata sa iOS-om"
```

---

## Task 5: Zatvaranje faze

**Fajlovi:**
- Izmeni: `CLAUDE.md`

- [ ] **Korak 1: Prebroj šta je ostalo**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
for f in ui/ChessClockView.kt ui/LearnView.kt ui/LessonDetailView.kt ui/LessonRenderer.kt \
         ui/BoardView.kt ui/BoardTheme.kt; do
  printf "  %-26s %s\n" "$f" "$(grep -c 'Color(0x\|Color\.White\|Color\.Black' $f)"
done
```

**Svaki pogodak PROČITAJ, ne samo prebroj.** U 6d-1 je ovaj broj bio pogrešan tri puta uzastopno,
dva puta zato što je grep uhvatio boju **unutar komentara** (`PathView.kt:175`,
`PromotionOverlay.kt:90`). Očekuj isto ovde. Navedi posebno stvarne izuzetke i posebno lažne pogotke.

- [ ] **Korak 2: Ispravi zastarelu tvrdnju o tabli**

`CLAUDE.md:237` kaže *„Highlight: žuto za poslednji potez, **sivo za selekciju**"*. Kod obe
platforme koristi **cijan** `#00D2FF` za selekciju (`SquareView.swift:101`,
`BoardView.kt:408`). Ispravi rečenicu i zapiši da je bila netačna — ne tiho.

- [ ] **Korak 3: Ažuriraj `CLAUDE.md`**

- Tabela „Stanje Android porta": red `1 — dizajn sistem` iz `delimično (6d-1)` u **`da`**,
  uz nov red `6d-2`
- Podsekcija „Android dizajn sistem": spisak „koji ekrani prate temu a koji ne" mora
  **nestati kao spisak izuzetaka** — svi prate. Zadrži rečenicu da je preneta paleta, a ne
  razmaci/radijusi/tipografija.
- „Poznata ograničenja": upiši (a) da ivice kartica ne postoje i zašto (izmereno 1,067 / 1,285 /
  1,109 — nijedan par ne daje vidljivu liniju; iOS to rešava nativnom grouped listom);
  (b) da emoji u UI hromu (~37) i u `lessonIcon()` mapi (44) ostaju, odlukom, i da je to izbor
  ikonografije a ne boja; (c) da `DS.Space`/`DS.Radius`/`Type.*` i dalje nemaju pozivno mesto
- Changelog: nov unos sa **izmerenim** brojevima

- [ ] **Korak 4: Provere celog stabla**

```bash
cd /Users/veljkoodobasic/Documents/Projects/Chess
D=$(git diff --stat main..HEAD -- Chessko Chessko.xcodeproj); [ -z "$D" ] && echo "iOS netaknut"
git diff --stat main..HEAD -- ChesskoAndroid/app/build.gradle.kts ChesskoAndroid/gradle/libs.versions.toml
grep -rn "Purple80\|Pink40\|dynamicColor" ChesskoAndroid/app/src/main/ || echo "sablon uklonjen"
```

- [ ] **Korak 5: Oba skupa testova**

JVM i instrumentisani. Brojeve čitaj **iz XML-a**. Polazno stanje: 43 JVM, 46 instrumentisanih.

- [ ] **Korak 6: JEDAN grupisan prolaz emulatora**

Ovo je jedini korak cele faze koji diže emulator. `-gpu host`, nikad `-gpu off`.
Snimi u **repo-scratch** (`.superpowers/sdd/<plan>/screenshots/`), ne u sesijski `/tmp` —
u 6d-1 su nestali pa ih finalni pregled nije mogao videti.

Obe teme, ovim redom:
1. **Sat** — izbor vremenske kontrole, info dijalog, stanje u toku, istek vremena.
   Potvrdi da su **crna i bela polovina i dalje crna i bela u obe teme.**
2. **Lekcija** — otvori bar dve, skroluj do dna; `RULE` i `WARNING` kutije, citat, tabela
   vrednosti figura, vežba
3. **Ekran učenja** — istraživač figura sa izabranim i neizabranim poljem, sve tri kartice vežbi
   u sva tri statusa ako je izvodljivo
4. **Podešavanja** — četiri kartice bez ivice, prekidači u oba stanja
5. **Tabla** — poslednji potez, selekcija, moguća polja, prsten uzimanja

Odmah po prolazu:
```bash
$ANDROID_HOME/platform-tools/adb emu kill
(cd ChesskoAndroid && ./gradlew --stop)
pgrep -f qemu-system || echo "qemu: nema"
```

- [ ] **Korak 7: Commit**

---

## Samopregled

**1. Pokrivenost spec-a 5.6**

| zahtev | task |
|---|---|
| Jedan uzdržan akcent umesto više boja po ekranu | 2 (sedam boja po lekciji → `DS.accent`) |
| Boje ostaju gde nose značenje | 2 (`RULE`/`WARNING`), 3 (statusi vežbi) |
| Tabla je jedini šaroliki element | 4 deo B — tabla ostaje netaknuta, uz iOS paritet |
| Sat prati stranu, ne temu | 1, jedanaest zaštićenih boja |
| Neutralne podloge | 1–4 |

**Ono što ovaj plan NE pokriva i to izričito kaže:** emoji → ikone; `DS.Space`/`DS.Radius`/
`Type.*` bez pozivnih mesta. Oba odložena odlukom korisnika, oba zapisana u Task-u 5.

**2. Placeholderi:** svaki task nosi svoje konkretne brojeve linija i svoju tabelu preslikavanja
po fajlu. Tri mesta namerno traže da izvršilac **sam izmeri i upiše** (`<UPISI STVARNU>`) —
to nisu placeholderi nego zahtev da se podloga utvrdi umesto pretpostavi, jer je pretpostavljena
podloga tačno ono na čemu je 6d-1 pukla četiri puta.

**3. Doslednost tipova:** `lessonAccent()` zadržava potpis (Task 2), pa Task 3 ne mora ništa da
menja na pozivnim mestima. `ContrastTest.check(...)` se koristi u Taskovima 1–4; potpis se čita
iz fajla pre prve upotrebe.
