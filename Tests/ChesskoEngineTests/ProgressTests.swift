import Testing
import Foundation
@testable import ChesskoEngine

// MARK: - Otkljucavanje koraka

@Test func firstStepIsAvailableAndRestLocked() {
    let s = PathProgress.stepStates(stepIds: ["a", "b", "c"], completed: [])
    #expect(s["a"] == .available)
    #expect(s["b"] == .locked)
    #expect(s["c"] == .locked)
}

@Test func completingAStepUnlocksExactlyTheNextOne() {
    let s = PathProgress.stepStates(stepIds: ["a", "b", "c"], completed: ["a"])
    #expect(s["a"] == .completed)
    #expect(s["b"] == .available)
    #expect(s["c"] == .locked)
}

@Test func allCompletedLeavesNothingAvailable() {
    let s = PathProgress.stepStates(stepIds: ["a", "b"], completed: ["a", "b"])
    #expect(s["a"] == .completed)
    #expect(s["b"] == .completed)
    #expect(!s.values.contains(.available))
}

@Test func gapInCompletedStepsStillOffersTheFirstUnfinished() {
    // Ne bi trebalo da se desi, ali podaci na disku mogu da kazu bilo sta —
    // korisnik ne sme da ostane bez ijednog dostupnog koraka.
    let s = PathProgress.stepStates(stepIds: ["a", "b", "c"], completed: ["b"])
    #expect(s["a"] == .available)
    #expect(s["b"] == .completed)
    #expect(s["c"] == .locked)
}

// MARK: - Dnevni cilj

@Test func dailyGoalNeedsOneStepOrThreePuzzles() {
    #expect(PathProgress.goalMet(steps: 1, puzzles: 0) == true)
    #expect(PathProgress.goalMet(steps: 0, puzzles: 3) == true)
    #expect(PathProgress.goalMet(steps: 0, puzzles: 2) == false)
    #expect(PathProgress.goalMet(steps: 0, puzzles: 0) == false)
}

// MARK: - Streak

@Test func streakCountsConsecutiveDaysEndingToday() {
    let days: Set<String> = ["2026-09-07", "2026-09-08", "2026-09-09"]
    #expect(PathProgress.currentStreak(goalDays: days, today: "2026-09-09") == 3)
}

@Test func streakSurvivesADayThatIsNotOverYet() {
    // Kljucni slucaj: cilj DANAS jos nije ispunjen. Niz se ne sme prekinuti
    // dok dan ne prodje — inace bi korisniku streak nestao svako jutro.
    let days: Set<String> = ["2026-09-07", "2026-09-08"]
    #expect(PathProgress.currentStreak(goalDays: days, today: "2026-09-09") == 2)
}

@Test func aMissedDayBreaksTheStreak() {
    // 08. preskocen; 09. je danas i nije ispunjen. Niz je prekinut.
    let days: Set<String> = ["2026-09-06", "2026-09-07"]
    #expect(PathProgress.currentStreak(goalDays: days, today: "2026-09-09") == 0)
}

@Test func todayAloneIsAStreakOfOne() {
    #expect(PathProgress.currentStreak(goalDays: ["2026-09-09"], today: "2026-09-09") == 1)
}

@Test func emptyHistoryHasNoStreak() {
    #expect(PathProgress.currentStreak(goalDays: [], today: "2026-09-09") == 0)
}

@Test func streakCrossesMonthAndYearBoundaries() {
    let days: Set<String> = ["2025-12-30", "2025-12-31", "2026-01-01"]
    #expect(PathProgress.currentStreak(goalDays: days, today: "2026-01-01") == 3)
}

// MARK: - Skladiste (u privremenom direktorijumu, nikad u home-u korisnika)

@MainActor
private func makeStore(_ defaults: UserDefaults = UserDefaults(suiteName: UUID().uuidString)!)
    -> (ProgressStore, URL) {
    let url = URL(fileURLWithPath: NSTemporaryDirectory())
        .appendingPathComponent("progress-\(UUID().uuidString).json")
    return (ProgressStore(fileURL: url, defaults: defaults), url)
}

