import SwiftUI

// MARK: - Board Theme Definition

enum BoardTheme: String, CaseIterable, Identifiable, Sendable {
    case classic
    case forest
    case wood
    case charcoal
    case midnightAurora = "midnight_aurora"
    case sageEmerald    = "sage_emerald"
    case warmTerracotta = "warm_terracotta"
    case cyberLavender  = "cyber_lavender"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .classic:        return Loc("Klasična")
        case .forest:         return Loc("Šumska")
        case .wood:           return Loc("Drvo")
        case .charcoal:       return Loc("Ugalj")
        case .midnightAurora: return Loc("Polarna")
        case .sageEmerald:    return Loc("Smaragd")
        case .warmTerracotta: return Loc("Pesak")
        case .cyberLavender:  return Loc("Sajber")
        }
    }

    var lightSquareColor: Color {
        switch self {
        case .classic:        return Color(hex: "#e9ebde")
        case .forest:         return Color(hex: "#ececd7")
        case .wood:           return Color(hex: "#f0d9b5")
        case .charcoal:       return Color(hex: "#e8e8e8")
        case .midnightAurora: return Color(hex: "#E4E9F2")
        case .sageEmerald:    return Color(hex: "#F2F3EC")
        case .warmTerracotta: return Color(hex: "#F5EFEB")
        case .cyberLavender:  return Color(hex: "#ECE9FC")
        }
    }

    var darkSquareColor: Color {
        switch self {
        case .classic:        return Color(hex: "#8592af")
        case .forest:         return Color(hex: "#739552")
        case .wood:           return Color(hex: "#b58863")
        case .charcoal:       return Color(hex: "#646464")
        case .midnightAurora: return Color(hex: "#1E2530")
        case .sageEmerald:    return Color(hex: "#3D4F41")
        case .warmTerracotta: return Color(hex: "#9E4A35")
        case .cyberLavender:  return Color(hex: "#2B1D4F")
        }
    }
}

// MARK: - Settings Sheet (hamburger menu)
//
// Presented from the game screen's top-left hamburger button.
// Lets the player set AI difficulty, choose the app language, customize the board, and configure sound/haptics.

struct SettingsSheet: View {

    var gameViewModel: GameViewModel
    var localization: LocalizationManager
    @Environment(\.dismiss) private var dismiss

    private let columns = [
        GridItem(.flexible(), spacing: 10),
        GridItem(.flexible(), spacing: 10),
        GridItem(.flexible(), spacing: 10),
        GridItem(.flexible(), spacing: 10)
    ]

    @AppStorage("soundEnabled")          private var soundEnabled:          Bool   = true
    @AppStorage("hapticsEnabled")        private var hapticsEnabled:        Bool   = true
    @AppStorage("appColorScheme")        private var colorScheme:           String = "system"
    @AppStorage("boardTheme")            private var boardTheme:            String = "classic"
    @AppStorage("showCoordinates")       private var showCoordinates:       Bool   = true
    @AppStorage("showLastMoveHighlight") private var showLastMoveHighlight: Bool   = true
    @AppStorage("showLegalMoves")        private var showLegalMoves:        Bool   = true
    @AppStorage("autoPromoteToQueen")    private var autoPromoteToQueen:    Bool   = false
    @AppStorage("pieceStyle")            private var pieceStyle:            String = "classic"
    @AppStorage("rotateBoardInLocalPlay") private var rotateBoardInLocalPlay: Bool   = true
    @AppStorage("swipeToChangeBoardTheme") private var swipeToChangeBoardTheme: Bool = true
    @AppStorage("swipeToChangePieceStyle") private var swipeToChangePieceStyle: Bool = true
    @State private var isDifficultyExpanded: Bool = false
    @State private var isLanguageExpanded:   Bool = false

    private var preferredColorScheme: ColorScheme? {
        switch colorScheme {
        case "light": return .light
        case "dark":  return .dark
        default:      return nil
        }
    }

