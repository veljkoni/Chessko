# Faza 4a — Put (mehanizam): plan implementacije

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Uvesti Put kao okosnicu aplikacije — niz koraka koje igrač prolazi redom, sa napretkom koji preživi gašenje aplikacije i streak-om koji se ispravno prekida.

**Architecture:** `curriculum.json` opisuje poglavlja i korake (van koda, kao i lekcije). `ProgressStore` čuva napredak kao JSON u Application Support i vlasnik je stanja koraka, dnevnog cilja i streak-a; `StatsManager` zadržava postojeći API i postaje tanka fasada nad njim, pa se 26 postojećih poziva ne dira. Ekran `PathView` zamenjuje `LearnView` u tabu, a pokretači koraka voze postojeće `PuzzleViewModel`/`GameViewModel`.

**Tech Stack:** Swift 6.0, SwiftUI, `Codable`/`JSONDecoder`, `FileManager` (Application Support).

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` (sekcije 5.1, 5.4 i 6, Faza 4)

## Global Constraints

- **Swift 6.0**, iOS deployment target **18.0**, Xcode 26.
- **NIKADA ne pokretati `create_xcode_project.py`** — regeneriše `project.pbxproj` i ne zna za `chesskit-engine`; pokretanje obara Stockfish. Ručne dopune samo.
- **Nijedna nova SPM zavisnost.**
- Sve boje, tipografija i razmaci idu kroz `DS` / `Font.ds*`. `DS.onScrim` samo na `DS.scrim`; na `DS.accent` ide `DS.onAccent`.
- UI stringovi idu kroz `Loc(_:)`/`LocF(_:_:)` i dobijaju `add(...)` liniju u `build_localizations.py` sa 7 prevoda. **Sadržaj** (naslovi poglavlja) ide u `curriculum.json`, ne u katalog.
- **`ChesskoAndroid/` se ne dira.**
- Build: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
- Testovi: `swift test` (trenutno **31**).
- **Kad se doda nov Swift fajl, dokazati da se kompajlira u aplikaciju** — ubaciti sintaksnu grešku, videti da build pada, pa vratiti fajl **kopijom** (`cp`), ne `git checkout`-om (ne radi nad nepraćenim fajlom).
- `Chessko/Content/` je **folder-referenca**: nov JSON ne traži izmenu `project.pbxproj`.

## Zatečeno stanje, provereno

- `StatsManager` (146 linija, `@MainActor`, u `ChesskoEngine` target-u) drži 10 skalara u `UserDefaults` sa prefiksom `stats_`, plus `applyPuzzleResult` i `resetStats`. **26 poziva u produkcionom kodu** (`GameViewModel` 12, `PuzzleViewModel` 12, `SettingsSheet` 2) i 6 u testovima.
- `PuzzleViewModel` (521 linija) već ima `PuzzleMode { daily, practice }`, red rešenih id-jeva, `nextPuzzle()` sa progresivnim širenjem prozora, i ceo tok rešavanja (`tap`/`attempt`/`showSolution`/`phase`).
- `GameViewModel` (912 linija): `newGame(gameMode:playerColor:)`, `difficulty: GameDifficulty`, `statusMessage`. `GameDifficulty` = `beginner|easy|medium|hard|stockfish`.
- `GameStatus` = `playing | check(PieceColor) | checkmate(PieceColor) | draw(DrawReason) | resigned(PieceColor)`.
- `LessonRepository` otkriva lekcije iz bundle-a; `lessonOrder` je samo ključ za sortiranje. Postoje 4 lekcije: `board-and-pieces`, `openings`, `middlegame`, `endgame`.
- Baza zadataka ima dovoljno materijala za kurikulum (provereno upitom, opseg 600–1400): `mateIn1` 2494, `opening` 531, `fork` 254, `endgame` 6010.
- Tabovi su `Igra` / `Zadaci` / `Učenje`; `ContentView` drži sve view-modele kao `@State` da se ne inicijalizuju tokom animacije prelaska.

## Odluke ove faze

1. **`ProgressStore` je vlasnik podataka; `StatsManager` postaje fasada.** Spec traži da postojeća statistika pređe u JSON. Umesto da se dira 26 poziva u `GameViewModel`/`PuzzleViewModel` — među njima i logika rejtinga proverena u Fazi 2 — `StatsManager` zadržava potpis svake metode i prosleđuje `ProgressStore`-u. Jedan izvor istine, nula regresionog rizika u tokovima igre i zadataka. Fasada je ~40 linija i dokumentovana je kao uklonjiva.
2. **`UserDefaults` ključevi se NE brišu.** Spec to izričito traži: migracija čita i ostavlja netaknuto, da povratak na stariju verziju aplikacije i dalje radi.
3. **Streak i otključavanje su čiste funkcije.** Tu žive greške (prelazak dana, preskočen dan, praznik u nizu), a ne u skladištu. Testiraju se bez fajlova i bez sata — dan se prosleđuje kao string.
4. **Streak se ne prekida u toku dana.** Ako cilj danas još nije ispunjen, niz se broji od juče. Prekida ga tek **propušten** dan.
5. **`test` korak nije poseban pokretač.** To je `practice` sa zahtevom nula grešaka. Isti ekran, jedna zastavica.
6. **Nove lekcije NISU deo ove faze.** Kurikulum se gradi od postojeće 4 lekcije: 4 poglavlja × 3 koraka = 12 koraka. Sadržaj dolazi u Fazi 4b, bez ijedne izmene mehanizma.

## Pregled zadataka

| # | Zadatak | Isporuka |
|---|---|---|
| 1 | Model kurikuluma + `curriculum.json` + testovi | Put postoji kao podatak |
| 2 | `ProgressStore`: perzistencija, migracija, streak | napredak preživi gašenje |
| 3 | Ekran Puta + tab `Učenje` → `Put` | Put se vidi i lekcijski koraci rade |
| 4 | Pokretač `practice` i `test` koraka | vežbe zaključavaju korak |
| 5 | Pokretač `game` koraka + upis završetka | partija zaključava korak |
| 6 | Provera i dokumentacija | faza zatvorena |

---

## Task 1: Model kurikuluma i `curriculum.json`

**Files:**
- Create: `Chessko/Models/Curriculum.swift`
- Create: `Chessko/Content/curriculum.json`
- Modify: `Package.swift`, `Chessko.xcodeproj/project.pbxproj`
- Test: `Tests/ChesskoEngineTests/CurriculumTests.swift`

**Interfaces:**
- Consumes: ništa
- Produces: `Curriculum`, `Chapter`, `CurriculumStep`, `StepKind`, `Curriculum.allStepIds`, `Curriculum.step(id:)` — troše ih zadaci 2–5. Učitavanje iz bundle-a NIJE ovde: `CurriculumRepository` stiže u Task-u 3, jer mu treba `Bundle.main` koji testni paket nema.

- [ ] **Step 1: Napisati test koji pada**

Create `Tests/ChesskoEngineTests/CurriculumTests.swift`:

```swift
import Testing
import Foundation
@testable import ChesskoEngine

