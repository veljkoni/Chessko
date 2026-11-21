import Foundation

// Poruke protokola igre preko interneta (docs/online-protocol.md,
// server/play/src/protocol.ts). Foundation-only. Nepoznato sa servera
// dekodira se u `.unknown`, nikad ne baca — novija verzija servera ne sme
// da obori stariju aplikaciju.

enum OnlineError: String, Sendable, Equatable {
    case version
    case badCode = "bad_code"
    case codeTaken = "code_taken"
    case offline
    case busy
    case self_ = "self"
    case rateLimited = "rate_limited"
    case gameGone = "game_gone"
    case needsUpdate = "needs_update"
    case badClock = "bad_clock"
}

/// Vremenska kontrola partije (sekunde). Sa servera i u pozivu je gola `{base, inc}`.
struct ClockSpec: Codable, Equatable, Sendable {
    let base: Int
    let inc: Int
    static let blitz = ClockSpec(base: 180, inc: 2)
    static let rapid = ClockSpec(base: 600, inc: 5)
}

/// Stanje sata kako ga šalje server (`{base, inc, w, b, run, first}`; w/b/first u ms).
struct WireClock: Equatable, Sendable {
    let spec: ClockSpec
    let whiteMs: Int
    let blackMs: Int
    let running: PieceColor?
    let firstMoveMs: Int?
}

private struct WireClockDTO: Decodable {
    let base: Int
    let inc: Int
    let w: Int
    let b: Int
    let run: String?
    let first: Int?

    var value: WireClock? {
        let running: PieceColor?
        switch run {
        case nil: running = nil
        case "w": running = .white
        case "b": running = .black
        default: return nil
        }
        return WireClock(spec: ClockSpec(base: base, inc: inc), whiteMs: w, blackMs: b,
                         running: running, firstMoveMs: first)
    }
}

enum ClientMessage: Encodable, Sendable, Equatable {
    case hello(code: String)
    case resume(game: String, token: String)
    case invite(to: String, clock: ClockSpec?)
    case cancel
    case accept(from: String, clock: ClockSpec?)
    case decline(from: String)
    case move(ply: Int, uci: String, end: Bool)
    case resign
    case drawOffer
    case drawReply(accept: Bool)
    case finished

    static let protocolVersion = 2

    private enum Keys: String, CodingKey {
        case t, v, code, game, token, to, from, ply, uci, accept, clock, end
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: Keys.self)
        switch self {
        case .hello(let code):
            try c.encode("hello", forKey: .t)
            try c.encode(Self.protocolVersion, forKey: .v)
            try c.encode(code, forKey: .code)
        case .resume(let game, let token):
            try c.encode("resume", forKey: .t)
            try c.encode(game, forKey: .game)
            try c.encode(token, forKey: .token)
        case .invite(let to, let clock):
            try c.encode("invite", forKey: .t)
            try c.encode(to, forKey: .to)
            try c.encodeIfPresent(clock, forKey: .clock)
        case .cancel:
            try c.encode("cancel", forKey: .t)
        case .accept(let from, let clock):
            try c.encode("accept", forKey: .t)
            try c.encode(from, forKey: .from)
            try c.encodeIfPresent(clock, forKey: .clock)
        case .decline(let from):
            try c.encode("decline", forKey: .t)
            try c.encode(from, forKey: .from)
        case .move(let ply, let uci, let end):
            try c.encode("move", forKey: .t)
            try c.encode(ply, forKey: .ply)
            try c.encode(uci, forKey: .uci)
            if end { try c.encode(true, forKey: .end) }
        case .resign:
            try c.encode("resign", forKey: .t)
        case .drawOffer:
            try c.encode("draw_offer", forKey: .t)
        case .drawReply(let accept):
            try c.encode("draw_reply", forKey: .t)
            try c.encode(accept, forKey: .accept)
        case .finished:
            try c.encode("finished", forKey: .t)
        }
    }
}

