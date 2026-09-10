import SwiftUI

// MARK: - Puzzle Phase

enum PuzzlePhase: Equatable {
    case loading
    case unavailable(String)
    case playing          // waiting for player's move
    case wrongMove        // player just tried the wrong move
    case solved           // all moves found correctly
    case showingSolution  // auto-playing remaining moves
}

// MARK: - Puzzle Mode

/// Razlikuje zadatak dana (vezan za `selectedDate`, kalendar i dnevni
/// limit) od slobodnog vezbanja (Task 5, "Sledeći zadatak"). Bitno je
/// zbog `markCurrentSolved()`: on upisuje `dateKey(selectedDate)` u
/// `solvedDates` — u praksi to ima smisla SAMO za zadatak dana. Da to
/// radi i u `.practice` rezimu, resen vezbovni zadatak bi lazno oznacio
/// KALENDARSKI DAN kao resen (obrise legitimnu informaciju o tome da li
/// je korisnik resio bas TAJ dnevni zadatak).
enum PuzzleMode: Equatable {
    case daily
    case practice
    /// Korak Puta (Faza 4a): fiksan red zadataka umesto jednog. `requireFlawless`
    /// razlikuje `test` od `practice` — jedina razlika izmedju ta dva tipa
    /// koraka je tolerancija na gresku, pa je to zastavica a ne cetvrti rezim.
    case step(id: String, requireFlawless: Bool)
}

// MARK: - Puzzle View Model

@Observable
@MainActor
final class PuzzleViewModel {

    // MARK: - Published State

    var gameState: GameState = .initial()
    var playerColor: PieceColor = .white
    var selectedPosition: Position?
    var legalMovesForSelected: [ChessMove] = []
    var lastMove: ChessMove?
    var animatingPiece: AnimatingPiece?
    var flyingCapture: FlyingCapture?

    var currentPuzzle: ChessPuzzle?
    var phase: PuzzlePhase = .loading
    private var puzzleHadError: Bool = false

    /// Da li je tekuci zadatak dnevni (vezan za `selectedDate`/kalendar) ili
    /// slobodno vezbanje (`nextPuzzle()`). Vidi `PuzzleMode` za zasto.
    private(set) var mode: PuzzleMode = .daily

    // MARK: - Korak Puta (Faza 4a)

    /// Red zadataka za tekuci korak Puta i koliko ih je reseno. Red se puni
    /// JEDNOM, u `startStepPractice(step:)`: da se zadaci izvlacili jedan po
    /// jedan, traka napretka ne bi imala ukupan broj, a `test` koji krece
    /// ispocetka ne bi mogao da garantuje da su zadaci NOVI.
    private(set) var stepQueue: [ChessPuzzle] = []
    private(set) var stepSolved: Int = 0
    private(set) var stepFailed: Bool = false
    var stepProgress: (solved: Int, total: Int) { (stepSolved, stepQueue.count) }

    /// Korak iz kojeg je red napunjen — cuva se da `test` moze da se ponovi sa
    /// novim zadacima bez pomoci ekrana.
    private(set) var currentStep: CurriculumStep?

    var isStepMode: Bool {
        if case .step = mode { return true }
        return false
    }

    var stepRequiresFlawless: Bool {
        if case .step(_, let flawless) = mode { return flawless }
        return false
    }

    /// `nil` kad `puzzles.sqlite` nedostaje iz bundle-a — `loadPuzzle()` tada
    /// odmah javlja `.unavailable` umesto praznog ekrana.
    private let repository = PuzzleRepository.bundled

    // MARK: - Solved Puzzle IDs (Task 5 — "Sledeći zadatak")

    /// Id-jevi svih zadataka koje je korisnik ikad uspesno resio, u OBA
    /// rezima (dnevni i vezbanje) — koristi se kao `excluding` skup u
    /// `nextPuzzle()` da se isti zadatak ne ponavlja iznova. Ucitava se
    /// JEDNOM iz `UserDefaults` (kljuc `solvedPuzzleIds`, niz stringova).
    /// Najgori slucaj je ~20 000 kratkih id-jeva (~200 KB serijalizovano) —
    /// prihvatljivo, bez potrebe za cisceniem/rotacijom.
    private(set) var solvedPuzzleIds: Set<String> = {
        let arr = UserDefaults.standard.stringArray(forKey: StatsManager.solvedPuzzleIdsKey) ?? []
        return Set(arr)
    }()

