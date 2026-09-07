import SwiftUI

// MARK: - Lesson Renderer
//
// Jedino mesto koje zna kako se blok sadržaja crta. Nova lekcija je nov JSON;
// nov tip bloka je jedna grana ovde plus jedan slučaj u `LessonBlock`.
//
// Razmaci: u Swift izvoru lekcija svako pozivno mesto je nosilo sopstveni
// `.padding(.bottom, …)` (4/8/12/16 za pasus, 0/8/16 za vežbu). JSON to ne
// prenosi — namerno, jer bi razmak u sadržaju bio dizajn u podacima. Ovde je
// po tipu bloka jedna vrednost; odstupanja su popisana u izveštaju Task-a 4.

struct LessonRenderer: View {
    let blocks: [LessonBlock]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                view(for: block)
            }
        }
        // Prvi blok svake lekcije je citat-kutija koja je u izvoru imala
        // `.padding(.top, 4)`; ovde stoji na korenu, sa istim rezultatom.
        .padding(.top, 4)
    }

    @ViewBuilder
    private func view(for block: LessonBlock) -> some View {
        switch block {
        case .heading(let text, let icon):
            L_SectionHeader(icon: icon, title: text, color: DS.accent)

        case .paragraph(let text):
            L_Para(text)
                .padding(.horizontal, 20).padding(.bottom, 8)

        case .bullets(let items):
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                L_Bullet(icon: item.icon,
                         color: item.style.map(color(for:)) ?? DS.accent,
                         title: item.title, text: item.text)
            }

        case .box(let style, let icon, let title, let text):
            L_Box(icon: icon, color: color(for: style), title: title, text: text)

        case .quote(let text, let author):
            L_Box(icon: "quote.opening", color: DS.accent, title: author, text: text)

        case .pieceRow(let piece, let name, let count):
            if let type = Self.pieceType(lessonKey: piece) {
                L_PieceRow(type: type, name: name, count: count)
            } else {
                LessonBlockError(message: "Nepoznata figura '\(piece)' u redu '\(name)'")
            }

        case .numberedRule(let number, let title, let text):
            L_NumberedRule(number: number, color: DS.accent, title: title, text: text)

        case .pieceValueTable(let rows):
            L_PieceValueTable(rows: rows)

        case .board(let fen, let caption, let interactive):
            // Interaktivna tabla u lekciji jos nije implementirana. Tiho crtanje
            // staticne bi dalo mrtvu tablu bez ijednog traga; bolje da pisac
            // lekcije odmah vidi da polje nista ne radi.
            if interactive {
                LessonBlockError(message: "Interaktivna tabla još nije podržana: '\(caption)'")
            } else {
                LessonStaticBoard(fen: fen, caption: caption)
            }

        case .explorer:
            LessonPieceExplorer()

        case .divider:
            Divider().background(DS.line)
                .padding(.horizontal, 20).padding(.vertical, 12)

        case .exercise(let spec):
            exerciseView(spec)
        }
    }

    @ViewBuilder
    private func exerciseView(_ spec: ExerciseSpec) -> some View {
        switch spec.kind {
        case .vsEngine:
            // `startFEN` je kod vsEngine obavezan; prazan string bi dao praznu
            // tablu, pa se blok preskače uz vidljivu poruku umesto tihe rupe.
            if let fen = spec.startFEN {
                MateExerciseCard(fen: fen, title: spec.title, hint: spec.hint,
                                 icon: spec.icon, color: DS.accent)
                    .padding(.horizontal, 16).padding(.bottom, 16)
            } else {
                LessonBlockError(message: "Vežba '\(spec.title)' nema startFEN")
            }

        case .scripted:
            if let moves = spec.uciMoves {
                if let mateIn = spec.mateIn {
                    // `MatePuzzleCard` NE prima `OpeningLine` nego raspakovane
                    // parametre, i `fen` mu NIJE opcion. `OpeningExerciseCard`
                    // prima `line:`. Potpisi se razlikuju — provereno u izvoru.
                    if let fen = spec.startFEN {
                        MatePuzzleCard(fen: fen, moves: moves, title: spec.title,
                                       hint: spec.hint, icon: spec.icon,
                                       accentColor: DS.accent, mateIn: mateIn,
                                       solvedMessage: spec.solvedMessage,
                                       wrongMessage: spec.wrongMessage,
                                       playingPrompt: spec.playingPrompt)
                            // Pet zadataka finalnog testa u izvoru stoje spojeni,
                            // bez razmaka među karticama — otud nema `.bottom`.
                            .padding(.horizontal, 16)
                    } else {
                        LessonBlockError(message: "Zadatak '\(spec.title)' ima mateIn ali nema startFEN")
                    }
                } else {
                    OpeningExerciseCard(line: OpeningLine(
                        name: spec.title, uciMoves: moves, hint: spec.hint,
                        icon: spec.icon, accentColor: DS.accent,
                        solvedMessage: spec.solvedMessage ?? Loc("Bravo! Otvaranje savladano! ✓"),
                        wrongMessage:  spec.wrongMessage  ?? Loc("Pogrešan potez — pokušaj ponovo."),
                        playingPrompt: spec.playingPrompt,
                        startFEN: spec.startFEN))
                        .padding(.horizontal, 16).padding(.bottom, 16)
                }
            } else {
                LessonBlockError(message: "Vežba '\(spec.title)' nema uciMoves")
            }
        }
    }

    private func color(for style: BoxStyle) -> Color {
        switch style {
        case .rule:    return DS.warning
        case .warning: return DS.danger
        case .info:    return DS.accent
        }
    }

    // Namerno NEMA `default: return .king`. Tipfeler u JSON-u ("knght") bi tako
    // nacrtao Kralja — tiha rupa, tacno ono sto `LessonBlockError` postoji da
    // sprecava. Vraca `nil`, pozivalac prijavljuje.
    static func pieceType(lessonKey raw: String) -> PieceType? {
        switch raw {
        case "pawn":   return .pawn
        case "knight": return .knight
        case "bishop": return .bishop
        case "rook":   return .rook
        case "queen":  return .queen
        case "king":   return .king
        default:       return nil
        }
    }
}