@Test func decodesAllStepKinds() throws {
    let json = """
    {
      "version": 1,
      "chapters": [
        {
          "id": "basics",
          "title": { "sr": "Osnove", "en": "Basics" },
          "steps": [
            { "id": "b-lesson", "type": "lesson", "lessonId": "board-and-pieces" },
            { "id": "b-practice", "type": "practice", "themes": ["mateIn1"],
              "count": 5, "ratingRange": [600, 1000] },
            { "id": "b-game", "type": "game", "difficulty": "beginner", "startFEN": null },
            { "id": "b-test", "type": "test", "themes": ["fork"],
              "count": 3, "ratingRange": [600, 1200] }
          ]
        }
      ]
    }
    """
    let c = try JSONDecoder().decode(Curriculum.self, from: Data(json.utf8))
    #expect(c.version == 1)
    #expect(c.chapters.count == 1)
    #expect(c.chapters[0].title["sr"] == "Osnove")

    let steps = c.chapters[0].steps
    #expect(steps.count == 4)
    guard case .lesson(let lessonId) = steps[0].kind else {
        Issue.record("korak 0 nije lesson"); return
    }
    #expect(lessonId == "board-and-pieces")

    guard case .practice(let themes, let count, let range) = steps[1].kind else {
        Issue.record("korak 1 nije practice"); return
    }
    #expect(themes == ["mateIn1"])
    #expect(count == 5)
    #expect(range == 600...1000)

    guard case .game(let difficulty, let fen) = steps[2].kind else {
        Issue.record("korak 2 nije game"); return
    }
    #expect(difficulty == "beginner")
    #expect(fen == nil)

    guard case .test = steps[3].kind else {
        Issue.record("korak 3 nije test"); return
    }
}

@Test func unknownStepTypeThrows() {
    let json = """
    { "version": 1, "chapters": [ { "id": "c", "title": {"sr":"C","en":"C"},
      "steps": [ { "id": "x", "type": "izmisljeno" } ] } ] }
    """
    #expect(throws: (any Error).self) {
        try JSONDecoder().decode(Curriculum.self, from: Data(json.utf8))
    }
}

@Test func invertedRatingRangeThrowsInsteadOfTrapping() {
    // `ClosedRange` sa donjom granicom vecom od gornje RUSI aplikaciju u
    // run-time-u. Dekoder mora da odbije takav JSON, ne da propusti pa da
    // pukne kasnije. (Ista klasa greske ispravljena u Fazi 2, Task 5.)
    let json = """
    { "version": 1, "chapters": [ { "id": "c", "title": {"sr":"C","en":"C"},
      "steps": [ { "id": "x", "type": "practice", "themes": ["fork"],
                   "count": 3, "ratingRange": [1200, 600] } ] } ] }
    """
    #expect(throws: (any Error).self) {
        try JSONDecoder().decode(Curriculum.self, from: Data(json.utf8))
    }
}

@Test func realCurriculumIsConsistentWithLessonsAndPuzzleDatabase() throws {
    let url = URL(fileURLWithPath: "Chessko/Content/curriculum.json")
    let curriculum = try JSONDecoder().decode(Curriculum.self, from: Data(contentsOf: url))

    #expect(!curriculum.chapters.isEmpty)

    // Svaki id koraka je jedinstven — `ProgressStore` ih koristi kao kljuc.
    let ids = curriculum.chapters.flatMap { $0.steps.map(\.id) }
    #expect(Set(ids).count == ids.count, "duplirani id-jevi koraka: \(ids)")

    // Svako poglavlje ima naslov na sr i en.
    for chapter in curriculum.chapters {
        #expect(chapter.title["sr"]?.isEmpty == false, "\(chapter.id) nema sr naslov")
        #expect(chapter.title["en"]?.isEmpty == false, "\(chapter.id) nema en naslov")
    }

    // Svaka lekcija koju kurikulum pominje mora da postoji kao fajl.
    let lessonsDir = URL(fileURLWithPath: "Chessko/Content/lessons")
    let lessonFiles = Set(try FileManager.default
        .contentsOfDirectory(at: lessonsDir, includingPropertiesForKeys: nil)
        .map { $0.lastPathComponent })
    for chapter in curriculum.chapters {
        for step in chapter.steps {
            if case .lesson(let id) = step.kind {
                #expect(lessonFiles.contains("\(id).sr.json"),
                        "korak \(step.id) trazi lekciju '\(id)' koje nema")
            }
        }
    }

    // Svaka tema mora da postoji u bazi zadataka i da ima materijala u
    // trazenom opsegu. Bez ove provere tipfeler u temi daje korak koji se
    // NIKAD ne moze zavrsiti, a nista ne prijavi gresku.
    let repo = PuzzleRepository(databaseURL: URL(fileURLWithPath: "Chessko/puzzles.sqlite"))
    #expect(repo != nil)
    for chapter in curriculum.chapters {
        for step in chapter.steps {
            let (themes, count, range): ([String], Int, ClosedRange<Int>)
            switch step.kind {
            case .practice(let t, let c, let r), .test(let t, let c, let r):
                (themes, count, range) = (t, c, r)
            default:
                continue
            }
            let found = repo?.puzzles(themes: themes, ratingRange: range,
                                      excluding: [], limit: count * 4) ?? []
            #expect(found.count >= count,
                    "korak \(step.id): teme \(themes) u opsegu \(range) daju \(found.count) zadataka, treba bar \(count)")
        }
    }
}
```

Poslednji test je glavna zaštita ove faze: kurikulum pokazuje na lekcije i teme, a tipfeler u bilo kom od njih daje korak koji se ne može završiti — i to bez ijedne poruke o grešci.

`PuzzleRepository` je `@MainActor`, pa testovi koji ga dodiruju moraju biti `@MainActor`. Dodati `@MainActor` na `realCurriculumIsConsistentWithLessonsAndPuzzleDatabase`.

- [ ] **Step 2: Pokrenuti test i videti da pada**

Run: `swift test --filter Curriculum`
Expected: FAIL — `cannot find 'Curriculum' in scope`.

- [ ] **Step 3: Napisati model**

Create `Chessko/Models/Curriculum.swift`:

```swift
import Foundation

// MARK: - Kurikulum (Put)
//
// Okosnica v2: niz koraka koje igrac prolazi redom. Zivi u
// `Chessko/Content/curriculum.json`, van koda, kao i lekcije.
//
// Kao i kod `LessonBlock`-a, nepoznat `type` koraka BACA gresku umesto da se
// tiho preskoci — preskocen korak bi napravio rupu u putu koju niko ne vidi.

