# Igra preko interneta — plan izrade

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dva prijatelja igraju partiju uživo, svako na svom telefonu (iOS ili Android). Povezuju se kodom
uređaja, kao u AnyDesk-u, bez naloga.

**Architecture:**
- **Server:** mali Cloudflare Worker sa jednim Durable Object-om (`Hub`) i WebSocket-ima sa hibernacijom.
  Ne zna šah: drži prisustvo kodova, prosleđuje pozive i poteze, i čuva listu poteza **samo dok partija
  traje**.
- **Obe aplikacije dele isti protokol** (`docs/online-protocol.md`) i istu čistu mašinu stanja
  (`OnlineMachine`: događaj → novo stanje + efekti). Ona se testira bez mreže i bez UI-ja.
- **Partija se igra kroz postojeći `GameViewModel`** u novom režimu `online`, sa sopstvenim ključem za
  čuvanje. Prethodna partija na tabu Igra se ne gubi.

**Tech Stack:**
- Server: TypeScript, Wrangler, Durable Objects (SQLite), `@cloudflare/vitest-pool-workers`.
- iOS: Swift 6 / SwiftUI, `URLSessionWebSocketTask`.
- Android: Kotlin / Compose, OkHttp WebSocket, `org.json`.

**Spec:** `docs/superpowers/specs/2026-11-15-igra-preko-interneta-design.md`. Pročitati pre svakog taska.

## Global Constraints

- Bez naloga, prijave, imena i e-maila. Identitet je samo kod uređaja, čuvan lokalno.
- Bez SDK-a treće strane:
  - iOS: nijedna nova zavisnost;
  - Android: jedina nova navedena zavisnost je OkHttp, i to verzija koju već donosi Coil;
  - server: samo Cloudflare.
- Mrežu koriste **samo** ekran „Igraj preko interneta“ i partija preko interneta. Igra protiv računara,
  zadaci, lekcije, sat i analiza ostaju bez ijednog mrežnog poziva.
- Server ne čuva ništa posle kraja partije ili isteka čekanja: nema baze istorije ni logova sa kodovima.
- Partija čeka: preživljava zaključan ekran, drugu aplikaciju, poziv i gašenje aplikacije. Rok čekanja na
  serveru je **30 minuta** od odlaska strane.
- Boje se dele nasumično. Nema „Vrati potez“. Postoje „Predaj“ i „Ponudi remi“. Rezultat se ne upisuje u
  statistiku.
- Paritet: iOS i Android dobijaju isto ponašanje, ista imena tipova i iste tekstove.
- Svaki nov tekst interfejsa ima svih 8 jezika (sr, en, fr, de, it, ru, zh-Hans, hi):
  - iOS: kroz `build_localizations.py` → `Localizable.xcstrings`;
  - Android: kroz `Loc.kt`.

  Postojeći testovi kataloga (`LocCatalogTests`, `LocTest`) moraju ostati zeleni. Ton je „ti“ i rodno
  neutralan, kao ostatak aplikacije.
- `Chessko/Localizable.xcstrings` ima zatečenu nekomitovanu izmenu od Xcode-a. `build_localizations.py` ceo
  fajl regeneriše, i to je u redu. Commituje se rezultat skripte.
- Server se objavljuje na `wss://play.chessko.app/ws`. Kreće se na besplatnom nivou, a kod ne sme da zavisi
  od plaćenog nivoa.
- Politika privatnosti (`chessko-site/privacy.html`, 8 jezika) mora biti objavljena **pre** slanja izdanja
  aplikacija.

## Review Focus

1. **Poruke koje se ukrste:** potez stigne posle predaje, ili dve strane pošalju potez sa istim `ply`.
   Server prima samo potez strane koja je na redu i sa tačnim `ply`; sve ostalo dobija `sync`. Test u Task 3.
2. **Povratak posle dugog odsustva:** aplikacija se otvori posle 10 minuta, a protivnik je u međuvremenu
   odigrao potez. `resumed` donosi celu listu, klijent dopuni samo ono što nema. Test u Task 4 (server) i
   Task 7 (iOS mašina).
3. **Promocija:** potez `e7e8q` mora da prođe tamo i nazad, i iOS → Android. Test u Task 6 i Task 11.
4. **Dva poziva istom kodu u isto vreme**, ili poziv kodu koji već igra: drugi pozivalac dobija `busy`.
   Test u Task 2.
5. **Lokalni zapis partije koja više ne postoji** (istekla dok je telefon bio ugašen): klijent dobije
   `error game_gone`, briše zapis i prikazuje „Partija je prekinuta.“, bez zaglavljivanja. Test u Task 7.

---

## Faza A — Server (`server/play/` u ovom repou)

### Task 1: Protokol, kostur servera, prisustvo koda

**Files:**
- Create: `docs/online-protocol.md`: jedini izvor istine o porukama; sadrži tabelu ispod doslovno.
- Create: `server/play/package.json`, `server/play/wrangler.jsonc`, `server/play/tsconfig.json`,
  `server/play/vitest.config.ts`.
