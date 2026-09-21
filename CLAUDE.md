# CLAUDE.md — Chessko

> Ovaj fajl uvek pročitaj na početku rada i ažuriraj na kraju svakog prompta
> (sekcija "Changelog" + relevantne sekcije ako se arhitektura promeni).

## Šta je aplikacija

**Chessko** — iOS aplikacija: šah protiv računara (AI), sa dodatnim modovima
(igra sa prijateljem na istom uređaju, samostalni šahovski sat). Igrač bira
boju (beli ili crni; podrazumevano beli) — tabla se rotira kad igra crnim.
UI je lokalizovan na 8 jezika (izvorni srpski). `ContentView` je `TabView`
sa tri taba: Igra, Zadaci (dnevni puzzle), Put (kurikulum).

- Platforma: **iOS 18.0+**, iPhone + iPad (`TARGETED_DEVICE_FAMILY = 1,2`)
- Jezik: **Swift 6.0**, **SwiftUI**
- Bundle ID: `com.veljkoni.chessko`
- Xcode projekat: `Chessko.xcodeproj` (target `Chessko`)
- Git repo (grana `v2/faza-0`); jedna eksterna SPM zavisnost (`chesskit-engine`,
  za Stockfish); testira se kroz odvojeni SwiftPM paket (vidi „Testovi").

## Build / Run

```bash
# Build (simulator)
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```
Najlakše: otvoriti `Chessko.xcodeproj` u Xcode i pokrenuti (⌘R).
Pomoćne skripte u rootu: `create_xcode_project.py` (generiše pbxproj),
`extract_pieces.py` (seče `design-reference.svg` u SVG figure za asset katalog).

> **UPOZORENJE:** `create_xcode_project.py` se **više ne sme pokretati**.
> Regeneriše ceo `project.pbxproj` iz nule i ne zna za `chesskit-engine`
> SPM zavisnost — pokretanje obara Stockfish. `project.pbxproj` se od
> 2026-07-03 održava ručno.

## Testovi

Motor se testira kroz SwiftPM paket u korenu (`Package.swift`), nezavisno
od Xcode projekta — kompajlira postojeće izvorne fajlove po putanji, bez
kopiranja.

```bash
swift test              # ceo skup
swift test --filter Perft
```

Pokriveno (**84 testa**): 7 perft testova za svih 6 standardnih pozicija (uključujući
početnu do dubine 5, 4.865.609 čvorova, ~85s), 4 testa prava rokade (uzimanje
topa na sva 4 ugla, i partija bez topa koja i dalje nosi zastarelo pravo),
14 testova `PuzzleRepository`-ja (uključujući dva koja prolaze **celu** bazu —
vidi ispod), 5 testova Elo rejtinga, 4 testa sadržaja lekcija (dekodiranje
svih 12 tipova blokova, round-trip, glasan pad na nepoznat tip, i prolaz kroz
sve lekcijske JSON-e), 7 testova kurikuluma (uključujući onaj koji tvrdi da
kurikulum ne laže — svaka lekcija koju pominje postoji, svaka tema ima dovoljno
zadataka u opsegu), 17 testova napretka (`ProgressStore`, dnevni cilj, streak) i
26 testova analize partije (`MoveAnalysisTests` — matematika ocena i klasa poteza,
plus parser UCI ocene). `Chessko/TestSupport/LocShim.swift`
postoji samo zbog paketa i zaštićen je `#if CHESSKO_ENGINE_PACKAGE` — u
aplikaciji se ne kompajlira.

Dva testa integriteta prolaze svih 20.000 zadataka deterministički, po `id`:
`everyPuzzleInDatabaseHasValidFenAndAtLeastOneMove` (FEN se parsira, lista
poteza nije prazna, broj pročitanih redova = `COUNT(*)`) i
`everyPuzzleFirstMoveParsesInItsOwnPosition` (prvi UCI potez se razrešava u
svojoj poziciji). Oba postoje zato što uzorak od 500 redova hvata pokvaren
red u ~2,5% pokretanja — praktično nikad.

## Baza zadataka

`Chessko/puzzles.sqlite` (7,0 MB, commit-ovan) — **20.000 zadataka iz Lichess
baze, licenca CC0**. Aplikacija od Faze 2 nema nijedan mrežni poziv za
zadatke; radi u avionskom režimu.

**Od Faze 6a istu bazu deli i Android**, bajt-identično (`sha256` počinje sa
`3cd00a83`) — `ChesskoAndroid/app/src/main/assets/puzzles.sqlite`. Nikad se ne
regeneriše po platformi: deljenje iste datoteke je ono što drži dve aplikacije
na istim zadacima i istim rejtinzima. Dve stvari koje Android traži a iOS ne:

- **`androidResources { noCompress += "sqlite" }`** u `app/build.gradle.kts`.
  AAPT po podrazumevanom spisku kompresuje `.sqlite`, a kompresovan asset se ne
  može otvoriti preko `openFd()` — sve puca na konstrukciji repozitorijuma.
  Provera: `unzip -v app-debug.apk | grep puzzles.sqlite` mora reći `Stored`.
- **Kopiranje iz `assets` u `filesDir` pri prvoj upotrebi**, jer `SQLiteDatabase`
  traži pravu putanju na disku. Isti obrazac koji `StockfishEngine` koristi za
  `.nnue`, uz dopunu da se kopija preskače samo ako ima i očekivanu veličinu —
  polovična kopija (pun disk, ubijen proces) inače ostaje zauvek.

Čita se kroz `ChesskoAndroid/.../logic/PuzzleRepository.kt` (`android.database.sqlite`,
sistemski — **bez ijedne nove Gradle zavisnosti**).

> **Generator je ZAMRZNUT.** `build_lesson_json.py` je prenео lekcije iz Swift-a u JSON
> tako što je prevode vadio iz `Localizable.xcstrings`. Task 6 iste faze obrisao je baš te
> ključeve, pa generator više **ne može da se pokrene** — i to jasno kaže ako se pokuša.
> **Izvor istine su od Faze 3 sami JSON fajlovi**; lekcija se menja tako što se uredi
> `Chessko/Content/lessons/<id>.<jezik>.json`. Skripta ostaje kao zapis kako je migracija
> izvedena i koji je blok došao sa kog mesta u starom `LessonDetailView.swift`.

- **Filter kvaliteta**: rejting 600–2200, `NbPlays >= 200`, `Popularity >= 90`,
  `RatingDeviation <= 80`. Propušta ~31% baze.
- **Uzorak je stratifikovan**, 8 opsega rejtinga × 2.500. Naivnih „prvih
  20.000" dalo bi premalo lakih zadataka — raspodela Lichess baze je nagnuta
  ka 1400–1800, a opseg 600–799 je najtanji. Unutar opsega se bira tako da
  svaka tema bude zastupljena (73 teme, najređa ima 137 zadataka).
- **Determinističko**: `random.Random(seed)`, ponovno pokretanje daje istu bazu.
- **`puzzle_themes` je razložena tabela** i postoji samo radi filtriranja po
  temi. Bez nje bi upit morao da radi `LIKE '%mate%'` nad tekstom, što pogađa
  i `mateIn1`, `mateIn2`, `smotheredMate`. `IN` nad razloženom tabelom poredi
  ceo string i uklanja tu klasu greške.
- Čita se isključivo kroz `Chessko/Logic/PuzzleRepository.swift` (`import
  SQLite3` — sistemski modul, **bez ijedne SPM zavisnosti**), otvorena
  `SQLITE_OPEN_READONLY`. Klasa je `@MainActor`: to nije ukras nego jedina
  stvar koja sprečava trku oko keširanog `count`-a, i budućeg pozadinskog
  pozivaoca pretvara u grešku pri kompajliranju.
- `build_puzzle_db.py` drži ~1,86M kandidata u memoriji (~1–2 GB) pre
  uzorkovanja. Radi na mašini sa dovoljno RAM-a; nije strimujuće po opsegu.

## Dizajn sistem

Od Faze 1 sve boje, tipografija i razmaci idu kroz `Chessko/Views/DesignSystem.swift`
(`enum DS`). Pravac je „Tiho i precizno" iz spec 5.6: neutralne podloge, **jedan
uzdržan akcent**, tabla je jedina zasićena stvar na ekranu.

| Token | Svetla | Tamna |
|---|---|---|
| `DS.accent` | `#2E4A8A` | `#7EA0E8` |
| `DS.ground` / `DS.surface` | `#F2F3F7` / `#FFFFFF` | `#0E1428` / `#161D33` |
| `DS.fill` / `DS.line` | `#E7EAF1` / `#DFE3EC` | `#1E2740` / `#232C46` |
| `DS.ink` / `DS.inkMuted` | `#161A22` / `#6B7280` | `#EEF1F7` / `#8B93A7` |

Uz njih: `DS.success/warning/danger` (nose značenje, ne ukras), `DS.scrim`/`DS.onScrim`
(modalni preklopi, namerno isti u obe teme), `DS.inkFixed` (taman tekst koji se NE
invertuje — za podloge koje same ne prate temu), `DS.Space.*`, `DS.Radius.*`,
`DS.maxBoardSide`, i tipografska skala `Font.dsTitle/dsHeading/dsBody/dsCaption/dsMono`.

**Akcent je fiksan i ne menja se sa temom table.** Osam tema table i dalje bira
korisnik; tabla ostaje jedini šaroliki element.

Šta NAMERNO nije token, i zašto:
- boje table (`Color.squareLight/squareDark/boardBackground`, `#EF4444` za šah,
  oznake poslednjeg i legalnih poteza) — bira ih tema table
- crna i bela polovina šahovskog sata (`#121212`, `#2C2C2E`, `#E5E5EA`) — te boje
  prate STRANU sata, ne sistemsku temu. Sa tokenom bi u svetloj temi crna polovina
  postala skoro bela ispod belog teksta
- prikaz tema table i stilova figura u podešavanjima — to je sadržaj, ne hrom
- `PieceColor.white/.black` nisu boje nego strane u igri

### Android dizajn sistem

Od Faze 6d-1 isti sistem postoji i na Androidu, ista paleta kao iOS (`ChesskoColors` u
`ChesskoAndroid/.../ui/theme/DesignSystem.kt`, vrednosti bit-za-bit iste kao gornja tabela).
Mehanizam je `CompositionLocalProvider(LocalChesskoColors provides colors)` u `ChesskoTheme`
(`Theme.kt`) — `staticCompositionLocalOf` namerno umesto `compositionLocalOf`, jer se paleta
menja retko (promena teme) a čita je na stotine mesta; pozivna mesta čitaju `DS.accent` itd.,
isti oblik kao iOS. `DS` dobija i `DS.onAccent` (tekst/ikone NA `accent` podlozi) — `accent`
menja svetlinu između tema, pa nijedna fiksna boja za tekst na njemu ne radi u obe; čuva ga
`ContrastTest.plainWhiteWouldFailOnTheDarkAccent` i `onAccentMeetsAAInBothThemes` (11 testova
ukupno u `ContrastTest.kt` posle talasa ispravki 6d-2 — bilo 7 na kraju 6d-1, 10 pre talasa —
JVM, bez emulatora, kontrast se računa WCAG formulom, ne procenjuje; od talasa ispravki test
ume da meri i **stvaran kompozit** providnog tinta, `private fun over(...)`, umesto približenja
nad golom podlogom). `dynamicColor` (Android Studio šablon, boja sa
korisnikove tapete na Androidu 12+) je uklonjen — sa spec-om koji traži jedan fiksan akcent to
nije funkcija nego greška.

**6d-1 i 6d-2 su prenele PALETU, ne ceo sistem.** `DS.Space`, `DS.Radius`, `DS.maxBoardSide` i
`Type.title/heading/body/caption` postoje kao tokeni ali **i dalje nemaju nijedno pozivno
mesto** — svi ekrani i dalje zakucavaju `12.dp`, `RoundedCornerShape(16.dp)` i
`fontSize = 14.sp`; jedina upotreba tipografske skale je `Type.mono`. Ovo je odloženo odlukom
korisnika (vidi „Poznata ograničenja"), ne propust: primena razmaka, radijusa i tipografije kroz
sve ekrane je posao veličine cele još jedne kriške. Tokeni se ne brišu u međuvremenu: brisanje
bi značilo da ih neka buduća faza ponovo uvodi.

**Načelo koje je 6d-1 platila četiri puta, a 6d-2 mu je ostala verna:** *tema-zavisna boja i
tema-izuzeta podloga ne smeju se mešati — ni u jednom smeru.* Prva tri puta u 6d-1 je to bilo
mešanje fiksnog i tokenizovanog (fiksni tekst na tokenizovanoj podlozi i obrnuto). Četvrti put
je bio podmukliji i nije mešanje uopšte: `Switch` je M3 podrazumevanim vrednostima dobio **par
čija su oba člana tokeni**, `DS.line` palac na `DS.fill` traci — **1,067:1**, nevidljiva
kontrola. I komentar u `Theme.kt` i pregled Task-a 6 proverili su da je uloga MAPIRANA, i to
tačno; nijedno nije proverilo da su mapirane vrednosti **međusobno različite**. Analiziran je
token, ne par. Otuda i pravilo: **svaka boja koju upišeš ima podlogu, i par se meri, ne
pretpostavlja.**

**Peti oblik je našao finalni pregled 6d-2, i to je ispravka a ne fusnota.** Kontrolna traka
sata (`ChessClockView.ControlBar`) prešla je na `DS.surface`, a dve polovine sata su ostale
namerno fiksne — svaki član „uredno" obrađen, par nikad izmeren. U svetloj temi je `DS.surface`
bukvalno `#FFFFFF`, ista boja kao aktivna bela polovina ispod: šav je pao sa **14,63 na 1,000**.
Nije kozmetika — traka nije `clickable`, pa igrač koji cilja vrh svoje polovine pogađa inertnu
traku. Talas ispravki je vratio podlogu na fiksnu (`ClockBarBackground = #1E293B`) i sav sadržaj
trake na `DarkColors` (fiksnu paletu, ne `DS.*`): **fiksna površina dobija fiksne članove.**
Čuva to `ContrastTest.clockControlBarIsReadableOverFixedHalves`.

**Od Faze 6d-2 svi ekrani prate temu — spisak izuzetaka je zatvoren.** Sat (`ChessClockView.kt`,
Task 1), blokovi i okvir lekcije (`LessonRenderer.kt`, `LessonDetailView.kt`, Task 2), ekran
učenja i sve tri kartice vežbi (`LearnView.kt`, Task 3) i četiri kartice u Podešavanjima
(Task 4, deo A) su prešli na `DS.*` tokene — pridružili su se sedam ekrana koje je 6d-1 već
migrirala. Ostaje isti obrazac izuzetaka koji ima i iOS (vidi „Šta NAMERNO nije token" gore).

**Spisak namerno fiksnih mesta, pun — raniji oblik ovog pasusa je nabrajao tri kategorije a
zvučao kao da je iscrpan:**

- boje table i osam tema table (`BoardView.kt`, `BoardTheme.kt`; Task 4 deo B ih je samo
  poravnao sa iOS vrednostima, ne tokenizovao — tabla je i dalje jedini šareni element)
- **cela kontrolna traka sata** (`ChessClockView.kt`): dve polovine prate STRANU u igri, a od
  talasa ispravki i traka između njih i sav njen sadržaj (`ClockBarBackground`/`ClockBarWell`/
  `ClockBarAccent`) — vidi „peti oblik" iznad. Dijalozi sata (izbor vremenske kontrole, info)
  NISU izuzeti: oni lebde nad scrim-om i legitimno prate temu.
- zlatna oznaka mata (`UiComponents.kt:150-163`)
- strane eval trake (`EvalBar.kt:86-87`)
- pločica promocije (`PromotionOverlay.kt:47`)
- gradijenti figura (`CapturedPiecesView.kt:54-68`)
- birač boje igrača (`MainActivity.kt:328-393`)
- **emoji u hromu — ZATVORENO Fazom 8, a lekcijska mapa Fazom 9.** 36 mesta u hromu (sat, ekran
  Igra, Zadaci i koraci Puta, Učenje i Podešavanja; 34 u četiri taska + 2 koja je provera obima
  propustila jer joj je grep izostavio blokove strelica i geometrije — vidi changelog) su
  zamenjena `Icons.*` + `tint = DS.*`; vidi „Faza 8" u Changelog-u. **Faza 9 je isto uradila sa
  lekcijskom mapom** — `SYMBOL_TO_GLYPH` (bivša `lessonIcon()`) je danas **43 `Icon` + 6
  `Emoji`**; vidi „Lekcijski glifovi" ispod. NAMERNO ostaju: tih **šest** simbola i **emoji u
  prevedenim porukama** (`Loc.kt`: `🎉`/`🏆`/`🔥`/`🧩` u porukama pobede/mata/statistike). Ovo
  drugo NIJE odstupanje Androida — iOS katalog nosi **iste ključeve sa istim emoji-jem**, a
  `Chessko/Views/SettingsSheet.swift:121,123` ima doslovno `🔥`/`🧩`; uklanjanje bi pokvarilo
  paritet dve platforme.

> **Emoji glif ne prima `color=`.** Skia za pun-kolor emoji glifove IGNORIŠE boju teksta, pa
> `Text("🔄", color = DS.ink)` izgleda kao da mehanizam radi a ne radi nikad — činjenica o Skia
> ostaje tačna, ali od Faze 8 **više ne opisuje naš kod**: dugmad koja su ovo pogađala
> (`🔄` na resetu sata i drugde) sada crtaju `Icon(imageVector = ..., tint = DS.*)`, gde `tint`
> radi normalno. Zatečena zaobilaznica `Modifier.alpha(0.38f)` na dugmetu za reset sata (uvedena
> baš zato što `color=` ne radi na emoji glifu) davala je **2,75:1** na glifu — ispod WCAG praga
> od 3:1 za ne-tekstualni sadržaj. Zamena za `Icon(tint = DS.inkMuted)` (krug netaknut) daje
> **3,26:1** — nije kozmetika, popravila je kontrast koji je padao. (Raniji oblik ovog pasusa
> je tvrdio da `lessonIcon()` mapa ostaje „poslednji potrošač `Modifier.alpha` na emoji glifu" —
> netačno: `grep -rn "\.alpha(" ChesskoAndroid/app/src` daje **0** pogodaka u celom modulu.
> Rečenica je slala čitaoca da traži potrošača koji ne postoji, pa je uklonjena.)

## Arhitektura (MVVM)

```
Chessko/
├── ChesskoApp.swift          @main, WindowGroup → ContentView
├── ContentView.swift         → GameView()
├── Models/                   čiste vrednosne strukture (struct/enum, Sendable)
│   ├── Position.swift        row 0 = rank 8 (crni), col 0 = file a
│   ├── ChessPiece.swift      PieceType, PieceColor, materialValue, Unicode symbol
│   ├── ChessMove.swift       from/to/flag; == poredi from+to **i** flag
│   ├── GameState.swift       cela tabla + prava rokade + status; immutable apply
│   ├── LessonContent.swift   LessonDocument + LessonBlock (12 tipova blokova)
│   └── Curriculum.swift      Curriculum + Chapter + CurriculumStep (4 tipa koraka)
├── Logic/
│   ├── ProgressStore.swift   napredak Puta, dnevni cilj, streak (progress.json)
│   ├── MoveGenerator.swift   generisanje poteza, detekcija šaha (enum, statičke fn)
│   ├── ChessAI.swift         negamax + alfa-beta, piece-square tabele
│   └── LessonRepository.swift  učitava Content/lessons/<id>.<jezik>.json iz bundle-a
├── ViewModels/
│   └── GameViewModel.swift   @Observable @MainActor — sva interakcija + AI okidač
├── Views/                    PathView (Put), StepPracticeView, StepGameView,
│                             GameView, BoardView, SquareView,
│                             CapturedPiecesView, PieceImageView,
│                             LessonDetailView (okvir) + LessonRenderer (blokovi)
├── Content/lessons/          FOLDER-REFERENCA: 32 JSON-a (4 lekcije × 8 jezika)
└── Assets.xcassets/          12 SVG figura: piece_{white|black}_{type}
```

### Ključni tokovi i konvencije

- **Koordinate**: `Position(row, col)`. `row 0` = rank 8 (crni red), `row 7` =
  rank 1 (beli red). `col 0` = file a. Beli pioni idu nagore (`dir = -1`).
- **Immutable state**: `GameState` se nikad ne menja in-place — `applying(_:)`
  vraća novu kopiju.
  - `applying(_:)` = puni apply: ažurira tablu, prava rokade **i** `status`
    (zove `legalMoves` da detektuje mat/pat). Koristi se za prave poteze i AI root.
  - `applyingForSearch(_:)` = lagani apply: **NE** računa status (izbegava
    beskonačnu rekurziju). Koristi se unutar MoveGenerator i AI pretrage.
- **Legalnost poteza**: `MoveGenerator.legalMoves` = pseudo-legalni potezi
  filtrirani tako da kralj ne ostaje u šahu (igra potez pa proverava `isInCheck`).
- **AI**: `ChessAI` — negamax sa alfa-beta, iterative deepening + transpoziciona
  tabela (Zobrist). Težina (`Difficulty`) određuje `maxDepth` + `timeLimit`
  (beginner/easy/medium/hard), NE fiksnu dubinu; korisnik bira težinu u
  podešavanjima (uključujući Stockfish nivoe). Evaluacija = materijal +
  piece-square bonus (+ endgame king tabela). Potezi se `shuffled()` radi varijacije.
- **Concurrency**: AI se računa na `Task.detached(priority: .userInitiated)`,
  rezultat se primenjuje nazad na `@MainActor`. `isThinking` gejtuje UI.
- **Promocija**: `promotionMove`/`showPromotion` u VM otvaraju overlay sa 4 figure
  (D/T/L/S) — `confirmPromotion(_:)`/`cancelPromotion()`. Postoji i podešavanje za
  automatsku promociju u damu (bez overlay-a) za korisnike koji to žele.
- **Boje table** (`SquareView`): `squareLight #e9ebde`, `squareDark #8592af`,
  `boardBackground #17234f`. Highlight: žuto za poslednji potez, cijan `#00D2FF` za selekciju
  (`SquareView.swift:101`). **Ova rečenica je do Faze 6d-2 pogrešno govorila „sivo za
  selekciju"** — nikad nije bilo tačno ni na jednoj platformi; ispravljeno kad je Android
  pregled (`BoardView.kt:408`) potvrdio isti cijan na obe strane.
- **Rotacija table**: `viewModel.isFlipped` → `BoardView` iterira redove/kolone u obrnutom
  redosledu; `SquareView` dobija `isBottomEdge`/`isLeftEdge` za koordinatne labele.
  `AnimatingPieceView` i `flyingCapture` overlay koriste display koordinate.

## Put (kurikulum i napredak)

Okosnica v2 od Faze 4a. Treći tab je **Put**, ne više Učenje.

- **Kurikulum** je `Chessko/Content/curriculum.json` — poglavlja i koraci, van koda kao i
  lekcije. Četiri tipa koraka: `lesson` (otvara lekciju, završava se izričitom potvrdom na
  dnu), `practice` (N zadataka po temi), `game` (partija do kraja), `test` (kao `practice`,
  ali se završava **samo bez ijedne greške** — prva greška vraća korak na početak sa novim
  zadacima).
- **Šest poglavlja, ovim redom**: `basics` (Osnove), `notation` (Notacija), `opening`
  (Otvaranje), `tactics` (Taktika), `middlegame` (Središnjica), `endgame` (Završnica).
  `notation` i `tactics` su dodati u Fazi 4b, ubačeni u sredinu niza (drugo i četvrto
  poglavlje) jer je to pedagoški redosled — notacija pre nego što partije iz otvaranja
  imaju smisla da se čitaju, taktika pre središnjice koja je već pretpostavlja. Cena:
  koraci Puta se broje po poziciji u nizu (`Korak %lld`), pa je ubacivanje pomerilo brojeve
  svakom koraku posle „Osnova" — namerno, ali vredi znati ako neko poređenje/link pamti
  stari redni broj. `tactics` ponavlja teme `fork`/`pin`/`discoveredAttack` iz
  `middlegame`-a. Razlog NIJE oskudica u bazi — alternativnih taktičkih tema ima napretek
  (`deflection` 190, `hangingPiece` 190, `sacrifice` 190 zadataka u opsegu [600,1200]).
  Korak `notation-practice` je pritom **svesan kompromis**: vežba ume samo da izvlači
  zadatke po temi, a teme „pročitaj zapis" u Lichess bazi nema — pa su uzeta tri laka
  jednopotezna zadatka (`oneMove`), dok samo čitanje notacije uvežbavaju četiri `exercise`
  bloka unutar lekcije. Razlog je pedagoški: vežba i test treniraju tačno one motive koje `tactics-lesson` upravo
  predaje. `middlegame` je pritom namerno ostavljen netaknut, pa se opsezi preklapaju
  (`tactics-test` [600,1100] je podskup `middlegame-test` [600,1200]) — korisnik može dobiti
  i bukvalno isti zadatak u oba poglavlja. Prihvaćeno kao ponavljanje-razmak; ako ikad zasmeta,
  popravka je suziti teme u `middlegame`-u, ne u `tactics`-u.
- **Nov korak** = nov unos u `curriculum.json`. Nema Swift koda. Dekoder odbija nepoznat tip
  koraka, nepoznatu težinu i **obrnut `ratingRange`** (`ClosedRange` sa donjom granicom većom
  od gornje ruši proces pri kreiranju, pa se hvata na ulazu).
- **Test tvrdi da kurikulum ne laže**: svaka lekcija koju pominje mora da postoji kao fajl, i
  svaka tema mora da ima dovoljno zadataka u traženom opsegu. Bez toga tipfeler daje korak
  koji se **nikad ne može završiti**, i to bez ijedne poruke.
- **Napredak** je `Chessko/Logic/ProgressStore.swift` → JSON u Application Support
  (`progress.json`), ne `UserDefaults`. Stara statistika se pri prvom pokretanju migrira, a
  `stats_*` ključevi se **namerno ne brišu** — da povratak na stariju verziju aplikacije radi.
- **`StatsManager` je od Faze 4a fasada** nad `ProgressStore`-om. Zadržava svaki potpis, pa
  njegovih 26 pozivnih mesta nije dirano. Sme da se ukloni, ali to znači dirati svih 26.
- **„Resetuj statistiku" NE dira Put.** Briše brojače partija i zadataka, ali završeni koraci,
  streak i istorija dnevnog cilja ostaju — napredak nije statistika. To je namerno, ali se na
  ekranu nigde ne kaže, pa vredi znati.
- **Dnevni cilj** = jedan završen korak **ili** tri rešena zadatka. **Streak** = dani zaredom
  sa ispunjenim ciljem. Ključno: **niz se ne prekida dok dan ne prođe** — ako cilj danas još
  nije ispunjen, broji se od juče. Inače bi korisniku streak nestajao svako jutro.
- Otključavanje, cilj i streak su **čiste funkcije** u `PathProgress` — dan ulazi kao string,
  pa testovi ne zavise od vremenske zone ni od trenutka pokretanja.

### Android čita isti kurikulum

Od Faze 6c isti `curriculum.json` čita i Android, iz
`ChesskoAndroid/app/src/main/assets/curriculum.json` — **bajt-identično**, dokaz je
`diff Chessko/Content/curriculum.json ChesskoAndroid/app/src/main/assets/curriculum.json`
(prazan izlaz). Kurikulum se menja na jednom mestu i menja se na obe platforme, isti obrazac
kao lekcije (vidi „Android čita isti JSON" ispod).

- **`CurriculumParser` (Android) je isto Foundation/`org.json`-only kao iOS dekoder** — bez
  ijednog `android.*` uvoza, pa je prenosiv, ali mu se JVM testovi ne mogu pokrenuti bez
  emulatora (isti razlog kao `LessonContentTest`: JVM stub za `org.json` baca). Zato
  `CurriculumTest` živi u `androidTest`.
- **Parser BACA na nepoznat tip koraka i na obrnut `ratingRange`**, isto kao iOS — sadržaj je
  van dometa kompajlera na obe platforme.
- `ProgressStore.kt` je Android ekvivalent `ProgressStore.swift`: JSON fajl u `filesDir`
  (`progress.json`, ne `SharedPreferences`), sa istom migracijom (stari `SharedPreferences`
  ključevi se čitaju jednom i **ne brišu**) i istim čistim funkcijama za otključavanje/cilj/streak
  (delegirane na `PathProgress`, isti naziv kao iOS-ov tip).
- **Jedna namerna razlika**: `ProgressSnapshot` na Androidu nosi i `winsBeginner…winsStockfish`,
  kojih iOS `ProgressSnapshot` nema — **ne** zato što bi ih ekran podešavanja prikazivao (ne
  prikazuje; `grep -rn "winsBeginner|winsStockfish" ChesskoAndroid/app/src` pogađa samo
  `ProgressStore.kt`, `StatsManager.kt` i jedan test, nijedan pogodak u `ui/` ni
  `viewmodels/` — korisnik ih nikad nije video, ni pre ove grane ni posle). Pravi razlog:
  ta polja postoje u `SharedPreferences` starijih verzija aplikacije, pa bi fasada bez njih
  tiho pojela podatke pri migraciji — korisnik bi izgubio istoriju pobeda po težini iako je
  nikad ne vidi.

### Zamke koje su već jednom ujele

- **`GameViewModel` ima parametrizovan ključ za čuvanje partije.** Slobodna partija drži
  `chessko.savedGame`, korak Puta `chessko.savedGame.step.<id>`. Sa jednim ključem bi korak
  učitao korisnikovu partiju i pregazio je prvim potezom.
- **Predaja u koraku Puta se ne upisuje u statistiku** — to je jedini predviđen izlaz iz
  koraka koji se ne može dobiti. Odigrana partija u koraku se broji normalno.
- **Ekrani koraka se guraju kao VREDNOST** (`NavigationLink(value:)` +
  `navigationDestination(for:)`). Sa `NavigationLink { pogled }` odredište se preračunava kad
  se lista osveži, pa bi se gurnuti ekran promenio pod korisnikom baš kad završi korak.
- **`CurriculumStep.knownDifficulties` i `GameDifficulty` su dva ručno vođena spiska**
  (kurikulum mora ostati Foundation-only). `PathView.route(for:)` ih poredi u debug build-u —
  razlaz bi inače bio tih: korak zauvek stoji kao „Uskoro".

## Analiza partije

Od Faze 5. Posle svake partije Stockfish prolazi sve pozicije, klasifikuje poteze po
gubitku u centipionima i prikazuje ekran sa procentom tačnosti, trakom poteza u boji i
prelomnim potezom. Dostupno je i iz `game` koraka Puta.

- **Matematika je odvojena od motora.** `Models/MoveAnalysis.swift` je Foundation-only,
  kompajlira se i u SwiftPM paket i pokriven je sa 17 testova (uz još 9 za parser, ukupno 26
  u `MoveAnalysisTests.swift`). Motor je zamenljiv; pravila
  klasifikacije nisu. `Logic/UCIScoreParser.swift` je iz istog razloga izdvojen iz
  `StockfishBridge`-a — `actor` koji uvozi `ChessKitEngine` ne može u testni paket, a
  čitanje ocene iz linije teksta mora da bude testirano.
- **N+1 pretraga, ne 2N.** Ocena pozicije PRE poteza daje „najbolje što se moglo", a ocena
  pozicije POSLE njega, sa obrnutim znakom, daje „šta je odigrano". Svaka pozicija se zato
  pretražuje tačno jednom: partija od 40 poteza traži **81 pretragu, ne 160**.
  `cpLoss = before + after` — sabiranje nije greška nego posledica toga što se perspektiva
  posle poteza okreće na protivnika.
- **Izmereno, ne procenjeno** (simulator iPhone 17 Pro, dubina 12 iz spec-a): 81 pozicija za
  **~17,5 s**, 81/81 ocena, tri uzastopna prolaza. Deljeni motor je izabran merenjem — svež
  motor po poziciji daje 20/20 ali ~3× sporije (25,1 s prema 8,7 s **na 20 pozicija**, i to na
  simulatoru iPhone 17 bez „Pro"). Na punih 81 poziciju svež motor nikad nije meren; brojevi iz
  dva reda ovog bullet-a nisu sa istog uređaja i ne porede se direktno.
- **Mat se mapira u centipione**, a `cpLoss` je ograničen na `0…1000`. Bez gornje granice
  jedan propušten mat (razlika ~20.000) sam odredi prosek cele partije i tačnost padne na ~0
  iako je ostatak bio solidan. Granica je trostruko iznad praga za promašaj (300), pa ne
  sakriva nijednu grešku. Potez koji DAJE mat ispadne `cpLoss = 0`, što je i tačno.
- **Prelomni potez** je najveći gubitak, ali samo ako je ≥ 100 i ako potez **nije** klase
  `.best`. Bez tog drugog uslova kartica ume da kaže „Prelomni potez (−1000)" i da ga oboji
  akcentom kao najbolji potez — dve suprotne poruke o istom potezu. Potez koji bi i motor
  odigrao nije prelomni, ma šta merenje reklo.
- **Završna pozicija se ne šalje motoru.** Za poziciju bez legalnih poteza Stockfish ne
  pošalje nijednu `<score>` liniju, pa bi analiza vraćala `nil` za SVAKU odigranu partiju —
  poslednja pozicija je uvek mat ili pat. `terminalEval` je rešava iz pravila (mat →
  `mate(0)`, pat → `cp(0)`), i to preko generisanja poteza, **ne** preko `state.status`, koji
  `GameState.fromFEN` ostavlja na `.playing` i za mat.
- **`game` korak Puta NE zavisi od analize.** Korak se upisuje kao završen pre nego što se
  ekran analize uopšte otvori. Da je obrnuto, korak bi bio nezavršiv kad motor nije dostupan —
  tiha, trajna blokada Puta.

### Dva pada koja su ovde dijagnostikovana

- **SIGPIPE pri otkazivanju analize gasio je celu aplikaciju, bez izveštaja o padu.**
  `ChessKitEngine` ne pokreće zaseban proces: Stockfish radi u našem i `dup2`-uje svoj pipe
  na `stdout`. `stop()` zatvara čitajući kraj, pa svaki naredni upis niti koja se još gasi
  šalje SIGPIPE, čija podrazumevana radnja gasi proces. Izmereno: 4 pada u 8 pokretanja; sa
  `signal(SIGPIPE, SIG_IGN)` u `ChesskoApp.init()` — 0 u 14. Upis u zatvoren pipe samo vrati
  `EPIPE`, a `ChessKitEngine` povratnu vrednost `write()`-a ionako ne gleda.
- **Motor koji se pokrene a nikad ne dobije `position` obara SLEDEĆI motor** u istom procesu
  (SIGABRT, assert u `Position::set`). Zato se pozicija šalje odmah po preuzimanju stream-a;
  `stop()` od toga ne spasava (mereno: padalo 2/3 i sa njim).

### Šta NIJE utvrđeno — namerno zapisano

- **Zašto je `bestMove` 2026-06-27 morao na svež motor po pozivu, ne zna se.** Objašnjenje
  „`AsyncStream` se završava kad se iterator ispusti" je **eksperimentalno oboreno**: posle
  ispuštanja iteratora `onTermination` se ne poziva, a nov iterator uredno dobija sledeću
  vrednost. Deljeni motor u analizi radi (81/81 ×3), ali **to nije dozvola** da se `bestMove`
  prebaci na isti obrazac — taj put je već jednom oboren u produkciji.
- **`generation` brojač u `AnalysisViewModel` je tačan ali neizvršiv.** Sa uklonjene sve tri
  zaštite izmereno je 0 zastarelih izveštaja kroz 14 pokretanja. Ostaje jer čuva stvarnu
  invarijantu, ali se ne navodi kao dokazana zaštita.
- **~2 od 14 brzih ponovnih pokretanja analize** završi na „Analiza nije uspela" — izlaz
  starog motora upadne u pipe novog. Uzrok je u `StockfishBridge`/`ChessKitEngine`.

### Android (Faza 6e) — šta je drugačije

Android nema `ChessKitEngine` — `Logic/StockfishEngine.kt` je tanak JNI most na PRAVI
Stockfish, kompajliran direktno u APK (`app/src/main/cpp/stockfish/`). Izlaz je zato čist
UCI tekst bez ijedne zagrade i bez decimale (`info depth 1 … score cp 445 … pv d5c4`) — iOS-ov
tagovan oblik (`<score> <cp> 34.0`) ovde ne važi uopšte, i pretpostavka po tom obrascu bi
oborila fazu identično onome što je već jednom oborilo iOS (vidi `UCIScoreParser.kt`, koji je
pisan po **uhvaćenom** izlazu, ne po pretpostavci).

**Najveća arhitektonska razlika: `StockfishEngine` je `object` singleton sa JEDNIM
`searchMutex`-om**, ne zaseban `Engine` primerak po analizi kao na iOS-u. Dok analiza drži taj
mutex kroz N+1 uzastopnih `evaluate()` poziva, NIJEDNA druga pretraga ne prolazi —
uključujući AI potez u živoj partiji. Posledice na otkazivanje, sve otkrivene tokom ove faze:

- `AnalysisViewModel.cancel()` mora biti pozvan **eksplicitno** iz `DisposableEffect`-a ekrana
  analize (Task 5) — `onCleared()` sam po sebi nije garantovan da se izvrši na vreme; bez
  eksplicitnog poziva bi korisnik koji zatvori ekran i odmah započne novu partiju čekao da se
  ceo preostali niz `evaluate()` poziva završi pre nego što AI uopšte odigra prvi potez.
- Otkazivanje mora poslati i `"stop"` motoru, ne samo otkazati korutinu i osloboditi mutex
  (Task 4 nalaz, popravljeno u `a988fec`): `Mutex.withLock` oslobađa bravu u `finally` čak i
  kad je korutina otkazana dok čeka unutar nje, ali BEZ `"stop"` motor nastavlja da računa
  staru poziciju u pozadini — zakasneli `bestmove` stare pretrage bi upao u kanal SLEDEĆE i bio
  pročitan kao odgovor na sasvim drugu poziciju. Ista klasa greške koju iOS ima zapisanu za
  analizu ("izlaz starog motora upadne u pipe novog"), ali ovde bi posledica bila pogrešan
  potez u ŽIVOJ partiji, ne samo neuspela analiza. Popravka ide kroz `NonCancellable`, jer
  običan `suspend` poziv u `finally` već otkazane korutine sam baca `CancellationException`.
- **`"stop"` sam po sebi NIJE dovoljan — on uklanja uzrok i proizvodi posledicu.** Zaustavljena
  pretraga obavezno ispiše `bestmove` (`search.cpp:266-267`, na kraju `start_searching`), i taj
  red sleti u isti `outputChannel`; sledeći pozivalac prazni kanal na ULASKU, pre nego što red
  stigne, pa ga onda pročita kao svoj. Ranija verzija ovog unosa (i komentara u kodu) tvrdila je
  da je lanac zatvoren — bio je **sužen**, ne zatvoren: koraci 1–3 lanca žive u motoru, 4–5 u
  kanalu. Zato `StockfishEngine.drainUntilBestMove()` posle svakog našeg `"stop"`-a pojede taj
  red, **još unutar `searchMutex`-a**. **Izmereno, ne pretpostavljeno:** put otkazivanje →
  `"stop"` → pročitan `bestmove` nad `go depth 30` koji traje već 1,5 s trajao je **1 ms**
  (motor je u NAŠEM procesu, bez pipe-a i IPC-a), a stari `bestmove` je u logcat-u stigao
  **2 ms pre** prve komande sledeće pretrage — tačno u prozor koji pražnjenje ne pokriva. Čuva
  ga instrumentisani test `cancelledDeepSearchDoesNotPoisonTheNextEvaluation`, **dokazan
  mutacijom**: sa uklonjena oba poziva `drainUntilBestMove()` pada na „evaluate posle otkazane
  pretrage je vratio null", sa njima prolazi. `STOP_DRAIN_TIMEOUT_MS = 2000` je tri reda
  veličine iznad izmerenog.
- Svaka pretraga (i `getBestMove`, i `evaluate` koji je ova faza dodala) ima TIMEOUT
  (`EVALUATE_TIMEOUT_MS = 30_000`, `BEST_MOVE_TIMEOUT_MS = 120_000`) — bez njega bi zaglavljena
  pretraga (motor umro, `"go"` progutan) trajno blokirala jedini mutex i time ugasila AI za
  **ceo ostatak života procesa**, ne samo pokvarila jednu analizu, jer restart procesa je jedini
  izlaz iz `object` singletona. Vrednosti su izvedene iz merenja Task-a 2 na ovom istom
  emulatoru (`depth 20` → 29,3 s, `depth 22` → 43,8 s, `depth 12` → 1,0 s) uz procenjenu
  marginu (4×/25×) — sama margina nije merena, samo je Task 6 (ovaj zadatak) prvi put pustio
  test da se stvarno izvrši na uređaju umesto da samo dokaže kompajliranje.
- **`waitUntilReady()` je bio upotrebljiv tačno jednom po životu procesa — pravi bug u
  `StockfishEngine.kt`, ne u testu.** Funkcija namerno ne šalje sopstveni `"isready"` (razlog
  ostaje: poslat pre `start()`-ovog `setoption` niza bi stigao prerano i učinio funkciju
  bezvrednom, isto kao `engineStarted`), nego čeka JEDINI `"readyok"` koji `start()` pošalje.
  Bez pamćenja da je taj `"readyok"` već viđen, DRUGI poziv u istom procesu čeka liniju koja
  nikad više neće stići i visi do **sopstvenog** `timeoutMs = 30_000L` — zaseban literal koji se
  slučajno poklapa sa `EVALUATE_TIMEOUT_MS`, pa je raniji tekst ovog unosa (i komentara u kodu)
  pogrešno pripisivao istek toj konstanti — **držeći `searchMutex` sve to vreme**, ista klasa
  greške (zaglavljeno unutar jedinog mutexa) koju timeout iznad ublažava za
  `getBestMove`/`evaluate`. Otkriveno kad je Task 6 prvi put pustio `StockfishEvaluateTest` da se
  stvarno izvrši (`mateInOneGivesPositiveMate`, `time="30.019"` u XML-u, drugi test klase koji
  zove `waitForEngineReady()`) — prvobitno pogrešno dijagnostikovano kao defekt test-poretka.
- **Ista funkcija je posle toga popravljena DRUGI put, i to je poučniji deo.** Prva popravka
  (`f61d26b`) je zastavicu postavljala u samoj `waitUntilReady()` — što rešava drugi poziv, ali
  ne i prvi poziv POSLE već obavljene pretrage: svaki `getBestMove`/`evaluate` prazni kanal na
  ulasku (`tryReceive` petlja), pa prva pretraga u aplikaciji POJEDE `"readyok"` i baci ga.
  Gejt uveden u analizi (vidi ispod) bi tako u najčešćem toku — korisnik odigra partiju pa je
  analizira — čekao 30 s i javio „motor nije spreman" za motor koji radi. Zato zastavicu sada
  postavlja `listenOutput()`, jedino mesto kroz koje prolazi svaka linija motora pre nego što je
  iko može pojesti, a `waitUntilReady()` je samo poll te zastavice i **više ne uzima
  `searchMutex`** (nema šta da čita iz kanala). **Provereno na uređaju, ne izvedeno:** u procesu
  koji je imao tačno jedan `"readyok"` (18:13:22) i pretragu posle njega (18:15:12, čije je
  pražnjenje taj red pojelo), analiza pokrenuta odmah zatim je prošla normalno umesto da
  istekne.
- **Pretraga poslata motoru koji još nije učitao mrežu GASI CEO PROCES, ne degradira tiho.**
  `Engine::go` zove `verify_networks()` (`engine.cpp:153-155`), a ta provera na neučitanu mrežu
  radi `exit(EXIT_FAILURE)` (`nnue/network.cpp:267`) — bez dijaloga o padu i bez izveštaja, ista
  vrsta nestanka kao iOS-ov SIGPIPE. `engineStarted` NIJE zaštita od toga (postaje `true` pre
  kopiranja NNUE mreža i pre handshake-a), pa `AnalysisViewModel` i obe funkcije motora čekaju
  `waitUntilReady()` pre prve pretrage. Analiza je za to i najizloženija: u režimu „Igra sa
  prijateljem" ona je JEDINA vrata ka motoru (prvo pokretanje → partija u dvoje → mat u 4
  poluteza → „Analiziraj partiju", bez ijednog ranijeg poziva motoru). **Izmereno na emulatoru**
  koliko taj prozor traje: hladan start (NNUE se stvarno prepisuje iz asseta) 18:13:16,862 →
  `readyok` 18:13:22,114 = **5,25 s**; topao start (mreže već na disku) **3,70 s**. Na sporijem
  telefonu je duži, jer je kopiranje ~140 MB.
- **Terminalna grana (`terminalEval`) ostaje, ali NE iz iOS-ovog razloga.** Gore piše da za
  poziciju bez legalnih poteza Stockfish „ne pošalje nijednu `score` liniju" — to važi za
  `ChessKitEngine`, a native Stockfish koji se kompajlira u ovaj APK radi suprotno: prazan
  `rootMoves` ide na `onUpdateNoMoves` (`search.cpp:212-216`), što ispisuje
  `info depth 0 score mate 0` (odnosno `cp 0` za pat; `uci.cpp:620-621` + `format_score` na
  `:541`/`:548`), dakle **tačno onu vrednost koju terminalna grana i vraća**. Grana je ovde
  optimizacija i nezavisnost od motora (nijedna UCI komanda, nijedan red u `searchMutex`-u, isti
  rezultat i kad je motor nespreman), ne uslov da analiza uopšte radi. Raniji komentar u kodu je
  iOS-ovo obrazloženje prepisao kao da važi i ovde — ista klasa greške kao „vežbe iz baze" iz
  Faze 4b.

**Izmereno u ovom zadatku (Task 6), prvi stvaran N+1 niz uživo:** partija od 10 poteza (11
pozicija) na emulatoru je analizirana za **~1,3 s** (17:01:02.931–17:01:04.256 u logcat-u),
prosek ~0,12 s po poziciji — primetno brže od baznog merenja Task-a 2 (~1,0 s na `depth 12`).
Motor se između poziva NE resetuje (`ucinewgame` se nikad ne šalje), pa transpoziciona tabela
ostaje deljena kroz sve `evaluate()` pozive iste partije; to je verovatan uzrok razlike, ali
NIJE dokazano — samo zabeleženo da je merenje sa ovog uređaja, ne generalna tvrdnja o brzini
motora. **Ovo je emulatorski broj, ne uređajski.**

Otkazivanje usred prave pretrage nije uhvaćeno na delu: na ovoj brzini ceo niz od 11 pozicija
završi za ~1,3 s, brže od ručne reakcije preko `adb`, pa tap na „Zatvori" 1 s nakon starta
verovatno stiže POSLE što se analiza već završila, ne usred nje. Provereno je zato ono što se
proveriti moglo — otkazivanje odmah nakon "Analiziraj partiju" pa trenutno pokretanje nove
partije nije ostavilo zaglavljen ili tuđ potez: AI je odigrao svoj, ispravan potez odmah.
Popravka iz Task-a 4 (`NonCancellable` `"stop"`) je zato potvrđena izvorom i logikom, ne i
snimljenim trenutkom prave trke.

## Sadržaj lekcija

Od Faze 3 tekst lekcija **nije u Swift-u**. Živi u
`Chessko/Content/lessons/<id>.<jezik>.json` — 36 fajla (4 lekcije × 8 jezika + 2 lekcije
× 2 jezika), `id ∈ {board-and-pieces, openings, middlegame, endgame, notation, tactics}`.

```bash
python3 build_lesson_json.py     # ZAMRZNUT — odmah odustaje, ne regeneriše NIŠTA
```

- **`notation` i `tactics` su prve lekcije napisane POSLE Faze 3** (Faza 4b), pa nisu
  prošle kroz `build_lesson_json.py` — pisane su ručno, direktno kao JSON, samo na `sr` i
  `en` (ne svih 8 jezika kao originalne četiri; vidi „Kako se dodaje nova lekcija").
  `build_lesson_json.py` je **zamrznut** (vidi „Baza zadataka") — pokretanje ispiše
  objašnjenje i odustane, ne regeneriše ni originalna 32 fajla ni ova četiri. Ako se
  `notation`/`tactics` ikad prevedu na preostalih 6 jezika, to ide ručno.
- **Vežbe u lekciji `tactics` su izvučene iz isporučene `puzzles.sqlite` baze i
  KONVERTOVANE**, ne prekopirane direktno — svaka se može pratiti nazad do zadatka u bazi
  (`lVYU8`, `osHSK`, `2CKmq`, `4tXYd`). **Vežbe u lekciji `notation` NISU iz baze** — to su ručno sastavljene pozicije
  iz teorije otvaranja (1.e4, 2.Sf3, Lxf7+, 0-0), jer lekcija uči kako se potez ZAPISUJE, a
  za to treba potez koji čitalac prepoznaje, ne taktički zadatak. Konverzija ispod se tiče
  samo zadataka iz baze. Lichess zadatak uvek počinje **protivnikovim**
  potezom (pozicija je posle poteza koji je napravio propust; prvi UCI potez u nizu je taj
  protivnički potez, drugi je rešenje) — `exercise` blok tipa `scripted` očekuje da je
  igrač odmah na potezu, pa je `startFEN` postavljen NA POZICIJU POSLE tog prvog poteza, a
  `uciMoves` sadrži samo poteze igrača i odgovore počev odatle. Nekonvertovana pozicija bi
  značila da vežba traži potez koji je zapravo protivnikov — tabla bi odbila svaki potez
  igrača i vežba bi delovala pokvareno bez ijedne poruke o tome zašto.

- **`Content/` je FOLDER-REFERENCA u Xcode projektu**, ne grupa. Cela struktura
  direktorijuma se prenosi u `.app`, pa **nova lekcija ne traži izmenu
  `project.pbxproj`** — dovoljno je spustiti JSON u folder. Zato
  `LessonRepository` traži `subdirectory: "Content/lessons"`; bez putanje bi
  radilo samo da fajlovi stoje pojedinačno u korenu bundle-a.
- **Tok**: `LessonRepository.shared.lesson(id:language:)` (keš, pad na `en` pa
  `sr` ako jezika nema) → `LessonDocument` → `LessonDetailView` (samo zaglavlje
  i skrol) → `LessonRenderer` (jedino mesto koje zna kako se blok crta).
- **12 tipova blokova** (`LessonBlock` u `Models/LessonContent.swift`):
  `heading`, `paragraph`, `bullets`, `box`, `quote`, `pieceRow`, `numberedRule`,
  `pieceValueTable`, `board`, `explorer`, `exercise`, `divider`.
  **Nov tip = jedan `case` u `LessonBlock` (+ `Codable` grane) i jedna grana u
  `LessonRenderer.view(for:)`.** Dekoder namerno **baca** na nepoznat tip —
  sadržaj je van dometa kompajlera, pa pokvaren JSON mora da vikne, ne da se
  tiho preskoči (`LessonRepository.load` loguje pa `assertionFailure`).
- **Tekst iz JSON-a stiže već preveden i NE ide kroz `Loc()`.** Renderer i
  `OpeningExerciseViewModel` zovu `Loc()` samo na sopstvenom hromu (dugmad,
  podrazumevane poruke vežbi, „Mat u %lld").
- **Katalog prevoda (`Localizable.xcstrings`) od ove faze pokriva samo
  interfejs** — 241 ključ. Sadržaj lekcija se u njega više ne dodaje; raste
  samo sa UI-jem. Izuzetak su četiri oznake bodova (`1 bod` / `3 boda` /
  `5 boda` / `9 bodova`) koje `L_PieceValueTable` **sastavlja interpolacijom**
  iz brojne vrednosti u JSON-u, pa moraju da ostanu ključevi.

### Android čita isti JSON

Od Faze 6b (2026-09-13) isti sadržaj čita i Android, iz
`ChesskoAndroid/app/src/main/assets/lessons/` — **bajt-identično**, dokaz je
`diff -r Chessko/Content/lessons ChesskoAndroid/app/src/main/assets/lessons`. Lekcija se menja
na jednom mestu i menja se na obe platforme.

- **Parser ide kroz `org.json`** (ugrađen u Android), ne `kotlinx.serialization` — ta bi bila
  nova zavisnost. Šema je `models/LessonContent.kt`, bez ijednog `android.*` uvoza, pa je
  prenosiva; ali se **njeni testovi ne mogu pokrenuti bez emulatora**, jer Android JVM testovi
  nose *stub* `org.json`-a koji baca. Zato `LessonContentTest` živi u `androidTest`, a ne u
  `test`. Test-only zavisnost `org.json:json` je odbijena ne zbog štednje nego zato što bi
  parser testirala protiv **druge implementacije** od one koja se isporučuje na uređaju.
- **Parser BACA na nepoznat tip bloka**, isto kao iOS. Sadržaj je van dometa kompajlera, pa je
  to jedino mesto koje može da vikne.
- **`icon` u JSON-u je ime SF simbola** (`crown.fill`, `tuningfork`). iOS ih crta nativno;
  Android ih razrešava kroz `lessonGlyph()` u `LessonRenderer.kt` (49 simbola). Bez te mape na
  svakom naslovu bi pisalo bukvalno `crown.fill`. Detalji — vidi „Lekcijski glifovi" ispod.

### Lekcijski glifovi (Faza 9)

Do Faze 9 je mapa bila `Map<String, String>` (`lessonIcon()`, 49 emoji-ja) i rezultat je išao
direktno u `Text(...)`. Danas je `SYMBOL_TO_GLYPH: Map<String, LessonGlyph>` — **43 `Icon` +
6 `Emoji` = 49** — a `lessonGlyph(String): LessonGlyph` je jedina ulazna tačka.

- **`LessonGlyph` je `sealed interface` sa tri grane:** `Icon(ImageVector)`, `Emoji(String)` i
  `Unknown(String)`. Tip postoji zato što ikona i emoji **ne mogu da stoje u istom
  `Map<String, ImageVector>`** — šest simbola namerno ostaje emoji (razlog niže). Plan Faze 9 je
  tu koliziju promašio i opisao izmenu kao „mapa postaje `Map<String, ImageVector>`"; pre-flight
  provera je to uhvatila i dodala Task 0, koji uvodi **samo tip i jedno mesto crtanja**, bez
  ijednog prevedenog simbola — ekran mora da izgleda identično pre i posle.
- **`LessonGlyphView(glyph, fontSize, tint, modifier)` je jedino mesto crtanja.** `tint` je
  **obavezan parametar, ne podrazumevan** — sa podrazumevanom vrednošću bi ikona tiho pala na
  `LocalContentColor` i razišla se sa semantičkom bojom naslova pored sebe, i to bi se ponovilo
  na svakom budućem pozivnom mestu. Emoji grana `tint` **ne prima** (vidi „Emoji glif ne prima
  `color=`" gore).
- **`Unknown` vikne, ne ćuti** — crveni `Icons.Default.Warning` + ime simbola kroz `DS.danger`.
  Zatečeni fallback je crtao `"•"` ili sam simbol; tiha praznina (ili bukvalna reč „tuningfork")
  usred lekcije je gora od ružnog znaka. **Pravilo razlikovanja je ASCII, ne tačka:** ime bez
  tačke (`globe`, `link`, `tuningfork` — tri isporučena SF imena su baš takva) je do runde
  ispravki Task-a 3 prolazilo kao `Emoji` i **tiho se crtalo kao sopstvena reč**. Sada svako
  ASCII ime van mape pada na `Unknown`, a stvaran emoji glif (non-ASCII) i dalje prolazi.
  Izmereno da se ni za jedan od 49 isporučenih simbola grana ne menja.
- **Šest simbola NAMERNO ostaje emoji, i to je merena odluka, ne nedovršen posao:**
  `crown.fill` 👑, `rectangle.portrait.fill` 🏰, `l.joystick.fill` 🐴, `rhombus.fill` 📐,
  `tuningfork` 🍴, `dot.square.fill` ⬛. Razlog: **u `Icons.Filled` nema nijedne šahovske figure
  ni ijednog taktičkog motiva** — provereno raspakivanjem sources jar-a
  `material-icons-extended` 1.7.8, **2083** imena, broj reprodukovan na tri načina. Postoji samo
  metafora (`Castle` za topa, `Restaurant` za viljušku), a `Icons.Filled.Restaurant` u lekciji o
  taktici šalje sledećeg čitaoca koda da traži grešku koje nema. Unicode figure (♙♘♗♖♕♔) su
  razmotrene kao treće rešenje i odbijene merenjem: `crown.fill` je 70 od 150 pojava tih šest i
  **polisemičan** je (od 11 mesta u `*.sr.json` četiri su Dama, tri Kralj, četiri uopšte nisu
  figura), pa jedan znak ne može biti i ♔ i ♕.
- **`square.grid.3x3.fill` je prešao na `Icons.Filled.GridOn`, i to NIJE u skupu od šest.**
  Plan ga je nabrajao među „sedam šahovskih koji ostaju", pa se broj čita kao 42+7 — netačno.
  Merenje: simbol se u **telu** lekcije ne javlja nijednom, a naslovna je ikona za **8 od 36**
  lekcijskih fajlova (jedino pozivno mesto je `LessonDetailView.kt:168`, pločica 50dp sa
  `accent@20%` podlogom). Da je ostao emoji, zaglavlje prve lekcije koju svaki korisnik otvori
  nosilo bi jedini šareni glif među četiri tintovane ikone u istom redu. Uz to zatečeni emoji
  ♟️ **nije bio ono što simbol znači** — `square.grid.3x3.fill` je mreža polja, a iOS na tom
  mestu crta TABLU, ne figuru.
- **Brojevi u komentarima se izvode, ne prepisuju.** `LessonRenderer.kt` uz mapu nosi komande
  kojima se broj klasa (36) i broj imena u `Icons.Filled` (2083) dobija iz izvora. To je
  popravka **uzroka**, ne ispisa: brojka je u ovoj fazi bila pogrešna tri puta, uvek iz istog
  razloga (v. changelog).
- **Markdown `**bold**` se mora obraditi** — `mdBold()` u istom fajlu. Bez toga se u sadržaju
  vidi 307 parova zvezdica. iOS to radi kroz `mdText()`.
- **Vežba sa `mateIn` ide u `MatePuzzleCard`**, bez njega u `OpeningExerciseCard` — isti izbor
  kao iOS. Obe kartice primaju `solvedMessage`/`wrongMessage`/`playingPrompt` iz JSON-a; bez
  toga bi za mat-zadatke izmena sadržaja tiho nestala, i pao bi ugovor „sadržaj je u JSON-u".
- **Figura i jedinica u `pieceRow`/`pieceValueTable` se crtaju iz `piece` i `value`.** Android
  koristi Unicode simbole (`ChessPiece.symbol` — ♙♘♗♖♕♔), iOS crta SVG kroz `PieceImageView`;
  to je jedina namerna vizuelna razlika. Jedinicu sklapa `valueUnitLabel()` (`"1"` → `loc("1 bod")`),
  jer **nijedan od 36 fajlova ne zadaje `valueLabel`** pa se uvek ide na taj put — bez njega
  tabela piše golo „1" umesto „1 bod" / „1 point". Ključevi su namerno literali unutar `loc()`
  da ih `everyLocCallInTheSourceHasAKeyInTheDictionary` proveri. Nepoznat naziv figure daje
  crveno `⚠`, ne tiho nacrtanog Kralja.

### Kako se dodaje nova lekcija

Bez ijedne linije Swift-a — provereno na simulatoru, ne pretpostavljeno:

1. Napiši `Chessko/Content/lessons/<id>.sr.json` i `<id>.en.json` (nove lekcije idu na
   sr + en; postojeće četiri imaju svih 8 jezika).
2. Rebuild. `Content/` je **folder-referenca**, pa `project.pbxproj` ostaje netaknut.

> **Od Faze 4a ovo VIŠE NIJE dovoljno.** `LearnView` — spisak lekcija — obrisan je kad je
> treći tab postao Put. Lekcija koja nije vezana ni za jedan korak u `curriculum.json`
> **nigde se ne vidi**, iako uredno stiže u aplikaciju. Uz JSON lekcije mora ići i
> `{"type": "lesson", "lessonId": "<id>"}` korak u nekom poglavlju.
>
> `LessonRepository` i dalje otkriva id-jeve iz imena fajlova, pa se lekcija **učitava** bez
> ijedne izmene koda; samo je više niko ne prikazuje sam od sebe.

Dve stvari na koje treba paziti pri pisanju JSON-a:

- **Nepoznat `type` bloka ruši dekodiranje namerno** — bolje glasan pad nego lekcija sa
  rupom koju niko ne primeti. Isto važi za nepoznat naziv figure (iOS crta prekriženi
  upitnik, Android crveno `⚠`) i za `interactive: true`
  (interaktivna tabla još ne postoji): oba daju vidljivu poruku u tekstu lekcije.
- **`pieceValueTable` redovi treba da zadaju `valueLabel`** („1 bod" / „1 point"). Bez njega
  renderer sklapa labelu iz `value` po srpskoj množini i traži ključ u katalogu — a ključevi
  postoje samo za 1/3/5/9/∞, pa bi vrednost „2" na svim jezicima dala srpsko „2 boda".

## Stanje Android porta

Android je zaseban Kotlin/Compose port (`ChesskoAndroid/`, ~10.500 linija). Spec Fazu 6
opisuje kao „prenos svega iz faza 0–5", što je pet faza posla, pa se radi u kriškama.

| Faza | Preneto? | Napomena |
|---|---|---|
| 0 — higijena | **da** | rokada popravljena (`bc58ab0`), 9 JVM testova: 6 perft + 3 regresiona za rokadu |
| 1 — dizajn sistem | **da** | svi ekrani na `DS.*` tokenima (vidi „Android dizajn sistem"); 6d-1 sedam ekrana, 6d-2 sat/lekcije/učenje/podešavanja/tabla |
| 2 — offline zadaci | **da** | to JESTE Faza 6a — ista stavka pod dva broja (iOS je numeriše 2, Android plan 6a) |
| **6a — offline zadaci** | **da** | deljena `puzzles.sqlite`, `PuzzleRepository`, Elo rejting, „Sledeći zadatak" |
| **6b — lekcije u JSON** (Faza 3) | **da** | isti 36 JSON fajlova kao iOS; `LearnView.kt` 1322 → 1111 linija |
| 4 — Put | **da** | ista stavka pod dva broja kao 2/6a — isporučeno kao **6c** |
| **6c — Put** | **da** | isti `curriculum.json` kao iOS, bajt-identičan (dokaz `diff`); `ProgressStore` (JSON u `filesDir`, migracija iz `SharedPreferences`); `PathView` sa sva četiri tipa koraka (`lesson`/`practice`/`test`/`game`) |
| **6d-1 — dizajn sistem, deo 1** | **da** | `ChesskoColors`/`DS`/`ChesskoTheme` (ista paleta kao iOS) + sedam ekrana (`MainActivity`, Zadaci, Put, `practice`/`test`/`game` koraci, Podešavanja) prebačeno sa zakucanih boja na tokene; `dynamicColor` uklonjen |
| **6d-2 — dizajn sistem, deo 2** | **da** | preostalih pet celina prebačeno na tokene: hrom sata, blokovi i okvir lekcije, ekran učenja + tri kartice vežbi, četiri kartice u Podešavanjima (bez ivice — vidi „Poznata ograničenja"); tabla i osam tema table ostaju namerno netokenizovane, samo poravnate sa iOS vrednostima (poslednji potez 0,40, prsten uzimanja 0,65, tačka praznog polja 0,55) |
| 5 — analiza partije | **da** | ista stavka pod dva broja kao 2/6a i 4/6c — isporučeno kao **6e** |
| **6e — analiza partije** | **da** | native Stockfish preko JNI (ne `ChessKitEngine`), isti N+1 ugovor i pragovi klasifikacije kao iOS; ekran analize + dugme u obe grane `MainActivity` (portret/pejzaž) i u `game` koraku Puta; vidi „Android (Faza 6e) — šta je drugačije" |

Testovi: **110 JVM** (`./gradlew testDebugUnitTest` — `ContrastTest` 18, `EngineTest` 9,
`ExampleUnitTest` 1, `GameStateFenHalfmoveTest` 5, `GameStateStatusFromPositionTest` 5,
`LessonBoardsOptOutOfSwipeTest` 2, `LocTest` 5,
`MainActivitySoundWiringTest` 1, `MoveAnalysisTest` 27, `PathProgressTest` 9,
`PuzzleDateFormatTest` 7, `PuzzleRatingTest` 9,
`StepWindowTest` 3, `UCIScoreParserTest` 9) + **54 instrumentisana**
(`./gradlew connectedDebugAndroidTest`, traži emulator — `CurriculumTest` 6, `ExampleInstrumentedTest` 1,
`GameViewModelActivityRecreationTest` 1, `LessonContentTest` 9, `LessonGlyphMapTest` 2,
`LessonRepositoryTest` 6,
`ProgressStoreTest` 10, `PuzzleRepositoryTest` 10,
`SoundReleaseOnLanguageKeyChangeTest` 1,
`StatsFacadeTest` 4, `StockfishEvaluateTest` 4). Oba broja su iz XML-a, ne iz izlaznog koda.

> **`espresso-core` je od Faze 7 na `3.7.0`, i to nije kozmetika.** `SoundReleaseOnLanguageKeyChangeTest`
> je prvi test u projektu koji uopšte koristi Compose UI test (`createComposeRule`), pa je prvi
> naleteo na to da `espresso-core` **3.5.1 i 3.6.1** na **API 36** bacaju
> `NoSuchMethodException: android.hardware.input.InputManager.getInstance` iz `Espresso.onIdle`,
> kroz koji Compose sinhronizuje. Izmereno, ne pretpostavljeno: 3.5.1 → pada, **3.6.1 → i dalje
> pada**, 3.7.0 → 52/52 prolazi. Verzija je test-only (`androidTestImplementation`) i nije nova
> zavisnost — samo podignut pin koji je stajao iz Android Studio šablona; isporučeni APK je
> netaknut.

> **`connectedDebugAndroidTest` ume da kaže `BUILD SUCCESSFUL` a da ne pokrene nijedan test**
> (npr. `INSTALL_FAILED_INSUFFICIENT_STORAGE`). Rezultat se čita iz
> `app/build/outputs/androidTest-results/connected/debug/*.xml`, ne iz izlaznog koda.

> **Emulator se pokreće bez prozora I ograničen**, inače otima i fokus i procesor:
> ```bash
> export ANDROID_HOME=~/Library/Android/sdk   # nije postavljen u okruženju
> nice -n 10 $ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 \
>   -no-window -no-audio -no-boot-anim -gpu host -cores 2 -memory 2048 &
> $ANDROID_HOME/platform-tools/adb wait-for-device
> $ANDROID_HOME/platform-tools/adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
> ```
>
> **`-gpu host` je jedina zastavica koja stvarno smanjuje opterećenje — i to sedmostruko.**
> Izmereno 2026-09-13, isti posao (hladan boot, otvaranje ekrana, šest skrolova, `uiautomator
> dump`, `screencap`), dva režima jedan za drugim:
>
> | Faza | `-gpu off` | `-gpu host` |
> |---|---|---|
> | boot | 691% vrhunac / 213% prosek | **227% / 105%** |
> | posao | 745% / **503%** | **216% / 73%** |
> | mirovanje | 95% / 24% | **40% / 13%** |
>
> Uzrok: `-gpu off` isključuje grafički čip, pa procesor **softverski rasterizuje 1080×2400 =
> 2,6 miliona piksela po kadru**. `-gpu host` to prepušta Metal-u. Gost je `arm64-v8a` na
> Apple Silicon-u, dakle instrukcije se i ne emuliraju — skoro sve opterećenje je bilo
> iscrtavanje.
>
> **Ovo je ispravka sopstvene greške, dvaput ponovljene.** Korisnik je dva puta morao ručno da
> ubije `qemu-system` (mereno 568%, pa 1076%), oba puta dok je bio na snazi recept sa
> `-gpu off` koji sam ja uveo i opisao kao „provereno bezopasan". Proverio sam da ne kvari
> sliku; nikad nisam proverio šta košta. `-gpu host` je provereno na oba: screenshot je pun
> (1080×2400, 215 KB, vizuelno potvrđen — ikone, boje, tekst) i `uiautomator` čita tekst.
>
> `-cores` ograničava samo gostujuće vCPU-ove, a `nice` snižava prioritet ne i broj niti (127
> niti u merenju) — nijedno od to dvoje nije rešavalo ništa.
>
> **Zastavice su samo pola priče — drugo pola je ŽIVOTNI VEK.** `-gpu host` snižava cenu dok
> emulator radi, ali ne sprečava da stoji upaljen satima. Zato: emulator se diže **samo za
> korak koji ga stvarno traži**, vizuelne provere se **grupišu u jedan prolaz** umesto da svaki
> task diže svoj, a odmah po tom prolazu ide gašenje i provera da je stvarno ugašen:
> ```bash
> $ANDROID_HOME/platform-tools/adb emu kill
> (cd ChesskoAndroid && ./gradlew --stop)
> pgrep -f qemu-system || echo "qemu: nema"
> ```
> Faza 6b je ovo prekršila i držala emulator aktivnim satima. Finalni pregled iste faze, sa
> pravilom na snazi, završio je ceo vizuelni deo za **15,5 minuta na ~149%**.
>
> Za razliku od iOS simulatora, **sintetički tapovi na Androidu rade**
> (`adb shell input tap`, koordinate iz `uiautomator dump`), pa nije potreban nijedan
> zaobilazni hak sa zakucavanjem korena. Screenshot: `adb exec-out screencap -p > …`.

## Poznata ograničenja / TODO kandidati

- **Šest parova tokena je ispod WCAG AA u svetloj temi.** Prva tri su nasleđena iz spec tabele i
  identična na obe platforme: `inkMuted`/`ground` 4,36; `inkMuted`/`fill` 4,01;
  `warning`/`surface` 3,61. Četvrti i peti — `success`/`fill` 4,18 i `warning`/`fill` 3,00 —
  **danas nemaju nijedno pozivno mesto**, ali su pinovana da ih budući pozivalac ne može tiho
  pogoršati. Šesti — `warning`/`ground` 3,26 — je dodat u Fazi 6d-2, Task 2: `BoxStyle.RULE`
  kutije u lekcijama (`LessonRenderer.colorFor`) crtaju `DS.warning` tekst nad `DS.ground`, ista
  klasa ograničenja kao `warning`/`surface`. (Pune vrednosti, ne zaokružene:
  `ContrastTest.knownSubAAPairsDoNotGetWorse` na Androidu proverava tačno
  4.359965479387139 / 4.014258257780754 / 3.611752903947211 / 4.184348841952418 /
  2.9989738277199414 / 3.257244931140301 — čuva od pogoršanja, ne od postojanja.) Popravka
  vrednosti bi značila razlaz sa iOS paletom, pa se ne radi.
- **`DS.line` nad `DS.fill` je 1,067 (svetla) / 1,071 (tamna) — ivica koja se ne vidi.** To su
  dve susedne vrednosti iste palete, pa se `DS.line` **ne sme koristiti kao granica NAD
  `DS.fill`**; jedini neutralan token koji tu prelazi WCAG prag 3:1 je `DS.inkMuted` (4,01 /
  4,81). Do Faze 6d-2 su četiri kartice u Podešavanjima (`SettingsView.kt`) radile baš to
  (`.background(DS.fill)` + `.border(1.dp, DS.line, …)`), pa im se ivica nije videla ni u jednoj
  temi — 6d-2 Task 4 ih je prebacio na `DS.surface` bez ivice (vidi bullet ispod). Par i dalje
  čuva `ContrastTest.nonTextPairsOverFillAreDistinguishable`, koji tvrdi oba smera: da
  `inkMuted` prelazi prag i da `line` ne prelazi — ostaje relevantan jer `DS.fill` i dalje nosi
  redove prekidača i drugi hrom gde se ivica ili kontrola crta preko `inkMuted`.
- **Ivice kartica u Podešavanjima (Android) ne postoje, i to je odluka Faze 6d-2 Task 4.**
  Izmereno: `line/fill` 1,067, `line/surface` 1,285, `surface/ground` 1,109 — nijedan par u
  paleti ne daje vidljivu ivicu (WCAG ne-tekstualni prag je 3:1). Umesto da se traži nova boja
  samo za ivicu (razlaz sa iOS paletom), četiri kartice (jezik, statistika, težina, o aplikaciji)
  su izgubile `.border(DS.line)` i prešle podlogu sa `DS.fill` na `DS.surface` — odvajanje od
  `DS.ground` sada nosi isključivo razlika u boji podloge, isto što iOS dobija besplatno od
  nativne grouped liste. Čuva ga `ContrastTest.settingsCardsSeparateFromGround` (puna
  preciznost: 1,1088367563082842 svetla / 1,0942153907723773 tamna) — ako `surface` i `ground`
  ikad postanu ista boja, kartice nestaju bez ijedne druge posledice, i test to hvata.
- **Grana na dva mesta tvrdi suprotno o istom tokenu, i jedno od njih je nepopravljeno.** Task 4
  je ivicu sa kartica u Podešavanjima uklonio zato što `line/surface` daje **1,285** — dakle
  nevidljivo. A neizabran red u biraču vremenske kontrole (`ChessClockView.kt:507`) od te iste
  faze stoji baš na tom paru: `DS.line` ivica nad `DS.surface`, isti 1,285. Finalni pregled je
  to našao; **ruling je da ostaje, ali se zapisuje.** Razlog: zatečeno stanje je bilo jednako
  nevidljivo (`White@8%` nad `#1E293B`), pa ovo **nije regresija ove grane** — a popravka bi
  značila novu odluku o tome kako se redovi u dijalozima uopšte odvajaju (ne samo u satu), što
  je posao za sebe, ne sitnica pred spajanje. Vidljivo na snimku
  `screenshots/40_clock_dark_presets.png`.
- **Šav između kontrolne trake sata i CRNE polovine iznad nje se ne vidi — 1,281 (aktivna) /
  1,050 (mirna), u obe teme.** Nije uvela ni 6d-1 ni 6d-2: fiksna `#1E293B` traka je oduvek
  stajala uz `#121212`/`#2C2C2E`. Šav prema BELOJ polovini ispod je 14,629 i talas ispravki 6d-2
  ga je upravo vratio (bio pao na 1,000 — vidi „peti oblik" u „Android dizajn sistem"). Ne
  postoji jedna boja koja istovremeno prelazi 3:1 prema `#FFFFFF` i prema `#121212` a da ostane
  u registru sata; zatvaranje bi tražilo dve različite hairline ivice (svetlu gore, tamnu dole),
  dakle novi vizuelni element koji iOS nema. Posledica je ista kao za belu polovinu pre
  ispravke: traka nije `clickable`, pa igrač koji cilja donji rub crne polovine pogađa nju.
- ~~**Emoji u Android UI hromu (~37) i u `lessonIcon()` mapi (44) ostaju**~~ — **hrom ZATVOREN
  Fazom 8, lekcijska mapa ZATVORENA Fazom 9** (43 od 49 simbola na `Icons.*` + `tint`; preostalih
  šest je merena odluka, ne rezidual — vidi „Lekcijski glifovi"). Ostatak ovog unosa opisuje
  nalaz Faze 6d-2 koji i dalje važi kao **pravilo**, iako više ne opisuje naš kod.
  Ovo je bio izbor ikonografije, ne boje — van obima Faze 6d-2. Konkretan nalaz iz te
  faze: `🔄` (dugme za resetovanje sata) je pun-kolor emoji glif, i **Skia ignoriše `color=`
  teksta za takve glifove** — tekstualni signal stanja nikad nije radio, ni pre grane. Popravka
  je bila `Modifier.alpha(0.38f)` (M3 standardna vrednost za onemogućeno stanje) umesto obojenog
  teksta — alfa radi na sloju, pa deluje i na emoji. Isti obrazac važi za bilo koje buduće
  dugme sa emoji glifom kome treba stanje omogućeno/onemogućeno.
  **Faza je taj nalaz napravila pa ga sama pregazila:** i posle njega je u `ChessClockView.kt`
  ostalo tri mrtva `color=` na emoji glifovima (`🔄` uz `DS.ink`/`DS.inkMuted`, `ℹ️` i
  `▶️`/`⏸️` uz `DS.accent`/`DS.onAccent`) — kod koji izgleda kao da mehanizam radi. Talas
  ispravki ih je uklonio. Pravilo: **na emoji glif se `color=` ne piše uopšte.**
- **`DS.Space`/`DS.Radius`/`DS.maxBoardSide`/`Type.title/heading/body/caption` (Android) i dalje
  nemaju nijedno pozivno mesto**, ni posle Faze 6d-2. Svi ekrani i dalje zakucavaju `12.dp`,
  `RoundedCornerShape(16.dp)`, `fontSize = 14.sp`; jedina upotreba tipografske skale je
  `Type.mono`. Odloženo odlukom korisnika (vidi „Android dizajn sistem" gore) — primena razmaka,
  radijusa i tipografije kroz sve ekrane je posao veličine cele još jedne kriške.
- **`dynamicColor` je uklonjen sa Android teme** (`ChesskoTheme` u `Theme.kt`). Zatečena verzija
  je bila Android Studio šablon sa `dynamicColor = true`, koji na Androidu 12+ vuče boje sa
  korisnikove tapete — za spec koji traži jedan fiksan akcent to nije funkcija nego greška
  (akcent bi se menjao sa pozadinom telefona).
- **Analiza pretpostavlja da je prvi potez beli.** `GameAnalysis.build` računa
  `byWhite = ply % 2 == 0` i broj poteza iz istog izraza. Za partiju iz početne pozicije to je
  tačno, ali `CurriculumStep.game` nosi opcioni `startFEN` koji je do kraja provučen kroz
  `PathView` → `StepGameView`. Korak zadat iz pozicije u kojoj je **crni** na potezu tiho bi
  označio svaki potez pogrešnom stranom i pogrešnim brojem (`1.e4` umesto `1…e4`) — i u traci
  poteza i u kartici prelomnog poteza. Danas nedostižno: nijedan `game` korak u
  `curriculum.json` nema `startFEN`. Popravka je jeftina (proslediti početni `currentTurn` u
  `build`), ali nije rađena jer bi bila neprovereno rešenje za problem koji ne postoji.
- **Šest klasa poteza preslikava se u četiri boje.** `excellent` i `good` dele `DS.success`,
  `inaccuracy` i `mistake` dele `DS.warning`. Pragovi 20 i 100 su u spec-u navedeni doslovno i
  testirani, ali se u traci poteza ne vide. Nazivi klasa jesu tačni u VoiceOver labeli, pa
  informacija nije izgubljena — samo nije u boji.
- **Eksplicitno postavljanje NNUE mreže u `analyzeGame` je mrtav kod.** `AnalysisViewModel`
  drži sopstvenu instancu `StockfishBridge`-a i ne zove `start()`, pa su `nnueBig`/`nnueSmall`
  uvek `nil` i nijedan `setoption` ne ode motoru. Analiza radi jer sama biblioteka pri
  `Engine.start()` šalje iste dve opcije iz `Bundle.main`. Detalji i razlog zašto nije
  „popravljeno" pred merge — u komentaru na mestu.
- ~~Spec 4.5 je rešen samo za ekran Zadataka~~ — **ZATVOREN U CELOSTI** u Fazi 6b. Ekran
  Učenja više ne nosi sadržaj u kodu: 49 zakucanih srpskih stringova je nestalo, lekcije se
  čitaju iz JSON-a. Provereno na tri načina, jer srpski može da procuri na tri:
  (1) literal sa srpskim slovima van `loc()` u lekcijskim fajlovima — **0**;
  (2) `loc()` sa ključem koji NE POSTOJI u rečniku — **0**, i to sada čuva test
  `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`;
  (3) srpski tekst u ne-`sr` JSON fajlu — **0**.
  Uz to je svih šest lekcija prošetano na engleskom, sa skrolovanjem do kraja, bez ijednog
  srpskog slova. Put (2) je bio stvaran do same završnice Faze 6b: scenario „Promocija" je
  prikazivao ceo srpski pasus na svakom stranom jeziku, jer je literal u kodu bio bez
  „(redovi 8)" a rečnik i iOS sa njim. Stari tekst ovog unosa (o tri stringa u
  `PuzzleViewModel` i 46 u `LearnView`) opisuje stanje pre 2026-09-13.
- **Lekcije se traže `Loc.fileLanguageCode()`, NE `Loc.getLanguage()`.** `getLanguage()` vraća
  `"zh"` (za UI birač jezika), a fajlovi se zovu `board-and-pieces.zh-Hans.json`. Repozitorijum
  koji bi koristio `getLanguage()` tiho bi vratio **engleski svakom kineskom korisniku** — bez
  pada i bez poruke. Čuvaju ga `LocTest.fileLanguageCodeKeepsScriptForChinese` i
  `LessonRepositoryTest.chineseResolvesToItsOwnFileNotEnglish`.
- **`Loc.get` na nepoznat ključ tiho vraća sam ključ**, dakle srpski tekst na svim jezicima.
  Krnj unos se zato ne vidi kao greška nego kao „mešanje jezika". Jedina zaštita je
  `LocTest.everyEntryHasAllEightLanguages`; ne isključivati ga.
- **iOS ima isti propust oko osvežavanja napretka koji je Android u Fazi 6a zatvorio.**
  `PuzzleViewModel.loadDailyPuzzle()` nosi `guard currentPuzzle == nil || isUnavailable`, pa
  `.onAppear` na već učitanom zadatku ne stigne do `reloadPersistedProgress()`. Posle „Resetuj
  statistiku" kvačica pored datuma i isključivanje rešenih zadataka mogu da prežive do restarta.
  Android je to rešio razdvajanjem: `refreshPersistedProgress()` osvežava samo keš, a
  `LaunchedEffect` ga zove pri ponovnom prikazu — zadatak se ne dira, jer je bezuslovno
  ponovno učitavanje već jednom restartovalo napola rešen zadatak.
- ~~**Traka datuma na Androidu prikazuje engleski naziv meseca i u srpskom UI-ju.**~~ —
  **ZATVORENO u Fazi 7 (Task 3).** Uzrok: `PuzzleView.kt` je datum formatirao bez ijednog
  `Locale`, pa je `DateTimeFormatter` uzimao **sistemski** jezik uređaja, ne onaj koji je
  korisnik izabrao u aplikaciji. Rešeno novom funkcijom `localeForDateFormatting(languageCode:)`
  u istom fajlu, koja jezik uzima iz `Loc.getLanguage()`. **Zamka koja se ne bi videla iz koda:**
  `Locale.forLanguageTag("sr")` daje **ćirilicu** („17. септембар 2026.") jer CLDR za goli kod
  `sr` podrazumeva `sr-Cyrl`, a ceo srpski sadržaj aplikacije je latinica — mesec bi bio jedini
  ćirilični tekst na ekranu, gore nego engleski. Zato `sr` ide na `sr-Latn`, ostali kodovi
  direktno. Potvrđeno na emulatoru u obe teme: „17. septembar 2026.".
- **Git LFS: odlučeno da se NE koristi** (2026-09-09). Repo nosi 4 `.nnue` mreže, ~145 MB
  ukupno; najveća je 71,4 MB, ispod GitHub-ovog tvrdog limita od 100 MB, pa push prolazi uz
  upozorenje. Razlozi protiv LFS-a: mreže se nikad ne menjaju, pa glavna korist LFS-a
  (da ne čuva svaku verziju) ovde ne postoji; LFS je trajni namet na svaki klon i CI;
  besplatni tier daje 1 GB saobraćaja mesečno, što je ~14 klonova. Uz to su dve iOS mreže
  bile **već objavljene** na `origin/main`, pa bi ih LFS zahvatio samo prepisivanjem
  objavljene istorije i force-push-om, koji lomi svaki postojeći klon. Ako se ovo ikad
  preispita, jedini pravi kandidat je `ChesskoAndroid/app/src/main/assets/nn-1c0000000000.nnue`
  (71 MB) — ali ga Android kod traži po imenu (`MainActivity.kt:85`), pa bi izbacivanje
  značilo da `git clone` više nije dovoljan da se aplikacija sagradi.
- **Notacija poteza nema razlikovanje dvosmislenih poteza (disambiguation).**
  `GameState.baseNotation` (`GameState.swift:252`) vraća `"\(letter)\(dest)"` bez ijedne
  provere da li i druga istovrsna figura može na isto polje — kad oba skakača mogu na d2,
  istorija poteza oba puta piše `Sd2` umesto `Sbd2`/`Sfd2`. Lekcija o notaciji ovo **ne
  pominje i ne uči pogrešno** (drži se slova, `x`, `+`, `#`, `exd5`, `=D`, `O-O` — sve to
  aplikacija piše tačno), ali njena rečenica „i ova aplikacija ti ispisuje poteze baš tako"
  je za nijansu jača od onoga što motor notacije stvarno radi. Popravka je u `baseNotation`,
  ne u tekstu lekcije.
- Stockfish radi samo sa `nn-37f18f62d772.nnue` (mali); `nn-1111cefa1111.nnue`
  (veliki, ~79MB) opcionalan za jaču igru — skinuti sa stockfishchess.org.
- `positionKey` (`GameState.swift:97-100`) uključuje prava rokade u heš. Partija
  sačuvana starijom verzijom sa zastarelim pravom (top uzet, pravo ostalo) daje
  drugačiji `positionKey` od identične pozicije bez tog prava — brojač za
  trostruko ponavljanje se posle nadogradnje može "razdvojiti" i propustiti remi.
  Bezopasno (retko, ne ruši partiju), ali vredi zapisati.
- ~~Isti bug sa rokadom postoji na Androidu~~ — **POPRAVLJEN**, commit `bc58ab0`.
  `ChesskoAndroid/.../models/GameState.kt` sada pomera POSTOJEĆEG topa u granama
  `CastleKingside`/`CastleQueenside` i oduzima prava i po `move.to`. Android uz to ima
  sopstvene regresione testove (`app/src/test/.../EngineTest.kt`: 6 perft pozicija,
  `rookCapturedOnHomeSquareRevokesCastlingRight`, `noRookOnCornerMeansNoCastleEvenIfFlagStale`).
  Ovaj unos je mesecima stajao kao „čeka Fazu 6" iako je popravka odavno u repozitorijumu —
  zapisano da se vidi kako zastarela dokumentacija preživi sopstveni rok.
- **Traka datuma stoji i nad vežbovnim zadatkom.** Kad se preko „Sledeći
  zadatak" pređe u vežbanje, u traci i dalje piše datum (npr. „Danas"), kvačica
  rešenosti tog DANA ostaje vidljiva i strelice za datum rade. Netačne tvrdnje
  su uklonjene u završnom talasu Faze 2 („Sledeći dan", poruka „Završio si
  zadatak za danas!" i animacija koja vuče pažnju ka strelicama sad su gejtovani
  na `mode == .daily`), a stanje se nikad nije kvarilo (upis u kalendar je od
  početka gejtovan isto). Ostaje samo to što traka opisuje datum, a ne zadatak
  na ekranu.
- **Napredak u rešavanju ne preživi gašenje aplikacije**, i do Faze 2 nije
  preživljavao ni prebacivanje taba (sad preživljava — `.onAppear` učitava samo
  kad zadatka nema).
- `PuzzleViewModel.nextPuzzle()` prosleđuje ceo skup rešenih id-jeva kao
  `excluding`, a `PuzzleRepository` vezuje **jedan SQL parametar po id-ju**. Na
  tavanici od 20.000 rešenih to je 20k bind-ova po dodiru, protiv
  `SQLITE_MAX_VARIABLE_NUMBER` (32.766). Bezbedno je, ali rezervu drži veličina
  baze a ne dizajn — ako baza ikad poraste, ovo treba prebaciti na privremenu
  tabelu ili `NOT EXISTS` podupit.
- Rejting igrača (`StatsManager.puzzleRating`) **nije ograničen** ni sa jedne
  strane. Uzastopni padovi ga zaustave oko ~80, uzastopna rešenja oko ~1520 (tu
  `round(32*(1-E))` padne na 0). Prozor za izbor zadatka je zato clamp-ovan
  posebno, u `PuzzleRepository.practiceRatingWindow(playerRating:)`.
- `build_localizations.py` pri svakom pokretanju regeneriše ceo
  `Localizable.xcstrings` i briše Xcode-ove auto-ekstraktovane ključeve iz
  izvornog koda (bez prevoda — Xcode ih sam vrati pri sledećem build-u), ali
  diff od ~28.000 linija po pokretanju može sakriti stvaran gubitak ako se
  ikad desi.
- **Promocija pešaka na Androidu je do Faze 6c bila neupotrebljiva.** `showPromotion`/
  `confirmPromotion`/`cancelPromotion` su postojali u `GameViewModel.kt` od ranije, ali
  nijedan ekran nije crtao izbor figure — potez do zadnjeg reda je tablu jednostavno
  BLOKIRAO (potez se nikad ne primenjuje, dalji dodiri ne rade), i to ne samo u `game`
  koraku Puta nego i u slobodnoj partiji na tabu Igra, jer je `autoPromoteToQueen`
  podrazumevano `false`. Faza 6c je dodala `PromotionOverlay.kt` (4 figure, poziva
  `confirmPromotion`/`cancelPromotion`) i okačila ga na oba mesta.
- ~~**`locF("Niz: %d dana", 1)` daje pogrešnu množinu na bar 3 od 8 jezika**~~ — **ZATVORENO
  u Fazi 7 (Task 1).** Uzrok: jedan string je nosio i broj i imenicu („Niz: 1 dana", „Streak:
  1 days", „Серия: 1 дней"), a `Loc`/`locF` na Androidu i dalje nemaju nikakvu podršku za
  množinske oblike. Rešeno **zaobilaženjem problema, ne dodavanjem mehanizma**: broj i oznaka
  su razdvojeni u dva `Text`-a u `Row`-u sa `alignByBaseline()` (Compose ekvivalent iOS-ovog
  `alignment: .firstTextBaseline`), tačno kao `Chessko/Views/PathView.swift` (`streakCard`) —
  otvoreno i pročitano pre izmene. Ključ `"Niz: %d dana"` obrisan, nov ključ `"Dana zaredom"`
  je **oznaka bez broja**, pa nijedan jezik nema šta da sklanja. Potvrđeno na emulatoru baš
  pri streak-u **1** (tu je bug bio vidljiv), u obe teme: „**1** Dana zaredom".
  **Sam mehanizam množine i dalje ne postoji** — ako neki budući string mora da nosi broj
  uz imenicu, odluka o `<plurals>`/`stringsdict` ekvivalentu tek predstoji.
- ~~**`BoardView.detectDragGestures` (Android) proguta ceo pokret prsta**~~ — **ZATVORENO u
  Fazi 7 (Task 5).** Uzrok: `detectDragGestures` **troši pokazivač čim se pređe touch slop**,
  bez obzira na to da li gest ima šta da radi. Provera „ima li figure na polju" je postojala,
  ali je stizala prekasno — u `onDragStart`, kad je pokazivač već bio potrošen. Rešeno
  prelaskom na `awaitEachGesture`: polje se ispituje na samom `down`-u, i **ako na njemu nema
  figure ne troši se nijedan `change`**, pa gest propada roditeljskom `scroll`-u. Izmereno na emulatoru, isti gest na oba build-a
  (`DOWN` na praznom polju table pa 12 × `MOVE` nagore): **pre popravke lekcija se ne pomeri
  ni za piksel** (razlika pre/posle gesta van trake stanja: prazna), **posle popravke se
  skroluje**; prevlačenje figure i dalje radi (dama d4 → d6 prevlačenjem).
  - **Zamka u samoj popravci, uhvaćena tek na uređaju:** `PointerInputChange.positionChange()`
    vraća `Offset.Zero` za **već potrošen** `change`, pa se pomeraj mora pročitati **pre**
    `consume()`. Obrnuto (prvi pokušaj) figura ostane zalepljena za polazno polje, potez se
    nikad ne odigra, a ništa ne pukne i nijedan test ne padne. `detectDragGestures` je
    interno radio isti redosled — što se vidi tek kad se otvori njegov izvor.
  - **Druga zamka u istoj popravci, nađena u završnom talasu:** provera „neko drugi je
    preuzeo pokret" (`if (change.isConsumed) return`) stajala je na **`Main` prolazu, gde je
    beskorisna** — na tom prolazu dete uvek ide PRE roditelja, pa roditeljski `verticalScroll`
    još nije ni stigao da potroši. Guard je bio mrtav kod, a rečenica koja je ovde ranije
    stajala („odustaje se čim skrol preuzme") **netačna**. Posledica je bila obrnuta od
    očekivane: jedan isti pokret je i skrolovao ekran **i** menjao stil figura. Izmereno na
    zatečenom `4092070` (`pieceStyle` čitan iz `chessko_settings.xml` preko `run-as`, ne
    odokativno): lekcija „Tabla, figure i kretanje", prevlačenje po praznom polju — `metal →
    flat` uz istovremeni skrol; ekran Igra sa prelivom — `neon → wood` uz skrol. I obično
    skrolovanje lekcije (`input swipe`) je usput prevrtalo stil. Popravka: isti događaj se
    čita **još jednom na `PointerEventPass.Final`**, koji ide obrnutim redom (roditelj pa
    dete), pa je potrošnja skrola tu vidljiva. Cena je zapisana kao ograničenje ispod.
- **Prečica za stil figura od Faze 9 ima drugi okidač — „zadrži pa prevuci".** Faza 7 je
  ostavila da uspravna prečica ćuti svuda gde je tabla u vertikalnom skrolu; Faza 9 je izabrala
  treću opciju koju je sama Faza 7 nabrojala („prečicu vezati za pokret koji se ne sudara sa
  skrolom"). Stanje danas, sve izmereno na emulatoru (vrednosti čitane iz
  `chessko_settings.xml` preko `run-as`, ne sa slike):

  | gde | vodoravno, obično (tema table) | uspravno, obično (stil figura) | uspravno, zadrži pa prevuci |
  |---|---|---|---|
  | Igra, portret (tabla u `verticalScroll`) | **radi** | **ne radi** | **radi** |
  | Igra, pejzaž (tabla van skrolujuće kolone) | **radi** | **radi** | **radi** |
  | Zadaci, portret (nema skrolera) | **radi** | **radi** | **radi** |
  | koraci Puta (nemaju skroler) | **radi** | **radi** | **radi** |
  | lekcijske table (5 mesta) | **ne radi** (novo, v. niže) | **ne radi** | **ne radi** |

  > **Tabela koju je ovo zamenilo bila je NETAČNA, i to od Faze 7 — ne od ove faze.** Stari red
  > je glasio „lekcija / **koraci Puta** → uspravno ne radi". Za korake Puta nije tačno:
  > `StepPracticeView.kt` i `StepGameView.kt` nemaju **nijedan** pogodak na
  > `verticalScroll|LazyColumn|rememberScrollState` (tabla im je `Box(Modifier.weight(1f))` u
  > običnoj `Column`), a `PathView.kt:108` skroluje samo **listu** koraka, koju ekran koraka
  > zamenjuje. Isto važi za Zadatke u portretu. Prečica je bila mrtva u **dva** konteksta
  > (Igra u portretu i lekcije), ne u četiri. Izmereno u Task-u 4 i nezavisno potvrđeno u
  > pregledu. To je i promenilo presudu: gašenje prekidača podrazumevano ili uklanjanje prečice
  > kaznili bi četiri ekrana na kojima funkcija radi zbog jednog na kom ne radi.

  Mehanizam: dug pritisak **bez pokreta** na praznom polju naoružava prečicu
  (`withTimeoutOrNull(longPressTimeoutMillis)`), i od tog trenutka se troši **svaki** pokret, pa
  roditeljski `verticalScroll` više ne može da preuzme gest. Dok se čeka hold ne troši se ništa,
  pa skrol radi normalno; prvi pokret preko `touchSlop`-a odustaje od naoružavanja i vraća gest
  na zatečenu putanju. Naoružavanje je gejtovano **na `swipeToChangePieceStyle`**, ne na „bilo
  koji od dva prekidača" — hold postoji isključivo zbog uspravne ose; sa uključenom samo temom
  bi krao skrol i vibrirao bez ijednog efekta (izmereno: sa `swipeToChangePieceStyle=false`
  uspravan hold-gest ne menja stil **i** strana se skroluje).

  **Cena, izričito:** dug pritisak na praznom polju je od sada **zauzet** i nije više slobodan
  za buduću funkciju (strelice na tabli, premove); gest se ne otkriva sam nego samo iz oznake u
  podešavanjima (zato nov ključ „Zadrži pa prevuci gore/dole (menja stil)" × 8 jezika, stari
  obrisan); oznaka se time **namerno razilazi sa iOS-om**, jer se gest stvarno razlikuje —
  iOS nema Compose-ov odnos deteta i roditeljskog skrola. Vodoravna oznaka je ostavljena kakva
  jeste: za nju obično prevlačenje i dalje radi svuda, pa bi je „zadrži" učinila netačnom u
  drugom smeru. I: `hapticManager.lightImpact()` na naoružavanje je **jedini** signal da je
  prečica aktivna, a `HapticManager.kt:60-61` ćuti kad je taktilni odziv ugašen — takvom
  korisniku gest nema povratnu informaciju dok se prečica ne okine.
- **Zatečen bug koji je Faza 9 popravila: vodoravno prevlačenje po LEKCIJSKOJ tabli menjalo je
  globalnu temu table.** Ista klasa greške koju je Faza 7 zatvorila za **uspravnu** osu —
  preživela je ceo taj talas na **vodoravnoj**, jer uspravnu je slučajno gušio roditeljski
  skrol a vodoravnu niko. iOS to sprečava od ranije: `BoardView.swift:21` nosi
  `allowsStyleSwipe: Bool = true`, a `false` stoji na **tačno pet** mesta u
  `LessonRenderer.swift` (`:194`, `:243`, `:645`, `:755`, `:886`). Android je imao **istih pet**
  ekvivalenata (`LStaticBoard` u `LessonRenderer.kt`; `PieceExplorer`, `OpeningExerciseCard`,
  `MateExerciseCard`, `MatePuzzleCard` u `LearnView.kt`) i **nijedan izuzetak**. Ime i semantika
  su preslikani sa iOS-a. Čuva ih JVM test `LessonBoardsOptOutOfSwipeTest` (čita izvor, isti
  obrazac kao `MainActivitySoundWiringTest`), koji tvrdi **obe strane**: da svih pet lekcijskih
  tabli ima zastavicu (i da ih je i dalje pet, koliko ih ima iOS) i da je nijedna tabla van
  lekcije **nema** — bez druge tvrdnje bi jedan zalutali `allowsStyleSwipe = false` u
  `MainActivity` ugasio prečicu na svim ekranima a test ostao zelen. Dokazan mutacijom.
- **Gest koji počne NA FIGURI i dalje guta skrol** (`BoardView.kt`). Popravka iz Faze 7 je
  oslobodila samo prazna polja; polje sa figurom se i dalje troši na `down`-u, jer je to jedini
  put do prevlačenja figure. Na vežbi iz otvaranja polovina polja nosi figuru, pa korisnik koji
  prstom krene baš sa figure ne može da skroluje — mora da pomeri prst na prazno polje ili van
  table. **Nije regresija** (staro ponašanje je bilo identično, samo je gutalo i prazna polja),
  ali naslov „ZATVORENO" iznad se odnosi na prazna polja, ne na celu tablu.
- **Za regresiju koju je Faza 9 uvela pa sama popravila NEMA TESTA — čuva je samo izveštaj.**
  Regresija: tap na prazno polje (pritisak kraći od `longPressTimeout`) je ostavljao gest-čvor
  **zaglavljen**. Petlja naoružavanja bi pročitala `up`, vratila `false`, i tok bi pao u
  zatečenu petlju koja čeka nov događaj za pokazivač **koji je već podignut**; `block()` ostane u
  toku, pa `awaitEachGesture` nikad ne stigne do `awaitAllPointersUp()` za taj gest, i
  **sledeći gest ovaj `pointerInput` preskoči u celosti** (mehanizam pročitan iz izvora Compose
  1.10.4, `ForEachGesture.kt:79-87`, pa reprodukovan na uređaju). Šteta je bila gora nego
  „potez se ne odigra": progutano prevlačenje **procuri roditeljskom skrolu**, pa korisnik koji
  tapne prazno polje (najčešća radnja na tabli — to je odredišno polje kod igre dodirom) i onda
  povuče figuru dobije **skrol umesto poteza**. Izmereno pre popravke, brojačem „Potez N od N"
  i granicama table čitanim `uiautomator`-om pre svakog poteza: kontrolno prevlačenje
  `2 → 4`, tap-pa-prevlačenje `4 → 4` (progutano), sledeće prevlačenje bez tapa `4 → 6`, igra
  dodirom `6 → 8` (nije pogođena — tapove nosi zaseban `clickable` čvor). Tabla se pri
  progutanom gestu pomerila **232 px** (`[53,426]` → `[53,194]`). Posle popravke, isti build:
  `8 → 10` i `10 → 12`. Popravka je trivijalna — faza naoružavanja razlikuje **tri** ishoda
  umesto dva (`null` = isteklo/naoružano, `true` = prst podignut pre isteka → izlazak iz
  `awaitEachGesture`, `false` = krenuo pokret).
  **Pouka nije „proveri gest" nego gde je zaštita gledala:** brief je izričito tražio da se
  prevlačenje figure ne pokvari, izvršilac je proverio **redosled `positionChange()`/`consume()`**
  (baš mesto gde je Faza 7 imala bug) i tu je bio čist — a regresija je ušla kroz **stanje
  gesta**, ne kroz redosled. Zaštita se branila od **prošle** greške.
  **Oblik testa koji nedostaje, da ga sledeća faza ne izmišlja:** Compose UI test nad
  `BoardView`-om, `performTouchInput { click(); swipe() }` na praznom polju pa prevlačenje sa
  polja sa figurom, uz tvrdnju da se potez odigrao (i, poželjno, da se roditeljski skroler nije
  pomerio). Mora **instrumentisan** (`createComposeRule`) — ponašanje živi u `pointerInput`-u i
  refleksijom mu se ne može prići. To je **nov obrazac u projektu**:
  `SoundReleaseOnLanguageKeyChangeTest` je jedini test koji uopšte koristi `createComposeRule`,
  i infrastruktura je već jednom ujela (`espresso-core` 3.5.1 i 3.6.1 padaju na API 36).
  Uvođenje novog test-obrasca u **zatvaranju** faze je način na koji obim eksplodira, pa je
  svesno odloženo. **Cena ako grešim:** regresiju danas može neko da vrati neprimećeno.
- **Dijalog analize (Android) ne pokriva sistemsku navigacionu traku.** Ispod zatamnjenja se na
  svakom snimku vide presečeni natpisi `Igra / Zadaci / Put`. iOS isti ekran prikazuje kao punu
  `sheet`, pa tamo tab bar nestane. Nije popravljeno jer bi tražilo menjanje tipa dijaloga
  (Compose `Dialog` → `ModalBottomSheet` ili zaseban ekran), što je promena rasporeda pred
  spajanje, ne ispravka. Kozmetika: dijalog je i dalje modalan, dodir po traci ispod ne prolazi.
- **`StatsFacadeTest` (Android) koristi prave singletone nad stvarnim `filesDir`**, za
  razliku od `ProgressStoreTest`, koji izoluje po jedan fajl po testu. Ponovljen prolaz
  istog dana bez `pm clear` (ili deinstalacije) može da pretvori neki test u tautologiju
  (čita stanje koje je sam prethodni prolaz ostavio, ne stanje koje test misli da postavlja).
- **`ProgressSnapshot.toStringSet`/`toStringMap` (Android) nemaju dokaz mutacijom**, za
  razliku od `toIntMap`, koji ga ima (vidi Task 3 changelog). Kod je strukturno identičan
  sa `toIntMap`, pa je rizik nizak, ali tvrdnja nije dokazana istim standardom.
- **`CurriculumParser` (Android) čita `version` kroz `optInt(key, 1)`** — fajl bez tog
  ključa tiho dobija verziju 1. iOS ekvivalent baca ako ključa nema. Razmimoilaženje je
  bezopasno dok god `curriculum.json` ostaje bajt-identičan između platformi (što i jeste,
  vidi „Android čita isti kurikulum"), ali vredi znati ako se dekoderi ikad razdvoje.
- **`loc("Uskoro")` (Android) je mrtav kod.** `routeFor` (`PathView.kt`) je iscrpan `when`
  izraz nad `StepKind` koji od Task-a 7 (korak `game`) više ne može da vrati `null` ni za
  jedan tip koraka — grana `StepState.AVAILABLE -> if (hasRoute) … else loc("Uskoro")` je
  time nedostižna. Ostaje kao zaštita ako se doda peti tip koraka bez ekrana.
- **Nema debug provere dva ručno vođena spiska težina na Androidu.**
  `CurriculumParser.KNOWN_DIFFICULTIES` (`Curriculum.kt`) i `enum GameDifficulty`
  (`SettingsManager.kt`) su dva odvojena spiska (kurikulum mora ostati bez `ui`/`logic`
  zavisnosti); `StepGameView.kt` na nepoznatu težinu tiho pada na `MEDIUM`. iOS ima tačno
  ovaj slučaj pokriven — `PathView.route(for:)` poredi `CurriculumStep.knownDifficulties` i
  `GameDifficulty` u debug build-u „jer bi razlaz inače bio tih" (vidi „Zamke koje su već
  jednom ujele"). Android ekvivalent te provere ne postoji.
- **Naslov poglavlja na Androidu (`PathView.kt`) traži jezik sa
  `fileLanguageCode().substringBefore('-')`**, što od „zh-Hans" pravi „zh". Danas
  nedostižno — `curriculum.json` nosi naslove samo za `sr` i `en` — ali je istog oblika kao
  bug koji `CLAUDE.md` već opisuje za lekcije (`Loc.fileLanguageCode()` vs. `getLanguage()`);
  ako kurikulum ikad dobije kineski naslov, ovo mesto će ga tiho promašiti.
- ~~**`SoundManager` se ne oslobadja kad promena jezika remontira glavni ekran.**~~ —
  **ZATVORENO u Fazi 7 (Task 2).** Rešeno `DisposableEffect(languageKey)` unutar **istog**
  `key(languageKey) { ... }` opsega koji pravi oba modela (`MainActivity.kt`), koji na
  `onDispose` zove `releaseSounds()` na oba — isti obrazac koji `StepPracticeView`/
  `StepGameView` nose od Faze 6c. Opseg je ono što je bilo lako promašiti: `DisposableEffect`
  izvan `key`-a oslobodio bi `SoundPool` koji ekran **još koristi**. Zato test
  (`SoundReleaseOnLanguageKeyChangeTest`, instrumentisan) tvrdi **obe** strane — stari modeli
  oslobođeni **i** novi nisu. **Taj test ne dodiruje `MainActivity`** (preimenovan je baš zato:
  staro ime `MainActivitySoundLifecycleTest` je obećavalo više nego što pruža) — on
  rekonstruiše obrazac u sopstvenom `setContent`-u i prošao bi i da neko obriše
  `DisposableEffect` iz `MainActivity.kt`. Da veza i dalje stoji u samom ekranu čuva
  `MainActivitySoundWiringTest` (JVM, čita izvor — isti obrazac kao
  `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`); **dokazan mutacijom**: sa obrisanim
  `DisposableEffect` blokom pada, sa vraćenim prolazi. Do modela se iz testa ne može doći ni
  refleksijom ni test tagom jer se prave kroz `remember { … }` u kompoziciji, ne kroz
  `ViewModelStore` — zato provera izvora, a ne ponašanja. Izmereno na emulatoru preko `dumpsys audio`, ne odokativno: u
  trenutku promene jezika prvo se stvore **dva nova** `SoundPool` player-a, pa se **dva stara
  oslobode**, a potez odigran posle toga daje `event:started` na novom player-u — zvuk radi,
  bez ijedne `SoundPool` greške u `logcat`-u. Opis zatečenog stanja (zašto je do toga došlo)
  ostaje ispod, jer obrazac `remember { ViewModel(...) }` u `setContent`-u i dalje stoji:
  `MainActivity.kt:100-101` pravi `gameViewModel` i `puzzleViewModel` kroz `remember { ... }`,
  unutar `key(languageKey) { ... }` bloka — a do Faze 7 tu nije bilo nijednog
  `DisposableEffect`-a. Svaki od tih
  modela pravi **sopstveni** `SoundManager` (`PuzzleViewModel.kt:54`, `GameViewModel.kt:87`)
  — `SoundManager` nije singleton. Posto se ne prave kroz `ViewModelStore`, `onCleared()` im
  se nikad ne izvrši, pa `soundManager.release()` ne radi. Kad korisnik promeni jezik,
  `key(languageKey)` odbacuje ceo blok i pravi NOVE modele sa novim `SoundPool`-ovima;
  stari ostaju neoslobodjeni do gašenja procesa. **Ovo je ZATEČENO, ne uvedeno Fazom 6c:**
  `git show 17dd16e:.../MainActivity.kt` već ima isti `remember { GameViewModel(...) }`
  obrazac, a `git diff 17dd16e..HEAD -- .../MainActivity.kt` ne pokazuje nijednu izmenu tog
  obrasca. Ekrani koraka (`StepPracticeView`, `StepGameView`) su u Fazi 6c dobili
  `DisposableEffect` + `releaseSounds()` baš zbog ovog obrasca; **`MainActivity` nije**,
  jer je van obima te faze — i tako je ostalo do Faze 7, koja je primenila istu, jeftinu
  popravku (po uzoru na `ChessClockView.kt:129-133`, koji to radi od ranije). Uticaj je bio
  uzak: promena jezika je redak događaj, a curenje je bilo ograničeno na po jedan `SoundPool`
  za Igru i Zadatke.
- **Analiza (Android) nasleđuje oba zapisana iOS ograničenja analize, doslovno.** `byWhite = i
  % 2 == 0` u `models/MoveAnalysis.kt` je isti izraz kao iOS `GameAnalysis.build` — ista
  pretpostavka da je prvi potez beli, isti razlog (nijedan `game` korak Puta danas nema
  `startFEN`, pa je nedostižno), ista jeftina a neurađena popravka. `ui/AnalysisView.kt`
  preslikava isti skup od šest klasa poteza u četiri boje (`BEST` na `DS.accent`,
  `EXCELLENT`/`GOOD` dele `DS.success`, `INACCURACY`/`MISTAKE` dele `DS.warning`, `BLUNDER` sam
  na `DS.danger`) — nazivi klasa i dalje tačni u `contentDescription`, samo ne u boji.
- **`on_update_full` (`cpp/stockfish/uci.cpp:624-637`) ubacuje ` wdl X Y Z` IZMEĐU ocene i
  `lowerbound`/`upperbound` kad je `UCI_ShowWDL` uključen.** `UCIScoreParser.parse` gleda TAČNO
  `tokens[scoreIdx + 3]` za `"lowerbound"`/`"upperbound"`; sa WDL-om uključenim taj token bi bio
  `"wdl"`, provera bi promašila, i nekonačna ocena (fail-high/fail-low usred iterative
  deepening-a) bi tiho prošla kao konačna. `UCI_ShowWDL` je podrazumevano `false` i
  `StockfishEngine.kt` ga nigde ne postavlja, pa je danas nedostižno — ali vredi znati ako se
  ta opcija ikad uključi (npr. radi prikaza % pobede/remija/poraza u UI-ju).
- ~~**Završena partija (mat, predaja, remi) ne preživi rekreaciju `Activity`-ja na Androidu**~~
  — **ZATVORENO u Fazi 7 (Task 4).** Uzrok je opisan ispod i nije se promenio: sačuvani zapis
  nije nosio `status`, a `GameState.fromFEN` uvek vraća `GameStatus.Playing`. Rešeno tako što
  se `status` od sada serijalizuje **odvojeno od FEN-a** (`statusToJson`/`statusFromJson` u
  `GameViewModel.kt`), sa svim podacima (boja za šah/mat/predaju, tačan `DrawReason`). Za
  zapise sačuvane PRE te izmene `load()` pada na novu `GameState.statusFromPosition` — javnu,
  `android.*`-free funkciju koja mat/pat/šah preračunava iz pravila; ona namerno **ne**
  prepoznaje remi po 50 poteza/ponavljanju/nedovoljnom materijalu ni predaju, jer se ta stanja
  ne vide iz gole pozicije. **Izvođenje statusa iz FEN-a je odbijeno, ne zaboravljeno** — FEN
  za to nema polje, a iOS ekvivalenta nema jer tamo ceo `GameState` ide kroz `Codable`.
  Potvrđeno na emulatoru: predaja pa promena sistemske teme — traka i dalje kaže „Predaja!
  Izgubio si.", dugme „Analiziraj partiju" **ostaje** (provereno u obe teme).
  **Asimetrija koju je baš ta popravka učinila vidljivom:** `status` se sada vraća tačno samo za
  **tekuće** stanje. Unosi u `history` se i dalje rekonstruišu golim `GameState.fromFEN`
  (`GameViewModel.kt:780-782`), koji vraća `status = Playing` i **prazne** `moveNotations` — pa
  prvi „Vrati potez" posle ponovnog otvaranja aplikacije da poziciju bez oznake šaha i sa
  **praznim** panelom „Potezi". Unosi istorije nikad nisu nosili ni status ni notacije, dakle
  nije uvedeno Fazom 7; do nje su i tekuće stanje i istorija bili podjednako krnji, pa se
  razlika nije videla. Nije popravljeno: značilo bi serijalizovati `status` i `moveNotations`
  po svakom unosu istorije, što je promena formata zapisa pred spajanje.
  Opis zatečenog stanja:
  `MainActivity` pravi `GameViewModel` kroz `remember { GameViewModel(...) }`, ne
  `rememberSaveable`/`ViewModelStore`, pa svaka rekreacija napravi NOV primerak čiji `init`
  učita sačuvanu partiju sa diska (`GameViewModel.load()`). Sačuvani JSON (`save()`/`load()`,
  `GameViewModel.kt`) do Faze 7 **nije nosio `status` polje uopšte** — pozicija se rekonstruiše kroz
  `GameState.fromFEN(savedFen)`, koji (isti uzrok koji `CLAUDE.md` već beleži za
  `terminalEval` u analizi) UVEK vraća `GameStatus.Playing`. Partija završena predajom se
  posle rekreacije vrati u stanje „u toku" — istorija poteza ostaje netaknuta, ali traka
  „Predaja! Izgubio si." i dugme „Analiziraj partiju" (koje zahteva `isGameOver`) nestanu dok
  se partija ponovo ne završi. Potvrđeno direktno, dvaput zaredom: predaja pa promena sistemske
  teme je oba puta vratila ekran na „Tvoj potez". Zatečen defekt (nije uveden ovom fazom), ali
  ga Faza 6e čini vidljivijim jer krije baš dugme koje je ova faza dodala.
- ~~**Polutez u Android FEN-u bio je zakucan na `0`**~~ — **ZATVORENO u Fazi 7 (Task 5), van
  prvobitnog obima faze.** `GameState.fen` (Kotlin) je peto polje FEN-a uvek pisao kao `"0"`,
  dok iOS šalje pravi `halfmoveClock` **od Faze 0** (`Chessko/Models/GameState+FEN.swift`).
  Nije bila kozmetika, jer taj FEN nije samo zapis — ide (1) Stockfish-u na **svaki AI potez**
  (`GameViewModel`), (2) Stockfish-u na **svaku poziciju u analizi** (`AnalysisViewModel`), i
  (3) u sačuvanu partiju (`GameViewModel.save()`), odakle ga `fromFEN` čita nazad. Posledice:
  motor nikad nije znao koliko je pozicija blizu pravila 50 poteza, a **brojač se tiho
  resetovao pri svakom ponovnom otvaranju aplikacije** — partija na 99 poluteza posle restarta
  je kretala od nule. `fromFEN` je polje čitao ispravno sve vreme (`parts[4]`); pisanje je bilo
  to što laže. Popravka je jedna linija; čuva je `GameStateFenHalfmoveTest` (5 JVM testova,
  dokazano mutacijom — sa vraćenom nulom pada 4 od 5). Šesto polje (broj poteza) ostaje `"1"`
  na **obe** platforme; ne vodi se nigde.
  - Veza sa stavkom iznad: za **stare** zapise (bez `status` polja) `statusFromPosition` i
    dalje ne prepoznaje `Draw(FiftyMoves)` — status se ne izvodi iz brojača nego iz
    eksplicitnog polja. Ali učitana partija sada bar **nastavlja da broji tamo gde je stala**.
- **`LessonRenderer.kt:235` crta hardkodovan `⚠︎` direktno u `Text`, van `SYMBOL_TO_GLYPH` mape
  i van `Loc.kt` rečnika.** (Bio `:193` do Faze 9, koja je fajl produžila; linija je ista.)
  Kad se FEN lekcijskog `board` bloka ne parsira, `LStaticBoard` prikaže
  `"⚠︎ $fen"` — glif koji migracija Faze 8 nije ni videla (mapa/rečnik su pretraživani odvojeno
  od doslovnih `Text(...)` literala). **I nije pokvaren: glif je `U+26A0` + `U+FE0E`, dakle
  VARIATION SELECTOR-15 — tekstualna, monohromatska prezentacija, koja `color=` PRIMA**, i kod mu
  stvarno postavlja `DS.danger`. To je **izuzetak od nalaza o Skia, ne njegova potvrda**: pun-kolor
  emoji ignoriše boju, tekstualna varijanta istog znaka ne ignoriše. Ostaje van šablona koji
  ostatak baze prati (`Icon` + `tint`), ali je **ispravan kod, ne defekt** — ko ga bude menjao, nek
  to radi zbog doslednosti, ne zato što misli da ne radi.
- **Pravilo dekorativno/opisno je Faza 8 platila trinaest puta pre nego što je stvarno primenjeno.**
  Kontroler je u tri taska (1, 2, 3) unapred zadao `contentDescription` ključeve izvodeći ih iz
  **izgleda** ikone, ne iz njene **upotrebe** — pa je pet ikona dobilo opis koji **ponavlja**
  vidljiv tekst odmah pored (kod dva slučaja opis je bio doslovno isti string kao tekst dugmeta),
  i tri su dobile opis iako je susedni tekst već govorio isto. Svih osam je ispravljeno na
  `contentDescription = null`. Pravilo koje je iz toga izvedeno i koje važi ubuduće:
  > Ikona uz vidljiv tekst istog značenja je **dekorativna** (`contentDescription = null`).
  > Ikona koja stoji **sama** traži opis.
  Uz nijansu koju je Task 4 dodao: „uz" znači stvarno uz. U `LearnView.kt` tri značke
  (`CheckCircle`/`EmojiEvents` u vežbama) su **opisne** iako izgledaju kao dekorativni trofej iz
  Task-a 2 — između značke i odgovarajućeg statusnog teksta stoji **cela šahovska tabla**
  (`BoardView` je 12 linija posle ikone, sledeći `Text` tek 35), pa opis ne ponavlja ništa.

  **Preostalih pet je našao tek finalni pregled**, i to je deo pouke: pravilo je uvedeno u
  Task-u 3, a **Task 2 se niko nije vratio da pomete**, pa su četiri ikone u `ActionsRow`
  (`MainActivity.kt`) ostale sa opisom koji ponavlja tekst pored sebe — jedna od njih i sa
  pogrešnim imenom radnje („Pokušaj ponovo" na dugmetu „Reset"). Peta je `loc("Trofej")`, opis
  izveden iz izgleda značke umesto iz stanja koje ona saopštava. Pouka nije o pravilu nego o
  njegovoj primeni: **kad se pravilo promeni usred faze, pometu se svi fajlovi koje je stari
  spisak dodirnuo, ne samo prijavljena instanca.**

  I jedna cena koju pravilo nosi: `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary` čita
  izvor **kao tekst**, pa ime uklonjenog ključa napisano u komentaru kao poziv (`loc` +
  zagrada) obara build. Zato se u komentarima ime ključa piše bez `loc` ispred.

## Next Steps / Roadmap (ideje za unapređenje)

Prioritet poređan po vrednosti; završene stavke označene su `[x]`.

### 1. Gameplay funkcije
- [x] **Izbor figure pri promociji** — `promotionMove`/`showPromotion` u VM;
      `promotionOverlay` u GameView sa 4 figure (D/T/L/S); `confirmPromotion(_:)` i
      `cancelPromotion()` metode u VM.
- [x] **Remi pravila** — ponavljanje pozicije (3x), pravilo 50 poteza, nedovoljan materijal.
      `DrawReason` enum; `GameStatus.stalemate` → `.draw(DrawReason)`. `halfmoveClock` i
      `positionHistory` u `GameState`; `positionKey` za repetition detection; `applying()`
      detektuje sve slučajeve pre skupog generisanja poteza.
- [x] **Undo poteza** — `history` stack u VM; undo skida AI + igračev potez; dugme u toolbar.
- [x] **Igranje crnim + rotacija table** — `playerColor` je `var`; `isFlipped` rotira prikaz;
      color picker overlay pri "Nova igra"; kad igrač bira crnog, AI (beli) igra prvi.
- [x] **Izbor težine AI** — `GameDifficulty` (Lak/Srednji/Težak/Stockfish) u toolbar
      menu; Stockfish 17 via `chesskit-engine` SPM integrisan.
- [x] **Čuvanje/nastavak partije** — auto-save u `UserDefaults` posle svakog poteza (igrač,
      AI, undo); auto-load pri startu; `clearSave()` pri "Nova igra".

### 2. Jačina AI-ja
- [x] **Move ordering** (MVV-LVA) — uzimanja sortirana po vrednosti žrtve/napadača.
- [x] **Quiescence search** — nastavak pretrage samo uzimanjima na kraju dubine (easy: 0, medium: 4, hard: 6 ply).
- [x] **Endgame king tabela** — `kingEndgamePST`, aktivira se kad nema dama ili ostane malo figura.
- [x] **Transpoziciona tabela** (Zobrist hashing) — `ZobristTable.swift`; inkrementalni heš, 256K TT.
- [x] **Iterative deepening + time limit** — depth 1→maxDepth, čuva best move iz prethodne iteracije.

### 3. UX / polish
- [x] Haptika (potez / uzimanje / šah / mat / undo) via `UIFeedbackGenerator`.
- [x] Pristupačnost (VoiceOver labele za polja i figure) — `a11yLabel/Hint/Traits` u `BoardView`;
      `srbName` na `PieceType`, `srbAdjective` na `PieceColor`, `a11yLabel` na `ChessPiece`;
      animacijski overlay elementi skriveni `.accessibilityHidden(true)`.
- [x] Istorija poteza u srpskoj algebarskoj notaciji — `PieceType.srbNotationLetter` (K/D/T/L/S);
      `GameState.moveNotations [String]` paralelno sa `moveHistory`; `baseNotation(for:in:)` u
      `applying()` (pre primene poteza za info o figuri, sufiks +/# posle statusа);
      `MoveHistoryView` sa `ScrollViewReader` (auto-scroll na poslednji potez, monospaced font,
      naizmenične pozadine redova); prikazuje se u `GameView` ispod status bara.
- [x] Čuvanje/nastavak partije — `Codable` na svim modelima; `SavedGame` struct u VM; UserDefaults.
- [x] **Lokalizacija na 8 jezika** — String Catalog (`Chessko/Localizable.xcstrings`), izvorni
      jezik srpski (`sr`), prevodi: en/fr/de/it/ru/zh-Hans/hi. Generiše se skriptom
      `build_localizations.py` (od Faze 3 samo UI: 241 ključ × 8 jezika).
      Notacija poteza (K/D/T/L/S) namerno NIJE lokalizovana (tehnička + izbegava stale save-ove);
      "En passant" ostaje univerzalni termin.
- [x] **Izbor jezika u aplikaciji** — hamburger meni (gore-levo na ekranu Igra) → `SettingsSheet`
      sa sekcijama Težina + Jezik. Live prebacivanje bez restarta preko `LocalizationManager`
      (`Logic/LocalizationManager.swift`): `Bundle.main` se zameni `LocalizedBundle` podklasom
      koja preusmerava `localizedString(...)` na izabrani `<code>.lproj`; `ContentView` se
      rebuilduje preko `.id(localization.refreshID)`. Opcija "Sistem" = prati telefon.

### 4. Kvalitet koda
- [x] **Test target + perft testovi za `MoveGenerator`** — SwiftPM paket u korenu
      (`Package.swift`) koji kompajlira postojeće izvorne fajlove po putanji, nezavisno
      od Xcode projekta; 11 testova (perft za svih 6 standardnih pozicija i 4 testa
      prava rokade). Pokreće se sa `swift test` (vidi „Testovi").
- [x] `ChessMove ==` sada poredi i `flag` (uz `from`/`to`) — uklonjena krhka logika
      oko promocije. Sva poklapanja poteza u kodu ionako koriste eksplicitno `from`/`to`
      poređenje; jedini whole-move `==` je TT move ordering u `ChessAI`, gde je
      uključivanje `flag`-a korektnije (TT promociju poklapa tačno, ne sve četiri).

## Pravila rada za Claude-a

1. Na početku svakog zadatka pročitaj ovaj fajl.
2. Poštuj postojeće konvencije: vrednosne strukture, immutable `GameState`,
   `applyingForSearch` u pretrazi (NIKAD `applying` u rekurziji — beskonačna petlja).
3. UI tekst je na srpskom — zadrži ton.
4. Na kraju svakog prompta ažuriraj **Changelog** (i ostale sekcije ako se nešto
   strukturno promeni).

## Changelog

- **2026-06-17** — Kreiran CLAUDE.md nakon analize cele kodne baze (Models, Logic,
  ViewModels, Views, build settings). Aplikacija funkcionalna: šah vs AI, igrač beli.
- **2026-06-17** — Dodata "Next Steps / Roadmap" sekcija sa idejama za unapređenje
  (gameplay funkcije, jačina AI, UX, kvalitet koda). Ništa još nije implementirano.
- **2026-06-19** — SVG figure izvučene iz `design-reference.svg` u `Assets.xcassets`
  (12 imageset-a: `piece_{white|black}_{type}`). `PieceImageView` umesto Unicode
  simbola. Boje table: squareLight `#e9ebde`, squareDark `#8592af`, bg `#17234f`.
  `create_xcode_project.py` popravljen (Assets.xcassets u Resources build phase).
  Dodat izbor težine (`GameDifficulty`) u toolbar menu.
  Stockfish 17 integrisan via `chesskit-engine` SPM; `StockfishBridge` actor u
  `Logic/StockfishBridge.swift`; `GameState+FEN.swift` generiše FEN string.
  Bug fix: `EvalFile` se eksplicitno postavlja na `nnueBig ?? nnueSmall` jer
  Stockfish 17 pada ako ne pronađe podrazumevani `nn-1111cefa1111.nnue`.
  ChessKitEngine: `responseStream` je nil pre `start()`; `rawValue` je tagged format
  (`<bestmove> d7d5 <ponder> e2e4`); stream se mora uzeti posle `start()` + `isRunning`.
- **2026-06-19** — Undo poteza (`history` stack, undo briše AI + igračev potez, toolbar dugme).
  Haptika via `UIFeedbackGenerator`: selekcija, potez, uzimanje, šah, mat, undo.
- **2026-06-21** — Animacija uzimanja figura: `FlyingCapture` struct u `GameViewModel`;
  `flyingCapture` property isključuje figuru iz `CapturedPiecesView` dok leti, BoardView
  prikazuje overlay uhvaćene figure na polju dok napadač klizi (ispod `AnimatingPieceView`),
  po 280ms overlay nestaje a figura se spring animacijom pojavljuje u sekciji „uzeto".
  Izbrisana labela „Uzeto:" iz `CapturedPiecesView` (zamenjena novim `capturedByColor` i
  `flyingCapture` parametrima).
- **2026-06-21** — Čuvanje partije: `Codable` dodat na `PieceType`, `PieceColor`, `ChessPiece`,
  `Position`, `ChessMove`, `MoveFlag` (custom), `GameStatus` (custom), `GameState`, `GameDifficulty`.
  `SavedGame` struct u `GameViewModel` sa `HistoryEntry`; auto-save u `UserDefaults` posle svakog
  poteza i undo-a; auto-load u `init()`; brisanje pri "Nova igra".
- **2026-06-21** — Remi pravila: `DrawReason` enum (stalemate/fiftyMoves/repetition/insufficientMaterial);
  `GameStatus.stalemate` zamenjen sa `.draw(DrawReason)` (backward-compat decode za stare save-ove);
  `GameState` dobija `halfmoveClock` (reset na pion/uzimanje, draw na 100), `positionHistory [String:Int]`
  (CoW — ne kopira se u search-u), `positionKey` (134-char string: tabla+potez+rokade+ep);
  `initial()` i `debugPromotion()` seeduju positionHistory sa count=1; `applying()` proverava
  remi uslove pre generisanja poteza; `isInsufficientMaterial()` private static helper.
  `ChessAI`, `GameViewModel`, `GameView` ažurirani za novi enum.
- **2026-06-21** — VoiceOver pristupačnost: `srbName` na `PieceType`, `srbAdjective` na `PieceColor`,
  `a11yLabel` na `ChessPiece`. `BoardView` daje svakom polju `.accessibilityElement(children: .ignore)`
  + label ("e4, beli pešak, izabrano") + hint ("Dupli dodir za potez") + trait (.isButton/.isSelected).
  Overlay elementi (animacija klizanja, flying capture) su `.accessibilityHidden(true)`.
- **2026-06-21** — Istorija poteza: `PieceType.srbNotationLetter` (K/D/T/L/S); `GameState.moveNotations`
  paralelno sa `moveHistory`; `baseNotation(for:in:)` private static u `applying()` — čita poziciju
  pre poteza, dodaje +/# na osnovu statusa; `MoveHistoryView` (ScrollViewReader, auto-scroll,
  monospaced, 136pt visina); prikazuje se u `GameView` ispod status bara.
- **2026-06-21** — Zvuk pomeranja figura: novi `Logic/SoundManager.swift` sa `AVAudioEngine`;
  dva sintetizovana PCM bafera (move: 480 Hz / 0.10s, capture: 270 Hz / 0.17s), deterministički
  XorShift64 šum, `.ambient` audio sesija (poštuje mute switch). Pozivi dodati u
  `GameViewModel.execute(_:)` (move/capture/check/mat/remi), `GameViewModel.undo()` i
  `PuzzleViewModel.apply(move:)`. Uz svaki zvuk ostaju i postojeći haptici.
- **2026-06-21** — Dnevni puzzle ekran: `BoardView` refaktorisan na eksplicitne parametre
  (više ne zavisi od `GameViewModel`); `GameState.fromFEN(_:)` parser; `ChessMove.fromUCI(_:in:)`
  helper; `Models/ChessPuzzle.swift`; `ViewModels/PuzzleViewModel.swift` (učitava puzzle po
  datumu — `ordinality(of:.day in:.era)` kao deterministički index, detektuje boju igrača iz
  FEN-a, vodi tok rešavanja, `showSolution()` auto-odigrava); `Views/PuzzleView.swift` (rating
  badge, lokalizovane teme, status kartica u boji); `ContentView` → `TabView` (Igra / Zadaci).
- **2026-06-21** — Onboarding/učenje: novi `Views/LearnView.swift` + `ViewModels/LearnViewModel.swift`;
  treći tab "Učenje" (graduationcap.fill) u `ContentView`; `LearnViewModel` drži `selectedPieceType`,
  `piecePosition`, `activeScenario?`; slobodno premeštanje figure tapom po bilo kom polju;
  za pešaka se dodaju dummy crni pešaci na dijagonalama da se vide uzimanja; specijalni scenariji:
  rokada (kralj+topovi na startnim pozicijama, oba prava rokade), en passant (beli pešak e5, crni d5,
  ep target d6), promocija (beli pešak e7); info kartica sa opisom i brojem mogućih poteza;
  `LearnViewModel` podignut u `ContentView` (nema janka pri tab prelasku).
- **2026-06-21** — Sistem lekcija: `LearnView` prepisana u listu 4 kartica; novi `LessonDetailView.swift`
  sa punim sadržajem svih 4 lekcija (Tabla i figure / Otvaranja / Središnjica / Završnica);
  Lekcija 1 embeds interaktivni piece explorer (`LearnViewModel`) sa piece picker-om i BoardView;
  pomoćne private view komponente: `L_SectionHeader`, `L_Para`, `L_Bullet`, `L_Box`, `L_PieceRow`,
  `L_NumberedRule`, `L_OpeningCard`, `L_PieceValueTable`; `LessonInfo` struct sa 4 predefinisana unosa.
- **2026-06-21** — Fix tab-switch animacije: `GameViewModel` i `PuzzleViewModel` podignuti u `ContentView`
  kao `@State` da se inicijalizuju pri startu aplikacije (ne tokom animacije tab prelaza).
  `GameView` i `PuzzleView` primaju ViewModel kao parametar; `#Preview` blokovi ažurirani.
- **2026-06-21** — Bug fix zvuka: `Haptics` metode dobile `@MainActor` (Swift 6 upozorenja o non-isolated kontekstu);
  `SoundManager` sada poziva i AI poteze u `triggerAI()` (ranije samo igrač); `create_xcode_project.py`
  proširen da prikuplja `.mp3` fajlove iz `Chessko/` i dodaje ih u `PBXFileReference`, `PBXBuildFile`,
  group section i `PBXResourcesBuildPhase`.
- **2026-06-21** — Zobrist hashing + transpoziciona tabela + iterative deepening s vremenskim limitom:
  novi fajl `Logic/ZobristTable.swift` sa `ZobristTable` singletonom (deterministični XorShift64 RNG,
  inkrementalno ažuriranje heša po tipu poteza), `TTEntry` struct (20B: hash/move/score/depth/flag),
  `TranspositionTable` klasa (256K unosa ≈ 5MB, `Int(bitPattern:)` za bezbedan indeks),
  `SearchContext` klasa (TT + tajmer, proverava vreme svakih 2048 čvorova).
  `ChessAI` prepisan: `Difficulty` dobija `timeLimit` (0.5/1.5/3.0s) i `maxDepth` (4/8/20) umesto
  fiksne dubine; `bestMove()` pokreće iterative deepening petlju (depth 1→maxDepth), čuva rezultat
  prethodne iteracije, prekida na matu ili isteku vremena; `searchRoot()` vraća `(move, score)?`;
  `negamax()` prvo proverava TT (exact cutoff / bound update), koristi TT potez za ordering, čuva
  rezultat po izlasku (exact/lowerBound/upperBound); `quiescence()` proverava vreme po čvoru.
- **2026-06-24** — Interaktivne vežbe otvaranja u Lekciji 2: novi `ViewModels/OpeningExerciseViewModel.swift`
  (`@Observable @MainActor`, igrač=beli, crni odgovara po skriptovanoj UCI sekvenci, progress pills,
  detekcija pogrešnog poteza bez promene pozicije, `reset()`); novi `OpeningExerciseCard` u `LessonDetailView.swift`
  (progress bar u vidu malih pilula, status u boji, crveni border za pogrešan potez, zeleni za rešeno).
  Tri vežbe: Španska partija (5 poteza), Italijanska partija (5 poteza), Sicilijanska odbrana (7 poteza).
  `lesson2Openings` promenjen iz statičnih `L_OpeningCard` u tri `OpeningExerciseCard`. `project.pbxproj` ažuriran.
- **2026-06-24** — Interaktivne vežbe elementarnih matova u Lekciji 1: novi `ViewModels/MateExerciseViewModel.swift`
  (`@Observable @MainActor`, igrač=beli, AI=crni na `.easy` težini, `isThinking` gejtuje UI, `reset()` vraća
  startnu poziciju); novi struct `MateExerciseCard` u `LessonDetailView.swift` (header sa ikonom i hintom,
  `BoardView`, status bar sa `ProgressView` dok AI razmišlja, dugme Ponovo, zeleni border + checkmark kad je rešeno).
  Tri vežbe ugrađene u lekciju 1 umesto statičnih `L_Box` opisa: Kralj+Top (FEN: Ke4/Ra1/ke6 ~ 10 poteza),
  Kralj+2Lovca (Ke1/Bc1/Bf1/ke8), Kralj+Dama (Ke1/Qd1/ke8). `project.pbxproj` ažuriran (FileReference +
  BuildFile u oba targeta + ViewModels group).
- **2026-06-24** — UX lekcija 1: sekcije "Jedinstven!" (skakač) i "Uslovi za rokadu" (rokada) prevedene
  na žuti `L_Box` dizajn (isti kao "Promocija"); "Specijalna pravila" karta redesajnirana sa žutim akcentom.
- **2026-06-24** — Mini finalni test u Lekciji 4: novi `MatePuzzleCard` struct u `LessonDetailView.swift`
  (koristi `OpeningExerciseViewModel` sa FEN startnom pozicijom, badge "Mat u N", trofej ikona kad rešeno,
  žuta/crvena/zelena boja statusa i bordera); `OpeningLine` proširen sa `solvedMessage`, `wrongMessage`,
  `playingPrompt`, `startFEN` (sve default vrednosti — backward-compat); `OpeningExerciseViewModel` init
  i `reset()` koriste `startState()` helper koji čita `line.startFEN`. Pet zadataka verifikovanih python-chess:
  1. Mat u 1 — Dama zadnja linija (`6k1/5ppp/8/8/8/8/3Q4/4R1K1`, key `d2d8`);
  2. Mat u 1 — Top zadnja linija (`6k1/5ppp/8/1R6/8/8/8/6K1`, key `b5b8`);
  3. Mat u 2 — Žrtva Topa, Dama daje mat (`2r3k1/5ppp/8/8/Q7/8/8/4R1K1`, `e1e8→c8e8→a4e8`);
  4. Mat u 2 — Lovac tera, Top daje mat (`5k2/5ppp/8/4B3/8/8/8/4R1K1`, `e5d6→f8g8→e1e8`);
  5. Mat u 2 — SparkChess (Greet–Hanley, Liverpool 2008): Žrtva Dame, Lovac daje mat
     (`r1bq2r1/b4pk1/p1pp1p2/1p2pP2/1P2P1PB/3P4/1PPQ2P1/R3K2R`, `d2h6→g7h6→h4f6`).
- **2026-06-24** — Uniformisane boje u `LessonDetailView`: svi `L_SectionHeader`, `L_Bullet` i `L_Box`
  sada koriste `lesson.accentColor` umesto nasumičnih `.blue`/`.green`/`.orange`/`.purple`/`.yellow`.
  Izuzeci: `.yellow` za posebne „zlatna pravila" kutijice (Promocija, Jedinstven, Uslovi za rokadu,
  Jedno drži dvoje, Šah-upozorenje), `.red` samo za Šah-Mat i greške (xmark bullets u L2).
  Popravka veličine table u GameView: VStack zamenjen `ScrollView { VStack }` — tabla uvek dobija
  `width × width` jer ScrollView predlaže ∞ visinu, pa se `.aspectRatio(1, contentMode: .fit)`
  uvek razrešava na širinu umesto na `min(width, remainingHeight)`.
- **2026-06-25** — `ChessMove ==` sada uključuje i `flag` (`lhs.from == rhs.from && lhs.to == rhs.to
  && lhs.flag == rhs.flag`) umesto samo `from`/`to`. Uklonjena krhka logika oko promocije
  (ranije radilo slučajno jer je dama prva u listi promocija). Audit: sva poklapanja poteza u
  VM/Puzzle/Opening exercise koriste eksplicitno `from`/`to` poređenje (`$0.to == position`,
  `move.from == expected.from`), tako da nisu pogođena; jedini whole-move `==` je TT move ordering
  u `ChessAI.mvvLvaScore` (`move == tt`) — sada TT-promociju poklapa tačno umesto sve četiri varijante.
- **2026-06-26** — Lokalizacija na 8 jezika (en/fr/de/it/ru/zh-Hans/hi + sr izvorni). Novi
  `Chessko/Localizable.xcstrings` String Catalog (297 ključeva) generisan skriptom
  `build_localizations.py` (dict SR→7 jezika). Kôd: računati stringovi u modelima/VM-ovima
  obmotani u `String(localized:)` (`PieceType.srbName`, `PieceColor.srbAdjective`, sve
  `statusMessage`, `GameDifficulty.label`, `LearnViewModel.infoText`, `PuzzleView.localizeTheme`,
  `BoardView` a11y, `ChessPuzzle.difficultyLabel`, itd.); `Text`/`Label` literali se auto-prevode;
  reusable lekcijske komponente (`L_SectionHeader/L_Bullet/L_Box/L_NumberedRule/L_PieceRow`,
  card naslovi/hintovi) sada koriste `Text(LocalizedStringKey(param))` umesto `Text(param)`;
  `PuzzleView.dateTitle` koristi `Locale.current`. `project.pbxproj` + `create_xcode_project.py`:
  dodat fileRef/buildFile/group/Resources za `.xcstrings`, `developmentRegion = sr`, `knownRegions`
  proširen (en, Base, sr, fr, de, it, ru, "zh-Hans", hi). Notacija poteza i "En passant" namerno
  ostavljeni nepromenjeni. Verifikovano: svaki ključ ima svih 8 jezika; pbxproj zagrade balansirane;
  svi izmenjeni Swift fajlovi balansirani (nema Swift toolchaina na ovom sistemu — build u Xcode).
- **2026-06-26** — Hamburger meni + izbor jezika u aplikaciji + trajna preporučena podešavanja.
  Novi `Logic/LocalizationManager.swift` (live language switch: `LocalizedBundle` podklasa +
  `object_setClass(Bundle.main,...)` + associated `<code>.lproj`; `@Observable` sa `languageCode`
  u `UserDefaults`, `setLanguage(_:)`, `refreshID`/`locale`). Novi `Views/SettingsSheet.swift`
  (sekcije Težina + Jezik, "Sistem" = prati telefon, jezici po endonimu). `GameView`: toolbar
  gore-levo zamenjen hamburger dugmetom (`line.3.horizontal`) koje otvara `SettingsSheet`;
  uklonjen `difficultyMenu`. `ContentView`: `@State localization`, `.id(refreshID)` +
  `.environment(\.locale,...)` da se ceo UI re-resolvuje pri promeni jezika. `PuzzleView.dateTitle`
  koristi `LocalizationManager.shared.locale`. Fix build greške: "String Catalog Symbol Generation"
  pravio iste simbole za "beli"/"Beli" i "crni"/"Crni" — uklonjeni "Beli"/"Crni" ključevi, color
  picker sada koristi `color.srbAdjective.capitalized`. Preporučena podešavanja (accept u Xcode)
  upisana i u `create_xcode_project.py` (CLANG_ANALYZER_LOCALIZABILITY_NONLOCALIZED,
  CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF, ENABLE_USER_SCRIPT_SANDBOXING, GCC_WARN_DUPLICATE_METHOD_MATCH,
  STRING_CATALOG_GENERATE_SYMBOLS) da regeneracija ne izgubi. `project.pbxproj`: dodati
  `LocalizationManager.swift` + `SettingsSheet.swift` (fileRef/buildFile/group/Sources). Katalog
  300 ključeva; provereno: nema case-insensitive ni normalizovanih simbol-kolizija.
- **2026-06-26** — Fix: status na ekranima Igra/Zadaci i „Danas" nisu pratili izbor jezika.
  Uzrok: `String(localized:)` (Foundation) zaobilazi `LocalizedBundle` swizzle (za razliku od
  `Text(...)`/`NSLocalizedString` koji idu kroz `Bundle.main.localizedString(...)`). Rešenje:
  novi globalni helperi `Loc(_:)` i `LocF(_:_:)` u `LocalizationManager.swift` koji čitaju preko
  `Bundle.main.localizedString(forKey:value:table:)` (presreće ga swizzle → prati izabrani jezik).
  Svi `String(localized: "…")` pozivi u modelima/VM-ovima/`BoardView` zamenjeni sa `Loc("…")`;
  4 interpolirana slučaja (`%lld …`, `%@`) sa `LocF(...)`. View-ovi se ionako rebuilduju preko
  `.id(refreshID)` pa se računati stringovi re-evaluiraju na novom jeziku.
- **2026-06-26** — Fix: `.nnue` mreže ispale iz `project.pbxproj` (generator skupljao samo `*.mp3`),
  pa Stockfish nedostupan. Obe mreže vraćene u projekat (fileRef/buildFile/group/Resources);
  `create_xcode_project.py` proširen da skuplja i `*.nnue` (`RESOURCE_GLOBS`, `res_filetype`).
- **2026-06-26** — Stockfish nivoi jačine: novi `StockfishLevel` enum (Početnik 1320 / Amater 1600 /
  Srednje 1900 / Napredno 2200 / Ekspert 2600 / Maksimalno = bez limita) sa `elo: Int?` i `label`.
  `GameViewModel.stockfishLevel` (+`setStockfishLevel`, u `SavedGame` uz backward-compat decode);
  `StockfishBridge.bestMove(for:elo:depth:)` šalje `UCI_LimitStrength`+`UCI_Elo` (nil = pun).
  `SettingsSheet`: sekcija „Stockfish nivo" (vidljiva kad je izabran Stockfish). Katalog 307 ključeva.
- **2026-06-26** — „Sistem" jezik fallback na engleski (ne srpski) kad jezik telefona nije podržan.
  `LocalizationManager.systemFallback()` mapira `Locale.preferredLanguages` na podržan kod
  (`zh*`→`zh-Hans`, `en-GB`→`en`), pa vraća `"en"` ako nema poklapanja; `effectiveCode` =
  `languageCode ?? systemFallback()` koristi se za bundle swap, `locale` i sve `Loc`/`Text` lookupe.
  Srpski je izbačen iz sistemskog poklapanja (`subtracting(["sr"])`) — bira se samo ručno; čak i
  na srpskom telefonu „Sistem" daje engleski.
- **2026-06-26** — Fix: Stockfish zaglavi posle prvog poteza („večno razmišlja"). Uzrok 1: slanje
  `UCI_LimitStrength`/`UCI_Elo` pre SVAKE pretrage wedge-uje motor (ne vrati `bestmove` na kasnijim
  potezima) — sada se opcije primenjuju samo kad se nivo promeni (`appliedElo`/`strengthApplied`
  keš u `StockfishBridge`). Uzrok 2: na niskom Elo-u motor odgovori skoro instant, pa je
  `pendingContinuation` mogao da se postavi posle dolaska `bestmove`; sada se nastavak registruje
  PRE `go` (`go` se šalje iz `Task`-a unutar `withCheckedContinuation`).
- **2026-06-26** — Stockfish nivoi: prelazak sa `UCI_LimitStrength`/`UCI_Elo` na slabljenje preko
  DUBINE pretrage. Eliminacijom utvrđeno: čim je `UCI_LimitStrength` aktivan, `go depth` na 2. potezu
  ne vrati `bestmove` (motor wedge-ovan) — nezavisno od keširanja opcija. Rešenje: `StockfishLevel`
  sada ima `searchDepth` (Početnik 1 / Amater 3 / Srednje 5 / Napredno 8 / Ekspert 11 / Maksimalno 15)
  umesto `elo`; `bestMove(for:depth:)` bez ikakvih UCI opcija, originalni `go`→continuation redosled
  (isti tok koji je uvek radio). Labela bez Elo brojeva; footer „Veći nivo = jača igra." Enum rawValue
  nepromenjen → save kompatibilan.
- **2026-06-27** — UX toolbar + podešavanja: toolbar `GameView` redizajniran — levo Nova igra +
  Undo, desno hamburger meni; `SettingsSheet` dobija `.listSectionSpacing(.compact)` i novu sekciju
  Zvuk (Toggle sa ikonom zvučnika); `SoundManager` dobija `isSoundEnabled` (UserDefaults key
  `soundEnabled`, default true) sa guard-om u `playMove()`/`playCapture()`.
- **2026-06-27** — Fix zvuka: `SoundManager` prebačen sa `.ambient` na `.playback + mixWithOthers`
  kategoriju — `.ambient` poštuje mute switch pa zvuk nije radio kad je telefon na tihom;
  `.playback + mixWithOthers` svira čak i na tihom, ali ne prekida muziku u pozadini (standardno
  za igrice). `UserDefaults.register(defaults:)` osigurava default `true` za `soundEnabled`;
  getter koristi `bool(forKey:)` umesto `object() as? Bool`.
- **2026-06-27** — Fix: Stockfish zaglavljuje posle 2. poteza. Dijagnoza: `responseStream` u
  ChessKitEngine nije pouzdan za višestruku upotrebu — stream/continuation se ne oporavlja
  posle prve pretrage. Rešenje: svaki `bestMove` poziv kreira SOPSTVENI `Engine` (fresh start),
  konfigurira NNUE, subscribuje na stream, pošalje position+go, `for await`-uje do `bestmove`,
  pa engine izlazi iz scope-a. NNUE URL-ovi se kešuju u `start()`. Dodat `guard !isThinking`
  u `triggerAI()` kao zaštita od duplog poziva.
- **2026-06-28** — Dorada odeljka sa podešavanjima (Settings): Dodate 4 vizuelne teme table (Klasična, Šumska, Drvo, Ugalj) sa 2x2 grid preview-om u `SettingsSheet`. Dodati prekidači za prikaz koordinata, oznake poslednjeg poteza i legalnih poteza na tabli. Dodata opcija za automatsku promociju pešaka u damu. Integrisan prekidač za haptički odziv (vibraciju) pod kontrolom korisnika. Sva nova podešavanja lokalizovana na svih 8 jezika i uspešno testirana kroz build na simulatoru.
- **2026-06-28** — Vizuelna unapređenja (Premium UX): Implementirana 4 stila figura (Klasični, Moderni metalik gradijenti, Stakleni sjaj, Neonski sjaj) u `PieceImageView` korišćenjem isključivo SwiftUI modifikatora na postojećim SVG resursima. Nadograđena pozadina aplikacije (`AppBackgroundView`) – višetačkasti MeshGradient dinamički prati izabranu temu table sa harmonizovanim tonovima (zeleni za šumu, čokoladni za drvo, monohromatski za ugalj) u svetlom i tamnom režimu sistema. Dodat selektor stila figura u `SettingsSheet` sa istovremenim preview-om stilova skakača. Sve izmene uspešno lokalizovane i verifikovane kroz build.
- **2026-06-28** — UX podešavanja: Skupljen odeljak za izbor težine u jedan red u `SettingsSheet.swift` koristeći SwiftUI `DisclosureGroup` za inline proširenje nadole. Red prikazuje trenutno selektovanu opciju (npr. "Težina: Srednji" ili "Težina: Stockfish (Srednje)") kada je skupljen, i sakriva je kada je proširen radi čistijeg izgleda.
- **2026-06-28** — UX podešavanja: Skupljena i sekcija za izbor jezika u jedan red u `SettingsSheet.swift` koristeći `DisclosureGroup` sa abecedno sortiranim jezicima po izvornom nazivu (Deutsch, English, Français, Italiano, Русский, Srpski, हिन्दी, 中文). Opcija "Sistem" ostaje na vrhu.
- **2026-06-28** — Lokalizacija: Dodat prevod za naslovnu ključnu reč "Izgled" (Appearance) u `build_localizations.py` za svih 8 jezika kako bi cela sekcija Izgled (Sistem, Svetla, Tamna) bila kompletno prevedena.
- **2026-06-28** — UX/Layout učenje: Fiksirano isključivo vertikalno skrolovanje u lekcijama u `LessonDetailView.swift` primenom `GeometryReader` i ograničavanjem unutrašnjeg `VStack` u `ScrollView`-u na širinu ekrana (`.frame(width: geo.size.width)`). Ovo na nivou šablona (template-a) u potpunosti sprečava bilo koje horizontalno prelivanje i skrolovanje na svim lekcijama (1, 2, 3 i 4). Takođe rešen problem u lekcijama 1 i 3: zamenjen horizontalni `HStack` za `piecePicker` sa `LazyVGrid` mrežom (3 kolone, 2 reda), konstranisana dugmad za scenarije i dodat `.frame(maxWidth: .infinity)` na `L_PieceValueTable`.
- **2026-06-28** — Premium UX & Adaptivne Boje: Rešen problem slabe čitljivosti teksta i kontrola na svetloj temi u tabovima „Zadaci“ i „Učenje“, kao i unutar detalja lekcija. Zamenjene su sve fiksne bele boje teksta i pozadine elemenata sa sistemskim dinamičkim bojama (`.primary`, `.secondary`, `Color.primary.opacity(0.04-0.08)` i `Color(uiColor: .systemBackground)`). Navigacione trake i naslovi se sada automatski prilagođavaju tamnoj i svetloj temi sistema.
- **2026-06-28** — Lokalizacija: Dodat prevod za labelu `"Potezi"` (Moves/Coups/Züge/Mosse/Ходы/着法/चालें) u `build_localizations.py` i regenerisan `Localizable.xcstrings` za svih 8 jezika kako bi istorija poteza na prvom tabu („Igra“) bila u potpunosti lokalizovana.
- **2026-06-28** — UX/Layout zadataka: Kontrole za odabir datuma (prethodni/sledeći dan i kalendarsko dugme sa statusom rešenosti) premeštene su u gornji desni ugao navigacione trake (`ToolbarItem(placement: .navigationBarTrailing)`) u `PuzzleView.swift`. Takođe povećana čitljivost i veličina kontrola u toolbaru (veličina teksta na `.subheadline.weight(.semibold)`, ikonica na `.subheadline`, a strelice na size 16 bold).
- **2026-06-28** — UX podešavanja: Integrisan `AppBackgroundView`, skrivena podrazumevana pozadina liste i dodat `.preferredColorScheme` modifikator u `SettingsSheet.swift`. Sada se i tema table i režim izgleda (Svetla, Tamna, Sistem) instantno primenjuju na ekranu podešavanja dok je otvoren.
- **2026-06-28** — UX/Micro-interactions: Uvedena sekvencijalna `.symbolEffect(.bounce)` animacija u `PuzzleView.swift` sa korakom od 0.15s (leva strelica -> kalendar -> kvačica -> desna strelica) i integrisanim `Haptics.impact(.light)` feedback-om za svaki skok. Animacija se aktivira sa odlaganjem od 3.0s nakon što korisnik reši zadatak, suptilno ga navodeći da promeni datum.
- **2026-06-30** — Pravna usklađenost: U sklopu izbora da aplikacija bude otvorenog koda (GPLv3 usklađenost za Stockfish), dodata je sekcija „O aplikaciji“ (About App) na dno ekrana podešavanja u `SettingsSheet.swift` koja sadrži licencne napomene i linkove ka Stockfish-u i Lichess-u. Rešen i SwiftUI List bag tako što je dodat `.buttonStyle(.plain)` na HStack linkova kako klik na Lichess ne bi pogrešno otvarao Stockfish. Svi stringovi su lokalizovani na 8 jezika u `build_localizations.py`.
- **2026-06-30** — Podrška za horizontalni mod (Landscape): Implementirana dinamička adaptacija interfejsa na ekranima „Igra“ (`GameView.swift`) i „Zadaci“ (`PuzzleView.swift`) pomoću `GeometryReader`-a. U landscape režimu, šahovska tabla se prikazuje sa leve strane u punoj visini ekrana, dok se prateće kontrole, informacije i istorija poteza premeštaju na desnu stranu u nezavisno skrolujući `ScrollView`.
- **2026-07-02** — Lokalni režim za dva igrača (Pass & Play): Implementiran režim za dva igrača na istom uređaju. Tabla se fizički i glatko rotira za 180 stepeni nakon svakog poteza pomoću `.rotationEffect` tranzicije od 0.5s. Kako figure i tekstualne oznake koordinata ne bi ostale okrenute naopako, primenjena je kontrarotacija od -180 stepeni na njih u `SquareView.swift` i prelaznim animacijama, dok su same koordinate matematički usklađene da uvek ostanu na fizičkoj levoj i donjoj ivici ekrana. Rotacija se može isključiti novim prekidačem „Rotiraj tablu u lokalnoj igri“ (podrazumevano uključen) u podešavanjima (`SettingsSheet.swift`). Dodat je visual overlay picker za izbor režima igre (Računar ili Prijatelj) prilikom pritiska na dugme `+`. Podržano je i vraćanje samo jednog poteza unazad (Undo) i čuvanje izabranog režima u perzistentnoj memoriji. Svi stringovi su lokalizovani na 8 jezika u `build_localizations.py`.
- **2026-07-03** — Šahovski sat (Chess Clock): Dodat je samostalni šahovski sat (`ChessClockView.swift`) kao treća opcija u meniju za pokretanje nove igre (`+` dugme). Prikaz deli ekran na dva dela (gornja polovina rotirana za 180 stepeni za crnog sa crnom pozadinom i belim tekstom, donja za belog sa belom pozadinom i crnim tekstom), a pritisak na sopstvenu polovinu zaustavlja sopstveni sat i pokreće protivnički. Podržani su zvučni i taktilni efekti pri prebacivanju, pauziranje, resetovanje, dinamičke vremenske kontrole (od Blica 3+2 do Klasičnog FIDE formata 90+30+30 sa inkrementom i automatskim dodavanjem vremena u 40. potezu), automatski prikaz sati `H:MM:SS` za duge partije, i prikaz desetinki sekunde kada preostane manje od 10 sekundi. Veličina cifara sata je povećana za 20% (sa 72pt na 86pt) radi bolje čitljivosti. **Dodato je i info dugme (`i`) pored odabira vremena koje otvara kategorisano objašnjenje svih vremenskih pravila (čist tekst bez suvišnih simbola/ikona)**. Ručno su dodate reference fajla u `project.pbxproj` radi očuvanja zavisnosti paketa.
- **2026-07-03** — Nativni Meni za dugme `+`: Zamenili smo dosadašnji custom overlay picker nativnim SwiftUI `Menu` padajućim menijem na dugmetu `+` u gornjem levom uglu navigacione trake. Meni pruža tri opcije (Igra protiv računara, Igra sa prijateljem, Šahovski sat) sa odgovarajućim sistemskim ikonama (cpu, person.2, timer). Integrisana je pametna provjera partije u toku (uz prompt za potvrdu napuštanja partije).
- **2026-07-03** — Kompletna Lokalizacija podešavanja i menija: Dodali smo sve prevode za opcije u podešavanjima (teme table, stilovi figura, zvuk, haptika, koordinatni sistemi, automatske promocije) i opcije u padajućem meniju za novu igru na svih 8 podržanih jezika u `build_localizations.py`. Zamenili smo `LocalizedStringKey` u `GameView.swift` sa `Loc(...)` kako bi se ispravno primenjivao in-app izbor jezika.
- **2026-07-04** — Realistične teme za figure (Drvo i Metal): Dodali smo dva nova, visoko-realistična stila u `PieceStyle.swift` i `PieceImageView.swift`. Drveni stil koristi krem-zlatne gradijente javora za bele i tamne gradijente oraha/ebanovine za crne figure uz 3D overlay senčenje. Metalni stil koristi polirani hrom/srebro za bele i tamni gunmetal/crni hrom za crne figure, kreirajući logičnu distribuciju svetle i tamne strane. Uveli smo i horizontalni `ScrollView` za izbor stilova u `SettingsSheet.swift` radi bezbednog prilagođavanja širini ekrana.
- **2026-07-04** — Ravne figure (Flat Style): Iskoristili smo priloženi vektorski SVG kôd za svih 6 figura. Napisali smo skriptu `split_svg.py` koja računa precizan bounding box za svaki element, centriranjem ih smešta u kvadratne SVG viewBox okvire (veličine 96x96) radi srazmerne visine. Stilizovali smo bele figure sa konturom od 1.8pt, a crne kao pune outline-free siluete. Izbacili smo stare stilove „Moderni“ i „Stakleni“.
- **2026-07-04** — Jednostavne figure (Simple Bold & Thin): Uvezli smo dva nova seta iz foldera projekta. Napisali smo skriptu `merge_simple_svgs.py` koja automatski spaja crne solid maske i bele šuplje linije kako bi se dobile bele figure sa belom unutrašnjošću i tamno-sivom konturom, dok su crne figure solid tamno-sive. Registrovani su kao dynamic vector SVG asseti u Xcode-u.
- **2026-09-05** — Faza 0 (higijena i temelji). Commit-ovan zaostali rad (Android port,
  `StatsManager`, `EvalBarView`, `PlatformHelper`, review mode, resign, PGN) i dodat
  `.gitignore` (potpisni ključ `appbundle.jks` više se ne prati). Uveden SwiftPM testni
  paket (`Package.swift` + `Tests/ChesskoEngineTests`; `Chessko/TestSupport/LocShim.swift`
  zamenjuje `Loc(_:)` u testovima jer `LocalizationManager` uvozi SwiftUI) koji kompajlira
  izvorne fajlove motora po putanji, bez kopiranja i bez diranja `Chessko.xcodeproj`;
  11 testova (perft za svih 6 standardnih pozicija, uključujući početnu do dubine 5, i
  4 testa prava rokade). **Popravljen stvarni bug u pravilima:** prava rokade su se
  oduzimala samo po `move.from`, pa pojeden top na startnom polju nije gasio pravo — motor
  je nudio rokadu bez topa, a grana rokade je stvarala novog topa iz vazduha (2 topa umesto
  1). Rokada sada pomera postojećeg topa, prava se oduzimaju i po `move.to` (perft poz. 5
  dubina 3: 62416 → 62379, dokaz da je bug otklonjen). **Naknadno (Task 4b, van
  prvobitnog plana):** ista greška je preživela na dva mesta koja su duplirala logiku
  rokade — `ZobristTable.hash(after:in:from:)` je i dalje gasio prava samo po `move.from`
  (netačan inkrementalni heš → gubljene transpozicije, ne pravilo legalnosti; komentar da
  „prati" `applyingForSearch` je posle Task 4 postao netačan, sad je ispravljen) i
  `MoveGenerator.kingMoves` je verovao zastavicama bez provere da top zaista stoji u uglu
  (partija sačuvana starijom verzijom aplikacije može nositi zastarelo pravo —
  `GameViewModel` učitava save-ove bez verzionisanja — pa bi motor posle nadogradnje i
  dalje nudio nelegalnu rokadu). Oba mesta popravljena, dodata dva regresiona testa (sva
  4 ugla umesto samo h1, i rokada bez topa). **Isti bug postoji na Androidu**
  (`ChesskoAndroid/.../GameState.kt`) — vidi „Poznata ograničenja", čeka Fazu 6.
  Popravljeni i UI bug-ovi: Markdown u lekcijama se renderovao kao zvezdice (`Text(Loc(_:))`
  ne parsira Markdown, samo `LocalizedStringKey`; dodata `mdText()` preko
  `AttributedString`), birač figura nevidljiv u svetloj temi (bela tipografija na
  `Color.white.opacity(0.04)`, zamenjeno `.primary`/`.secondary`), sirovi ključevi tema
  zadataka (`localizeTheme` je za nepoznatu temu vraćala sam ključ, npr. `backRankMate`;
  sad vraća prazan string i čip se preskače — dopuna mape svim ~60 Lichess tema ostaje za
  Fazu 2) i dva odsečena prevoda („Napad na dam" → „Napad na damu", „Napad na kral" →
  „Napad na kralja"). Sadržaj ispod plutajućeg iOS 26 tab bara popravljen
  `safeAreaPadding(.bottom, 24)` u **tri** fajla — `LessonDetailView.swift`,
  `LearnView.swift` i portretnoj grani `GameView.swift`. Plan je prvobitno ciljao i
  `PuzzleView`, ali taj `ScrollView` je unutar `.sheet` birača datuma koji tab bar nikad
  ne prekriva, a portretna grana `PuzzleView`-a uopšte nema `ScrollView`; ta izmena je
  namerno izostavljena (vidi i „Poznata ograničenja"). `project.pbxproj` se od 2026-07-03
  održava ručno — `create_xcode_project.py` se više ne sme pokretati (vidi „Build / Run").
  **Vizuelna provera u obe teme nije završena** — ekran host Mac-a se zaključao usred
  faze, pa gorenavedeni UI fix-evi nisu potvrđeni na simulatoru/uređaju, samo pregledom
  koda i diff-a.



- **2026-09-05** — Fix wave iz finalnog pregleda cele grane Faze 0 (5 nalaza):
  (1) `GameState.fen` je i dalje slepo verovala zastavicama rokade — dodat
  `GameState.hasCastlingRook(_:kingside:)` helper (jedno mesto istine); `fen` gejtuje
  sva 4 slova na njemu, `MoveGenerator.kingMoves` prepravljen da poziva isti helper
  umesto duplirane inline provere; halfmove polje u FEN-u sada šalje pravi
  `halfmoveClock` umesto hardkodovane `"0"`. (2) Svetla tema: preostala tri mesta u
  `LessonDetailView.swift` (header „Specijalna pravila", scenario dugmad, `MatePuzzleCard`)
  su i dalje imala `.white`/`Color.white.opacity(...)` — zamenjeno `.primary`/`.secondary`/
  `Color.primary.opacity(...)` po istom obrascu kao `OpeningExerciseCard`/`MateExerciseCard`.
  (3) Arhitektura sekcija ovog fajla je ispravljena na 4 mesta gde je bila u sukobu sa
  sopstvenim roadmap-om/kodom: igrač bira boju + 8 jezika + `TabView` sa 3 taba (ne
  "uvek beli, jedan ekran"); `ChessMove ==` uključuje `flag`; AI koristi `maxDepth`/
  `timeLimit` po težini koju bira korisnik (ne hardkodovan `.medium`/fiksna dubina);
  promocija ima UI izbor figure (ne uvek dama). Dodata napomena u „Poznata ograničenja"
  da `positionKey` nosi prava rokade pa stari save može razdvojiti brojač ponavljanja
  posle nadogradnje. (4) `PuzzleView` je sekla temu na `prefix(2)` PRE filtriranja
  nepoznatih Lichess tema — puzzle čije prve dve teme nisu u mapi nije prikazivao
  nijedan čip iako je treća bila poznata; sad se prvo mapira+filtrira, pa tek onda
  `prefix(2)`. (5) `Logic/ZobristTable.swift` dodat u `sources:` liste `Package.swift`
  (kompajlira se samo sa Foundation) da kasnija faza može da mu piše test bez izmene
  build konfiguracije. Perft nepromenjen (potvrđeno `swift test`); build zelen.

- **2026-09-06** — Faza 1 (dizajn sistem). Uveden `Chessko/Views/DesignSystem.swift`
  sa `enum DS` (boje, `DS.Space`, `DS.Radius`, `DS.maxBoardSide`) i tipografskom
  skalom `Font.ds*` koja ide kroz `appFont` da zadrži Mac veličine. Sva tri taba,
  sat i podešavanja prevedeni na tokene; per-lekcijske boje (plava/zelena/narandžasta/
  crvena) svedene na jedan akcent, a boja zadržana samo tamo gde nosi značenje.
  Ikone tabova: `square.grid.3x3.fill` / `puzzlepiece.fill` / `book.fill` (`chessboard`
  ne postoji na ovom SDK). Uklonjen `–` placeholder iz kartica igrača, protivnik se
  zove „Računar" a ne po težini, eval bar prosiren na 10pt i uzima boje table umesto
  dupliranog broja u kartici. Mrtav prostor **preuređen, ne popunjen**: Igra i Zadaci
  koriste `GeometryReader` + `.frame(minHeight:)` + `Spacer` na oba kraja pa se kratak
  sadržaj centrira; Učenje namerno ostaje poravnato uz vrh jer je lista. Duplirani
  naslov na Učenju rešen sakrivanjem nav bara — nativni veliki naslov se tu ne
  iscrtava jer je `ScrollView` umotan u `ZStack`. `DS.maxBoardSide` (560pt) sprečava
  da tabla proguta ceo iPad ekran.
  Tri greške su bile u planu, ne u izvršenju, i ispravljene su usput:
  `.frame(maxHeight: .infinity)` na detetu `ScrollView`-a ne radi ništa; aritmetika
  širine table pošla je od pogrešne osnovice; i obe neaktivne polovine sata mapirane
  su na jedan tema-adaptivan token, što bi u svetloj temi obojilo crnu polovinu skoro
  belo ispod belog teksta. Vizuelno provereno na iPhone 17 Pro (svetla i tamna) i
  iPad Pro 11" — osim liste Učenja u tamnoj temi, sata i pejzažnog režima.
- **2026-09-06** — Faza 1, talas ispravki posle celokupnog pregleda grane (šest nalaza,
  izveštaj u `.superpowers/sdd/2026-09-06-faza-1-dizajn-sistem/final-fix-report.md`).
  (1) `ChessClockView.swift`: timeout boje na oba polutimera vraćene na fiksne hex
  literale (`#8C2525` crna, `#FADAD8` bela) — bile su promašene u prethodnom revertu
  neaktivnih polovina i pravile isti bag (DS.danger je adaptivan, pa je u tamnoj temi
  beli tekst na crnoj polovini bio ~2.5:1, a bela polovina je na 18% providnosti
  postajala tamna). Sat sada nema nijedan adaptivni token u fiksnom dvo-tonskom bloku.
  (2) Svih pet `Color.accentColor`/`.accentColor` mesta (`GameView.swift:416` — bordura
  aktivne kartice igrača; `ChessClockView.swift` — 4 kontrole sata) prebačeno na
  `DS.accent`; dodat `.tint(DS.accent)` na `TabView` u `ContentView.swift` (bez tog
  tint-a tab bar i dalje čita sistemsko plavo iz nepostojećeg `AccentColor` colorset-a).
  `AccentColor` colorset i `project.pbxproj` namerno nisu dirani — sledeći korak izvan
  ovog talasa. (3) `MoveHistoryView.swift`: sve četiri `Color.cyan` selekcije → `DS.accent`
  (fajl nije bio ni u jednom task listu pa je promašen u fazi 1; sudario se vizuelno sa
  `reviewControlsView` odmah ispod, već na `DS.accent`). (4) `SettingsSheet.swift`:
  uklonjena tri modifikatora (`.listRowBackground(DS.surface)`, `.scrollContentBackground(.hidden)`,
  `.background(DS.ground)`) — `listRowBackground` je row-scoped pa primenjen na `List`
  ne radi ništa; ostavljena nativna grouped-list pozadina. (5) Pejzažni eval bar u
  `GameView.swift` razvlačio se preko table (ista greška kao u portretu, ispravljena
  4 puta pre nego što je stigla u pejzaž — nikad vizuelno provereno); strana table se
  sada u pejzažu računa iz raspoložive VISINE (`geo.size.height`), isto kao što se u
  portretu računa iz širine, i eval bar/tabla dobijaju `.frame(height:)`/`.frame(width:height:)`
  umesto `.aspectRatio` + `.frame(maxHeight: .infinity)`. (6) `DesignSystem.swift`:
  ispravljena dva netačna komentara (tipografski header sad navodi `dsMono` kao izuzetak
  jer `appFont` nema `design:` parametar; `dsMono` dokumentacija sad navodi stvarnog
  jedinog potrošača — rejting bedž na Zadacima — umesto netačnog "sat, eval, notacija").
  Verifikovano širokim grep-om po `Chessko/ContentView.swift` + `Chessko/Views/*.swift`:
  jedini preostali pogodak je multi-color konfeti paleta u `BoardView.swift:347`
  (van dometa — boje table/figura). Build (`iPhone 17 Pro` simulator) uspešan,
  `swift test` 11/11 prošlo (~84s).
- **2026-09-06** — Faza 2, Task 4 (rejting igrača). `StatsManager` dobija Elo-stil
  `puzzleRating: Int` (default 800, ceo obrazac za razlikovanje „nema vrednosti" od
  0 kao `resetStats()`) i `nonisolated static func newRating(current:puzzleRating:solved:)`
  — čista funkcija (`E = 1/(1+10^((Rp-R)/400))`, `R' = R + 32*(S-E)`), namerno `nonisolated`
  da izbegne MainActor izolaciju nasleđenu od klase i ostane testabilna bez `UserDefaults`;
  instanca `applyPuzzleResult(puzzleRating:solved:)` je zove i upisuje. `PuzzleViewModel`
  poziva `applyPuzzleResult` na sva tri mesta gde već postoji `recordPuzzleSolved()`/
  `recordPuzzleFailed()` (pogrešan potez, rešeno, `showSolution()`), uzimajući rejting iz
  `currentPuzzle?.rating` — ako je `currentPuzzle` nil, rejting se ne ažurira.
  `Logic/StatsManager.swift` dodat u `Package.swift` `sources:` (kompajlira se čisto sa
  Foundation, bez app-only zavisnosti). Novi `Tests/ChesskoEngineTests/RatingTests.swift`,
  5 testova: 4 fiksne vrednosti iz spec-a (800/800/rešeno→816, 800/800/nerešeno→784,
  800/1600/rešeno→832, 800/400/nerešeno→771) i peti kao svojstvo (property test).
  **Nalaz:** peti test u planu je tražio da rejting posle 100 uzastopnih rešenih zadataka
  ocene 800 ostane ispod 1000 — provereno istom (verifikovanom) formulom da to nije tačno:
  pošto je rejting zadatka fiksiran na 800 dok rejting igrača raste, `E` raste ka 1 ali
  nikad ga ne dostiže, pa je svaki prirast pozitivan; posle 100 ponavljanja rejting je
  1288, a stvarna fiksna tačka niza (gde `round(32*(1-E))` prvi put padne na 0) je oko
  1520, ne 800. Plan (`docs/superpowers/plans/2026-09-06-faza-2-offline-zadaci.md`) je
  ispravljen eksplicitnom napomenom, ne tihom izmenom. Peti test je zatim prepisan da
  tvrdi **svojstvo** koje Elo formula stvarno garantuje: prirast po rešenom zadatku je
  pozitivan i **ne raste** kroz svih 100 iteracija. `<=`, ne `<` — celobrojno
  zaokruživanje pravi platoe (…16, 16, 15…), pa bi strogo opadanje palo iako je formula
  ispravna. Granica `< 1600` je ostala samo kao gruba zaštita od linearnog rasta; sama
  za sebe prolazi i kad je formula pokvarena na više načina.
  `swift test`: 23/23 prošlo. `xcodebuild` (simulator `iPhone 17` — `iPhone 16` ne postoji
  na ovoj mašini): BUILD SUCCEEDED. `project.pbxproj` nije dirran (test fajlovi i
  `StatsManager.swift` već registrovani).
- **2026-09-06** — Faza 2, Task 5: neograničeno rešavanje zadataka ("Sledeći zadatak").
  `PuzzleRepository`: `nonisolated static let minRating = 600` / `maxRating = 2200`
  (granice isporučene baze) i `nonisolated static func practiceRatingWindow(playerRating:)`
  — čista funkcija, `lo = max(minRating, r-200)`, `hi = max(lo, min(maxRating, r+100))`.
  Ovo NIJE kozmetika nego fix za crash: rejting igrača nije ograničen (dug niz neuspeha
  ga vodi ka ~80), pa bi naivan prozor za rejting 80 bio `-120...180` (baza počinje od
  600 — prazno), a klampovanje SAMO donje granice dalo bi `600...180`, `ClosedRange` sa
  donjom granicom većom od gornje — puca pri kreiranju, ne samo vraća prazan niz. Tri nova
  testa u `PuzzleRepositoryTests.swift` (srednji rejting, 80, 3000) — za rejting 3000
  formula ispravno vraća `2800...2800` (validan ali prazan u bazi; na to se oslanja
  progresivno proširenje niže).
  `PuzzleViewModel`: novi `enum PuzzleMode { daily, practice }` + `private(set) var mode`.
  `loadPuzzle()` postavlja `.daily` (pokriva i `load(date:)`, koji ga zove); novi
  `nextPuzzle()` postavlja `.practice`, čita `StatsManager.shared.puzzleRating` U TRENUTKU
  poziva (ne kešira se — prati igrača kako napreduje unutar sesije) i pokušava
  `randomPuzzle` kroz 4 sve šira prozora: `practiceRatingWindow` → `±400` → `±800` →
  cela baza `600...2200`, uz `excluding: solvedPuzzleIds`; ako je i cela baza sa
  isključivanjem prazna (korisnik rešio svih ~20 000), poslednje pribežište ignoriše
  `excluding` i ponovi već rešen zadatak (bolje ponavljanje nego prazan ekran).
  Novi `solvedPuzzleIds: Set<String>` (UserDefaults ključ `solvedPuzzleIds`, niz stringova,
  učitan jednom pri inicijalizaciji svojstva — najgori slučaj ~20 000 kratkih id-jeva,
  ~200 KB, prihvatljivo bez čišćenja) upisuje se u OBA režima kad zadatak bude uspešno
  rešen kroz `attempt()`; `markCurrentSolved()` (kalendarski dan) i dalje se zove SAMO
  kad je `mode == .daily` — u `.practice` bi lažno označio kalendarski dan kao rešen.
  `showSolution()` namerno NE upisuje ni `solvedPuzzleIds` ni kalendar (isti obrazac kao
  postojeći komentar "Ne označavamo kao rešeno kad se prikaže rešenje") — sopstvena odluka,
  van eksplicitnog obima brief-a.
  Fix defekta koji bi ovaj task pogoršao: `.onAppear { viewModel.loadDailyPuzzle() }` u
  `PuzzleView` se okidao na SVAKI povratak na tab Zadaci i bezuslovno je restartovao
  zadatak (do sada je to tiho brisalo upola rešen dnevni zadatak; sa `.practice` bi
  izbacilo korisnika i iz vežbovnog zadatka). `loadDailyPuzzle()` sada učitava SAMO kad
  `currentPuzzle == nil` ili je `phase == .unavailable` (retry dugme i dalje radi jer je u
  tom stanju `currentPuzzle` već `nil`); `load(date:)` i retry i dalje prisilno učitavaju,
  nedirani.
  UI: `.solved` grana `actionButtons` u `PuzzleView.swift` sad je `VStack` — nova primarna
  akcija "Sledeći zadatak" (`DS.accent` pozadina + `DS.onAccent` tekst, isti par kao dugme
  za pauzu u `ChessClockView`) uvek na vrhu, dostupna i posle dnevnog i posle vežbovnog
  zadatka; postojeće "Sledeći dan"/"Završio si zadatak za danas!" ispod, nepromenjene
  funkcionalno (samo "Sledeći dan" prebačen na sekundarni stil — `Color.primary.opacity`,
  isti kao "Prikaži rešenje" — da ustupi mesto novoj primarnoj akciji). Nov ključ
  "Sledeći zadatak" dodat u `build_localizations.py` (za `add("Sledeći dan", ...)`) i
  katalog regenerisan: 406 → 407 ključeva (tačno +1), svih 8 jezika po ključu potvrđeno
  python skriptom.
  `swift test`: 26/26 prošlo (23 postojeća + 3 nova za `practiceRatingWindow`). `xcodebuild`
  (simulator `iPhone 17`): BUILD SUCCEEDED. `project.pbxproj` nije dirran — nema novih fajlova.
- **2026-09-07** — Faza 2 završena (offline zadaci). Tab Zadaci više nema nijedan mrežni
  poziv: `chess-puzzles-api.vercel.app` je zamenjen lokalnom bazom od 20.000 Lichess
  zadataka (CC0) koja se čita kroz `PuzzleRepository` (`import SQLite3`, bez ijedne nove
  SPM zavisnosti). Detalji baze i njeno regenerisanje — vidi sekciju „Baza zadataka".
  Uz to: Elo rejting igrača (start 800, K=32) i neograničeno rešavanje („Sledeći zadatak")
  sa izborom po rejtingu. `swift test` 27/27; grep za mrežom nad `Chessko/*.swift` daje
  samo 2 licencna komentara i 2 atribucijska linka u „O aplikaciji" (GPLv3 obaveza).
  Vizuelno provereno na iPhone 17 Pro u obe teme (dnevni zadatak i rešeno stanje).

  **Ispravke koje su ispale iz pregleda, vredne pamćenja:**
  - `PuzzleRepository.swift` je bio dodat samo u `Package.swift`, **ne i u Xcode target**.
    `swift test` je prolazio (paket kompajlira izvore po putanji) i `xcodebuild` je prolazio
    (fajla nije ni bilo za target) — aplikacija ga nikad nije kompajlirala. Ista klasa greške
    kao `.nnue` mreže 2026-06-26. Otkriveno tek u Task-u 3, kad je nešto počelo da ga zove.
    **Pouka: kad se doda nov izvorni fajl, dokazati da se kompajlira u aplikaciju** —
    ubaciti sintaksnu grešku i videti da build pada.
  - `applyNextComputerMove()` je pri neuspehu `ChessMove.fromUCI` ćutke izlazio i ostavljao
    `phase` na `.loading`, gde su sve kontrole onemogućene a `actionButtons` prazan — ekran
    bez izlaza do restarta. Guard je razdvojen: prazna lista poteza = normalan kraj,
    nerazrešiv potez = `.unavailable(Loc("Zadatak je oštećen"))`.
  - Odloženi `Task`-ovi (protivnikov potez, reprodukcija rešenja) dobili su
    **brojač generacije**. Strelice za datum se gase samo na `.loading`, pa su
    tokom 600ms čekanja na protivnikov potez i tokom ~700ms po potezu u
    reprodukciji rešenja bile aktivne: promena datuma tu je ostavljala zaostali
    `Task` koji je onda odigrao potez nad **novim** zadatkom (`rawMoves` je već
    zamenjen), pa je zadatak počinjao sam sebe da rešava. Svaki odloženi `Task`
    sada pamti `loadGeneration` i odustaje ako se promenio. Uz to je
    `showSolution()` gejtovan na `!awaitingOpponent` — dugme je tokom tog
    prozora vidljivo, a odloženi potez bi pregazio `.showingSolution` nazad u
    `.playing`.
  - Dodat `DS.onAccent`. `DS.accent` **menja svetlinu između tema** (`#2E4A8A` svetla /
    `#7EA0E8` tamna), pa nijedna fiksna boja teksta ne radi u obe: bela je davala 8,5:1 u
    svetloj ali 2,6:1 u tamnoj. Pogođena su bila dva mesta — novo dugme „Sledeći zadatak"
    i dugme pauze u satu. **`DS.onScrim` (fiksna bela) sme samo na `DS.scrim`; na `DS.accent`
    ide `DS.onAccent`.**

- **2026-09-07** — Faza 2, talas ispravki iz finalnog pregleda cele grane (šest nalaza,
  izveštaj u `.superpowers/sdd/2026-09-06-faza-2-offline-zadaci/final-fix-report.md`).
  (1) **Rupa u unosu od 600 ms posle svakog tačnog poteza**, koja je korisnika koštala
  rejtinga: `attempt()` je posle tačnog poteza vraćao `phase = .playing` i tek za 600 ms
  odigravao protivnički odgovor, a `isPlayerTurn` je u tom prozoru bio `true` — brz tap
  je poređen sa PROTIVNIČKIM potezom iz `rawMoves`, pa je padao kao greška (−16…−32 Elo,
  `puzzleHadError = true`, tačno rešenje se posle toga više nije brojalo). Rešenje: novi
  `awaitingOpponent` flag u `PuzzleViewModel` (postavlja se pre `Task`-a, briše se
  `defer`-om na svim izlazima `applyNextComputerMove()`) uključen u `isPlayerTurn`.
  `.loading` se ovde NE sme koristiti (kao u `loadPuzzle()`) jer `PuzzleView` u toj fazi
  crta ekran učitavanja umesto table; odloženi poziv iz `setup()` je već pokriven
  `.loading`-om koji obe ulazne tačke postavljaju pre njega.
  (2) **Rejting zadataka se računao i čuvao, a nigde nije bio prikazan** (spec §6 traži
  vidljiv rejting i njegovo kretanje) — dodat `StatBox(Loc("Rejting zadataka"))` u
  statističku mrežu u `SettingsSheet.swift` (drugi red sad ima 4 kolone, kao prvi); ključ
  dodat u `build_localizations.py`, katalog 408 → 409 ključeva × 8 jezika (provereno).
  (3) **Režim vežbanja je tvrdio da je dnevni zadatak završen** — `.solved` grana u
  `PuzzleView` je gledala samo `canGoNext`/`selectedDate`, pa je posle rešenog vežbovnog
  zadatka pisalo „Završio si zadatak za danas!" dok je kvačica u traci na istom ekranu
  govorila suprotno, a „Sledeći dan" je tiho izbacivao korisnika iz vežbanja. Ceo blok je
  gejtovan na `viewModel.mode == .daily`; „Sledeći zadatak" ostaje vidljiv u oba režima.
  (4) **„Resetuj statistiku" nije resetovao napredak na zadacima** — `resetStats()` je
  vraćao rejting na 800 ali ostavljao `solvedPuzzleIds` i `chessko.solvedDates`. Oba imena
  ključa su sad `nonisolated static let` na `StatsManager` (ne mogu da žive u
  `PuzzleViewModel` — `StatsManager.swift` se kompajlira i u `ChesskoEngine` SwiftPM target
  gde `PuzzleViewModel` ne postoji), `resetStats()` ih briše, a `PuzzleViewModel` ih koristi
  umesto svojih literala i ponovo čita oba skupa iz `UserDefaults` u novom
  `reloadPersistedProgress()` (jednom po učitavanju zadatka — iz `loadPuzzle()` **i**
  `nextPuzzle()`, jer je vežbanje baš ono što `solvedPuzzleIds` isključuje).
  (5) **Animacija koja vuče pažnju ka strelicama za datum okidala se i posle vežbovnih
  rešenja** (četiri haptika 3 s kasnije, usred sledećeg zadatka) — gejtovana na
  `mode == .daily`, uz dodatnu proveru stanja unutar odloženog bloka jer se sekvenca ne
  može otkazati.
  (6) Tri zastarela mesta u dokumentaciji: komentar mape tema u `PuzzleView.swift` više ne
  obećava dopunu „u Fazi 2" nego navodi zašto 21 od 73 teme dovoljno (0 od 20.000 zadataka
  bez ijednog čipa, 184 sa jednim); iz „Poznatih ograničenja" uklonjen netačan unos o
  `PuzzleView` portretnom rasporedu bez `ScrollView`-a (netačno od Faze 1, vidi
  `PuzzleView.swift:77`) i preformulisan unos o dnevnom „hromu" nad vežbanjem (delovi koje
  ovaj talas popravlja); u changelog unosu za Task 5 `DS.onScrim` ispravljen na `DS.onAccent`.
  `swift test` 27/27 prošlo (~85 s), `xcodebuild` (iPhone 17 Pro simulator) BUILD SUCCEEDED.
  `project.pbxproj` nije diran (nema novih fajlova).

- **2026-09-08** — Faza 3 (sadržaj lekcija u JSON). Tekst četiri lekcije izvučen iz
  Swift-a u podatke: nova šema blokova (`Models/LessonContent.swift` — `LessonDocument`
  + `LessonBlock` sa 12 tipova, `Codable` koji na nepoznat tip **baca** umesto da tiho
  preskoči), generator `build_lesson_json.py` (struktura je zapisana u skripti, tekst se
  NE prekucava nego vadi iz kataloga za svih 8 jezika) i 32 fajla u
  `Chessko/Content/lessons/`. `Content/` je u projekat dodat kao **folder-referenca**, pa
  nova lekcija ne traži izmenu `project.pbxproj`; `Logic/LessonRepository.swift` čita
  `subdirectory: "Content/lessons"`, kešira po `id.jezik` i pada na `en` pa `sr`.
  `Views/LessonRenderer.swift` je jedino mesto koje zna kako se blok crta (tu su se
  preselile i `L_*` komponente i tri kartice vežbi), a `LessonDetailView.swift` je sa
  **1238 spao na 96 linija** — samo zaglavlje, skrol i poziv renderera. Četiri nova
  testa (`Tests/ChesskoEngineTests/LessonContentTests.swift`): svih 12 tipova se
  dekodira, round-trip, nepoznat tip pada glasno, i svih 32 generisana JSON-a se
  dekodiraju. `swift test` 27 → 31.
  **Task 6 (zatvaranje faze):** iz `build_localizations.py` obrisano **169 `add(...)`
  unosa** koje su koristile samo lekcije; katalog **410 → 241 ključ**, svaki i dalje sa
  svih 8 jezika. Skup za brisanje nije uzet iz naivnog grep-a po Swift-u (on prijavi 191
  ključ) nego iz preseka „nema literala u Swift-u" **i** „postoji u `*.sr.json`" — grep
  sam bi obrisao i 22 zatečena siročeta iz ranijih faza (ostavljena, van dometa) i,
  gore, četiri oznake bodova koje `L_PieceValueTable` **sastavlja interpolacijom**
  (`"\(value) bod…"`) pa ih pretraga literala ne vidi; one i tri stringa istraživača
  figura su na keep-listi (razlog upisan i kao komentar iznad njih u skripti).
  U **istom commit-u** skinut `Loc()` sa sadržaja koji iz JSON-a stiže već preveden —
  17 mesta u `LessonRenderer.swift` i 3 u `OpeningExerciseViewModel.swift` (iz plana),
  plus 4 koje je plan promašio jer je gledao samo renderer: naslov+podnaslov lekcije u
  `LessonDetailView.swift` i u kartici na `LearnView.swift`. Razdvojeno bi u prozoru
  između dva commita srpski pao na katalog koji ga više ne nosi. Hrom (dugmad,
  podrazumevane poruke vežbi, `LocF("Mat u %lld")`) zadržava `Loc()` — 11 mesta.
  Provereno: 0 `Loc()`/`LocF()` poziva u `Chessko/**/*.swift` je ostalo bez ključa zbog
  ove izmene (19 poziva je i pre ovoga bilo bez ključa i tiho padalo na srpski — isti
  skup pre i posle, zaseban nalaz za neku sledeću fazu). Simulator (iPhone 17 Pro):
  tabela vrednosti figura čita „1 bod / 3 boda / 3 boda / 5 boda / 9 bodova / ∞" na
  srpskom i „1 point / 3 points / … / ∞" na engleskom. `swift test` 31/31,
  `xcodebuild` BUILD SUCCEEDED, `project.pbxproj` nije diran.
- **2026-09-08** — Prevedeno 19 stringova interfejsa koji su ranije ostali bez ključa u
  katalogu, pa ih je korisnik na svih 7 stranih jezika video na srpskom — među njima objave
  koje se vide u svakoj partiji („Mat! Beli je pobedio!", „Šah! Beli kralj je napadnut."),
  dijalog za predaju, nazivi stilova figura, „Pregledaj partiju", „Potvrda", „Resetuj",
  „Zatvori". Katalog 241 → 258 ključeva.
  **Dva od 19 nisu dodata u katalog, namerno.** `„Beli"`/`„Crni"` bi sa postojećim
  `„beli"`/`„crni"` dali ISTI simbol pod `STRING_CATALOG_GENERATE_SYMBOLS` i oborili build —
  ista greška je već napravljena i ispravljena 2026-06-26. `GameView.headerLabel(for:)` zato
  koristi `color.srbAdjective.capitalized`, isti obrazac kao birač boje. Provereno da
  `.capitalized` radi na svim jezicima (`blanc→Blanc`, `weiß→Weiß`, `белый→Белый`, kineski
  nema veličinu slova pa ostaje `白方`).
  Provereno u **izgrađenoj aplikaciji**, ne samo u katalogu: pročitani `fr/de/ru.lproj`
  unutar `.app` i potvrđeno da svaki nov ključ ima prevod. Kontrolna provera „svaki `Loc()`
  ima ključ" sada daje **0**.

- **2026-09-10** — Faza 4a, Task 4 (pokretac `practice` i `test` koraka Puta).
  `PuzzleMode` dobija treci slucaj `.step(id:requireFlawless:)` (+ `Equatable`,
  koji je enum sa asocijativnom vrednoscu izgubio); `PuzzleViewModel` dobija red
  zadataka (`stepQueue`/`stepSolved`/`stepFailed`/`stepProgress`),
  `startStepPractice(step:)` koji red puni jednim upitom i pokrece prvi zadatak,
  `advanceStepAfterSolve()` (broji i, kad je red iscrpljen, zove
  `ProgressStore.completeStep`) i `failStepIfTest()` (prva greska u testu ga
  obara i posle ~1.4s pokrece korak iznova sa NOVIM zadacima). Tok resavanja se
  ne duplira — `tap`/`attempt`/`apply` su isti kao na tabu Zadaci, pa i
  `StatsManager.recordPuzzleSolved()` (rejting + dnevni cilj) ide postojecim
  putem. Novi `Chessko/Views/StepPracticeView.swift` je tanak (traka pilula,
  brojac „Zadatak N od M", status, `BoardView`) i sopstveni `PuzzleViewModel` mu
  je bitan: deljeni bi ulaskom u korak pregazio zadatak dana. Namerno NE nudi
  „Prikazi resenje" (bio bi izlaz iz provere) ni „Sledeci zadatak" (red je
  fiksan). `PuzzleRepository.stepRatingWindow(playerRating:stepRange:)` (spec
  5.4) sece prozor rejtinga opsegom koraka, a kad je presek prazan prednost ima
  OPSEG KORAKA — 3 nova testa (56 ukupno). Katalog 279 → 284 kljuca × 8 jezika.
  **Popravljen stvarni bug iz Task-a 3:** `PathView.destination(for:)` je bila
  `@ViewBuilder` funkcija sa tipom `(some View)?`, a takva funkcija NIKAD ne
  vrati `nil` — builder umota i granu `nil as LessonDetailView?` u
  `Optional.some(_ConditionalContent<…>)`, pa je `if let` uvek prolazio i vezba,
  test i partija su izgledali aktivno („Nastavi" umesto „Uskoro", strelica u
  redu) i vodili na prazan ekran. Odluka je prebacena u privatni `enum StepRoute`
  + `route(for:)` (jedno mesto istine, kao i pre), a `destination(for:)` je sad
  cist renderer. Verifikovano na simulatoru: `practice` i `test` se stvarno
  igraju, `game` red je bez strelice i kartica kaze „Uskoro".

- **2026-09-11** — Faza 4a, Task 5 (pokretac `game` koraka Puta). `StepRoute` dobija
  `case game(difficulty:startFEN:stepId:)`, pa `route(for:)` vise ni za jedan tip koraka
  ne vraca `nil` sa isporucenim kurikulumom — poglavlja 2–4 su time prvi put dostizna
  (korak 3 je bio zid). Tezina iz kurikuluma se mapira u `GameDifficulty` u `route(for:)`
  i dalje putuje kao enum: mapiranje je totalno (`CurriculumStep.knownDifficulties` su
  bukvalno `rawValue`-ovi), a ako se dva spiska ikad raziđu korak ostaje NEAKTIVAN umesto
  da se tiho igra na pogresnoj jacini.
  **`GameViewModel` vise ne cuva partiju pod jednim globalnim kljucem:** `savedGameKey`
  je sada svojstvo primerka (`init(saveKey:)`, podrazumevano `GameViewModel.freePlaySaveKey`
  = zatecen `chessko.savedGame`), a korak dobija svoj slot
  (`GameViewModel.stepSaveKey(_:)` → `chessko.savedGame.step.<stepId>`). Bez toga bi drugi
  primerak modela ucitao partiju koju korisnik ima u toku na tabu Igra i prvim potezom je
  pregazio — model snima celu partiju posle SVAKOG poteza. Uzgredna dobit: prekinuta
  partija iz koraka se nastavlja (zavrsena se ne nastavlja).
  Novi `Chessko/Views/StepGameView.swift` (sopstveni `GameViewModel`, kartica protivnika,
  status, tabla, predaja i undo u toolbaru) i novi `Chessko/Views/PromotionOverlay.swift`
  — birac promocije je izdvojen iz `GameView`-a doslovno, jer bi bez njega partija koraka
  stala na promociji i korak se ne bi mogao zavrsiti. Korak se zavrsava kad
  `GameViewModel.isGameOver` postane `true`, TACNO jednom po ulasku (`pendingStepId`), i
  **bez obzira na ishod** — predaja i poraz zavrsavaju korak isto kao pobeda (spec:
  „partija odigrana do kraja", ne pobeda). Tab Igra je netaknut: sopstveni model, sopstveni
  kljuc, `newGame` tamo ne dodiruje Put. Katalog 284 → 285 kljuca × 8 jezika; `swift test`
  56/56; dokazano na simulatoru da `chessko.savedGame` ostaje bajt-identican (sha256
  `dd3deee0…`) dok korak pise u svoj slot.
- **2026-09-11** — Faza 4a (Put — mehanizam). Treći tab je **Put** umesto Učenja; `LearnView`
  obrisan. Kurikulum (`Chessko/Content/curriculum.json`, 4 poglavlja × 3 koraka) i napredak
  (`ProgressStore` → `progress.json` u Application Support) su novi; `StatsManager` je postao
  fasada nad njim, pa njegovih 26 pozivnih mesta nije dirano. Sva četiri tipa koraka rade:
  lekcija, vežba, test (nula grešaka) i partija. Testova 31 → 56.

  **Provereno u pokrenutoj aplikaciji, ne izvedeno iz koda:** migracija stare statistike iz
  `UserDefaults` (zasejano 7/5/42/1234 → isto u `progress.json`, stari ključevi netaknuti);
  napredak preživljava gašenje (aplikacija sama upisala korak → `terminate` → posle ponovnog
  pokretanja lekcija ima kvačicu, vežba otključana, poglavlje 33%); streak se prekida
  propuštenim danom (0) ali **preživi dan koji još traje** (2 uz „cilj danas nije ispunjen");
  slobodna partija preživljava partiju iz koraka (isti sha256).

  **Greške uhvaćene usput, vredne pamćenja:** `@ViewBuilder` funkcija sa opcionim povratkom
  **nikad ne vraća `nil`** — zbog toga su vežba, test i partija izgledali aktivno i vodili na
  prazan ekran; odluka je premeštena u običan `enum`. Ekran koraka se gurao kao *pogled*, pa
  se odredište preračunavalo pri osvežavanju liste i menjalo pod korisnikom baš kad završi
  korak; sada se gura kao vrednost. I: ekran se mora proveriti u stanju u kom sporna putanja
  **postoji** — prva provera Puta je rađena u početnom stanju, gde su vežba i partija
  zaključane, pa se prvi od ta dva buga nije ni mogao videti.
- **2026-09-11** — Faza 4b (dve nove lekcije: Notacija i Taktika). Task 1 i 2 su dodali
  `Chessko/Content/lessons/{notation,tactics}.{sr,en}.json` — sadržinski nezavisan rad,
  bez ijedne linije Swift-a; obe lekcije koriste isključivo postojećih 12 tipova blokova
  (uključujući `exercise` tipa `scripted`, isti mehanizam koji već nose lekcije 2 i 4).
  Task 3 (ovaj) je uklopio obe u kurikulum i zatvorio fazu: `curriculum.json` dobija
  `notation` kao DRUGO i `tactics` kao ČETVRTO poglavlje (vidi „Put" i „Sadržaj lekcija"
  gore za detalje i poznata ponavljanja tema sa `middlegame`-om). `swift test` 58/58 bez
  ijedne izmene testa — `realCurriculumIsConsistentWithLessonsAndPuzzleDatabase` je
  potvrdio da obe lekcije postoje i da nove teme (`oneMove`, `fork`/`pin`/`skewer` u [600,
  1200], `fork`/`pin`/`discoveredAttack` u [600, 1100]) imaju dovoljno zadataka u bazi.
  `git diff --stat main..HEAD -- '*.swift' Chessko.xcodeproj/project.pbxproj` je prazan —
  **dokazano, ne pretpostavljeno**, da je "nova lekcija = nov JSON" i dalje tačno tvrdnja
  i posle dodavanja dva nova poglavlja, ne samo dva nova fajla lekcije.
  Izgrađen `.app` nosi tačno 36 lekcijskih JSON-a (4×8 + 2×2, prebrojano `find` po
  `Content/lessons`). Put u simulatoru vizuelno potvrđen u obe teme sa svih 6 poglavlja
  (privremeno zakucan koren `ContentView`-a na Put/na `LessonDetailView` direktno + zasejan
  `progress.json` u kontejneru; sve privremene izmene vraćene, `git status --short` prazan
  posle). **Nalaz o alatu, ne o sadržaju:** sintetički klik (cliclick) je JEDNOM pogodio
  tablu na prvom pokušaju, ali ni jedan sledeći klik (drugačije koordinate, isto mesto,
  drugi ekran) nije registrovan — `System Events` je u ovoj sesiji dosledno prijavljivao
  0 prozora za proces Simulator, što znači da GUI prozor simulatora ovde nije stvarno
  dostupan za klik; ekrani su ipak snimljeni preko `simctl io screenshot` (čita framebuffer
  direktno, ne zavisi od prozora). Zaključak: **odigravanje poteza na tabli unutar vežbi
  NIJE potvrđeno dodirom** u ovoj sesiji — potvrđeno je samo da se vežba ispravno iscrtava
  (tabla odgovara `startFEN`-u iz JSON-a) i da koristi isti rendering put kao već ranije
  vizuelno potvrđene vežbe u lekcijama 2 i 4. Ko god sledeći put radi vizuelnu proveru neka
  prvo proveri `osascript -e 'tell application "System Events" to count windows of process
  "Simulator"'` pre nego što pretpostavi da klik radi.

  **Talas ispravki posle pregleda Task-a 3 (tri nalaza, sva tri u dokumentaciji):**
  (1) Ovaj fajl je tvrdio da su vežbe u **obe** nove lekcije izvučene iz `puzzles.sqlite` i
  konvertovane. Tačno je samo za `tactics` — sve četiri se traže nazad do stvarnih zadataka
  (`lVYU8`, `osHSK`, `2CKmq`, `4tXYd`), potvrđeno pretragom cele baze i za direktan FEN i za
  FEN posle uklonjenog protivničkog poteza. Vežbe u `notation` **nisu iz baze**: to su ručno
  sastavljene pozicije iz teorije otvaranja, jer ta lekcija uči kako se potez ZAPISUJE pa joj
  treba potez koji se prepoznaje, ne taktički zadatak. Formulacija je došla iz plana faze, pa
  je implementator preneo tuđu netačnost — ista klasa greške kao netačan brief za vezivanje u
  Task-u 2. (2) Opravdanje da `tactics` ponavlja teme iz `middlegame`-a „jer baza nema dovoljno
  drugih tema" je izmišljeno: `deflection`/`hangingPiece`/`sacrifice` imaju po 190 zadataka u
  [600,1200]. Pravi razlog je pedagoški i sada tako i piše. (3) Sekcija „Testovi" je od Faze 4a
  govorila „31 test" umesto 58; razbijeno po fajlovima (perft 7, rokada 4, `PuzzleRepository` 14,
  rejting 5, sadržaj lekcija 4, kurikulum 7, napredak 17).

  **Talas ispravki posle finalnog pregleda cele grane (1 Important + 7 Minor, bez ijednog
  blokirajućeg):** engleska lekcija je slala čitaoca „on the Game screen", a tab se na
  engleskom zove **Play** (`Localizable.xcstrings`: `Igra → Play`) — ista klasa greške,
  netačna tvrdnja o sopstvenoj aplikaciji, samo blaža. Tvrdnja da zapis raste „pored table"
  važi samo u pejzažu; u portretu `MoveHistoryView` stoji ISPOD table (`GameView.swift:81`
  pejzaž / `:148` portret) — sada „uz tablu" / „alongside the board". Pasus o vezivanju je
  tvrdio kao opšte pravilo ono što važi samo **van** linije vezivanja (vezan top po e-liniji
  i dalje stvarno brani e5 i sme da se kreće po njoj) — dodat je taj uslov u oba jezika.
  FEN vežbe `Lxf7+` je nosio polutez `3`, koji nije dostižan: pozicija se dobija samo
  transpozicijom (`1.e4 e6 2.Sf3 e5 3.Lc4 Sc6`), gde je sat poluteza `2` — ispravljeno
  (sama pozicija je bila i ostala legalna). U dokumentaciji: blok koda je tvrdio da
  `build_lesson_json.py` regeneriše 32 fajla, a skripta je zamrznuta i odmah odustaje;
  dodato je i objašnjenje zašto `notation-practice` vuče `oneMove` zadatke umesto
  notacijskih (takve teme u Lichess bazi nema), i nov unos u „Poznata ograničenja" o tome
  da SAN nema disambiguaciju.
  **Jedan nalaz je NAMERNO ostavljen:** doc-komentar iznad `LessonStaticBoard`
  (`LessonRenderer.swift:175`) tvrdi da nijedna lekcija ne koristi `board` blok — od ove
  grane `notation` ga koristi, i to je prvi put da se ta grana koda uopšte iscrtava.
  Ispravka bi dirnula Swift i oborila centralnu tvrdnju faze („nijedna linija Swift-a"),
  pa ide kao prvi zadatak sledeće faze.

- **2026-09-12** — Faza 5, Task 5 (ekran analize partije). Nov `Chessko/Views/AnalysisView.swift`
  (tačnost oba igrača, kartica prelomnog poteza, traka poteza obojena po klasi; dodir na potez
  vodi u zatečen review mod preko `goToMove(ply + 1)`) i dugme „Analiziraj partiju" u `GameView`,
  vidljivo samo na gotovoj partiji sa bar jednim potezom. Dugme stoji u **obe** grane rasporeda —
  portret i pejzaž ne dele telo, pa bi jedno mesto ostavilo pejzaž bez dugmeta. Katalog 285 → 299
  ključeva × 8 jezika. `swift test` 82/82.
  **Popravljen stvarni bug, van obima taska:** `AnalysisViewModel.cancel()` postoji od Task-a 4
  ali ga do ovog ekrana niko nije zvao — prvo izvršavanje ga je i srušilo. ChessKitEngine ne
  pokreće zaseban proces: `EngineMessenger` `dup2`-uje `stdout` na sopstveni pipe, a `stop()`
  zatvara čitajući kraj; svaki upis posle toga šalje **SIGPIPE**, koji gasi CEO proces i ne
  ostavlja crash izveštaj. Zatvaranje lista analize u toku rada pa trenutno otvaranje obaralo je
  aplikaciju **4 od 8 pokretanja** (`RBSProcessExitStatus| domain:signal(2) code:SIGPIPE(13)` u
  logu simulatora); sa `signal(SIGPIPE, SIG_IGN)` u `ChesskoApp.init()` — **0 od 14**.
  **Nalaz koji NIJE popravljen (prijavljen):** `generation` brojač iz Task-a 4 se ne može dovesti
  u stanje koje brani. Mereno direktno (privremen brojač zaostalih izveštaja u `onProgress`):
  kroz 14 pokretanja sa `cancel()` + `start()` stiglo je **0** zaostalih izveštaja, i sa zaštitom
  i bez nje. Razlog: `AsyncStream.Iterator.next()` po otkazivanju završi iteraciju, pa se do
  `onProgress` posle poslednje pozicije nikad ne stigne. Brojač je ostavljen (tačan je i ne košta
  ništa), ali nije nosiv. Isto tako prijavljeno: ~15% brzih restart-ova završi na „Analiza nije
  uspela." jer izlaz starog motora završi u pipe-u novog — **to je jedini nalaz iz ovog taska
  koji je i dalje nepopravljen**. Drugi prijavljeni nalaz (`GameAnalysis.turningPoint` je umeo
  da izdvoji potez koji `MoveClass.classify` naziva „najboljim", pa je kartica nosila boju
  akcenta umesto upozorenja) **popravljen je na istoj grani**, u `cb19173` — `.best` se sada
  isključuje iz izbora, uz dva testa. Pun izveštaj:
  `.superpowers/sdd/2026-09-11-faza-5-analiza-partije/task-5-report.md`.

- **2026-09-12** — Faza 5 (analiza partije). Posle svake partije Stockfish prolazi sve
  pozicije, klasifikuje poteze i prikazuje ekran sa tačnošću oba igrača, trakom poteza u boji
  i prelomnim potezom; isti ekran služi kao povratna informacija za `game` korake Puta. Novi
  fajlovi: `Models/MoveAnalysis.swift` (Foundation-only, 17 testova), `Logic/UCIScoreParser.swift`,
  `ViewModels/AnalysisViewModel.swift`, `Views/AnalysisView.swift`; `StockfishBridge` dobio
  `evaluate` i `analyzeGame`. Testova 58 → 84, katalog 285 → 299 ključeva × 8 jezika.
  Detalji i izmereni brojevi — vidi sekciju „Analiza partije".

  **Četiri greške koje su uhvaćene tek zato što je traženo merenje umesto čitanja koda:**
  (1) Parser ocene je bio pisan po **pretpostavljenom** formatu `<score> cp 34`; stvarni je
  `<score> <cp> 34.0` — tag sa zagradama, a `cp` je u biblioteci `Double`. Parser bi vraćao
  `nil` za svaku poziciju i cela faza ne bi radila, a **svih 6 testova je prolazilo** jer su
  koristili isti izmišljeni oblik kao i kod. Test koji deli pretpostavku sa kodom ne testira
  ništa. Ispravljeno tako što su linije dobijene kompajliranjem i pokretanjem same biblioteke.
  (2) Za završnu poziciju motor ne vraća ocenu, pa bi analiza padala na svakoj odigranoj
  partiji — dodat `terminalEval`. (3) Otkazivanje analize je gasilo aplikaciju SIGPIPE-om.
  (4) `deinit { task?.cancel() }` se u `@Observable` klasi ne kompajlira; samostalan `swiftc`
  test bez makroa lažno prolazi.

  **Dve tvrdnje su povučene iz koda pošto su oborene merenjem**, jer je netačan zapis o uzroku
  gori od zapisa „ne znamo": objašnjenje kvara `responseStream`-a preko ispuštanja iteratora, i
  tvrdnja da bez `generation` brojača zaostali izveštaj gazi novu analizu.

- **2026-09-12** — Faza 6a (Android: offline zadaci + mešanje jezika). Android tab „Zadaci"
  više ne zove `chess-puzzles-api.vercel.app` nego čita istu `puzzles.sqlite` koju isporučuje
  i iOS, bajt-identično. Dodati `PuzzleRepository.kt`, `PuzzleRating.kt` (Elo, bez ijednog
  `android.*` uvoza pa JVM-testabilan), `puzzleRating` u `StatsManager` i režim vežbanja
  („Sledeći zadatak"). Testova 10 → 22 JVM + 9 instrumentisanih (11 posle talasa ispravki). Detalji — vidi „Baza zadataka"
  i „Stanje Android porta".

  **Dokaz koji se tražio nije bio screenshot nego rad bez mreže:** zadatak učitan i rešen sa
  isključenim wifi-jem i podacima (`ping` → „Network is unreachable").

  **Dva nalaza koja bi mehanizam učinila neupotrebljivim, uhvaćena merenjem:** AAPT po
  podrazumevanom spisku kompresuje `.sqlite`, pa `assets.openFd()` baca `FileNotFoundException`
  i svih 8 testova pada na konstrukciji — rešeno sa `noCompress`. I: testovi bi mogli da LAŽU,
  jer repozitorijum kopira bazu samo ako je nema, pa bi stara kopija u `filesDir` pustila
  testove da prolaze i kad asset uopšte nije u APK-u; zato se posebno dokazuje `unzip -v`.

  **„Resetuj statistiku" je popravljana TRI puta, i svaki put je popravka bila preplitka:**
  prvo je čistila samo disk (kvačica ostaje), pa je dobila osvežavanje keša ali bez okidača
  (nestaje tek kad korisnik sam okine učitavanje), i tek iz trećeg puta okidač koji ne
  restartuje zadatak u toku. Popravka koja očisti disk a ostavi keš je **gora od nikakve** —
  deluje kao da radi. Usput je utvrđeno da **iOS ima isti propust** (vidi „Poznata ograničenja");
  raniji komentar u kodu koji je tvrdio da ga iOS nema je ispravljen.

  **Spec 4.5 je zatvoren samo delimično**, i to je zapisano umesto prećutano: ekran Zadataka je
  čist, ekran Učenja nije — `LearnView.kt` nosi 46 zakucanih srpskih stringova koje pretraga po
  `text = "…"` ne vidi. Plan je tvrdio da faza zatvara 4.5 u celini; ta tvrdnja je bila preterana.

  Uklonjena i tiha mina: pet ključeva u `Loc.kt` nosilo je `%lld` — Swift format, nevažeći u
  Kotlinu, koji postojeći `try/catch` ćutke guta.

- **2026-09-12** — Faza 6a, talas ispravki iz finalnog pregleda cele grane (izveštaj u
  `.superpowers/sdd/2026-09-12-faza-6a-android-offline-zadaci/fix-wave-report.md`). Četiri
  IMPORTANT nalaza plus M-1/M-7/M-8/M-10.
  **(1) Rupa u unosu od 600 ms tiho je poništavala rešen zadatak.** Posle TAČNOG poteza
  `attempt()` je vraćao `phase = PLAYING` i tek za 600 ms igrao protivnički odgovor, a
  `isPlayerTurn` je u tom prozoru bio `true` — brz dodir se poredio sa PROTIVNIČKIM potezom
  iz `rawMoves` i padao kao greška koju korisnik nije napravio. Reprodukovano: četiri
  sintetička dodira za 101 ms obore `currentPuzzleStreak` sa 1 na 0, ekran ne pokaže ništa
  (`WRONG_MOVE` bude pregažen), a zadatak dovršen POTPUNO TAČNO ne uveća `puzzlesSolved` —
  dok ekran čestita. Faza 6a je to pogoršala, jer greška sada vodi i u
  `applyPuzzleResult(solved = false)`. Popravka je iOS obrazac: `awaitingOpponent` se diže
  pre odloženog `launch`-a, gasi u `finally` unutar `applyNextComputerMove()` (funkcija ima
  tri izlaza i svaki mora da otvori tablu nazad), i ulazi u `isPlayerTurn` I u gejt
  `showSolution()`. `LOADING` se ovde NE sme koristiti kao gejt — `PuzzleView` u toj fazi
  crta ekran učitavanja umesto table.
  **(2) Zaostala korutina je igrala potez nad NOVIM zadatkom.** Tri odložene korutine
  (400 ms prvi protivnički potez, 600 ms odgovor, 700 ms po potezu u reprodukciji rešenja)
  nisu imale brojač generacije, a strelice za datum su žive tokom svih tih pauza.
  Reprodukovano: tačan potez pa odmah strelica — aplikacija je sama odigrala IGRAČEV potez
  novog zadatka, `movePointer` je ostao na protivničkom potezu i zadatak je postao NEREŠIV.
  Dokaz je poređenje tabli piksel po piksel: pre popravke razlika (bbox
  `(0,279,237,1005)` — crni top već uzeo na g3), posle popravke `None`, pa je zadatak
  odigran do kraja. `loadGeneration` raste u `loadDailyPuzzle()`, `nextPuzzle()` **i**
  `setupPuzzle()` — poslednje zato što učitavanje ide preko IO korutine, pa dva brza zahteva
  mogu da stignu do `setupPuzzle` oba.
  **(3) Rejting igrača BIRA zadatke a nigde se nije video** — dodat `StatItem`
  „Rejting zadataka" u statistiku u `SettingsView.kt`, na isto mesto i u isti red kao na
  iOS-u. **(4) Tri stringa su zaobilazila `loc()`** (`"Greška pri učitavanju: …"`,
  `"Nema dostupnih zadataka."`, `"Neispravan FEN u zadatku."`); prvi sada prevodi samo okvir
  i interpolira već preveden razlog. Četiri nova ključa u `Loc.kt`, svaki na svih 8 jezika,
  prevodima usklađenim sa iOS katalogom.
  **M-7:** uklonjena `android.permission.INTERNET` — aplikacija nema nijedan mrežni poziv, a
  odsustvo dozvole je najjači dokaz za to. Provereni i build i pokretanje. **M-1:** dva nova
  instrumentisana testa (9 → 11). Plan je za negativan `dayIndex` tražio samo „ne-null"; to
  ne bi bilo dovoljno jer SQLite **negativan `OFFSET` tretira kao nulu** (provereno), pa test
  traži tačno preslikavanje — mutacija `dayIndex % n` ga obori. Drugi test na API 36 ne može
  da padne (SQLite 3.50 prima 32.766 parametara, granica od 999 postoji tek na `minSdk 26`
  uređajima), pa nosi i tvrdnju nad samom konstantom `MAX_EXCLUDED <= 999`; oboje zapisano u
  komentaru testa. **M-8/M-10:** doc komentar vraćen nad svoju funkciju; `openFd(...).length`
  → `.use { it.length }` (curio je po jedan fd po instanci repozitorijuma).
  `testDebugUnitTest` 22/22, `connectedDebugAndroidTest` 11/11 (čitano iz XML-a, ne iz
  izlaznog koda), `assembleDebug` uspešan.

- **2026-09-13** — Faza 6b (Android: sadržaj lekcija iz JSON-a). Ekran Učenja više ne nosi tekst
  zakucan u Kotlinu: `LearnView.kt` je sa 1322 pao na 1111 linija, 49 srpskih stringova je
  nestalo, a šest lekcija se čita iz istih 36 JSON fajlova koje isporučuje iOS. Novi fajlovi:
  `models/LessonContent.kt` (šema + `org.json` parser), `logic/LessonRepository.kt`,
  `ui/LessonRenderer.kt`. Testova 22 → 24 JVM + 26 instrumentisanih. **Spec 4.5 je time zatvoren
  u celosti** — vidi „Poznata ograničenja" za tri puta kojima srpski može da procuri i za dokaz
  da su sva tri zatvorena.

  **Pet defekata koje je otkrio tek stvarni ekran, nijedan vidljiv iz koda:** `icon` je ime SF
  simbola pa bi na svakom naslovu pisalo `crown.fill`; markdown `**bold**` se video kao
  zvezdice (614 pojave `**`, dakle 307 parova); `playingPrompt` iz JSON-a se nigde nije prikazivao, pa je 13 od 16
  vežbi gubilo uputstvo; četiri srpska stringa koja ni pretraga po `LPara("…")` ne vidi; i moj
  brief koji se nije kompajlirao jer `LessonInfo` više nema redni broj.

  **Blokirajući nalaz je proizvela jedna netačna rečenica u izveštaju** — tvrdnja da „ključ
  postoji". Nije postojao: literal je bio bez „(redovi 8)", pa je scenario „Promocija"
  prikazivao ceo srpski pasus na svakom stranom jeziku. Odatle i sistemska zaštita:
  `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary` izvlači **sve** `loc()` literale iz
  izvora i traži ih u rečniku. Stari test hvata samo krnje unose — ključ koji ne postoji mu je
  nevidljiv.

  **Usput ispravljena i posledica sopstvene popravke:** usmeravanje mat-zadataka u
  `MatePuzzleCard` je isključilo tri polja iz JSON-a. Razlika se danas ne bi videla jer se
  vrednosti poklapaju sa podrazumevanima, ali bi pao ugovor cele faze.

- **2026-09-13** — Faza 6c (Android: Put). Treći tab je od ove faze **Put** i na Androidu,
  isto kao na iOS-u od Faze 4a — `MainActivity.kt` menja `loc("Učenje")`/`Icons.Default.Book`
  u `loc("Put")`/`Icons.Filled.Map` i poziva `PathView()` umesto `LearnView(...)`. Sva četiri
  tipa koraka rade: `lesson`, `practice`, `test` (nula grešaka), `game`. Novi fajlovi:
  `models/Curriculum.kt` (`StepKind` sealed class, `CurriculumParser` — baca na nepoznat tip
  koraka i na obrnut `ratingRange`, isto kao iOS), `logic/PathProgress.kt` (čiste funkcije za
  otključavanje/cilj/streak, `object` bez ijednog `android.*` uvoza), `logic/ProgressStore.kt`
  (JSON u `filesDir`, migracija iz zatečenog `SharedPreferences` bez brisanja starih ključeva),
  `ui/PathView.kt`, `ui/StepPracticeView.kt`, `ui/StepGameView.kt`, `ui/PromotionOverlay.kt`.
  `ChesskoAndroid/app/src/main/assets/curriculum.json` je kopija
  `Chessko/Content/curriculum.json`, bajt-identična (`diff` prazan) — deljena baš kao lekcije i
  `puzzles.sqlite`, ne generisana po platformi. Testova 24 → 36 JVM + 26 → 46 instrumentisanih,
  0 padova (čitano iz XML-a) na završnom stablu. Nijedna nova Gradle zavisnost.

  **Živ bug pronađen i popravljen u Task-u 7, van teksta brief-a:** `showPromotion`/
  `confirmPromotion`/`cancelPromotion` su u `GameViewModel.kt` postojali od ranije, ali
  nijedan ekran nije crtao izbor figure — potez do zadnjeg reda je BLOKIRAO tablu (potez se
  nikad ne primenjuje), i to ne samo u koraku `game` nego i u slobodnoj partiji na tabu Igra,
  jer je `autoPromoteToQueen` podrazumevano `false`. Dokazano na emulatoru pre popravke
  (tabla se zamrzne posle tapa a7→a8) i posle (`PromotionOverlay.kt`, dijalog sa 4 figure,
  partija nastavlja).

  **Task 8 (zatvaranje faze) je popravio i drugi bug koji je Task 7 ostavio pod „Nedoumice":**
  završen `game` korak ostaje klikabilan (`PathView.kt`: `clickable = state != LOCKED`, a
  `COMPLETED != LOCKED`), pa drugi ulazak pravi nov `GameViewModel` čiji `init` SINHRONO učita
  staru ZAVRŠENU partiju sa diska — tek asinhroni `LaunchedEffect(stepId)` je posle toga
  resetuje, pa korisnik nakratko vidi staru gotovu tablu. Preneto od iOS-a
  (`GameViewModel.clearStepSave()`, `Chessko/ViewModels/GameViewModel.swift:814`): nov
  `GameViewModel.clearStepSave(context, stepId)` u `companion object`-u (radi direktno nad
  `SharedPreferences`, bez potrebe za živim primerkom modela), pozvan u `StepGameView.kt`
  odmah posle `progressStore.completeStep(stepId)`, na oba mesta gde se korak može završiti.

  **Odluka o `LearnView.kt`:** ekran spiska lekcija (`LearnView()` composable, `LessonCard`,
  `LessonInfo`) je posle Task-a 5 nedostižan iz UI-ja — obrisan, isto kao iOS (Faza 4a). Fajl
  SAM po sebi nije obrisan: `LBox`/`LPara`/`LBullet`/`LSectionHeader`/`LNumberedRule`/
  `PieceExplorer`/`OpeningExerciseCard`/`MateExerciseCard`/`MatePuzzleCard`/`OpeningLine`/
  `OpeningPhase`/`OpeningExerciseState`/`MateExerciseState` su i dalje jedini nosioci
  renderovanja sadržaja lekcije i `LessonRenderer.kt` ih zove direktno — to je većina sadržaja
  fajla, pa bi preseljavanje značilo premestiti skoro ceo fajl radi brisanja par stotina mrtvih
  linija. `LearnView.kt`: 1111 → 1021 (Task 5, izdvajanje `LessonDetailView`) → 880 (ovo
  brisanje, uz tri mrtva importa). Uz to uklonjen neiskorišćen `learnViewModel` u
  `MainActivity.kt` (deklarisan, nikad pročitan).

- **2026-09-14** — Faza 6d-1 (Android: dizajn sistem, deo 1). Sedam ekrana Android UI-ja
  prebačeno sa zakucanih boja na `DS.*` tokene, ista paleta kao iOS (`ChesskoColors` u
  `ui/theme/DesignSystem.kt`, `CompositionLocalProvider` u `ChesskoTheme`). Task 1: tokeni +
  `ContrastTest` (5 testova, kontrast se računa WCAG formulom). Task 2: `MainActivity.kt`.
  Task 3: zajedničke komponente (`UiComponents.kt`, `CapturedPiecesView.kt`,
  `MoveHistoryView.kt`, `EvalBar.kt`, `PromotionOverlay.kt`). Task 4: tab Zadaci. Task 5: tab
  Put. Task 6: Podešavanja. `dynamicColor` (Android Studio šablon, boja sa tapete) uklonjen.
  Detalji, tačne vrednosti i spisak namernih izuzetaka — vidi „Dizajn sistem" → „Android dizajn
  sistem" i „Poznata ograničenja".

  **Task 7 (zatvaranje kriške).** Preostalih zakucanih boja u sedam migriranih fajlova: **23**,
  sve namerne (birač boje figure u `MainActivity.kt` — 6, gradijenti uzetih figura u
  `CapturedPiecesView.kt` — 10, zlatna oznaka mata i njena fiksna podloga u `UiComponents.kt` —
  3, bela i crna polovina eval trake plus njen fiksni okvir u `EvalBar.kt` — 3, fiksna pločica
  ispod figure u `PromotionOverlay.kt` — 1; `MoveHistoryView.kt`, `PuzzleView.kt`, `PathView.kt`,
  `StepPracticeView.kt`, `StepGameView.kt`, `SettingsView.kt` su na nuli). Poslednja dva
  (`#708090`, oba) dodao je talas ispravki pred spajanje — objašnjena su niže, uz merenje. Sve prate ISTU logiku kao iOS-ovi „šta namerno nije token": boje strane u igri i
  sadržaj, ne hrom. Provere celog stabla: `Chessko`/`Chessko.xcodeproj` netaknuti (dokaz `git
  diff --stat` prema početku faze), nema novih zavisnosti u `build.gradle.kts`/
  `libs.versions.toml`, `Purple80`/`Pink40` šablon obrisan. Testovi na završnom stablu: **JVM
  41/41** (0 padova, iz `test-results/testDebugUnitTest/*.xml`), **instrumentisani 46/46** (0
  padova, iz `androidTest-results/connected/debug/*.xml`, emulator dignut `-gpu host` samo za
  ovaj prolaz i ugašen odmah posle).

  **Dva lažna pogotka, namerno NE upisana kao izuzeci:** `ui/PathView.kt:175` i
  `ui/PromotionOverlay.kt:90` pogađaju naivan grep za `Color.White`/`Color(0x`, ali oba pogotka
  su unutar komentara — oba fajla imaju nula takvih boja na tim mestima u kodu. Zato sirovi
  `grep -c` vraća 25 a tačan broj je 23. **Grepovana kontrolna lista mora se čitati, ne samo
  brojati.**

  Taj broj je u ovoj fazi bio pogrešan **tri puta uzastopno**, svaki put drugačije, i to je
  poučnije od samog broja. Prvo je dispatch beleška tvrdila 22 uz raščlanu koja daje 21, uz
  uputstvo „već izmereno, NE meri ponovo" — pa je izvršilac poslušno preneo tuđu netačnost;
  pouka nije „proveri brojeve" nego: **ko prosledi izmerenu vrednost sa zabranom ponovnog
  merenja, preuzima odgovornost za nju.** Zatim je ispravka na 21 zastarela u istom commit-u,
  jer su popravke eval trake i overlay-a promocije dodale dva nova namerna tona. I na kraju je
  ponovno prebrojavanje dalo 24, jer je naletelo na drugi komentar — isti oblik greške protiv
  koga ovaj pasus upozorava.

  **Talas ispravki posle finalnog pregleda cele grane (7 nalaza, 1 blokirajući).** Svaka
  vrednost ispod je IZMERENA WCAG 2.1 formulom, istom koju koristi `ContrastTest`.
  **(1) Isključen `Switch` je bio nevidljiv.** Task 6 je obrisao `SwitchDefaults.colors(...)` i
  prepustio M3 podrazumevanim vrednostima, koje isključen palac i ivicu vode na `Outline`
  (= `DS.line`) a traku na `SurfaceContainerHighest` (= `DS.fill`) — **1,067 (svetla) / 1,071
  (tamna)**, a red ispod je i sam `DS.fill`, pa se nije videlo ni gde da se tapne. Pogađalo je
  svih 10 `SettingsToggle` poziva; pre grane je bio `White 50%` palac na `White 10%` traci, dakle
  **regresija koju je grana uvela**. Vraćen eksplicitan `SwitchDefaults.colors` samo za
  isključeno stanje: palac i ivica `DS.inkMuted`, traka `DS.surface` — palac/traka **4,83 / 5,43**,
  palac/red **4,01 / 4,81**, sve preko praga 3:1. Uključeno stanje nedirano (8,53 / 6,71).
  **(2) Eval traka je gubila po jednu polovinu u stranicu.** Polovine su ispravno ostale fiksne,
  ali je okvir oko njih prešao na tokene, pa je bela polovina prema `DS.ground` merila **1,012 u
  svetloj** a crna **1,249 u tamnoj**, uz ivicu `DS.line` na 1,159 / 1,323 — preslabu da ih omeđi.
  Okvir vraćen na fiksan `#708090`, izabran merenjem a ne okom (jedini ton koji omeđuje i
  near-belu i near-crnu u obe teme): bela polovina **3,70**, crna **3,61**, `DS.ground` **3,66 /
  4,51**. Isti izuzetak kao sat: **fiksan sadržaj mora imati i fiksan okvir**.
  **(3) Naizmenične pruge u istoriji poteza su bile mrtva grana.** Kontejner je `DS.fill`, paran
  red proziran (dakle `DS.fill`), a neparan je slikao `DS.fill` preko `DS.fill` — **obe grane
  identičan piksel, 1,000**. Neparan red prešao na `DS.surface`: **1,204 / 1,129**. Dokazano
  pikselima iz screenshot-a, ne okom: `(231,234,241)` vs `(255,255,255)` u svetloj,
  `(30,39,64)` vs `(22,29,51)` u tamnoj.
  **(4) `PromotionOverlay` je koristio `DS.scrim` kao ispunu kartice**, uz komentar koji je to
  branio sa „isto kao iOS". Nije tačno — na iOS-u je `DS.scrim` isključivo celoekranska podloga
  (`PromotionOverlay.swift:24`) a kartica je `.ultraThinMaterial` (`:49`). `DS.scrim` je 55% crno
  i providno, pa su se polja table čitala kroz panel. Kartica prešla na `DS.surface` po tabeli
  preslikavanja (kao svih pet ostalih mesta), naslov sa `DS.onScrim` na `DS.ink` (**17,43 /
  14,76**). Usput je ispravljen i **zatečen defekt koji ovo tek učinilo vidljivim**: pločica ispod
  figure je bila `DS.onScrim` @8% nad scrim-om, što se slaže u `#3D3D3D`/`#17181A` — na njoj crna
  figura stila „Ravne" (puna silueta `#2C2C30`) meri **1,280 / 1,278**, praktično nevidljiva.
  Pločica je sada fiksna `#708090` (SVG figure ne prate temu, pa im ni podloga ne sme):
  „Ravne" bela **4,06**, „Ravne" crna **3,43**, prema kartici **4,05 / 4,12**.
  **(5)** Broj zakucanih boja u Task-u 7 ispravljen sa 22 na **21** (vidi pasus iznad).
  **(6) Undo u koraku `game` je imao istu boju u oba stanja** (`contentColor` i
  `disabledContentColor` oba `DS.inkMuted`), a uz to je zadržao M3 podrazumevanu
  `disabledContainerColor` (`onSurface` @12%) — pa je afordansa čitala **naopako**: onemogućeno
  dugme puna siva pilula, omogućeno goli glif. Poravnato sa ostatkom grane: `DS.ink` uključeno
  (**15,72 / 16,15** na `DS.ground`), `DS.inkMuted` isključeno (**4,36 / 5,94**), kontejner
  proziran u oba stanja.
  **(7) `ContrastTest` nije imao nijedan par nad `DS.fill`** — proveravali su se samo parovi nad
  `surface`, `ground` i `accent`. Da je taj blok postojao, nalaz 1 bi pao odmah. Dodata dva testa
  (5 → 7): `textOnFillMeetsAA` (`ink`/`accent`/`danger` ≥ 4,5) i
  `nonTextPairsOverFillAreDistinguishable`, koji tvrdi **oba smera** — da `inkMuted` prelazi prag
  3:1 i da `line` ne prelazi i nikad neće. Svaka nova tvrdnja dokazana mutacijom palete
  pojedinačno (6 mutacija, svaka obori tačno očekivani test).
  **Presuđeno drugačije nego što je brief predlagao — `warning`/`fill` = 3,00.** Brief je nudio
  „popravi boju ili upiši kao poznat par". Izabrano treće: **podloga**, ne boja. Statistička
  kartica je jedina na ekranu Podešavanja koja nosi semantički obojen tekst, pa je prešla sa
  `DS.fill` na `DS.surface` — time `success` ide 4,18 → **5,04** i `inkMuted` labela 4,01 →
  **4,83** (oboje preko AA), a `warning` na **3,61**, što je TAČNO par koji iOS `StatBox` ima na
  istom mestu i koji je već dokumentovan. Popravka boje bi značila razlaz sa iOS paletom;
  dokumentovanje bi značilo upisati DVE nove sub-AA vrednosti umesto da nestanu. Oba para su
  svejedno pinovana u testu, jer im vrednost i dalje postoji.
  Testovi: **JVM 43/43** (41 + 2 nova, 0 padova, čitano iz `test-results/testDebugUnitTest/*.xml`),
  `assembleDebug` uspešan. Vizuelna provera: jedan grupisan prolaz emulatora (`-gpu host`),
  obe teme, svih pet nalaza — screenshot-ovi u
  `.superpowers/sdd/2026-09-13-faza-6d-1-android-dizajn-sistem/final-fix-screenshots/`.
  Instrumentisani testovi (46) nisu ponovo pokretani: nijedan dirnut fajl nije u njihovom dometu
  (samo boje i jedan test fajl).
- **2026-09-16** — Faza 6d-2 (Android: dizajn sistem, deo 2, zatvaranje). Preostalih pet celina
  koje je 6d-1 ostavila van tokena sada prate temu: hrom sata (Task 1: dugmad, dijalog za izbor
  vremenske kontrole, info dijalog — crna i bela polovina sata OSTAJU fiksne, jer prate stranu a
  ne temu, isto kao iOS), blokovi i okvir lekcije (Task 2: `LessonRenderer.kt`,
  `LessonDetailView.kt` — `RULE`/`WARNING` kutije idu na `DS.warning`/`DS.danger` jer nose
  značenje, ne na akcent lekcije), ekran učenja i sve tri kartice vežbi (Task 3: `LearnView.kt`
  — `PieceExplorer`, `OpeningExerciseCard`, `MateExerciseCard`, `MatePuzzleCard`), i četiri
  kartice u Podešavanjima + tabla (Task 4: deo A uklanja mrtvu ivicu kartica, deo B poravnava
  boje table sa iOS vrednostima — poslednji potez 0,35 → 0,40, prsten uzimanja 0,55 → 0,65,
  tačka praznog polja 0,45 → 0,55; tabla i osam njenih tema OSTAJU netokenizovane, namerno —
  jedini šareni element). Detalji i razlozi — vidi „Android dizajn sistem" i „Poznata
  ograničenja" gore.

  **Netačna rečenica u ovom fajlu je ispravljena, ne prećutana**: „Highlight: žuto za poslednji
  potez, sivo za selekciju" nikad nije bilo tačno ni na jednoj platformi — obe koriste cijan
  `#00D2FF` (`SquareView.swift:101`, `BoardView.kt:408`).

  **Plan Task-a 4 je zamenio mesta dve alfa vrednosti** — tvrdio je da je prsten uzimanja na
  0,45 a tačka praznog polja na 0,55, obrnuto od stvarnosti (`BoardView.kt:436-450`, komentari
  `Capture ring` / `Empty-square dot`). Izvršilac Task-a 4 je razliku PRIJAVIO umesto da je
  prećuti, plan je ispravljen na izvoru sa zapisanim razlogom, i obe oznake su popravljene (obe
  su nosile isti pomak -0,10 od iOS vrednosti).

  `ContrastTest.kt`: 7 → 10 testova na kraju faze (Task 1 dodao pa uklonio jedan duplikat —
  `clockDialogTextMeetsAA` je proveravao tačno one parove koje `textOnBackgroundsMeetsAA` već
  pokriva u petlji nad obe teme, pa je test koji ne može da padne a da i drugi ne padne obrisan
  bez gubitka informacije; Task 4 dodao dva nova — `accentTextOnSurfaceAndGroundMeetsAA` i
  `settingsCardsSeparateFromGround`).

  > **Poruka commit-a `23b1bd5` tvrdi „38/38" — greška u sabiranju, nijedan test ne nedostaje.**
  > Tačan broj JVM testova u tom trenutku (posle Task-a 4, pre komentar-only ispravke sledećeg
  > commit-a) bio je 46; ispravljeno ovde, istorija commit-a se ne prepisuje.

  **Šest parova tokena je sada ispod WCAG AA u svetloj temi**, ne pet — Task 2 je dodao
  `warning`/`ground` (3,26) za `RULE` kutije koje sede direktno na `DS.ground` (bez `Card`/
  `Surface` iza sebe). Vidi „Poznata ograničenja" za svih šest vrednosti pune preciznosti.

  **Brojanje zakucanih boja u šest fajlova iz brief-a za Task 5 — svaki pogodak pročitan, ne
  samo prebrojan** (ista disciplina kao u 6d-1, gde je grep tri puta pogrešno pročitan, dva puta
  zbog pogotka unutar komentara): `ChessClockView.kt` 10 (svih 10 stvarni — crna/bela strana
  sata, namerno fiksne), `LearnView.kt` 1 (LAŽAN pogodak — `Color.White.copy(alpha=0.18f)`
  unutar KOMENTARA na liniji 432, koji opisuje istoimenu zamku iz 6d-1; stvaran kod na tom mestu
  koristi `DS.accent`/`DS.fill`), `LessonDetailView.kt` 0, `LessonRenderer.kt` 0 (oba potpuno
  migrirana Task-om 2), `BoardView.kt` 35 (svih 35 stvarni — boje table, prstenovi, konfeti
  paleta na liniji 611, sve namerno izvan dometa), `BoardTheme.kt` 8 (svih 8 stvarni — osam tema
  table koje bira korisnik). Naivan `grep -c` preko svih šest fajlova daje 54; stvaran broj
  namernih izuzetaka je 53, lažnih pogodaka 1.

  Provere celog stabla: `git diff --stat main..HEAD -- Chessko Chessko.xcodeproj` prazan (iOS
  netaknut), `git diff --stat main..HEAD -- build.gradle.kts libs.versions.toml` prazan (nijedna
  nova Gradle zavisnost), `Purple80`/`Pink40`/`dynamicColor` šablon i dalje odsutan (dva pogotka
  na „dynamicColor" u `Theme.kt` su komentari koji OPISUJU uklanjanje, ne upotreba). Testovi na
  završnom stablu, oba čitana iz XML-a: **JVM 46/46** (`ExampleUnitTest` 1, `PathProgressTest` 9,
  `LocTest` 5, `PuzzleRatingTest` 9, `ContrastTest` 10, `StepWindowTest` 3, `EngineTest` 9, sve
  0 padova) i **instrumentisani 46/46** (`CurriculumTest` 6, `ExampleInstrumentedTest` 1,
  `LessonContentTest` 9, `LessonRepositoryTest` 6, `ProgressStoreTest` 10, `PuzzleRepositoryTest`
  10, `StatsFacadeTest` 4, sve 0 padova/grešaka) — brojevi identični polaznom stanju od pre
  faze, što je i očekivano jer nijedna izmena nije dirala logiku koju instrumentisani testovi
  pokrivaju (samo boje i poravnanje table).

  **Emulator je za ovu fazu dizan DVA puta, ne jednom, i to je ovde priznato umesto prećutano.**
  Prvi (duži) prolaz je bio vizuelna provera: obe teme, sat (izbor vremenske kontrole preko
  kategorija Blic/Ubrzani/Klasični, info dijalog, aktivno/pauzirano stanje, i stvaran istek
  vremena posle pravog isteka od 5 minuta — potvrđena poruka „Vreme je isteklo!" na fiksnoj
  tamnocrvenoj/beloj podlozi u obe teme sistema), lekcija (`RULE` kutija „Uslovi za rokadu" i
  „Promocija", citat „Kapablanka piše", tabela vrednosti figura, `info`/bullet ikonice, vežbe
  elementarnih matova), ekran učenja (istraživač figura sa izabranim/neizabranim poljem u obe
  teme), podešavanja (četiri kartice bez ivice, prekidači u oba stanja), tabla (poslednji potez,
  selekcija, moguća polja — sve u obe teme sistema, dokazano da tabla ostaje identična nezavisno
  od teme aplikacije). Potvrđeno: **crna i bela polovina sata ostaju crna i bela u obe teme** —
  najveći rizik cele faze, obrnut ishod bi značio da je „prati stranu, ne temu" pravilo
  prekršeno tačno tamo gde je najvidljivije.

  > **ISPRAVKA (talas ispravki pred spajanje): tvrdnja iznad o satu je bila bez pokrića.** U
  > folderu snimaka nije bilo **nijednog** snimka sata u tamnoj temi (11–15 su svi bili svetli),
  > a `11_clock_select_light.png` i `12_clock_info_light.png` nisu prikazivali ni izbor vremenske
  > kontrole ni info dijalog nego mirni/aktivni sat. Snimci su preimenovani u ono što stvarno
  > prikazuju, a tamna tema je snimljena (39–43: mirno stanje, izbor vremenske kontrole, info
  > dijalog, partija u toku, istek vremena). Tvrdnja je time postala tačna; do tog trenutka je
  > bila samo napisana. Ironija koja vredi zapisati: baš `12_...` je najbolji dokaz **suprotnog**
  > nalaza — na njemu je bela polovina aktivna (`#FFFFFF`) i tadašnja `DS.surface` traka se u
  > njoj potpuno gubi (V-3).

  Drugi (kratak) prolaz je bio isključivo za
  instrumentisane testove (Korak 5) — otkriveno kasno da postojeći `androidTest-results` XML
  potiče od pre svih pet zadataka ove faze, pa broj nije mogao da se „pročita" bez novog
  pokretanja. Oba prolaza su odmah zatvorena (`adb emu kill` + `./gradlew --stop`, potvrđeno
  `pgrep -f qemu-system` praznim), i oba su koristila `-gpu host`, nikad `-gpu off`.
  Screenshot-ovi (prvi prolaz):
  `.superpowers/sdd/2026-09-14-faza-6d-2-android-dizajn-sistem-2/screenshots/`.

- **2026-09-16** — Faza 6d-2, talas ispravki posle finalnog pregleda cele grane (tri važna
  nalaza i sedam sitnih; nijedan blokirajući). Izveštaj:
  `.superpowers/sdd/2026-09-14-faza-6d-2-android-dizajn-sistem-2/final-fix-report.md`.

  **(V-1/V-2) Raspon alfe 0,75–0,9 je bio preslikan naniže, i to je bila greška u planu.**
  Tabela preslikavanja je pokrivala `White@0,4–0,7 → inkMuted` i `Color.White → ink`, a raspon
  između je preskočila; izvršilac je morao da pogađa. Posledica: telo lekcije je u svetloj temi
  palo na `inkMuted`. Sada je `DS.ink` na tri mesta u `LearnView.kt` — `LPara` (nosi 30 blokova
  isporučenog sadržaja, 4,36 → **15,72** nad `DS.ground`), telo `LBox`-a (24 kutije, 3,72–3,94 →
  **13,40–14,19** nad sopstvenim tintom, mereno kao stvaran kompozit) i `infoText` istraživača
  figura. iOS je za sva tri otvoren i proveren, ne pretpostavljen: `L_Para`
  (`LessonRenderer.swift:395`) i `L_Box` (`:447`) daju `.primary.opacity(0.85)`.
  **Tri mesta u istom rasponu NISU promenjena, i to je odluka a ne previd** — status „u toku" u
  `OpeningExerciseCard` i `MateExerciseCard` i količina u `LPieceRow`: sva tri iOS eksplicitno
  drži na `.secondary` (`LessonRenderer.swift:685`, `:809`, `:474`). Razlog je i sadržinski: to
  su mirni članovi pored obojenih, i `ink` bi ih učinio glasnijim od „rešeno". Izuzeci su
  upisani kao komentari na mestu, da ih sledeći prolaz ne „popravi".
  Uzgredna dobit koja je i sama bila nalaz: u istraživaču figura su dva različita zatečena nivoa
  (0,8 i 0,4) završila na istoj boji, pa je hijerarhija nestala — „Najjača figura…" i „27
  mogućih poteza" su se čitali kao isti red. Sada su tri nivoa, tri boje (`accent`/`ink`/
  `inkMuted`), snimak `47_explorer_hijerarhija_light.png`.

  **(V-3) Peti oblik načela „ne mešaj fiksno i tokenizovano" — vidi „Android dizajn sistem".**
  Kontrolna traka sata je prešla na `DS.surface` dok su polovine ostale fiksne; u svetloj temi je
  šav prema aktivnoj beloj polovini pao sa **14,63 na 1,000**. Popravka nije samo podloga:
  vraćanje podloge na fiksnu a ostavljanje `DS.ink`/`DS.accent` sadržaja NA njoj napravilo bi
  istu grešku u drugom smeru (`DS.ink` svetle teme na `#1E293B` = **1,19**). Zato traka sada ima
  tri fiksne konstante (`ClockBarBackground`/`ClockBarWell`/`ClockBarAccent`) i sav sadržaj čita
  `DarkColors` — fiksna površina, fiksni članovi. Čip vremenske kontrole je uz to spušten sa
  `@15%` na `@12%` tinta (4,339 → **4,583**, i to je iOS vrednost). Dijalozi sata ostaju na
  `DS.*`: oni lebde nad scrim-om i legitimno prate temu.
  **Šav prema CRNOJ polovini iznad trake ostaje nevidljiv (1,281/1,050) i to NIJE popravljeno** —
  bilo je tako i pre grane, i nijedna boja ne prelazi 3:1 istovremeno prema `#FFFFFF` i `#121212`;
  upisano u „Poznata ograničenja" umesto prećutano.

  **Sitni nalazi — svi su bili netačne tvrdnje, i zato su svi popravljeni:** KDoc u
  `ContrastTest.kt` je još pisao da ivice kartica u Podešavanjima „čekaju 6d-2" (ova grana JESTE
  6d-2 i Task 4 ih je uklonio); komentar u `PuzzleView.kt` je obećavao emoji migraciju „u 6d-2 za
  sve fajlove odjednom" (izričito van obima, odlukom korisnika — nije zakazana ni za jednu fazu);
  komentar u `LearnView.kt` je tvrdio „isti par kao birač teme/**težine**" — za temu tačno
  (`SettingsView.kt:506-507`), za težinu netačno (`DifficultyOptionRow`, `:690-721`, nosi izbor
  akcent TEKSTOM i kvačicom, bez ispune i bez `onAccent`). Ista klasa greške kao „isti obrazac
  kao kartica u `PathView`" iz Task-a 2 — tvrdnja o paraleli napisana bez otvaranja fajla.
  Uklonjena i tri mrtva `color=` sa emoji glifova (`🔄`, `ℹ️`, `▶️`/`⏸️`) — nalaz koji je ova
  faza sama napravila pa ga u istom fajlu pregazila.

  `ContrastTest.kt` 10 → 11 testova. Nov je samo jedan par —
  `clockControlBarIsReadableOverFixedHalves`, sa negativnom polovinom po uzoru na
  `plainWhiteWouldFailOnTheDarkAccent` (svetli akcent na traci daje 1,716, dokaz da `DarkColors`
  ovde nije stvar ukusa). Telo kutije je dodato kao tvrdnja u POSTOJEĆI `lessonBoxStylesMeetAA`,
  ne kao nov test: on je do sada merio samo naslov, a telo nosi ceo tekst. Test je za to dobio
  `private fun over(...)` — meri **stvaran kompozit** providnog tinta, ne približenje nad golom
  podlogom. Nijedan prag nije spušten i nijedan nov par nije upisan u
  `knownSubAAPairsDoNotGetWorse` (i dalje šest).

  Provere: `testDebugUnitTest` **47/47, 0 padova** (čitano iz XML-a: `ContrastTest` 11,
  `EngineTest` 9, `ExampleUnitTest` 1, `LocTest` 5, `PathProgressTest` 9, `PuzzleRatingTest` 9,
  `StepWindowTest` 3), `assembleDebug` uspešan, `BoardView.kt`/`BoardTheme.kt` i dalje 0 pogodaka
  na `DS.`. Jedan prolaz emulatora (`-gpu host`), zatvoren odmah po završetku (`adb emu kill` +
  `./gradlew --stop`, `pgrep -f qemu-system` prazan). Deset novih snimaka (39–48) i dva
  preimenovana (11, 12) u
  `.superpowers/sdd/2026-09-14-faza-6d-2-android-dizajn-sistem-2/screenshots/`.
- **2026-09-17** — Faza 6e (Android: analiza partije). Peta stavka spec Faze 6 je preneta —
  posle svake partije (i iz `game` koraka Puta) Android sada prikazuje isti ekran analize kao
  iOS: tačnost oba igrača, kartica prelomnog poteza, traka poteza u boji, dodir na potez vodi
  u review. Novi fajlovi: `models/MoveAnalysis.kt` (matematika, Foundation/JVM-only, 27
  testova), `logic/UCIScoreParser.kt` (parser UCI ocene, pisan po **uhvaćenom** izlazu native
  Stockfish-a, ne po pretpostavci — iOS-ov tagovan format ovde ne važi), `StockfishEngine.evaluate()`,
  `viewmodels/AnalysisViewModel.kt`, `ui/AnalysisView.kt` + dugme u obe grane `MainActivity.kt`
  i u `StepGameView.kt`. Detalji arhitekture (singleton motor sa jednim mutexom, timeout na
  svaku pretragu, `NonCancellable` „stop" pri otkazivanju) — vidi „Analiza partije — Android,
  šta je drugačije". Testova: JVM 47 → **83** (`MoveAnalysisTest` 27, `UCIScoreParserTest` 9);
  instrumentisanih 46 → **49** (nov `StockfishEvaluateTest`, 3 testa).

  **Task 6 (zatvaranje) je prvi put stvarno pokrenuo `StockfishEvaluateTest` na uređaju** —
  Taskovi 3/4 su ga samo kompajlirali. Prvi prolaz (drugo dizanje emulatora u fazi), čitan iz
  XML-a, reprodukovan dvaput identično: **48/49**, jedan pad (`mateInOneGivesPositiveMate`,
  30,0 s, „motor nije javio readyok na vreme"). Prva dijagnoza je bila POGREŠNA — pripisana
  test-poretku (prvi test klase troši jedinu `"readyok"` liniju, drugi visi). **Kontrolor je
  ispravio**: uzrok je u proizvodnom kodu, `StockfishEngine.waitUntilReady()` — funkcija je bila
  upotrebljiva TAČNO JEDNOM po životu procesa i to nigde nije govorila. Ispravna popravka:
  `@Volatile private var readyObserved` — drugi i svaki naredni poziv se vrati odmah `true` čim
  je spremnost jednom viđena, bez slanja novog `"isready"` (razlog zašto se to ne sme i dalje
  važi, vidi „Android (Faza 6e) — šta je drugačije"). Bez zastavice bi svaki DRUGI poziv
  ove funkcije u istom procesu — ne samo drugi test — visio 30 s **držeći `searchMutex`**, isto
  onako kako Taskovi 3/4 već štite `getBestMove`/`evaluate`. Popravka je zahtevala **treće
  dizanje emulatora u fazi** (plan je predvideo dva; treće je platilo ovu popravku, koju je
  drugo dizanje otkrilo — upisano iskreno, tvrdnja o dva dizanja iz ranijih task-ova nije
  prepravljena). Posle popravke: **49/49, 0 padova**, čitano iz XML-a;
  `mateInOneGivesPositiveMate` sada prolazi za 0,004 s (ranije 30,0 s).

  **Vizuelno provereno na emulatoru, obe teme** (drugo dizanje u fazi — treće je zasebno, samo
  za popravku `waitUntilReady()` iznad): ekran
  analize posle partije protiv računara (uključujući stvaran promašaj — hangovana dama, `2.Dg4`
  klasifikovana `BLUNDER`, kartica prelomnog poteza `−779`/`−756` u dva odvojena pokretanja —
  vidi napomenu ispod), četiri boje trake (`BEST` plavo/`DS.accent`, vidljivo odvojeno od
  zelenog `DS.success` za `EXCELLENT`/`GOOD`), dodir na potez → review mod (potvrđeno: klik na
  `2.Dg4` otvara tablu na „Potez 3 od 4"), i ekran analize iz `game` koraka Puta (otključan
  ručnim upisom u `progress.json` preko `run-as`, ne kroz UI — bez toga bi trebalo odigrati/
  otključati čitavo prvo poglavlje) — potvrđeno da „Korak je završen" (zelena kvačica) stoji
  PRE nego što je „Analiziraj partiju" uopšte dotaknuto, tačno ugovor koji spec traži. Snimci u
  `.superpowers/sdd/2026-09-16-faza-6e-android-analiza-partije/screenshots/`.

  **Izmereno, ne procenjeno:** partija od 10 poteza (11 pozicija) na `depth 12` završena za
  **~1,3 s** (17:01:02.931–17:01:04.256, iz `logcat`-a), prosek ~0,12 s/poziciju — primetno brže
  od baznog merenja Task-a 2 (~1,0 s/poziciju). Motor se između poziva ne resetuje
  (`ucinewgame` se ne šalje), pa deljena transpoziciona tabela unutar iste partije je verovatan
  ali nepotvrđen uzrok. **Broj je sa ovog emulatora, ne sa uređaja.**

  **Otkazivanje usred prave pretrage NIJE snimljeno na delu** — na izmerenoj brzini (~1,3 s za
  ceo niz) ručna reakcija preko `adb` (tap na „Zatvori" ~1 s posle starta) po svemu sudeći
  stiže POSLE što je analiza već završena, ne usred nje. Provereno je ono što se proveriti
  moglo: otkazivanje pa trenutačno pokretanje nove partije nije ostavilo zaglavljen ili tuđ
  potez — AI je odigrao svoj, ispravan potez odmah (1.e4 → 1…Sf6, isti odgovor kao u ranijoj,
  nesmetanoj partiji). Popravka Task-a 4 (`NonCancellable` „stop" pri otkazivanju,
  `a988fec`) ostaje potvrđena izvorom (`uci.cpp:105-106`, `engine.cpp:159` — `stop` je
  idempotentan) i logikom, ne i uhvaćenim trenutkom prave trke. Pokušano jeftino produžavanje
  partije (ponavljano premeštanje skakača) radi šireg prozora za otkazivanje — zaustavljeno na
  14 poteza kad je skakač nehotice izgubljen (dalji tapovi u istoj petlji su gađali prazna
  polja i partija se zamrzla na istom potezu), što nije dovoljno duže od postojećih 10 poteza da
  promeni zaključak. Ostaje zapisano kao nedokazano, ne kao provereno.

  **Uzgredan nalaz, van obima ovog zadatka (nije popravljen, samo zapisan):** završena partija
  (predaja) ne preživi rekreaciju `Activity`-ja (npr. promenu sistemske teme) — `load()`
  rekonstruiše poziciju kroz `GameState.fromFEN`, koji uvek vraća `GameStatus.Playing` (isti
  uzrok koji `terminalEval` u analizi već zaobilazi). Potvrđeno dvaput; upisano u „Poznata
  ograničenja".

  Provera celog stabla: `git diff --stat main..HEAD -- Chessko Chessko.xcodeproj` prazan (iOS
  netaknut); `ChesskoAndroid/app/build.gradle.kts` i `ChesskoAndroid/gradle/libs.versions.toml`
  bez izmena (nijedna nova Gradle zavisnost). `Chessko/Localizable.xcstrings` ostaje izmenjen u
  radnom stablu (Xcode-ova regeneracija) i namerno nije ušao u commit.

- **2026-09-17** — Faza 6e, talas ispravki posle finalnog pregleda cele grane (pet važnih
  nalaza i osam sitnih, bez ijednog blokirajućeg). Detalji svakog nalaza — vidi
  „Android (Faza 6e) — šta je drugačije" i „Poznata ograničenja".

  **Peta greška iste porodice, i najpoučnija: popravka koja je rešila uzrok a ostavila
  posledicu.** Task 4 je na otkazivanje dodao `"stop"` motoru i lanac od pet koraka proglasio
  zatvorenim — i u komentaru i u ovom fajlu. Zatvoreni su bili koraci 1–3 (motor prestaje da
  računa); koraci 4–5 žive u kanalu, ne u motoru: zaustavljena pretraga OBAVEZNO ispiše
  `bestmove`, a sledeći pozivalac prazni kanal na ulasku, pre nego što taj red stigne. Novi
  `StockfishEngine.drainUntilBestMove()` pojede red još pod istim `searchMutex`-om. **Izmereno:**
  put otkazivanje → `"stop"` → pročitan `bestmove` = **1 ms**, a stari `bestmove` je stizao
  **2 ms pre** prve komande sledeće pretrage — prozor je stvaran i uzak. Nov instrumentisani test
  `cancelledDeepSearchDoesNotPoisonTheNextEvaluation` (49 → **50**), **dokazan mutacijom**: bez
  oba poziva `drainUntilBestMove()` pada, sa njima prolazi.

  **`waitUntilReady()` je popravljena drugi put, jer je prva popravka bila preplitka.**
  `f61d26b` je zastavicu postavljala u samoj funkciji — rešava DRUGI poziv, ali ne i prvi poziv
  posle već obavljene pretrage, jer pražnjenje kanala na ulasku u svaku pretragu pojede jedini
  `"readyok"` u životu procesa. Da je gejt spremnosti uveden nad tom verzijom, najčešći tok
  (odigraj partiju → analiziraj) bi čekao 30 s i javio „motor nije spreman" za motor koji radi —
  peta greška bi se ponovila u samoj svojoj popravci. Zastavicu sada postavlja `listenOutput()`,
  jedino mesto kroz koje prolazi svaka linija motora, a funkcija je poll te zastavice i **više ne
  uzima `searchMutex`**. Provereno na uređaju: proces sa tačno jednim `"readyok"` (18:13:22) i
  pretragom posle njega (18:15:12) analizu odmah zatim odradi normalno.

  **Gejt spremnosti (V-5) je ušao i u `getBestMove`, ne samo u analizu** — obrazloženo, ne
  usput: posledica pretrage pre učitane mreže nije tiha degradacija nego `exit(EXIT_FAILURE)`
  (`engine.cpp:153-155` → `nnue/network.cpp:267`), dakle nestanak celog procesa, a cena gejta je
  posle gornje popravke jedno čitanje `@Volatile` polja. Prozor je izmeren: **5,25 s** na hladnom
  startu, **3,70 s** na toplom. Provereno da živa partija nije pogođena — partija na težini
  „Stockfish Majstor" odigrana na emulatoru posle izmene, AI odgovara normalno (`bestmove c7c5`).
  Nova greška `AnalysisError.ENGINE_NOT_READY` sa sopstvenim ključem na svih 8 jezika (ne
  pozajmljuje „motor nije pronađen", jer to je druga tvrdnja: nema motora se ne popravlja
  čekanjem). Uz to: analiza sada izlazi na PRVOJ neuspeloj poziciji, kao iOS
  (`StockfishBridge.analyze`), umesto da korisnik odstoji svih N+1 pretraga da bi mu se reklo da
  nije uspela; `String.format` za procenat tačnosti dobio je `Locale.US` (bez njega „41,9%" na
  nemačkom uređaju, iako se jezik bira u aplikaciji — isti obrazac koji traka ocene već koristi).

  **Ispravljene tvrdnje, ne prećutane:** komentar je za terminalnu poziciju prepisao iOS-ovo
  obrazloženje („Stockfish ne pošalje nijednu `score` liniju") koje za native Stockfish **nije
  tačno** — on ispiše `info depth 0 score mate 0` (`search.cpp:212-216` → `uci.cpp:620-621`);
  grana ostaje, ali iz pravog razloga. Istek `waitUntilReady()` nije pripadao
  `EVALUATE_TIMEOUT_MS` nego sopstvenom literalu koji se s njim slučajno poklapa.
  `StockfishLevel.MAXIMUM` nije „jedini nivo koji `getBestMove` dobija" nego najviši od šest.
  Paragraf „Testovi:" u „Stanju Android porta" je i dalje govorio **47/46** dok je changelog
  govorio 83/49 — sada **83 JVM / 50 instrumentisanih**, razbijeno po fajlovima. Jedna sekcija se
  zvala tri različita imena na tri mesta — ujednačeno.

  **Snimak označen `-dark` bio je svetao.** Piksel (20,300) u
  `03-put-step-analysis-dark.png` je bio `#F2F3F7` (`DS.ground` SVETLE teme), identičan onom u
  `04-…-light.png` — dva ista snimka, jedan pogrešno imenovan, i **nijedan snimak ekrana analize
  u tamnoj temi nije postojao** iako je ovaj fajl tvrdio „provereno u obe teme". Zato je emulator
  dizan **četvrti put** u fazi (plan je predvideo dva, Task 6 je zapisao tri — ovo je dopuna te
  tvrdnje, ne njena prepravka): snimljena je analiza u tamnoj temi i za slobodnu partiju
  (`05-analysis-dark.png`) i za `game` korak Puta (`03-put-step-analysis-dark.png`, koji je
  zamenio pogrešno označeni duplikat — stari fajl se od `04-…-light.png` razlikovao samo u
  statusnoj traci, dakle ništa nije izgubljeno). Pri tom je još jednom potvrđeno da „Korak je
  završen" stoji PRE nego što je „Analiziraj partiju" dotaknuto. Emulator ugašen odmah
  (`adb emu kill`, `./gradlew --stop`, `pgrep -f qemu-system` prazan).

  Uz to arhivirana dva `logcat` dump-a (`uhvaceno/full-dump-3.txt`, `-4.txt`) zbog kojih su dve
  od pet testnih UCI linija imale trag samo kroz izveštaj, ne kroz `uhvaceno/`; sada se svih pet
  linija iz `UCIScoreParserTest` nalazi doslovno u arhiviranom dump-u. Zapisano i jedno
  ograničenje koje se NE popravlja (dijalog analize ne pokriva sistemsku navigacionu traku).

  Provere: `testDebugUnitTest` **83/83** i `connectedDebugAndroidTest` **50/50**, 0 padova, oba
  broja čitana iz XML-a. `git diff --stat main..HEAD -- Chessko Chessko.xcodeproj` i dalje prazan
  (iOS netaknut), `build.gradle.kts`/`libs.versions.toml` bez izmena (nijedna nova Gradle
  zavisnost), `Chessko/Localizable.xcstrings` ostaje izmenjen u radnom stablu i **nije** ušao u
  commit.

- **2026-09-17** — Faza 7 (sitnice koje korisnik vidi). Pet stavki iz „Poznatih ograničenja"
  zatvoreno; svaka je gore **prepisana kao zatvorena, ne obrisana** — uz uzrok i način rešenja.
  Task 1: streak broj i oznaka razdvojeni (nema više „Niz: 1 dana"/„Streak: 1 days"). Task 2:
  `DisposableEffect(languageKey)` u `MainActivity` oslobađa `SoundPool` pri promeni jezika.
  Task 3: naziv meseca prati izabran jezik, uz `sr → sr-Latn` izuzetak (CLDR za goli `sr` daje
  ćirilicu). Task 4: `status` partije se serijalizuje odvojeno od FEN-a, pa završena partija
  preživi rekreaciju `Activity`-ja. Task 5: tabla više ne guta vertikalni skrol.
  **Šesta stavka je dodata, ne zatvorena zatečena:** polutez u Android FEN-u bio je zakucan na
  `0` (iOS to ima tačno od Faze 0) — detalji u „Poznatim ograničenjima".

  **Izmereni brojevi:** JVM **83 → 101** (+5 `GameStateStatusFromPositionTest`, +5
  `GameStateFenHalfmoveTest`, +8 `PuzzleDateFormatTest`), instrumentisani **50 → 52**
  (+`MainActivitySoundLifecycleTest`, +`GameViewModelActivityRecreationTest`). Oba iz XML-a,
  0 padova. Jedan prolaz emulatora u celoj fazi; `adb emu kill` + `./gradlew --stop` odmah po
  prolazu, `pgrep -f qemu-system` prazan.

  **Tri stvari koje je uhvatio tek emulator, i nijedna se ne vidi iz koda:**
  - **Dva od tri instrumentisana testa Taskova 2 i 4 nikad nisu bila izvršena** (samo
    kompajlirana), i prvo izvršavanje je otkrilo da `MainActivitySoundLifecycleTest` uopšte ne
    može da se pokrene na API 36 sa `espresso-core` 3.5.1. Nije bug u testu ni u aplikaciji nego
    u test-biblioteci; **3.6.1 takođe pada**, tek 3.7.0 prolazi (vidi „Stanje Android porta").
    Da nije bilo ovog prolaza, faza bi se zatvorila sa testom koji nikad nije ništa dokazao.
  - **Prva verzija popravke gesta bila je pokvarena, a ništa nije puklo.** `change.consume()`
    pre `change.positionChange()` čini da pomeraj uvek bude nula — skrol je radio, figura se
    dizala, ali se potez nikad nije odigrao. Nijedan test to ne pokriva (gest nema JVM test),
    pa je jedini dokaz bio pokušaj prevlačenja na uređaju.
  - **Poređenje „pre/posle" na istom uređaju, sa istim sintetičkim gestom**, bilo je jedino što
    je dokazalo da popravka gesta uopšte nešto menja: sa zatečenim `detectDragGestures` lekcija
    se ne pomeri ni za piksel, sa `awaitEachGesture` se skroluje.

  **Dokaz da je zvuk posle promene jezika živ nije bio screenshot** (emulator radi sa
  `-no-audio`) nego `dumpsys audio`: u trenutku promene jezika **prvo** se stvore dva nova
  `SoundPool` player-a pa se **onda** dva stara oslobode, a potez odigran posle toga daje
  `event:started` na novom player-u. Taj redosled je tačno ono što popravka Task-a 2 mora da
  garantuje — obrnut bi oslobodio pool koji je još u upotrebi.

  Snimci (22, uključujući i one koji dokumentuju pokvarenu prvu verziju popravke gesta —
  `06a`/`06b` — i stanje PRE popravke, `09a`/`09b`) u
  `.superpowers/sdd/2026-09-17-faza-7-sitnice-koje-korisnik-vidi/screenshots/`,
  ne u sesijskom `/tmp` — u ranijoj fazi su odatle nestali pre finalnog pregleda.
  `git diff --stat main..HEAD -- Chessko Chessko.xcodeproj` prazan (iOS netaknut);
  `Chessko/Localizable.xcstrings` ostaje izmenjen u radnom stablu i **nije** ušao ni u jedan
  commit. Jedina izmena build fajlova u celoj fazi je pin `espressoCore` (test-only).

- **2026-09-17** — Faza 7, talas ispravki posle finalnog pregleda cele grane (jedan važan nalaz
  i četiri sitna, nijedan blokirajući). Izveštaj:
  `.superpowers/sdd/2026-09-17-faza-7-sitnice-koje-korisnik-vidi/final-fix-report.md`.

  **V-1 je izmeren, i ispao je obrnut od nalaza.** Recenzent je iz koda izveo da je prečica
  „prevlačenje gore-dole menja stil figura" verovatno mrtva na ekranu Igra (roditeljski
  `verticalScroll` potroši pokret pre praga od 100f) i **pošteno označio da nije izmerio**.
  Izmereno je oba stanja koja rezonovanje ne razrešava — ekran koji staje bez skrolovanja i
  ekran sa prelivom — sa `pieceStyle`/`boardTheme` čitanim iz `chessko_settings.xml` preko
  `run-as`, ne sa slike. Na zatečenom `4092070` prečica radi u **oba** stanja, i u pejzažu, i
  vodoravno. Nalaz je time oboren — ali je isto merenje otkrilo **gori defekt koji niko nije
  tražio**: jedan isti pokret **i skroluje ekran i menja stil figura**. Lekcija „Tabla, figure i
  kretanje": prevlačenje po praznom polju dalo je `metal → flat` uz skrol; ekran Igra sa
  prelivom: `neon → wood` uz skrol; i obično skrolovanje lekcije je usput prevrtalo stil.
  Uzrok: `if (change.isConsumed) return` stajao je na **`Main` prolazu, gde dete uvek ide PRE
  roditelja**, pa potrošnja skrola tu nikad nije vidljiva — guard je bio mrtav kod, a rečenica u
  ovom fajlu („odustaje se čim skrol preuzme") **netačna**. Popravljeno čitanjem istog događaja
  još jednom na `PointerEventPass.Final`, koji ide obrnutim redom.

  **Cena popravke je zapisana kao ograničenje, ne prećutana:** `verticalScroll` troši pokret i
  kad nema šta da skroluje (provereno na svežoj partiji koja cela staje na ekran), pa je
  uspravna prečica sada mrtva svuda gde je tabla u vertikalnom skrolu — Igra u portretu,
  lekcije, koraci Puta. Preživljava u pejzažu (tabla je van skrolujuće kolone) i vodoravno
  (nijedan roditelj ne traži vodoravni pokret); tabela sa sve četiri kombinacije je u „Poznatim
  ograničenjima". **To je zamena, i svesna:** tiho menjanje korisnikovog podešavanja pri
  običnom skrolu je gore od prečice koja radi na pola ekrana. Odluka šta dalje (ugasiti
  `swipeToChangePieceStyle` podrazumevano, ili prečicu vezati za gest koji se ne sudara sa
  skrolom) je ostavljena korisniku, nije doneta ovde. Provereno i da popravka nije vratila
  staru zamku: prevlačenje figure i dalje odigrava potez (d2→d4, brojač poteza 6 → 8).

  **S-1** dopisan kao rezidual, gest nije diran: prevlačenje koje počne **na figuri** i dalje
  guta skrol, pa na vežbi iz otvaranja korisnik koji krene prstom sa figure ne može da skroluje.
  Nije regresija (staro ponašanje je gutalo i prazna polja), ali „ZATVORENO" se odnosi na prazna
  polja, ne na celu tablu.

  **S-2:** `MainActivitySoundLifecycleTest` je **preimenovan** u
  `SoundReleaseOnLanguageKeyChangeTest`, jer ne dodiruje `MainActivity` — rekonstruiše obrazac u
  sopstvenom `setContent`-u i prošao bi i da neko obriše `DisposableEffect` iz ekrana. Zaštitu
  koju je staro ime obećavalo sada stvarno pruža nov JVM test `MainActivitySoundWiringTest`
  (čita izvor, isti obrazac kao `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`),
  **dokazan mutacijom**: sa obrisanim `DisposableEffect` blokom pada, sa vraćenim prolazi. Do
  modela se iz testa ne može doći ni refleksijom ni test tagom (`remember { … }` u kompoziciji,
  ne `ViewModelStore`), pa je provera izvora jedino što postoji — i tako je i nazvana.

  **S-3:** `PuzzleDateFormatTest` više ne tvrdi doslovne nazive meseci za 8 jezika (ruski
  genitiv „сентября" i ostali su podatak CLDR baze u JDK-u, ne ponašanje aplikacije — nadogradnja
  JDK-a je mogla da obori build bez ikakve veze sa Chessko-om). Suštinske tvrdnje su zadržane
  ali izražene kroz **pismo** umesto kroz slova: srpski ne sme biti ćirilica (uz kontrolni test
  da ruski **jeste** — inače tvrdnja ne vredi ništa), `zh` i `hi` ne padaju na engleski.
  Dodato i mapiranje svih 8 kodova u sopstveni `Locale` i provera da obrazac `d. MMMM yyyy.`
  ostaje ceo. 8 → 7 testova, pokrivenost veća a ne manja.

  **S-4** zapisan, nije popravljan: `status` se od Task-a 4 vraća tačno samo za **tekuće**
  stanje; unosi u `history` se i dalje čitaju golim `fromFEN`, pa prvi „Vrati potez" posle
  ponovnog otvaranja aplikacije da poziciju bez oznake šaha i sa **praznim** panelom „Potezi".
  Asimetrija je nastala tek sada (ranije su i tekuće stanje i istorija bili podjednako krnji),
  ali nije uvedena — popravka bi tražila promenu formata zapisa pred spajanje.

  **Emulator je u ovoj fazi dizan peti put** (plan je predvideo dva; Task 6 je zapisao tri,
  finalna provera Faze 6e četiri — ovo je dopuna tog niza, ne njegova prepravka). Peto dizanje
  je platilo tačno jednu stvar: merenje nalaza koji je prethodno dizanje proizvelo. `-gpu host`,
  gašenje odmah po prolazu. Snimci `23`–`32` u
  `.superpowers/sdd/2026-09-17-faza-7-sitnice-koje-korisnik-vidi/screenshots/`.

  Provere: `testDebugUnitTest` **101/101** i `connectedDebugAndroidTest` **52/52**, 0 padova,
  oba broja iz XML-a. Nijedna nova Gradle zavisnost; `BoardView.kt` i dalje bez ijednog `DS.`;
  `git diff --stat main..HEAD -- Chessko Chessko.xcodeproj` prazan (iOS netaknut), a
  `Chessko/Localizable.xcstrings` ostaje izmenjen u radnom stablu i **nije** ušao ni u jedan
  commit.

- **2026-09-20** — Faza 8 (Android: emoji → Material ikone u hromu). Četiri taska su zamenila
  **34 emoji glifa** u interaktivnom hromu odgovarajućim `Icons.*` + `tint = DS.*` parom: sat
  (Task 1, 6 mesta — `436915d`), ekran Igra (Task 2, 13 mesta — `f621899`), Zadaci i koraci Puta
  (Task 3, 10 mesta — `52eb7ad`), Učenje i Podešavanja (Task 4, 5 mesta — `83b0806`). Namerno
  ostaju: `lessonIcon()` mapa (49 SF-simbol → emoji parova, sadržaj lekcije) i emoji u
  prevedenim porukama (`Loc.kt`) — oboje odlukom korisnika, drugo je i pariteta radi (iOS ima
  iste ključeve, isti emoji). Detalji, obrazloženja i izmereni kontrasti — vidi „Android dizajn
  sistem" gore.

  **Kontroler je obavezujućom tabelom ključeva pravio istu grešku tri puta pre nego što je
  postala pravilo.** Ime `contentDescription` ključa je izvodio iz izgleda ikone, ne iz njene
  upotrebe u kodu — posledica je bila pet ikona sa opisom koji ponavlja susedni vidljivi tekst
  (dva doslovno identična stringa), plus ikona protivnika (`Computer`/`People`) kojoj je isti
  ključ dat u DVA konteksta gde je oba puta dekorativna. Od Task-a 3 tabela je zamenjena
  pravilom: „ikona uz vidljiv tekst istog značenja je dekorativna, ikona koja stoji sama traži
  opis" — vidi „Poznata ograničenja". Izvršioci su sve nesuglasice **prijavili i ipak upisali
  zadato**, što je bilo tačno postupanje dok je tabela važila.

  **Merenje je promenilo odgovor jednom, ne samo potvrdilo ga.** Zatečena `Modifier.alpha(0.38f)`
  na dugmetu za reset sata (zaobilaznica iz Faze 6d-2, jer Skia ignoriše `color=` na emoji
  glifu) davala je glifu **2,75:1** — ispod WCAG praga 3:1. Zamena za `Icon(tint = DS.inkMuted)`
  (krug netaknut) daje **3,26:1** — zamena nije bila kozmetika nego popravka kontrasta koji je
  padao ispod praga.

  **Task 5 (zatvaranje):** JVM **101 → 105** (3 nova `ContrastTest` iz Task-a 1; Task-i 2–4 nisu
  dodali nove testove — parovi koje su uveli su već bili pokriveni postojećim testovima).
  Instrumentisanih **52** (nepromenjeno). Vizuelni prolaz: **svetla tema** je završena u toku
  Task-ova 1–4 (29 snimaka); **tamna tema** je dovršena ovim taskom (19 dodatnih snimaka,
  `29`–`47` u `.superpowers/sdd/2026-09-18-faza-8-emoji-u-ikone/screenshots/`, ukupno 48) — sat u oba
  aktivna stanja i sa onemogućenim resetom, info dijalog i birač vremenske kontrole (zvezdica
  dekorativna, vidljiva), ekran Igra u portretu i pejzažu sa svih šest dugmadi i sa dijalogom
  poraza, Zadaci sa kvačicom rešenosti i sa greškom u zadatku (`Nije to — traži pravi ključni
  potez!`), Učenje sa oba tražena ikona — `CheckCircle` (rešena vežba otvaranja, Sicilijanska
  odbrana odigrana potez-po-potez) i `EmojiEvents` (rešen mat-zadatak, Mini finalni test
  Zadatak 1) — i Podešavanja sa oba linka u „O aplikaciji". Crna i bela polovina sata su u
  tamnoj temi ostale crna i bela, kako i treba (prate stranu, ne temu) — najveći rizik faze je
  potvrđen bez nalaza.

  Prijavljena, nepopravljena (Task 5 sme samo `CLAUDE.md`): `LessonRenderer.kt:193` crta
  hardkodovan `⚠︎` mimo `lessonIcon()`/`Loc.kt` šablona (vidi „Poznata ograničenja").

  **Dugme „Predaj" je bilo prijavljeno kao pad ispod AA, pa izmereno i oboreno.** `DS.danger`
  tekst na `DS.danger@15%` podlozi daje **4,623** nad `ground` (gde dugme stvarno i stoji —
  `ActionsRow` sedi u `Box(DS.ground)`, `MainActivity.kt:691`), **5,086** nad `surface`, i
  **5,842 / 5,233** u tamnoj temi: **prelazi prag 4,5:1**, nema šta da se popravlja. Raniji
  brojevi (3,47 / 3,81 / 3,22) poticali su od **pretpostavljene** vrednosti tokena `#DC2626`
  umesto pročitane `#B3261E` (`DesignSystem.kt:61`); potvrđeno i uzorkovanjem piksela sa
  snimka `02_game_light_portrait_actions.png` (tekst `#B3261E` na `#E9D5D6` = **4,654**, uz
  okolinu `#F2F3F7` = `DS.ground`). Unos je zato **uklonjen** iz „Poznatih ograničenja":
  lažno ograničenje je gore od nezapisanog, jer šalje nekoga da „popravlja" dugme koje radi.

  **Talas ispravki pred spajanje** (isti dan). Pet važnih nalaza i pet sitnih, nijedan
  blokirajući; sve izmereno, JVM **105/105**, `assembleDebug` uspešan.
  - **Ista greška koju faza tvrdi da je zatvorila preživela je u `MainActivity`.** Četiri
    ikone u `ActionsRow` (`Undo`/`Flag`/`Share`/`Refresh`) nosile su opis koji ponavlja tekst
    odmah pored — `Button` spaja semantiku potomaka, pa bi TalkBack rekao „Vrati potez, Vrati,
    dugme". Četvrta je uz to i **pogrešno imenovala radnju**: opis „Pokušaj ponovo" (fraza sa
    ekrana Zadataka) na dugmetu koje se zove „Reset" i zove `onReset()` — jedna kontrola, dva
    imena. Sve četiri su sada `null`. Popravka pre ovog talasa je dirala **prijavljenu
    instancu** (`Computer`/`People`) a nije pomela fajl; pouka je da se pravilo primenjuje na
    fajl, ne na red iz izveštaja. Ni jedan `loc()` ključ nije ostao bez pozivaoca (sva četiri
    imaju druge potrošače — „Vrati potez"/„Predaj partiju" u `StepGameView`, „Podeli partiju"
    kao naslov `Intent.createChooser`-a, „Pokušaj ponovo" kao vidljiv tekst u `PuzzleView`).
  - **Provera obima je bila slepa za dva Unicode bloka, i dva glifa su je preživela.** Grep
    Task-a 5 pokrivao je `1F300–1FAFF`, `2600–27BF`, `2B00–2BFF` — van toga su **strelice
    `2190–21FF` i geometrijski oblici `25xx`**, pa provera **nije mogla** da potvrdi ono što
    tvrdi. Preživeli su `StepGameView.kt` (`Text("↩︎")`, `U+21A9`, ceo sadržaj dugmeta za
    vraćanje poteza u `game` koraku — bez opisa i bez auto-mirroring-a, dok je identična
    radnja na ekranu Igra u istoj fazi dobila `AutoMirrored.Filled.Undo`) i
    `LessonDetailView.kt` (`"◀ " + loc("Put")`, `U+25C0` — tačno obrazac „glif zalepljen na
    tekst" koji faza imenuje). Oba su prevedena: prvi u `Undo` **sa** opisom jer stoji sam,
    drugi u `Row { Icon; Text }` sa **dekorativnom** `AutoMirrored.Filled.ArrowBack`.
    **Ispravan opseg za sledeću proveru je `2190–27BF`, `2B00–2BFF`, `25xx`, `1F300–1FAFF`.**
    Pokrenut nad njim, modul danas daje pogotke samo u četiri namerno izuzeta segmenta:
    `lessonIcon()` mapa, prevedene poruke, `🔥`/`🧩` deljeni sa iOS-om, i `ChessPiece.kt`
    (plus `LessonRenderer.kt:193`, koji je ispravan kod — vidi „Poznata ograničenja").
  - **`loc("Trofej")` je bio poslednji ključ izveden iz izgleda ikone**, na značku rešenosti u
    `MatePuzzleCard`-u; dve identične značke 180 linija iznad su pisale „Rešeno". Usklađeno,
    ključ „Trofej" uklonjen iz `Loc.kt` kao mrtav.
  - **Pet blokova komentara tvrdilo je suprotno od koda dva reda ispod** — ranija ispravka je
    promenila kod i dopisala inline napomenu, ali ostavila originalni blok („primenjeno kako je
    tabela zadala" iznad `contentDescription = null`; „isti ključ" za ključ koji više ne
    postoji), plus komentar-siroče u `Loc.kt` koji je visio nad nepovezanim unosom. Sledeći
    čitalac iz toga nije mogao da zna da li je `null` odluka ili previd. Svih pet prepisano.
  - **Testovi sata su merili kopiju jednog člana para, ne sam par.** Oba nova testa iz Task-a 1
    su podlogu izvodila ručno (`over(Color.White, 0.12f, ClockBarBackground)`) dok je
    `ClockBarWell` stajao **uvezen a neupotrebljen** — a njegov doc-komentar otvoreno bira
    između 6% i 12%, pa bi promena te alfe promenila ekran a testovi ostali zeleni nad starom
    vrednošću. Sada čitaju konstantu (`over(ClockBarWell, ClockBarBackground)`); **dokazano
    mutacijom**: sa alfom 0,90 oba pada (1,064 i 2,554), sa 0,12 oba prolaze.
  - **`Icons.Outlined.Info` umesto `Icons.Default.Info` na traci sata** — jedina namerna zamena
    varijante na grani, i zato zapisana. `Filled` je pun disk: na 20dp u `DarkColors.ink` bio je
    najsvetlija puna površina na traci, teži i od primarnog čipa vremenske kontrole pored sebe,
    dok su `Close` i `Refresh` konturni glifovi u prigušenim krugovima — pomoć je najmanje važna
    kontrola u redu pa ne sme da bude najglasnija. Par boja je nepromenjen (12,929), pa ga i
    dalje pokriva `clockControlBarIsReadableOverFixedHalves`.
  - **`LocTest` je uhvatio komentar ove ispravke.**
    `everyLocCallInTheSourceHasAKeyInTheDictionary` čita izvor **kao tekst**, pa je ime
    uklonjenog ključa napisano u komentaru kao poziv (`loc` + zagrada) pročitao kao živog
    pozivaoca i oborio build. Nije bag testa nego njegova cena: zato se u komentarima ime
    ključa piše **bez** `loc` ispred.

  **Emulator:** sesija koja je radila Task-ove 1–4 je pukla na limitu i ostavila emulator
  aktivan **3 sata 59 minuta** — nijedan zadatak ga tada nije koristio, samo je zaboravljen
  upaljen. Ova sesija ga je zatekla i ugasila pre sopstvenog dizanja. Sopstveno dizanje (za
  tamnu temu) je bilo jedno, `-gpu host`, i ugašeno odmah po snimanju — `adb emu kill` +
  `./gradlew --stop` + `pgrep -f qemu-system` prazan, potvrđeno.

- **2026-09-21** — Faza 9 (Android: lekcijski simboli u Material ikone + prečica prevlačenjem).
  Poslednji emoji-blok na Androidu — **lekcijska mapa** — prešao je na `Icons.*` + `tint`:
  `SYMBOL_TO_GLYPH` je danas **43 `Icon` + 6 `Emoji` = 49**. Uz to je prečica „prevlačenje menja
  stil figura" dobila okidač koji se ne sudara sa skrolom (*zadrži pa prevuci*), a usput su
  popravljena **dva buga u gestu table** — jedan zatečen, jedan koji je faza sama uvela. Detalji:
  „Lekcijski glifovi" i „Poznata ograničenja" gore.

  **Izmereni brojevi, oba iz XML-a, 0 padova:** JVM **105 → 110** (`ContrastTest` 15 → 18 kroz
  Taskove 1–2, nov `LessonBoardsOptOutOfSwipeTest` 2), instrumentisani **52 → 54** (nov
  `LessonGlyphMapTest` 2). Nijedna nova Gradle zavisnost; `git diff --stat main..HEAD --
  Chessko Chessko.xcodeproj` prazan (iOS netaknut); lekcijski JSON i `curriculum.json` i dalje
  bajt-identični sa iOS-om (`diff -r` prazan).

  **Task 0 postoji zato što je plan bio strukturno pogrešan, i to je uhvaćeno PRE dispečovanja.**
  Plan je izmenu opisao kao „mapa postaje `Map<String, ImageVector>`, fajl `LessonRenderer.kt`".
  Oboje netačno: `lessonIcon()` je imao **8 pozivnih mesta u 2 fajla** i ulazio u **šest
  potpisa**, a `ImageVector` ne može da nosi šest simbola koji ostaju emoji. Dodat je Task 0 koji
  uvodi `LessonGlyph` (Icon/Emoji/Unknown) i jedno mesto crtanja, **bez ijednog prevedenog
  simbola** — ekran identičan pre i posle, dokazano poređenjem stare i nove mape (bajt-identična)
  i provlačenjem svih 49 stvarnih `icon` vrednosti kroz obe logike (0 razlika). **Pouka:**
  planirao sam izmenu tipa ne otvorivši potrošače tog tipa — ista klasa greške kao obavezujuća
  tabela ključeva iz Faze 8, samo uhvaćena pre nego što je dobila autoritet plana.

  **Presuda o šest simbola je merena, ne odokativna.** U `Icons.Filled` nema **nijedne** šahovske
  figure ni ijednog taktičkog motiva (raspakovan sources jar `material-icons-extended` 1.7.8,
  **2083** imena). Unicode figure su razmotrene kao treće rešenje i odbijene brojevima:
  `crown.fill` je 70 od 150 pojava tih šest i **polisemičan** (4 Dama / 3 Kralj / 4 nije figura
  uopšte, po 11 mesta u `*.sr.json`), a ostala tri su POTEZ i MOTIV, ne figura — za preostala dva
  (top, lovac, 27% pojava) bi trebao **treći slučaj** u `LessonGlyph`, jer emoji grana namerno ne
  prima boju a Unicode figura je mora dobiti. Nov mehanizam za 2 od 49 simbola.

  **`square.grid.3x3.fill` NIJE među tih šest, iako ga je plan tamo stavio.** Zato je broj
  **43+6**, ne 42+7. Merenje: simbol se u **telu** lekcije ne javlja nijednom, a naslovna je
  ikona za **8 od 36** lekcijskih fajlova — opcija „ikona u zaglavlju, emoji u telu", koju sam
  sam ponudio kao treći put, nije imala predmet. Ide na `Icons.Filled.GridOn`; zatečeni ♟️
  ionako nije bio ono što simbol znači (mreža polja, ne figura — iOS tu crta tablu).

  **Zatečen bug, van teksta brief-a: vodoravno prevlačenje po lekcijskoj tabli menjalo je
  GLOBALNU temu table.** iOS to sprečava od ranije (`allowsStyleSwipe`, `false` na tačno pet
  mesta u `LessonRenderer.swift`); Android je imao **istih pet** mesta bez ijednog izuzetka. Ista
  klasa greške koju je Faza 7 zatvorila za **uspravnu** osu — preživela je taj ceo talas na
  vodoravnoj, jer uspravnu je slučajno gušio roditeljski skrol a vodoravnu niko.

  **Regresija koju je ova faza uvela pa sama popravila, i njen uzrok:** tap na prazno polje je
  ostavljao gest-čvor zaglavljen, pa je **sledeće prevlačenje** bilo progutano i procurilo
  roditeljskom skrolu — korisnik dobija **skrol umesto poteza** (izmereno: tabla se pomeri
  **232 px**; brojač poteza `4 → 4` umesto `4 → 6`). **Pouka nije „proveri gest":** brief je
  izričito tražio da se prevlačenje figure ne pokvari, provera je gledala **redosled
  `positionChange()`/`consume()`** — mesto gde je Faza 7 imala bug — i bila čista. Regresija je
  ušla kroz **stanje gesta**, ne kroz redosled: zaštita se branila od **prošle** greške.
  Našao ju je pregled **čitanjem izvora Compose-a** (`ForEachGesture.kt:79-87`), a merenje ju je
  potvrdilo; za nju **nema testa** i to je upisano kao poznato ograničenje, sa navedenim oblikom
  testa koji nedostaje.

  **Tabela prečice iz Faze 7 je bila NETAČNA, i to od Faze 7 — ispravljena je, ne obrisana.**
  Tvrdila je da uspravna prečica ćuti u „koraci Puta"; ti ekrani nemaju **nijedan** skroler, pa
  tamo radi. Mrtva je bila u **dva** konteksta (Igra u portretu, lekcije), ne u četiri. To je
  promenilo i presudu: gašenje prekidača ili uklanjanje prečice kaznili bi četiri ekrana zbog
  jednog.

  ### Četiri stvari koje je ova faza pogrešila, i koje ne smeju ispasti iz zapisa

  - **Kontroler je dvaput prosledio tuđi broj ne izmerivši ga, i oba puta je implementer koji je
    merio bio u pravu.** (1) Broj imena u `Icons.Filled`: prosledio sam recenzentovih **2082**;
    implementer ga nije prepisao nego izmerio na tri načina i sva tri dala **2083**, pa je
    zadržao 2083 i u komentar upisao komande kojima se izvodi. (2) Pinovana kontrast vrednost
    `2.9361847662648937`: tvrdio sam da se ne reprodukuje, na osnovu **tri računa sa strane**
    (pun double, float32, ručno FP16 pakovanje) — nijedan nije dao pin. Implementer je pustio
    **stvarni proizvodni put** (`over()`/`contrast()`) i ispisao rezultat: identičan pinu, dvaput,
    deterministički. Uzrok razlike: `Color(red:green:blue:)` Compose pakuje u **FP16**, pa svako
    čitanje `.red`/`.green`/`.blue` posle kompozicije već nosi izgubljenu preciznost, a moje
    ručno FP16 pakovanje nije bit-identično njegovom. **Pouka: broj koji proizvodi biblioteka
    proverava se pokretanjem biblioteke, ne ponovnim izvođenjem njene aritmetike.** Provera ipak
    nije bila uzaludna — istom sondom je izmereno i **zatečenih četiri brojeva** u doc-komentaru,
    i sva četiri su bila pogrešna, jer ih nikad nije nosila nijedna tvrdnja.
  - **Kontroler je i sam upao u zamku koju ovaj fajl već opisuje.** Naivan
    `grep -c "LessonGlyph.Icon("` nad opsegom mape dao mi je **44**; 44. pogodak je bio
    **komentar**. Tačno je 43. Isto kao brojanje zakucanih boja u Fazi 6d-1: *grepovana
    kontrolna lista se čita, ne samo broji.* Recenzent je istu zamku uhvatio na drugom broju
    (36 sa filterom, 37 bez — višak je `Icons.Filled.Restaurant` u komentaru).
  - **Task 2 je nasledio pogrešnu brojku klasa sabirajući tuđe brojeve umesto izvođenjem iz
    mape** (14+20=34; stvarno 35, jer Task 1 ima 15 distinct klasa, ne 14). Ista greška koju ovaj
    fajl već nosi iz Faze 6d-1, gde je broj zakucanih boja bio pogrešan **tri puta uzastopno** —
    i tamo je koren bio isti: prosleđena vrednost primljena bez ponovnog merenja. Runda ispravki
    nije popravila samo ispis nego **izvor**: komentar sada navodi komandu kojom se broj dobija.
  - **Merenje na uređaju je implementera dvaput slagalo, i to je prijavio umesto da prećuti.**
    Prvi put su „pala" i kontrolna merenja, jer su **zakucane koordinate** posle odskrolovane
    strane gađale pogrešna polja; drugi put jer je pozicija bila **šah**, pa su izabrani potezi
    bili nelegalni. Oba puta je zaključak bio pogrešan dok se stanje nije pročitalo **sa ekrana**.
    Isti rod greške kao računanje umesto merenja, samo obrnut: **merio je, ali ne ono što je
    mislio.**

  Uz to su dva izveštaja nosila tvrdnje koje nisu izdržale otvaranje fajla, i oba puta ih je
  uhvatio **sledeći** task, ne pregled: Task 1 je zapisao `crown.fill` kao naslovnu ikonu lekcije
  (nije — to je `square.grid.3x3.fill`, 8 od 36 fajlova), a `task-2-report.md` je opisao posao
  koji nije uradio („dopunjen komentar iznad testa" — komentar je bit-za-bit nepromenjen; jedina
  izmena je bio nov test). Tehnički zaključci su u oba slučaja bili tačni; pogrešan je bio **opis
  sopstvenog posla**, a to je način na koji neproverena tvrdnja dobije autoritet i uđe u sledeći
  task.

  **Nalaz koji ni jedan od tri dotadašnja pregleda nije video:** `Unknown` se dodeljivao samo
  imenu **sa tačkom**, a tri isporučena SF imena je nemaju (`globe`, `link`, `tuningfork`) —
  izbacivanje takvog para bi **tiho nacrtalo bukvalnu reč** „tuningfork" usred lekcije, tačno ono
  što Task 0 postoji da spreči. Pravilo je prebačeno na **ASCII** i zadržano staro uz novo (novo
  je strogo šire, pa nijedan slučaj koji je ranije vikao sada ne ćuti); izmereno da se ni za jedan
  od 49 isporučenih simbola grana ne menja. Test je pri tom **preuređen, ne dopunjen**: tvrdnja
  koja je popravkom postala **nedostižna** je uklonjena umesto da ostane kao provera koja ne može
  da padne, a mutacija sada obara **49/49** parova zahvaljujući **pravilu**, ne dodatnoj tvrdnji.

  **Emulator je dizan dvaput, i drugi put je bio odobren izuzetak.** Faza je predvidela jedan
  prolaz (Task 4); drugi je platila sumnja na živu regresiju u gestu table — pravilo je protiv
  emulatora koji stoji upaljen i protiv dizanja po tasku, ne protiv merenja kad postoji osnovana
  sumnja. Oba puta `-gpu host`, oba puta ugašen odmah (`adb emu kill` + `./gradlew --stop` +
  `pgrep -f qemu-system` prazan, zalepljeno u izveštaju).

  **Šta NIJE potvrđeno, i tako se i navodi:**
  - **4 od 6 emoji-ja nisu vizuelno potvrđena** (`dot.square.fill`, `l.joystick.fill`,
    `rhombus.fill`, `tuningfork`) — nisu se pojavila na obiđenim ekranima. Mapa ih nosi i
    `LessonGlyphMapTest` ih pokriva kao ne-`Unknown`, ali ih nijedan snimak ne prikazuje.
    Viđena su 👑 i 🏰, u punoj boji među monohromatskim ikonama.
  - **Pejzaž je dobijen `wm size 2400x1080`, ne rotacijom** (headless rotacija na ovom AVD-u ne
    prolazi: `user_rotation` se upiše, `mCurrentOrientation` ostane 0). Grana rasporeda se bira
    po odnosu stranica, pa **jeste** izvršena — ali je geometrija simulirana, ne rotiran uređaj.
  - **Zatečeno ponašanje gejta „bilo koji od dva prekidača" nije mereno zasebno** (stari APK je
    u međuvremenu prepisan); da bi kralo skrol sledi iz merenja naoružanog gesta, i ne tvrdi se
    više od toga.
  - Jedan snimak je bio **bajt-identičan** prethodnom (skrol nije napredovao jer je gest počeo na
    polju sa figurom) — obrisan umesto da stoji kao dokaz, uhvaćeno poređenjem piksela.

  22 snimka (obe teme za svaki ekran iz brief-a; tema potvrđena **pikselom**, `(242,243,247)` =
  `#F2F3F7`) u `.superpowers/sdd/2026-09-20-faza-9-lekcijske-ikone-i-precica/screenshots/`.
  `Chessko/Localizable.xcstrings` nije diran u ovoj fazi.