struct Curriculum: Codable, Equatable {
    let version: Int
    let chapters: [Chapter]

    /// Redosled svih koraka kroz sva poglavlja. `ProgressStore` ga koristi za
    /// otkljucavanje, a ekran Puta za redni broj koraka.
    var allStepIds: [String] { chapters.flatMap { $0.steps.map(\.id) } }

    func step(id: String) -> CurriculumStep? {
        chapters.lazy.flatMap(\.steps).first { $0.id == id }
    }
}

struct Chapter: Codable, Equatable {
    let id: String
    /// Naslov po jeziku. Kurikulum je mali, pa naslovi stoje ovde umesto u
    /// odvojenim fajlovima po jeziku kao kod lekcija.
    let title: [String: String]
    let steps: [CurriculumStep]
}

enum StepKind: Equatable {
    /// Teorija; zavrsava se kad korisnik dodje do kraja i potvrdi.
    case lesson(lessonId: String)
    /// N zadataka filtriranih po temi; zavrsava se kad su svi reseni.
    case practice(themes: [String], count: Int, ratingRange: ClosedRange<Int>)
    /// Partija protiv racunara; zavrsava se kad partija dodje do kraja.
    case game(difficulty: String, startFEN: String?)
    /// Kao `practice`, ali se zavrsava SAMO bez ijedne greske — zakljucava poglavlje.
    case test(themes: [String], count: Int, ratingRange: ClosedRange<Int>)
}

struct CurriculumStep: Codable, Equatable {
    let id: String
    let kind: StepKind

    private enum CodingKeys: String, CodingKey {
        case id, type, lessonId, themes, count, ratingRange, difficulty, startFEN
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        let type = try c.decode(String.self, forKey: .type)

        /// `ClosedRange` sa donjom granicom vecom od gornje RUSI proces pri
        /// kreiranju. Zato se opseg proverava ovde, gde greska jos moze da se
        /// prijavi kao neispravan JSON.
        func range(_ raw: [Int]) throws -> ClosedRange<Int> {
            guard raw.count == 2, raw[0] <= raw[1] else {
                throw DecodingError.dataCorruptedError(
                    forKey: .ratingRange, in: c,
                    debugDescription: "ratingRange mora biti [donja, gornja] sa donja <= gornja, dobijeno \(raw)")
            }
            return raw[0]...raw[1]
        }

        switch type {
        case "lesson":
            self.kind = .lesson(lessonId: try c.decode(String.self, forKey: .lessonId))
        case "practice":
            self.kind = .practice(themes: try c.decode([String].self, forKey: .themes),
                                  count: try c.decode(Int.self, forKey: .count),
                                  ratingRange: try range(c.decode([Int].self, forKey: .ratingRange)))
        case "test":
            self.kind = .test(themes: try c.decode([String].self, forKey: .themes),
                              count: try c.decode(Int.self, forKey: .count),
                              ratingRange: try range(c.decode([Int].self, forKey: .ratingRange)))
        case "game":
            self.kind = .game(difficulty: try c.decode(String.self, forKey: .difficulty),
                              startFEN: try c.decodeIfPresent(String.self, forKey: .startFEN))
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type, in: c,
                debugDescription: "Nepoznat tip koraka '\(type)'. Dodaj ga u StepKind ili ispravi JSON.")
        }
    }

    func encode(to encoder: any Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        switch kind {
        case .lesson(let lessonId):
            try c.encode("lesson", forKey: .type)
            try c.encode(lessonId, forKey: .lessonId)
        case .practice(let themes, let count, let r):
            try c.encode("practice", forKey: .type)
            try c.encode(themes, forKey: .themes); try c.encode(count, forKey: .count)
            try c.encode([r.lowerBound, r.upperBound], forKey: .ratingRange)
        case .test(let themes, let count, let r):
            try c.encode("test", forKey: .type)
            try c.encode(themes, forKey: .themes); try c.encode(count, forKey: .count)
            try c.encode([r.lowerBound, r.upperBound], forKey: .ratingRange)
        case .game(let difficulty, let startFEN):
            try c.encode("game", forKey: .type)
            try c.encode(difficulty, forKey: .difficulty)
            try c.encodeIfPresent(startFEN, forKey: .startFEN)
        }
    }
}
```

- [ ] **Step 4: Napisati `curriculum.json`**

Create `Chessko/Content/curriculum.json`. Četiri poglavlja od postojeće 4 lekcije, 12 koraka. Opsezi rejtinga su izabrani tako da svaka tema ima dovoljno materijala (provereno upitom — vidi „Zatečeno stanje"):

```json
{
  "version": 1,
  "chapters": [
    {
      "id": "basics",
      "title": { "sr": "Osnove", "en": "Basics" },
      "steps": [
        { "id": "basics-lesson", "type": "lesson", "lessonId": "board-and-pieces" },
        { "id": "basics-practice", "type": "practice", "themes": ["mateIn1"], "count": 5, "ratingRange": [600, 1000] },
        { "id": "basics-game", "type": "game", "difficulty": "beginner" }
      ]
    },
    {
      "id": "opening",
      "title": { "sr": "Otvaranje", "en": "The opening" },
      "steps": [
        { "id": "opening-lesson", "type": "lesson", "lessonId": "openings" },
        { "id": "opening-practice", "type": "practice", "themes": ["opening"], "count": 5, "ratingRange": [600, 1400] },
        { "id": "opening-game", "type": "game", "difficulty": "easy" }
      ]
    },
    {
      "id": "middlegame",
      "title": { "sr": "Središnjica", "en": "The middlegame" },
      "steps": [
        { "id": "middlegame-lesson", "type": "lesson", "lessonId": "middlegame" },
        { "id": "middlegame-practice", "type": "practice", "themes": ["fork", "pin"], "count": 5, "ratingRange": [600, 1400] },
        { "id": "middlegame-test", "type": "test", "themes": ["fork", "pin", "discoveredAttack"], "count": 3, "ratingRange": [600, 1200] }
      ]
    },
    {
      "id": "endgame",
      "title": { "sr": "Završnica", "en": "The endgame" },
      "steps": [
        { "id": "endgame-lesson", "type": "lesson", "lessonId": "endgame" },
        { "id": "endgame-practice", "type": "practice", "themes": ["endgame"], "count": 5, "ratingRange": [600, 1200] },
        { "id": "endgame-test", "type": "test", "themes": ["endgame", "mateIn2"], "count": 3, "ratingRange": [600, 1200] }
      ]
    }
  ]
}
```

Ako test iz Step-a 1 prijavi da neka tema nema dovoljno zadataka u opsegu, **proširiti opseg ili zameniti temu** — ne smanjivati `count` ispod 3 i ne menjati test da prođe.

- [ ] **Step 5: Registrovati u paketu i u Xcode target-u, pa DOKAZATI**

U `Package.swift`, u `sources:`, posle `"Models/LessonContent.swift",`:
```swift
                "Models/Curriculum.swift",