/// Vidljiva poruka umesto tihe rupe kad je blok nepotpun. Sadržaj je van koda,
/// pa greška u JSON-u ne sme da se izgubi bez traga.
struct LessonBlockError: View {
    let message: String
    var body: some View {
        Text(message)
            .font(.dsCaption)
            .foregroundStyle(DS.danger)
            .padding(.horizontal, 20).padding(.bottom, 8)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Static Board
// ─────────────────────────────────────────────────────────────────────────────

/// Nepokretna pozicija iz FEN-a sa potpisom ispod. Nijedna od četiri lekcije je
/// za sada ne koristi (`board` blok postoji u šemi, ali ga generator ne emituje),
/// pa je namerno svedena na isti okvir kakav nose kartice vežbi.
struct LessonStaticBoard: View {
    let fen: String
    let caption: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let state = GameState.fromFEN(fen) {
                BoardView(
                    board:            state.board,
                    isFlipped:        false,
                    selectedPosition: nil,
                    legalMoves:       [],
                    lastMove:         nil,
                    animatingPiece:   nil,
                    flyingCapture:    nil,
                    playerColor:      .white,
                    isPlayerTurn:     false,
                    allowsStyleSwipe: false,
                    onTap:            { _ in }
                )
                .aspectRatio(1, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .allowsHitTesting(false)
            } else {
                LessonBlockError(message: "Neispravan FEN: \(fen)")
            }
            if !caption.isEmpty {
                Text(Loc(caption))
                    .font(.dsCaption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, 16).padding(.bottom, 16)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Piece Explorer
// ─────────────────────────────────────────────────────────────────────────────

/// Interaktivni „sandbox" iz lekcije 1, izdvojen iz `LessonDetailView`-a
/// nepromenjen. Tri stringa oko table (naslov specijalnih pravila, njegov
/// dodatak i savet o tapu) ostaju ovde jer `case explorer` nema sadržaja.
struct LessonPieceExplorer: View {

    /// `LearnView` do Task-a 5 i dalje drži jedan `LearnViewModel` podignut u
    /// `ContentView`-u i prosleđuje ga; renderer ga nema pa pravi sopstveni.
    private let injected: LearnViewModel?
    @State private var owned = LearnViewModel()

    init(viewModel: LearnViewModel? = nil) { self.injected = viewModel }

    private var vm: LearnViewModel { injected ?? owned }

    var body: some View {
            VStack(spacing: 10) {
                piecePicker
                BoardView(
                    board:            vm.board,
                    isFlipped:        false,
                    selectedPosition: vm.selectedPosition,
                    legalMoves:       vm.legalMoves,
                    lastMove:         vm.lastMove,
                    animatingPiece:   vm.animatingPiece,
                    flyingCapture:    vm.flyingCapture,
                    playerColor:      .white,
                    isPlayerTurn:     true,
                    allowsStyleSwipe: false,
                    onTap:            { vm.tap(position: $0) }
                )
                .padding(.horizontal, 4)

                let scenarios = vm.availableScenarios
                if !scenarios.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 6) {
                            Image(systemName: "star.fill")
                                .font(.dsCaption.weight(.semibold))
                                .foregroundStyle(DS.warning)
                            Text(Loc("Specijalna pravila"))
                                .font(.dsBody.weight(.semibold))
                                .foregroundStyle(.primary)
                            Text(Loc("— izaberi i istraži na tabli"))
                                .font(.dsCaption)
                                .foregroundStyle(.secondary)
                        }
                        HStack(spacing: 10) {
                            ForEach(scenarios, id: \.self) { s in
                                let active = vm.activeScenario == s
                                Button {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        vm.toggleScenario(s)
                                    }
                                } label: {
                                    HStack(spacing: 5) {
                                        Image(systemName: active ? "checkmark.circle.fill" : "circle")
                                            .font(.dsCaption.weight(.semibold))
                                        Text(s.label)
                                            .font(.dsBody.weight(.semibold))
                                            .lineLimit(1)
                                            .minimumScaleFactor(0.8)
                                    }
                                    .foregroundStyle(active ? DS.inkFixed : Color.primary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 9)
                                    .background(
                                        active ? DS.warning : Color.primary.opacity(0.08),
                                        in: RoundedRectangle(cornerRadius: 10)
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .strokeBorder(
                                                active ? DS.warning : Color.primary.opacity(0.14),
                                                lineWidth: 1
                                            )
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(14)
                    .background(DS.warning.opacity(0.07), in: RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(DS.warning.opacity(0.25), lineWidth: 1)
                    )
                }

                HStack(spacing: 5) {
                    Image(systemName: "hand.point.up.left").font(.appFont(.caption2))  // van skale — caption2 nema DS ekvivalent
                    Text(Loc("Tapni figuru da je promeniš · Tapni polje da je premestiš")).font(.dsCaption)
                }
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 16).padding(.bottom, 24)
    }

    private var piecePicker: some View {
        let pieces: [PieceType] = [.pawn, .knight, .bishop, .rook, .queen, .king]
        let columns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 3)
        return LazyVGrid(columns: columns, spacing: 6) {
            ForEach(pieces, id: \.self) { piece in
                let sel = vm.selectedPieceType == piece
                Button {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        vm.select(piece: piece)
                    }
                } label: {
                    VStack(spacing: 3) {
                        PieceImageView(piece: ChessPiece(type: piece, color: .white))
                            .frame(width: 28, height: 28)
                        Text(piece.srbName)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(sel ? Color.primary : Color.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(sel ? Color.primary.opacity(0.14) : Color.primary.opacity(0.05),
                                in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Reusable Lesson Components
//
// Premešteno iz `LessonDetailView.swift` nepromenjeno (Faza 3, Task 4).
// `L_OpeningCard` nije prenesen — mrtav kod od 2026-06-24.
// ─────────────────────────────────────────────────────────────────────────────

struct L_SectionHeader: View {
    let icon: String
    let title: String
    let color: Color

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.dsBody.weight(.semibold))
                .foregroundStyle(color)
            Text(Loc(title))
                .font(.dsBody.weight(.bold))
                .foregroundStyle(.primary)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 10)
    }
}

/// Parsira inline Markdown (**podebljano**, *kurziv*) u vec prevedenom stringu.
/// Loc() vraca String, a Text(String) ne parsira Markdown — parsira ga samo
/// Text(LocalizedStringKey), sto bi ovde znacilo drugo trazenje po katalogu.
fileprivate func mdText(_ localized: String) -> Text {
    if let attributed = try? AttributedString(
        markdown: localized,
        options: AttributedString.MarkdownParsingOptions(
            interpretedSyntax: .inlineOnlyPreservingWhitespace
        )
    ) {
        return Text(attributed)
    }
    return Text(localized)
}

struct L_Para: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        mdText(Loc(text))
            .font(.dsBody)
            .foregroundStyle(.primary.opacity(0.85))
            .fixedSize(horizontal: false, vertical: true)
    }
}

struct L_Bullet: View {
    let icon: String
    let color: Color
    let title: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.dsCaption.weight(.semibold))
                .foregroundStyle(color)
                .frame(width: 18)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 3) {
                Text(Loc(title))
                    .font(.dsBody.weight(.semibold))
                    .foregroundStyle(.primary)
                mdText(Loc(text))
                    .font(.dsBody)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }
}

struct L_Box: View {
    let icon: String
    let color: Color
    let title: String
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.dsCaption.weight(.bold))
                    .foregroundStyle(color)
                Text(Loc(title))
                    .font(.dsCaption.weight(.bold))
                    .foregroundStyle(color)
            }
            mdText(Loc(text))
                .font(.dsBody)
                .foregroundStyle(.primary.opacity(0.85))
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(color.opacity(0.3), lineWidth: 1))
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }
}

struct L_PieceRow: View {
    let type: PieceType
    let name: String
    let count: String

