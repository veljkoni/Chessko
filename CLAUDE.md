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

Pokriveno (**58 testova**): 7 perft testova za svih 6 standardnih pozicija (uključujući
početnu do dubine 5, 4.865.609 čvorova, ~85s), 4 testa prava rokade (uzimanje
topa na sva 4 ugla, i partija bez topa koja i dalje nosi zastarelo pravo),
14 testova `PuzzleRepository`-ja (uključujući dva koja prolaze **celu** bazu —
vidi ispod), 5 testova Elo rejtinga, 4 testa sadržaja lekcija (dekodiranje
svih 12 tipova blokova, round-trip, glasan pad na nepoznat tip, i prolaz kroz
sve lekcijske JSON-e), 7 testova kurikuluma (uključujući onaj koji tvrdi da
kurikulum ne laže — svaka lekcija koju pominje postoji, svaka tema ima dovoljno
zadataka u opsegu) i 17 testova napretka (`ProgressStore`, dnevni cilj, streak). `Chessko/TestSupport/LocShim.swift`
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
  `boardBackground #17234f`. Highlight: žuto za poslednji potez, sivo za selekciju.
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
  rupom koju niko ne primeti. Isto važi za nepoznat naziv figure i za `interactive: true`
  (interaktivna tabla još ne postoji): oba daju vidljivu poruku u tekstu lekcije.
- **`pieceValueTable` redovi treba da zadaju `valueLabel`** („1 bod" / „1 point"). Bez njega
  renderer sklapa labelu iz `value` po srpskoj množini i traži ključ u katalogu — a ključevi
  postoje samo za 1/3/5/9/∞, pa bi vrednost „2" na svim jezicima dala srpsko „2 boda".

## Poznata ograničenja / TODO kandidati

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
- **Isti bug sa rokadom postoji na Androidu** —
  `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/models/GameState.kt`
  (grane `CastleKingside`/`CastleQueenside` i blok koji oduzima prava rokade
  gledaju samo `move.from`, tačno ono što je iOS imao pre Faze 0). Popravka je
  planirana za Fazu 6.
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
  uspela." jer izlaz starog motora završi u pipe-u novog, i `GameAnalysis.turningPoint` ume da
  izdvoji potez koji `MoveClass.classify` naziva „najboljim" (kartica tada nosi boju akcenta, ne
  upozorenja). Pun izveštaj:
  `.superpowers/sdd/2026-09-11-faza-5-analiza-partije/task-5-report.md`.