```

U `project.pbxproj` dodati `Curriculum.swift` po ručnom obrascu (fileRef + buildFile + grupa `Models` + `PBXSourcesBuildPhase`), sa **slobodnim** id-parom — proveriti da nema kolizije, jer su ranije predložene oznake već bile zauzete.

`curriculum.json` **ne traži izmenu projekta** (`Content/` je folder-referenca), ali to treba potvrditi:
```bash
cp Chessko/Models/Curriculum.swift /tmp/curriculum-backup.swift
echo "OVO NIJE SWIFT @@@" >> Chessko/Models/Curriculum.swift
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build 2>&1 | grep -c "error:"
cp /tmp/curriculum-backup.swift Chessko/Models/Curriculum.swift
grep -c "OVO NIJE SWIFT" Chessko/Models/Curriculum.swift   # mora biti 0
```
Expected: broj grešaka **veći od nule**, pa 0 posle vraćanja. Ako je nula, fajl nije u target-u — STATI i prijaviti.

Zatim potvrditi da `curriculum.json` stvarno stiže u aplikaciju:
```bash
APP=$(xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -showBuildSettings 2>/dev/null \
  | awk '/BUILT_PRODUCTS_DIR/{print $3}')/Chessko.app
ls "$APP/Content/curriculum.json"
```

- [ ] **Step 6: Testovi i commit**

Run: `swift test`
Expected: **35 testova prolazi** (31 + 4 nova).

```bash
git add Chessko/Models/Curriculum.swift Chessko/Content/curriculum.json \
        Package.swift Chessko.xcodeproj/project.pbxproj Tests/ChesskoEngineTests/CurriculumTests.swift
git commit -m "feat: model kurikuluma i curriculum.json

4 poglavlja, 12 koraka od postojece 4 lekcije. Nepoznat tip koraka i
obrnut ratingRange BACAJU gresku — obrnut opseg bi inace srusio proces
pri kreiranju ClosedRange-a. Test tvrdi da svaka lekcija koju kurikulum
pominje postoji i da svaka tema ima dovoljno zadataka u trazenom opsegu."
```

---

## Task 2: `ProgressStore` — perzistencija, migracija, streak

**Files:**
- Create: `Chessko/Logic/ProgressStore.swift`
- Modify: `Chessko/Logic/StatsManager.swift` (postaje fasada)
- Modify: `Package.swift`, `Chessko.xcodeproj/project.pbxproj`
- Test: `Tests/ChesskoEngineTests/ProgressTests.swift`

**Interfaces:**
- Consumes: `Curriculum` (Task 1)
- Produces: `ProgressStore.shared`, `StepState`, `PathProgress.stepStates(stepIds:completed:)`, `PathProgress.currentStreak(goalDays:today:)`, `PathProgress.goalMet(steps:puzzles:)`, `ProgressStore.completeStep(_:)`, `ProgressStore.recordPuzzleSolvedToday()`

- [ ] **Step 1: Napisati testove čistih funkcija**

Ovde žive greške ove faze, pa se testira bez fajlova i bez sata — dan ulazi kao string.

Create `Tests/ChesskoEngineTests/ProgressTests.swift`:

```swift
import Testing
import Foundation
@testable import ChesskoEngine

// MARK: - Otkljucavanje koraka

@Test func firstStepIsAvailableAndRestLocked() {
    let s = PathProgress.stepStates(stepIds: ["a", "b", "c"], completed: [])
    #expect(s["a"] == .available)
    #expect(s["b"] == .locked)
    #expect(s["c"] == .locked)
}

@Test func completingAStepUnlocksExactlyTheNextOne() {
    let s = PathProgress.stepStates(stepIds: ["a", "b", "c"], completed: ["a"])
    #expect(s["a"] == .completed)
    #expect(s["b"] == .available)
    #expect(s["c"] == .locked)
}

@Test func allCompletedLeavesNothingAvailable() {
    let s = PathProgress.stepStates(stepIds: ["a", "b"], completed: ["a", "b"])
    #expect(s["a"] == .completed)
    #expect(s["b"] == .completed)
    #expect(!s.values.contains(.available))
}

@Test func gapInCompletedStepsStillOffersTheFirstUnfinished() {
    // Ne bi trebalo da se desi, ali podaci na disku mogu da kazu bilo sta —
    // korisnik ne sme da ostane bez ijednog dostupnog koraka.
    let s = PathProgress.stepStates(stepIds: ["a", "b", "c"], completed: ["b"])
    #expect(s["a"] == .available)
    #expect(s["b"] == .completed)
    #expect(s["c"] == .locked)
}

// MARK: - Dnevni cilj

@Test func dailyGoalNeedsOneStepOrThreePuzzles() {
    #expect(PathProgress.goalMet(steps: 1, puzzles: 0) == true)
    #expect(PathProgress.goalMet(steps: 0, puzzles: 3) == true)
    #expect(PathProgress.goalMet(steps: 0, puzzles: 2) == false)
    #expect(PathProgress.goalMet(steps: 0, puzzles: 0) == false)
}

// MARK: - Streak

@Test func streakCountsConsecutiveDaysEndingToday() {
    let days: Set<String> = ["2026-09-07", "2026-09-08", "2026-09-09"]
    #expect(PathProgress.currentStreak(goalDays: days, today: "2026-09-09") == 3)
}

@Test func streakSurvivesADayThatIsNotOverYet() {
    // Kljucni slucaj: cilj DANAS jos nije ispunjen. Niz se ne sme prekinuti
    // dok dan ne prodje — inace bi korisniku streak nestao svako jutro.
    let days: Set<String> = ["2026-09-07", "2026-09-08"]
    #expect(PathProgress.currentStreak(goalDays: days, today: "2026-09-09") == 2)
}

@Test func aMissedDayBreaksTheStreak() {
    // 08. preskocen; 09. je danas i nije ispunjen. Niz je prekinut.
    let days: Set<String> = ["2026-09-06", "2026-09-07"]
    #expect(PathProgress.currentStreak(goalDays: days, today: "2026-09-09") == 0)
}

@Test func todayAloneIsAStreakOfOne() {
    #expect(PathProgress.currentStreak(goalDays: ["2026-09-09"], today: "2026-09-09") == 1)
}

@Test func emptyHistoryHasNoStreak() {
    #expect(PathProgress.currentStreak(goalDays: [], today: "2026-09-09") == 0)
}

@Test func streakCrossesMonthAndYearBoundaries() {
    let days: Set<String> = ["2025-12-30", "2025-12-31", "2026-01-01"]
    #expect(PathProgress.currentStreak(goalDays: days, today: "2026-01-01") == 3)
}
```

- [ ] **Step 2: Pokrenuti i videti da pada**

Run: `swift test --filter Progress`
Expected: FAIL — `cannot find 'PathProgress' in scope`.

- [ ] **Step 3: Napisati čiste funkcije i skladište**

Create `Chessko/Logic/ProgressStore.swift`:

```swift
import Foundation

