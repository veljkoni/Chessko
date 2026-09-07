# Faza 3 — Sadržaj lekcija u JSON: plan implementacije

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Izvući tekst sve četiri lekcije iz Swift koda u JSON fajlove van koda, tako da nova lekcija bude nov JSON bez ijedne linije Swift-a.

**Architecture:** Tipizirani blokovi sadržaja (`LessonBlock`, `Codable` enum) se učitavaju iz `Chessko/Content/lessons/<id>.<lang>.json` kroz `LessonRepository`, a `LessonRenderer` ih mapira u postojeće SwiftUI komponente. `LessonDetailView` prestaje da nosi tekst i postaje isključivo renderer. Prevodi se ne prekucavaju — generator ih vadi iz postojećeg `Localizable.xcstrings` po ključu.

**Tech Stack:** Swift 6.0, SwiftUI, `Codable`/`JSONDecoder`, Python 3 za generator.

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` (sekcije 5.2 i 6, Faza 3)

## Global Constraints

- **Swift 6.0**, iOS deployment target **18.0**, Xcode 26.
- **NIKADA ne pokretati `create_xcode_project.py`** — regeneriše `project.pbxproj` i ne zna za `chesskit-engine`; pokretanje obara Stockfish. `project.pbxproj` se održava ručno.
- **Nijedna nova SPM zavisnost.**
- Sve boje, tipografija i razmaci idu kroz `DS` / `Font.ds*`. `DS.onScrim` sme samo na `DS.scrim`; na `DS.accent` ide `DS.onAccent`.
- UI izvorni jezik je **srpski**. Stringovi **korisničkog interfejsa** idu kroz `Loc(_:)`/`LocF(_:_:)` i `build_localizations.py`. Tekst **sadržaja lekcija** posle ove faze NE ide više kroz katalog — dolazi iz JSON-a.
- **`ChesskoAndroid/` se ne dira.**
- Build: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
- Testovi: `swift test` (trenutno **27**; faza ih dodaje).
- **Kad se doda nov izvorni fajl, dokazati da se kompajlira u aplikaciju** — ubaciti sintaksnu grešku i videti da build pada. Fajl može da prođe i `swift test` i `xcodebuild` a da ga aplikacija nikad ne kompajlira (vidi Fazu 2, `PuzzleRepository.swift`).

## Zatečeno stanje, provereno

- `Chessko/Views/LessonDetailView.swift` = **1238 linija**; sadržaj lekcija je u `@ViewBuilder` svojstvima (L1: linije 66–313, L2: 314–387, L3: 388–451, L4: 452–627). Ostatak su komponente za prikaz.
- **173 ključa** iz `Localizable.xcstrings` koristi se u `LessonDetailView.swift` + `LearnView.swift`. Katalog ima **409** ključeva — dakle 42% kataloga je sadržaj lekcija. Provereno: **svaki** tekst lekcije postoji kao ključ sa svih 8 jezika (uključujući one koji počinju escape-ovanim navodnikom, npr. `"\"Prva stvar koju učenik…"`).
- Komponente koje lekcije stvarno koriste — brojano po lekciji:

  | | L1 | L2 | L3 | L4 |
  |---|---|---|---|---|
  | `L_SectionHeader` | 5 | 3 | 5 | 8 |
  | `L_Para` | 10 | 2 | 4 | 7 |
  | `L_Bullet` | 10 | 3 | 5 | 10 |
  | `L_Box` | 4 | 1 | 5 | 10 |
  | `L_PieceRow` | 6 | — | — | — |
  | `L_PieceValueTable` | 1 | — | 1 | — |
  | `L_NumberedRule` | — | 3 | — | — |
  | explorer (`piecePicker`+`BoardView`) | 1 | — | — | — |
  | `MateExerciseCard` | 3 | — | — | — |
  | `OpeningExerciseCard` | — | 3 | — | — |
  | `MatePuzzleCard` | — | — | — | 5 |

- `L_OpeningCard` postoji u fajlu ali se **više ne koristi** (zamenjen `OpeningExerciseCard`-om 2026-06-24). Briše se u Task-u 5.
- Dve vrste vežbi, ne tri: `OpeningExerciseCard` i `MatePuzzleCard` obe voze `OpeningExerciseViewModel` po skriptovanoj UCI liniji i razlikuju se samo u prikazu; `MateExerciseCard` vozi `MateExerciseViewModel` protiv AI-ja (`.easy`), bez skripte.
- `OpeningLine` (`OpeningExerciseViewModel.swift:5-15`): `name`, `uciMoves[]`, `hint`, `icon`, `accentColor`, `solvedMessage`, `wrongMessage`, `playingPrompt?`, `startFEN?`.
- `MateExerciseCard` parametri: `fen`, `title`, `hint`, `icon`, `color`.
- `LessonInfo` (`LearnView.swift:5-32`) drži `id`, `title`, `subtitle`, `icon`, `accentColor`. Posle Faze 1 su sve četiri `accentColor` = `DS.accent`, pa boja **ne ide u JSON**.
- `mdText(_:)` (`LessonDetailView.swift:653`) renderuje inline Markdown preko `AttributedString`. Ostaje i koristi ga renderer.

## Odluke ove faze

1. **Verno prenošenje.** Svaki postojeći element dobija svoj tip bloka (11 tipova). Lekcije 1–4 izgledaju identično kao pre. Konsolidacija šeme je odbačena jer bi promenila izgled lekcija 1, 3 i 4.
2. **`Content/` ide u projekat kao folder-referenca**, ne kao 33 pojedinačna unosa. Jedan unos u `project.pbxproj`; nov JSON kasnije ne traži nikakvu izmenu projekta. Ovo direktno gasi klasu greške koja je u Fazi 2 sakrila `PuzzleRepository.swift`.
3. **Prevodi se ne prekucavaju.** Generator čita `Localizable.xcstrings` i po ključu vadi svih 8 jezika. Ručno prekucavanje 173 × 8 = 1384 stringa bilo bi i skupo i nepouzdano.
4. **Dva svesna odstupanja od šeme u spec-u 5.2.** `box` dobija i `style: "info"` (spec navodi samo `rule` i `warning`) jer većina postojećih kutija koristi akcent lekcije, a ne žutu ni crvenu. `explorer` nema `pieces[]` polje iz spec-a — postojeći explorer u lekciji 1 ima sopstveni birač figura i ne parametrizuje se; polje se dodaje kad zatreba.
5. **`curriculum.json` NIJE deo ove faze.** Spec ga navodi u 5.2, ali „Gotovo kad" za Fazu 3 govori samo o lekcijama. Put se gradi u Fazi 4.

## Pregled zadataka

| # | Zadatak | Isporuka |
|---|---|---|
| 1 | Šema blokova + dekodiranje + testovi | JSON se pretvara u tipizirane blokove |
| 2 | Generator + 32 JSON fajla (4 lekcije × 8 jezika) | sadržaj postoji van koda |
| 3 | Folder-referenca + `LessonRepository` | aplikacija čita sadržaj iz bundle-a |
| 4 | `LessonRenderer` | blokovi se crtaju |
| 5 | `LessonDetailView` sveden na renderer | tekst lekcija izbačen iz Swift-a |
| 6 | Čišćenje kataloga, provera, dokumentacija | katalog prestaje da raste sa sadržajem |

---

## Task 1: Šema blokova i dekodiranje

**Files:**
- Create: `Chessko/Models/LessonContent.swift`
- Modify: `Package.swift` (dodati izvor u `sources:`)
- Modify: `Chessko.xcodeproj/project.pbxproj` (registrovati izvor u Xcode target)
- Test: `Tests/ChesskoEngineTests/LessonContentTests.swift`

**Interfaces:**
- Consumes: ništa
- Produces: `LessonDocument`, `LessonBlock`, `BoxStyle`, `ExerciseKind`, `PieceValueRow` — troše ih zadaci 2–5.

- [ ] **Step 1: Napisati test koji pada**

Create `Tests/ChesskoEngineTests/LessonContentTests.swift`:

```swift
import Testing
import Foundation
@testable import ChesskoEngine

