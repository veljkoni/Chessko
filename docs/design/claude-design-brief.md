# Chessko — dizajn brief za vizuelni pregled

> Ovaj dokument je ulaz za dizajnerski pregled. Cilj: pogledati aplikaciju očima dizajnera,
> pronaći šta deluje nedovršeno, neujednačeno ili slabo čitljivo, i predložiti konkretna
> doterivanja **unutar postojećeg dizajn sistema**. Ne tražimo redizajn od nule.

---

## 1. Šta je Chessko

Šahovska aplikacija za učenje i igru, na **iOS-u (SwiftUI)** i **Androidu (Jetpack Compose)**.
Obe platforme imaju iste funkcije, isti sadržaj i istu paletu — paritet je obavezan.

- **Korisnik:** početnik do srednji igrač (rejting zadataka 600–2200), često neko ko se tek vraća šahu.
  Ton je miran i ohrabrujući, ne takmičarski.
- **Jezik:** izvorno srpski (latinica), plus en / fr / de / it / ru / zh-Hans / hi.
- **Radi bez interneta.** Nema naloga, reklama ni mreže.

Tri taba (donja navigacija):

| Tab | Šta radi |
|---|---|
| **Igra** | Partija protiv računara (sopstveni AI ili Stockfish), igra sa prijateljem na istom uređaju, šahovski sat. Posle partije: analiza. |
| **Zadaci** | Dnevni zadatak po datumu + neograničeno vežbanje („Sledeći zadatak"), Elo rejting igrača. |
| **Put** | Kurikulum: 6 poglavlja (Osnove, Notacija, Otvaranje, Taktika, Središnjica, Završnica), svako sa koracima tipa lekcija / vežba / test / partija. Dnevni cilj i niz dana zaredom. |

---

## 2. Dizajn pravac: „Tiho i precizno"

Sistemski iOS urađen kako treba: neutralne podloge, **jedan uzdržan akcent**, jedna tipografska
skala. **Tabla je jedina zasićena stvar na ekranu.** Boja nosi značenje (uspeh, upozorenje,
greška), nikad ukras.

### Paleta (tokeni — iste vrednosti na obe platforme)

| Token | Svetla | Tamna | Uloga |
|---|---|---|---|
| `accent` | `#2E4A8A` | `#7EA0E8` | jedini akcent; izabrano stanje, primarna dugmad |
| `onAccent` | `#FFFFFF` | `#161A22` | tekst/ikone na `accent` |
| `ground` | `#F2F3F7` | `#0E1428` | podloga ekrana |
| `surface` | `#FFFFFF` | `#161D33` | kartice |
| `navBar` | `#FFFFFF` | `#131A2E` | navigacija |
| `fill` | `#E7EAF1` | `#1E2740` | čipovi, ispune, redovi prekidača |
| `line` | `#DFE3EC` | `#232C46` | linije |
| `ink` | `#161A22` | `#EEF1F7` | tekst |
| `inkMuted` | `#6B7280` | `#8B93A7` | sekundarni tekst |
| `success` | `#2F7D4F` | `#6FCF97` | rešeno, tačno |
| `warning` | `#B07D2B` | `#E0B252` | pravilo, oprez |
| `danger` | `#B3261E` | `#F2857A` | greška, predaja, mat |
| `scrim` / `onScrim` | crna 55% / bela | isto | modalni preklop |

### Tipografija (Apple lestvica, ista na Androidu)

`title 22 bold · heading 17 · body 15 · caption 12 · label 11 · mono 13`.
Izuzeci: cifre šahovskog sata (72), koordinate table (9).

### Razmaci i radijusi

`Space: 4 / 8 / 12 / 16 / 24` · `Radius: 8 / 12 / 16` · tabla najviše **560 pt/dp** po strani.

---

## 3. Šta se NE dira (namerno van tokena)

Predlozi mogu da ih pomenu, ali ih ne treba tretirati kao greške:

- **Boje table.** Korisnik bira jednu od 8 tema: Klasična, Šumska, Drvo, Ugalj, Polarna,
  Smaragd, Pesak, Sajber. Oznake na tabli: žuto za poslednji potez, cijan `#00D2FF` za selekciju,
  crveno `#EF4444` za šah.
- **Stilovi figura.** 8 stilova: Klasični, Neonski, Drvene, Metalne, Ravne, Tanke, Igraonica,
  Staklene. To je sadržaj, ne hrom.
- **Polovine šahovskog sata.** Crna i bela polovina prate **stranu u igri**, ne temu telefona.
  Traka između njih je takođe fiksno tamna.
- **Fiksni elementi:** strane eval trake (belo/crno), zlatna oznaka mata, pločica figure u
  izboru promocije, birač boje pri novoj igri.
- **Emoji u porukama** (🎉 🏆 🔥 🧩) — namerno, isti na obe platforme.

---

## 4. Ograničenja za svaki predlog

1. **Paritet iOS ↔ Android.** Predlog koji radi samo na jednoj platformi mora to da kaže.
2. **Obe teme.** Svaki par boja važi i u svetloj i u tamnoj temi.
3. **Kontrast se meri, ne procenjuje.** WCAG AA: 4,5:1 za tekst, 3:1 za ikone i ivice.
   Navedi par (boja + podloga) za svaku novu boju.
4. **Dugi jezici.** Ruski, nemački i hindi su znatno duži od srpskog. Ništa ne sme da se lomi
   usred reči niti da se odseca.
5. **Tablet.** iPad i Android tablet, portret i pejzaž.
6. **Bez novih zavisnosti** (fontova, biblioteka ikona). Ikone: SF Symbols na iOS-u,
   Material Icons na Androidu.
7. **Novi token = poslednja opcija.** Prvo probati postojeće.

---

## 5. Gde mislimo da je dizajn najslabiji

Ovo smo već sami uočili. Proveri, dopuni i predloži rešenje:

- **Odvajanje kartica od podloge je slabo.** `surface` naspram `ground` je samo 1,11:1, a ivica
  `line` ne prelazi 3:1 ni na jednoj podlozi. Kartice u Podešavanjima trenutno nemaju ivicu.
  Kako da se jasnije odvoje, bez nove boje?
- **Sedam parova je malo ispod praga u svetloj temi:** `inkMuted` na `ground` (4,36) i na `fill`
  (4,01), `warning` na `surface` (3,61) i na `ground` (3,26), ikona u žutoj kutiji pravila (2,94).
  Da li je bolje promeniti vrednost tokena (obe platforme) ili način upotrebe?
- **Analiza partije:** šest klasa poteza (najbolji / odličan / dobar / netačnost / greška /
  promašaj) prikazuje se sa samo četiri boje. Može li se razlika pokazati bez novih boja
  (oblik, ikona, intenzitet)?
- **Tablet u portretu** (Android): ekran Igra stoji uz vrh, donja polovina je prazna; kartice
  igrača idu celom širinom od 1600 px.
- **Ruski u Podešavanjima:** nazivi tema table i stilova figura se lome u mreži.
- **Dijalog analize** na Androidu ne pokriva donju navigaciju — vide se presečeni nazivi tabova.
- **Lekcije:** dosta teksta; da li je hijerarhija (naslovi, kutije pravila, citati, tabele,
  vežbe) dovoljno jasna za skeniranje? Naslov žute kutije pravila (12 bold) manji je od teksta
  ispod njega (15) — to je preuzeto sa iOS-a, ali vredi ga preispitati.
- **Šest simbola u lekcijama ostaje emoji** (👑 🏰 🐴 📐 🍴 ⬛), jer nema odgovarajuće ikone figure
  u Material/SF skupu. Među monohromatskim ikonama deluju šareno.
- **Put:** da li je napredak (poglavlje, korak, dnevni cilj, niz dana) čitljiv na prvi pogled?

---

## 6. Šta tražimo nazad

Po ekranu, kratko i konkretno:

1. **Nalaz** — šta ne valja i zašto (jedna rečenica).
2. **Predlog** — izraženo kroz tokene (`DS.surface`, `Space.l`, `Type.heading`…), ne kroz nove hex
   vrednosti, osim ako novi token stvarno treba.
3. **Kontrast** za svaki novi ili promenjen par, u obe teme.
4. **Prioritet:** visok (korisnik se zbuni ili ne vidi nešto) / srednji / nizak (poliranje).
5. **Platforma:** obe / samo iOS / samo Android.

Na kraju: lista od najviše 10 izmena, poređanih po vrednosti, koje bi pre javnog izdanja
najviše podigle utisak kvaliteta.

---

## 7. Snimci ekrana

Uz ovaj dokument idu snimci sa Android emulatora (telefon 1080×2400, tablet simuliran
2560×1600). Nalaze se u
`.superpowers/sdd/2026-09-21-faza-10-android-razmaci-tipografija/screenshots/`.

Preporučen izbor:

| Ekran | Svetla | Tamna |
|---|---|---|
| Igra, portret | `00_igra_light_portret.png` | `01_igra_dark_portret.png` |
| Igra, kraj partije i predaja | `15_…`, `16_…`, `17_…` | — |
| Nova igra (meni) | `19_nova_igra_meni_light.png` | — |
| Analiza partije | `18_analiza_light.png` | — |
| Igra, pejzaž | `24_igra_light_pejzaz.png` | — |
| Zadaci | `04_zadaci_light_portret.png`, `25_zadaci_light_pejzaz.png` | — |
| Put | `05_put_light.png`, `12_put_light_zavrsen_korak.png` | `40_put_dark.png` |
| Lekcija | `06_…` do `11_…` | `41_lekcija_dark_vrh.png`, `42_lekcija_dark_kralj_glif.png` |
| Koraci Puta | `13_korak_vezba_light.png`, `14_korak_partija_light.png` | — |
| Šahovski sat | `20_…` do `23_…` | — |
| Podešavanja | `02_…`, `03_…` | `36_…`, `37_…_ru.png` |
| Tablet | `44_tablet_igra_portret_light_5b.png`, `45_…`, `28_…`–`32_…` | — |

**Ne slati:** `26_zadaci_light_pejzaz_greska.png` (to je početni ekran telefona, ne aplikacija),
`43_zadaci_dark_portret.png` (splash ekran), `33_…`/`34_…` (stanje pre popravke) i
`38_…`/`39_…` (namerno stara tipografija).

Snimaka iOS-a i tamne teme za Zadatke, Analizu i Sat trenutno nema. Ako zatrebaju, možemo ih
napraviti.
