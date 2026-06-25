import Foundation

// MARK: - Board Position
// row: 0 = rank 8 (black back rank), 7 = rank 1 (white back rank)
// col: 0 = file a, 7 = file h

struct Position: Equatable, Hashable, Sendable, Codable {
    let row: Int
    let col: Int

    var isValid: Bool {
        (0..<8).contains(row) && (0..<8).contains(col)
    }

    func offset(dr: Int, dc: Int) -> Position {
        Position(row: row + dr, col: col + dc)
    }

    // e.g. "e4"
    var algebraic: String {
        let files = "abcdefgh"
        let fileChar = files[files.index(files.startIndex, offsetBy: col)]
        return "\(fileChar)\(8 - row)"
    }
}
