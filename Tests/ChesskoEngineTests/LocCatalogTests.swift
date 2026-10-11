import Foundation
import Testing

// iOS blizanac Android `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`
// (dizajnerski pregled, Task 5 fix runda 1). `Loc("…")` na ključ koji ne postoji
// u `Localizable.xcstrings` tiho vraća sam ključ — srpski tekst na svih 7 stranih
// jezika, bez ikakve greške. Ovaj test čita IZVOR kao tekst: svaki string literal
// prosleđen `Loc(` ili `LocF(` mora postojati u katalogu sa svih 8 jezika.
//
// Cena (ista kao na Androidu): komentar koji ime ključa piše kao poziv (`Loc` +
// zagrada + navodnik) test čita kao živog pozivaoca. Linije koje počinju sa `//`
// se preskaču; u komentaru usred linije ime ključa piši bez `Loc` ispred.

private let languages = ["sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi"]

/// Ključevi koji namerno nisu u katalogu, sa razlogom. Danas prazno.
private let allowedMissing: [String: String] = [:]

private var repoRoot: URL {
    // Tests/ChesskoEngineTests/LocCatalogTests.swift → koren repoa
    URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
}

private func catalogLocalizations() throws -> [String: Set<String>] {
    let url = repoRoot.appendingPathComponent("Chessko/Localizable.xcstrings")
    let data = try Data(contentsOf: url)
    let root = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
    let strings = try #require(root["strings"] as? [String: Any])
    var result: [String: Set<String>] = [:]
    for (key, value) in strings {
        let entry = value as? [String: Any]
        let locs = entry?["localizations"] as? [String: Any] ?? [:]
        result[key] = Set(locs.keys)
    }
    return result
}

/// Svi literali prosleđeni `Loc(`/`LocF(` u `Chessko/**/*.swift`, sa mestom.
private func locLiterals() throws -> [(key: String, place: String)] {
    let pattern = #"\bLocF?\(\s*"((?:[^"\\]|\\.)*)""#
    let regex = try NSRegularExpression(pattern: pattern)
    let sourceRoot = repoRoot.appendingPathComponent("Chessko")
    let enumerator = try #require(FileManager.default.enumerator(at: sourceRoot, includingPropertiesForKeys: nil))
    var found: [(String, String)] = []
    for case let url as URL in enumerator where url.pathExtension == "swift" {
        let text = try String(contentsOf: url, encoding: .utf8)
        for (index, line) in text.components(separatedBy: "\n").enumerated() {
            if line.trimmingCharacters(in: .whitespaces).hasPrefix("//") { continue }
            let ns = line as NSString
            for match in regex.matches(in: line, range: NSRange(location: 0, length: ns.length)) {
                let raw = ns.substring(with: match.range(at: 1))
                let key = raw
                    .replacingOccurrences(of: #"\""#, with: "\"")
                    .replacingOccurrences(of: #"\n"#, with: "\n")
                    .replacingOccurrences(of: #"\\"#, with: "\\")
                found.append((key, "\(url.lastPathComponent):\(index + 1)"))
            }
        }
    }
    return found
}

@Test func everyLocCallInTheSourceHasAKeyInTheCatalogWithAllLanguages() throws {
    let catalog = try catalogLocalizations()
    let literals = try locLiterals()
    // Bez ovoga bi pokvaren regex (0 pogodaka) dao lažno zelen test.
    #expect(literals.count > 100, "pronađeno samo \(literals.count) poziva Loc — regex je verovatno pokvaren")

    var problems: [String] = []
    for (key, place) in literals {
        if allowedMissing[key] != nil { continue }
        if key.contains(#"\("#) {
            problems.append("\(place): interpolacija u ključu \"\(key)\" — koristi LocF")
            continue
        }
        guard let langs = catalog[key] else {
            problems.append("\(place): ključ \"\(key)\" ne postoji u Localizable.xcstrings")
            continue
        }
        let missing = languages.filter { !langs.contains($0) }
        if !missing.isEmpty {
            problems.append("\(place): ključ \"\(key)\" nema jezike \(missing.joined(separator: ", "))")
        }
    }
    let report = "\(problems.count) problema:\n" + problems.joined(separator: "\n")
    #expect(problems.isEmpty, Comment(rawValue: report))
}
