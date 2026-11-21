import Foundation
import SwiftUI

// Pokreće čistu `OnlineMachine` nad pravom vezom i vozi tablu (`GameViewModel`).
// Mašina odlučuje, ovde se samo izvršavaju efekti. Kreira se lazy (`shared`) —
// nijedan postojeći put koda ga ne sme dodirnuti pri pokretanju (mreža).

@Observable
@MainActor
final class OnlineViewModel {
    static let shared = OnlineViewModel()

    static let codeKey = "chessko.online.code"
    static let activeGameKey = "chessko.online.activeGame"
    static let clockKey = "chessko.online.clock"

    /// Poslednji izbor vremena u lobiju (`nil` = bez sata). Prvi put 3+2.
    var selectedClock: ClockSpec? {
        didSet { UserDefaults.standard.set(Self.storedValue(for: selectedClock), forKey: Self.clockKey) }
    }

    static func storedValue(for clock: ClockSpec?) -> String {
        switch clock {
        case ClockSpec.blitz?: return "3+2"
        case ClockSpec.rapid?: return "10+5"
        default: return "none"
        }
    }

    static func clock(fromStored value: String?) -> ClockSpec? {
        switch value {
        case "10+5": return .rapid
        case "none": return nil
        default: return .blitz
        }
    }

    /// „3+2“ (osnova u minutima + dodatak u sekundama) za dijalog poziva i izbor.
    static func clockLabel(_ spec: ClockSpec) -> String { "\(spec.base / 60)+\(spec.inc)" }

    /// Preostalo vreme za prikaz: `M:SS`, ispod 10 s `S.d`; nikad ispod 0 (0 → `0:00`).
    static func clockText(ms: Int) -> String {
        let ms = max(0, ms)
        if ms == 0 { return "0:00" }
        if ms < 10_000 { return "\(ms / 1000).\((ms % 1000) / 100)" }
        let total = ms / 1000
        let h = total / 3600, m = (total % 3600) / 60, sec = total % 60
        if h > 0 { return String(format: "%d:%02d:%02d", h, m, sec) }
        return String(format: "%d:%02d", m, sec)
    }

    /// Čita sačuvanu partiju BEZ pravljenja `shared` (to bi podiglo GameViewModel i motor).
    static var hasStoredGame: Bool {
        guard let data = UserDefaults.standard.data(forKey: activeGameKey) else { return false }
        return (try? JSONDecoder().decode(ActiveGame.self, from: data)) != nil
    }

    /// Ekran igre preko interneta je otvoren — `ContentView` tad ne prosleđuje
    /// `scenePhase` (to radi ekran), da se veza ne otvara dvaput.
    private(set) static var isCoverOpen = false

    private(set) var state: OnlineState = .idle
    var notice: OnlineNotice?
    /// Server je odbio verziju protokola. Važi do kraja procesa: nijedna veza se više
    /// ne otvara, a ekrani trajno traže ažuriranje umesto „Povezujem…".
    private(set) var updateRequired = false
    private(set) var myCode: String
    @ObservationIgnored let game: GameViewModel

    @ObservationIgnored private var machine: OnlineMachine
    @ObservationIgnored private var connection: OnlineConnection?
    /// Broj trenutnog soketa; callback-ovi starijih soketa se ignorišu.
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var reconnectTask: Task<Void, Never>?
    @ObservationIgnored private var uiOpen = false
    /// Aplikacija je u pozadini: veza je namerno prekinuta i nijedna se ne otvara
    /// (ni zakazani reconnect) dok se ne vrati `.active`.
    @ObservationIgnored private var backgrounded = false
    // Događaji se serijalizuju: efekat (npr. `replaceMoves` → `onBoardFinished`)
    // sme da proizvede novi događaj, ali se on obrađuje tek posle tekućeg.
    @ObservationIgnored private var queue: [OnlineEvent] = []
    @ObservationIgnored private var draining = false

    private init() {
        let defaults = UserDefaults.standard
        let code: String
        if let saved = defaults.string(forKey: Self.codeKey), OnlineCode.normalize(saved) == saved {
            code = saved
        } else {
            code = OnlineCode.generate()
            defaults.set(code, forKey: Self.codeKey)
        }
        var stored: ActiveGame?
        if let data = defaults.data(forKey: Self.activeGameKey) {
            stored = try? JSONDecoder().decode(ActiveGame.self, from: data)
        }
        myCode = code
        selectedClock = Self.clock(fromStored: defaults.string(forKey: Self.clockKey))
        machine = OnlineMachine(myCode: code, stored: stored)
        game = GameViewModel(saveKey: GameViewModel.onlineSaveKey)
        game.onlineCanMove = machine.canAct
        // Poziva se posle primene poteza, pa `isGameOver` kaže da li potez završava partiju.
        game.onLocalMove = { [weak self] ply, uci in
            guard let self else { return }
            self.handle(.localMove(ply: ply, uci: uci, ends: self.game.isGameOver))
        }
        game.onBoardFinished = { [weak self] in self?.handle(.boardFinished) }
    }

