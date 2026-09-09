import Testing
import Foundation
@testable import ChesskoEngine

@Test func decodesAllStepKinds() throws {
    let json = """
    {
      "version": 1,
      "chapters": [
        {
          "id": "basics",
          "title": { "sr": "Osnove", "en": "Basics" },
          "steps": [
            { "id": "b-lesson", "type": "lesson", "lessonId": "board-and-pieces" },
            { "id": "b-practice", "type": "practice", "themes": ["mateIn1"],
              "count": 5, "ratingRange": [600, 1000] },
            { "id": "b-game", "type": "game", "difficulty": "beginner", "startFEN": null },
            { "id": "b-test", "type": "test", "themes": ["fork"],
              "count": 3, "ratingRange": [600, 1200] }
          ]
        }
      ]
    }
    """
    let c = try JSONDecoder().decode(Curriculum.self, from: Data(json.utf8))
    #expect(c.version == 1)
    #expect(c.chapters.count == 1)
    #expect(c.chapters[0].title["sr"] == "Osnove")

    let steps = c.chapters[0].steps
    #expect(steps.count == 4)
    guard case .lesson(let lessonId) = steps[0].kind else {
        Issue.record("korak 0 nije lesson"); return
    }
    #expect(lessonId == "board-and-pieces")

    guard case .practice(let themes, let count, let range) = steps[1].kind else {
        Issue.record("korak 1 nije practice"); return
    }
    #expect(themes == ["mateIn1"])
    #expect(count == 5)
    #expect(range == 600...1000)

    guard case .game(let difficulty, let fen) = steps[2].kind else {
        Issue.record("korak 2 nije game"); return
    }
    #expect(difficulty == "beginner")
    #expect(fen == nil)

    guard case .test = steps[3].kind else {
        Issue.record("korak 3 nije test"); return
    }
}

@Test func unknownStepTypeThrows() {
    let json = """
    { "version": 1, "chapters": [ { "id": "c", "title": {"sr":"C","en":"C"},
      "steps": [ { "id": "x", "type": "izmisljeno" } ] } ] }
    """
    #expect(throws: (any Error).self) {
        try JSONDecoder().decode(Curriculum.self, from: Data(json.utf8))
    }
}

@Test func invertedRatingRangeThrowsInsteadOfTrapping() {
    // `ClosedRange` sa donjom granicom vecom od gornje RUSI aplikaciju u
    // run-time-u. Dekoder mora da odbije takav JSON, ne da propusti pa da
    // pukne kasnije. (Ista klasa greske ispravljena u Fazi 2, Task 5.)
    let json = """
    { "version": 1, "chapters": [ { "id": "c", "title": {"sr":"C","en":"C"},
      "steps": [ { "id": "x", "type": "practice", "themes": ["fork"],
                   "count": 3, "ratingRange": [1200, 600] } ] } ] }
    """
    #expect(throws: (any Error).self) {
        try JSONDecoder().decode(Curriculum.self, from: Data(json.utf8))
    }
}

@Test @MainActor func realCurriculumIsConsistentWithLessonsAndPuzzleDatabase() throws {
    let url = URL(fileURLWithPath: "Chessko/Content/curriculum.json")
    let curriculum = try JSONDecoder().decode(Curriculum.self, from: Data(contentsOf: url))

    #expect(!curriculum.chapters.isEmpty)

    // Svaki id koraka je jedinstven — `ProgressStore` ih koristi kao kljuc.
    let ids = curriculum.chapters.flatMap { $0.steps.map(\.id) }
    #expect(Set(ids).count == ids.count, "duplirani id-jevi koraka: \(ids)")

    // Svako poglavlje ima naslov na sr i en.
    for chapter in curriculum.chapters {
        #expect(chapter.title["sr"]?.isEmpty == false, "\(chapter.id) nema sr naslov")
        #expect(chapter.title["en"]?.isEmpty == false, "\(chapter.id) nema en naslov")
    }

    // Svaka lekcija koju kurikulum pominje mora da postoji kao fajl.
    let lessonsDir = URL(fileURLWithPath: "Chessko/Content/lessons")
    let lessonFiles = Set(try FileManager.default
        .contentsOfDirectory(at: lessonsDir, includingPropertiesForKeys: nil)
        .map { $0.lastPathComponent })
    for chapter in curriculum.chapters {
        for step in chapter.steps {
            if case .lesson(let id) = step.kind {
                #expect(lessonFiles.contains("\(id).sr.json"),
                        "korak \(step.id) trazi lekciju '\(id)' koje nema")
            }
        }
    }

    // Svaka tema mora da postoji u bazi zadataka i da ima materijala u
    // trazenom opsegu. Bez ove provere tipfeler u temi daje korak koji se
    // NIKAD ne moze zavrsiti, a nista ne prijavi gresku.
    let repo = PuzzleRepository(databaseURL: URL(fileURLWithPath: "Chessko/puzzles.sqlite"))
    #expect(repo != nil)
    for chapter in curriculum.chapters {
        for step in chapter.steps {
            let (themes, count, range): ([String], Int, ClosedRange<Int>)
            switch step.kind {
            case .practice(let t, let c, let r), .test(let t, let c, let r):
                (themes, count, range) = (t, c, r)
            default:
                continue
            }
            let found = repo?.puzzles(themes: themes, ratingRange: range,
                                      excluding: [], limit: count * 4) ?? []
            #expect(found.count >= count,
                    "korak \(step.id): teme \(themes) u opsegu \(range) daju \(found.count) zadataka, treba bar \(count)")
        }
    }
}

@Test func unknownGameDifficultyThrows() {
    // "begginer" bi inace prosao svaki test i ispao tek kao korak koji se ne
    // moze odigrati — isto kao tipfeler u imenu lekcije ili teme.
    let json = """
    { "version": 1, "chapters": [ { "id": "c", "title": {"sr":"C","en":"C"},
      "steps": [ { "id": "x", "type": "game", "difficulty": "begginer" } ] } ] }
    """
    #expect(throws: (any Error).self) {
        try JSONDecoder().decode(Curriculum.self, from: Data(json.utf8))
    }
}

@Test func singleValueRatingRangeIsAccepted() {
    let json = """
    { "version": 1, "chapters": [ { "id": "c", "title": {"sr":"C","en":"C"},
      "steps": [ { "id": "x", "type": "practice", "themes": ["fork"],
                   "count": 1, "ratingRange": [800, 800] } ] } ] }
    """
    let c = try? JSONDecoder().decode(Curriculum.self, from: Data(json.utf8))
    guard case .practice(_, _, let r)? = c?.chapters[0].steps[0].kind else {
        Issue.record("nije dekodirano kao practice"); return
    }
    #expect(r == 800...800)
}

// `encode(to:)` ne koristi nijedan drugi test. Cetiri kasnija zadatka zavise od
// ovog oblika, pa bi izmena koja rasklopi par kljuceva (dekodira se pod jednim
// imenom, kodira pod drugim) isplivala tek daleko od uzroka.
@Test func everyStepKindSurvivesEncodeDecodeRoundTrip() throws {
    let original = try JSONDecoder().decode(
        Curriculum.self,
        from: Data(contentsOf: URL(fileURLWithPath: "Chessko/Content/curriculum.json")))
    let back = try JSONDecoder().decode(Curriculum.self,
                                        from: JSONEncoder().encode(original))
    #expect(back == original)
    for (a, b) in zip(original.chapters.flatMap(\.steps), back.chapters.flatMap(\.steps)) {
        #expect(a == b, "korak \(a.id) se ne vraca isti kroz enkodiranje")
    }
}
