# Faza 0 — Higijena i temelji: plan implementacije

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Napraviti tačku povratka za dva meseca nekomitovanog rada, postaviti automatske testove za šahovski motor, popraviti jedan stvarni bug u pravilima šaha i četiri vidljiva UI bug-a.

**Architecture:** Testovi se dodaju kao **zaseban SwiftPM paket u korenu repozitorijuma** koji kompajlira postojeće izvorne fajlove motora po putanji (bez kopiranja) i pokreće se sa `swift test` na macOS-u. Xcode projekat se ne dira — nema novog test target-a, nema izmena `project.pbxproj`. Popravke bug-ova su lokalne izmene u `GameState.swift` i tri View fajla.

**Tech Stack:** Swift 6.0, SwiftUI, Swift Testing (`import Testing`), SwiftPM, Xcode 26.

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` (sekcija 6, Faza 0)

## Global Constraints

- **Swift 6.0**, iOS deployment target **18.0**, Xcode 26.
- **NIKADA ne pokretati `create_xcode_project.py`.** Skripta regeneriše ceo `project.pbxproj` iz nule i **ne zna za `chesskit-engine` SPM zavisnost** (7 referenci u pbxproj, 0 u skripti). Pokretanje obara Stockfish.
- **Ne dirati `Chessko.xcodeproj/project.pbxproj`** ni u jednom zadatku ovog plana.
- Izvorni jezik UI stringova je **srpski**; srpski tekst u kodu *jeste* ključ prevoda. Svaki nov UI string mora da dobije `add(...)` liniju u `build_localizations.py` sa 7 prevoda.
- `GameState` je immutable — `applying(_:)` i `applyingForSearch(_:)` vraćaju novu kopiju. U rekurziji pretrage koristi se **isključivo** `applyingForSearch`.
- Build aplikacije: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
- Testovi motora: `swift test` iz korena repozitorijuma.

---

## Pregled zadataka

| # | Zadatak | Vrsta provere |
|---|---|---|
| 1 | Sigurnosni commit zatečenog rada | `git status` čist |
| 2 | SwiftPM test harness | `swift test` prolazi |
| 3 | Perft skup za svih 6 pozicija | 1 test pada — dokazuje bug |
| 4 | Popravka prava rokade pri jedenju topa | perft prolazi u celini |
| 5 | Markdown se renderuje u lekcijama | vizuelna provera |
| 6 | Belo-na-belom u svetloj temi | vizuelna provera |
| 7 | Safe area za plutajući tab bar | vizuelna provera |
| 8 | Sirovi ključevi tema zadataka | vizuelna provera |
| 9 | Ažuriranje CLAUDE.md | pregled |

---

## Task 1: Sigurnosni commit zatečenog rada

U radnom stablu stoji oko dva meseca rada koji nikad nije commit-ovan: ceo Android port, `StatsManager`, `EvalBarView`, `PlatformHelper`, review mode, resign, PGN. Pre bilo kakve izmene mora da postoji tačka povratka.

**Files:**
- Create: `.gitignore` (dopuna — build artefakti i potpisni ključ)
- Modify: ništa u izvornom kodu

**Interfaces:**
- Consumes: ništa
- Produces: granu `v2/faza-0` sa čistim radnim stablom, na koju se naslanjaju svi ostali zadaci

- [ ] **Step 1: Pogledati šta tačno stoji necommit-ovano**

```bash
git status --short
git diff --stat
```

Očekivano: oko 18 izmenjenih fajlova i 9 nepraćenih stavki, uključujući `ChesskoAndroid/`, `AppStoreScreenshots/`, `GooglePlayAssets/`, `appbundle.jks`.

- [ ] **Step 2: Napraviti granu**

```bash
git checkout -b v2/faza-0
```

Rad ostaje van `main` dok ga vlasnik ne spoji sam.

- [ ] **Step 3: Dopuniti .gitignore**

`appbundle.jks` je Android potpisni ključ i **ne sme** u repozitorijum. `ChesskoAndroid/app/build/` i `.idea/` su build artefakti.

Napraviti ili dopuniti `.gitignore` u korenu ovim sadržajem:

```gitignore
# macOS
.DS_Store

# Xcode
build/
DerivedData/
*.xcuserstate
Chessko.xcodeproj/xcuserdata/

# SwiftPM
.build/
Package.resolved

# Android
ChesskoAndroid/app/build/
ChesskoAndroid/build/
ChesskoAndroid/.gradle/
ChesskoAndroid/.idea/
ChesskoAndroid/local.properties

# Potpisni kljucevi — nikad u repozitorijum
*.jks
*.keystore

# Logovi
build.log
```

- [ ] **Step 4: Proveriti da ključ više nije u pripremi za commit**

```bash
git add -A
git status --short | grep -E "jks|keystore|app/build" || echo "OK: kljuc i build artefakti su ignorisani"
```

Očekivano: ispis `OK: kljuc i build artefakti su ignorisani`.

- [ ] **Step 5: Commit**

```bash
git commit -m "Snapshot: Android port, statistika, eval bar, pregled poteza