    private func recordPuzzleIdSolved(_ id: String) {
        guard solvedPuzzleIds.insert(id).inserted else { return }
        UserDefaults.standard.set(Array(solvedPuzzleIds), forKey: StatsManager.solvedPuzzleIdsKey)
    }

    /// Oba skupa (`solvedPuzzleIds`, `solvedDates`) se kesiraju u memoriji, a
    /// "Resetuj statistiku" (`StatsManager.resetStats()`) brise njihove kljuceve
    /// direktno iz `UserDefaults` — bez ovog ponovnog citanja kes bi prezive
    /// reset, pa bi vezbanje i dalje iskljucivalo sve ranije resene zadatke a
    /// kalendar ostao zelen. Poziva se jednom po ucitavanju zadatka (ne po
    /// tapu), pa je cena zanemarljiva.
    private func reloadPersistedProgress() {
        let ids = UserDefaults.standard.stringArray(forKey: StatsManager.solvedPuzzleIdsKey) ?? []
        solvedPuzzleIds = Set(ids)
        loadSolvedDates()
    }

    // MARK: - Date Navigation

    var selectedDate: Date = Calendar.current.startOfDay(for: Date())
    private(set) var solvedDates: Set<String> = []

    private static let cal = Calendar.current
    private let solvedKey = StatsManager.solvedDatesKey

    var canGoPrevious: Bool {
        selectedDate > minSelectableDate
    }

    var canGoNext: Bool {
        selectedDate < maxSelectableDate
    }

    var minSelectableDate: Date {
        Self.cal.date(byAdding: .month, value: -2,
                      to: Self.cal.startOfDay(for: Date())) ?? Date()
    }

    var maxSelectableDate: Date {
        Self.cal.startOfDay(for: Date())
    }

    func goToPrevious() {
        guard canGoPrevious,
              let d = Self.cal.date(byAdding: .day, value: -1, to: selectedDate)
        else { return }
        load(date: d)
    }

    func goToNext() {
        guard canGoNext,
              let d = Self.cal.date(byAdding: .day, value: 1, to: selectedDate)
        else { return }
        load(date: d)
    }

    func load(date: Date) {
        selectedDate = Self.cal.startOfDay(for: date)
        loadPuzzle()
    }

    func dateKey(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }

    func isSolved(_ date: Date) -> Bool {
        solvedDates.contains(dateKey(date))
    }

    private func markCurrentSolved() {
        solvedDates.insert(dateKey(selectedDate))
        UserDefaults.standard.set(Array(solvedDates), forKey: solvedKey)
    }

    private func loadSolvedDates() {
        let arr = UserDefaults.standard.stringArray(forKey: solvedKey) ?? []
        solvedDates = Set(arr)
    }

    // MARK: - Private

    private var rawMoves: [String] = []  // all UCI moves from the puzzle
    private var movePointer: Int = 0     // index of the next move to apply/find

    /// `true` dok traje skriptovana pauza (600ms) izmedu igracevog tacnog
    /// poteza i protivnickog odgovora. Bez ovoga bi `isPlayerTurn` bio `true`
    /// u tom prozoru, pa bi brz korisnik odigrao potez koji `attempt()` poredi
    /// sa PROTIVNICKIM potezom iz `rawMoves` — greska koju korisnik nije
    /// napravio, a kosta ga rejtinga i oznacava zadatak kao promasen.
    /// `.loading` se ovde NE sme koristiti (kao u `loadPuzzle()`) jer
    /// `PuzzleView` u toj fazi crta ekran ucitavanja umesto table.
    private var awaitingOpponent = false

