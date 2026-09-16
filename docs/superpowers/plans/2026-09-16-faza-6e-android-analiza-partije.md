# Faza 6e — Android: analiza partije

> **Za agentske izvršioce:** OBAVEZNA POD-VEŠTINA: koristi
> superpowers:subagent-driven-development za izvođenje ovog plana task po task.

**Cilj:** Posle svake partije Android prolazi sve pozicije kroz Stockfish, klasifikuje poteze po
gubitku u centipionima i prikazuje ekran sa tačnošću oba igrača, trakom poteza u boji i prelomnim
potezom — isto što iOS ima od Faze 5.

**Arhitektura:** Matematika se odvaja od motora. `models/MoveAnalysis.kt` je čist Kotlin bez
ijednog `android.*` uvoza, pa mu JVM testovi rade bez emulatora; `logic/UCIScoreParser.kt` isto.
Motor je zamenljiv, pravila klasifikacije nisu.

**Tehnologije:** Kotlin, Jetpack Compose, postojeći native Stockfish preko JNI. **Bez ijedne nove
zavisnosti.**

**Spec:** `docs/superpowers/specs/2026-09-05-chessko-v2-design.md`, sekcija 5.5
**iOS original:** `Chessko/Models/MoveAnalysis.swift`, `Chessko/Logic/UCIScoreParser.swift`,
`Chessko/ViewModels/AnalysisViewModel.swift`, `Chessko/Views/AnalysisView.swift`

---

## Globalna ograničenja

- **Ni jedna nova Gradle zavisnost.** Posebno ne `androidx.navigation`.
- **`Chessko/` (iOS) se čita radi poređenja, nikad ne menja.**
- **`create_xcode_project.py` se NIKAD ne pokreće.**
- Svaki `loc()` ključ mora biti **literal unutar `loc()`** i postojati u rečniku, na svih 8 jezika
  (`LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`).
- **Sav nov UI se rađa na `DS.*` tokenima.** Faza 6d je upravo završena; nijedna nova zakucana
  boja ne sme ući. Tema-zavisna boja i tema-izuzeta podloga se ne mešaju — ni u jednom smeru.
- `models/MoveAnalysis.kt` i `logic/UCIScoreParser.kt` **ne smeju imati nijedan `android.*` uvoz** —
  inače im testovi traže emulator (isto pravilo koje već drži `PathProgress.kt` i `PuzzleRating.kt`).

---

## Najvažnija pouka koju ova faza nasleđuje

iOS je parser ocene napisao po **pretpostavljenom** formatu. Stvarni je bio drugačiji. Parser bi
vraćao `nil` za svaku poziciju i cela faza ne bi radila — a **svih 6 testova je prolazilo**, jer su
koristili isti izmišljeni oblik kao i kod.

> **Test koji deli pretpostavku sa kodom ne testira ništa.**

Android ima **drugačiji motor od iOS-a**: native Stockfish preko JNI, ne `ChessKitEngine`. iOS-ov
format (`<score> <cp> 34.0`, sa zagradama i `Double`-om) ovde **skoro sigurno ne važi** — očekuje se
standardan UCI (`info depth 12 … score cp 34 … pv e2e4`). **Ali „skoro sigurno" je tačno ono što je
iOS koštalo.** Zato Task 2 počinje hvatanjem STVARNOG izlaza sa uređaja, pre nego što se napiše
ijedna linija parsera.

---

## Šta Android već ima (izmereno pre pisanja plana)

| stvar | stanje |
|---|---|
| native Stockfish | `cpp/stockfish/` + `cpp/bridge.cpp`, JNI |
| `logic/StockfishEngine.kt` | `object` singleton, 115 linija, `Channel` + `Mutex` |
| `getBestMove(fen, depth)` | postoji — ali **baca `info` linije**, čita samo `bestmove` |
| `GameState.fromFEN` | postoji |
| istorija poteza (`moveNotations`) | postoji |
| review mod | `isReviewing`, `goToMove(moveNumber)` u `GameViewModel` |
| cela Faza 5 | **ne postoji** — nema `MoveAnalysis`, `UCIScoreParser`, `AnalysisViewModel`, `AnalysisView` |

