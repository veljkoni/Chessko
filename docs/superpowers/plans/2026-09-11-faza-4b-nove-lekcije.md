# Faza 4b — Nove lekcije: plan implementacije

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Napisati dve nove lekcije — **Notacija** i **Taktika** — i uklopiti ih u Put, čime kurikulum ide sa 4 na 6 poglavlja.

**Architecture:** Nova lekcija je nov JSON u `Chessko/Content/lessons/` plus `lesson` korak u `curriculum.json`. Nijedna linija Swift-a se ne piše; mehanizam je gotov iz Faze 4a. Vežbe u tekstu lekcije koriste `exercise` blokove sa pozicijama izvučenim iz isporučene baze zadataka i proverenim `python-chess`-om.

**Tech Stack:** JSON (šema iz `Chessko/Models/LessonContent.swift`), `curriculum.json`, Python 3 + `python-chess` za proveru pozicija.

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md` (sekcija 5.2 i Faza 4 u sekciji 6)

## Global Constraints

- **Nijedna izmena Swift koda.** Ako se učini da je potrebna, to je nalaz — prijaviti ga, ne implementirati. Ova faza je provera tvrdnje „nova lekcija = nov JSON".
- **NIKADA ne pokretati `create_xcode_project.py`.** `Content/` je folder-referenca pa `project.pbxproj` **ne treba dirati** — ako se čini da treba, stati i prijaviti.
- **`build_lesson_json.py` je ZAMRZNUT** i ne može da se pokrene (Faza 3, Task 6 obrisala je ključeve iz kojih je čitao prevode). Nove lekcije se pišu **direktno kao JSON**.
- **Nove lekcije idu na `sr` + `en`**, ne na svih 8 jezika (odluka iz Faze 3; četiri postojeće zadržavaju svih 8).
- Sadržaj lekcije **ne ide kroz `Localizable.xcstrings`** — prevodi su u samom JSON-u. Katalog pokriva samo interfejs.
- Testovi: `swift test` (trenutno **58**). Build: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`.
- **`ChesskoAndroid/` se ne dira.**

## Zatečeno stanje, provereno

- Postoje 4 lekcije, svaka sa 8 jezika: `board-and-pieces` (45 blokova, 2035 znakova srpskog teksta), `endgame` (42 / 2599), `middlegame` (21 / 1376), `openings` (15 / 1022). Nove treba da budu istog reda veličine — **oko 2000 znakova**, ne duže.
- Ton i izvor: postojeće lekcije prate Kapablankine *Chess Fundamentals* (javno vlasništvo, Project Gutenberg), sa citatima u `box`/`info` blokovima. Nove lekcije **nastavljaju isti glas**: kratke rečenice, drugo lice jednine, bez žargona koji nije objašnjen.
- Šema ima **12 tipova blokova** (`Chessko/Models/LessonContent.swift`). Nepoznat tip, nepoznata figura i `interactive: true` **bacaju grešku ili ispisuju vidljivu poruku** — nema tihog preskakanja.
- `pieceValueTable` redovi treba da nose `valueLabel` (npr. `"1 bod"` / `"1 point"`); bez njega renderer sklapa srpsku množinu i traži ključ u katalogu, što radi samo za 1/3/5/9/∞.
- Test `everyGeneratedLessonFileDecodes` već podnosi lekcije na samo `sr`+`en` (`files.count >= 32`, četiri prenete moraju imati 8 jezika, svaka lekcija bar `sr`+`en`). **Ne menjati ga da bi prošao** — ako padne, greška je u sadržaju.
- Test `realCurriculumIsConsistentWithLessonsAndPuzzleDatabase` tvrdi da svaka lekcija koju kurikulum pominje **postoji kao fajl** i da svaka tema ima **dovoljno zadataka u opsegu**. To je glavna zaštita ove faze.
- Baza zadataka — prebrojano **tačno za opsege koje ovaj plan koristi**, ne odokativno:
  `oneMove` 600–1000 → **1693** (treba 3); `fork`+`pin`+`skewer` 600–1200 → **583** (treba 5);
  `fork`+`pin`+`discoveredAttack` 600–1100 → **497** (treba 3). Sve tri sa velikom rezervom.

## Odluke ove faze

1. **Poglavlja se ubacuju po pedagoškom redu, ne dodaju na kraj.** Novi redosled: Osnove → **Notacija** → Otvaranje → **Taktika** → Središnjica → Završnica. Notacija mora doći rano, jer aplikacija ispisuje poteze u algebarskoj notaciji od prvog ekrana; taktika pripada ispred središnjice, čiji je ona sadržaj.
   **Posledica, namerna:** korisnik koji je već prešao ceo put dobiće nova poglavlja kao sledeći korak, a koraci posle njih vraćaju se u zaključano stanje dok nova ne završi. Napredak se **ne gubi** (`completedSteps` je po id-ju), samo se put produžio. Kurs koji doda gradivo se tako i ponaša.
