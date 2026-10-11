# Chessko — specifikacija za implementaciju dizajnerskog pregleda

> Izvor: dizajnerski pregled pred javno izdanje (113 snimaka, iOS + Android, telefon i tablet, obe teme)
> i odluke vlasnika proizvoda od 11. 10. 2026. Ovaj dokument je **jedini izvor istine za implementaciju**.
> Vizuelne makete i obrazloženja su na dizajn kanvasu („Chessko — pregled pred izdanje“); ovde je sve
> što je potrebno za kod.

---

## 0. Kako koristiti ovaj dokument

- Aplikacija: **iOS (SwiftUI)** i **Android (Jetpack Compose)**. **Paritet je obavezan**: svaka stavka
  označena „obe“ radi se na obe platforme u istom koraku.
- Tokeni se u ovom dokumentu zovu kao u briefu: `DS.accent`, `DS.surface`, `Type.heading`, `Space.l`,
  `Radius.m`… Imena razmaka i radijusa su pretpostavka:
  `Space.xs 4 · s 8 · m 12 · l 16 · xl 24`, `Radius.s 8 · m 12 · l 16`. **Ako se u kodu zovu drugačije,
  važe vrednosti, ne imena.** Na početku napravi mapu „ime u dokumentu → ime u kodu“ za obe platforme.
- Tipografija: `Type.title 22 bold · heading 17 · body 15 · caption 12 · label 11 · mono 13`.
- Prioritet: **P1** = pre izdanja obavezno (korisnik se zbuni ili ne vidi), **P2** = treba,
  **P3** = poliranje.
- Platforma: **obe / iOS / Android** = gde se menja kod.
- Kontrast je naveden kao **L / D** (svetla / tamna tema), WCAG 2.1. Prag: 4,5 : 1 tekst, 3 : 1 ikone
  i ivice.
- Materijal uz dokument (isti folder):
  - `ikone/` — ikonica aplikacije i ikona taba Igra (SVG, PNG, Android XML)
  - `figure-C-reljef/` — 12 SVG figura, set „Reljef“
  - `figure-A-rez/` — 12 SVG figura, set „Rez“
  - `NN_*.png` — snimci ekrana na koje se pozivaju brojevi u zagradama, npr. (75)

### Šta se NE dira (iz briefa, odeljak 3)

- **Boje 8 postojećih tema table**, oznake na tabli (žuto, cijan `#00D2FF`, crveno `#EF4444`).
  Izuzetak: dodaju se dve nove teme i okvir table (§ 11).
- **8 postojećih stilova figura.** Izuzetak: dodaju se dva nova stila (§ 12).
- **Polovine šahovskog sata** i traka između njih (fiksno tamna).
- Fiksni elementi: strane eval trake, zlatna oznaka mata, pločica promocije, birač boje pri novoj igri.
- Emoji u porukama (🎉 🏆 🔥 🧩).
- **Bez novih zavisnosti** (fontova, biblioteka). Jedini sopstveni vektor je skakač za tab Igra (§ 10),
  odobren kao izuzetak; Material Symbols `route` se uvozi kao jedan vektor ako ga Compose ikone nemaju.

---

## 1. Usvojene odluke (sažetak)

| # | Odluka | Gde |
|---|---|---|
| 1 | `inkMuted` (svetla) `#6B7280 → #5E6573`; `warning` (svetla) `#B07D2B → #865F21`. Tamna tema bez promene. | § 2 |
| 2 | Svi ekrani na `DS.ground`, kartice na `DS.surface` + ivica `DS.line`; na iOS-u nijedna sistemska boja za podloge. | § 3 |
| 3 | Analiza: 6 klasa poteza simbolom i ispunom (★ ! ✓ ?! ? ??), „najbolji“ u `success`, ne u `accent`. | § 6 |
| 4 | Zadaci i koraci: iOS raspored na obe; uputstvo je okvir, ne dugme; bez „Pokušaj ponovo“. | § 7 |
| 5 | Android: Analiza, Podešavanja i Sat preko celog prozora (bez donje trake); ispravni inseti. | § 4 |
| 6 | Ikonica aplikacije **I1 Skakač**; tabovi **Igra = sopstveni skakač, Zadaci = slagalica, Put = ruta**. | § 10 |
| 7 | **Tabla na tabletu 560–800** po visini ekrana; telefon ostaje do 560. Ista funkcija na svim ekranima sa tablom. | § 13 |
| 8 | Jezik: jedan red u Podešavanjima, opcija **„Sistem“** na obe, isti redosled jezika. | § 15 |
| 9 | Klasična ostaje podrazumevana tema; nove teme **Žalfija** i **Glina**; okvir table 1 pt. | § 11 |
| 10 | Dva nova stila figura: **„Reljef“** (C) i **„Rez“** (A). | § 12 |

---

## 2. Tokeni (P1 · obe)

Menjaju se **samo vrednosti** u svetloj temi. Imena i tamna tema ostaju.

