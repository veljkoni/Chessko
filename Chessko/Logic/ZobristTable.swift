import Foundation

// MARK: - Zobrist Table
//
// Deterministic 64-bit random keys for Zobrist hashing.
// XorShift64 seeded with a fixed constant gives the same keys every run.

struct ZobristTable: Sendable {
    static let shared = ZobristTable()

    // pieces[colorRaw 0-1][typeRaw 0-5][row 0-7][col 0-7]
    // PieceColor.rawValue: white=0, black=1
    // PieceType.rawValue:  king=0, queen=1, rook=2, bishop=3, knight=4, pawn=5
    let pieces: [[[[UInt64]]]]
    let sideToMove: UInt64      // XOR when it is black's turn
    let castling: [UInt64]      // [0]=WK, [1]=WQ, [2]=BK, [3]=BQ
    let enPassant: [UInt64]     // [col 0-7]

    private init() {
        var rng = XorShift64(state: 0x9E3779B97F4A7C15)
        var p = [[[[UInt64]]]](
            repeating: [[[UInt64]]](
                repeating: [[UInt64]](
                    repeating: [UInt64](repeating: 0, count: 8),
                    count: 8),
                count: 6),
            count: 2)
        for c in 0..<2 { for t in 0..<6 { for r in 0..<8 { for col in 0..<8 {
            p[c][t][r][col] = rng.next()
        }}}}
        pieces    = p
        sideToMove = rng.next()
        castling   = (0..<4).map { _ in rng.next() }
        enPassant  = (0..<8).map { _ in rng.next() }
    }

    // MARK: - Full hash from scratch

    func hash(for state: GameState) -> UInt64 {
        var h: UInt64 = 0
        for row in 0..<8 {
            for col in 0..<8 {
                if let p = state.board[row][col] {
                    h ^= pieces[p.color.rawValue][p.type.rawValue][row][col]
                }
            }
        }
        if state.currentTurn == .black    { h ^= sideToMove }
        if state.whiteCanCastleKingside   { h ^= castling[0] }
        if state.whiteCanCastleQueenside  { h ^= castling[1] }
        if state.blackCanCastleKingside   { h ^= castling[2] }
        if state.blackCanCastleQueenside  { h ^= castling[3] }
        if let ep = state.enPassantTarget { h ^= enPassant[ep.col] }
        return h
    }

    // MARK: - Incremental update

