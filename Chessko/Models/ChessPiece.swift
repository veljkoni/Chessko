import Foundation

// MARK: - Piece Type

enum PieceType: Int, CaseIterable, Equatable, Sendable, Codable {
    case king, queen, rook, bishop, knight, pawn

    var materialValue: Int {
        switch self {
        case .king:   return 20_000
        case .queen:  return 900
        case .rook:   return 500
        case .bishop: return 330
        case .knight: return 320
        case .pawn:   return 100
        }
    }

    /// Letter used in Serbian algebraic notation (K/D/T/L/S; pawn = "")
    var srbNotationLetter: String {
        switch self {
        case .king:   return "K"
        case .queen:  return "D"
        case .rook:   return "T"
        case .bishop: return "L"
        case .knight: return "S"
        case .pawn:   return ""
        }
    }

    /// Localized piece name (nominative). Source language Serbian: "Kralj", "Pešak"…
    var srbName: String {
        switch self {
        case .king:   return Loc("Kralj")
        case .queen:  return Loc("Dama")
        case .rook:   return Loc("Top")
        case .bishop: return Loc("Lovac")
        case .knight: return Loc("Skakač")
        case .pawn:   return Loc("Pešak")
        }
    }
}

// MARK: - Piece Color

enum PieceColor: Int, Equatable, Sendable, Codable {
    case white, black

    var opposite: PieceColor { self == .white ? .black : .white }

    /// Localized colour adjective (lowercase). Source language Serbian: "beli", "crni"
    var srbAdjective: String { self == .white ? Loc("beli") : Loc("crni") }
}

// MARK: - Chess Piece

struct ChessPiece: Equatable, Sendable, Codable {
    let type: PieceType
    let color: PieceColor

    /// VoiceOver label, e.g. "beli pešak"
    var a11yLabel: String { "\(color.srbAdjective) \(type.srbName.lowercased())" }

    var symbol: String {
        switch (color, type) {
        case (.white, .king):   return "♔"
        case (.white, .queen):  return "♕"
        case (.white, .rook):   return "♖"
        case (.white, .bishop): return "♗"
        case (.white, .knight): return "♘"
        case (.white, .pawn):   return "♙"
        case (.black, .king):   return "♚"
        case (.black, .queen):  return "♛"
        case (.black, .rook):   return "♜"
        case (.black, .bishop): return "♝"
        case (.black, .knight): return "♞"
        case (.black, .pawn):   return "♟"
        }
    }
}
