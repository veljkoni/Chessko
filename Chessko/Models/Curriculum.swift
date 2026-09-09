import Foundation

// MARK: - Kurikulum (Put)
//
// Okosnica v2: niz koraka koje igrac prolazi redom. Zivi u
// `Chessko/Content/curriculum.json`, van koda, kao i lekcije.
//
// Kao i kod `LessonBlock`-a, nepoznat `type` koraka BACA gresku umesto da se
// tiho preskoci — preskocen korak bi napravio rupu u putu koju niko ne vidi.

struct Curriculum: Codable, Equatable {
    let version: Int
    let chapters: [Chapter]

    /// Redosled svih koraka kroz sva poglavlja. `ProgressStore` ga koristi za
    /// otkljucavanje, a ekran Puta za redni broj koraka.
    var allStepIds: [String] { chapters.flatMap { $0.steps.map(\.id) } }

    func step(id: String) -> CurriculumStep? {
        chapters.lazy.flatMap(\.steps).first { $0.id == id }
    }
}

struct Chapter: Codable, Equatable {
    let id: String
    /// Naslov po jeziku. Kurikulum je mali, pa naslovi stoje ovde umesto u
    /// odvojenim fajlovima po jeziku kao kod lekcija.
    let title: [String: String]
    let steps: [CurriculumStep]
}

enum StepKind: Equatable {
    /// Teorija; zavrsava se kad korisnik dodje do kraja i potvrdi.
    case lesson(lessonId: String)
    /// N zadataka filtriranih po temi; zavrsava se kad su svi reseni.
    case practice(themes: [String], count: Int, ratingRange: ClosedRange<Int>)
    /// Partija protiv racunara; zavrsava se kad partija dodje do kraja.
    case game(difficulty: String, startFEN: String?)
    /// Kao `practice`, ali se zavrsava SAMO bez ijedne greske — zakljucava poglavlje.
    case test(themes: [String], count: Int, ratingRange: ClosedRange<Int>)
}

struct CurriculumStep: Codable, Equatable {
    let id: String
    let kind: StepKind

    private enum CodingKeys: String, CodingKey {
        case id, type, lessonId, themes, count, ratingRange, difficulty, startFEN
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        let type = try c.decode(String.self, forKey: .type)

        /// `ClosedRange` sa donjom granicom vecom od gornje RUSI proces pri
        /// kreiranju. Zato se opseg proverava ovde, gde greska jos moze da se
        /// prijavi kao neispravan JSON.
        func range(_ raw: [Int]) throws -> ClosedRange<Int> {
            guard raw.count == 2, raw[0] <= raw[1] else {
                throw DecodingError.dataCorruptedError(
                    forKey: .ratingRange, in: c,
                    debugDescription: "ratingRange mora biti [donja, gornja] sa donja <= gornja, dobijeno \(raw)")
            }
            return raw[0]...raw[1]
        }

        switch type {
        case "lesson":
            self.kind = .lesson(lessonId: try c.decode(String.self, forKey: .lessonId))
        case "practice":
            self.kind = .practice(themes: try c.decode([String].self, forKey: .themes),
                                  count: try c.decode(Int.self, forKey: .count),
                                  ratingRange: try range(c.decode([Int].self, forKey: .ratingRange)))
        case "test":
            self.kind = .test(themes: try c.decode([String].self, forKey: .themes),
                              count: try c.decode(Int.self, forKey: .count),
                              ratingRange: try range(c.decode([Int].self, forKey: .ratingRange)))
        case "game":
            self.kind = .game(difficulty: try c.decode(String.self, forKey: .difficulty),
                              startFEN: try c.decodeIfPresent(String.self, forKey: .startFEN))
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type, in: c,
                debugDescription: "Nepoznat tip koraka '\(type)'. Dodaj ga u StepKind ili ispravi JSON.")
        }
    }

    func encode(to encoder: any Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        switch kind {
        case .lesson(let lessonId):
            try c.encode("lesson", forKey: .type)
            try c.encode(lessonId, forKey: .lessonId)
        case .practice(let themes, let count, let r):
            try c.encode("practice", forKey: .type)
            try c.encode(themes, forKey: .themes); try c.encode(count, forKey: .count)
            try c.encode([r.lowerBound, r.upperBound], forKey: .ratingRange)
        case .test(let themes, let count, let r):
            try c.encode("test", forKey: .type)
            try c.encode(themes, forKey: .themes); try c.encode(count, forKey: .count)
            try c.encode([r.lowerBound, r.upperBound], forKey: .ratingRange)
        case .game(let difficulty, let startFEN):
            try c.encode("game", forKey: .type)
            try c.encode(difficulty, forKey: .difficulty)
            try c.encodeIfPresent(startFEN, forKey: .startFEN)
        }
    }
}
