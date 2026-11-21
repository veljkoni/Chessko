import SwiftUI
import Network

// MARK: - Igra preko interneta (celoekranski prikaz)
//
// Jedan prikaz za ceo tok: lobi (`OnlineLobbyView`) dok nema partije, tabla dok
// traje, rezultat posle kraja. Otvaranje poziva `open()`, zatvaranje `close()`.
// Mašina i veza žive u `OnlineViewModel`; ovde se samo crta. Ovaj ekran je
// jedino mesto (uz `ContentView` kad postoji sačuvana partija) koje sme da
// dodirne `OnlineViewModel.shared`.

/// Prati da li uređaj uopšte ima mrežu (sistemski `Network`, bez zavisnosti).
@Observable
@MainActor
final class NetworkReachability {
    private(set) var isOnline = true
    @ObservationIgnored private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let ok = path.status == .satisfied
            Task { @MainActor in self?.isOnline = ok }
        }
        monitor.start(queue: DispatchQueue(label: "chessko.online.path"))
    }

    deinit { monitor.cancel() }
}

struct OnlineGameView: View {
    private var vm: OnlineViewModel { .shared }

    @State private var reachability: NetworkReachability?
    @State private var showCloseConfirm = false
    /// Varijanta potvrde zatvaranja, zamrznuta pri otvaranju dijaloga.
    @State private var closeConfirm: OnlineCloseConfirm = .gameWaits
    @State private var showResignConfirm = false
    @State private var showAnalysis = false

    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.verticalSizeClass) private var vSize

    private var isTablet: Bool { hSize == .regular && vSize == .regular }
    private var hasNetwork: Bool { reachability?.isOnline ?? true }

    private var game: GameViewModel { vm.game }

    /// Tabla je na ekranu (partija traje, ili je završena, ili se nastavlja
    /// sačuvana dok se veza ne uspostavi).
    private var showsBoard: Bool {
        switch vm.state {
        case .playing, .ended: return true
        case .idle, .connecting: return vm.hasActiveGame
        default: return false
        }
    }

    private var isPlaying: Bool {
        if case .playing = vm.state { return true }
        return false
    }

    // MARK: Telo

    var body: some View {
        NavigationStack {
            ZStack {
                AppBackgroundView()
                if showsBoard {
                    gameContent
                } else {
                    OnlineLobbyView(vm: vm, hasNetwork: hasNetwork)
                }
            }
            .navigationTitle(Loc("Igraj preko interneta"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(DS.ground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(Loc("Zatvori")) { requestClose() }
                        .foregroundStyle(DS.accent)
                }
            }
            .overlay(promotionOverlay)
            // Zatvaranje usred partije: ista potvrda kao „Predaj" (spec § 5).
            // Predaja se nudi samo dok je veza živa; inače bi `resign` bio tihi
            // no-op a ekran bi se zatvorio. Bez veze partija čeka (sačuvana je i
            // otvara se pri sledećem pokretanju; prijatelj vidi „izgubio vezu").
            // Varijanta je zamrznuta pri otvaranju: veza koja se vrati dok je
            // dijalog otvoren ne sme pretvoriti „Zatvori" u „Predaj" pod prstom.
            .alert(closeConfirm == .resign ? Loc("Predati partiju?") : Loc("Partija će te čekati."),
                   isPresented: $showCloseConfirm) {
                if closeConfirm == .resign {
                    Button(Loc("Predaj"), role: .destructive) {
                        // Veza je u međuvremenu pala: bez predaje, partija čeka.
                        if closeConfirm.resignsOnConfirm(canActNow: game.onlineCanMove) { vm.resign() }
                        closeNow()
                    }
                } else {
                    Button(Loc("Zatvori")) { closeNow() }
                }
                Button(Loc("Nastavi"), role: .cancel) {}
            }
            .alert(Loc("Predati partiju?"), isPresented: $showResignConfirm) {
                Button(Loc("Predaj"), role: .destructive) { vm.resign() }
                Button(Loc("Nastavi"), role: .cancel) {}
            }
            .sheet(isPresented: $showAnalysis) {
                AnalysisView(viewModel: game)
            }
        }
        .onAppear {
            if reachability == nil { reachability = NetworkReachability() }
            vm.open()
        }
        .onChange(of: scenePhase) { _, phase in vm.scenePhaseChanged(phase) }
        .task(id: vm.notice) {
            guard vm.notice != nil, showsBoard else { return }
            try? await Task.sleep(for: .seconds(4))
            if !Task.isCancelled { vm.notice = nil }
        }
    }

    private func requestClose() {
        if isPlaying {
            closeConfirm = OnlineCloseConfirm(canAct: game.onlineCanMove)
            showCloseConfirm = true
        } else {
            closeNow()
        }
    }

    private func closeNow() {
        vm.close()
        dismiss()
    }

    // MARK: Partija

    private var gameContent: some View {
        GeometryReader { geo in
            let isLandscape = geo.size.width > geo.size.height
            let side = BoardSizing.boardSide(
                width: geo.size.width, height: geo.size.height,
                isTablet: isTablet, isLandscape: isLandscape,
                phoneHeightReserve: BoardSizing.stepPhoneHeightReserve)
            if isTablet && isLandscape {
                HStack(alignment: .top, spacing: DS.Space.xl) {
                    board(side: side)
                        .padding(.vertical, DS.Space.xl)
                    ScrollView {
                        VStack(spacing: DS.Space.m) {
                            opponentCard
                            playerCard
                            statusArea
                            actions
                        }
                        .padding(.vertical, DS.Space.xl)
                    }
                    .scrollBounceBehavior(.basedOnSize)
                    .frame(width: BoardSizing.tabletPanelWidth)
                }
                .frame(width: geo.size.width, height: geo.size.height, alignment: .top)
            } else {
                ScrollView {
                    VStack(spacing: DS.Space.m) {
                        opponentCard
                        board(side: side)
                        playerCard
                        statusArea
                        actions
                    }
                    .frame(maxWidth: side)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, DS.Space.l)
                    .padding(.vertical, DS.Space.l)
                }
                .scrollBounceBehavior(.basedOnSize)
                .safeAreaPadding(.bottom, 24)
                .frame(width: geo.size.width, height: geo.size.height)
            }
        }
    }

    private var interactive: Bool {
        game.onlineCanMove && game.onlineResult == nil && !game.isGameOver
    }

    private func board(side: CGFloat) -> some View {
        BoardView(
            board:            game.gameState.board,
            isFlipped:        game.isFlipped,
            selectedPosition: game.selectedPosition,
            legalMoves:       game.legalMovesForSelected,
            lastMove:         game.lastMove,
            animatingPiece:   game.animatingPiece,
            flyingCapture:    game.flyingCapture,
            playerColor:      game.activePlayerColor,
            isPlayerTurn:     game.isPlayerTurn,
            gameStatus:       game.gameState.status,
            onTap:            { game.tap(position: $0) }
        )
        .frame(width: side, height: side)
        .opacity(interactive || game.isGameOver ? 1 : 0.6)
        .allowsHitTesting(interactive)
    }

    // MARK: Kartice

    private var friendColor: PieceColor { game.playerColor == .white ? .black : .white }

    private func isActive(_ color: PieceColor) -> Bool {
        game.gameState.currentTurn == color && !game.isGameOver && game.onlineResult == nil
    }

    private func isInCheck(_ color: PieceColor) -> Bool {
        if case .check(let c) = game.gameState.status { return c == color }
        return false
    }

    private func captured(by color: PieceColor) -> [ChessPiece] {
        color == .white ? game.gameState.capturedByWhite : game.gameState.capturedByBlack
    }

    private func materialAdvantage(for color: PieceColor) -> Int {
        let w = game.gameState.capturedByWhite.reduce(0) { $0 + $1.type.materialValue / 100 }
        let b = game.gameState.capturedByBlack.reduce(0) { $0 + $1.type.materialValue / 100 }
        return max(0, color == .white ? w - b : b - w)
    }

    private var opponentCard: some View {
        let color = friendColor
        return DSPlayerCard(
            color: color,
            name: Loc("Prijatelj"),
            status: isActive(color) ? .toMove(inCheck: isInCheck(color)) : nil,
            isActive: isActive(color),
            materialAdvantage: materialAdvantage(for: color),
            capturedPieces: captured(by: color),
            flyingCapture: game.flyingCapture
        )
    }

    private var playerCard: some View {
        let color = game.playerColor
        let status: PlayerCardStatus? = {
            if let text = resultText { return .result(text) }
            guard isActive(color) else { return nil }
            return .toMove(inCheck: isInCheck(color))
        }()
        return DSPlayerCard(
            color: color,
            name: Loc("Ti"),
            status: status,
            isActive: isActive(color),
            materialAdvantage: materialAdvantage(for: color),
            capturedPieces: captured(by: color),
            flyingCapture: game.flyingCapture
        )
    }

    /// Rezultat posle kraja; do tada `nil`.
    private var resultText: String? {
        guard case .ended(let result) = vm.state else { return nil }
        switch result {
        case .iResigned, .opponentResigned, .board: return game.statusMessage
        case .drawAgreed: return Loc("Remi – dogovor.")
        case .aborted, .firstMoveExpired: return Loc("Partija je prekinuta.")
        case .iFlagged, .opponentFlagged: return game.statusMessage   // tekstovi isteka: Task 7
        }
    }

    // MARK: Traka stanja

    @ViewBuilder
    private var statusArea: some View {
        switch vm.state {
        case .ended:
            endedArea
        case .playing where vm.updateRequired:
            statusLine(Loc("Za igru preko interneta potrebna je novija verzija aplikacije."), showsProgress: false)
        case .playing(_, let link, let away, let drawByOpp, _, _):
            VStack(spacing: DS.Space.s) {
                if let text = connectionText(link: link, away: away) {
                    statusLine(text, showsProgress: true)
                } else if drawByOpp {
                    drawOfferBox
                } else if let notice = vm.notice {
                    statusLine(OnlineNoticeText.text(for: notice), showsProgress: false)
                } else {
                    statusLine(game.statusMessage, showsProgress: false)
                }
            }
        default:
            // Sačuvana partija, veza se još uspostavlja (ili server traži noviju verziju).
            if vm.updateRequired {
                statusLine(Loc("Za igru preko interneta potrebna je novija verzija aplikacije."), showsProgress: false)
            } else {
                statusLine(hasNetwork ? Loc("Povezujem…") : Loc("Nema veze sa internetom."),
                           showsProgress: hasNetwork)
            }
        }
    }

    private func connectionText(link: OnlineLink, away: Bool) -> String? {
        if link == .reconnecting {
            return hasNetwork ? Loc("Ponovo se povezujem…") : Loc("Nema veze sa internetom.")
        }
        if away { return Loc("Prijatelj je izgubio vezu, čekam…") }
        return nil
    }

    private func statusLine(_ text: String, showsProgress: Bool) -> some View {
        HStack(spacing: DS.Space.s) {
            if showsProgress { ProgressView() }
            Text(text)
                .font(.dsBody)
                .foregroundStyle(DS.inkMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var drawOfferBox: some View {
        VStack(spacing: DS.Space.s) {
            Text(Loc("Prijatelj nudi remi."))
                .font(.dsBody.weight(.semibold))
                .foregroundStyle(DS.ink)
            HStack(spacing: DS.Space.s) {
                Button(Loc("Prihvati")) { vm.answerDraw(true) }
                    .buttonStyle(DSPrimaryButtonStyle())
                Button(Loc("Odbij")) { vm.answerDraw(false) }
                    .buttonStyle(DSSecondaryButtonStyle())
            }
        }
        .padding(DS.Space.m)
        .frame(maxWidth: .infinity)
        .dsCard()
    }

    private var endedArea: some View {
        VStack(spacing: DS.Space.s) {
            Button(Loc("Nova partija")) { vm.backToLobby() }
                .buttonStyle(DSPrimaryButtonStyle())
            if !game.gameState.moveNotations.isEmpty {
                Button(Loc("Analiziraj partiju")) { showAnalysis = true }
                    .buttonStyle(DSSecondaryButtonStyle())
            }
            Button(Loc("Zatvori")) { closeNow() }
                .buttonStyle(DSTextButtonStyle())
        }
        .padding(.top, DS.Space.s)
    }

    // MARK: Akcije

    @ViewBuilder
    private var actions: some View {
        if case .playing(_, _, _, let drawByOpp, let drawByMe, _) = vm.state {
            let canAct = game.onlineCanMove
            HStack(spacing: DS.Space.s) {
                Button { showResignConfirm = true } label: {
                    DSActionButtonLabel(systemImage: "flag", title: Loc("Predaj"), tint: DS.danger)
                }
                .buttonStyle(DSPressableButtonStyle())
                .disabled(!canAct)
                .opacity(canAct ? 1 : 0.38)

                let drawEnabled = canAct && !drawByMe && !drawByOpp
                Button { vm.offerDraw() } label: {
                    DSActionButtonLabel(systemImage: "equal.circle", title: Loc("Ponudi remi"))
                }
                .buttonStyle(DSPressableButtonStyle())
                .disabled(!drawEnabled)
                .opacity(drawEnabled ? 1 : 0.38)
            }
        }
    }

    // MARK: Promocija

    @ViewBuilder
    private var promotionOverlay: some View {
        if game.showPromotion {
            PromotionOverlay(color: game.playerColor) { type in
                game.confirmPromotion(type)
            }
        }
    }
}
