# Igra preko interneta na vreme — plan izvođenja

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Partija preko interneta sa satom 3+2 ili 10+5 (ili bez sata), koji meri server, na iOS-u i Androidu, uz punu saradnju sa starim verzijama za partije bez sata.

**Architecture:** Server (`server/play`, Durable Object `Hub`) dobija čist modul za sat (`clock.ts`) i protokol v2: pamti preostalo vreme i početak poteza po partiji, šalje stanje sata uz svaku promenu i sam javlja istek preko postojećeg `alarm`-a. Klijenti (iOS `OnlineMachine`/`OnlineViewModel`, Android 1:1 isto) samo prikazuju i lokalno odbrojavaju od poslednjeg stanja sa servera.

**Tech Stack:** Cloudflare Workers + SQLite Durable Object (TypeScript, vitest-pool-workers), Swift 6/SwiftUI (`swift test`), Kotlin/Compose (JVM + instrumentisani testovi).

**Spec:** `docs/superpowers/specs/2026-11-21-partija-na-vreme-design.md` (odobren). Protokol: `docs/online-protocol.md`. Kontekst projekta: `CLAUDE.md` („Igra preko interneta").

## Global Constraints

- Server prihvata `hello` sa `v:1` i `v:2`; drugo daje `error version`. Ka vezi sa `v:1` (ili bez `v` u prilogu, stari hibernirani prilozi) server nikad ne šalje novu poruku ni novo polje.
- Dozvoljeni satovi, u sekundama: `{base:180, inc:2}` i `{base:600, inc:5}`. Ništa drugo.
- Rok za prvi potez: `FIRST_MOVE_MS = 30_000` po strani, od trenutka kad je ta strana prvi put na potezu (beli: `start`; crni: kad server prihvati prvi potez belog). Istek → partija se briše bez rezultata (`first_move_expired`).
- Sat strane teče samo dok je ona na potezu i tek od njenog drugog poteza; prvi potez svake strane je bez sata i bez dodatka.
- Važi potez odigran na vreme: potez koji stigne kad je `sada ≥ rok` se ne prihvata, već je `flag` (gubi strana koja je igrala). Potez sa `end:true` koji stigne na vreme zaustavlja sat i završava partiju kao `finished`.
- Rok odsustva od 30 min (`AWAY_LIMIT_MS`) važi za partije bez sata i za već završene partije (`ended IS NOT NULL`); partija sa satom u toku se ne briše zbog odsustva.
- Podrazumevani izbor pri prvom pozivu: 3+2; posle toga poslednji izbor (lokalno: iOS `UserDefaults` ključ `chessko.online.clock`, Android `online.clock`; vrednosti `"3+2"`, `"10+5"`, `"none"`).
- Tekst: srpski izvor, svih 8 jezika, „ti", rodno neutralno. iOS kroz `build_localizations.py` (`add(...)`), Android kroz `logic/Loc.kt`; isti tekstovi na obe platforme.
- Nijedna nova npm/SPM/Gradle zavisnost; wrangler ostaje pinovan `4.124.0`. Android bez `ACCESS_NETWORK_STATE`.
- Nikad ne pokretati `create_xcode_project.py`; novi Swift fajl se ručno dodaje u `project.pbxproj` i u `Package.swift` ako je Foundation-only. Ne commit-ovati Xcode-ovu izmenu `Localizable.xcstrings` (samo izlaz `build_localizations.py`), ni ništa iz `chessko-dizajn-snimci/`.
- Server ne čuva ništa posle kraja partije (red se briše kao i sada).
- Objava servera (`npx wrangler deploy`) i push na GitHub samo uz izričito odobrenje vlasnika.

## Review Focus

1. Povratak (`resume`) usred tuđeg ili svog poteza: preostalo vreme strane na potezu mora biti umanjeno za vreme provedeno van veze, a druga strana netaknuta (test u Task 3).
2. Dodatak (`inc`) se ne dodaje na prvi potez nijedne strane, i dodaje se tek posle oduzimanja potrošenog vremena (test u Task 1).
3. Potez koji stigne tačno na rok ili posle njega je `flag`, ne potez; protivnik ne dobija `move` (test u Task 3).
4. Klijent `v:1` u partiji bez sata sa klijentom `v:2` dobija `start`/`move`/`ack` tačno starog oblika, bez polja `clock` (test u Task 2).
5. Obe strane odsutne u partiji sa satom: alarm javlja istek, a obe kasnije pri `resume` dobijaju `resumed` + `flag`, posle čega se red briše (test u Task 3).

---

### Task 1: Server — čist modul sata

**Files:**
- Create: `server/play/src/clock.ts`
- Test: `server/play/test/clock.test.ts`

**Interfaces:**
- Produces:
  - `type Side = "w" | "b"`
  - `interface ClockSpec { base: number; inc: number }` (sekunde)
  - `const ALLOWED_CLOCKS: readonly ClockSpec[]` = `[{base:180,inc:2},{base:600,inc:5}]`
  - `function isAllowedClock(x: unknown): x is ClockSpec`
  - `const FIRST_MOVE_MS = 30_000`
  - `interface ClockState { base: number; inc: number; wMs: number; bMs: number; turnStarted: number; ply: number }` (`ply` = broj već odigranih poteza; strana na potezu = `ply % 2 === 0 ? "w" : "b"`)
  - `function newClock(spec: ClockSpec, now: number): ClockState` (`wMs = bMs = base*1000`, `turnStarted = now`, `ply = 0`)
  - `function runningSide(c: ClockState): Side | null` — `null` dok je `ply < 2`
  - `function deadline(c: ClockState): number` — `ply < 2`: `turnStarted + FIRST_MOVE_MS`; inače `turnStarted + preostalo strane na potezu`
  - `function applyMove(c: ClockState, now: number): { ok: true; next: ClockState } | { ok: false; reason: "flag" | "first_move" }` — `now >= deadline(c)` → `ok:false` (`first_move` ako `ply < 2`); inače za `ply ≥ 2` oduzme `now − turnStarted` i doda `inc*1000`; `next.ply = ply+1`, `next.turnStarted = now`
  - `function snapshot(c: ClockState, now: number): WireClock` gde je `interface WireClock { base: number; inc: number; w: number; b: number; run: Side | null; first: number | null }` — `w`/`b` preostalo u ms u trenutku `now` (strana koja troši umanjena, ne ispod 0), `first` = ms do roka prvog poteza dok je `ply < 2`, inače `null`

- [ ] **Step 1: Write the failing tests** (`clock.test.ts`, čist vitest bez `SELF`):
  - `isAllowedClock` prihvata samo dve vrednosti; odbija `{180,3}`, `{base:"180"}`, `null`.
  - `newClock({180,2}, 1000)` → `wMs=bMs=180000`, `runningSide=null`, `deadline=31000`.
  - prvi potez belog u `t=20000`: `ok`, `wMs` ostaje `180000` (bez dodatka), `ply=1`, `deadline = 20000+30000`.
  - prvi potez belog u `t=31000` (tačno rok) → `{ok:false, reason:"first_move"}`.
  - posle oba prva poteza (`ply=2`, `turnStarted=T`): potez belog u `T+5000` → `wMs = 180000-5000+2000 = 177000`; `bMs` netaknut.
  - potez u `T + wMs` (tačno rok) → `{ok:false, reason:"flag"}`.
  - `snapshot` u `T+1500` dok beli troši: `w = wMs-1500`, `b = bMs`, `run="w"`, `first=null`; u `ply=0`, `t=start+10000`: `first=20000`, `run=null`.
- [ ] **Step 2: Run** `cd server/play && npx vitest run test/clock.test.ts` → FAIL (modul ne postoji).
- [ ] **Step 3: Implement** `clock.ts` po potpisima gore; bez I/O, bez `Date.now()`.
- [ ] **Step 4: Run** `npx vitest run && npx tsc --noEmit` → sve prolazi (48 postojećih + novi).
- [ ] **Step 5: Commit** `server: čist modul sata za partiju na vreme`.

### Task 2: Server — protokol v2, poziv i start sa satom

**Files:**
- Modify: `server/play/src/protocol.ts`, `server/play/src/hub.ts` (`onHello`, `onInvite`, `onAccept`, `onMove`, `gameRow`, šema u konstruktoru, `send` pozivi za `ack`/`move`/`sync`)
- Test: `server/play/test/clock-protocol.test.ts` (pomoćnici `connect/next/ask/join` kao u `test/game.test.ts`, uz `join(code, v)`)

**Interfaces:**
- Consumes: Task 1 (`ClockSpec`, `ClockState`, `isAllowedClock`, `newClock`, `applyMove`, `snapshot`, `WireClock`).
- Produces (wire, isto će čitati iOS i Android):
  - `PROTOCOL_VERSIONS = [1, 2]`; `Attachment` dobija `v?: number` (izostalo = 1) i `inviteClock?: ClockSpec`.
  - `ErrorReason` + `"needs_update" | "bad_clock"`.
  - K→S: `{t:"invite", to, clock?: ClockSpec}`, `{t:"accept", from, clock?: ClockSpec}`, `{t:"move", ply, uci, end?: true}`.
  - S→K (samo `v:2`): `invited.clock?`, a `start`, `ack`, `move`, `sync` i `resumed` u partiji sa satom nose `clock: WireClock`; `move` ka protivniku nosi `end: true` ako ga je poslao igrač.
  - SQL kolone u `games` (dodaju se `ALTER TABLE` kao `ended`): `clock_base INTEGER, clock_inc INTEGER, w_ms INTEGER, b_ms INTEGER, turn_started INTEGER` (sve `NULL` za partiju bez sata). Pomoćnik `private clockOf(row): ClockState | null` čita ih uz `ply = moves.length`.

- [ ] **Step 1: Write the failing tests:**
  - `hello` sa `v:2` → `welcome`; `v:3` → `error version`.
  - `v:2` poziva `v:2` sa `{180,2}` → pozvani dobija `{t:"invited", from, clock:{base:180,inc:2}}`.
  - `invite` sa `{180,3}` → `error bad_clock`, pozvani ne dobija ništa.
  - `v:2` poziva `v:1` sa satom → pozivalac `error needs_update`, pozvani ne dobija ništa (`silent`).
  - `accept` bez `clock` (ili sa drugim) na poziv sa satom → ignorisan, nema `start`; `accept` sa istim `clock` → oba `start` sa `clock: {base:180, inc:2, w:180000, b:180000, run:null, first: ≈30000}` (dozvoljeno odstupanje ≤ 1000 ms).
  - Review Focus 4: `v:1` i `v:2` bez sata → `start` kod `v:1` je tačno `{t,game,color,token,opponent}` (`toEqual`), a `move`/`ack` bez `clock` i bez `end`.
  - partija sa satom: posle poteza belog `ack` i protivnikov `move` nose `clock` sa `run:null` i `first` za crnog ≈30000.
- [ ] **Step 2: Run** `npx vitest run test/clock-protocol.test.ts` → FAIL.
- [ ] **Step 3: Implement:** verzija se pamti u prilogu u `onHello`; `onInvite` validira `clock` (`isAllowedClock`), proverava `v` mete pre slanja, čuva `inviteClock`; `onAccept` poredi `clock` iz poruke sa `inviteClock` (oba izostala ili jednaka po `base`/`inc`) i upisuje `newClock(spec, Date.now())` u red; `onMove` u partiji sa satom zove `applyMove` (neuspeh obrađuje Task 3 — ovde samo uspešan put) i upisuje novo stanje; slanje `clock` samo vezama sa `v:2`.
- [ ] **Step 4: Run** `npx vitest run && npx tsc --noEmit` → sve prolazi, uključujući svih 48 starih testova nepromenjenih.
- [ ] **Step 5: Commit** `server: protokol v2, poziv i start sa satom`.

### Task 3: Server — istek, rok prvog poteza, `end:true`, odsustvo i povratak

**Files:**
- Modify: `server/play/src/hub.ts` (`onMove`, `scheduleAlarm`, `alarm`, `onResume`, `endWhileAway`, `onFinished`), `server/play/src/protocol.ts`
- Test: `server/play/test/clock-end.test.ts` (vreme se pomera kao u `test/resume.test.ts`: `runInDurableObject` + SQL `UPDATE games SET turn_started = ?`, pa `runDurableObjectAlarm`)

**Interfaces:**
- Consumes: Task 1, Task 2 (kolone, `clockOf`).
- Produces: S→K `{t:"flag", loser: Side}` i `{t:"first_move_expired"}`; `ended` dobija vrednosti `"flag_w" | "flag_b"`.

- [ ] **Step 1: Write the failing tests:**
  - Review Focus 3: potez stigne posle roka (`turn_started` pomeren unazad) → obe strane dobijaju `{t:"flag", loser:<onaj ko je igrao>}`, protivnik ne dobija `move`, red obrisan.
  - alarm posle roka strane na potezu (obe na vezi) → obe `flag`, red obrisan; alarm pre roka → ništa.
  - rok prvog poteza: posle `start` pomeri `turn_started` 31 s unazad, alarm → obe `{t:"first_move_expired"}`, red obrisan; isto za crnog posle prvog poteza belog.
  - `move` sa `end:true` na vreme → protivnik dobija `move` sa `end:true`; red obrisan (obe prisutne); kasniji alarm ništa ne šalje.
  - partija sa satom u toku: `white_away` pomeren 31 min unazad, a `turn_started` je sadašnji trenutak (rok sata nije istekao) → posle `runDurableObjectAlarm` red i dalje postoji i niko ne dobija `aborted`.
  - Review Focus 1: beli na potezu se odjavi; posle 4 s `resume` belog → `resumed.clock.w` ≈ `wMs − 4000` (±1000), `b` netaknut.
  - Review Focus 5: obe strane zatvore vezu, alarm posle roka; `resume` belog → `resumed` pa `flag`; `resume` crnog → `resumed` pa `flag`, posle toga red obrisan i treći `resume` → `game_gone`.
- [ ] **Step 2: Run** `npx vitest run test/clock-end.test.ts` → FAIL.
- [ ] **Step 3: Implement:** `onMove` na `applyMove → ok:false` šalje `flag`/`first_move_expired` i završava (preko istog puta kao `endWhileAway` kad protivnik nema vezu; `first_move_expired` se ne čuva, red se briše kao kod `aborted`); `end:true` posle uspešnog poteza ide postojećim putem `onFinished`; `scheduleAlarm` uzima minimum preko rokova odsustva (partije bez sata i završene) i `deadline(...)` partija sa satom u toku; `alarm` obrađuje oba; `onResume` šalje `clock: snapshot(...)` i, za `ended = "flag_*"`, posle `resumed` šalje `flag`.
- [ ] **Step 4: Run** `npx vitest run && npx tsc --noEmit` → sve prolazi.
- [ ] **Step 5: Commit** `server: istek vremena, rok prvog poteza i kraj potezom`.

### Task 4: Server — dokumentacija protokola i provera uživo

**Files:**
- Modify: `docs/online-protocol.md` (v2: nova polja, poruke, greške, pravila sata, saradnja sa v1), `server/play/scripts/smoke.mjs`

- [ ] **Step 1:** Dopuni `docs/online-protocol.md` tako da svaka poruka i polje iz Taskova 2–3 stoji u tabeli; dodaj odeljak „Sat (v2)" sa pravilima iz Global Constraints.
- [ ] **Step 2:** `smoke.mjs` dobija scenario: dva `v:2` klijenta, poziv `{180,2}`, `start` sa `clock`, dva poteza, `move` sa `end:true`; i scenario `v:1` ↔ `v:2` bez sata (stari oblik poruka). Pokreće se protiv servera koji je zadat promenljivom `CHESSKO_WS` (podrazumevano produkcija).
- [ ] **Step 3: Run** lokalno: `npx wrangler dev` u jednom terminalu, `CHESSKO_WS=ws://127.0.0.1:8787/ws npm run smoke` → sve PASS.
- [ ] **Step 4:** U `docs/online-protocol.md` dodaj odeljak „Objava i povratak": pre objave zapiši trenutnu verziju (`npx wrangler deployments list`); povratak je `npx wrangler rollback <verzija>`. Stari server nove kolone samo ne čita, pa zapisi partija ostaju upotrebljivi.
- [ ] **Step 5: Commit** `docs: protokol v2 (sat); smoke za partiju na vreme`. Objava (`npx wrangler deploy`) i smoke protiv produkcije tek uz odobrenje vlasnika — izvođač ovde staje i javlja.

### Task 5: iOS — poruke protokola v2

**Files:**
- Modify: `Chessko/Models/OnlineProtocol.swift`
- Test: `Tests/ChesskoEngineTests/OnlineProtocolTests.swift`

**Interfaces:**
- Produces:
  - `struct ClockSpec: Codable, Equatable, Sendable { let base: Int; let inc: Int; static let blitz = ClockSpec(base: 180, inc: 2); static let rapid = ClockSpec(base: 600, inc: 5) }`
  - `struct WireClock: Equatable, Sendable { let spec: ClockSpec; let whiteMs: Int; let blackMs: Int; let running: PieceColor?; let firstMoveMs: Int? }`
  - `ClientMessage.protocolVersion = 2`; `.invite(to: String, clock: ClockSpec?)`, `.accept(from: String, clock: ClockSpec?)`, `.move(ply: Int, uci: String, end: Bool)` (`end` se kodira samo kad je `true`).
  - `ServerMessage`: `.invited(from: String, clock: ClockSpec?)`, `.start(..., clock: WireClock?)`, `.move(ply:, uci:, end: Bool, clock: WireClock?)`, `.ack(ply:, clock: WireClock?)`, `.sync(moves:, clock: WireClock?)`, `.resumed(..., clock: WireClock?)`, `.flag(loser: PieceColor)`, `.firstMoveExpired`.
  - `OnlineError` + `.needsUpdate = "needs_update"`, `.badClock = "bad_clock"`.

- [ ] **Step 1: Write the failing tests:** kodiranje `hello` nosi `"v":2`; `invite` sa i bez `clock`; `move` bez `end` kad je `false`; dekodiranje svake nove poruke iz JSON-a oblika iz Taska 2–3; stari oblik `start` bez `clock` daje `clock == nil`; nepoznat `reason` i dalje daje `.unknown`/postojeće ponašanje.
- [ ] **Step 2: Run** `swift test --filter OnlineProtocolTests` → FAIL.
- [ ] **Step 3: Implement** po potpisima; ostala pozivna mesta (`OnlineMachine`, `OnlineViewModel`) samo prilagodi da se kompajliraju (`clock: nil`, `end: false`) — ponašanje se menja u Tasku 6.
- [ ] **Step 4: Run** `swift test` → sve prolazi (166 + novi).
- [ ] **Step 5: Commit** `iOS: poruke protokola v2 (sat)`.

### Task 6: iOS — mašina stanja sa satom

**Files:**
- Modify: `Chessko/Models/OnlineMachine.swift`
- Test: `Tests/ChesskoEngineTests/OnlineMachineTests.swift`

**Interfaces:**
- Consumes: Task 5.
- Produces:
  - `OnlineEvent.invite(code: String, clock: ClockSpec?)`; `OnlineState.invited(from: String, clock: ClockSpec?)`.
  - `ActiveGame` dobija `let clock: ClockSpec?` (`Codable`, staro sačuvano bez polja dekodira se kao `nil`).
  - Stanje `.playing` nosi `clock: ClockReading?`, gde je `struct ClockReading: Equatable, Sendable { let wire: WireClock; let receivedAt: Double }` (`receivedAt` je vreme koje UI ubaci u događaj: `OnlineEvent.received(ServerMessage, at: Double)` — svi postojeći pozivi prosleđuju trenutno vreme).
  - `OnlineResult` + `.iFlagged`, `.opponentFlagged`, `.firstMoveExpired`.
  - `OnlineNotice` + `.needsUpdate`.
  - Efekat `.send(.move(ply:, uci:, end: true))` kad je partija sa satom a potez završava partiju po pravilima: `OnlineEvent.localMove(ply: Int, uci: String, ends: Bool)`; u partiji sa satom `boardFinished` se ne šalje kao `finished` nego se kraj proglašava na `ack` sopstvenog završnog poteza (u partiji bez sata ponašanje ostaje kao danas).

- [ ] **Step 1: Write the failing tests:** `.invited` nosi sat iz poruke; `accept` šalje `.accept(from:, clock:)` sa istim satom; `start` sa `clock` postavlja `ClockReading`; `ack`/`move`/`sync`/`resumed` osvežavaju ga; `flag(loser: moja boja)` → `.ended(.iFlagged)`, `flag` protivnika → `.opponentFlagged`; `firstMoveExpired` → `.ended(.firstMoveExpired)`; `error(.needsUpdate)` u `.inviting` → nazad u lobi + `.showMessage(.needsUpdate)`; završni lokalni potez u partiji sa satom šalje `end:true` i ostaje u `.playing` do `ack`, a na `ack` prelazi u `.ended(.board)`; u partiji bez sata `boardFinished` šalje `.finished` kao danas (postojeći test ostaje zelen).
- [ ] **Step 2: Run** `swift test --filter OnlineMachineTests` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `swift test` → sve prolazi.
- [ ] **Step 5: Commit** `iOS: mašina stanja igre na vreme`.

### Task 7: iOS — ekrani i tekstovi

**Files:**
- Modify: `Chessko/ViewModels/OnlineViewModel.swift`, `Chessko/Views/OnlineLobbyView.swift`, `Chessko/Views/OnlineGameView.swift`, `build_localizations.py`, `Chessko/Localizable.xcstrings` (samo izlaz skripte)

**Interfaces:**
- Consumes: Task 6.
- Produces: `OnlineViewModel.selectedClock: ClockSpec?` (učitava/čuva `chessko.online.clock`, prvi put `.blitz`); `func displayedMs(for color: PieceColor, now: Date) -> Int?` (preostalo = `wire` vrednost umanjena za `now − receivedAt` samo za `running` stranu, ne ispod 0); `func firstMoveMsLeft(now: Date) -> Int?`.

- [ ] **Step 1:** Nove poruke i oznake u `build_localizations.py` (sr, en, fr, de, it, ru, zh-Hans, hi), tačan srpski tekst: „Vreme", „Bez sata", „Partija: %@", „Odigraj prvi potez: %@", „Isteklo ti je vreme.", „Prijatelju je isteklo vreme.", „Partija je prekinuta jer prvi potez nije odigran.", „Prijatelj treba da ažurira aplikaciju za igru na vreme.". Pokreni skriptu; proveri da svaki nov ključ ima 8 jezika i da `LocCatalogTests` prolazi.
- [ ] **Step 2:** Lobi: segmentirani izbor `3+2` / `10+5` / `Bez sata` iznad dugmeta „Pozovi"; dijalog poziva prikazuje „Partija: 3+2" ili „Bez sata".
- [ ] **Step 3:** Partija: sat u kartici svakog igrača (`M:SS`, ispod 10 s `S.d`, isti format kao `ChessClockView.formatTime`), aktivan istaknut, ispod 10 s `DS.danger`; osvežavanje `TimelineView(.periodic(from:by: 0.1))`; dok je `firstMoveMsLeft != nil` igrač na potezu vidi „Odigraj prvi potez: 0:25"; krajevi `.iFlagged`/`.opponentFlagged`/`.firstMoveExpired` sa tekstovima iz Step 1.
- [ ] **Step 4: Run** `swift test` i `xcodebuild -project Chessko.xcodeproj -scheme Chessko -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build` → prolazi. Posle build-a vrati Xcode-ovu izmenu kataloga ako je drugačija od izlaza skripte (`git diff --stat Chessko/Localizable.xcstrings`).
- [ ] **Step 5: Commit** `iOS: izbor vremena, sat u partiji, krajevi na vreme`.

### Task 8: Android — poruke protokola v2

**Files:**
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/online/OnlineMessages.kt`, `online/OnlineCodec.kt`
- Test: `ChesskoAndroid/app/src/androidTest/java/com/veljkoni/chessko/online/OnlineCodecTest.kt` (instrumentisan, jer je `org.json`)

**Interfaces:**
- Produces (1:1 sa Taskom 5): `data class ClockSpec(val base: Int, val inc: Int)` sa `BLITZ`/`RAPID`; `data class WireClock(val spec: ClockSpec, val whiteMs: Int, val blackMs: Int, val running: PieceColor?, val firstMoveMs: Int?)`; `ClientMessage.Invite(to, clock: ClockSpec?)`, `Accept(from, clock: ClockSpec?)`, `Move(ply, uci, end: Boolean)`; `ServerMessage.Invited(from, clock)`, `Start(..., clock: WireClock?)`, `Move(ply, uci, end, clock)`, `Ack(ply, clock)`, `Sync(moves, clock)`, `Resumed(..., clock)`, `Flag(loser: PieceColor)`, `FirstMoveExpired`; `OnlineError.NEEDS_UPDATE`, `BAD_CLOCK`; `PROTOCOL_VERSION = 2`.

- [ ] **Step 1: Write the failing tests** — isti slučajevi kao Task 5 Step 1.
- [ ] **Step 2:** Pokreće se u Tasku 11 (jedan prolaz emulatora); ovde: `./gradlew compileDebugAndroidTestKotlin` prolazi, a `testDebugUnitTest` ostaje zelen.
- [ ] **Step 3: Implement**; ostala pozivna mesta samo da se kompajliraju.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` (broj iz XML-a) → prolazi.
- [ ] **Step 5: Commit** `Android: poruke protokola v2 (sat)`.

### Task 9: Android — mašina stanja sa satom

**Files:**
- Modify: `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/online/OnlineMachine.kt`
- Test: `ChesskoAndroid/app/src/test/java/com/veljkoni/chessko/OnlineMachineTest.kt`

**Interfaces:**
- Consumes: Task 8. Produces: 1:1 sa Taskom 6 (`ClockReading`, `OnlineResult.I_FLAGGED`, `OPPONENT_FLAGGED`, `FIRST_MOVE_EXPIRED`, `OnlineNotice.NEEDS_UPDATE`, događaji sa satom i vremenom prijema, `localMove(..., ends)`).

- [ ] **Step 1: Write the failing tests** — isti slučajevi kao Task 6 Step 1, istim imenima gde Kotlin dozvoljava.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*OnlineMachineTest*'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` → sve prolazi (broj iz XML-a, XML obrisan pre prolaza).
- [ ] **Step 5: Commit** `Android: mašina stanja igre na vreme`.

### Task 10: Android — ekrani i tekstovi

**Files:**
- Modify: `online/OnlineViewModel.kt`, `ui/OnlineLobbyView.kt`, `ui/OnlineGameView.kt`, `logic/Loc.kt`

**Interfaces:**
- Consumes: Task 9. Produces: `OnlineViewModel.selectedClock` (`online.clock`, prvi put `BLITZ`), `displayedMs(color, nowMs)`, `firstMoveMsLeft(nowMs)` — isto ponašanje kao Task 7.

- [ ] **Step 1:** Isti tekstovi kao Task 7 Step 1 u `Loc.kt`, svih 8 jezika, identični iOS katalogu; `LocTest` prolazi.
- [ ] **Step 2:** Lobi: izbor `3+2` / `10+5` / `Bez sata` (M3 `SingleChoiceSegmentedButtonRow` ili ekvivalent na `DS.*` tokenima); dijalog poziva prikazuje vreme.
- [ ] **Step 3:** Partija: sat u kartici igrača (format kao `ChessClockView.formatTime`), osvežavanje `LaunchedEffect` petljom na 100 ms dok partija sa satom traje, aktivan istaknut, ispod 10 s `DS.danger`; odbrojavanje prvog poteza; krajevi na vreme. Nijedan `fontSize` van `Type.*` (čuva `TypographyScaleTest`), razmaci po `SpacingTokensTest`.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug` → prolazi.
- [ ] **Step 5: Commit** `Android: izbor vremena, sat u partiji, krajevi na vreme`.

### Task 11: Završna provera, unakrsna partija i dokumentacija

**Files:**
- Modify: `CLAUDE.md` (sekcija „Igra preko interneta": sat; Changelog), `ChesskoAndroid/app/build.gradle.kts` (`versionCode = 8`, `versionName = "2.5"`)

- [ ] **Step 1:** Server: `npx vitest run && npx tsc --noEmit`; `swift test`; Android `testDebugUnitTest` (iz XML-a).
- [ ] **Step 2:** Jedan prolaz emulatora (recept iz `CLAUDE.md`, `-gpu host`, telefonski AVD; ako javi `INSUFFICIENT_STORAGE`, prvo `adb shell pm uninstall-system-updates`): `connectedDebugAndroidTest` (iz XML-a), pa unakrsna partija iOS simulator (Xcode MCP) ↔ Android emulator protiv lokalnog `wrangler dev` servera: 3+2 do isteka vremena jedne strane; rok prvog poteza; mat potezom sa `end:true`; poziv starije verzije (server sa `v:1` klijentom iz smoke skripte) → poruka o ažuriranju; partija bez sata kao pre. Ugasi emulator odmah posle (`adb emu kill`, `./gradlew --stop`, `pgrep -f qemu-system` prazan).
- [ ] **Step 3: Prave stare aplikacije protiv novog servera.** U zasebnom worktree-ju od commit-a `59a220e` (stanje pre ovog plana: iOS kakav je 1.3, Android 2.4) napravi obe aplikacije i usmeri ih na lokalni `wrangler dev` sa novim serverom (privremeno, u radnom stablu tog worktree-ja, izmena adrese servera se ne commit-uje). Proveri: stara iOS ↔ stara Android partija bez sata do mata; stara ↔ nova bez sata (u oba smera ko poziva); nova poziva staru sa 3+2 → nova dobija poruku o ažuriranju, stara ne dobija ništa; posle svega stara aplikacija i dalje radi (nema poruke „potrebna je novija verzija"). Zabeleži rezultat u izveštaj.
- [ ] **Step 4:** Ažuriraj `CLAUDE.md` i verziju Androida; iOS verziju (1.4 i build broj) podešava vlasnik u Xcode-u pri arhiviranju.
- [ ] **Step 5: Commit** `Igra na vreme: završna provera i dokumentacija`. Objava servera, push i izdanja u prodavnicama čekaju vlasnika.
