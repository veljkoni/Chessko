import Foundation

// MARK: - Move Flag

enum MoveFlag: Equatable, Sendable, Codable {
    case normal
    case castleKingside
    case castleQueenside
    case enPassant
    case promotion(PieceType)

    // Custom Codable — Swift cannot synthesize for enum with associated value
    private enum CodingKeys: String, CodingKey { case type, promotionPiece }
    private enum FlagType: String, Codable {
        case normal, castleKingside, castleQueenside, enPassant, promotion
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .normal:          try c.encode(FlagType.normal,          forKey: .type)
        case .castleKingside:  try c.encode(FlagType.castleKingside,  forKey: .type)
        case .castleQueenside: try c.encode(FlagType.castleQueenside, forKey: .type)
        case .enPassant:       try c.encode(FlagType.enPassant,       forKey: .type)
        case .promotion(let pt):
            try c.encode(FlagType.promotion, forKey: .type)
            try c.encode(pt,                 forKey: .promotionPiece)
        }
    }

    init(from decoder: Decoder) throws {
        let c    = try decoder.container(keyedBy: CodingKeys.self)
        let type = try c.decode(FlagType.self, forKey: .type)
        switch type {
        case .normal:          self = .normal
        case .castleKingside:  self = .castleKingside
        case .castleQueenside: self = .castleQueenside
        case .enPassant:       self = .enPassant
        case .promotion:
            self = .promotion(try c.decode(PieceType.self, forKey: .promotionPiece))
        }
    }
}

// MARK: - Chess Move

struct ChessMove: Equatable, Sendable, Codable {
    let from: Position
    let to: Position
    let flag: MoveFlag

    init(from: Position, to: Position, flag: MoveFlag = .normal) {
        self.from = from
        self.to = to
        self.flag = flag
    }

    static func == (lhs: ChessMove, rhs: ChessMove) -> Bool {
        lhs.from == rhs.from && lhs.to == rhs.to && lhs.flag == rhs.flag
    }

    var notation: String {
        "\(from.algebraic)\(to.algebraic)"
    }

    /// Converts a UCI string (e.g. "e2e4", "e7e8q") to a ChessMove by
    /// matching against legal moves in `state`.  Returns nil if not found.
    static func fromUCI(_ uci: String, in state: GameState) -> ChessMove? {
        let chars = Array(uci.lowercased())
        guard chars.count >= 4,
              let fc = chars[0].asciiValue,
              let fr = chars[1].wholeNumberValue,
              let tc = chars[2].asciiValue,
              let tr = chars[3].wholeNumberValue else { return nil }

        let fromCol = Int(fc) - 97          // 'a'=0 … 'h'=7
        let fromRow = 8 - fr               // '1'→row 7, '8'→row 0
        let toCol   = Int(tc) - 97
        let toRow   = 8 - tr

        guard (0...7).contains(fromCol), (0...7).contains(fromRow),
              (0...7).contains(toCol),   (0...7).contains(toRow) else { return nil }

        let legal = MoveGenerator.legalMoves(for: state.currentTurn, in: state)

        if chars.count >= 5 {
            let promPiece: PieceType = switch chars[4] {
            case "r": .rook
            case "b": .bishop
            case "n": .knight
            default:  .queen
            }
            return legal.first {
                $0.from.row == fromRow && $0.from.col == fromCol &&
                $0.to.row   == toRow   && $0.to.col   == toCol   &&
                $0.flag == .promotion(promPiece)
            }
        }

        return legal.first {
            $0.from.row == fromRow && $0.from.col == fromCol &&
            $0.to.row   == toRow   && $0.to.col   == toCol
        }
    }
}