    /// Postoji sačuvana partija koja čeka nastavak (UI do povezivanja već crta tablu).
    var hasActiveGame: Bool { machine.stored != nil }

    // MARK: Javni API

    func open() {
        uiOpen = true
        Self.isCoverOpen = true
        if case .idle = machine.state, connection == nil { openSocket() }
    }

    /// Dok partija traje veza ostaje pod kontrolom mašine (UI prvo pita „Predaj partiju?").
    func close() {
        uiOpen = false
        Self.isCoverOpen = false
        if case .playing = machine.state { return }
        shutDownSocket()
        machine = OnlineMachine(myCode: myCode, stored: machine.stored)
        sync()
    }

    func invite(_ input: String) {
        guard let code = OnlineCode.normalize(input) else { notice = .badCode; return }
        handle(.invite(code: code, clock: selectedClock))
    }
    func accept() { handle(.accept) }
    func decline() { handle(.decline) }
    func cancelInvite() { handle(.cancelInvite) }
    func resign() { handle(.resign) }
    func offerDraw() { handle(.offerDraw) }
    func answerDraw(_ yes: Bool) { handle(.answerDraw(yes)) }
    func backToLobby() { handle(.backToLobby) }

    /// Novi kod samo kad nema ni žive ni sačuvane partije.
    func newCode() {
        guard machine.stored == nil else { return }
        switch machine.state {
        case .idle, .connecting, .lobby: break
        default: return
        }
        let code = OnlineCode.generate()
        UserDefaults.standard.set(code, forKey: Self.codeKey)
        myCode = code
        shutDownSocket()
        machine = OnlineMachine(myCode: code, stored: nil)
        sync()
        if uiOpen { openSocket() }
    }

    func scenePhaseChanged(_ phase: ScenePhase) {
        switch phase {
        case .background:
            guard OnlineScenePolicy.shouldDropOnBackground(uiOpen: uiOpen, hasStoredGame: machine.stored != nil) else { return }
            // Suspendovana aplikacija ne zatvara soket sama: server je i dalje vidi
            // kao prisutnu pa protivnik nikad ne dobije „away". Zato eksplicitno.
            backgrounded = true
            dropSocketForBackground()
            handle(.socketClosed)
        case .active:
            backgrounded = false
            guard uiOpen || machine.stored != nil else { return }
            if case .idle = machine.state {
                if connection == nil { openSocket() }
                return
            }
            handle(.appBecameActive)
        default:
            break // .inactive ne prekida vezu
        }
    }