@Test func decodesAllBlockTypesFromJSON() throws {
    let json = """
    {
      "id": "test-lesson",
      "language": "sr",
      "title": "Naslov",
      "subtitle": "Podnaslov",
      "icon": "book.fill",
      "blocks": [
        { "type": "heading", "text": "Sekcija", "icon": "star.fill" },
        { "type": "paragraph", "text": "Tekst sa **podebljanim**." },
        { "type": "bullets", "items": [
            { "icon": "checkmark", "title": "Prvo", "text": "Opis prvog" }
        ] },
        { "type": "box", "style": "rule", "icon": "lightbulb", "title": "Pravilo", "text": "Telo" },
        { "type": "quote", "text": "Citat", "author": "Kapablanka" },
        { "type": "pieceRow", "piece": "knight", "name": "Skakač", "count": "2 komada" },
        { "type": "numberedRule", "number": 1, "title": "Razvoj", "text": "Razvijaj figure" },
        { "type": "pieceValueTable", "rows": [
            { "piece": "pawn", "name": "Pion", "value": 1 }
        ] },
        { "type": "board", "fen": "8/8/8/8/8/8/8/R3K2R w KQ - 0 1", "caption": "Rokada", "interactive": false },
        { "type": "explorer" },
        { "type": "exercise", "kind": "scripted", "title": "Španska",
          "hint": "Odigraj e4", "icon": "flame.fill",
          "uciMoves": ["e2e4", "e7e5"], "startFEN": null,
          "solvedMessage": "Bravo!", "wrongMessage": "Pogrešno.",
          "playingPrompt": null, "mateIn": null },
        { "type": "exercise", "kind": "vsEngine", "title": "Kralj + Top",
          "hint": "Oteraj kralja", "icon": "rectangle.portrait.fill",
          "uciMoves": null, "startFEN": "8/8/4k3/8/4K3/8/8/R7 w - - 0 1",
          "solvedMessage": null, "wrongMessage": null,
          "playingPrompt": null, "mateIn": null }
      ]
    }
    """
    let doc = try JSONDecoder().decode(LessonDocument.self, from: Data(json.utf8))

    #expect(doc.id == "test-lesson")
    #expect(doc.language == "sr")
    #expect(doc.title == "Naslov")
    #expect(doc.blocks.count == 12)

    guard case .heading(let text, let icon) = doc.blocks[0] else {
        Issue.record("blok 0 nije heading"); return
    }
    #expect(text == "Sekcija")
    #expect(icon == "star.fill")

    guard case .bullets(let items) = doc.blocks[2] else {
        Issue.record("blok 2 nije bullets"); return
    }
    #expect(items.count == 1)
    #expect(items[0].title == "Prvo")

    guard case .box(let style, _, _, _) = doc.blocks[3] else {
        Issue.record("blok 3 nije box"); return
    }
    #expect(style == .rule)

    guard case .exercise(let scripted) = doc.blocks[10] else {
        Issue.record("blok 10 nije exercise"); return
    }
    #expect(scripted.kind == .scripted)
    #expect(scripted.uciMoves == ["e2e4", "e7e5"])

    guard case .exercise(let vsEngine) = doc.blocks[11] else {
        Issue.record("blok 11 nije exercise"); return
    }
    #expect(vsEngine.kind == .vsEngine)
    #expect(vsEngine.startFEN == "8/8/4k3/8/4K3/8/8/R7 w - - 0 1")
}

@Test func unknownBlockTypeFailsLoudlyInsteadOfBeingSkipped() {
    let json = """
    { "id": "x", "language": "sr", "title": "T", "subtitle": "S", "icon": "book.fill",
      "blocks": [ { "type": "izmisljeni-tip", "text": "nešto" } ] }
    """
    #expect(throws: (any Error).self) {
        try JSONDecoder().decode(LessonDocument.self, from: Data(json.utf8))
    }
}
```

Drugi test postoji zato što je tiho preskakanje nepoznatog bloka najgori mogući ishod: lekcija bi se prikazala sa rupom koju niko ne primeti. Bolje da build/test padne.

- [ ] **Step 2: Pokrenuti test i videti da pada**

Run: `swift test --filter LessonContent`
Expected: FAIL — `cannot find 'LessonDocument' in scope`.

- [ ] **Step 3: Napisati model**

Create `Chessko/Models/LessonContent.swift`:

```swift
import Foundation

// MARK: - Sadržaj lekcije
//
// Lekcije od Faze 3 žive u `Chessko/Content/lessons/<id>.<lang>.json`, ne u
// Swift kodu. Nova lekcija = nov JSON. Ovaj fajl je JEDINO mesto koje zna
// kako sadržaj izgleda; `LessonRenderer` ga crta, `LessonRepository` učitava.
//
// Namerno NEMA `default` grane pri dekodiranju tipa bloka: nepoznat tip mora
// da baci grešku. Tiho preskakanje bi dalo lekciju sa rupom koju niko ne vidi.

struct LessonDocument: Codable, Equatable {
    let id: String
    let language: String
    let title: String
    let subtitle: String
    let icon: String
    let blocks: [LessonBlock]
}

enum BoxStyle: String, Codable, Equatable {
    case rule      // žuta „zlatno pravilo" kutija
    case warning   // crvena kutija (greške, šah-mat)
    case info      // kutija u akcentu lekcije
}

struct BulletItem: Codable, Equatable {
    let icon: String
    let title: String
    let text: String
    /// `nil` znaci akcent lekcije. Postoji jer tri stavke ("Tipicne greske" u
    /// lekciji 2) NISU u akcentu nego crvene.
    let style: BoxStyle?
}

struct PieceValueRow: Codable, Equatable {
    let piece: String   // "pawn" | "knight" | "bishop" | "rook" | "queen" | "king"
    let name: String
    let value: Int
}