    /// Raste pri SVAKOM ucitavanju zadatka. Svaki odlozeni `Task` (protivnikov
    /// potez, automatsko prikazivanje resenja) upamti vrednost pri pokretanju i
    /// odustaje ako se u medjuvremenu promenila. Bez toga zaostali `Task` iz
    /// prethodnog zadatka odigra potez nad NOVIM zadatkom: `rawMoves` je vec
    /// zamenjen, pa se odigra tudji potez i `movePointer` odmakne — zadatak
    /// pocne sam sebe da resava.
    private var loadGeneration = 0

    // MARK: - Computed

    var isFlipped: Bool { playerColor == .black }

    var isPlayerTurn: Bool {
        !awaitingOpponent && (phase == .playing || phase == .wrongMove)
    }

    var statusMessage: String {
        switch phase {
        case .loading:         return Loc("Učitavam zadatak...")
        case .unavailable(let msg): return msg
        case .playing:
            return playerColor == .white
                ? Loc("Pronađi pravi potez za bele")
                : Loc("Pronađi pravi potez za crne")
        case .wrongMove:
            // U testu prva greska nije "pokusaj ponovo" nego kraj pokusaja —
            // poruka mora da kaze sta se upravo desilo, jer se tabla za koji
            // trenutak sama zameni novim zadacima.
            return stepFailed
                ? Loc("Greška — test kreće ispočetka")
                : Loc("Pogrešno. Pokušaj ponovo.")
        case .solved:          return Loc("Odlično! Zadatak rešen! 🎉")
        case .showingSolution: return Loc("Rešenje...")
        }
    }

    // MARK: - Load

    /// Zove se iz `.onAppear` na SVAKI povratak na tab Zadaci — NE sme
    /// bezuslovno da resetuje vec ucitan zadatak (ranije je "yank"-ovalo
    /// korisnika iz upola resenog zadatka pri svakom prelasku taba, a od
    /// Task-a 5 bi isto ponasanje izbacilo korisnika i iz vezbovnog zadatka).
    /// Ucitava SAMO kad nema tekuceg zadatka (prvi start) ili kad je faza
    /// `.unavailable` (retry put i dalje mora da forsira ponovni pokusaj).
    /// `load(date:)` i dugme "Pokusaj ponovo" i dalje idu direktno na
    /// `loadPuzzle()`/ovu funkciju bez ovog gejta kad korisnik EKSPLICITNO
    /// trazi ucitavanje.
    func loadDailyPuzzle() {
        guard currentPuzzle == nil || isUnavailable else { return }
        loadPuzzle()   // sam osvezava `solvedDates` preko `reloadPersistedProgress()`
    }

    private var isUnavailable: Bool {
        if case .unavailable = phase { return true }
        return false
    }

    /// Cita zadatak dana direktno iz `PuzzleRepository` — sinhrono, bez mrezne
    /// zavisnosti. `.loading` se i dalje postavlja na pocetku svakog poziva: ne
    /// pokriva vise mrezni delay, nego kratku skriptovanu pauzu pre protivnickog
    /// poteza (`setup()` zakazuje `applyNextComputerMove()` posle 400ms — dok
    /// ono ne postavi `.playing`, `phase` NE SME da ostane na vrednosti
    /// PRETHODNOG zadatka, jer `isPlayerTurn` gejtuje `tap(position:)` i bio bi
    /// otvoren prozor da igrac odigra potez pre nego sto je protivnicki uopste
    /// prikazan).
    private func loadPuzzle() {
        reloadPersistedProgress()
        clearStepState()
        mode = .daily
        phase = .loading
        currentPuzzle = nil
        puzzleHadError = false
        awaitingOpponent = false
        loadGeneration += 1

        guard let repository else {
            phase = .unavailable(Loc("Baza zadataka nije dostupna")); return
        }

        guard let puzzle = repository.dailyPuzzle(for: selectedDate) else {
            phase = .unavailable(Loc("Nema dostupnih zadataka")); return
        }

        setup(puzzle: puzzle)
    }

    // MARK: - Sledeći zadatak (Task 5 — vežbanje bez dnevnog ograničenja)

