import Foundation

// MARK: - Strana table (spec § 13)
//
// JEDNA funkcija za veličinu table na svim ekranima sa tablom (Igra, Zadaci,
// koraci Puta, lekcija). Foundation-only, da je `swift test` pokrije; isti račun
// na Androidu je `logic/BoardSizing.kt` (testovi reprodukuju tabelu iz § 13 na
// obe platforme).
//
// Ekrani zovu funkciju sa veličinom SVOG prostora (GeometryReader), koji je već
// bez navigacione trake i trake tabova — zato tamo `chromeTop`/`chromeBottom`/
// `railWidth` ostaju 0. Parametri postoje da bi se tabela iz spec-a (zadata u
// veličini PROZORA) mogla reprodukovati u testu.

enum BoardSizing {
    /// Telefon (i uzak prozor na tabletu): bez promene, do 560 (= `DS.maxBoardSide`).
    static let phoneMaxSide: CGFloat = 560
    /// Tablet: do 800 — polje 100 pt, figure „do 100 pt" (spec § 13).
    static let tabletMaxSide: CGFloat = 800
    /// Eval traka pored table (spec § 13: „8 = eval traka").
    static let evalBarReserve: CGFloat = 8
    /// Panel uz tablu u pejzažu na tabletu.
    static let tabletPanelWidth: CGFloat = 400
    /// Portret na tabletu: „264 ≈ kartice + akcije".
    static let tabletPortraitReserve: CGFloat = 264

    /// Koraci Puta na telefonu: zatečena rezerva po visini (zaglavlje, uputstvo,
    /// napomena), da tabla u pejzažu ne proguta ekran.
    static let stepPhoneHeightReserve: CGFloat = 220

    // Vrednosti tokena (`DS.Space.l` / `DS.Space.xl`); `DS` živi u SwiftUI fajlu
    // koji testni paket ne kompajlira.
    static let spaceL: CGFloat = 16
    static let spaceXL: CGFloat = 24

    /// Strana table.
    ///
    /// - Parameters:
    ///   - isTablet: širina prozora ≥ 600 **i** niži prozor nije telefon u
    ///     pejzažu (iOS: `horizontalSizeClass` i `verticalSizeClass` oba `.regular`;
    ///     Android `isTabletWindow`). Split View / Slide Over / uzak prozor = telefon.
    ///   - phoneHeightReserve: samo telefon — visina koju ekran čuva za sadržaj
    ///     koji nije tabla (zatečena logika ekrana, „telefon — bez promene"): u
    ///     pejzažu je tabla uvek ograničena visinom, u portretu samo ako je
    ///     rezerva > 0 (koraci Puta, 220).
    static func boardSide(
        width: CGFloat,
        height: CGFloat,
        isTablet: Bool,
        isLandscape: Bool,
        chromeTop: CGFloat = 0,
        chromeBottom: CGFloat = 0,
        railWidth: CGFloat = 0,
        phoneHeightReserve: CGFloat = 0
    ) -> CGFloat {
        if !isTablet {
            var s = width - 2 * spaceL
            if isLandscape || phoneHeightReserve > 0 {
                // Spec piše samo `min(w − 2·Space.l, 560)`; telefon u pejzažu bi tako
                // dobio 560 na visini ~390 i prelio se. „Bez promene" = zatečena
                // granica po visini.
                s = min(s, height - chromeTop - chromeBottom - phoneHeightReserve)
            }
            return clamp(s, max: phoneMaxSide)
        }
        let s: CGFloat
        if !isLandscape {
            s = min(width - 2 * spaceL - evalBarReserve,
                    height - chromeTop - chromeBottom - tabletPortraitReserve)
        } else {
            s = min(height - chromeTop - 2 * spaceXL,
                    width - railWidth - tabletPanelWidth - 3 * spaceXL - evalBarReserve)
        }
        return clamp(s, max: tabletMaxSide)
    }

    private static func clamp(_ s: CGFloat, max upper: CGFloat) -> CGFloat {
        Swift.max(0, Swift.min(s, upper))
    }
}
