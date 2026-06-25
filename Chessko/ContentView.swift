import SwiftUI

struct ContentView: View {
    // Initialized at app launch — not during tab-switch animation
    @State private var gameViewModel   = GameViewModel()
    @State private var puzzleViewModel = PuzzleViewModel()
    @State private var learnViewModel  = LearnViewModel()

    var body: some View {
        TabView {
            GameView(viewModel: gameViewModel)
                .tabItem {
                    Label("Igra", systemImage: "crown.fill")
                }

            PuzzleView(viewModel: puzzleViewModel)
                .tabItem {
                    Label("Zadaci", systemImage: "puzzlepiece.fill")
                }

            LearnView(viewModel: learnViewModel)
                .tabItem {
                    Label("Učenje", systemImage: "graduationcap.fill")
                }
        }
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
