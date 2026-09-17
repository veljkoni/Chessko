# Faza 7 — sitnice koje korisnik vidi

> **Za agentske izvršioce:** OBAVEZNA POD-VEŠTINA: koristi
> superpowers:subagent-driven-development za izvođenje ovog plana task po task.

**Cilj:** Zatvoriti pet zapisanih defekata koje korisnik stvarno primeti. Nijedna nova funkcija.

**Arhitektura:** Ništa se ne uvodi. Četiri od pet popravki su **preuzimanje rešenja koje iOS već
ima**; peta je lokalna izmena rukovanja gestom.

**Tehnologije:** Kotlin, Jetpack Compose. **Bez ijedne nove zavisnosti.**

**Spec:** v2 (`docs/superpowers/specs/2026-09-05-chessko-v2-design.md`) je **završen** — ovo je
naplata duga iz „Poznatih ograničenja", ne nova faza spec-a.

---

## Globalna ograničenja

- **Ni jedna nova Gradle zavisnost.**
- **`Chessko/` (iOS) se čita radi poređenja, nikad ne menja.**
- **`create_xcode_project.py` se NIKAD ne pokreće.**
- Svaki `loc()` ključ **literal unutar `loc()`**, u rečniku, na **svih 8 jezika**, **nijedan `%lld`**.
- Sav UI ostaje na `DS.*` tokenima — **nijedna nova zakučana boja**. Par se meri, ne pretpostavlja.
- `BoardView.kt` / `BoardTheme.kt` ostaju **bez ijednog `DS.`** (tabla je sadržaj).
- `models/MoveAnalysis.kt`, `logic/UCIScoreParser.kt`, `logic/PathProgress.kt`,
  `logic/PuzzleRating.kt` ostaju **bez ijednog `android.*` uvoza**.

---

## Izmereno pre pisanja plana — i to je obim prepolovilo

Prvobitna pretpostavka je bila „treba podrška za množinu". **Nije.**

| provera | nalaz |
|---|---|
| `locF` sa `%d` na Androidu | **10** poziva |
| od toga stvarno lome množinu | **1** — `"Niz: %d dana"` (`PathView.kt:162`) |
| `"%d mogućih poteza"` | **nije defekt** — `LearnViewModel.kt:100-108` već ima iste tri grane kao iOS (0 / 1 / ostalo) |
| ostalih 8 | broj iza oznake („Korak 3", „Mat u 2") — oblik se ne menja |
| `plural variations` u iOS katalogu | **0** |

**iOS taj problem nema jer ga je rešio rasporedom, ne mehanizmom** (`PathView.swift:286-292`):
crta **broj i oznaku odvojeno** — formatiran broj, pa `Loc("Dana zaredom")`. Oznaka se ne menja sa
brojem ni u jednom od 8 jezika.

Zato Task 1 **ne gradi podršku za množinu**. Gradnja mehanizma za jedan string bila bi skuplja od
problema i uvela bi drugi izvor istine pored `Loc.kt`.

---

## Vodeće načelo ove faze

> **Četiri od pet popravki već postoje na iOS-u. Pročitaj ih tamo pre nego što ih napišeš ovde.**

Ovaj projekat je u prethodne tri faze platio **šest** grešaka iste porodice — *nešto se pretpostavi
umesto da se proveri*. Dve od njih bile su tvrdnje o sopstvenom kodu napisane po sećanju.

Ako u izveštaju napišeš „isto kao iOS" ili „postoji presedan" — **otvori fajl i potvrdi.**
Netačno obrazloženje je nalaz iste težine kao bug.

---

## Ruling: emulator se diže JEDNOM, u Task-u 5

Faza 6d-1 ga je digla šest puta, 6d-2 dvaput, 6e četiri puta. Ovde su sve popravke vizuelne ili
lako proverljive instrumentisanim testom, pa se grupišu u **jedan** prolaz na kraju.

Taskovi 1–4 dokazuju se `assembleDebug`-om, JVM testovima i — gde je primenljivo — instrumentisanim
testom koji se **samo kompajlira**. Ako neki task stvarno ne može bez emulatora, **javi umesto da
ga dižeš.**

`-gpu host`, **NIKAD `-gpu off`**. Gašenje odmah: `adb emu kill`, `./gradlew --stop`,
`pgrep -f qemu-system` prazno.

---

## Polazno stanje

JVM **83**, instrumentisani **50**, oba 0 padova. Grana polazi od `main` = `38f96db`.

