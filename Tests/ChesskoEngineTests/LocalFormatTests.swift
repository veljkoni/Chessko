import Foundation
import Testing
@testable import ChesskoEngine

// Množine i lokalni format brojeva (spec § 3, § 16). Pravila su ista kao
// Android `PluralRulesTest` — ako se jedna strana promeni, druga mora isto.

@Test func serbianPluralCategories() {
    let expected: [Int: PluralCategory] = [
        0: .other, 1: .one, 2: .few, 4: .few, 5: .other, 11: .other, 12: .other,
        14: .other, 21: .one, 22: .few, 25: .other, 101: .one, 111: .other, 112: .other
    ]
    for (n, c) in expected {
        #expect(PluralRules.category(languageCode: "sr", n: n) == c, "sr \(n)")
    }
}

@Test func russianUsesManyWhereSerbianUsesOther() {
    #expect(PluralRules.category(languageCode: "ru", n: 1) == .one)
    #expect(PluralRules.category(languageCode: "ru", n: 3) == .few)
    #expect(PluralRules.category(languageCode: "ru", n: 5) == .many)
    #expect(PluralRules.category(languageCode: "ru", n: 11) == .many)
    #expect(PluralRules.category(languageCode: "ru", n: 0) == .many)
}

@Test func oneOtherLanguages() {
    for lang in ["en", "de", "it"] {
        #expect(PluralRules.category(languageCode: lang, n: 1) == .one)
        #expect(PluralRules.category(languageCode: lang, n: 0) == .other)
        #expect(PluralRules.category(languageCode: lang, n: 2) == .other)
    }
    for lang in ["fr", "hi"] {
        #expect(PluralRules.category(languageCode: lang, n: 0) == .one)
        #expect(PluralRules.category(languageCode: lang, n: 1) == .one)
        #expect(PluralRules.category(languageCode: lang, n: 2) == .other)
    }
    #expect(PluralRules.category(languageCode: "zh-Hans", n: 1) == .other)
}

/// Svaka osnova koju kod množi mora da ima sva četiri ključa, i svaki svih osam
/// jezika — inače `LocPlural` tiho pada na srpski ili na sam ključ.
@Test func everyPluralBaseHasAllCategoriesInCatalog() throws {
    let data = try Data(contentsOf: URL(fileURLWithPath: "Chessko/Localizable.xcstrings"))
    let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
    let strings = json["strings"] as! [String: Any]
    let langs = ["sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi"]
    // Osnove se čitaju iz IZVORA (svaki `LocPlural("…"` u `Chessko/`), isti obrazac
    // kao Android `everyLocPluralCallInTheSourceHasAnEntry` — nov poziv bez ključa
    // u katalogu obara test, bez ručnog spiska koji može da zastari.
    let bases = try pluralBasesInSource()
    #expect(bases.contains("dana zaredom"))
    #expect(bases.contains("%lld mogućih poteza"))
    for base in bases {
        for c in PluralCategory.allCases {
            let key = PluralRules.key(base, c)
            let entry = try #require(strings[key] as? [String: Any], "nema ključa \(key)")
            let locs = entry["localizations"] as? [String: Any] ?? [:]
            for l in langs { #expect(locs[l] != nil, "\(key) nema \(l)") }
        }
    }
}

@Test func decimalAndPercentFollowLanguage() {
    #expect(LocalNumber.decimal(-2.3, languageCode: "en") == "-2.3")
    #expect(LocalNumber.decimal(-2.3, languageCode: "sr").contains(","))
    #expect(LocalNumber.decimal(2.3, signed: true, languageCode: "en") == "+2.3")
    #expect(LocalNumber.percent(0, languageCode: "sr").hasPrefix("0,0"))
    #expect(LocalNumber.percent(41.9, languageCode: "en") == "41.9%")
    #expect(LocalNumber.percent(41.9, languageCode: "de").contains("41,9"))
}

/// Sve osnove prosleđene `LocPlural` kao literal, iz svih `.swift` fajlova u `Chessko/`.
private func pluralBasesInSource() throws -> Set<String> {
    let root = URL(fileURLWithPath: "Chessko")
    let regex = try NSRegularExpression(pattern: #"\bLocPlural\(\s*"((?:[^"\\]|\\.)*)""#)
    var bases = Set<String>()
    let files = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil)
    while let url = files?.nextObject() as? URL {
        guard url.pathExtension == "swift" else { continue }
        let text = try String(contentsOf: url, encoding: .utf8)
        let range = NSRange(text.startIndex..., in: text)
        for m in regex.matches(in: text, range: range) {
            if let r = Range(m.range(at: 1), in: text) { bases.insert(String(text[r])) }
        }
    }
    return bases
}

// MARK: - Procena u kartici igrača (spec § 5) — isti slučajevi kao Android `EvaluationLabelTest`

@Test func evaluationLabelSignCarriesSide() {
    let white = EvaluationLabel.make(score: 1.24, mateIn: nil, languageCode: "en")
    #expect(white == EvaluationLabel(text: "+1.2", isMate: false))
    let black = EvaluationLabel.make(score: -2.3, mateIn: nil, languageCode: "en")
    #expect(black.text == "-2.3")
    #expect(!black.isMate)
    #expect(EvaluationLabel.make(score: 1.24, mateIn: nil, languageCode: "sr").text == "+1,2")
}

@Test func evaluationLabelZeroThresholdHasNoSign() {
    #expect(EvaluationLabel.make(score: 0.04, mateIn: nil, languageCode: "en").text == "0.0")
    #expect(EvaluationLabel.make(score: -0.049, mateIn: nil, languageCode: "en").text == "0.0")
    #expect(EvaluationLabel.make(score: 0.05, mateIn: nil, languageCode: "en").text == "+0.1")
    #expect(EvaluationLabel.make(score: -0.05, mateIn: nil, languageCode: "en").text == "-0.1")
    #expect(EvaluationLabel.make(score: 0, mateIn: nil, languageCode: "sr").text == "0,0")
}

@Test func evaluationLabelMate() {
    #expect(EvaluationLabel.make(score: 0, mateIn: 1, languageCode: "en") == EvaluationLabel(text: "M1", isMate: true))
    #expect(EvaluationLabel.make(score: 99, mateIn: -2, languageCode: "en") == EvaluationLabel(text: "\u{2212}M2", isMate: true))
}
