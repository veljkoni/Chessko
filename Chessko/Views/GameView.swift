import SwiftUI

// MARK: - Game View

struct GameView: View {
    var viewModel: GameViewModel
    var localization: LocalizationManager = .shared
    @State private var showColorPicker = false
    @State private var showNewGameConfirm = false
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {

                    // Status bar
                    //statusBar

                    // Computer header (opposite of player's color)
                    let computerColor = viewModel.playerColor.opposite
                    playerHeader(
                        color: computerColor,
                        label: "Računar",
                        capturedPieces: computerColor == .white
                            ? viewModel.gameState.capturedByWhite
                            : viewModel.gameState.capturedByBlack
                    )

                    // Chess board — square based on container width;
                    // inside ScrollView the proposed height is ∞ so
                    // aspectRatio always resolves to width × width.
                    BoardView(
                        board:            viewModel.gameState.board,
                        isFlipped:        viewModel.isFlipped,
                        selectedPosition: viewModel.selectedPosition,
                        legalMoves:       viewModel.legalMovesForSelected,
                        lastMove:         viewModel.lastMove,
                        animatingPiece:   viewModel.animatingPiece,
                        flyingCapture:    viewModel.flyingCapture,
                        playerColor:      viewModel.playerColor,
                        isPlayerTurn:     viewModel.isPlayerTurn,
                        onTap:            { viewModel.tap(position: $0) }
                    )
                    .frame(maxWidth: .infinity)
                    .aspectRatio(1, contentMode: .fit)

                    // Player header (player's chosen color)
                    playerHeader(
                        color: viewModel.playerColor,
                        label: "Ti",
                        capturedPieces: viewModel.playerColor == .white
                            ? viewModel.gameState.capturedByWhite
                            : viewModel.gameState.capturedByBlack
                    )

                    // Move history
                    if !viewModel.gameState.moveNotations.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Potezi")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .padding(.leading, 4)
                            MoveHistoryView(notations: viewModel.gameState.moveNotations)
                        }
                    }
                }
                .padding(.horizontal, 8)
                .padding(.top, 4)
                .padding(.bottom, 16)
            }
            .scrollBounceBehavior(.basedOnSize)
            .navigationTitle("Chessko")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        requestNewGame()
                    } label: {
                        Image(systemName: "plus")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        viewModel.undo()
                    } label: {
                        Image(systemName: "arrow.uturn.backward")
                    }
                    .disabled(!viewModel.canUndo)
                }
                if #available(iOS 26.0, *) {
                    ToolbarSpacer(.fixed, placement: .primaryAction)
                } 
ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "line.3.horizontal")
                    }
                }
            }
            .background(AppBackgroundView())
            .toolbarBackground(Color.appBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .overlay(gameOverOverlay)
            .overlay(promotionOverlay)
            .overlay(colorPickerOverlay)
            .alert("Napustiti partiju?", isPresented: $showNewGameConfirm) {
                Button("Napusti", role: .destructive) {
                    withAnimation(.spring(duration: 0.3)) { showColorPicker = true }
                }
                Button("Nastavi", role: .cancel) { }
            } message: {
                Text("Partija je u toku. Sigurno želiš da počneš iznova?")
            }
            .sheet(isPresented: $showSettings) {
                SettingsSheet(gameViewModel: viewModel, localization: localization)
            }
        }
    }

    // MARK: - Sub-views

    @ViewBuilder
    private func playerHeader(color: PieceColor, label: String, capturedPieces: [ChessPiece]) -> some View {
        let isActive   = viewModel.gameState.currentTurn == color && !viewModel.isGameOver
        let isComputer = color != viewModel.playerColor
        let matAdv     = materialAdvantage(for: color)

        HStack(alignment: .center, spacing: 10) {

            // King avatar
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(color == .white
                          ? Color.white.opacity(0.13)
                          : Color.black.opacity(0.28))
                    .frame(width: 38, height: 38)
                PieceImageView(piece: ChessPiece(type: .king, color: color))
                    .frame(width: 26, height: 26)
            }

            // Name row + captured pieces
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(LocalizedStringKey(label))
                        .font(.subheadline.weight(.semibold))
                    if matAdv > 0 {
                        Text("+\(matAdv)")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    // Turn / thinking indicator (right-aligned)
                    if isActive {
                        if isComputer && viewModel.isThinking {
                            ThinkingIndicator(isActive: true)
                        } else {
                            Circle()
                                .fill(Color.green)
                                .frame(width: 7, height: 7)
                        }
                    }
                }
                CapturedPiecesView(
                    pieces: capturedPieces,
                    capturedByColor: color,
                    flyingCapture: viewModel.flyingCapture
                )
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(
                    isActive ? Color.accentColor.opacity(0.55) : Color.primary.opacity(0.07),
                    lineWidth: isActive ? 1.5 : 1
                )
        )
        .animation(.easeInOut(duration: 0.2), value: isActive)
    }

    /// Razlika u vrednosti uzetih figura za datog igrača (0 ako je negativna).
    private func materialAdvantage(for color: PieceColor) -> Int {
        let w = viewModel.gameState.capturedByWhite.reduce(0) { $0 + $1.type.materialValue / 100 }
        let b = viewModel.gameState.capturedByBlack.reduce(0) { $0 + $1.type.materialValue / 100 }
        return max(0, color == .white ? w - b : b - w)
    }

