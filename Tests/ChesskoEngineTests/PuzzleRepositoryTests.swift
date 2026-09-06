import Testing
import Foundation
@testable import ChesskoEngine

// Baza `Chessko/puzzles.sqlite` se otvara po relativnoj putanji iz korena
// repozitorijuma — `swift test` se pokreće iz korena, pa je ova putanja
// stabilna nezavisno od radnog direktorijuma test runnera.
private func openTestRepository() -> PuzzleRepository {
    let url = URL(fileURLWithPath: "Chessko/puzzles.sqlite")
    guard let repo = PuzzleRepository(databaseURL: url) else {
        Issue.record("Baza zadataka nije pronadjena na \(url.path) — pokreni `swift test` iz korena repozitorijuma.")
        fatalError("PuzzleRepository init failed")
    }
    return repo
}

@Test func repositoryOpensAndReportsExpectedCount() {
    let repo = openTestRepository()
    #expect(repo.count >= 15_000)
}

@Test func dailyPuzzleIsDeterministicForSameDateAndVariesAcrossDates() {
    let repo = openTestRepository()

    let day1 = Date(timeIntervalSince1970: 1_700_000_000) // fiksan datum
    let day2 = Date(timeIntervalSince1970: 1_700_000_000 + 86_400 * 5)

    let first = repo.dailyPuzzle(for: day1)
    let second = repo.dailyPuzzle(for: day1)
    #expect(first != nil)
    #expect(first?.puzzleId == second?.puzzleId)

    let other = repo.dailyPuzzle(for: day2)
    #expect(other != nil)
    #expect(other?.puzzleId != first?.puzzleId)
}

@Test func themeFilterOnlyMatchesExactThemeNotSubstring() {
    let repo = openTestRepository()

    let forkPuzzles = repo.puzzles(themes: ["fork"], ratingRange: 600...2200, excluding: [], limit: 50)
    #expect(!forkPuzzles.isEmpty)
    for puzzle in forkPuzzles {
        #expect(puzzle.themeList.contains("fork"))
    }

    // "mate" ne sme da pogodi "mateIn2" kroz razlaganje tema (IN poredi ceo string).
    let matePuzzles = repo.puzzles(themes: ["mate"], ratingRange: 600...2200, excluding: [], limit: 200)
    for puzzle in matePuzzles {
        #expect(puzzle.themeList.contains("mate"))
        #expect(!(puzzle.themeList.contains("mateIn2") && !puzzle.themeList.contains("mate")))
    }
}

@Test func excludingSetIsRespected() {
    let repo = openTestRepository()

    guard let sample = repo.randomPuzzle(ratingRange: 600...2200, excluding: []) else {
        Issue.record("Ocekivan bar jedan zadatak")
        return
    }

    let excluded: Set<String> = [sample.puzzleId]
    let results = repo.puzzles(themes: [], ratingRange: 600...2200, excluding: excluded, limit: 500)
    #expect(!results.contains { $0.puzzleId == sample.puzzleId })

    let anotherRandom = repo.randomPuzzle(ratingRange: 600...2200, excluding: excluded)
    #expect(anotherRandom?.puzzleId != sample.puzzleId)
}

@Test func everyReturnedPuzzleHasValidFenAndAtLeastOneMove() {
    let repo = openTestRepository()

    let sample = repo.puzzles(themes: [], ratingRange: 600...2200, excluding: [], limit: 500)
    #expect(sample.count == 500)

    for puzzle in sample {
        #expect(!puzzle.fen.isEmpty)
        #expect(!puzzle.uciMoves.isEmpty)
        let state = GameState.fromFEN(puzzle.fen)
        #expect(state != nil, "GameState.fromFEN nije uspeo za puzzle \(puzzle.puzzleId): \(puzzle.fen)")
    }
}

@Test func puzzleByIdRoundTripsAndUnknownIdReturnsNil() {
    let repo = openTestRepository()

    guard let sample = repo.randomPuzzle(ratingRange: 600...2200, excluding: []) else {
        Issue.record("Ocekivan bar jedan zadatak")
        return
    }

    let fetched = repo.puzzle(id: sample.puzzleId)
    #expect(fetched?.puzzleId == sample.puzzleId)
    #expect(fetched?.fen == sample.fen)

    #expect(repo.puzzle(id: "ovaj-id-ne-postoji-nikako") == nil)
}

@Test func databaseURLThatDoesNotExistFailsToInit() {
    let bogus = URL(fileURLWithPath: "Chessko/does-not-exist.sqlite")
    #expect(PuzzleRepository(databaseURL: bogus) == nil)
}
