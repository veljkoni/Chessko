import Testing
@testable import ChesskoEngine

// MARK: - EngineScore

@Test func centipawnScorePassesThrough() {
    #expect(EngineScore.cp(0).centipawns == 0)
    #expect(EngineScore.cp(250).centipawns == 250)
    #expect(EngineScore.cp(-80).centipawns == -80)
}

@Test func mateScoreMapsFarAboveAnyCentipawnScore() {
    // Mat u 1 mora biti bolji od mata u 2, a oba daleko iznad bilo koje
    // realne centipionske ocene (koja se u praksi drzi unutar +-5000).
    #expect(EngineScore.mate(1).centipawns > EngineScore.mate(2).centipawns)
    #expect(EngineScore.mate(2).centipawns > 5000)
    #expect(EngineScore.mate(-1).centipawns < EngineScore.mate(-2).centipawns)
    #expect(EngineScore.mate(-2).centipawns < -5000)
}

// MARK: - cpLoss

@Test func cpLossIsBeforePlusAfterBecausePerspectiveFlips() {
    // `before` je iz ugla igraca koji vuce potez; `after` je iz ugla PROTIVNIKA,
    // jer je posle poteza on na potezu. Zato se sabiraju, ne oduzimaju.
    // Bio +50 za mene, posle poteza +50 za protivnika => izgubio sam 100.
    #expect(GameAnalysis.cpLoss(before: .cp(50), after: .cp(50)) == 100)
    // Bio +50 za mene, posle poteza -50 za protivnika (= +50 za mene) => nista.
    #expect(GameAnalysis.cpLoss(before: .cp(50), after: .cp(-50)) == 0)
}

@Test func cpLossNeverGoesNegative() {
    // Pretraga na fiksnoj dubini ume da oceni poziciju POSLE poteza bolje nego
    // onu pre njega. To nije "negativan gubitak" nego nula.
    #expect(GameAnalysis.cpLoss(before: .cp(10), after: .cp(-200)) == 0)
}

@Test func cpLossIsCappedSoOneMissedMateCannotEatTheWholeGame() {
    // Propusten mat daje razliku od ~20.000 centipiona. Bez granice bi jedan
    // takav potez sam odredio prosek cele partije.
    let loss = GameAnalysis.cpLoss(before: .mate(1), after: .cp(0))
    #expect(loss == 1000)
    #expect(loss > 300)  // i dalje uredno iznad praga za promasaj
}

// MARK: - Klasifikacija (pragovi doslovno iz spec-a)

@Test func classificationFollowsSpecThresholds() {
    #expect(MoveClass.classify(cpLoss: 0,   playedEngineBest: true)  == .best)
    #expect(MoveClass.classify(cpLoss: 0,   playedEngineBest: false) == .excellent)
    #expect(MoveClass.classify(cpLoss: 19,  playedEngineBest: false) == .excellent)
    #expect(MoveClass.classify(cpLoss: 20,  playedEngineBest: false) == .good)
    #expect(MoveClass.classify(cpLoss: 49,  playedEngineBest: false) == .good)
    #expect(MoveClass.classify(cpLoss: 50,  playedEngineBest: false) == .inaccuracy)
    #expect(MoveClass.classify(cpLoss: 99,  playedEngineBest: false) == .inaccuracy)
    #expect(MoveClass.classify(cpLoss: 100, playedEngineBest: false) == .mistake)
    #expect(MoveClass.classify(cpLoss: 299, playedEngineBest: false) == .mistake)
    #expect(MoveClass.classify(cpLoss: 300, playedEngineBest: false) == .blunder)
    #expect(MoveClass.classify(cpLoss: 999, playedEngineBest: false) == .blunder)
}

@Test func engineBestMoveWinsOverThresholdsEvenWithSmallLoss() {
    // Potez motora je po definiciji najbolji, i kad merenje da mali gubitak
    // (razlicite dubine daju razlicite ocene iste pozicije).
    #expect(MoveClass.classify(cpLoss: 15, playedEngineBest: true) == .best)
}

// MARK: - Procenat tacnosti (formula doslovno iz spec-a, nad gubitkom procenta pobede)