Dva meseca rada koji nije bio commit-ovan: ceo ChesskoAndroid
(Kotlin/Compose port), StatsManager, EvalBarView, PlatformHelper,
review mode, resign i generisanje PGN-a na iOS-u.

Commit-uje se kao tacka povratka pre pocetka rada na v2.
Dodat .gitignore — potpisni kljuc appbundle.jks i build artefakti
se vise ne prate."
```

- [ ] **Step 6: Provera**

```bash
git status --short
```

Očekivano: prazan ispis.

---

## Task 2: SwiftPM test harness

Motor (`MoveGenerator`, `GameState`, modeli) nema nijedan test. Xcode test target bi tražio ručno krpljenje `project.pbxproj`, što je zabranjeno globalnim ograničenjima. Umesto toga pravimo SwiftPM paket u korenu koji kompajlira **iste izvorne fajlove po putanji** — testovi uvek testiraju pravi kod, ne kopiju.

Šest fajlova koje motor traži (`Position`, `ChessPiece`, `ChessMove`, `GameState`, `GameState+FEN`, `MoveGenerator`) uvoze samo `Foundation`. Jedina prepreka je `ChessPiece.swift`, koji na 7 mesta zove globalnu funkciju `Loc(_:)` iz `LocalizationManager.swift` — a taj fajl uvozi SwiftUI i radi `object_setClass(Bundle.main, ...)`. Zato paket dobija shim koji `Loc` svodi na identitet, zaštićen `#if` flagom tako da je bezopasan i ako fajl ikad završi u app targetu.

**Files:**
- Create: `Package.swift`
- Create: `Chessko/TestSupport/LocShim.swift`
- Test: `Tests/ChesskoEngineTests/PerftTests.swift`

**Interfaces:**
- Consumes: `GameState.initial()`, `MoveGenerator.legalMoves(for:in:)` iz postojećeg koda
- Produces: modul `ChesskoEngine` (dostupan testovima preko `@testable import ChesskoEngine`) i funkciju `perft(_ state: GameState, _ depth: Int) -> Int` koju koristi Task 3

- [ ] **Step 1: Napisati Package.swift**

Create `Package.swift`:

```swift
// swift-tools-version: 6.0
import PackageDescription

// Testni paket za sahovski motor. Kompajlira POSTOJECE izvorne fajlove po
// putanji — bez kopiranja — pa testovi uvek proveravaju pravi kod.
// Xcode projekat (Chessko.xcodeproj) je potpuno nezavisan od ovog paketa.
let package = Package(
    name: "ChesskoEngine",
    platforms: [.macOS(.v14)],
    targets: [
        .target(
            name: "ChesskoEngine",
            path: "Chessko",
            sources: [
                "Models/Position.swift",
                "Models/ChessPiece.swift",
                "Models/ChessMove.swift",
                "Models/GameState.swift",
                "Models/GameState+FEN.swift",
                "Logic/MoveGenerator.swift",
                "TestSupport/LocShim.swift",
            ],
            swiftSettings: [.define("CHESSKO_ENGINE_PACKAGE")]
        ),
        .testTarget(
            name: "ChesskoEngineTests",
            dependencies: ["ChesskoEngine"],
            path: "Tests/ChesskoEngineTests"
        ),
    ]
)
```

- [ ] **Step 2: Napisati Loc shim**

Create `Chessko/TestSupport/LocShim.swift`:

```swift
import Foundation

// Ovaj fajl se kompajlira ISKLJUCIVO u sklopu SwiftPM testnog paketa
// (Package.swift definise CHESSKO_ENGINE_PACKAGE). U aplikaciji pravi
// Loc(_:) dolazi iz Logic/LocalizationManager.swift.
//
// ChessPiece.swift zove Loc(...) za srbName/srbAdjective. Testovima motora
// prevodi nisu bitni, pa je ovde Loc identitet.
#if CHESSKO_ENGINE_PACKAGE
func Loc(_ key: String) -> String { key }
#endif
```

- [ ] **Step 3: Napisati prvi test (mora da padne pre nego što paket postoji)**

Create `Tests/ChesskoEngineTests/PerftTests.swift`:

```swift
import Testing
@testable import ChesskoEngine

/// Broji listove stabla poteza do zadate dubine — standardna provera
/// ispravnosti generatora poteza. Koristi applyingForSearch (lagani apply,
/// bez racunanja statusa) jer applying() poziva legalMoves i bio bi spor.
func perft(_ state: GameState, _ depth: Int) -> Int {
    if depth == 0 { return 1 }
    let moves = MoveGenerator.legalMoves(for: state.currentTurn, in: state)
    if depth == 1 { return moves.count }
    var total = 0
    for move in moves {
        total += perft(state.applyingForSearch(move), depth - 1)
    }
    return total
}

@Test func startingPositionPerft() {
    let state = GameState.initial()
    #expect(perft(state, 1) == 20)
    #expect(perft(state, 2) == 400)
    #expect(perft(state, 3) == 8_902)
    #expect(perft(state, 4) == 197_281)
}
```

- [ ] **Step 4: Pokrenuti testove**

