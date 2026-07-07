import Foundation

// MARK: - Chess AI (Negamax + Alpha-Beta + TT + Iterative Deepening)

struct ChessAI {

    // MARK: - Difficulty

    enum Difficulty {
        case easy, medium, hard

        /// Maximum search depth (iterative deepening stops here or when time runs out).
        var maxDepth: Int {
            switch self {
            case .easy:   return 2
            case .medium: return 4
            case .hard:   return 10
            }
        }

        /// Time budget per move (seconds).
        var timeLimit: Double {
            switch self {
            case .easy:   return 0.20
            case .medium: return 0.50
            case .hard:   return 2.00
            }
        }

        /// Extra plies of capture-only search after main search (quiescence).
        var quiescenceDepth: Int {
            switch self {
            case .easy:   return 0
            case .medium: return 2
            case .hard:   return 4
            }
        }
    }

    let difficulty: Difficulty

    init(difficulty: Difficulty = .medium) {
        self.difficulty = difficulty
    }

    // MARK: - Best Move (Iterative Deepening)

    func bestMove(for color: PieceColor, in state: GameState) -> ChessMove? {
        let moves = MoveGenerator.legalMoves(for: color, in: state)
        guard !moves.isEmpty else { return nil }

        // Single legal move — return immediately, no need to search.
        if moves.count == 1 { return moves[0] }

        // Introduce random blunder rate for easy/medium
        let roll = Double.random(in: 0...1)
        switch difficulty {
        case .easy:
            if roll < 0.25 {
                return moves.randomElement()
            }
        case .medium:
            if roll < 0.08 {
                return moves.randomElement()
            }
        case .hard:
            break
        }

        let ctx  = SearchContext(timeLimit: difficulty.timeLimit)
        let zt   = ZobristTable.shared
        let root = zt.hash(for: state)

        var bestMove: ChessMove? = nil
        var bestScore = -Int.max / 2

        for depth in 1...difficulty.maxDepth {
            guard !ctx.shouldStop else { break }

            if let (m, s) = searchRoot(color: color, state: state, rootHash: root,
                                       moves: moves, depth: depth, ctx: ctx) {
                bestMove  = m
                bestScore = s
            }

            if ctx.shouldStop { break }

            // Found a forced mate — no point searching deeper.
            if abs(bestScore) > 90_000 { break }
        }

        return bestMove ?? moves.first
    }

    // MARK: - Root Search (one iteration at `depth`)
    //
    // Returns the best move found, or nil if time ran out before completing
    // even the first move (extremely rare; bestMove() falls back to prior iter).

    private func searchRoot(color: PieceColor, state: GameState, rootHash: UInt64,
                             moves: [ChessMove], depth: Int,
                             ctx: SearchContext) -> (move: ChessMove, score: Int)? {
        let zt     = ZobristTable.shared
        var alpha  = -Int.max / 2
        let beta   =  Int.max / 2

        // Use best move from previous iteration (stored in TT) for ordering.
        let ttHint  = ctx.tt.bestMove(for: rootHash)
        let ordered = orderMoves(moves, in: state, ttMove: ttHint)

        var bestMove:  ChessMove? = nil
        var bestScore = -Int.max / 2

        for move in ordered {
            guard !ctx.shouldStop else { break }

            let newHash = zt.hash(after: move, in: state, from: rootHash)
            let next    = state.applyingForSearch(move)
            let score   = -negamax(state: next, hash: newHash,
                                   depth: depth - 1,
                                   alpha: -beta, beta: -alpha,
                                   color: color.opposite, ctx: ctx)

            if score > bestScore || bestMove == nil {
                bestScore = score
                bestMove  = move
            }
            alpha = max(alpha, score)
        }

        // Store result only if we completed the iteration without mid-search timeout.
        if !ctx.shouldStop, let m = bestMove {
            ctx.tt.store(hash: rootHash, depth: depth, score: bestScore,
                         flag: .exact, move: m)
            return (m, bestScore)
        }

        // If we timed out but had at least one move scored, return it.
        if let m = bestMove { return (m, bestScore) }
        return nil
    }

    // MARK: - Negamax + Alpha-Beta + Transposition Table