**Ključna razlika prema iOS-u koju plan mora da poštuje:** Android motor je **singleton sa
mutexom**. iOS je za analizu pravio zaseban primerak motora; Android to ne može. Posledica je u
Task-u 4 (otkazivanje).

---

## Ruling: emulator se diže DVAPUT, i oba puta su imenovana

Faza 6d-1 je dizala emulator po tasku (šest puta) iako `CLAUDE.md` traži grupisanje. Faza 6d-2 ga je
digla dvaput i to iskreno zapisala — jednom za vizuelnu proveru, jednom za instrumentisane testove,
jer je zatečen XML bio stariji od svih taskova.

Ova faza unapred priznaje **dva** razloga i grupiše oko njih:

1. **Task 2** — hvatanje stvarnog UCI izlaza. Ne može se zameniti čitanjem koda; to je cela poenta.
2. **Task 6** — vizuelna provera + instrumentisani testovi + merenje trajanja analize.

Nijedan drugi task ne sme dići emulator. `-gpu host`, **NIKAD `-gpu off`**; gašenje odmah po
prolazu (`adb emu kill`, `./gradlew --stop`, `pgrep -f qemu-system` prazno).

---

## Struktura fajlova

| fajl | odgovornost | task |
|---|---|---|
| `models/MoveAnalysis.kt` | `EngineScore`, `MoveClass`, `AnalyzedMove`, `GameAnalysis` — čista matematika | 1 |
| `logic/UCIScoreParser.kt` | čitanje ocene iz `info` linije | 2 |
| `logic/StockfishEngine.kt` (izmena) | nov `evaluate(fen, depth)` | 3 |
| `viewmodels/AnalysisViewModel.kt` | tok analize, napredak, otkazivanje | 4 |
| `ui/AnalysisView.kt` | ekran | 5 |
| `MainActivity.kt`, `ui/StepGameView.kt` (izmena) | ulazne tačke | 5 |
| `app/src/test/.../MoveAnalysisTest.kt` | JVM testovi matematike | 1 |
| `app/src/test/.../UCIScoreParserTest.kt` | JVM testovi parsera | 2 |
| `CLAUDE.md` | dokumentacija | 6 |

---

## Task 1: Matematika analize

**Fajlovi:**
- Kreiraj: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/models/MoveAnalysis.kt`
- Kreiraj: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/MoveAnalysisTest.kt`

**Interfejsi:**
- Proizvodi: `EngineScore` (`Cp(Int)` / `Mate(Int)`, svojstvo `centipawns`), `MoveClass`,
  `AnalyzedMove`, `GameAnalysis` sa `build(notations, scores, engineBestMatched)`,
  `cpLoss(before, after)`, `accuracy(avgCpLoss)`, `turningPoint`
- Troši: ništa

**Obavezno pročitaj `Chessko/Models/MoveAnalysis.swift` pre pisanja.** Prenosiš, ne izmišljaš.

- [ ] **Korak 1: Napiši testove PRE koda**

Sedamnaest tvrdnji koje iOS pokriva. Svaka mora da padne dok koda nema:

```kotlin
// 1-3  mat u centipionima: monoton i daleko iznad realnih ocena
assertEquals(9999, EngineScore.Mate(1).centipawns)
assertEquals(9998, EngineScore.Mate(2).centipawns)
assertEquals(-9999, EngineScore.Mate(-1).centipawns)
// 4    granica: mat dalji od 99 poteza se ne prelива u drugu stranu
assertEquals(10000 - 99, EngineScore.Mate(150).centipawns)
// 5-9  klase poteza po pragovima iz spec-a 5.5
assertEquals(MoveClass.EXCELLENT, MoveClass.classify(19, false))
assertEquals(MoveClass.GOOD,      MoveClass.classify(20, false))
assertEquals(MoveClass.INACCURACY, MoveClass.classify(50, false))
assertEquals(MoveClass.MISTAKE,   MoveClass.classify(100, false))
assertEquals(MoveClass.BLUNDER,   MoveClass.classify(300, false))
// 10   potez motora je BEST bez obzira na izmeren gubitak
assertEquals(MoveClass.BEST, MoveClass.classify(900, true))
// 11-12 cpLoss = before + after (perspektiva se okrece), clamp 0…1000
assertEquals(0,    GameAnalysis.cpLoss(EngineScore.Cp(30), EngineScore.Cp(-30)))
assertEquals(1000, GameAnalysis.cpLoss(EngineScore.Mate(1), EngineScore.Mate(1)))
// 13-14 tacnost: 0 gubitka -> 100, veliki gubitak -> 0, nikad van [0,100]
// 15   build: neslaganje duzina vraca praznu analizu, ne pad
// 16   prelomni potez: najveci gubitak >= 100
// 17   prelomni potez ISKLJUCUJE .best
```

