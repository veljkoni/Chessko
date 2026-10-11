# Dizajnerski pregled pred izdanje — plan implementacije

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Svaki task je jedna faza iz § 18 specifikacije.

**Goal:** Sprovesti dizajnerski pregled (spec `docs/design/CHESSKO-IMPLEMENTACIJA.md`) na iOS-u i Androidu, u paritetu, bez novih zavisnosti.

**Architecture:** Izmene idu kroz postojeće dizajn sisteme — iOS `Chessko/Views/DesignSystem.swift` (`enum DS`, `Font.ds*`), Android `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/ui/theme/DesignSystem.kt` (`DS`, `Type`). Zajedničke komponente se izdvajaju tamo gde ih spec traži (§ 18 korak 4), po jedna po platformi.

**Tech Stack:** Swift 6 / SwiftUI (iOS 18+), Kotlin / Jetpack Compose (Material 3).

**Spec:** `docs/design/CHESSKO-IMPLEMENTACIJA.md` (kopija; original + materijal: `/Users/veljkoodobasic/Desktop/chessko-dizajn-snimci/` — `ikone/`, `figure-C-reljef/`, `figure-A-rez/`, snimci `NN_*.png`, `claude-design-brief.md`). Projektna pravila: `CLAUDE.md` u korenu.

## Global Constraints

- Paritet: svaka stavka označena „obe" menja se na iOS-u i Androidu u istom tasku i istom commit-u; obe teme (svetla/tamna).
- **Odluka vlasnika (menja spec § 0/§ 11/§ 12 — greška u specifikaciji):** teme table ostaju samo Klasična, Šumska, Drvo, Ugalj (+ nove Žalfija, Glina = 6); stilovi figura ostaju samo Klasični, Igraonica, Staklene (+ novi Reljef, Rez = 5). **Brišu se** teme Polarna, Smaragd, Pesak, Sajber i stilovi Neonski, Drvene, Metalne, Ravne, Tanke — na obe platforme, u Task-u 12.
- Ne menjati: boje tema table koje ostaju, oznake na tabli (žuto, cijan `#00D2FF`, crveno `#EF4444`), polovine šahovskog sata i traku između njih (fiksno tamna), strane eval trake, zlatnu oznaku mata, pločicu promocije, birač boje pri novoj igri, emoji u porukama (🎉 🏆 🔥 🧩), stilove figura koji ostaju.
- Bez novih zavisnosti (fontova, biblioteka, Gradle/SPM). Ikone: SF Symbols i Material. Izuzeci: sopstveni skakač za tab Igra i jedan uvezen vektor `route` (§ 10).
- Nove boje se ne uvode, osim dve nove teme table (§ 11). Menjaju se samo `inkMuted` svetla `#6B7280 → #5E6573` i `warning` svetla `#B07D2B → #865F21`; tamna tema bez promene. Izvedene tinte se računaju iz postojećih tokena.
- Tekstovi: kroz postojeće resurse prevoda (iOS `Localizable.xcstrings` / `build_localizations.py` prema `CLAUDE.md`; Android `Loc.kt`). Nov string = svih 8 jezika (sr, en, fr, de, it, ru, zh-Hans, hi). Bez pouzdanog prevoda: srpski ili engleski + oznaka u `IMPLEMENTACIJA-LOG.md`.
- Ako se kod razlikuje od pretpostavki dokumenta: rešenje najbliže nameri, odluka i razlog u `IMPLEMENTACIJA-LOG.md`. Rizična stavka: preskoči, `TODO(design § N)` u kodu, navedi u logu.
- Svaki task: build + postojeći testovi za obe platforme moraju proći pre commit-a:
  - iOS: `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'id=E068B886-0A3F-47D4-87FF-A809E0FEA086' build` (cilj po id-ju, ne po imenu) i `swift test`.
  - Android: `cd ChesskoAndroid && ./gradlew assembleDebug testDebugUnitTest`; broj testova se čita iz XML-a (`app/build/test-results/testDebugUnitTest/`), ne iz izlaznog koda.
  - Testovi koji namerno čuvaju stare vrednosti (npr. `ContrastTest.knownSubAAPairsDoNotGetWorse`, `TypographyScaleTest`, `SpacingTokensTest`, `LocTest`) ažuriraju se svesno kad spec menja vrednost, uz beleženje u logu — ne gase se.
