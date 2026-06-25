import Foundation

// MARK: - Chess Puzzle (Lichess puzzle database via chess-puzzles-api.vercel.app)

struct ChessPuzzle: Codable, Sendable {
    let puzzleId: String
    let fen: String
    let moves: String     // space-separated UCI: "b6c5 e2g4 h3g4 d1g4"
    let rating: Int
    let themes: String    // space-separated: "advantage middlegame short"

    enum CodingKeys: String, CodingKey {
        case puzzleId = "PuzzleId"
        case fen      = "FEN"
        case moves    = "Moves"
        case rating   = "Rating"
        case themes   = "Themes"
    }

    var uciMoves: [String] { moves.split(separator: " ").map(String.init) }
    var themeList: [String] { themes.split(separator: " ").map(String.init) }

    /// Friendly difficulty label based on Lichess rating.
    var difficultyLabel: String {
        switch rating {
        case ..<1200: return "Lako"
        case 1200..<1600: return "Srednje"
        default: return "Teško"
        }
    }

    var difficultyColor: String {
        switch rating {
        case ..<1200: return "green"
        case 1200..<1600: return "yellow"
        default: return "red"
        }
    }
}
