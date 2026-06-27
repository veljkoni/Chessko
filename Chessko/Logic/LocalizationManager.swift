import SwiftUI
import ObjectiveC

// MARK: - Bundle language override (live in-app language switching)
//
// Text("…") and String(localized:"…") ultimately call
// Bundle.main.localizedString(forKey:value:table:). By swapping Bundle.main's
// class to a subclass that forwards lookups to a chosen `<code>.lproj` bundle,
// we can switch language at runtime without an app restart.

// Only ever used for its stable address as an associated-object key.
nonisolated(unsafe) private var bundleAssocKey: UInt8 = 0

private final class LocalizedBundle: Bundle, @unchecked Sendable {
    override func localizedString(forKey key: String, value: String?, table tableName: String?) -> String {
        if let override = objc_getAssociatedObject(self, &bundleAssocKey) as? Bundle {
            return override.localizedString(forKey: key, value: value, table: tableName)
        }
        return super.localizedString(forKey: key, value: value, table: tableName)
    }
}

extension Bundle {
    /// Routes Bundle.main string lookups to `<language>.lproj` (nil = system default).
    static func setAppLanguage(_ language: String?) {
        if !(Bundle.main is LocalizedBundle) {
            object_setClass(Bundle.main, LocalizedBundle.self)
        }
        let override: Bundle?
        if let language,
           let path = Bundle.main.path(forResource: language, ofType: "lproj"),
           let b = Bundle(path: path) {
            override = b
        } else {
            override = nil   // follow system
        }
        objc_setAssociatedObject(Bundle.main, &bundleAssocKey, override,
                                 .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
    }
}

// MARK: - Supported languages

struct AppLanguage: Identifiable, Hashable, Sendable {
    let code: String      // e.g. "en", "zh-Hans"
    let endonym: String   // displayed in its own language
    var id: String { code }

    /// Order shown in the settings sheet. Must match Localizable.xcstrings locales.
    static let all: [AppLanguage] = [
        AppLanguage(code: "sr",      endonym: "Srpski"),
        AppLanguage(code: "en",      endonym: "English"),
        AppLanguage(code: "fr",      endonym: "Français"),
        AppLanguage(code: "de",      endonym: "Deutsch"),
        AppLanguage(code: "it",      endonym: "Italiano"),
        AppLanguage(code: "ru",      endonym: "Русский"),
        AppLanguage(code: "zh-Hans", endonym: "中文 (简体)"),
        AppLanguage(code: "hi",      endonym: "हिन्दी"),
    ]
}

// MARK: - Localization manager

@Observable
@MainActor
final class LocalizationManager {
    static let shared = LocalizationManager()

    private let key = "chessko.appLanguage"

    /// Selected language code, or nil to follow the system language.
    private(set) var languageCode: String?

    private init() {
        languageCode = UserDefaults.standard.string(forKey: key)
        Bundle.setAppLanguage(effectiveCode)
    }

    /// The language actually applied: the explicit choice, or — when following
    /// the system — the best supported match, falling back to English if the
    /// device language isn't one we ship.
    var effectiveCode: String {
        languageCode ?? Self.systemFallback()
    }

    /// Best supported language for the current device, or "en" if none match.
    /// Serbian is intentionally excluded — it's offered only on explicit request.
    private static func systemFallback() -> String {
        let supported = Set(AppLanguage.all.map(\.code)).subtracting(["sr"])
        for pref in Locale.preferredLanguages {
            if pref.hasPrefix("zh"), supported.contains("zh-Hans") { return "zh-Hans" }
            if supported.contains(pref) { return pref }
            let base = String(pref.prefix(while: { $0 != "-" }))
            if supported.contains(base) { return base }
        }
        return "en"
    }

    /// Locale driving SwiftUI formatting (dates, numbers).
    var locale: Locale {
        Locale(identifier: effectiveCode)
    }

    /// Identity used to rebuild the UI tree so every Text re-resolves on change.
    var refreshID: String { languageCode ?? "system" }

    func setLanguage(_ code: String?) {
        languageCode = code
        if let code { UserDefaults.standard.set(code, forKey: key) }
        else { UserDefaults.standard.removeObject(forKey: key) }
        Bundle.setAppLanguage(effectiveCode)
    }
}

// MARK: - Localized lookup helpers
//
// These route through Bundle.main.localizedString(...), which IS intercepted by
// the LocalizedBundle swizzle above — unlike Foundation's String(localized:),
// which bypasses it and always reads the process language. Use Loc/LocF for any
// String value that must follow the in-app language (status text, accessibility,
// piece names, themes, dates, …). The key is the Serbian source string.

/// Localized string for `key`; falls back to the key itself if missing.
func Loc(_ key: String) -> String {
    Bundle.main.localizedString(forKey: key, value: key, table: nil)
}

/// Localized format string filled with `args`, e.g. LocF("%lld poteza", n).
func LocF(_ key: String, _ args: CVarArg...) -> String {
    String(format: Bundle.main.localizedString(forKey: key, value: key, table: nil),
           arguments: args)
}
