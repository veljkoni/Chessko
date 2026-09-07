import Testing
@testable import ChesskoEngine

// Elo-stil rejting igraca za zadatke (spec 5.4): pocetna vrednost 800, K = 32.
// `StatsManager.newRating` je cista staticka funkcija — nema @MainActor, ne
// dira UserDefaults — pa se testira direktno bez ikakve pripreme.

@Test func equalRatingSolvedRaisesTo816() {
    let result = StatsManager.newRating(current: 800, puzzleRating: 800, solved: true)
    #expect(result == 816)
}

@Test func equalRatingUnsolvedDropsTo784() {
    let result = StatsManager.newRating(current: 800, puzzleRating: 800, solved: false)
    #expect(result == 784)
}

@Test func muchHarderPuzzleSolvedRaisesTo832() {
    let result = StatsManager.newRating(current: 800, puzzleRating: 1600, solved: true)
    #expect(result == 832)
}

@Test func muchEasierPuzzleUnsolvedDropsTo771() {
    let result = StatsManager.newRating(current: 800, puzzleRating: 400, solved: false)
    #expect(result == 771)
}

// Plan (task-4-brief.md) je tvrdio da rejting ostaje ispod 1000 jer se
// "asimptotski primice 800 odozgo". To je NETACNO i uhvaceno je pri izvrsavanju
// ovog zadatka: rejting raste iznad 800 — posle 100 resenih je 1288, posle 1000
// je 1520. Niz se zaustavlja tek oko 1520, gde `round(32*(1-E))` prvi put padne
// na 0; dakle ogranicava ga celobrojno zaokruzivanje, ne asimptota ka 800.
// Plan je ispravljen.
//
// Stvarno svojstvo koje ovde treba tvrditi je da PRIRAST NE RASTE: kako
// rejting igraca raste a rejting zadatka ostaje 800, `E` se primice 1 pa je
// `K*(1-E)` sve manji. To se testira direktno; granica na samoj vrednosti je
// samo gruba zastita od linearnog rasta (pokvaren, nepromenljiv prirast od +16
// po zadatku dao bi 2400).
@Test func consecutiveSolvesYieldNonIncreasingGains() {
    var rating = 800
    var previousGain = Int.max

    for _ in 0..<100 {
        let next = StatsManager.newRating(current: rating, puzzleRating: 800, solved: true)
        let gain = next - rating
        #expect(gain > 0, "prirast mora ostati pozitivan dok je rejting ispod ~1520")
        // `<=`, ne `<`: celobrojno zaokruzivanje pravi platoe (…16, 16, 15…),
        // pa bi strogo opadanje palo iako je formula ispravna.
        #expect(gain <= previousGain, "prirast ne sme da poraste, bio je \(previousGain) pa \(gain)")
        previousGain = gain
        rating = next
    }

    #expect(rating < 1600)
}
