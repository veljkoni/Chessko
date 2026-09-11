# Faza 5 — Analiza partije: plan implementacije

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Posle svake partije Stockfish prolazi sve pozicije, klasifikuje poteze po gubitku u centipionima i prikazuje ekran analize sa procentom tačnosti, trakom poteza u boji i prelomnim potezom.

**Architecture:** Računica je odvojena od motora. Sva matematika (mapiranje mat-ocena u centipione, `cpLoss`, klasifikacija, procenat tačnosti, agregacija po stranama) živi u jednom Foundation-only fajlu koji se kompajlira i u SwiftPM testni paket, pa je u celosti pokrivena testovima bez Stockfish-a. `StockfishBridge` dobija samo dve nove sposobnosti: da pored poteza vrati i **ocenu** pozicije, i da oceni **niz** pozicija uz izveštaj o napretku. Ekran analize je čist prikaz gotovog `GameAnalysis` modela i jedini je koji zna za boje i oznake.

**Ključna arhitektonska odluka:** partija od N poteza analizira se sa **N+1 pretraga, ne 2N**. Ocena pozicije pre poteza daje „najbolje što se moglo"; ocena pozicije posle poteza, sa obrnutim znakom, daje „šta je odigrano". Svaka pozicija se dakle pretražuje **tačno jednom**, a svaka susedna dva rezultata daju jedan `cpLoss`.

**Tech Stack:** Swift 6.0, SwiftUI, `@Observable`, `@MainActor`, `ChessKitEngine` (Stockfish 17, već integrisan), SwiftPM testni paket (`swift test`), Swift Testing (`@Test`).

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` — sekcija **5.5 Analiza partije** i **Faza 5**.

## Global Constraints

Svaki task ih implicitno nosi.

- **`create_xcode_project.py` se NIKAD ne pokreće.** Regeneriše `project.pbxproj` iz nule, ne zna za `chesskit-engine` SPM zavisnost i obara Stockfish. `project.pbxproj` se od 2026-07-03 **održava ručno**.
- **Nijedna nova SPM zavisnost.** `ChesskoAndroid/` se ne dira.
- **Svaki NOV `.swift` fajl mora ručno u `project.pbxproj`** — i mora se **dokazati** da se kompajlira u aplikaciju, ne pretpostaviti. Postupak je u Task-u 1, Step 6. Ova greška je u projektu napravljena dvaput (`.nnue` mreže 2026-06-26, `PuzzleRepository.swift` u Fazi 2) i oba puta su i `swift test` i `xcodebuild` bili zeleni dok aplikacija fajl nikad nije kompajlirala.
- **Foundation-only fajlovi idu i u `Package.swift` `sources:`** da budu testabilni. Fajl koji uvozi SwiftUI tamo NE sme.
- **Dubina pretrage je fiksna 12** (spec 5.5: „Dubina fiksna (12) da analiza traje predvidivo").
- **Klase poteza i pragovi su iz spec-a, doslovno:** najbolji = odigran je potez motora; odličan `< 20`; dobar `< 50`; netačnost `< 100`; greška `< 300`; promašaj `>= 300`.
- **Formula tačnosti je iz spec-a, doslovno:** `103.1668 * exp(-0.04354 * avgCpLoss) - 3.1669`, ograničeno na `0...100`.
- **UI tekst je na srpskom** (izvorni jezik) i ide kroz `Loc(...)`/`LocF(...)`, sa ključevima dodatim u `build_localizations.py` i regenerisanim `Localizable.xcstrings` za svih 8 jezika.
- **Boje idu kroz `DS` tokene.** `DS.onAccent` na `DS.accent`, `DS.onScrim` samo na `DS.scrim`. Nikad `Color.accentColor`.
- **Analiza ne sme da blokira glavnu nit.** Spec 5.5 to formuliše kao „radi na `Task.detached`"; ovde je ispunjeno drugačije i namerno: `StockfishBridge` je `actor`, pa `await` na njegovu metodu već izmešta ceo teški posao sa glavne niti. Orkestrirajući `Task` ostaje na `@MainActor` samo da bi upisivao `phase`, što je `@Observable` stanje koje se ionako sme menjati samo tamo. `Task.detached` bi ovde tražio dodatni skok nazad na `@MainActor` za svaki izveštaj o napretku, bez ijedne dobiti.

---

## Struktura fajlova

| Fajl | Odgovornost |
|---|---|
| `Chessko/Models/MoveAnalysis.swift` **(nov, Foundation-only)** | `EngineScore`, `MoveClass`, `AnalyzedMove`, `GameAnalysis` + sva matematika. Ne zna za Stockfish ni za SwiftUI. |
| `Chessko/Logic/UCIScoreParser.swift` **(nov, Foundation-only)** | Čita `score cp` / `score mate` iz UCI `info` linije. Odvojen od `StockfishBridge`-a jer je čista funkcija nad stringom, pa je testabilan. |
| `Chessko/Logic/StockfishBridge.swift` (izmena) | Dve nove metode: ocena jedne pozicije i ocena niza pozicija sa napretkom. |
| `Chessko/ViewModels/AnalysisViewModel.swift` **(nov)** | Pokreće analizu, drži napredak, otkazivanje, gotov `GameAnalysis`. |
| `Chessko/Views/AnalysisView.swift` **(nov)** | Ekran analize: tačnost oba igrača, traka poteza sa oznakama, prelomni potez, otvaranje pozicije u review modu. |
| `Chessko/Views/GameView.swift` (izmena) | Dugme „Analiziraj partiju" kad je partija gotova. |
| `Chessko/Views/StepGameView.swift` (izmena) | Posle `game` koraka Puta prikaz analize umesto pukog „pobedio/izgubio". |
| `Tests/ChesskoEngineTests/MoveAnalysisTests.swift` **(nov)** | Testovi matematike i parsera. |

---

## Task 1: Model i matematika analize (bez Stockfish-a)

Ovo je srž faze. Ako je ovde nešto pogrešno, ceo ekran laže korisniku o tome koliko dobro igra — a to se ne vidi ni iz jednog build-a.

**Files:**
- Create: `Chessko/Models/MoveAnalysis.swift`
- Create: `Tests/ChesskoEngineTests/MoveAnalysisTests.swift`
- Modify: `Package.swift` (dodati izvor u `sources:`)
- Modify: `Chessko.xcodeproj/project.pbxproj` (4 linije, ručno)

**Interfaces:**
- Consumes: `GameState`, `ChessMove` (postojeći, Foundation-only).
- Produces, i na ovo se oslanjaju Taskovi 2–6:
  - `enum EngineScore: Equatable, Sendable { case cp(Int); case mate(Int) }`
  - `var EngineScore.centipawns: Int`
  - `enum MoveClass: String, CaseIterable, Sendable { case best, excellent, good, inaccuracy, mistake, blunder }`
  - `static func MoveClass.classify(cpLoss: Int, playedEngineBest: Bool) -> MoveClass`
  - `struct AnalyzedMove: Sendable { let ply: Int; let notation: String; let byWhite: Bool; let cpLoss: Int; let moveClass: MoveClass; let scoreBefore: EngineScore; let scoreAfter: EngineScore }`
  - `struct GameAnalysis: Sendable { let moves: [AnalyzedMove]; let whiteAccuracy: Double; let blackAccuracy: Double; var turningPoint: AnalyzedMove? }`
  - `static func GameAnalysis.cpLoss(before: EngineScore, after: EngineScore) -> Int`
  - `static func GameAnalysis.accuracy(avgCpLoss: Double) -> Double`
  - `static func GameAnalysis.build(notations: [String], scores: [EngineScore], engineBestMatched: [Bool]) -> GameAnalysis`

### Odluke koje spec ne pokriva — donesene ovde, namerno i eksplicitno

Spec daje tabelu pragova i formulu tačnosti, ali obe pretpostavljaju da je ocena **centipion**. Stockfish za forsiran mat ne vraća centipione nego `score mate N`. Tri posledice se moraju rešiti, i rešavaju se ovako:

1. **Mat se mapira u centipione** `mate(n) → n > 0 ? 10_000 - min(abs(n), 99) : -10_000 + min(abs(n), 99)`. Time je mat u 1 bolji od mata u 2, a svaki mat je daleko iznad svake realne centipionske ocene.
2. **`cpLoss` se ograničava na `0...1000`.** Bez gornje granice jedan propušten mat (razlika ~20.000) uništi prosek cele partije i tačnost padne na ~0 iako je partija bila solidna. Granica od 1000 je i dalje trostruko iznad praga za promašaj (300), pa ne sakriva nijednu grešku — samo sprečava da jedna pozicija proguta sve ostale.
3. **Donja granica je 0.** Ako je pozicija posle poteza ocenjena bolje nego pre njega (dešava se — pretraga nije determinističko savršenstvo na fiksnoj dubini), to nije „negativan gubitak" nego nula.

**Prelomni potez** je potez sa najvećim `cpLoss`-om u partiji, ali **samo ako je taj gubitak ≥ 100** (bar netačnost). U čisto odigranoj partiji ne sme da se izdvaja potez od 12 centipiona kao „prelomni" — to bi bilo lažno dramatizovanje.

- [ ] **Step 1: Napisati testove koji padaju**

Kreirati `Tests/ChesskoEngineTests/MoveAnalysisTests.swift`:

```swift
import Testing
@testable import ChesskoEngine