    private func negamax(state: GameState, hash: UInt64,
                         depth: Int, alpha: Int, beta: Int,
                         color: PieceColor, ctx: SearchContext) -> Int {
        ctx.tick()
        if ctx.shouldStop { return 0 }

        // Terminal position check
        switch state.status {
        case .checkmate(let loser):
            return loser == color ? -(100_000 + depth) : (100_000 + depth)
        case .draw:
            return 0
        default:
            break
        }

        // Drop into quiescence at horizon
        if depth == 0 {
            return quiescence(state: state, hash: hash,
                              alpha: alpha, beta: beta,
                              color: color, depth: difficulty.quiescenceDepth,
                              ctx: ctx)
        }

        // Transposition table probe
        var alpha = alpha
        var beta  = beta
        if let hit = ctx.tt.probe(hash: hash, depth: depth, alpha: &alpha, beta: &beta) {
            return hit.score
        }
        let ttMove = ctx.tt.bestMove(for: hash)   // for move ordering even on depth miss

        let moves = MoveGenerator.legalMoves(for: color, in: state)
        if moves.isEmpty {
            return MoveGenerator.isInCheck(color: color, in: state)
                ? -(100_000 + depth)
                : 0
        }

        let zt      = ZobristTable.shared
        var best    = -Int.max / 2
        var bestMv: ChessMove? = nil
        var flag    = TTFlag.upperBound

        for move in orderMoves(moves, in: state, ttMove: ttMove) {
            guard !ctx.shouldStop else { return 0 }

            let newHash = zt.hash(after: move, in: state, from: hash)
            let next    = state.applyingForSearch(move)
            let score   = -negamax(state: next, hash: newHash,
                                   depth: depth - 1,
                                   alpha: -beta, beta: -alpha,
                                   color: color.opposite, ctx: ctx)

            if score > best {
                best   = score
                bestMv = move
            }
            if score > alpha {
                alpha = score
                flag  = .exact
            }
            if score >= beta {
                // Beta cutoff — store as lower bound
                ctx.tt.store(hash: hash, depth: depth, score: score,
                             flag: .lowerBound, move: move)
                return score
            }
        }

        ctx.tt.store(hash: hash, depth: depth, score: best, flag: flag, move: bestMv)
        return best
    }

    // MARK: - Quiescence Search
    //
    // Continues searching only captures until the position is "quiet",
    // preventing the horizon effect.

    private func quiescence(state: GameState, hash: UInt64,
                             alpha: Int, beta: Int,
                             color: PieceColor, depth: Int,
                             ctx: SearchContext) -> Int {
        ctx.tick()
        if ctx.shouldStop { return 0 }

        // Stand-pat: assume we can choose not to capture.
        let standPat = evaluate(state: state, color: color)
        if standPat >= beta { return beta }
        var alpha = max(alpha, standPat)

        guard depth > 0 else { return alpha }

        let zt      = ZobristTable.shared
        let allMoves = MoveGenerator.legalMoves(for: color, in: state)
        let captures = allMoves.filter { isCapture($0, in: state) }

        for move in orderMoves(captures, in: state, ttMove: nil) {
            guard !ctx.shouldStop else { return 0 }
            let newHash = zt.hash(after: move, in: state, from: hash)
            let next    = state.applyingForSearch(move)
            let score   = -quiescence(state: next, hash: newHash,
                                      alpha: -beta, beta: -alpha,
                                      color: color.opposite, depth: depth - 1,
                                      ctx: ctx)
            if score >= beta { return beta }
            alpha = max(alpha, score)
        }
        return alpha
    }

    // MARK: - Move Ordering (TT move first, then MVV-LVA)

    private func orderMoves(_ moves: [ChessMove], in state: GameState,
                             ttMove: ChessMove?) -> [ChessMove] {
        moves.sorted { mvvLvaScore($0, in: state, ttMove: ttMove) >
                       mvvLvaScore($1, in: state, ttMove: ttMove) }
    }

    private func mvvLvaScore(_ move: ChessMove, in state: GameState,
                              ttMove: ChessMove?) -> Int {
        // TT / PV move — try first
        if let tt = ttMove, move == tt { return Int.max / 2 }

        guard let attacker = state.board[move.from.row][move.from.col] else { return 0 }

        if let victim = state.board[move.to.row][move.to.col] {
            return 10 * victim.type.materialValue - attacker.type.materialValue
        }
        if move.flag == .enPassant {
            return 10 * PieceType.pawn.materialValue - attacker.type.materialValue
        }
        if case .promotion(let pt) = move.flag { return pt.materialValue }
        return 0
    }

    private func isCapture(_ move: ChessMove, in state: GameState) -> Bool {
        state.board[move.to.row][move.to.col] != nil || move.flag == .enPassant
    }

    // MARK: - Static Evaluation

    private func evaluate(state: GameState, color: PieceColor) -> Int {
        let endgame = isEndgame(state: state)
        var score = 0
        for row in 0..<8 {
            for col in 0..<8 {
                guard let piece = state.board[row][col] else { continue }
                let pv = piece.type.materialValue
                     + positionalBonus(piece: piece, row: row, col: col, endgame: endgame)
                score += piece.color == color ? pv : -pv
            }
        }
        return score
    }

    /// Endgame: no queens, or very few pieces remaining.
    private func isEndgame(state: GameState) -> Bool {
        var queens = 0, minors = 0
        for row in 0..<8 {
            for col in 0..<8 {
                guard let p = state.board[row][col] else { continue }
                if p.type == .queen                          { queens += 1 }
                if p.type == .bishop || p.type == .knight   { minors += 1 }
            }
        }
        return queens == 0 || (queens <= 2 && minors <= 2)
    }