// MARK: - Stanje koraka

enum StepState: String, Codable, Equatable {
    case locked, available, completed
}

// MARK: - Cista logika Puta
//
// Sve sto se moze pogresiti oko otkljucavanja, dnevnog cilja i streak-a stoji
// OVDE, kao ciste funkcije bez fajlova i bez sata. Dan ulazi kao string
// ("yyyy-MM-dd") da test ne zavisi od vremenske zone ni od trenutka pokretanja.

enum PathProgress {

    /// Korak je dostupan ako je PRVI nezavrsen u redosledu; svi posle su
    /// zakljucani. Prazan skup zavrsenih znaci da je dostupan samo prvi.
    static func stepStates(stepIds: [String], completed: Set<String>) -> [String: StepState] {
        var result: [String: StepState] = [:]
        var foundAvailable = false
        for id in stepIds {
            if completed.contains(id) {
                result[id] = .completed
            } else if !foundAvailable {
                result[id] = .available
                foundAvailable = true
            } else {
                result[id] = .locked
            }
        }
        return result
    }

    /// Dnevni cilj: jedan zavrsen korak Puta ILI tri resena zadatka.
    static func goalMet(steps: Int, puzzles: Int) -> Bool {
        steps >= 1 || puzzles >= 3
    }

    /// Dani zaredom sa ispunjenim ciljem, zakljucno sa danas.
    ///
    /// Ako cilj DANAS jos nije ispunjen, brojanje krece od juce — dan jos
    /// traje, pa niz ne sme da se prekine. Prekida ga tek propusten dan.
    static func currentStreak(goalDays: Set<String>, today: String,
                              calendar: Calendar = .current) -> Int {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = calendar.timeZone
        fmt.locale = Locale(identifier: "en_US_POSIX")
        guard var day = fmt.date(from: today) else { return 0 }

        if !goalDays.contains(today) {
            guard let yesterday = calendar.date(byAdding: .day, value: -1, to: day) else { return 0 }
            day = yesterday
        }

        var count = 0
        while goalDays.contains(fmt.string(from: day)) {
            count += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return count
    }
}

// MARK: - Snimak napretka na disku

struct ProgressSnapshot: Codable, Equatable {
    var version: Int = 1

    // Put
    var completedSteps: Set<String> = []
    var stepCompletionDates: [String: String] = [:]   // stepId -> "yyyy-MM-dd"

    // Dnevni cilj i streak
    var stepsCompletedByDay: [String: Int] = [:]
    var puzzlesSolvedByDay: [String: Int] = [:]

    // Preneto iz `UserDefaults` pri prvom pokretanju
    var gamesPlayed = 0, gamesWon = 0, gamesLost = 0, gamesDrawn = 0
    var currentWinStreak = 0, bestWinStreak = 0
    var puzzlesSolved = 0, currentPuzzleStreak = 0, bestPuzzleStreak = 0
    var puzzleRating = 800
}

// MARK: - Skladiste
//
// JSON u Application Support, ne `UserDefaults`: napredak je struktura, ne
// sacica skalara. `UserDefaults` kljucevi se NAMERNO ne brisu posle migracije —
// tako povratak na stariju verziju aplikacije i dalje radi (spec 5.4).

@MainActor
final class ProgressStore {
    static let shared = ProgressStore()

    private(set) var snapshot: ProgressSnapshot

    private static let fileName = "progress.json"

    private static var fileURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory,
                                           in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(fileName)
    }

    private init() {
        if let data = try? Data(contentsOf: Self.fileURL),
           let loaded = try? JSONDecoder().decode(ProgressSnapshot.self, from: data) {
            snapshot = loaded
        } else {
            snapshot = Self.migratedFromUserDefaults()
            save()
        }
    }

    /// Prvo pokretanje posle nadogradnje: statistika se preuzima iz
    /// `UserDefaults`-a i ostavlja tamo netaknuta.
    private static func migratedFromUserDefaults() -> ProgressSnapshot {
        let d = UserDefaults.standard
        var s = ProgressSnapshot()
        s.gamesPlayed = d.integer(forKey: "stats_gamesPlayed")
        s.gamesWon = d.integer(forKey: "stats_gamesWon")
        s.gamesLost = d.integer(forKey: "stats_gamesLost")
        s.gamesDrawn = d.integer(forKey: "stats_gamesDrawn")
        s.currentWinStreak = d.integer(forKey: "stats_currentWinStreak")
        s.bestWinStreak = d.integer(forKey: "stats_bestWinStreak")
        s.puzzlesSolved = d.integer(forKey: "stats_puzzlesSolved")
        s.currentPuzzleStreak = d.integer(forKey: "stats_currentPuzzleStreak")
        s.bestPuzzleStreak = d.integer(forKey: "stats_bestPuzzleStreak")
        s.puzzleRating = d.object(forKey: "stats_puzzleRating") == nil
            ? 800 : d.integer(forKey: "stats_puzzleRating")
        return s
    }

    func save() {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        do {
            try data.write(to: Self.fileURL, options: .atomic)
        } catch {
            print("[Chessko] GRESKA: napredak nije sacuvan: \(error)")
        }
    }

    /// Mutira snimak pa odmah upisuje. Napredak se menja retko (jednom po
    /// koraku ili zadatku), pa upis po izmeni ne kosta nista, a gasenje
    /// aplikacije u bilo kom trenutku ne gubi vise od poslednje akcije.
    private func mutate(_ change: (inout ProgressSnapshot) -> Void) {
        change(&snapshot)
        save()
    }