---

## Task 1: Streak — broj i oznaka odvojeno, kao iOS

**Fajlovi:**
- Izmeni: `ChesskoAndroid/.../ui/PathView.kt`
- Izmeni: `ChesskoAndroid/.../logic/Loc.kt`

- [ ] **Korak 1: Pročitaj iOS original pre izmene**

`Chessko/Views/PathView.swift:283-305` (`streakCard`). Obrati pažnju da su broj i oznaka **dva
odvojena `Text`-a** u `HStack`-u sa `alignment: .firstTextBaseline`.

- [ ] **Korak 2: Zameni jedan `Text` sa dva**

Zatečeno (`PathView.kt:162`):
```kotlin
Text(text = locF("Niz: %d dana", streak), color = DS.ink, fontSize = 15.sp, ...)
```
Novo: `Row` sa poravnanjem po osnovnoj liniji — broj (`DS.ink`, veći) pa `loc("Dana zaredom")`
(`DS.inkMuted`, manji). Prati iOS hijerarhiju: broj je `dsTitle`, oznaka `dsBody`.

**Zašto ovako, upiši u komentar:** `locF("Niz: %d dana", 1)` daje „Niz: 1 **dana**" na srpskom,
„Streak: 1 **days**" na engleskom i „Серия: 1 **дней**" na ruskom — izmereno na uređaju, ne
pretpostavljeno. Oznaka odvojena od broja nema taj problem ni u jednom od 8 jezika, i to je
rešenje koje iOS već koristi.

- [ ] **Korak 3: Nov ključ u `Loc.kt`**

`"Dana zaredom"` na svih 8 jezika. **Prepiši prevode doslovno iz `Chessko/Localizable.xcstrings`** —
ključ tamo već postoji. Ne prevodi sam.

- [ ] **Korak 4: Ukloni stari ključ ako je ostao bez ijednog pozivaoca**

```bash
grep -rn '"Niz: %d dana"' ChesskoAndroid/app/src/main/
```
Ako ostane samo u `Loc.kt`, obriši ga — mrtav ključ je šum u rečniku.

**Usput zapaženo, ne popravljaj bez razloga:** Android koristi `"Cilj za danas **je** ispunjen"`,
iOS `"Cilj za danas ispunjen"`. Ako je jeftino uskladiti sa iOS-om, uradi i zapiši; ako traži
diranje rečnika na 8 jezika radi jedne reči, **zapiši u izveštaj i ostavi.**

- [ ] **Korak 5: Testovi i build**

`./gradlew testDebugUnitTest` (očekivano 83, `LocTest` mora da prođe — on proverava da svaki
`loc()` literal ima ključ) + `assembleDebug`. Broj **iz XML-a**.

- [ ] **Korak 6: Commit**

---

## Task 2: `SoundManager` se oslobađa pri promeni jezika

**Fajlovi:**
- Izmeni: `ChesskoAndroid/.../MainActivity.kt`

**Zatečeno stanje, izmereno:** `MainActivity.kt` ima **nula** `DisposableEffect`-a.

`MainActivity.kt:100-101` pravi `gameViewModel` i `puzzleViewModel` kroz `remember { ... }` unutar
`key(languageKey) { ... }`. Svaki od njih pravi **sopstveni** `SoundManager`
(`PuzzleViewModel.kt:54`, `GameViewModel.kt:87`) — nije singleton. Pošto se ne prave kroz
`ViewModelStore`, `onCleared()` im se nikad ne izvrši, pa `release()` ne radi.

Kad korisnik promeni jezik, `key(languageKey)` odbacuje ceo blok i pravi **nove** modele sa novim
`SoundPool`-ovima; stari ostaju neoslobođeni do gašenja procesa.

- [ ] **Korak 1: Pročitaj obrazac koji projekat već koristi**

`ChesskoAndroid/.../ui/ChessClockView.kt:129-133` — tamo `DisposableEffect` već radi tačno ovo.
Ekrani koraka (`StepPracticeView`, `StepGameView`) su ga dobili u Fazi 6c. `MainActivity` nije,
jer je bio van obima te faze.

- [ ] **Korak 2: Dodaj `DisposableEffect` za oba modela**

`onDispose` mora da pozove `releaseSounds()` (ili ekvivalent koji ti modeli izlažu — **proveri kako
se zove**, ne pretpostavljaj).

- [ ] **Korak 3: Dokaži da popravka radi**