2. **Vežbe u tekstu koriste pozicije iz isporučene baze**, ne izmišljene. One su stvarne, ocenjene i već proverene. **Ali ih treba konvertovati**: Lichess zadatak počinje protivnikovim potezom, a `exercise` blok počinje **igračevim** — vidi tabelu pozicija u Task-u 2, gde su već konvertovane.
3. **Sve pozicije u ovom planu su provereno legalne** `python-chess`-om, uključujući ceo niz poteza iz date pozicije. Ako neka ne prolazi pri izvršavanju — to je nalaz, ne razlog da se pozicija tiho zameni.

## Pregled zadataka

| # | Zadatak | Isporuka |
|---|---|---|
| 1 | Lekcija **Notacija** (sr + en) | čita se i piše potez |
| 2 | Lekcija **Taktika** (sr + en) | četiri motiva sa vežbama |
| 3 | Uklapanje u kurikulum, provera, dokumentacija | Put ima 6 poglavlja |

---

## Task 1: Lekcija „Notacija"

**Files:**
- Create: `Chessko/Content/lessons/notation.sr.json`
- Create: `Chessko/Content/lessons/notation.en.json`

**Interfaces:**
- Consumes: šemu iz `Chessko/Models/LessonContent.swift`
- Produces: lekciju sa `id: "notation"` — Task 3 je vezuje u kurikulum

- [ ] **Step 1: Napisati `notation.sr.json`**

Struktura (blokovi redom). Tekst piše implementer, u tonu postojećih lekcija; ovde su zadati **sekcije, tipovi blokova i tačne pozicije**.

> **Zašto proza nije prepisana u plan.** Tekst lekcije je ono što se u ovoj fazi zapravo stvara; prepisan ovde, plan bi postao drugi original i dva bi se razišla. Ono što plan MORA da zakuca su stvari koje se ne daju popraviti lepim pisanjem — redosled sekcija, tipovi blokova i **pozicije sa potezima**, jer pogrešan FEN daje vežbu koja ne radi ma koliko tekst bio dobar. Te su proverene `python-chess`-om pre pisanja plana.

| # | blok | sadržaj |
|---|---|---|
| 1 | `box` / `info` | zašto notacija: cela šahovska literatura, i sama aplikacija, zapisuju poteze ovako |
| 2 | `paragraph` | tabla ima koordinate: kolone `a`–`h`, redovi `1`–`8`; svako polje ima ime |
| 3 | `board` | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1`, caption o koordinatama, `interactive: false` |
| 4 | `heading` | Slova figura |
| 5 | `pieceRow` ×5 | `king` K, `queen` D, `rook` T, `bishop` L, `knight` S — `count` nosi slovo, npr. `"K"` |
| 6 | `paragraph` | pešak nema slovo — piše se samo polje |
| 7 | `divider` | |
| 8 | `heading` | Kako se čita potez |
| 9 | `bullets` | `e4` pešak na e4 · `Sf3` skakač na f3 · `Lxf7` lovac uzima na f7 · `+` šah · `#` mat · `0-0` rokada |
| 10 | `exercise` `scripted` | **Odigraj `1.e4`** — `startFEN` `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1`, `uciMoves` `["e2e4"]` |
| 11 | `exercise` `scripted` | **Odigraj `2.Sf3`** — `startFEN` `rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2`, `uciMoves` `["g1f3"]` |
| 12 | `divider` | |
| 13 | `heading` | Uzimanje, šah i rokada |
| 14 | `exercise` `scripted` | **Odigraj `Lxf7+`** — `startFEN` `r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4`, `uciMoves` `["c4f7"]` |
| 15 | `exercise` `scripted` | **Odigraj `0-0`** — `startFEN` `r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 6 5`, `uciMoves` `["e1g1"]` |
| 16 | `box` / `rule` | u aplikaciji: istorija poteza na ekranu Igra koristi baš ovu notaciju |

Svaki `exercise` nosi `title`, `hint`, `icon`, `solvedMessage`, `wrongMessage`; `playingPrompt` i `mateIn` ostaju `null`.

> **Ikone: koristiti samo one koje se već pojavljuju u postojećim lekcijama.** `Image(systemName:)` sa nepostojećim imenom **ne crta ništa i ne prijavljuje grešku** — ikona prosto nestane, a niko to ne primeti dok ne pogleda baš taj blok. Proveriti izbor sa
> `grep -rho '"[a-z][a-z0-9.]*\.fill"' Chessko/Content/lessons/ | sort -u`
> pre nego što se upiše. `flag.fill` i `arrow.triangle.2.circlepath` su potvrđeno u upotrebi.