Run: `swift test`

Expected: PASS — jedan test, sve četiri dubine tačne. Traje oko 2,5 sekunde.

Ako `swift build` prijavi `cannot find 'Loc' in scope`, znači da `swiftSettings: [.define("CHESSKO_ENGINE_PACKAGE")]` nedostaje u `Package.swift` — shim je tada isključen `#if` guardom.

- [ ] **Step 5: Commit**

```bash
git add Package.swift Chessko/TestSupport/LocShim.swift Tests/ChesskoEngineTests/PerftTests.swift
git commit -m "test: SwiftPM harness za motor + perft za pocetnu poziciju

Testovi kompajliraju postojece izvorne fajlove po putanji, bez kopiranja
i bez diranja Chessko.xcodeproj. Pokrecu se sa 'swift test' na macOS-u.
LocShim zamenjuje Loc(_:) u testovima jer LocalizationManager uvozi SwiftUI."
```

---

## Task 3: Perft skup za svih 6 standardnih pozicija

Početna pozicija ne hvata rokadu, en passant ni promociju. Standardni skup od šest pozicija hvata. **Jedan od njih će pasti** — to nije greška u testu nego stvarni bug u motoru, koji popravlja Task 4.

Sve očekivane vrednosti u ovom zadatku su unapred proverene na ovom motoru: pet pozicija daje tačne brojeve, pozicija 5 na dubini 3 daje **62416 umesto 62379**.

**Files:**
- Modify: `Tests/ChesskoEngineTests/PerftTests.swift`

**Interfaces:**
- Consumes: `perft(_:_:)` iz Task 2, `GameState.fromFEN(_:)`
- Produces: test `positionFivePerft()` koji pada i time definiše posao za Task 4

- [ ] **Step 1: Dopisati preostale pozicije**

Dodati na kraj `Tests/ChesskoEngineTests/PerftTests.swift`:

```swift
// Kiwipete — hvata rokadu, en passant i vezivanja.
@Test func kiwipetePerft() {
    let fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 48)
    #expect(perft(state, 2) == 2_039)
    #expect(perft(state, 3) == 97_862)
}

// Pozicija 3 — en passant i ivicni slucajevi piona.
@Test func positionThreePerft() {
    let fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 14)
    #expect(perft(state, 2) == 191)
    #expect(perft(state, 3) == 2_812)
    #expect(perft(state, 4) == 43_238)
}

// Pozicija 4 — promocija.
@Test func positionFourPerft() {
    let fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 6)
    #expect(perft(state, 2) == 264)
    #expect(perft(state, 3) == 9_467)
}

// Pozicija 5 — hvata pravo rokade kad se top POJEDE na startnom polju.
// Ovaj test PADA pre Task 4 (motor vrati 62416 umesto 62379).
@Test func positionFivePerft() {
    let fen = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 44)
    #expect(perft(state, 2) == 1_486)
    #expect(perft(state, 3) == 62_379)
}

// Pocetna pozicija, dubina 5 — 4.865.609 cvorova, traje ~90 sekundi
// (provereno na ovom motoru).
// Odvojen test da moze da se preskoci sa --filter kad se radi brzo.
@Test func startingPositionDeepPerft() {
    #expect(perft(GameState.initial(), 5) == 4_865_609)
}

// Pozicija 6 — mirna sredisnjica, kontrolna.
@Test func positionSixPerft() {
    let fen = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
    let state = try! #require(GameState.fromFEN(fen))
    #expect(perft(state, 1) == 46)
    #expect(perft(state, 2) == 2_079)
    #expect(perft(state, 3) == 89_890)
}
```

- [ ] **Step 2: Pokrenuti testove i potvrditi da pada tačno jedan**

Run: `swift test`

Expected: FAIL — šest testova prolazi, `positionFivePerft()` pada sa porukom oblika `Expectation failed: (perft(state, 3) → 62416) == 62379`.

Ako padne bilo koji drugi test, **stati i prijaviti** — plan pretpostavlja da je jedini problem pravo rokade.

- [ ] **Step 3: Commit testa koji pada**

```bash
git add Tests/ChesskoEngineTests/PerftTests.swift
git commit -m "test: perft za svih 6 standardnih pozicija

Pozicija 5 pada (62416 umesto 62379) — motor ne oduzima pravo rokade
kad se top pojede na startnom polju. Popravka sledi."
```

---

## Task 4: Popravka prava rokade kad se top pojede

**Ovo je stvarni bug u objavljenoj aplikaciji, na obe platforme.**

`applyingForSearch(_:)` ažurira prava rokade samo na osnovu `move.from` — dakle kad se top **pomeri**. Kad se top **pojede** na startnom polju, pravo ostaje. `MoveGenerator.kingMoves` veruje isključivo tim zastavicama i ne proverava da li top uopšte postoji. Grana za rokadu zatim **stvara novog topa** (`s.board[row][5] = ChessPiece(type: .rook, ...)`) umesto da pomeri postojećeg.

Posledica u partiji: protivnik pojede topa na h1, beli i dalje sme da rokira, i **dobija novog topa na f1**. Provereno: posle takve rokade beli ima 2 topa tamo gde je imao 1.