    static func dayKey(_ date: Date = Date(), calendar: Calendar = .current) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = calendar.timeZone
        fmt.locale = Locale(identifier: "en_US_POSIX")
        return fmt.string(from: date)
    }

    // MARK: Put

    func stepStates(for curriculum: Curriculum) -> [String: StepState] {
        PathProgress.stepStates(stepIds: curriculum.allStepIds,
                                completed: snapshot.completedSteps)
    }

    func completeStep(_ id: String) {
        guard !snapshot.completedSteps.contains(id) else { return }
        let day = Self.dayKey()
        mutate {
            $0.completedSteps.insert(id)
            $0.stepCompletionDates[id] = day
            $0.stepsCompletedByDay[day, default: 0] += 1
        }
    }

    func recordPuzzleSolvedToday() {
        let day = Self.dayKey()
        mutate { $0.puzzlesSolvedByDay[day, default: 0] += 1 }
    }

    // MARK: Cilj i streak

    var goalMetToday: Bool {
        let day = Self.dayKey()
        return PathProgress.goalMet(steps: snapshot.stepsCompletedByDay[day] ?? 0,
                                    puzzles: snapshot.puzzlesSolvedByDay[day] ?? 0)
    }

    var currentStreak: Int {
        let days = Set(Set(snapshot.stepsCompletedByDay.keys)
            .union(snapshot.puzzlesSolvedByDay.keys)
            .filter { PathProgress.goalMet(steps: snapshot.stepsCompletedByDay[$0] ?? 0,
                                           puzzles: snapshot.puzzlesSolvedByDay[$0] ?? 0) })
        return PathProgress.currentStreak(goalDays: days, today: Self.dayKey())
    }

    // MARK: Pristup statistici (koristi fasada `StatsManager`)

    func updateStats(_ change: (inout ProgressSnapshot) -> Void) { mutate(change) }
}
```

- [ ] **Step 4: `StatsManager` postaje fasada**

`StatsManager` zadržava **svaki postojeći potpis** i prosleđuje `ProgressStore`-u. Time se 26 poziva u `GameViewModel`/`PuzzleViewModel`/`SettingsSheet` ne dira — među njima i logika rejtinga proverena u Fazi 2.

Svako svojstvo postaje računato nad `ProgressStore.shared.snapshot`, npr.:
```swift
var gamesPlayed: Int { ProgressStore.shared.snapshot.gamesPlayed }
```
a svaka metoda upisuje kroz `updateStats`:
```swift
func recordGameWon() {
    ProgressStore.shared.updateStats {
        $0.gamesPlayed += 1; $0.gamesWon += 1
        $0.currentWinStreak += 1
        $0.bestWinStreak = max($0.bestWinStreak, $0.currentWinStreak)
    }
}
```
`recordPuzzleSolved()` dodatno zove `ProgressStore.shared.recordPuzzleSolvedToday()` — otud dnevni cilj zna za zadatke.

`nonisolated static let solvedPuzzleIdsKey` / `solvedDatesKey` **ostaju** gde jesu; `PuzzleViewModel` ih koristi.

Zadržati `didSet` upis u `UserDefaults`? **Ne.** Jedan izvor istine je JSON. Ali stari ključevi se ne brišu (spec), pa `resetStats()` nastavlja da briše `solvedPuzzleIdsKey`/`solvedDatesKey` i da vraća snimak na podrazumevani.

Dodati komentar na vrh `StatsManager`-a da je fasada i zašto.

- [ ] **Step 5: Registrovati fajl i DOKAZATI** (isti postupak kao Task 1 Step 5, uz `cp` za vraćanje)

- [ ] **Step 6: Testovi i commit**

Run: `swift test`
Expected: **46 testova prolazi** (35 + 11 novih). Postojećih 6 testova rejtinga mora i dalje da prolazi — oni gađaju `StatsManager.newRating`, koja je čista i ne menja se.

```bash
git add Chessko/Logic/ProgressStore.swift Chessko/Logic/StatsManager.swift \
        Package.swift Chessko.xcodeproj/project.pbxproj Tests/ChesskoEngineTests/ProgressTests.swift
git commit -m "feat: ProgressStore — napredak, dnevni cilj i streak u JSON-u

Streak i otkljucavanje su ciste funkcije bez fajlova i bez sata; dan ulazi
kao string pa test ne zavisi od vremenske zone. Kljucni slucaj: niz se NE
prekida dok dan ne prodje, inace bi korisniku nestajao svako jutro.

StatsManager zadrzava svaki potpis i postaje fasada nad ProgressStore-om —
26 postojecih poziva se ne dira, ukljucujuci logiku rejtinga iz Faze 2.
UserDefaults kljucevi se namerno NE brisu (spec 5.4): povratak na stariju
verziju aplikacije i dalje radi."
```

---

## Task 3: Ekran Puta i preimenovanje taba

**Files:**
- Create: `Chessko/Views/PathView.swift`
- Modify: `Chessko/ContentView.swift`
- Modify: `build_localizations.py`
- Modify: `Chessko.xcodeproj/project.pbxproj`

**Interfaces:**
- Consumes: `Curriculum`, `ProgressStore`, `LessonRepository`
- Produces: `PathView`, `CurriculumRepository.shared.curriculum`

- [ ] **Step 1: Učitavanje kurikuluma**

Dodati u `Chessko/Logic/LessonRepository.swift` (isti sloj, isti obrazac):

```swift
@MainActor
final class CurriculumRepository {
    static let shared = CurriculumRepository()

    /// `nil` znaci da `curriculum.json` nedostaje iz bundle-a — ekran Puta to
    /// mora da prikaze, ne da ostane prazan.
    private(set) lazy var curriculum: Curriculum? = {
        guard let url = Bundle.main.url(forResource: "curriculum", withExtension: "json",
                                        subdirectory: "Content") else { return nil }
        do {
            return try JSONDecoder().decode(Curriculum.self, from: Data(contentsOf: url))
        } catch {
            print("[Chessko] GRESKA: curriculum.json se ne dekodira: \(error)")
            assertionFailure("curriculum.json se ne dekodira: \(error)")
            return nil
        }
    }()
}
```

- [ ] **Step 2: Ekran**

`PathView` prikazuje, odozgo nadole:

1. **Nastavak** — kartica „Nastavi" sa rednim brojem i naslovom prvog dostupnog koraka (spec 5.1: „Nastavi: Korak 7 — Vezivanje"). Ako su svi koraci završeni, kartica kaže da je put pređen.
2. **Streak i dnevni cilj** — broj dana zaredom i da li je današnji cilj ispunjen.
3. **Poglavlja** — svako sa naslovom na tekućem jeziku i procentom završenosti, pa koraci sa stanjem (`completed` kvačica, `available` normalno, `locked` zaključano i nedodirljivo).

Naslov koraka: za `lesson` — naslov lekcije iz `LessonRepository`; za ostale tipove — lokalizovana oznaka tipa (`Vežba`, `Partija`, `Test`) uz broj zadataka gde ima smisla.

> **Zašto ceo ekran nije prepisan u plan.** Raspored je stvar dizajna i biće ~200 linija SwiftUI-ja; prepisan ovde, samo bi dodao još jedno mesto na kome se greši. Umesto toga plan zaključava **tok podataka** (skelet ispod), sve stringove, i pravilo da sve ide kroz `DS`. Ostalo je izvedba.

Skelet — imena i tok podataka su obavezni, raspored nije:

```swift
struct PathView: View {
    private var curriculum: Curriculum? { CurriculumRepository.shared.curriculum }
    private var store: ProgressStore { ProgressStore.shared }

    /// Stanje svakog koraka po id-ju. Jedan izvor za sve tri sekcije ekrana.
    private var states: [String: StepState] {
        guard let curriculum else { return [:] }
        return store.stepStates(for: curriculum)
    }

