import SwiftUI

// MARK: - Step Game View (pokretac `game` koraka Puta)
//
// Partija protiv racunara, sa tezinom i (opciono) startnom pozicijom iz koraka.
// Ceo tok partije — tapovi, legalnost, AI, zvuk, haptika, kraj partije — vec
// zivi u `GameViewModel`-u i deli se sa tabom Igra; ovde se dodaje samo ono sto
// korak ima a slobodna partija nema: zavrsetak KORAKA.
//
// Tri odluke koje ovaj ekran nosi:
//
// 1. SOPSTVENI `GameViewModel`, sa sopstvenim slotom u `UserDefaults`-u
//    (`GameViewModel.stepSaveKey(_:)`). Model tabu Igra cuva celu partiju pod
//    jednim kljucem posle SVAKOG poteza; deljen primerak (ili zajednicki kljuc)
//    bi ulaskom u korak ucitao partiju koju korisnik ima u toku na tabu Igra i
//    prvim potezom je pregazio. Isti razlog zbog kojeg `StepPracticeView` drzi
//    svoj `PuzzleViewModel`.
// 2. Korak se zavrsava kad partija dodje do KRAJA, bez obzira na ishod — spec
//    kaze „partija odigrana do kraja", ne „pobeda". Predaja je takodje kraj.
// 3. Ekran NE nudi „Nova igra" ni izbor boje. To su kontrole slobodne partije;
//    ovde bi bile nacin da se korak zavrsi bez partije.

struct StepGameView: View {

    let stepId: String
    /// Tezina dolazi kao `GameDifficulty`, ne kao string iz kurikuluma: mesto
    /// koje sme da promasi mapiranje je `PathView.route(for:)`, jedno jedino.
    let difficulty: GameDifficulty
    let startFEN: String?

    /// Model se pravi u `onAppear`, a ne kao pocetna vrednost `@State`-a:
    /// svako kreiranje `GameViewModel`-a cita `UserDefaults`, budi Stockfish i
    /// pokrece racunanje evaluacije, a pocetna vrednost `@State`-a se izracuna
    /// pri SVAKOM kreiranju ove strukture — a ekran Puta se prekrsti na svaku
    /// promenu napretka. Ovako postoji tacno jedan primerak.
    @State private var viewModel: GameViewModel?

    /// Korak koji jos ceka upis. `nil` znaci „vec upisan": zavrsetak se
    /// okida TACNO jednom po ulasku, a ne na svako ponovno izracunavanje
    /// `isGameOver`-a.
    @State private var pendingStepId: String?

    @State private var showResignConfirm = false
    @State private var showAnalysis = false

