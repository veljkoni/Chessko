import Foundation

// MARK: - UCI parser ocene
//
// Odvojen od `StockfishBridge`-a namerno: `StockfishBridge` je `actor` koji
// uvozi ChessKitEngine i ne moze u SwiftPM testni paket, a citanje ocene iz
// linije teksta je cista funkcija koja se mora testirati. Granica je tacno na
// mestu gde prestaje rad sa motorom, a pocinje rad sa stringom.

enum UCIScoreParser {

    /// Cita ocenu iz jedne UCI `info` linije, u tagovanom formatu koji vraca
    /// `ChessKitEngine` (`<info> <score> cp 34 …`).
    ///
    /// Trazi token `<score>` i cita DVA tokena posle njega, umesto da se
    /// oslanja na fiksnu poziciju — redosled tagova nije garantovan i menja se
    /// izmedju verzija motora.
    static func score(from line: String) -> EngineScore? {
        let tokens = line.split(separator: " ")
        guard let idx = tokens.firstIndex(of: "<score>"),
              idx + 2 < tokens.count else { return nil }

        let kind = tokens[idx + 1]
        guard let value = Int(tokens[idx + 2]) else { return nil }

        switch kind {
        case "cp":   return .cp(value)
        case "mate": return .mate(value)
        default:     return nil
        }
    }
}