enum ExerciseKind: String, Codable, Equatable {
    /// Protivnik igra po unapred zapisanoj UCI liniji (otvaranja, „mat u N").
    case scripted
    /// Protivnik je motor na lakoj težini (elementarni matovi).
    case vsEngine
}

struct ExerciseSpec: Codable, Equatable {
    let kind: ExerciseKind
    let title: String
    let hint: String
    let icon: String
    /// Samo za `.scripted`. Svi potezi, naizmenično beli/crni.
    let uciMoves: [String]?
    /// `nil` kod `.scripted` znači standardnu početnu poziciju.
    /// Kod `.vsEngine` je obavezan.
    let startFEN: String?
    let solvedMessage: String?
    let wrongMessage: String?
    let playingPrompt: String?
    /// Ako je zadat, kartica dobija bedž „Mat u N".
    let mateIn: Int?
}

enum LessonBlock: Codable, Equatable {
    case heading(text: String, icon: String)
    case paragraph(text: String)
    case bullets(items: [BulletItem])
    case box(style: BoxStyle, icon: String, title: String, text: String)
    case quote(text: String, author: String)
    case pieceRow(piece: String, name: String, count: String)
    case numberedRule(number: Int, title: String, text: String)
    case pieceValueTable(rows: [PieceValueRow])
    case board(fen: String, caption: String, interactive: Bool)
    case explorer
    case exercise(ExerciseSpec)

    private enum CodingKeys: String, CodingKey {
        case type, text, icon, items, style, title, author, piece, name, count
        case number, rows, fen, caption, interactive
        case kind, hint, uciMoves, startFEN, solvedMessage, wrongMessage
        case playingPrompt, mateIn
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let type = try c.decode(String.self, forKey: .type)

        switch type {
        case "heading":
            self = .heading(text: try c.decode(String.self, forKey: .text),
                            icon: try c.decode(String.self, forKey: .icon))
        case "paragraph":
            self = .paragraph(text: try c.decode(String.self, forKey: .text))
        case "bullets":
            self = .bullets(items: try c.decode([BulletItem].self, forKey: .items))
        case "box":
            self = .box(style: try c.decode(BoxStyle.self, forKey: .style),
                        icon: try c.decode(String.self, forKey: .icon),
                        title: try c.decode(String.self, forKey: .title),
                        text: try c.decode(String.self, forKey: .text))
        case "quote":
            self = .quote(text: try c.decode(String.self, forKey: .text),
                          author: try c.decode(String.self, forKey: .author))
        case "pieceRow":
            self = .pieceRow(piece: try c.decode(String.self, forKey: .piece),
                             name: try c.decode(String.self, forKey: .name),
                             count: try c.decode(String.self, forKey: .count))
        case "numberedRule":
            self = .numberedRule(number: try c.decode(Int.self, forKey: .number),
                                 title: try c.decode(String.self, forKey: .title),
                                 text: try c.decode(String.self, forKey: .text))
        case "pieceValueTable":
            self = .pieceValueTable(rows: try c.decode([PieceValueRow].self, forKey: .rows))
        case "board":
            self = .board(fen: try c.decode(String.self, forKey: .fen),
                          caption: try c.decode(String.self, forKey: .caption),
                          interactive: try c.decode(Bool.self, forKey: .interactive))
        case "explorer":
            self = .explorer
        case "exercise":
            self = .exercise(ExerciseSpec(
                kind:          try c.decode(ExerciseKind.self, forKey: .kind),
                title:         try c.decode(String.self, forKey: .title),
                hint:          try c.decode(String.self, forKey: .hint),
                icon:          try c.decode(String.self, forKey: .icon),
                uciMoves:      try c.decodeIfPresent([String].self, forKey: .uciMoves),
                startFEN:      try c.decodeIfPresent(String.self, forKey: .startFEN),
                solvedMessage: try c.decodeIfPresent(String.self, forKey: .solvedMessage),
                wrongMessage:  try c.decodeIfPresent(String.self, forKey: .wrongMessage),
                playingPrompt: try c.decodeIfPresent(String.self, forKey: .playingPrompt),
                mateIn:        try c.decodeIfPresent(Int.self, forKey: .mateIn)))
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type, in: c,
                debugDescription: "Nepoznat tip bloka '\(type)'. Dodaj ga u LessonBlock ili ispravi JSON.")
        }
    }

    func encode(to encoder: any Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .heading(let text, let icon):
            try c.encode("heading", forKey: .type)
            try c.encode(text, forKey: .text); try c.encode(icon, forKey: .icon)
        case .paragraph(let text):
            try c.encode("paragraph", forKey: .type); try c.encode(text, forKey: .text)
        case .bullets(let items):
            try c.encode("bullets", forKey: .type); try c.encode(items, forKey: .items)
        case .box(let style, let icon, let title, let text):
            try c.encode("box", forKey: .type); try c.encode(style, forKey: .style)
            try c.encode(icon, forKey: .icon); try c.encode(title, forKey: .title)
            try c.encode(text, forKey: .text)
        case .quote(let text, let author):
            try c.encode("quote", forKey: .type); try c.encode(text, forKey: .text)
            try c.encode(author, forKey: .author)
        case .pieceRow(let piece, let name, let count):
            try c.encode("pieceRow", forKey: .type); try c.encode(piece, forKey: .piece)
            try c.encode(name, forKey: .name); try c.encode(count, forKey: .count)
        case .numberedRule(let number, let title, let text):
            try c.encode("numberedRule", forKey: .type); try c.encode(number, forKey: .number)
            try c.encode(title, forKey: .title); try c.encode(text, forKey: .text)
        case .pieceValueTable(let rows):
            try c.encode("pieceValueTable", forKey: .type); try c.encode(rows, forKey: .rows)
        case .board(let fen, let caption, let interactive):
            try c.encode("board", forKey: .type); try c.encode(fen, forKey: .fen)
            try c.encode(caption, forKey: .caption); try c.encode(interactive, forKey: .interactive)
        case .explorer:
            try c.encode("explorer", forKey: .type)
        case .exercise(let spec):
            try c.encode("exercise", forKey: .type)
            try c.encode(spec.kind, forKey: .kind); try c.encode(spec.title, forKey: .title)
            try c.encode(spec.hint, forKey: .hint); try c.encode(spec.icon, forKey: .icon)
            try c.encodeIfPresent(spec.uciMoves, forKey: .uciMoves)
            try c.encodeIfPresent(spec.startFEN, forKey: .startFEN)
            try c.encodeIfPresent(spec.solvedMessage, forKey: .solvedMessage)
            try c.encodeIfPresent(spec.wrongMessage, forKey: .wrongMessage)
            try c.encodeIfPresent(spec.playingPrompt, forKey: .playingPrompt)
            try c.encodeIfPresent(spec.mateIn, forKey: .mateIn)
        }
    }
}
```

- [ ] **Step 4: Dodati fajl u testni paket**

U `Package.swift`, u `sources:` niz, odmah posle `"Models/ChessPuzzle.swift",`:

```swift
                "Models/LessonContent.swift",
