import Foundation

// MARK: - Množine i brojevi po jeziku aplikacije (spec § 3, § 16)
//
// Foundation-only, pa se kompajlira i u SwiftPM paket i pokriven je testovima
// (`Tests/ChesskoEngineTests/LocalFormatTests.swift`). Android ima isti fajl,
// istih pravila: `ChesskoAndroid/.../logic/LocalFormat.kt`.
//
// Zašto sopstvena pravila a ne String Catalog plural varijante: aplikacija bira
// jezik SAMA (`LocalizationManager` zamenjuje `Bundle.main`), a `Loc()`/`LocF()`
// idu kroz `Bundle.localizedString(forKey:)`. Rešavanje `.stringsdict` varijanti
// kroz taj put zavisi od toga da li `String(format:)` dobije lokal i da li
// zamenjen bundle vrati format-proksi — što se bez simulatora ne može dokazati.
// Ovde je izbor kategorije čista funkcija sa testom, a tekst svake kategorije je
// običan ključ u katalogu (`<osnova>#one|few|many|other`). Isto pravilo, isti
// ključevi na obe platforme.

/// CLDR kategorija množine za CELE brojeve (aplikacija množi samo cele).
enum PluralCategory: String, CaseIterable, Sendable {
    case one, few, many, other
}

enum PluralRules {

    /// CLDR pravila za osam jezika aplikacije, samo celobrojna grana:
    /// - `sr`: one (…1 osim …11), few (…2–4 osim …12–14), other
    /// - `ru`: isto, ali umesto other daje many
    /// - `en`, `de`, `it`: one samo za 1
    /// - `fr`, `hi`: one za 0 i 1
    /// - `zh-Hans` (i svaki nepoznat jezik bez pravila): other
    static func category(languageCode: String, n: Int) -> PluralCategory {
        let lang = languageCode.split(separator: "-").first.map(String.init) ?? languageCode
        let a = abs(n)
        switch lang {
        case "sr", "ru":
            let m10 = a % 10, m100 = a % 100
            if m10 == 1 && m100 != 11 { return .one }
            if (2...4).contains(m10) && !(12...14).contains(m100) { return .few }
            return lang == "ru" ? .many : .other
        case "en", "de", "it":
            return a == 1 ? .one : .other
        case "fr", "hi":
            return a <= 1 ? .one : .other
        default:
            return .other
        }
    }

    /// Ključ u katalogu za datu osnovu i kategoriju: `"dana zaredom#few"`.
    static func key(_ base: String, _ category: PluralCategory) -> String {
        "\(base)#\(category.rawValue)"
    }
}

enum LocalNumber {

    /// Lokal za BROJEVE. Srpski ide na latinicu (isti razlog kao datum na Androidu,
    /// `localeForDateFormatting`) — cifre su iste, ali je ovako sigurno.
    static func locale(for languageCode: String) -> Locale {
        languageCode == "sr" ? Locale(identifier: "sr-Latn") : Locale(identifier: languageCode)
    }

    /// Decimalni broj u lokalnom formatu: „−2,3" na srpskom, „-2.3" na engleskom.
    /// `signed` dodaje „+" ispred pozitivnih (ocena pozicije).
    static func decimal(_ value: Double, fractionDigits: Int = 1,
                        signed: Bool = false, languageCode: String) -> String {
        let f = NumberFormatter()
        f.locale = locale(for: languageCode)
        f.numberStyle = .decimal
        f.minimumFractionDigits = fractionDigits
        f.maximumFractionDigits = fractionDigits
        if signed { f.positivePrefix = f.plusSign }
        return f.string(from: NSNumber(value: value)) ?? String(value)
    }

    /// Procenat iz vrednosti 0…100 („41,9 %" na srpskom, „41.9%" na engleskom).
    static func percent(_ value0to100: Double, fractionDigits: Int = 1, languageCode: String) -> String {
        let f = NumberFormatter()
        f.locale = locale(for: languageCode)
        f.numberStyle = .percent
        f.minimumFractionDigits = fractionDigits
        f.maximumFractionDigits = fractionDigits
        return f.string(from: NSNumber(value: value0to100 / 100)) ?? "\(value0to100)%"
    }
}