    // MARK: - Piece-Square Tables (White's perspective; row 0 = rank 8)

    private static let pawnPST: [[Int]] = [
        [ 0,  0,  0,  0,  0,  0,  0,  0],
        [50, 50, 50, 50, 50, 50, 50, 50],
        [10, 10, 20, 30, 30, 20, 10, 10],
        [ 5,  5, 10, 25, 25, 10,  5,  5],
        [ 0,  0,  0, 20, 20,  0,  0,  0],
        [ 5, -5,-10,  0,  0,-10, -5,  5],
        [ 5, 10, 10,-20,-20, 10, 10,  5],
        [ 0,  0,  0,  0,  0,  0,  0,  0]
    ]
    private static let knightPST: [[Int]] = [
        [-50,-40,-30,-30,-30,-30,-40,-50],
        [-40,-20,  0,  0,  0,  0,-20,-40],
        [-30,  0, 10, 15, 15, 10,  0,-30],
        [-30,  5, 15, 20, 20, 15,  5,-30],
        [-30,  0, 15, 20, 20, 15,  0,-30],
        [-30,  5, 10, 15, 15, 10,  5,-30],
        [-40,-20,  0,  5,  5,  0,-20,-40],
        [-50,-40,-30,-30,-30,-30,-40,-50]
    ]
    private static let bishopPST: [[Int]] = [
        [-20,-10,-10,-10,-10,-10,-10,-20],
        [-10,  0,  0,  0,  0,  0,  0,-10],
        [-10,  0,  5, 10, 10,  5,  0,-10],
        [-10,  5,  5, 10, 10,  5,  5,-10],
        [-10,  0, 10, 10, 10, 10,  0,-10],
        [-10, 10, 10, 10, 10, 10, 10,-10],
        [-10,  5,  0,  0,  0,  0,  5,-10],
        [-20,-10,-10,-10,-10,-10,-10,-20]
    ]
    private static let rookPST: [[Int]] = [
        [ 0,  0,  0,  0,  0,  0,  0,  0],
        [ 5, 10, 10, 10, 10, 10, 10,  5],
        [-5,  0,  0,  0,  0,  0,  0, -5],
        [-5,  0,  0,  0,  0,  0,  0, -5],
        [-5,  0,  0,  0,  0,  0,  0, -5],
        [-5,  0,  0,  0,  0,  0,  0, -5],
        [-5,  0,  0,  0,  0,  0,  0, -5],
        [ 0,  0,  0,  5,  5,  0,  0,  0]
    ]
    private static let queenPST: [[Int]] = [
        [-20,-10,-10, -5, -5,-10,-10,-20],
        [-10,  0,  0,  0,  0,  0,  0,-10],
        [-10,  0,  5,  5,  5,  5,  0,-10],
        [ -5,  0,  5,  5,  5,  5,  0, -5],
        [  0,  0,  5,  5,  5,  5,  0, -5],
        [-10,  5,  5,  5,  5,  5,  0,-10],
        [-10,  0,  5,  0,  0,  0,  0,-10],
        [-20,-10,-10, -5, -5,-10,-10,-20]
    ]
    private static let kingMiddlePST: [[Int]] = [
        [-30,-40,-40,-50,-50,-40,-40,-30],
        [-30,-40,-40,-50,-50,-40,-40,-30],
        [-30,-40,-40,-50,-50,-40,-40,-30],
        [-30,-40,-40,-50,-50,-40,-40,-30],
        [-20,-30,-30,-40,-40,-30,-30,-20],
        [-10,-20,-20,-20,-20,-20,-20,-10],
        [ 20, 20,  0,  0,  0,  0, 20, 20],
        [ 20, 30, 10,  0,  0, 10, 30, 20]
    ]
    private static let kingEndgamePST: [[Int]] = [
        [-50,-40,-30,-20,-20,-30,-40,-50],
        [-30,-20,-10,  0,  0,-10,-20,-30],
        [-30,-10, 20, 30, 30, 20,-10,-30],
        [-30,-10, 30, 40, 40, 30,-10,-30],
        [-30,-10, 30, 40, 40, 30,-10,-30],
        [-30,-10, 20, 30, 30, 20,-10,-30],
        [-30,-30,  0,  0,  0,  0,-30,-30],
        [-50,-30,-30,-30,-30,-30,-30,-50]
    ]

    private func positionalBonus(piece: ChessPiece, row: Int, col: Int, endgame: Bool) -> Int {
        let r = piece.color == .white ? row : (7 - row)
        switch piece.type {
        case .pawn:   return ChessAI.pawnPST[r][col]
        case .knight: return ChessAI.knightPST[r][col]
        case .bishop: return ChessAI.bishopPST[r][col]
        case .rook:   return ChessAI.rookPST[r][col]
        case .queen:  return ChessAI.queenPST[r][col]
        case .king:   return endgame ? ChessAI.kingEndgamePST[r][col]
                                     : ChessAI.kingMiddlePST[r][col]
        }
    }
}
