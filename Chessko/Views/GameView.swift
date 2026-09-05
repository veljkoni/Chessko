import SwiftUI

// MARK: - Game View

struct GameView: View {
    var viewModel: GameViewModel
    var localization: LocalizationManager = .shared
    @AppStorage("showEvalBar") private var showEvalBar: Bool = true
    @State private var showColorPicker = false
    @State private var showNewGameConfirm = false
    @State private var showResignConfirm = false
    @State private var showSettings = false
    @State private var dismissGameOverOverlay = false

    enum PendingSelection {
        case computer
        case localFriend
        case chessClock
    }
    @State private var pendingSelection: PendingSelection? = nil
    @State private var showChessClock = false

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                let isLandscape = geo.size.width > geo.size.height

                if isLandscape {
                    HStack(alignment: .top, spacing: 12) {
                        // Left side: EvalBar (optional) + Board
                        HStack(spacing: 8) {
                            if showEvalBar {
                                EvalBarView(
                                    evaluation: viewModel.evaluationScore,
                                    mateIn: viewModel.evaluationMateIn,
                                    isFlipped: viewModel.isFlipped
                                )
                                .padding(.vertical, 8)
                            }

                            BoardView(
                                board:            viewModel.displayState.board,
                                isFlipped:        viewModel.isFlipped,
                                selectedPosition: viewModel.isReviewing ? nil : viewModel.selectedPosition,
                                legalMoves:       viewModel.isReviewing ? [] : viewModel.legalMovesForSelected,
                                lastMove:         viewModel.displayLastMove,
                                animatingPiece:   viewModel.animatingPiece,
                                flyingCapture:    viewModel.flyingCapture,
                                playerColor:      viewModel.activePlayerColor,
                                isPlayerTurn:     viewModel.isPlayerTurn,
                                gameStatus:       viewModel.displayState.status,
                                onTap:            { viewModel.tap(position: $0) }
                            )
                            .aspectRatio(1, contentMode: .fit)
                            .padding(.vertical, 8)
                        }
                        .frame(maxHeight: .infinity)

                        // Right side: Info, Review Controls and History
                        ScrollView {
                            VStack(spacing: 10) {
                                topHeader
                                bottomHeader

                                if viewModel.totalReviewMoves > 0 {
                                    reviewControlsView
                                }

                                if !viewModel.gameState.moveNotations.isEmpty {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(Loc("Potezi"))
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(.secondary)
                                            .padding(.leading, 4)
                                        MoveHistoryView(
                                            notations: viewModel.gameState.moveNotations,
                                            selectedMoveIndex: viewModel.viewingMoveIndex,
                                            onSelectMove: { viewModel.goToMove($0) }
                                        )
                                    }
                                }
                            }
                            .padding(.trailing, 8)
                            .padding(.vertical, 8)
                        }
                        .scrollBounceBehavior(.basedOnSize)
                    }
                    .padding(.horizontal, 16)
                } else {
                    ScrollView {
                        VStack(spacing: 12) {
                            topHeader

                            HStack(spacing: 8) {
                                if showEvalBar {
                                    EvalBarView(
                                        evaluation: viewModel.evaluationScore,
                                        mateIn: viewModel.evaluationMateIn,
                                        isFlipped: viewModel.isFlipped
                                    )
                                }

                                BoardView(
                                    board:            viewModel.displayState.board,
                                    isFlipped:        viewModel.isFlipped,
                                    selectedPosition: viewModel.isReviewing ? nil : viewModel.selectedPosition,
                                    legalMoves:       viewModel.isReviewing ? [] : viewModel.legalMovesForSelected,
                                    lastMove:         viewModel.displayLastMove,
                                    animatingPiece:   viewModel.animatingPiece,
                                    flyingCapture:    viewModel.flyingCapture,
                                    playerColor:      viewModel.activePlayerColor,
                                    isPlayerTurn:     viewModel.isPlayerTurn,
                                    gameStatus:       viewModel.displayState.status,
                                    onTap:            { viewModel.tap(position: $0) }
                                )
                                .frame(maxWidth: .infinity)
                                .aspectRatio(1, contentMode: .fit)
                            }

                            bottomHeader

                            if viewModel.totalReviewMoves > 0 {
                                reviewControlsView
                            }

                            if !viewModel.gameState.moveNotations.isEmpty {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(Loc("Potezi"))
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(.secondary)
                                        .padding(.leading, 4)
                                    MoveHistoryView(
                                        notations: viewModel.gameState.moveNotations,
                                        selectedMoveIndex: viewModel.viewingMoveIndex,
                                        onSelectMove: { viewModel.goToMove($0) }
                                    )
                                }
                            }
                        }
                        .padding(.horizontal, 8)
                        .padding(.top, 4)
                        .padding(.bottom, 16)
                    }
                    .scrollBounceBehavior(.basedOnSize)
                }
            }
            .navigationTitle("Chessko")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Menu {
                        Button {
                            handleNewGameSelection(.computer)
                        } label: {
                            Label(Loc("Igraj protiv računara"), systemImage: "cpu")
                        }

                        Button {
                            handleNewGameSelection(.localFriend)
                        } label: {
                            Label(Loc("Igraj sa prijateljem"), systemImage: "person.2")
                        }

                        Button {
                            handleNewGameSelection(.chessClock)
                        } label: {
                            Label(Loc("Šahovski sat"), systemImage: "timer")
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if viewModel.canResign {
                        Button {
                            showResignConfirm = true
                        } label: {
                            Image(systemName: "flag.fill")
                                .foregroundStyle(.red.opacity(0.85))
                        }
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
            .alert(Loc("Napustiti partiju?"), isPresented: $showNewGameConfirm) {
                Button(Loc("Napusti"), role: .destructive) {
                    if let selection = pendingSelection {
                        executeNewGameSelection(selection)
                        pendingSelection = nil
                    }
                }
                Button(Loc("Nastavi"), role: .cancel) {
                    pendingSelection = nil
                }
            } message: {
                Text(Loc("Partija je u toku. Sigurno želiš da počneš iznova?"))
            }
            .alert(Loc("Predati partiju?"), isPresented: $showResignConfirm) {
                Button(Loc("Predaj"), role: .destructive) {
                    viewModel.resign()
                }
                Button(Loc("Otkaži"), role: .cancel) {}
            } message: {
                Text(Loc("Da li ste sigurni da želite da predate trenutnu partiju?"))
            }
            .sheet(isPresented: $showSettings) {
                SettingsSheet(gameViewModel: viewModel, localization: localization)
            }
            .fullScreenCover(isPresented: $showChessClock) {
                ChessClockView()
            }
        }
    }

    // MARK: - Review Controls

    private var reviewControlsView: some View {
        HStack {
            Button {
                viewModel.goToStart()
            } label: {
                Image(systemName: "backward.end.fill")
                    .font(.system(size: 15, weight: .semibold))
                    .frame(width: 44, height: 38)
                    .contentShape(Rectangle())
            }
            .disabled(!viewModel.canStepBackward)

            Button {
                viewModel.stepBackward()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .bold))
                    .frame(width: 44, height: 38)
                    .contentShape(Rectangle())
            }
            .disabled(!viewModel.canStepBackward)

            Spacer()

            let label: String = {
                if viewModel.totalReviewMoves == 0 {
                    return Loc("Početna pozicija")
                } else if viewModel.currentReviewMoveIndex == 0 {
                    return Loc("Početna pozicija")
                } else {
                    return String(format: Loc("Potez %d od %d"), viewModel.currentReviewMoveIndex, viewModel.totalReviewMoves)
                }
            }()

            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(viewModel.isReviewing ? Color.cyan : Color.primary)

            Spacer()

            Button {
                viewModel.stepForward()
            } label: {
                Image(systemName: "chevron.right")
                    .font(.system(size: 17, weight: .bold))
                    .frame(width: 44, height: 38)
                    .contentShape(Rectangle())
            }
            .disabled(!viewModel.canStepForward)

            Button {
                viewModel.goToEnd()
            } label: {
                Image(systemName: "forward.end.fill")
                    .font(.system(size: 15, weight: .semibold))
                    .frame(width: 44, height: 38)
                    .contentShape(Rectangle())
            }
            .disabled(!viewModel.canStepForward)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.primary.opacity(0.12), lineWidth: 1)
        )
    }

    // MARK: - Sub-views

    private var topHeader: some View {
        let color = viewModel.isFlipped ? PieceColor.white : PieceColor.black
        return playerHeader(
            color: color,
            label: headerLabel(for: color),
            capturedPieces: color == .white
                ? viewModel.gameState.capturedByWhite
                : viewModel.gameState.capturedByBlack
        )
    }

    private var bottomHeader: some View {
        let color = viewModel.isFlipped ? PieceColor.black : PieceColor.white
        return playerHeader(
            color: color,
            label: headerLabel(for: color),
            capturedPieces: color == .white
                ? viewModel.gameState.capturedByWhite
                : viewModel.gameState.capturedByBlack
        )
    }

    private func headerLabel(for color: PieceColor) -> String {
        if viewModel.gameMode == .localFriend {
            return color == .white ? Loc("Beli") : Loc("Crni")
        } else {
            return color == viewModel.playerColor ? Loc("Ti") : viewModel.difficulty.label
        }
    }

    @ViewBuilder
    private func playerHeader(color: PieceColor, label: String, capturedPieces: [ChessPiece]) -> some View {
        let isActive   = viewModel.gameState.currentTurn == color && !viewModel.isGameOver
        let isComputer = viewModel.gameMode == .vsComputer && color != viewModel.playerColor
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
                    Text(Loc(label))
                        .font(.subheadline.weight(.semibold))
                    if matAdv > 0 {
                        Text("+\(matAdv)")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    // Evaluation score badge
                    if showEvalBar, let evalStr = evalTextForPlayer(color: color) {
                        Text(evalStr)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(isMateForPlayer(color: color) ? Color.yellow : Color.primary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.primary.opacity(0.08), in: RoundedRectangle(cornerRadius: 6))
                            .overlay(
                                RoundedRectangle(cornerRadius: 6)
                                    .stroke(Color.primary.opacity(0.12), lineWidth: 1)
                            )
                    }
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

    private func evalTextForPlayer(color: PieceColor) -> String? {
        if let mate = viewModel.evaluationMateIn {
            if color == .white && mate > 0 {
                return "M\(mate)"
            } else if color == .black && mate < 0 {
                return "M\(abs(mate))"
            }
            return nil
        }
        let score = viewModel.evaluationScore
        if color == .white {
            if score >= 0.1 {
                return String(format: "+%.1f", score)
            } else if abs(score) < 0.1 {
                return "0.0"
            }
            return nil
        } else {
            if score <= -0.1 {
                return String(format: "+%.1f", abs(score))
            }
            return nil
        }
    }

    private func isMateForPlayer(color: PieceColor) -> Bool {
        guard let mate = viewModel.evaluationMateIn else { return false }
        return (color == .white && mate > 0) || (color == .black && mate < 0)
    }

    /// Razlika u vrednosti uzetih figura za datog igrača (0 ako je negativna).
    private func materialAdvantage(for color: PieceColor) -> Int {
        let w = viewModel.gameState.capturedByWhite.reduce(0) { $0 + $1.type.materialValue / 100 }
        let b = viewModel.gameState.capturedByBlack.reduce(0) { $0 + $1.type.materialValue / 100 }
        return max(0, color == .white ? w - b : b - w)
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
                    Text(Loc("Izaberi stranu"))
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

    private func handleNewGameSelection(_ selection: PendingSelection) {
        let gameInProgress = !viewModel.isGameOver && !viewModel.gameState.moveHistory.isEmpty
        if gameInProgress {
            pendingSelection = selection
            showNewGameConfirm = true
        } else {
            executeNewGameSelection(selection)
        }
    }

    private func executeNewGameSelection(_ selection: PendingSelection) {
        dismissGameOverOverlay = false
        switch selection {
        case .computer:
            withAnimation(.spring(duration: 0.25)) {
                showColorPicker = true
            }
        case .localFriend:
            viewModel.newGame(gameMode: .localFriend, playerColor: .white)
        case .chessClock:
            showChessClock = true
        }
    }

    private func colorButton(color: PieceColor) -> some View {
        Button {
            dismissGameOverOverlay = false
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
                    Text(Loc("Izaberi figuru"))
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
        if viewModel.isGameOver && !dismissGameOverOverlay {
            ZStack {
                Color.black.opacity(0.55).ignoresSafeArea()
                VStack(spacing: 18) {
                    Text(gameOverEmoji)
                        .font(.system(size: 60))
                    Text(viewModel.statusMessage)
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)

                    HStack(spacing: 12) {
                        Button {
                            withAnimation(.easeInOut(duration: 0.25)) {
                                dismissGameOverOverlay = true
                            }
                        } label: {
                            Text(Loc("Pregledaj partiju"))
                                .font(.subheadline.weight(.semibold))
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(.white.opacity(0.15))
                                .foregroundStyle(.white)
                                .clipShape(Capsule())
                        }

                        Button {
                            withAnimation(.spring(duration: 0.3)) { showColorPicker = true }
                        } label: {
                            Text(Loc("Nova igra"))
                                .font(.subheadline.weight(.bold))
                                .padding(.horizontal, 20)
                                .padding(.vertical, 10)
                                .background(.white)
                                .foregroundStyle(.black)
                                .clipShape(Capsule())
                        }
                    }
                }
                .padding(28)
            }
            .transition(.opacity)
        }
    }

    private var gameOverEmoji: String {
        switch viewModel.gameState.status {
        case .checkmate(let c): return c == viewModel.playerColor ? "😔" : "🏆"
        case .resigned(let c):  return c == viewModel.playerColor ? "🏳️" : "🏆"
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
