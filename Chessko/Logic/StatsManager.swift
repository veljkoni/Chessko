import Foundation
import SwiftUI

@Observable
@MainActor
final class StatsManager {
    static let shared = StatsManager()

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
    }
}