- Create: `server/play/src/index.ts`: Worker; `GET /health` → `ok`; `GET /ws` sa upgrade → `Hub` (jedan
  primerak, `idFromName("hub")`).
- Create: `server/play/src/protocol.ts`: tipovi poruka, `PROTOCOL_VERSION = 1`, `isValidCode(code)`.
- Create: `server/play/src/hub.ts`: `export class Hub extends DurableObject`, WebSocket hibernation API
  (`ctx.acceptWebSocket`, `webSocketMessage`, `webSocketClose`, `serializeAttachment`).
- Test: `server/play/test/presence.test.ts`.

**Protokol v1.** Sve poruke su JSON objekti sa poljem `t`. Kod je 6 znakova iz abecede
`23456789ABCDEFGHJKMNPQRSTUVWXYZ`; na ekranu se piše `XXX-XXX`, a preko mreže bez crtice.

| Smer | Poruka | Odgovor / posledica |
|---|---|---|
| K→S | `{t:"hello", v:1, code}` | `{t:"welcome", code}` ili `{t:"error", reason:"version"\|"bad_code"\|"code_taken"}` |
| K→S | `{t:"resume", game, token}` | `{t:"resumed", game, color:"w"\|"b", moves:[uci], opponent:"online"\|"away"}` ili `{t:"error", reason:"game_gone"}` |
| K→S | `{t:"invite", to}` | pozivaocu `{t:"invite_sent", to}`; pozvanom `{t:"invited", from}`; ili greška `offline`\|`busy`\|`self`\|`rate_limited`\|`bad_code` |
| K→S | `{t:"cancel"}` | pozvanom `{t:"invite_cancelled", from}` |
| K→S | `{t:"accept", from}` | oba dobiju `{t:"start", game, color, token, opponent}` |
| K→S | `{t:"decline", from}` | pozivaocu `{t:"declined", by}` |
| K→S | `{t:"move", ply, uci}` | protivniku `{t:"move", ply, uci}`; pošiljaocu `{t:"ack", ply}`; pogrešan `ply` ili red → pošiljaocu `{t:"sync", moves:[uci]}` |
| K→S | `{t:"resign"}` | protivniku `{t:"resigned"}`; partija se briše |
| K→S | `{t:"draw_offer"}` | protivniku `{t:"draw_offer"}` |
| K→S | `{t:"draw_reply", accept}` | protivniku `{t:"draw_reply", accept}`; `accept:true` briše partiju |
| K→S | `{t:"finished"}` | kraj po pravilima (mat, pat, remi po pravilu), koji klijent sam vidi; partija se briše |
| S→K | `{t:"opponent", state:"away"\|"back"}` | prisutnoj strani, kad druga izgubi ili vrati vezu |
| S→K | `{t:"aborted"}` | prisutnoj strani, posle 30 min odsustva druge; partija se briše |

`ply` je redni broj poteza od 0. Beli igra parne, crni neparne. `token` je 128-bitni nasumični heks i važi
samo za jednu partiju i jednu stranu.

- [ ] **Step 1: Napiši testove prisustva** (`presence.test.ts`, WebSocket kroz `SELF.fetch` sa
  `Upgrade: websocket`):
  - `hello sa ispravnim kodom dobija welcome`: šalje `{t:"hello",v:1,code:"K7M4QX"}` i očekuje
    `{t:"welcome",code:"K7M4QX"}`.
  - `hello sa nepoznatom verzijom dobija error version`: `v:2` → `{t:"error",reason:"version"}`.
  - `hello sa neispravnim kodom dobija bad_code`: `"K7M4Q0"`, `"k7m4qx"` i `"K7M4QXX"` → `bad_code`.
  - `drugi hello sa istim kodom dobija code_taken` dok je prvi povezan.
  - `posle zatvaranja prve veze isti kod ponovo prolazi`.
  - `GET /health vraća 200 i "ok"`.
- [ ] **Step 2:** `cd server/play && npx vitest run`. Očekuje se: FAIL, jer nema implementacije.
- [ ] **Step 3:** Implementiraj kostur, `isValidCode(code: string): boolean` i prisustvo u `Hub`. Prilog
  WebSocket-a (attachment) nosi `{code, game?, invites: number[]}`. Prisustvo se čita iz
  `ctx.getWebSockets()` i priloga, ne iz memorijske mape, jer posle hibernacije memorija ne postoji.
  `wrangler.jsonc`:
  - Durable Object `HUB` sa migracijom `new_sqlite_classes: ["Hub"]`;
  - ruta `{pattern:"play.chessko.app", custom_domain:true}`.
- [ ] **Step 4:** `npx vitest run`. Očekuje se: svi testovi u `presence.test.ts` PASS.
- [ ] **Step 5:** Commit `server/play`, `docs/online-protocol.md`: „Server za igru preko interneta: protokol i
  prisustvo koda“. `node_modules` i `.wrangler` dodaj u `server/play/.gitignore`.

### Task 2: Pozivi

