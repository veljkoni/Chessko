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
        machine = OnlineMachine(myCode: code, stored: stored)
        game = GameViewModel(saveKey: GameViewModel.onlineSaveKey)
        game.onlineCanMove = machine.canAct
        game.onLocalMove = { [weak self] ply, uci in self?.handle(.localMove(ply: ply, uci: uci)) }
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
        handle(.invite(code: code))
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
        guard phase == .active, uiOpen || machine.stored != nil else { return }
        if case .idle = machine.state {
            if connection == nil { openSocket() }
            return
        }
        handle(.appBecameActive)
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
        guard !updateRequired else { return }
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
            self.handle(.received(m))
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
        reconnectTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled, let self, self.needsConnection else { return }
            self.openSocket()
        }
    }

    private var needsConnection: Bool {
        switch machine.state {
        case .connecting: return uiOpen || machine.stored != nil
        case .playing(_, .reconnecting, _, _, _): return true
        default: return false
        }
    }
}