`applying(_:)` poziva `applyingForSearch(_:)`, pa bug pogađa i prave poteze i AI pretragu.

**Files:**
- Modify: `Chessko/Models/GameState.swift` (grane rokade u `applyingForSearch`, oko linija 281–295; blok prava rokade, oko linija 312–330)
- Test: `Tests/ChesskoEngineTests/CastlingTests.swift`

**Interfaces:**
- Consumes: `GameState.fromFEN(_:)`, `GameState.applying(_:)`, `MoveGenerator.legalMoves(for:in:)`
- Produces: ispravan `applyingForSearch(_:)`; nema promene potpisa

- [ ] **Step 1: Napisati test koji reprodukuje bug u partiji**

Create `Tests/ChesskoEngineTests/CastlingTests.swift`:

```swift
import Testing
@testable import ChesskoEngine

// Crni skakac na f2 moze da pojede belog topa na h1. f1 i g1 su prazni,
// beli jos ima pravo na obe rokade. Posle Sxh1 beli NE SME da rokira.
private let rookCaptureFEN = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R b KQ - 1 8"

@Test func capturingRookRevokesCastlingRight() {
    let state = try! #require(GameState.fromFEN(rookCaptureFEN))
    let knightTakesRook = ChessMove(from: Position(row: 6, col: 5),
                                    to:   Position(row: 7, col: 7))
    let after = state.applying(knightTakesRook)

    #expect(after.whiteCanCastleKingside == false)

    let castles = MoveGenerator.legalMoves(for: .white, in: after)
        .filter { $0.flag == .castleKingside }
    #expect(castles.isEmpty)
}

@Test func castlingMovesTheRealRookInsteadOfCreatingOne() {
    let state = GameState.initial()
    // 1.e4 e5 2.Sf3 Sc6 3.Lc4 Lc5 — oslobadja f1 i g1 za belog.
    let opening = [
        (Position(row: 6, col: 4), Position(row: 4, col: 4)),
        (Position(row: 1, col: 4), Position(row: 3, col: 4)),
        (Position(row: 7, col: 6), Position(row: 5, col: 5)),
        (Position(row: 0, col: 1), Position(row: 2, col: 2)),
        (Position(row: 7, col: 5), Position(row: 4, col: 2)),
        (Position(row: 0, col: 5), Position(row: 3, col: 2)),
    ]
    var s = state
    for (from, to) in opening {
        s = s.applying(ChessMove(from: from, to: to))
    }

    let castle = try! #require(
        MoveGenerator.legalMoves(for: .white, in: s)
            .first { $0.flag == .castleKingside }
    )
    let after = s.applying(castle)

    #expect(after.board[7][6]?.type == .king)
    #expect(after.board[7][5]?.type == .rook)
    #expect(after.board[7][7] == nil)
    #expect(countRooks(in: after, color: .white) == 2)
}

private func countRooks(in state: GameState, color: PieceColor) -> Int {
    var count = 0
    for row in 0..<8 {
        for col in 0..<8 {
            if let p = state.board[row][col], p.color == color, p.type == .rook {
                count += 1
            }
        }
    }
    return count
}
```

- [ ] **Step 2: Pokrenuti i potvrditi da testovi padaju**

Run: `swift test --filter CastlingTests`

Expected: FAIL — `capturingRookRevokesCastlingRight()` pada na `after.whiteCanCastleKingside == false`.

- [ ] **Step 3: Popraviti grane rokade da pomeraju pravog topa**

U `Chessko/Models/GameState.swift`, u funkciji `applyingForSearch(_:)`, zameniti:

```swift
        case .castleKingside:
            let row = move.from.row
            s.board[row][6] = ChessPiece(type: .king, color: piece.color)
            s.board[row][5] = ChessPiece(type: .rook, color: piece.color)
            s.board[row][4] = nil
            s.board[row][7] = nil

        case .castleQueenside:
            let row = move.from.row
            s.board[row][2] = ChessPiece(type: .king, color: piece.color)
            s.board[row][3] = ChessPiece(type: .rook, color: piece.color)
            s.board[row][4] = nil
            s.board[row][0] = nil
```

sa:

```swift
        case .castleKingside:
            let row = move.from.row
            // Pomeri POSTOJECEG topa; ranije se pravio nov, pa je rokada
            // posle pojedenog topa stvarala figuru iz vazduha.
            s.board[row][5] = s.board[row][7]
            s.board[row][6] = piece
            s.board[row][4] = nil
            s.board[row][7] = nil

        case .castleQueenside:
            let row = move.from.row
            s.board[row][3] = s.board[row][0]
            s.board[row][2] = piece
            s.board[row][4] = nil
            s.board[row][0] = nil
```

- [ ] **Step 4: Dodati oduzimanje prava rokade po odredištu poteza**

U istoj funkciji, odmah **posle** postojećeg bloka `if piece.type == .rook { switch move.from { ... } }`, dodati:

```swift
        // Top pojeden na startnom polju takodje oduzima pravo rokade
        // vlasniku tog topa. Vazi za svaku figuru koja stigne na to polje.
        switch move.to {
        case Position(row: 7, col: 7): s.whiteCanCastleKingside  = false
        case Position(row: 7, col: 0): s.whiteCanCastleQueenside = false
        case Position(row: 0, col: 7): s.blackCanCastleKingside  = false
        case Position(row: 0, col: 0): s.blackCanCastleQueenside = false
        default: break
        }
```

- [ ] **Step 5: Pokrenuti testove rokade**

Run: `swift test --filter CastlingTests`

Expected: PASS — oba testa.

- [ ] **Step 6: Pokrenuti ceo skup**

Run: `swift test`

Expected: PASS — svih 7 testova, uključujući `positionFivePerft()` koji sada daje 62379.

- [ ] **Step 7: Provera da aplikacija i dalje kompajlira**

Run: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 8: Commit**

```bash
git add Chessko/Models/GameState.swift Tests/ChesskoEngineTests/CastlingTests.swift
git commit -m "fix: pojeden top oduzima pravo rokade

Prava rokade su se azurirala samo po move.from (kad se top POMERI).
Kad se top POJEDE na startnom polju pravo je ostajalo, a kako
kingMoves veruje samo zastavicama, motor je nudio rokadu bez topa —
i grana rokade je stvarala novog topa iz vazduha (2 topa umesto 1).

Rokada sada pomera postojeceg topa, a prava se oduzimaju i po move.to.
Perft pozicija 5 dubina 3: 62416 -> 62379 (tacno)."
```

---

## Task 5: Markdown se renderuje u lekcijama

`L_Para` i srodne komponente rade `Text(Loc(text))`. `Loc()` vraća `String`, a SwiftUI parsira Markdown samo za `LocalizedStringKey`. Korisnik doslovno vidi `Šah se igra na tabli od **64 polja**`. Pogođeno je 25 pojava u `LessonDetailView.swift`, na svih 8 jezika.

Rešenje nije `Text(.init(Loc(text)))` — to bi već prevedeni string ponovo tražilo kao ključ u katalogu. Ispravno je eksplicitno parsiranje kroz `AttributedString(markdown:)`.

**Files:**
- Modify: `Chessko/Views/LessonDetailView.swift` (`L_Para` ~648, `L_Bullet` ~660, `L_Box` ~688, `L_NumberedRule` ~743)

**Interfaces:**
- Consumes: `Loc(_:)` iz `Logic/LocalizationManager.swift`
- Produces: `fileprivate func mdText(_ localized: String) -> Text` — koriste je sve četiri komponente

- [ ] **Step 1: Dodati pomoćnu funkciju**

U `Chessko/Views/LessonDetailView.swift`, neposredno **iznad** `private struct L_Para`, dodati:

```swift
/// Parsira inline Markdown (**podebljano**, *kurziv*) u vec prevedenom stringu.
/// Loc() vraca String, a Text(String) ne parsira Markdown — parsira ga samo
/// Text(LocalizedStringKey), sto bi ovde znacilo drugo trazenje po katalogu.
fileprivate func mdText(_ localized: String) -> Text {
    if let attributed = try? AttributedString(
        markdown: localized,
        options: AttributedString.MarkdownParsingOptions(
            interpretedSyntax: .inlineOnlyPreservingWhitespace
        )
    ) {
        return Text(attributed)
    }
    return Text(localized)
}
```

- [ ] **Step 2: Prevesti L_Para na mdText**

U `private struct L_Para`, zameniti `Text(Loc(text))` sa `mdText(Loc(text))`. Telo posle izmene:

```swift
    var body: some View {
        mdText(Loc(text))
            .font(.appFont(.subheadline))
            .foregroundStyle(.primary.opacity(0.85))
            .fixedSize(horizontal: false, vertical: true)
    }
```

- [ ] **Step 3: Prevesti L_Bullet, L_Box i L_NumberedRule**

U sve tri komponente zameniti **samo** `Text(Loc(text))` sa `mdText(Loc(text))`. Naslovi (`Text(Loc(title))`) ostaju nepromenjeni — u njima nema Markdown-a.

Tačna mesta:
- `L_Bullet`: red `Text(Loc(text))` unutar unutrašnjeg `VStack`, ispod `Text(Loc(title))`
- `L_Box`: red `Text(Loc(text))` ispod `HStack` sa ikonom i naslovom
- `L_NumberedRule`: red `Text(Loc(text))` unutar `VStack`, ispod `Text(Loc(title))`

- [ ] **Step 4: Build**

Run: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 5: Vizuelna provera**

Ove izmene su u SwiftUI prikazu i ne pokrivaju se testovima motora; provera je vizuelna.

```bash
xcrun simctl boot "iPhone 17 Pro" 2>/dev/null; sleep 8
xcrun simctl install "iPhone 17 Pro" "$(find ~/Library/Developer/Xcode/DerivedData -name Chessko.app -path "*Debug-iphonesimulator*" | head -1)"
xcrun simctl launch "iPhone 17 Pro" com.veljkoni.chessko
```