- [ ] **Korak 2: Pokreni testove, potvrdi da padaju**

```bash
cd ChesskoAndroid && ./gradlew testDebugUnitTest
```
Očekivano: pad na nepostojeće simbole.

- [ ] **Korak 3: Napiši `MoveAnalysis.kt`**

Prenesi iz iOS-a doslovno, uključujući **razloge u komentarima** — oni su plaćeni merenjem:

- `centipawns`: `mate(n)` → `n > 0 ? 10000 - min(|n|,99) : -10000 + min(|n|,99)`
- `classify`: **provera `playedEngineBest` ide PRVA.** Razlog: pozicija pre poteza i posle njega
  pretražuju se nezavisno, iz različitih čvorova, pa i potez motora ume da da mali gubitak.
- `cpLoss(before, after)` = `max(0, min(1000, before.centipawns + after.centipawns))`.
  **Sabiranje nije greška** — `after` je iz ugla protivnika, pa je vrednost iz ugla igrača `-after`,
  a gubitak `before - (-after)`.
- `maxCpLoss = 1000`: bez gornje granice jedan propušten mat (razlika ~20.000) sam odredi prosek
  cele partije i tačnost padne na ~0 iako je ostatak bio solidan. 1000 je trostruko iznad praga za
  promašaj (300), pa ne sakriva nijednu grešku.
- `accuracy(avg)` = `103.1668 * exp(-0.04354 * avg) - 3.1669`, ograničeno na `0…100`
- `turningPoint`: najveći gubitak, **ali samo ako klasa nije `BEST`** i ako je `>= 100`
- `build`: `scores.size == notations.size + 1` i `engineBestMatched.size == notations.size`;
  **neslaganje vraća praznu analizu, ne baca** — pad bi srušio aplikaciju posle partije, u trenutku
  kad korisnik nije tražio ništa osim rezultata

**Nijedan `android.*` uvoz.** Samo `kotlin.math`.

- [ ] **Korak 4: Testovi prolaze**

Broj čitaj iz `app/build/test-results/testDebugUnitTest/*.xml`, **ne iz izlaznog koda**.
Polazno stanje: 47 JVM testova.

- [ ] **Korak 5: Dokaži mutacijom da testovi mogu da padnu**

Obori `maxCpLoss` na 100000 i potvrdi pad; obori redosled u `classify` (stavi `playedEngineBest`
posle `when`) i potvrdi pad testa 10. Zalepi oba izlaza.

- [ ] **Korak 6: Commit**

```bash
git add ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/models/MoveAnalysis.kt \
        ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/MoveAnalysisTest.kt
git commit -m "Faza 6e, Task 1: matematika analize partije"
```

---

## Task 2: Parser UCI ocene — počinje HVATANJEM, ne pisanjem

