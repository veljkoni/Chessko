# IMPLEMENTACIJA-LOG — dizajnerski pregled pred izdanje

Spec: `docs/design/CHESSKO-IMPLEMENTACIJA.md`. Plan: `docs/superpowers/plans/2026-10-11-dizajn-pregled.md`.
Grana `v2/dizajn-pregled`. Svaki task dopisuje svoj odeljak ispod (urađeno po stavkama, neurađeno i
zašto, odluke, `TODO(design § N)`).

Skraćenice putanja u ovom fajlu:
- `iOS/` = `Chessko/` (npr. `iOS/Views/GameView.swift` = `Chessko/Views/GameView.swift`)
- `AND/` = `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/`
- `ANDT/` = `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/`

Brojevi linija su sa stabla `a207d28` (pre ijedne izmene koda). Kasniji taskovi pomeraju linije —
tražiti po navedenom isečku, ne slepo po broju.

---

## Task 1 — Inventar (§ 18 korak 1)

Bez izmena koda. Svaki spisak je dobijen grep-om i **pročitan pogodak po pogodak**; pogoci u
komentarima, u modelima (`.white`/`.black` kao strana u igri) i u definicijama samih tokena nisu
upisani kao pozivna mesta.

### 1.1 Mapa „ime u dokumentu → ime u kodu"

**Imena razmaka i radijusa se poklapaju sa pretpostavkom dokumenta na obe platforme** — mapa je
identitet, nema preimenovanja.

| Dokument | iOS (`iOS/Views/DesignSystem.swift`) | Android (`AND/ui/theme/DesignSystem.kt`, `Type.kt`) | Vrednost |
|---|---|---|---|
| `DS.accent` | `DS.accent` (:27) | `DS.accent` (:98; `LightColors`/`DarkColors` :51/:69) | `#2E4A8A` / `#7EA0E8` |
| `DS.ground` | `DS.ground` (:28) | `DS.ground` | `#F2F3F7` / `#0E1428` |
| `DS.surface` | `DS.surface` (:29) | `DS.surface` | `#FFFFFF` / `#161D33` |
| `DS.navBar` / `navBar` | `DS.navBar` (:30) — **0 pozivnih mesta** | `DS.navBar` — 1 mesto: donja traka `AND/MainActivity.kt:666` | `#FFFFFF` / `#131A2E` |
| `DS.fill` | `DS.fill` (:31) | `DS.fill` | `#E7EAF1` / `#1E2740` |
| `DS.line` | `DS.line` (:32) | `DS.line` | `#DFE3EC` / `#232C46` |
| `DS.ink` | `DS.ink` (:33) | `DS.ink` | `#161A22` / `#EEF1F7` |
| `DS.inkMuted` | `DS.inkMuted` (:34) | `DS.inkMuted` (`LightColors` :58) | `#6B7280` → **`#5E6573`** (Task 2) / `#8B93A7` |
| `DS.success` | `DS.success` (:38) | `DS.success` | `#2F7D4F` / `#6FCF97` |
| `DS.warning` | `DS.warning` (:39) | `DS.warning` (`LightColors` :60) | `#B07D2B` → **`#865F21`** (Task 2) / `#E0B252` |
| `DS.danger` | `DS.danger` (:40) | `DS.danger` | `#B3261E` / `#F2857A` |
| `onAccent` | `DS.onAccent` (:58) | `DS.onAccent` | `#FFFFFF` / `#161A22` |
| (nije u dokumentu) | `DS.scrim`, `DS.onScrim`, `DS.inkFixed` | isto | scrim crno 55 %, onScrim bela, inkFixed `#161A22` |
| `Space.xs/s/m/l/xl` 4/8/12/16/24 | `DS.Space.xs/s/m/l/xl` (:62-68, `CGFloat`) | `DS.Space.xs/s/m/l/xl` (:288-293, `Dp`) | isto |
| `Radius.s/m/l` 8/12/16 | `DS.Radius.s/m/l` (:76-80) | `DS.Radius.s/m/l` (:322-325) | isto |
| `maxBoardSide` 560 | `DS.maxBoardSide` (:72) | `DS.maxBoardSide` (:332) | 560 |
| `Type.title` 22 bold | `Font.dsTitle` = `.appFont(.title2).weight(.bold)` | `Type.title` (Bold 22/28) | 22 bold |
| `Type.heading` 17 | `Font.dsHeading` = `.appFont(.headline)` (= 17 **semibold**) | `Type.heading` (**SemiBold** 17/22) | 17 |
| `Type.body` 15 | `Font.dsBody` = `.appFont(.subheadline)` | `Type.body` (Normal 15/20) | 15 |
| `Type.caption` 12 | `Font.dsCaption` = `.appFont(.caption)` | `Type.caption` (12/16) | 12 |
| `Type.label` 11 | **ne postoji** (nema `Font.dsLabel`; ekvivalent bi bio `.appFont(.caption2)`) | `Type.label` (11/15) | 11 |
| `Type.mono` 13 | `Font.dsMono` = `.system(.footnote, design: .monospaced)` (ne ide kroz `appFont`) | `Type.mono` (Monospace 13/18) | 13 |

Napomene uz mapu:
- **„`Type.heading` bold"** (naslovi sekcija, § 3/§ 9/§ 15) — token je na obe platforme *semibold*.
  Ili `.bold()`/`fontWeight = FontWeight.Bold` na pozivnom mestu, ili Task 2 odlučuje o tokenu;
  `TypographyScaleTest` (Android) proverava samo `fontSize`, ne težinu.
