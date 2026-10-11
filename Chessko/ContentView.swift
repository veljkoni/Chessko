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

    /// Izabran tab — potreban da bi ikona birala pun oblik (izabran) ili obris
    /// (neizabran), spec § 10. Sistemska traka sama puni SVAKI simbol
    /// (`symbolVariant(.fill)`), pa bez ovoga obris nikad ne bi bio vidljiv.
    @State private var selectedTab: AppTab = .game

    private enum AppTab: Hashable { case game, puzzles, path }

    init() {
        // Neizabran tab: obris u `inkMuted` (5,86 / 5,62 na navBar), spec § 10.
        // Izabran ide kroz `.tint(DS.accent)` ispod. Staklena traka ostaje (§ 4).
        UITabBar.appearance().unselectedItemTintColor = UIColor(DS.inkMuted)
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            GameView(viewModel: gameViewModel, localization: localization)
                .tabItem {
                    // Sopstveni skakac (spec § 10) — vektor iz asset kataloga, template.
                    // TODO(design § 10): pravi SF custom symbol (`chessko.knight` iz
                    // SF Symbols aplikacije) umesto SVG slike; ime asseta je vec isto.
                    Label {
                        Text("Igra")
                    } icon: {
                        Image(selectedTab == .game ? "chessko.knight.fill" : "chessko.knight")
                            .renderingMode(.template)
                    }
                }
                .tag(AppTab.game)

            PuzzleView(viewModel: puzzleViewModel)
                .tabItem {
                    Label("Zadaci", systemImage: selectedTab == .puzzles ? "puzzlepiece.fill" : "puzzlepiece")
                        .environment(\.symbolVariants, .none)
                }
                .tag(AppTab.puzzles)

            PathView(viewModel: learnViewModel)
                .tabItem {
                    // Dostupan od iOS 17 (SF Symbols 5) — provereno u
                    // `name_availability.plist`; cilj je iOS 18.
                    Label("Put", systemImage: selectedTab == .path
                          ? "point.topleft.down.to.point.bottomright.curvepath.fill"
                          : "point.topleft.down.to.point.bottomright.curvepath")
                        .environment(\.symbolVariants, .none)
                }
                .tag(AppTab.path)
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