// MARK: - EngineScore

@Test func centipawnScorePassesThrough() {
    #expect(EngineScore.cp(0).centipawns == 0)
    #expect(EngineScore.cp(250).centipawns == 250)
    #expect(EngineScore.cp(-80).centipawns == -80)
}

@Test func mateScoreMapsFarAboveAnyCentipawnScore() {
    // Mat u 1 mora biti bolji od mata u 2, a oba daleko iznad bilo koje
    // realne centipionske ocene (koja se u praksi drzi unutar +-5000).
    #expect(EngineScore.mate(1).centipawns > EngineScore.mate(2).centipawns)
    #expect(EngineScore.mate(2).centipawns > 5000)
    #expect(EngineScore.mate(-1).centipawns < EngineScore.mate(-2).centipawns)
    #expect(EngineScore.mate(-2).centipawns < -5000)
}

// MARK: - cpLoss

@Test func cpLossIsBeforePlusAfterBecausePerspectiveFlips() {
    // `before` je iz ugla igraca koji vuce potez; `after` je iz ugla PROTIVNIKA,
    // jer je posle poteza on na potezu. Zato se sabiraju, ne oduzimaju.
    // Bio +50 za mene, posle poteza +50 za protivnika => izgubio sam 100.
    #expect(GameAnalysis.cpLoss(before: .cp(50), after: .cp(50)) == 100)
    // Bio +50 za mene, posle poteza -50 za protivnika (= +50 za mene) => nista.
    #expect(GameAnalysis.cpLoss(before: .cp(50), after: .cp(-50)) == 0)
}

@Test func cpLossNeverGoesNegative() {
    // Pretraga na fiksnoj dubini ume da oceni poziciju POSLE poteza bolje nego
    // onu pre njega. To nije "negativan gubitak" nego nula.
    #expect(GameAnalysis.cpLoss(before: .cp(10), after: .cp(-200)) == 0)
}

@Test func cpLossIsCappedSoOneMissedMateCannotEatTheWholeGame() {
    // Propusten mat daje razliku od ~20.000 centipiona. Bez granice bi jedan
    // takav potez sam odredio prosek cele partije.
    let loss = GameAnalysis.cpLoss(before: .mate(1), after: .cp(0))
    #expect(loss == 1000)
    #expect(loss > 300)  // i dalje uredno iznad praga za promasaj
}

// MARK: - Klasifikacija (pragovi doslovno iz spec-a)

@Test func classificationFollowsSpecThresholds() {
    #expect(MoveClass.classify(cpLoss: 0,   playedEngineBest: true)  == .best)
    #expect(MoveClass.classify(cpLoss: 0,   playedEngineBest: false) == .excellent)
    #expect(MoveClass.classify(cpLoss: 19,  playedEngineBest: false) == .excellent)
    #expect(MoveClass.classify(cpLoss: 20,  playedEngineBest: false) == .good)
    #expect(MoveClass.classify(cpLoss: 49,  playedEngineBest: false) == .good)
    #expect(MoveClass.classify(cpLoss: 50,  playedEngineBest: false) == .inaccuracy)
    #expect(MoveClass.classify(cpLoss: 99,  playedEngineBest: false) == .inaccuracy)
    #expect(MoveClass.classify(cpLoss: 100, playedEngineBest: false) == .mistake)
    #expect(MoveClass.classify(cpLoss: 299, playedEngineBest: false) == .mistake)
    #expect(MoveClass.classify(cpLoss: 300, playedEngineBest: false) == .blunder)
    #expect(MoveClass.classify(cpLoss: 999, playedEngineBest: false) == .blunder)
}

@Test func engineBestMoveWinsOverThresholdsEvenWithSmallLoss() {
    // Potez motora je po definiciji najbolji, i kad merenje da mali gubitak
    // (razlicite dubine daju razlicite ocene iste pozicije).
    #expect(MoveClass.classify(cpLoss: 15, playedEngineBest: true) == .best)
}

// MARK: - Procenat tacnosti (formula doslovno iz spec-a)

@Test func accuracyMatchesSpecFormula() {
    // 103.1668 * exp(-0.04354 * x) - 3.1669
    #expect(abs(GameAnalysis.accuracy(avgCpLoss: 0) - 99.9999) < 0.01)
    #expect(abs(GameAnalysis.accuracy(avgCpLoss: 10) - 63.5) < 0.5)
    #expect(abs(GameAnalysis.accuracy(avgCpLoss: 50) - 8.5) < 0.5)
}

@Test func accuracyIsClampedToZeroAndHundred() {
    // Formula za veliko x ide ispod nule (-3.1669 u limitu).
    #expect(GameAnalysis.accuracy(avgCpLoss: 1000) == 0)
    #expect(GameAnalysis.accuracy(avgCpLoss: 0) <= 100)
    #expect(GameAnalysis.accuracy(avgCpLoss: -5) == 100)
}

@Test func accuracyFallsAsAverageLossRises() {
    let a = GameAnalysis.accuracy(avgCpLoss: 5)
    let b = GameAnalysis.accuracy(avgCpLoss: 25)
    let c = GameAnalysis.accuracy(avgCpLoss: 80)
    #expect(a > b)
    #expect(b > c)
}

// MARK: - Sklapanje cele analize

@Test func buildPairsScoresIntoMovesAndSplitsBySide() {
    // 3 poteza => 4 ocene. Potezi 0 i 2 su beli, potez 1 je crni.
    let analysis = GameAnalysis.build(
        notations: ["e4", "e5", "Sf3"],
        scores: [.cp(20), .cp(-20), .cp(20), .cp(-20)],
        engineBestMatched: [true, true, true]
    )
    #expect(analysis.moves.count == 3)
    #expect(analysis.moves[0].byWhite == true)
    #expect(analysis.moves[1].byWhite == false)
    #expect(analysis.moves[2].byWhite == true)
    #expect(analysis.moves[0].notation == "e4")
    #expect(analysis.moves.allSatisfy { $0.cpLoss == 0 })
    #expect(analysis.whiteAccuracy == 100)
    #expect(analysis.blackAccuracy == 100)
}

@Test func buildComputesEachSideAccuracyFromOnlyThatSideMoves() {
    // Beli igra savrseno, crni gubi po 200 centipiona po potezu.
    //
    // Ocene se moraju izabrati tako da SVAKI belи potez ima gubitak 0, ne samo
    // prvi: gubitak poteza `i` je `scores[i] + scores[i+1]`, pa jedna ocena
    // ulazi u DVA susedna poteza. Niz [0, 0, 200, -200, 400] daje belom 0 i 0,
    // a crnom 200 i 200.
    let analysis = GameAnalysis.build(
        notations: ["e4", "a5", "Sf3", "b5"],
        scores: [.cp(0), .cp(0), .cp(200), .cp(-200), .cp(400)],
        engineBestMatched: [false, false, false, false]
    )
    #expect(analysis.moves[0].cpLoss == 0)    // beli
    #expect(analysis.moves[1].cpLoss == 200)  // crni
    #expect(analysis.moves[2].cpLoss == 0)    // beli
    #expect(analysis.moves[3].cpLoss == 200)  // crni
    #expect(analysis.whiteAccuracy == 100)
    #expect(analysis.whiteAccuracy > analysis.blackAccuracy)
}

@Test func turningPointIsTheBiggestLossButOnlyIfItActuallyHurts() {
    let clean = GameAnalysis.build(
        notations: ["e4", "e5"],
        scores: [.cp(0), .cp(-5), .cp(5)],
        engineBestMatched: [false, false]
    )
    // Najveci gubitak je ~5 centipiona — to nije prelomni potez ni u jednoj partiji.
    #expect(clean.turningPoint == nil)

    let withBlunder = GameAnalysis.build(
        notations: ["e4", "e5", "Lc4"],
        scores: [.cp(0), .cp(0), .cp(0), .cp(400)],
        engineBestMatched: [false, false, false]
    )
    #expect(withBlunder.turningPoint?.notation == "Lc4")
    #expect(withBlunder.turningPoint?.cpLoss == 400)
}

