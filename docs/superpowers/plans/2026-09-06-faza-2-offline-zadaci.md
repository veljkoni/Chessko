# Faza 2 — Offline zadaci: plan implementacije

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ugasiti zavisnost od tuđeg API-ja, ugraditi lokalnu bazu zadataka i uvesti rejting igrača — tako da Zadaci rade u avionskom režimu i da Faza 4 može da traži zadatke po temi.

**Architecture:** Skripta u Pythonu strimuje Lichess bazu, filtrira po kvalitetu, uzima stratifikovan uzorak i piše `puzzles.sqlite` koji se commit-uje. Swift čita bazu preko `import SQLite3` (deo sistema, bez ijedne nove zavisnosti). `PuzzleRepository` je jedini sloj koji zna za SQL; `PuzzleViewModel` prestaje da zna za mrežu.

**Tech Stack:** Swift 6.0, SwiftUI, SQLite3 (sistemski), Python 3 + zstd za generisanje baze.

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` (sekcije 5.3, 5.4 i 6, Faza 2)

## Global Constraints

- **Swift 6.0**, iOS deployment target **18.0**, Xcode 26.
- **NIKADA ne pokretati `create_xcode_project.py`** — regeneriše `project.pbxproj` i ne zna za `chesskit-engine`; pokretanje obara Stockfish.
- `project.pbxproj` se **ne regeneriše**, ali se sme hirurški dopuniti za registraciju nove datoteke (Task 2). Obrazac je `nn-37f18f62d772.nnue` — resurs ide u **Resources** fazu, ne u Sources.
- **Nijedna nova SPM zavisnost.** `import SQLite3` je proveren i kompajlira se za iOS bez dodavanja biblioteke.
- Sve boje, tipografija i razmaci idu kroz `DS` iz Faze 1. Nema golih boja.
- UI izvorni jezik je **srpski**; svaki nov string dobija `add(...)` liniju u `build_localizations.py` sa 7 prevoda.
- **`ChesskoAndroid/` se ne dira.** Android prati u Fazi 6, ali generisanu `puzzles.sqlite` deli — zato baza ne sme da sadrži ništa iOS-specifično.
- Build: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
- Testovi: `swift test` (trenutno 11; faza ih dodaje).

## Zatečeno stanje, provereno

- Lichess baza: `https://database.lichess.org/lichess_db_puzzle.csv.zst`, **304 MB** komprimovano, ~5M zadataka.
- Zaglavlje CSV-a: `PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags,DailyDate`. FEN ne sadrži zarez, pa obično cepanje po `,` radi.
- Na uzorku od 58.774 zadatka filter kvaliteta propušta **30,7%**. Puna baza daje ~1,5M kvalitetnih — više nego dovoljno za uzorak od 20.000.
- Raspodela rejtinga je nagnuta ka 1400–1800; opseg 600–799 je najtanji (312 u uzorku). **Zato uzorkovanje mora biti stratifikovano** — naivnih „prvih 20.000" dalo bi premalo lakih zadataka.
- U bazi je **72 različite teme**.
- `ChessPuzzle` (`Chessko/Models/ChessPuzzle.swift`) već ima tačno polja `puzzleId/fen/moves/rating/themes` — ista koja CSV nosi. Model se **ne menja**.
- Jedini mrežni poziv je u `PuzzleViewModel.swift` (`chess-puzzles-api.vercel.app`).

---

## Pregled zadataka

| # | Zadatak | Isporuka |
|---|---|---|
| 1 | `build_puzzle_db.py` + `puzzles.sqlite` | baza commit-ovana |
| 2 | Registracija resursa + `PuzzleRepository` + testovi | Swift čita bazu |
| 3 | `PuzzleViewModel` na bazu, mreža ugašena | radi u avionskom režimu |
| 4 | Rejting igrača (Elo) | rejting se pomera pri rešavanju |
| 5 | Neograničeno rešavanje u tabu Zadaci | dnevni zadatak ostaje istaknut |
| 6 | Provera i dokumentacija | avionski režim, vizuelno, CLAUDE.md |

---

## Task 1: Generator baze

**Files:**
- Create: `build_puzzle_db.py`
- Create: `Chessko/puzzles.sqlite` (generisan, commit-uje se)

**Interfaces:**
- Consumes: ništa iz koda
- Produces: `Chessko/puzzles.sqlite` sa šemom ispod — troše je zadaci 2–5

