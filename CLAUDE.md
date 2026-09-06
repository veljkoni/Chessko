# CLAUDE.md — Chessko

> Ovaj fajl uvek pročitaj na početku rada i ažuriraj na kraju svakog prompta
> (sekcija "Changelog" + relevantne sekcije ako se arhitektura promeni).

## Šta je aplikacija

**Chessko** — iOS aplikacija: šah protiv računara (AI), sa dodatnim modovima
(igra sa prijateljem na istom uređaju, samostalni šahovski sat). Igrač bira
boju (beli ili crni; podrazumevano beli) — tabla se rotira kad igra crnim.
UI je lokalizovan na 8 jezika (izvorni srpski). `ContentView` je `TabView`
sa tri taba: Igra, Zadaci (dnevni puzzle), Učenje (lekcije).

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

Pokriveno (11 testova): perft za svih 6 standardnih pozicija (uključujući
početnu do dubine 5, 4.865.609 čvorova, ~85s) i 4 testa prava rokade
(uzimanje topa na sva 4 ugla, i partija bez topa koja i dalje nosi zastarelo
pravo). `Chessko/TestSupport/LocShim.swift` postoji samo zbog paketa i
zaštićen je `#if CHESSKO_ENGINE_PACKAGE` — u aplikaciji se ne kompajlira.

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

## Arhitektura (MVVM)

```
Chessko/
├── ChesskoApp.swift          @main, WindowGroup → ContentView
├── ContentView.swift         → GameView()
├── Models/                   čiste vrednosne strukture (struct/enum, Sendable)
│   ├── Position.swift        row 0 = rank 8 (crni), col 0 = file a
│   ├── ChessPiece.swift      PieceType, PieceColor, materialValue, Unicode symbol
│   ├── ChessMove.swift       from/to/flag; == poredi from+to **i** flag
│   └── GameState.swift       cela tabla + prava rokade + status; immutable apply
├── Logic/
│   ├── MoveGenerator.swift   generisanje poteza, detekcija šaha (enum, statičke fn)
│   └── ChessAI.swift         negamax + alfa-beta, piece-square tabele
├── ViewModels/
│   └── GameViewModel.swift   @Observable @MainActor — sva interakcija + AI okidač
├── Views/                    GameView, BoardView, SquareView,
│                             CapturedPiecesView, PieceImageView
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
  `boardBackground #17234f`. Highlight: žuto za poslednji potez, sivo za selekciju.
- **Rotacija table**: `viewModel.isFlipped` → `BoardView` iterira redove/kolone u obrnutom
  redosledu; `SquareView` dobija `isBottomEdge`/`isLeftEdge` za koordinatne labele.
  `AnimatingPieceView` i `flyingCapture` overlay koriste display koordinate.

## Poznata ograničenja / TODO kandidati

- Stockfish radi samo sa `nn-37f18f62d772.nnue` (mali); `nn-1111cefa1111.nnue`
  (veliki, ~79MB) opcionalan za jaču igru — skinuti sa stockfishchess.org.
- `positionKey` (`GameState.swift:97-100`) uključuje prava rokade u heš. Partija
  sačuvana starijom verzijom sa zastarelim pravom (top uzet, pravo ostalo) daje
  drugačiji `positionKey` od identične pozicije bez tog prava — brojač za
  trostruko ponavljanje se posle nadogradnje može "razdvojiti" i propustiti remi.
  Bezopasno (retko, ne ruši partiju), ali vredi zapisati.
- **Isti bug sa rokadom postoji na Androidu** —
  `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/models/GameState.kt`
  (grane `CastleKingside`/`CastleQueenside` i blok koji oduzima prava rokade
  gledaju samo `move.from`, tačno ono što je iOS imao pre Faze 0). Popravka je
  planirana za Fazu 6.
- `PuzzleView` portretni raspored je `VStack` bez `ScrollView`-a, sa svega
  ~10–20pt rezerve ispod poslednje kontrole — rizik od sečenja sadržaja na
  većem Dynamic Type-u ili na manjim ekranima.
- `build_localizations.py` pri svakom pokretanju regeneriše ceo
  `Localizable.xcstrings` i briše Xcode-ove auto-ekstraktovane ključeve iz
  izvornog koda (bez prevoda — Xcode ih sam vrati pri sledećem build-u), ali
  diff od ~28.000 linija po pokretanju može sakriti stvaran gubitak ako se
  ikad desi.

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
      `build_localizations.py` (300 ključeva × 8 jezika).
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
  akcija "Sledeći zadatak" (`DS.accent` pozadina + `DS.onScrim` tekst, isti par kao dugme
  za pauzu u `ChessClockView`) uvek na vrhu, dostupna i posle dnevnog i posle vežbovnog
  zadatka; postojeće "Sledeći dan"/"Završio si zadatak za danas!" ispod, nepromenjene
  funkcionalno (samo "Sledeći dan" prebačen na sekundarni stil — `Color.primary.opacity`,
  isti kao "Prikaži rešenje" — da ustupi mesto novoj primarnoj akciji). Nov ključ
  "Sledeći zadatak" dodat u `build_localizations.py` (za `add("Sledeći dan", ...)`) i
  katalog regenerisan: 406 → 407 ključeva (tačno +1), svih 8 jezika po ključu potvrđeno
  python skriptom.
  `swift test`: 26/26 prošlo (23 postojeća + 3 nova za `practiceRatingWindow`). `xcodebuild`
  (simulator `iPhone 17`): BUILD SUCCEEDED. `project.pbxproj` nije dirran — nema novih fajlova.