**Fajlovi:**
- Kreiraj: `ChesskoAndroid/.../logic/UCIScoreParser.kt`
- Kreiraj: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/UCIScoreParserTest.kt`
- Privremeno izmeni: `logic/StockfishEngine.kt` (samo logovanje, vraća se u Koraku 3)

**Interfejsi:**
- Troši: `EngineScore` iz Task-a 1
- Proizvodi: `UCIScoreParser.parse(line: String): EngineScore?`

- [ ] **Korak 1: Uhvati STVARAN izlaz motora — emulator, prvi od dva dizanja**

Ovo je jedini korak koji ne sme da se preskoči ni skrati. iOS je ovde pao.

Privremeno dodaj `android.util.Log.d("UCI", output)` u `listenOutput()` u `StockfishEngine.kt`,
sagradi, pokreni, odigraj jedan potez protiv Stockfish-a, pa:

```bash
export ANDROID_HOME=~/Library/Android/sdk
$ANDROID_HOME/platform-tools/adb logcat -d -s UCI:D > /tmp/uci-stvarni-izlaz.txt
```

**U izveštaj zalepi bar 10 stvarnih `info` linija, doslovno.** Ne prepričavaj ih.
Iz njih izvedi format. Obrati pažnju na:
- da li je `score cp 34` ili nešto drugo
- kako izgleda `score mate N` (i negativan mat)
- ima li `lowerbound` / `upperbound` sufiksa — **te linije nose nepotpunu ocenu**
- ima li `multipv`

Odmah po hvatanju ugasi emulator (`adb emu kill`, `./gradlew --stop`, `pgrep -f qemu-system`).

- [ ] **Korak 2: Vrati `StockfishEngine.kt` u prvobitno stanje**

```bash
git diff --stat ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/logic/StockfishEngine.kt
```
Očekivano: prazno. Logovanje je bilo privremeno.

- [ ] **Korak 3: Napiši testove od UHVAĆENIH linija**

Svaki test koristi **doslovno prekopiranu liniju** iz `/tmp/uci-stvarni-izlaz.txt`, ne rekonstrukciju.
Pokrij: običnu `cp` ocenu, negativnu, `mate` pozitivan i negativan, liniju bez ocene (`info depth 1
currmove …`) koja mora dati `null`, i — ako postoje — `lowerbound`/`upperbound`, koje takođe daju
`null` jer ocena nije konačna.

- [ ] **Korak 4: Pokreni, potvrdi pad. Napiši `UCIScoreParser.kt`. Pokreni, potvrdi prolaz.**

Bez ijednog `android.*` uvoza.

- [ ] **Korak 5: Commit**

```bash
git commit -m "Faza 6e, Task 2: parser UCI ocene, pisan po uhvacenom izlazu"
```

---

## Task 3: `evaluate()` na motoru

**Fajlovi:**
- Izmeni: `ChesskoAndroid/.../logic/StockfishEngine.kt`
- Kreiraj/izmeni test: `app/src/androidTest/.../StockfishEvaluateTest.kt`

**Interfejsi:**
- Troši: `UCIScoreParser`, `EngineScore`
- Proizvodi: `suspend fun evaluate(fen: String, depth: Int = 12): EngineScore?`

- [ ] **Korak 1: Dodaj `evaluate`, po uzoru na postojeći `getBestMove`**

Razlike od `getBestMove`, sve tri obavezne:

1. **Čita `info` linije**, ne samo `bestmove`.
2. **Uzima POSLEDNJU viđenu ocenu pre `bestmove`.** Motor tokom produbljivanja šalje ocenu za
   svaku dubinu; zanima nas ona sa pune dubine, ne prva koju je prijavio.
3. Vraća i podatak da li je odigrani potez bio `bestmove` — potreban je za `engineBestMatched`.
   Predlog: `data class PositionEval(val score: EngineScore, val bestMove: String?)`.

Zadrži `searchMutex` i pražnjenje kanala, isto kao `getBestMove`.

- [ ] **Korak 2: Terminalna pozicija se NE šalje motoru**

```kotlin
// Za poziciju bez legalnih poteza Stockfish ne posalje nijednu `score` liniju,
// pa bi analiza vracala null za SVAKU odigranu partiju — poslednja pozicija je
// uvek mat ili pat. Resava se iz PRAVILA, preko generisanja poteza, a NE preko
// `state.status`, koji `GameState.fromFEN` ostavlja na Playing i za mat.
```
Ako nema legalnih poteza: šah → `Mate(0)`, inače → `Cp(0)`.

**Proveri u `GameState.kt` da li `fromFEN` i na Androidu ostavlja `Playing`** — `CLAUDE.md` tvrdi da
da, ali otvori i potvrdi pre nego što se osloniš.

- [ ] **Korak 3: Instrumentisani test**

`evaluate` traži živ motor, pa test ide u `androidTest`, ne u `test`. Tvrdi: početna pozicija daje
ocenu blizu nule (`|cp| < 100`); pozicija sa matom u jedan daje `Mate` sa pozitivnim brojem;
terminalna pozicija (mat na tabli) vraća `Mate(0)` **bez slanja motoru**.

- [ ] **Korak 4: Commit**

---

## Task 4: `AnalysisViewModel`

**Fajlovi:**
- Kreiraj: `ChesskoAndroid/.../viewmodels/AnalysisViewModel.kt`

**Interfejsi:**
- Troši: `StockfishEngine.evaluate`, `GameAnalysis.build`, `GameState`, `MoveGenerator`
- Proizvodi: stanje ekrana — `analysis: GameAnalysis?`, `progress: Float`, `isRunning`, `error`

- [ ] **Korak 1: N+1 pretraga, ne 2N**

```kotlin
// Ocena pozicije PRE poteza daje „najbolje sto se moglo", a ocena pozicije POSLE
// njega, sa obrnutim znakom, daje „sta je odigrano". Svaka pozicija se zato
// pretrazuje TACNO JEDNOM: partija od 40 poteza trazi 81 pretragu, ne 160.
```
Rekonstruiši niz pozicija iz početnog stanja primenom poteza redom, oceni svaku jednom, pa
prosledi `GameAnalysis.build`.

- [ ] **Korak 2: Otkazivanje — ovde je Android RAZLIČIT od iOS-a**

iOS pravi zaseban primerak motora po analizi. **Android ne može** — `StockfishEngine` je `object`
sa jednim `searchMutex`. Dok analiza drži mutex, nijedna druga pretraga ne prolazi.

Zato:
- analiza se pokreće u `viewModelScope` i **otkazuje u `onCleared()`**
- ekran koji se zatvara mora otkazati analizu (`DisposableEffect`), inače korisnik koji izađe i
  odmah počne novu partiju čeka da se 81 pretraga završi pre nego što AI odigra prvi potez
- **napiši u komentaru zašto**, sa ovim razlogom — sledeći čitalac neće sam pogoditi

- [ ] **Korak 3: Napredak se prijavljuje po poziciji**

`progress = ocenjenih / ukupno`. Bez toga korisnik gleda prazan ekran desetinama sekundi.

- [ ] **Korak 4: Commit**

---

## Task 5: Ekran i ulazne tačke

**Fajlovi:**
- Kreiraj: `ChesskoAndroid/.../ui/AnalysisView.kt`
- Izmeni: `MainActivity.kt` (dugme na gotovoj partiji)
- Izmeni: `ui/StepGameView.kt` (isto, za `game` korak Puta)

**Interfejsi:**
- Troši: `AnalysisViewModel`, `GameAnalysis`, `DS.*`

- [ ] **Korak 1: Ekran — sve na tokenima od rođenja**

Tačnost oba igrača, kartica prelomnog poteza, traka poteza obojena po klasi, dodir na potez vodi u
zatečen review mod preko `goToMove(ply + 1)`.

**Preslikavanje klasa u boje** (iOS ima isto — šest klasa u četiri boje, i to je zapisano
ograničenje, ne previd):
```
BEST, EXCELLENT, GOOD  -> DS.success
INACCURACY, MISTAKE    -> DS.warning
BLUNDER                -> DS.danger
```
Naziv klase mora ostati u `contentDescription`, pa informacija nije izgubljena — samo nije u boji.

**Za svaku boju izmeri par sa podlogom na kojoj stoji.** Faza 6d je na tome pukla pet puta;
`ContrastTest` ima 11 testova i pomoćnu `check(name, p, fg, bg, prag)`. Ako uvedeš nov par —
dodaj tvrdnju. **Nov test samo za NOV par**; ako je par već pokriven, dopiši komentar.

- [ ] **Korak 2: Dugme „Analiziraj partiju"**

Vidljivo **samo na gotovoj partiji sa bar jednim potezom**. Mora stajati u **obe** grane rasporeda
ako `MainActivity` ima portret i pejzaž odvojeno — iOS je tu grešku već napravio jednom.

- [ ] **Korak 3: `game` korak Puta NE SME zavisiti od analize**

```kotlin
// Korak se upisuje kao zavrsen PRE nego sto se ekran analize otvori. Da je
// obrnuto, korak bi bio nezavrsiv kad motor nije dostupan — tiha, trajna
// blokada Puta.
```
Proveri u `StepGameView.kt` da je `completeStep` već pozvan pre otvaranja analize.

- [ ] **Korak 4: Novi `loc()` ključevi**

Svi literali unutar `loc()`, svi dodati u `Loc.kt` na **svih 8 jezika**, prevodi usklađeni sa
iOS katalogom (`Chessko/Localizable.xcstrings` — čitaj ga, ne prevodi sam).
**Nijedan `%lld`** — to je Swift format i u Kotlinu ne radi.

- [ ] **Korak 5: Commit**

---

## Task 6: Zatvaranje faze

**Fajlovi:** `CLAUDE.md`

- [ ] **Korak 1: Oba skupa testova**

JVM i instrumentisani, brojevi **iz XML-a**. Polazno: 47 JVM, 46 instrumentisanih.

- [ ] **Korak 2: Emulator — drugi i poslednji put**

Vizuelna provera u **obe teme**: ekran analize posle partije protiv računara, ekran analize iz
`game` koraka Puta, traka poteza sa bar jednim promašajem, kartica prelomnog poteza, dodir na potez
vodi u review.

**Izmeri trajanje analize** i zapiši broj pozicija i sekundi. Ako je na emulatoru sporo — reci da
je to **emulatorski broj**, ne uređajski, umesto da ga predstaviš kao merenje performansi.

Snimci u `.superpowers/sdd/<plan>/screenshots/`, ne u sesijski `/tmp`.

Gašenje odmah: `adb emu kill`, `./gradlew --stop`, `pgrep -f qemu-system` prazno.

- [ ] **Korak 3: Provere celog stabla**

```bash
D=$(git diff --stat main..HEAD -- Chessko Chessko.xcodeproj); [ -z "$D" ] && echo "iOS netaknut"
git diff --stat main..HEAD -- ChesskoAndroid/app/build.gradle.kts ChesskoAndroid/gradle/libs.versions.toml
```

- [ ] **Korak 4: `CLAUDE.md`**

- Tabela „Stanje Android porta": red `5 — analiza partije` iz `ne` u **`da`**, nov red `6e`
- Nova podsekcija u „Analiza partije": **šta je na Androidu drugačije** — native Stockfish umesto
  `ChessKitEngine`, singleton sa mutexom umesto zasebnog primerka po analizi, i posledica toga na
  otkazivanje
- „Poznata ograničenja": prenesi iOS-ove koje Android nasleđuje (prvi potez se pretpostavlja beli;
  šest klasa u četiri boje) i dodaj sve nove koje faza nađe
- Changelog sa **izmerenim** brojevima

- [ ] **Korak 5: Commit**

---

## Samopregled

**1. Pokrivenost spec-a 5.5**

| zahtev | task |
|---|---|
| Klasifikacija poteza po gubitku u centipionima | 1 |
| Pragovi 20 / 50 / 100 / 300 | 1 (testirani) |
| Procenat tačnosti po igraču | 1 |
| Prelomni potez | 1 |
| Traka poteza u boji | 5 |
| Dostupno posle partije i iz `game` koraka | 5 |

**2. Placeholderi:** Task 2 namerno **ne zadaje format UCI linije** — zadaje postupak kojim se
dolazi do njega. To nije rupa u planu nego jedina ispravna formulacija: format koji bi plan zadao
bio bi pretpostavka, a pretpostavljen format je tačno ono što je iOS koštalo cele faze.

**3. Doslednost tipova:** `EngineScore` (Task 1) koristi se u Taskovima 2, 3, 4;
`PositionEval` (Task 3) u Task-u 4; `GameAnalysis` (Task 1) u Taskovima 4 i 5.
`GameAnalysis.build` traži `scores.size == notations.size + 1` — Task 4 mora da isporuči tačno
toliko, i to je mesto gde se N+1 ugovor vidi u tipovima.
