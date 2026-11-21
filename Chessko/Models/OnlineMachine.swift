import Foundation

// Mašina stanja igre preko interneta: čista funkcija (događaj → novo stanje +
// efekti). Bez mreže i tajmera — efekte izvršava OnlineViewModel. Vreme ulazi
// samo kroz `received(_, at:)` (za prikaz sata), mašina ga ne meri.
// Foundation-only.

struct ActiveGame: Codable, Equatable, Sendable {
    let game: String
    let token: String
    let color: PieceColor
    let opponent: String
    /// Vremenska kontrola; `nil` = partija bez sata. Stari zapisi (bez polja) se
    /// dekodiraju kao `nil` — svaka partija pre protokola v2 je bez sata.
    let clock: ClockSpec?

    init(game: String, token: String, color: PieceColor, opponent: String, clock: ClockSpec? = nil) {
        self.game = game
        self.token = token
        self.color = color
        self.opponent = opponent
        self.clock = clock
    }
}

/// Poslednje stanje sata sa servera i trenutak kad je primljeno (`receivedAt`, iz
/// događaja — mašina ne zna za vreme). Prikaz odbrojava od toga; server je istina.
struct ClockReading: Equatable, Sendable {
    let wire: WireClock
    let receivedAt: Double
}

// Zove se OnlineLink (ne Link) da se ne sudara sa SwiftUI-jevim `Link`.
enum OnlineLink: Equatable, Sendable { case connected, reconnecting }

enum OnlineResult: Equatable, Sendable {
    case iResigned, opponentResigned, drawAgreed, aborted, board
    /// Partija na vreme: istekao mi je sat / protivniku je istekao sat.
    case iFlagged, opponentFlagged
    /// Partija na vreme: prvi potez nije odigran u roku; bez rezultata.
    case firstMoveExpired
}

enum OnlineNotice: Equatable, Sendable {
    case offline, busy, selfCode, rateLimited, badCode, declined
    /// U interfejsu, ali ga mašina ne emituje: `code_taken` se tiho rešava
    /// (resume preuzima mesto, ili se ponovo povezuje sa backoff-om).
    case codeTaken
    case inviteCancelled, drawDeclined
    /// Poziv sa satom stigao je do starije verzije aplikacije (`needs_update`).
    case needsUpdate
}

enum OnlineState: Equatable, Sendable {
    case idle, connecting, lobby
    case inviting(to: String)
    /// `clock == nil`: poziv bez sata.
    case invited(from: String, clock: ClockSpec?)
    /// `clock == nil`: partija bez sata (ili sat još nije stigao).
    case playing(ActiveGame, link: OnlineLink, opponentAway: Bool,
                 drawOfferedByOpponent: Bool, drawOfferedByMe: Bool, clock: ClockReading?)
    case ended(OnlineResult)
}

enum OnlineEvent: Sendable {
    case socketOpened, socketClosed
    /// `at`: trenutak prijema (sekunde, monotono: `ProcessInfo.processInfo.systemUptime`).
    case received(ServerMessage, at: Double)
    case invite(code: String, clock: ClockSpec?), cancelInvite, accept, decline
    /// `ends`: potez po pravilima završava partiju (mat, pat, remi po pravilu).
    case localMove(ply: Int, uci: String, ends: Bool)
    case resign, offerDraw, answerDraw(Bool), boardFinished, appBecameActive
    /// Iz `.ended` nazad u lobi (isti socket, bez novog hello-a ako je živ).
    case backToLobby
}

enum OnlineEffect: Equatable, Sendable {
    case send(ClientMessage)
    case connect
    case startGame(ActiveGame)
    case applyRemoteMove(ply: Int, uci: String)
    case replaceMoves([String])
    case persist(ActiveGame?)
    case showMessage(OnlineNotice)
    case scheduleReconnect(seconds: Int)
}

struct OnlineMachine: Sendable {
    var state: OnlineState = .idle
    let myCode: String
    var stored: ActiveGame?
    private var reconnectAttempt = 0
    /// Da li je socket trenutno otvoren (za `backToLobby` iz `.ended`).
    private var socketAlive = false
    /// Da li je TRENUTNI socket vezan za `myCode` (welcome/resumed stigli).
    private var socketBound = false
    /// Server je odbio verziju protokola (`error version`). Kraj za ovu sesiju:
    /// mašina više ne traži vezu i ignoriše sve događaje; UI trajno traži ažuriranje.
    private(set) var updateRequired = false
    /// Partija na vreme: poslat je sopstveni završni potez (`end: true`), kraj se
    /// proglašava tek na njegov `ack` (server je istina — potez je mogao stići kasno).
    private var pendingEndPly: Int?