**Files:** Modify `server/play/src/hub.ts`. Test `server/play/test/invite.test.ts`.

**Interfaces:**
- Produces: poruke `invite`, `cancel`, `accept`, `decline`, `invited`, `invite_sent`, `invite_cancelled`,
  `declined`, `start` tačno po tabeli iz Task 1.
- Partija se pravi u SQLite tabeli `games(id TEXT PRIMARY KEY, white TEXT, black TEXT, white_token TEXT,
  black_token TEXT, moves TEXT, away_code TEXT, away_since INTEGER)`. Kolona `moves` je JSON niz.

- [ ] **Step 1: Napiši testove:**
  - `poziv povezanom kodu stiže kao invited, a pozivalac dobija invite_sent`;
  - `poziv kodu koji nije povezan daje offline`;
  - `poziv sopstvenom kodu daje self`;
  - `poziv kodu koji je već u partiji daje busy`;
  - `drugi poziv istom kodu dok prvi čeka daje busy drugom pozivaocu`;
  - `accept daje start obema stranama`: boje su različite, `token` je različit i ima 32 heks znaka, `game` je
    isti, a `opponent` je kod druge strane;
  - `decline daje declined pozivaocu i oslobađa oba koda`;
  - `cancel daje invite_cancelled pozvanom`;
  - `11. poziv u istom minutu daje rate_limited`.
- [ ] **Step 2:** `npx vitest run test/invite.test.ts`. Očekuje se: FAIL.
- [ ] **Step 3:** Implementiraj. Boja ide `crypto.getRandomValues`; ograničenje je 10 poziva u 60 s po vezi
  (vremena u prilogu).
- [ ] **Step 4:** `npx vitest run`. Očekuje se: sve PASS.
- [ ] **Step 5:** Commit: „Server: pozivi i početak partije“.

### Task 3: Potezi, predaja, remi, kraj

**Files:** Modify `server/play/src/hub.ts`. Test `server/play/test/game.test.ts`.

- [ ] **Step 1: Napiši testove:**
  - `potez belog na ply 0 stiže crnom, a beli dobija ack 0`;
  - `potez crnog kad je beli na redu daje sync sa trenutnom listom i ne prosleđuje se`;
  - `potez sa pogrešnim ply daje sync`;
  - `promocija e7e8q se prosleđuje neizmenjena`;
  - `resign šalje resigned protivniku i briše partiju` (posle toga `resume` sa tim tokenom daje `game_gone`);
  - `potez posle resign daje error game_gone`;
  - `draw_offer pa draw_reply accept:true briše partiju`; `accept:false` je ne briše;
  - `finished od bilo koje strane briše partiju`.
- [ ] **Step 2:** `npx vitest run test/game.test.ts`. Očekuje se: FAIL.
- [ ] **Step 3:** Implementiraj. Red se određuje iz `moves.length % 2` i boje pošiljaoca. Server ne proverava
  šahovsku legalnost.
- [ ] **Step 4:** `npx vitest run`. Očekuje se: sve PASS.
- [ ] **Step 5:** Commit: „Server: potezi, predaja, remi i kraj partije“.

### Task 4: Prekid veze, povratak, istek od 30 minuta

**Files:** Modify `server/play/src/hub.ts`. Test `server/play/test/resume.test.ts`.

- [ ] **Step 1: Napiši testove** (alarm se okida kroz `runDurableObjectAlarm` iz `cloudflare:test`):
  - `zatvaranje veze igrača šalje protivniku opponent away`;
  - `resume sa ispravnim tokenom vraća boju i celu listu poteza i šalje protivniku opponent back`;
  - `resume sa tuđim ili pogrešnim tokenom daje game_gone`;
  - `dok je strana odsutna protivnik i dalje može da odigra potez koji je na redu`, i taj potez stiže u
    `resumed.moves`;
  - `alarm posle 30 min odsustva šalje aborted prisutnoj strani i briše partiju`;
  - `alarm pre isteka ne briše partiju`;
  - `ako su obe strane odsutne duže od 30 min, alarm briše partiju bez poruka`.
- [ ] **Step 2:** `npx vitest run test/resume.test.ts`. Očekuje se: FAIL.
- [ ] **Step 3:** Implementiraj. `webSocketClose` upisuje `away_code` i `away_since` i postavlja alarm na
  najraniji `away_since + 30 min`. `resume` ih briše. Konstanta je `AWAY_LIMIT_MS = 30 * 60 * 1000`.
- [ ] **Step 4:** `npx vitest run`. Očekuje se: sve PASS (Task 1–4).
- [ ] **Step 5:** Commit: „Server: povratak u partiju i istek posle 30 minuta“.

### Task 5: Objava na play.chessko.app

**Files:** Modify `server/play/wrangler.jsonc` samo ako objava traži nešto što nedostaje.

- [ ] **Step 1:** `cd server/play && npx wrangler deploy`. Ako wrangler traži prijavu, zaustavi se i javi
  vlasniku.
