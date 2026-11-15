import Foundation

/// Kod uređaja za igru preko interneta: 6 znakova, bez 0/1/I/L/O.
/// Isti alfabet kao `CODE_ALPHABET` na serveru.
enum OnlineCode {
    static let alphabet = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    static let length = 6

    static func generate() -> String {
        var rng = SystemRandomNumberGenerator()
        let chars = Array(alphabet)
        return String((0..<length).map { _ in chars[Int.random(in: 0..<chars.count, using: &rng)] })
    }

    /// "K7M4QX" → "K7M-4QX" (za prikaz).
    static func format(_ code: String) -> String {
        guard code.count == length else { return code }
        return String(code.prefix(3)) + "-" + String(code.suffix(3))
    }

    /// Mala slova, razmaci i crtica se prihvataju; vraća nil ako nije ispravan kod.
    static func normalize(_ input: String) -> String? {
        let cleaned = input.uppercased().filter { $0 != "-" && !$0.isWhitespace }
        guard cleaned.count == length, cleaned.allSatisfy({ alphabet.contains($0) }) else { return nil }
        return cleaned
    }
}
