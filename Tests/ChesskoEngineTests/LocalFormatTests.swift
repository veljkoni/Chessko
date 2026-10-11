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
    for base in ["dana zaredom", "%lld mogućih poteza"] {
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