- [ ] **Step 2:** `curl -s https://play.chessko.app/health`. Očekuje se: `ok`.
- [ ] **Step 3:** Probna partija skriptom `server/play/scripts/smoke.mjs`. Skripta ostaje u repou, za kasnije
  provere. Dva WebSocket klijenta na `wss://play.chessko.app/ws`: `hello` ×2, `invite`, `accept`, dva poteza,
  `resign`. Očekuje se izlaz `smoke ok`.
- [ ] **Step 4:** Commit: „Server objavljen na play.chessko.app“.

---

## Faza B — iOS

### Task 6: Kod uređaja, poruke protokola, UCI potez, remi dogovorom

**Files:**
- Create: `Chessko/Models/OnlineCode.swift`, `Chessko/Models/OnlineProtocol.swift` (Foundation-only).
- Modify: `Chessko/Models/ChessMove.swift`: dodaj `var uci: String` (npr. `"e7e8q"`, slovo promocije
  malo).
- Modify: `Chessko/Models/GameState.swift`: `DrawReason.agreement`, rawValue `"agreement"`.
- Modify: `Package.swift`: dodaj dva nova fajla u `sources:`.
- Modify: mesta koja prikazuju `DrawReason` (`GameViewModel.statusMessage`) dobijaju „Remi – dogovor.“.
- Test: `Tests/ChesskoEngineTests/OnlineProtocolTests.swift`.

**Interfaces (Produces):**
- `enum OnlineCode { static let alphabet: String; static func generate() -> String; static func format(_ code: String) -> String /* "K7M-4QX" */; static func normalize(_ input: String) -> String? /* nil ako nije ispravan */ }`
- `enum ClientMessage: Encodable` sa slučajevima `hello(code:)`, `resume(game:token:)`, `invite(to:)`,
  `cancel`, `accept(from:)`, `decline(from:)`, `move(ply:uci:)`, `resign`, `drawOffer`,
  `drawReply(accept:)`, `finished`. Kodiraju se tačno po tabeli iz Task 1; `v:1` je uz `hello`.
- `enum ServerMessage: Decodable` sa slučajevima `welcome(code:)`, `resumed(game:color:moves:opponentOnline:)`,
  `inviteSent(to:)`, `invited(from:)`, `inviteCancelled(from:)`, `declined(by:)`,
  `start(game:color:token:opponent:)`, `move(ply:uci:)`, `ack(ply:)`, `sync(moves:)`, `resigned`,
  `drawOffer`, `drawReply(accept:)`, `opponent(online:)`, `aborted`, `error(OnlineError)`, `unknown`.
  `color` je `PieceColor`.
- `enum OnlineError: String { case version, badCode = "bad_code", codeTaken = "code_taken", offline, busy, self_ = "self", rateLimited = "rate_limited", gameGone = "game_gone" }`

- [ ] **Step 1: Napiši testove:**
  - `testGeneratedCodeIsSixCharsFromAlphabet` (1000 kodova);
  - `testFormatInsertsDash`: `"K7M4QX"` → `"K7M-4QX"`;
  - `testNormalizeAcceptsLowercaseAndDash`: `"k7m-4qx"` → `"K7M4QX"`;
  - `testNormalizeRejectsZeroAndWrongLength`: `"K7M4Q0"` i `"K7M4Q"` → `nil`;
  - `testMoveUciRoundTripIncludingPromotion`: `ChessMove.fromUCI("e7e8q", in: s)!.uci == "e7e8q"`, uz FEN
    `8/4P3/8/8/8/8/8/k6K w - - 0 1`;
  - `testHelloEncodesExactJSON`: ključevi `t`, `v`, `code`, vrednosti `"hello"`, `1`, `"K7M4QX"`;
  - `testStartDecodes`: `{"t":"start","game":"g1","color":"b","token":"ab","opponent":"K7M4QX"}` →
    `.start(game:"g1", color:.black, token:"ab", opponent:"K7M4QX")`;
  - `testUnknownTypeDecodesAsUnknown`: nepoznat `t` daje `.unknown`, ne grešku;
  - `testDrawAgreementRoundTripsCodable`.
- [ ] **Step 2:** `swift test --filter OnlineProtocolTests`. Očekuje se: FAIL (ne kompajlira se).
- [ ] **Step 3:** Implementiraj. `generate()` koristi `SystemRandomNumberGenerator`.
- [ ] **Step 4:** `swift test`. Očekuje se: svi testovi PASS (postojećih 111 + novi).
- [ ] **Step 5:** Commit: „iOS: kod uređaja, poruke protokola, UCI potez, remi dogovorom“.

### Task 7: Mašina stanja `OnlineMachine`

**Files:** Create `Chessko/Models/OnlineMachine.swift` (Foundation-only, u `Package.swift`). Test
`Tests/ChesskoEngineTests/OnlineMachineTests.swift`.

