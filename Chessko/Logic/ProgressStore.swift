import Foundation

// MARK: - Stanje koraka

enum StepState: String, Codable, Equatable {
    case locked, available, completed
}

// MARK: - Cista logika Puta
//
// Sve sto se moze pogresiti oko otkljucavanja, dnevnog cilja i streak-a stoji
// OVDE, kao ciste funkcije bez fajlova i bez sata. Dan ulazi kao string
// ("yyyy-MM-dd") da test ne zavisi od vremenske zone ni od trenutka pokretanja.

enum PathProgress {

    /// Korak je dostupan ako je PRVI nezavrsen u redosledu; svi posle su
    /// zakljucani. Prazan skup zavrsenih znaci da je dostupan samo prvi.
    static func stepStates(stepIds: [String], completed: Set<String>) -> [String: StepState] {
        var result: [String: StepState] = [:]
        var foundAvailable = false
        for id in stepIds {
            if completed.contains(id) {
                result[id] = .completed
            } else if !foundAvailable {
                result[id] = .available
                foundAvailable = true
            } else {
                result[id] = .locked
            }
        }
        return result
    }

    /// Dnevni cilj: jedan zavrsen korak Puta ILI tri resena zadatka.
    static func goalMet(steps: Int, puzzles: Int) -> Bool {
        steps >= 1 || puzzles >= 3
    }

    /// Dani zaredom sa ispunjenim ciljem, zakljucno sa danas.
    ///
    /// Ako cilj DANAS jos nije ispunjen, brojanje krece od juce — dan jos
    /// traje, pa niz ne sme da se prekine. Prekida ga tek propusten dan.
    static func currentStreak(goalDays: Set<String>, today: String,
                              calendar: Calendar = .current) -> Int {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = calendar.timeZone
        fmt.locale = Locale(identifier: "en_US_POSIX")
        guard var day = fmt.date(from: today) else { return 0 }

        if !goalDays.contains(today) {
            guard let yesterday = calendar.date(byAdding: .day, value: -1, to: day) else { return 0 }
            day = yesterday
        }

        var count = 0
        while goalDays.contains(fmt.string(from: day)) {
            count += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return count
    }
}

// MARK: - Snimak napretka na disku

struct ProgressSnapshot: Codable, Equatable {
    var version: Int = 1

    // Put
    var completedSteps: Set<String> = []
    var stepCompletionDates: [String: String] = [:]   // stepId -> "yyyy-MM-dd"

    // Dnevni cilj i streak
    var stepsCompletedByDay: [String: Int] = [:]
    var puzzlesSolvedByDay: [String: Int] = [:]

    // Preneto iz `UserDefaults` pri prvom pokretanju
    var gamesPlayed = 0, gamesWon = 0, gamesLost = 0, gamesDrawn = 0
    var currentWinStreak = 0, bestWinStreak = 0
    var puzzlesSolved = 0, currentPuzzleStreak = 0, bestPuzzleStreak = 0
    var puzzleRating = 800
}

// MARK: - Skladiste
//
// JSON u Application Support, ne `UserDefaults`: napredak je struktura, ne
// sacica skalara. `UserDefaults` kljucevi se NAMERNO ne brisu posle migracije —
// tako povratak na stariju verziju aplikacije i dalje radi (spec 5.4).

@MainActor
final class ProgressStore {
    static let shared = ProgressStore()

    private(set) var snapshot: ProgressSnapshot

    private let fileURL: URL
    private let defaults: UserDefaults

    static var defaultFileURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory,
                                           in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("progress.json")
    }

    /// Putanja i `UserDefaults` se UBRIZGAVAJU, ne uzimaju iz okruzenja.
    /// Pod `swift test` se `.applicationSupportDirectory` razresava u STVARNI
    /// `~/Library/Application Support` korisnika — test koji dodirne skladiste
    /// bez ovoga upisao bi pravi fajl u home, migrirao iz `UserDefaults`-a test
    /// procesa, i davao rezultat zavisan od prethodnih pokretanja. Ovako
    /// migracija i perzistencija postaju testabilne u privremenom direktorijumu.
    init(fileURL: URL = ProgressStore.defaultFileURL,
         defaults: UserDefaults = .standard) {
        self.fileURL = fileURL
        self.defaults = defaults
        if let data = try? Data(contentsOf: fileURL),
           let loaded = try? JSONDecoder().decode(ProgressSnapshot.self, from: data) {
            snapshot = loaded
        } else {
            snapshot = Self.migrated(from: defaults)
            save()
        }
    }