| Token | Svetla sada | Svetla novo | Tamna |
|---|---|---|---|
| `inkMuted` | `#6B7280` | **`#5E6573`** | `#8B93A7` (bez promene) |
| `warning` | `#B07D2B` | **`#865F21`** | `#E0B252` (bez promene) |

Kontrast posle promene:

| Par | L (pre → posle) | D |
|---|---|---|
| inkMuted na ground | 4,36 → **5,28** | 5,94 |
| inkMuted na surface | 4,83 → **5,86** | 5,43 |
| inkMuted na fill | 4,01 → **4,86** | 4,81 |
| inkMuted u kutiji citata | 3,73 → **4,52** | 5,10 |
| warning na surface | 3,61 → **5,72** | 8,48 |
| warning na ground | 3,26 → **5,16** | 9,27 |
| warning na fill | 3,00 → **4,75** | 7,51 |
| ikona u kutiji pravila | 2,93 → **4,99** (nova kutija) | 7,05 |
| bela na warning (puna oznaka) | → **5,72** | — |

Izvedene boje (nisu novi tokeni — računaju se iz postojećih):

- **Kutija pravila:** `warning` 10 % preko `surface`.
- **Izabran red / potez:** `accent` 12 % (L) / 16 % (D) preko `surface`; tekst `accent` (7,02 / 4,87).

---

## 3. Globalna pravila upotrebe (P1 · obe, osim gde piše drugačije)

- [ ] **Podloga svakog ekrana je `DS.ground`.** Na iOS-u pretraži i ukloni `systemBackground`,
  `systemGroupedBackground`, `secondarySystemBackground`, `.secondary` za podloge, `Color.black`,
  `#1C1C1E`, `#1F1F1F`, `#F2F2F7`. Za `List`/`Form`: `.scrollContentBackground(.hidden)` +
  `.background(DS.ground)`. Pogođeni ekrani: Igra, Zadaci, Lekcija, Podešavanja, svi koraci Puta
  (lekcija, vežba, test, partija), iPad (traka tabova `#0E1428` iznad crnog sadržaja). **(iOS)**
- [ ] **Kartica = `DS.surface` + ivica 1 pt `DS.line`**, u obe teme, na svim ekranima
  (Podešavanja, Put, Analiza, Igra na iOS-u). line na ground je dekorativan (1,16 / 1,32) — kartica
  se prepoznaje po sadržaju.
- [ ] **`DS.fill` nije kartica.** Ostaje samo za čipove, polja, dugmad i redove unutar kartice. Danas
  su fill blokovi na ground: prekidači u Podešavanjima, opcije „Nova igra“, objašnjenje kontrola sata,
  lista poteza → prelaze u `surface` grupe.
- [ ] **Grupe:** `Space.xl` između grupa, `Space.s` od naslova do grupe, unutar grupe redovi sa `line`
  razdelnikom.
- [ ] **`DS.accent` samo za ono što se tapa ili je izabrano.** Naslovi sekcija, vrednosti u tabelama
  i statični brojevi nisu accent.
- [ ] **Naslovi sekcija:** `Type.heading` bold, `DS.ink` (15,72 / 16,15). Važi i za iOS Podešavanja
  (danas sistemska siva `#85858B`, 3,29 ✕) i Android (danas accent).
- [ ] **Dugi tekst (pasusi, opisi) je `DS.ink`.** `inkMuted` samo za meta-tekst (podnaslovi, napomene,
  vreme, broj koraka).
- [ ] **Status nikad ne izgleda kao dugme ni kao polje za unos.** („Tvoj potez“, „Pronađi pravi
  potez…“) — vidi § 5 i § 7.
- [ ] **Dijalozi:** jedan izgled — `DS.surface`, `Radius.l`. Primarno dugme `accent` + `onAccent`,
  `Radius.m` (nigde „stadion“). Sporedna radnja: tekst-dugme u `accent` (8,53 / 6,43).
  „Zatvori“/„Otkaži“ nikad u `inkMuted`. Sporedno dugme sa ispunom: `fill` + `ink` u obe teme (51).
- [ ] **Brojevi kroz lokalni format** (decimalni zarez u srpskom: „0,0 %“, „−2,3“).
- [ ] **Množine kroz `plurals` / `.stringsdict`** — nigde „1 Dana“.

---

## 4. Navigacija i hrom

- [ ] **P1 · Android — celoekranski prikazi.** Analiza, Podešavanja i Šahovski sat ne smeju da
  ostave donju traku vidljivu ispod sadržaja (presečeni nazivi tabova, siva zavesa preko statusne
  trake: 02, 18, 20, 52, 96, 101, 106, 111).
  Najbolje: zasebna odredišta navigacije bez donje trake. Ako ostaje dijalog:
  `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false))`,
  sadržaj edge-to-edge sa `WindowInsets.safeDrawing` paddingom. (iOS već pokriva ceo ekran — 64, 71.)