**Interfaces (Produces):**
- `struct ActiveGame: Codable, Equatable { let game: String; let token: String; let color: PieceColor; let opponent: String }`
- `enum OnlineState: Equatable`:
  - `idle`, `connecting`, `lobby`;
  - `inviting(to: String)`, `invited(from: String)`;
  - `playing(ActiveGame, link: Link, opponentAway: Bool, drawOfferedByOpponent: Bool, drawOfferedByMe: Bool)`;
  - `ended(OnlineResult)`.

  `Link` je `connected`, `reconnecting`.
- `enum OnlineResult: Equatable { case iResigned, opponentResigned, drawAgreed, aborted, board }`
- `enum OnlineEvent`:
  - mreža: `socketOpened`, `socketClosed`, `received(ServerMessage)`;
  - korisnik: `invite(code: String)`, `cancelInvite`, `accept`, `decline`, `localMove(ply: Int, uci: String)`,
    `resign`, `offerDraw`, `answerDraw(Bool)`, `boardFinished`, `appBecameActive`.
- `enum OnlineEffect: Equatable`:
  - slanje: `send(ClientMessage)`, `connect`;
  - partija: `startGame(ActiveGame)`, `applyRemoteMove(ply: Int, uci: String)`, `replaceMoves([String])`,
    `persist(ActiveGame?)`;
  - prikaz: `showMessage(OnlineNotice)`;
  - mreža: `scheduleReconnect(seconds: Int)`.
- `enum OnlineNotice: Equatable { case offline, busy, selfCode, rateLimited, badCode, declined, updateApp, codeTaken, inviteCancelled, drawDeclined }`
- `struct OnlineMachine { var state: OnlineState; let myCode: String; var stored: ActiveGame?; private var reconnectAttempt: Int; mutating func handle(_ e: OnlineEvent) -> [OnlineEffect] }`

**Pravila koja testovi zaključavaju:**
- `socketOpened` → `send(.hello)`, a ako postoji `stored`, odmah i `send(.resume)`.
- `resumed` → `playing` + `replaceMoves(moves)`.
- `error(.gameGone)` dok postoji `stored` → `persist(nil)` + `ended(.aborted)`.
- `socketClosed` u `playing` → `link: .reconnecting` + `scheduleReconnect`, sa pauzama 1, 2, 4, 8, 15, 15…
  sekundi; posle uspeha se brojač resetuje.
- `appBecameActive` u `.reconnecting` → odmah `connect`.
- `socketClosed` u `lobby` → `connecting` + `scheduleReconnect`.
- `start` → `persist(game)` + `startGame(game)`.
- Kraj (`resigned`, `draw_reply accept`, `boardFinished`, `aborted`, sopstveni `resign`) → `persist(nil)` +
  `ended(...)`. `boardFinished` šalje i `send(.finished)`.

- [ ] **Step 1: Napiši testove**, po jedan za svako pravilo iznad, plus:
  - `testIncomingInviteInLobbyGoesToInvited`;
  - `testInviteWhileInvitedSendsNothing`;
  - `testRemoteMoveIsAppliedOnlyInPlaying`;
  - `testSyncReplacesMoves`;
  - `testDrawOfferThenDeclineClearsFlagAndShowsNotice`;
  - `testVersionErrorShowsUpdateApp`;
  - `testResumeAfterLongAbsenceAppliesMissingMoves`: `resumed` sa 3 poteza dok je lokalno 2 →
    `replaceMoves` sa 3.
- [ ] **Step 2:** `swift test --filter OnlineMachineTests`. Očekuje se: FAIL.
- [ ] **Step 3:** Implementiraj `handle(_:)` kao čistu funkciju, bez tajmera i mreže.
- [ ] **Step 4:** `swift test`. Očekuje se: sve PASS.
- [ ] **Step 5:** Commit: „iOS: mašina stanja igre preko interneta“.

### Task 8: `GameViewModel` u režimu `online`

**Files:** Modify `Chessko/ViewModels/GameViewModel.swift`.

**Interfaces:**
- Produces:
  - `GameMode.online`;
  - `static let onlineSaveKey = "chessko.savedGame.online"`;
  - `var onLocalMove: ((Int, String) -> Void)?`: poziva se posle svakog sopstvenog poteza, sa `ply` (broj
    poteza pre ovog) i `uci`;
  - `func startOnlineGame(color: PieceColor, moves: [String])`;
  - `func applyRemoteMove(_ uci: String) -> Bool`: `false` ako potez nije legalan;
  - `func replaceMoves(_ moves: [String])`;
  - `func endOnline(_ result: OnlineResult)`: `.drawAgreed` postavlja `status = .draw(.agreement)`,
    `.opponentResigned` postavlja `.resigned(protivnikova boja)`;
  - `var onBoardFinished: (() -> Void)?`.
- Ponašanje u `.online`:
  - na potezu se može igrati samo sopstvenom bojom;
  - `isFlipped == (playerColor == .black)`;
  - nema AI-ja ni `undo`-a, a statistika se ne upisuje;
  - status je „Tvoj potez“ / „Prijatelj je na potezu“;
  - mat, pat ili remi po pravilu posle bilo kog poteza zovu `onBoardFinished`.