@Test func buildRejectsMismatchedInputLengthsInsteadOfCrashing() {
    // N poteza trazi tacno N+1 ocena. Neslaganje je greska pozivaoca i mora
    // da vrati praznu analizu, ne da srusi proces indeksiranjem van granica.
    let bad = GameAnalysis.build(
        notations: ["e4", "e5"],
        scores: [.cp(0), .cp(0)],
        engineBestMatched: [false, false]
    )
    #expect(bad.moves.isEmpty)
    #expect(bad.turningPoint == nil)
}

@Test func emptyGameProducesEmptyAnalysisWithFullAccuracy() {
    let empty = GameAnalysis.build(notations: [], scores: [.cp(0)], engineBestMatched: [])
    #expect(empty.moves.isEmpty)
    #expect(empty.whiteAccuracy == 100)
    #expect(empty.blackAccuracy == 100)
}
```

- [ ] **Step 2: Pokrenuti testove i videti da padaju**

Run: `swift test --filter MoveAnalysis`
Expected: FAIL sa greškom kompajliranja `cannot find 'EngineScore' in scope` (tipovi još ne postoje). To je očekivan oblik pada za prvi test u nizu.

- [ ] **Step 3: Napisati `Chessko/Models/MoveAnalysis.swift`**

```swift
import Foundation

// MARK: - Analiza partije: model i matematika
//
// Ovaj fajl NE zna za Stockfish ni za SwiftUI. Sve sto radi je racunica nad
// ocenama koje mu neko drugi doda, pa se u celosti testira u SwiftPM paketu.
// Motor je zamenljiv; ova pravila nisu.

/// Ocena pozicije iz ugla strane koja je NA POTEZU.
enum EngineScore: Equatable, Sendable {
    case cp(Int)
    /// Broj poteza do mata. Pozitivno = strana na potezu matira.
    case mate(Int)

    /// Jedinstvena centipionska vrednost za poredjenje.
    ///
    /// Mat nema centipionsku ocenu, a cela racunica ispod je centipionska, pa
    /// se mora preslikati. Uslov je samo da mat bude (a) daleko iznad svake
    /// realne centipionske ocene i (b) monoton — mat u 1 bolji od mata u 2.
    var centipawns: Int {
        switch self {
        case .cp(let v):
            return v
        case .mate(let n):
            let distance = min(abs(n), 99)
            return n > 0 ? (10_000 - distance) : (-10_000 + distance)
        }
    }
}

/// Klasa odigranog poteza. Pragovi su iz spec-a 5.5 i ne smeju se menjati
/// bez izmene spec-a — prikazuju se korisniku kao ocena njegove igre.
enum MoveClass: String, CaseIterable, Sendable {
    case best        // odigran je potez motora
    case excellent   // < 20
    case good        // < 50
    case inaccuracy  // < 100
    case mistake     // < 300
    case blunder     // >= 300

    static func classify(cpLoss: Int, playedEngineBest: Bool) -> MoveClass {
        // Potez motora je po definiciji najbolji. Provera ide PRVA jer merenje
        // ume da da mali gubitak i za potez motora: pozicija pre poteza i
        // pozicija posle njega pretrazuju se nezavisno, na istoj dubini ali iz
        // razlicitih cvorova, pa se ocene ne poklope do centipiona.
        if playedEngineBest { return .best }
        switch cpLoss {
        case ..<20:  return .excellent
        case ..<50:  return .good
        case ..<100: return .inaccuracy
        case ..<300: return .mistake
        default:     return .blunder
        }
    }
}

struct AnalyzedMove: Sendable, Identifiable, Equatable {
    /// Redni broj poluteza, od 0. `ply 0` je prvi belи potez.
    let ply: Int
    let notation: String
    let byWhite: Bool
    let cpLoss: Int
    let moveClass: MoveClass
    let scoreBefore: EngineScore
    let scoreAfter: EngineScore

    var id: Int { ply }

    /// Broj poteza kako ga korisnik broji (1, 1, 2, 2, 3, …).
    var moveNumber: Int { ply / 2 + 1 }

    /// „17.Lc4" ili „17…Lc4" — oblik iz spec-a za prelomni potez.
    var displayNotation: String {
        byWhite ? "\(moveNumber).\(notation)" : "\(moveNumber)…\(notation)"
    }
}

struct GameAnalysis: Sendable, Equatable {
    let moves: [AnalyzedMove]
    let whiteAccuracy: Double
    let blackAccuracy: Double

    /// Ispod ovoga se potez ne proglasava prelomnim. U cisto odigranoj partiji
    /// najveci gubitak ume da bude 12 centipiona; izdvojiti ga kao „prelomni"
    /// bilo bi lazno dramatizovanje.
    static let turningPointMinLoss = 100

    /// Gornja granica gubitka po potezu.
    ///
    /// Propusten mat daje razliku od ~20.000 centipiona. Bez ove granice bi
    /// jedan takav potez sam odredio prosek cele partije i tacnost bi pala na
    /// ~0 iako je ostatak partije bio solidan. 1000 je trostruko iznad praga
    /// za promasaj (300), pa ne sakriva nijednu gresku.
    static let maxCpLoss = 1000

    var turningPoint: AnalyzedMove? {
        guard let worst = moves.max(by: { $0.cpLoss < $1.cpLoss }),
              worst.cpLoss >= Self.turningPointMinLoss else { return nil }
        return worst
    }

    /// Gubitak u centipionima za jedan potez.
    ///
    /// `before` je ocena pozicije PRE poteza, iz ugla igraca koji vuce.
    /// `after` je ocena pozicije POSLE poteza — a tada je na potezu PROTIVNIK,
    /// pa je i ocena iz njegovog ugla. Vrednost iz ugla igraca koji je vukao
    /// je `-after`, i gubitak je `before - (-after)` = `before + after`.
    /// Sabiranje ovde nije greska nego posledica okretanja perspektive.
    static func cpLoss(before: EngineScore, after: EngineScore) -> Int {
        let raw = before.centipawns + after.centipawns
        return max(0, min(maxCpLoss, raw))
    }

    /// Lichess formula iz spec-a 5.5, ogranicena na 0…100.
    static func accuracy(avgCpLoss: Double) -> Double {
        let raw = 103.1668 * exp(-0.04354 * avgCpLoss) - 3.1669
        return min(100, max(0, raw))
    }

    /// Sklapa analizu iz N notacija i N+1 ocena.
    ///
    /// `scores[i]` je ocena pozicije PRE poteza `i`; `scores[i+1]` je ocena
    /// pozicije posle njega. Zato je svaka pozicija pretrazena tacno jednom, a
    /// ne dvaput — partija od 40 poteza trazi 81 pretragu, ne 160.
    static func build(
        notations: [String],
        scores: [EngineScore],
        engineBestMatched: [Bool]
    ) -> GameAnalysis {
        // Neslaganje duzina je greska pozivaoca. Vracamo praznu analizu umesto
        // da indeksiramo van granica — pad ovde bi srusio aplikaciju posle
        // partije, u trenutku kad korisnik nista nije ni trazio osim rezultata.
        guard scores.count == notations.count + 1,
              engineBestMatched.count == notations.count,
              !notations.isEmpty else {
            return GameAnalysis(moves: [], whiteAccuracy: 100, blackAccuracy: 100)
        }

        var moves: [AnalyzedMove] = []
        moves.reserveCapacity(notations.count)
        for i in notations.indices {
            let before = scores[i]
            let after = scores[i + 1]
            let loss = cpLoss(before: before, after: after)
            moves.append(AnalyzedMove(
                ply: i,
                notation: notations[i],
                byWhite: i % 2 == 0,
                cpLoss: loss,
                moveClass: MoveClass.classify(cpLoss: loss, playedEngineBest: engineBestMatched[i]),
                scoreBefore: before,
                scoreAfter: after
            ))
        }

        func accuracyFor(white: Bool) -> Double {
            let side = moves.filter { $0.byWhite == white }
            guard !side.isEmpty else { return 100 }
            let avg = Double(side.reduce(0) { $0 + $1.cpLoss }) / Double(side.count)
            return accuracy(avgCpLoss: avg)
        }

        return GameAnalysis(
            moves: moves,
            whiteAccuracy: accuracyFor(white: true),
            blackAccuracy: accuracyFor(white: false)
        )
    }
}
```

> **Napomena za implementatora:** u komentaru iznad `ply` gore stoji ćirilično „и" u reči „belи" — ispravi u latinično `beli`. Ovo je namerno ostavljeno kao provera da čitaš kod koji prepisuješ, ne da ga slepo kopiraš.

- [ ] **Step 4: Dodati fajl u `Package.swift`**

U `sources:` listu, posle `"Models/Curriculum.swift",` dodati:

```swift
                "Models/MoveAnalysis.swift",
