import Foundation
import SwiftUI

// MARK: - Fasada nad `ProgressStore`-om
//
// Od Faze 4a jedini izvor istine za statistiku je `progress.json`
// (`ProgressStore`), a NE `UserDefaults`. `StatsManager` ostaje zbog 26
// postojecih poziva u `GameViewModel`-u, `PuzzleViewModel`-u i
// `SettingsSheet`-u — svaki potpis je namerno nepromenjen, pa nijedno mesto
// poziva nije diralo ovu izmenu (medju njima i logika rejtinga proverena u
// Fazi 2).
//
// Stari `stats_*` kljucevi u `UserDefaults`-u se posle migracije NAMERNO ne
// brisu (spec 5.4): povratak na stariju verziju aplikacije tako i dalje radi.
// Zato ovde vise nema `didSet` upisa — pisanje na oba mesta bi napravilo dva
// izvora istine.

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

    /// Uveden u Task-u 2, kad `ProgressStore` JOS NIJE bio `@Observable`: bez
    /// njega racunati getteri ne bi obavestili SwiftUI, pa se `SettingsSheet`
    /// ne bi osvezio posle "Resetuj statistiku" dok je list otvoren.
    ///
    /// Od Task-a 3 je `ProgressStore` `@Observable` (`Observation` je deo
    /// standardne biblioteke, ne SwiftUI-ja, pa testni paket to podnosi), tako
    /// da citanje `snapshot`-a samo po sebi vec registruje pracenje i ovaj
    /// brojac je **suvisan**. Zadrzan je namerno: uklanjanje se ne moze jeftino
    /// proveriti bez sinteticnih tapova, koji u ovom simulatoru ne rade, a
    /// cena drzanja je nekoliko linija. Sme da se ukloni kad neko bude mogao
    /// rucno da potvrdi da se statistika i dalje osvezava uzivo.
    private var revision = 0

    private var s: ProgressSnapshot {
        _ = revision
        return ProgressStore.shared.snapshot
    }

    private func write(_ change: (inout ProgressSnapshot) -> Void) {
        ProgressStore.shared.updateStats(change)
        revision &+= 1
    }

    var gamesPlayed: Int { s.gamesPlayed }
    var gamesWon: Int { s.gamesWon }
    var gamesLost: Int { s.gamesLost }
    var gamesDrawn: Int { s.gamesDrawn }

    var currentWinStreak: Int { s.currentWinStreak }
    var bestWinStreak: Int { s.bestWinStreak }

    var puzzlesSolved: Int { s.puzzlesSolved }
    var currentPuzzleStreak: Int { s.currentPuzzleStreak }
    var bestPuzzleStreak: Int { s.bestPuzzleStreak }

    /// Elo-stil rejting igraca za zadatke. Pocinje na 800 (spec 5.4).
    var puzzleRating: Int { s.puzzleRating }

    var winRate: Int {
        gamesPlayed > 0 ? Int((Double(gamesWon) / Double(gamesPlayed)) * 100.0) : 0
    }

    private init() {}

    func recordGameWon() {
        write {
            $0.gamesPlayed += 1
            $0.gamesWon += 1
            $0.currentWinStreak += 1
            $0.bestWinStreak = max($0.bestWinStreak, $0.currentWinStreak)
        }
    }

    func recordGameLost() {
        write {
            $0.gamesPlayed += 1
            $0.gamesLost += 1
            $0.currentWinStreak = 0
        }
    }

    func recordGameDrawn() {
        write {
            $0.gamesPlayed += 1
            $0.gamesDrawn += 1
        }
    }

    func recordPuzzleSolved() {
        write {
            $0.puzzlesSolved += 1
            $0.currentPuzzleStreak += 1
            $0.bestPuzzleStreak = max($0.bestPuzzleStreak, $0.currentPuzzleStreak)
        }
        // Otud dnevni cilj Puta zna za resene zadatke.
        ProgressStore.shared.recordPuzzleSolvedToday()
    }

    func recordPuzzleFailed() {
        write { $0.currentPuzzleStreak = 0 }
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
        write {
            $0.puzzleRating = StatsManager.newRating(current: $0.puzzleRating,
                                                     puzzleRating: rp,
                                                     solved: solved)
        }
    }

    func resetStats() {
        write {
            $0.gamesPlayed = 0
            $0.gamesWon = 0
            $0.gamesLost = 0
            $0.gamesDrawn = 0
            $0.currentWinStreak = 0
            $0.bestWinStreak = 0
            $0.puzzlesSolved = 0
            $0.currentPuzzleStreak = 0
            $0.bestPuzzleStreak = 0
            $0.puzzleRating = 800
        }

        // Rejting nazad na 800 nema smisla ako napredak na zadacima ostane:
        // vezbanje bi i dalje iskljucivalo svaki ikad resen zadatak, a
        // kalendar bi ostao zelen. `PuzzleViewModel` oba skupa drzi u kesu i
        // ponovo ih cita pri sledecem ucitavanju zadatka.
        UserDefaults.standard.removeObject(forKey: Self.solvedPuzzleIdsKey)
        UserDefaults.standard.removeObject(forKey: Self.solvedDatesKey)
    }
}
