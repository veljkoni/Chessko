import Foundation

// MARK: - Analiza partije: model i matematika
//
// Ovaj fajl NE zna za Stockfish ni za SwiftUI. Sve sto radi je racunica nad
// ocenama koje mu neko drugi doda, pa se u celosti testira u SwiftPM paketu.
// Motor je zamenljiv; ova pravila nisu.

/// Ocena pozicije iz ugla strane koja je NA POTEZU.
enum EngineScore: Equatable, Sendable {
    case cp(Int)
    /// Broj poteza do mata. Pozitivno = strana na potezu matira.
    case mate(Int)

    /// Jedinstvena centipionska vrednost za poredjenje.
    ///
    /// Mat nema centipionsku ocenu, a cela racunica ispod je centipionska, pa
    /// se mora preslikati. Uslov je samo da mat bude (a) daleko iznad svake
    /// realne centipionske ocene i (b) monoton — mat u 1 bolji od mata u 2.
    var centipawns: Int {
        switch self {
        case .cp(let v):
            return v
        case .mate(let n):
            let distance = min(abs(n), 99)
            return n > 0 ? (10_000 - distance) : (-10_000 + distance)
        }
    }
}

/// Klasa odigranog poteza. Pragovi su iz spec-a 5.5 i ne smeju se menjati
/// bez izmene spec-a — prikazuju se korisniku kao ocena njegove igre.
enum MoveClass: String, CaseIterable, Sendable {
    case best        // odigran je potez motora
    case excellent   // < 20
    case good        // < 50
    case inaccuracy  // < 100
    case mistake     // < 300
    case blunder     // >= 300

    static func classify(cpLoss: Int, playedEngineBest: Bool) -> MoveClass {
        // Potez motora je po definiciji najbolji. Provera ide PRVA jer merenje
        // ume da da mali gubitak i za potez motora: pozicija pre poteza i
        // pozicija posle njega pretrazuju se nezavisno, na istoj dubini ali iz
        // razlicitih cvorova, pa se ocene ne poklope do centipiona.
        if playedEngineBest { return .best }
        switch cpLoss {
        case ..<20:  return .excellent
        case ..<50:  return .good
        case ..<100: return .inaccuracy
        case ..<300: return .mistake
        default:     return .blunder
        }
    }
}

struct AnalyzedMove: Sendable, Identifiable, Equatable {
    /// Redni broj poluteza, od 0. `ply 0` je prvi beli potez.
    let ply: Int
    let notation: String
    let byWhite: Bool
    let cpLoss: Int
    let moveClass: MoveClass
    let scoreBefore: EngineScore
    let scoreAfter: EngineScore

    var id: Int { ply }

    /// Broj poteza kako ga korisnik broji (1, 1, 2, 2, 3, …).
    var moveNumber: Int { ply / 2 + 1 }

    /// „17.Lc4" ili „17…Lc4" — oblik iz spec-a za prelomni potez.
    var displayNotation: String {
        byWhite ? "\(moveNumber).\(notation)" : "\(moveNumber)…\(notation)"
    }
}

struct GameAnalysis: Sendable, Equatable {
    let moves: [AnalyzedMove]
    let whiteAccuracy: Double
    let blackAccuracy: Double

    /// Ispod ovoga se potez ne proglasava prelomnim. U cisto odigranoj partiji
    /// najveci gubitak ume da bude 12 centipiona; izdvojiti ga kao „prelomni"
    /// bilo bi lazno dramatizovanje.
    static let turningPointMinLoss = 100

    /// Gornja granica gubitka po potezu.
    ///
    /// Propusten mat daje razliku od ~20.000 centipiona. Bez ove granice bi
    /// jedan takav potez sam odredio prosek cele partije i tacnost bi pala na
    /// ~0 iako je ostatak partije bio solidan. 1000 je trostruko iznad praga
    /// za promasaj (300), pa ne sakriva nijednu gresku.
    static let maxCpLoss = 1000

    var turningPoint: AnalyzedMove? {
        guard let worst = moves.max(by: { $0.cpLoss < $1.cpLoss }),
              worst.cpLoss >= Self.turningPointMinLoss else { return nil }
        return worst
    }

    /// Gubitak u centipionima za jedan potez.
    ///
    /// `before` je ocena pozicije PRE poteza, iz ugla igraca koji vuce.
    /// `after` je ocena pozicije POSLE poteza — a tada je na potezu PROTIVNIK,
    /// pa je i ocena iz njegovog ugla. Vrednost iz ugla igraca koji je vukao
    /// je `-after`, i gubitak je `before - (-after)` = `before + after`.
    /// Sabiranje ovde nije greska nego posledica okretanja perspektive.
    static func cpLoss(before: EngineScore, after: EngineScore) -> Int {
        let raw = before.centipawns + after.centipawns
        return max(0, min(maxCpLoss, raw))
    }

    /// Lichess formula iz spec-a 5.5, ogranicena na 0…100.
    static func accuracy(avgCpLoss: Double) -> Double {
        let raw = 103.1668 * exp(-0.04354 * avgCpLoss) - 3.1669
        return min(100, max(0, raw))
    }

    /// Sklapa analizu iz N notacija i N+1 ocena.
    ///
    /// `scores[i]` je ocena pozicije PRE poteza `i`; `scores[i+1]` je ocena
    /// pozicije posle njega. Zato je svaka pozicija pretrazena tacno jednom, a
    /// ne dvaput — partija od 40 poteza trazi 81 pretragu, ne 160.
    static func build(
        notations: [String],
        scores: [EngineScore],
        engineBestMatched: [Bool]
    ) -> GameAnalysis {
        // Neslaganje duzina je greska pozivaoca. Vracamo praznu analizu umesto
        // da indeksiramo van granica — pad ovde bi srusio aplikaciju posle
        // partije, u trenutku kad korisnik nista nije ni trazio osim rezultata.
        guard scores.count == notations.count + 1,
              engineBestMatched.count == notations.count,
              !notations.isEmpty else {
            return GameAnalysis(moves: [], whiteAccuracy: 100, blackAccuracy: 100)
        }

        var moves: [AnalyzedMove] = []
        moves.reserveCapacity(notations.count)
        for i in notations.indices {
            let before = scores[i]
            let after = scores[i + 1]
            let loss = cpLoss(before: before, after: after)
            moves.append(AnalyzedMove(
                ply: i,
                notation: notations[i],
                byWhite: i % 2 == 0,
                cpLoss: loss,
                moveClass: MoveClass.classify(cpLoss: loss, playedEngineBest: engineBestMatched[i]),
                scoreBefore: before,
                scoreAfter: after
            ))
        }

        func accuracyFor(white: Bool) -> Double {
            let side = moves.filter { $0.byWhite == white }
            guard !side.isEmpty else { return 100 }
            let avg = Double(side.reduce(0) { $0 + $1.cpLoss }) / Double(side.count)
            return accuracy(avgCpLoss: avg)
        }

        return GameAnalysis(
            moves: moves,
            whiteAccuracy: accuracyFor(white: true),
            blackAccuracy: accuracyFor(white: false)
        )
    }
}
