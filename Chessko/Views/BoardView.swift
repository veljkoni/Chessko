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

    @AppStorage("boardTheme") private var boardTheme: String = "classic"
    @AppStorage("pieceStyle") private var pieceStyle: String = "classic"
    @AppStorage("swipeToChangeBoardTheme") private var swipeToChangeBoardTheme: Bool = true
    @AppStorage("swipeToChangePieceStyle") private var swipeToChangePieceStyle: Bool = true

    var body: some View {
        GeometryReader { geo in
            let squareSize = geo.size.width / 8

            ZStack(alignment: .topLeading) {
                // Grid of squares
                VStack(spacing: 0) {
                    ForEach(0..<8, id: \.self) { displayRow in
                        let boardRow = displayRow
                        HStack(spacing: 0) {
                            ForEach(0..<8, id: \.self) { displayCol in
                                let boardCol   = displayCol
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
                                    isBottomEdge: isFlipped ? (displayRow == 0) : (displayRow == 7),
                                    isLeftEdge:   isFlipped ? (displayCol == 7) : (displayCol == 0),
                                    isFlipped:    isFlipped
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
                    let displayRow = CGFloat(fc.fromPosition.row)
                    let displayCol = CGFloat(fc.fromPosition.col)
                    PieceImageView(piece: fc.piece)
                        .frame(width: squareSize * 0.88, height: squareSize * 0.88)
                        .rotationEffect(.degrees(isFlipped ? -180 : 0))
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
            .rotationEffect(.degrees(isFlipped ? 180 : 0))
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .shadow(color: .black.opacity(0.35), radius: 8, x: 0, y: 4)
        .gesture(
            DragGesture(minimumDistance: 30)
                .onEnded { value in
                    let horizontal = value.translation.width
                    let vertical = value.translation.height
                    
                    if abs(horizontal) > abs(vertical) {
                        if swipeToChangeBoardTheme && abs(horizontal) > 30 {
                            if horizontal > 0 {
                                // Swipe Right -> cycle backward (previous theme)
                                cycleBoardTheme(forward: false)
                            } else {
                                // Swipe Left -> cycle forward (next theme)
                                cycleBoardTheme(forward: true)
                            }
                        }
                    } else {
                        if swipeToChangePieceStyle && abs(vertical) > 30 {
                            if vertical > 0 {
                                // Swipe Down -> cycle backward (previous style)
                                cyclePieceStyle(forward: false)
                            } else {
                                // Swipe Up -> cycle forward (next style)
                                cyclePieceStyle(forward: true)
                            }
                        }
                    }
                }
        )
    }

    private func cyclePieceStyle(forward: Bool) {
        let allStyles = PieceStyle.allCases
        let currentStyle = PieceStyle(rawValue: pieceStyle) ?? .classic
        guard let currentIndex = allStyles.firstIndex(of: currentStyle) else { return }
        
        let nextIndex: Int
        if forward {
            nextIndex = (currentIndex + 1) % allStyles.count
        } else {
            nextIndex = (currentIndex - 1 + allStyles.count) % allStyles.count
        }
        
        let newStyle = allStyles[nextIndex]
        
        // Haptic feedback
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
        
        withAnimation(.easeInOut(duration: 0.25)) {
            pieceStyle = newStyle.rawValue
        }
    }

    private func cycleBoardTheme(forward: Bool) {
        let allThemes = BoardTheme.allCases
        let currentTheme = BoardTheme(rawValue: boardTheme) ?? .classic
        guard let currentIndex = allThemes.firstIndex(of: currentTheme) else { return }
        
        let nextIndex: Int
        if forward {
            nextIndex = (currentIndex + 1) % allThemes.count
        } else {
            nextIndex = (currentIndex - 1 + allThemes.count) % allThemes.count
        }
        
        let newTheme = allThemes[nextIndex]
        
        // Haptic feedback
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
        
        withAnimation(.easeInOut(duration: 0.25)) {
            boardTheme = newTheme.rawValue
        }
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
        let displayRow = CGFloat(pos.row)
        let displayCol = CGFloat(pos.col)
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
            .rotationEffect(.degrees(isFlipped ? -180 : 0))
            .position(pos)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.25)) {
                    atDestination = true
                }
            }
    }
}
