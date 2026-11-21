package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.online.ClientMessage
import com.veljkoni.chessko.online.ClockSpec
import com.veljkoni.chessko.online.OnlineCodec
import com.veljkoni.chessko.online.OnlineError
import com.veljkoni.chessko.online.ServerMessage
import com.veljkoni.chessko.online.WireClock
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isti JSON nizovi kao iOS OnlineProtocolTests — dokaz da dve platforme govore isti jezik.
 * Instrumentisan jer JVM testovi nose samo stub `org.json`.
 */
@RunWith(AndroidJUnit4::class)
class OnlineCodecTest {
    private fun json(m: ClientMessage) = JSONObject(OnlineCodec.encode(m))
    private fun decode(s: String) = OnlineCodec.decode(s)

    @Test fun helloEncodesExactJSON() {
        val o = json(ClientMessage.Hello("K7M4QX"))
        assertEquals("hello", o.getString("t"))
        assertEquals(2, o.getInt("v"))
        assertEquals("K7M4QX", o.getString("code"))
        assertEquals(3, o.length())
    }

    @Test fun otherClientMessagesEncode() {
        assertEquals("draw_offer", json(ClientMessage.DrawOffer).getString("t"))
        val r = json(ClientMessage.DrawReply(true))
        assertTrue(r.getString("t") == "draw_reply" && r.getBoolean("accept"))
        assertEquals("cancel", json(ClientMessage.Cancel).getString("t"))
        val mv = json(ClientMessage.Move(3, "e2e4"))
        assertTrue(mv.getInt("ply") == 3 && mv.getString("uci") == "e2e4" && !mv.has("v"))
        val rs = json(ClientMessage.Resume("g", "t"))
        assertTrue(rs.getString("game") == "g" && rs.getString("token") == "t")
        assertEquals("K7M4QX", json(ClientMessage.Invite("K7M4QX")).getString("to"))
        assertEquals("K7M4QX", json(ClientMessage.Accept("K7M4QX")).getString("from"))
        assertEquals("decline", json(ClientMessage.Decline("K7M4QX")).getString("t"))
        assertEquals("resign", json(ClientMessage.Resign).getString("t"))
        assertEquals("finished", json(ClientMessage.Finished).getString("t"))
        assertFalse(json(ClientMessage.Finished).has("v"))
    }

    @Test fun startDecodes() {
        val m = decode("""{"t":"start","game":"g1","color":"b","token":"ab","opponent":"K7M4QX"}""")
        assertEquals(ServerMessage.Start("g1", PieceColor.BLACK, "ab", "K7M4QX"), m)
    }

    @Test fun otherServerMessagesDecode() {
        assertEquals(ServerMessage.Welcome("K7M4QX"), decode("""{"t":"welcome","code":"K7M4QX"}"""))
        assertEquals(
            ServerMessage.Resumed("g", PieceColor.WHITE, listOf("e2e4"), false),
            decode("""{"t":"resumed","game":"g","color":"w","moves":["e2e4"],"opponent":"away"}"""),
        )
        assertEquals(ServerMessage.InviteSent("A"), decode("""{"t":"invite_sent","to":"A"}"""))
        assertEquals(ServerMessage.Invited("A"), decode("""{"t":"invited","from":"A"}"""))
        assertEquals(ServerMessage.InviteCancelled("A"), decode("""{"t":"invite_cancelled","from":"A"}"""))
        assertEquals(ServerMessage.Declined("A"), decode("""{"t":"declined","by":"A"}"""))
        assertEquals(ServerMessage.Move(2, "e7e5"), decode("""{"t":"move","ply":2,"uci":"e7e5"}"""))
        assertEquals(ServerMessage.Ack(2), decode("""{"t":"ack","ply":2}"""))
        assertEquals(ServerMessage.Sync(listOf("a", "b")), decode("""{"t":"sync","moves":["a","b"]}"""))
        assertEquals(ServerMessage.Resigned, decode("""{"t":"resigned"}"""))
        assertEquals(ServerMessage.DrawOffer, decode("""{"t":"draw_offer"}"""))
        assertEquals(ServerMessage.DrawReply(false), decode("""{"t":"draw_reply","accept":false}"""))
        assertEquals(ServerMessage.Opponent(false), decode("""{"t":"opponent","state":"away"}"""))
        assertEquals(ServerMessage.Opponent(true), decode("""{"t":"opponent","state":"back"}"""))
        assertEquals(ServerMessage.Aborted, decode("""{"t":"aborted"}"""))
        assertEquals(ServerMessage.Error(OnlineError.BAD_CODE), decode("""{"t":"error","reason":"bad_code"}"""))
        assertEquals(ServerMessage.Error(OnlineError.SELF), decode("""{"t":"error","reason":"self"}"""))
    }

    @Test fun unknownTypeDecodesAsUnknown() {
        assertEquals(ServerMessage.Unknown, decode("""{"t":"from_the_future","x":1}"""))
        assertEquals(ServerMessage.Unknown, decode("""{"t":"error","reason":"brand_new"}"""))
        assertEquals(ServerMessage.Unknown, decode("""{"t":"start","game":"g1"}"""))
        assertEquals(ServerMessage.Unknown, decode("""{"t":"move","ply":"x","uci":"e2e4"}"""))
        assertEquals(ServerMessage.Unknown, decode("""{"t":"start","game":"g","color":"x","token":"t","opponent":"o"}"""))
        assertEquals(ServerMessage.Unknown, decode("""{"nope":1}"""))
    }