```

- [ ] **Step 5: Pokrenuti testove i videti da prolaze**

Run: `swift test --filter MoveAnalysis`
Expected: PASS, 15 testova.

Zatim ceo skup: `swift test`
Expected: **73/73** (58 zatečenih + 15 novih).

- [ ] **Step 6: Dodati fajl u `project.pbxproj` i DOKAZATI da se kompajlira u aplikaciju**

Ovo nije formalnost. Isti propust je u projektu napravljen dvaput i oba puta su i `swift test` i `xcodebuild` bili zeleni dok aplikacija fajl **nikad nije kompajlirala** — jer ga nije ni bilo u target-u.

Obrazac je četiri linije sa ručno izmišljenim, jedinstvenim ID-jem (ugledaj se na `PromotionOverlay.swift`, ID `10CA7A1000000000000000Z2`). Koristi `10CA7A1000000000000000A1`/`A2`:

```
# 1) u PBXBuildFile sekciju (oko linije 41):
		10CA7A1000000000000000A1 /* MoveAnalysis.swift in Sources */ = {isa = PBXBuildFile; fileRef = 10CA7A1000000000000000A2 /* MoveAnalysis.swift */; };
# 2) u PBXFileReference sekciju (oko linije 88):
		10CA7A1000000000000000A2 /* MoveAnalysis.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = MoveAnalysis.swift; sourceTree = "<group>"; };
# 3) u children listu Models grupe:
			10CA7A1000000000000000A2 /* MoveAnalysis.swift */,
# 4) u PBXSourcesBuildPhase files listu:
				10CA7A1000000000000000A1 /* MoveAnalysis.swift in Sources */,
```

Dokaz da je fajl stvarno u target-u — namerno ubaci sintaksnu grešku i vidi da build **padne**:

```bash
cp Chessko/Models/MoveAnalysis.swift /tmp/MoveAnalysis.swift.bak
echo "this is not swift ###" >> Chessko/Models/MoveAnalysis.swift
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build 2>&1 | tail -5
# OCEKIVANO: BUILD FAILED sa greskom BAS u MoveAnalysis.swift.
# Ako je BUILD SUCCEEDED, fajl NIJE u target-u i pbxproj izmena nije uspela.
cp /tmp/MoveAnalysis.swift.bak Chessko/Models/MoveAnalysis.swift
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build 2>&1 | tail -3
# OCEKIVANO: ** BUILD SUCCEEDED **
```

`cp`, ne `git checkout` — fajl je nov i još nije u git-u, pa ga `git checkout` ne može vratiti.

- [ ] **Step 7: Commit**

```bash
git add Chessko/Models/MoveAnalysis.swift Tests/ChesskoEngineTests/MoveAnalysisTests.swift Package.swift Chessko.xcodeproj/project.pbxproj
git commit -m "Faza 5, Task 1: model i matematika analize partije"
```

---

## Task 2: Čitanje ocene iz UCI izlaza

`StockfishBridge` danas iz motorovog izlaza čita **samo** `<bestmove>` liniju i baca sve ostalo. Ocena pozicije stiže u `info` linijama, koje se trenutno preskaču. Ovaj task uvodi parser kao čistu funkciju nad stringom, pa je testabilan bez pokretanja motora.

**Files:**
- Create: `Chessko/Logic/UCIScoreParser.swift`
- Modify: `Tests/ChesskoEngineTests/MoveAnalysisTests.swift` (dodati testove na kraj)
- Modify: `Package.swift`
- Modify: `Chessko.xcodeproj/project.pbxproj`

**Interfaces:**
- Consumes: `EngineScore` iz Task-a 1.
- Produces: `enum UCIScoreParser { static func score(from line: String) -> EngineScore? }`

**Oblik ulaza.** `ChessKitEngine` vraća `response.rawValue` u tagovanom formatu — isti onaj zbog kog `bestMove` gleda prefiks `<bestmove>`. Za `info` linije oblik je:

```
<info> <depth> 12 <seldepth> 18 <score> cp 34 <nodes> 120000 <pv> e2e4 e7e5
<info> <depth> 12 <score> mate 3 <nodes> 9000 <pv> d1h5
```

Parser mora da radi i ako se redosled tagova promeni, zato traži token `<score>` i čita **dva** tokena posle njega.

- [ ] **Step 1: Napisati testove koji padaju**

Dodati na kraj `Tests/ChesskoEngineTests/MoveAnalysisTests.swift`:

```swift
// MARK: - UCI parser ocene

@Test func parsesCentipawnScoreFromInfoLine() {
    let line = "<info> <depth> 12 <seldepth> 18 <score> cp 34 <nodes> 120000 <pv> e2e4"
    #expect(UCIScoreParser.score(from: line) == .cp(34))
}

@Test func parsesNegativeCentipawnScore() {
    #expect(UCIScoreParser.score(from: "<info> <score> cp -250 <pv> e2e4") == .cp(-250))
}

@Test func parsesMateScoreInBothDirections() {
    #expect(UCIScoreParser.score(from: "<info> <score> mate 3 <pv> d1h5") == .mate(3))
    #expect(UCIScoreParser.score(from: "<info> <score> mate -2 <pv> d1h5") == .mate(-2))
}

@Test func returnsNilForLinesWithoutScore() {
    #expect(UCIScoreParser.score(from: "<bestmove> e2e4 <ponder> e7e5") == nil)
    #expect(UCIScoreParser.score(from: "<info> <depth> 12 <nodes> 4000") == nil)
    #expect(UCIScoreParser.score(from: "") == nil)
}

@Test func returnsNilWhenScoreTagIsTruncated() {
    // Motor je prekinut usred linije. Bolje nista nego pogresna ocena.
    #expect(UCIScoreParser.score(from: "<info> <score>") == nil)
    #expect(UCIScoreParser.score(from: "<info> <score> cp") == nil)
    #expect(UCIScoreParser.score(from: "<info> <score> cp abc") == nil)
}

@Test func ignoresLowerboundAndUpperboundQualifiers() {
    // Stockfish uz ocenu ume da doda "lowerbound"/"upperbound" kad je vrednost
    // samo granica, ne tacna ocena. Sama vrednost je i dalje upotrebljiva.
    #expect(UCIScoreParser.score(from: "<info> <score> cp 34 lowerbound") == .cp(34))
}
```

- [ ] **Step 2: Pokrenuti testove i videti da padaju**

Run: `swift test --filter UCIScore`
Expected: FAIL, `cannot find 'UCIScoreParser' in scope`.

- [ ] **Step 3: Napisati `Chessko/Logic/UCIScoreParser.swift`**

```swift
import Foundation

// MARK: - UCI parser ocene
//
// Odvojen od `StockfishBridge`-a namerno: `StockfishBridge` je `actor` koji
// uvozi ChessKitEngine i ne moze u SwiftPM testni paket, a citanje ocene iz
// linije teksta je cista funkcija koja se mora testirati. Granica je tacno na
// mestu gde prestaje rad sa motorom, a pocinje rad sa stringom.

enum UCIScoreParser {

    /// Cita ocenu iz jedne UCI `info` linije, u tagovanom formatu koji vraca
    /// `ChessKitEngine` (`<info> <score> cp 34 …`).
    ///
    /// Trazi token `<score>` i cita DVA tokena posle njega, umesto da se
    /// oslanja na fiksnu poziciju — redosled tagova nije garantovan i menja se
    /// izmedju verzija motora.
    static func score(from line: String) -> EngineScore? {
        let tokens = line.split(separator: " ")
        guard let idx = tokens.firstIndex(of: "<score>"),
              idx + 2 < tokens.count else { return nil }

        let kind = tokens[idx + 1]
        guard let value = Int(tokens[idx + 2]) else { return nil }

        switch kind {
        case "cp":   return .cp(value)
        case "mate": return .mate(value)
        default:     return nil
        }
    }
}
```

- [ ] **Step 4: Dodati u `Package.swift` i `project.pbxproj`**

U `Package.swift` `sources:`, posle `"Logic/PuzzleRepository.swift",`:

```swift
                "Logic/UCIScoreParser.swift",
