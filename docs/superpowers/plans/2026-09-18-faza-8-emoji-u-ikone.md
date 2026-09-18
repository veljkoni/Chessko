# Faza 8 — emoji u ikone (Android hrom)

> **Za agentske izvršioce:** OBAVEZNA POD-VEŠTINA: koristi
> superpowers:subagent-driven-development za izvođenje ovog plana task po task.

**Cilj:** Zameniti emoji koji glume kontrolu Material ikonama, na 34 mesta u Android hromu.
Nijedna nova funkcija.

**Arhitektura:** Ništa se ne uvodi. Mapiranje se **čita iz iOS SF simbola**, ikone su već dostupne.

**Spec:** v2 §5.6 („Emoji u UI → ikone"). Spec imenuje `🔄`, koji je u `MainActivity`.

---

## Globalna ograničenja

- **Ni jedna nova Gradle zavisnost.** `material-icons-extended` je **već** zavisnost
  (`app/build.gradle.kts:67`) — pun set je dostupan, ništa se ne dodaje.
- **`Chessko/` (iOS) se čita radi poređenja, nikad ne menja.**
- Svaki `loc()` ključ literal unutar `loc()`, u rečniku, na svih 8 jezika, **nijedan `%lld`**.
- Sav UI na `DS.*` tokenima — **nijedna nova zakučana boja**. Par se meri, ne pretpostavlja.
- `BoardView.kt` / `BoardTheme.kt` ostaju **bez ijednog `DS.`**.
- **Jedanaest boja polovina sata ostaju fiksne** — prate STRANU u igri, ne temu.

---

## Dva merenja koja su obim prepolovila

**1. `material-icons-extended` je već tu.** Nema odluke o zavisnosti; ceo Material set je dostupan.
Aplikacija već koristi 28 ikona, među njima `Computer`, `Timer`, `Warning`, `PlayArrow`,
`ChevronLeft/Right`, `Check`.

**2. Emoji unutar prevedenih poruka NISU odstupanje Androida — van su obima.**
iOS katalog ima **istih 8 ključeva sa istim emoji-jem** (`Bravo! Mat! 🎉`,
`Sjajno! Mat pronađen! 🏆`, `Odlično! Zadatak rešen! 🎉`…), a `SettingsSheet.swift:121` ima doslovno
`"\(stats.bestWinStreak) 🔥"` i `:123` `"\(stats.puzzlesSolved) 🧩"`.

To je **deljena, namerna odluka**. Uklanjanje na Androidu bi pokvarilo paritet, ne uspostavilo ga.
**Ne diraj ih.**

**Odlukom korisnika van obima i `lessonIcon()`** — 49 imena SF simbola iz lekcijskog JSON-a u emoji.
iOS ih crta nativno; Android ih mapira, i `CLAUDE.md` to dokumentuje kao namernu razliku. Ostaje.

---

## Zašto ovo nije estetika

Faza 6d je izmerila: **Skia ignoriše `color=` za pun-kolor emoji glifove.** Emoji zato ne prima
nijedan uobičajeni signal stanja. Dugme za resetovanje sata je zbog toga moralo da dobije
`Modifier.alpha(0.38f)` kao zaobilaznicu (`ChessClockView.kt`), a tri komentara u tom fajlu
objašnjavaju zašto `color=` tamo nedostaje.

Sa pravom ikonom `tint` ponovo radi. **Ti komentari i ta zaobilaznica postaju netačni i moraju se
prepisati, ne ostaviti.**

---

## Tabela mapiranja — obavezujuća, čitana iz iOS-a

Za svako mesto je naveden SF simbol koji iOS koristi na istom mestu. **Gde SF simbol nije naveden,
zapisano je da ga nema** — tu presuđuješ sam i obrazlažeš.

| emoji | značenje | iOS SF simbol | Material |
|---|---|---|---|
| `↩️` | vrati potez | `arrow.uturn.backward` | `Icons.AutoMirrored.Filled.Undo` |
| `🏳️` `🏳` | predaja | `flag.fill` | `Icons.Filled.Flag` |
| `🔄` | reset / pokušaj ponovo | `arrow.clockwise` | `Icons.Filled.Refresh` |
| `🤖` | protivnik računar | `cpu` | `Icons.Filled.Computer` *(već u upotrebi)* |
| `❌` | zatvori | `xmark` | `Icons.Filled.Close` |
| `⏱️` | vremenska kontrola | `timer` | `Icons.Filled.Timer` *(već u upotrebi)* |
| `ℹ️` | info | `info.circle` | `Icons.Outlined.Info` |
| `▶️` / `⏸️` | pusti / pauziraj | — | `Icons.Filled.PlayArrow` *(već)* / `Icons.Filled.Pause` |
| `◀️` / `▶️` | prethodni / sledeći dan | `chevron.left` / `chevron.right` | `Icons.Default.ChevronLeft/Right` *(već)* |
| `✅` | rešeno / tačno | `checkmark.circle.fill` | `Icons.Filled.CheckCircle` |
| `⚠️` | greška / upozorenje | `exclamationmark.triangle.fill` | `Icons.Filled.Warning` *(već)* |
| `🏆` | trofej (mat rešen) | `trophy.fill` | `Icons.Filled.EmojiEvents` |
| `⭐` | izabrana kontrola | `star.fill` | `Icons.Filled.Star` |
| `💡` | prikaži rešenje | — | `Icons.Filled.Lightbulb` |
| `📤` | podeli | — | `Icons.Filled.Share` |
| `🌐` | spoljni link | — | `Icons.Filled.Public` |
| `😔` `🤝` `♟️` | poraz / remi / u toku | — | vidi Task 2 |

**Nijedno ime ikone ne uzimaj zdravo za gotovo.** `material-icons-extended` ima veliki ali ne
beskonačan set; ako se neko ime ne kompajlira, nađi najbliže i **zapiši šta si zamenio i zašto**.

---

## Tri stvari koje se lako promaše

**1. Emoji je često zalepljen na tekst.** `"↩️ " + loc("Vrati")` nije zamena znaka nego prelazak na
`Row { Icon(...); Text(loc("Vrati")) }`. Razmak koji je nosio emoji sada nosi raspored.

**2. Ikona traži `contentDescription`.** Emoji je nosio značenje sam; ikona bez opisa je nevidljiva
za čitač ekrana. iOS ima a11y labele na tim mestima. **Svaka nova ikona dobija `contentDescription`,
kroz `loc()`** — i to je poboljšanje koje ova faza donosi usput, ne teret.

**3. Ikona uzima boju iz tokena, emoji nije.** Svaka nova ikona ima `tint` i podlogu — **izmeri par.**
Faza 6d je na tome pukla pet puta; peti put su oba člana bila uredno tokenizovana ali međusobno
nerazlučiva (1,067:1). **Token nije jedinica provere — par jeste.**

---

## Ruling: emulator se diže JEDNOM, u Task-u 5

Faza 7 ga je digla pet puta iako je plan predvideo jedno. Ovde su sve izmene vizuelne i grupišu se
prirodno. Taskovi 1–4 dokazuju se `assembleDebug`-om, JVM testovima i `ContrastTest`-om.

Ako neki task **stvarno** ne može bez emulatora — **javi umesto da ga dižeš.**

`-gpu host`, **NIKAD `-gpu off`**. Gašenje odmah po prolazu.

---

## Polazno stanje

JVM **101**, instrumentisani **52**, oba 0 padova. Grana polazi od `main` = `9ba3cfc`.
34 mesta sa emoji-jem u hromu, prebrojana poimence.

---

## Task 1: Šahovski sat (6 mesta)

**Fajlovi:** `ui/ChessClockView.kt`

Mesta: `:413` `❌`, `:433` `⏱️`, `:447` `ℹ️`, `:460` `▶️`/`⏸️`, `:486` `🔄`, `:580` `⭐`.

- [ ] **Korak 1: Zameni šest emoji-ja ikonama po tabeli**

- [ ] **Korak 2: Vrati signal stanja dugmetu za resetovanje**

`:486` (`🔄`) ima `Modifier.alpha(0.38f)` kao **zaobilaznicu** uvedenu u Fazi 6d, jer emoji ne prima
`color=`. Sa ikonom `tint` ponovo radi.

Presudi **obrazloženo**: ostaje li `alpha` (M3 standard za onemogućeno) ili se prelazi na `tint`
sa `DS.ink`/`DS.inkMuted` kao ostala dugmad na grani. **Izmeri oba para** i reci šta si izabrao.

- [ ] **Korak 3: Prepiši tri netačna komentara**

Komentari na `:444`, `:457` i uz `:486` objašnjavaju zašto `color=` nedostaje — posle ove izmene to
više nije tačno. **Ne briši ih nego prepiši**: zapis da Skia ignoriše `color=` za emoji je i dalje
vredan, samo više ne opisuje ovaj kod.

- [ ] **Korak 4: Jedanaest zaštićenih boja ostaje netaknuto**

Crna i bela polovina sata prate **STRANU U IGRI**, ne temu. Ikone koje stoje **na njima** moraju
uzeti fiksnu boju, ne token — inače se u svetloj temi izgube.

```bash
sed -n '165,295p' ui/ChessClockView.kt | grep -c "DS\."
```
Očekivano posle tvog rada: **0**, isto kao pre.

- [ ] **Korak 5: `contentDescription` za svih šest**, kroz `loc()`. Novi ključevi na svih 8 jezika,
      prevodi usklađeni sa iOS a11y labelama gde postoje.

- [ ] **Korak 6: Kontrast** — za svaku novu ikonu izmeri `tint` prema stvarnoj podlozi, obe teme.
      Nov par → tvrdnja u `ContrastTest`; **nov test samo za NOV par**.

- [ ] **Korak 7: Testovi, build, commit**

---

## Task 2: Ekran Igra (13 mesta)

**Fajlovi:** `MainActivity.kt`

Dva različita posla u istom fajlu:

**(a) Traka statusa partije** — `:521-538`, glif se bira po `GameStatus`:
`🏆` (pobeda), `😔` (poraz), `🏳️` (predaja), `🤝` (remi), `♟️` (u toku).

`🏆` ima iOS par (`trophy.fill`); ostala tri nemaju. **Presudi i obrazloži.** Predlog za razmatranje,
ne nalog: poraz → `Icons.Filled.SentimentDissatisfied`, remi → `Icons.Filled.Handshake`,
u toku → bez ikone (status nosi tekst).

**Pazi:** boja glifa mora da nosi značenje kao i sada — pobeda/poraz/remi nisu ista poruka.
`DS.success` / `DS.danger` / `DS.inkMuted`, i **izmeri svaki par**.

**(b) Dugmad** — `:1242` `🤖`, `:1292` `↩️`, `:1310` `🏳️`, `:1328` `📤`, `:1342` `🔄`.
Sva četiri u obliku `"emoji " + loc("...")` — prelaze na `Row { Icon; Text }`.

- [ ] **Korak 1: Traka statusa** — [ ] **Korak 2: Pet dugmadi**
- [ ] **Korak 3: `contentDescription` svuda** — [ ] **Korak 4: Kontrast, obe teme**
- [ ] **Korak 5: Dugmad moraju raditi u OBE grane rasporeda** — `MainActivity` ima odvojenu
      pejzažnu granu (`:646-672`, `if (isLandscape)`). Provereno pri planiranju, ne pretpostavljeno.
- [ ] **Korak 6: Testovi, build, commit**

---

## Task 3: Zadaci i koraci Puta (10 mesta)

**Fajlovi:** `ui/PuzzleView.kt` (7), `ui/StepPracticeView.kt` (1), `ui/StepGameView.kt` (2)

`PuzzleView`: `:89` `⚠️`, `:208` `⚠️`, `:321` `◀️`, `:345` `✅`, `:361` `▶️`, `:492` `💡`, `:506` `🔄`.
`StepPracticeView`: `:112` `⚠️`. `StepGameView`: `:163` `🏳`, `:270` `🤖`.

- [ ] **Korak 1: Kvačica rešenosti** (`:345`) — pročitaj komentar iznad nje pre izmene: kaže da
      emoji glif nosi **sopstvenu** boju, pa boje nije ni bilo. Sa ikonom boja postoji i mora se
      izabrati; `DS.success` je očekivan, ali **izmeri par** prema stvarnoj podlozi trake.

- [ ] **Korak 2: Strelice datuma** (`:321`, `:361`) — `ChevronLeft/Right` su **već u upotrebi**
      drugde u projektu; koristi isti obrazac, ne nov.

- [ ] **Korak 3: Ostalo po tabeli** — [ ] **Korak 4: `contentDescription`** — [ ] **Korak 5: Kontrast**
- [ ] **Korak 6: Testovi, build, commit**

---

## Task 4: Učenje i podešavanja (5 mesta)

**Fajlovi:** `ui/LearnView.kt` (3), `ui/SettingsView.kt` (2)

`LearnView`: `:585` `✅`, `:702` `✅`, `:873` `🏆`. `SettingsView`: `:580` `🌐`, `:596` `🌐`.

- [ ] **Korak 1: Dve kvačice i trofej** — iste ikone kao Task 3, isti par; **ne izmišljaj drugu.**
- [ ] **Korak 2: Dva `🌐` u „O aplikaciji"** — to su linkovi ka Stockfish-u i Lichess-u, deo
      GPLv3 obaveze. **Tekst linkova se ne dira**, samo znak ispred.
- [ ] **Korak 3: `contentDescription`** — [ ] **Korak 4: Kontrast** — [ ] **Korak 5: Commit**

---

## Task 5: Zatvaranje faze

**Fajlovi:** `CLAUDE.md`

- [ ] **Korak 1: Prebroj šta je ostalo**

```bash
cd ChesskoAndroid/app/src/main/java/com/veljkoni/chessko
grep -rnP '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}\x{2B00}-\x{2BFF}]' --include='*.kt' . | wc -l
```

**Svaki pogodak PROČITAJ, ne samo prebroj.** U prethodnim fazama je isti broj bio pogrešan četiri
puta, najčešće zato što je grep uhvatio znak **u komentaru**. Očekivano posle ove faze: samo
`lessonIcon()` mapa (49), emoji u prevedenim porukama (8 ključeva), `🔥`/`🧩` u statistici, i
`ChessPiece.kt` (Unicode figure — **nisu emoji**).

- [ ] **Korak 2: Oba skupa testova** — JVM i instrumentisani, brojevi **iz XML-a**. Polazno 101 / 52.

> **Zamka iz Faze 7:** ako si negde radio dokaz mutacijom, **pokreni skup ponovo posle vraćanja
> koda** — inače na disku ostaje crven XML iz namerno pokvarenog prolaza, koji je tačan ali ne
> opisuje stablo.

- [ ] **Korak 3: JEDAN prolaz emulatora**, obe teme:
  1. **Sat** — zatvori, info, pusti/pauziraj, reset u **oba** stanja (omogućeno i onemogućeno),
     izbor vremenske kontrole sa zvezdicom. **Crna i bela polovina moraju ostati crna i bela.**
  2. **Igra** — traka statusa u sva četiri ishoda ako je izvodljivo; pet dugmadi, **obe** grane
     rasporeda (portret i pejzaž)
  3. **Zadaci** — strelice datuma, kvačica rešenosti, „Prikaži rešenje", greška
  4. **Učenje** — dve kvačice i trofej
  5. **Podešavanja** — dva linka u „O aplikaciji"

  Snimci u `.superpowers/sdd/<plan>/screenshots/`, **ne u sesijski `/tmp`**.

- [ ] **Korak 4: `CLAUDE.md`**
  - stavku o emoji-ju prepiši kao **delimično zatvorenu**: hrom je gotov, `lessonIcon()` i emoji u
    porukama **namerno ostaju** — uz razlog (iOS ima isto)
  - **zapiši da je nalaz o `color=` i dalje tačan** kao činjenica o Skia, samo više ne opisuje naš kod
  - changelog sa izmerenim brojevima

- [ ] **Korak 5: Provere celog stabla i commit**

---

## Samopregled

**1. Pokrivenost:** 34 mesta, raspoređena po ekranima tako da recenzent može odbiti jedan ekran a
primiti drugi.

**2. Placeholderi:** Task 2(a) namerno **ne zadaje** ikone za poraz/remi/u toku — iOS par ne postoji,
pa je to presuda sa obrazloženjem, ne prepisivanje. Task 1, Korak 2 isto: `alpha` protiv `tint` se
bira merenjem.

**3. Doslednost:** `CheckCircle` se koristi u Taskovima 3 i 4 — isti par, ista boja; Task 4 izričito
zabranjuje da izmisli drugu.