    /// Prvi dostupan korak — to je „Nastavi". `nil` znaci da je put pređen.
    private var nextStep: (index: Int, step: CurriculumStep)? {
        guard let curriculum else { return nil }
        let all = curriculum.chapters.flatMap(\.steps)
        guard let i = all.firstIndex(where: { states[$0.id] == .available })
        else { return nil }
        return (i + 1, all[i])   // redni broj je 1-baziran, za „Korak %lld"
    }

    private func completion(of chapter: Chapter) -> Double {
        let done = chapter.steps.filter { states[$0.id] == .completed }.count
        return chapter.steps.isEmpty ? 0 : Double(done) / Double(chapter.steps.count)
    }

    private func title(of step: CurriculumStep, language: String) -> String {
        switch step.kind {
        case .lesson(let id):
            return LessonRepository.shared.lesson(id: id, language: language)?.title
                ?? Loc("Lekcija nije dostupna")
        case .practice(_, let count, _): return LocF("Vežba · %lld", count)
        case .test(_, let count, _):     return LocF("Test · %lld", count)
        case .game:                      return Loc("Partija")
        }
    }

    var body: some View { /* nastavak · streak · poglavlja */ }
}
```

Ako `curriculum` bude `nil`, ekran prikazuje `Loc("Put nije dostupan")` — ne prazan ekran.

Novi stringovi u `build_localizations.py`:
```python
add("Put", "Path", "Parcours", "Pfad", "Percorso", "Путь", "路径", "पथ")
add("Nastavi", "Continue", "Continuer", "Weiter", "Continua", "Продолжить", "继续", "जारी रखें")
add("Korak %lld", "Step %lld", "Étape %lld", "Schritt %lld", "Passo %lld", "Шаг %lld", "第 %lld 步", "चरण %lld")
add("Vežba", "Practice", "Entraînement", "Übung", "Esercizio", "Практика", "练习", "अभ्यास")
add("Partija", "Game", "Partie", "Partie", "Partita", "Партия", "对局", "बाज़ी")
add("Test", "Test", "Test", "Test", "Test", "Тест", "测验", "परीक्षा")
add("Dana zaredom", "Day streak", "Jours d'affilée", "Tage in Folge", "Giorni di fila", "Дней подряд", "连续天数", "लगातार दिन")
add("Cilj za danas ispunjen", "Today's goal met", "Objectif du jour atteint", "Tagesziel erreicht", "Obiettivo di oggi raggiunto", "Цель на сегодня выполнена", "今日目标已完成", "आज का लक्ष्य पूरा")
add("Prešao si ceo put!", "You finished the whole path!", "Tu as terminé tout le parcours !", "Du hast den ganzen Pfad geschafft!", "Hai completato tutto il percorso!", "Вы прошли весь путь!", "你走完了整条路径！", "आपने पूरा पथ पूरा किया!")
add("Put nije dostupan", "Path unavailable", "Parcours indisponible", "Pfad nicht verfügbar", "Percorso non disponibile", "Путь недоступен", "路径不可用", "पथ उपलब्ध नहीं")
```
pa `python3 build_localizations.py`.

Sve boje i razmaci kroz `DS`. Bez bedževa i konfeta (spec 5.4).

- [ ] **Step 3: Tab `Učenje` → `Put`**

U `ContentView`, treći tab: `Label(Loc("Put"), systemImage: "signpost.right.fill")` i `PathView` umesto `LearnView`. **`LearnView` se ne briše** — Task 3 samo menja tab; brisanje ide u Task 6, kad se potvrdi da Put pokriva sve što je Učenje nudilo.

`learnViewModel` ostaje podignut u `ContentView` (potreban je explorer-u u lekcijama) i prosleđuje se dalje kroz `.environment(...)` kao i sada.

- [ ] **Step 4: Lekcijski koraci rade, ostali još ne**

Tap na `lesson` korak otvara `LessonDetailView(lessonId:)`. Na dnu lekcije dodati dugme koje završava korak (`ProgressStore.shared.completeStep(id)`) — spec: „korisnik dođe do kraja i potvrdi".

Koraci tipa `practice`/`test`/`game` u ovom zadatku prikazuju se u listi, ali tap na njih **ne radi ništa** — pokretači stižu u zadacima 4 i 5. To je namerno i mora se navesti u izveštaju.

- [ ] **Step 5: Registrovati fajl i DOKAZATI**, pa build i vizuelna provera u obe teme

Sintetički klikovi u simulatoru **ne rade** (potvrđeno u fazama 0–3). Do ekrana se stiže determinističkim putem: privremeno zakucati koren `ContentView`-a na `PathView`, snimiti, **vratiti i potvrditi da je radno stablo čisto**.

- [ ] **Step 6: Commit**

---

## Task 4: Pokretač `practice` i `test` koraka

**Files:**
- Modify: `Chessko/ViewModels/PuzzleViewModel.swift`
- Create: `Chessko/Views/StepPracticeView.swift`
- Modify: `Chessko/Views/PathView.swift`, `build_localizations.py`, `project.pbxproj`

**Interfaces:**
- Consumes: `CurriculumStep`, `ProgressStore`, `PuzzleRepository`
- Produces: `PuzzleViewModel.startStepPractice(step:)`, `PuzzleViewModel.stepProgress`

- [ ] **Step 1: Treći režim u `PuzzleViewModel`**

`PuzzleMode` dobija `case step(id: String, requireFlawless: Bool)`. Uz njega:

```swift
/// Red zadataka za tekuci korak Puta i koliko ih je reseno.
private(set) var stepQueue: [ChessPuzzle] = []
private(set) var stepSolved: Int = 0
private(set) var stepFailed: Bool = false
var stepProgress: (solved: Int, total: Int) { (stepSolved, stepQueue.count) }
```

`startStepPractice(step:)` puni red iz `PuzzleRepository.puzzles(themes:ratingRange:excluding:limit:)`.

**Presek rejtinga i koraka (spec 5.4):** prozor je `rejting-200 … rejting+100` **presečen** sa `ratingRange` koraka. **Ako je presek prazan, prednost ima opseg koraka** — kurikulum zna šta se uči, rejting je samo podešavanje. Presek se računa čistom funkcijom da bi bio testabilan:

```swift
/// Vraca opseg koraka kad je presek prazan — kurikulum ima prednost (spec 5.4).
nonisolated static func stepRatingWindow(playerRating r: Int,
                                         stepRange: ClosedRange<Int>) -> ClosedRange<Int> {
    let lo = max(stepRange.lowerBound, r - 200)
    let hi = min(stepRange.upperBound, r + 100)
    return lo <= hi ? lo...hi : stepRange
}
```

Dodati tri testa u `Tests/ChesskoEngineTests/PuzzleRepositoryTests.swift` — tamo gde već stoje testovi za `practiceRatingWindow`, jer funkcija živi u istom tipu: presek postoji; rejting ispod opsega koraka (prazan presek → opseg koraka); rejting iznad opsega koraka (isto).

Ova funkcija ide u `PuzzleRepository.swift` (Foundation-only, već u paketu), ne u `PuzzleViewModel` (SwiftUI, nije u paketu).

- [ ] **Step 2: Završetak koraka**

`practice` korak je završen kad je `stepSolved == stepQueue.count`.
`test` korak je završen samo ako uz to `stepFailed == false`; prva greška ga obara i korak kreće ispočetka sa novim zadacima.

Po završetku: `ProgressStore.shared.completeStep(step.id)`.

Svaki rešen zadatak u koraku takođe zove `StatsManager.shared.recordPuzzleSolved()` (postojeći put), pa dnevni cilj i rejting rade kao i na tabu Zadaci.

- [ ] **Step 3: Ekran**

`StepPracticeView` je tanak: `BoardView` + traka napretka (`3/5`) + status. Ponovo koristi postojeće komponente sa taba Zadaci; **ne duplirati tok rešavanja.**

Novi stringovi:
```python
add("Zadatak %lld od %lld", "Puzzle %lld of %lld", "Problème %lld sur %lld", "Aufgabe %lld von %lld", "Problema %lld di %lld", "Задача %lld из %lld", "第 %lld 题，共 %lld 题", "पहेली %lld / %lld")
add("Test mora biti rešen bez greške", "The test must be solved without mistakes", "Le test doit être réussi sans erreur", "Der Test muss fehlerfrei gelöst werden", "Il test va superato senza errori", "Тест нужно пройти без ошибок", "测验必须零失误通过", "परीक्षा बिना गलती के पूरी करनी होगी")
add("Greška — test kreće ispočetka", "Mistake — the test restarts", "Erreur — le test recommence", "Fehler — der Test beginnt von vorn", "Errore — il test ricomincia", "Ошибка — тест начинается заново", "失误 — 测验重新开始", "गलती — परीक्षा फिर से शुरू")
```

- [ ] **Step 4: Testovi, build, commit**

Expected: **49 testova** (46 + 3 nova za prozor rejtinga).

---

## Task 5: Pokretač `game` koraka

**Files:**
- Modify: `Chessko/ViewModels/GameViewModel.swift`, `Chessko/Views/PathView.swift`
- Modify: `build_localizations.py`, `project.pbxproj`

- [ ] **Step 1: Partija iz koraka**

`game` korak otvara postojeći `GameView` sa težinom iz koraka i, ako je zadat, `startFEN`-om.

`GameViewModel` već ima `isGameOver` (linija 335: tačno kad je status `.checkmate`, `.draw` ili `.resigned`). Ne dodavati nov izvor istine — korak se završava kad **to** postane `true`:

```swift
.onChange(of: gameViewModel.isGameOver) { _, over in
    guard over, let stepId = activeStepId else { return }
    ProgressStore.shared.completeStep(stepId)
    activeStepId = nil          // jednom po ulasku, ne po svakoj promeni
}
```

Korak se završava **bez obzira na ishod**. Spec: uslov je „partija odigrana do kraja", ne pobeda.

Predaja partije (`resigned`) **takođe** završava korak: partija jeste došla do kraja. Ovo zapisati u izveštaj kao odluku, jer je čitljivo i drugačije.

- [ ] **Step 2: Ne pokvariti slobodnu partiju**

Tab Igra i dalje mora da radi bez ikakve veze sa Putem. Korak Puta samo postavlja početne parametre i pretplaćuje se na kraj partije; `newGame` iz taba Igra ne sme da završi korak.

- [ ] **Step 3: Testovi, build, commit**

---

## Task 6: Provera i dokumentacija

- [ ] **Step 1: Napredak preživljava gašenje**

U simulatoru: završiti korak, ubiti aplikaciju (`xcrun simctl terminate`), pokrenuti ponovo, potvrditi da je korak i dalje završen i da je sledeći otključan. Ovo je uslov završetka faze iz specifikacije i **ne sme se izvesti iz koda** — mora se videti.

- [ ] **Step 2: Streak se prekida**

Napredak se čuva po danu, pa se prekid proverava izmenom `progress.json` u kontejneru aplikacije: upisati cilj ispunjen pre dva dana, pokrenuti, potvrditi da je streak 0. Zatim upisati juče, potvrditi da je 1.

```bash
CONT=$(xcrun simctl get_app_container <dev> com.veljkoni.chessko data)
cat "$CONT/Library/Application Support/progress.json"
```

- [ ] **Step 3: Migracija**

Obrisati `progress.json`, ostaviti `UserDefaults`, pokrenuti — statistika mora da se pojavi u novom fajlu, a `UserDefaults` ključevi da ostanu.

- [ ] **Step 4: Vizuelna provera** ekrana Puta u obe teme.

- [ ] **Step 5: Ukloniti `LearnView`** ako ga više niko ne koristi (`grep`), i tek tada.

- [ ] **Step 6: `CLAUDE.md`** — nova sekcija „Put": gde je `curriculum.json`, kako se dodaje korak, tipovi koraka, gde živi napredak i zašto u Application Support a ne u `UserDefaults`, i da je `StatsManager` fasada. Changelog unos.

---

## Završna provera faze

- [ ] `swift test` prolazi (31 postojeći + ~18 novih)
- [ ] `xcodebuild … build` → `** BUILD SUCCEEDED **`
- [ ] `git status --short` prazan
- [ ] `curriculum.json` u izgrađenom `.app`
- [ ] Napredak preživljava gašenje — **provereno u simulatoru, ne izvedeno iz koda**
- [ ] Streak se prekida propuštenim danom — provereno izmenom `progress.json`
- [ ] Migracija iz `UserDefaults` radi, stari ključevi netaknuti
- [ ] Ekran Puta pregledan u obe teme

## Rizici

- **Streak je najlakše pogrešiti, a najteže primetiti.** Zato su sve odluke o danima čiste funkcije sa danom kao stringom, a ne `Date` aritmetika nad `now`. Prelazak meseca i godine je pokriven testom.
- **`StatsManager` kao fasada je namerna, ali je debt.** Ako se ikad ukloni, treba dirati 26 poziva odjednom — zato je odluka zapisana u planu i ide u `CLAUDE.md`.
- **Kurikulum pokazuje na lekcije i teme po imenu.** Tipfeler daje korak koji se ne može završiti, bez ijedne poruke. Protivmera je test iz Task-a 1 koji tvrdi da svaka lekcija postoji i da svaka tema ima dovoljno zadataka u opsegu; on mora da ostane zelen i posle Faze 4b.
- **Obrnut `ratingRange` u JSON-u ruši proces.** `ClosedRange` sa donjom granicom većom od gornje puca pri kreiranju. Dekoder ga odbija kao neispravan JSON — ista klasa greške koja je u Fazi 2 zamalo prošla.