@Test @MainActor func completedStepSurvivesReload() {
    let (store, url) = makeStore()
    defer { try? FileManager.default.removeItem(at: url) }

    store.completeStep("basics-lesson")
    #expect(store.snapshot.completedSteps.contains("basics-lesson"))

    // Novi primerak nad ISTIM fajlom — to je ono sto se desava posle gasenja
    // aplikacije, i uslov zavrsetka faze iz spec-a.
    let reloaded = ProgressStore(fileURL: url, defaults: UserDefaults(suiteName: UUID().uuidString)!)
    #expect(reloaded.snapshot.completedSteps.contains("basics-lesson"))
}

@Test @MainActor func completingTheSameStepTwiceCountsOnce() {
    let (store, url) = makeStore()
    defer { try? FileManager.default.removeItem(at: url) }
    store.completeStep("a")
    store.completeStep("a")
    let day = ProgressStore.dayKey()
    #expect(store.snapshot.stepsCompletedByDay[day] == 1)
}

@Test @MainActor func migrationReadsUserDefaultsAndLeavesThemIntact() {
    let d = UserDefaults(suiteName: UUID().uuidString)!
    d.set(7, forKey: "stats_gamesPlayed")
    d.set(1234, forKey: "stats_puzzleRating")

    let (store, url) = makeStore(d)
    defer { try? FileManager.default.removeItem(at: url) }

    #expect(store.snapshot.gamesPlayed == 7)
    #expect(store.snapshot.puzzleRating == 1234)
    // Spec 5.4: stari kljucevi ostaju, da povratak na stariju verziju radi.
    #expect(d.integer(forKey: "stats_gamesPlayed") == 7)
    #expect(d.integer(forKey: "stats_puzzleRating") == 1234)
}

@Test @MainActor func missingRatingKeyMigratesToEightHundredNotZero() {
    // `UserDefaults.integer(forKey:)` vraca 0 za nepostojeci kljuc; nov
    // korisnik mora da krene sa 800, ne sa 0.
    let (store, url) = makeStore()
    defer { try? FileManager.default.removeItem(at: url) }
    #expect(store.snapshot.puzzleRating == 800)
}

// Sintetisani `Decodable` IGNORISE podrazumevane vrednosti i baca `keyNotFound`
// za svaki kljuc kog nema u fajlu. Bez rucnog dekodera bi prvo novo polje u
// `ProgressSnapshot`-u razbilo `progress.json` svakog postojeceg korisnika i
// migracija bi ga odmah pregazila — ceo napredak na Putu i istorija streak-a
// nestali bi bez poruke. Ovaj test tvrdi da stariji fajl i dalje moze da se cita.
@Test @MainActor func snapshotFromAnOlderSchemaStillLoads() throws {
    let older = """
    {"version":1,"completedSteps":["basics-lesson"],"stepsCompletedByDay":{"2026-09-10":1}}
    """
    let url = URL(fileURLWithPath: NSTemporaryDirectory())
        .appendingPathComponent("progress-old-\(UUID().uuidString).json")
    try Data(older.utf8).write(to: url)
    defer { try? FileManager.default.removeItem(at: url) }

    let store = ProgressStore(fileURL: url,
                              defaults: UserDefaults(suiteName: UUID().uuidString)!)

    // Preziveo je ono sto je u fajlu...
    #expect(store.snapshot.completedSteps == ["basics-lesson"])
    #expect(store.snapshot.stepsCompletedByDay["2026-09-10"] == 1)
    // ...a polja kojih u fajlu nema dobila su podrazumevane vrednosti, ne nulu
    // tamo gde nula nije tacna.
    #expect(store.snapshot.puzzleRating == 800)
    #expect(store.snapshot.gamesPlayed == 0)
}

@Test @MainActor func corruptProgressFileIsSetAsideInsteadOfOverwritten() throws {
    let url = URL(fileURLWithPath: NSTemporaryDirectory())
        .appendingPathComponent("progress-bad-\(UUID().uuidString).json")
    try Data("{ ovo nije json".utf8).write(to: url)
    let backup = url.appendingPathExtension("corrupt")
    defer {
        try? FileManager.default.removeItem(at: url)
        try? FileManager.default.removeItem(at: backup)
    }

    _ = ProgressStore(fileURL: url, defaults: UserDefaults(suiteName: UUID().uuidString)!)

    // Ostecen fajl mora da se sacuva sa strane — mozda se moze spasiti rucno.
    #expect(FileManager.default.fileExists(atPath: backup.path),
            "ostecen progress.json nije odlozen u stranu")
}
