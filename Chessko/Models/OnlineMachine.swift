import Foundation

// Mašina stanja igre preko interneta: čista funkcija (događaj → novo stanje +
// efekti). Bez mreže i tajmera — efekte izvršava OnlineViewModel (Task 9).
// Foundation-only.

struct ActiveGame: Codable, Equatable, Sendable {
    let game: String
    let token: String
    let color: PieceColor
    let opponent: String
}

// Zove se OnlineLink (ne Link) da se ne sudara sa SwiftUI-jevim `Link`.
enum OnlineLink: Equatable, Sendable { case connected, reconnecting }

enum OnlineResult: Equatable, Sendable { case iResigned, opponentResigned, drawAgreed, aborted, board }

enum OnlineNotice: Equatable, Sendable {
    case offline, busy, selfCode, rateLimited, badCode, declined, updateApp
    /// U interfejsu, ali ga mašina ne emituje: `code_taken` se tiho rešava
    /// (resume preuzima mesto, ili se ponovo povezuje sa backoff-om).
    case codeTaken
    case inviteCancelled, drawDeclined
}

enum OnlineState: Equatable, Sendable {
    case idle, connecting, lobby
    case inviting(to: String)
    case invited(from: String)
    case playing(ActiveGame, link: OnlineLink, opponentAway: Bool,
                 drawOfferedByOpponent: Bool, drawOfferedByMe: Bool)
    case ended(OnlineResult)
}

enum OnlineEvent: Sendable {
    case socketOpened, socketClosed
    case received(ServerMessage)
    case invite(code: String), cancelInvite, accept, decline
    case localMove(ply: Int, uci: String)
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

    /// Igrač sme da igra/predaje/nudi remi samo dok je veza živa. UI onemogućava
    /// tablu i dugmad kad je `false`.
    var canAct: Bool {
        if case .playing(_, .connected, _, _, _) = state { return true }
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
        state = .ended(r)
        return [.persist(nil)]
    }

    mutating func handle(_ e: OnlineEvent) -> [OnlineEffect] {
        // Socket živost se prati u svakom stanju, i u `.ended`.
        switch e {
        case .socketOpened: socketAlive = true; socketBound = false
        case .socketClosed: socketAlive = false; socketBound = false
        case .received(.welcome), .received(.resumed): socketBound = true
        default: break
        }
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
            case .playing(_, .reconnecting, _, _, _):
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
            case .playing(let g, _, let away, let dBy, let dMe):
                state = .playing(g, link: .reconnecting, opponentAway: away,
                                 drawOfferedByOpponent: dBy, drawOfferedByMe: dMe)
                return [nextBackoff()]
            case .ended:
                return []
            }

        case .appBecameActive:
            switch state {
            case .playing(_, .reconnecting, _, _, _), .connecting: return [.connect]
            default: return []
            }

        case .invite(let code):
            guard case .lobby = state else { return [] }
            state = .inviting(to: code)
            return [.send(.invite(to: code))]
        case .cancelInvite:
            guard case .inviting = state else { return [] }
            state = .lobby
            return [.send(.cancel)]
        case .accept:
            guard case .invited(let from) = state else { return [] }
            return [.send(.accept(from: from))]
        case .decline:
            guard case .invited(let from) = state else { return [] }
            state = .lobby
            return [.send(.decline(from: from))]

