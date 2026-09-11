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

    @Environment(\.dismiss) private var dismiss

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
        .toolbarBackground(Color.appBackground, for: .navigationBar)
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
        .alert(Loc("Predati partiju?"), isPresented: $showResignConfirm) {
            Button(Loc("Predaj"), role: .destructive) {
                viewModel?.resign()
            }
            Button(Loc("Otkaži"), role: .cancel) {}
        } message: {
            Text(Loc("Da li ste sigurni da želite da predate trenutnu partiju?"))
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
    }

    // MARK: - Raspored

    private func content(_ viewModel: GameViewModel) -> some View {
        GeometryReader { geo in
            ScrollView {
                // Spacer-i na oba kraja + `minHeight`: kratak sadrzaj se
                // centrira po visini, dug normalno skroluje (isti obrazac kao
                // `StepPracticeView`).
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    VStack(spacing: DS.Space.m) {
                        opponentCard(viewModel)
                        statusCard(viewModel)
                        board(viewModel, side: boardSide(in: geo.size))
                        footer(viewModel)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, DS.Space.l)
                    .padding(.vertical, DS.Space.m)
                    Spacer(minLength: 0)
                }
                .frame(minHeight: geo.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
            .safeAreaPadding(.bottom, 24)
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }

    /// Tabla mora da stane i kad je ekran polozen — bez granice po VISINI
    /// racun bi je vodio samo po sirini i u pejzazu bi progutala ceo ekran.
    private func boardSide(in size: CGSize) -> CGFloat {
        let byWidth = size.width - 2 * DS.Space.l
        let byHeight = size.height - 220
        return max(200, min(byWidth, byHeight, DS.maxBoardSide))
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

    // MARK: - Protivnik

    private func opponentCard(_ viewModel: GameViewModel) -> some View {
        HStack(spacing: DS.Space.s) {
            Image(systemName: "cpu")
                .font(.dsBody)
                .foregroundStyle(DS.accent)
            Text(Loc("Računar"))
                .font(.dsBody)
                .foregroundStyle(DS.ink)
            Text(viewModel.difficulty.label)
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
            Spacer(minLength: 0)
            if viewModel.isThinking {
                ThinkingIndicator(isActive: true)
            }
        }
        .padding(.horizontal, DS.Space.m)
        .padding(.vertical, DS.Space.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.m))
        .overlay(RoundedRectangle(cornerRadius: DS.Radius.m).strokeBorder(DS.line, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }

    // MARK: - Status

    private func statusCard(_ viewModel: GameViewModel) -> some View {
        let style = statusStyle(viewModel)
        return HStack(spacing: DS.Space.s) {
            Image(systemName: style.icon)
                .font(.dsBody)
                .foregroundStyle(style.color)
            Text(viewModel.statusMessage)
                .font(.dsBody)
                .foregroundStyle(style.color)
                .animation(.default, value: viewModel.statusMessage)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, DS.Space.m)
        .padding(.vertical, DS.Space.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(style.color.opacity(0.12), in: RoundedRectangle(cornerRadius: DS.Radius.m))
        .overlay(RoundedRectangle(cornerRadius: DS.Radius.m)
            .strokeBorder(style.color.opacity(0.3), lineWidth: 1))
    }

    /// Boja nosi ISHOD, ne ukras: zeleno kad je korisnik dobio, crveno kad je
    /// izgubio ili predao, zuto za remi i sah.
    private func statusStyle(_ viewModel: GameViewModel) -> (icon: String, color: Color) {
        switch viewModel.gameState.status {
        case .playing:
            return ("info.circle", DS.ink)
        case .check:
            return ("exclamationmark.triangle.fill", DS.warning)
        case .draw:
            return ("equal.circle.fill", DS.warning)
        case .checkmate(let loser), .resigned(let loser):
            return loser == viewModel.playerColor
                ? ("xmark.circle.fill", DS.danger)
                : ("checkmark.seal.fill", DS.success)
        }
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
                        .font(.dsBody.weight(.semibold))
                        .foregroundStyle(DS.onAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, DS.Space.m)
                        .background(DS.accent, in: RoundedRectangle(cornerRadius: DS.Radius.m))
                }
                .buttonStyle(.plain)
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