    /// Ucitava nasumican zadatak iz prozora rejtinga oko trenutnog rejtinga
    /// igraca (spec 5.4), iskljucujuci vec resene id-jeve. Rejting se cita
    /// OVDE, u trenutku poziva — ne kesira se — da prozor prati igraca kako
    /// napreduje/nazaduje unutar iste sesije.
    ///
    /// Progresivno prosirenje kad prozor ne vrati nista (korisnik je resio
    /// sve u opsegu): base (`practiceRatingWindow`, -200/+100) → ±400 → ±800
    /// → cela baza (600...2200). Ako je i cela baza sa iskljucivanjem prazna
    /// (korisnik je resio SVIH ~20 000 zadataka), poslednje pribezište
    /// ignorise `excluding` i ponovi vec resen zadatak — ponavljanje je
    /// bolje od praznog ekrana ili greske.
    func nextPuzzle() {
        // `showSolution()` namerno NE upisuje u `solvedPuzzleIds` (prati pravilo
        // za kalendar), pa bi bez ovoga sledece izvlacenje moglo da vrati bas
        // zadatak koji je upravo bio na ekranu.
        let justShown = currentPuzzle?.puzzleId

        // Isti razlog kao u `loadPuzzle()`: reset statistike brise kljuc
        // direktno iz `UserDefaults`, a `solvedPuzzleIds` je ovde `excluding`
        // skup — bez osvezavanja bi vezbanje i posle reseta iskljucivalo
        // sve ranije resene zadatke.
        reloadPersistedProgress()
        clearStepState()

        mode = .practice
        phase = .loading
        currentPuzzle = nil
        puzzleHadError = false
        awaitingOpponent = false
        loadGeneration += 1

        guard let repository else {
            phase = .unavailable(Loc("Baza zadataka nije dostupna")); return
        }

        let playerRating = StatsManager.shared.puzzleRating
        let windows: [ClosedRange<Int>] = [
            PuzzleRepository.practiceRatingWindow(playerRating: playerRating),
            Self.clampedWindow(center: playerRating, radius: 400),
            Self.clampedWindow(center: playerRating, radius: 800),
            PuzzleRepository.minRating...PuzzleRepository.maxRating
        ]

        let excluded = justShown.map { solvedPuzzleIds.union([$0]) } ?? solvedPuzzleIds

        var found: ChessPuzzle?
        for window in windows {
            if let puzzle = repository.randomPuzzle(ratingRange: window, excluding: excluded) {
                found = puzzle
                break
            }
        }

        // Krajnje pribezište: cela baza je vec resena. Bolje ponoviti nego
        // prazan ekran.
        if found == nil {
            found = repository.randomPuzzle(
                ratingRange: PuzzleRepository.minRating...PuzzleRepository.maxRating,
                excluding: justShown.map { [$0] } ?? []
            )
        }

        guard let puzzle = found else {
            phase = .unavailable(Loc("Nema dostupnih zadataka")); return
        }

        setup(puzzle: puzzle)
    }

    private static func clampedWindow(center r: Int, radius: Int) -> ClosedRange<Int> {
        let lo = max(PuzzleRepository.minRating, r - radius)
        let hi = max(lo, min(PuzzleRepository.maxRating, r + radius))
        return lo...hi
    }

    // MARK: - Korak Puta (Faza 4a — pokretac `practice` i `test` koraka)

    /// Sve sto pokretac koraka treba da zna, izvuceno iz `StepKind`-a. `test`
    /// se od `practice`-a razlikuje SAMO po `requireFlawless` — zato jedan plan
    /// i jedan tok, umesto dva skoro identicna.
    private struct StepPlan {
        let themes: [String]
        let count: Int
        let range: ClosedRange<Int>
        let requireFlawless: Bool
    }

    private static func plan(for step: CurriculumStep) -> StepPlan? {
        switch step.kind {
        case .practice(let themes, let count, let range):
            return StepPlan(themes: themes, count: count, range: range, requireFlawless: false)
        case .test(let themes, let count, let range):
            return StepPlan(themes: themes, count: count, range: range, requireFlawless: true)
        case .lesson, .game:
            return nil
        }
    }