U simulatoru otvoriti tab **Učenje → Lekcija 1** i potvrditi da u tekstu „Šah se igra na tabli od 64 polja" **nema zvezdica**, a da je „64 polja" podebljano.

- [ ] **Step 6: Commit**

```bash
git add Chessko/Views/LessonDetailView.swift
git commit -m "fix: Markdown u lekcijama se renderuje umesto da se ispisuje

Text(Loc(text)) prima String, a SwiftUI parsira Markdown samo za
LocalizedStringKey — korisniku su se prikazivale zvezdice (25 pojava,
na svih 8 jezika). Dodata mdText() koja parsira kroz AttributedString."
```

---

## Task 6: Belo-na-belom u svetloj temi

Piece picker u Lekciji 1 crta belu tipografiju na skoro beloj podlozi — u svetloj temi je nečitljiv, u tamnoj je ispravan.

**Files:**
- Modify: `Chessko/Views/LessonDetailView.swift` (`piecePicker`, linije ~220–248)

**Interfaces:**
- Consumes: `pieceExplorer.selectedPieceType`, `PieceType.srbName`
- Produces: ništa novo

- [ ] **Step 1: Zameniti hardkodirane bele boje semantičkim**

U `private var piecePicker`, zameniti tri mesta:

```swift
                            .foregroundStyle(sel ? .white : .white.opacity(0.6))
```
sa
```swift
                            .foregroundStyle(sel ? Color.primary : Color.secondary)
```

```swift
                    .background(sel ? Color.white.opacity(0.18) : Color.white.opacity(0.04),
                                in: RoundedRectangle(cornerRadius: 10))
```
sa
```swift
                    .background(sel ? Color.primary.opacity(0.14) : Color.primary.opacity(0.05),
                                in: RoundedRectangle(cornerRadius: 10))
```

```swift
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 14))
```
sa
```swift
        .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
```

- [ ] **Step 2: Build**

Run: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 3: Vizuelna provera u obe teme**

```bash
xcrun simctl ui "iPhone 17 Pro" appearance light
```
Otvoriti **Učenje → Lekcija 1**, doskrolovati do birača figura. Labele Pešak / Skakač / Lovac / Top / Dama / Kralj moraju biti čitljive, a izabrana figura vidljivo istaknuta.

```bash
xcrun simctl ui "iPhone 17 Pro" appearance dark
```
Ponoviti — mora ostati čitljivo i u tamnoj temi.

- [ ] **Step 4: Commit**

```bash
git add Chessko/Views/LessonDetailView.swift
git commit -m "fix: birac figura je bio nevidljiv u svetloj temi

Bela tipografija na Color.white.opacity(0.04) podlozi. Zamenjeno
semantickim bojama (.primary/.secondary) koje rade u obe teme."
```

**Napomena za kasnije:** ovim je popravljen samo birač figura. U `LessonDetailView.swift` ostaje 32 hardkodiranih `.white`, u `GameView.swift` 13, u `ChessClockView.swift` 5. Njihova sistematska zamena je posao **Faze 1 (dizajn sistem)** — ne raditi je ovde.

---

## Task 7: Safe area za plutajući tab bar

Na iOS-u 26 tab bar pluta iznad sadržaja. Tabla u lekciji je presečena. Nigde u projektu nema `safeAreaPadding` ni `safeAreaInset`.

**Files:**
- Modify: `Chessko/Views/LessonDetailView.swift:15`
- Modify: `Chessko/Views/LearnView.swift:91`
- Modify: `Chessko/Views/PuzzleView.swift:175`
- Modify: `Chessko/Views/GameView.swift:90`

**Interfaces:**
- Consumes: ništa
- Produces: ništa

- [ ] **Step 1: LessonDetailView**

Na `ScrollView(.vertical, showsIndicators: true)` koji počinje na liniji 15, dodati modifikator **na sam ScrollView** (ne na VStack unutra), odmah posle zatvarajuće vitičaste zagrade njegovog sadržaja:

```swift
                .safeAreaPadding(.bottom, 24)
```

Postojeći `.padding(.bottom, 40)` na unutrašnjem `VStack` ostaje — on razmiče sadržaj od dna, `safeAreaPadding` uvlači skrol oblast ispod plutajućeg tab bara.

- [ ] **Step 2: LearnView**

Na `ScrollView` sa linije 91 dodati isti modifikator:

```swift
                .safeAreaPadding(.bottom, 24)
```

- [ ] **Step 3: PuzzleView**

Na `ScrollView` sa linije 175 (portret grana) dodati:

```swift
            .safeAreaPadding(.bottom, 24)
```

- [ ] **Step 4: GameView**

Na `ScrollView` sa linije 90 (portret grana) dodati:

```swift
                    .safeAreaPadding(.bottom, 24)
```

`ScrollView` na liniji 60 je desna kolona u pejzažnom režimu, gde tab bar ne preklapa sadržaj — njega **ne dirati**.

- [ ] **Step 5: Build**

Run: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 6: Vizuelna provera**