- [ ] **Step 1: Napisati skriptu**

Create `build_puzzle_db.py` u korenu. Ključne odluke koje mora da sprovede:

**Filter kvaliteta** (proveren na uzorku, propušta ~31%):
```python
600 <= rating <= 2200 and nb_plays >= 200 and popularity >= 90 and rating_deviation <= 80
```

**Stratifikovan uzorak.** Cilj je 20.000 zadataka. Umesto uzimanja redom:
1. Osam opsega rejtinga po 200 poena: 600-799, 800-999, …, 2000-2199.
2. Ciljno **2.500 po opsegu**. Ako opseg nema dovoljno kandidata, uzima se koliko ima i manjak se **ne** nadoknađuje iz drugih opsega — bolje manja baza nego iskrivljena.
3. Unutar opsega, birati tako da svaka tema bude zastupljena: voditi brojač po temi i preferirati zadatke čije su teme trenutno najslabije zastupljene. Prost i dovoljan pristup: sortirati kandidate po `min(broj_već_uzetih_za_svaku_njegovu_temu)` i uzimati redom.
4. Determinizam: `random.seed(20260906)` ako se bilo gde koristi slučajnost, da ponovno pokretanje da istu bazu.

**Strimovanje.** Ne raspakivati 1GB na disk:
```bash
curl -sL https://database.lichess.org/lichess_db_puzzle.csv.zst | zstd -dc | python3 build_puzzle_db.py --stdin
```
Skripta mora da podrži i `--csv <putanja>` za lokalni fajl, radi ponovnog pokretanja bez preuzimanja.

**Šema** (iz spec 5.3, sa jednom dopunom):
```sql
CREATE TABLE puzzles (
  id       TEXT PRIMARY KEY,
  fen      TEXT NOT NULL,
  moves    TEXT NOT NULL,   -- UCI, razdvojeno razmakom
  rating   INTEGER NOT NULL,
  themes   TEXT NOT NULL    -- razdvojeno razmakom
);
CREATE INDEX idx_rating ON puzzles(rating);
CREATE TABLE puzzle_themes (theme TEXT NOT NULL, puzzle_id TEXT NOT NULL);
CREATE INDEX idx_theme ON puzzle_themes(theme, puzzle_id);
```
`puzzle_themes` je razložena tabela — bez nje bi upit „daj 5 zadataka teme `fork`" morao da radi `LIKE '%fork%'` nad tekstom, što je i sporo i netačno (`fork` bi pogodio i `queensideAttack`? ne, ali `mate` bi pogodio `mateIn1`, `mateIn2`, `mateIn3` i `smotheredMate`). Razlaganje uklanja tu klasu greške.

Na kraju izvršiti `VACUUM;` da fajl bude što manji.

- [ ] **Step 2: Pokrenuti i izmeriti**

```bash
curl -sL https://database.lichess.org/lichess_db_puzzle.csv.zst | zstd -dc | python3 build_puzzle_db.py --stdin --out Chessko/puzzles.sqlite
ls -lh Chessko/puzzles.sqlite
```

Preuzimanje je 304 MB i traje nekoliko minuta — to je jednokratno, jer se rezultat commit-uje.

Expected: fajl **ispod 6 MB**. Ako pređe 8 MB, STATI i prijaviti — nešto se ne filtrira kako treba.

- [ ] **Step 3: Provera sadržaja baze**

```bash
sqlite3 Chessko/puzzles.sqlite "SELECT COUNT(*) FROM puzzles;"
sqlite3 Chessko/puzzles.sqlite "SELECT rating/200*200 AS band, COUNT(*) FROM puzzles GROUP BY band ORDER BY band;"
sqlite3 Chessko/puzzles.sqlite "SELECT COUNT(DISTINCT theme) FROM puzzle_themes;"
sqlite3 Chessko/puzzles.sqlite "SELECT theme, COUNT(*) c FROM puzzle_themes GROUP BY theme ORDER BY c LIMIT 10;"
sqlite3 Chessko/puzzles.sqlite "SELECT COUNT(*) FROM puzzles WHERE fen IS NULL OR moves IS NULL OR moves = '';"
```

Expected: ~20.000 zadataka; nijedan opseg prazan; bar 50 različitih tema; **nula** redova sa praznim `fen`/`moves`. Zapisati stvarne brojeve u izveštaj.

- [ ] **Step 4: Commit**

