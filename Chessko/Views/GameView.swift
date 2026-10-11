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
    @State private var showAnalysis = false
    @State private var dismissGameOverOverlay = false
    /// Visina dela iznad liste poteza (kartice, tabla, akcije) u portretu —
    /// lista dobija ostatak visine ekrana (spec § 5).
    @State private var portraitFixedHeight: CGFloat = 0
    /// Isto za panel u pejzažu — panel skroluje ako gornji deo ne staje.
    @State private var landscapeFixedHeight: CGFloat = 0

    enum PendingSelection {
        case computer
        case localFriend
        case chessClock
        /// „Nova partija" iz menija „Više" — isti protivnik i ista boja.
        case restart
    }
    @State private var pendingSelection: PendingSelection? = nil
    @State private var showChessClock = false

    /// Najmanja visina liste poteza (tri reda + unutrašnji razmak).
    private static let minMoveListHeight: CGFloat = MoveHistoryView.rowHeight * 3 + DS.Space.s
    /// Širina panela u pejzažu (spec § 5 „Telefon pejzaž", § 13).
    private static let landscapePanelWidth: CGFloat = 400

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                let isLandscape = geo.size.width > geo.size.height
                if isLandscape {
                    landscapeLayout(size: geo.size)
                } else {
                    portraitLayout(size: geo.size)
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
                // spec § 5: „Vrati" i „Predaj" su prešli iz zaglavlja u akcionu
                // traku ispod table (paritet sa Androidom).
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "line.3.horizontal")
                    }
                    .accessibilityLabel(Loc("Podešavanja"))
                }
            }
            .background(AppBackgroundView())
            .toolbarBackground(DS.ground, for: .navigationBar)
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
            // spec § 5: „Predati partiju?" · „Nastavi" (otkazivanje) · „Predaj"
            // (destruktivno). Bez teksta na „Vi" — obraćanje je „ti" svuda.
            .alert(Loc("Predati partiju?"), isPresented: $showResignConfirm) {
                Button(Loc("Predaj"), role: .destructive) {
                    viewModel.resign()
                }
                Button(Loc("Nastavi"), role: .cancel) {}
            }
            .sheet(isPresented: $showSettings) {
                SettingsSheet(gameViewModel: viewModel, localization: localization)
            }
            .fullScreenCover(isPresented: $showChessClock) {
                ChessClockView()
            }
            .sheet(isPresented: $showAnalysis) {
                AnalysisView(viewModel: viewModel)
            }
        }
    }

    // MARK: - Raspored

    /// Strana table. JEDNO mesto za veličinu table na ovom ekranu — Task 11
    /// (spec § 13) ovde menja telo zajedničkom funkcijom `boardSide`; raspored
    /// ispod čita samo ovu vrednost. Logika je zatečena (telefon do 560).
    private func boardSide(for size: CGSize, isLandscape: Bool) -> CGFloat {
        if isLandscape {
            // Pejzaž je visinski ograničen — strana se računa iz VISINE.
            return min(max(0, size.height - DS.Space.s * 2), DS.maxBoardSide)
        }
        let available = max(0, size.width - DS.Space.s * 2 - evalBarWidth)
        return min(available, DS.maxBoardSide)
    }

    private var evalBarWidth: CGFloat { showEvalBar ? EvalBarView.width + DS.Space.xs : 0 }

    /// Portret: od vrha — kartica protivnika, tabla, kartica igrača, akcije,
    /// (pregled), lista poteza koja zauzima ostatak visine (spec § 5, § 13).
    /// Ništa nije centrirano po visini.
    @ViewBuilder
    private func portraitLayout(size: CGSize) -> some View {
        let side = boardSide(for: size, isLandscape: false)
        let topPadding = DS.Space.xs
        let bottomPadding = DS.Space.l
        let tabBarClearance: CGFloat = 24   // isto kao `safeAreaPadding` ispod
        let listHeight = max(
            Self.minMoveListHeight,
            size.height - portraitFixedHeight - DS.Space.m - topPadding - bottomPadding - tabBarClearance
        )

        ScrollView {
            VStack(spacing: DS.Space.m) {
                VStack(spacing: DS.Space.m) {
                    opponentCard
                    boardRow(side: side)
                    playerCard
                    actionBar(compact: size.width < 360)
                    if viewModel.totalReviewMoves > 0 {
                        reviewControlsView
                    }
                }
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { portraitFixedHeight = $0 }

                moveList
                    .frame(height: listHeight)
            }
            .frame(maxWidth: side + evalBarWidth)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, DS.Space.s)
            .padding(.top, topPadding)
            .padding(.bottom, bottomPadding)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaPadding(.bottom, tabBarClearance)
    }

    /// Pejzaž (telefon, spec § 5): tabla i panel 400 kao par, vrhovi poravnati;
    /// lista poteza ide do dna table.
    @ViewBuilder
    private func landscapeLayout(size: CGSize) -> some View {
        let side = boardSide(for: size, isLandscape: true)
        let panelWidth = max(0, min(Self.landscapePanelWidth,
                                    size.width - side - evalBarWidth - DS.Space.l * 3))

        HStack(alignment: .top, spacing: DS.Space.l) {
            boardRow(side: side)

            // Lista dobija ostatak visine panela (do dna table); kad gornji deo ne
            // staje (nizak telefon, traka pregleda), panel skroluje umesto da seče.
            let listHeight = max(Self.minMoveListHeight, side - landscapeFixedHeight - DS.Space.s)
            ScrollView {
                VStack(spacing: DS.Space.s) {
                    VStack(spacing: DS.Space.s) {
                        opponentCard
                        playerCard
                        actionBar(compact: false)
                        if viewModel.totalReviewMoves > 0 {
                            reviewControlsView
                        }
                    }
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { landscapeFixedHeight = $0 }

                    moveList
                        .frame(height: listHeight)
                }
            }
            .scrollBounceBehavior(.basedOnSize)
            .frame(width: panelWidth, height: side)
        }
        .padding(.vertical, DS.Space.s)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private func boardRow(side: CGFloat) -> some View {
        HStack(spacing: DS.Space.xs) {
            if showEvalBar {
                EvalBarView(
                    evaluation: viewModel.evaluationScore,
                    mateIn: viewModel.evaluationMateIn,
                    isFlipped: viewModel.isFlipped
                )
                .frame(height: side)
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
            .frame(width: side, height: side)
        }
    }

    private var moveList: some View {
        MoveHistoryView(
            notations: viewModel.gameState.moveNotations,
            selectedMoveIndex: viewModel.viewingMoveIndex,
            onSelectMove: { viewModel.goToMove($0) }
        )
    }

    // MARK: - Akciona traka (spec § 5)

    private var canAnalyze: Bool {
        viewModel.isGameOver && !viewModel.gameState.moveNotations.isEmpty
    }

    private func actionBar(compact: Bool) -> some View {
        GameActionBar(
            canUndo: viewModel.canUndo,
            canResign: viewModel.canResign,
            compact: compact,
            onUndo: { viewModel.undo() },
            onResign: { showResignConfirm = true }
        ) {
            // „Više": Podeli, Nova partija, Analiza. Analiza je ovde u OBA
            // rasporeda (portret i pejzaž dele traku) i u dijalogu kraja partije.
            ShareLink(item: viewModel.generatePGN()) {
                Label(Loc("Podeli"), systemImage: "square.and.arrow.up")
            }
            .disabled(viewModel.gameState.moveNotations.isEmpty)

            Button {
                handleNewGameSelection(.restart)
            } label: {
                Label(Loc("Nova partija"), systemImage: "arrow.counterclockwise")
            }

            Button {
                showAnalysis = true
            } label: {
                Label(Loc("Analiza"), systemImage: "chart.bar.doc.horizontal")
            }
            .disabled(!canAnalyze)
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
            .accessibilityLabel(Loc("Početna pozicija"))

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
                if viewModel.totalReviewMoves == 0 || viewModel.currentReviewMoveIndex == 0 {
                    return Loc("Početna pozicija")
                } else {
                    return String(format: Loc("Potez %d od %d"), viewModel.currentReviewMoveIndex, viewModel.totalReviewMoves)
                }
            }()

            Text(label)
                .font(.dsCaption.weight(.semibold))
                .foregroundStyle(viewModel.isReviewing ? DS.accent : DS.ink)

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
        .foregroundStyle(DS.ink)
        .padding(.horizontal, DS.Space.s)
        .padding(.vertical, DS.Space.xs)
        // spec § 3: kartica (`surface` + `line`), ne materijal.
        .dsCard()
    }

    // MARK: - Kartice igrača (spec § 5)

    private var topColor: PieceColor { viewModel.isFlipped ? .white : .black }
    private var bottomColor: PieceColor { viewModel.isFlipped ? .black : .white }

    private func captured(by color: PieceColor) -> [ChessPiece] {
        color == .white ? viewModel.gameState.capturedByWhite : viewModel.gameState.capturedByBlack
    }

    private func isActive(_ color: PieceColor) -> Bool {
        viewModel.gameState.currentTurn == color && !viewModel.isGameOver
    }

    /// Strana `color` je u šahu (spec § 5: šah se kaže u kartici, ne u posebnom redu).
    private func isInCheck(_ color: PieceColor) -> Bool {
        if case .check(let c) = viewModel.gameState.status { return c == color }
        return false
    }

    private func isComputer(_ color: PieceColor) -> Bool {
        viewModel.gameMode == .vsComputer && color != viewModel.playerColor
    }

    /// Kartica protivnika (gore): ime + drugi red „Srednje · ~1300 · igra
    /// crnima" + strelica; tap otvara izbor protivnika i nivoa.
    private var opponentCard: some View {
        let color = topColor
        let status: PlayerCardStatus? = {
            guard isActive(color) else { return nil }
            if isComputer(color) { return viewModel.isThinking ? .thinking : nil }
            return .toMove(inCheck: isInCheck(color))
        }()
        return Menu {
            opponentMenu
        } label: {
            DSPlayerCard(
                color: color,
                name: headerLabel(for: color),
                subtitle: isComputer(color) ? opponentSubtitle(computerColor: color) : nil,
                status: status,
                isActive: isActive(color),
                materialAdvantage: materialAdvantage(for: color),
                capturedPieces: captured(by: color),
                flyingCapture: viewModel.flyingCapture,
                showsChevron: true
            )
        }
        .buttonStyle(DSPressableButtonStyle())
    }

    /// Izbor protivnika i nivoa (spec § 5 — „Računar" i „Srednje" iz stare
    /// Android trake prelaze ovde). Promena protivnika pokreće novu partiju
    /// (uz potvrdu ako je partija u toku); promena nivoa važi odmah.
    @ViewBuilder
    private var opponentMenu: some View {
        if viewModel.gameMode == .vsComputer {
            Picker(Loc("Težina"), selection: Binding(
                get: { viewModel.difficulty },
                set: { viewModel.setDifficulty($0) }
            )) {
                ForEach(GameDifficulty.allCases, id: \.self) { diff in
                    Text(diff.title).tag(diff)
                }
            }
            .pickerStyle(.inline)

            if viewModel.difficulty == .stockfish {
                Picker(Loc("Stockfish nivo"), selection: Binding(
                    get: { viewModel.stockfishLevel },
                    set: { viewModel.setStockfishLevel($0) }
                )) {
                    ForEach(StockfishLevel.allCases, id: \.self) { level in
                        Text(level.label).tag(level)
                    }
                }
                .pickerStyle(.menu)
            }

            Divider()

            Button {
                handleNewGameSelection(.localFriend)
            } label: {
                Label(Loc("Igraj sa prijateljem"), systemImage: "person.2")
            }
        } else {
            Button {
                handleNewGameSelection(.computer)
            } label: {
                Label(Loc("Igraj protiv računara"), systemImage: "cpu")
            }
        }
    }

    /// „Srednje · ~1300 · igra crnima". Elo se izostavlja kad ga nema
    /// (Stockfish „Maksimalno") — nikad „–".
    private func opponentSubtitle(computerColor: PieceColor) -> String {
        var parts: [String] = [viewModel.difficulty.label]
        if let elo = opponentElo { parts.append(elo) }
        parts.append(computerColor == .black ? Loc("igra crnima") : Loc("igra belima"))
        return parts.joined(separator: " · ")
    }

    private var opponentElo: String? {
        switch viewModel.difficulty {
        case .beginner:  return "~500"
        case .easy:      return "~900"
        case .medium:    return "~1300"
        case .hard:      return "~1700"
        case .stockfish: return viewModel.stockfishLevel.elo
        }
    }

    /// Kartica igrača (dole): „Na potezu" + tačka, kraj partije kao `Type.body`,
    /// procena desno (kad je eval uključen).
    private var playerCard: some View {
        let color = bottomColor
        let status: PlayerCardStatus? = {
            if viewModel.isGameOver { return .result(viewModel.statusMessage) }
            guard isActive(color) else { return nil }
            if isComputer(color) { return viewModel.isThinking ? .thinking : nil }
            return .toMove(inCheck: isInCheck(color))
        }()
        return DSPlayerCard(
            color: color,
            name: headerLabel(for: color),
            status: status,
            isActive: isActive(color),
            materialAdvantage: materialAdvantage(for: color),
            capturedPieces: captured(by: color),
            flyingCapture: viewModel.flyingCapture,
            evaluation: showEvalBar
                ? DSEvaluation.make(score: viewModel.evaluationScore, mateIn: viewModel.evaluationMateIn)
                : nil
        )
    }

    private func headerLabel(for color: PieceColor) -> String {
        if viewModel.gameMode == .localFriend {
            // NE `Loc("Beli")`/`Loc("Crni")`: kljucevi "beli"/"crni" vec postoje,
            // a `STRING_CATALOG_GENERATE_SYMBOLS` im pravi ISTI simbol pa se
            // build ruši na duplikatu. Zato isti obrazac kao birac boje —
            // postojeci kljuc pa `capitalized`. (Ista greska je vec napravljena
            // i ispravljena 2026-06-26.)
            return color.srbAdjective.capitalized
        } else {
            return color == viewModel.playerColor ? Loc("Ti") : Loc("Računar")
        }
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
                DS.scrim.ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.spring(duration: 0.25)) { showColorPicker = false }
                    }

                VStack(spacing: 24) {
                    Text(Loc("Izaberi stranu"))
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(DS.onScrim)

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
        case .restart:
            viewModel.newGame(gameMode: viewModel.gameMode, playerColor: viewModel.playerColor)
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
                        DS.onScrim.opacity(0.18),
                        in: RoundedRectangle(cornerRadius: 14)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(DS.onScrim.opacity(0.35), lineWidth: 1)
                    )
                Text(color.srbAdjective.capitalized)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(DS.onScrim)
            }
        }
    }

    // MARK: - Promotion Overlay

    @ViewBuilder
    private var promotionOverlay: some View {
        if viewModel.showPromotion {
            // Sam birac zivi u `PromotionOverlay` — deli ga sa ekranom `game`
            // koraka Puta. Izgled je nepromenjen (kod je prenet doslovno).
            PromotionOverlay(color: viewModel.playerColor) { type in
                viewModel.confirmPromotion(type)
            }
        }
    }

    // MARK: - Game Over Overlay

    /// Kraj partije (spec § 5, § 3): `surface` + `Radius.l`; primarno „Nova
    /// igra", sporedno „Analiziraj partiju" (jedan naziv za radnju — nema više
    /// „Pregledaj partiju"), „Zatvori" kao tekst-dugme u `accent` (bez njega
    /// se završna pozicija ne bi mogla pogledati). Poruka je rodno neutralna.
    @ViewBuilder
    private var gameOverOverlay: some View {
        if viewModel.isGameOver && !dismissGameOverOverlay {
            ZStack {
                DS.scrim.ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.easeInOut(duration: 0.25)) { dismissGameOverOverlay = true }
                    }

                VStack(spacing: DS.Space.l) {
                    Text(gameOverEmoji)
                        .font(.system(size: 56))
                        .accessibilityHidden(true)
                    Text(viewModel.statusMessage)
                        .font(.dsTitle)
                        .foregroundStyle(DS.ink)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)

                    VStack(spacing: DS.Space.s) {
                        Button(Loc("Nova igra")) {
                            withAnimation(.spring(duration: 0.3)) { showColorPicker = true }
                        }
                        .buttonStyle(DSPrimaryButtonStyle())

                        if canAnalyze {
                            Button(Loc("Analiziraj partiju")) {
                                dismissGameOverOverlay = true
                                showAnalysis = true
                            }
                            .buttonStyle(DSSecondaryButtonStyle())
                        }

                        Button(Loc("Zatvori")) {
                            withAnimation(.easeInOut(duration: 0.25)) { dismissGameOverOverlay = true }
                        }
                        .buttonStyle(DSTextButtonStyle())
                    }
                }
                .padding(DS.Space.xl)
                .frame(maxWidth: 400)
                .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.l, style: .continuous))
                .padding(.horizontal, DS.Space.xl)
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
                    .fill(DS.inkMuted)
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