U simulatoru proći sva tri taba i **Učenje → Lekcija 1**, doskrolovati do dna svakog. Nijedan sadržaj ne sme da završi ispod plutajućeg tab bara; tabla u lekciji mora da bude cela vidljiva.

- [ ] **Step 7: Commit**

```bash
git add Chessko/Views/LessonDetailView.swift Chessko/Views/LearnView.swift Chessko/Views/PuzzleView.swift Chessko/Views/GameView.swift
git commit -m "fix: sadrzaj vise ne prolazi ispod plutajuceg tab bara

iOS 26 tab bar lebdi iznad sadrzaja; tabla u lekciji je bila presecena.
Dodat safeAreaPadding(.bottom, 24) na skrolujuce kontejnere u sva tri
taba i u detalju lekcije."
```

---

## Task 8: Sirovi ključevi tema zadataka

`localizeTheme` pokriva 22 teme, a `default` vraća sam ključ. Lichess ima oko 60 tema, pa korisniku redovno piše `backRankMate` u camelCase. Uz to su dva postojeća prevoda odsečena.

Popravka je namerno minimalna: `default` prestaje da vraća sirov ključ, pa se nepoznata tema jednostavno ne prikazuje. Dopunjavanje mape svim Lichess temama traži ~40 novih ključeva puta 8 jezika i pripada **Fazi 2**, kad offline baza uvede filtriranje po temi.

**Files:**
- Modify: `Chessko/Views/PuzzleView.swift:491-515` (`localizeTheme`), `PuzzleView.swift:300-311` (prikaz čipa)
- Modify: `build_localizations.py:131-132`

**Interfaces:**
- Consumes: `ChessPuzzle.themeList`
- Produces: `localizeTheme(_:) -> String` koja za nepoznatu temu vraća prazan string

- [ ] **Step 1: Ispraviti dva odsečena prevoda u Swift kodu**

U `Chessko/Views/PuzzleView.swift`, u `localizeTheme`:

```swift
        case "queensideAttack":  return Loc("Napad na dam")
        case "kingsideAttack":   return Loc("Napad na kral")
```
zameniti sa:
```swift
        case "queensideAttack":  return Loc("Napad na damu")
        case "kingsideAttack":   return Loc("Napad na kralja")
```

- [ ] **Step 2: Promeniti default da ne vraća sirov ključ**

U istoj funkciji:

```swift
        default:                 return theme
```
zameniti sa:
```swift
        // Nepoznata tema se ne prikazuje — bolje nista nego sirov kljuc
        // tipa "backRankMate". Mapa se dopunjava u Fazi 2, uz offline bazu.
        default:                 return ""
```

- [ ] **Step 3: Ne prikazivati prazan čip**

U `Chessko/Views/PuzzleView.swift`, oko linije 300, prikaz teme je:

```swift
                Text(localizeTheme(theme))
                    .font(.caption2)
                    .foregroundStyle(Color.secondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(Color.primary.opacity(0.06), in: Capsule())
```

Obmotati ga tako da se prazan rezultat preskoči — zameniti gornji blok sa:

```swift
                let label = localizeTheme(theme)
                if !label.isEmpty {
                    Text(label)
                        .font(.caption2)
                        .foregroundStyle(Color.secondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Color.primary.opacity(0.06), in: Capsule())
                }
```

Ako okolni `ForEach` ne dozvoljava `let` na tom mestu, koristiti `if !localizeTheme(theme).isEmpty { Text(localizeTheme(theme)) ... }`.

- [ ] **Step 4: Ispraviti iste ključeve u build_localizations.py**

U `build_localizations.py`, linije 131–132, promeniti **samo srpski ključ** (prevodi su već tačni):

```python
add("Napad na dam", "Queenside attack", ...)
add("Napad na kral", "Kingside attack", ...)
```
u
```python
add("Napad na damu", "Queenside attack", ...)
add("Napad na kralja", "Kingside attack", ...)
```

Ostatak svake linije ostaje nepromenjen.

- [ ] **Step 5: Regenerisati katalog prevoda**

Run: `python3 build_localizations.py`

Expected: skripta ispiše broj generisanih ključeva bez greške.

```bash
grep -c "Napad na damu" Chessko/Localizable.xcstrings
```
Expected: `1` ili više. Stari ključevi „Napad na dam" / „Napad na kral" ne smeju više da postoje:
```bash
grep -c '"Napad na dam"' Chessko/Localizable.xcstrings || echo "OK: stari kljuc uklonjen"
```

- [ ] **Step 6: Build**

Run: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 7: Vizuelna provera**

Otvoriti tab **Zadaci**. Pored rejtinga smeju da stoje samo prevedene teme; nijedan čip ne sme da sadrži camelCase tekst tipa `backRankMate`. Ako zadatak nema nijednu poznatu temu, čipova jednostavno nema.

- [ ] **Step 8: Commit**

```bash
git add Chessko/Views/PuzzleView.swift build_localizations.py Chessko/Localizable.xcstrings
git commit -m "fix: sirovi kljucevi tema zadataka se vise ne prikazuju

localizeTheme je za nepoznatu temu vracala sam kljuc, pa je korisniku
pisalo 'backRankMate'. Sada vraca prazan string i cip se preskace.
Ispravljena i dva odsecena prevoda: 'Napad na dam(u)', 'Napad na kral(ja)'.
Dopuna mape svim Lichess temama je posao Faze 2."
```