@Test func accuracyMatchesSpecFormula() {
    // 103.1668 * exp(-0.04354 * x) - 3.1669
    #expect(abs(GameAnalysis.accuracy(winPercentLoss: 0) - 99.9999) < 0.01)
    #expect(abs(GameAnalysis.accuracy(winPercentLoss: 10) - 63.5) < 0.5)
    #expect(abs(GameAnalysis.accuracy(winPercentLoss: 50) - 8.5) < 0.5)
}

@Test func accuracyIsClampedToZeroAndHundred() {
    // Formula za veliko x ide ispod nule (-3.1669 u limitu).
    #expect(GameAnalysis.accuracy(winPercentLoss: 1000) == 0)
    #expect(GameAnalysis.accuracy(winPercentLoss: 0) <= 100)
    #expect(GameAnalysis.accuracy(winPercentLoss: -5) == 100)
}

@Test func accuracyFallsAsAverageLossRises() {
    let a = GameAnalysis.accuracy(winPercentLoss: 5)
    let b = GameAnalysis.accuracy(winPercentLoss: 25)
    let c = GameAnalysis.accuracy(winPercentLoss: 80)
    #expect(a > b)
    #expect(b > c)
}

// MARK: - Sklapanje cele analize

@Test func buildPairsScoresIntoMovesAndSplitsBySide() {
    // 3 poteza => 4 ocene. Potezi 0 i 2 su beli, potez 1 je crni.
    let analysis = GameAnalysis.build(
        notations: ["e4", "e5", "Sf3"],
        scores: [.cp(20), .cp(-20), .cp(20), .cp(-20)],
        engineBestMatched: [true, true, true]
    )
    #expect(analysis.moves.count == 3)
    #expect(analysis.moves[0].byWhite == true)
    #expect(analysis.moves[1].byWhite == false)
    #expect(analysis.moves[2].byWhite == true)
    #expect(analysis.moves[0].notation == "e4")
    #expect(analysis.moves.allSatisfy { $0.cpLoss == 0 })
    // Savrsena igra NE daje tacno 100: formula iz spec-a u nuli daje 99.9999
    // (103.1668 - 3.1669). To nije greska nego oblik same formule, i korisnik
    // ionako vidi "100.0%" jer se prikazuje na jednu decimalu. Zato tolerancija,
    // a ne `== 100` — inace bi test terao da se formula tiho odvoji od spec-a.
    #expect(analysis.whiteAccuracy > 99.99)
    #expect(analysis.blackAccuracy > 99.99)
}

@Test func buildComputesEachSideAccuracyFromOnlyThatSideMoves() {
    // Beli igra savrseno, crni gubi po 200 centipiona po potezu.
    //
    // Ocene se moraju izabrati tako da SVAKI beli potez ima gubitak 0, ne samo
    // prvi: gubitak poteza `i` je `scores[i] + scores[i+1]`, pa jedna ocena
    // ulazi u DVA susedna poteza. Niz [0, 0, 200, -200, 400] daje belom 0 i 0,
    // a crnom 200 i 200.
    let analysis = GameAnalysis.build(
        notations: ["e4", "a5", "Sf3", "b5"],
        scores: [.cp(0), .cp(0), .cp(200), .cp(-200), .cp(400)],
        engineBestMatched: [false, false, false, false]
    )
    #expect(analysis.moves[0].cpLoss == 0)    // beli
    #expect(analysis.moves[1].cpLoss == 200)  // crni
    #expect(analysis.moves[2].cpLoss == 0)    // beli
    #expect(analysis.moves[3].cpLoss == 200)  // crni
    #expect(analysis.whiteAccuracy > 99.99)
    #expect(analysis.whiteAccuracy > analysis.blackAccuracy)
}

