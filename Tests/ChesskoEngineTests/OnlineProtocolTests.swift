import Testing
import Foundation
@testable import ChesskoEngine

@Test func generatedCodeIsSixCharsFromAlphabet() {
    for _ in 0..<1000 {
        let code = OnlineCode.generate()
        #expect(code.count == 6)
        #expect(code.allSatisfy { OnlineCode.alphabet.contains($0) })
    }
}

@Test func formatInsertsDash() {
    #expect(OnlineCode.format("K7M4QX") == "K7M-4QX")
}

@Test func normalizeAcceptsLowercaseAndDash() {
    #expect(OnlineCode.normalize("k7m-4qx") == "K7M4QX")
    #expect(OnlineCode.normalize(" K7M 4QX ") == "K7M4QX")
}

@Test func normalizeRejectsZeroAndWrongLength() {
    #expect(OnlineCode.normalize("K7M4Q0") == nil)
    #expect(OnlineCode.normalize("K7M4Q") == nil)
    #expect(OnlineCode.normalize("K7M4QXX") == nil)
}

@Test func moveUciRoundTripIncludingPromotion() throws {
    let s = try #require(GameState.fromFEN("8/4P3/8/8/8/8/8/k6K w - - 0 1"))
    let m = try #require(ChessMove.fromUCI("e7e8q", in: s))
    #expect(m.uci == "e7e8q")
    let n = try #require(ChessMove.fromUCI("e7e8n", in: s))
    #expect(n.uci == "e7e8n")
    let start = GameState.initial()
    #expect(ChessMove.fromUCI("e2e4", in: start)!.uci == "e2e4")
}

private func json(_ m: ClientMessage) throws -> [String: Any] {
    let data = try JSONEncoder().encode(m)
    return try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
}

@Test func helloEncodesExactJSON() throws {
    let o = try json(.hello(code: "K7M4QX"))
    #expect(o["t"] as? String == "hello")
    #expect(o["v"] as? Int == 2)
    #expect(o["code"] as? String == "K7M4QX")
    #expect(o.count == 3)
}

@Test func otherClientMessagesEncode() throws {
    #expect(try json(.drawOffer)["t"] as? String == "draw_offer")
    let r = try json(.drawReply(accept: true))
    #expect(r["t"] as? String == "draw_reply" && r["accept"] as? Bool == true)
    #expect(try json(.cancel)["t"] as? String == "cancel")
    let mv = try json(.move(ply: 3, uci: "e2e4", end: false))
    #expect(mv["ply"] as? Int == 3 && mv["uci"] as? String == "e2e4" && mv["v"] == nil && mv["end"] == nil)
    let rs = try json(.resume(game: "g", token: "t"))
    #expect(rs["game"] as? String == "g" && rs["token"] as? String == "t")
    #expect(try json(.invite(to: "K7M4QX", clock: nil))["to"] as? String == "K7M4QX")
    #expect(try json(.accept(from: "K7M4QX", clock: nil))["from"] as? String == "K7M4QX")
    #expect(try json(.decline(from: "K7M4QX"))["t"] as? String == "decline")
    #expect(try json(.resign)["t"] as? String == "resign")
    #expect(try json(.finished)["t"] as? String == "finished")
}

private func decode(_ s: String) throws -> ServerMessage {
    try JSONDecoder().decode(ServerMessage.self, from: Data(s.utf8))
}

@Test func startDecodes() throws {
    let m = try decode(#"{"t":"start","game":"g1","color":"b","token":"ab","opponent":"K7M4QX"}"#)
    #expect(m == .start(game: "g1", color: .black, token: "ab", opponent: "K7M4QX", clock: nil))
}

@Test func otherServerMessagesDecode() throws {
    #expect(try decode(#"{"t":"welcome","code":"K7M4QX"}"#) == .welcome(code: "K7M4QX"))
    #expect(try decode(#"{"t":"resumed","game":"g","color":"w","moves":["e2e4"],"opponent":"away"}"#)
            == .resumed(game: "g", color: .white, moves: ["e2e4"], opponentOnline: false, clock: nil))
    #expect(try decode(#"{"t":"invite_sent","to":"A"}"#) == .inviteSent(to: "A"))
    #expect(try decode(#"{"t":"invited","from":"A"}"#) == .invited(from: "A", clock: nil))
    #expect(try decode(#"{"t":"invite_cancelled","from":"A"}"#) == .inviteCancelled(from: "A"))
    #expect(try decode(#"{"t":"declined","by":"A"}"#) == .declined(by: "A"))
    #expect(try decode(#"{"t":"move","ply":2,"uci":"e7e5"}"#) == .move(ply: 2, uci: "e7e5", end: false, clock: nil))
    #expect(try decode(#"{"t":"ack","ply":2}"#) == .ack(ply: 2, clock: nil))
    #expect(try decode(#"{"t":"sync","moves":["a","b"]}"#) == .sync(moves: ["a", "b"], clock: nil))
    #expect(try decode(#"{"t":"resigned"}"#) == .resigned)
    #expect(try decode(#"{"t":"draw_offer"}"#) == .drawOffer)
    #expect(try decode(#"{"t":"draw_reply","accept":false}"#) == .drawReply(accept: false))
    #expect(try decode(#"{"t":"opponent","state":"away"}"#) == .opponent(online: false))
    #expect(try decode(#"{"t":"opponent","state":"back"}"#) == .opponent(online: true))
    #expect(try decode(#"{"t":"aborted"}"#) == .aborted)
    #expect(try decode(#"{"t":"error","reason":"bad_code"}"#) == .error(.badCode))
    #expect(try decode(#"{"t":"error","reason":"self"}"#) == .error(.self_))
}

