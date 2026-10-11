import SwiftUI

// MARK: - Analysis View
//
// Cist prikaz gotovog `GameAnalysis`-a. Ne racuna nista — sve brojke stizu iz
// `GameAnalysis` (Foundation-only, testirano). Dizajnerski pregled § 6: klasa
// poteza je simbol + ispuna (`MoveClassBadge`, `GameComponents.swift`), `accent`
// se za klasu NE koristi (u istoj listi znaci „izabran potez"); lista poteza je
// ista komponenta kao u Igri (`MoveHistoryView`); prelomni potez nosi `warning`
// ikonu, gubitak u pionima („−2,3") i recenicu za pocetnika. Android:
// `ui/AnalysisView.kt`, ista pravila.

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
                        accuracySection(result)
                        if let tp = result.turningPoint { turningPointCard(tp) }
                        moveList(result)
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

    /// § 6 / § 17: ispod `GameAnalysis.minMovesForAccuracy` sopstvenih poteza
    /// strana dobija „—" i napomenu ispod kartica (`inkMuted` na `ground`).
    private func accuracySection(_ result: GameAnalysis) -> some View {
        let white = result.reliableAccuracy(white: true)
        let black = result.reliableAccuracy(white: false)
        return VStack(alignment: .leading, spacing: DS.Space.s) {
            HStack(spacing: DS.Space.l) {
                accuracyBox(title: Loc("beli").capitalized, value: white)
                accuracyBox(title: Loc("crni").capitalized, value: black)
            }
            if white == nil || black == nil {
                Text(Loc("Premalo poteza za procenu tačnosti."))
                    .font(.dsCaption)
                    .foregroundStyle(DS.inkMuted)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private func accuracyBox(title: String, value: Double?) -> some View {
        VStack(spacing: DS.Space.xs) {
            Text(title)
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
            // Lokalni format jezika aplikacije (spec § 3): „41,9 %" na srpskom.
            Text(verbatim: value.map { LocPercent($0) } ?? "\u{2014}")
                .font(.dsTitle)
                .foregroundStyle(DS.ink)
            Text(Loc("Tačnost"))
                .font(.dsCaption)
                .foregroundStyle(DS.inkMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(DS.Space.m)
        .dsCard(radius: DS.Radius.m) // spec § 3: surface + 1 pt line
        .accessibilityElement(children: .combine)
    }

    // MARK: - Prelomni potez

    /// § 6: ikona `warning` (bila `danger`/boja klase), „2.g4  −2,3" i recenica za
    /// pocetnika umesto „2.g4 (−233)".
    private func turningPointCard(_ move: AnalyzedMove) -> some View {
        let loss = EvaluationLabel.loss(cpLoss: move.cpLoss,
                                        languageCode: LocalizationManager.shared.effectiveCode)
        let sentence = explanation(for: move)
        return HStack(alignment: .top, spacing: DS.Space.m) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(DS.warning)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: DS.Space.xs) {
                Text(Loc("Prelomni potez"))
                    .font(.dsCaption)
                    .foregroundStyle(DS.inkMuted)
                HStack(spacing: DS.Space.s) {
                    MoveClassBadge(moveClass: move.moveClass)
                    Text(verbatim: move.displayNotation)
                        .font(.dsHeading)
                        .foregroundStyle(DS.ink)
                    Text(verbatim: loss)
                        .font(.dsHeading)
                        .foregroundStyle(DS.ink)
                        .monospacedDigit()
                }
                Text(sentence)
                    .font(.dsBody)
                    .foregroundStyle(DS.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(DS.Space.m)
        .dsCard(radius: DS.Radius.m) // spec § 3: surface + 1 pt line
        .contentShape(Rectangle())
        .onTapGesture { open(move) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim:
            "\(Loc("Prelomni potez")), \(move.moveClass.accessibilityName), \(move.displayNotation), \(loss). \(sentence)"))
        .accessibilityAddTraits(.isButton)
    }

    /// Recenica za pocetnika (§ 6). Strana je ona koja je odigrala potez.
    private func explanation(for move: AnalyzedMove) -> String {
        switch move.beginnerExplanation {
        case .pawnsLost(let n):
            return move.byWhite ? LocPlural("Beli gubi oko %lld piona prednosti.", n)
                                : LocPlural("Crni gubi oko %lld piona prednosti.", n)
        case .missedMate:
            return move.byWhite ? Loc("Beli propušta mat.") : Loc("Crni propušta mat.")
        case .allowedMate:
            return move.byWhite ? Loc("Beli dozvoljava mat.") : Loc("Crni dozvoljava mat.")
        }
    }

    // MARK: - Lista poteza

    /// § 6: ista lista kao u Igri (broj · beli · crni), oznaka klase ispred poteza,
    /// izabran red na accent tinti. Izabran je potez koji je korisnik vec otvorio u
    /// pregledu partije (isti `viewingMoveIndex` kao u Igri); tap otvara potez na tabli.
    /// Lista je unutar skrola ekrana, pa dobija punu visinu (bez sopstvenog skrola).
    private func moveList(_ result: GameAnalysis) -> some View {
        let classes = Dictionary(uniqueKeysWithValues: result.moves.map { ($0.ply, $0.moveClass) })
        let rows = (result.moves.count + 1) / 2
        return MoveHistoryView(
            notations: result.moves.map(\.notation),
            selectedMoveIndex: viewModel.viewingMoveIndex,
            highlightsLastMoveWhenLive: false,
            moveClass: { classes[$0] },
            onSelectMove: { index in   // broj odigranih poluteza (ply + 1)
                viewModel.goToMove(index)
                dismiss()
            }
        )
        .frame(height: CGFloat(rows) * MoveHistoryView.rowHeight + 2 * DS.Space.xs)
    }

    /// Otvara poziciju POSLE odigranog poteza u zatecenom review modu.
    /// `allHistoryStates` indeks je `ply + 1` jer je na indeksu 0 pocetna
    /// pozicija, pre ijednog poteza.
    private func open(_ move: AnalyzedMove) {
        viewModel.goToMove(move.ply + 1)
        dismiss()
    }
}