```bash
git add build_puzzle_db.py Chessko/puzzles.sqlite
git commit -m "feat: generator i lokalna baza zadataka (20k, Lichess CC0)

Strimuje lichess_db_puzzle.csv.zst, filtrira po kvalitetu
(NbPlays>=200, Popularity>=90, RatingDeviation<=80, rejting 600-2200)
i uzima STRATIFIKOVAN uzorak po opsegu rejtinga i temi — naivnih prvih
20k dalo bi premalo lakih zadataka i neravnomerne teme.

Tabela puzzle_themes je razlozena da upit po temi ne bude LIKE nad
tekstom, gde bi 'mate' pogadjao i mateIn1/mateIn2/smotheredMate."
```

---

## Task 2: Registracija resursa i `PuzzleRepository`

**Files:**
- Modify: `Chessko.xcodeproj/project.pbxproj` (4 reda)
- Create: `Chessko/Logic/PuzzleRepository.swift`
- Test: `Tests/ChesskoEngineTests/PuzzleRepositoryTests.swift`

**Interfaces:**
- Consumes: `Chessko/puzzles.sqlite`, model `ChessPuzzle`
- Produces: `PuzzleRepository` sa metodama koje troše zadaci 3–5:
  - `init?(databaseURL: URL)` — nil ako baza ne postoji ili se ne otvori
  - `static let bundled: PuzzleRepository?` — otvara bazu iz bundle-a
  - `func puzzle(id: String) -> ChessPuzzle?`
  - `func dailyPuzzle(for date: Date) -> ChessPuzzle?` — deterministički po datumu
  - `func puzzles(themes: [String], ratingRange: ClosedRange<Int>, excluding: Set<String>, limit: Int) -> [ChessPuzzle]`
  - `func randomPuzzle(ratingRange: ClosedRange<Int>, excluding: Set<String>) -> ChessPuzzle?`
  - `var count: Int`

- [ ] **Step 1: Registrovati bazu u `project.pbxproj`**

Isti obrazac kao `nn-37f18f62d772.nnue` (linije 49, 89, 171, 276), ali **u Resources fazi**. Koristiti ove ID-jeve:

1. `PBXBuildFile`:
```
\t\t953F5BE085B54B8E8B4B00D5 /* puzzles.sqlite in Resources */ = {isa = PBXBuildFile; fileRef = E438AB95F72D4C4CB1243B49 /* puzzles.sqlite */; };
```
2. `PBXFileReference`:
```
\t\tE438AB95F72D4C4CB1243B49 /* puzzles.sqlite */ = {isa = PBXFileReference; lastKnownFileType = file; path = puzzles.sqlite; sourceTree = "<group>"; };
```
3. `children` grupe u kojoj su `.nnue`:
```
\t\t\t\tE438AB95F72D4C4CB1243B49 /* puzzles.sqlite */,
```
4. `files` liste **`PBXResourcesBuildPhase`** (ne Sources):
```
\t\t\t\t953F5BE085B54B8E8B4B00D5 /* puzzles.sqlite in Resources */,
```

Provera ravnoteže zagrada:
```bash
python3 -c "s=open('Chessko.xcodeproj/project.pbxproj').read(); print('balans:', s.count('{')-s.count('}'))"
```
Expected: `balans: 0`

- [ ] **Step 2: Napisati `PuzzleRepository`**

`import SQLite3`. Ključne stvari koje se lako promaše:

- **`SQLITE_TRANSIENT`.** Swift nema tu konstantu; bez nje `sqlite3_bind_text` zadrži pokazivač na string koji je već oslobođen. Definisati:
  ```swift
  private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
  ```
- Otvarati bazu **samo za čitanje**: `sqlite3_open_v2(path, &db, SQLITE_OPEN_READONLY, nil)`.
- Svaki `sqlite3_prepare_v2` prati `defer { sqlite3_finalize(stmt) }`.
- Klasa mora biti sigurna za `@MainActor` upotrebu iz `PuzzleViewModel`-a; najprostije je označiti je `final class` i pristupati joj sa glavne niti, pošto su upiti mikrosekundni nad 20k redova.

`dailyPuzzle(for:)` mora biti deterministički i stabilan: uzeti redni broj dana (`Calendar.current.ordinality(of: .day, in: .era, for: date)`), pa `offset = dayIndex % count`, pa `SELECT ... FROM puzzles ORDER BY id LIMIT 1 OFFSET ?`. **Sortiranje po `id` je obavezno** — bez `ORDER BY` SQLite ne garantuje redosled, pa bi „zadatak dana" umeo da se promeni između pokretanja.