    /// Igrač sme da igra/predaje/nudi remi samo dok je veza živa. UI onemogućava
    /// tablu i dugmad kad je `false`. Dok se čeka `ack` završnog poteza nema akcija.
    var canAct: Bool {
        guard !updateRequired, pendingEndPly == nil else { return false }
        if case .playing(_, .connected, _, _, _, _) = state { return true }
        return false
    }

    private var isTimed: Bool {
        if case .playing(let g, _, _, _, _, _) = state { return g.clock != nil }
        return false
    }

    private static let backoff = [1, 2, 4, 8, 15]

    init(myCode: String, stored: ActiveGame? = nil) {
        self.myCode = myCode
        self.stored = stored
    }

    private mutating func nextBackoff() -> OnlineEffect {
        let s = Self.backoff[min(reconnectAttempt, Self.backoff.count - 1)]
        reconnectAttempt += 1
        return .scheduleReconnect(seconds: s)
    }

    private mutating func end(_ r: OnlineResult) -> [OnlineEffect] {
        stored = nil
        pendingEndPly = nil
        state = .ended(r)
        return [.persist(nil)]
    }

    mutating func handle(_ e: OnlineEvent) -> [OnlineEffect] {
        // Socket živost se prati u svakom stanju, i u `.ended`.
        switch e {
        case .socketOpened: socketAlive = true; socketBound = false
        case .socketClosed: socketAlive = false; socketBound = false
        case .received(.welcome, _), .received(.resumed, _): socketBound = true
        default: break
        }
        if updateRequired { return [] }
        if case .ended = state {
            switch e {
            case .backToLobby:
                if socketAlive && socketBound { state = .lobby; return [] }
                state = .connecting
                // Živ a nevezan socket je već poslao hello; njegov `welcome` vodi u lobi.
                return socketAlive ? [] : [.connect]
            case .socketOpened:
                // Nikad resume: stored je ovde nil.
                return [.send(.hello(code: myCode))]
            default:
                return []
            }
        }
        switch e {
        case .backToLobby:
            return []
        case .socketOpened:
            switch state {
            case .idle, .connecting:
                state = .connecting
            case .playing(_, .reconnecting, _, _, _, _):
                break
            default:
                return []
            }
            var fx: [OnlineEffect] = [.send(.hello(code: myCode))]
            if let s = stored { fx.append(.send(.resume(game: s.game, token: s.token))) }
            return fx

        case .socketClosed:
            switch state {
            case .idle, .connecting, .lobby, .inviting, .invited:
                state = .connecting
                return [nextBackoff()]
            case .playing(let g, _, let away, let dBy, let dMe, let c):
                state = .playing(g, link: .reconnecting, opponentAway: away,
                                 drawOfferedByOpponent: dBy, drawOfferedByMe: dMe, clock: c)
                return [nextBackoff()]
            case .ended:
                return []
            }

        case .appBecameActive:
            switch state {
            case .playing(_, .reconnecting, _, _, _, _), .connecting: return [.connect]
            default: return []
            }

        case .invite(let code, let clock):
            guard case .lobby = state else { return [] }
            state = .inviting(to: code)
            return [.send(.invite(to: code, clock: clock))]
        case .cancelInvite:
            guard case .inviting = state else { return [] }
            state = .lobby
            return [.send(.cancel)]
        case .accept:
            // Isti sat koji je pozvani video (server ignoriše `accept` sa drugim).
            guard case .invited(let from, let clock) = state else { return [] }
            return [.send(.accept(from: from, clock: clock))]
        case .decline:
            guard case .invited(let from, _) = state else { return [] }
            state = .lobby
            return [.send(.decline(from: from))]

        // Dok je veza prekinuta (.reconnecting) akcije se odbijaju (canAct).
        // Potez koji je utrčao u umirući socket ispravlja `replaceMoves` posle
        // `resumed` — server je istina.
        case .localMove(let ply, let uci, let ends):
            guard canAct else { return [] }
            // Bez sata `end` se ne šalje (kraj ide kroz `finished`, kao u v1).
            guard ends, isTimed else { return [.send(.move(ply: ply, uci: uci, end: false))] }
            pendingEndPly = ply
            return [.send(.move(ply: ply, uci: uci, end: true))]
        case .resign:
            guard canAct else { return [] }
            return [.send(.resign)] + end(.iResigned)
        case .offerDraw:
            guard canAct, case .playing(let g, let l, let a, let dBy, _, let c) = state else { return [] }
            state = .playing(g, link: l, opponentAway: a, drawOfferedByOpponent: dBy, drawOfferedByMe: true, clock: c)
            return [.send(.drawOffer)]
        case .answerDraw(let acc):
            guard canAct, case .playing(let g, let l, let a, _, let dMe, let c) = state else { return [] }
            if acc { return [.send(.drawReply(accept: true))] + end(.drawAgreed) }
            state = .playing(g, link: l, opponentAway: a, drawOfferedByOpponent: false, drawOfferedByMe: dMe, clock: c)
            return [.send(.drawReply(accept: false))]
        case .boardFinished:
            guard case .playing = state else { return [] }
            // Sa satom i sopstvenim završnim potezom na putu (`end: true`) kraj stiže
            // sa njegovim `ack`-om — ovde se ništa ne šalje i ne završava.
            if isTimed && pendingEndPly != nil { return [] }
            // Tabla je gotova pa se završava lokalno čak i bez veze; `finished`
            // se šalje samo ako je veza živa. Bez sata partiju inače čisti protivnikov
            // `finished` ili serverov prekid posle 30 minuta. Sa satom se `finished`
            // šalje isto: ako se dva telefona ikad ne slože oko remija po pravilu
            // (protivnikov potez je stigao bez `end: true`), server bi bez njega držao
            // partiju i sat bi i dalje tekao, pa bi igrač kasnije izgubio na vreme.
            // Kad je partija na serveru već završena (npr. `resumed` posle protivnikovog
            // `end: true` dok smo bili odsutni), server odgovara `game_gone`, koji se u
            // `.ended` zanemaruje.
            let send: [OnlineEffect] = canAct ? [.send(.finished)] : []
            return send + end(.board)

        case .received(let m, let at):
            return receive(m, at: at)
        }
    }

