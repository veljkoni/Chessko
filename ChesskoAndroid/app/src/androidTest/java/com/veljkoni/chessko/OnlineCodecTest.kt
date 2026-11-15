package com.veljkoni.chessko

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.online.ClientMessage
import com.veljkoni.chessko.online.OnlineCodec
import com.veljkoni.chessko.online.OnlineError
import com.veljkoni.chessko.online.ServerMessage
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
        assertEquals(1, o.getInt("v"))
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
}