- [ ] **P1 · Android — inseti donje trake.** Ručka za gestove seče labelu „Zadaci“ (92–111):
  `NavigationBar(windowInsets = NavigationBarDefaults.windowInsets)` ili `Modifier.navigationBarsPadding()`.
  Proveriti i telefon sa navigacijom gestovima.
- [ ] **P2 · Android — `NavigationRail`** na širini prozora ≥ 840 dp (M3 *expanded*); ispod te širine
  donja traka. Izabran tab: `accent` ikona na `fill` indikatoru (7,08 / 5,69).
- [ ] **P2 · obe — ikone tabova** (§ 10). Na iOS-u staklena traka ostaje.
- [ ] **P2 · obe — app bar u Lekciji:** `navBar` pozadina, nazad + „Put“ u `accent`, naslov lekcije
  `Type.body` medium, `line` ispod kad sadržaj klizi (accent na navBar 8,53 / 6,65). Plutajuće
  „← Put“ na fill pločici se uklanja (Android). iOS ima naslov — uskladiti.
- [ ] **P3 · obe — naslov „Chessko“** u zaglavlju: `Type.heading` semibold na obe (danas ~28 na
  Androidu, ~17 na iOS-u).

---

## 5. Igra (P1 · obe)

Referenca: maketa „Igra posle“ (svetla i tamna) na kanvasu; snimci 00, 01, 15–17, 19, 24, 60, 67.

- [ ] **Kartica protivnika:** ime + drugi red „Srednje · ~1300 · igra crnima“ (`Type.caption`,
  `inkMuted`, 5,86 / 5,43) + strelica; tap otvara izbor protivnika i nivoa. Nikad „–“: ako nema
  sadržaja, red se ne prikazuje, a ime je vertikalno centrirano. (iOS već prikazuje nivo.)
- [ ] **Kartica igrača:** status „Na potezu“ (`Type.caption` medium, `ink`) + tačka `success`
  (5,04 / 8,78). Kraj partije (mat, predaja) u istoj kartici kao `Type.body`.
  **Poseban red/traka „Tvoj potez“ se uklanja.**
- [ ] **Aktivna kartica:** ivica 2 pt `accent` na obe (iOS danas `#8797BA`, 2,63 ✕ → 7,69 / 7,03).
- [ ] **Procena** (0.0 / +1,2 / M1) na jednom stalnom mestu — desno u kartici igrača; znak +/− nosi
  stranu. Eval traka ostaje.
- [ ] **Akcije ispod table: Vrati · Predaj · Više.** `Više` (⋯) sadrži Podeli, Nova partija, Analiza.
  „Računar“ i „Srednje“ prelaze u karticu protivnika; „Reset“ postaje „Nova partija“ u meniju.
  Dugmad: `fill`, `Radius.m`, visina 48. Ispod 360 pt širine: ikona iznad labele (`Type.label`).
  Nedostupna dugmad ostaju na mestu sa 38 % providnosti (Predaj pre prvog poteza). „Predaj“ u
  `danger` (na fill 5,43 / 5,93). Na iOS-u „Vrati“ prelazi iz zaglavlja u traku (paritet).
- [ ] **Lista poteza uvek rezerviše ostatak visine** ispod table. Prazno stanje: „Potezi će se
  pojaviti ovde“ (`Type.body`, `inkMuted`). Izgled: `surface` + `line`; mreža broj · beli · crni,
  red 36, `Type.mono`; izabrani potez `accent` tekst na accent 12 % / 16 % tinti.
- [ ] **Dijalog predaje:** „Predati partiju?“ · „Nastavi“ (accent tekst) · „Predaj“ (danger, 6,54 /
  6,69). Obraćanje na „ti“ svuda.
- [ ] **Kraj partije:** dijalog `surface`; primarno „Nova igra“, sporedno „Analiziraj partiju“
  (jedan naziv za radnju — ne i „Pregledaj partiju“). Poruka rodno neutralna: „Partija je predata ·
  pobeda crnih“ (ne „Izgubio si“).
- [ ] **Nova igra (meni):** opcije kao redovi u `surface` dijalogu, razdvojeni `line`; opisi
  `inkMuted` na surface (5,86 / 5,43). „Premium šahovski sat“ → „Šahovski sat sa vremenskim
  kontrolama“ (nema kupovina).
- [ ] **Izbor boje (54):** ako postoji podrazumevani izbor — ivica 2 pt `accent`; ako ne — ista ispuna
  za sve tri pločice (birač sam se ne dira).
- [ ] **Telefon pejzaž (24):** vrhovi table i panela poravnati; panel 400.
- [ ] **P3 · Okvir table:** 1 pt u boji tamnog polja aktivne teme (svetlo polje i ground su 1,09 : 1 —
  uglovi se gube). Tamno polje Klasične na ground 2,81 / 5,85.

---

## 6. Analiza partije

- [ ] **P1 · Android:** preko celog prozora (§ 4).
- [ ] **P1 · obe — klase poteza.** Boja = porodica, simbol + ispuna = klasa:

| Klasa | Simbol | Boja | Oblik | Kontrast L / D |
|---|---|---|---|---|
| Najbolji | ★ | `success` | puna (bela/`surface` na success) | 5,04 / 8,78 |
| Odličan | ! | `success` | obris | 5,04 / 8,78 |
| Dobar | ✓ | `inkMuted` | obris | 5,86 / 5,43 |
| Netačnost | ?! | `warning` | obris | 5,72 / 8,48 |
| Greška | ? | `warning` | puna | 5,72 / 8,48 |
| Promašaj | ?? | `danger` | puna | 6,54 / 6,69 |

  Oznaka: `Radius.s`, visina 22, `Type.caption` bold. ★ kao tekstualni znak (ili SF `star.fill` /
  Material `star`). Za čitače ekrana oznaka nosi naziv klase („Promašaj, 2. g4“).
  **`accent` se ne koristi za klasu** — u istoj listi znači „izabran potez“.
- [ ] **P2 · obe — ikona prelomnog poteza** ista na obe: `warning` (iOS danas `danger`).
- [ ] **P2 · obe — tačnost:** „0,0 %“ za jednu stranu javlja se i posle 8–9 poteza (52, 64, 71) —
  **proveriti proračun**, ne samo prikaz. Ispod praga poteza: „—“ i napomena „Premalo poteza za
  procenu tačnosti.“ (`inkMuted` na ground 5,28 / 5,94).
- [ ] **P2 · obe — procena za početnike:** umesto „2.g4 (−233)“ prikazati „−2,3“ + rečenicu
  „Beli gubi oko dva piona prednosti.“
- [ ] **P2 · obe — lista poteza** kao u Igri (broj · beli · crni), oznaka klase ispred poteza,
  izabrani red na accent tinti. Danas čipovi u tri kolone i neporavnato „1…Sc6“.
- [ ] **P3 · obe:** ispod liste mini-tabla izabranog poteza (≤ 280) ako je ekran inače prazan.

---

## 7. Zadaci i koraci Puta (vežba, test, partija)

### Zadaci (P1 · Android raspored, iOS boje)

- [ ] **iOS raspored na obe:** „‹ Danas ›“ u zaglavlju (strelice kao ikone-dugmad 44 × 44 u `accent`;
  za današnji dan desna nedostupna, 38 %), red tagova, uputstvo, tabla, jedna radnja.
- [ ] **Uputstvo je okvir, ne dugme:** `fill`, bez ivice, `Radius.m`, sijalica `inkMuted` (4,86 / 4,81),
  tekst `Type.body` `ink` (14,47 / 13,08), uz krug strane na potezu. Danas na Androidu puno `accent`
  dugme (najjači element ekrana); na iOS-u sistemski sivi okvir sa ivicom (`#D8D8DD` / `#1D1D1E`).
- [ ] **Samo „Prikaži rešenje“** (sporedno dugme). „Pokušaj ponovo“ se **uklanja sa Androida**.
- [ ] **Tagovi u tokenima:** `fill` + `inkMuted` (danas iOS sistemske sive, 3,11 ✕ → 4,86 / 4,81).
  Rejting „1503“ (bez tačke), broj u `ink`, zvezdica u `warning`.
- [ ] **Težina bez `danger`/`success`:** neutralan čip `fill` + `ink` uz 1–3 crtice. („Teško“ nije
  greška, „Lako“ nije uspeh.)
- [ ] **Raspored od vrha:** datum → tagovi → uputstvo → tabla → akcija odmah ispod (`Space.l`).
  Ništa nije centrirano po visini, ništa nije prikovano za dno.
- [ ] **P3:** „Rejting: 1940“ → „Zadatak 1940“; igračev Elo posebno („Tvoj Elo 800“).

### Koraci Puta (iOS: 74–82, 112–115; Android: 13, 14)

- [ ] **P1 · iOS — podloga `ground`** (danas `#F2F2F7` / `#000000`).
- [ ] **P2 · obe — isti uputni okvir kao u Zadacima.** Status „Tvoj potez“ u partiji ide u karticu
  igrača („Na potezu“) — iste komponente kao u Igri (kartica „Računar · Početnik“ kao kartica
  protivnika).
- [ ] **P2 · obe — sadržaj od vrha:** `Space.l` ispod zaglavlja; danas ~150 pt (iOS) / ~300 dp
  (Android) prazno iznad. Napomena „Korak se završava kad partija dođe do kraja.“ odmah ispod table.
- [ ] **P2 · obe — tekst testa** „Test mora biti rešen bez greške“ u `warning` (novi: 5,16 / 9,27).
- [ ] **P3 · obe — traka napretka:** rešeni segmenti `accent` (7,69 / 7,03), nerešeni `fill`
  (potvrđeno). Tekst „Zadatak N od M“ u `inkMuted` (5,28 / 5,94). Opciono: tekući segment `accent` 40 %.
