import SwiftUI

// MARK: - Design System Colors

extension Color {
    static let boardBackground = Color(hex: "#17234f")
    static let squareLight     = Color(hex: "#e9ebde")
    static let squareDark      = Color(hex: "#8592af")

    /// Pozadina aplikacije — prilagođava se svetloj/tamnoj temi.
    /// Svetla: topla svetlo-plava (#edf0f7). Tamna: duboki tamno-plavi (#0e1528).
    static let appBackground = Color(UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.055, green: 0.082, blue: 0.157, alpha: 1)  // #0e1528
            : UIColor(red: 0.929, green: 0.941, blue: 0.969, alpha: 1)  // #edf0f7
    })

    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r = Double((int >> 16) & 0xff) / 255
        let g = Double((int >> 8)  & 0xff) / 255
        let b = Double(int & 0xff) / 255
        self.init(red: r, green: g, blue: b)
    }
}

// MARK: - App Background (MeshGradient)

/// Suptilni višetačkasti gradijent koji prati temu sistema i izabranu temu table.
/// Suptilna pozadina koja prati sistemsku temu.
struct AppBackgroundView: View {
    var body: some View {
        Color(uiColor: .systemGroupedBackground)
            .ignoresSafeArea()
    }
}

// MARK: - Square View

struct SquareView: View {
    let position: Position
    let piece: ChessPiece?
    let isSelected: Bool
    let isLegalMove: Bool
    let isLastMove: Bool
    let isLight: Bool
    var isInCheck: Bool = false
    var hidePiece: Bool = false
    /// True when this square is on the bottom display row — shows file label (a–h).
    var isBottomEdge: Bool = false
    /// True when this square is on the left display column — shows rank label (1–8).
    var isLeftEdge: Bool = false
    var isFlipped: Bool = false

    @AppStorage("boardTheme")            private var boardTheme:            String = "classic"
    @AppStorage("showCoordinates")       private var showCoordinates:       Bool   = true
    @AppStorage("showLastMoveHighlight") private var showLastMoveHighlight: Bool   = true
    @AppStorage("showLegalMoves")        private var showLegalMoves:        Bool   = true

    @State private var checkPulse = false
    @State private var selectPulse = false
    @State private var dotAnimated = false

    var body: some View {
        ZStack {
            // Base square
            baseColor

            // King in Check Red Aura
            if isInCheck {
                RadialGradient(
                    colors: [
                        Color(hex: "#EF4444").opacity(checkPulse ? 0.85 : 0.40),
                        Color(hex: "#DC2626").opacity(checkPulse ? 0.45 : 0.20),
                        Color.clear
                    ],
                    center: .center,
                    startRadius: 0,
                    endRadius: 40
                )
                .overlay(
                    Rectangle()
                        .stroke(Color(hex: "#EF4444").opacity(checkPulse ? 0.85 : 0.40), lineWidth: 2)
                )
                .onAppear {
                    withAnimation(.easeInOut(duration: 0.65).repeatForever(autoreverses: true)) {
                        checkPulse = true
                    }
                }
            }

            // Last-move highlight
            if isLastMove && showLastMoveHighlight && !isInCheck {
                Color.yellow.opacity(0.40)
            }

            // Selection highlight with Cyan glow
            if isSelected {
                Color(hex: "#00D2FF").opacity(0.28)
                    .overlay(
                        Rectangle()
                            .stroke(Color(hex: "#00D2FF").opacity(0.75), lineWidth: 1.5)
                    )
            }

            // Legal move indicator
            if isLegalMove && showLegalMoves {
                legalMoveIndicator
            }

            // Piece image (hidden during slide animation — AnimatingPieceView takes over)
            if let piece, !hidePiece {
                GeometryReader { geo in
                    PieceImageView(piece: piece)
                        .padding(geo.size.width * 0.05)
                        .rotationEffect(.degrees(isFlipped ? -180 : 0))
                        .scaleEffect(isSelected ? (selectPulse ? 1.07 : 1.0) : 1.0)
                        .transition(.scale.combined(with: .opacity))
                        .onAppear {
                            if isSelected {
                                withAnimation(.easeInOut(duration: 0.70).repeatForever(autoreverses: true)) {
                                    selectPulse = true
                                }
                            }
                        }
                        .onChange(of: isSelected) { selected in
                            if selected {
                                withAnimation(.easeInOut(duration: 0.70).repeatForever(autoreverses: true)) {
                                    selectPulse = true
                                }
                            } else {
                                selectPulse = false
                            }
                        }
                }
            }

            // Coordinate labels
            if showCoordinates {
                coordinateLabels
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }

    // MARK: - Colors

    @ViewBuilder
    private var baseColor: some View {
        let theme = BoardTheme(rawValue: boardTheme) ?? .classic
        if !isLight && (theme == .midnightAurora || theme == .cyberLavender) {
            let centerColor = theme == .midnightAurora ? Color(hex: "#3A475C") : Color(hex: "#4F3B8C")
            RadialGradient(
                colors: [centerColor, theme.darkSquareColor],
                center: .center,
                startRadius: 0,
                endRadius: 42
            )
        } else {
            isLight ? theme.lightSquareColor : theme.darkSquareColor
        }
    }

    // MARK: - Legal Move

    @ViewBuilder
    private var legalMoveIndicator: some View {
        Group {
            if piece != nil {
                // Capture ring
                GeometryReader { geo in
                    let line = geo.size.width * 0.08
                    Rectangle()
                        .stroke(Color(hex: "#00D2FF").opacity(0.65), lineWidth: line)
                }
            } else {
                // Empty-square dot
                GeometryReader { geo in
                    Circle()
                        .fill(Color(hex: "#00D2FF").opacity(0.55))
                        .frame(width:  geo.size.width  * 0.28,
                               height: geo.size.height * 0.28)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
        .scaleEffect(dotAnimated ? 1.0 : 0.4)
        .opacity(dotAnimated ? 1.0 : 0.0)
        .onAppear {
            withAnimation(.easeOut(duration: 0.18)) {
                dotAnimated = true
            }
        }
    }

    // MARK: - Coordinates

    @ViewBuilder
    private var coordinateLabels: some View {
        let theme = BoardTheme(rawValue: boardTheme) ?? .classic
        let labelColor = isLight ? theme.darkSquareColor : theme.lightSquareColor

        // File letter (a–h) on the bottom display row
        if isBottomEdge {
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Text(String("abcdefgh"["abcdefgh".index("abcdefgh".startIndex, offsetBy: position.col)]))
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(labelColor)
                        .rotationEffect(.degrees(isFlipped ? -180 : 0))
                        .padding(2)
                }
            }
        }

        // Rank number (1–8) on the left display column
        if isLeftEdge {
            VStack {
                Text("\(8 - position.row)")
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(labelColor)
                    .rotationEffect(.degrees(isFlipped ? -180 : 0))
                    .padding(2)
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
