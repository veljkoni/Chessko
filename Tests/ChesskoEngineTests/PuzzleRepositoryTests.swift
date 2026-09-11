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

// MARK: - practiceRatingWindow (Task 5)
//
// Klamp je fix za crash, ne kozmetika: bez njega bi rejting igraca ispod
// ~700 ili iznad ~2100 mogao da proizvede `ClosedRange` sa donjom granicom
// vecom od gornje, sto puca pri kreiranju (runtime trap), ne samo vraca
// prazan rezultat.

@Test func practiceRatingWindowForMidRangeRatingMatchesSpec() {
    // Spec 5.4: playerRating - 200 ... playerRating + 100, bez dodira granica baze.
    let window = PuzzleRepository.practiceRatingWindow(playerRating: 1200)
    #expect(window == 1000...1300)
}

@Test func practiceRatingWindowForRatingFarBelowFloorDoesNotTrapAndStaysValid() {
    // Naivno: (80-200)...(80+100) = -120...180 — baza pocinje od 600, prazno.
    // Klampovana samo donja granica bi dala 600...180 i PUCALA bi pri kreiranju.
    let window = PuzzleRepository.practiceRatingWindow(playerRating: 80)
    #expect(window.lowerBound <= window.upperBound)
    #expect(window.lowerBound == PuzzleRepository.minRating)
    #expect(window.upperBound >= PuzzleRepository.minRating)
}

@Test func practiceRatingWindowForRatingFarAboveCeilingDoesNotTrapAndStaysValid() {
    // Naivno: (3000-200)...(3000+100) = 2800...3100 — iznad baze u celosti.
    // `lo` (2800) je vec iznad `maxRating` (2200) pre nego sto se `hi` uopste
    // klampuje, pa formula ispravno vraca 2800...2800 (validan, ne-prazan
    // ClosedRange koji jednostavno ne pogadja nijedan red u bazi — na to se
    // oslanja progresivno prosirenje prozora u `PuzzleViewModel.nextPuzzle()`).
    // Bitno je SAMO da ne pukne pri kreiranju.
    let window = PuzzleRepository.practiceRatingWindow(playerRating: 3000)
    #expect(window.lowerBound <= window.upperBound)
    #expect(window.lowerBound >= PuzzleRepository.minRating)
}

// MARK: - stepRatingWindow (Faza 4a, Task 4)
//
// Prozor koraka Puta je presek prozora oko rejtinga igraca sa opsegom koji je
// korak propisao. Kad je presek prazan, PREDNOST IMA OPSEG KORAKA: kurikulum
// zna sta se uci, rejting je samo podesavanje.

@Test func stepRatingWindowIntersectsPlayerWindowWithStepRange() {
    // Prozor igraca 1000...1300, opseg koraka 600...1400 — presek postoji.
    let window = PuzzleRepository.stepRatingWindow(playerRating: 1200, stepRange: 600...1400)
    #expect(window == 1000...1300)
}

@Test func stepRatingWindowFallsBackToStepRangeWhenPlayerIsBelowIt() {
    // Pocetnik (600) na koraku za 1500-1800: prozor igraca je 400...700, presek
    // sa 1500...1800 je prazan. Kurikulum pobedjuje — inace bi korisnik dobio
    // zadatke koji nemaju veze sa lekcijom koju je upravo procitao.
    let window = PuzzleRepository.stepRatingWindow(playerRating: 600, stepRange: 1500...1800)
    #expect(window == 1500...1800)
}

@Test func stepRatingWindowFallsBackToStepRangeWhenPlayerIsAboveIt() {
    // Jak igrac (2500) na uvodnom koraku 600...1000: prozor igraca je
    // 2300...2600, presek je opet prazan. Isti ishod, druga strana.
    let window = PuzzleRepository.stepRatingWindow(playerRating: 2500, stepRange: 600...1000)
    #expect(window == 600...1000)
}

// Integritet baze nije samo "FEN se parsira". `applyNextComputerMove()` odigrava
// PRVI potez zadatka preko `ChessMove.fromUCI` i, ako taj potez ne prodje, tiho
// izlazi ostavljajuci `phase` na `.loading` — ekran bez ijedne aktivne kontrole.
// Zato se ovde proverava da svaki zadatak u bazi ima prvi potez koji se zaista
// razresi u svojoj FEN poziciji.
@Test @MainActor func everyPuzzleFirstMoveParsesInItsOwnPosition() {
    let repo = openTestRepository()

    var broken: [String] = []
    for puzzle in repo.allPuzzlesOrderedById() {
        guard let state = GameState.fromFEN(puzzle.fen),
              let first = puzzle.uciMoves.first else {
            broken.append("\(puzzle.puzzleId) (FEN/potezi)")
            if broken.count >= 5 { break }
            continue
        }
        if ChessMove.fromUCI(first, in: state) == nil {
            broken.append("\(puzzle.puzzleId) (\(first))")
            if broken.count >= 5 { break }
        }
    }
    #expect(broken.isEmpty, "Prvi potez se ne razresava kod zadataka: \(broken)")
}
