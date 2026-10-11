package com.veljkoni.chessko

import com.veljkoni.chessko.logic.EvaluationLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Procena u kartici igraca (dizajnerski pregled, spec § 5): znak nosi stranu, prag
 * nule, mat. Isti slucajevi kao iOS `LocalFormatTests.evaluationLabel*`.
 */
class EvaluationLabelTest {

    @Test
    fun signCarriesSide() {
        assertEquals(EvaluationLabel("+1.2", isMate = false), EvaluationLabel.of(1.24, null, "en"))
        val black = EvaluationLabel.of(-2.3, null, "en")
        assertEquals("-2.3", black.text)
        assertFalse(black.isMate)
        assertEquals("+1,2", EvaluationLabel.of(1.24, null, "sr").text)
    }

    @Test
    fun zeroThresholdHasNoSign() {
        assertEquals("0.0", EvaluationLabel.of(0.04, null, "en").text)
        assertEquals("0.0", EvaluationLabel.of(-0.049, null, "en").text)
        assertEquals("+0.1", EvaluationLabel.of(0.05, null, "en").text)
        assertEquals("-0.1", EvaluationLabel.of(-0.05, null, "en").text)
        assertEquals("0,0", EvaluationLabel.of(0.0, null, "sr").text)
    }

    @Test
    fun mate() {
        assertEquals(EvaluationLabel("M1", isMate = true), EvaluationLabel.of(0.0, 1, "en"))
        assertEquals(EvaluationLabel("−M2", isMate = true), EvaluationLabel.of(99.0, -2, "en"))
    }
}
