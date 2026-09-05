# Chessko v2 — dizajn

**Datum:** 2026-09-05
**Status:** odobren dizajn, čeka plan implementacije
**Platforma:** iOS prvo; Android (Kotlin/Compose) u zasebnom ciklusu posle

---

## 1. Polazno stanje

Chessko je objavljen na App Store-u i Google Play-u (`MARKETING_VERSION = 1.1`).
Dve odvojene kodne baze:

| Kodna baza | Veličina | Stanje |
|---|---|---|
| iOS — Swift 6 / SwiftUI | ~8.600 linija | u produkciji |
| Android — Kotlin / Compose | ~9.700 linija | u produkciji, na paritetu |

Tri taba: **Igra**, **Zadaci**, **Učenje**. Postoji AI (negamax + alfa-beta, Zobrist,
iterative deepening) i Stockfish 17 preko `chesskit-engine`, šahovski sat, pass & play,
8 jezika, teme table, stilovi figura, statistika, haptika, zvuk, eval bar, pregled
odigranih poteza.

**Rad koji nije commit-ovan:** ceo Android port, `StatsManager`, `EvalBarView`,
`PlatformHelper`, review mode, resign, PGN generisanje — oko dva meseca rada stoji
kao neispraćena izmena u radnom stablu. Poslednji commit je od 2026-07-07.

---

## 2. Ciljevi

1. **Zadržavanje korisnika** — dati igraču razlog da se vrati sutra.
2. **Više sadržaja** — škola šaha kao glavni adut, ne kao dodatak.
3. **Moderan UX** — aplikacija danas izgleda kao kompetentna SwiftUI aplikacija iz
   2023, ne kao iOS 26 aplikacija.

### Ne-ciljevi za v2

- Monetizacija. Aplikacija ostaje besplatna, bez reklama.
- Online igra protiv drugih ljudi (server, nalozi, matchmaking).
- iCloud sinhronizacija naloga i napretka.
- Game Center, rang-liste, deljeni bedževi.
- Prepisivanje šahovskog motora.

---

## 3. Odluke

| Odluka | Izbor | Obrazloženje |
|---|---|---|
| Platforme | iOS prvo, Android posle | Dve kodne baze; paralelan rad udvostručuje svaki korak |
| Rok | nema roka, prioritet po vrednosti | Verzija izlazi kad bude dobra |
| Okosnica | vođeni put (kurikulum) | Sadržaj i zadržavanje postaju ista stvar; razlikuje Chessko od Chess.com klonova |
| Sadržaj lekcija | JSON van koda | 1218-linijski `LessonDetailView.swift` ne skalira na 15 lekcija |
| Jezici za nov sadržaj | sr + en | Ostalih 6 dobijaju postojeće 4 lekcije i pun UI; šire se kad se sadržaj slegne |
| Testovi | perft testovi u fazi 0 | Motor je ručno pisan, bez ijednog testa, a faze 2–5 ga sve više koriste |

---

## 4. Zatečeni bug-ovi (P0)

Utvrđeni pokretanjem aktuelnog koda na iPhone 17 Pro simulatoru (iOS 26), svetla i
tamna tema, sva tri taba.

### 4.1 Markdown se ne renderuje

`LessonDetailView.swift:648` — `L_Para` radi `Text(Loc(text))`. `Loc()` vraća `String`,
a SwiftUI parsira Markdown samo za `LocalizedStringKey`. Korisnik doslovno vidi
`Šah se igra na tabli od **64 polja**`.

Pogođeno: **25 pojava `**...**`** u `LessonDetailView.swift`, na svih 8 jezika.

Popravka: `Text(.init(Loc(text)))` ili `AttributedString(markdown:)`. Isto proveriti
za `L_Bullet`, `L_Box`, `L_NumberedRule` — svi primaju `String`.

### 4.2 Belo na belom u svetloj temi

`LessonDetailView.swift:236,240` — piece picker koristi
`.foregroundStyle(sel ? .white : .white.opacity(0.6))` na pozadini
`Color.white.opacity(0.04)`. U svetloj temi labele Pešak/Skakač/Lovac/Top/Dama/Kralj
su nevidljive. U tamnoj temi je ispravno.