```

- [ ] **Step 5: Registrovati fajl u Xcode target i DOKAZATI da se kompajlira**

Dodavanje u `Package.swift` čini fajl vidljivim samo testovima. Aplikacija ga ne
kompajlira dok ne uđe i u `project.pbxproj`. Ako se ovo preskoči, `swift test`
prolazi, `xcodebuild` prolazi (niko ga još ne referencira), a puklo bi tek u
Task-u 3 — daleko od uzroka. Tačno to se desilo u Fazi 2 sa `PuzzleRepository.swift`.

Dodati po postojećem ručnom obrascu (isti kao `StatsManager.swift`): `PBXFileReference`
+ `PBXBuildFile` + unos u grupu `Models` + unos u `PBXSourcesBuildPhase`.

Zatim dokazati:
```bash
echo "OVO NIJE SWIFT @@@" >> Chessko/Models/LessonContent.swift
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build 2>&1 | grep -c "error:"
git checkout Chessko/Models/LessonContent.swift
```
Expected: broj **veći od nule**. Ako je nula, fajl NIJE u target-u — STATI i prijaviti.

- [ ] **Step 6: Pokrenuti testove**

Run: `swift test`
Expected: **30 testova prolazi** (27 + 3 nova).

- [ ] **Step 7: Commit**

```bash
git add Chessko/Models/LessonContent.swift Package.swift Chessko.xcodeproj/project.pbxproj Tests/ChesskoEngineTests/LessonContentTests.swift
git commit -m "feat: sema blokova sadrzaja lekcije