Upit po temama:
```sql
SELECT p.id, p.fen, p.moves, p.rating, p.themes
FROM puzzles p
JOIN puzzle_themes t ON t.puzzle_id = p.id
WHERE t.theme IN (…) AND p.rating BETWEEN ? AND ?
  AND p.id NOT IN (…)
GROUP BY p.id
ORDER BY RANDOM()
LIMIT ?;
```

- [ ] **Step 3: Dodati fajl u SwiftPM paket radi testova**

`Package.swift` već kompajlira izvore po putanji. Dodati `"Logic/PuzzleRepository.swift"` u `sources:` liste. `SQLite3` je dostupan i na macOS-u, pa testovi rade bez simulatora.

- [ ] **Step 4: Napisati testove**

Create `Tests/ChesskoEngineTests/PuzzleRepositoryTests.swift`. Baza se u testu otvara po relativnoj putanji iz korena repozitorijuma (`Chessko/puzzles.sqlite`).

Testovi koji moraju da postoje:
1. Baza se otvara i `count` je u očekivanom opsegu (bar 15.000).
2. `dailyPuzzle(for:)` za **isti datum vraća isti zadatak** pri dva uzastopna poziva, i za dva različita datuma vraća različite.
3. `puzzles(themes: ["fork"], …)` vraća samo zadatke koji zaista imaju `fork` u `themes` — provera da razlaganje tema radi i da `mate` ne pogađa `mateIn2`.
4. `excluding:` se poštuje — zadatak koji je prosleđen kao rešen se ne vraća.
5. Svaki vraćeni zadatak ima neprazan `fen` i bar jedan potez, i `GameState.fromFEN(puzzle.fen)` uspeva. **Ovo je najvredniji test u zadatku** — vezuje bazu za motor i hvata svaki pokvaren red.

- [ ] **Step 5: Pokrenuti**

Run: `swift test`
Expected: postojećih 11 + novi testovi prolaze.

Run build. Expected: `** BUILD SUCCEEDED **`.

Ako build prođe a baza se u aplikaciji ne pronađe, resurs nije stigao u Resources fazu — proveriti tačku 4 iz Step 1.

- [ ] **Step 6: Commit**

```bash
git add Chessko.xcodeproj/project.pbxproj Chessko/Logic/PuzzleRepository.swift Package.swift Tests/ChesskoEngineTests/PuzzleRepositoryTests.swift
git commit -m "feat: PuzzleRepository cita lokalnu bazu preko SQLite3

Bez nove zavisnosti — SQLite3 je deo sistema. Baza se otvara read-only.
dailyPuzzle sortira po id pre OFFSET-a: bez ORDER BY SQLite ne garantuje
redosled pa bi zadatak dana umeo da se promeni izmedju pokretanja."
```

---

## Task 3: `PuzzleViewModel` na bazu, mreža ugašena

**Files:**
- Modify: `Chessko/ViewModels/PuzzleViewModel.swift`
- Modify: `Chessko/Models/ChessPuzzle.swift` (samo komentar u zaglavlju)

- [ ] **Step 1: Zameniti mrežno učitavanje**

`fetchPuzzle()` trenutno gradi URL ka `chess-puzzles-api.vercel.app` i dekodira JSON. Zameniti pozivom `PuzzleRepository.bundled?.dailyPuzzle(for: selectedDate)`.

Pošto više nema mrežnog kašnjenja, `phase = .loading` prestaje da ima smisla kao dugotrajno stanje — zadatak se dobija sinhrono. Zadržati `.loading` samo kao početnu vrednost pre prvog učitavanja.

**Stanje greške se ne uklanja.** Ako `PuzzleRepository.bundled` vrati `nil` (baza nedostaje iz bundle-a), korisnik mora da vidi jasnu poruku, ne prazan ekran. Dodati string u `build_localizations.py`:
```python
add("Baza zadataka nije dostupna", "Puzzle database unavailable", "Base de problèmes indisponible", "Aufgabendatenbank nicht verfügbar", "Database dei problemi non disponibile", "База задач недоступна", "题目数据库不可用", "पहेली डेटाबेस उपलब्ध नहीं")
```
pa `python3 build_localizations.py`.

- [ ] **Step 2: Ukloniti svaki trag mreže**

