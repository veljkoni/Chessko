import SwiftUI

// MARK: - Piece Image View

struct PieceImageView: View {
    let piece: ChessPiece

    private var assetName: String {
        let color = piece.color == .white ? "white" : "black"
        let type: String = switch piece.type {
        case .king:   "king"
        case .queen:  "queen"
        case .rook:   "rook"
        case .bishop: "bishop"
        case .knight: "knight"
        case .pawn:   "pawn"
        }
        return "piece_\(color)_\(type)"
    }

    var body: some View {
        Image(assetName)
            .resizable()
            .interpolation(.high)
            .scaledToFit()
    }
}

#Preview {
    HStack {
        PieceImageView(piece: ChessPiece(type: .king, color: .white))
        PieceImageView(piece: ChessPiece(type: .queen, color: .black))
        PieceImageView(piece: ChessPiece(type: .knight, color: .white))
    }
    .padding()
    .frame(height: 120)
}