- [ ] **P2 · obe — „Zatvori“** kao tekst-dugme u `accent` (danas `inkMuted`, izgleda onemogućeno).

---

## 8. Put (P1 · Android preuzima iOS, obe dopune)

- [ ] **Android preuzima iOS raspored:** ikone vrste koraka, katanac, traka napretka, karta
  „Nastavi“ sa nazivom lekcije.
- [ ] **Status koraka ikonom 24** (naslov ostaje `ink`; zaključan `inkMuted`):
  završeno `checkmark.circle.fill` / `check_circle` u `success` (5,04 / 8,78), trenutni
  `play.circle.fill` / `play_circle` u `accent`, zaključano `lock` u `inkMuted` (5,86 / 5,43).
- [ ] **Trenutni korak istaknut:** red na `fill`, `Radius.m` (ink na fill 14,47 / 13,08).
- [ ] **Napredak poglavlja:** traka 4 pt ispod naslova, `accent` na `fill` (7,08 / 5,69), uz „1/3“
  na obe (iOS danas „0%“).
- [ ] **Dugme:** „Nastavi: Vežba · 5“ u jednom redu, poglavlje ispod kao `Type.caption`
  (onAccent na accent 8,53 / 6,71).
- [ ] **Dnevni cilj:** „Danas: 1 / 1 korak“ sa trakom; niz „1 dan zaredom“, „2 dana“, „5 dana“
  (plurals / stringsdict).
- [ ] **P3:** zaključana poglavlja sažeta (naslov · „3 koraka“ · katanac); nazivi „Vežba: 5 zadataka“,
  „Partija protiv računara“.
- [ ] **Tablet:** kolona do 720, centrirana.

---

## 9. Lekcija (P1 · obe)

Referenca: maketa „Lekcija posle“ (svetla i tamna); snimci 06–11, 41, 42, 59, 63, 70.

| Nivo | Stil | Boja |
|---|---|---|
| Naslov lekcije | `Type.title` 22 bold | `ink` |
| Sekcija (Lovac, Rokada…) | `Type.heading` 17 bold + ikona/glif | `ink`, ikona `inkMuted` (5,28 / 5,94) |
| Pod-blok (Kretanje) | `Type.body` 15 medium | `ink` |
| Telo | `Type.body` 15, prored 1,5 | `ink` (15,72 / 16,15) |
| Kutija (pravilo, citat) | naslov `Type.body` bold, telo `Type.body` | `ink`, ikona u boji kutije |
| Meta | `Type.caption` | `inkMuted` |

- [ ] Naslovi sekcija **nisu** `accent` (danas izgledaju kao linkovi).
- [ ] **Kutija pravila:** `warning` 10 % preko `surface`, `Radius.m`, bez ivice; naslov 12 → 15
  (`Type.body` bold, `ink` — 15,22 / 12,28); ikona `warning` (4,99 / 7,05). Uslovi (rokada) kao lista
  sa tačkama.
- [ ] **Emoji → glifovi:** 👑 → ♔ U+2654, 🏰 → ♖ U+2656, 🐴 → ♘ U+2658, 📐 → ♗ U+2657 — **uvek uz
  U+FE0E** (inače Android crta emoji); 🍴 → SF `arrow.triangle.branch` / Material `call_split`;
  ⬛ → SF `square.fill` / Material `square`. Boja `ink`.
- [ ] **Čipovi figura:** izabrani `accent` + `onAccent` (8,53 / 6,71), ostali `fill` + `ink`
  (14,47 / 13,08). iOS danas sistemske sive (labele 3,0 ✕).
- [ ] **Ritam:** `Space.xl` iznad i ispod razdelnika, `Space.m` od naslova do teksta; kartica čipova
  `Space.m` unutra (danas ~70 dp praznog dna).
- [ ] **Tabela vrednosti:** zaglavlje, redovi 44 sa `line`, vrednosti `ink` medium desno, tabelarne
  cifre; ne `accent`. „∞“ → „neprocenjiv“. „× 2“ → „2 po strani“.
- [ ] „Elementarni matovi“: ikona koja ne nosi status (ne kvačica).
- [ ] „Na potezu si!“ u `ink`; primarno dugme „Završi korak“ `Radius.m` (ne stadion).
- [ ] App bar (§ 4).
- [ ] **Tablet:** tekst u koloni do 640; tabla po § 13; u pejzažu dve kolone (tekst 480–520 levo,
  tabla desno, lepljiva dok tekst skroluje). Danas tabla nema granicu (do 1336 pt — 87, 95, 110, 119).

---

## 10. Ikone (usvojeno)

### Ikonica aplikacije — I1 Skakač (P1 · obe)