    var body: some View {
        HStack(spacing: 12) {
            PieceImageView(piece: ChessPiece(type: type, color: .white))
                .frame(width: 32, height: 32)
            Text(Loc(name))
                .font(.dsBody.weight(.bold))
                .foregroundStyle(.primary)
            Spacer()
            Text(count)
                .font(.dsCaption.weight(.medium))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(Color.primary.opacity(0.06), in: Capsule())
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 6)
    }
}

struct L_NumberedRule: View {
    let number: Int
    let color: Color
    let title: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                Circle().fill(color.opacity(0.2)).frame(width: 32, height: 32)
                Text("\(number)")
                    .font(.dsBody.weight(.bold))
                    .foregroundStyle(color)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(Loc(title))
                    .font(.dsBody.weight(.bold))
                    .foregroundStyle(.primary)
                mdText(Loc(text))
                    .font(.dsBody)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 14)
    }
}

/// Redovi više nisu ugrađeni — dolaze iz JSON-a. Formatiranje i boja su
/// nepromenjeni: srpska množina se računa ovde ("1 bod" / "3 boda" / "9 bodova"),
/// a Kraljevo „∞" je jedina vrednost u akcentu.
struct L_PieceValueTable: View {
    let rows: [PieceValueRow]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { idx, row in
                HStack(spacing: 12) {
                    // Isto preslikavanje kao u rendereru — jedan izvor istine.
                    // Nepoznat kljuc daje prazno mesto i vidljivu poruku umesto
                    // tiho nacrtanog Kralja.
                    if let type = LessonRenderer.pieceType(lessonKey: row.piece) {
                        PieceImageView(piece: ChessPiece(type: type, color: .white))
                            .frame(width: 28, height: 28)
                    } else {
                        Image(systemName: "questionmark.square.dashed")
                            .frame(width: 28, height: 28)
                            .foregroundStyle(DS.danger)
                    }
                    Text(Loc(row.name))
                        .font(.dsBody)
                        .foregroundStyle(.primary)
                    Spacer()
                    Text(Loc(row.value == "∞" ? "∞" : "\(row.value) bod\(row.value == "1" ? "" : row.value == "9" ? "ova" : "a")"))
                        .font(.dsBody.weight(.semibold))
                        .foregroundStyle(row.value == "∞" ? DS.accent : .primary)
                }
                .padding(.vertical, 9)
                .padding(.horizontal, 14)
                .background(idx % 2 == 0 ? Color.primary.opacity(0.03) : Color.clear)
            }
        }
        .frame(maxWidth: .infinity)
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.primary.opacity(0.08), lineWidth: 1))
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
    }

}

