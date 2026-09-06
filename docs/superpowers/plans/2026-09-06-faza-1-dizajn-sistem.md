# Faza 1 — Dizajn sistem: plan implementacije

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Uvesti jedan izvor istine za boje, tipografiju i razmake, prevesti sva tri taba na njega, i preurediti rasporede tako da nema mrtvog prostora — pre nego što se u Fazi 4 na toj osnovi gradi Put.

**Architecture:** Nov fajl `Chessko/Views/DesignSystem.swift` drži tokene (`DS`) i tipografsku skalu. Postojeći `Color.appBackground`, `Color(hex:)` i `Font.appFont` se zadržavaju — `appFont` ostaje jer nosi Mac varijantu veličina. Ekrani se prevode jedan po jedan, svaki je zaseban zadatak i zaseban commit.

**Tech Stack:** Swift 6.0, SwiftUI, Xcode 26.

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` (sekcije 5.6 i 6, Faza 1)

## Global Constraints

- **Swift 6.0**, iOS deployment target **18.0**, Xcode 26.
- **NIKADA ne pokretati `create_xcode_project.py`** — regeneriše `project.pbxproj` i ne zna za `chesskit-engine` SPM zavisnost; pokretanje obara Stockfish.
- **`project.pbxproj` se ne REGENERIŠE**, ali se sme hirurški dopuniti. Projekat
  nema sinhronizovane foldere — svih 33 Swift fajla su eksplicitno navedena, pa
  nov fajl mora da se doda ručno na 4 mesta (Task 1, Step 2). Obrazac je
  `EvalBarView.swift`. Nikakva druga izmena pbxproj-a nije dozvoljena.
- **Ne dirati `ChesskoAndroid/`.** Android prati u zasebnom ciklusu.
- **Akcent je fiksan** i ne zavisi od teme table (spec 5.6).
- **Boje table se NE menjaju.** `BoardTheme` (8 tema), `Color.squareLight/squareDark/boardBackground`, boja šaha `#EF4444`, oznaka poslednjeg poteza i legalnih poteza ostaju kakve jesu.
- **`PieceColor.white` / `PieceColor.black` nisu boje** nego strane u igri. Nikad ih ne menjati u tokene.
- **Mrtav prostor se preuređuje, ne popunjava.** Faza 1 ne dodaje nov sadržaj (statistika, streak, „Nastavi" dolaze u Fazi 4).
- UI izvorni jezik je **srpski**; svaki nov string ide u `build_localizations.py` sa 7 prevoda.
- Build: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
- Testovi: `swift test` (11 testova, ~85s). Motor se u ovoj fazi ne dira — testovi su regresiona provera.

## Tokeni (referenca za sve zadatke)

| Token | Svetla | Tamna | Za šta |
|---|---|---|---|
| `DS.accent` | `#2E4A8A` | `#7EA0E8` | akcije, izabrano stanje, aktivni tab |
| `DS.ground` | `#F2F3F7` | `#0E1428` | pozadina ekrana |
| `DS.surface` | `#FFFFFF` | `#161D33` | kartice |
| `DS.navBar` | `#FFFFFF` | `#131A2E` | nav bar, tab bar |
| `DS.fill` | `#E7EAF1` | `#1E2740` | čipovi, ispune, prigušene pozadine |
| `DS.line` | `#DFE3EC` | `#232C46` | linije i ivice |
| `DS.ink` | `#161A22` | `#EEF1F7` | osnovni tekst |
| `DS.inkMuted` | `#6B7280` | `#8B93A7` | sekundarni tekst |

---

## Pregled zadataka

| # | Zadatak | Fajlovi |
|---|---|---|
| 1 | Sloj tokena | `DesignSystem.swift` (nov) |
| 2 | Ekran Igra | `GameView`, `EvalBarView` |
| 3 | Ekran Zadaci | `PuzzleView` |
| 4 | Ekran Učenje | `LearnView`, `LessonDetailView` |
| 5 | Ostatak | `ContentView`, `ChessClockView`, `SettingsSheet` |
| 6 | Vizuelna provera | — |

---

## Task 1: Sloj tokena

Nema izmena na ekranima. Samo se uvodi rečnik koji naredni zadaci troše.

**Files:**
- Create: `Chessko/Views/DesignSystem.swift`
- Modify: `Chessko.xcodeproj/project.pbxproj` (4 reda — registracija novog fajla)

**Interfaces:**
- Consumes: `Color(hex:)` iz `Chessko/Views/SquareView.swift`, `Font.appFont(_:)` iz `Chessko/Logic/PlatformHelper.swift`
- Produces: `enum DS` (boje, `DS.Space`, `DS.Radius`) i `Font.ds*` skalu — koriste ih zadaci 2–5

- [ ] **Step 1: Napisati fajl**

Create `Chessko/Views/DesignSystem.swift`:

```swift
import SwiftUI

// MARK: - Dizajn tokeni
//
// Jedan izvor istine za boje, tipografiju i razmake.
// Pravac „Tiho i precizno" (spec 5.6): neutralne podloge, jedan uzdržan
// akcent izveden iz #17234f, tabla je jedina zasićena stvar na ekranu.
//
// Akcent je FIKSAN — ne menja se sa temom table. Boje vezane za tablu
// (polja, poslednji potez, legalni potezi, šah) i dalje dolaze iz BoardTheme.

extension Color {
    /// Boja koja se sama razrešava po svetloj/tamnoj temi.
    static func adaptive(light: String, dark: String) -> Color {
        Color(UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(Color(hex: dark))
                : UIColor(Color(hex: light))
        })
    }
}

enum DS {

    // MARK: Boje

    static let accent   = Color.adaptive(light: "#2E4A8A", dark: "#7EA0E8")
    static let ground   = Color.adaptive(light: "#F2F3F7", dark: "#0E1428")
    static let surface  = Color.adaptive(light: "#FFFFFF", dark: "#161D33")
    static let navBar   = Color.adaptive(light: "#FFFFFF", dark: "#131A2E")
    static let fill     = Color.adaptive(light: "#E7EAF1", dark: "#1E2740")
    static let line     = Color.adaptive(light: "#DFE3EC", dark: "#232C46")
    static let ink      = Color.adaptive(light: "#161A22", dark: "#EEF1F7")
    static let inkMuted = Color.adaptive(light: "#6B7280", dark: "#8B93A7")

    /// Semantičke boje — nose značenje, nisu ukras. Ostaju u boji i u
    /// dizajnu koji je inače neutralan.
    static let success = Color.adaptive(light: "#2F7D4F", dark: "#6FCF97")
    static let warning = Color.adaptive(light: "#B07D2B", dark: "#E0B252")
    static let danger  = Color.adaptive(light: "#B3261E", dark: "#F2857A")

    /// Zatamnjenje ispod modalnih preklopa (promocija, izbor boje, kraj
    /// partije). Namerno isto u obe teme — preklop je uvek taman.
    static let scrim   = Color.black.opacity(0.55)
    /// Tekst i ikone NA scrim-u. Namerno bela u obe teme.
    static let onScrim = Color.white

    // MARK: Razmaci

    enum Space {
        static let xs: CGFloat = 4
        static let s:  CGFloat = 8
        static let m:  CGFloat = 12
        static let l:  CGFloat = 16
        static let xl: CGFloat = 24
    }

    // MARK: Radijusi

    enum Radius {
        static let s: CGFloat = 8
        static let m: CGFloat = 12
        static let l: CGFloat = 16
    }
}

// MARK: - Tipografska skala
//
// Sve ide kroz Font.appFont da bi se zadržale uvećane veličine na Mac-u
// (vidi Chessko/Logic/PlatformHelper.swift).

extension Font {
    /// Naslov ekrana.
    static var dsTitle: Font { .appFont(.title2).weight(.bold) }
    /// Naslov sekcije ili kartice.
    static var dsHeading: Font { .appFont(.headline) }
    /// Osnovni tekst.
    static var dsBody: Font { .appFont(.subheadline) }
    /// Prigušen, sitan tekst — podnaslovi, oznake.
    static var dsCaption: Font { .appFont(.caption) }
    /// Cifre koje se poravnavaju u kolone (sat, eval, notacija).
    static var dsMono: Font { .system(.footnote, design: .monospaced) }
}
```

- [ ] **Step 2: Dodati fajl u `project.pbxproj`**

Projekat nema sinhronizovane foldere, pa se nov fajl NE pokupi sam. Dodati ga na
ista četiri mesta na kojima figurira `EvalBarView.swift` (linije 35, 91, 120, 309).

Koristiti ove ID-jeve (generisani za ovaj fajl, ne smeju se poklopiti s postojećim):

1. U `PBXBuildFile` sekciju, uz ostale (oko linije 35):
```
		DFF4981773CA469D8413546E /* DesignSystem.swift in Sources */ = {isa = PBXBuildFile; fileRef = 253C1D9E82F34FFA9CE161C1 /* DesignSystem.swift */; };
```

2. U `PBXFileReference` sekciju (oko linije 91):
```
		253C1D9E82F34FFA9CE161C1 /* DesignSystem.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = DesignSystem.swift; sourceTree = "<group>"; };
```

3. U `children` grupe `Views`, odmah uz `EvalBarView.swift` (oko linije 120):
```
				253C1D9E82F34FFA9CE161C1 /* DesignSystem.swift */,
```

4. U `files` liste `PBXSourcesBuildPhase` (oko linije 309):
```
				DFF4981773CA469D8413546E /* DesignSystem.swift in Sources */,
```

Posle izmene proveriti da su zagrade i dalje uravnotežene:
```bash
python3 -c "s=open('Chessko.xcodeproj/project.pbxproj').read(); print('balans:', s.count('{')-s.count('}'))"
```
Expected: `balans: 0`

- [ ] **Step 3: Build**

Run: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`

Expected: `** BUILD SUCCEEDED **`

Ako linker prijavi da `DS` nije pronađen, fajl nije stigao u Sources fazu — proveriti tačku 4.

- [ ] **Step 4: Testovi (regresiona provera)**

Run: `swift test`
Expected: 11 testova prolazi. Motor nije diran; ovo samo potvrđuje da ništa nije slučajno pomereno.

- [ ] **Step 5: Commit**

```bash
git add Chessko/Views/DesignSystem.swift Chessko.xcodeproj/project.pbxproj
git commit -m "feat: sloj dizajn tokena (boje, tipografska skala, razmaci)

Pravac 'Tiho i precizno' iz spec 5.6. Akcent je fiksan i nezavisan od
teme table. Nijedan ekran jos ne koristi tokene — to rade zadaci 2-5."
```

---

## Task 2: Ekran Igra

Najgušći ekran: 17 hardkodiranih boja, mrtav prostor ispod table, eval bar koji duplira informaciju iz kartice igrača, i placeholder `–` koji izgleda kao nedovršen UI.

**Files:**
- Modify: `Chessko/Views/GameView.swift`
- Modify: `Chessko/Views/EvalBarView.swift`

**Interfaces:**
- Consumes: `DS` i `Font.ds*` iz Task 1
- Produces: ništa novo

- [ ] **Step 1: Zameniti boje u GameView**

Tačna mesta i zamene:

| Linija (orijentaciono) | Zatečeno | Novo |
|---|---|---|
| 180 | `.foregroundStyle(.red.opacity(0.85))` | `.foregroundStyle(DS.danger)` |
| 355–356 | `Color.white.opacity(0.13)` / `Color.black.opacity(0.28)` | `DS.fill` (uslov po temi više ne treba — token to radi sam) |
| 377 | `isMateForPlayer(...) ? Color.yellow : Color.primary` | `isMateForPlayer(...) ? DS.warning : DS.ink` |
| 392 | `.fill(Color.green)` | `.fill(DS.success)` |
| 460, 540, 578 | `Color.black.opacity(0.55)` / `0.5` | `DS.scrim` |
| 468, 529, 545, 584, 598 | `.foregroundStyle(.white)` | `.foregroundStyle(DS.onScrim)` |
| 519–520 | `Color.white.opacity(0.18)` / `Color.black.opacity(0.35)` | `DS.onScrim.opacity(0.18)` |

**NE dirati** linije 314 i 325 — tamo `PieceColor.white` / `PieceColor.black` označavaju stranu u igri, ne boju.

- [ ] **Step 2: Preurediti portretni raspored — tabla veća, bez praznine**

U portretnoj grani (`else` blok, `ScrollView` oko linije 90), sadržaj je trenutno `VStack(spacing: 12)` sa `topHeader`, tablom, `bottomHeader`, kontrolama pregleda i istorijom poteza — a ispod svega ostaje 25–30% praznog ekrana na početku partije.

Izmene:
1. `VStack(spacing: 12)` → `VStack(spacing: DS.Space.m)`.
2. Na `ScrollView` dodati `.scrollBounceBehavior(.basedOnSize)` da se ne odbija kad sadržaj stane na ekran.
3. Ispod poslednjeg elementa u `VStack`-u dodati `Spacer(minLength: 0)`, a `VStack` staviti u `.frame(maxHeight: .infinity, alignment: .top)` — sadržaj ostaje pri vrhu, tabla dobija svu širinu, a prazan prostor prestaje da bude „rupa u sredini".
4. Ukloniti `.padding(.horizontal, 16)` gde postoji i zameniti sa `.padding(.horizontal, DS.Space.l)`.

Cilj je da tabla bude najveći element na ekranu i da ispod nje ne ostane veliki neobjašnjen prazan blok.

- [ ] **Step 3: Kartice igrača — skloniti placeholder `–`**

`topHeader` i `bottomHeader` prikazuju `–` kad nema pojedenih figura, što izgleda kao greška. Umesto toga: kad nema pojedenih figura, red se jednostavno ne prikazuje.

Poziv na `GameView.swift:397-401` glasi:

```swift
                CapturedPiecesView(
                    pieces: capturedPieces,
                    capturedByColor: color,
                    flyingCapture: viewModel.flyingCapture
                )
```

Obmotati ga uslovom:

```swift
                if !capturedPieces.isEmpty {
                    CapturedPiecesView(
                        pieces: capturedPieces,
                        capturedByColor: color,
                        flyingCapture: viewModel.flyingCapture
                    )
                }
```

Uz to, protivnik se ne zove po težini: `Text(viewModel.difficulty.label)` kao **ime** zameniti sa `Text(Loc("Računar"))`, a težina ostaje kao podnaslov ispod. Dodati `add("Računar", "Computer", "Ordinateur", "Computer", "Computer", "Компьютер", "电脑", "कंप्यूटर")` u `build_localizations.py` i pokrenuti `python3 build_localizations.py`.

- [ ] **Step 4: Eval bar — jedan prikaz umesto dva**

Ista informacija stoji dvaput: kao tanka traka levo od table i kao brojčani `pill` u kartici igrača. Zadržati **traku**, ukloniti duplirani broj iz kartice igrača (jer traka je stalno vidljiva i uz tablu je), a traku učiniti čitljivijom.

U `Chessko/Views/EvalBarView.swift`:
1. `.frame(width: 6)` → `.frame(width: 10)`.
2. `Color(red: 0.94, green: 0.96, blue: 0.98)` → `Color.squareLight`, `Color(red: 0.12, green: 0.16, blue: 0.23)` → `Color.boardBackground` (traka pripada tabli, pa uzima boje table — to je izuzetak od fiksnog akcenta, dozvoljen po spec 5.6).
3. `Color.white.opacity(0.15)` u `.stroke(...)` → `DS.line`.

- [ ] **Step 5: Build i testovi**

Run: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: `** BUILD SUCCEEDED **`

Run: `swift test`
Expected: 11 testova prolazi.

- [ ] **Step 6: Provera da nema zaostalih hardkodiranih boja**

```bash
grep -n "Color\.\(white\|black\|blue\|green\|orange\|red\|yellow\|purple\)\|foregroundStyle(\.\(white\|blue\|green\|orange\|red\|yellow\|purple\))" Chessko/Views/GameView.swift
```

Expected: prijavljuje samo linije 314 i 325 (`PieceColor`), ništa drugo.

- [ ] **Step 7: Commit**

```bash
git add Chessko/Views/GameView.swift Chessko/Views/EvalBarView.swift build_localizations.py Chessko/Localizable.xcstrings
git commit -m "design: ekran Igra na tokene, tabla veca, eval bar bez duplikata

Sve boje kroz DS; portretni raspored preuredjen da nema mrtvog prostora;
placeholder '-' u karticama igraca uklonjen; protivnik se zove 'Racunar'
umesto po tezini; eval bar sa 6pt na 10pt i u bojama table."
```

---

## Task 3: Ekran Zadaci

**Files:**
- Modify: `Chessko/Views/PuzzleView.swift`

**Interfaces:**
- Consumes: `DS` i `Font.ds*` iz Task 1

- [ ] **Step 1: Zameniti boje**

```bash
grep -n "Color\.\(white\|black\|blue\|green\|orange\|red\|yellow\|purple\)\|foregroundStyle(\.\(white\|blue\|green\|orange\|red\|yellow\|purple\))" Chessko/Views/PuzzleView.swift
```

Zameniti po istoj tabeli kao u Task 2: pozadine kartica → `DS.surface`, ivice → `DS.line`, prigušen tekst → `DS.inkMuted`, osnovni → `DS.ink`, uspeh → `DS.success`, greška → `DS.danger`, akcije → `DS.accent`.

Boju rejtinga (`difficultyColor` vraća `"green"`/`"yellow"`/`"red"`) mapirati na `DS.success` / `DS.warning` / `DS.danger`.

- [ ] **Step 2: Popraviti rizik od sečenja pri dnu**

Iz Faze 0 je zabeleženo: portretna grana je `VStack(spacing: 0)` bez `ScrollView`-a, sa svega ~10–20pt rezerve ispod poslednje kontrole — rizik od sečenja na većem Dynamic Type-u ili niskom ekranu.

Rešenje bez dodavanja sadržaja: portretnu granu obmotati u `ScrollView` sa `.scrollBounceBehavior(.basedOnSize)` i dodati `.safeAreaPadding(.bottom, 24)`. Kad sadržaj stane, ekran izgleda isto kao sada; kad ne stane, može da se doskroluje umesto da se seče.

Unutrašnji `VStack` zadržati, `Spacer()` u njemu zameniti sa `Spacer(minLength: DS.Space.l)` da raspored ostane pri vrhu.

- [ ] **Step 3: Tipografija**

Zameniti direktne pozive (`.font(.caption2)`, `.font(.subheadline)`, `.font(.title2)` …) skalom: naslov ekrana → `.dsTitle`, naslovi kartica → `.dsHeading`, tekst → `.dsBody`, oznake i čipovi → `.dsCaption`, rejting → `.dsMono`.

- [ ] **Step 4: Build i testovi**

Run build i `swift test` kao u Task 2. Oba moraju proći.

- [ ] **Step 5: Commit**

```bash
git add Chessko/Views/PuzzleView.swift
git commit -m "design: ekran Zadaci na tokene i skalu, portret vise ne sece sadrzaj"
```

---

## Task 4: Ekran Učenje

**Files:**
- Modify: `Chessko/Views/LearnView.swift`
- Modify: `Chessko/Views/LessonDetailView.swift`

- [ ] **Step 1: Ukloniti duplirani naslov**

`LearnView.swift:122` postavlja `.navigationTitle(Loc("Učenje"))`, a `LearnView.swift:96` odmah ispod prikazuje `Text(Loc("Nauči šah"))` kao veliki naslov u sadržaju. Korisnik vidi dva naslova jedan iznad drugog.

Zadržati **naslov u sadržaju** (jer nosi i podnaslov „4 lekcije od osnova do završnice") i skloniti duplikat iz nav bara:

```swift
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
```

- [ ] **Step 2: Zameniti boje u oba fajla**

U `LessonDetailView.swift` još stoje hardkodirane boje po lekcijama (`.blue`, `.green`, `.orange`, `.red`, `.purple` kroz `lesson.accentColor`). Po spec 5.6 akcent je jedan.

`LessonInfo.accentColor` je deklarisan na `LearnView.swift:10`, a četiri vrednosti
(`.blue`, `.green`, `.orange`, `.red`) su na linijama 17, 22, 27 i 32. Sve četiri
zameniti sa `DS.accent`. Polje se **ne uklanja** — koristi se na `LearnView.swift:45`
(`info.accentColor.opacity(0.18)`) i u `LessonDetailView` kroz `lesson.accentColor`.

Boja se zadržava **samo** tamo gde nosi značenje: žute kutije „zlatnih pravila" →
`DS.warning`, greške i šah-mat → `DS.danger`, rešeno → `DS.success`.

Ikone lekcija u `LearnView` ostaju različite — razlikovanje lekcija ide preko ikone, ne preko boje.

- [ ] **Step 3: Tipografija**

`LessonDetailView` već koristi `appFont` na 48 mesta — prevesti ih na skalu (`.dsBody`, `.dsHeading`, `.dsCaption`) tamo gde se poklapa; gde je potrebna veličina van skale, ostaviti `appFont` i dopisati kratak komentar zašto.

- [ ] **Step 4: Build i testovi**

Run build i `swift test`. Oba moraju proći.

- [ ] **Step 5: Commit**

```bash
git add Chessko/Views/LearnView.swift Chessko/Views/LessonDetailView.swift
git commit -m "design: ekran Ucenje na tokene, jedan akcent, bez dupliranog naslova"
```

---

## Task 5: Ostatak

**Files:**
- Modify: `Chessko/ContentView.swift`
- Modify: `Chessko/Views/ChessClockView.swift`
- Modify: `Chessko/Views/SettingsSheet.swift`

- [ ] **Step 1: Ikone tabova**

`ContentView.swift:24-34`. Ikona za Zadatke (`play.rectangle.on.rectangle`) čita se kao video, ne kao zadatak. Zamene:

```swift
Label("Igra", systemImage: "chessboard")            // umesto play.house.fill
Label("Zadaci", systemImage: "puzzlepiece.fill")    // umesto play.rectangle.on.rectangle
Label("Učenje", systemImage: "book.fill")           // ostaje
```

Ako `chessboard` nije dostupan u SF Symbols na ovoj verziji Xcode-a, koristiti `square.grid.3x3.fill` i to zabeležiti u izveštaju.

- [ ] **Step 2: Boje sata**

`ChessClockView.swift` ima 5 hardkodiranih hex boja (`#8C2525`, `#121212`, `#2C2C2E`, `#FADAD8`, `#E5E5EA`). Sat namerno koristi punu crnu i punu belu polovinu — to je deo njegovog identiteta i **ostaje**. Zameniti samo boje upozorenja i pozadina koje nisu deo te podele:
- `#8C2525` (istekло vreme) → `DS.danger`
- `#FADAD8` (svetlo upozorenje) → `DS.danger.opacity(0.18)`
- `#2C2C2E` i `#E5E5EA` (neutralne pozadine) → `DS.fill`

`#121212` ostaje — to je crna polovina sata.

- [ ] **Step 3: SettingsSheet**

Zameniti pozadine sekcija i tekst na `DS.surface` / `DS.ink` / `DS.inkMuted`, a akcente prekidača na `DS.accent`. Prikaz tema table i stilova figura **ne dirati** — tamo se prikazuju stvarne boje tema.

- [ ] **Step 4: Build i testovi**

Run build i `swift test`. Oba moraju proći.

- [ ] **Step 5: Provera preostalih hardkodiranih boja u celom projektu**

```bash
for f in Chessko/Views/*.swift Chessko/ContentView.swift; do
  n=$(grep -c "Color\.\(white\|black\|blue\|green\|orange\|red\|yellow\|purple\)\|foregroundStyle(\.\(white\|blue\|green\|orange\|red\|yellow\|purple\))" "$f")
  [ "$n" -gt 0 ] && echo "$n  $f"
done
```

Expected: prijavljuju se samo `SquareView.swift` (boje table), `PieceImageView.swift` (boje figura), `BoardView.swift` (konfeti i oznake na tabli), `ChessClockView.swift` (crna/bela polovina sata) i `SettingsSheet.swift` (prikaz tema). Sve ostalo mora biti nula. Ako nije — dovršiti pre commita.

- [ ] **Step 6: Commit**

```bash
git add Chessko/ContentView.swift Chessko/Views/ChessClockView.swift Chessko/Views/SettingsSheet.swift
git commit -m "design: ikone tabova, sat i podesavanja na tokene"
```

---

## Task 6: Vizuelna provera

Ovo je uslov završetka faze iz specifikacije i **ne sme se preskočiti**. Nije automatski test — radi se na simulatoru.

**Files:** nijedan (samo provera; nalazi idu u izveštaj)

- [ ] **Step 1: Pokrenuti aplikaciju**

```bash
xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath /tmp/ds-check build
xcrun simctl boot "iPhone 17 Pro" 2>/dev/null; sleep 8
xcrun simctl install "iPhone 17 Pro" "$(find /tmp/ds-check/Build/Products -name Chessko.app | head -1)"
xcrun simctl launch "iPhone 17 Pro" com.veljkoni.chessko
```

Snimanje ekrana: `xcrun simctl io "iPhone 17 Pro" screenshot <putanja>.png`, pa pogledati sliku.

**Napomena o kliktanju u simulatoru** (naučeno u Fazi 0): AppleScript/System Events ne radi (greška -25204). Koristiti `cliclick` (`/opt/homebrew/bin/cliclick`). Plutajući tab bar traži **spor** pritisak: `cliclick m:X,Y w:250 dd:X,Y w:200 du:X,Y` — običan `c:X,Y` na njemu ne reaguje. Mapiranje: uzeti poziciju i veličinu prozora preko
`osascript -e 'tell application "System Events" to tell process "Simulator" to get position of window 1'`
pa `screenX = ox + pointX * k`, gde je za prozor (1120,224) veličine (456,972): `ox=1133, oy=262, k=1.0687`. Ekran uređaja je 402×874 tačaka.

- [ ] **Step 2: Proći sva tri taba u svetloj temi**

`xcrun simctl ui "iPhone 17 Pro" appearance light`

Za svaki tab (Igra, Zadaci, Učenje) i za Lekciju 1 proveriti i zabeležiti:
- nijedan tekst nije nečitljiv (belo na belom ili tamno na tamnom)
- nema velikog neobjašnjenog praznog bloka
- akcent je svuda isti (`#2E4A8A`), nema zaostale plave/zelene/narandžaste po lekcijama
- tabla je vidljivo najveći element na ekranu Igre

- [ ] **Step 3: Ponoviti u tamnoj temi**

`xcrun simctl ui "iPhone 17 Pro" appearance dark` i isto to.

- [ ] **Step 4: Ponoviti na iPad-u**

```bash
xcrun simctl list devices available | grep -i ipad
```
Uzeti prvi dostupan iPad, instalirati i proći ista tri taba u obe teme. Na iPad-u posebno proveriti da tabla nije razvučena preko cele širine ekrana i da raspored ne izgleda kao uvećan telefon.

- [ ] **Step 5: Zapisati nalaze**

Sve što odstupa zapisati kao listu sa ekranom, temom, uređajem i opisom. Ako je nalaz ozbiljan (nečitljiv tekst, presečen sadržaj), popraviti ga u zasebnom commitu i ponoviti provere za taj ekran.

- [ ] **Step 6: Ažurirati CLAUDE.md**

Dodati u `## Changelog` unos za Fazu 1: uveden `DesignSystem.swift`, pravac „Tiho i precizno", akcent fiksan i nezavisan od teme table, mrtav prostor preuređen a ne popunjen, ikone tabova zamenjene, eval bar bez duplikata, `–` placeholder uklonjen. U `## Poznata ograničenja` skloniti stavke koje su ovom fazom rešene.

```bash
git add CLAUDE.md
git commit -m "docs: azuriran CLAUDE.md za fazu 1"
```

---

## Završna provera faze

- [ ] `swift test` → 11 testova prolazi
- [ ] `xcodebuild … build` → `** BUILD SUCCEEDED **`
- [ ] `git status --short` → prazno
- [ ] Sva tri taba pregledana u svetloj i tamnoj temi, na iPhone-u i iPad-u
- [ ] Provera iz Task 5 Step 5 prijavljuje samo dozvoljene fajlove
