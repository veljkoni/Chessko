import Foundation

// MARK: - Lesson Repository
//
// Ucitava lekcije iz `Content/lessons/<id>.<lang>.json` u bundle-u. `Content`
// je u projektu FOLDER-REFERENCA, pa se cela struktura direktorijuma prenosi u
// .app — nova lekcija ne trazi izmenu `project.pbxproj`.
//
// `@MainActor` iz istog razloga kao `PuzzleRepository`: kes nije zasticen, a
// ceo pozivni graf je ionako na glavnoj niti. Ovako je pozadinski pozivalac
// greska pri kompajliranju, a ne tiha trka.

@MainActor
final class LessonRepository {
    static let shared = LessonRepository()

    /// Redosled kojim se lekcije prikazuju na ekranu Učenje.
    static let lessonOrder = ["board-and-pieces", "openings", "middlegame", "endgame"]

    private var cache: [String: LessonDocument] = [:]

    /// Lekcija na trazenom jeziku. Ako tog jezika nema (nove lekcije idu samo
    /// sr+en), pada na engleski pa na srpski — bolje lekcija na drugom jeziku
    /// nego prazan ekran.
    func lesson(id: String, language: String) -> LessonDocument? {
        for candidate in [language, "en", "sr"] {
            let key = "\(id).\(candidate)"
            if let cached = cache[key] { return cached }
            guard let url = Bundle.main.url(forResource: key, withExtension: "json",
                                            subdirectory: "Content/lessons"),
                  let data = try? Data(contentsOf: url),
                  let doc = try? JSONDecoder().decode(LessonDocument.self, from: data)
            else { continue }
            cache[key] = doc
            return doc
        }
        return nil
    }

    /// Sve lekcije redom, na trazenom jeziku. Koristi ekran Učenje za listu.
    func allLessons(language: String) -> [LessonDocument] {
        Self.lessonOrder.compactMap { lesson(id: $0, language: language) }
    }
}