    /// Returns the hash after applying `move` to `state` (pre-move state).
    /// Must mirror the logic in `GameState.applyingForSearch`.
    func hash(after move: ChessMove, in state: GameState, from oldHash: UInt64) -> UInt64 {
        var h = oldHash
        guard let mover = state.board[move.from.row][move.from.col] else { return h }

        // Toggle side to move
        h ^= sideToMove

        // XOR out old en passant
        if let ep = state.enPassantTarget { h ^= enPassant[ep.col] }

        // XOR out old castling rights
        if state.whiteCanCastleKingside  { h ^= castling[0] }
        if state.whiteCanCastleQueenside { h ^= castling[1] }
        if state.blackCanCastleKingside  { h ^= castling[2] }
        if state.blackCanCastleQueenside { h ^= castling[3] }

        // Apply piece movements
        let mc = mover.color.rawValue
        let mt = mover.type.rawValue

        switch move.flag {
        case .normal:
            h ^= pieces[mc][mt][move.from.row][move.from.col]
            if let cap = state.board[move.to.row][move.to.col] {
                h ^= pieces[cap.color.rawValue][cap.type.rawValue][move.to.row][move.to.col]
            }
            h ^= pieces[mc][mt][move.to.row][move.to.col]
            // New en passant square?
            if mover.type == .pawn && abs(move.to.row - move.from.row) == 2 {
                h ^= enPassant[move.from.col]
            }

        case .castleKingside:
            let row = move.from.row
            let rr  = PieceType.rook.rawValue
            let kr  = PieceType.king.rawValue
            h ^= pieces[mc][kr][row][4]; h ^= pieces[mc][rr][row][7]  // remove
            h ^= pieces[mc][kr][row][6]; h ^= pieces[mc][rr][row][5]  // place

        case .castleQueenside:
            let row = move.from.row
            let rr  = PieceType.rook.rawValue
            let kr  = PieceType.king.rawValue
            h ^= pieces[mc][kr][row][4]; h ^= pieces[mc][rr][row][0]  // remove
            h ^= pieces[mc][kr][row][2]; h ^= pieces[mc][rr][row][3]  // place

        case .enPassant:
            let oppC = mover.color.opposite.rawValue
            let pr   = PieceType.pawn.rawValue
            h ^= pieces[mc][pr][move.from.row][move.from.col]           // remove mover
            h ^= pieces[oppC][pr][move.from.row][move.to.col]           // remove captured pawn
            h ^= pieces[mc][pr][move.to.row][move.to.col]               // place mover

        case .promotion(let promoteTo):
            h ^= pieces[mc][mt][move.from.row][move.from.col]           // remove pawn
            if let cap = state.board[move.to.row][move.to.col] {
                h ^= pieces[cap.color.rawValue][cap.type.rawValue][move.to.row][move.to.col]
            }
            h ^= pieces[mc][promoteTo.rawValue][move.to.row][move.to.col]  // place promoted
        }

        // Compute new castling rights (mirrors applyingForSearch)
        var wck = state.whiteCanCastleKingside
        var wcq = state.whiteCanCastleQueenside
        var bck = state.blackCanCastleKingside
        var bcq = state.blackCanCastleQueenside

        if mover.type == .king {
            if mover.color == .white { wck = false; wcq = false }
            else                     { bck = false; bcq = false }
        }
        if mover.type == .rook {
            switch move.from {
            case Position(row: 7, col: 7): wck = false
            case Position(row: 7, col: 0): wcq = false
            case Position(row: 0, col: 7): bck = false
            case Position(row: 0, col: 0): bcq = false
            default: break
            }
        }

        // XOR in new castling rights
        if wck { h ^= castling[0] }
        if wcq { h ^= castling[1] }
        if bck { h ^= castling[2] }
        if bcq { h ^= castling[3] }

        return h
    }
}

// MARK: - Transposition Table

enum TTFlag: UInt8 {
    case exact      = 0   // PV node — score is exact
    case lowerBound = 1   // Cut node — score is a lower bound (failed high)
    case upperBound = 2   // All node — score is an upper bound (failed low)
}

struct TTEntry {
    var hash:  UInt64 = 0
    var move:  UInt32 = 0   // encoded move; 0 = none stored
    var score: Int32  = 0
    var depth: Int8   = 0
    var flag:  UInt8  = 0
}

/// Transposition table: fixed-size hash map for caching search results.
/// Not thread-safe — only one AI search task accesses it at a time.
final class TranspositionTable {
    // 256 K entries × ~20 bytes ≈ 5 MB — fast to allocate, effective hit rate.
    static let size = 1 << 18
    static let mask = size - 1

    var entries = [TTEntry](repeating: TTEntry(), count: TranspositionTable.size)

    // MARK: Probe

    /// Returns (score, bestMove) if there is a usable hit at `depth`, else nil.
    /// Alpha and beta are updated for bound entries before returning nil.
    func probe(hash: UInt64, depth: Int, alpha: inout Int, beta: inout Int) -> (score: Int, move: ChessMove?)? {
        let e = entries[Int(hash & UInt64(TranspositionTable.mask))]
        guard e.hash == hash else { return nil }
        let bestMove = decodeMove(e.move)
        guard Int(e.depth) >= depth else {
            // Shallow hit — still useful for move ordering
            return nil
        }
        let s = Int(e.score)
        switch TTFlag(rawValue: e.flag) {
        case .exact:
            return (s, bestMove)
        case .lowerBound:
            alpha = max(alpha, s)
        case .upperBound:
            beta  = min(beta,  s)
        default:
            break
        }
        if alpha >= beta { return (s, bestMove) }
        return nil
    }