    private func clearStepState() {
        stepQueue = []
        stepSolved = 0
        stepFailed = false
        currentStep = nil
    }

    /// Puni red zadataka za korak Puta i pokrece prvi. Poziva se i pri ulasku na
    /// ekran i pri ponovnom pokretanju palog `test`-a — u oba slucaja zadaci su
    /// NOVI (upit je `ORDER BY RANDOM()`), sto je i smisao "krece ispocetka".
    func startStepPractice(step: CurriculumStep) {
        guard let plan = Self.plan(for: step) else {
            // Lekcija i partija imaju sopstvene ekrane; da neko ovde dovede
            // takav korak, prazan ekran bi bio gori od poruke.
            phase = .unavailable(Loc("Korak nije dostupan"))
            return
        }

        reloadPersistedProgress()
        clearStepState()
        currentStep = step
        mode = .step(id: step.id, requireFlawless: plan.requireFlawless)
        phase = .loading
        currentPuzzle = nil
        puzzleHadError = false
        awaitingOpponent = false
        loadGeneration += 1

        guard let repository else {
            phase = .unavailable(Loc("Baza zadataka nije dostupna")); return
        }

        // Prozor rejtinga presecen sa opsegom koraka; kad je presek prazan,
        // prednost ima opseg koraka (`PuzzleRepository.stepRatingWindow`).
        let window = PuzzleRepository.stepRatingWindow(
            playerRating: StatsManager.shared.puzzleRating,
            stepRange: plan.range)

        // Popustanje ide redom: prvo uzi prozor bez vec resenih, pa ceo opseg
        // koraka, pa isto to SA vec resenim. Tema se ne popusta ni u jednom
        // koraku — ona je ono sto korak uci; radije ponovljen zadatak na pravu
        // temu nego nov na pogresnu.
        let attempts: [(range: ClosedRange<Int>, excluding: Set<String>)] = [
            (window, solvedPuzzleIds),
            (plan.range, solvedPuzzleIds),
            (window, []),
            (plan.range, [])
        ]

        var best: [ChessPuzzle] = []
        for attempt in attempts {
            let found = repository.puzzles(themes: plan.themes,
                                           ratingRange: attempt.range,
                                           excluding: attempt.excluding,
                                           limit: plan.count)
            if found.count > best.count { best = found }
            if best.count >= plan.count { break }
        }

        // Kraci red od trazenog je prihvatljiv (korak se zavrsava kad se resi
        // sve sto je u redu); PRAZAN nije — to je ekran bez zadatka.
        guard !best.isEmpty else {
            phase = .unavailable(Loc("Nema dostupnih zadataka")); return
        }

        stepQueue = best
        setup(puzzle: best[0])
    }

    /// Ucitava sledeci zadatak iz reda. Radi isti reset kao `loadPuzzle()` (novi
    /// `loadGeneration` gasi zaostale odlozene poteze prethodnog zadatka), ali
    /// NE dira red ni brojac resenih.
    private func loadStepPuzzle(at index: Int) {
        guard index >= 0, index < stepQueue.count else { return }
        phase = .loading
        currentPuzzle = nil
        puzzleHadError = false
        awaitingOpponent = false
        loadGeneration += 1
        setup(puzzle: stepQueue[index])
    }

    // MARK: - Setup

    private func setup(puzzle: ChessPuzzle) {
        guard let state = GameState.fromFEN(puzzle.fen) else {
            phase = .unavailable(Loc("Neispravan FEN")); return
        }

        currentPuzzle = puzzle
        rawMoves      = puzzle.uciMoves
        movePointer   = 0
        selectedPosition     = nil
        legalMovesForSelected = []
        lastMove      = nil
        animatingPiece = nil
        flyingCapture  = nil

        playerColor = state.currentTurn.opposite
        gameState   = state

        let generation = loadGeneration
        Task {
            try? await Task.sleep(for: .milliseconds(400))
            guard generation == loadGeneration else { return }
            applyNextComputerMove()
        }
    }

    // MARK: - Square Tap

