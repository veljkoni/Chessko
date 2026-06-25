import SwiftUI

// MARK: - Captured Pieces View

struct CapturedPiecesView: View {
    let pieces: [ChessPiece]
    /// Which player made these captures (e.g. .white = white player captured these pieces).
    let capturedByColor: PieceColor
    var flyingCapture: FlyingCapture?

    /// True when this view is the destination for the currently flying piece.
    /// White captures black pieces, so a flying black piece lands in the white player's section.
    private var isDestination: Bool {
        guard let fc = flyingCapture else { return false }
        return fc.piece.color != capturedByColor
    }

    /// Sorted by material value, with the flying piece temporarily excluded while in flight.
    private var sorted: [ChessPiece] {
        var all = pieces.sorted { $0.type.materialValue > $1.type.materialValue }
        if isDestination, let fc = flyingCapture,
           let idx = all.firstIndex(where: { $0.type == fc.piece.type && $0.color == fc.piece.color }) {
            all.remove(at: idx)
        }
        return all
    }

    private let pieceSize: CGFloat = 28

    var body: some View {
        HStack(spacing: 4) {
            if sorted.isEmpty && !isDestination {
                Text("–")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: -6) {
                        ForEach(Array(sorted.enumerated()), id: \.offset) { _, piece in
                            PieceImageView(piece: piece)
                                .frame(width: pieceSize, height: pieceSize)
                                .transition(.asymmetric(
                                    insertion: .scale(scale: 0.15).combined(with: .opacity),
                                    removal: .opacity
                                ))
                        }
                    }
                    .animation(.spring(duration: 0.4, bounce: 0.3), value: sorted.count)
                }
            }
            Spacer()
        }
        .frame(height: pieceSize)
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 12) {
        CapturedPiecesView(
            pieces: [
                ChessPiece(type: .queen,  color: .black),
                ChessPiece(type: .rook,   color: .black),
                ChessPiece(type: .bishop, color: .black),
                ChessPiece(type: .pawn,   color: .black),
                ChessPiece(type: .pawn,   color: .black),
            ],
            capturedByColor: .white
        )
        CapturedPiecesView(pieces: [], capturedByColor: .white)
    }
    .padding()
}
