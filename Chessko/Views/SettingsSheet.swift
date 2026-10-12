import SwiftUI

// MARK: - Board Theme Definition

enum BoardTheme: String, CaseIterable, Identifiable, Sendable {
    // Redosled = redosled u mreži Podešavanja i u prečici prevlačenjem (spec § 11 + odluka
    // vlasnika: Polarna/Smaragd/Pesak/Sajber obrisane, Žalfija i Glina dodate na kraj, ravna boja).
    // Sačuvana obrisana vrednost se pri startu prepisuje na `classic` —
    // `GameViewModel.migrateRemovedAppearance()`.
    case classic
    case forest
    case wood
    case charcoal
    case sage
    case clay

    var id: String { rawValue }

    var label: String {
        switch self {
        case .classic:  return Loc("Klasična")
        case .forest:   return Loc("Šumska")
        case .wood:     return Loc("Drvo")
        case .charcoal: return Loc("Ugalj")
        case .sage:     return Loc("Žalfija")
        case .clay:     return Loc("Glina")
        }
    }

    var lightSquareColor: Color {
        switch self {
        case .classic:  return Color(hex: "#e9ebde")
        case .forest:   return Color(hex: "#ececd7")
        case .wood:     return Color(hex: "#f0d9b5")
        case .charcoal: return Color(hex: "#e8e8e8")
        case .sage:     return Color(hex: "#E7EAE5")
        case .clay:     return Color(hex: "#EEEAE8")
        }
    }

    var darkSquareColor: Color {
        switch self {
        case .classic:  return Color(hex: "#8592af")
        case .forest:   return Color(hex: "#739552")
        case .wood:     return Color(hex: "#b58863")
        case .charcoal: return Color(hex: "#646464")
        case .sage:     return Color(hex: "#87927F")
        case .clay:     return Color(hex: "#9A8A87")
        }
    }
}

// MARK: - Settings Sheet (hamburger menu)
//
// Presented from the game screen's top-left hamburger button.
// Lets the player set AI difficulty, choose the app language, customize the board, and configure sound/haptics.
//
// spec § 15: grupe su sopstvene `surface` kartice sa 1 pt `line` ivicom i `line` razdelnicima
// (`SettingsGroup`), u `ScrollView`-u nad `DS.ground`. Inset-grouped `List` ne prima okvir
// (Task 2), pa je lista zamenjena. Redosled grupa je referentni i za Android
// (`ChesskoAndroid/.../ui/SettingsView.kt`): Statistika, Težina, Izgled table, Stil figura,
// Igra i pravila, Jezik (jedan red), Izgled, Ostalo, O aplikaciji.

struct SettingsSheet: View {

    var gameViewModel: GameViewModel
    var localization: LocalizationManager
    @Environment(\.dismiss) private var dismiss

    /// spec § 15: adaptivna mreža, najmanje 88 pt po koloni (teme i stilovi isto).
    private let tileColumns = [GridItem(.adaptive(minimum: 88), spacing: DS.Space.s, alignment: .top)]

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
    @AppStorage("showEvalBar")           private var showEvalBar:           Bool   = true
    @State private var showResetStatsConfirm: Bool = false