11 tipova blokova pokriva sve sto postojece 4 lekcije stvarno koriste.
Nepoznat tip BACA gresku umesto da se tiho preskoci — preskakanje bi dalo
lekciju sa rupom koju niko ne primeti."
```

---

## Task 2: Generator i 32 JSON fajla

**Files:**
- Create: `build_lesson_json.py`
- Create: `Chessko/Content/lessons/*.json` (32 fajla, generisana, commit-uju se)
- Test: `Tests/ChesskoEngineTests/LessonContentTests.swift` (dopuna)

**Interfaces:**
- Consumes: `LessonDocument` iz Task-a 1
- Produces: 32 JSON fajla imenovana `<lessonId>.<lang>.json`, gde je `lessonId` jedan od `board-and-pieces`, `openings`, `middlegame`, `endgame`, a `lang` jedan od `sr, en, fr, de, it, ru, zh-Hans, hi`.

- [ ] **Step 1: Napisati generator**

Create `build_lesson_json.py` u korenu. Skripta drži **strukturu** lekcija (redosled blokova + srpski ključ svakog teksta), a **prevode vadi iz `Chessko/Localizable.xcstrings`** po tom ključu. Prekucavanje 173 × 8 = 1384 stringa se ne radi ručno.

Kostur skripte i potpuno urađen primer prvog dela Lekcije 1:

```python
#!/usr/bin/env python3
"""Generise Chessko/Content/lessons/<id>.<lang>.json iz strukture zapisane
ovde + prevoda koji vec postoje u Chessko/Localizable.xcstrings.

Pokretanje:  python3 build_lesson_json.py
"""
import json, pathlib, sys

ROOT = pathlib.Path(__file__).parent
CATALOG = ROOT / "Chessko" / "Localizable.xcstrings"
OUT_DIR = ROOT / "Chessko" / "Content" / "lessons"
LANGS = ["sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi"]

_catalog = json.loads(CATALOG.read_text())["strings"]

def T(key):
    """Marker za tekst koji se prevodi. Vraca sam kljuc; `render` ga zameni."""
    return ("__T__", key)

def render(value, lang):
    """Rekurzivno zamenjuje T(...) markere prevodom na `lang`."""
    if isinstance(value, tuple) and len(value) == 2 and value[0] == "__T__":
        key = value[1]
        entry = _catalog.get(key)
        if entry is None:
            sys.exit(f"GRESKA: kljuc nije u katalogu: {key!r}")
        loc = entry.get("localizations", {}).get(lang)
        if loc is None:
            sys.exit(f"GRESKA: kljuc {key!r} nema jezik {lang}")
        return loc["stringUnit"]["value"]
    if isinstance(value, dict):
        return {k: render(v, lang) for k, v in value.items()}
    if isinstance(value, list):
        return [render(v, lang) for v in value]
    return value

# ─────────────────────────────────────────────────────────────────────────
# LEKCIJA 1 — Tabla, figure i kretanje
# Izvor: LessonDetailView.swift, linije 66-313. Redosled blokova mora da
# prati izvor tacno; svaki tekst se navodi kao T("<srpski kljuc iz kataloga>").
# ─────────────────────────────────────────────────────────────────────────
LESSON_1 = {
    "id": "board-and-pieces",
    "title": T("Tabla, figure i kretanje"),
    "subtitle": T("Osnove šaha za početnike"),
    "icon": "square.grid.3x3.fill",
    "blocks": [
        {"type": "box", "style": "info", "icon": "quote.opening",
         "title": T("Kapablanka piše"),
         "text": T("\"Prva stvar koju učenik treba da uradi jeste da upozna snagu figura. Ovo se najlakše postiže učenjem kako se brzo postiže šah-mat.\"")},
        {"type": "paragraph",
         "text": T("Šah se igra na tabli od **64 polja** naizmenično svetle i tamne boje. Uvek zapamti: **donje desno polje mora biti svetlo**. Svaki igrač počinje sa **16 figura**.")},
        # ... nastaviti tacno po izvoru do kraja lekcije 1 ...
    ],
}

LESSON_2 = { "id": "openings", ... }
LESSON_3 = { "id": "middlegame", ... }
LESSON_4 = { "id": "endgame", ... }

LESSONS = [LESSON_1, LESSON_2, LESSON_3, LESSON_4]

def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    written = 0
    for lesson in LESSONS:
        for lang in LANGS:
            doc = render(lesson, lang)
            doc["language"] = lang
            path = OUT_DIR / f"{lesson['id']}.{lang}.json"
            path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
            written += 1
    print(f"Zapisano {written} fajlova u {OUT_DIR}")

if __name__ == "__main__":
    main()
```

> **Zašto struktura četiri lekcije nije prepisana u ovaj plan.** To bi bilo ~1500 linija doslovnog prekucavanja onoga što već stoji u `LessonDetailView.swift`, i prekucavanje bi samo dodalo još jedno mesto na kome se greši. Umesto toga plan daje tri stvari koje taj posao čine mehaničkim i **proverljivim**: potpuno urađen primer formata iznad, tabelu preslikavanja svake komponente u blok ispod, i proveru pokrivenosti u Step-u 3 koja pada ako ijedan blok ispadne. Izvor istine je Swift fajl koji se migrira.

**Kako preneti strukturu:** čitaj `LessonDetailView.swift` odozgo nadole po lekciji i za svaki poziv komponente upiši odgovarajući blok:

| u Swift-u | blok u JSON-u |
|---|---|
| `L_SectionHeader(icon: I, title: X, color:)` | `{"type":"heading","text":T(X),"icon":I}` |
| `L_Para(X)` | `{"type":"paragraph","text":T(X)}` |
| uzastopni `L_Bullet(icon: I, color: C, title: A, text: B)` | jedan `bullets` blok sa svim susednim stavkama; `style` je `null` kad je `C` = `DS.accent`/`lesson.accentColor`, a `"warning"` kad je `C` = `DS.danger` (tri stavke „Tipične greške" u lekciji 2) |
| `L_Box(icon: I, color: .yellow, title: A, text: B)` | `{"type":"box","style":"rule",...}` |
| `L_Box(icon: I, color: .red, ...)` | `style: "warning"` |
| `L_Box` sa `lesson.accentColor` | `style: "info"` |
| `L_PieceRow(type: .knight, name: A, count: B)` | `{"type":"pieceRow","piece":"knight","name":T(A),"count":T(B)}` |
| `L_NumberedRule(number: N, title: A, text: B)` | `{"type":"numberedRule","number":N,...}` |
| `L_PieceValueTable()` | `{"type":"pieceValueTable","rows":[...]}` — vrednosti prepisati iz same komponente |
| piece explorer (picker + `BoardView`) | `{"type":"explorer"}` |
| `MateExerciseCard(fen:title:hint:icon:color:)` | `exercise` sa `kind:"vsEngine"`, `startFEN` = `fen` |
| `OpeningExerciseCard(line:)` / `MatePuzzleCard(line:)` | `exercise` sa `kind:"scripted"`, `uciMoves` = `line.uciMoves` |

`L_OpeningCard` se ne prenosi — mrtav je kod.

- [ ] **Step 2: Pokrenuti generator**

```bash
python3 build_lesson_json.py
ls Chessko/Content/lessons/ | wc -l
```
Expected: `32`.

- [ ] **Step 3: Provera pokrivenosti — ovo je glavna zaštita od izostavljanja**

Prenošenje 1238 linija ručno *hoće* da izgubi po neki blok. Zato se ne oslanjamo na pažnju nego na proveru: **svaki od 173 ključa koje `LessonDetailView.swift` i `LearnView.swift` koriste mora da se pojavi u tačno jednom lekcijskom JSON-u.**

```bash
python3 - <<'PY'
import json, re, pathlib
cat = json.loads(pathlib.Path('Chessko/Localizable.xcstrings').read_text())['strings']

used = set()
for p in ['Chessko/Views/LessonDetailView.swift', 'Chessko/Views/LearnView.swift']:
    for line in pathlib.Path(p).read_text().splitlines():
        if line.strip().startswith('//'):
            continue
        for lit in re.findall(r'"((?:[^"\\]|\\.)*)"', line):
            k = lit.replace('\\"', '"').replace('\\\\', '\\')
            if k in cat:
                used.add(k)

srps = {}
for f in pathlib.Path('Chessko/Content/lessons').glob('*.sr.json'):
    doc = json.loads(f.read_text())
    srps[f.name] = json.dumps(doc, ensure_ascii=False)

missing = [k for k in used if not any(json.dumps(k, ensure_ascii=False)[1:-1] in blob
                                      for blob in srps.values())]
print("koriscenih kljuceva:", len(used))
print("NEPRENESENIH:", len(missing))
for m in sorted(missing):
    print("   ", repr(m[:70]))
PY
```
Expected: `NEPRENESENIH: 0`. Ako nije nula, ispisani ključevi su tačno blokovi koji su ispali — dodati ih i ponoviti.

- [ ] **Step 4: Dodati test koji dekodira sve generisane fajlove**

U `Tests/ChesskoEngineTests/LessonContentTests.swift` dodati:

```swift
@Test func everyGeneratedLessonFileDecodes() throws {
    let dir = URL(fileURLWithPath: "Chessko/Content/lessons")
    let files = try FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)
        .filter { $0.pathExtension == "json" }
        .sorted { $0.lastPathComponent < $1.lastPathComponent }

    #expect(files.count == 32, "Ocekivano 4 lekcije × 8 jezika")

    var perLesson: [String: [Int]] = [:]
    for file in files {
        let doc = try JSONDecoder().decode(LessonDocument.self, from: Data(contentsOf: file))
        #expect(!doc.blocks.isEmpty, "\(file.lastPathComponent) nema nijedan blok")
        #expect(!doc.title.isEmpty, "\(file.lastPathComponent) nema naslov")
        perLesson[doc.id, default: []].append(doc.blocks.count)
    }

    // Svih 8 jezika iste lekcije mora da ima ISTI broj blokova — razlicit broj
    // znaci da je prevod negde ispao ili da je struktura razlicito generisana.
    #expect(perLesson.count == 4)
    for (id, counts) in perLesson {
        #expect(counts.count == 8, "\(id) nema svih 8 jezika")
        #expect(Set(counts).count == 1, "\(id) ima razlicit broj blokova po jeziku: \(counts)")
    }
}
```

- [ ] **Step 5: Pokrenuti testove**

Run: `swift test`
Expected: **31 testova prolazi**.

- [ ] **Step 6: Commit**

```bash
git add build_lesson_json.py Chessko/Content Tests/ChesskoEngineTests/LessonContentTests.swift
git commit -m "feat: generator i JSON sadrzaj za 4 lekcije na 8 jezika

Struktura lekcija je zapisana u build_lesson_json.py, prevodi se vade iz
Localizable.xcstrings po kljucu — 1384 stringa se ne prekucavaju rucno.
Provera pokrivenosti tvrdi da nijedan od 173 koriscena kljuca nije ispao."
```

---

## Task 3: Registracija sadržaja i `LessonRepository`

**Files:**
- Modify: `Chessko.xcodeproj/project.pbxproj` (folder-referenca, 4 reda)
- Create: `Chessko/Logic/LessonRepository.swift`
- Test: `Tests/ChesskoEngineTests/LessonContentTests.swift` (dopuna)

**Interfaces:**
- Consumes: `LessonDocument` (Task 1), JSON fajlovi (Task 2)
- Produces: `LessonRepository.shared`, `func lesson(id: String, language: String) -> LessonDocument?`, `static let lessonOrder: [String]`

- [ ] **Step 1: Dodati `Content/` kao folder-referencu**

`Content/` ide u projekat kao **jedna folder-referenca**, ne kao 32 zasebna unosa. Razlog: nova lekcija tada ne traži nikakvu izmenu `project.pbxproj`, a upravo je propuštena registracija sakrila `PuzzleRepository.swift` u Fazi 2.

U `Chessko.xcodeproj/project.pbxproj` dodati četiri reda po postojećem ručnom obrascu (isti kao za `puzzles.sqlite`, ali `lastKnownFileType = folder`):

1. u `PBXBuildFile` sekciju:
```
		10CA7A1000000000000000C1 /* Content in Resources */ = {isa = PBXBuildFile; fileRef = 10CA7A1000000000000000C2 /* Content */; };