**Sve četiri pozicije su provereno legalne** — `e4`, `Nf3`, `Bxf7+`, `O-O` redom. Ako neka ne prođe, prijaviti.

- [ ] **Step 2: Prevesti na engleski** u `notation.en.json` — isti `id`, isti broj i redosled blokova, `language: "en"`. Slova figura se **menjaju**: srpsko K/D/T/L/S postaje englesko K/Q/R/B/N. To je jedina lekcija gde prevod nije samo jezik nego i sadržaj — obavezno napomenuti u izveštaju.

- [ ] **Step 3: Provera**

```bash
python3 - <<'PY'
import json, pathlib, subprocess
for lang in ("sr","en"):
    d = json.loads(pathlib.Path(f"Chessko/Content/lessons/notation.{lang}.json").read_text())
    print(lang, "blokova:", len(d["blocks"]), "| naslov:", d["title"])
PY
swift test --filter LessonContent
```
Expected: isti broj blokova na oba jezika; `everyGeneratedLessonFileDecodes` prolazi.

Zatim provera pozicija:
```bash
python3 - <<'PY'
import json, pathlib, chess
for lang in ("sr","en"):
    for b in json.loads(pathlib.Path(f"Chessko/Content/lessons/notation.{lang}.json").read_text())["blocks"]:
        if b["type"] != "exercise": continue
        board = chess.Board(b["startFEN"])
        ok = all(chess.Move.from_uci(m) in board.legal_moves and not board.push(chess.Move.from_uci(m))
                 for m in b["uciMoves"])
        print(f'{lang} {b["title"][:28]:30} legalno={ok}')
PY
```
Expected: `legalno=True` za svaku vežbu.

- [ ] **Step 4: Commit**

---

## Task 2: Lekcija „Taktika"

**Files:**
- Create: `Chessko/Content/lessons/tactics.sr.json`
- Create: `Chessko/Content/lessons/tactics.en.json`

- [ ] **Step 1: Napisati `tactics.sr.json`**

Četiri motiva, svaki: `heading` → `paragraph` (šta je motiv) → `exercise` (pokaži ga). Između motiva `divider`.

**Pozicije su izvučene iz isporučene baze i KONVERTOVANE**, jer Lichess zadatak počinje protivnikovim potezom a `exercise` blok igračevim: protivnikov prvi potez je odigran, `startFEN` je pozicija posle njega, a `uciMoves` su preostali potezi. Sve su provereno legalne iz date pozicije, i u svima je **beli na potezu**:

| motiv | `startFEN` | `uciMoves` | izvor |
|---|---|---|---|
| **Vilica** | `8/5p2/6p1/1N2B1P1/p4P2/5k2/2r5/5K2 w - - 4 47` | `["b5d4","f3e4","d4c2"]` | lVYU8, 647 |
| **Vezivanje** | `r1b3k1/pp3rpp/2p3q1/8/2B5/8/P1P2PPP/R3Q1K1 w - - 0 19` | `["e1e8"]` | osHSK, 654 — mat u 1 |
| **Probod** | `r4k2/7R/6p1/4p1P1/4P3/8/2n1K3/8 w - - 1 52` | `["h7h8","f8e7","h8a8"]` | 2CKmq, 650 |
| **Otkriveni napad** | `6k1/7p/p3P1p1/p1pBBp2/P1P3Pq/1rP4P/8/5K2 w - - 1 41` | `["e6e7"]` | 4tXYd, 680 — mat u 1 |

Vezivanje i otkriveni napad završavaju matom — to se sme reći u `solvedMessage`.

Uvodni `box`/`info` sa Kapablankinom mišlju o tome da taktika služi planu, ne obrnuto. Završni `box`/`rule`: motivi se prepoznaju uvežbavanjem — otud korak vežbe koji sledi.

- [ ] **Step 2: Prevesti na engleski** — `tactics.en.json`, isti broj i redosled blokova. Ovde je prevod samo jezik; pozicije i potezi su identični.

- [ ] **Step 3: Provera** — isti skript kao Task 1 Step 3, nad `tactics.*.json`. Expected: `legalno=True` za sve četiri vežbe, isti broj blokova na oba jezika.

- [ ] **Step 4: Commit**

---

## Task 3: Uklapanje u kurikulum, provera i dokumentacija

**Files:**
- Modify: `Chessko/Content/curriculum.json`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Dva nova poglavlja, po pedagoškom redu**