- [ ] **Step 1:** Implementiraj, prateći postojeće grane za `.localFriend` i `.vsComputer`. `uci` dolazi iz
  Task 6.
- [ ] **Step 2:** Build za simulator (komanda iz `CLAUDE.md`). Očekuje se: `BUILD SUCCEEDED`.
- [ ] **Step 3:** `swift test`. Očekuje se: sve PASS.
- [ ] **Step 4:** Commit: „iOS: GameViewModel u režimu igre preko interneta“.

### Task 9: Veza i `OnlineViewModel`

**Files:**
- Create: `Chessko/Logic/OnlineConnection.swift`: `URLSessionWebSocketTask` na `wss://play.chessko.app/ws`;
  `send(ClientMessage)`; callback-ovi `onOpen`, `onMessage(ServerMessage)` i `onClose`; ping na 25 s.
- Create: `Chessko/ViewModels/OnlineViewModel.swift`: `@Observable @MainActor`. Drži `OnlineMachine`,
  `OnlineConnection` i `GameViewModel(saveKey: GameViewModel.onlineSaveKey)`; izvršava efekte.
- Modify: `Chessko.xcodeproj/project.pbxproj`: dodaj nove fajlove **ručno** (`create_xcode_project.py` se
  NE pokreće; vidi `CLAUDE.md`). Dokaži da se kompajliraju: privremena sintaksna greška mora da obori build.

**Interfaces (Produces):**
- `OnlineViewModel.shared`;
- `var myCode: String`: iz `UserDefaults` `chessko.online.code`, a ako ga nema, pravi se kroz
  `OnlineCode.generate()`;
- `func newCode()`, `func open()`, `func close()`, `func invite(_ input: String)`;
- `func accept()`, `func decline()`, `func cancelInvite()`, `func resign()`, `func offerDraw()`,
  `func answerDraw(_ yes: Bool)`;
- `var state: OnlineState`, `var notice: OnlineNotice?`, `let game: GameViewModel`.

Aktivna partija se čuva u `UserDefaults` pod `chessko.online.activeGame` (JSON `ActiveGame`).
`scenePhase == .active` → `appBecameActive`.

- [ ] **Step 1:** Implementiraj. `persist` piše ili briše `chessko.online.activeGame`, a `scheduleReconnect`
  koristi `Task.sleep`.
- [ ] **Step 2:** Build. Očekuje se: `BUILD SUCCEEDED`.
- [ ] **Step 3:** Commit: „iOS: veza sa serverom i model igre preko interneta“.

### Task 10: Ekrani i tekstovi (iOS)

**Files:**
- Create: `Chessko/Views/OnlineLobbyView.swift`, `Chessko/Views/OnlineGameView.swift` (pbxproj ručno).
- Modify: `Chessko/Views/GameView.swift`: stavka menija „Igraj preko interneta“ (`globe`) otvara
  `OnlineLobbyView` kao celoekranski prikaz.
- Modify: `Chessko/ContentView.swift`: ako pri pokretanju postoji `chessko.online.activeGame`, otvara se
  `OnlineGameView`.
- Modify: `build_localizations.py` (+ regenerisan `Localizable.xcstrings`).

**Ekrani** (komponente `DS.*` i `BoardView`; izgled kao ostatak aplikacije):
- **Lobi:**
  - „Tvoj kod“ velikim slovima, sa dugmićima „Kopiraj“ i „Podeli kod“ (ShareLink) i „Novi kod“;
  - polje „Kod prijatelja“ (velika slova, crtica se dodaje sama) i dugme „Pozovi“;
  - stanje `inviting`: „Čekam da prijatelj prihvati…“ i „Otkaži“;
  - stanje `invited`: dijalog „Kod %@ te poziva na partiju“ sa „Prihvati“ i „Odbij“.
- **Partija:**
  - kartice igrača „Ti“ i „Prijatelj“, sa tačkom boje;
  - tabla;
  - akcije „Predaj“ (uz postojeću potvrdu) i „Ponudi remi“;
  - traka stanja: „Povezujem…“, „Prijatelj je izgubio vezu, čekam…“, „Ponovo se povezujem…“, „Prijatelj nudi
    remi.“ sa „Prihvati“ i „Odbij“;
  - posle kraja: rezultat i „Analiziraj partiju“ (postojeći tok) i „Zatvori“.

  Zatvaranje usred partije pita „Predaj partiju?“.

**Novi ključevi (sr, izvor):**
- lobi: „Igraj preko interneta“, „Tvoj kod“, „Kopiraj“, „Podeli kod“, „Novi kod“, „Kod prijatelja“,
  „Pozovi“, „Čekam da prijatelj prihvati…“, „Otkaži“, „Kod %@ te poziva na partiju“, „Prihvati“, „Odbij“;
- obaveštenja: „Prijatelj je odbio poziv.“, „Prijatelj trenutno nije dostupan. Neka otvori „Igraj preko
  interneta“.“, „Prijatelj je već u partiji.“, „To je tvoj kod.“, „Previše poziva, pokušaj za minut.“,
  „Neispravan kod.“, „Ažuriraj aplikaciju da bi igrao preko interneta.“, „Poziv je otkazan.“;
