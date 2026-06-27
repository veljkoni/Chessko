import SwiftUI

// MARK: - Board View
//
// Presents the chessboard. Decoupled from any ViewModel — callers pass
// explicit state values and an onTap closure.

struct BoardView: View {
    let board: [[ChessPiece?]]
    let isFlipped: Bool
    let selectedPosition: Position?
    let legalMoves: [ChessMove]
    let lastMove: ChessMove?
    let animatingPiece: AnimatingPiece?
    let flyingCapture: FlyingCapture?
    let playerColor: PieceColor
    let isPlayerTurn: Bool
    let onTap: (Position) -> Void

    var body: some View {
        GeometryReader { geo in
            let squareSize = geo.size.width / 8

            ZStack(alignment: .topLeading) {
                // Grid of squares
                VStack(spacing: 0) {
                    ForEach(0..<8, id: \.self) { displayRow in
                        let boardRow = isFlipped ? (7 - displayRow) : displayRow
                        HStack(spacing: 0) {
                            ForEach(0..<8, id: \.self) { displayCol in
                                let boardCol   = isFlipped ? (7 - displayCol) : displayCol
                                let pos        = Position(row: boardRow, col: boardCol)
                                let piece      = board[boardRow][boardCol]
                                let isSelected = selectedPosition == pos
                                let isLegal    = legalMoves.contains { $0.to == pos }
                                let isLast     = isLastMove(pos)

                                SquareView(
                                    position: pos,
                                    piece: piece,
                                    isSelected: isSelected,
                                    isLegalMove: isLegal,
                                    isLastMove: isLast,
                                    isLight: (boardRow + boardCol) % 2 == 0,
                                    hidePiece: animatingPiece?.to == pos,
                                    isBottomEdge: displayRow == 7,
                                    isLeftEdge:   displayCol == 0
                                )
                                .frame(width: squareSize, height: squareSize)
                                .onTapGesture { onTap(pos) }
                                // MARK: VoiceOver
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel(a11yLabel(pos: pos, piece: piece,
                                                              isSelected: isSelected,
                                                              isLegal: isLegal,
                                                              isLast: isLast))
                                .accessibilityHint(a11yHint(piece: piece, isLegal: isLegal))
                                .accessibilityAddTraits(a11yTraits(piece: piece,
                                                                    isSelected: isSelected,
                                                                    isLegal: isLegal))
                            }
                        }
                    }
                }

                // Captured piece overlay — transient, hidden from VoiceOver
                if let fc = flyingCapture {
                    let displayRow = isFlipped ? CGFloat(7 - fc.fromPosition.row)
                                               : CGFloat(fc.fromPosition.row)
                    let displayCol = isFlipped ? CGFloat(7 - fc.fromPosition.col)
                                               : CGFloat(fc.fromPosition.col)
                    PieceImageView(piece: fc.piece)
                        .frame(width: squareSize * 0.88, height: squareSize * 0.88)
                        .position(
                            x: squareSize * displayCol + squareSize / 2,
                            y: squareSize * displayRow + squareSize / 2
                        )
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                        .transition(.scale(scale: 0.15).combined(with: .opacity))
                }

                // Sliding piece overlay — transient, hidden from VoiceOver
                if let ap = animatingPiece {
                    AnimatingPieceView(animatingPiece: ap,
                                       squareSize: squareSize,
                                       isFlipped: isFlipped)
                        .accessibilityHidden(true)
                }
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .shadow(color: .black.opacity(0.35), radius: 8, x: 0, y: 4)
    }

    private func isLastMove(_ pos: Position) -> Bool {
        guard let last = lastMove else { return false }
        return last.from == pos || last.to == pos
    }

    // MARK: - Accessibility helpers

    private func a11yLabel(pos: Position, piece: ChessPiece?,
                            isSelected: Bool, isLegal: Bool, isLast: Bool) -> String {
        var parts = [pos.algebraic]
        if isLegal {
            parts.append(Loc("mogući potez"))
            if let p = piece { parts.append(p.a11yLabel) }
        } else if let p = piece {
            parts.append(p.a11yLabel)
        } else {
            parts.append(Loc("prazno"))
        }
        if isSelected { parts.append(Loc("izabrano")) }
        if isLast     { parts.append(Loc("poslednji potez")) }
        return parts.joined(separator: ", ")
    }

    private func a11yHint(piece: ChessPiece?, isLegal: Bool) -> String {
        if isLegal { return Loc("Dupli dodir za potez") }
        if let p = piece, p.color == playerColor, isPlayerTurn {
            return Loc("Dupli dodir za izbor")
        }
        return ""
    }

    private func a11yTraits(piece: ChessPiece?, isSelected: Bool, isLegal: Bool) -> AccessibilityTraits {
        var traits: AccessibilityTraits = []
        let isOwnPiece = piece?.color == playerColor && isPlayerTurn
        if isLegal || isOwnPiece { traits.formUnion(.isButton) }
        if isSelected             { traits.formUnion(.isSelected) }
        return traits
    }
}

// MARK: - Animating Piece View

struct AnimatingPieceView: View {
    let animatingPiece: AnimatingPiece
    let squareSize: CGFloat
    let isFlipped: Bool

    @State private var atDestination = false

    private func center(for pos: Position) -> CGPoint {
        let displayRow = isFlipped ? CGFloat(7 - pos.row) : CGFloat(pos.row)
        let displayCol = isFlipped ? CGFloat(7 - pos.col) : CGFloat(pos.col)
        return CGPoint(
            x: squareSize * displayCol + squareSize / 2,
            y: squareSize * displayRow + squareSize / 2
        )
    }

    var body: some View {
        let from = center(for: animatingPiece.from)
        let to   = center(for: animatingPiece.to)
        let pos  = atDestination ? to : from

        PieceImageView(piece: animatingPiece.piece)
            .frame(width: squareSize * 0.9, height: squareSize * 0.9)
            .position(pos)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.25)) {
                    atDestination = true
                }
            }
    }
}
