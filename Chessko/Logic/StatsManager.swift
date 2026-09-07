import Foundation
import SwiftUI

@Observable
@MainActor
final class StatsManager {
    static let shared = StatsManager()

    // MARK: - Kljucevi napretka na zadacima
    //
    // Zive OVDE, a ne u `PuzzleViewModel`, jer `resetStats()` mora da ih obrise,
    // a `StatsManager.swift` se kompajlira i u `ChesskoEngine` SwiftPM target
    // gde `PuzzleViewModel` ne postoji (referenca na njega bi oborila
    // `swift test`). `PuzzleViewModel` koristi bas ove konstante.

    /// Id-jevi svih ikad resenih zadataka (niz stringova).
    nonisolated static let solvedPuzzleIdsKey = "solvedPuzzleIds"

    /// Datumi ("yyyy-MM-dd") za koje je resen zadatak dana.
    nonisolated static let solvedDatesKey = "chessko.solvedDates"

    var gamesPlayed: Int {
        didSet { UserDefaults.standard.set(gamesPlayed, forKey: "stats_gamesPlayed") }
    }
    var gamesWon: Int {
        didSet { UserDefaults.standard.set(gamesWon, forKey: "stats_gamesWon") }
    }
    var gamesLost: Int {
        didSet { UserDefaults.standard.set(gamesLost, forKey: "stats_gamesLost") }
    }
    var gamesDrawn: Int {
        didSet { UserDefaults.standard.set(gamesDrawn, forKey: "stats_gamesDrawn") }
    }

    var currentWinStreak: Int {
        didSet { UserDefaults.standard.set(currentWinStreak, forKey: "stats_currentWinStreak") }
    }
    var bestWinStreak: Int {
        didSet { UserDefaults.standard.set(bestWinStreak, forKey: "stats_bestWinStreak") }
    }

    var puzzlesSolved: Int {
        didSet { UserDefaults.standard.set(puzzlesSolved, forKey: "stats_puzzlesSolved") }
    }
    var currentPuzzleStreak: Int {
        didSet { UserDefaults.standard.set(currentPuzzleStreak, forKey: "stats_currentPuzzleStreak") }
    }
    var bestPuzzleStreak: Int {
        didSet { UserDefaults.standard.set(bestPuzzleStreak, forKey: "stats_bestPuzzleStreak") }
    }

    /// Elo-stil rejting igraca za zadatke. Pocinje na 800 (spec 5.4).
    var puzzleRating: Int {
        didSet { UserDefaults.standard.set(puzzleRating, forKey: "stats_puzzleRating") }
    }

    var winRate: Int {
        gamesPlayed > 0 ? Int((Double(gamesWon) / Double(gamesPlayed)) * 100.0) : 0
    }

    private init() {
        self.gamesPlayed = UserDefaults.standard.integer(forKey: "stats_gamesPlayed")
        self.gamesWon = UserDefaults.standard.integer(forKey: "stats_gamesWon")
        self.gamesLost = UserDefaults.standard.integer(forKey: "stats_gamesLost")
        self.gamesDrawn = UserDefaults.standard.integer(forKey: "stats_gamesDrawn")
        self.currentWinStreak = UserDefaults.standard.integer(forKey: "stats_currentWinStreak")
        self.bestWinStreak = UserDefaults.standard.integer(forKey: "stats_bestWinStreak")
        self.puzzlesSolved = UserDefaults.standard.integer(forKey: "stats_puzzlesSolved")
        self.currentPuzzleStreak = UserDefaults.standard.integer(forKey: "stats_currentPuzzleStreak")
        self.bestPuzzleStreak = UserDefaults.standard.integer(forKey: "stats_bestPuzzleStreak")
        if UserDefaults.standard.object(forKey: "stats_puzzleRating") == nil {
            self.puzzleRating = 800
        } else {
            self.puzzleRating = UserDefaults.standard.integer(forKey: "stats_puzzleRating")
        }
    }

    func recordGameWon() {
        gamesPlayed += 1
        gamesWon += 1
        currentWinStreak += 1
        if currentWinStreak > bestWinStreak {
            bestWinStreak = currentWinStreak
        }
    }

    func recordGameLost() {
        gamesPlayed += 1
        gamesLost += 1
        currentWinStreak = 0
    }

    func recordGameDrawn() {
        gamesPlayed += 1
        gamesDrawn += 1
    }

    func recordPuzzleSolved() {
        puzzlesSolved += 1
        currentPuzzleStreak += 1
        if currentPuzzleStreak > bestPuzzleStreak {
            bestPuzzleStreak = currentPuzzleStreak
        }
    }

    func recordPuzzleFailed() {
        currentPuzzleStreak = 0
    }

    /// E = 1 / (1 + 10^((Rp - R)/400));  R' = R + K*(S - E),  K = 32
    /// Cista funkcija — nema stanja, testira se direktno.
    nonisolated static func newRating(current r: Int, puzzleRating rp: Int, solved: Bool) -> Int {
        let expected = 1.0 / (1.0 + pow(10.0, (Double(rp) - Double(r)) / 400.0))
        let score = solved ? 1.0 : 0.0
        return Int((Double(r) + 32.0 * (score - expected)).rounded())
    }

    /// PAZNJA na dva razlicita znacenja istog imena: svojstvo `puzzleRating`
    /// je rejting IGRACA, a parametar `puzzleRating` (`rp`) je rejting
    /// RESAVANOG ZADATKA. Red ispod cita jedno a pise drugo.
    func applyPuzzleResult(puzzleRating rp: Int, solved: Bool) {
        puzzleRating = StatsManager.newRating(current: puzzleRating,
                                              puzzleRating: rp,
                                              solved: solved)
    }

    func resetStats() {
        gamesPlayed = 0
        gamesWon = 0
        gamesLost = 0
        gamesDrawn = 0
        currentWinStreak = 0
        bestWinStreak = 0
        puzzlesSolved = 0
        currentPuzzleStreak = 0
        bestPuzzleStreak = 0
        puzzleRating = 800

        // Rejting nazad na 800 nema smisla ako napredak na zadacima ostane:
        // vezbanje bi i dalje iskljucivalo svaki ikad resen zadatak, a
        // kalendar bi ostao zelen. `PuzzleViewModel` oba skupa drzi u kesu i
        // ponovo ih cita pri sledecem ucitavanju zadatka.
        UserDefaults.standard.removeObject(forKey: Self.solvedPuzzleIdsKey)
        UserDefaults.standard.removeObject(forKey: Self.solvedDatesKey)
    }
}