    /// Zatvara soket uz `beginBackgroundTask` da close frame stigne pre suspenzije.
    private func dropSocketForBackground() {
        reconnectTask?.cancel()
        reconnectTask = nil
        generation += 1
        guard let c = connection else { return }
        connection = nil
        var taskID = UIBackgroundTaskIdentifier.invalid
        let end: @MainActor () -> Void = {
            guard taskID != .invalid else { return }
            UIApplication.shared.endBackgroundTask(taskID)
            taskID = .invalid
        }
        taskID = UIApplication.shared.beginBackgroundTask(withName: "online-close") { end() }
        c.close {
            // Kratka rezerva da close frame zaista napusti uređaj.
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(300))
                end()
            }
        }
    }

    // MARK: Sat

    /// Monotono vreme u sekundama — jedini izvor i za `at` pri prijemu poruke i za
    /// odbrojavanje. Ručna promena sata ili NTP korak ne pomera prikazani sat (zidni
    /// `Date` bi ga pomerio). Android isto koristi `SystemClock.elapsedRealtime()`.
    nonisolated static func monotonicNow() -> TimeInterval { ProcessInfo.processInfo.systemUptime }

    /// Preostalo ms za stranu (sat se računa lokalno samo za prikaz): `wire` vrednost
    /// umanjena za `now − receivedAt` samo za stranu koja trči, najmanje 0. Lokalna nula
    /// se NIKAD ne tumači — kraj dolazi samo od servera (`flag`).
    func displayedMs(for color: PieceColor, now: TimeInterval = OnlineViewModel.monotonicNow()) -> Int? {
        guard case .playing(_, _, _, _, _, let reading?) = state else { return nil }
        let wire = reading.wire
        let base = color == .white ? wire.whiteMs : wire.blackMs
        guard wire.running == color else { return max(0, base) }
        let elapsed = Int(((now - reading.receivedAt) * 1000).rounded(.down))
        return max(0, base - max(0, elapsed))
    }

    /// Preostalo ms za prvi potez (`nil` kad rok ne važi).
    func firstMoveMsLeft(now: TimeInterval = OnlineViewModel.monotonicNow()) -> Int? {
        guard case .playing(_, _, _, _, _, let reading?) = state, let first = reading.wire.firstMoveMs else { return nil }
        let elapsed = Int(((now - reading.receivedAt) * 1000).rounded(.down))
        return max(0, first - max(0, elapsed))
    }

    /// Partija ima sat (za prikaz kartica).
    var isTimedGame: Bool {
        if case .playing(let g, _, _, _, _, _) = state { return g.clock != nil }
        return false
    }

    // MARK: Događaji i efekti

    private func handle(_ event: OnlineEvent) {
        queue.append(event)
        guard !draining else { return }
        draining = true
        defer { draining = false }
        while !queue.isEmpty { process(queue.removeFirst()) }
    }

    private func process(_ event: OnlineEvent) {
        let wasEnded: Bool
        if case .ended = machine.state { wasEnded = true } else { wasEnded = false }
        let effects = machine.handle(event)
        for effect in effects { execute(effect) }
        if machine.updateRequired && !updateRequired {
            updateRequired = true
            shutDownSocket()
        }
        sync()
        if case .ended(let result) = machine.state, !wasEnded {
            game.endOnline(result)
            // Ekran zatvoren: kod ne sme ostati registrovan na serveru. Mašina
            // dobija socketClosed da `socketAlive` ostane tačan (backToLobby ponovo povezuje).
            if !uiOpen {
                shutDownSocket()
                handle(.socketClosed)
            }
        }
    }

    private func sync() {
        game.onlineCanMove = machine.canAct
        state = machine.state
    }

    private func execute(_ effect: OnlineEffect) {
        switch effect {
        case .send(let message):
            connection?.send(message)
        case .connect:
            openSocket()
        case .startGame(let g):
            game.startOnlineGame(color: g.color, moves: [])
        case .replaceMoves(let moves):
            guard let color = machine.stored?.color else { return }
            if game.gameMode != .online || game.playerColor != color {
                game.startOnlineGame(color: color, moves: moves)
                if game.isGameOver { handle(.boardFinished) }
            } else {
                game.replaceMoves(moves)
            }
        case .applyRemoteMove(_, let uci):
            if !game.applyRemoteMove(uci) { forceReconnect() }
        case .persist(let g):
            persist(g)
        case .showMessage(let n):
            notice = n
        case .scheduleReconnect(let seconds):
            scheduleReconnect(seconds: seconds)
        }
    }

    private func persist(_ g: ActiveGame?) {
        let defaults = UserDefaults.standard
        if let g, let data = try? JSONEncoder().encode(g) {
            defaults.set(data, forKey: Self.activeGameKey)
        } else {
            defaults.removeObject(forKey: Self.activeGameKey)
        }
    }

    // MARK: Veza

    private func openSocket() {
        guard !updateRequired, OnlineScenePolicy.mayConnect(backgrounded: backgrounded) else { return }
        reconnectTask?.cancel()
        reconnectTask = nil
        connection?.close()
        generation += 1
        let gen = generation
        let c = OnlineConnection()
        c.onOpen = { [weak self] in
            guard let self, self.generation == gen else { return }
            self.handle(.socketOpened)
        }
        c.onMessage = { [weak self] m in
            guard let self, self.generation == gen else { return }
            self.handle(.received(m, at: OnlineViewModel.monotonicNow()))
        }
        c.onClose = { [weak self] in
            guard let self, self.generation == gen else { return }
            self.connection = nil
            self.handle(.socketClosed)
        }
        connection = c
        c.open()
    }

    private func shutDownSocket() {
        reconnectTask?.cancel()
        reconnectTask = nil
        connection?.close()
        connection = nil
        generation += 1
    }

    /// Tabla nije u skladu sa serverom: odbaci soket pa ponovo kroz mašinu;
    /// `resume` na novom vraća serverovu listu poteza.
    private func forceReconnect() {
        connection?.close()
        connection = nil
        generation += 1
        handle(.socketClosed)
    }

    private func scheduleReconnect(seconds: Int) {
        reconnectTask?.cancel()
        reconnectTask = nil
        guard OnlineScenePolicy.mayConnect(backgrounded: backgrounded) else { return }
        reconnectTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled, let self, self.needsConnection else { return }
            self.openSocket()
        }
    }

    private var needsConnection: Bool {
        switch machine.state {
        case .connecting: return uiOpen || machine.stored != nil
        case .playing(_, .reconnecting, _, _, _, _): return true
        default: return false
        }
    }
}