    /// Novo stanje sata (`nil` u poruci ne briše poslednje poznato).
    private mutating func refreshClock(_ wire: WireClock?, at: Double) {
        guard let wire, case .playing(let g, let l, let a, let dBy, let dMe, _) = state else { return }
        state = .playing(g, link: l, opponentAway: a, drawOfferedByOpponent: dBy, drawOfferedByMe: dMe,
                         clock: ClockReading(wire: wire, receivedAt: at))
    }

    private mutating func receive(_ m: ServerMessage, at: Double) -> [OnlineEffect] {
        switch m {
        case .welcome:
            switch state {
            case .idle, .connecting:
                reconnectAttempt = 0
                // Sa sačuvanom partijom čekamo resumed / game_gone.
                if stored == nil { state = .lobby }
                return []
            default: return []
            }

        case .resumed(_, _, let moves, let online, let clock):
            guard let s = stored else { return [] }
            reconnectAttempt = 0
            // Završni potez koji nije stigao do servera nije u `moves`; partija ide dalje.
            pendingEndPly = nil
            // Za već završenu partiju sat je zamrznut, a poruka kraja (ili kraj na
            // tabli) stiže odmah posle — ovo stanje sata traje samo do nje.
            state = .playing(s, link: .connected, opponentAway: !online,
                             drawOfferedByOpponent: false, drawOfferedByMe: false,
                             clock: clock.map { ClockReading(wire: $0, receivedAt: at) })
            return [.replaceMoves(moves)]

        case .error(let err):
            switch err {
            case .gameGone:
                guard stored != nil else { return [] }
                // `game_gone` dobija strana koja je završila partiju: njen završni potez
                // (`end: true`) je prihvaćen, samo `ack` nije stigao pre prekida veze.
                if pendingEndPly != nil { return end(.board) }
                return end(.aborted)
            case .codeTaken:
                if stored != nil { return [] }
                state = .connecting
                return [nextBackoff()]
            case .version:
                updateRequired = true
                return []
            case .badCode: return backToLobby(.badCode)
            case .offline: return backToLobby(.offline)
            case .busy: return backToLobby(.busy)
            case .self_: return backToLobby(.selfCode)
            case .rateLimited: return backToLobby(.rateLimited)
            case .needsUpdate: return backToLobby(.needsUpdate)
            case .badClock:
                // Ispravan klijent je ne izaziva (šalje samo `.blitz`/`.rapid`): nazad u lobi.
                switch state {
                case .inviting, .invited: state = .lobby
                default: break
                }
                return []
            }

        case .inviteSent(let to):
            if case .inviting = state { state = .inviting(to: to) }
            return []
        case .invited(let from, let clock):
            guard case .lobby = state else { return [] }
            // Bez `clock` (ili neispravan) = poziv bez sata; sat se ne izmišlja.
            state = .invited(from: from, clock: clock)
            return []
        case .inviteCancelled:
            guard case .invited = state else { return [] }
            state = .lobby
            return [.showMessage(.inviteCancelled)]
        case .declined:
            guard case .inviting = state else { return [] }
            state = .lobby
            return [.showMessage(.declined)]

        case .start(let game, let color, let token, let opponent, let clock):
            switch state {
            case .lobby, .inviting, .invited: break
            default: return []
            }
            let g = ActiveGame(game: game, token: token, color: color, opponent: opponent, clock: clock?.spec)
            stored = g
            pendingEndPly = nil
            state = .playing(g, link: .connected, opponentAway: false,
                             drawOfferedByOpponent: false, drawOfferedByMe: false,
                             clock: clock.map { ClockReading(wire: $0, receivedAt: at) })
            return [.persist(g), .startGame(g)]

        case .move(let ply, let uci, let ends, let clock):
            guard case .playing = state else { return [] }
            refreshClock(clock, at: at)
            // Sa satom server je partiju već završio (Ruling 2): bez `finished`.
            // Bez sata `end` nema značenje — kraj ide kroz `boardFinished` kao do sada.
            if ends && isTimed { return [.applyRemoteMove(ply: ply, uci: uci)] + end(.board) }
            return [.applyRemoteMove(ply: ply, uci: uci)]
        case .sync(let moves, let clock):
            guard case .playing = state else { return [] }
            refreshClock(clock, at: at)
            // Server nije prihvatio naš potez (pa ni završni).
            pendingEndPly = nil
            return [.replaceMoves(moves)]
        case .ack(let ply, let clock):
            guard case .playing = state else { return [] }
            refreshClock(clock, at: at)
            if let p = pendingEndPly, p == ply { return end(.board) }
            return []
        case .flag(let loser):
            guard case .playing(let g, _, _, _, _, _) = state else { return [] }
            return end(loser == g.color ? .iFlagged : .opponentFlagged)
        case .firstMoveExpired:
            guard case .playing = state else { return [] }
            return end(.firstMoveExpired)
        case .unknown:
            return []

        case .resigned:
            guard case .playing = state else { return [] }
            return end(.opponentResigned)
        case .aborted:
            guard case .playing = state else { return [] }
            return end(.aborted)
        case .drawOffer:
            guard case .playing(let g, let l, let a, _, let dMe, let c) = state else { return [] }
            state = .playing(g, link: l, opponentAway: a, drawOfferedByOpponent: true, drawOfferedByMe: dMe, clock: c)
            return []
        case .drawReply(let acc):
            guard case .playing(let g, let l, let a, let dBy, _, let c) = state else { return [] }
            if acc { return end(.drawAgreed) }
            state = .playing(g, link: l, opponentAway: a, drawOfferedByOpponent: dBy, drawOfferedByMe: false, clock: c)
            return [.showMessage(.drawDeclined)]
        case .opponent(let online):
            guard case .playing(let g, let l, _, let dBy, let dMe, let c) = state else { return [] }
            state = .playing(g, link: l, opponentAway: !online, drawOfferedByOpponent: dBy, drawOfferedByMe: dMe, clock: c)
            return []
        }
    }

