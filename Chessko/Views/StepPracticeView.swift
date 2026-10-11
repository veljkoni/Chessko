import SwiftUI

// MARK: - Step Practice View (pokretac `practice` i `test` koraka Puta)
//
// Ekran je NAMERNO tanak. Ceo tok resavanja (tap, provera poteza, protivnikov
// odgovor, animacije, rejting) vec zivi u `PuzzleViewModel`-u i deli se sa
// tabom Zadaci; ovde se dodaje samo ono sto korak ima a pojedinacan zadatak
// nema — red zadataka, traka napretka i zavrsetak koraka.
//
// Dve stvari koje ovaj ekran svesno NE nudi:
//
// 1. „Prikazi resenje". Na tabu Zadaci to je izlaz iz zaglavljenog zadatka; u
//    koraku Puta bi bio izlaz iz PROVERE — korak bi se zavrsavao gledanjem.
// 2. „Sledeci zadatak". Red je fiksan i napunjen unapred; sledeci stize sam
//    kad se tekuci resi.
//
// Sopstveni `PuzzleViewModel` (a ne onaj iz `ContentView`-a) je bitan: deljeni
// model bi ulaskom u korak pregazio zadatak dana koji korisnik mozda ima
// zapocet na drugom tabu, i obrnuto.

struct StepPracticeView: View {

    let step: CurriculumStep

    @State private var viewModel = PuzzleViewModel()

    /// `onAppear` se okida i pri povratku sa drugog ekrana; bez ovoga bi svaki
    /// povratak nulirao napredak kroz korak i izvukao nove zadatke.
    @State private var started = false

    @Environment(\.dismiss) private var dismiss
    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.verticalSizeClass) private var vSize

    /// Spec § 13: tablet = `regular` u obe ose.
    private var isTablet: Bool { hSize == .regular && vSize == .regular }

    // MARK: - Body

    var body: some View {
        ZStack {
            AppBackgroundView()

            GeometryReader { geo in
                let isLandscape = geo.size.width > geo.size.height
                let side = boardSide(in: geo.size, isLandscape: isLandscape)
                if isTablet && isLandscape {
                    // spec § 13: korak vežbe u pejzažu na tabletu isto kao Zadaci —
                    // tabla + panel 400 centrirani kao par, vrhovi poravnati.
                    HStack(alignment: .top, spacing: DS.Space.xl) {
                        board(side: side)
                            .padding(.vertical, DS.Space.xl)
                        ScrollView {
                            VStack(spacing: DS.Space.m) {
                                progressHeader
                                instructionBox
                                footer
                            }
                            .padding(.vertical, DS.Space.xl)
                        }
                        .scrollBounceBehavior(.basedOnSize)
                        .frame(width: BoardSizing.tabletPanelWidth)
                    }
                    .frame(width: geo.size.width, height: geo.size.height, alignment: .top)
                } else {
                    ScrollView {
                        // spec § 7: sadržaj od vrha — `Space.l` ispod zaglavlja,
                        // ništa centrirano po visini. § 13: kolona široka kao tabla.
                        VStack(spacing: DS.Space.m) {
                            progressHeader
                            instructionBox
                            board(side: side)
                            footer
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
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(DS.ground, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .onAppear {
            guard !started else { return }
            started = true
            viewModel.startStepPractice(step: step)
        }
    }

    // MARK: - Raspored

    private var instructionBox: some View {
        DSInstructionBox(
            text: viewModel.statusMessage,
            sideToMove: viewModel.phase.isSolving ? viewModel.playerColor : nil,
            tone: viewModel.phase.instructionTone
        )
    }

    /// Strana table — zajednička funkcija `BoardSizing.boardSide` (spec § 13).
    /// Na telefonu ostaje zatečena rezerva od 220 po visini: bez nje bi tabla u
    /// pejzažu progutala ceo ekran i gurnula traku napretka i status van vidnog
    /// polja (ista greška koja je u Fazi 1 četiri puta popravljana na ekranu Igra).
    private func boardSide(in size: CGSize, isLandscape: Bool) -> CGFloat {
        BoardSizing.boardSide(width: size.width, height: size.height,
                              isTablet: isTablet, isLandscape: isLandscape,
                              phoneHeightReserve: BoardSizing.stepPhoneHeightReserve)
    }

    private func board(side: CGFloat) -> some View {
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
            gameStatus:       viewModel.gameState.status,
            onTap:            { viewModel.tap(position: $0) }
        )
        .frame(width: side, height: side)
    }

    // MARK: - Traka napretka

    private var progressHeader: some View {
        let progress = viewModel.stepProgress
        // Pre nego sto se red napuni total je 0; brojac tada ne sme da kaze
        // „Zadatak 1 od 0".
        let total = max(progress.total, 1)
        let current = min(progress.solved + 1, total)

        return VStack(alignment: .leading, spacing: DS.Space.s) {
            HStack(spacing: DS.Space.s) {
                Text(LocF("Zadatak %lld od %lld", current, total))
                    .font(.dsCaption.weight(.semibold))
                    .foregroundStyle(DS.inkMuted)
                    .monospacedDigit()
                Spacer(minLength: 0)
            }

            // Po jedna pilula za svaki zadatak u redu — isti obrazac kao vezbe
            // otvaranja u lekcijama.
            // spec § 7: rešeni `accent`, tekući `accent` 40 %, nerešeni `fill`.
            HStack(spacing: DS.Space.xs) {
                ForEach(0..<total, id: \.self) { index in
                    Capsule()
                        .fill(segmentColor(index: index, solved: progress.solved))
                        .frame(height: 6)
                }
            }

            if viewModel.stepRequiresFlawless && !isStepFinished {
                Text(Loc("Test mora biti rešen bez greške"))
                    .font(.dsCaption)
                    .foregroundStyle(DS.warning)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private func segmentColor(index: Int, solved: Int) -> Color {
        if index < solved { return DS.accent }
        if index == solved && !isStepFinished { return DS.accent.opacity(0.4) }
        return DS.fill
    }

    // MARK: - Zavrsetak koraka

    /// Korak je gotov kad je resen SVAKI zadatak iz reda. Za `test` ovo je
    /// dostizno samo bez greske — prva greska ga vrati na pocetak sa novim
    /// zadacima, pa `stepSolved` krene od nule.
    private var isStepFinished: Bool {
        // `!stepFailed` je drugi branik za isti slucaj koji `isPlayerTurn` sada
        // gasi na izvoru: pao test NIJE zavrsen korak, ma koliko zadataka bilo
        // reseno. Bez ovoga bi ekran napisao "Korak je zavrsen" i ponudio
        // "Nazad na Put", dok Put isti korak jos vodi kao nezavrsen.
        let progress = viewModel.stepProgress
        return !viewModel.stepFailed && progress.total > 0 && progress.solved >= progress.total
    }

    @ViewBuilder
    private var footer: some View {
        if isStepFinished {
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
        }
    }

    // MARK: - Naslov

    /// Isti tekst koji red nosi na ekranu Puta, iz istih kljuceva — korisnik
    /// dolazi sa tog ekrana i mora da prepozna gde je usao.
    private var navigationTitle: String {
        switch step.kind {
        case .practice(_, let count, _): return LocF("Vežba · %lld", count)
        case .test(_, let count, _):     return LocF("Test · %lld", count)
        case .lesson, .game:             return Loc("Put")
        }
    }
}
