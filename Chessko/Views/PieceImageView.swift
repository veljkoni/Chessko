import SwiftUI

// MARK: - Piece Style Definition

enum PieceStyle: String, CaseIterable, Identifiable, Sendable {
    case classic
    case neon
    case wood
    case metal
    case flat
    case simpleThin
    case gameroom
    case glass

    var id: String { rawValue }

    var label: String {
        switch self {
        case .classic:    return Loc("Klasični")
        case .neon:       return Loc("Neonski")
        case .wood:       return Loc("Drvene")
        case .metal:      return Loc("Metalne")
        case .flat:       return Loc("Ravne")
        case .simpleThin: return Loc("Tanke")
        case .gameroom:   return Loc("Igraonica")
        case .glass:      return Loc("Staklene")
        }
    }
}

// MARK: - Piece Image View

struct PieceImageView: View {
    let piece: ChessPiece
    var styleOverride: PieceStyle? = nil
    
    @AppStorage("pieceStyle") private var pieceStyle: String = "classic"
    @AppStorage("boardTheme") private var boardTheme: String = "classic"

    private var assetName: String {
        let style = styleOverride ?? PieceStyle(rawValue: pieceStyle) ?? .classic
        let color = piece.color == .white ? "white" : "black"
        let type: String = switch piece.type {
        case .king:   "king"
        case .queen:  "queen"
        case .rook:   "rook"
        case .bishop: "bishop"
        case .knight: "knight"
        case .pawn:   "pawn"
        }
        
        let isDarkTheme = boardTheme == "midnight_aurora" || boardTheme == "cyber_lavender"
        
        if style == .flat {
            if color == "black" && isDarkTheme {
                return "piece_black_\(type)_flat_outlined"
            }
            return "piece_\(color)_\(type)_flat"
        } else if style == .simpleThin {
            return "piece_\(color)_\(type)_simple_thin"
        } else if style == .neon {
            return "piece_\(color)_\(type)_neo"
        } else if style == .wood {
            return "piece_\(color)_\(type)_wood"
        } else if style == .glass {
            return "piece_\(color)_\(type)_glass"
        } else if style == .gameroom {
            return "piece_\(color)_\(type)_gameroom"
        }
        return "piece_\(color)_\(type)"
    }

    var body: some View {
        let style = styleOverride ?? PieceStyle(rawValue: pieceStyle) ?? .classic
        
        switch style {
        case .classic:
            Image(assetName)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .shadow(color: .black.opacity(0.18), radius: 1, x: 0, y: 1)
                

                
        case .neon:
            let glowColor: Color = piece.color == .white ? Color(hex: "#00d2ff") : Color(hex: "#ff3b30")
            Image(assetName)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .shadow(color: glowColor.opacity(0.7), radius: 5, x: 0, y: 0)
                .shadow(color: glowColor.opacity(0.35), radius: 2, x: 0, y: 0)

        case .wood:
            Image(assetName)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .shadow(color: .black.opacity(0.25), radius: 2, x: 0, y: 1.5)

        case .gameroom:
            Image(assetName)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .shadow(color: .black.opacity(0.18), radius: 1, x: 0, y: 1)

        case .glass:
            Image(assetName)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .shadow(color: .black.opacity(0.20), radius: 1.5, x: 0, y: 1)

        case .metal:
            // Srebrno-hromirani gradijent za bele figure (svetle)
            let whiteMetalGradient = LinearGradient(
                colors: [
                    Color(hex: "#FFFFFF"),
                    Color(hex: "#E0E2E5"),
                    Color(hex: "#B9BFC5"),
                    Color(hex: "#F1F3F5"),
                    Color(hex: "#8B939C")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            // Tamni hrom / gunmetal gradijent za crne figure (tamne)
            let blackMetalGradient = LinearGradient(
                colors: [
                    Color(hex: "#4A4D54"),
                    Color(hex: "#2B2D32"),
                    Color(hex: "#1A1B1E"),
                    Color(hex: "#383A3E"),
                    Color(hex: "#111214")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            let metalGradient = piece.color == .white ? whiteMetalGradient : blackMetalGradient

            Image(assetName)
                .renderingMode(.template)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .foregroundStyle(metalGradient)
                .overlay(
                    Image(assetName)
                        .renderingMode(.template)
                        .resizable()
                        .interpolation(.high)
                        .scaledToFit()
                        .foregroundStyle(
                            LinearGradient(
                                stops: [
                                    .init(color: .white.opacity(0.55), location: 0.0),
                                    .init(color: .white.opacity(0.1), location: 0.35),
                                    .init(color: .clear, location: 0.45),
                                    .init(color: .white.opacity(0.65), location: 0.5),
                                    .init(color: .clear, location: 0.55),
                                    .init(color: .black.opacity(0.65), location: 1.0)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .blendMode(.overlay)
                )
                .shadow(color: .black.opacity(0.4), radius: 3.5, x: 0, y: 2.5)

        case .flat:
            Image(assetName)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .shadow(color: .black.opacity(0.12), radius: 1, x: 0, y: 1)

        case .simpleThin:
            Image(assetName)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .shadow(color: .black.opacity(0.12), radius: 1, x: 0, y: 1)
        }
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