    // --- v2 (sat) ---

    private val wc = WireClock(ClockSpec(180, 2), 180000, 179000, PieceColor.WHITE, null)

    @Test fun inviteAndAcceptEncodeClock() {
        val i = json(ClientMessage.Invite("K7M4QX", ClockSpec.BLITZ))
        assertEquals(180, i.getJSONObject("clock").getInt("base"))
        assertEquals(2, i.getJSONObject("clock").getInt("inc"))
        assertEquals(2, i.getJSONObject("clock").length())
        assertFalse(json(ClientMessage.Invite("K7M4QX")).has("clock"))
        val a = json(ClientMessage.Accept("K7M4QX", ClockSpec.RAPID))
        assertTrue(a.getJSONObject("clock").getInt("base") == 600 && a.getJSONObject("clock").getInt("inc") == 5)
        assertFalse(json(ClientMessage.Accept("K7M4QX")).has("clock"))
    }

    @Test fun moveEncodesEndOnlyWhenTrue() {
        assertFalse(json(ClientMessage.Move(3, "e2e4")).has("end"))
        assertTrue(json(ClientMessage.Move(3, "e2e4", end = true)).getBoolean("end"))
    }

    @Test fun startDecodesClock() {
        val m = decode(
            """{"t":"start","game":"g1","color":"w","token":"ab","opponent":"K7M4QX",""" +
                """"clock":{"base":180,"inc":2,"w":180000,"b":180000,"run":null,"first":30000}}""",
        )
        assertEquals(
            ServerMessage.Start("g1", PieceColor.WHITE, "ab", "K7M4QX",
                WireClock(ClockSpec(180, 2), 180000, 180000, null, 30000)),
            m,
        )
    }

    @Test fun clockRunningDecodes() {
        val m = decode("""{"t":"ack","ply":3,"clock":{"base":180,"inc":2,"w":180000,"b":179000,"run":"w"}}""")
        assertEquals(ServerMessage.Ack(3, wc), m)
        val b = decode("""{"t":"ack","ply":3,"clock":{"base":180,"inc":2,"w":1,"b":2,"run":"b","first":null}}""")
        assertEquals(ServerMessage.Ack(3, WireClock(ClockSpec(180, 2), 1, 2, PieceColor.BLACK, null)), b)
    }

    @Test fun moveSyncResumedInvitedCarryClock() {
        val c = """"clock":{"base":180,"inc":2,"w":180000,"b":179000,"run":"w"}"""
        assertEquals(ServerMessage.Move(2, "e7e5", true, wc),
            decode("""{"t":"move","ply":2,"uci":"e7e5","end":true,$c}"""))
        assertEquals(ServerMessage.Move(2, "e7e5", false, null), decode("""{"t":"move","ply":2,"uci":"e7e5"}"""))
        assertEquals(ServerMessage.Sync(listOf("a"), wc), decode("""{"t":"sync","moves":["a"],$c}"""))
        assertEquals(
            ServerMessage.Resumed("g", PieceColor.WHITE, listOf("e2e4"), true, wc),
            decode("""{"t":"resumed","game":"g","color":"w","moves":["e2e4"],"opponent":"online",$c}"""),
        )
        assertEquals(ServerMessage.Invited("A", ClockSpec(180, 2)),
            decode("""{"t":"invited","from":"A","clock":{"base":180,"inc":2}}"""))
    }

    @Test fun oldShapeAndMalformedClockDecodeAsNull() {
        assertEquals(ServerMessage.Ack(2, null), decode("""{"t":"ack","ply":2}"""))
        assertEquals(ServerMessage.Ack(2, null), decode("""{"t":"ack","ply":2,"clock":{"base":180}}"""))
        assertEquals(ServerMessage.Ack(2, null),
            decode("""{"t":"ack","ply":2,"clock":{"base":180,"inc":2,"w":1,"b":2,"run":"x"}}"""))
        assertEquals(ServerMessage.Ack(2, null), decode("""{"t":"ack","ply":2,"clock":"nope"}"""))
        assertEquals(ServerMessage.Invited("A", null), decode("""{"t":"invited","from":"A","clock":{"base":"x","inc":2}}"""))
    }

    @Test fun flagAndFirstMoveExpiredDecode() {
        assertEquals(ServerMessage.Flag(PieceColor.WHITE), decode("""{"t":"flag","loser":"w"}"""))
        assertEquals(ServerMessage.Flag(PieceColor.BLACK), decode("""{"t":"flag","loser":"b"}"""))
        assertEquals(ServerMessage.Unknown, decode("""{"t":"flag","loser":"x"}"""))
        assertEquals(ServerMessage.FirstMoveExpired, decode("""{"t":"first_move_expired"}"""))
    }

    @Test fun newErrorsDecode() {
        assertEquals(ServerMessage.Error(OnlineError.NEEDS_UPDATE), decode("""{"t":"error","reason":"needs_update"}"""))
        assertEquals(ServerMessage.Error(OnlineError.BAD_CLOCK), decode("""{"t":"error","reason":"bad_clock"}"""))
    }
}
