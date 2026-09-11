import Foundation

// MARK: - UCI parser ocene
//
// Odvojen od `StockfishBridge`-a namerno: `StockfishBridge` je `actor` koji
// uvozi ChessKitEngine i ne moze u SwiftPM testni paket, a citanje ocene iz
// linije teksta je cista funkcija koja se mora testirati. Granica je tacno na
// mestu gde prestaje rad sa motorom, a pocinje rad sa stringom.
//
// FORMAT NIJE PRETPOSTAVLJEN NEGO IZMEREN. Prva verzija ovog parsera trazila je
// `<score> cp 34` i vracala `nil` na svakoj stvarnoj liniji. Pravi oblik je
// dobijen kompajliranjem i pokretanjem same biblioteke (ChessKitEngine 0.7.0,
// `EngineResponseInfo.swift:264` gradi `" <cp> \(cp)"`, a `cp` je `Double?`):
//
//     <info> <depth> 12 <score> <cp> 34.0
//     <info> <score> <cp> -250.0
//     <info> <score> <mate> 3
//     <info> <score> <cp> 34.0 <upperbound>
//
// Dakle: tag posle `<score>` nosi UGLASTE ZAGRADE, `cp` stize kao decimalan
// broj, a `<lowerbound>`/`<upperbound>` su zasebni tagovi bez vrednosti.

enum UCIScoreParser {

    /// Cita ocenu iz jedne UCI `info` linije, u tagovanom formatu koji vraca
    /// `ChessKitEngine`.
    ///
    /// Trazi token `<score>` pa cita tagove iza njega, umesto da se oslanja na
    /// fiksnu poziciju — redosled polja u `info` liniji nije garantovan i menja
    /// se od pretrage do pretrage (motor salje `<pv>` samo ponekad, `<seldepth>`
    /// samo na vecim dubinama).
    static func score(from line: String) -> EngineScore? {
        let tokens = line.split(separator: " ")
        guard let scoreIdx = tokens.firstIndex(of: "<score>") else { return nil }

        var i = tokens.index(after: scoreIdx)
        while i < tokens.endIndex {
            let next = tokens.index(after: i)
            switch tokens[i] {
            case "<cp>":
                // `cp` je u biblioteci `Double`, pa stize kao "34.0". `Int(...)`
                // bi na tome vratio nil — zato Double pa zaokruzivanje.
                guard next < tokens.endIndex, let v = Double(tokens[next]) else { return nil }
                return .cp(Int(v.rounded()))
            case "<mate>":
                guard next < tokens.endIndex, let v = Int(tokens[next]) else { return nil }
                return .mate(v)
            case "<lowerbound>", "<upperbound>":
                // Tagovi bez vrednosti. U praksi stoje IZA ocene, pa se dovde ne
                // stigne; preskacu se zbog redosleda na koji se ne oslanjamo.
                i = next
            default:
                // Izasli smo iz `<score>` bloka a da nismo nasli ni cp ni mate.
                return nil
            }
        }
        return nil
    }
}
