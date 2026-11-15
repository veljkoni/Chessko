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
}

enum ClientMessage: Encodable, Sendable, Equatable {
    case hello(code: String)
    case resume(game: String, token: String)
    case invite(to: String)
    case cancel
    case accept(from: String)
    case decline(from: String)
    case move(ply: Int, uci: String)
    case resign
    case drawOffer
    case drawReply(accept: Bool)
    case finished

    static let protocolVersion = 1

    private enum Keys: String, CodingKey {
        case t, v, code, game, token, to, from, ply, uci, accept
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
        case .invite(let to):
            try c.encode("invite", forKey: .t)
            try c.encode(to, forKey: .to)
        case .cancel:
            try c.encode("cancel", forKey: .t)
        case .accept(let from):
            try c.encode("accept", forKey: .t)
            try c.encode(from, forKey: .from)
        case .decline(let from):
            try c.encode("decline", forKey: .t)
            try c.encode(from, forKey: .from)
        case .move(let ply, let uci):
            try c.encode("move", forKey: .t)
            try c.encode(ply, forKey: .ply)
            try c.encode(uci, forKey: .uci)
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
    case resumed(game: String, color: PieceColor, moves: [String], opponentOnline: Bool)
    case inviteSent(to: String)
    case invited(from: String)
    case inviteCancelled(from: String)
    case declined(by: String)
    case start(game: String, color: PieceColor, token: String, opponent: String)
    case move(ply: Int, uci: String)
    case ack(ply: Int)
    case sync(moves: [String])
    case resigned
    case drawOffer
    case drawReply(accept: Bool)
    case opponent(online: Bool)
    case aborted
    case error(OnlineError)
    case unknown

    private enum Keys: String, CodingKey {
        case t, code, reason, game, color, moves, opponent, to, from, by, token, ply, uci, accept, state
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
            return .resumed(game: g, color: col, moves: moves, opponentOnline: opp == "online")
        case "invite_sent":
            return s(.to).map { .inviteSent(to: $0) }
        case "invited":
            return s(.from).map { .invited(from: $0) }
        case "invite_cancelled":
            return s(.from).map { .inviteCancelled(from: $0) }
        case "declined":
            return s(.by).map { .declined(by: $0) }
        case "start":
            guard let g = s(.game), let col = color(c), let tok = s(.token), let opp = s(.opponent)
            else { return nil }
            return .start(game: g, color: col, token: tok, opponent: opp)
        case "move":
            guard let p = i(.ply), let u = s(.uci) else { return nil }
            return .move(ply: p, uci: u)
        case "ack":
            return i(.ply).map { .ack(ply: $0) }
        case "sync":
            return (try? c.decode([String].self, forKey: .moves)).map { .sync(moves: $0) }
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
        default:
            return nil
        }
    }
}