```

U `project.pbxproj` isti četvorolinijski obrazac kao u Task-u 1, sa ID-jevima `10CA7A1000000000000000B1`/`B2`, `path = UCIScoreParser.swift`, u `Logic` grupu.

- [ ] **Step 5: Pokrenuti testove**

Run: `swift test`
Expected: **79/79** (73 + 6 novih).

- [ ] **Step 6: Dokazati da je i ovaj fajl u target-u**

Isti postupak sa sintaksnom greškom kao u Task-u 1, Step 6, nad `Chessko/Logic/UCIScoreParser.swift`.

- [ ] **Step 7: Commit**

```bash
git add Chessko/Logic/UCIScoreParser.swift Tests/ChesskoEngineTests/MoveAnalysisTests.swift Package.swift Chessko.xcodeproj/project.pbxproj
git commit -m "Faza 5, Task 2: citanje ocene pozicije iz UCI izlaza"
```

---

## Task 3: Batch analiza u `StockfishBridge` — i merenje koje odlučuje njen oblik

**Ovo je task sa najvećim rizikom u celoj fazi.** Pročitaj ceo uvod pre nego što napišeš ijednu liniju.

**Files:**
- Modify: `Chessko/Logic/StockfishBridge.swift`

**Interfaces:**
- Consumes: `EngineScore` (Task 1), `UCIScoreParser` (Task 2), postojeći `parseUCI` i `GameState.fen`.
- Produces:
  - `struct PositionEval: Sendable { let score: EngineScore; let bestMove: ChessMove? }`
  - `func StockfishBridge.evaluate(state: GameState, depth: Int = 12) async -> PositionEval?`
  - `func StockfishBridge.analyzeGame(states: [GameState], depth: Int = 12, onProgress: @Sendable (Int, Int) -> Void) async -> [PositionEval]?`

### Zatečeno stanje koje moraš razumeti pre nego što kreneš

`bestMove(for:depth:)` pravi **nov `Engine` za svaki poziv**. To nije nepažnja nego posledica dijagnoze od 2026-06-27, zapisane u `CLAUDE.md`:

> `responseStream` u ChessKitEngine nije pouzdan za višestruku upotrebu — stream/continuation se ne oporavlja posle prve pretrage. Rešenje: svaki `bestMove` poziv kreira SOPSTVENI `Engine`.

Za jedan potez to je prihvatljivo. Za analizu partije od 40 poteza treba **81 pretraga**, a to znači 81 pokretanje motora, svako sa učitavanjem NNUE mreže i čekanjem na spremnost do 5 sekundi. Ako je pokretanje skupo ~1s, analiza traje minut i po pre nego što je odigran ijedan potez pretrage.

Otud: **prvo meri, pa onda biraj.** Naivna „popravka" (jedan motor za sve pozicije) je tačno ono što je već jednom utvrđeno kao pokvareno; ali utvrđeno je pre više meseci, sa `go depth` posle `UCI_LimitStrength`, u drugačijem toku. Ne pretpostavljaj ni da radi ni da ne radi.

- [ ] **Step 1: Izmeriti oba pristupa, u pokrenutoj aplikaciji**

Merenje mora da bude u aplikaciji — `ChessKitEngine` je SPM zavisnost Xcode target-a i ne kompajlira se u SwiftPM testni paket.

Privremeno dodaj u `ChesskoApp.swift` (ili u `.task` modifikator na `ContentView`) poziv mernog bloka koji ispisuje u konzolu, pokreni na simulatoru i pročitaj izlaz. Meri **20 pozicija** (dovoljno da se vidi trend, dovoljno kratko da se čeka):

```swift
// PRIVREMENO — merenje za Fazu 5, Task 3. Obrisati pre commit-a.
func measureAnalysisApproaches() async {
    var states: [GameState] = [.initial()]
    // 20 pozicija iz stvarne partije: pusti AI da odigra sam protiv sebe na
    // maloj dubini, ili prosto primeni niz legalnih poteza.
    var s = GameState.initial()
    for _ in 0..<20 {
        guard let m = MoveGenerator.legalMoves(for: s.currentTurn, in: s).first else { break }
        s = s.applying(m)
        states.append(s)
    }

    let bridge = StockfishBridge()
    await bridge.start()

    let t0 = Date()
    for (i, st) in states.enumerated() {
        let r = await bridge.evaluate(state: st, depth: 12)
        print("[MERENJE] svez motor, pozicija \(i): \(r == nil ? "NIL" : "ok") \(Date().timeIntervalSince(t0))s")
    }
    print("[MERENJE] UKUPNO svez motor po poziciji: \(Date().timeIntervalSince(t0))s")
}
```

Zatim isto merenje sa **jednim** motorom koji ostaje živ kroz svih 20 pretraga.

Zapiši u izveštaj **tri broja**: ukupno vreme za svež motor po poziciji, ukupno vreme za jedan deljeni motor, i **koliko je pozicija vratilo ocenu** u drugom slučaju. Treći broj je ono što odlučuje: ako deljeni motor posle prve pretrage počne da vraća `nil`, obrazac iz 2026-06-27 i dalje važi i pristup otpada bez obzira na brzinu.

- [ ] **Step 2: Odabrati pristup i zapisati zašto**

Pravilo odluke, bez prostora za improvizaciju:

- Ako deljeni motor vrati ocenu za **svih 20** pozicija i brži je → koristi deljeni motor, sa `defer` koji ga gasi.
- Ako deljeni motor vrati ocenu za **manje od 20** pozicija → koristi svež motor po poziciji, ma koliko sporije bilo. Netačna analiza je gora od spore.

U oba slučaja upiši izmerene brojeve kao komentar iznad `analyzeGame` — sledeći čitalac mora da vidi da je odluka merena, ne pretpostavljena.

- [ ] **Step 3: Napisati `evaluate` i `analyzeGame`**

Dodati u `StockfishBridge` (iznad `parseUCI`):

```swift
    // MARK: - Analiza pozicije

    struct PositionEval: Sendable {
        /// Ocena iz ugla strane koja je na potezu u toj poziciji.
        let score: EngineScore
        /// Potez koji motor smatra najboljim; `nil` u zavrsnoj poziciji.
        let bestMove: ChessMove?
    }

    /// Ocena jedne pozicije. Za razliku od `bestMove(for:depth:)`, cita i
    /// `info` linije da bi izvukla ocenu, ne samo `<bestmove>`.
    ///
    /// Uzima se POSLEDNJA vidjena ocena pre `<bestmove>`, jer motor tokom
    /// produbljivanja salje ocenu za svaku dubinu — a zanima nas ona sa pune
    /// dubine, ne prva koju je prijavio.
    func evaluate(state: GameState, depth: Int = 12) async -> PositionEval? {
        let eng = Engine(type: .stockfish, loggingEnabled: false)
        await eng.start()

        var waited = 0
        while !(await eng.isRunning), waited < 50 {
            try? await Task.sleep(for: .milliseconds(100))
            waited += 1
        }
        guard await eng.isRunning else { return nil }

        let evalFile      = nnueBig ?? nnueSmall
        let evalFileSmall = nnueSmall ?? nnueBig
        if let url = evalFile      { await eng.send(command: .setoption(id: "EvalFile",      value: url.path())) }
        if let url = evalFileSmall { await eng.send(command: .setoption(id: "EvalFileSmall", value: url.path())) }

        guard let stream = await eng.responseStream else { return nil }

        await eng.send(command: .position(.fen(state.fen)))
        await eng.send(command: .go(depth: depth))

        var lastScore: EngineScore?
        for await response in stream {
            let raw = response.rawValue
            if let s = UCIScoreParser.score(from: raw) {
                lastScore = s
                continue
            }
            guard raw.hasPrefix("<bestmove>") else { continue }
            let tokens = raw.split(separator: " ")
            let uci = tokens.count >= 2 ? String(tokens[1]) : "(none)"
            let move = uci == "(none)" ? nil : parseUCI(uci, in: state)
            // Bez ijedne ocene nema sta da se vrati — pozicija bez ocene bi u
            // racunici prosla kao cp(0), sto je tvrdnja da je izjednaceno.
            guard let score = lastScore else { return nil }
            return PositionEval(score: score, bestMove: move)
        }
        return nil
    }

    /// Ocena niza pozicija, redom. `onProgress(gotovo, ukupno)` se zove posle
    /// svake pozicije da ekran moze da prikaze napredak.
    ///
    /// Vraca `nil` ako ijedna pozicija ne uspe — delimicna analiza bi prikazala
    /// tacnost izracunatu iz dela partije, a korisnik bi je citao kao da vazi
    /// za celu.
    ///
    /// Otkazivanje: proverava `Task.isCancelled` pre svake pozicije, pa
    /// napustanje ekrana ne ostavlja motor da melje u pozadini.
    func analyzeGame(
        states: [GameState],
        depth: Int = 12,
        onProgress: @Sendable (Int, Int) -> Void
    ) async -> [PositionEval]? {
        var out: [PositionEval] = []
        out.reserveCapacity(states.count)
        for (i, state) in states.enumerated() {
            if Task.isCancelled { return nil }
            guard let e = await evaluate(state: state, depth: depth) else { return nil }
            out.append(e)
            onProgress(i + 1, states.count)
        }
        return out
    }
