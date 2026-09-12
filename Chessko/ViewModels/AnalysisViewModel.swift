import Foundation
import Observation

// MARK: - Analysis View Model
//
// Pokrece analizu partije i drzi njen tok. Sva racunica je u `GameAnalysis`
// (Foundation-only, testirano); ovde je samo orkestracija: sta se salje motoru,
// kako se prijavljuje napredak i sta se desava kad korisnik ode sa ekrana.

@Observable
@MainActor
final class AnalysisViewModel {

    enum Phase: Equatable {
        case idle
        case running(done: Int, total: Int)
        case done(GameAnalysis)
        case failed(String)
    }

    private(set) var phase: Phase = .idle

    private let stockfish = StockfishBridge()

    // `@ObservationIgnored`: bez ovoga `@Observable` (`@ObservationTracked` na
    // `task`) obara `deinit { task?.cancel() }` sa "main actor-isolated
    // property 'task' can not be referenced from a nonisolated context" —
    // IZMERENO xcodebuild-om, ne pretpostavljeno (brief je tvrdio da se ovo
    // kompajlira; ne kompajlira se sa `@Observable`, samostalan `swiftc` test
    // bez makroa je lazno prosao). `task` ionako ne utice na prikaz (samo
    // `phase` to radi), pa mu praćenje ne treba.
    @ObservationIgnored
    private var task: Task<Void, Never>?

    /// Redni broj pokretanja analize. Isti obrazac koji `PuzzleViewModel` vec
    /// nosi kao `loadGeneration` (uveden 2026-09-07 zbog iste klase greske).
    ///
    /// Sluzi kao zastita od ovoga: `task?.cancel()` otkazuje SAMO obuhvatajuci
    /// `Task`, a izvestaji o napretku se salju kroz zasebne `Task { @MainActor }`
    /// jedinice koje NISU njegova deca i ne nasledjuju otkazivanje. Bez identiteta
    /// pokretanja, `isRunning` ih ne razlikuje — posle `cancel()` pa `start()` je
    /// ponovo `true`.
    ///
    /// ALI: da je scenario stvarno dostizan NIJE dokazano, i to se ovde kaze
    /// izricito. Sa uklonjena sva tri `guard`-a izmereno je **0 zastarelih
    /// izvestaja kroz 14 pokretanja**. Razlog je sto `AsyncStream.Iterator.next()`
    /// po otkazivanju zavrsi iteraciju, pa se do poslednjeg `onProgress` nikad ne
    /// stigne, a zavrsna stanja vec pokriva `guard !Task.isCancelled`.
    ///
    /// Brojac ostaje jer je tacan, kosta jedan `Int` i cuva invarijantu koja je
    /// tacna bez obzira na to sto je trenutni tok poziva ne dodiruje. Ne brise se,
    /// ali se ni ne navodi kao dokazana zastita.
    @ObservationIgnored
    private var generation = 0

    /// Dubina je fiksna po spec-u 5.5 — analiza mora da traje predvidivo.
    private static let depth = 12

    var isRunning: Bool {
        if case .running = phase { return true }
        return false
    }

    /// `states` je `GameViewModel.allHistoryStates`: N+1 pozicija za N poteza,
    /// pocev od pocetne. `notations` je `gameState.moveNotations`, duzine N.
    func start(states: [(state: GameState, lastMove: ChessMove?)], notations: [String]) {
        guard !isRunning else { return }

        // Neslaganje duzina nije korisnikov problem i ne sme da srusi ekran.
        guard states.count == notations.count + 1, !notations.isEmpty else {
            phase = .failed(Loc("Nema dovoljno poteza za analizu."))
            return
        }
        guard stockfish.isAvailable else {
            phase = .failed(Loc("Analiza nije dostupna — motor nije pronađen."))
            return
        }

        generation += 1
        let gen = generation

        phase = .running(done: 0, total: states.count)
        let positions = states.map(\.state)
        let playedMoves = states.dropFirst().map(\.lastMove)

        task = Task { [weak self] in
            guard let self else { return }
            let evals = await self.stockfish.analyzeGame(
                states: positions,
                depth: Self.depth
            ) { done, total in
                Task { @MainActor [weak self] in
                    guard let self, self.generation == gen, self.isRunning else { return }
                    self.phase = .running(done: done, total: total)
                }
            }

            guard !Task.isCancelled, self.generation == gen else { return }
            guard let evals, evals.count == positions.count else {
                self.phase = .failed(Loc("Analiza nije uspela."))
                return
            }

            // Potez motora iz pozicije `i` prema stvarno odigranom potezu.
            // `ChessMove ==` poredi from+to+flag, pa je poredjenje tacno i za
            // promociju i za rokadu.
            let matched: [Bool] = playedMoves.enumerated().map { i, played in
                guard let played, let best = evals[i].bestMove else { return false }
                return played == best
            }

            guard self.generation == gen else { return }
            self.phase = .done(GameAnalysis.build(
                notations: notations,
                scores: evals.map(\.score),
                engineBestMatched: matched
            ))
        }
    }

    /// Zove se kad korisnik napusti ekran. Bez ovoga motor nastavi da melje
    /// 81 poziciju u pozadini iako rezultat vise nema ko da vidi.
    func cancel() {
        // Podizanje generacije je ono sto stvarno gasi zaostale izvestaje:
        // `task.cancel()` ne dopire do `Task { @MainActor }` jedinica koje je
        // `onProgress` vec stavio u red.
        generation += 1
        task?.cancel()
        task = nil
        if isRunning { phase = .idle }
    }

    deinit { task?.cancel() }
}
