import SwiftUI

// MARK: - Piece Style Definition

enum PieceStyle: String, CaseIterable, Identifiable, Sendable {
    // Redosled = redosled u mreži Podešavanja i u prečici prevlačenjem (spec § 12 + odluka
    // vlasnika: Neonski/Drvene/Metalne/Ravne/Tanke su obrisani, Reljef i Rez dodati na kraj).
    // Sačuvana obrisana vrednost (`neon`, `wood`, `metal`, `flat`, `simpleThin`) se pri startu
    // prepisuje na `classic` — `GameViewModel.migrateRemovedAppearance()`.
    case classic
    case gameroom
    case glass
    case reljef
    case rez

    var id: String { rawValue }

    var label: String {
        switch self {
        case .classic:  return Loc("Klasični")
        case .gameroom: return Loc("Igraonica")
        case .glass:    return Loc("Staklene")
        case .reljef:   return Loc("Reljef")
        case .rez:      return Loc("Rez")
        }
    }

    /// Sufiks imena asseta (`piece_white_king_<sufiks>`); klasični nema sufiks.
    var assetSuffix: String? {
        switch self {
        case .classic:  return nil
        case .gameroom: return "gameroom"
        case .glass:    return "glass"
        case .reljef:   return "reljef"
        case .rez:      return "rez"
        }
    }
}

// MARK: - Piece Image View

struct PieceImageView: View {
    let piece: ChessPiece
    var styleOverride: PieceStyle? = nil

    @AppStorage("pieceStyle") private var pieceStyle: String = "classic"

    private var style: PieceStyle {
        styleOverride ?? PieceStyle(rawValue: pieceStyle) ?? .classic
    }

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
        if let suffix = style.assetSuffix {
            return "piece_\(color)_\(type)_\(suffix)"
        }
        return "piece_\(color)_\(type)"
    }

    var body: some View {
        let image = Image(assetName)
            .resizable()
            .interpolation(.high)
            .scaledToFit()

        switch style {
        case .classic, .gameroom:
            image.shadow(color: .black.opacity(0.18), radius: 1, x: 0, y: 1)
        case .glass:
            image.shadow(color: .black.opacity(0.20), radius: 1.5, x: 0, y: 1)
        case .reljef:
            // Senka je deo crteža (clipPath u SVG-u) — bez dodatne senke.
            image
        case .rez:
            image.shadow(color: .black.opacity(0.18), radius: 1, x: 0, y: 1)
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
