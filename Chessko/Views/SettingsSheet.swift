import SwiftUI

// MARK: - Settings Sheet (hamburger menu)
//
// Presented from the game screen's top-left hamburger button.
// Lets the player set AI difficulty and choose the app language.

struct SettingsSheet: View {

    var gameViewModel: GameViewModel
    var localization: LocalizationManager
    @Environment(\.dismiss) private var dismiss

    @AppStorage("soundEnabled")    private var soundEnabled:    Bool   = true
    @AppStorage("appColorScheme") private var colorScheme:    String = "system"

    var body: some View {
        NavigationStack {
            List {
                // ── Difficulty (basic AI + Stockfish in one section) ────────
                Section("Težina") {
                    // Basic AI levels
                    let basicDiffs: [GameDifficulty] = [.easy, .medium, .hard]
                    ForEach(basicDiffs, id: \.self) { diff in
                        Button {
                            gameViewModel.setDifficulty(diff)
                        } label: {
                            HStack {
                                Text("\(diff.icon) \(diff.label)")
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
                                    Text("⚡ \(level.label)" + (level.elo.map { " (\($0))" } ?? ""))
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
                }

                // ── Language ────────────────────────────────────────────────
                Section("Jezik") {
                    languageRow(code: nil, name: Loc("Sistem"))
                    ForEach(AppLanguage.all) { lang in
                        languageRow(code: lang.code, name: lang.endonym)
                    }
                }

                // ── Appearance ───────────────────────────────────────────────
                Section("Izgled") {
                    themeRow("Sistem", value: "system", icon: "circle.lefthalf.filled")
                    themeRow("Svetla", value: "light",  icon: "sun.max")
                    themeRow("Tamna",  value: "dark",   icon: "moon")
                }

                // ── Sound ────────────────────────────────────────────────────
                Section {
                    Toggle(isOn: $soundEnabled) {
                        Label("Zvuk", systemImage: soundEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill")
                    }
                }
            }
            .listSectionSpacing(.compact)
            .navigationTitle("Podešavanja")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gotovo") { dismiss() }
                }
            }
        }
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
}

#Preview {
    SettingsSheet(gameViewModel: GameViewModel(), localization: .shared)
}