@Test func turningPointIsTheBiggestLossButOnlyIfItActuallyHurts() {
    let clean = GameAnalysis.build(
        notations: ["e4", "e5"],
        scores: [.cp(0), .cp(-5), .cp(5)],
        engineBestMatched: [false, false]
    )
    // Najveci gubitak je ~5 centipiona — to nije prelomni potez ni u jednoj partiji.
    #expect(clean.turningPoint == nil)

    let withBlunder = GameAnalysis.build(
        notations: ["e4", "e5", "Lc4"],
        scores: [.cp(0), .cp(0), .cp(0), .cp(400)],
        engineBestMatched: [false, false, false]
    )
    #expect(withBlunder.turningPoint?.notation == "Lc4")
    #expect(withBlunder.turningPoint?.cpLoss == 400)
}

@Test func turningPointNeverPicksAMoveTheEngineItselfWouldPlay() {
    // `classify` daje `.best` cim je odigran potez motora, i kad izmeren
    // gubitak ispadne velik (dve nezavisne pretrage se ne poklope do
    // centipiona). Takav potez ne sme da bude proglasen prelomnim — kartica bi
    // ga prikazala kao najveci gubitak, a obojila kao najbolji potez.
    let analysis = GameAnalysis.build(
        notations: ["Sxd7", "Kh8"],
        scores: [.cp(0), .cp(1000), .cp(-800)],
        engineBestMatched: [true, false]
    )
    #expect(analysis.moves[0].cpLoss == 1000)
    #expect(analysis.moves[0].moveClass == .best)
    #expect(analysis.moves[1].cpLoss == 200)
    // Prelomni je drugi potez, iako je njegov gubitak PETOSTRUKO manji.
    #expect(analysis.turningPoint?.notation == "Kh8")
}

@Test func turningPointIsNilWhenEveryLosingMoveWasTheEngineMove() {
    let analysis = GameAnalysis.build(
        notations: ["Sxd7"],
        scores: [.cp(0), .cp(1000)],
        engineBestMatched: [true]
    )
    #expect(analysis.turningPoint == nil)
}

@Test func buildRejectsMismatchedInputLengthsInsteadOfCrashing() {
    // N poteza trazi tacno N+1 ocena. Neslaganje je greska pozivaoca i mora
    // da vrati praznu analizu, ne da srusi proces indeksiranjem van granica.
    let bad = GameAnalysis.build(
        notations: ["e4", "e5"],
        scores: [.cp(0), .cp(0)],
        engineBestMatched: [false, false]
    )
    #expect(bad.moves.isEmpty)
    #expect(bad.turningPoint == nil)
}

/// Prazna partija je JEDINI slucaj u kome je tacnost tacno 100: nema nijednog
/// poteza, pa se formula i ne primenjuje — `build` vraca zastitnu vrednost.
@Test func emptyGameProducesEmptyAnalysisWithFullAccuracy() {
    let empty = GameAnalysis.build(notations: [], scores: [.cp(0)], engineBestMatched: [])
    #expect(empty.moves.isEmpty)
    #expect(empty.whiteAccuracy == 100)
    #expect(empty.blackAccuracy == 100)
}

// MARK: - UCI parser ocene
//
// SVE linije ispod su STVARNE — dobijene kompajliranjem i pokretanjem same
// biblioteke (ChessKitEngine 0.7.0), ne izmisljene. Prva verzija ovih testova
// koristila je `<score> cp 34`, oblik koji biblioteka nikad ne emituje, pa su
// testovi prolazili nad parserom koji u aplikaciji ne bi radio nijednom.

@Test func parsesCentipawnScoreFromRealInfoLine() {
    let line = "<info> <depth> 12 <score> <cp> 34.0"
    #expect(UCIScoreParser.score(from: line) == .cp(34))
}

@Test func parsesNegativeCentipawnScore() {
    #expect(UCIScoreParser.score(from: "<info> <score> <cp> -250.0") == .cp(-250))
}

@Test func parsesZeroCentipawnScore() {
    // Nula mora da prodje kao vrednost, ne da se pobrka sa "nema ocene".
    #expect(UCIScoreParser.score(from: "<info> <score> <cp> 0.0") == .cp(0))
}

@Test func parsesMateScoreInBothDirections() {
    #expect(UCIScoreParser.score(from: "<info> <score> <mate> 3") == .mate(3))
    #expect(UCIScoreParser.score(from: "<info> <score> <mate> -2") == .mate(-2))
}