- iOS nema `Type.label`. Spec ga traži (§ 5 labela ispod ikone < 360 pt, § 14 segmenti i oznaka „FIDE",
  § 15 autoSize). Predlog za Task 2: `Font.dsLabel = .appFont(.caption2)` (11 pt, CLAUDE.md
  „Android dizajn sistem" — Android skala je merena po UIKit-u, `caption2` = 11).
- `Type.mono` na iOS-u je `.footnote` monospaced — u Task-u 4 lista poteza koristi `.dsMono`; danas
  iOS `MoveHistoryView` ima sopstveni font (komentar `DesignSystem.swift:99`).
- iOS `Font.appFont` (`iOS/Logic/PlatformHelper.swift:5`) na Mac-u (idiom `.mac`) uvećava veličine;
  na iPhone/iPad vraća sistemski stil. Ne dirati.
- Android pored `Type` ima i M3 `Typography` (`AND/ui/theme/Type.kt:10-31`, bodyLarge 16 itd.) —
  koristi ga svaki `Text` bez `style`. Nije deo skale; ne uvoditi nove pozive koji se na njega
  oslanjaju.
- M3 šema (`AND/ui/theme/Theme.kt`) mapira `primary→accent`, `background→ground`,
  `surface/surfaceContainerHigh→surface`, `surfaceContainerHighest→fill`, `outline(Variant)→line`,
  `onSurfaceVariant→inkMuted`, `error→danger`. `AlertDialog` bez `containerColor` dobija
  `surfaceContainerHigh` = `surface` — dakle već na tokenu.
- Upotreba danas (pozivna mesta, grep `DS.Space.x|DS.Radius.x|Type.x|.dsX`):
  iOS `Space` xs 9 · s 22 · m 29 · l 20 · xl 6; `Radius` s 2 · m 12 · l 9; `.dsBody` 44 · `.dsCaption`
  45 · `.dsHeading` 10 · `.dsTitle` 5 · `.dsMono` 2. Android `Space` xs 42 · s 50 · m 49 · l 38 · xl 4;
  `Radius` s 19 · m 38 · l 10; `Type.body` 60 · `caption` 55 · `label` 30 · `heading` 18 · `title` 7 ·
  `mono` 4. iOS još ima mnogo zakucanih `.font(.subheadline…)`/`cornerRadius: 12/14/20` van tokena.

### 1.2 iOS — sistemske boje i materijali kao PODLOGA (§ 3, § 17)

**Koren problema je jedan tip, ne šest ekrana:**

| # | Mesto | Šta | Ko ga koristi |
|---|---|---|---|
| R1 | `iOS/Views/SquareView.swift:33-37` `AppBackgroundView` | `Color(uiColor: .systemGroupedBackground)` = `#F2F2F7` / `#000000` (tačno ono što spec vidi na snimcima) | `GameView.swift:228`, `PuzzleView.swift:18`, `LessonDetailView.swift:46`, `PathView.swift:171`, `StepGameView.swift:52`, `StepPracticeView.swift:37` |
| R2 | `iOS/Views/SquareView.swift:12-16` `Color.appBackground` | zakucan RGB `#EDF0F7` / `#0E1528` — **nije `DS.ground`** (`#F2F3F7`/`#0E1428`), pa se nav bar razlikuje od podloge za nijansu | `.toolbarBackground(Color.appBackground, for: .navigationBar)`: `GameView.swift:229`, `PuzzleView.swift:97`, `LessonDetailView.swift:80`, `StepGameView.swift:60`, `StepPracticeView.swift:67` |

Zamena R1 → `DS.ground` i R2 → `DS.ground` (ili `DS.navBar` po § 4 za Lekciju) popravlja sve
ekrane odjednom. Predlog: `AppBackgroundView` zadržati kao ime (6 pozivnih mesta) i promeniti mu
telo u `DS.ground.ignoresSafeArea()`; `Color.appBackground` ukloniti i 5 poziva prebaciti na token.

Ostala mesta (podloga/ispuna, ne tekst):

| Fajl:linija | Šta | Uloga | Task |
|---|---|---|---|
| `ChessClockView.swift:89` | `Color(uiColor: .systemBackground)` | koren sata, ispod polovina | 9 (pokriveno polovinama; svejedno zameniti) |
| `ChessClockView.swift:324` | `.ultraThinMaterial` | traka između polovina (svetla u svetloj temi, § 14) | 9 — postaje fiksno tamna (Android `ClockBarBackground #1E293B`) |
| `ChessClockView.swift:327`, `:333` | `Color.primary.opacity(0.08)` | hairline ivice trake | 9 |
| `ChessClockView.swift:236`, `:318` | `Color.primary.opacity(0.05)` u `Circle()` | okrugla dugmad na traci | 9 |
| `ChessClockView.swift:451-452` (`ChessClockInfoView`) | `NavigationStack { ScrollView` bez pozadine → sistemska | info ekran sata | 9 |
| `ChessClockView.swift:515` | `Color.primary.opacity(0.03)` | redovi objašnjenja kontrola (fill-blok, § 14 → `surface` grupa) | 9 |
| `SettingsSheet.swift:101` | `List {` bez `.scrollContentBackground(.hidden)` → nativna grouped pozadina + sistemska pozadina redova | ceo ekran Podešavanja | 2 (podloga) / 10 (grupe) |
| `SettingsSheet.swift:103,143,172,237,287,303,326,333,343` | `Section(Loc(…))` — sistemski siv naslov sekcije (`#85858B`, 3,29) | naslovi grupa | 2 / 10 |
| `SettingsSheet.swift:202`, `:256` | `.stroke(Color.primary.opacity(0.12/0.10))` | ivica neizabrane pločice teme/stila | 10 |
| `SettingsSheet.swift:252` | `.fill(Color.primary.opacity(0.04))` | podloga pločice stila | 10 |
| `GameView.swift:355` (+ `:358` stroke primary 0.12) | `.regularMaterial` | traka za pregled partije (review) | 4 |
| `GameView.swift:454` (+ `:458` stroke `accent.opacity(0.55)` / `primary.opacity(0.07)`) | `.regularMaterial` | kartica igrača; `accent@55%` preko materijala = `#8797BA` iz § 5 | 4 |
| `GameView.swift:494` | `.ultraThinMaterial` | birač boje pri novoj igri — **§ 0: ne dira se** (samo ivica 2 pt po § 5 ako postoji podrazumevani izbor) | 4 |
| `GameView.swift:566-605` (`gameOverOverlay`) | `DS.scrim` + tekst na scrim-u, dugmad `Capsule()` | kraj partije — nije `surface` dijalog, „stadion" dugmad | 4 |
| `GameView.swift:632` | `.fill(Color.secondary)` | tačke „razmišlja" (sitno, ne podloga) | 4 (opciono `inkMuted`) |
| `MoveHistoryView.swift:82` | `.regularMaterial` | kontejner liste poteza | 4 |
| `MoveHistoryView.swift:94`, `:102` | `Color.primary.opacity(0.08/0.04)` | zaglavlje / pruge redova | 4 |
| `PromotionOverlay.swift:49` | `.ultraThinMaterial` | kartica oko pločica promocije (pločica je § 0 izuzetak; kartica na Androidu je od 6d-1 `DS.surface`) | 4 (dijalog) — pločice `:39` ne dirati |
| `PuzzleView.swift:192` (kalendar `.sheet`) | `NavigationStack` bez pozadine → sistemska | birač datuma | 5 |
| `PuzzleView.swift:260` | `Color.secondary.opacity(0.08)` | dan u kalendaru | 5 |
| `PuzzleView.swift:330` | `Color.primary.opacity(0.06)` u `Capsule()` | tagovi teme (§ 7 → `fill` + `inkMuted`) | 5 |
| `PuzzleView.swift:352` (+ `:375`) | `statusColor.opacity(0.12)`, `statusColor` podrazumevano `Color.primary.opacity(0.9)` | **uputstvo** (siv okvir iz § 7) | 5 |
| `PuzzleView.swift:393`, `:426` | `Color.primary.opacity(0.06)` | sporedna dugmad „Prikaži rešenje", „Sledeći dan" | 5 |
| `PuzzleView.swift:477-478` | `.background(Color.primary)` + `.foregroundStyle(Color(uiColor: .systemBackground))`, `Capsule()` | „Pokušaj ponovo" na ekranu greške učitavanja (ostaje, ali u tokene) | 5 |
| `LessonRenderer.swift:282`, `:288` | `Color.primary.opacity(0.08/0.14)` | dugmad scenarija u istraživaču | 8 |
| `LessonRenderer.swift:335` | `Color.primary.opacity(0.14/0.05)` | čipovi figura (§ 9 → izabrani `accent`, ostali `fill`) | 8 |
| `LessonRenderer.swift:342` | `Color.primary.opacity(0.06)` | kartica istraživača figura | 8 |
| `LessonRenderer.swift:476` | `Color.primary.opacity(0.06)` u `Capsule()` | broj figura u `L_PieceRow` | 8 |
| `LessonRenderer.swift:549`, `:553`, `:554` | primary 0.03 / 0.04 / stroke 0.08 | tabela vrednosti (pruge, podloga, ivica) | 8 |
| `LessonRenderer.swift:666`, `:791`, `:913` | `Color.primary.opacity(0.06)` u `Capsule()` | bedževi u karticama vežbi | 8 |
| `LessonRenderer.swift:673`, `:798`, `:920` | `Color.primary.opacity(0.04)` | kartice vežbi (MatePuzzle/Opening/Mate) | 8 |
| `LessonRenderer.swift:770` | `Color.primary.opacity(0.12)` | nedovršena pilula napretka | 8 |
| `ContentView.swift:21` | `TabView` bez konfiguracije pozadine | traka tabova (na iPad-u gore) | 3 (staklena traka ostaje, § 4) |

Ukupno: **2 korena (R1, R2) sa 11 pozivnih mesta + 33 reda u tabeli (55 linija koda).** Nema nijednog
`secondarySystemBackground`, `#1C1C1E`, `#1F1F1F`, `#F2F2F7` literala ni `Color.black` kao podloge —
`#F2F2F7`/`#000000` sa snimaka dolaze iz R1 (`systemGroupedBackground`).
`AnalysisView.swift:39` je već `DS.ground` (ispravno).

Uz to (tekst, ne podloga — za Task 2 pravilo „dugi tekst `ink`"): `.secondary` kao boja teksta
ima 28 pozivnih mesta (LessonRenderer 15, PuzzleView 5, GameView 3, MoveHistoryView 2,
ChessClockView 1, LessonDetailView 1, SettingsSheet 1); `primary.opacity(0.8/0.85/0.9)` kao tekst
8 mesta (LessonRenderer `:395` `L_Para` i `:447` telo `L_Box`, PuzzleView 4, MoveHistoryView 2).

### 1.3 Zakucane hex vrednosti van § 0 izuzetaka

Pregledani su **svi** pogoci (`Color(hex`/`#RRGGBB`/`Color(red:`/`UIColor(red:` na iOS-u;
`Color(0x`/`Color.White|Black|…`/`parseColor` na Androidu). Ispod su samo oni koji NISU izuzetak;
iza tabele je spisak izuzetaka da se vidi da su pregledani.

**Nisu izuzetak (treba rešiti):**

| Platforma | Fajl:linija | Vrednost | Uloga | Predlog / Task |
|---|---|---|---|---|
| iOS | `SquareView.swift:14-15` | `#0E1528` / `#EDF0F7` (`Color.appBackground`) | podloga nav bara | → `DS.ground`/`DS.navBar` (Task 2, R2) |
| iOS | `Views/PuzzleView.swift:478` | `Color(uiColor: .systemBackground)` kao boja teksta | tekst na `Color.primary` dugmetu | → `onAccent` na `accent` (Task 5) |
| iOS | `ChessClockView.swift:175-176` | `Color(uiColor: .systemRed)` | tekst isteklog vremena na BELOJ polovini | polovina je § 0; Android tu ima `#EF4444` (`AND/ui/ChessClockView.kt:319,325`) — razlika u paritetu, ne dirati osim ako Task 9 odluči (sat se ne dira) |
| iOS + AND | `iOS/Views/SquareView.swift:6` `Color.boardBackground #17234f`; `AND/ui/BoardView.kt:168` `Color(0xFF17234F)` | tamnoplava podloga ispod polja (ugao radijusa 6) | sadržaj table; § 5/§ 11 uvode okvir 1 pt u boji tamnog polja — Task 4 (okvir) odlučuje da li ova podloga ostaje |

Ostalo je sve izuzetak ili ide u brisanje (Task 12). **Android nema nijednu zakucanu boju hroma
van izuzetaka** — 6d-1/6d-2 su to već očistile.

**Pregledani izuzeci (§ 0 / CLAUDE.md „namerno fiksno"):**
- Teme table: `iOS/Views/SettingsSheet.swift:32-52` (16 hex), `AND/ui/BoardTheme.kt:11-18` (16 hex).
- Oznake na tabli: iOS `SquareView.swift:75-76,85` (`#EF4444`/`#DC2626` šah), `:96` (`Color.yellow`
  poslednji potez), `:101,104,176,182` (`#00D2FF`); Android `AND/ui/BoardView.kt:577-578,583,592,601-602,633,647`.
- Radijalni gradijent Polarne/Sajbera: iOS `SquareView.swift:153-154` (`#3A475C`/`#4F3B8C`),
  Android `AND/ui/BoardView.kt:514-515` — **Android ga takođe ima** (§ 17 kaže „Android ravna
  boja" — netačno, vidi 1.6); nestaje brisanjem tema (Task 12).
- Sat: iOS `ChessClockView.swift:152,156,163,174,184` (`#8C2525/#121212/#2C2C2E/#FADAD8/#E5E5EA`) i
  `:153-186` `.white`/`.black` tekst; Android `AND/ui/ChessClockView.kt:61` (`ClockBarBackground`),
  `:71` (`ClockBarWell`), `:212` (`Color.Black` koren), `:310-327`.
- Eval traka: iOS `EvalBarView.swift:27-28` (`Color.squareLight`/`Color.boardBackground`), Android
  `AND/ui/EvalBar.kt:42` (`#708090` okvir), `:86-87` (`#F1F5F9`/`#1E293B`). Napomena: **strane eval
  trake se razlikuju između platformi** (iOS `#E9EBDE`/`#17234F`, Android `#F1F5F9`/`#1E293B`) —
  § 0 kaže ne dirati; samo zapisano.
- Zlatna oznaka mata: `AND/ui/UiComponents.kt:156-157,169`.
- Pločica promocije: `AND/ui/PromotionOverlay.kt:47` (`#708090`); iOS `PromotionOverlay.swift:39`.
- Birač boje: `AND/MainActivity.kt:354,357,404-405,416,419`; iOS `GameView.swift:535-541`.
- Konfete: iOS `BoardView.swift:347`, Android `AND/ui/BoardView.kt:799-804`.
- Senke figura/table (`.shadow(color: .black.opacity…)`): iOS `BoardView.swift:134`,
  `PieceImageView.swift:82,100,107,114,171,178,185`, `SettingsSheet.swift:204,265`.
- Stilovi figura koji se BRIŠU (Task 12 uklanja i boje): iOS `PieceImageView.swift:87` (neon
  sjaj `#00d2ff`/`#ff3b30`), `:120-136` + `:158-163` (metal gradijenti); Android metal četkice
  `AND/ui/BoardView.kt:431-450`, `:657-676`, `AND/ui/CapturedPiecesView.kt:52-70`.
- Tokeni sami: `DesignSystem.swift`, `DesignSystem.kt` (definicije).

### 1.4 Zajedničke komponente (§ 18 korak 4) — gde danas žive

Nijedna nije izdvojena kao zajednička na iOS-u; Android ih ima više, ali po ekranu. „Postoji?" =
postoji li JEDNA komponenta koja se može ponovo koristiti.

| Komponenta | iOS danas | Android danas | Postoji? |
|---|---|---|---|
| **Kartica** (`surface` + 1 pt `line`) | nema; ad hoc `.background(DS.surface, in: RoundedRectangle(...))` bez ivice: `AnalysisView.swift:98,118,143`, `StepGameView.swift:215`, `PathView.swift:240,304,347,443`; kartica igrača na `.regularMaterial` (`GameView.swift:454`) | nema; `Card(CardDefaults…DS.surface)` `AND/ui/PathView.kt:153,242`; ad hoc `.background(DS.surface)` `AND/ui/AnalysisView.kt:251,283,350`, `AND/ui/SettingsView.kt:115,210,273,558`, `AND/ui/LearnView.kt:450,514,581,720,872`. **`DS.fill` kao kartica** (§ 3: prelazi u `surface`): nova igra `AND/MainActivity.kt:213,240,267` (fill + `line`), review traka `:1186`, `SettingsToggle` `AND/ui/SettingsView.kt:683`, objašnjenje sata `AND/ui/ChessClockView.kt:819`, lista poteza `AND/ui/MoveHistoryView.kt:78`, protivnik/status u koraku `AND/ui/StepGameView.kt:296,333`, `StatusBanner` `AND/ui/UiComponents.kt:232`, traka datuma `AND/ui/PuzzleView.kt:343` | **ne** — Task 2 uvodi (npr. `DSCard` / `Modifier.dsCard()` + iOS `.dsCard()` modifier) |
| **Dijalog** (`surface`, `Radius.l`, primarno `accent` `Radius.m`, sporedno tekst `accent`) | sistemski `.alert` (`GameView.swift:234,247`, `StepGameView.swift:87`, `SettingsSheet.swift:386`) — „Otkaži" je sistemsko, ne `inkMuted`; kraj partije je scrim preklop sa `Capsule()` dugmadima (`GameView.swift:566`); nova igra je `Menu` u toolbaru (`GameView.swift:177`); birač boje preklop (`:475`) | `Dialog`+`Surface(DS.surface, Radius.l)`: nova igra `AND/MainActivity.kt:185`, birač boje `:302`, kraj partije `:510` (**`RoundedCornerShape(20.dp)`**, nije `Radius.l`); `AlertDialog(containerColor = DS.surface)`: reset `:456`, predaja `:479`, reset statistike `AND/ui/SettingsView.kt:168`, predaja u koraku `AND/ui/StepGameView.kt:261`; sat: `AND/ui/ChessClockView.kt:591`, `:727`. **„Otkaži/Nastavi igru" u `inkMuted`** (krši § 3): `AND/MainActivity.kt:470`, `:493`, `AND/ui/SettingsView.kt:182`, `AND/ui/StepGameView.kt:279`. „Zatvori" u `inkMuted`: `AND/ui/StepGameView.kt:197-201`, `AND/ui/StepPracticeView.kt:97-101` | **ne** |
| **Dugme** (primarno / sporedno sa ispunom `fill`+`ink` / tekst) | ad hoc: primarno `DS.accent` + `RoundedRectangle(Radius.m)` `GameView.swift:284`, `StepGameView.swift:281`, `StepPracticeView.swift:217`, `LessonDetailView.swift:117`, `PuzzleView.swift:408` (`cornerRadius: 12`); sporedno `Color.primary.opacity(0.06)` `PuzzleView.swift:393,426`; „stadion" `Capsule()` `GameView.swift:588,600`, `PuzzleView.swift:479` | ad hoc `Button(colors = ButtonDefaults.buttonColors(containerColor = DS.fill…))`: `AND/MainActivity.kt:610,1289,1323,1346,1403,1428`, `AND/ui/PuzzleView.kt:552,583`, `AND/ui/SettingsView.kt:520,589,621`, `AND/ui/LearnView.kt:548,689,812,1015`, `AND/ui/LessonDetailView.kt:94` | **ne** |
| **Okvir uputstva** (`fill`, `Radius.m`, sijalica `inkMuted`, tekst `ink`) | `PuzzleView.swift:339` `statusCard` (tint `statusColor@12%`); `StepPracticeView.swift:144-160` `statusCard` (isti obrazac + ivica `statusColor@30%`); `StepGameView.swift:222` `statusCard` | `PuzzleStatusBanner` `AND/ui/PuzzleView.kt:495` (puno `accent` — § 7); korak vežbe koristi `StatusBanner` (`AND/ui/StepPracticeView.kt:143` → `AND/ui/UiComponents.kt:227`, `fill`); `StatusCard` `AND/ui/StepGameView.kt:327` | **ne** (tri kopije po platformi) |
| **Kartica igrača / protivnika** | `playerHeader(color:label:capturedPieces:)` `GameView.swift:400` (+ `topHeader :364`, `bottomHeader :375`); protivnik u koraku `opponentCard` `StepGameView.swift:196` | `PlayerHeaderCard` `AND/ui/UiComponents.kt:35` (aktivna ivica **1,5 dp** `accent` — § 5 traži 2); avatar kralja `:72-80` (`getAssetName` → Coil) — mesto za § 17 „prazan avatar"; protivnik u koraku `OpponentCard` `AND/ui/StepGameView.kt:289` | delimično (Android ima komponentu, iOS privatnu funkciju) |
| **Status „Tvoj potez"** (uklanja se, § 5) | `statusMessage` se na iOS-u prikazuje samo u preklopu kraja partije; status reda nema | `StatusBanner` `AND/ui/UiComponents.kt:227` (pozivi `AND/MainActivity.kt:906` portret, `:1128` pejzaž; deli ga i `AND/ui/StepPracticeView.kt:143` — uklanjanje iz Igre ne sme da obriše komponentu dok je korak koristi) | — |
| **Akciona traka** (Vrati · Predaj · Više) | **ne postoji** — radnje su u toolbaru: nova igra `Menu` `GameView.swift:176-197`, predaj `:199-208`, vrati `:209-216`, podešavanja `:220`; „Analiziraj partiju" `analysisButton :274` | `ActionsRow` `AND/MainActivity.kt:1267` (Vrati/Predaj/Podeli/Reset); `AnalysisButton` `AND/ui/UiComponents.kt:328`; `ReviewControlsRow` `AND/MainActivity.kt:1175` | Android da, iOS ne |
| **Lista poteza** | `MoveHistoryView` `iOS/Views/MoveHistoryView.swift:9` (mreža broj·beli·crni već postoji; materijal; izabran `accent.opacity(0.18)` `:125,142`) | `MoveHistoryView` `AND/ui/MoveHistoryView.kt:31` (+ `MovePairCell :159`; `fill` + ivica `line`, radijus 10.dp) | **da**, po jedna na platformi — Task 4 je usklađuje i Task 6 ponovo koristi u Analizi |
| **Oznaka klase poteza** (★ ! ✓ ?! ? ??) | nema; boja po klasi `AnalysisView.swift:153` `color(for:)` (`.best → DS.accent`), čipovi u `moveStrip :127` | nema; `MoveChip` `AND/ui/AnalysisView.kt:345`, `colorFor` `:382` (`BEST → DS.accent`) | **ne** — nova komponenta (Task 4), upotreba Task 6 |
| **Mreža tema / stilova** | `LazyVGrid(columns:)` sa 4 fiksne kolone (`SettingsSheet.swift:68-72`), teme `:178`, stilovi `:243` | teme `LazyRow` `AND/ui/SettingsView.kt:309-313`, stilovi ručna mreža 4 kolone `:402-412` | **ne** — Task 10 (adaptivna 88) |

### 1.5 Mapa brisanja tema i stilova (odluka vlasnika, Task 12)

Brišu se teme **Polarna, Smaragd, Pesak, Sajber** i stilovi **Neonski, Drvene, Metalne, Ravne,
Tanke**. Ostaju teme Klasična/Šumska/Drvo/Ugalj (+ Žalfija, Glina) i stilovi Klasični/Igraonica/
Staklene (+ Reljef, Rez).

**Sačuvane vrednosti koje moraju pasti na podrazumevanu (raw string u podešavanjima):**

| | ključ | obrisane vrednosti | podrazumevano |
|---|---|---|---|
| tema | `boardTheme` | `midnight_aurora`, `sage_emerald`, `warm_terracotta`, `cyber_lavender` | `classic` |
| stil | `pieceStyle` | `neon`, `wood`, `metal`, `flat`, `simpleThin` | `classic` |

Pažnja: **`wood` postoji i kao tema (Drvo — OSTAJE) i kao stil (Drvene — BRIŠE SE)**. Isti string,
različit ključ; grep po `"wood"` mora da se čita.

Skladište: iOS `UserDefaults` (`@AppStorage("boardTheme")`/`("pieceStyle")`; podrazumevane vrednosti
registrovane u `iOS/ViewModels/GameViewModel.swift:203,208`). Android `SharedPreferences`
`"chessko_settings"` (`AND/logic/SettingsManager.kt:10`, čitanje `:25`, `:35`, upis `:71-73`, `:96-98`).

**Pad na podrazumevanu već postoji svuda** (nijedan pad procesa nije moguć):
- iOS: `?? .classic` u `PieceImageView.swift:41,74`, `SquareView.swift:152,202`,
  `BoardView.swift:168,192`.
- Android: `?: BoardTheme.CLASSIC` / `?: PieceStyle.CLASSIC` u `AND/MainActivity.kt:707,710`,
  `AND/ui/PromotionOverlay.kt:68,71`, `AND/ui/PuzzleView.kt:137,140,273,276`,
  `AND/ui/StepGameView.kt:211,214`, `AND/ui/StepPracticeView.kt:147,150`; prečica
  `AND/ui/BoardView.kt:70,82` sa `indexOfFirst == -1` skače na indeks 0 (napred) ili `size-2`
  (nazad) — ne pada.
- Ali: mreža u podešavanjima poredi sirov string (`iOS SettingsSheet.swift:202,256`,
  `AND/ui/SettingsView.kt:314,413`), pa uz obrisanu vrednost **nijedna pločica nije izabrana**.
  Preporuka: pri startu prepisati obrisanu vrednost na `classic` (jedno mesto po platformi:
  iOS `GameViewModel.init` uz `register(defaults:)`, Android `SettingsManager` init).

**iOS:**

| Šta | Gde |
|---|---|
| `enum BoardTheme` slučajevi | `iOS/Views/SettingsSheet.swift:10-13` (`midnightAurora`, `sageEmerald`, `warmTerracotta`, `cyberLavender`), `label` `:23-26`, `lightSquareColor` `:36-39`, `darkSquareColor` `:49-52` |
| radijalni gradijent tamnih polja | `iOS/Views/SquareView.swift:153-160` (cela grana `if !isLight && (… midnightAurora || … cyberLavender)`) |
| „tamna tema" za `flat_outlined` | `iOS/Views/PieceImageView.swift:52` (`isDarkTheme`) i `:54-58` |
| `enum PieceStyle` slučajevi | `iOS/Views/PieceImageView.swift:7-11` (`neon`, `wood`, `metal`, `flat`, `simpleThin`), `label` `:20-24` |
| izbor asseta | `PieceImageView.swift:54-64` (grane flat/simpleThin/neon/wood) |
| crtanje po stilu | `PieceImageView.swift:86-93` (`.neon`), `:95-100` (`.wood`), `:116-171` (`.metal`, gradijenti), `:173-185` (`.flat`, `.simpleThin`) |
| `#Preview` | `PieceImageView.swift:~190-196` koristi samo `ChessPiece`, bez stila — ostaje |
| prečica prevlačenjem | `iOS/Views/BoardView.swift:166-212` — iterira `allCases`, ne treba izmena (sama se skraćuje) |
| mreža u podešavanjima | `SettingsSheet.swift:178-210`, `:243-270` — iterira `allCases` |
| asseti (54 imageset-a) | `iOS/Assets.xcassets/piece_{white,black}_{king,queen,rook,bishop,knight,pawn}_{neo,wood,flat,simple_thin}.imageset` (48) + `piece_black_*_flat_outlined.imageset` (6). Metalne **nemaju** sopstvene assete (koriste `piece_*_*` klasične + gradijent). Asset katalog je jedna referenca u `project.pbxproj` (0 pogodaka za imageset), pa brisanje foldera NE traži izmenu pbxproj-a |
| prevodi | `Localizable.xcstrings` ključevi `Polarna`, `Smaragd`, `Pesak`, `Sajber`, `Neonski`, `Drvene`, `Metalne`, `Ravne`, `Tanke` (svaki sa 8 jezika); izvor u `build_localizations.py:94-97` (Neonski, Drvene, Metalne, Ravne), `:325-328` (Polarna…Sajber), `:343` (Tanke). Ključevi nemaju drugog pozivaoca (grep). Pažnja: `build_localizations.py` regeneriše ceo katalog (diff ~28.000 linija, CLAUDE.md) — bezbednije je obrisati 9 ključeva iz `.xcstrings` skriptom nad JSON-om **i** obrisati iste `add(...)` redove iz `.py`, da ostanu usklađeni |

**Android:**

| Šta | Gde |
|---|---|
| `enum class BoardTheme` | `AND/ui/BoardTheme.kt:15-18` (`MIDNIGHT_AURORA`, `SAGE_EMERALD`, `WARM_TERRACOTTA`, `CYBER_LAVENDER`) |
| radijalni gradijent | `AND/ui/BoardView.kt:514-521` |
| `isDarkTheme` / `flat_outlined` | `AND/ui/BoardView.kt:765-774` (`getAssetName`) |
| `enum class PieceStyle` | `AND/ui/PieceStyle.kt:8-12` (`NEON`, `WOOD`, `METAL`, `FLAT`, `SIMPLE_THIN`) |
| izbor asseta | `AND/ui/BoardView.kt:768-777` |
| metal četkica (3 kopije) | `AND/ui/BoardView.kt:431-450` (prevlačena figura), `:657-676` (figura na polju), `AND/ui/CapturedPiecesView.kt:52-70` |
| prečica prevlačenjem | `AND/ui/BoardView.kt:61-91` `applyBoardSwipe` — iterira `values()`, ne treba izmena |
| mreža u podešavanjima | `AND/ui/SettingsView.kt:313` (teme), `:402-447` (stilovi) — iteriraju `values()` |
| komentar koji pominje stil | `AND/ui/BoardView.kt:322` (`pieceStyle` metal → …, istorijski zapis merenja — može ostati) |
| asseti (54 SVG) | `ChesskoAndroid/app/src/main/assets/pieces/piece_*_{neo,wood,flat,simple_thin}.svg` (48) + `piece_black_*_flat_outlined.svg` (6). Ukupno 90 SVG-a → posle brisanja 36 (12 klasičnih + 12 glass + 12 gameroom) |
| prevodi (`AND/logic/Loc.kt`) | `"Polarna"` `:270`, `"Smaragd"` `:330`, `"Pesak"` `:260`, `"Sajber"` `:317`, `"Tanke"` `:357`. **`Neonski`/`Drvene`/`Metalne`/`Ravne` NEMAJU unos** (zato su na ruskom bili srpski). `loc(theme.label)`/`loc(style.label)` je dinamičan (`AND/ui/SettingsView.kt:345,447`), pa `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary` ove ključeve ne vidi — brisanje ne obara test |
| testovi | nijedan JVM/instrumentisani test ne pominje obrisane teme/stilove (`"neon"` u `LessonContentTest.kt:178` je stil `box` bloka, `"Pesak"` u `LessonContentTest.kt:54` je tekst lekcije — nisu vezani) |

Posle brisanja **„Klasični" je jedini preostali stil bez Android prevoda** (`Loc.kt` nema
`"Klasični"`; iOS katalog ga ima na 8 jezika, `build_localizations.py:93`) — Task 12 ga dodaje u
`Loc.kt` sa prevodima iz iOS-a. iOS katalog već ima ruske prevode za sve stilove.

CLAUDE.md pominje „8 tema / stilovi" na više mesta (Dizajn sistem, Changelog) — ažurira Task 13.

### 1.6 Neslaganja specifikacije i koda

1. **§ 12/§ 16 „5 stilova neprevedeno na ruski"** — važi samo za Android (`Loc.kt` nema unose).
   iOS katalog ima ruski za sve (`Классические`, `Неоновые`, …). Posle brisanja ostaje samo
   „Klasični" na Androidu.
2. **§ 12 „Android: uvoz u VectorDrawable"** — Android figure NE koriste VectorDrawable: crtaju
   se kao SVG iz `assets/pieces/` preko Coil-a (`io.coil-kt:coil-svg:2.6.0`, već zavisnost;
   `model = "file:///android_asset/pieces/$assetName.svg"` u `AND/ui/BoardView.kt:456,721`,
   `CapturedPiecesView.kt:92`, `PromotionOverlay.kt:133`, `SettingsView.kt:438`,
   `UiComponents.kt:80`, `LearnView.kt:481`). Novi stilovi se dodaju kao SVG fajlovi
   `piece_{white|black}_{type}_{sufiks}.svg` (preimenovanje `wK.svg` → `piece_white_king_…svg`) i
   jedna grana u `getAssetName` (`AND/ui/BoardView.kt:762`). Coil-svg koristi AndroidSVG, koji
   podržava `clipPath` — konverzija verovatno nije potrebna. Predlog Task-u 12: SVG u asset (paritet
   sa iOS-om, nijedna nova zavisnost), VectorDrawable samo ako Coil ne iscrta `clipPath` (proverljivo
   tek na uređaju — Task 13 / ponovno snimanje). Iste SVG datoteke idu i u iOS asset katalog.
   (`figure-C-reljef/wK.svg` ima 1 `clipPath`.)
3. **§ 17 „iOS radijalni gradijent, Android ravna boja"** — netačno: Android ima isti gradijent
   (`AND/ui/BoardView.kt:514-521`). Brisanjem Polarne i Sajbera pitanje nestaje na obe.
4. **§ 6 „ikona prelomnog poteza: iOS danas `danger`"** — obe platforme boje ikonu po klasi
   poteza (iOS `AnalysisView.swift:106` `color(for:)`, Android `AND/ui/AnalysisView.kt:293`
   `colorFor`), pa je za promašaj `danger` na obe. Task 6 menja na `warning` na obe.
5. **§ 4 „iOS već pokriva ceo ekran (Analiza)"** — Analiza je na iOS-u `.sheet`
   (`GameView.swift:261`), sat je `.fullScreenCover` (`:258`), Podešavanja `.sheet` (`:255`). Na
   iPhone-u sheet ne ostavlja tab bar vidljivim, pa namera važi; samo napomena.
6. **`DS.navBar` na iOS-u ima 0 upotreba** — § 4 (app bar u Lekciji) ga prvi put koristi.
7. **iOS nema `Type.label`** (vidi 1.1).
8. **`BoardWidthCapTest` (`ANDT/BoardWidthCapTest.kt:55-64`) zabranjuje `DS.maxBoardSide` u
   lekcijskim fajlovima** — § 9/§ 13 traže da i lekcijska tabla ide kroz `boardSide`. Task 8/11
   moraju svesno izmeniti test (pravilo iz global constraints), ne zaobići ga.
9. **Android „Pokušaj ponovo" tokom rešavanja** je `AND/ui/PuzzleView.kt:600` (u
   `PuzzleActionsRow`); `:130` i `:266` su ekrani greške učitavanja — ostaju.
10. **Množine** — nijedna platforma nema mehanizam: 0 `.stringsdict`, 0 `"variations"` u
    `Localizable.xcstrings`, `Loc.kt` bez množine (CLAUDE.md, Faza 7). Task 2 ga uvodi.
11. **Kartica igrača na Androidu ima aktivnu ivicu 1,5 dp** (`AND/ui/UiComponents.kt:63`), ne „bez"
    — § 5 svejedno traži 2 pt na obe.
12. **iOS ikona taba Put je `signpost.right.fill`** (`ContentView.swift:33`) — spec ovo navodi tačno.
    `point.topleft.down.to.point.bottomright.curvepath` je iz SF Symbols 4 (iOS 16+), a cilj je
    iOS 18, pa bi trebalo da postoji — Task 3 proverava buildom (nepostojeće ime ne obara build,
    nego crta prazno — proveriti `UIImage(systemName:) != nil` ili u Xcode SF pregledu).
13. **Snimci „Igra na iOS-u" bez akcione trake** — iOS danas radnje drži u toolbaru (vidi 1.4), pa
    § 5 „Na iOS-u Vrati prelazi iz zaglavlja u traku" znači uvođenje cele trake, ne samo pomeranje.

### 1.7 Plan po fazama i rizici

| Task | Sekcije | Glavna mesta | Rizici |
|---|---|---|---|
| 2 | § 2, § 3 | `DesignSystem.swift`/`.kt` vrednosti; R1/R2 iz 1.2; `SettingsSheet` `List` → `.scrollContentBackground(.hidden)` + `DS.ground`; kartica/dijalog/dugme komponente; `Font.dsLabel`; množine | `ContrastTest` pinovi (`ANDT/ContrastTest.kt:183-211` `knownSubAAPairsDoNotGetWorse` — sa novim vrednostima `inkMuted/ground` 5,28, `inkMuted/fill` 4,86, `warning/surface` 5,72 … parovi prelaze AA i izlaze sa spiska; `:599-611` `lessonBoxIconTint…` pin 2,936 menja se); iOS `List` u sheet-u bez sistemske pozadine traži `listRowBackground(DS.surface)` po redu; množine: iOS String Catalog plural varijante ručno u JSON-u vs. `build_localizations.py` koji regeneriše katalog — odlučiti koji je izvor; Android `Loc` množina = nov API + `LocTest` mora da ga razume |
| 3 | § 4, § 10 | Android `AND/MainActivity.kt:172/438/447` (`Dialog(usePlatformDefaultWidth=false)`) → celoekranski; donja traka `:659-` (`Row` visine 68, bez inseta); `NavigationRail`; app bar Lekcije `AND/ui/LessonDetailView.kt:~100-122`; ikone (`ContentView.swift:24,29,33`; Android `BottomNavItem` `AND/ui/UiComponents.kt:249`, poziv `AND/MainActivity.kt:672`) | custom SF symbol se ne može napraviti iz CLI-ja → SVG kao template vektor + `TODO(design § 10)`; nov asset u `project.pbxproj` NE treba (katalog); `route` vektor ručno u `res/drawable`; AppIcon katalog: tri varijante (Any/Dark/Tinted) u `Contents.json` |
| 4 | § 18 k4, § 5 | komponente iz 1.4; iOS uvodi akcionu traku (danas toolbar); Android `ActionsRow`, `PlayerHeaderCard`, `StatusBanner` (uklanja se) | najveći task; iOS `GameView.swift` (650 linija) i Android `MainActivity.kt` (1448) imaju portret+pejzaž kao dve grane — svaka izmena dva puta; „Više" meni sa Analizom menja put do Analize (CLAUDE.md: dugme „Analiziraj partiju" u obe grane); okvir table 1 pt vs. postojeća `#17234F` podloga + radijus 6 |
| 5 | § 7 | `PuzzleView` obe, `StepPracticeView`, `StepGameView`; uklanjanje `AND/ui/PuzzleView.kt:600` | iOS raspored na Androidu = prelazak Android trake datuma (`DateNavigationRow` `:338`) u zaglavlje; „ništa centrirano po visini" sudara se sa postojećim `GeometryReader`+`Spacer` centriranjem iz Faze 1 (iOS) |
| 6 | § 6, § 17 | `iOS/Views/AnalysisView.swift`, `AND/ui/AnalysisView.kt`, `Models/MoveAnalysis.swift`/`models/MoveAnalysis.kt` | tačnost „0,0 %": prvo test koji reprodukuje (§ 17), formula je deljena i pokrivena sa 26/27 testova — izmena formule menja pinove; „−2,3" zahteva lokalni format + rečenicu sa množinom („dva piona") |
| 7 | § 8 | `PathView` obe | množine iz Task 2; Android `PathView.kt` mora da dobije iOS raspored (veći prepis) |
| 8 | § 9 | `LessonRenderer`/`LessonDetailView` obe, `AND/ui/LearnView.kt` (`LBox` `:319`, `LSectionHeader` `:389`), `SYMBOL_TO_GLYPH` (`AND/ui/LessonRenderer.kt`) i iOS SF imena | emoji → Unicode figure sa U+FE0E: Android `LessonGlyph` ima `Icon/Emoji/Unknown`; Unicode figura u boji `ink` traži treći slučaj ili `Text` sa `color` (CLAUDE.md „Lekcijski glifovi": VS-15 glif PRIMA boju, `⚠︎` primer); `ContrastTest.lessonBoxIconTint…` pin se menja (nova kutija nad `surface`); `BoardWidthCapTest.lessonBoardsAreNotCapped` (1.6/8) |
| 9 | § 14 | `ChessClockView` obe | polovine i Android traka se NE diraju; iOS traka `:324` postaje fiksno tamna; `tnum` na Androidu = `fontFeatureSettings`, bez zavisnosti |
| 10 | § 15 | `SettingsSheet.swift`, `AND/ui/SettingsView.kt` | Android „Sistem" jezik: `AppCompatDelegate` traži `appcompat`, koji **NIJE** zavisnost (grep `build.gradle.kts`/`libs.versions.toml` = 0) — ne dodavati, rešiti postojećim `Loc`/`SettingsManager` mehanizmom (plan); iOS danas sortira jezike `localizedCompare` po endonimu (`SettingsSheet.swift:307-308`), što zavisi od lokala — spec traži fiksan redosled; redosled grupa kao iOS = premeštanje sekcije Jezik sa vrha (`SettingsView.kt:106`) na šestu poziciju |
| 11 | § 13, § 17 | `boardSide` funkcija (nova, čista, testirana), svi ekrani sa tablom (`DS.maxBoardSide` mesta iz 1.1: iOS `GameView.swift:35,105`, `StepGameView.swift:174`, `StepPracticeView.swift:84`, `PuzzleView.swift:296`; Android `AND/MainActivity.kt:749,1026`, `AND/ui/PuzzleView.kt:74,292`, `AND/ui/StepPracticeView.kt:167`, `AND/ui/StepGameView.kt:231`) | `BoardWidthCapTest` (proverava string `DS.maxBoardSide` u fajlu); prazan avatar „Ti" — Android avatar `AND/ui/UiComponents.kt:72-80` (Coil, `getAssetName`); CLAUDE.md pominje snimak `44_…5b` sa pozicijom bez belog kralja (stanje nepoznatog porekla) — mogući uzrok nije u avataru nego u stanju |
| 12 | § 11, § 12 + brisanje | mapa 1.5 | `wood` dvoznačnost; brisanje 54+54 asseta; Android SVG kroz Coil (1.6/2); prevodi: obrisati 9 ključeva na iOS-u bez regeneracije kataloga; nove teme i stilovi na 8 jezika |
| 13 | § 16, § 17, § 19 | ruski „Внешний vuд доски" (`build_localizations.py:329`, `Loc.kt:133`, `Localizable.xcstrings`); izveštaj | jedini task sa emulatorom; ažuriranje CLAUDE.md (paleta, broj tema/stilova, testova) |

Opšti rizici:
- **Bez simulatora/emulatora do Task-a 13** — vizuelne greške (preklapanje, preliv u de/hi/ru) neće
  se videti pre ponovnog snimanja; svaki task treba da drži izmene unutar postojećih rasporeda.
- Kartica = `surface` + `line` na `ground`: `line/ground` je 1,16 / 1,32 (dekorativno, spec to
  prihvata); `ContrastTest.settingsCardsSeparateFromGround` ostaje relevantan.
- iOS `.alert` je sistemski dijalog i ne može dobiti `surface`/`Radius.l`/accent dugmad; § 3
  „jedan izgled dijaloga" na iOS-u znači sopstveni preklop umesto `.alert` (predaja, napuštanje
  partije, reset statistike) — odluka za Task 2/4.

---

## Task 2 — Tokeni i globalna pravila (§ 2, § 3)

### Urađeno po stavkama

**§ 2 Tokeni (obe)**
- [x] `inkMuted` svetla `#6B7280 → #5E6573`, `warning` svetla `#B07D2B → #865F21` —
  `iOS/Views/DesignSystem.swift`, `AND/ui/theme/DesignSystem.kt`. Tamna tema netaknuta.
- [x] `ContrastTest` (svesno izmenjen, ne ugašen): `knownSubAAPairsDoNotGetWorse` sada drži samo
  `success/fill` (4,184…, jedini preostali sub-AA par); pet parova koji su prešli AA
  (inkMuted/ground 5,28, inkMuted/fill 4,86, warning/surface 5,72, warning/fill 4,75,
  warning/ground 5,16) tvrdi nov test `designReviewTokensLiftFormerSubAAPairsAboveAA` (prag 4,5,
  obe teme, plus „bela na warning" i dve tačne vrednosti iz spec tabele). Pin
  `lessonBoxIconTint…` 2,936 → prag 3,0 (sada ~4,52 nad ground-tintom). Svetla `warning/ground`
  dodata u `lessonBoxStylesMeetAA`. ContrastTest 18 → 19.
- [ ] „Izvedene boje" (kutija pravila `warning` 10 % preko `surface`, izabran red `accent` 12/16 %)
  — primena je u ekranskim taskovima (§ 9 Task 8, § 5/§ 6 Task 4/6).

**§ 3 Globalna pravila**
- [x] **Podloga `DS.ground` (iOS).** Koren R1: `AppBackgroundView` (`iOS/Views/SquareView.swift`)
  sada crta `DS.ground` (6 ekrana: Igra, Zadaci, Lekcija, Put, koraci partija/vežba). R2:
  `Color.appBackground` (#EDF0F7) **obrisan**, 5 `.toolbarBackground` poziva → `DS.ground`.
  Sat koren `systemBackground` → `DS.ground`. Podešavanja: `.scrollContentBackground(.hidden)` +
  `.background(DS.ground)` + `.listRowBackground(DS.surface)` + `.listRowSeparatorTint(DS.line)`
  na svakoj sekciji + nav bar `DS.ground`. Info ekran sata i kalendar Zadataka (sheet-ovi bez
  pozadine) → `.background(DS.ground)`. Grep posle: 0 `systemBackground`/`systemGroupedBackground`
  u `iOS/`. iPad (crni sadržaj ispod trake) rešen istim korenom.
- [x] **Kartica = `surface` + 1 pt `line`.** Nova komponenta na obe: iOS `View.dsCard(radius:)`
  (`DesignSystem.swift`), Android `Modifier.dsCard(radius)` + `DSCardGroup` + `DSRowDivider`
  (`AND/ui/theme/Components.kt`, nov fajl). Primenjeno na sve postojeće ad hoc `surface` kartice:
  iOS `AnalysisView` (2), `PathView` (4), `StepGameView` (1); Android `SettingsView` (4 kartice).
  Komentari „ivica uklonjena (1,285)" iz 6d-2 su obrisani — spec tu presudu obrće (ivica je
  dekorativna, 1,16/1,32 nad ground, i namerno ne prelazi 3:1). iOS `List` sekcije u Podešavanjima
  su `surface` redovi bez ivice (inset grouped lista ne prima stroke) → Task 10.
- [x] **`fill` nije kartica** — Android prekidači u Podešavanjima više nemaju `fill` podlogu; sedam
  `SettingsToggle` redova je u `DSCardGroup` grupama sa `line` razdelnikom (izgled table, stil
  figura, igra i pravila, efekti). **Neurađeno ovde (ekranski taskovi):** opcije „Nova igra"
  (`AND/MainActivity.kt` ~213/240/267 — redovi su već unutar `surface` dijaloga, Task 4), lista
  poteza (`AND/ui/MoveHistoryView.kt:78` — zamena na `surface` bi ubila pruge neparnih redova koje
  su `surface`, Task 4 je redizajnira), objašnjenje kontrola sata (`AND/ui/ChessClockView.kt:819`,
  iOS `ChessClockView.swift:516`, Task 9).
- [x] **Grupe:** Android Podešavanja `spacedBy(20.dp)` → `DS.Space.xl`; naslov → grupa `Space.s`
  (već bilo). iOS `List` razmaci → Task 10.
- [x] **`accent` samo za tapabilno/izabrano** (u meri globalnog pravila): statični brojevi u
  statistici (Uspešnost, Rešeno zadataka, Rejting) `accent → ink` na obe; Android „+N" materijalna
  prednost `accent@85% → inkMuted` (iOS već `inkMuted`). Ostali `accent` naslovi/vrednosti su po
  ekranima (Analiza, Put… — Task 6/7).
- [x] **Naslovi sekcija `Type.heading` bold `ink`.** iOS `DSSectionHeader` + `Font.dsSectionHeading`
  (= headline bold; `dsHeading` ostaje semibold); Android `DSSectionHeader` (`Type.heading` +
  `FontWeight.Bold`). Primenjeno: iOS Podešavanja (svih 8 `Section(Loc(…))` → `header:`
  `DSSectionHeader`, `textCase(nil)`), iOS kalendar „Rešeni zadaci" (`secondary → ink bold`);
  Android `SettingsSection` (bio `accent` body) i naslov kategorije u info dijalogu sata
  (`accent → ink`). Odluka: token `Type.heading`/`dsHeading` NIJE promenjen na bold (koristi se i
  za naslove kartica) — bold se dodaje na mestu naslova sekcije.
- [x] **Dugi tekst `ink`.** iOS: `L_Para`, telo `L_Box`, telo bullet-a i `L_NumberedRule`
  (`LessonRenderer.swift`, bili `.primary.opacity(0.85)`/`.secondary`), opis vremenske kontrole u
  info ekranu sata, tekst sporednih dugmadi u Zadacima i lista poteza (`primary.opacity(0.8–0.9)`).
  Svaki preostali `.secondary` tekst u `iOS/Views/` (meta: podnaslovi, natpisi, hint, status „u
  toku") → `DS.inkMuted`, svaki `.primary` tekst → `DS.ink` — iOS `Views/` više nema sistemsku boju
  teksta. Android: tekst u telu dijaloga (napusti/predaj/reset statistike, 4 mesta) `inkMuted → ink`.
- [ ] **Status ne izgleda kao dugme/polje** — ekranski (§ 5 Task 4, § 7 Task 5).
- [x] **Dijalozi.** Android: svih 6 `AlertDialog`/`Dialog` na `surface` + `Radius.l` (kraj partije
  bio `RoundedCornerShape(20.dp)`); „Otkaži"/„Nastavi igru" (4) i „Zatvori" u koracima (2)
  `inkMuted → accent`. **Nigde „stadion":** M3 `Button` je podrazumevano `CornerFull` (nije iz
  `MaterialTheme.shapes`), pa je 14 `Button`-a bez sopstvenog oblika dobilo `shape = ButtonShape`
  (`Radius.m`); 20 je već imalo eksplicitan oblik (10.dp/`Radius.m`/`Radius.s` — ekranski taskovi).
  Pomoćnici `DialogShape`, `ButtonShape`, `dsPrimaryButtonColors`, `dsSecondaryButtonColors` u
  `Components.kt` (za Task 4+). iOS: sistemski `.alert` ostaje (odluka kontrolora) — sve četiri
  imaju `.destructive` + `.cancel` uloge (provereno); `Capsule()` dugmad → `Radius.m`
  (kraj partije ×2, „Pokušaj ponovo" na grešci učitavanja, koji je uz to sa `primary` +
  `systemBackground` prešao na `accent` + `onAccent`). Sopstveni preklop kraja partije
  (scrim, ne `surface`) je Task 4.
- [x] **Brojevi kroz lokalni format.** Nov `LocalFormat` na obe (`iOS/Logic/LocalFormat.swift`,
  `AND/logic/LocalFormat.kt`): `LocalNumber.decimal/percent` po jeziku APLIKACIJE (srpski →
  `sr-Latn`); omotači iOS `LocDecimal/LocPercent`, Android `locDecimal/locPercent/locInteger`.
  Primenjeno: tačnost u Analizi (obe; Android je imao `Locale.US` namerno — spec to obrće, „0,0 %"),
  uspešnost u statistici (obe), ocena u eval traci i pilulama igrača (Android; iOS nema tekstualnu
  ocenu), broj niza na Putu (Android; iOS je već formatirao).
- [x] **Množine.** Nov mehanizam na obe, ista pravila (`PluralRules.category`, CLDR celobrojno:
  sr/ru one-few-other/many, en/de/it one-other, fr/hi 0–1 one, zh other). iOS `LocPlural(base, n)`
  čita ključ `<osnova>#one|few|many|other` iz kataloga; Android `locPlural(key, n)` čita
  `Loc.pluralDictionary`. Migrirano: niz na Putu („1 Dana zaredom" — upravo primer iz spec-a →
  „1 dan zaredom / 2 dana / 5 dana") i broj mogućih poteza u istraživaču figura („2 mogućih poteza"
  → „2 moguća poteza"). Stari ključevi `Dana zaredom`, `1 mogući potez`, `%lld/%d mogućih poteza`
  obrisani (katalog, `build_localizations.py`, `Loc.kt`). Ostali `%lld` ključevi nisu broj+imenica
  sa slaganjem („Korak %lld", „Zadatak %lld od %lld", „Poteza: %d" kao natpis) ili nemaju pozivaoca
  (`%lld lekcije od osnova…`, `%lld/%lld poteza` — iOS `progressLabel` se nigde ne prikazuje).

### Odluke
- **Množine: sopstvena pravila, ne String Catalog plural varijante / `.stringsdict`.** Razlog:
  `Loc()` ide kroz zamenjen `Bundle.main` (`LocalizationManager`); da li `.stringsdict` proksi
  preživi taj put i da li `String(format:)` bez lokala bira varijantu ne može se dokazati bez
  simulatora (zabranjen do Task-a 13). Ovako je izbor kategorije čista funkcija pokrivena testovima
  na obe platforme, a ključevi su obični stringovi (8 jezika, `LocalFormatTests` proverava da
  svaka osnova ima sva 4 ključa). `build_localizations.py` dobio `add_plural()`; katalog je
  dopunjen hirurški (8 unosa), bez regeneracije (izbegnut diff od ~28.000 linija) — generator i
  katalog daju iste vrednosti (provereno skriptom).
- `Font.dsLabel` (11 pt, `caption2`) uveden na iOS-u (Task 1, 1.1) — još bez pozivnih mesta.
- `Type.heading` ostaje semibold kao token; bold je na naslovu sekcije (`DSSectionHeader`).
- `Color.appBackground` obrisan umesto da postane alias — jedno ime za jednu boju.

### Novi stringovi (prevod nesiguran)
- `dana zaredom#…` — `zh-Hans` zadržan postojeći „连续天数" za sve kategorije (kineski nema množinu;
  uz broj ispred čita se „5 连续天数", isto kao do sada); `hi` „लगातार दिन" za one i other (imenica
  se ne menja). Nove forme za fr/de/it/en („jour/jours d'affilée", „Tag/Tage in Folge", „giorno/
  giorni di fila", „day/days in a row") i ru („день/дня/дней подряд") — gramatički sigurne, ali bez
  izvornog govornika.
- `%lld mogućih poteza#…` — ru few/many („возможных хода/ходов") nove.

### Neurađeno / za kasnije taskove
- iOS Podešavanja: ivica `line` oko `List` grupa, razmaci grupa, ivice pločica tema/stilova
  (`Color.primary.opacity(0.12/0.10)`) → Task 10.
- `Color.primary.opacity(…)` kao PODLOGA (tabela 1.2: `GameView`, `MoveHistoryView`,
  `LessonRenderer`, `ChessClockView`, `PuzzleView`) — ekranski taskovi 4/5/8/9, nisu dirani.
- Android sat „Poteza: $moves" (`AND/ui/ChessClockView.kt:361`) nije kroz `loc` — nađeno usput,
  Task 9 (iOS ima ključ „Poteza: %d").
- Tekst dijaloga „Da li ste sigurni…" (obraćanje „Vi") — § 16, Task 13.

## Task 3 — Navigacija i hrom (§ 4) + ikone (§ 10)

_(popunjava Task 3)_

## Task 4 — Zajedničke komponente + Igra (§ 18 korak 4, § 5)

_(popunjava Task 4)_

## Task 5 — Zadaci i koraci Puta (§ 7)

_(popunjava Task 5)_

## Task 6 — Analiza partije (§ 6, § 17 tačnost)

_(popunjava Task 6)_

## Task 7 — Put (§ 8)

_(popunjava Task 7)_

## Task 8 — Lekcija (§ 9)

_(popunjava Task 8)_

## Task 9 — Šahovski sat (§ 14)

_(popunjava Task 9)_

## Task 10 — Podešavanja (§ 15)

_(popunjava Task 10)_

## Task 11 — Tablet (§ 13, § 17 avatar)

_(popunjava Task 11)_

## Task 12 — Teme table i stilovi figura (§ 11, § 12 + brisanje)

_(popunjava Task 12)_

## Task 13 — Lokalizacija, provere i završni izveštaj (§ 16, § 17, § 19)

_(popunjava Task 13)_