- veza: „Nema veze sa internetom.“, „Povezujem…“, „Prijatelj je izgubio vezu, čekam…“, „Ponovo se
  povezujem…“;
- partija: „Ponudi remi“, „Prijatelj nudi remi.“, „Prijatelj je odbio remi.“, „Remi – dogovor.“, „Partija je
  prekinuta.“, „Prijatelj“, „Prijatelj je na potezu“.

Ključ koji već postoji u katalogu se koristi, ne duplira.

- [ ] **Step 1:** Dodaj ključeve na 8 jezika u `build_localizations.py` i pokreni ga.
- [ ] **Step 2:** `swift test --filter LocCatalogTests`. Očekuje se: PASS.
- [ ] **Step 3:** Implementiraj ekrane.
- [ ] **Step 4:** Build. Očekuje se: `BUILD SUCCEEDED`.
- [ ] **Step 5:** Dva simulatora (npr. iPhone 17 Pro i iPad) protiv `play.chessko.app`:
  - poziv, prihvatanje, nekoliko poteza, promocija;
  - ponuda remija sa odbijanjem;
  - aplikacija ugašena usred partije i ponovo otvorena (partija se nastavlja);
  - predaja.

  Snimci u svetloj i tamnoj temi.
- [ ] **Step 6:** Commit: „iOS: ekrani igre preko interneta“.

---

## Faza C — Android (isti tipovi i pravila kao iOS)

### Task 11: Kod, protokol, UCI, remi dogovorom, mašina stanja

**Files:**
- Create u `ChesskoAndroid/app/src/main/java/com/veljkoni/chessko/online/`:
  - `OnlineCode.kt`;
  - `OnlineMessages.kt`: sealed `ClientMessage` i `ServerMessage`, isti slučajevi kao iOS Task 6;
  - `OnlineCodec.kt`: `org.json`, `fun encode(m: ClientMessage): String`, `fun decode(s: String): ServerMessage`;
  - `OnlineMachine.kt`: isti stanje, događaji, efekti i pravila kao iOS Task 7.
- Modify: `models/ChessMove.kt` (`val uci: String`), `models/GameState.kt`
  (`DrawReason.AGREEMENT`), `GameViewModel.kt` (`drawReasonToName` / `drawReasonFromName`: `"agreement"`).
- Test JVM: `app/src/test/.../OnlineMachineTest.kt`, `OnlineCodeTest.kt`, `MoveUciTest.kt`.
- Test instrumentisani: `app/src/androidTest/.../OnlineCodecTest.kt`. `org.json` je u JVM testovima samo stub
  (vidi `CLAUDE.md`).

- [ ] **Step 1:** Napiši testove sa istim imenima i tvrdnjama kao iOS Task 6 i Task 7 (Kotlin stil imena).
  Kodek se testira **istim JSON nizovima** kao iOS. To je dokaz da dve platforme govore isti jezik.
- [ ] **Step 2:** `cd ChesskoAndroid && ./gradlew testDebugUnitTest`. Očekuje se: FAIL (ne kompajlira se).
- [ ] **Step 3:** Implementiraj.
- [ ] **Step 4:** `./gradlew testDebugUnitTest`. Broj se čita iz XML-a (XML obriši pre prolaza, vidi
  `CLAUDE.md`). Očekuje se: 149 + novi, 0 padova.
- [ ] **Step 5:** Commit: „Android: kod, protokol i mašina stanja igre preko interneta“. Instrumentisani
  `OnlineCodecTest` ide u zajednički prolaz emulatora u Task 14.

### Task 12: `GameViewModel` u režimu `ONLINE` (Android)

**Files:** Modify `viewmodels/GameViewModel.kt`.

**Interfaces:** isto kao iOS Task 8, sa Kotlin imenima:
- `GameMode.ONLINE`, `ONLINE_SAVE_KEY = "saved_game.online"`;
- `onLocalMove: ((Int, String) -> Unit)?`, `onBoardFinished`;
- `startOnlineGame(color, moves)`, `applyRemoteMove(uci): Boolean`, `replaceMoves(moves)`,
  `endOnline(result)`.

- [ ] **Step 1:** Implementiraj.
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug`. Očekuje se: sve PASS, build uspešan.
- [ ] **Step 3:** Commit: „Android: GameViewModel u režimu igre preko interneta“.

### Task 13: Veza i `OnlineViewModel` (Android)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`: dodaj `android.permission.INTERNET`. Zamena za komentar koji
  kaže da je namerno uklonjena: komentar sada kaže da je koristi **samo** igra preko interneta.
- Modify: `gradle/libs.versions.toml` + `app/build.gradle.kts`: OkHttp, verzija ista kao ona koju Coil 2.6.0
  već donosi (proveri `./gradlew :app:dependencies`).