```
2. u `PBXFileReference` sekciju:
```
		10CA7A1000000000000000C2 /* Content */ = {isa = PBXFileReference; lastKnownFileType = folder; path = Content; sourceTree = "<group>"; };
```
3. u grupu `Chessko` (pored `puzzles.sqlite`):
```
			10CA7A1000000000000000C2 /* Content */,
```
4. u `PBXResourcesBuildPhase` `files` niz:
```
				10CA7A1000000000000000C1 /* Content in Resources */,
```

- [ ] **Step 2: Dokazati da sadržaj stvarno stiže u aplikaciju**

```bash
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build 2>&1 | grep -E "error:|BUILD"
APP=$(xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -showBuildSettings 2>/dev/null \
  | awk '/BUILT_PRODUCTS_DIR/{print $3}')/Chessko.app
ls "$APP/Content/lessons" | wc -l
```
Expected: `BUILD SUCCEEDED` i `32`. Ako je 0 ili direktorijum ne postoji, folder-referenca nije primenjena — STATI i prijaviti.

- [ ] **Step 3: Napisati repozitorijum**

Create `Chessko/Logic/LessonRepository.swift`:

```swift
import Foundation

// MARK: - Lesson Repository
//
// Ucitava lekcije iz `Content/lessons/<id>.<lang>.json` u bundle-u. `Content`
// je u projektu FOLDER-REFERENCA, pa se cela struktura direktorijuma prenosi u
// .app — nova lekcija ne trazi izmenu `project.pbxproj`.
//
// `@MainActor` iz istog razloga kao `PuzzleRepository`: kes nije zasticen, a
// ceo pozivni graf je ionako na glavnoj niti. Ovako je pozadinski pozivalac
// greska pri kompajliranju, a ne tiha trka.

@MainActor
final class LessonRepository {
    static let shared = LessonRepository()

    /// Redosled kojim se lekcije prikazuju na ekranu Učenje.
    static let lessonOrder = ["board-and-pieces", "openings", "middlegame", "endgame"]

    private var cache: [String: LessonDocument] = [:]

    /// Lekcija na trazenom jeziku. Ako tog jezika nema (nove lekcije idu samo
    /// sr+en), pada na engleski pa na srpski — bolje lekcija na drugom jeziku
    /// nego prazan ekran.
    func lesson(id: String, language: String) -> LessonDocument? {
        for candidate in [language, "en", "sr"] {
            let key = "\(id).\(candidate)"
            if let cached = cache[key] { return cached }
            guard let url = Bundle.main.url(forResource: key, withExtension: "json",
                                            subdirectory: "Content/lessons"),
                  let data = try? Data(contentsOf: url),
                  let doc = try? JSONDecoder().decode(LessonDocument.self, from: data)
            else { continue }
            cache[key] = doc
            return doc
        }
        return nil
    }

    /// Sve lekcije redom, na trazenom jeziku. Koristi ekran Učenje za listu.
    func allLessons(language: String) -> [LessonDocument] {
        Self.lessonOrder.compactMap { lesson(id: $0, language: language) }
    }
}
```

- [ ] **Step 4: Dodati fajl u Xcode target i dokazati da se kompajlira**

Dodati `LessonRepository.swift` u `project.pbxproj` po istom ručnom obrascu kao `PuzzleRepository.swift` (fileRef + buildFile + grupa `Logic` + `PBXSourcesBuildPhase`).

Zatim **dokazati** da je stvarno u target-u:
```bash
echo "OVO NIJE SWIFT @@@" >> Chessko/Logic/LessonRepository.swift
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build 2>&1 | grep -c "error:"
git checkout Chessko/Logic/LessonRepository.swift
```
Expected: broj veći od nule. Ako je nula, fajl NIJE u target-u — STATI i prijaviti.

- [ ] **Step 5: Testovi i commit**

Run: `swift test` → 31 prolazi (repozitorijum zavisi od `Bundle.main` pa se ne testira u paketu; pokrivenost daje Step 2 i Step 4).

```bash
git add Chessko/Logic/LessonRepository.swift Chessko.xcodeproj/project.pbxproj
git commit -m "feat: LessonRepository i Content/ kao folder-referenca

Folder-referenca znaci da nova lekcija NE trazi izmenu project.pbxproj —
propustena registracija je u Fazi 2 sakrila PuzzleRepository.swift od
aplikacije iako su i swift test i xcodebuild prolazili."
```

---

## Task 4: `LessonRenderer`

**Files:**
- Create: `Chessko/Views/LessonRenderer.swift`
- Modify: `Chessko/Views/LessonDetailView.swift` (samo premeštanje `L_*` komponenti)

**Interfaces:**
- Consumes: `LessonBlock`, `ExerciseSpec` (Task 1)
- Produces: `struct LessonRenderer: View { let blocks: [LessonBlock] }`

- [ ] **Step 1: Premestiti komponente za prikaz**

Iz `LessonDetailView.swift` premestiti u `LessonRenderer.swift`, **nepromenjene**: `L_SectionHeader`, `mdText(_:)`, `L_Para`, `L_Bullet`, `L_Box`, `L_PieceRow`, `L_NumberedRule`, `L_PieceValueTable`, `MatePuzzleCard`, `OpeningExerciseCard`, `MateExerciseCard`. Skinuti `private` sa onih koje renderer koristi. **`L_OpeningCard` obrisati** — mrtav kod od 2026-06-24.

- [ ] **Step 2: Napisati renderer**

```swift
import SwiftUI

// MARK: - Lesson Renderer
//
// Jedino mesto koje zna kako se blok sadrzaja crta. Nova lekcija je nov JSON;
// nov tip bloka je jedna grana ovde plus jedan slucaj u `LessonBlock`.

