package com.veljkoni.chessko.online

import com.veljkoni.chessko.models.PieceColor
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON kodek protokola. Nepoznato sa servera (nepoznat `t`, nepoznat `reason`,
 * polje koje nedostaje ili je pogrešnog tipa) dekodira se u `Unknown`, nikad ne baca —
 * novija verzija servera ne sme da obori stariju aplikaciju. Neispravan JSON
 * (nije objekat) baca `JSONException`, kao i iOS.
 */
object OnlineCodec {
    fun encode(m: ClientMessage): String {
        val o = JSONObject()
        when (m) {
            is ClientMessage.Hello -> {
                o.put("t", "hello"); o.put("v", ClientMessage.PROTOCOL_VERSION); o.put("code", m.code)
            }
            is ClientMessage.Resume -> { o.put("t", "resume"); o.put("game", m.game); o.put("token", m.token) }
            is ClientMessage.Invite -> { o.put("t", "invite"); o.put("to", m.to) }
            ClientMessage.Cancel -> o.put("t", "cancel")
            is ClientMessage.Accept -> { o.put("t", "accept"); o.put("from", m.from) }
            is ClientMessage.Decline -> { o.put("t", "decline"); o.put("from", m.from) }
            is ClientMessage.Move -> { o.put("t", "move"); o.put("ply", m.ply); o.put("uci", m.uci) }
            ClientMessage.Resign -> o.put("t", "resign")
            ClientMessage.DrawOffer -> o.put("t", "draw_offer")
            is ClientMessage.DrawReply -> { o.put("t", "draw_reply"); o.put("accept", m.accept) }
            ClientMessage.Finished -> o.put("t", "finished")
        }
        return o.toString()
    }

    fun decode(s: String): ServerMessage = parse(JSONObject(s)) ?: ServerMessage.Unknown

    // Strogo tipizirano: org.json-ov optString/optInt pretvaraju tipove, a iOS ne.
    private fun str(o: JSONObject, k: String): String? = o.opt(k) as? String
    private fun int(o: JSONObject, k: String): Int? = when (val v = o.opt(k)) {
        is Int -> v
        is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null
        else -> null
    }
    private fun bool(o: JSONObject, k: String): Boolean? = o.opt(k) as? Boolean
    private fun strings(o: JSONObject, k: String): List<String>? {
        val a = o.opt(k) as? JSONArray ?: return null
        return List(a.length()) { a.opt(it) as? String ?: return null }
    }
    private fun color(o: JSONObject): PieceColor? = when (str(o, "color")) {
        "w" -> PieceColor.WHITE
        "b" -> PieceColor.BLACK
        else -> null
    }

    private fun parse(o: JSONObject): ServerMessage? {
        val t = str(o, "t") ?: return null
        return when (t) {
            "welcome" -> str(o, "code")?.let { ServerMessage.Welcome(it) }
            "error" -> str(o, "reason")?.let { OnlineError.fromWire(it) }?.let { ServerMessage.Error(it) }
            "resumed" -> {
                val g = str(o, "game") ?: return null
                val col = color(o) ?: return null
                val moves = strings(o, "moves") ?: return null
                val opp = str(o, "opponent")
                if (opp != "online" && opp != "away") return null
                ServerMessage.Resumed(g, col, moves, opp == "online")
            }
            "invite_sent" -> str(o, "to")?.let { ServerMessage.InviteSent(it) }
            "invited" -> str(o, "from")?.let { ServerMessage.Invited(it) }
            "invite_cancelled" -> str(o, "from")?.let { ServerMessage.InviteCancelled(it) }
            "declined" -> str(o, "by")?.let { ServerMessage.Declined(it) }
            "start" -> {
                val g = str(o, "game") ?: return null
                val col = color(o) ?: return null
                val tok = str(o, "token") ?: return null
                val opp = str(o, "opponent") ?: return null
                ServerMessage.Start(g, col, tok, opp)
            }
            "move" -> {
                val p = int(o, "ply") ?: return null
                val u = str(o, "uci") ?: return null
                ServerMessage.Move(p, u)
            }
            "ack" -> int(o, "ply")?.let { ServerMessage.Ack(it) }
            "sync" -> strings(o, "moves")?.let { ServerMessage.Sync(it) }
            "resigned" -> ServerMessage.Resigned
            "draw_offer" -> ServerMessage.DrawOffer
            "draw_reply" -> bool(o, "accept")?.let { ServerMessage.DrawReply(it) }
            "opponent" -> when (str(o, "state")) {
                "back" -> ServerMessage.Opponent(true)
                "away" -> ServerMessage.Opponent(false)
                else -> null
            }
            "aborted" -> ServerMessage.Aborted
            else -> null
        }
    }
}
