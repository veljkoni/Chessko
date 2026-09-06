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
enum PuzzleMode {
    case daily
    case practice
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
        let arr = UserDefaults.standard.stringArray(forKey: "solvedPuzzleIds") ?? []
        return Set(arr)
    }()

    private func recordPuzzleIdSolved(_ id: String) {
        guard solvedPuzzleIds.insert(id).inserted else { return }
        UserDefaults.standard.set(Array(solvedPuzzleIds), forKey: "solvedPuzzleIds")
    }

    // MARK: - Date Navigation

    var selectedDate: Date = Calendar.current.startOfDay(for: Date())
    private(set) var solvedDates: Set<String> = []

    private static let cal = Calendar.current
    private let solvedKey = "chessko.solvedDates"

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

    // MARK: - Computed

    var isFlipped: Bool { playerColor == .black }

    var isPlayerTurn: Bool {
        phase == .playing || phase == .wrongMove
    }

    var statusMessage: String {
        switch phase {
        case .loading:         return Loc("Učitavam zadatak...")
        case .unavailable(let msg): return msg
        case .playing:
            return playerColor == .white
                ? Loc("Pronađi pravi potez za bele")
                : Loc("Pronađi pravi potez za crne")
        case .wrongMove:       return Loc("Pogrešno. Pokušaj ponovo.")
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
        loadSolvedDates()
        loadPuzzle()
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
        mode = .daily
        phase = .loading
        currentPuzzle = nil
        puzzleHadError = false

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

        mode = .practice
        phase = .loading
        currentPuzzle = nil
        puzzleHadError = false

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

        Task {
            try? await Task.sleep(for: .milliseconds(400))
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
            return
        }

        phase = .playing
        Task {
            try? await Task.sleep(for: .milliseconds(600))
            applyNextComputerMove()
        }
    }

    // MARK: - Computer Move

    private func applyNextComputerMove() {
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
        guard phase == .playing || phase == .wrongMove else { return }
        phase = .showingSolution
        if !puzzleHadError {
            puzzleHadError = true
            StatsManager.shared.recordPuzzleFailed()
            if let currentPuzzle {
                StatsManager.shared.applyPuzzleResult(puzzleRating: currentPuzzle.rating, solved: false)
            }
        }

        Task {
            while movePointer < rawMoves.count {
                guard let move = ChessMove.fromUCI(rawMoves[movePointer], in: gameState)
                else { break }
                apply(move: move, isPlayerMove: movePointer % 2 == 1)
                movePointer += 1
                try? await Task.sleep(for: .milliseconds(700))
            }
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