    private mutating func backToLobby(_ n: OnlineNotice) -> [OnlineEffect] {
        switch state {
        case .inviting, .invited: state = .lobby
        default: break
        }
        return [.showMessage(n)]
    }
}

/// Potvrda zatvaranja usred partije. Varijanta se bira kad se dijalog otvori i ne
/// menja se dok je otvoren (veza koja se vrati ne sme pretvoriti „Zatvori" u „Predaj").
enum OnlineCloseConfirm: Equatable, Sendable {
    /// „Predati partiju?" [Predaj]
    case resign
    /// „Partija će te čekati." [Zatvori] — partija bez sata.
    case gameWaits
    /// „Sat i dalje teče." [Zatvori] — partija na vreme: odsustvo ne zaustavlja sat.
    case clockRuns

    init(canAct: Bool, timed: Bool = false) {
        self = canAct ? .resign : (timed ? .clockRuns : .gameWaits)
    }

    /// Potvrda predaje samo ako je ponuđena predaja I mašina u trenutku dodira još sme
    /// da igra; inače se ekran zatvara a partija čeka.
    func resignsOnConfirm(canActNow: Bool) -> Bool { self == .resign && canActNow }
}


/// Odluke oko `scenePhase` koje ne zavise od UI-ja (testabilne). Isto kao Android
/// `appWentToBackground`/`appBecameActive`.
enum OnlineScenePolicy {
    /// U pozadini se veza prekida samo ako je ekran igre otvoren ili čeka sačuvana partija.
    /// `.inactive` (Kontrolni centar, baner poziva) nikad ne prekida vezu.
    static func shouldDropOnBackground(uiOpen: Bool, hasStoredGame: Bool) -> Bool {
        uiOpen || hasStoredGame
    }
    /// Dok je aplikacija u pozadini nijedna veza se ne otvara (ni zakazani reconnect).
    static func mayConnect(backgrounded: Bool) -> Bool { !backgrounded }
}