    func tap(position: Position) {
        guard isPlayerTurn else { return }

        if let move = legalMovesForSelected.first(where: { $0.to == position }) {
            attempt(move: move)
            return
        }

        let piece = gameState.board[position.row][position.col]

        if let piece, piece.color == playerColor {
            selectedPosition      = position
            legalMovesForSelected = MoveGenerator.legalMoves(for: playerColor, in: gameState)
                .filter { $0.from == position }
            if phase == .wrongMove { phase = .playing }
        } else {
            selectedPosition      = nil
            legalMovesForSelected = []
        }
    }

    // MARK: - Attempt Player Move

    private func attempt(move: ChessMove) {
        guard movePointer < rawMoves.count,
              let expected = ChessMove.fromUCI(rawMoves[movePointer], in: gameState)
        else { return }

        guard move.from == expected.from && move.to == expected.to else {
            Haptics.notification(.error)
            phase = .wrongMove
            selectedPosition      = nil
            legalMovesForSelected = []
            if !puzzleHadError {
                puzzleHadError = true
                StatsManager.shared.recordPuzzleFailed()
                if let currentPuzzle {
                    StatsManager.shared.applyPuzzleResult(puzzleRating: currentPuzzle.rating, solved: false)
                }
            }
            failStepIfTest()
            return
        }

        Haptics.impact(.medium)
        apply(move: move, isPlayerMove: true)
        movePointer += 1

        if movePointer >= rawMoves.count {
            phase = .solved
            Haptics.notification(.success)
            if let currentPuzzle {
                recordPuzzleIdSolved(currentPuzzle.puzzleId)
            }
            // Kalendarski dan se oznacava resenim SAMO u .daily rezimu —
            // vezbovni zadatak (Task 5) nije vezan ni za jedan datum.
            if mode == .daily {
                markCurrentSolved()
            }
            if !puzzleHadError {
                StatsManager.shared.recordPuzzleSolved()
                if let currentPuzzle {
                    StatsManager.shared.applyPuzzleResult(puzzleRating: currentPuzzle.rating, solved: true)
                }
            }
            advanceStepAfterSolve()
            return
        }

        phase = .playing
        awaitingOpponent = true
        let generation = loadGeneration
        Task {
            try? await Task.sleep(for: .milliseconds(600))
            guard generation == loadGeneration else { return }
            applyNextComputerMove()
        }
    }

    // MARK: - Napredovanje kroz korak Puta

    /// Zove se posle svakog resenog zadatka. U `test`-u greska korak vec obara
    /// pre ovoga, pa je ovde dovoljno brojati.
    private func advanceStepAfterSolve() {
        guard case .step(let stepId, let requireFlawless) = mode else { return }

        stepSolved += 1

        guard stepSolved >= stepQueue.count else {
            // Jos ima zadataka: kratka pauza da korisnik vidi da je resio, pa
            // sledeci. Odlozeni `Task` se, kao i svi ostali, gasi ako se u
            // medjuvremenu ucita nesto drugo.
            let generation = loadGeneration
            let next = stepSolved
            Task {
                try? await Task.sleep(for: .milliseconds(900))
                guard generation == loadGeneration else { return }
                loadStepPuzzle(at: next)
            }
            return
        }

        // `requireFlawless && stepFailed` je ovde nedostizno (pao test se
        // restartuje, a restart nulira `stepFailed`), ali stoji da bi pravilo
        // "test se zavrsava SAMO bez greske" bilo iskazano na mestu gde se
        // korak zaista zavrsava, a ne samo u toku koji do njega vodi.
        // Bez dodatnog haptika: `attempt()` je vec ispalio `.success` za resen
        // zadatak pre koji milisekundu, pa bi drugi bio samo buka.
        guard !(requireFlawless && stepFailed) else { return }
        ProgressStore.shared.completeStep(stepId)
    }

    /// Prva greska u `test` koraku obara ceo korak. Tabla ostaje na mestu ~1.4s
    /// (status kaze zasto), pa se korak pokrece iznova sa NOVIM zadacima.
    private func failStepIfTest() {
        guard case .step(_, true) = mode, !stepFailed else { return }
        stepFailed = true

        let generation = loadGeneration
        let step = currentStep
        Task {
            try? await Task.sleep(for: .milliseconds(1400))
            guard generation == loadGeneration, let step else { return }
            startStepPractice(step: step)
        }
    }