- **Emulator i simulator se NE pokreću u taskovima 1–12.** Samo build i JVM/`swift test`. Task 13 jedini sme da digne emulator (za instrumentisane testove), po pravilima iz `CLAUDE.md` (`-no-window -gpu host`, gašenje `adb emu kill` + `./gradlew --stop` + `pgrep -f qemu-system` prazan).
- `create_xcode_project.py` se NE pokreće. Nov Swift fajl ili asset ide ručno u `project.pbxproj` (dokazati da se kompajlira u aplikaciju).
- Jedan commit po tasku, poruka navodi sekcije (npr. „§ 2, § 3: tokeni i podloge"), sa trailerom `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Bez push-a.
- Svaki task dopisuje svoj odeljak u `IMPLEMENTACIJA-LOG.md` (koren repoa): urađeno po stavkama spec-a, neurađeno i zašto, odluke, `TODO(design § N)`.
- `CLAUDE.md` changelog dopisuje samo Task 13.

---

### Task 1: Inventar (§ 18 korak 1) — bez izmena koda

Pročitaj ceo spec. Napravi `IMPLEMENTACIJA-LOG.md` u korenu repoa sa:
1. Mapom „ime u dokumentu → ime u kodu" za obe platforme: boje (`DS.*`), tipografija (`Type.*` / `Font.ds*`), razmaci (`Space.xs 4 · s 8 · m 12 · l 16 · xl 24`), radijusi (`Radius.s 8 · m 12 · l 16`), `navBar`, `maxBoardSide`.
2. iOS: spisak svih mesta gde se sistemska boja koristi kao podloga (`systemBackground`, `systemGroupedBackground`, `secondarySystemBackground`, `Color(uiColor:)`, `.secondary` za podloge, `Color.black`, `#1C1C1E`, `#1F1F1F`, `#F2F2F7`, `Color.primary.opacity(...)` kao ispuna, nativna grouped `List` pozadina, `.ultraThinMaterial`) — fajl:linija.
3. Hardkodovane hex vrednosti van tema table / sata / izuzetaka iz § 0, obe platforme — fajl:linija + uloga.
4. Spisak zajedničkih komponenti iz § 18 korak 4 (kartica, dijalog, dugme, okvir uputstva, kartica igrača, akciona traka, lista poteza, oznaka klase poteza, mreža tema) — gde danas žive na svakoj platformi i da li postoje.
5. Kratak plan po fazama (taskovi 2–13 ovog plana) sa rizicima koje vidiš.
Commit samo `IMPLEMENTACIJA-LOG.md` („§ 18: inventar"). Build/testovi nisu potrebni (nema izmena koda).

### Task 2: Tokeni i globalna pravila (§ 2, § 3)

Spec § 2 i § 3 u celosti, obe platforme: dve vrednosti tokena; podloga svakog ekrana `DS.ground` (iOS: ukloniti sistemske podloge sa spiska iz Task 1 — ekrani Igra, Zadaci, Lekcija, Podešavanja, svi koraci Puta, iPad); kartica = `surface` + 1 pt `line`; `fill` nije kartica; grupe i razmaci; `accent` samo za tapabilno/izabrano; naslovi sekcija `Type.heading` bold `ink`; dugi tekst `ink`; dijalozi jedan izgled (`surface`, `Radius.l`, primarno `accent`+`onAccent` `Radius.m`, sporedno tekst-dugme `accent`, nikad „Zatvori/Otkaži" u `inkMuted`); brojevi kroz lokalni format; množine kroz plurals/stringsdict (uvedi mehanizam ako ne postoji — Android `Loc.kt` danas nema množine; iOS `.stringsdict` ili String Catalog plural varijante). Ažuriraj `ContrastTest` pinove za nove vrednosti (parovi koji sada prelaze AA izlaze iz `knownSubAAPairsDoNotGetWorse`). Ono što konkretno pripada kasnijim ekranima (Igra, Analiza, Put…) uradi samo u meri globalnog pravila; detalji ekrana su u kasnijim taskovima.

### Task 3: Navigacija i hrom (§ 4) + ikone (§ 10)

§ 4 u celosti (Android celoekranski prikazi Analize, Podešavanja i Sata; inseti donje trake; `NavigationRail` ≥ 840 dp; app bar u Lekciji; naslov „Chessko" `Type.heading` semibold) i § 10 u celosti: ikonica aplikacije I1 (iOS asset katalog Any/Dark/Tinted iz `ikone/ios/`; Android adaptivna ikona iz `ikone/android/`), ikone tabova (Igra = sopstveni skakač iz `ikone/tab/` i `ikone/android/ic_tab_igra*.xml`; Zadaci = `puzzlepiece`/`extension`; Put = SF `point.topleft.down.to.point.bottomright.curvepath` ako postoji na iOS 18, Material `route` kao jedan uvezen vektor). Ako SF custom symbol ne može automatski: SVG kao template vektor u asset katalogu + `TODO(design § 10)`. Materijal je u `/Users/veljkoodobasic/Desktop/chessko-dizajn-snimci/ikone/`.

### Task 4: Zajedničke komponente + Igra (§ 18 korak 4, § 5)

Izdvoj/uskladi zajedničke komponente na obe platforme: kartica igrača (i protivnika), okvir uputstva, akciona traka (Vrati · Predaj · Više), lista poteza, dijalog, dugmad, oznaka klase poteza (komponenta; upotreba u Analizi je Task 6). Zatim ekran Igra po § 5 u celosti (kartica protivnika bez „–", kartica igrača sa „Na potezu", aktivna ivica 2 pt accent, procena u kartici igrača, akcije Vrati · Predaj · Više, lista poteza sa praznim stanjem, dijalog predaje „ti", kraj partije rodno neutralno, meni Nova igra bez „Premium", izbor boje — birač se ne dira, telefon pejzaž, okvir table 1 pt u boji tamnog polja).

### Task 5: Zadaci i koraci Puta (§ 7)

§ 7 u celosti, obe platforme, koristeći komponente iz Task 4 (okvir uputstva, kartica igrača/protivnika). Uključuje uklanjanje „Pokušaj ponovo" sa Androida tokom rešavanja (dugme na ekranu greške pri učitavanju ostaje), iOS podlogu koraka `ground`, sadržaj od vrha, „Zatvori" kao tekst-dugme accent, traku napretka.

### Task 6: Analiza partije (§ 6, § 17 tačnost)

§ 6 u celosti, obe platforme: klase poteza simbolom i ispunom (komponenta iz Task 4), `accent` se ne koristi za klasu, ikona prelomnog poteza `warning`, lista poteza kao u Igri, procena za početnike („−2,3" + rečenica), mini-tabla (P3, opciono). **Tačnost „0,0 %": proveri proračun** u `Chessko/Models/MoveAnalysis.swift` i `ChesskoAndroid/.../models/MoveAnalysis.kt` — reprodukuj testom (npr. partija od 8–9 poteza sa jednim promašajem) pre izmene; ako je greška u formuli, popravi na obe platforme uz test; ispod praga poteza prikaži „—" + „Premalo poteza za procenu tačnosti.". Nalaz upiši u log (§ 17).

### Task 7: Put (§ 8)

§ 8 u celosti: Android preuzima iOS raspored; status koraka ikonom; trenutni korak na `fill`; traka napretka poglavlja „1/3"; dugme „Nastavi: …"; dnevni cilj i niz dana kroz množine (mehanizam iz Task 2); P3 sažeta zaključana poglavlja; tablet kolona do 720.

### Task 8: Lekcija (§ 9)

§ 9 u celosti, obe platforme: hijerarhija iz tabele, naslovi sekcija nisu accent, kutija pravila (`warning` 10 % preko `surface`, naslov `Type.body` bold), emoji → glifovi sa U+FE0E i ikone (iOS lekcijski simboli i Android `SYMBOL_TO_GLYPH` — vidi `CLAUDE.md` „Lekcijski glifovi"), čipovi figura, ritam, tabela vrednosti („neprocenjiv", „2 po strani"), „Elementarni matovi" ikona, „Završi korak" `Radius.m`, tablet kolona 640 i pejzaž u dve kolone. Lekcijski JSON se menja samo ako je neophodno i bajt-identično na obe platforme (`Chessko/Content/lessons` ↔ `ChesskoAndroid/app/src/main/assets/lessons`).

### Task 9: Šahovski sat (§ 14)

§ 14 u celosti; polovine sata i traka između njih se ne diraju na Androidu; na iOS-u traka između polovina postaje fiksno tamna u obe teme (iOS danas svetla — to je usklađivanje sa Androidom, dozvoljeno spec-om).

### Task 10: Podešavanja (§ 15)

§ 15 u celosti: grupisane `surface` liste, jedan naslov po grupi, redosled grupa kao na iOS-u na obe, adaptivna mreža tema i stilova (88), labele bez loma usred reči, statistika „Remi" u `ink`, jezik kao jedan red u šestoj grupi sa opcijom „Sistem" na obe i istim redosledom jezika (Android „Sistem" preko `AppCompatDelegate.setApplicationLocales` + `locales_config.xml` — ako to traži novu zavisnost (`appcompat`) koje nema, ne dodaj je: reši „Sistem" postojećim mehanizmom aplikacije i zabeleži odluku).

### Task 11: Tablet (§ 13, § 17 avatar)

§ 13 u celosti: jedna funkcija `boardSide` za sve ekrane sa tablom na obe platforme (Igra, Zadaci, koraci, lekcija, analiza), sa očekivanim vrednostima iz tabele kao JVM/`swift test` testovima čiste funkcije; raspored portret/pejzaž; doslednost leve ivice; figure do 100 pt. Postojeći `DS.maxBoardSide` i `BoardWidthCapTest` se usklađuju (telefon ostaje 560). Uz to § 17: prazan avatar „Ti" na tabletu (snimak 44) — nađi uzrok i popravi ili zabeleži.

### Task 12: Teme table i stilovi figura (§ 11, § 12)

**Prvo brisanje (odluka vlasnika, vidi Global Constraints):** ukloni teme Polarna, Smaragd, Pesak, Sajber i stilove Neonski, Drvene, Metalne, Ravne, Tanke na obe platforme (kod, asseti, prevodni ključevi koji ostanu bez pozivaoca, podešavanja i prečica prevlačenjem kroz teme/stilove); sačuvan izbor obrisane teme/stila pada na podrazumevani (Klasična / Klasični) bez pada. Zatim § 11: dve nove teme Žalfija (`#E7EAE5`/`#87927F`) i Glina (`#EEEAE8`/`#9A8A87`) posle preostale 4, ravna boja, obe platforme, nazivi kroz prevode; Klasična ostaje podrazumevana. § 12: stilovi „Reljef" (`figure-C-reljef/`) i „Rez" (`figure-A-rez/`) posle preostala 3; iOS asset katalog sa Preserve Vector Data (ručno u `project.pbxproj` ako treba); Android VectorDrawable (konverzija skriptom; `clipPath` senka — ako konverzija ne uspe, presek oblika, zabeleži). Prevesti na ruski stilove koji ostaju a nemaju prevod (Klasični). Sačuvan izbor stila/teme koji ne postoji mora pasti na podrazumevani, ne srušiti učitavanje.

### Task 13: Lokalizacija, provere i završni izveštaj (§ 16, § 17, § 19)

§ 16 u celosti (ruski „Внешний вид доски" na obe platforme, množine svuda, „ti", rodno neutralno, lokalni format brojeva, bez „Premium", bez fiksnih visina reda za de/hi). § 17: iOS spisak sistemskih boja (stanje posle Task 2), razlika radijalnog gradijenta Polarne/Sajbera je nestala brisanjem tih tema (Task 12) — zabeležiti. Pokreni `connectedDebugAndroidTest` jednom (emulator po pravilima iz `CLAUDE.md`, ugasiti odmah). Prođi kontrolnu listu § 19 nad kodom (bez snimanja) i u `IMPLEMENTACIJA-LOG.md` napiši završni izveštaj: urađeno/neurađeno po sekcijama, sve odluke i odstupanja uz razlog, svi `TODO(design § N)` (grep), stavke § 17 (tačnost „0,0 %", prazan avatar), spisak ekrana za ponovno snimanje (telefon i tablet, obe teme, sr/ru/de/hi). Dopiši changelog unos u `CLAUDE.md` i ažuriraj sekcije koje su postale netačne (paleta `inkMuted`/`warning`, broj tema/stilova, broj testova).
