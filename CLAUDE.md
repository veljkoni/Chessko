# CLAUDE.md — Chessko

> Ovaj fajl uvek pročitaj na početku rada i ažuriraj na kraju svakog prompta
> (sekcija "Changelog" + relevantne sekcije ako se arhitektura promeni).

## Šta je aplikacija

**Chessko** — iOS aplikacija: šah protiv računara (AI). Igrač je uvek **beli**,
računar je **crni**. UI je na srpskom. Jedan ekran, jedan mod igre.

- Platforma: **iOS 18.0+**, iPhone + iPad (`TARGETED_DEVICE_FAMILY = 1,2`)
- Jezik: **Swift 6.0**, **SwiftUI**
- Bundle ID: `com.veljkoni.chessko`
- Xcode projekat: `Chessko.xcodeproj` (target `Chessko`)
- Bez eksternih dependency-ja, bez test target-a, nije git repo.

## Build / Run

```bash
# Build (simulator)
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```
Najlakše: otvoriti `Chessko.xcodeproj` u Xcode i pokrenuti (⌘R).
Pomoćne skripte u rootu: `create_xcode_project.py` (generiše pbxproj),
`extract_pieces.py` (seče `design-reference.svg` u SVG figure za asset katalog).

## Arhitektura (MVVM)

```
Chessko/
├── ChesskoApp.swift          @main, WindowGroup → ContentView
├── ContentView.swift         → GameView()
├── Models/                   čiste vrednosne strukture (struct/enum, Sendable)
│   ├── Position.swift        row 0 = rank 8 (crni), col 0 = file a
│   ├── ChessPiece.swift      PieceType, PieceColor, materialValue, Unicode symbol
│   ├── ChessMove.swift       from/to/flag; == poredi SAMO from+to (ne flag!)
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
- **AI**: `ChessAI` — negamax sa alfa-beta. Dubina po težini: easy 2 / medium 3 /
  hard 4. Trenutno hardkodirano `.medium` u `GameViewModel`. Evaluacija =
  materijal + piece-square bonus. Potezi se `shuffled()` radi varijacije.
- **Concurrency**: AI se računa na `Task.detached(priority: .userInitiated)`,
  rezultat se primenjuje nazad na `@MainActor`. `isThinking` gejtuje UI.
- **Promocija**: UI uvek automatski promoviše u **damu** (`handleMove` u VM);
  postoje `promotionMove`/`showPromotion` polja ali UI za izbor još nije urađen.
- **Boje table** (`SquareView`): `squareLight #e9ebde`, `squareDark #8592af`,
  `boardBackground #17234f`. Highlight: žuto za poslednji potez, sivo za selekciju.
- **Rotacija table**: `viewModel.isFlipped` → `BoardView` iterira redove/kolone u obrnutom
  redosledu; `SquareView` dobija `isBottomEdge`/`isLeftEdge` za koordinatne labele.
  `AnimatingPieceView` i `flyingCapture` overlay koriste display koordinate.

## Poznata ograničenja / TODO kandidati

- Izbor figure pri promociji nije implementiran (uvek dama).
- Izbor figure pri promociji nije implementiran (uvek dama).
- Nema undo poteza, nema čuvanja partije, nema detekcije ponavljanja/50 poteza
  (remi samo na pat/mat).
- Nema test target-a.
- Igrač ne može da igra crnim, tabla se ne rotira.
- Stockfish radi samo sa `nn-37f18f62d772.nnue` (mali); `nn-1111cefa1111.nnue`
  (veliki, ~79MB) opcionalan za jaču igru — skinuti sa stockfishchess.org.

## Next Steps / Roadmap (ideje za unapređenje)

Prioritet poređan po vrednosti; ništa od ovoga još nije započeto.

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

### 4. Kvalitet koda
- [ ] Test target + perft testovi za `MoveGenerator`.
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
