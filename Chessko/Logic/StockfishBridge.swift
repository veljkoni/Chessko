import Foundation
import ChessKitEngine

// MARK: - Stockfish Bridge
//
// Wraps ChessKitEngine's Stockfish 17 UCI engine.
//
// Requirements (add to Xcode project as bundle resources):
//   • nn-37f18f62d772.nnue  (~15 MB, small network — required)
//   • nn-1111cefa1111.nnue  (~79 MB, big network — optional, stronger)
//
// Download from: https://tests.stockfishchess.org/nns
//
// Add SPM package in Xcode → File → Add Package Dependencies:
//   https://github.com/chesskit-app/chesskit-engine

actor StockfishBridge {

    // NNUE file URLs cached once at startup.
    private var nnueBig:   URL?
    private var nnueSmall: URL?

    // MARK: - Availability

    nonisolated var isAvailable: Bool {
        Bundle.main.url(forResource: "nn-37f18f62d772", withExtension: "nnue") != nil
            || Bundle.main.url(forResource: "nn-1111cefa1111", withExtension: "nnue") != nil
    }

    // MARK: - Lifecycle

    /// Cache NNUE URLs once at startup. The engine itself is created fresh
    /// for each search so the responseStream is always in a clean state.
    func start() async {
        nnueBig   = Bundle.main.url(forResource: "nn-1111cefa1111", withExtension: "nnue")
        nnueSmall = Bundle.main.url(forResource: "nn-37f18f62d772", withExtension: "nnue")
    }

    // MARK: - Best Move

    /// Creates a fresh Engine for every search so the responseStream is always
    /// in a clean state. The engine is started, used once, then released.
    func bestMove(for state: GameState, depth: Int = 12) async -> ChessMove? {
        // Fresh engine — ensures responseStream is pristine every call.
        let eng = Engine(type: .stockfish, loggingEnabled: false)
        await eng.start()

        // Wait up to 5 s for the engine to become ready.
        var waited = 0
        while !(await eng.isRunning), waited < 50 {
            try? await Task.sleep(for: .milliseconds(100))
            waited += 1
        }
        guard await eng.isRunning else { return nil }

        // Configure NNUE. EvalFile (big) is required — fall back to small if missing.
        let evalFile      = nnueBig ?? nnueSmall
        let evalFileSmall = nnueSmall ?? nnueBig
        if let url = evalFile      { await eng.send(command: .setoption(id: "EvalFile",      value: url.path())) }
        if let url = evalFileSmall { await eng.send(command: .setoption(id: "EvalFileSmall", value: url.path())) }

        // Subscribe to the response stream before sending any search commands.
        guard let stream = await eng.responseStream else { return nil }

        await eng.send(command: .position(.fen(state.fen)))
        await eng.send(command: .go(depth: depth))

        // Iterate until we get a bestmove response.
        for await response in stream {
            let raw = response.rawValue
            guard raw.hasPrefix("<bestmove>") else { continue }
            let tokens = raw.split(separator: " ")
            guard tokens.count >= 2 else { break }
            let uciMove = String(tokens[1])
            guard uciMove != "(none)" else { break }
            return parseUCI(uciMove, in: state)
        }
        return nil
    }

    // MARK: - Analiza pozicije

    struct PositionEval: Sendable {
        /// Ocena iz ugla strane koja je na potezu u toj poziciji.
        let score: EngineScore
        /// Potez koji motor smatra najboljim; `nil` u zavrsnoj poziciji.
        let bestMove: ChessMove?
    }

    /// Ocena zavrsne pozicije — bez pitanja motora.
    ///
    /// IZMERENO (simulator, 2026-09-11): za poziciju bez legalnih poteza
    /// Stockfish preko `ChessKitEngine`-a NE posalje nijednu `info … <score> …`
    /// liniju, nego ide pravo na `<bestmove> (none)`. Bez ove grane bi
    /// `evaluate` i `analyzeGame` vracali `nil` za SVAKU odigranu partiju, jer
    /// je poslednja od N+1 pozicija upravo mat ili pat.
    ///
    /// Vrednost se ne pogadja nego sledi iz pravila: ako je strana na potezu
    /// matirana, ocena iz NJENOG ugla je `mate(0)` (isto sto bi motor rekao sa
    /// `score mate 0`, tj. −10.000 centipiona); u svakom drugom slucaju bez
    /// poteza to je pat, dakle `cp(0)`.
    ///
    /// Namerno se NE gleda `state.status` — `GameState.fromFEN` ga ostavlja na
    /// `.playing` i za matiranu poziciju, pa bi oslanjanje na njega vezalo
    /// tacnost analize za to kako je pozicija nastala.
    private func terminalEval(for state: GameState) -> PositionEval? {
        guard MoveGenerator.legalMoves(for: state.currentTurn, in: state).isEmpty else { return nil }
        let mated = MoveGenerator.isInCheck(color: state.currentTurn, in: state)
        return PositionEval(score: mated ? .mate(0) : .cp(0), bestMove: nil)
    }

    /// Ocena jedne pozicije. Za razliku od `bestMove(for:depth:)`, cita i
    /// `info` linije da bi izvukla ocenu, ne samo `<bestmove>`.
    ///
    /// Uzima se POSLEDNJA vidjena ocena pre `<bestmove>`, jer motor tokom
    /// produbljivanja salje ocenu za svaku dubinu — a zanima nas ona sa pune
    /// dubine, ne prva koju je prijavio.
    func evaluate(state: GameState, depth: Int = 12) async -> PositionEval? {
        if let terminal = terminalEval(for: state) { return terminal }
        // Provera PRE pravljenja motora: u vec otkazanom zadatku motor se ne
        // pravi uopste, pa ne moze ni da ostane "neupitan".
        if Task.isCancelled { return nil }

        let eng = Engine(type: .stockfish, loggingEnabled: false)
        await eng.start()

        var waited = 0
        while !(await eng.isRunning), waited < 50 {
            try? await Task.sleep(for: .milliseconds(100))
            waited += 1
        }
        guard await eng.isRunning else {
            await eng.stop()
            return nil
        }

        let evalFile      = nnueBig ?? nnueSmall
        let evalFileSmall = nnueSmall ?? nnueBig
        if let url = evalFile      { await eng.send(command: .setoption(id: "EvalFile",      value: url.path())) }
        if let url = evalFileSmall { await eng.send(command: .setoption(id: "EvalFileSmall", value: url.path())) }

        guard let stream = await eng.responseStream else {
            await eng.stop()
            return nil
        }

        // Pozicija ide ODMAH po preuzimanju stream-a: motor koji se pokrene a
        // nikad ne dobije `position` obara SLEDECI motor u istom procesu
        // (SIGABRT, assert u `Position::set`). Posle ove linije nijedan izlaz
        // ne ostavlja "neupitan" motor.
        await eng.send(command: .position(.fen(state.fen)))
        await eng.send(command: .go(depth: depth))

        var lastScore: EngineScore?
        for await response in stream {
            let raw = response.rawValue
            if let s = UCIScoreParser.score(from: raw) {
                lastScore = s
                continue
            }
            guard raw.hasPrefix("<bestmove>") else { continue }
            let tokens = raw.split(separator: " ")
            let uci = tokens.count >= 2 ? String(tokens[1]) : "(none)"
            let move = uci == "(none)" ? nil : parseUCI(uci, in: state)
            // Bez ijedne ocene nema sta da se vrati — pozicija bez ocene bi u
            // racunici prosla kao cp(0), sto je tvrdnja da je izjednaceno.
            // `evaluate` sam gasi svoj motor na SVAKOM izlazu. Ranije se
            // oslanjao na `Engine.deinit`, koji gasi asinhrono i tek kad stigne;
            // u petlji bi to ostavilo vise motora da se gase uporedo, u istom
            // procesu u kom je vec zabelezen SIGABRT zbog motora koji se "pusti".
            guard let score = lastScore else {
                await eng.stop()
                return nil
            }
            await eng.stop()
            return PositionEval(score: score, bestMove: move)
        }
        await eng.stop()
        return nil
    }

    /// Ocena niza pozicija, redom. `onProgress(gotovo, ukupno)` se zove posle
    /// svake pozicije da ekran moze da prikaze napredak.
    ///
    /// Vraca `nil` ako ijedna pozicija ne uspe — delimicna analiza bi prikazala
    /// tacnost izracunatu iz dela partije, a korisnik bi je citao kao da vazi
    /// za celu.
    ///
    /// Otkazivanje: proverava `Task.isCancelled` pre svake pozicije, pa
    /// napustanje ekrana ne ostavlja motor da melje u pozadini.
    ///
    /// DELJENI MOTOR — odluka je IZMERENA, ne pretpostavljena (simulator
    /// iPhone 17, iOS 26.5, dubina 12, 2026-09-11):
    ///
    ///   20 pozicija, svez motor po poziciji: 25,06 s i 26,14 s, ocena 20/20
    ///   20 pozicija, jedan deljeni motor:     8,69 s i  8,65 s, ocena 20/20
    ///   81 pozicija, jedan deljeni motor:    24,1–24,8 s,       ocena 81/81
    ///
    /// Dva merenja po pristupu, u oba redosleda, da ubrzanje ne bude posledica
    /// zagrejanog simulatora. Deljeni motor je ~3x brzi i ne gubi nijednu
    /// ocenu ni na punoj duzini partije. 81 pretraga je analiza partije od 40
    /// poteza, dakle ~25 s deljenim motorom prema ~105 s svezim po poziciji.
    ///
    /// ZASTO ovo radi — NIJE utvrdjeno, i to je ovde zapisano namerno.
    ///
    /// Prva verzija ovog komentara tvrdila je da se `AsyncStream` zavrsava kad
    /// se njegov iterator ispusti, pa da je stari kvar od 2026-06-27 bio u
    /// `for await … in stream`, koji iterator ispusta na izlasku iz petlje.
    /// TA TVRDNJA JE EKSPERIMENTALNO OBORENA: u sondi nad golim `AsyncStream`-om
    /// `onTermination` se posle ispustanja iteratora NE poziva, a nov iterator
    /// uredno dobija sledecu vrednost. Iterator ne poseduje terminaciju — deli
    /// kontekst sa samom vrednoscu stream-a, koju `EngineConfiguration` drzi
    /// zivu za ceo zivot motora.
    ///
    /// Izmereno je dakle DA deljeni motor daje 81/81 u tri uzastopna prolaza,
    /// ali NIJE utvrdjeno zasto je stari obrazac padao. Dok se to ne utvrdi,
    /// ovo NIJE dozvola da se `bestMove(for:depth:)` prebaci na deljeni motor
    /// ili na dugoziveci iterator — taj put je vec jednom oboren u produkciji.
    /// `bestMove` zato ostaje netaknut.
    func analyzeGame(
        states: [GameState],
        depth: Int = 12,
        onProgress: @Sendable (Int, Int) -> Void
    ) async -> [PositionEval]? {
        guard !states.isEmpty else { return [] }

        // Zavrsne pozicije se ocenjuju bez motora (vidi `terminalEval`).
        // Racuna se ovde, jednom, da se `legalMoves` ne bi zvao dvaput po poziciji.
        let terminals = states.map(terminalEval(for:))

        // Ako NIJEDNA pozicija ne trazi motor, motor se ne pokrece.
        //
        // IZMERENO, ne pretpostavljeno: `Engine` koji se pokrene (a `start`
        // ucitava NNUE mrezu) pa se pusti a da nikad nije dobio `position`,
        // obara SLEDECI `Engine` u istom procesu — Stockfish padne na `assert`
        // u `Position::set` (SIGABRT). Ista sekvenca bez tog praznog motora
        // prolazi 3/3 puta; sa njim pada 2/3 puta.
        guard terminals.contains(where: { $0 == nil }) else {
            for i in states.indices { onProgress(i + 1, states.count) }
            return terminals.compactMap { $0 }
        }

        // Isto kao u `evaluate`: u vec otkazanom zadatku motor se ne pravi.
        if Task.isCancelled { return nil }

        let eng = Engine(type: .stockfish, loggingEnabled: false)
        let result = await analyze(states: states, terminals: terminals, depth: depth, using: eng, onProgress: onProgress)
        // Gasi se na SVAKOM izlazu, i na gresci i na otkazivanju. `defer` ovde
        // ne moze jer je gasenje `async`; zato jedan izlaz i eksplicitan `stop`.
        await eng.stop()
        return result
    }

    private func analyze(
        states: [GameState],
        terminals: [PositionEval?],
        depth: Int,
        using eng: Engine,
        onProgress: @Sendable (Int, Int) -> Void
    ) async -> [PositionEval]? {
        await eng.start()

        var waited = 0
        while !(await eng.isRunning), waited < 50 {
            try? await Task.sleep(for: .milliseconds(100))
            waited += 1
        }
        guard await eng.isRunning else { return nil }

        let evalFile      = nnueBig ?? nnueSmall
        let evalFileSmall = nnueSmall ?? nnueBig
        if let url = evalFile      { await eng.send(command: .setoption(id: "EvalFile",      value: url.path())) }
        if let url = evalFileSmall { await eng.send(command: .setoption(id: "EvalFileSmall", value: url.path())) }

        guard let stream = await eng.responseStream else { return nil }
        // JEDAN iterator za sve pretrage. NE zato sto bi ispustanje iteratora
        // zavrsilo stream (provereno da ne zavrsava), nego zato sto je to
        // oblik koji je izmeren kao ispravan — vidi objasnjenje iznad
        // `analyzeGame`.
        var iterator = stream.makeAsyncIterator()

        // Motor koji se pokrene a NIKAD ne dobije `position` obara SLEDECI
        // motor u istom procesu (SIGABRT, assert u `Position::set`), a `stop()`
        // ga od toga ne spasava — mereno, padalo je 2/3 puta i sa `stop()`-om.
        // Guard iznad `analyzeGame` pokriva slucaj kad nijedna pozicija ne
        // trazi pretragu, ali NE i otkazivanje: `Task.isCancelled` u prvoj
        // iteraciji petlje znaci da je korisnik napustio ekran tokom ~1 s
        // pokretanja motora, i tada bi motor ostao neupitan. Zato mu se odmah
        // salje bezopasna pocetna pozicija: posle ove linije nijedan izlaz ne
        // ostavlja neupitan motor, bez ijedne dodatne pretrage.
        await eng.send(command: .position(.startpos))

        var out: [PositionEval] = []
        out.reserveCapacity(states.count)

        for (i, state) in states.enumerated() {
            if Task.isCancelled { return nil }

            // Zavrsna pozicija: motor za nju ne posalje ocenu (vidi
            // `terminalEval`), pa se ni ne pita.
            if let terminal = terminals[i] {
                out.append(terminal)
                onProgress(i + 1, states.count)
                continue
            }

            await eng.send(command: .position(.fen(state.fen)))
            await eng.send(command: .go(depth: depth))

            var lastScore: EngineScore?
            var eval: PositionEval?
            while let response = await iterator.next() {
                let raw = response.rawValue
                if let s = UCIScoreParser.score(from: raw) {
                    lastScore = s
                    continue
                }
                guard raw.hasPrefix("<bestmove>") else { continue }
                let tokens = raw.split(separator: " ")
                let uci = tokens.count >= 2 ? String(tokens[1]) : "(none)"
                let move = uci == "(none)" ? nil : parseUCI(uci, in: state)
                // Bez ijedne ocene nema sta da se vrati — pozicija bez ocene bi
                // u racunici prosla kao cp(0), sto je tvrdnja da je izjednaceno.
                guard let score = lastScore else { return nil }
                eval = PositionEval(score: score, bestMove: move)
                break
            }
            guard let eval else { return nil }

            out.append(eval)
            onProgress(i + 1, states.count)
        }
        return out
    }

    // MARK: - UCI Move Parser

    /// Converts UCI move string (e.g. "e2e4", "e7e8q") to ChessMove
    /// by matching against legal moves so flags (castling, en passant) are set correctly.
    private func parseUCI(_ uci: String, in state: GameState) -> ChessMove? {
        let chars = Array(uci)
        guard chars.count >= 4,
              let fc = chars[0].asciiValue, let fr = chars[1].asciiValue,
              let tc = chars[2].asciiValue, let tr = chars[3].asciiValue else { return nil }

        let fromCol = Int(fc) - 97          // 'a'=0 … 'h'=7
        let fromRow = 7 - (Int(fr) - 49)   // '1'=row7 … '8'=row0
        let toCol   = Int(tc) - 97
        let toRow   = 7 - (Int(tr) - 49)

        guard (0...7).contains(fromCol), (0...7).contains(fromRow),
              (0...7).contains(toCol),   (0...7).contains(toRow) else { return nil }

        let from = Position(row: fromRow, col: fromCol)
        let to   = Position(row: toRow,   col: toCol)
        let legal = MoveGenerator.legalMoves(for: state.currentTurn, in: state)

        if chars.count >= 5 {
            // Promotion suffix: q r b n
            let promType: PieceType = switch chars[4] {
            case "r": .rook
            case "b": .bishop
            case "n": .knight
            default:  .queen
            }
            return legal.first { $0.from == from && $0.to == to && $0.flag == .promotion(promType) }
        }

        // Matches castling, en passant, and normal moves by from/to coordinates
        return legal.first { $0.from == from && $0.to == to }
    }
}
