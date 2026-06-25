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

/// Suptilni višetačkasti gradijent koji prati temu sistema.
struct AppBackgroundView: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        MeshGradient(width: 3, height: 3, points: [
            [0.0, 0.0], [0.5, 0.0], [1.0, 0.0],
            [0.0, 0.5], [0.5, 0.5], [1.0, 0.5],
            [0.0, 1.0], [0.5, 1.0], [1.0, 1.0]
        ], colors: colorScheme == .dark ? darkColors : lightColors)
        .ignoresSafeArea()
    }

    /// Tamna tema: duboki navy, blagi plavo-ljubičasti akcenat gore-levo.
    private var darkColors: [Color] { [
        Color(hex: "#080e1c"), Color(hex: "#0b1328"), Color(hex: "#0a1122"),
        Color(hex: "#0e1830"), Color(hex: "#0e1528"), Color(hex: "#10183a"),
        Color(hex: "#0c1426"), Color(hex: "#0f1b32"), Color(hex: "#0d1528")
    ] }

    /// Svetla tema: hladna bela, blagi plavi tonovi po uglovima.
    private var lightColors: [Color] { [
        Color(hex: "#e2e9f6"), Color(hex: "#edf0f7"), Color(hex: "#eef2fb"),
        Color(hex: "#e8edf8"), Color(hex: "#edf0f7"), Color(hex: "#f3f6fc"),
        Color(hex: "#dfe8f4"), Color(hex: "#e9eef8"), Color(hex: "#f0f4fb")
    ] }
}

// MARK: - Square View

struct SquareView: View {
    let position: Position
    let piece: ChessPiece?
    let isSelected: Bool
    let isLegalMove: Bool
    let isLastMove: Bool
    let isLight: Bool
    var hidePiece: Bool = false
    /// True when this square is on the bottom display row — shows file label (a–h).
    var isBottomEdge: Bool = false
    /// True when this square is on the left display column — shows rank label (1–8).
    var isLeftEdge: Bool = false

    var body: some View {
        ZStack {
            // Base square
            baseColor

            // Last-move highlight
            if isLastMove {
                Color.yellow.opacity(0.40)
            }

            // Selection highlight
            if isSelected {
                Color(hex: "#8d98b0").opacity(0.70)
            }

            // Legal move indicator
            if isLegalMove {
                legalMoveIndicator
            }

            // Piece image (hidden during slide animation — AnimatingPieceView takes over)
            if let piece, !hidePiece {
                GeometryReader { geo in
                    PieceImageView(piece: piece)
                        .padding(geo.size.width * 0.05)
                        .transition(.scale.combined(with: .opacity))
                }
            }

            // Coordinate labels
            coordinateLabels
        }
        .aspectRatio(1, contentMode: .fit)
    }

    // MARK: - Colors

    private var baseColor: Color {
        isLight ? .squareLight : .squareDark
    }

    // MARK: - Legal Move

    @ViewBuilder
    private var legalMoveIndicator: some View {
        if piece != nil {
            // Capture ring
            GeometryReader { geo in
                let line = geo.size.width * 0.10
                RoundedRectangle(cornerRadius: 0)
                    .strokeBorder(Color.black.opacity(0.22), lineWidth: line)
            }
        } else {
            // Empty-square dot
            GeometryReader { geo in
                Circle()
                    .fill(Color.black.opacity(0.20))
                    .frame(width:  geo.size.width  * 0.28,
                           height: geo.size.height * 0.28)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }

    // MARK: - Coordinates

    @ViewBuilder
    private var coordinateLabels: some View {
        let labelColor = isLight ? Color.squareDark : Color.squareLight

        // File letter (a–h) on the bottom display row
        if isBottomEdge {
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Text(String("abcdefgh"["abcdefgh".index("abcdefgh".startIndex, offsetBy: position.col)]))
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(labelColor)
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
                    .padding(2)
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