Ukupno hardkodiranih `.white`: **35** u `LessonDetailView.swift`, **13** u
`GameView.swift`, 5 u `ChessClockView.swift`, 3 u `PieceImageView.swift`,
1 u `EvalBarView.swift`.

Popravka: zamena semantičkim bojama (`.primary`, `.secondary`, tokeni iz dizajn
sistema). Izuzetak su boje figura i table, gde je bela namerna.

### 4.3 Sadržaj prolazi ispod plutajućeg tab bara

iOS 26 tab bar pluta iznad sadržaja. Interaktivna tabla u lekciji je presečena.
Nigde u projektu nema `safeAreaPadding` ni `safeAreaInset`.

Popravka: `safeAreaPadding(.bottom)` na skrolujućim kontejnerima u sva tri taba i u
detalju lekcije.

### 4.4 Neprevedene teme zadataka

`PuzzleView.swift:491` — `localizeTheme` pokriva 22 teme, `default: return theme`.
Lichess ima oko 60 tema, pa korisnik redovno vidi sirove ključeve tipa `backRankMate`.

Uz to, dva postojeća prevoda su odsečena: `"Napad na dam"` → `"Napad na damu"`,
`"Napad na kral"` → `"Napad na kralja"`.

Popravka: dopuniti mapu svim Lichess temama; `default` vraća prazan string (tema se
ne prikazuje) umesto sirovog ključa.

### 4.5 Android meša jezike

Na ekranu Zadaci istovremeno stoje „Rejting: 1494", „Prikaži rešenje" i
„Find the right move for Black". Deo stringova prolazi kroz `Loc.kt`, deo ne.

Popravka: u fazi 6, uz prenos ostatka. Do tada ostaje kao poznat nedostatak.

---

## 5. Arhitektura

### 5.1 Put

Okosnica v2. Niz koraka koje igrač prolazi redom. Četiri tipa koraka:

| Tip | Sadržaj | Uslov završetka |
|---|---|---|
| `lesson` | teorija + interaktivna tabla | korisnik dođe do kraja i potvrdi |
| `practice` | N zadataka iz offline baze, filtriranih po temi | rešeno N zadataka |
| `game` | partija protiv AI zadate jačine, opciono iz zadatog FEN-a | partija odigrana do kraja |
| `test` | mini test koji zaključava poglavlje | rešeno bez greške |

Stanje koraka: `locked` → `available` → `completed`. Završetak koraka otključava
sledeći. Poglavlje (`chapter`) grupiše korake.

Početni ekran Puta ne prikazuje spisak lekcija nego nastavak:
**„Nastavi: Korak 7 — Vezivanje"**, iznad njega streak i procenat poglavlja.

Tabovi u v2 — `Učenje` postaje `Put`, broj tabova se ne menja:

```
Put            Igra              Zadaci
kurikulum      slobodna partija  slobodno rešavanje
```

Vežbe u Putu i tab Zadaci dele istu offline bazu. Zato je baza tehnički preduslov.

### 5.2 Sadržaj van koda

```
Chessko/Content/
├── curriculum.json          definicija puta
├── lessons/
│   ├── <lessonId>.sr.json   blokovi sadržaja
│   ├── <lessonId>.en.json
│   └── ...
└── puzzles.sqlite           offline baza zadataka
```

**`curriculum.json`:**

```json
{
  "version": 1,
  "chapters": [
    {
      "id": "basics",
      "title": { "sr": "Osnove", "en": "Basics" },
      "steps": [
        { "id": "board", "type": "lesson", "lessonId": "board-and-pieces" },
        { "id": "board-practice", "type": "practice",
          "themes": ["mateIn1"], "count": 5, "ratingRange": [600, 1000] },
        { "id": "first-game", "type": "game", "difficulty": "beginner" }
      ]
    }
  ]
}
```

**Lekcija — tipizirani blokovi.** Svaki blok ima `type` i polja specifična za tip:

| `type` | Polja | Prikaz |
|---|---|---|
| `paragraph` | `text` (Markdown inline) | tekst |
| `heading` | `text` | naslov sekcije |
| `quote` | `text`, `author` | citat u okviru |
| `box` | `text`, `style` (`rule` / `warning`) | istaknuta kutija |
| `bullets` | `items[]` | lista |
| `board` | `fen`, `caption`, `interactive` | statična ili interaktivna tabla |
| `explorer` | `pieces[]` | interaktivni prikaz kretanja figura |
| `exercise` | `fen`, `moves[]` (UCI), `hint`, `solvedText` | vežba u tekstu lekcije |

U kodu ostaje **`LessonRenderer`** koji mapira blokove u SwiftUI poglede.
`LessonDetailView.swift` pada sa 1218 linija na ~300 i postaje isključivo renderer.
Nova lekcija = nov JSON, bez Swift koda.

Postojeće 4 lekcije se prenose u JSON na svih 8 jezika (prevodi već postoje u
`build_localizations.py` i preuzimaju se odatle). Nove lekcije idu sr + en.

`Localizable.xcstrings` i `build_localizations.py` ostaju **samo za UI** — dugmad,
poruke, podešavanja, pristupačnost. Prestaju da rastu sa sadržajem.

### 5.3 Offline baza zadataka