@Test func readsCentipawnsAsDecimalBecauseLibraryTypesThemAsDouble() {
    // `EngineResponse.Info.Score.cp` je `Double?`, pa vrednost uvek nosi
    // decimalu. Parser koji je cita kao `Int` vraca nil na SVAKOJ stvarnoj
    // liniji — tacno to je bio defekt prve verzije.
    #expect(UCIScoreParser.score(from: "<info> <score> <cp> 34.0") != nil)
    #expect(Int("34.0") == nil)
}

@Test func ignoresBoundQualifierThatFollowsTheValue() {
    #expect(UCIScoreParser.score(from: "<info> <score> <cp> 34.0 <upperbound>") == .cp(34))
}

@Test func returnsNilForLinesWithoutScore() {
    #expect(UCIScoreParser.score(from: "<bestmove> e2e4 <ponder> e7e5") == nil)
    #expect(UCIScoreParser.score(from: "<info> <depth> 12 <nodes> 4000") == nil)
    #expect(UCIScoreParser.score(from: "") == nil)
}

@Test func returnsNilWhenScoreTagIsTruncatedOrUnparsable() {
    // Motor je prekinut usred linije. Bolje nista nego pogresna ocena.
    #expect(UCIScoreParser.score(from: "<info> <score>") == nil)
    #expect(UCIScoreParser.score(from: "<info> <score> <cp>") == nil)
    #expect(UCIScoreParser.score(from: "<info> <score> <cp> abc") == nil)
}

@Test func returnsNilWhenScoreBlockCarriesNeitherCpNorMate() {
    // `<score>` postoji ali odmah sledi drugi tag — nema sta da se procita.
    #expect(UCIScoreParser.score(from: "<info> <score> <nodes> 4000") == nil)
}

// MARK: - Dizajnerski pregled § 6 / § 17: tačnost „0,0 %" posle 8–9 poteza

/// Ocene iz zadatih gubitaka: `loss_i = s[i] + s[i+1]`, pa `s[i+1] = loss_i - s[i]`.
/// Tako test zadaje gubitke kakve bi motor stvarno dao (ne izmišlja ocene).
private func scores(start: Int, losses: [Int]) -> [EngineScore] {
    var s = [start]
    for loss in losses { s.append(loss - s.last!) }
    return s.map { .cp($0) }
}

/// Reprodukcija snimaka 52, 64, 71: partija od 9 poteza, crni igra solidno i
/// napravi JEDAN promašaj (ostavi damu). Do popravke je crni dobijao tačno 0,0 %,
/// jer je formula, čije su konstante Lichess-ove za GUBITAK PROCENTA POBEDE,
/// primenjivana na PROSEČAN GUBITAK U CENTIPIONIMA: već prosek od ~80 cp daje 0.
@Test func shortGameWithOneBlunderDoesNotCollapseAccuracyToZero() {
    // beli / crni naizmenično; crni: 10, 0, 5, 0, 900 (promašaj), 0, 10, 0, 5
    let losses = [0, 10, 0, 0, 0, 5, 5, 0, 0, 900, 0, 0, 10, 10, 0, 0, 0, 5]
    let analysis = GameAnalysis.build(
        notations: losses.indices.map { "m\($0)" },
        scores: scores(start: 20, losses: losses),
        engineBestMatched: losses.map { _ in false }
    )
    #expect(analysis.moves[9].moveClass == .blunder)
    #expect(analysis.blackAccuracy != 0)
    #expect(analysis.blackAccuracy < analysis.whiteAccuracy)
    #expect(analysis.whiteAccuracy > 90)
    // Lichess `gameAccuracy` (harmonijska + ponderisana sredina). Vrednosti su iz
    // nezavisne Python reference tog algoritma, ne iz ovog koda. Aritmetička
    // sredina (stanje do 2026-10) bi dala 89,46 — promašaj bi se razvodnio.
    #expect(abs(analysis.blackAccuracy - 44.27) < 0.05)
    #expect(abs(analysis.whiteAccuracy - 99.71) < 0.05)
}