```bash
grep -rn "URLSession\|vercel\|https://" Chessko/ViewModels/ Chessko/Models/ChessPuzzle.swift
```
Expected: nema pogodaka. Ispraviti i komentar u zaglavlju `ChessPuzzle.swift` koji još pominje API.

- [ ] **Step 3: Build i testovi**

Oba moraju proći.

- [ ] **Step 4: Commit**

```bash
git add -u && git commit -m "feat: Zadaci citaju lokalnu bazu, mrezna zavisnost ugasena

chess-puzzles-api.vercel.app se vise ne poziva nigde. Stanje greske
zadrzano za slucaj da baza nedostaje iz bundle-a."
```

---

## Task 4: Rejting igrača

**Files:**
- Modify: `Chessko/Logic/StatsManager.swift`
- Test: `Tests/ChesskoEngineTests/RatingTests.swift`

**Interfaces:**
- Produces: `StatsManager.puzzleRating: Int` i `func applyPuzzleResult(puzzleRating: Int, solved: Bool)`

- [ ] **Step 1: Dodati rejting**

Po spec 5.4: početna vrednost **800**, K = 32.

Račun se izdvaja u **čistu statičku funkciju** da bi bio testabilan bez
`UserDefaults` i bez `@MainActor` — instanca je samo zove.

```swift
/// Elo-stil rejting igraca za zadatke. Pocinje na 800 (spec 5.4).
var puzzleRating: Int {
    didSet { UserDefaults.standard.set(puzzleRating, forKey: "stats_puzzleRating") }
}

/// E = 1 / (1 + 10^((Rp - R)/400));  R' = R + K*(S - E),  K = 32
/// Cista funkcija — nema stanja, testira se direktno.
static func newRating(current r: Int, puzzleRating rp: Int, solved: Bool) -> Int {
    let expected = 1.0 / (1.0 + pow(10.0, (Double(rp) - Double(r)) / 400.0))
    let score = solved ? 1.0 : 0.0
    return Int((Double(r) + 32.0 * (score - expected)).rounded())
}

func applyPuzzleResult(puzzleRating rp: Int, solved: Bool) {
    puzzleRating = StatsManager.newRating(current: puzzleRating,
                                          puzzleRating: rp,
                                          solved: solved)
}
```

U `init()` učitati sa podrazumevanom vrednošću 800 — **`UserDefaults.integer(forKey:)` vraća 0 za nepostojeći ključ**, pa se mora razlikovati „nema vrednosti" od nule:
```swift
if UserDefaults.standard.object(forKey: "stats_puzzleRating") == nil {
    self.puzzleRating = 800
} else {
    self.puzzleRating = UserDefaults.standard.integer(forKey: "stats_puzzleRating")
}
```
Isti obrazac primeniti i u `resetStats()` — rejting se vraća na 800, ne na 0.

- [ ] **Step 2: Povezati sa rešavanjem**

U `PuzzleViewModel`, tamo gde se već zovu `StatsManager.shared.recordPuzzleSolved()` i `recordPuzzleFailed()`, dodati i `applyPuzzleResult(puzzleRating: currentPuzzle.rating, solved:)`.

- [ ] **Step 3: Testovi**

Create `Tests/ChesskoEngineTests/RatingTests.swift`, protiv `StatsManager.newRating(current:puzzleRating:solved:)`.

**Ove vrednosti su izračunate i proverene — koristiti ih doslovno:**

| slučaj | trenutni | zadatak | rešen | očekivano |
|---|---|---|---|---|
| jednak rejting, rešen | 800 | 800 | da | **816** |
| jednak rejting, nerešen | 800 | 800 | ne | **784** |
| mnogo teži, rešen | 800 | 1600 | da | **832** |
| mnogo lakši, nerešen | 800 | 400 | ne | **771** |

Peti test proverava **svojstvo, ne fiksnu vrednost**: prirast po rešenom
zadatku strogo opada. Kako rejting igrača raste iznad 800 (a rejting zadatka
ostaje 800), `E` se primiče 1 pa je `K*(1-E)` sve manji.

> **Ispravka plana (2026-09-06).** Ranija verzija ovog koraka je tvrdila da se
> rejting „asimptotski primiče 800 odozgo" i ostaje ispod 1000. To je netačno i
> uhvaćeno je pri izvršavanju Task-a 4. Rejting *raste* iznad 800: posle 100
> rešenih je 1288, posle 1000 je 1520. Niz se zaustavlja tek oko **1520**, gde
> `round(32*(1-E))` prvi put padne na 0 — dakle ograničava ga celobrojno
> zaokruživanje, ne asimptota ka 800. Test mora da tvrdi opadanje prirasta;
> svaka granica na samoj vrednosti je samo gruba zaštita od linearnog rasta.