Instrumentisani test ili logovanje koje pokazuje da se `release()` stvarno zove pri promeni jezika.
Ako ide kroz instrumentisani test — **samo ga kompajliraj**, izvršavanje je u Task-u 5.
Ako ne možeš da dokažeš bez emulatora, **reci to izričito** umesto da tvrdiš da radi.

- [ ] **Korak 4: Testovi, build, commit**

---

## Task 3: Datum na srpskom, ne na engleskom

**Fajlovi:**
- Izmeni: `ChesskoAndroid/.../ui/PuzzleView.kt`

**Zatečeno:** `PuzzleView.kt:305`
```kotlin
val dateStr = viewModel.selectedDate.format(DateTimeFormatter.ofPattern("d. MMMM yyyy."))
```
`ofPattern` bez `Locale` uzima **sistemski**, ne jezik izabran u aplikaciji — pa u srpskom UI-ju
na telefonu sa engleskim sistemom piše „September".

- [ ] **Korak 1: Nađi kako aplikacija zna izabran jezik**

`logic/Loc.kt` drži izbor. **Pročitaj kako se čita** (`getLanguage()` / `fileLanguageCode()` — imaju
različito značenje, vidi „Poznata ograničenja" u `CLAUDE.md`) i koji je ispravan za `Locale`.

**Zamka koja već postoji u projektu:** `getLanguage()` vraća `"zh"`, a lekcijski fajlovi traže
`"zh-Hans"`. Za `Locale` ti treba jezički kod, ne kod fajla — ali **potvrdi čitanjem**, ne odokativno.

- [ ] **Korak 2: Proslediti `Locale` formatteru**

`DateTimeFormatter.ofPattern(pattern, locale)`. Proveri da `zh-Hans` i `hi` daju smislen rezultat,
ne samo `sr` i `en`.

- [ ] **Korak 3: Proveri ima li još mesta sa istim propustom**

```bash
grep -rn "ofPattern\|SimpleDateFormat\|String.format" ChesskoAndroid/app/src/main/java/
```
Faza 6e je već popravila jedan takav slučaj (`AnalysisView.kt`, `Locale.US` za procenat). Uskladi
se sa tim presedanom — **otvori ga i vidi zašto je tamo `US`**, jer za procenat i za naziv meseca
odgovor nije isti.

- [ ] **Korak 4: Testovi, build, commit**

---

## Task 4: Završena partija preživljava rekreaciju `Activity`-ja

**Fajlovi:**
- Izmeni: `ChesskoAndroid/.../viewmodels/GameViewModel.kt` (verovatno; **prvo dijagnostikuj**)

**Zatečeno, prijavljeno i dvaput reprodukovano u Fazi 6e:** posle mata, predaje ili remija, promena
sistemske teme (ili rotacija) rekreira `Activity`, partija se učita sa diska — i **dugme
„Analiziraj partiju" nestane**, jer `canAnalyzeGame` traži `isGameOver`, a `GameState.fromFEN`
uvek vraća `GameStatus.Playing`, i za mat.

- [ ] **Korak 1: Dijagnostikuj pre nego što popravljaš**

Utvrdi **tačno** kako se partija čuva i vraća: da li se `status` uopšte serijalizuje, ili se
pozicija vraća kroz `fromFEN` koji ga gubi. Zalepi nalaz u izveštaj sa brojevima linija.

Dve moguće popravke — **izaberi obrazloženo**:
- **(a)** serijalizuj `status` uz partiju i vrati ga pri učitavanju
- **(b)** posle učitavanja **preračunaj** status iz pravila (`MoveGenerator.legalMoves` + `isInCheck`),
  isto kao `terminalEval` u `StockfishEngine.kt:334-344`

(b) ima prednost što ne zavisi od formata zapisa i ne lomi stare sačuvane partije; (a) je brža.
**Proveri kako iOS rešava isto** (`Chessko/ViewModels/GameViewModel.swift`, `SavedGame`) i reci da
li se platforme razilaze.

- [ ] **Korak 2: Pazi da ne pokvariš zatečeno**

`GameViewModel` ima **parametrizovan ključ** za čuvanje (slobodna partija vs. korak Puta) — vidi
„Zamke koje su već jednom ujele" u `CLAUDE.md`. Popravka ne sme da pomeša ta dva slota.

- [ ] **Korak 3: Dokaz**

Instrumentisani test je ovde prirodan (rekreacija `Activity`-ja). **Samo ga kompajliraj**;
izvršavanje je u Task-u 5.

- [ ] **Korak 4: Testovi, build, commit**

---

## Task 5: Gest na tabli ne guta vertikalni skrol — i zatvaranje faze

Dva posla: poslednja popravka i zatvaranje. Popravka je najosetljivija u fazi, pa ide uz prolaz
emulatora koji je ionako potreban.

**Fajlovi:**
- Izmeni: `ChesskoAndroid/.../ui/BoardView.kt`
- Izmeni: `CLAUDE.md`

### Deo A — gest

**Zatečeno:** `BoardView.kt:109-110` koristi `detectDragGestures`. `onDragStart` **već** proverava
ima li figure na polju (`:122-130`), ali `detectDragGestures` **troši pokazivač bez obzira na to** —
pa vertikalni skrol prestaje da radi kad su dve interaktivne table blizu u vidnom polju (dve vežbe
jedna ispod druge u istoj lekciji).

Zatečeno pre Faze 6c; ta faza ga je samo učinila vidljivim, jer je Put doneo nove table u nove
kontekste skrolovanja.

- [ ] **Korak 1: Troši pokazivač samo kad gest počinje NA FIGURI**

Umesto `detectDragGestures`, koristi `awaitEachGesture` / `awaitPointerEventScope` i **ne troši**
prvi `down` ako na tom polju nema figure — tada gest propada roditeljskom `scroll`-u.

**Ne menjaj ponašanje kad figura postoji** — prevlačenje figure mora da radi tačno kao sada,
uključujući `onTap(pos)` pri početku.

- [ ] **Korak 2: Dokaži da skrol radi i da prevlačenje nije pokvareno**

Ovo se **mora** videti na emulatoru (Deo B). Nabroj šta si tačno proverio.

### Deo B — zatvaranje

- [ ] **Korak 3: Oba skupa testova**

JVM i instrumentisani, brojevi **iz XML-a**. Polazno: 83 / 50.

- [ ] **Korak 4: JEDAN prolaz emulatora**

Obe teme gde je relevantno:
1. **Streak kartica** — broj i oznaka odvojeno; posebno pri streak-u **1** (tu je bug bio vidljiv)
2. **Promena jezika** → potvrdi da se zvuk i dalje čuje posle promene (popravka Task-a 2 ne sme da
   oslobodi `SoundPool` koji je još u upotrebi)
3. **Traka datuma** na srpskom — naziv meseca mora biti srpski
4. **Završi partiju → promeni temu** → dugme „Analiziraj partiju" mora **ostati**
5. **Lekcija sa dve table** → vertikalni skrol radi; prevlačenje figure i dalje radi

Snimci u `.superpowers/sdd/<plan>/screenshots/`, **ne u sesijski `/tmp`**.
Gašenje odmah po prolazu.

- [ ] **Korak 5: `CLAUDE.md`**

Za svaku od pet stavki u „Poznatim ograničenjima" — **prepiši je kao zatvorenu, ne briši je.**
Zapis zašto je postojala vredi više od praznog mesta. Uz svaku: šta je bio uzrok i kako je rešeno.

Changelog sa **izmerenim** brojevima.

- [ ] **Korak 6: Provere celog stabla i commit**

```bash
D=$(git diff --stat main..HEAD -- Chessko Chessko.xcodeproj); [ -z "$D" ] && echo "iOS netaknut"
git diff --stat main..HEAD -- ChesskoAndroid/app/build.gradle.kts ChesskoAndroid/gradle/libs.versions.toml
```

---

## Samopregled

**1. Pokrivenost:** pet stavki iz „Poznatih ograničenja", svaka u svom tasku (3 i 4 su odvojeni jer
je jedan formatiranje a drugi perzistencija stanja — recenzent može odbiti jedan a primiti drugi).

**2. Placeholderi:** Task 4 namerno **ne zadaje popravku** nego dve opcije sa kriterijumom izbora —
jer se popravka ne može odabrati bez dijagnoze koja još nije urađena. Task 3 namerno ne zadaje koji
je kod jezika ispravan, nego traži da se pročita; projekat već ima zapisanu zamku oko toga.

**3. Doslednost tipova:** nijedan task ne menja javni interfejs koji drugi troši. Task 2 i Task 4
oba diraju `GameViewModel`/`MainActivity` okolinu — Task 2 samo dodaje `DisposableEffect`, Task 4
samo vraćanje statusa; ne preklapaju se, ali **Task 4 mora da pročita šta je Task 2 dodao** pre
nego što dira isti fajl.