    // MARK: - Computer Move

    private func applyNextComputerMove() {
        // Prozor u kome tabla ne prima tapove se zatvara na SVAKOM izlazu.
        defer { awaitingOpponent = false }

        // Iscrpljena lista poteza je NORMALAN kraj zadatka, ne greska.
        guard movePointer < rawMoves.count else { return }

        // Potez koji se ne razresi jeste greska, i ne sme da se preskoci u
        // tisini: `phase` bi ostao na `.loading`, gde su sve kontrole
        // onemogucene a `actionButtons` prazan — ekran bez izlaza do restarta
        // aplikacije. (Za isporucenu bazu ovo je nedostizno: test
        // `everyPuzzleFirstMoveParsesInItsOwnPosition` proverava svih 20 000
        // prvih poteza. Guard stoji zbog buduce regeneracije baze.)
        guard let move = ChessMove.fromUCI(rawMoves[movePointer], in: gameState) else {
            phase = .unavailable(Loc("Zadatak je oštećen"))
            return
        }

        apply(move: move, isPlayerMove: false)
        movePointer += 1
        phase = .playing
    }

    // MARK: - Show Solution

    func showSolution() {
        // `!awaitingOpponent`: dok se ceka protivnikov odgovor faza je `.playing`,
        // pa je dugme "Prikazi resenje" vidljivo. Bez ovog gejta bi odlozeni
        // potez stigao usred reprodukcije, pregazio `.showingSolution` nazad u
        // `.playing` i dvaput odmakao `movePointer`.
        guard !awaitingOpponent, phase == .playing || phase == .wrongMove else { return }
        phase = .showingSolution
        if !puzzleHadError {
            puzzleHadError = true
            StatsManager.shared.recordPuzzleFailed()
            if let currentPuzzle {
                StatsManager.shared.applyPuzzleResult(puzzleRating: currentPuzzle.rating, solved: false)
            }
        }

        let generation = loadGeneration
        Task {
            while movePointer < rawMoves.count {
                // Reprodukcija traje ~700ms po potezu; za to vreme strelice za
                // datum nisu onemogucene (gase se samo na `.loading`). Bez ove
                // provere bi petlja nastavila da igra poteze NOVOG zadatka.
                guard generation == loadGeneration else { return }
                guard let move = ChessMove.fromUCI(rawMoves[movePointer], in: gameState)
                else { break }
                apply(move: move, isPlayerMove: movePointer % 2 == 1)
                movePointer += 1
                try? await Task.sleep(for: .milliseconds(700))
            }
            guard generation == loadGeneration else { return }
            phase = .solved
            // Ne označavamo kao rešeno kad se prikaže rešenje
        }
    }

    // MARK: - Apply Move (with animation)

    @MainActor func apply(move: ChessMove, isPlayerMove: Bool) {
        guard let piece = gameState.board[move.from.row][move.from.col] else { return }

        animatingPiece = AnimatingPiece(piece: piece, from: move.from, to: move.to)

        let captured = gameState.board[move.to.row][move.to.col]
        if let cap = captured {
            SoundManager.shared.playCapture()
            withAnimation(.easeOut(duration: 0.15)) {
                flyingCapture = FlyingCapture(piece: cap, fromPosition: move.to)
            }
        } else {
            SoundManager.shared.playMove()
        }

        gameState = gameState.applyingForSearch(move)
        lastMove  = move
        selectedPosition      = nil
        legalMovesForSelected = []

        Task {
            try? await Task.sleep(for: .milliseconds(280))
            withAnimation(.spring(duration: 0.25)) { flyingCapture = nil }
            try? await Task.sleep(for: .milliseconds(100))
            animatingPiece = nil
        }
    }
}

// MARK: - Preview

#Preview {
    PuzzleView(viewModel: PuzzleViewModel())
}