```

> Ako je merenje u Step-u 1 pokazalo da deljeni motor radi, `analyzeGame` umesto petlje nad `evaluate` drži jedan `Engine` i šalje `position`/`go` po pozicijama; `evaluate` u tom slučaju ostaje kao samostalna metoda za jednu poziciju. Struktura povratne vrednosti i potpis se **ne menjaju** — Taskovi 4–6 zavise od njih.

- [ ] **Step 4: Ukloniti merni kod i potvrditi čisto stablo**

```bash
git status --short   # OCEKIVANO: samo StockfishBridge.swift
```

Merni blok iz Step-a 1 **ne sme** ostati u `ChesskoApp.swift`.

- [ ] **Step 5: Build i testovi**

```bash
swift test          # OCEKIVANO: 79/79 (StockfishBridge nije u paketu, broj se ne menja)
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build   # ** BUILD SUCCEEDED **
```

- [ ] **Step 6: Commit**

```bash
git add Chessko/Logic/StockfishBridge.swift
git commit -m "Faza 5, Task 3: batch analiza pozicija u StockfishBridge"
```

---

## Task 4: `AnalysisViewModel` — pokretanje, napredak, otkazivanje

**Files:**
- Create: `Chessko/ViewModels/AnalysisViewModel.swift`
- Modify: `Chessko.xcodeproj/project.pbxproj`

**Interfaces:**
- Consumes: `GameAnalysis.build(notations:scores:engineBestMatched:)` (Task 1), `StockfishBridge.analyzeGame(states:depth:onProgress:)` i `PositionEval` (Task 3), `GameViewModel.allHistoryStates` i `gameState.moveNotations` (zatečeno).
- Produces:
  - `@Observable @MainActor final class AnalysisViewModel`
  - `AnalysisViewModel.Phase: Equatable` — `.idle`, `.running(done: Int, total: Int)`, `.done(GameAnalysis)`, `.failed(String)` (ugnjezden tip, ne samostalan)
  - `var phase: AnalysisViewModel.Phase`
  - `func start(states: [(state: GameState, lastMove: ChessMove?)], notations: [String])`
  - `func cancel()`

**Kako se dobija `engineBestMatched`.** `PositionEval.bestMove` za poziciju `i` je potez koji motor smatra najboljim iz te pozicije. Odigran potez je `states[i+1].lastMove`. Poklapanje se poredi po `from`/`to`/`flag` — `ChessMove ==` od 2026-06-25 poredi sva tri, pa je `==` ovde tačan i dovoljan.

- [ ] **Step 1: Napisati `Chessko/ViewModels/AnalysisViewModel.swift`**

```swift
import Foundation
import Observation

// MARK: - Analysis View Model
//
// Pokrece analizu partije i drzi njen tok. Sva racunica je u `GameAnalysis`
// (Foundation-only, testirano); ovde je samo orkestracija: sta se salje motoru,
// kako se prijavljuje napredak i sta se desava kad korisnik ode sa ekrana.

@Observable
@MainActor
final class AnalysisViewModel {

    enum Phase: Equatable {
        case idle
        case running(done: Int, total: Int)
        case done(GameAnalysis)
        case failed(String)
    }

    private(set) var phase: Phase = .idle

    private let stockfish = StockfishBridge()
    private var task: Task<Void, Never>?

    /// Dubina je fiksna po spec-u 5.5 — analiza mora da traje predvidivo.
    private static let depth = 12

    var isRunning: Bool {
        if case .running = phase { return true }
        return false
    }

    /// `states` je `GameViewModel.allHistoryStates`: N+1 pozicija za N poteza,
    /// pocev od pocetne. `notations` je `gameState.moveNotations`, duzine N.
    func start(states: [(state: GameState, lastMove: ChessMove?)], notations: [String]) {
        guard !isRunning else { return }

        // Neslaganje duzina nije korisnikov problem i ne sme da srusi ekran.
        guard states.count == notations.count + 1, !notations.isEmpty else {
            phase = .failed(Loc("Nema dovoljno poteza za analizu."))
            return
        }
        guard stockfish.isAvailable else {
            phase = .failed(Loc("Analiza nije dostupna — motor nije pronađen."))
            return
        }

        phase = .running(done: 0, total: states.count)
        let positions = states.map(\.state)
        let playedMoves = states.dropFirst().map(\.lastMove)

        task = Task { [weak self] in
            guard let self else { return }
            let evals = await self.stockfish.analyzeGame(
                states: positions,
                depth: Self.depth
            ) { done, total in
                Task { @MainActor [weak self] in
                    guard let self, self.isRunning else { return }
                    self.phase = .running(done: done, total: total)
                }
            }

            guard !Task.isCancelled else { return }
            guard let evals, evals.count == positions.count else {
                self.phase = .failed(Loc("Analiza nije uspela."))
                return
            }

            // Potez motora iz pozicije `i` prema stvarno odigranom potezu.
            // `ChessMove ==` poredi from+to+flag, pa je poredjenje tacno i za
            // promociju i za rokadu.
            let matched: [Bool] = playedMoves.enumerated().map { i, played in
                guard let played, let best = evals[i].bestMove else { return false }
                return played == best
            }

            self.phase = .done(GameAnalysis.build(
                notations: notations,
                scores: evals.map(\.score),
                engineBestMatched: matched
            ))
        }
    }

    /// Zove se kad korisnik napusti ekran. Bez ovoga motor nastavi da melje
    /// 81 poziciju u pozadini iako rezultat vise nema ko da vidi.
    func cancel() {
        task?.cancel()
        task = nil
        if isRunning { phase = .idle }
    }