| | iOS | Android |
|---|---|---|
| Fajlovi | `ikone/ios/AppIcon-1024.png`, `-dark.png`, `-tinted.png` (+ SVG) | `ikone/android/ic_launcher.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `ic_launcher_background_color.xml` (+ SVG izvori), `play-store-512.png` |
| Ugradnja | Xcode asset katalog, jedna 1024 sa tri varijante (Any / Dark / Tinted). Bez zaobljenja i providnosti. | `mipmap-anydpi-v26/ic_launcher.xml` (+ `ic_launcher_round`), foreground i monochrome u `drawable/`, boja u `values/`. Skakač je u sigurnoj zoni (najdalja tačka 29,3 od 33). |

Boje: svetla `accent #2E4A8A` + skakač `#F8F8F5`; tamna `#0E1428` + `#7EA0E8`; tinted: siva skala na crnom.

### Ikone tabova (P2 · obe)

| Tab | iOS | Android |
|---|---|---|
| Igra | sopstveni simbol `chessko.knight` / `chessko.knight.fill` — iz `ikone/tab/tab_igra.svg` i `tab_igra_fill.svg` napraviti custom symbol (aplikacija SF Symbols → Template), asset katalog, rendering *template* | `ikone/android/ic_tab_igra.xml`, `ic_tab_igra_fill.xml` (24 dp, viewport 96) |
| Zadaci | `puzzlepiece` / `puzzlepiece.fill` | Material Symbols Rounded `extension` (outline / fill) |
| Put | `point.topleft.down.to.point.bottomright.curvepath` / `.fill` — **proveriti dostupnost za najniži podržani iOS** | Material Symbols Rounded `route` (jedan vektor uvesti ako ga nema u Compose ikonama) |

Zamenjuju: iOS `square.grid.3x3.fill` i `signpost.right.fill`; Android `play_arrow` i mapu.
Stanja: izabran = pun oblik `accent`, neizabran = obris `inkMuted`. Na `navBar`: izabran 8,53 / 6,65,
neizabran 5,86 / 5,62. Labele iz prevoda; ikona se ne menja po jeziku.

**Ako SF Symbols šablon za skakača ne može da se napravi automatski, uvezi `tab_igra.svg` i
`tab_igra_fill.svg` kao vektorske slike u asset katalog (template rendering, Preserve Vector Data),
ostavi TODO za pravi custom symbol i navedi to u završnom izveštaju. Ne zameni skakača drugom ikonom.**

---

## 11. Teme table (P2 · obe)

- [ ] **Klasična ostaje podrazumevana.**
- [ ] **Dodati dve teme** (posle postojećih 8, iste vrednosti na obe platforme, ravna boja):

| Tema | Svetlo polje | Tamno polje |
|---|---|---|
| Žalfija | `#E7EAE5` | `#87927F` |
| Glina | `#EEEAE8` | `#9A8A87` |

  Oznake na tabli (žuto, cijan, crveno) se ne menjaju — proverene su na obe teme (najslabija ΔE ≥ 20).
  Nazivi u svih 8 jezika idu kroz prevode (srpski: „Žalfija“, „Glina“).
- [ ] **Okvir table 1 pt** u boji tamnog polja aktivne teme (§ 5).
- [ ] **Mreža tema na Androidu** kao na iOS-u (§ 15) — sa 10 tema: adaptivna mreža.

---

## 12. Stil figura (P2 · obe)

- [ ] **Dodati stil „Reljef“** iz `figure-C-reljef/` (12 fajlova `wK.svg … bP.svg`, `viewBox 0 0 96 96`,
  bez rastera i fontova). Postojećih 8 stilova se ne menja.
  - Android: uvoz u VectorDrawable (Android Studio → Vector Asset). Senka koristi `clipPath`; ako
    uvoz ne uspe, zameni senku gotovim presekom (presek oblika figure i pravougaonika iz
    `clipPath`, izračunat skriptom ili u vektorskom alatu), proveri izgled i zabeleži to u izveštaju.
  - iOS: SVG u asset katalog sa „Preserve Vector Data“.
  - Proveri na uređaju na polju 36 pt (telefon) i 100 pt (iPad, § 13).
- [ ] **Dodati stil „Rez“** iz `figure-A-rez/` (12 fajlova, isti crtež kao „Reljef“ bez senke, 13 KB,
  bez clipPath). Ide kao poseban stil, odmah posle „Reljefa“. Uvoz isto kao za „Reljef“.
- [ ] Nazivi stilova kroz prevode; **prevesti i 5 postojećih stilova na ruski** (danas neprevedeno:
  Klasični, Neonski, Drvene, Metalne, Ravne).

---

## 13. Tablet (P1 · obe) — usvojeno pravilo veličine table

**Tablet** = širina prozora ≥ 600 (iOS `horizontalSizeClass == .regular`; Android
`WindowWidthSizeClass` Medium ili Expanded). U Split View / Slide Over / uskom prozoru važi
telefonsko pravilo.

**Jedna funkcija za veličinu table za sve ekrane** (Igra, Zadaci, koraci, lekcija, analiza).
Danas se razlikuje: Igra 560, Zadaci 661 na istom iPadu.