        // Dok je veza prekinuta (.reconnecting) akcije se odbijaju (canAct).
        // Potez koji je utrčao u umirući socket ispravlja `replaceMoves` posle
        // `resumed` — server je istina.
        case .localMove(let ply, let uci):
            guard canAct else { return [] }
            return [.send(.move(ply: ply, uci: uci))]
        case .resign:
            guard canAct else { return [] }
            return [.send(.resign)] + end(.iResigned)
        case .offerDraw:
            guard canAct, case .playing(let g, let l, let a, let dBy, _) = state else { return [] }
            state = .playing(g, link: l, opponentAway: a, drawOfferedByOpponent: dBy, drawOfferedByMe: true)
            return [.send(.drawOffer)]
        case .answerDraw(let acc):
            guard canAct, case .playing(let g, let l, let a, _, let dMe) = state else { return [] }
            if acc { return [.send(.drawReply(accept: true))] + end(.drawAgreed) }
            state = .playing(g, link: l, opponentAway: a, drawOfferedByOpponent: false, drawOfferedByMe: dMe)
            return [.send(.drawReply(accept: false))]
        case .boardFinished:
            guard case .playing = state else { return [] }
            // Tabla je gotova pa se završava lokalno čak i bez veze; `finished`
            // se šalje samo ako je veza živa. Inače partiju čisti protivnikov
            // `finished` ili serverov prekid posle 30 minuta.
            let send: [OnlineEffect] = canAct ? [.send(.finished)] : []
            return send + end(.board)

        case .received(let m):
            return receive(m)
        }
    }

    private mutating func receive(_ m: ServerMessage) -> [OnlineEffect] {
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

        case .resumed(_, _, let moves, let online):
            guard let s = stored else { return [] }
            reconnectAttempt = 0
            state = .playing(s, link: .connected, opponentAway: !online,
                             drawOfferedByOpponent: false, drawOfferedByMe: false)
            return [.replaceMoves(moves)]

        case .error(let err):
            switch err {
            case .gameGone:
                guard stored != nil else { return [] }
                return end(.aborted)
            case .codeTaken:
                if stored != nil { return [] }
                state = .connecting
                return [nextBackoff()]
            case .version: return [.showMessage(.updateApp)]
            case .badCode: return backToLobby(.badCode)
            case .offline: return backToLobby(.offline)
            case .busy: return backToLobby(.busy)
            case .self_: return backToLobby(.selfCode)
            case .rateLimited: return backToLobby(.rateLimited)
            }

        case .inviteSent(let to):
            if case .inviting = state { state = .inviting(to: to) }
            return []
        case .invited(let from):
            guard case .lobby = state else { return [] }
            state = .invited(from: from)
            return []
        case .inviteCancelled:
            guard case .invited = state else { return [] }
            state = .lobby
            return [.showMessage(.inviteCancelled)]
        case .declined:
            guard case .inviting = state else { return [] }
            state = .lobby
            return [.showMessage(.declined)]

        case .start(let game, let color, let token, let opponent):
            switch state {
            case .lobby, .inviting, .invited: break
            default: return []
            }
            let g = ActiveGame(game: game, token: token, color: color, opponent: opponent)
            stored = g
            state = .playing(g, link: .connected, opponentAway: false,
                             drawOfferedByOpponent: false, drawOfferedByMe: false)
            return [.persist(g), .startGame(g)]

        case .move(let ply, let uci):
            guard case .playing = state else { return [] }
            return [.applyRemoteMove(ply: ply, uci: uci)]
        case .sync(let moves):
            guard case .playing = state else { return [] }
            return [.replaceMoves(moves)]
        case .ack, .unknown:
            return []

        case .resigned:
            guard case .playing = state else { return [] }
            return end(.opponentResigned)
        case .aborted:
            guard case .playing = state else { return [] }
            return end(.aborted)
        case .drawOffer:
            guard case .playing(let g, let l, let a, _, let dMe) = state else { return [] }
            state = .playing(g, link: l, opponentAway: a, drawOfferedByOpponent: true, drawOfferedByMe: dMe)
            return []
        case .drawReply(let acc):
            guard case .playing(let g, let l, let a, let dBy, _) = state else { return [] }
            if acc { return end(.drawAgreed) }
            state = .playing(g, link: l, opponentAway: a, drawOfferedByOpponent: dBy, drawOfferedByMe: false)
            return [.showMessage(.drawDeclined)]
        case .opponent(let online):
            guard case .playing(let g, let l, _, let dBy, let dMe) = state else { return [] }
            state = .playing(g, link: l, opponentAway: !online, drawOfferedByOpponent: dBy, drawOfferedByMe: dMe)
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