//    private var statusBar: some View {
//        HStack {
//            Image(systemName: statusIcon)
//                .foregroundStyle(statusColor)
//                .font(.subheadline)
//            Text(viewModel.statusMessage)
//                .font(.subheadline)
//                .foregroundStyle(statusColor)
//                .animation(.default, value: viewModel.statusMessage)
//            Spacer()
//        }
//        .padding(.horizontal, 12)
//        .padding(.vertical, 10)
//        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
//    }

    private var statusIcon: String {
        switch viewModel.gameState.status {
        case .playing:   return viewModel.isThinking ? "cpu" : "circle.fill"
        case .check:     return "exclamationmark.triangle.fill"
        case .checkmate: return "crown.fill"
        case .draw:      return "equal.circle.fill"
        }
    }

    private var statusColor: Color {
        switch viewModel.gameState.status {
        case .playing:          return .primary
        case .check:            return .orange
        case .checkmate(let c): return c == viewModel.playerColor ? .red : .green
        case .draw:             return .secondary
        }
    }

    // MARK: - Color Picker Overlay

    @ViewBuilder
    private var colorPickerOverlay: some View {
        if showColorPicker {
            ZStack {
                Color.black.opacity(0.55).ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.spring(duration: 0.25)) { showColorPicker = false }
                    }

                VStack(spacing: 24) {
                    Text("Izaberi stranu")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.white)

                    HStack(spacing: 20) {
                        colorButton(color: .white)
                        colorButton(color: .black)
                    }
                }
                .padding(28)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
                .padding(.horizontal, 40)
            }
            .transition(.opacity.combined(with: .scale(scale: 0.95)))
        }
    }

    /// Starts a new game — asks for confirmation if a game is already in progress.
    private func requestNewGame() {
        let gameInProgress = !viewModel.isGameOver && !viewModel.gameState.moveHistory.isEmpty
        if gameInProgress {
            showNewGameConfirm = true
        } else {
            withAnimation(.spring(duration: 0.3)) { showColorPicker = true }
        }
    }

    private func colorButton(color: PieceColor) -> some View {
        Button {
            withAnimation(.spring(duration: 0.25)) { showColorPicker = false }
            viewModel.newGame(playerColor: color)
        } label: {
            VStack(spacing: 10) {
                PieceImageView(piece: ChessPiece(type: .king, color: color))
                    .frame(width: 64, height: 64)
                    .padding(12)
                    .background(
                        color == .white
                            ? Color.white.opacity(0.18)
                            : Color.black.opacity(0.35),
                        in: RoundedRectangle(cornerRadius: 14)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(.white.opacity(0.35), lineWidth: 1)
                    )
                Text(color.srbAdjective.capitalized)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
            }
        }
    }

    // MARK: - Promotion Overlay

    @ViewBuilder
    private var promotionOverlay: some View {
        if viewModel.showPromotion {
            ZStack {
                Color.black.opacity(0.5).ignoresSafeArea()

                VStack(spacing: 20) {
                    Text("Izaberi figuru")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.white)

                    HStack(spacing: 12) {
                        ForEach([PieceType.queen, .rook, .bishop, .knight], id: \.self) { type in
                            Button {
                                viewModel.confirmPromotion(type)
                            } label: {
                                PieceImageView(piece: ChessPiece(type: type, color: viewModel.playerColor))
                                    .frame(width: 64, height: 64)
                                    .padding(10)
                                    .background(.white.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(.white.opacity(0.3), lineWidth: 1)
                                    )
                            }
                        }
                    }
                }
                .padding(28)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
                .padding(.horizontal, 24)
            }
            .transition(.opacity.combined(with: .scale(scale: 0.95)))
        }
    }

    // MARK: - Game Over Overlay

    @ViewBuilder
    private var gameOverOverlay: some View {
        if viewModel.isGameOver {
            ZStack {
                Color.black.opacity(0.45).ignoresSafeArea()
                VStack(spacing: 20) {
                    Text(gameOverEmoji)
                        .font(.system(size: 64))
                    Text(viewModel.statusMessage)
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                    Button {
                        withAnimation(.spring(duration: 0.3)) { showColorPicker = true }
                    } label: {
                        Text("Nova igra")
                            .font(.headline)
                            .padding(.horizontal, 32)
                            .padding(.vertical, 12)
                            .background(.white)
                            .foregroundStyle(.black)
                            .clipShape(Capsule())
                    }
                }
                .padding(32)
            }
            .transition(.opacity)
        }
    }

    private var gameOverEmoji: String {
        switch viewModel.gameState.status {
        case .checkmate(let c): return c == viewModel.playerColor ? "😔" : "🏆"
        case .draw:             return "🤝"
        default:                return ""
        }
    }
}

// MARK: - Thinking Indicator

struct ThinkingIndicator: View {
    let isActive: Bool
    @State private var animating = false

    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .fill(Color.secondary)
                    .frame(width: 5, height: 5)
                    .scaleEffect(animating ? 1.0 : 0.4)
                    .animation(
                        isActive
                            ? .easeInOut(duration: 0.5).repeatForever().delay(Double(i) * 0.15)
                            : .default,
                        value: animating
                    )
            }
        }
        .onAppear { if isActive { animating = true } }
        .onChange(of: isActive) { _, newVal in animating = newVal }
    }
}

#Preview {
    GameView(viewModel: GameViewModel())
}
