package com.veljkoni.chessko

import com.veljkoni.chessko.models.DrawReason
import com.veljkoni.chessko.models.GameState
import com.veljkoni.chessko.models.ChessMove
import com.veljkoni.chessko.online.OnlineCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Isti sadržaj kao iOS OnlineProtocolTests (deo bez JSON-a; JSON kodek je u androidTest). */
class OnlineCodeTest {
    @Test
    fun generatedCodeIsSixCharsFromAlphabet() {
        repeat(1000) {
            val code = OnlineCode.generate()
            assertEquals(6, code.length)
            assertTrue(code.all { OnlineCode.ALPHABET.contains(it) })
        }
    }

    @Test
    fun formatInsertsDash() {
        assertEquals("K7M-4QX", OnlineCode.format("K7M4QX"))
    }

    @Test
    fun normalizeAcceptsLowercaseAndDash() {
        assertEquals("K7M4QX", OnlineCode.normalize("k7m-4qx"))
        assertEquals("K7M4QX", OnlineCode.normalize(" K7M 4QX "))
    }

    @Test
    fun normalizeRejectsZeroAndWrongLength() {
        assertNull(OnlineCode.normalize("K7M4Q0"))
        assertNull(OnlineCode.normalize("K7M4Q"))
        assertNull(OnlineCode.normalize("K7M4QXX"))
    }

    @Test
    fun drawAgreementIsADrawReason() {
        assertNotNull(DrawReason.Agreement)
    }
}