- Create: `online/OnlineConnection.kt` (OkHttp `WebSocket`, ping 25 s), `online/OnlineViewModel.kt` (isti
  API kao iOS Task 9). Ključevi u `SharedPreferences`: `online.code` i `online.activeGame`. Životni ciklus:
  `ON_START` → `appBecameActive`.

- [ ] **Step 1:** Implementiraj.
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug`. Očekuje se: PASS, build uspešan.
- [ ] **Step 3:** `unzip -p app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml` preko `aapt dump
  permissions`. Očekuje se: `INTERNET` i `VIBRATE`, ništa drugo novo.
- [ ] **Step 4:** Commit: „Android: veza sa serverom i model igre preko interneta“.

### Task 14: Ekrani i tekstovi (Android), zajednički prolaz emulatora

**Files:**
- Create: `ui/OnlineLobbyView.kt`, `ui/OnlineGameView.kt`, kao celoekranska odredišta kroz `ui/FullScreen.kt`.
- Modify: `MainActivity.kt` (stavka „Igraj preko interneta“ u istom meniju gde je „Igraj sa prijateljem“;
  povratak u aktivnu partiju pri pokretanju), `Loc.kt` (isti ključevi kao iOS Task 10, na 8 jezika).

- [ ] **Step 1:** Ključevi u `Loc.kt`, pa `./gradlew testDebugUnitTest`. Očekuje se: `LocTest` PASS.
- [ ] **Step 2:** Implementiraj ekrane po opisu iz iOS Task 10.
- [ ] **Step 3:** Jedan prolaz emulatora (recept iz `CLAUDE.md`, `-gpu host`):
  - `./gradlew connectedDebugAndroidTest`; broj iz XML-a, očekuje se 55 + `OnlineCodecTest`, 0 padova;
  - **unakrsna partija iPhone simulator ↔ Android emulator** preko `play.chessko.app`: poziv u oba smera,
    promocija, ponuda remija, gašenje aplikacije usred partije i povratak, predaja;
  - snimci obe teme.

  Emulator se gasi odmah (`adb emu kill`, `./gradlew --stop`, `pgrep -f qemu-system` prazan).
- [ ] **Step 4:** Commit: „Android: ekrani igre preko interneta“.

---

## Faza D — Privatnost i zatvaranje

### Task 15: Politika privatnosti (repo `chessko-site`)

**Files:** Modify `/Users/veljkoodobasic/Documents/Projects/chessko-site/privacy.html`.

Generator iz prethodnih sesija (`scratchpad/site/gen.py` + `content.py`) možda više ne postoji. U tom slučaju
se istovremeno menjaju `const PP` (svih 8 jezika) i statičan srpski HTML u istom fajlu, i moraju ostati isti.

Šta se menja:
- **uvod:** „aplikacija ne šalje ništa preko interneta“ → sve radi bez interneta osim igre preko interneta;
- **odeljak „Bez interneta“:**
  - izuzetak je igra preko interneta: dok traje, kod uređaja i potezi idu preko servera Chessko na
    Cloudflare-u;
  - server ih briše čim se partija završi ili posle 30 minuta bez povratka;
  - Cloudflare tehnički vidi IP adresu;
  - rečenica „Na Androidu aplikacija nema ni dozvolu za pristup internetu“ se briše;
- **„Bez naloga“:** kod uređaja je nasumičan, čuva se samo na telefonu i može se promeniti;
- **datum izmene.**

- [ ] **Step 1:** Izmeni na svih 8 jezika.
- [ ] **Step 2:** Provera: statičan srpski HTML je isti kao onaj koji JS napravi za `sr`. Svaki znak novog
  teksta postoji u podskupu Fraunces-a (`fonts/fraunces-*.v2.woff2`, `fontTools`, `cmap`). Ako ne postoji,
  ponovo napravi podskup (vidi `CLAUDE.md`, 2026-11-14).
- [ ] **Step 3:** Commit u `chessko-site`, pa `npx wrangler pages deploy . --project-name chessko`.
  `https://chessko.app/privacy` mora biti bajt-isti fajlu.

### Task 16: Zatvaranje

- [ ] **Step 1:** Pun prolaz testova: `swift test`, `cd server/play && npx vitest run`,
  `./gradlew testDebugUnitTest`. Brojevi iz izlaza i XML-a, posle poslednjeg commit-a.
- [ ] **Step 2:** `CLAUDE.md`: nova sekcija „Igra preko interneta“ (server, protokol, mašina stanja, gde su
  ključevi, rok od 30 min, šta se ne čuva) i unos u Changelog. Uz to ispravke zastarelih tvrdnji o „nijednom
  mrežnom pozivu“ i o Androidu bez `INTERNET` (sekcije „Baza zadataka“, „Stanje Android porta“, unos o
  sajtu).
- [ ] **Step 3:** Commit. Izveštaj vlasniku:
  - šta je provereno, sa snimcima;
  - šta on radi pre slanja izdanja: App Store App Privacy i Google Play Data safety, i tekst sajta koji
    tvrdi „bez interneta“ (dizajn).