struct LessonRenderer: View {
    let blocks: [LessonBlock]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                view(for: block)
            }
        }
    }

    @ViewBuilder
    private func view(for block: LessonBlock) -> some View {
        switch block {
        case .heading(let text, let icon):
            L_SectionHeader(icon: icon, title: text, color: DS.accent)

        case .paragraph(let text):
            L_Para(text)

        case .bullets(let items):
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                L_Bullet(icon: item.icon,
                         color: item.style.map(color(for:)) ?? DS.accent,
                         title: item.title, text: item.text)
            }

        case .box(let style, let icon, let title, let text):
            L_Box(icon: icon, color: color(for: style), title: title, text: text)

        case .quote(let text, let author):
            L_Box(icon: "quote.opening", color: DS.accent, title: author, text: text)

        case .pieceRow(let piece, let name, let count):
            L_PieceRow(type: pieceType(piece), name: name, count: count)

        case .numberedRule(let number, let title, let text):
            L_NumberedRule(number: number, color: DS.accent, title: title, text: text)

        case .pieceValueTable(let rows):
            L_PieceValueTable(rows: rows)

        case .board(let fen, let caption, _):
            LessonStaticBoard(fen: fen, caption: caption)

        case .explorer:
            LessonPieceExplorer()

        case .exercise(let spec):
            exerciseView(spec)
        }
    }

    @ViewBuilder
    private func exerciseView(_ spec: ExerciseSpec) -> some View {
        switch spec.kind {
        case .vsEngine:
            // `startFEN` je kod vsEngine obavezan; prazan string bi dao praznu
            // tablu, pa se blok preskace uz vidljivu poruku umesto tihe rupe.
            if let fen = spec.startFEN {
                MateExerciseCard(fen: fen, title: spec.title, hint: spec.hint,
                                 icon: spec.icon, color: DS.accent)
                    .padding(.horizontal, 16).padding(.bottom, 16)
            } else {
                LessonBlockError(message: "Vežba '\(spec.title)' nema startFEN")
            }

        case .scripted:
            if let moves = spec.uciMoves {
                let line = OpeningLine(
                    name: spec.title, uciMoves: moves, hint: spec.hint,
                    icon: spec.icon, accentColor: DS.accent,
                    solvedMessage: spec.solvedMessage ?? Loc("Bravo! Otvaranje savladano! ✓"),
                    wrongMessage:  spec.wrongMessage  ?? Loc("Pogrešan potez — pokušaj ponovo."),
                    playingPrompt: spec.playingPrompt,
                    startFEN: spec.startFEN)
                if let mateIn = spec.mateIn {
                    // PAZNJA: `MatePuzzleCard` NE prima `OpeningLine` nego
                    // raspakovane parametre (init je fen/moves/title/hint/
                    // icon/accentColor/mateIn), i `fen` mu NIJE opcion.
                    // `OpeningExerciseCard` prima `line:`. Potpisi se
                    // razlikuju — provereno u izvoru.
                    if let fen = spec.startFEN {
                        MatePuzzleCard(fen: fen, moves: moves, title: spec.title,
                                       hint: spec.hint, icon: spec.icon,
                                       accentColor: DS.accent, mateIn: mateIn)
                            .padding(.horizontal, 16).padding(.bottom, 16)
                    } else {
                        LessonBlockError(message: "Zadatak '\(spec.title)' ima mateIn ali nema startFEN")
                    }
                } else {
                    OpeningExerciseCard(line: line)
                        .padding(.horizontal, 16).padding(.bottom, 16)
                }
            } else {
                LessonBlockError(message: "Vežba '\(spec.title)' nema uciMoves")
            }
        }
    }

    private func color(for style: BoxStyle) -> Color {
        switch style {
        case .rule:    return DS.warning
        case .warning: return DS.danger
        case .info:    return DS.accent
        }
    }

    private func pieceType(_ raw: String) -> PieceType {
        switch raw {
        case "pawn":   return .pawn
        case "knight": return .knight
        case "bishop": return .bishop
        case "rook":   return .rook
        case "queen":  return .queen
        default:       return .king
        }
    }
}

/// Vidljiva poruka umesto tihe rupe kad je blok nepotpun. Sadrzaj je van koda,
/// pa greska u JSON-u ne sme da se izgubi bez traga.
struct LessonBlockError: View {
    let message: String
    var body: some View {
        Text(message)
            .font(.dsCaption)
            .foregroundStyle(DS.danger)
            .padding(.horizontal, 20).padding(.bottom, 8)
    }
}
```

- [ ] **Step 3: Napisati dve pomoćne komponente**

`LessonStaticBoard` (statična tabla iz FEN-a) i `LessonPieceExplorer` (postojeći piece explorer izdvojen iz `LessonDetailView`-a, sa `piecePicker`-om i `LearnViewModel`-om). Izdvojiti ih iz `LessonDetailView.swift` linija 66–313 **nepromenjene po izgledu**; menja se samo to što više ne stoje ugrađene u lekciju 1 nego se zovu iz renderera.

`L_PieceValueTable` sada prima redove umesto da ih drži hardkodirane — potpis postaje `L_PieceValueTable(rows: [PieceValueRow])`, a telo iterira `rows` umesto ugrađene liste.

- [ ] **Step 4: Build**

```bash
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCEEDED`. (`LessonRenderer.swift` dodati u `project.pbxproj` i **dokazati** injekcijom sintaksne greške, kao u Task-u 3 Step 4.)

- [ ] **Step 5: Commit**

```bash
git add Chessko/Views/LessonRenderer.swift Chessko/Views/LessonDetailView.swift Chessko.xcodeproj/project.pbxproj
git commit -m "feat: LessonRenderer mapira blokove u postojece komponente

Komponente prikaza premestene nepromenjene; L_OpeningCard obrisan (mrtav
kod od 2026-06-24). Nepotpun blok daje VIDLJIVU poruku, ne tihu rupu."
```

---

## Task 5: `LessonDetailView` i `LearnView` na repozitorijum

**Files:**
- Modify: `Chessko/Views/LessonDetailView.swift` (1238 → ~120 linija)
- Modify: `Chessko/Views/LearnView.swift`

- [ ] **Step 1: Svesti `LessonDetailView` na renderer**

Ceo sadržaj lekcija (linije 66–627) se briše. Ostaje zaglavlje lekcije i:

```swift
struct LessonDetailView: View {
    let lessonId: String
    @Environment(\.dismiss) private var dismiss
    private var localization = LocalizationManager.shared

    private var document: LessonDocument? {
        LessonRepository.shared.lesson(id: lessonId,
                                       language: LocalizationManager.shared.effectiveCode)
    }