// MARK: - Mate Puzzle Card

struct MatePuzzleCard: View {

    let mateIn: Int         // 1 or 2 (for badge display)

    @State private var vm: OpeningExerciseViewModel

    /// Poruke su parametri, ne konstante: sadrzaj je od Faze 3 u JSON-u, pa bi
    /// zakucane vrednosti znacile da izmena `solvedMessage`/`wrongMessage` u
    /// `endgame.*.json` prividno nista ne radi. Podrazumevane vrednosti su
    /// dosadasnje zakucane, da ponasanje ostane isto kad ih JSON ne zada.
    init(fen: String, moves: [String], title: String, hint: String,
         icon: String, accentColor: Color, mateIn: Int,
         solvedMessage: String? = nil, wrongMessage: String? = nil,
         playingPrompt: String? = nil) {
        self.mateIn = mateIn
        let line = OpeningLine(
            name: title,
            uciMoves: moves,
            hint: hint,
            icon: icon,
            accentColor: accentColor,
            solvedMessage: solvedMessage ?? Loc("Sjajno! Mat pronađen! 🏆"),
            wrongMessage:  wrongMessage  ?? Loc("Nije to — traži pravi ključni potez!"),
            playingPrompt: playingPrompt ?? (mateIn == 1
                ? Loc("Pronađi mat u 1 potezu!")
                : Loc("Pronađi ključni potez!")),
            startFEN: fen
        )
        _vm = State(initialValue: OpeningExerciseViewModel(line: line))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            // Header
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(vm.line.accentColor.opacity(0.2))
                        .frame(width: 32, height: 32)
                    Image(systemName: vm.line.icon)
                        .font(.dsCaption.weight(.semibold))
                        .foregroundStyle(vm.line.accentColor)
                }
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(Loc(vm.line.name))
                            .font(.dsBody.weight(.semibold))
                            .foregroundStyle(.primary)
                        Text(LocF("Mat u %lld", mateIn))
                            .font(.appFont(.caption2).weight(.bold))  // van skale — caption2 nema DS ekvivalent
                            .foregroundStyle(vm.line.accentColor)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(vm.line.accentColor.opacity(0.18), in: Capsule())
                    }
                    Text(Loc(vm.line.hint))
                        .font(.dsCaption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
                if vm.phase == .solved {
                    Image(systemName: "trophy.fill")
                        .foregroundStyle(DS.success)
                        .font(.appFont(.title3))  // van skale — ikonica statusa, title3 nema DS ekvivalent
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)

            // Board
            BoardView(
                board:            vm.gameState.board,
                isFlipped:        false,
                selectedPosition: vm.selectedPosition,
                legalMoves:       vm.legalMovesForSelected,
                lastMove:         vm.lastMove,
                animatingPiece:   vm.animatingPiece,
                flyingCapture:    nil,
                playerColor:      .white,
                isPlayerTurn:     vm.isPlayerTurn,
                allowsStyleSwipe: false,
                onTap:            { vm.tap(position: $0) }
            )
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .padding(.horizontal, 10)

            // Status bar
            HStack(spacing: 6) {
                Text(vm.statusMessage)
                    .font(.dsCaption.weight(.medium))
                    .foregroundStyle(puzzleStatusColor)
                    .lineLimit(1).minimumScaleFactor(0.8)
                Spacer(minLength: 0)
                Button {
                    withAnimation(.easeInOut(duration: 0.15)) { vm.reset() }
                } label: {
                    Label("Ponovo", systemImage: "arrow.counterclockwise")
                        .font(.dsCaption.weight(.medium))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(Color.primary.opacity(0.06), in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(puzzleBorderColor, lineWidth: 1.5)
        )
        .animation(.easeInOut(duration: 0.2), value: vm.phase)
    }

    private var puzzleStatusColor: Color {
        switch vm.phase {
        case .solved:    return DS.success
        case .wrongMove: return DS.danger
        case .playing:   return .secondary
        }
    }

    private var puzzleBorderColor: Color {
        switch vm.phase {
        case .solved:    return DS.success.opacity(0.6)
        case .wrongMove: return DS.danger.opacity(0.5)
        case .playing:   return vm.line.accentColor.opacity(0.3)
        }
    }
}

// MARK: - Opening Exercise Card

struct OpeningExerciseCard: View {

    let line: OpeningLine

    @State private var vm: OpeningExerciseViewModel

    init(line: OpeningLine) {
        self.line = line
        _vm = State(initialValue: OpeningExerciseViewModel(line: line))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            // Header
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(line.accentColor.opacity(0.2))
                        .frame(width: 32, height: 32)
                    Image(systemName: line.icon)
                        .font(.dsCaption.weight(.semibold))
                        .foregroundStyle(line.accentColor)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(Loc(line.name))
                        .font(.dsBody.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(Loc(line.hint))
                        .font(.dsCaption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Spacer(minLength: 0)
                if vm.phase == .solved {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(DS.success)
                        .font(.appFont(.title3))  // van skale — ikonica statusa, title3 nema DS ekvivalent
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)

            // Board
            BoardView(
                board:           vm.gameState.board,
                isFlipped:       false,
                selectedPosition: vm.selectedPosition,
                legalMoves:      vm.legalMovesForSelected,
                lastMove:        vm.lastMove,
                animatingPiece:  vm.animatingPiece,
                flyingCapture:   nil,
                playerColor:     .white,
                isPlayerTurn:    vm.isPlayerTurn,
                allowsStyleSwipe: false,
                onTap:           { vm.tap(position: $0) }
            )
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .padding(.horizontal, 10)

            // Status bar
            HStack(spacing: 6) {
                // Progress pills
                let whiteMoveCount = (line.uciMoves.count + 1) / 2
                HStack(spacing: 3) {
                    ForEach(0..<whiteMoveCount, id: \.self) { i in
                        let done = i * 2 < vm.movePointer
                        RoundedRectangle(cornerRadius: 2)
                            .fill(done ? line.accentColor : Color.primary.opacity(0.12))
                            .frame(width: 18, height: 4)
                    }
                }

                Text(vm.statusMessage)
                    .font(.dsCaption.weight(.medium))
                    .foregroundStyle(openingStatusColor)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)

                Spacer(minLength: 0)

                Button {
                    withAnimation(.easeInOut(duration: 0.15)) { vm.reset() }
                } label: {
                    Label("Ponovo", systemImage: "arrow.counterclockwise")
                        .font(.dsCaption.weight(.medium))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.primary.opacity(0.06), in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(openingBorderColor, lineWidth: 1)
        )
    }

    private var openingStatusColor: Color {
        switch vm.phase {
        case .solved:    return DS.success
        case .wrongMove: return DS.danger
        case .playing:   return .secondary
        }
    }

    private var openingBorderColor: Color {
        switch vm.phase {
        case .solved:    return DS.success.opacity(0.5)
        case .wrongMove: return DS.danger.opacity(0.4)
        case .playing:   return line.accentColor.opacity(0.3)
        }
    }
}

// MARK: - Mate Exercise Card

struct MateExerciseCard: View {

    let fen:   String
    let title: String
    let hint:  String
    let icon:  String
    let color: Color

    @State private var vm: MateExerciseViewModel

    init(fen: String, title: String, hint: String, icon: String, color: Color) {
        self.fen   = fen
        self.title = title
        self.hint  = hint
        self.icon  = icon
        self.color = color
        _vm = State(initialValue: MateExerciseViewModel(fen: fen, title: title, hint: hint))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            // Header
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(color.opacity(0.2))
                        .frame(width: 32, height: 32)
                    Image(systemName: icon)
                        .font(.dsCaption.weight(.semibold))
                        .foregroundStyle(color)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(Loc(title))
                        .font(.dsBody.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(Loc(hint))
                        .font(.dsCaption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Spacer(minLength: 0)
                if vm.isSolved {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(DS.success)
                        .font(.appFont(.title3))  // van skale — ikonica statusa, title3 nema DS ekvivalent
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)

            // Board
            BoardView(
                board:          vm.gameState.board,
                isFlipped:      false,
                selectedPosition: vm.selectedPosition,
                legalMoves:     vm.legalMovesForSelected,
                lastMove:       vm.lastMove,
                animatingPiece: vm.animatingPiece,
                flyingCapture:  nil,
                playerColor:    .white,
                isPlayerTurn:   vm.isPlayerTurn,
                allowsStyleSwipe: false,
                onTap:          { vm.tap(position: $0) }
            )
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .padding(.horizontal, 10)

            // Status bar
            HStack(spacing: 6) {
                if vm.isThinking {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .scaleEffect(0.7)
                        .tint(color)
                }
                Text(vm.statusMessage)
                    .font(.dsCaption.weight(.medium))
                    .foregroundStyle(statusColor)
                Spacer(minLength: 0)
                Button {
                    withAnimation(.easeInOut(duration: 0.15)) { vm.reset() }
                } label: {
                    Label("Ponovo", systemImage: "arrow.counterclockwise")
                        .font(.dsCaption.weight(.medium))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.primary.opacity(0.06), in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(borderColor, lineWidth: 1)
        )
    }

    private var statusColor: Color {
        if vm.isSolved { return DS.success }
        if case .draw = vm.gameState.status { return DS.warning }
        if case .checkmate(let c) = vm.gameState.status, c == .white { return DS.danger }
        if case .check = vm.gameState.status { return DS.warning }
        return .secondary
    }

    private var borderColor: Color {
        if vm.isSolved { return DS.success.opacity(0.5) }
        return color.opacity(0.3)
    }
}