---

## Task 9: Ažuriranje CLAUDE.md

Changelog je stao na 2026-07-04, a u međuvremenu su nastali Android port, statistika, eval bar i pregled poteza. Uz to, dva pravila u dokumentu su sada netačna.

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: ništa
- Produces: ništa

- [ ] **Step 1: Dopuniti sekciju „Build / Run" upozorenjem o generatoru**

U sekciji `## Build / Run`, ispod postojećeg opisa `create_xcode_project.py`, dodati:

```markdown
> **UPOZORENJE:** `create_xcode_project.py` se **više ne sme pokretati**.
> Regeneriše ceo `project.pbxproj` iz nule i ne zna za `chesskit-engine`
> SPM zavisnost — pokretanje obara Stockfish. `project.pbxproj` se od
> 2026-07-03 održava ručno.
```

- [ ] **Step 2: Dodati sekciju o testovima**

Ispod sekcije `## Build / Run` dodati:

```markdown
## Testovi

Motor se testira kroz SwiftPM paket u korenu (`Package.swift`), nezavisno
od Xcode projekta — kompajlira postojeće izvorne fajlove po putanji, bez
kopiranja.

```bash
swift test              # ceo skup
swift test --filter Perft
```

Pokriveno: perft za svih 6 standardnih pozicija (uključujući početnu do
dubine 5) i prava rokade.
`Chessko/TestSupport/LocShim.swift` postoji samo zbog paketa i zaštićen je
`#if CHESSKO_ENGINE_PACKAGE` — u aplikaciji se ne kompajlira.
```

- [ ] **Step 3: Ispraviti netačnu stavku u „Poznata ograničenja"**

U sekciji `## Poznata ograničenja / TODO kandidati` ukloniti red:

```markdown
- Nema test target-a.
```

i ukloniti duplirani red „Izbor figure pri promociji nije implementiran (uvek dama)." (pojavljuje se dvaput, a funkcija je odavno urađena).

- [ ] **Step 4: Dodati zapise u Changelog**

Na kraj sekcije `## Changelog` dodati:

```markdown
- **2026-09-05** — Faza 0 (higijena i temelji). Commit-ovan zaostali rad
  (Android port, `StatsManager`, `EvalBarView`, `PlatformHelper`, review mode,
  resign, PGN) i dodat `.gitignore` (potpisni ključ `appbundle.jks` više se ne
  prati). Uveden SwiftPM testni paket (`Package.swift` + `Tests/ChesskoEngineTests`)
  koji kompajlira izvorne fajlove motora po putanji; perft za svih 6 standardnih
  pozicija. **Popravljen stvarni bug u pravilima:** prava rokade su se oduzimala
  samo po `move.from`, pa pojeden top na startnom polju nije gasio pravo — motor
  je nudio rokadu bez topa, a grana rokade je stvarala novog topa iz vazduha
  (2 topa umesto 1). Rokada sada pomera postojećeg topa, prava se oduzimaju i po
  `move.to` (perft poz. 5 dubina 3: 62416 → 62379). **Isti bug postoji na
  Androidu** (`GameState.kt`), popravlja se u Fazi 6. Popravljeni i UI bug-ovi:
  Markdown u lekcijama se renderovao kao zvezdice (`mdText()` preko
  `AttributedString`), birač figura nevidljiv u svetloj temi, sadržaj ispod
  plutajućeg iOS 26 tab bara (`safeAreaPadding`), sirovi ključevi tema zadataka
  (`backRankMate`).
```

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: azuriran CLAUDE.md za fazu 0

Upozorenje da se create_xcode_project.py vise ne sme pokretati,
sekcija o SwiftPM testovima, uklonjene zastarele stavke iz
'Poznata ogranicenja', changelog za fazu 0."
```

---

## Završna provera faze

- [ ] **Svi testovi prolaze**

Run: `swift test`
Expected: 9 testova, 0 grešaka.

- [ ] **Aplikacija se gradi**

Run: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: `** BUILD SUCCEEDED **`

- [ ] **Radno stablo je čisto**

Run: `git status --short`
Expected: prazan ispis.

- [ ] **Vizuelna provera u obe teme**

Proći sva tri taba i Lekciju 1 u svetloj i u tamnoj temi. Bez zvezdica u tekstu, bez nevidljivih labela, bez sadržaja ispod tab bara, bez camelCase tema.

---

## Otvoreno pitanje za vlasnika

Bug sa rokadom postoji i na Androidu — identičan kod u
`ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/models/GameState.kt`
(linije 188–200 i 234–241). Spec predviđa da Android čeka Fazu 6, ali ovo je
greška u pravilima šaha koja igraču poklanja topa, u objavljenoj verziji.

Popravka je istih ~12 linija Kotlina. Odluka vlasnika: da li se radi odmah kao
Task 10 ove faze, ili čeka Fazu 6.