    var body: some View {
        ScrollView {
            if let document {
                VStack(alignment: .leading, spacing: 0) {
                    lessonHeader(document)
                    LessonRenderer(blocks: document.blocks)
                }
            } else {
                // Sadrzaj je van koda; ako fajl nedostaje iz bundle-a korisnik
                // mora da vidi zasto, a ne prazan ekran.
                Text(Loc("Lekcija nije dostupna"))
                    .font(.dsBody)
                    .foregroundStyle(DS.inkMuted)
                    .padding(40)
            }
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaPadding(.bottom, 24)
    }
}
```

Nov string u `build_localizations.py`:
```python
add("Lekcija nije dostupna", "Lesson unavailable", "Leçon indisponible", "Lektion nicht verfügbar", "Lezione non disponibile", "Урок недоступен", "课程不可用", "पाठ उपलब्ध नहीं")
```

- [ ] **Step 2: `LearnView` čita listu iz repozitorijuma**

`LessonInfo.all` se briše. Lista se gradi iz `LessonRepository.shared.allLessons(language:)`, a kartica uzima `title`, `subtitle` i `icon` iz dokumenta. `accentColor` se ne čita iz JSON-a — sve četiri su `DS.accent` od Faze 1.

- [ ] **Step 3: Build i vizuelna uporedba**

```bash
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build 2>&1 | grep -E "error:|BUILD"
```

Zatim snimiti sve četiri lekcije u **obe teme** i uporediti sa izgledom pre faze. Sintetički klikovi u simulatoru **ne rade** (provereno u Fazama 0, 1 i 2 — ni plutajući tab bar ni obična dugmad), pa se do lekcije stiže determinističkim putem: privremeno zakucati `LearnView` da otvara traženu lekciju, snimiti, pa **vratiti izmenu i potvrditi da je radno stablo čisto**.

Expected: nema vizuelne razlike u odnosu na pre. Svaka razlika se prijavljuje.

- [ ] **Step 4: Provera da tekst lekcija više nije u Swift-u**

```bash
wc -l Chessko/Views/LessonDetailView.swift
grep -c "L_Para\|L_Bullet\|L_Box\|L_SectionHeader" Chessko/Views/LessonDetailView.swift
```
Expected: ispod 150 linija; nula pogodaka (komponente su sada u `LessonRenderer.swift`).

- [ ] **Step 5: Commit**

```bash
git add Chessko/Views/LessonDetailView.swift Chessko/Views/LearnView.swift Chessko/Localizable.xcstrings build_localizations.py
git commit -m "refactor: LessonDetailView sveden na renderer

1238 -> ~120 linija. Tekst lekcija vise ne postoji u Swift kodu; LearnView
gradi listu iz LessonRepository-ja."
```

---

## Task 6: Čišćenje kataloga, provera i dokumentacija

**Files:**
- Modify: `build_localizations.py`
- Modify: `Chessko/Localizable.xcstrings` (regenerisan)
- Modify: `CLAUDE.md`

- [ ] **Step 1: Izbaciti sadržaj lekcija iz kataloga**

173 ključa koje su koristile lekcije sada žive u JSON-u. Iz `build_localizations.py` obrisati **samo one koje više nijedan Swift fajl ne koristi**:

```bash
python3 - <<'PY'
import json, re, pathlib
cat = json.loads(pathlib.Path('Chessko/Localizable.xcstrings').read_text())['strings']
srcs = list(pathlib.Path('Chessko').rglob('*.swift'))
text = "\n".join(p.read_text() for p in srcs)
unused = []
for k in cat:
    lit = k.replace('\\', '\\\\').replace('"', '\\"')
    if f'"{lit}"' not in text:
        unused.append(k)
print("kljuceva bez ijedne reference u Swift-u:", len(unused))
pathlib.Path('/tmp/unused-keys.txt').write_text("\n".join(sorted(unused)))
PY
```

Obrisati te `add(...)` linije iz `build_localizations.py`, pa:
```bash
python3 build_localizations.py
```

- [ ] **Step 2: Provera kataloga**

```bash
python3 - <<'PY'
import json, pathlib
d = json.loads(pathlib.Path('Chessko/Localizable.xcstrings').read_text())['strings']
langs = {'sr','en','fr','de','it','ru','zh-Hans','hi'}
bad = [k for k,v in d.items() if set(v.get('localizations',{})) != langs]
print("kljuceva:", len(d), "| bez svih 8 jezika:", len(bad))
PY
```
Expected: oko **236** ključeva (409 − 173), nula bez svih 8 jezika.

- [ ] **Step 3: Provera da nijedan UI string nije slučajno obrisan**

```bash
xcodebuild -project Chessko.xcodeproj -scheme Chessko \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build 2>&1 | grep -E "error:|BUILD"
python3 - <<'PY'
import json, re, pathlib
cat = set(json.loads(pathlib.Path('Chessko/Localizable.xcstrings').read_text())['strings'])
missing = set()
for p in pathlib.Path('Chessko').rglob('*.swift'):
    for line in p.read_text().splitlines():
        if line.strip().startswith('//'): continue
        for m in re.findall(r'Loc\("((?:[^"\\]|\\.)*)"\)', line):
            k = m.replace('\\"','"').replace('\\\\','\\')
            if k not in cat: missing.add((p.name, k))
print("Loc() poziva bez kljuca u katalogu:", len(missing))
for f,k in sorted(missing): print("   ", f, repr(k[:60]))
PY
```
Expected: `BUILD SUCCEEDED` i **0** poziva bez ključa. Svaki pogodak znači obrisan UI string — vratiti ga.

- [ ] **Step 4: Ažurirati `CLAUDE.md`**

Nova sekcija „Sadržaj lekcija": gde JSON živi, kako se regeneriše (`python3 build_lesson_json.py`), da je `Content/` **folder-referenca** pa nova lekcija ne traži izmenu `project.pbxproj`, kojih 11 tipova blokova postoji i gde se dodaje nov (`LessonBlock` + `LessonRenderer`), i da katalog od ove faze pokriva **samo UI**. U sekciji „Arhitektura" dopuniti stablo sa `Content/`. Changelog unos.

- [ ] **Step 5: Commit**

```bash
git add build_localizations.py Chessko/Localizable.xcstrings CLAUDE.md
git commit -m "chore: katalog prevoda sveden na UI, dokumentacija Faze 3

409 -> ~236 kljuceva; sadrzaj lekcija je od sad u Content/lessons/*.json.
Katalog vise ne raste sa sadrzajem, samo sa interfejsom."
```

---

## Završna provera faze

- [ ] `swift test` prolazi (27 postojećih + 4 nova = 31)
- [ ] `xcodebuild … build` → `** BUILD SUCCEEDED **`
- [ ] `git status --short` prazan
- [ ] `ls "$APP/Content/lessons" | wc -l` = **32** unutar izgrađenog `.app`
- [ ] `LessonDetailView.swift` ispod 150 linija, bez ijednog `L_*` poziva
- [ ] Katalog ~236 ključeva, svaki sa svih 8 jezika, nula `Loc()` poziva bez ključa
- [ ] Sve četiri lekcije vizuelno iste kao pre, u obe teme

## Rizici

- **Prenošenje 1238 linija ručno hoće da izgubi blok.** Protivmera je provera pokrivenosti u Task-u 2 Step 3 (svaki od 173 ključa mora da se pojavi u tačno jednom JSON-u) i test iz Task-a 2 Step 4 (svih 8 jezika iste lekcije mora da ima isti broj blokova). Ne oslanjati se na pažnju.
- **Folder-referenca se ponaša drugačije od pojedinačnih fajlova.** `Bundle.main.url(forResource:withExtension:subdirectory:)` traži `subdirectory: "Content/lessons"`; bez folder-reference bi radio i bez putanje. Zato Task 3 Step 2 broji fajlove unutar izgrađenog `.app`, a ne veruje build-u.
- **Brisanje ključeva iz kataloga može da obriše UI string.** Protivmera je Task 6 Step 3 — svaki `Loc()` poziv u Swift-u mora da ima ključ.
