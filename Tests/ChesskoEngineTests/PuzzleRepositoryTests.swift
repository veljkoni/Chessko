import Testing
import Foundation
@testable import ChesskoEngine

// Baza `Chessko/puzzles.sqlite` se otvara po relativnoj putanji iz korena
// repozitorijuma — `swift test` se pokreće iz korena, pa je ova putanja
// stabilna nezavisno od radnog direktorijuma test runnera.
@MainActor
private func openTestRepository() -> PuzzleRepository {
    let url = URL(fileURLWithPath: "Chessko/puzzles.sqlite")
    guard let repo = PuzzleRepository(databaseURL: url) else {
        Issue.record("Baza zadataka nije pronadjena na \(url.path) — pokreni `swift test` iz korena repozitorijuma.")
        fatalError("PuzzleRepository init failed")
    }
    return repo
}

@Test @MainActor func repositoryOpensAndReportsExpectedCount() {
    let repo = openTestRepository()
    #expect(repo.count >= 15_000)
}

@Test @MainActor func dailyPuzzleIsDeterministicForSameDateAndVariesAcrossDates() {
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

@Test @MainActor func themeFilterOnlyMatchesExactThemeNotSubstring() {
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

@Test @MainActor func excludingSetIsRespected() {
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

// Validira CELU bazu, ne uzorak: los red bi se u nasumicnom uzorku od 500
// od 20 000 redova pojavio u ~2.5% pokretanja, sto znaci da bi test prakticno
// uvek prolazio i pored pokvarenih podataka. Prolaz je deterministican
// (`ORDER BY id`), a prvi neispravan red se prijavljuje po `id`-ju.
@Test @MainActor func everyPuzzleInDatabaseHasValidFenAndAtLeastOneMove() {
    let repo = openTestRepository()

    let all = repo.allPuzzlesOrderedById()
    // Ako `puzzleFromRow` odbije red (NULL kolona), on tiho ispada iz liste —
    // poredjenje sa COUNT(*) hvata i taj slucaj.
    #expect(all.count == repo.count, "Ocitano \(all.count) redova, baza ima \(repo.count)")
    #expect(all.count >= 15_000)

    var invalid: [String] = []
    for puzzle in all {
        if puzzle.fen.isEmpty || puzzle.uciMoves.isEmpty || GameState.fromFEN(puzzle.fen) == nil {
            invalid.append(puzzle.puzzleId)
            if invalid.count >= 5 { break }
        }
    }
    #expect(invalid.isEmpty, "Neispravan FEN ili prazna lista poteza kod zadataka: \(invalid)")
}

@Test @MainActor func puzzleByIdRoundTripsAndUnknownIdReturnsNil() {
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

@Test @MainActor func databaseURLThatDoesNotExistFailsToInit() {
    let bogus = URL(fileURLWithPath: "Chessko/does-not-exist.sqlite")
    #expect(PuzzleRepository(databaseURL: bogus) == nil)
}