    deinit { task?.cancel() }
}
```

- [ ] **Step 2: Dodati u `project.pbxproj`**

Isti četvorolinijski obrazac, ID-jevi `10CA7A1000000000000000C1`/`C2`, `path = AnalysisViewModel.swift`, u `ViewModels` grupu.

**Ne** dodavati u `Package.swift` — fajl uvozi `Observation` i zove `Loc(...)`, koji je iza SwiftUI-ja.

- [ ] **Step 3: Build i dokaz da je fajl u target-u**

Isti postupak sa sintaksnom greškom kao u Task-u 1, Step 6.

```bash
swift test   # OCEKIVANO: 79/79, nepromenjeno
```

- [ ] **Step 4: Commit**

```bash
git add Chessko/ViewModels/AnalysisViewModel.swift Chessko.xcodeproj/project.pbxproj
git commit -m "Faza 5, Task 4: AnalysisViewModel — pokretanje, napredak, otkazivanje"
```

---

## Task 5: Ekran analize

**Files:**
- Create: `Chessko/Views/AnalysisView.swift`
- Modify: `Chessko/Views/GameView.swift` (dugme „Analiziraj partiju")
- Modify: `build_localizations.py` (novi ključevi)
- Modify: `Chessko/Localizable.xcstrings` (regenerisan)
- Modify: `Chessko.xcodeproj/project.pbxproj`

**Interfaces:**
- Consumes: `AnalysisViewModel` (Task 4), `GameAnalysis`/`AnalyzedMove`/`MoveClass` (Task 1), `GameViewModel.goToMove(_:)` (zatečeno).
- Produces: `struct AnalysisView: View`, `init(viewModel: GameViewModel)`.

**Boje klasa — kroz `DS` tokene, bez novih literala:**

| Klasa | Token | Srpski naziv |
|---|---|---|
| `best` | `DS.accent` | „najbolji" |
| `excellent` | `DS.success` | „odličan" |
| `good` | `DS.success` | „dobar" |
| `inaccuracy` | `DS.warning` | „netačnost" |
| `mistake` | `DS.warning` | „greška" |
| `blunder` | `DS.danger` | „promašaj" |

`best` i `excellent` se razlikuju bojom; `excellent` i `good` dele boju ali ne oznaku. `DS.accent` na bedžu nosi tekst `DS.onAccent`, nikad `DS.onScrim`.

- [ ] **Step 1: Dodati nove ključeve u `build_localizations.py`**

Pored postojećih poziva dodati sledeće — svih 14 ključeva, svih 8 jezika, ništa se ne dopisuje naknadno:

`add` je pozicioni sa tačno osam argumenata — `add(sr, en, fr, de, it, ru, zh, hi)`. Dodati kao nov blok na kraj, sa komentarom-zaglavljem kao ostali blokovi u skripti:

```python
# ── Analiza partije (Faza 5) ────────────────────────────────────────────────
add("Analiza partije", "Game analysis", "Analyse de la partie", "Partieanalyse", "Analisi della partita", "Анализ партии", "对局分析", "गेम विश्लेषण")
add("Analiziraj partiju", "Analyse game", "Analyser la partie", "Partie analysieren", "Analizza la partita", "Анализировать партию", "分析对局", "गेम का विश्लेषण करें")
add("Analiziram…", "Analysing…", "Analyse en cours…", "Analysiere…", "Analisi in corso…", "Анализирую…", "分析中…", "विश्लेषण जारी…")
add("Tačnost", "Accuracy", "Précision", "Genauigkeit", "Precisione", "Точность", "准确率", "सटीकता")
add("Prelomni potez", "Turning point", "Coup décisif", "Wendepunkt", "Mossa decisiva", "Переломный ход", "转折点", "निर्णायक चाल")
add("najbolji", "best", "meilleur", "bester", "migliore", "лучший", "最佳", "सर्वोत्तम")
add("odličan", "excellent", "excellent", "ausgezeichnet", "eccellente", "отличный", "优秀", "उत्कृष्ट")
add("dobar", "good", "bon", "gut", "buono", "хороший", "良好", "अच्छा")
add("netačnost", "inaccuracy", "imprécision", "Ungenauigkeit", "imprecisione", "неточность", "不精确", "अशुद्धि")
add("greška", "mistake", "erreur", "Fehler", "errore", "ошибка", "失误", "गलती")
add("promašaj", "blunder", "gaffe", "Patzer", "errore grave", "грубая ошибка", "严重失误", "भारी भूल")
add("Analiza nije uspela.", "Analysis failed.", "L\u2019analyse a échoué.", "Analyse fehlgeschlagen.", "Analisi non riuscita.", "Анализ не удался.", "分析失败。", "विश्लेषण विफल रहा।")
add("Analiza nije dostupna — motor nije pronađen.", "Analysis unavailable — engine not found.", "Analyse indisponible — moteur introuvable.", "Analyse nicht verfügbar — Engine nicht gefunden.", "Analisi non disponibile — motore non trovato.", "Анализ недоступен — движок не найден.", "无法分析 — 未找到引擎。", "विश्लेषण उपलब्ध नहीं — इंजन नहीं मिला।")
add("Nema dovoljno poteza za analizu.", "Not enough moves to analyse.", "Pas assez de coups à analyser.", "Nicht genug Züge für die Analyse.", "Non ci sono abbastanza mosse da analizzare.", "Недостаточно ходов для анализа.", "棋步不足，无法分析。", "विश्लेषण के लिए पर्याप्त चालें नहीं।")
```

Zatim:

```bash
python3 build_localizations.py
python3 -c "
import json; d=json.load(open('Chessko/Localizable.xcstrings'))
ks=d['strings']; print('kljuceva:', len(ks))
bad=[k for k,v in ks.items() if len(v.get('localizations',{}))!=8]
print('bez svih 8 jezika:', bad or 'nema')"
```

Expected: 285 + 14 = **299 ključeva**, nijedan bez svih 8 jezika.
(Provereno pri pisanju plana: nijedan od 14 novih ključeva se ne sudara sa zatečenim ni kao simbol ni doslovno. `Zatvori` već postoji i koristi se, ne dodaje se ponovo.)

> **Oprez sa `STRING_CATALOG_GENERATE_SYMBOLS`:** ključevi koji se razlikuju samo po veličini slova daju isti simbol i **obaraju build**. Ova greška je napravljena dvaput (`beli`/`Beli`, `crni`/`Crni`). Pre commit-a proveri:
> ```bash
> python3 -c "
> import json,collections; d=json.load(open('Chessko/Localizable.xcstrings'))
> c=collections.Counter(k.lower() for k in d['strings'])
> print('kolizije:', [k for k,n in c.items() if n>1] or 'nema')"
> ```

- [ ] **Step 2: Napisati `Chessko/Views/AnalysisView.swift`**

```swift
import SwiftUI

// MARK: - Analysis View
//
// Cist prikaz gotovog `GameAnalysis`-a. Ne racuna nista — sve brojke stizu iz
// `GameAnalysis` (Foundation-only, testirano). Jedino sto ovaj fajl zna, a
// model ne, jeste kako klasa poteza izgleda: boja i naziv.

struct AnalysisView: View {

    let viewModel: GameViewModel
    @State private var analysis = AnalysisViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: DS.Space.l) {
                    switch analysis.phase {
                    case .idle:
                        ProgressView().padding(.top, DS.Space.xl)
                    case .running(let done, let total):
                        runningView(done: done, total: total)
                    case .failed(let message):
                        Text(message)
                            .font(.dsBody)
                            .foregroundStyle(DS.inkMuted)
                            .multilineTextAlignment(.center)
                            .padding(.top, DS.Space.xl)
                    case .done(let result):
                        accuracyRow(result)
                        if let tp = result.turningPoint { turningPointCard(tp) }
                        moveStrip(result)
                    }
                }
                .padding(DS.Space.l)
                .frame(maxWidth: .infinity)
            }
            .background(DS.ground)
            .navigationTitle(Loc("Analiza partije"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(Loc("Zatvori")) { dismiss() }
                }
            }
        }
        .task {
            analysis.start(
                states: viewModel.allHistoryStates,
                notations: viewModel.gameState.moveNotations
            )
        }
        // Analiza od 81 pozicije ne sme da nastavi da melje kad ekran nestane.
        .onDisappear { analysis.cancel() }
    }

    // MARK: - Napredak

    private func runningView(done: Int, total: Int) -> some View {
        VStack(spacing: DS.Space.m) {
            ProgressView(value: Double(done), total: Double(max(total, 1)))
                .tint(DS.accent)
            // Bez `Loc`: goli brojevi se ne prevode, pa bi kljuc bio suvisan.
            Text("\(done) / \(total)")
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
            Text(Loc("Analiziram…"))
                .font(.dsBody)
                .foregroundStyle(DS.ink)
        }
        .padding(.top, DS.Space.xl)
    }

    // MARK: - Tacnost

    private func accuracyRow(_ result: GameAnalysis) -> some View {
        HStack(spacing: DS.Space.l) {
            accuracyBox(title: Loc("beli").capitalized, value: result.whiteAccuracy)
            accuracyBox(title: Loc("crni").capitalized, value: result.blackAccuracy)
        }
    }

    private func accuracyBox(title: String, value: Double) -> some View {
        VStack(spacing: DS.Space.xs) {
            Text(title)
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
            Text(String(format: "%.1f%%", value))
                .font(.dsTitle)
                .foregroundStyle(DS.ink)
            Text(Loc("Tačnost"))
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(DS.Space.m)
        .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.m))
    }

    // MARK: - Prelomni potez

    private func turningPointCard(_ move: AnalyzedMove) -> some View {
        HStack(spacing: DS.Space.m) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(color(for: move.moveClass))
            VStack(alignment: .leading, spacing: 2) {
                Text(Loc("Prelomni potez"))
                    .font(.dsCaption)
                    .foregroundStyle(DS.inkMuted)
                Text("\(move.displayNotation)  (−\(move.cpLoss))")
                    .font(.dsHeading)
                    .foregroundStyle(DS.ink)
            }
            Spacer()
        }
        .padding(DS.Space.m)
        .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.m))
        .onTapGesture { open(move) }
    }

    // MARK: - Traka poteza

    private func moveStrip(_ result: GameAnalysis) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: DS.Space.s)],
                  spacing: DS.Space.s) {
            ForEach(result.moves) { move in
                Button { open(move) } label: {
                    HStack(spacing: DS.Space.xs) {
                        Circle()
                            .fill(color(for: move.moveClass))
                            .frame(width: 8, height: 8)
                        Text(move.displayNotation)
                            .font(.dsMono)
                            .foregroundStyle(DS.ink)
                        Spacer(minLength: 0)
                    }
                    .padding(.vertical, DS.Space.xs)
                    .padding(.horizontal, DS.Space.s)
                    .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.s))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(move.displayNotation), \(label(for: move.moveClass))")
            }
        }
    }

    // MARK: - Klase

    private func color(for c: MoveClass) -> Color {
        switch c {
        case .best:       DS.accent
        case .excellent:  DS.success
        case .good:       DS.success
        case .inaccuracy: DS.warning
        case .mistake:    DS.warning
        case .blunder:    DS.danger
        }
    }

    private func label(for c: MoveClass) -> String {
        switch c {
        case .best:       Loc("najbolji")
        case .excellent:  Loc("odličan")
        case .good:       Loc("dobar")
        case .inaccuracy: Loc("netačnost")
        case .mistake:    Loc("greška")
        case .blunder:    Loc("promašaj")
        }
    }

    /// Otvara poziciju POSLE odigranog poteza u zatecenom review modu.
    /// `allHistoryStates` indeks je `ply + 1` jer je na indeksu 0 pocetna
    /// pozicija, pre ijednog poteza.
    private func open(_ move: AnalyzedMove) {
        viewModel.goToMove(move.ply + 1)
        dismiss()
    }
}
```

- [ ] **Step 3: Dodati dugme u `GameView.swift`**

U `reviewControlsView` (oko linije 261), ili neposredno uz njega, dodati dugme koje se vidi **samo kad je partija gotova**:

```swift
                if viewModel.isGameOver && !viewModel.gameState.moveNotations.isEmpty {
                    Button {
                        showAnalysis = true
                    } label: {
                        Label(Loc("Analiziraj partiju"), systemImage: "chart.bar.doc.horizontal")
                            .font(.dsBody.weight(.semibold))
                            .foregroundStyle(DS.onAccent)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, DS.Space.m)
                            .background(DS.accent, in: RoundedRectangle(cornerRadius: DS.Radius.m))
                    }
                    .buttonStyle(.plain)
                }