```
boardSide(window, isTablet, isLandscape, chromeTop, chromeBottom, railWidth):
  if !isTablet:
      return min(window.width − 2·Space.l, 560)              // telefon — bez promene
  if !isLandscape:
      s = min(window.width − 2·Space.l − 8,                   // 8 = eval traka
              window.height − chromeTop − chromeBottom − 264) // 264 ≈ kartice + akcije
  else:
      s = min(window.height − chromeTop − 2·Space.xl,
              window.width − railWidth − 400 − 3·Space.xl − 8) // 400 = panel
  return min(s, 800)                                         // očekivano ≥ 560; nikad ne prelivati
```

Očekivane vrednosti (provera):

| Uređaj | Portret | Pejzaž |
|---|---|---|
| iPhone | ≤ 560 | bez promene |
| Pixel Tablet 800 × 1280 dp | 760 | 664 |
| iPad Pro 13" 1032 × 1376 pt | 800 | 800 |
| iPad 11" 834 × 1194 pt | 794 | 688 |

Raspored:

- [ ] **Portret:** kolona široka kao tabla (+ eval traka 8), centrirana. Kartice igrača, uputstvo,
  status, akcije i lista poteza u toj koloni (danas 776 dp / 1016 pt). Sadržaj od vrha: zaglavlje →
  kartica ili uputstvo → tabla → akcije (`Space.l`). Višak visine: lista poteza (Igra), inače ostaje
  ispod. Nikad centriranje po visini ni prikivanje za dno.
- [ ] **Pejzaž:** tabla + panel 400 centrirani kao par, **vrhovi poravnati**. Panel Igre: kartice
  igrača, Vrati · Predaj · Više, lista poteza do dna table (danas na iPadu panel 745 sa dve kartice i
  379 pt praznog ispod table). Panel Zadataka: tagovi, uputstvo, „Prikaži rešenje“. Korak vežbe:
  isto kao Zadaci (danas traka i uputstvo idu na ~1350 pt).
- [ ] **Lekcija:** § 9. **Put:** kolona do 720.
- [ ] **Doslednost:** tabla na istoj levoj ivici i vrhu u Igri, Zadacima i koracima.
- [ ] **Android:** `NavigationRail` ≥ 840 dp, inseti (§ 4).
- [ ] Figure na polju do 100 pt — tačke legalnih poteza rastu sa poljem, prsten šaha ostaje 2 pt.

---

## 14. Šahovski sat (P2 · obe; polovine se ne diraju)

- [ ] Android: preko celog prozora (§ 4).
- [ ] **iOS: traka između polovina fiksno tamna** u obe teme (danas svetla `#F4F5F5` u svetloj).
- [ ] **Cifre iste:** SF Pro `.monospacedDigit()` (bez precrtane nule) ↔ Roboto
  `fontFeatureSettings = "tnum"`.
- [ ] **Objašnjenje kontrola:** vrednost iznad opisa (`Type.heading`, neprelomni razmak U+00A0 oko
  „+“ — „90 + 30 + 30“ se ne lomi), opis `Type.body` `ink` (17,43 / 14,76), redovi u `surface` grupi
  sa `line`.
- [ ] **Segmenti** (Blic / Ubrzani / Klasični): do 2 reda ili čipovi sa skrolom; najmanje `Type.label`
  (ruski „Классические шахматы“ ne staje u trećinu).
- [ ] **Izbor vremena:** ceo red je dugme (bez „Izaberi“); izabrano = kvačica `accent` + ivica 2 pt
  `accent`. ★ (zvanični FIDE format) u `inkMuted` ili oznaka „FIDE“ (`Type.label`); za čitače ekrana
  ★ sakriti.
- [ ] „Zatvori“ kao tekst-dugme `accent`.

---

## 15. Podešavanja (P2 · obe)

- [ ] **Grupisane `surface` liste sa `line` razdelnicima** umesto zasebnih `fill` kartica (prekidači);
  ikone `inkMuted` (5,86 / 5,43).
- [ ] **Jedan naslov po grupi** (`Type.heading` bold `ink`): bez duplog „Izgled table“ + „Tema
  table“, „Stil figura“ + „Stil“.
- [ ] **Redosled grupa kao na iOS-u** na obe.
- [ ] **Teme i stilovi u istoj adaptivnoj mreži:** najmanje 88 po koloni — Android
  `LazyVerticalGrid(GridCells.Adaptive(88.dp))` (umesto `LazyRow`, gde se vidi 5 od 8), iOS
  `LazyVGrid(.adaptive(minimum: 88))`.
- [ ] **Labele u mreži:** `Type.caption`, najviše 2 reda, rezervisana visina 2 reda.
  Android: `Hyphens.None` + `LineBreak.Heading`; ako najduža reč ne staje — autoSize do `Type.label`.
  iOS: `lineLimit(2)` + `minimumScaleFactor(0.9)`. Ništa se ne lomi usred reči („Классическая“).