    /// Returns the best move stored for this hash (if any), ignoring depth.
    func bestMove(for hash: UInt64) -> ChessMove? {
        let e = entries[Int(hash & UInt64(TranspositionTable.mask))]
        guard e.hash == hash else { return nil }
        return decodeMove(e.move)
    }

    // MARK: Store

    func store(hash: UInt64, depth: Int, score: Int, flag: TTFlag, move: ChessMove?) {
        let idx = Int(hash & UInt64(TranspositionTable.mask))
        entries[idx] = TTEntry(
            hash:  hash,
            move:  encodeMove(move),
            score: Int32(clamping: score),
            depth: Int8(clamping: depth),
            flag:  flag.rawValue
        )
    }

    // MARK: Move encoding
    //
    // Packs a ChessMove into a UInt32 so we can store it cheaply.
    // Bits 0-2: from.row, 3-5: from.col, 6-8: to.row, 9-11: to.col, 12-14: flag index.
    // 0 is used as "no move" (a1→a1 normal is never a legal chess move).

    private func encodeMove(_ move: ChessMove?) -> UInt32 {
        guard let m = move else { return 0 }
        let f: UInt32
        switch m.flag {
        case .normal:             f = 0
        case .castleKingside:     f = 1
        case .castleQueenside:    f = 2
        case .enPassant:          f = 3
        case .promotion(.knight): f = 4
        case .promotion(.bishop): f = 5
        case .promotion(.rook):   f = 6
        default:                  f = 7   // queen promotion (default)
        }
        return UInt32(m.from.row)        |
              (UInt32(m.from.col) <<  3) |
              (UInt32(m.to.row)   <<  6) |
              (UInt32(m.to.col)   <<  9) |
              (f                  << 12)
    }

    private func decodeMove(_ encoded: UInt32) -> ChessMove? {
        guard encoded != 0 else { return nil }
        let fromRow  = Int((encoded >>  0) & 0x7)
        let fromCol  = Int((encoded >>  3) & 0x7)
        let toRow    = Int((encoded >>  6) & 0x7)
        let toCol    = Int((encoded >>  9) & 0x7)
        let flagBits = (encoded >> 12) & 0x7
        let flag: MoveFlag
        switch flagBits {
        case 1:  flag = .castleKingside
        case 2:  flag = .castleQueenside
        case 3:  flag = .enPassant
        case 4:  flag = .promotion(.knight)
        case 5:  flag = .promotion(.bishop)
        case 6:  flag = .promotion(.rook)
        case 7:  flag = .promotion(.queen)
        default: flag = .normal
        }
        return ChessMove(from: Position(row: fromRow, col: fromCol),
                         to:   Position(row: toRow,   col: toCol),
                         flag: flag)
    }
}

// MARK: - Search Context
//
// Bundles per-search mutable state so we don't pass many arguments through
// every recursive call.  One context is created per bestMove() call.

final class SearchContext {
    let tt: TranspositionTable
    let startTime: CFAbsoluteTime
    let timeLimit: Double
    var nodeCount: Int = 0
    var shouldStop: Bool = false

    init(timeLimit: Double) {
        self.tt        = TranspositionTable()
        self.timeLimit = timeLimit
        self.startTime = CFAbsoluteTimeGetCurrent()
    }

    /// Call once per node.  Checks the clock every 2048 nodes to stay cheap.
    @inline(__always)
    func tick() {
        nodeCount &+= 1
        if nodeCount & 2047 == 0 {
            shouldStop = CFAbsoluteTimeGetCurrent() - startTime >= timeLimit
        }
    }
}

// MARK: - Private: XorShift64 RNG

private struct XorShift64: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}