enum ServerMessage: Decodable, Sendable, Equatable {
    case welcome(code: String)
    case resumed(game: String, color: PieceColor, moves: [String], opponentOnline: Bool, clock: WireClock?)
    case inviteSent(to: String)
    case invited(from: String, clock: ClockSpec?)
    case inviteCancelled(from: String)
    case declined(by: String)
    case start(game: String, color: PieceColor, token: String, opponent: String, clock: WireClock?)
    case move(ply: Int, uci: String, end: Bool, clock: WireClock?)
    case ack(ply: Int, clock: WireClock?)
    case sync(moves: [String], clock: WireClock?)
    case resigned
    case drawOffer
    case drawReply(accept: Bool)
    case opponent(online: Bool)
    case aborted
    case flag(loser: PieceColor)
    case firstMoveExpired
    case error(OnlineError)
    case unknown

    private enum Keys: String, CodingKey {
        case t, code, reason, game, color, moves, opponent, to, from, by, token, ply, uci, accept, state, clock, end, loser
    }

    init(from decoder: Decoder) throws {
        // Neispravan JSON (nije objekat) i dalje baca; sve ostalo ide u .unknown.
        let c = try decoder.container(keyedBy: Keys.self)
        self = Self.parse(c) ?? .unknown
    }

    private static func color(_ c: KeyedDecodingContainer<Keys>) -> PieceColor? {
        switch try? c.decode(String.self, forKey: .color) {
        case "w": return .white
        case "b": return .black
        default: return nil
        }
    }

    private static func wireClock(_ c: KeyedDecodingContainer<Keys>) -> WireClock? {
        (try? c.decode(WireClockDTO.self, forKey: .clock))?.value
    }

    private static func parse(_ c: KeyedDecodingContainer<Keys>) -> ServerMessage? {
        func s(_ k: Keys) -> String? { try? c.decode(String.self, forKey: k) }
        func i(_ k: Keys) -> Int? { try? c.decode(Int.self, forKey: k) }
        guard let t = s(.t) else { return nil }
        switch t {
        case "welcome":
            return s(.code).map { .welcome(code: $0) }
        case "error":
            return s(.reason).flatMap(OnlineError.init(rawValue:)).map { .error($0) }
        case "resumed":
            guard let g = s(.game), let col = color(c),
                  let moves = try? c.decode([String].self, forKey: .moves),
                  let opp = s(.opponent), opp == "online" || opp == "away" else { return nil }
            return .resumed(game: g, color: col, moves: moves, opponentOnline: opp == "online", clock: wireClock(c))
        case "invite_sent":
            return s(.to).map { .inviteSent(to: $0) }
        case "invited":
            guard let f = s(.from) else { return nil }
            return .invited(from: f, clock: try? c.decode(ClockSpec.self, forKey: .clock))
        case "invite_cancelled":
            return s(.from).map { .inviteCancelled(from: $0) }
        case "declined":
            return s(.by).map { .declined(by: $0) }
        case "start":
            guard let g = s(.game), let col = color(c), let tok = s(.token), let opp = s(.opponent)
            else { return nil }
            return .start(game: g, color: col, token: tok, opponent: opp, clock: wireClock(c))
        case "move":
            guard let p = i(.ply), let u = s(.uci) else { return nil }
            return .move(ply: p, uci: u, end: (try? c.decode(Bool.self, forKey: .end)) ?? false, clock: wireClock(c))
        case "ack":
            return i(.ply).map { .ack(ply: $0, clock: wireClock(c)) }
        case "sync":
            return (try? c.decode([String].self, forKey: .moves)).map { .sync(moves: $0, clock: wireClock(c)) }
        case "resigned":
            return .resigned
        case "draw_offer":
            return .drawOffer
        case "draw_reply":
            return (try? c.decode(Bool.self, forKey: .accept)).map { .drawReply(accept: $0) }
        case "opponent":
            switch s(.state) {
            case "back": return .opponent(online: true)
            case "away": return .opponent(online: false)
            default: return nil
            }
        case "aborted":
            return .aborted
        case "flag":
            switch s(.loser) {
            case "w": return .flag(loser: .white)
            case "b": return .flag(loser: .black)
            default: return nil
            }
        case "first_move_expired":
            return .firstMoveExpired
        default:
            return nil
        }
    }
}
