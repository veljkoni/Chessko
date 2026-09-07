import SwiftUI

// MARK: - Dizajn tokeni
//
// Jedan izvor istine za boje, tipografiju i razmake.
// Pravac „Tiho i precizno" (spec 5.6): neutralne podloge, jedan uzdržan
// akcent izveden iz #17234f, tabla je jedina zasićena stvar na ekranu.
//
// Akcent je FIKSAN — ne menja se sa temom table. Boje vezane za tablu
// (polja, poslednji potez, legalni potezi, šah) i dalje dolaze iz BoardTheme.

extension Color {
    /// Boja koja se sama razrešava po svetloj/tamnoj temi.
    static func adaptive(light: String, dark: String) -> Color {
        Color(UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(Color(hex: dark))
                : UIColor(Color(hex: light))
        })
    }
}

enum DS {

    // MARK: Boje

    static let accent   = Color.adaptive(light: "#2E4A8A", dark: "#7EA0E8")
    static let ground   = Color.adaptive(light: "#F2F3F7", dark: "#0E1428")
    static let surface  = Color.adaptive(light: "#FFFFFF", dark: "#161D33")
    static let navBar   = Color.adaptive(light: "#FFFFFF", dark: "#131A2E")
    static let fill     = Color.adaptive(light: "#E7EAF1", dark: "#1E2740")
    static let line     = Color.adaptive(light: "#DFE3EC", dark: "#232C46")
    static let ink      = Color.adaptive(light: "#161A22", dark: "#EEF1F7")
    static let inkMuted = Color.adaptive(light: "#6B7280", dark: "#8B93A7")

    /// Semantičke boje — nose značenje, nisu ukras. Ostaju u boji i u
    /// dizajnu koji je inače neutralan.
    static let success = Color.adaptive(light: "#2F7D4F", dark: "#6FCF97")
    static let warning = Color.adaptive(light: "#B07D2B", dark: "#E0B252")
    static let danger  = Color.adaptive(light: "#B3261E", dark: "#F2857A")

    /// Zatamnjenje ispod modalnih preklopa (promocija, izbor boje, kraj
    /// partije). Namerno isto u obe teme — preklop je uvek taman.
    static let scrim   = Color.black.opacity(0.55)
    /// Tekst i ikone NA scrim-u. Namerno bela u obe teme.
    static let onScrim = Color.white
    /// Taman tekst zakovan tako da se NE invertuje u tamnoj temi.
    /// Za površine koje same ne prate temu: beli taster na scrim-u, čip u boji
    /// upozorenja, i slično — tamo `DS.ink` ne valja jer bi u tamnoj temi
    /// postao skoro beo na svetloj podlozi. Vrednost je svetla varijanta `ink`-a.
    static let inkFixed = Color(hex: "#161A22")

    /// Tekst i ikone NA `DS.accent` podlozi. MORA da bude adaptivan, za razliku
    /// od `onScrim`-a: `accent` menja svetlinu izmedju tema (taman `#2E4A8A` u
    /// svetloj, svetao `#7EA0E8` u tamnoj), pa nijedna fiksna boja teksta ne
    /// radi u obe. Bela na tamnoj varijanti daje 8.5:1, ali na svetloj samo
    /// 2.6:1 — ispod AA. Tamno mastilo na svetloj varijanti daje 6.7:1.
    static let onAccent = Color.adaptive(light: "#FFFFFF", dark: "#161A22")

    // MARK: Razmaci

    enum Space {
        static let xs: CGFloat = 4
        static let s:  CGFloat = 8
        static let m:  CGFloat = 12
        static let l:  CGFloat = 16
        static let xl: CGFloat = 24
    }

    /// Najveca stranica table. Na iPhone-u se nikad ne dostigne; na iPad-u
    /// sprecava da tabla proguta ceo ekran i da raspored izgleda kao uvecan telefon.
    static let maxBoardSide: CGFloat = 560

    // MARK: Radijusi

    enum Radius {
        static let s: CGFloat = 8
        static let m: CGFloat = 12
        static let l: CGFloat = 16
    }
}

// MARK: - Tipografska skala
//
// Sve, osim dsMono, ide kroz Font.appFont da bi se zadržale uvećane veličine
// na Mac-u (vidi Chessko/Logic/PlatformHelper.swift). dsMono je izuzetak jer
// appFont nema design: parametar potreban za monospaced cifre.

extension Font {
    /// Naslov ekrana.
    static var dsTitle: Font { .appFont(.title2).weight(.bold) }
    /// Naslov sekcije ili kartice.
    static var dsHeading: Font { .appFont(.headline) }
    /// Osnovni tekst.
    static var dsBody: Font { .appFont(.subheadline) }
    /// Prigušen, sitan tekst — podnaslovi, oznake.
    static var dsCaption: Font { .appFont(.caption) }
    /// Monospaced cifre malog obima — trenutno rejting bedž na ekranu Zadaci.
    /// Sat, eval traka i istorija poteza imaju sopstvene fontove, ne ovaj.
    static var dsMono: Font { .system(.footnote, design: .monospaced) }
}