```

Uz `@State private var showAnalysis = false` na vrhu `GameView`-a i, na kraju `body`-ja:

```swift
        .sheet(isPresented: $showAnalysis) {
            AnalysisView(viewModel: viewModel)
        }
```

- [ ] **Step 4: `project.pbxproj`, build, dokaz da je fajl u target-u**

Četvorolinijski obrazac, ID-jevi `10CA7A1000000000000000D1`/`D2`, `path = AnalysisView.swift`, u `Views` grupu. Zatim isti dokaz sintaksnom greškom.

- [ ] **Step 5: Vizuelna provera na simulatoru, u obe teme**

Sintetički tapovi u simulatoru **ne rade** — pre nego što pretpostaviš da rade, proveri:

```bash
osascript -e 'tell application "System Events" to count windows of process "Simulator"'
```

Ako vrati `0`, do ekrana se stiže privremenim zakucavanjem korena `ContentView`-a na `AnalysisView` sa unapred sklopljenim `GameViewModel`-om koji ima odigranu partiju. Snimi `xcrun simctl io booted screenshot`.

Snimiti i potvrditi: stanje **napretka** (traka i brojač), stanje **gotove analize** (tačnost oba igrača, prelomni potez, traka poteza sa bar dve različite boje), i stanje **greške**. Sve tri u svetloj i tamnoj temi.

**Proveri u stanju u kome sporni slučaj postoji.** Screenshot partije bez ijednog promašaja ne dokazuje da se `DS.danger` bedž iscrtava — zasej partiju koja ima bar jedan promašaj.

Posle svega vrati privremene izmene i potvrdi `git status --short` prazan.

- [ ] **Step 6: Commit**

```bash
git add Chessko/Views/AnalysisView.swift Chessko/Views/GameView.swift \
        build_localizations.py Chessko/Localizable.xcstrings Chessko.xcodeproj/project.pbxproj
git commit -m "Faza 5, Task 5: ekran analize partije"
```

---

## Task 6: Povratna informacija za `game` korake u Putu

Spec 5.5: „U Putu, korak tipa `game` koristi analizu za povratnu informaciju umesto pukog „pobedio / izgubio"."

**Files:**
- Modify: `Chessko/Views/StepGameView.swift`

**Interfaces:**
- Consumes: `AnalysisView` (Task 5), postojeći `pendingStepId`/`completeStepOnce()` tok.

**Ograničenje koje se NE sme prekršiti.** `StepGameView` završava korak kad `isGameOver` postane `true`, **tačno jednom**, i **bez obzira na ishod** (predaja i poraz završavaju korak isto kao pobeda — spec traži „partija odigrana do kraja", ne pobedu). Analiza je **prikaz**, ne uslov: korak se završava i ako korisnik analizu nikad ne otvori, i ako analiza padne. Ako se završetak koraka veže za analizu, korak postaje nezavršiv kad motor nije dostupan.

- [ ] **Step 1: Dodati prikaz analize posle kraja partije**

U `StepGameView` dodati `@State private var showAnalysis = false`, a u `completeStepOnce()` — **posle** postojećeg poziva koji upisuje završetak koraka — postaviti `showAnalysis = true`. Zatim isti `.sheet` kao u `GameView`-u:

```swift
        .sheet(isPresented: $showAnalysis) {
            if let viewModel {
                AnalysisView(viewModel: viewModel)
            }
        }
```

- [ ] **Step 2: Dokazati da se korak završava i BEZ analize**

Ovo je tvrdnja koja se mora proveriti u pokrenutoj aplikaciji, ne izvesti iz koda. Zasej `progress.json` tako da `game` korak bude otključan, odigraj partiju do kraja (ili je predaj), **zatvori analizu bez čekanja**, pa proveri da korak ima kvačicu:

```bash
CONT=$(xcrun simctl get_app_container booted com.veljkoni.chessko data)
python3 -c "
import json,sys
p='$CONT/Library/Application Support/progress.json'
d=json.load(open(p)); print(json.dumps(d.get('completedSteps'), indent=1))"
```

Expected: id `game` koraka je u listi završenih.

- [ ] **Step 3: Build, testovi, commit**

```bash
swift test    # 79/79
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
git add Chessko/Views/StepGameView.swift
git commit -m "Faza 5, Task 6: analiza kao povratna informacija za game korake Puta"
```

---

## Task 7: Dokumentacija i zatvaranje faze

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Nova sekcija „Analiza partije" u `CLAUDE.md`**

Postaviti je posle sekcije „Put (kurikulum i napredak)". Mora da sadrži, svaku tvrdnju proverenu a ne prepisanu iz ovog plana:

- da je matematika u `Models/MoveAnalysis.swift`, Foundation-only i testirana, a motor u `StockfishBridge`-u;
- **izmerene brojeve iz Task-a 3** — koliko traje analiza partije od 40 poteza na dubini 12, i koji je pristup izabran (svež motor po poziciji ili deljeni) i zašto;
- da je N+1 pretraga, ne 2N, i zašto (`cpLoss` se dobija iz dve susedne ocene);
- da se mat mapira u centipione i da je `cpLoss` ograničen na 1000 — sa razlogom (jedan propušten mat inače proguta prosek cele partije);
- da je prag za prelomni potez 100;
- da `game` korak Puta **ne zavisi** od analize da bi se završio.

- [ ] **Step 2: Dopuniti sekciju „Testovi"**

Broj testova je posle ove faze **79**; dodati `MoveAnalysisTests` u razbijanje po fajlovima.

- [ ] **Step 3: Changelog unos**

Jedan unos za celu Fazu 5, sa datumom, izmerenim vremenima i svakom greškom uhvaćenom usput.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "Faza 5: dokumentacija analize partije"
```

---

## Završna provera faze

- [ ] `swift test` prolazi **79/79**
- [ ] `xcodebuild … build` → `** BUILD SUCCEEDED **`
- [ ] `git status --short` prazan
- [ ] Sva **četiri** nova `.swift` fajla dokazano u target-u (sintaksna greška obara build u svakom)
- [ ] `Localizable.xcstrings` ima 299 ključeva, svaki sa svih 8 jezika, bez case-insensitive kolizija
- [ ] Analiza partije od 40 poteza završava u izmerenom, zapisanom vremenu, sa prikazom napretka (spec: „Gotovo kad")
- [ ] Ekran analize pregledan u **obe teme**, u stanju napretka, gotove analize i greške
- [ ] Traka poteza pokazuje bar dve različite klase u boji — provereno na partiji koja stvarno ima promašaj
- [ ] `game` korak Puta završava se i kad se analiza zatvori bez čekanja

## Rizici

- **Merenje iz Task-a 3 može da obori pretpostavku o brzini.** Ako i deljeni motor daje minute po partiji, spec-ov uslov „završi u razumnom vremenu" nije ispunjen i faza mora da se vrati na odluku o dubini — ali dubina je u spec-u fiksirana na 12, pa je to izmena spec-a, ne tiha izmena plana. Prijaviti, ne improvizovati.
- **`cpLoss` iz dve nezavisne pretrage nije savršeno merenje.** Pozicija pre poteza i pozicija posle njega pretražuju se iz različitih čvorova na istoj dubini, pa se ocene ne poklope do centipiona. Otud provera „potez motora" ide **pre** pragova u `classify` — bez toga bi potez motora ponekad ispao „odličan" umesto „najbolji".
- **Ekran analize je prvi ekran koji zavisi od Stockfish-a da bi uopšte nešto prikazao.** Ako mreže nema u bundle-u (`isAvailable == false`), mora da kaže zašto, a ne da stoji prazan — zato `failed` stanje postoji i mora biti vizuelno provereno.
- **`allHistoryStates` je izvedeno svojstvo koje se računa pri svakom čitanju.** Za 81 poziciju to je u redu, ali ga treba pročitati **jednom** i proslediti, ne u petlji.