`curriculum.json` dobija `notation` kao **drugo** poglavlje i `tactics` kao **četvrto**:

```json
{
  "id": "notation",
  "title": { "sr": "Notacija", "en": "Notation" },
  "steps": [
    { "id": "notation-lesson", "type": "lesson", "lessonId": "notation" },
    { "id": "notation-practice", "type": "practice", "themes": ["oneMove"], "count": 3, "ratingRange": [600, 1000] }
  ]
},
```
```json
{
  "id": "tactics",
  "title": { "sr": "Taktika", "en": "Tactics" },
  "steps": [
    { "id": "tactics-lesson", "type": "lesson", "lessonId": "tactics" },
    { "id": "tactics-practice", "type": "practice", "themes": ["fork", "pin", "skewer"], "count": 5, "ratingRange": [600, 1200] },
    { "id": "tactics-test", "type": "test", "themes": ["fork", "pin", "discoveredAttack"], "count": 3, "ratingRange": [600, 1100] }
  ]
},
```

Redosled poglavlja mora biti: `basics`, `notation`, `opening`, `tactics`, `middlegame`, `endgame`.

- [ ] **Step 2: Testovi moraju proći bez ijedne izmene testa**

```bash
swift test
```
Expected: **58/58**, isti broj kao pre. `realCurriculumIsConsistentWithLessonsAndPuzzleDatabase` je taj koji tvrdi da nove lekcije postoje i da nove teme imaju dovoljno zadataka u opsegu. **Ako padne, ispraviti sadržaj — ne test.**

- [ ] **Step 3: Dokazati da nijedna linija Swift-a nije promenjena**

```bash
git diff --stat main..HEAD -- '*.swift' Chessko.xcodeproj/project.pbxproj
```
Expected: **prazno**. To je tvrdnja koju ova faza proverava; ako nije prazno, objasniti zašto u izveštaju.

- [ ] **Step 4: Vizuelna provera**

Sintetički tapovi u simulatoru **ne rade**. Do ekrana Puta se stiže privremenim zakucavanjem korena `ContentView`-a; do same lekcije zasejavanjem `progress.json` u kontejneru aplikacije tako da korak bude dostupan. Posle svega **vratiti izmene i potvrditi da je radno stablo čisto**.

Snimiti: Put sa 6 poglavlja, i obe nove lekcije — u obe teme. Potvrditi da vežbe u tekstu lekcije stvarno rade (tabla prima potez), ne samo da se iscrtavaju.

- [ ] **Step 5: `CLAUDE.md`**

Dopuniti sekciju „Put": kurikulum ima 6 poglavlja, nabrojati ih redom. U sekciji o sadržaju lekcija dodati da su `notation` i `tactics` prve lekcije pisane **posle** Faze 3, samo na `sr`+`en`, i da su njihove vežbe izvučene iz isporučene baze i konvertovane (Lichess zadatak počinje protivnikovim potezom). Changelog unos.

- [ ] **Step 6: Commit**

---

## Završna provera faze

- [ ] `swift test` prolazi **58/58** — bez ijedne izmene testa
- [ ] `xcodebuild … build` → `** BUILD SUCCEEDED **`
- [ ] `git diff --stat main..HEAD -- '*.swift' Chessko.xcodeproj/project.pbxproj` je **prazan**
- [ ] `git status --short` prazan
- [ ] 6 lekcija × odgovarajući jezici u izgrađenom `.app` (4 × 8 + 2 × 2 = **36 fajlova**)
- [ ] Put prikazuje 6 poglavlja, pregledan u obe teme
- [ ] Vežbe u obe nove lekcije proverene da **rade**, ne samo da se prikazuju

## Rizici

- **Prevod notacije nije samo jezik.** Slova figura se razlikuju (K/D/T/L/S prema K/Q/R/B/N), pa engleska verzija mora da menja i sadržaj bulleta i `pieceRow` vrednosti. Najlakše mesto da se napravi lekcija koja na engleskom uči pogrešna slova.
- **Ubacivanje poglavlja u sredinu pomera korisnike unazad.** Namerno i ispravno, ali ako se ikad poželi drugačije, jedini način je dodavanje na kraj — što je pedagoški gore.
- **Vežbe iz baze moraju biti konvertovane.** Nekonvertovana pozicija znači da vežba traži potez koji je zapravo protivnikov; tabla će odbiti igračev potez i vežba deluje pokvareno.
- **Ova faza je i provera tvrdnje iz Faze 3** da je nova lekcija samo nov JSON. Svaka potreba da se dirne Swift je nalaz o mehanizmu, a ne posao ove faze.