@Test func unknownTypeDecodesAsUnknown() throws {
    #expect(try decode(#"{"t":"from_the_future","x":1}"#) == .unknown)
    #expect(try decode(#"{"t":"error","reason":"brand_new"}"#) == .unknown)
    #expect(try decode(#"{"t":"start","game":"g1"}"#) == .unknown)
    #expect(try decode(#"{"t":"move","ply":"x","uci":"e2e4"}"#) == .unknown)
    #expect(try decode(#"{"t":"start","game":"g","color":"x","token":"t","opponent":"o"}"#) == .unknown)
    #expect(try decode(#"{"nope":1}"#) == .unknown)
}

@Test func drawAgreementRoundTripsCodable() throws {
    let data = try JSONEncoder().encode(GameStatus.draw(.agreement))
    #expect(try JSONDecoder().decode(GameStatus.self, from: data) == .draw(.agreement))
    #expect(DrawReason.agreement.rawValue == "agreement")
}

// MARK: - protokol v2 (sat)

private let wireJSON = #"{"base":180,"inc":2,"w":175000,"b":180000,"run":"b","first":null}"#
private let wire = WireClock(spec: .blitz, whiteMs: 175000, blackMs: 180000, running: .black, firstMoveMs: nil)

@Test func clockSpecPresets() {
    #expect(ClockSpec.blitz == ClockSpec(base: 180, inc: 2))
    #expect(ClockSpec.rapid == ClockSpec(base: 600, inc: 5))
}

@Test func inviteAndAcceptEncodeClockOnlyWhenPresent() throws {
    let i = try json(.invite(to: "K7M4QX", clock: .blitz))
    let c = try #require(i["clock"] as? [String: Any])
    #expect(c["base"] as? Int == 180 && c["inc"] as? Int == 2)
    #expect(try json(.invite(to: "K7M4QX", clock: nil))["clock"] == nil)
    let a = try json(.accept(from: "K7M4QX", clock: .rapid))
    #expect((a["clock"] as? [String: Any])?["base"] as? Int == 600)
    #expect(try json(.accept(from: "K7M4QX", clock: nil))["clock"] == nil)
}

@Test func moveEncodesEndOnlyWhenTrue() throws {
    let o = try json(.move(ply: 5, uci: "d8h4", end: true))
    #expect(o["end"] as? Bool == true)
    #expect(try json(.move(ply: 5, uci: "d8h4", end: false))["end"] == nil)
}

@Test func v2ServerMessagesDecode() throws {
    #expect(try decode(#"{"t":"invited","from":"A","clock":{"base":600,"inc":5}}"#)
            == .invited(from: "A", clock: .rapid))
    #expect(try decode(#"{"t":"start","game":"g","color":"w","token":"t","opponent":"o","clock":"# + wireJSON + "}")
            == .start(game: "g", color: .white, token: "t", opponent: "o", clock: wire))
    #expect(try decode(#"{"t":"move","ply":2,"uci":"e7e5","end":true,"clock":"# + wireJSON + "}")
            == .move(ply: 2, uci: "e7e5", end: true, clock: wire))
    #expect(try decode(#"{"t":"ack","ply":2,"clock":"# + wireJSON + "}") == .ack(ply: 2, clock: wire))
    #expect(try decode(#"{"t":"sync","moves":["a"],"clock":"# + wireJSON + "}") == .sync(moves: ["a"], clock: wire))
    #expect(try decode(#"{"t":"resumed","game":"g","color":"b","moves":[],"opponent":"online","clock":"# + wireJSON + "}")
            == .resumed(game: "g", color: .black, moves: [], opponentOnline: true, clock: wire))
    #expect(try decode(#"{"t":"flag","loser":"w"}"#) == .flag(loser: .white))
    #expect(try decode(#"{"t":"flag","loser":"b"}"#) == .flag(loser: .black))
    #expect(try decode(#"{"t":"first_move_expired"}"#) == .firstMoveExpired)
    #expect(try decode(#"{"t":"error","reason":"needs_update"}"#) == .error(.needsUpdate))
    #expect(try decode(#"{"t":"error","reason":"bad_clock"}"#) == .error(.badClock))
}

@Test func wireClockFirstAndRunDecode() throws {
    let m = try decode(#"{"t":"ack","ply":0,"clock":{"base":600,"inc":5,"w":600000,"b":600000,"run":null,"first":30000}}"#)
    #expect(m == .ack(ply: 0, clock: WireClock(spec: .rapid, whiteMs: 600000, blackMs: 600000, running: nil, firstMoveMs: 30000)))
}

@Test func v1ShapesStillDecodeWithoutClock() throws {
    #expect(try decode(#"{"t":"start","game":"g","color":"w","token":"t","opponent":"o"}"#)
            == .start(game: "g", color: .white, token: "t", opponent: "o", clock: nil))
    #expect(try decode(#"{"t":"move","ply":1,"uci":"e7e5"}"#) == .move(ply: 1, uci: "e7e5", end: false, clock: nil))
    #expect(try decode(#"{"t":"error","reason":"brand_new"}"#) == .unknown)
}