    var body: some View {
        NavigationStack {
            List {
                // ── Difficulty (basic AI + Stockfish in one expandable DisclosureGroup) ──
                Section {
                    DisclosureGroup(isExpanded: $isDifficultyExpanded) {
                        // Basic AI levels
                        let basicDiffs: [GameDifficulty] = [.easy, .medium, .hard]
                        ForEach(basicDiffs, id: \.self) { diff in
                            Button {
                                gameViewModel.setDifficulty(diff)
                            } label: {
                                HStack {
                                    Text(diff.label)
                                        .foregroundStyle(.primary)
                                    Spacer()
                                    if gameViewModel.difficulty == diff {
                                        Image(systemName: "checkmark")
                                            .foregroundStyle(.tint)
                                    }
                                }
                            }
                        }

                        // Stockfish levels
                        if gameViewModel.isStockfishAvailable {
                            ForEach(StockfishLevel.allCases, id: \.self) { level in
                                Button {
                                    gameViewModel.setDifficulty(.stockfish)
                                    gameViewModel.setStockfishLevel(level)
                                } label: {
                                    HStack {
                                        Text(level.label)
                                            .foregroundStyle(.primary)
                                        Spacer()
                                        if gameViewModel.difficulty == .stockfish
                                            && gameViewModel.stockfishLevel == level {
                                            Image(systemName: "checkmark")
                                                .foregroundStyle(.tint)
                                        }
                                    }
                                }
                            }
                        }
                    } label: {
                        HStack {
                            Label(Loc("Težina"), systemImage: "gauge.with.needle")
                            Spacer()
                            if !isDifficultyExpanded {
                                Text(currentDifficultyLabel)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                // ── Board Customization ──────────────────────────────────────
                Section(Loc("Izgled table")) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(Loc("Tema table"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        
                        LazyVGrid(columns: columns, spacing: 14) {
                            ForEach(BoardTheme.allCases) { theme in
                                Button {
                                    boardTheme = theme.rawValue
                                } label: {
                                    VStack(spacing: 6) {
                                        // Mini 2x2 grid representing the chessboard colors
                                        VStack(spacing: 0) {
                                            HStack(spacing: 0) {
                                                theme.lightSquareColor
                                                    .frame(width: 20, height: 20)
                                                theme.darkSquareColor
                                                    .frame(width: 20, height: 20)
                                            }
                                            HStack(spacing: 0) {
                                                theme.darkSquareColor
                                                    .frame(width: 20, height: 20)
                                                theme.lightSquareColor
                                                    .frame(width: 20, height: 20)
                                            }
                                        }
                                        .clipShape(RoundedRectangle(cornerRadius: 6))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 6)
                                                .stroke(boardTheme == theme.rawValue ? Color.accentColor : Color.primary.opacity(0.12), lineWidth: boardTheme == theme.rawValue ? 2.5 : 1)
                                        )
                                        .shadow(color: .black.opacity(0.08), radius: 2, y: 1)
                                        
                                        Text(theme.label)
                                            .font(.caption.weight(boardTheme == theme.rawValue ? .semibold : .medium))
                                            .foregroundStyle(boardTheme == theme.rawValue ? Color.accentColor : .primary)
                                            .lineLimit(1)
                                            .minimumScaleFactor(0.8)
                                    }
                                    .padding(.vertical, 4)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.vertical, 6)

                    Toggle(isOn: $showCoordinates) {
                        Label(Loc("Prikaži koordinate"), systemImage: "character.textbox")
                    }
                    
                    Toggle(isOn: $showLastMoveHighlight) {
                        Label(Loc("Prikaži poslednji potez"), systemImage: "arrow.left.and.right.square")
                    }
                    
                    Toggle(isOn: $showLegalMoves) {
                        Label(Loc("Prikaži moguća polja"), systemImage: "circle.dashed")
                    }
                    Toggle(isOn: $swipeToChangeBoardTheme) {
                        Label(Loc("Prevlačenje levo/desno"), systemImage: "arrow.left.and.right")
                    }
                }

                // ── Piece Customization ──────────────────────────────────────
                Section(Loc("Stil figura")) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(Loc("Stil"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        
                        LazyVGrid(columns: columns, spacing: 14) {
                            ForEach(PieceStyle.allCases) { style in
                                Button {
                                    pieceStyle = style.rawValue
                                } label: {
                                    VStack(spacing: 8) {
                                        // Mini preview of a Knight in this style
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 8)
                                                .fill(Color.primary.opacity(0.04))
                                                .frame(width: 44, height: 44)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 8)
                                                        .stroke(pieceStyle == style.rawValue ? Color.accentColor : Color.primary.opacity(0.10), lineWidth: pieceStyle == style.rawValue ? 2 : 1)
                                                )
                                            
                                            PieceImageView(
                                                piece: ChessPiece(type: .knight, color: .white),
                                                styleOverride: style
                                            )
                                            .frame(width: 30, height: 30)
                                        }
                                        .shadow(color: .black.opacity(0.05), radius: 2, y: 1)
                                        
                                        Text(style.label)
                                            .font(.caption.weight(pieceStyle == style.rawValue ? .semibold : .medium))
                                            .foregroundStyle(pieceStyle == style.rawValue ? Color.accentColor : .primary)
                                            .lineLimit(1)
                                            .minimumScaleFactor(0.8)
                                    }
                                    .padding(.vertical, 4)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.vertical, 6)
                    
                    Toggle(isOn: $swipeToChangePieceStyle) {
                        Label(Loc("Prevlačenje gore/dole"), systemImage: "arrow.up.and.down")
                    }
                }

                // ── Gameplay & Rules ─────────────────────────────────────────
                Section(Loc("Igra i pravila")) {
                    Toggle(isOn: $autoPromoteToQueen) {
                        Label(Loc("Automatska promocija u damu"), systemImage: "crown")
                    }
                    Toggle(isOn: $rotateBoardInLocalPlay) {
                        Label(Loc("Rotiraj tablu u lokalnoj igri"), systemImage: "arrow.triangle.2.circlepath")
                    }
                }

                // ── Language (collapsible DisclosureGroup) ──────────────────
                Section {
                    DisclosureGroup(isExpanded: $isLanguageExpanded) {
                        languageRow(code: nil, name: Loc("Sistem"))
                        
                        let sortedLanguages = AppLanguage.all.sorted { $0.endonym.localizedCompare( $1.endonym ) == .orderedAscending }
                        ForEach(sortedLanguages) { lang in
                            languageRow(code: lang.code, name: lang.endonym)
                        }
                    } label: {
                        HStack {
                            Label(Loc("Jezik"), systemImage: "globe")
                            Spacer()
                            if !isLanguageExpanded {
                                Text(currentLanguageLabel)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                // ── Appearance ───────────────────────────────────────────────
                Section(Loc("Izgled")) {
                    themeRow(Loc("Sistem"), value: "system", icon: "circle.lefthalf.filled")
                    themeRow(Loc("Svetla"), value: "light",  icon: "sun.max")
                    themeRow(Loc("Tamna"),  value: "dark",   icon: "moon")
                }

                // ── Sound & Haptics (Other) ──────────────────────────────────
                Section(Loc("Ostalo")) {
                    Toggle(isOn: $soundEnabled) {
                        Label(Loc("Zvuk"), systemImage: soundEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill")
                    }
                    Toggle(isOn: $hapticsEnabled) {
                        Label(Loc("Vibracija"), systemImage: hapticsEnabled ? "iphone.radiowaves.left.and.right" : "iphone.slash")
                    }
                }

                // ── About App & Licenses ─────────────────────────────────────
                Section(Loc("O aplikaciji")) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Chessko")
                            .font(.headline)
                            .foregroundStyle(.primary)
                        Text(Loc("Verzija 1.0.0"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        
                        Divider()
                            .padding(.vertical, 4)
                        
                        Text(Loc("Ova aplikacija je otvorenog koda, koristi Stockfish šahovski pokretač pod GPLv3 licencom i preuzima šahovske zadatke iz slobodne Lichess baze."))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(nil)
                            .fixedSize(horizontal: false, vertical: true)
                        
                        HStack(spacing: 16) {
                            Link(destination: URL(string: "https://stockfishchess.org")!) {
                                Label("Stockfish", systemImage: "safari")
                                    .font(.caption.weight(.semibold))
                            }
                            
                            Link(destination: URL(string: "https://lichess.org")!) {
                                Label("Lichess", systemImage: "safari")
                                    .font(.caption.weight(.semibold))
                            }
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 2)
                    }
                    .padding(.vertical, 4)
                }
            }
            .listSectionSpacing(.compact)
            .navigationTitle(Loc("Podešavanja"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(Loc("Gotovo")) { dismiss() }
                }
            }
        }
        .preferredColorScheme(preferredColorScheme)
    }

    @ViewBuilder
    private func themeRow(_ label: String, value: String, icon: String) -> some View {
        Button {
            colorScheme = value
        } label: {
            HStack {
                Label(label, systemImage: icon).foregroundStyle(.primary)
                Spacer()
                if colorScheme == value {
                    Image(systemName: "checkmark").foregroundStyle(.tint)
                }
            }
        }
    }

    @ViewBuilder
    private func languageRow(code: String?, name: String) -> some View {
        Button {
            localization.setLanguage(code)
        } label: {
            HStack {
                Text(name)
                    .foregroundStyle(.primary)
                Spacer()
                if localization.languageCode == code {
                    Image(systemName: "checkmark")
                        .foregroundStyle(.tint)
                }
            }
        }
    }

    private var currentDifficultyLabel: String {
        if gameViewModel.difficulty == .stockfish {
            return gameViewModel.stockfishLevel.label
        } else {
            return gameViewModel.difficulty.label
        }
    }

    private var currentLanguageLabel: String {
        if let code = localization.languageCode,
           let lang = AppLanguage.all.first(where: { $0.code == code }) {
            return lang.endonym
        } else {
            return Loc("Sistem")
        }
    }
}

#Preview {
    SettingsSheet(gameViewModel: GameViewModel(), localization: .shared)
}