- [ ] **Statistika:** „Remi“ u `ink` (nije upozorenje); Pobede `success`, Porazi `danger` mogu ostati.
- [ ] **Jezik (P2):**
  - Jedan red „Jezik · Srpski ›“ sa globusom (SF `globe` / Material `language`), u šestoj grupi
    (posle „Igra i pravila“), kao na iOS-u. Android danas: prva sekcija sa 8 otvorenih jezika.
  - Lista na obe, istim redom: **Sistem**, Deutsch, English, Français, Italiano, Srpski, Русский,
    हिन्दी, 中文 (简体). Ispod „Sistem“ sitno (`inkMuted`) trenutni jezik.
  - Android „Sistem“: `AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())`
    + `locales_config.xml` (aplikacija se tada vidi i u sistemskim podešavanjima jezika, Android 13+).

---

## 16. Lokalizacija (P2 · obe)

- [ ] Ruski: „Внешний vuд доски“ ima latinična slova → „Внешний вид доски“ (obe platforme).
- [ ] Ruski: prevesti 5 stilova figura (§ 12); proveriti da „Игровая комната“ ne pomera mrežu.
- [ ] Množine svuda kroz plurals / stringsdict (dani, koraci, zadaci, potezi).
- [ ] Obraćanje „ti“ svuda (dijalog predaje danas „Vi“).
- [ ] Rodno neutralne poruke o ishodu partije.
- [ ] Decimalni zarez i procenti kroz lokalni format.
- [ ] Bez reči „Premium“.
- [ ] Nemački („Klassisch“, „Zurücksetzen“) i hindi (viši glifovi) — **nijedna visina reda nije
  fiksna**; ništa se ne odseca.

---

## 17. Za proveru u kodu (moguće greške, ne samo dizajn)

- [ ] Tačnost „0,0 %“ posle 8–9 poteza (§ 6).
- [ ] Prazan avatar „Ti“ na tabletu (snimak 44) — beli kralj se ne crta.
- [ ] iOS: sve sistemske boje za podloge (§ 3) — lista mesta u izveštaju.
- [ ] iOS radijalni gradijent na tamnim poljima Polarne i Sajbera, a Android ravna boja —
  **ne menjati**, samo zabeležiti razliku u izveštaju (odluka vlasnika).

---

## 18. Redosled rada (predlog)

1. **Inventar (bez izmena):** mapa tokena i tipografije u kodu za obe platforme; spisak mesta sa
   sistemskim bojama (iOS) i hardkodovanim hex vrednostima; spisak ekrana i komponenti (kartica,
   dijalog, dugme, okvir uputstva, kartica igrača, lista poteza, mreža tema). Kratak plan po fazama.
2. **Tokeni + globalna pravila** (§ 2, § 3) — najveći efekat, najmanje rizika.
3. **Navigacija i hrom** (§ 4) + ikone (§ 10).
4. **Zajedničke komponente:** kartica igrača, okvir uputstva, akciona traka, lista poteza, dijalog,
   dugmad, oznaka klase poteza. Pa ekrani koji ih koriste: Igra (§ 5), Zadaci i koraci (§ 7),
   Analiza (§ 6).
5. **Put** (§ 8) i **Lekcija** (§ 9).
6. **Sat** (§ 14) i **Podešavanja** (§ 15).
7. **Tablet** (§ 13).
8. **Teme i figure** (§ 11, § 12).
9. **Lokalizacija** (§ 16) i provere (§ 17).

Svaka faza: obe platforme, obe teme, jedan commit po fazi, kratka beleška šta je urađeno i šta
nije. **Sve faze se rade u jednom prolazu, bez čekanja na potvrdu između faza**; nejasnoće se rešavaju
prema nameri dokumenta i beleže za završni izveštaj. Kontrola je na kraju (§ 19).

---

## 19. Definicija završenog (QA)

Snimci posle izmena, isti kadrovi kao u folderu, za:

- iPhone i Android telefon — svetla i tamna;
- iPad (portret i pejzaž) i Android tablet (portret i pejzaž) — svetla i tamna;
- srpski, ruski, nemački i hindi bar za Igru, Podešavanja, Sat i Lekciju.

Provera po ekranu:

- [ ] Nijedan tekst ispod 4,5 : 1, nijedna ikona/ivica koja nosi značenje ispod 3 : 1 (tabele iznad).
- [ ] Nijedna sistemska boja kao podloga na iOS-u; ista paleta u tamnoj temi na obe platforme.
- [ ] Na Androidu se donja traka ne vidi ispod Analize, Podešavanja i Sata; ručka ne seče labele.
- [ ] Nijedan status ne liči na dugme; nijedan red „–“.
- [ ] Ništa se ne lomi usred reči i ništa se ne odseca u ru / de / hi.
- [ ] Na tabletu tabla prati § 13, sadržaj počinje od vrha, nema razvučenih kartica.
- [ ] Ništa iz „Šta se NE dira“ nije promenjeno.
