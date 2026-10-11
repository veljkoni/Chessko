package com.veljkoni.chessko

import com.veljkoni.chessko.logic.BoardSizing
import com.veljkoni.chessko.ui.theme.DS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec § 13 — tabela „Ocekivane vrednosti". Tabela je zadata u velicini PROZORA,
 * pa test zadaje i hrom koji formula oduzima. Te vrednosti spec ne navodi; ovde su
 * one koje uz formulu daju tabelu (i odgovaraju stvarnom hromu):
 *  - Pixel Tablet: chromeTop 88 (status 24 + traka 64), chromeBottom 80 (donja
 *    traka), railWidth 80 u pejzazu (M3 `NavigationRail` od 840 dp).
 *  - iPad: chromeTop 98, chromeBottom 20. 98 je vrednost iz koje formula daje 688
 *    na iPad-u 11" u pejzazu (834 − 98 − 48); iPad Pro 13" daje 800 za svaki
 *    chromeTop <= 184.
 * Isti brojevi su u iOS `BoardSizingTests.swift`.
 */
class BoardSizingTest {

    private fun side(
        w: Float, h: Float, tablet: Boolean = true, landscape: Boolean,
        top: Float = 0f, bottom: Float = 0f, rail: Float = 0f, phoneReserve: Float = 0f
    ) = BoardSizing.boardSide(w, h, tablet, landscape, top, bottom, rail, phoneReserve)

    @Test
    fun pixelTabletPortraitIs760AndLandscapeIs664() {
        assertEquals(760f, side(800f, 1280f, landscape = false, top = 88f, bottom = 80f), 0f)
        assertEquals(664f, side(1280f, 800f, landscape = true, top = 88f, rail = 80f), 0f)
    }

    @Test
    fun iPadPro13Is800BothWays() {
        assertEquals(800f, side(1032f, 1376f, landscape = false, top = 98f, bottom = 20f), 0f)
        assertEquals(800f, side(1376f, 1032f, landscape = true, top = 98f), 0f)
    }

    @Test
    fun iPad11Is794PortraitAnd688Landscape() {
        assertEquals(794f, side(834f, 1194f, landscape = false, top = 98f, bottom = 20f), 0f)
        assertEquals(688f, side(1194f, 834f, landscape = true, top = 98f), 0f)
    }

    @Test
    fun phoneNeverExceeds560AndLandscapeStaysBoundByHeight() {
        assertEquals(379f, side(411f, 891f, tablet = false, landscape = false), 0f)
        assertEquals(BoardSizing.PHONE_MAX_SIDE, side(700f, 1000f, tablet = false, landscape = false), 0f)
        // Bez granice po visini formula iz spec-a bi dala 560 na ekranu visokom ~360.
        assertEquals(360f, side(891f, 360f, tablet = false, landscape = true), 0f)
    }

    @Test
    fun stepPhoneReserveKeepsOldGuard() {
        assertEquals(328f, side(375f, 548f, tablet = false, landscape = false, phoneReserve = 220f), 0f)
        assertEquals(379f, side(411f, 800f, tablet = false, landscape = false, phoneReserve = 220f), 0f)
    }

    @Test
    fun tabletNeverExceeds800AndNeverGoesNegative() {
        assertEquals(BoardSizing.TABLET_MAX_SIDE, side(4000f, 4000f, landscape = false), 0f)
        assertEquals(BoardSizing.TABLET_MAX_SIDE, side(4000f, 4000f, landscape = true), 0f)
        assertEquals(0f, side(100f, 100f, landscape = true), 0f)
    }

    @Test
    fun tabletWindowIsWide_andPhoneLandscapeIsNot() {
        assertTrue(BoardSizing.isTabletWindow(800, 1280))
        assertTrue(BoardSizing.isTabletWindow(1280, 800))
        assertFalse(BoardSizing.isTabletWindow(411, 891))
        assertFalse(BoardSizing.isTabletWindow(891, 411)) // telefon u pejzazu
        assertFalse(BoardSizing.isTabletWindow(599, 900))
    }

    @Test
    fun designSystemCapsComeFromBoardSizing() {
        assertEquals(BoardSizing.PHONE_MAX_SIDE, DS.maxBoardSide.value, 0f)
        assertEquals(BoardSizing.TABLET_MAX_SIDE, DS.maxBoardSideTablet.value, 0f)
    }
}
