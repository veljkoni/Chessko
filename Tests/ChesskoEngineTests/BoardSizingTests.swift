import Testing
import Foundation
@testable import ChesskoEngine

// Spec § 13 — tabela „Očekivane vrednosti". Tabela je zadata u veličini PROZORA,
// pa test zadaje i hrom koji formula oduzima. Te vrednosti spec ne navodi; ovde
// su one koje uz formulu daju tabelu (i odgovaraju stvarnom hromu):
//   Pixel Tablet: chromeTop 88 (status 24 + traka 64), chromeBottom 80 (donja
//     traka), railWidth 80 u pejzažu (M3 `NavigationRail` od 840 dp).
//   iPad: chromeTop 98, chromeBottom 20 (indikator). 98 je vrednost iz koje
//     formula daje 688 na iPad-u 11" u pejzažu (834 − 98 − 48); iPad Pro 13"
//     daje 800 za svaki chromeTop ≤ 184.

private func side(_ w: CGFloat, _ h: CGFloat, tablet: Bool = true, landscape: Bool,
                  top: CGFloat = 0, bottom: CGFloat = 0, rail: CGFloat = 0,
                  phoneReserve: CGFloat = 0) -> CGFloat {
    BoardSizing.boardSide(width: w, height: h, isTablet: tablet, isLandscape: landscape,
                          chromeTop: top, chromeBottom: bottom, railWidth: rail,
                          phoneHeightReserve: phoneReserve)
}

@Test func pixelTabletPortraitIs760() {
    #expect(side(800, 1280, landscape: false, top: 88, bottom: 80) == 760)
}

@Test func pixelTabletLandscapeIs664() {
    #expect(side(1280, 800, landscape: true, top: 88, rail: 80) == 664)
}

@Test func iPadPro13PortraitAndLandscapeAre800() {
    #expect(side(1032, 1376, landscape: false, top: 98, bottom: 20) == 800)
    #expect(side(1376, 1032, landscape: true, top: 98) == 800)
}

@Test func iPad11PortraitIs794AndLandscapeIs688() {
    #expect(side(834, 1194, landscape: false, top: 98, bottom: 20) == 794)
    #expect(side(1194, 834, landscape: true, top: 98) == 688)
}

@Test func phoneNeverExceeds560() {
    // iPhone 16 Pro Max (440 pt) portret, i širok „telefonski" prozor.
    #expect(side(440, 956, tablet: false, landscape: false) == 408)
    #expect(side(700, 1000, tablet: false, landscape: false) == BoardSizing.phoneMaxSide)
}

@Test func phoneLandscapeStaysBoundByHeight() {
    // Bez granice po visini formula iz spec-a bi dala 560 na ekranu visokom 390.
    #expect(side(844, 390, tablet: false, landscape: true, phoneReserve: 16) == 374)
}

@Test func phoneReserveKeepsOldStepGuard() {
    // Koraci Puta: zatečena rezerva 220 po visini i u portretu.
    #expect(side(375, 548, tablet: false, landscape: false, phoneReserve: 220) == 328)
    #expect(side(390, 760, tablet: false, landscape: false, phoneReserve: 220) == 358)
}

@Test func tabletNeverExceeds800AndNeverGoesNegative() {
    #expect(side(4000, 4000, landscape: false) == BoardSizing.tabletMaxSide)
    #expect(side(4000, 4000, landscape: true) == BoardSizing.tabletMaxSide)
    #expect(side(100, 100, landscape: true) == 0)
}