    /// Prvo pokretanje posle nadogradnje: statistika se preuzima iz
    /// `UserDefaults`-a i ostavlja tamo netaknuta.
    private static func migrated(from d: UserDefaults) -> ProgressSnapshot {
        var s = ProgressSnapshot()
        s.gamesPlayed = d.integer(forKey: "stats_gamesPlayed")
        s.gamesWon = d.integer(forKey: "stats_gamesWon")
        s.gamesLost = d.integer(forKey: "stats_gamesLost")
        s.gamesDrawn = d.integer(forKey: "stats_gamesDrawn")
        s.currentWinStreak = d.integer(forKey: "stats_currentWinStreak")
        s.bestWinStreak = d.integer(forKey: "stats_bestWinStreak")
        s.puzzlesSolved = d.integer(forKey: "stats_puzzlesSolved")
        s.currentPuzzleStreak = d.integer(forKey: "stats_currentPuzzleStreak")
        s.bestPuzzleStreak = d.integer(forKey: "stats_bestPuzzleStreak")
        s.puzzleRating = d.object(forKey: "stats_puzzleRating") == nil
            ? 800 : d.integer(forKey: "stats_puzzleRating")
        return s
    }

    /// Upis nikad ne baca dalje — napredak ne sme da obori aplikaciju. Ali NE
    /// sme ni da cuti:
    ///
    /// - Neuspelo kodiranje je greska u kodu (ceo snimak su Codable primitivi),
    ///   pa puca u debug build-u umesto da se tiho preskoci.
    /// - Neuspeo upis je stanje okruzenja (pun disk, zakljucan uredjaj pa
    ///   zasticeni podaci nisu dostupni), pa se samo prijavljuje.
    ///
    /// Gubitak je uzak jer je snimak u memoriji CEO: sledeca uspesna izmena
    /// upisuje i ono sto je ranije palo. Zato `mutate` upisuje po svakoj izmeni
    /// i ne sme da se "optimizuje" u grupno snimanje — time bi se ovaj oporavak
    /// izgubio.
    func save() {
        let data: Data
        do {
            data = try JSONEncoder().encode(snapshot)
        } catch {
            print("[Chessko] GRESKA: napredak se ne moze kodirati: \(error)")
            assertionFailure("ProgressSnapshot se ne kodira: \(error)")
            return
        }
        do {
            try data.write(to: fileURL, options: .atomic)
        } catch {
            print("[Chessko] GRESKA: napredak nije sacuvan: \(error)")
        }
    }

    /// Mutira snimak pa odmah upisuje. Napredak se menja retko (jednom po
    /// koraku ili zadatku), pa upis po izmeni ne kosta nista, a gasenje
    /// aplikacije u bilo kom trenutku ne gubi vise od poslednje akcije.
    private func mutate(_ change: (inout ProgressSnapshot) -> Void) {
        change(&snapshot)
        save()
    }

    static func dayKey(_ date: Date = Date(), calendar: Calendar = .current) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = calendar.timeZone
        fmt.locale = Locale(identifier: "en_US_POSIX")
        return fmt.string(from: date)
    }

    // MARK: Put

    func stepStates(for curriculum: Curriculum) -> [String: StepState] {
        PathProgress.stepStates(stepIds: curriculum.allStepIds,
                                completed: snapshot.completedSteps)
    }

    func completeStep(_ id: String) {
        guard !snapshot.completedSteps.contains(id) else { return }
        let day = Self.dayKey()
        mutate {
            $0.completedSteps.insert(id)
            $0.stepCompletionDates[id] = day
            $0.stepsCompletedByDay[day, default: 0] += 1
        }
    }

    func recordPuzzleSolvedToday() {
        let day = Self.dayKey()
        mutate { $0.puzzlesSolvedByDay[day, default: 0] += 1 }
    }

    // MARK: Cilj i streak

    var goalMetToday: Bool {
        let day = Self.dayKey()
        return PathProgress.goalMet(steps: snapshot.stepsCompletedByDay[day] ?? 0,
                                    puzzles: snapshot.puzzlesSolvedByDay[day] ?? 0)
    }

    var currentStreak: Int {
        let days = Set(Set(snapshot.stepsCompletedByDay.keys)
            .union(snapshot.puzzlesSolvedByDay.keys)
            .filter { PathProgress.goalMet(steps: snapshot.stepsCompletedByDay[$0] ?? 0,
                                           puzzles: snapshot.puzzlesSolvedByDay[$0] ?? 0) })
        return PathProgress.currentStreak(goalDays: days, today: Self.dayKey())
    }

    // MARK: Pristup statistici (koristi fasada `StatsManager`)

    func updateStats(_ change: (inout ProgressSnapshot) -> Void) { mutate(change) }
}