/// Tačno jedna težina po potezu, uvek u Lichess opsegu 0,5…12.
@Test func volatilityWeightsHaveOnePerMoveAndStayInRange() {
    for moveCount in [1, 2, 9, 25, 120] {
        let losses = (0..<moveCount).map { $0 % 7 == 3 ? 400 : 5 }
        let weights = GameAnalysis.volatilityWeights(scores: scores(start: 30, losses: losses))
        #expect(weights.count == moveCount)
        #expect(weights.allSatisfy { $0 >= 0.5 && $0 <= 12 })
    }
    // Mirna partija: sve težine na donjoj granici.
    let calm = GameAnalysis.volatilityWeights(scores: scores(start: 0, losses: Array(repeating: 0, count: 20)))
    #expect(calm.allSatisfy { $0 == 0.5 })
}

/// Promašaj u izgubljenoj poziciji košta manje od istog promašaja u izjednačenoj —
/// tako radi procenat pobede (Lichess), a prosečan gubitak u centipionima ne.
@Test func accuracyWeighsLossByWinningChancesNotRawCentipawns() {
    let even = GameAnalysis.winPercentLoss(before: .cp(0), after: .cp(300))
    let alreadyLost = GameAnalysis.winPercentLoss(before: .cp(-900), after: .cp(1200))
    #expect(even > alreadyLost)
    #expect(GameAnalysis.winPercentLoss(before: .cp(50), after: .cp(-50)) == 0)
}

@Test func winPercentIsFiftyAtEqualAndMonotone() {
    #expect(abs(GameAnalysis.winPercent(centipawns: 0) - 50) < 1e-9)
    #expect(GameAnalysis.winPercent(centipawns: 300) > GameAnalysis.winPercent(centipawns: 100))
    #expect(GameAnalysis.winPercent(centipawns: 100_000) < 100)
    #expect(GameAnalysis.winPercent(centipawns: -100_000) > 0)
}

/// Ispod praga poteza po strani tačnost se ne prikazuje („—").
@Test func accuracyIsWithheldBelowMoveThreshold() {
    let n = GameAnalysis.minMovesForAccuracy
    // 2n − 1 poluteza: beli ima n poteza, crni n − 1.
    let losses = Array(repeating: 0, count: 2 * n - 1)
    let analysis = GameAnalysis.build(
        notations: losses.indices.map { "m\($0)" },
        scores: scores(start: 0, losses: losses),
        engineBestMatched: losses.map { _ in true }
    )
    #expect(analysis.reliableAccuracy(white: true) != nil)
    #expect(analysis.reliableAccuracy(white: false) == nil)
}

// MARK: - Objašnjenje prelomnog poteza za početnike (§ 6)

@Test func beginnerExplanationRoundsPawnsAndDetectsMates() {
    func move(_ before: EngineScore, _ after: EngineScore) -> AnalyzedMove {
        GameAnalysis.build(notations: ["g4"], scores: [before, after], engineBestMatched: [false]).moves[0]
    }
    #expect(move(.cp(0), .cp(233)).beginnerExplanation == .pawnsLost(2))
    #expect(move(.cp(0), .cp(260)).beginnerExplanation == .pawnsLost(3))
    #expect(move(.cp(0), .cp(110)).beginnerExplanation == .pawnsLost(1))
    #expect(move(.cp(0), .mate(2)).beginnerExplanation == .allowedMate)
    #expect(move(.mate(1), .cp(0)).beginnerExplanation == .missedMate)
    // Već matiran i dalje matiran: nije „dozvolio mat".
    #expect(move(.mate(-3), .mate(2)).beginnerExplanation != .allowedMate)
}

@Test func lossLabelUsesLocalDecimalAndTypographicMinus() {
    #expect(EvaluationLabel.loss(cpLoss: 233, languageCode: "sr") == "\u{2212}2,3")
    #expect(EvaluationLabel.loss(cpLoss: 233, languageCode: "en") == "\u{2212}2.3")
    #expect(EvaluationLabel.loss(cpLoss: 1000, languageCode: "sr") == "\u{2212}10,0")
}
