import SwiftUI

// MARK: - Analysis View
//
// Cist prikaz gotovog `GameAnalysis`-a. Ne racuna nista — sve brojke stizu iz
// `GameAnalysis` (Foundation-only, testirano). Jedino sto ovaj fajl zna, a
// model ne, jeste kako klasa poteza izgleda: boja i naziv.

struct AnalysisView: View {

    let viewModel: GameViewModel
    @State private var analysis = AnalysisViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: DS.Space.l) {
                    switch analysis.phase {
                    case .idle:
                        ProgressView().padding(.top, DS.Space.xl)
                    case .running(let done, let total):
                        runningView(done: done, total: total)
                    case .failed(let message):
                        Text(message)
                            .font(.dsBody)
                            .foregroundStyle(DS.inkMuted)
                            .multilineTextAlignment(.center)
                            .padding(.top, DS.Space.xl)
                    case .done(let result):
                        accuracyRow(result)
                        if let tp = result.turningPoint { turningPointCard(tp) }
                        moveStrip(result)
                    }
                }
                .padding(DS.Space.l)
                .frame(maxWidth: .infinity)
            }
            .background(DS.ground)
            .navigationTitle(Loc("Analiza partije"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(Loc("Zatvori")) { dismiss() }
                }
            }
        }
        .task {
            analysis.start(
                states: viewModel.allHistoryStates,
                notations: viewModel.gameState.moveNotations
            )
        }
        // Analiza od 81 pozicije ne sme da nastavi da melje kad ekran nestane.
        .onDisappear { analysis.cancel() }
    }

    // MARK: - Napredak

    private func runningView(done: Int, total: Int) -> some View {
        VStack(spacing: DS.Space.m) {
            ProgressView(value: Double(done), total: Double(max(total, 1)))
                .tint(DS.accent)
            // Bez `Loc`: goli brojevi se ne prevode, pa bi kljuc bio suvisan.
            Text(verbatim: "\(done) / \(total)")
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
            Text(Loc("Analiziram…"))
                .font(.dsBody)
                .foregroundStyle(DS.ink)
        }
        .padding(.top, DS.Space.xl)
    }

    // MARK: - Tacnost

    private func accuracyRow(_ result: GameAnalysis) -> some View {
        HStack(spacing: DS.Space.l) {
            accuracyBox(title: Loc("beli").capitalized, value: result.whiteAccuracy)
            accuracyBox(title: Loc("crni").capitalized, value: result.blackAccuracy)
        }
    }

    private func accuracyBox(title: String, value: Double) -> some View {
        VStack(spacing: DS.Space.xs) {
            Text(title)
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
            Text(verbatim: String(format: "%.1f%%", value))
                .font(.dsTitle)
                .foregroundStyle(DS.ink)
            Text(Loc("Tačnost"))
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(DS.Space.m)
        .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.m))
    }

    // MARK: - Prelomni potez

    private func turningPointCard(_ move: AnalyzedMove) -> some View {
        HStack(spacing: DS.Space.m) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(color(for: move.moveClass))
            VStack(alignment: .leading, spacing: 2) {
                Text(Loc("Prelomni potez"))
                    .font(.dsCaption)
                    .foregroundStyle(DS.inkMuted)
                Text(verbatim: "\(move.displayNotation)  (−\(move.cpLoss))")
                    .font(.dsHeading)
                    .foregroundStyle(DS.ink)
            }
            Spacer()
        }
        .padding(DS.Space.m)
        .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.m))
        .contentShape(Rectangle())
        .onTapGesture { open(move) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    // MARK: - Traka poteza

    private func moveStrip(_ result: GameAnalysis) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: DS.Space.s)],
                  spacing: DS.Space.s) {
            ForEach(result.moves) { move in
                Button { open(move) } label: {
                    HStack(spacing: DS.Space.xs) {
                        Circle()
                            .fill(color(for: move.moveClass))
                            .frame(width: 8, height: 8)
                        Text(verbatim: move.displayNotation)
                            .font(.dsMono)
                            .foregroundStyle(DS.ink)
                        Spacer(minLength: 0)
                    }
                    .padding(.vertical, DS.Space.xs)
                    .padding(.horizontal, DS.Space.s)
                    .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.s))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: "\(move.displayNotation), \(label(for: move.moveClass))"))
            }
        }
    }

    // MARK: - Klase

    private func color(for c: MoveClass) -> Color {
        switch c {
        case .best:       DS.accent
        case .excellent:  DS.success
        case .good:       DS.success
        case .inaccuracy: DS.warning
        case .mistake:    DS.warning
        case .blunder:    DS.danger
        }
    }

    private func label(for c: MoveClass) -> String {
        switch c {
        case .best:       Loc("najbolji")
        case .excellent:  Loc("odličan")
        case .good:       Loc("dobar")
        case .inaccuracy: Loc("netačnost")
        case .mistake:    Loc("greška")
        case .blunder:    Loc("promašaj")
        }
    }

    /// Otvara poziciju POSLE odigranog poteza u zatecenom review modu.
    /// `allHistoryStates` indeks je `ply + 1` jer je na indeksu 0 pocetna
    /// pozicija, pre ijednog poteza.
    private func open(_ move: AnalyzedMove) {
        viewModel.goToMove(move.ply + 1)
        dismiss()
    }
}