    @Environment(\.dismiss) private var dismiss
    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.verticalSizeClass) private var vSize

    /// Spec § 13: tablet = `regular` u obe ose.
    private var isTablet: Bool { hSize == .regular && vSize == .regular }

    // MARK: - Body

    var body: some View {
        ZStack {
            AppBackgroundView()

            if let viewModel {
                content(viewModel)
            }
        }
        .navigationTitle(Loc("Partija"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(DS.ground, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                // Predaja je jedini izlaz iz partije koja se ne moze dobiti.
                // Bez nje bi korak (a s njim i ostatak Puta) ostao zakljucan
                // dok korisnik ne matira racunar.
                if let viewModel, viewModel.canResign {
                    Button {
                        showResignConfirm = true
                    } label: {
                        Image(systemName: "flag.fill")
                            .foregroundStyle(DS.danger)
                    }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    viewModel?.undo()
                } label: {
                    Image(systemName: "arrow.uturn.backward")
                }
                // `canUndo` je `false` kad je partija gotova, pa zavrsen korak
                // ne moze da se „odvrti" nazad u nezavrsenu partiju.
                .disabled(!(viewModel?.canUndo ?? false))
            }
        }
        .overlay(promotionOverlay)
        // spec § 5: „Predati partiju?" · „Nastavi" · „Predaj" — isto kao ekran Igra.
        .alert(Loc("Predati partiju?"), isPresented: $showResignConfirm) {
            Button(Loc("Nastavi"), role: .cancel) {}
            Button(Loc("Predaj"), role: .destructive) {
                viewModel?.resign()
            }
        }
        .sheet(isPresented: $showAnalysis) {
            if let viewModel {
                AnalysisView(viewModel: viewModel)
            }
        }
        .onAppear(perform: start)
        // `isGameOver` je JEDINI izvor istine o kraju partije (`GameViewModel`);
        // ekran ga posmatra, ne izvodi ponovo.
        .onChange(of: viewModel?.isGameOver ?? false) { _, over in
            guard over else { return }
            completeStepOnce()
        }
    }

    // MARK: - Zivotni ciklus

    private func start() {
        if viewModel == nil {
            let model = GameViewModel(saveKey: GameViewModel.stepSaveKey(stepId))
            model.startStepGame(difficulty: difficulty, startFEN: startFEN)
            viewModel = model
        }
        pendingStepId = stepId
        // Branik za slucaj da je partija gotova pre nego sto `onChange` ima sta
        // da uporedi. `startStepGame` zavrsenu partiju ne nastavlja, pa je ovo
        // danas nedostizno — ali uslov zavrsetka stoji na oba puta do njega.
        completeStepOnce()
    }

    /// Korak se zavrsava kad partija dodje do kraja — BEZ OBZIRA NA ISHOD.
    /// Poraz i predaja zavrsavaju korak isto kao pobeda: spec trazi odigranu
    /// partiju, ne dobijenu.
    private func completeStepOnce() {
        guard let viewModel, viewModel.isGameOver, let id = pendingStepId else { return }
        pendingStepId = nil
        ProgressStore.shared.completeStep(id)
        // Bez dodatnog haptika: `GameViewModel` je za kraj partije vec ispalio
        // svoj (pobeda/poraz/remi) pre koji milisekundu.
        viewModel.clearStepSave()
        // Analiza je PRIKAZ, ne uslov: korak je gore vec upisan kao zavrsen,
        // pa otvaranje ekrana analize ovde nikad ne moze da spreci zavrsetak
        // koraka — ni ako se ne otvori, ni ako padne, ni ako motor ne radi.
        showAnalysis = true
    }

    // MARK: - Raspored

    private func content(_ viewModel: GameViewModel) -> some View {
        GeometryReader { geo in
            let isLandscape = geo.size.width > geo.size.height
            let side = boardSide(in: geo.size, isLandscape: isLandscape)
            if isTablet && isLandscape {
                // spec § 13: tabla + panel 400 centrirani kao par, vrhovi
                // poravnati (isti raspored kao Igra i Zadaci na tabletu).
                HStack(alignment: .top, spacing: DS.Space.xl) {
                    board(viewModel, side: side)
                        .padding(.vertical, DS.Space.xl)
                    ScrollView {
                        VStack(spacing: DS.Space.m) {
                            opponentCard(viewModel)
                            playerCard(viewModel)
                            footer(viewModel)
                        }
                        .padding(.vertical, DS.Space.xl)
                    }
                    .scrollBounceBehavior(.basedOnSize)
                    .frame(width: BoardSizing.tabletPanelWidth)
                }
                .frame(width: geo.size.width, height: geo.size.height, alignment: .top)
            } else {
                ScrollView {
                    // spec § 7: sadržaj od vrha (`Space.l` ispod zaglavlja), iste
                    // komponente kao Igra — kartica protivnika, tabla, kartica igrača
                    // („Na potezu" umesto posebnog reda „Tvoj potez"), pa napomena.
                    // § 13: kolona široka kao tabla, centrirana.
                    VStack(spacing: DS.Space.m) {
                        opponentCard(viewModel)
                        board(viewModel, side: side)
                        playerCard(viewModel)
                        footer(viewModel)
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

    /// Strana table — zajednička funkcija `BoardSizing.boardSide` (spec § 13).
    /// Na telefonu zatečena rezerva 220 po visini (bez nje bi tabla u pejzažu
    /// progutala ceo ekran).
    private func boardSide(in size: CGSize, isLandscape: Bool) -> CGFloat {
        BoardSizing.boardSide(width: size.width, height: size.height,
                              isTablet: isTablet, isLandscape: isLandscape,
                              phoneHeightReserve: BoardSizing.stepPhoneHeightReserve)
    }

    private func board(_ viewModel: GameViewModel, side: CGFloat) -> some View {
        BoardView(
            board:            viewModel.gameState.board,
            isFlipped:        viewModel.isFlipped,
            selectedPosition: viewModel.selectedPosition,
            legalMoves:       viewModel.legalMovesForSelected,
            lastMove:         viewModel.lastMove,
            animatingPiece:   viewModel.animatingPiece,
            flyingCapture:    viewModel.flyingCapture,
            playerColor:      viewModel.activePlayerColor,
            isPlayerTurn:     viewModel.isPlayerTurn,
            gameStatus:       viewModel.gameState.status,
            onTap:            { viewModel.tap(position: $0) }
        )
        .frame(width: side, height: side)
    }

    // MARK: - Kartice (spec § 7: iste komponente kao Igra)

    private var computerColor: PieceColor {
        viewModel?.playerColor == .white ? .black : .white
    }

    private func isInCheck(_ viewModel: GameViewModel, _ color: PieceColor) -> Bool {
        if case .check(let c) = viewModel.gameState.status { return c == color }
        return false
    }

    private func isActive(_ viewModel: GameViewModel, _ color: PieceColor) -> Bool {
        viewModel.gameState.currentTurn == color && !viewModel.isGameOver
    }

    private func captured(_ viewModel: GameViewModel, by color: PieceColor) -> [ChessPiece] {
        color == .white ? viewModel.gameState.capturedByWhite : viewModel.gameState.capturedByBlack
    }

    private func materialAdvantage(_ viewModel: GameViewModel, for color: PieceColor) -> Int {
        let w = viewModel.gameState.capturedByWhite.reduce(0) { $0 + $1.type.materialValue / 100 }
        let b = viewModel.gameState.capturedByBlack.reduce(0) { $0 + $1.type.materialValue / 100 }
        return max(0, color == .white ? w - b : b - w)
    }

    /// Kartica protivnika „Računar · Početnik" — bez strelice: korak sam
    /// zadaje protivnika i nivo, pa nema šta da se bira.
    private func opponentCard(_ viewModel: GameViewModel) -> some View {
        let color = computerColor
        return DSPlayerCard(
            color: color,
            name: Loc("Računar"),
            subtitle: viewModel.difficulty.label,
            status: isActive(viewModel, color) && viewModel.isThinking ? .thinking : nil,
            isActive: isActive(viewModel, color),
            materialAdvantage: materialAdvantage(viewModel, for: color),
            capturedPieces: captured(viewModel, by: color),
            flyingCapture: viewModel.flyingCapture
        )
    }

    /// Kartica igrača: „Na potezu" (+ „· Šah"), kraj partije kao `Type.body`.
    private func playerCard(_ viewModel: GameViewModel) -> some View {
        let color = viewModel.playerColor
        let status: PlayerCardStatus? = {
            if viewModel.isGameOver { return .result(viewModel.statusMessage) }
            guard isActive(viewModel, color) else { return nil }
            return .toMove(inCheck: isInCheck(viewModel, color))
        }()
        return DSPlayerCard(
            color: color,
            name: Loc("Ti"),
            status: status,
            isActive: isActive(viewModel, color),
            materialAdvantage: materialAdvantage(viewModel, for: color),
            capturedPieces: captured(viewModel, by: color),
            flyingCapture: viewModel.flyingCapture
        )
    }

    // MARK: - Zavrsetak koraka

    @ViewBuilder
    private func footer(_ viewModel: GameViewModel) -> some View {
        if viewModel.isGameOver {
            VStack(spacing: DS.Space.m) {
                HStack(spacing: DS.Space.s) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(DS.success)
                    Text(Loc("Korak je završen"))
                        .foregroundStyle(DS.ink)
                }
                .font(.dsHeading)

                Button {
                    dismiss()
                } label: {
                    Text(Loc("Nazad na Put"))
                }
                .buttonStyle(DSPrimaryButtonStyle())
            }
            .padding(.top, DS.Space.s)
        } else {
            // Uslov zavrsetka se kaze unapred: korisnik koji ocekuje da mora da
            // POBEDI odustao bi od izgubljene partije umesto da je odigra.
            Text(Loc("Korak se završava kad partija dođe do kraja."))
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
    }

    // MARK: - Promocija

    @ViewBuilder
    private var promotionOverlay: some View {
        if let viewModel, viewModel.showPromotion {
            PromotionOverlay(color: viewModel.playerColor) { type in
                viewModel.confirmPromotion(type)
            }
        }
    }
}