    private var preferredColorScheme: ColorScheme? {
        switch colorScheme {
        case "light": return .light
        case "dark":  return .dark
        default:      return nil
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DS.Space.xl) {
                    statisticsGroup
                    difficultyGroup
                    boardGroup
                    piecesGroup
                    gameplayGroup
                    languageGroup
                    appearanceGroup
                    otherGroup
                    aboutGroup
                }
                .padding(.horizontal, DS.Space.l)
                .padding(.vertical, DS.Space.l)
            }
            // spec § 3: podloga ekrana je `DS.ground`, grupe `DS.surface` — nijedna sistemska boja.
            .background(DS.ground)
            .toolbarBackground(DS.ground, for: .navigationBar)
            .navigationTitle(Loc("Podešavanja"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(Loc("Gotovo")) { dismiss() }
                }
            }
            .alert(Loc("Potvrda"), isPresented: $showResetStatsConfirm) {
                Button(Loc("Resetuj"), role: .destructive) {
                    StatsManager.shared.resetStats()
                }
                Button(Loc("Otkaži"), role: .cancel) {}
            } message: {
                Text(Loc("Da li želiš da resetuješ celu statistiku?"))
            }
        }
        .tint(DS.accent)
        .preferredColorScheme(preferredColorScheme)
    }

    // MARK: Groups

    private var statisticsGroup: some View {
        let stats = StatsManager.shared
        return SettingsGroup(title: Loc("Statistika igranja")) {
            VStack(spacing: DS.Space.m) {
                HStack {
                    StatBox(label: Loc("Odigrano"), value: "\(stats.gamesPlayed)", color: DS.ink)
                    Spacer()
                    StatBox(label: Loc("Pobede"), value: "\(stats.gamesWon)", color: DS.success)
                    Spacer()
                    StatBox(label: Loc("Porazi"), value: "\(stats.gamesLost)", color: DS.danger)
                    Spacer()
                    // spec § 15: „Remi" nije upozorenje — `ink`.
                    StatBox(label: Loc("Remi"), value: "\(stats.gamesDrawn)", color: DS.ink)
                }

                SettingsRowDivider()

                HStack {
                    StatBox(label: Loc("Uspešnost"), value: LocPercent(Double(stats.winRate), fractionDigits: 0), color: DS.ink)
                    Spacer()
                    StatBox(label: Loc("Najbolji niz"), value: "\(stats.bestWinStreak) 🔥", color: DS.warning)
                    Spacer()
                    StatBox(label: Loc("Rešeno zadataka"), value: "\(stats.puzzlesSolved) 🧩", color: DS.ink)
                    Spacer()
                    StatBox(label: Loc("Rejting zadataka"), value: "\(stats.puzzleRating)", color: DS.ink)
                }

                if stats.gamesPlayed > 0 || stats.puzzlesSolved > 0 {
                    SettingsRowDivider()
                    Button(role: .destructive) {
                        showResetStatsConfirm = true
                    } label: {
                        Text(Loc("Resetuj statistiku"))
                            .font(.dsCaption.weight(.medium))
                            .foregroundStyle(DS.danger)
                    }
                    .buttonStyle(.plain)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
            .padding(DS.Space.l)
        }
    }

    private var difficultyGroup: some View {
        SettingsGroup(title: Loc("Težina protivnika (AI)")) {
            let all = Array(GameDifficulty.allCases.enumerated())
            ForEach(all, id: \.element) { index, diff in
                if index > 0 { SettingsRowDivider() }
                Button {
                    gameViewModel.setDifficulty(diff)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(diff.title)
                                .font(.dsBody)
                                .bold(gameViewModel.difficulty == diff)
                                .foregroundStyle(gameViewModel.difficulty == diff ? DS.accent : DS.ink)
                            Text(diff.subtitle)
                                .font(.dsLabel)
                                .foregroundStyle(DS.inkMuted)
                        }
                        Spacer()
                        if gameViewModel.difficulty == diff {
                            Image(systemName: "checkmark")
                                .foregroundStyle(DS.accent)
                        }
                    }
                    .settingsRowPadding()
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    // spec § 15: jedan naslov po grupi — bez unutrašnjeg „Tema table" ispod „Izgled table".
    private var boardGroup: some View {
        SettingsGroup(title: Loc("Izgled table")) {
            LazyVGrid(columns: tileColumns, spacing: DS.Space.m) {
                // Podaci iz `BoardTheme.allCases` — brisanje/dodavanje teme je izmena enum-a.
                ForEach(BoardTheme.allCases) { theme in
                    let isSelected = boardTheme == theme.rawValue
                    Button {
                        boardTheme = theme.rawValue
                    } label: {
                        VStack(spacing: DS.Space.xs + 2) {
                            VStack(spacing: 0) {
                                HStack(spacing: 0) {
                                    theme.lightSquareColor.frame(width: 22, height: 22)
                                    theme.darkSquareColor.frame(width: 22, height: 22)
                                }
                                HStack(spacing: 0) {
                                    theme.darkSquareColor.frame(width: 22, height: 22)
                                    theme.lightSquareColor.frame(width: 22, height: 22)
                                }
                            }
                            .clipShape(RoundedRectangle(cornerRadius: DS.Radius.s))
                            .overlay(
                                RoundedRectangle(cornerRadius: DS.Radius.s)
                                    .strokeBorder(isSelected ? DS.accent : DS.line, lineWidth: isSelected ? 2.5 : 1)
                            )
                            .shadow(color: .black.opacity(0.08), radius: 2, y: 1)

                            SettingsTileLabel(text: theme.label, isSelected: isSelected)
                        }
                        .frame(maxWidth: .infinity)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(DS.Space.l)

            SettingsRowDivider()
            toggleRow(Loc("Prikaži koordinate"), systemImage: "character.textbox", isOn: $showCoordinates)
            SettingsRowDivider()
            toggleRow(Loc("Prikaži poslednji potez"), systemImage: "arrow.left.and.right.square", isOn: $showLastMoveHighlight)
            SettingsRowDivider()
            toggleRow(Loc("Prikaži moguća polja"), systemImage: "circle.dashed", isOn: $showLegalMoves)
            SettingsRowDivider()
            toggleRow(Loc("Prevlačenje levo/desno"), systemImage: "arrow.left.and.right", isOn: $swipeToChangeBoardTheme)
        }
    }

    // spec § 15: bez unutrašnjeg „Stil" ispod „Stil figura".
    private var piecesGroup: some View {
        SettingsGroup(title: Loc("Stil figura")) {
            LazyVGrid(columns: tileColumns, spacing: DS.Space.m) {
                // Podaci iz `PieceStyle.allCases` — brisanje/dodavanje stila je izmena enum-a.
                ForEach(PieceStyle.allCases) { style in
                    let isSelected = pieceStyle == style.rawValue
                    Button {
                        pieceStyle = style.rawValue
                    } label: {
                        VStack(spacing: DS.Space.xs + 2) {
                            ZStack {
                                RoundedRectangle(cornerRadius: DS.Radius.s)
                                    .fill(DS.fill)
                                    .frame(width: 44, height: 44)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: DS.Radius.s)
                                            .strokeBorder(isSelected ? DS.accent : DS.line, lineWidth: isSelected ? 2 : 1)
                                    )
                                PieceImageView(
                                    piece: ChessPiece(type: .knight, color: .white),
                                    styleOverride: style
                                )
                                .frame(width: 30, height: 30)
                            }
                            .shadow(color: .black.opacity(0.05), radius: 2, y: 1)

                            SettingsTileLabel(text: style.label, isSelected: isSelected)
                        }
                        .frame(maxWidth: .infinity)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(DS.Space.l)

            SettingsRowDivider()
            toggleRow(Loc("Prevlačenje gore/dole"), systemImage: "arrow.up.and.down", isOn: $swipeToChangePieceStyle)
        }
    }

    private var gameplayGroup: some View {
        SettingsGroup(title: Loc("Igra i pravila")) {
            toggleRow(Loc("Automatska promocija u damu"), systemImage: "crown", isOn: $autoPromoteToQueen)
            SettingsRowDivider()
            toggleRow(Loc("Rotiraj tablu u lokalnoj igri"), systemImage: "arrow.triangle.2.circlepath", isOn: $rotateBoardInLocalPlay)
            SettingsRowDivider()
            toggleRow(Loc("Evaluaciona traka (Eval Bar)"), systemImage: "chart.bar.xaxis", isOn: $showEvalBar)
                .onChange(of: showEvalBar) { _, _ in
                    gameViewModel.updateEvaluation()
                }
        }
    }

    /// spec § 15: jezik je JEDAN red „Jezik · Srpski ›" u šestoj grupi; lista je na
    /// sledećem ekranu. Grupa nema naslov — naslov „Jezik" iznad reda „Jezik" bio bi dupli.
    private var languageGroup: some View {
        SettingsGroup(title: nil) {
            NavigationLink {
                LanguageListView(localization: localization)
            } label: {
                HStack(spacing: DS.Space.m) {
                    rowIcon("globe")
                    Text(Loc("Jezik"))
                        .font(.dsBody)
                        .foregroundStyle(DS.ink)
                    Spacer(minLength: DS.Space.s)
                    Text(currentLanguageLabel)
                        .font(.dsBody)
                        .foregroundStyle(DS.inkMuted)
                        .lineLimit(1)
                    Image(systemName: "chevron.right")
                        .font(.dsCaption.weight(.semibold))
                        .foregroundStyle(DS.inkMuted)
                }
                .settingsRowPadding()
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private var appearanceGroup: some View {
        SettingsGroup(title: Loc("Izgled")) {
            themeRow(Loc("Sistem"), value: "system", icon: "circle.lefthalf.filled")
            SettingsRowDivider()
            themeRow(Loc("Svetla"), value: "light",  icon: "sun.max")
            SettingsRowDivider()
            themeRow(Loc("Tamna"),  value: "dark",   icon: "moon")
        }
    }

    private var otherGroup: some View {
        SettingsGroup(title: Loc("Ostalo")) {
            toggleRow(Loc("Zvuk"), systemImage: soundEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill", isOn: $soundEnabled)
            SettingsRowDivider()
            toggleRow(Loc("Vibracija"), systemImage: hapticsEnabled ? "iphone.radiowaves.left.and.right" : "iphone.slash", isOn: $hapticsEnabled)
        }
    }

    private var aboutGroup: some View {
        SettingsGroup(title: Loc("O aplikaciji")) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Chessko")
                    .font(.dsHeading)
                    .foregroundStyle(DS.ink)
                Text(Loc("Verzija 1.0.0"))
                    .font(.dsBody)
                    .foregroundStyle(DS.inkMuted)

                SettingsRowDivider()
                    .padding(.vertical, 4)

                Text(Loc("Ova aplikacija je otvorenog koda, koristi Stockfish šahovski pokretač pod GPLv3 licencom i preuzima šahovske zadatke iz slobodne Lichess baze."))
                    .font(.dsCaption)
                    .foregroundStyle(DS.inkMuted)
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: 16) {
                    Link(destination: URL(string: "https://stockfishchess.org")!) {
                        Label("Stockfish", systemImage: "safari")
                            .font(.dsCaption.weight(.semibold))
                            .foregroundStyle(DS.accent)
                    }
                    Link(destination: URL(string: "https://lichess.org")!) {
                        Label("Lichess", systemImage: "safari")
                            .font(.dsCaption.weight(.semibold))
                            .foregroundStyle(DS.accent)
                    }
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(DS.Space.l)
        }
    }

    // MARK: Rows

    struct StatBox: View {
        let label: String
        let value: String
        let color: Color

        var body: some View {
            VStack(spacing: 2) {
                Text(value)
                    .font(.dsHeading)
                    .bold()
                    .foregroundStyle(color)
                Text(label)
                    .font(.dsLabel)
                    .foregroundStyle(DS.inkMuted)
                    .multilineTextAlignment(.center)
            }
        }
    }

    /// spec § 15: ikone u redovima su `inkMuted` (5,86 / 5,43), ne `accent`.
    private func rowIcon(_ systemImage: String) -> some View {
        Image(systemName: systemImage)
            .font(.dsBody)
            .foregroundStyle(DS.inkMuted)
            .frame(width: 24)
    }

    private func toggleRow(_ title: String, systemImage: String, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            HStack(spacing: DS.Space.m) {
                rowIcon(systemImage)
                Text(title)
                    .font(.dsBody)
                    .foregroundStyle(DS.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .tint(DS.accent)
        .padding(.horizontal, DS.Space.l)
        .padding(.vertical, DS.Space.s)
        .frame(minHeight: 44)
    }

    private func themeRow(_ label: String, value: String, icon: String) -> some View {
        Button {
            colorScheme = value
        } label: {
            HStack(spacing: DS.Space.m) {
                rowIcon(icon)
                Text(label)
                    .font(.dsBody)
                    .foregroundStyle(DS.ink)
                Spacer()
                if colorScheme == value {
                    Image(systemName: "checkmark").foregroundStyle(DS.accent)
                }
            }
            .settingsRowPadding()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(colorScheme == value ? .isSelected : [])
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

// MARK: - Language list (spec § 15)

/// Lista jezika, isti redosled na obe platforme: Sistem, pa `AppLanguage.all`
/// (Deutsch, English, Français, Italiano, Srpski, Русский, हिन्दी, 中文 (简体)).
/// Ispod „Sistem" sitno (`inkMuted`) jezik u koji se „Sistem" na ovom uređaju razrešava
/// (`systemFallback`; kad je „Sistem" izabran, to je i trenutni jezik).
private struct LanguageListView: View {
    var localization: LocalizationManager

    var body: some View {
        ScrollView {
            SettingsGroup(title: nil) {
                languageRow(code: nil,
                            name: Loc("Sistem"),
                            detail: AppLanguage.all.first(where: { $0.code == LocalizationManager.systemFallback() })?.endonym)
                ForEach(AppLanguage.all) { lang in
                    SettingsRowDivider()
                    languageRow(code: lang.code, name: lang.endonym, detail: nil)
                }
            }
            .padding(DS.Space.l)
        }
        .background(DS.ground)
        .toolbarBackground(DS.ground, for: .navigationBar)
        .navigationTitle(Loc("Jezik"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func languageRow(code: String?, name: String, detail: String?) -> some View {
        let isSelected = localization.languageCode == code
        return Button {
            localization.setLanguage(code)
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(name)
                        .font(.dsBody)
                        .foregroundStyle(isSelected ? DS.accent : DS.ink)
                    if let detail {
                        Text(detail)
                            .font(.dsCaption)
                            .foregroundStyle(DS.inkMuted)
                    }
                }
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark").foregroundStyle(DS.accent)
                }
            }
            .settingsRowPadding()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

// MARK: - Grouped list building blocks (spec § 3, § 15)

/// Grupa: naslov (`DSSectionHeader`, opciono) + `surface` kartica sa 1 pt `line` ivicom.
/// Redovi se razdvajaju sa `SettingsRowDivider()` (spec § 3 „Grupe").
private struct SettingsGroup<Content: View>: View {
    let title: String?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: DS.Space.s) {
            if let title {
                DSSectionHeader(title)
            }
            VStack(spacing: 0) {
                content
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .clipShape(RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
            .dsCard()
        }
    }
}

/// Razdelnik između redova grupe: 1 pt `DS.line`.
private struct SettingsRowDivider: View {
    var body: some View {
        DS.line
            .frame(height: 1)
            .frame(maxWidth: .infinity)
    }
}

/// Labela ispod pločice teme/stila — spec § 15: `Type.caption`, najviše 2 reda sa
/// rezervisanom visinom, `minimumScaleFactor(0.9)`.
private struct SettingsTileLabel: View {
    let text: String
    let isSelected: Bool

    var body: some View {
        Text(text)
            .font(.dsCaption.weight(isSelected ? .semibold : .medium))
            .foregroundStyle(isSelected ? DS.accent : DS.ink)
            .multilineTextAlignment(.center)
            .lineLimit(2, reservesSpace: true)
            .minimumScaleFactor(0.9)
            .frame(maxWidth: .infinity)
    }
}

private extension View {
    /// Unutrašnji razmak reda u grupi: `Space.l` vodoravno, `Space.m` uspravno, najmanje 44 pt.
    func settingsRowPadding() -> some View {
        self
            .padding(.horizontal, DS.Space.l)
            .padding(.vertical, DS.Space.m)
            .frame(minHeight: 44)
    }
}

#Preview {
    SettingsSheet(gameViewModel: GameViewModel(), localization: .shared)
}