Rešava zavisnost od `chess-puzzles-api.vercel.app` (tuđi servis, jedan zadatak dnevno,
ne radi offline — a store opis obećava „full offline support") i omogućava ciljane
vežbe po temi.

- Izvor: Lichess puzzle database, licenca CC0
- Podskup: ~20.000 zadataka, rejting 600–2200, filtrirani po broju odigranih partija
- Format: SQLite, ugrađen u bundle

```sql
CREATE TABLE puzzles (
  id       TEXT PRIMARY KEY,
  fen      TEXT NOT NULL,
  moves    TEXT NOT NULL,   -- UCI, razdvojeno razmakom
  rating   INTEGER NOT NULL,
  themes   TEXT NOT NULL    -- razdvojeno razmakom
);
CREATE INDEX idx_rating ON puzzles(rating);
CREATE TABLE puzzle_themes (theme TEXT, puzzle_id TEXT);
CREATE INDEX idx_theme ON puzzle_themes(theme, puzzle_id);
```

Očekivana veličina **2–3MB** — zanemarljivo naspram 74MB NNUE mreže koja se već nosi.

Upit koji arhitektura mora da podrži: *„daj 5 zadataka teme `fork`, rejting 900–1200,
koje korisnik nije rešio"*.

Tab Zadaci prestaje da bude „jedan dnevno" i postaje neograničeno rešavanje.
Dnevni zadatak ostaje kao istaknuta stavka na vrhu, deterministički biran po datumu
(kao sada), ali iz lokalne baze.

Skripta `build_puzzle_db.py` u rootu projekta pravi bazu iz Lichess CSV izvoza;
rezultat se commit-uje da build ne zavisi od mreže.

### 5.4 Napredak

`StatsManager` (već postoji) proširuje se u `ProgressStore`:

- **Napredak po koracima** — stanje svakog koraka, datum završetka
- **Dnevni cilj** — ispunjen završetkom jednog koraka Puta **ili** rešavanjem tri zadatka
- **Streak** — dani zaredom sa ispunjenim ciljem; prekida se propuštenim danom
- **Rejting igrača** — Elo-stil, počinje na 800

Formula rejtinga pri rešavanju zadatka rejtinga `Rp`:

```
E  = 1 / (1 + 10^((Rp - R) / 400))
R' = R + K * (S - E)        K = 32,  S = 1 rešeno / 0 nerešeno
```

Rejting bira težinu zadataka u vežbama: prozor je `R - 200` do `R + 100`,
presečen sa `ratingRange` koji korak navodi u `curriculum.json`. Ako je presek prazan,
prednost ima `ratingRange` koraka — kurikulum zna šta se uči, rejting je samo podešavanje.
Tab Zadaci, gde nema koraka, koristi čist prozor po rejtingu.

Čuvanje: JSON fajl u Application Support, ne `UserDefaults` — napredak je struktura,
ne šačica skalara. Postojeća statistika iz `UserDefaults` se pri prvom pokretanju
migrira i ostavlja netaknuta (bez brisanja, radi povratka na stariju verziju).

Bez bedževa i konfeta. Brojke služe putu, nisu same sebi svrha.

### 5.5 Analiza partije

Posle svake partije Stockfish prolazi sve pozicije i klasifikuje poteze po gubitku u
centipionima (`cpLoss` = evaluacija najboljeg poteza − evaluacija odigranog):

| Klasa | `cpLoss` |
|---|---|
| najbolji | odigran je potez motora |
| odličan | < 20 |
| dobar | < 50 |
| netačnost | < 100 |
| greška | < 300 |
| promašaj | ≥ 300 |

Procenat tačnosti računa se iz prosečnog `cpLoss` po potezu, po Lichess formuli
(`103.1668 * exp(-0.04354 * avgCpLoss) - 3.1669`, ograničeno na 0–100).

Prikaz: traka poteza sa oznakama u boji, procenat tačnosti za oba igrača, izdvojen
prelomni potez („najveći gubitak — 17.Lc4"), mogućnost da se svaka pozicija otvori u
postojećem review modu.

Tehnički: nova upotreba `StockfishBridge` — batch analiza niza FEN-ova umesto jednog
poteza. Radi na `Task.detached`, sa prikazom napretka. Dubina fiksna (12) da analiza
traje predvidivo.

U Putu, korak tipa `game` koristi analizu za povratnu informaciju umesto pukog
„pobedio / izgubio".

### 5.6 Dizajn sistem

Uvodi se pre novih ekrana, da se Put ne gradi na zatečenoj neujednačenoj osnovi.

- **Tokeni:** boje, razmaci, radijusi, tipografska skala. `Font.appFont` iz
  `PlatformHelper.swift` je začetak tipografskog dela i proširuje se.
- **Jedan akcent** umesto današnjih plava / zelena / narandžasta / crvena po lekcijama.
  Boje ostaju samo tamo gde nose značenje (šah, greška, uspeh).
- **Hijerarhija kartica** — danas su sve kartice isto sive, bez razlike u važnosti.
- **Mrtav prostor:** na sva tri taba 25–30% ekrana je prazno.
  - Igra: ispod table status, istorija poteza, poziv na sledeći korak Puta
  - Zadaci: streak, istorija rešavanja, sledeći zadatak
  - Put: nastavak, napredak poglavlja
- **Kartice igrača:** `–` kao placeholder za pojedene figure izgleda kao nedovršen UI;
  prikazivati materijalnu prednost ili ništa. Protivnik se ne zove „Medium".
- **Eval bar:** danas je tanka crtica levo od table, bez oznake, a ista informacija
  stoji i kao badge u kartici igrača. Zadržati jedan prikaz, čitljiv.
- **Ikone tabova:** `play.rectangle.on.rectangle` za Zadatke čita se kao video;
  zameniti puzzle ikonom (Android je već koristi).
- **Duplirani naslovi:** nav bar „Učenje" + naslov „Uči šah" odmah ispod; ostaviti jedan.
- **Emoji u UI** (👆 u lekciji, 💡/🔄 na Androidu) zameniti SF Symbols ikonama.
- **iOS 26:** `safeAreaPadding` za plutajući tab bar, Liquid Glass gde ima smisla.
- Tabla ostaje junak ekrana; sve oko nje se stišava.

---

## 6. Faze

Svaka faza je zaokružena i može da se pusti u store nezavisno.

### Faza 0 — Higijena i temelji

- Commit zatečenog rada u granu, sa smislenom porukom (tačka povratka)
- Test target + perft testovi za `MoveGenerator` (standardne pozicije:
  početna do dubine 5, Kiwipete, pozicije 3–6 iz perft skupa)
- Popravke P0 bug-ova 4.1–4.4
- Ažuriranje `CLAUDE.md` (changelog je stao na 2026-07-04)

**Gotovo kad:** perft testovi prolaze, četiri bug-a popravljena i provereni na
simulatoru u obe teme, rad je commit-ovan.

### Faza 1 — Dizajn sistem

- Tokeni i tipografska skala
- Zamena hardkodiranih `.white` semantičkim bojama
- Popunjavanje mrtvog prostora na sva tri taba
- Sređivanje eval bara, kartica igrača, ikona tabova, dupliranih naslova

**Gotovo kad:** sva tri taba prolaze pregled u svetloj i tamnoj temi, na iPhone-u i
iPad-u, bez hardkodiranih boja izvan figura i table.

### Faza 2 — Offline zadaci

- `build_puzzle_db.py`, generisana i commit-ovana `puzzles.sqlite`
- `PuzzleRepository` — upiti po temi, rejtingu i rešenosti
- `PuzzleViewModel` prelazi sa mreže na lokalnu bazu
- Tab Zadaci: neograničeno rešavanje, dnevni zadatak kao istaknuta stavka
- Rejting igrača (5.4) i njegovo pomeranje pri rešavanju

**Gotovo kad:** aplikacija radi u avionskom režimu, `chess-puzzles-api.vercel.app`
više se ne poziva nigde u kodu.

### Faza 3 — Sadržaj u JSON

- Šema blokova i `LessonRenderer`
- Prenos postojeće 4 lekcije u JSON na 8 jezika
- `LessonDetailView` sveden na renderer

**Gotovo kad:** 4 postojeće lekcije izgledaju isto kao pre (ili bolje), a u
`LessonDetailView.swift` nema teksta lekcija.

### Faza 4 — Put

- `curriculum.json` i model Puta
- `ProgressStore`, migracija postojeće statistike
- Početni ekran Puta, streak, napredak poglavlja
- Nove lekcije (sr + en) do zaokruženog kurikuluma
- Tab `Učenje` postaje `Put`

**Gotovo kad:** igrač može da pređe put od prve lekcije do kraja, napredak preživi
gašenje aplikacije, streak se ispravno prekida.

### Faza 5 — Analiza partije

- Batch analiza u `StockfishBridge`
- Klasifikacija poteza i procenat tačnosti
- Ekran analize, povezan sa review modom
- Povratna informacija za `game` korake u Putu

**Gotovo kad:** analiza partije od 40 poteza završi u razumnom vremenu na stvarnom
uređaju, sa prikazom napretka.

### Faza 6 — Android

Prenos svega iz faza 0–5 na Kotlin/Compose, uključujući popravku mešanja jezika (4.5).
`curriculum.json`, lekcije i `puzzles.sqlite` se dele između platformi bez izmena.

---

## 7. Rizici

| Rizik | Ublažavanje |
|---|---|
| Nekomitovan rad se izgubi pre nego što faza 0 počne | Faza 0 počinje commit-om, pre svega ostalog |
| Prenos lekcija u JSON izgubi detalj postojećeg prikaza | Poređenje ekran po ekran pre i posle; 4 lekcije su mala površina |
| Analiza partije presporana na starijim uređajima | Fiksna dubina 12, prikaz napretka, mogućnost prekida |
| Kurikulum ostane nedovršen jer je pisanje sadržaja sporo | Put se pušta po poglavljima; prvo poglavlje je isporučivo samo za sebe |
| Android sve više zaostaje | Faze 0–5 su na iOS-u; faza 6 je jedan prenos, ne kap-po-kap sinhronizacija |
| Veličina aplikacije (74MB NNUE + 3MB baza) | Van obima v2; zabeleženo kao kandidat za kasnije (skidanje velike mreže na zahtev) |

---

## 8. Šta se ne menja

- Šahovski motor (`MoveGenerator`, `ChessAI`, `ZobristTable`) — dobija testove, ne izmene
- Immutable `GameState` i konvencija `applyingForSearch` u pretrazi
- Šahovski sat, pass & play, teme table, stilovi figura
- Osam jezika za UI
- Besplatno, bez reklama
