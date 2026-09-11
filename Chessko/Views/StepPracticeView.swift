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

    // MARK: - Body

    var body: some View {
        ZStack {
            AppBackgroundView()

            GeometryReader { geo in
                ScrollView {
                    // Spacer-i na oba kraja + `minHeight`: kratak sadrzaj se
                    // centrira po visini, dug normalno skroluje. Bez
                    // `minHeight`-a ovo ne radi nista — `ScrollView` predlaze
                    // neogranicenu visinu (isti obrazac kao na tabu Zadaci).
                    VStack(spacing: 0) {
                        Spacer(minLength: 0)
                        VStack(spacing: DS.Space.m) {
                            progressHeader
                            statusCard
                            board(side: boardSide(in: geo.size))
                            footer
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
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.appBackground, for: .navigationBar)
        .onAppear {
            guard !started else { return }
            started = true
            viewModel.startStepPractice(step: step)
        }
    }

    // MARK: - Raspored

    /// Tabla mora da stane i kad je ekran polozen: bez ogranicenja po VISINI
    /// racun bi je vodio samo po sirini, pa bi u pejzazu progutala ceo ekran i
    /// gurnula traku napretka i status van vidnog polja (ista greska koja je u
    /// Fazi 1 cetiri puta popravljana na ekranu Igra).
    private func boardSide(in size: CGSize) -> CGFloat {
        let byWidth = size.width - 2 * DS.Space.l
        let byHeight = size.height - 220
        return max(200, min(byWidth, byHeight, DS.maxBoardSide))
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
            HStack(spacing: DS.Space.xs) {
                ForEach(0..<total, id: \.self) { index in
                    Capsule()
                        .fill(index < progress.solved ? DS.accent : DS.fill)
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

    // MARK: - Status

    private var statusCard: some View {
        HStack(spacing: DS.Space.s) {
            Image(systemName: statusIcon)
                .font(.dsBody)
                .foregroundStyle(statusColor)
            Text(viewModel.statusMessage)
                .font(.dsBody)
                .foregroundStyle(statusColor)
                .animation(.default, value: viewModel.statusMessage)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, DS.Space.m)
        .padding(.vertical, DS.Space.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(statusColor.opacity(0.12), in: RoundedRectangle(cornerRadius: DS.Radius.m))
        .overlay(RoundedRectangle(cornerRadius: DS.Radius.m)
            .strokeBorder(statusColor.opacity(0.3), lineWidth: 1))
    }

    private var statusIcon: String {
        switch viewModel.phase {
        case .loading:         return "arrow.clockwise"
        case .unavailable:     return "exclamationmark.triangle"
        case .playing:         return "lightbulb"
        case .wrongMove:       return "xmark.circle.fill"
        case .solved:          return "checkmark.seal.fill"
        case .showingSolution: return "eye.fill"
        }
    }

    private var statusColor: Color {
        switch viewModel.phase {
        case .wrongMove:   return DS.danger
        case .solved:      return DS.success
        case .unavailable: return DS.warning
        default:           return DS.ink
        }
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
                        .font(.dsBody.weight(.semibold))
                        .foregroundStyle(DS.onAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, DS.Space.m)
                        .background(DS.accent, in: RoundedRectangle(cornerRadius: DS.Radius.m))
                }
                .buttonStyle(.plain)
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
