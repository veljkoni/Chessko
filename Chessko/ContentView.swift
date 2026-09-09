import SwiftUI

struct ContentView: View {
    // Initialized at app launch — not during tab-switch animation
    @State private var gameViewModel   = GameViewModel()
    @State private var puzzleViewModel = PuzzleViewModel()
    @State private var learnViewModel  = LearnViewModel()
    @State private var localization    = LocalizationManager.shared

    @AppStorage("appColorScheme") private var colorSchemePref: String = "system"

    private var preferredColorScheme: ColorScheme? {
        switch colorSchemePref {
        case "light": return .light
        case "dark":  return .dark
        default:      return nil
        }
    }

    var body: some View {
        TabView {
            GameView(viewModel: gameViewModel, localization: localization)
                .tabItem {
                    Label("Igra", systemImage: "square.grid.3x3.fill")
                }

            PuzzleView(viewModel: puzzleViewModel)
                .tabItem {
                    Label("Zadaci", systemImage: "puzzlepiece.fill")
                }

            // Od Faze 4a treci tab je Put, a ne Učenje. `LearnView` ostaje u
            // projektu dok se ne potvrdi da Put pokriva sve sto je Učenje
            // nudilo (brisanje ide u zavrsni zadatak faze).
            PathView(viewModel: learnViewModel)
                .tabItem {
                    Label("Put", systemImage: "signpost.right.fill")
                }
        }
        .tint(DS.accent)
        .preferredColorScheme(preferredColorScheme)
        // Rebuild the whole tree when the language changes so every Text
        // re-resolves against the newly selected .lproj bundle.
        .id(localization.refreshID)
        .environment(\.locale, localization.locale)
    }
}

#Preview("Svetla tema") {
    ContentView()
        .preferredColorScheme(.light)
}

#Preview("Tamna tema") {
    ContentView()
        .preferredColorScheme(.dark)
}
