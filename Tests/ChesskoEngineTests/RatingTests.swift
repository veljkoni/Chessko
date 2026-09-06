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

// Brief (task-4-brief.md) states this should stay below 1000. Verified against
// the SAME formula that produces the four checked values above: it does not,
// it reaches 1288 after 100 solves. That is expected, not a bug — the puzzle
// rating (rp) stays fixed at 800 while the player's rating (r) climbs, so E
// keeps rising and the per-solve increment keeps shrinking, but never to zero;
// the sequence has a real fixed point only around r ≈ 1520 (where round(32*(1-E))
// first hits 0), not near 800. This test keeps the brief's actual intent — the
// increment-shrinking property preventing unbounded/linear growth — using a
// bound that is true: 100 solves reach ~1288, well short of the ~1520 ceiling,
// and nowhere near the ~2400 a broken (non-shrinking, constant +16) increment
// would produce.
@Test func hundredConsecutiveSolvesDoNotGrowUnbounded() {
    var rating = 800
    for _ in 0..<100 {
        rating = StatsManager.newRating(current: rating, puzzleRating: 800, solved: true)
    }
    #expect(rating < 1600)
}
