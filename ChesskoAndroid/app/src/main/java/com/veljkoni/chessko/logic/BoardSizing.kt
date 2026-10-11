package com.veljkoni.chessko.logic

// MARK: - Strana table (spec § 13)
//
// JEDNA funkcija za velicinu table na svim ekranima sa tablom (Igra, Zadaci,
// koraci Puta, lekcija). Bez ijednog `android.*` / Compose uvoza, pa je JVM test
// pokriva (`BoardSizingTest`); isti racun na iOS-u je `Chessko/Models/BoardSizing.swift`.
// Sve vrednosti su u dp.
//
// Ekrani zovu funkciju sa velicinom SVOG prostora (`BoxWithConstraints`), koji je
// vec bez sistemskih traka, donje trake i rail-a — zato tamo `chromeTop`/
// `chromeBottom`/`railWidth` ostaju 0. Parametri postoje da bi se tabela iz spec-a
// (zadata u velicini PROZORA) mogla reprodukovati u testu.

object BoardSizing {
    /** Telefon (i uzak prozor): bez promene, do 560 (= `DS.maxBoardSide`). */
    const val PHONE_MAX_SIDE = 560f
    /** Tablet: do 800 — polje 100 dp, figure „do 100" (spec § 13). */
    const val TABLET_MAX_SIDE = 800f
    /** Eval traka pored table (spec § 13: „8 = eval traka"). */
    const val EVAL_BAR_RESERVE = 8f
    /** Panel uz tablu u pejzazu na tabletu. */
    const val TABLET_PANEL_WIDTH = 400f
    /** Portret na tabletu: „264 ≈ kartice + akcije". */
    const val TABLET_PORTRAIT_RESERVE = 264f
    /** Koraci Puta na telefonu: zatecena rezerva po visini (isto kao iOS). */
    const val STEP_PHONE_HEIGHT_RESERVE = 220f

    /** Vrednosti tokena `DS.Space.l` / `DS.Space.xl` (DS je Compose, ovde se ne uvozi). */
    const val SPACE_L = 16f
    const val SPACE_XL = 24f

    /** Tablet po visini: telefon u pejzazu je >= 600 dp sirok, ali nizak (~410). */
    const val TABLET_MIN_WIDTH_DP = 600
    const val TABLET_MIN_HEIGHT_DP = 480

    /**
     * Spec § 13: tablet = sirina prozora >= 600 (M3 Medium/Expanded). Uz to visina
     * >= 480, jer je telefon u pejzazu po sirini „Expanded" — bez toga bi dobio
     * tabletni raspored (panel 400 + tabla ~290). Citati iz `LocalConfiguration`
     * (`screenWidthDp`/`screenHeightDp`), isto kao prag rail-a (`useNavigationRail`).
     */
    fun isTabletWindow(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= TABLET_MIN_WIDTH_DP && heightDp >= TABLET_MIN_HEIGHT_DP

    /**
     * Strana table u dp.
     *
     * @param phoneHeightReserve samo telefon — visina koju ekran cuva za sadrzaj
     *   koji nije tabla (zatecena logika ekrana, „telefon — bez promene"): u pejzazu
     *   je tabla uvek ogranicena visinom, u portretu samo ako je rezerva > 0.
     */
    fun boardSide(
        width: Float,
        height: Float,
        isTablet: Boolean,
        isLandscape: Boolean,
        chromeTop: Float = 0f,
        chromeBottom: Float = 0f,
        railWidth: Float = 0f,
        phoneHeightReserve: Float = 0f
    ): Float {
        if (!isTablet) {
            var s = width - 2 * SPACE_L
            if (isLandscape || phoneHeightReserve > 0f) {
                // Spec pise samo `min(w − 2·Space.l, 560)`; telefon u pejzazu bi tako
                // dobio 560 na visini ~410 i prelio se. „Bez promene" = zatecena
                // granica po visini.
                s = minOf(s, height - chromeTop - chromeBottom - phoneHeightReserve)
            }
            return s.coerceIn(0f, PHONE_MAX_SIDE)
        }
        val s = if (!isLandscape) {
            minOf(
                width - 2 * SPACE_L - EVAL_BAR_RESERVE,
                height - chromeTop - chromeBottom - TABLET_PORTRAIT_RESERVE
            )
        } else {
            minOf(
                height - chromeTop - 2 * SPACE_XL,
                width - railWidth - TABLET_PANEL_WIDTH - 3 * SPACE_XL - EVAL_BAR_RESERVE
            )
        }
        return s.coerceIn(0f, TABLET_MAX_SIDE)
    }
}