`StatsManager` je `@MainActor` i piše u `UserDefaults`; `newRating` nije ni
jedno ni drugo, pa se testira direktno bez ikakve pripreme.

- [ ] **Step 4: Build, testovi, commit**

---

## Task 5: Neograničeno rešavanje

**Files:**
- Modify: `Chessko/ViewModels/PuzzleViewModel.swift`
- Modify: `Chessko/Views/PuzzleView.swift`

- [ ] **Step 1: Sledeći zadatak**

Dodati `func nextPuzzle()` koji uzima `randomPuzzle(ratingRange:excluding:)` u prozoru `puzzleRating - 200 ... puzzleRating + 100` (spec 5.4) i pamti rešene u `UserDefaults` (ključ `solvedPuzzleIds`, `Set<String>` serijalizovan kao niz).

Ako prozor ne vrati ništa (korisnik je rešio sve u opsegu), proširiti prozor umesto da ekran ostane prazan — i to zabeležiti u izveštaju.

- [ ] **Step 2: Dugme u UI**

Posle rešenog zadatka, pored postojećih dugmadi dodati **„Sledeći zadatak"** kao primarnu akciju. Nov string:
```python
add("Sledeći zadatak", "Next puzzle", "Problème suivant", "Nächste Aufgabe", "Prossimo problema", "Следующая задача", "下一题", "अगली पहेली")
```

Dnevni zadatak ostaje kako jeste — izbor datuma i strelice se ne diraju.

Sve boje i tipografija kroz `DS` i `Font.ds*`.

- [ ] **Step 3: Build, testovi, commit**

---

## Task 6: Provera i dokumentacija

- [ ] **Step 1: Avionski režim**

Ovo je uslov završetka faze iz specifikacije.

```bash
grep -rn "URLSession\|vercel\|https://\|http://" Chessko/ --include=*.swift
```
Expected: nema pogodaka izvan komentara o licencama.

Zatim u simulatoru: pokrenuti aplikaciju, otvoriti Zadatke, rešiti zadatak i tražiti sledeći — sve mora da radi. Simulator nema avionski režim, ali odsustvo mrežnog koda je jače od te provere; grep je merodavan.

- [ ] **Step 2: Vizuelna provera**

Tab Zadaci u svetloj i tamnoj temi: dnevni zadatak, rešen zadatak, „Sledeći zadatak", stanje greške ako baza nedostaje.

**Napomena o simulatoru** (iz Faza 0 i 1): System Events ne radi; koristiti `cliclick`. Plutajući tab bar traži spor pritisak `cliclick m:X,Y w:250 dd:X,Y w:200 du:X,Y`. Dugmad u nav baru **ne reaguju** na sintetičke klikove. Prvi klik posle instalacije se troši na fokusiranje prozora. Mapiranje za prozor (1120,224) veličine (456,972): `ox=1133, oy=262, k=1.0687`, ekran uređaja 402×874 tačaka.

- [ ] **Step 3: Ažurirati `CLAUDE.md`**

Nova sekcija o bazi zadataka: odakle je, kako se regeneriše, zašto je `puzzle_themes` razložena, i da je licenca **CC0**. U „Poznata ograničenja" ukloniti stavku o zavisnosti od Vercel API-ja. Changelog unos.

- [ ] **Step 4: Commit**

---

## Završna provera faze

- [ ] `swift test` prolazi (11 postojećih + novi)
- [ ] `xcodebuild … build` → `** BUILD SUCCEEDED **`
- [ ] `git status --short` prazan
- [ ] Grep za mrežom nad `Chessko/` bez pogodaka
- [ ] `Chessko/puzzles.sqlite` commit-ovan i ispod praga iz Task-a 1 (8 MB)

> **Ispravka plana (2026-09-07).** Ova stavka je ranije tražila „ispod 6 MB", što je
> u sukobu sa samim Task-om 1, gde je prag za prekid rada bio 8 MB. Stvarna baza je
> **7,0 MB** — prošla je Task 1 i njegov pregled. Merodavan je prag iz Task-a 1;
> ovde je 6 MB bilo omaška.
- [ ] Tab Zadaci pregledan u obe teme

