import SwiftUI

// MARK: - Lesson Renderer
//
// Jedino mesto koje zna kako se blok sadržaja crta. Nova lekcija je nov JSON;
// nov tip bloka je jedna grana ovde plus jedan slučaj u `LessonBlock`.
//
// Dizajnerski pregled, spec § 9 (Task 8): hijerarhija (naslov lekcije `title`,
// sekcija `heading` bold `ink` + ikona `inkMuted`, pod-blok `body` medium, telo
// `body` sa proredom 1,5 u `ink`, meta `caption` `inkMuted`), kutija pravila
// `warning` 10 % preko `surface` bez ivice, i ritam: razmak IZMEĐU blokova se
// računa na jednom mestu (`gap(before:after:)`), ne padding-om po komponenti.
// Android: `ui/LessonRenderer.kt` (`LessonBlocks`, `gapBefore`) i `ui/LearnView.kt`.

/// Telo lekcije: `.dsBody` (15) sa proredom 1,5 → visina reda ~22,5. Prirodna
/// visina reda SF-a na 15 pt je ~18, pa je dodatak ~4,5 (Android: `LessonBodyStyle`,
/// `lineHeight = 22.5.sp`).
let lessonBodyLineSpacing: CGFloat = 4.5

/// Gornja granica lekcijske table (spec § 9/§ 13). Vrednost računa
/// `LessonDetailView` zajedničkom funkcijom `BoardSizing.boardSide` i spušta je
/// kroz okruženje; podrazumevano je telefonska granica (560). Bez granice je
/// tabla na iPad-u išla do 1336 pt.
private struct LessonBoardMaxSideKey: EnvironmentKey {
    static let defaultValue: CGFloat = BoardSizing.phoneMaxSide
}

extension EnvironmentValues {
    var lessonBoardMaxSide: CGFloat {
        get { self[LessonBoardMaxSideKey.self] }
        set { self[LessonBoardMaxSideKey.self] = newValue }
    }
}

/// Primena granice — svaka lekcijska tabla je nosi.
private struct LessonBoardCap: ViewModifier {
    @Environment(\.lessonBoardMaxSide) private var maxSide
    func body(content: Content) -> some View {
        content.frame(maxWidth: maxSide)
    }
}

/// Kako se crtaju blokovi sa tablom. `.inline` — u toku teksta; `.markers` —
/// iPad u pejzažu (spec § 9): na mestu bloka stoji samo oznaka, a tabla se crta
/// u desnoj, lepljivoj koloni (`LessonDetailView`).
enum LessonBoardMode: Equatable {
    case inline
    case markers(activeIndex: Int?)
}

extension LessonBlock {
    /// Blok koji nosi tablu (vežba, istraživač, statična tabla).
    var carriesBoard: Bool {
        switch self {
        case .board, .explorer, .exercise: return true
        default: return false
        }
    }
}

/// Vertikalni položaj oznaka (po indeksu bloka) u koordinatnom prostoru skrola.
struct LessonMarkerYKey: PreferenceKey {
    static let defaultValue: [Int: CGFloat] = [:]
    static func reduce(value: inout [Int: CGFloat], nextValue: () -> [Int: CGFloat]) {
        value.merge(nextValue(), uniquingKeysWith: { $1 })
    }
}

struct LessonRenderer: View {
    let blocks: [LessonBlock]
    var boardMode: LessonBoardMode = .inline

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { index, block in
                view(for: block, at: index)
                    .padding(.top, index == 0 ? 0 : Self.gap(before: blocks[index - 1], after: block))
            }
        }
    }

    /// Spec § 9 „Ritam": `Space.xl` iznad i ispod razdelnika, `Space.m` od naslova
    /// do teksta, inače `Space.l`.
    static func gap(before prev: LessonBlock, after cur: LessonBlock) -> CGFloat {
        if case .divider = cur { return DS.Space.xl }
        if case .divider = prev { return DS.Space.xl }
        if case .heading = prev { return DS.Space.m }
        return DS.Space.l
    }

    @ViewBuilder
    private func view(for block: LessonBlock, at index: Int) -> some View {
        if block.carriesBoard, case .markers(let active) = boardMode {
            LessonBoardMarker(block: block, isActive: active == index)
                .background(GeometryReader { g in
                    Color.clear.preference(key: LessonMarkerYKey.self,
                                           value: [index: g.frame(in: .named("lessonScroll")).minY])
                })
        } else {
            content(for: block)
        }
    }

    @ViewBuilder
    private func content(for block: LessonBlock) -> some View {
        switch block {
        case .heading(let text, let icon):
            L_SectionHeader(icon: icon, title: text)

        case .paragraph(let text):
            L_Para(text)

        case .bullets(let items):
            VStack(alignment: .leading, spacing: DS.Space.m) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    L_Bullet(icon: item.icon,
                             color: Self.bulletColor(item.style),
                             title: item.title, text: item.text)
                }
            }

        case .box(let style, let icon, let title, let text):
            L_Box(icon: icon, color: Self.boxColor(style), title: title, text: text)

        case .quote(let text, let author):
            L_Box(icon: "quote.opening", color: DS.accent, title: author, text: text)

        case .pieceRow(let piece, let name, let count):
            if let type = Self.pieceType(lessonKey: piece) {
                L_PieceRow(type: type, name: name, count: count)
            } else {
                LessonBlockError(message: "Nepoznata figura '\(piece)' u redu '\(name)'")
            }

        case .numberedRule(let number, let title, let text):
            L_NumberedRule(number: number, title: title, text: text)

        case .pieceValueTable(let rows):
            L_PieceValueTable(rows: rows)

        case .divider:
            Rectangle().fill(DS.line).frame(height: 1)

        case .board, .explorer, .exercise:
            LessonBoardBlock(block: block)
        }
    }

    /// Neutralna stavka nabrajanja je `inkMuted`, ne `accent` (spec § 3: accent
    /// samo za ono što se tapa). Pravilo/upozorenje nose značenje.
    static func bulletColor(_ style: BoxStyle?) -> Color {
        switch style {
        case .rule:          return DS.warning
        case .warning:       return DS.danger
        case .info, .none:   return DS.inkMuted
        }
    }

    static func boxColor(_ style: BoxStyle) -> Color {
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

/// Blok sa tablom — isti crtež u toku teksta i u desnoj koloni na iPad-u.
struct LessonBoardBlock: View {
    let block: LessonBlock

    var body: some View {
        switch block {
        case .board(let fen, let caption, let interactive):
            // Interaktivna tabla u lekciji jos nije implementirana. Tiho crtanje
            // staticne bi dalo mrtvu tablu bez ijednog traga.
            if interactive {
                LessonBlockError(message: "Interaktivna tabla još nije podržana: '\(caption)'")
            } else {
                LessonStaticBoard(fen: fen, caption: caption)
            }
        case .explorer:
            LessonPieceExplorer()
        case .exercise(let spec):
            exerciseView(spec)
        default:
            EmptyView()
        }
    }

    @ViewBuilder
    private func exerciseView(_ spec: ExerciseSpec) -> some View {
        switch spec.kind {
        case .vsEngine:
            // `startFEN` je kod vsEngine obavezan; prazan string bi dao praznu
            // tablu, pa se blok preskače uz vidljivu poruku umesto tihe rupe.
            if let fen = spec.startFEN {
                MateExerciseCard(fen: fen, title: spec.title, hint: spec.hint, icon: spec.icon)
            } else {
                LessonBlockError(message: "Vežba '\(spec.title)' nema startFEN")
            }

        case .scripted:
            if let moves = spec.uciMoves {
                if let mateIn = spec.mateIn {
                    if let fen = spec.startFEN {
                        MatePuzzleCard(fen: fen, moves: moves, title: spec.title,
                                       hint: spec.hint, icon: spec.icon,
                                       accentColor: DS.accent, mateIn: mateIn,
                                       solvedMessage: spec.solvedMessage,
                                       wrongMessage: spec.wrongMessage,
                                       playingPrompt: spec.playingPrompt)
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
                }
            } else {
                LessonBlockError(message: "Vežba '\(spec.title)' nema uciMoves")
            }
        }
    }
}

/// Oznaka bloka sa tablom u levoj koloni (iPad pejzaž). Trenutna nosi izgled
/// izabranog reda (`selectionTint` preko `surface`, tekst `accent`).
struct LessonBoardMarker: View {
    let block: LessonBlock
    let isActive: Bool

    var body: some View {
        let (icon, title): (String, String) = {
            switch block {
            case .exercise(let spec):         return (spec.icon, spec.title)
            case .board(_, let caption, _):   return ("square.grid.3x3.fill", caption)
            default:                          return ("hand.point.up.left.fill", Loc("Istraživač figura"))
            }
        }()
        let tint = isActive ? DS.accent : DS.ink
        HStack(spacing: DS.Space.s) {
            LessonSymbolView(name: icon, color: tint, font: .dsBody)
            Text(title)
                .font(.dsBody.weight(.medium))
                .foregroundStyle(tint)
                .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "arrow.right")
                .font(.dsCaption.weight(.semibold))
                .foregroundStyle(isActive ? DS.accent : DS.inkMuted)
        }
        .padding(DS.Space.m)
        .dsCard()
        .overlay(
            RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous)
                .fill(isActive ? DS.selectionTint : Color.clear)
                .allowsHitTesting(false)
        )
    }
}

/// SF simbol ili tekstualni glif iz `LessonSymbol.resolve` (spec § 9).
struct LessonSymbolView: View {
    let name: String
    let color: Color
    let font: Font

    var body: some View {
        switch LessonSymbol.resolve(name) {
        case .sf(let sf):
            Image(systemName: sf).font(font).foregroundStyle(color)
        case .glyph(let g):
            Text(g).font(font).foregroundStyle(color)
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Static Board
// ─────────────────────────────────────────────────────────────────────────────

struct LessonStaticBoard: View {
    let fen: String
    let caption: String

    var body: some View {
        VStack(alignment: .leading, spacing: DS.Space.s) {
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
                .modifier(LessonBoardCap())
                .frame(maxWidth: .infinity)
            } else {
                LessonBlockError(message: "Neispravan FEN: \(fen)")
            }
            if !caption.isEmpty {
                Text(caption)
                    .font(.dsCaption)
                    .foregroundStyle(DS.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Piece Explorer
// ─────────────────────────────────────────────────────────────────────────────

/// Interaktivni „sandbox" iz lekcije 1. Spec § 9 „Čipovi figura": izabrani
/// `accent` + `onAccent` (8,53 / 6,71), ostali `fill` + `ink` (14,47 / 13,08) —
/// ranije sistemske sive (labele 3,0 ✕). Kartica čipova je obična kartica sa
/// `Space.m` unutra.
struct LessonPieceExplorer: View {

    /// Jedan `LearnViewModel` živi u `ContentView`-u i `PathView` ga ubacuje u
    /// okruženje (vidi PathView).
    @Environment(LearnViewModel.self) private var vm

    var body: some View {
        VStack(alignment: .leading, spacing: DS.Space.m) {
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
            .modifier(LessonBoardCap())
            .frame(maxWidth: .infinity)

            let scenarios = vm.availableScenarios
            if !scenarios.isEmpty {
                VStack(alignment: .leading, spacing: DS.Space.s) {
                    HStack(spacing: DS.Space.s) {
                        Text(Loc("Specijalna pravila"))
                            .font(.dsBody.weight(.semibold))
                            .foregroundStyle(DS.ink)
                        Text(Loc("— izaberi i istraži na tabli"))
                            .font(.dsCaption)
                            .foregroundStyle(DS.inkMuted)
                    }
                    HStack(spacing: DS.Space.s) {
                        ForEach(scenarios, id: \.self) { s in
                            let active = vm.activeScenario == s
                            Button {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    vm.toggleScenario(s)
                                }
                            } label: {
                                Text(s.label)
                                    .font(.dsCaption.weight(.bold))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)
                                    .foregroundStyle(active ? DS.onAccent : DS.ink)
                                    .frame(maxWidth: .infinity)
                                    .frame(minHeight: 40)
                                    .background(active ? DS.accent : DS.fill,
                                                in: RoundedRectangle(cornerRadius: DS.Radius.s))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            HStack(spacing: DS.Space.xs) {
                Image(systemName: "hand.point.up.left").font(.dsLabel)
                Text(Loc("Tapni figuru da je promeniš · Tapni polje da je premestiš")).font(.dsCaption)
            }
            .foregroundStyle(DS.inkMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var piecePicker: some View {
        let pieces: [PieceType] = [.pawn, .knight, .bishop, .rook, .queen, .king]
        let columns = Array(repeating: GridItem(.flexible(), spacing: DS.Space.s), count: 3)
        return LazyVGrid(columns: columns, spacing: DS.Space.s) {
            ForEach(pieces, id: \.self) { piece in
                let sel = vm.selectedPieceType == piece
                Button {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        vm.select(piece: piece)
                    }
                } label: {
                    HStack(spacing: 6) {
                        PieceImageView(piece: ChessPiece(type: piece, color: .white))
                            .frame(width: 20, height: 20)
                        Text(piece.srbName)
                            .font(.dsCaption.weight(.bold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .foregroundStyle(sel ? DS.onAccent : DS.ink)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, DS.Space.s)
                    .background(sel ? DS.accent : DS.fill,
                                in: RoundedRectangle(cornerRadius: DS.Radius.s))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(DS.Space.m)
        .dsCard()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Reusable Lesson Components
// ─────────────────────────────────────────────────────────────────────────────

/// Naslov sekcije (spec § 9): `heading` 17 bold `ink` + ikona/glif `inkMuted`
/// (5,28 / 5,94). Nije `accent` — izgledao je kao link.
struct L_SectionHeader: View {
    let icon: String
    let title: String

    var body: some View {
        HStack(spacing: DS.Space.s) {
            LessonSymbolView(name: icon, color: DS.inkMuted, font: .dsHeading)
            Text(title)
                .font(.dsSectionHeading)
                .foregroundStyle(DS.ink)
        }
    }
}

/// Parsira inline Markdown (**podebljano**, *kurziv*) u tekstu koji iz JSON-a
/// stize vec na izabranom jeziku. `Text(String)` ne parsira Markdown — parsira
/// ga samo `Text(LocalizedStringKey)`, sto bi ovde znacilo trazenje sadrzaja
/// lekcije po katalogu prevoda, a on od Faze 3 nosi samo interfejs.
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

/// Telo lekcije: `.dsBody`, prored 1,5, `ink` (spec § 9).
fileprivate struct LessonBodyText: View {
    let text: String
    var body: some View {
        mdText(text)
            .font(.dsBody)
            .lineSpacing(lessonBodyLineSpacing)
            .foregroundStyle(DS.ink)
            .fixedSize(horizontal: false, vertical: true)
    }
}

struct L_Para: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        LessonBodyText(text: text)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Stavka nabrajanja: naslov je pod-blok (`body` medium `ink`), telo je telo lekcije.
struct L_Bullet: View {
    let icon: String
    let color: Color
    let title: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: DS.Space.m) {
            LessonSymbolView(name: icon, color: color, font: .dsBody)
                .frame(width: 20)
                .padding(.top, 1)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.dsBody.weight(.medium))
                    .foregroundStyle(DS.ink)
                LessonBodyText(text: text)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Kutija (pravilo, upozorenje, info, citat) — spec § 9: boja kutije 10 % preko
/// `surface`, `Radius.m`, bez ivice; naslov `body` bold `ink` (bio 12 u boji
/// kutije), ikona u boji kutije (pravilo: `warning` 4,99 / 7,05). Uslovi
/// razdvojeni sa „ · " (rokada) se crtaju kao lista sa tačkama.
struct L_Box: View {
    let icon: String
    let color: Color
    let title: String
    let text: String

    var body: some View {
        let items = LessonLabels.boxItems(text)
        VStack(alignment: .leading, spacing: DS.Space.s) {
            HStack(spacing: DS.Space.s) {
                LessonSymbolView(name: icon, color: color, font: .dsBody.weight(.semibold))
                Text(title)
                    .font(.dsBody.weight(.bold))
                    .foregroundStyle(DS.ink)
            }
            if items.count > 1 {
                VStack(alignment: .leading, spacing: DS.Space.xs) {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                        HStack(alignment: .firstTextBaseline, spacing: DS.Space.s) {
                            Text("•").font(.dsBody).foregroundStyle(DS.ink)
                            LessonBodyText(text: item)
                        }
                    }
                }
            } else {
                LessonBodyText(text: text)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(DS.Space.m)
        .background(
            RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous)
                .fill(DS.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous)
                        .fill(color.opacity(0.1))
                )
        )
    }
}

/// Red figure: naziv je naslov pod-bloka (`heading` bold `ink`), količina je
/// meta na `fill` čipu — „× 2" iz JSON-a se prikazuje kao „2 po strani" (spec § 9).
struct L_PieceRow: View {
    let type: PieceType
    let name: String
    let count: String

    var body: some View {
        HStack(spacing: DS.Space.m) {
            PieceImageView(piece: ChessPiece(type: type, color: .white))
                .frame(width: 32, height: 32)
            Text(name)
                .font(.dsSectionHeading)
                .foregroundStyle(DS.ink)
            Spacer()
            Text(LessonLabels.perSideCount(count).map { LocF("%lld po strani", $0) } ?? count)
                .font(.dsCaption.weight(.medium))
                .foregroundStyle(DS.inkMuted)
                .padding(.horizontal, DS.Space.s).padding(.vertical, 3)
                .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.s))
        }
    }
}

/// Numerisano pravilo: broj na `fill` pločici u `ink` (statičan broj nije `accent`).
struct L_NumberedRule: View {
    let number: Int
    let title: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: DS.Space.m) {
            Text("\(number)")
                .font(.dsBody.weight(.bold))
                .foregroundStyle(DS.ink)
                .frame(width: 28, height: 28)
                .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.s))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.dsBody.weight(.medium))
                    .foregroundStyle(DS.ink)
                LessonBodyText(text: text)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Tabela vrednosti (spec § 9): zaglavlje, redovi 44 sa `line` razdelnikom,
/// vrednost `ink` medium desno, tabelarne cifre, ne `accent`. Kraljevo „∞" je
/// „neprocenjiv". Oznake bodova („1 bod" / „3 boda" / „9 bodova") su lekcijski
/// tekst koji je OSTAO u katalogu prevoda.
struct L_PieceValueTable: View {
    let rows: [PieceValueRow]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(Loc("Figura"))
                Spacer()
                Text(Loc("Vrednost"))
            }
            .font(.dsCaption.weight(.medium))
            .foregroundStyle(DS.inkMuted)
            .padding(.horizontal, DS.Space.m)
            .padding(.vertical, DS.Space.s)

            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                Rectangle().fill(DS.line).frame(height: 1)
                HStack(spacing: DS.Space.m) {
                    // Nepoznat kljuc daje vidljivu poruku umesto tiho nacrtanog Kralja.
                    if let type = LessonRenderer.pieceType(lessonKey: row.piece) {
                        PieceImageView(piece: ChessPiece(type: type, color: .white))
                            .frame(width: 28, height: 28)
                    } else {
                        Image(systemName: "questionmark.square.dashed")
                            .frame(width: 28, height: 28)
                            .foregroundStyle(DS.danger)
                    }
                    Text(row.name)
                        .font(.dsBody)
                        .foregroundStyle(DS.ink)
                    Spacer()
                    Text(row.valueLabel ?? Self.valueLabel(row.value))
                        .font(.dsBody.weight(.medium).monospacedDigit())
                        .foregroundStyle(DS.ink)
                }
                .frame(minHeight: 44)
                .padding(.horizontal, DS.Space.m)
            }
        }
        .frame(maxWidth: .infinity)
        .dsCard()
    }

    /// `valueLabel` je vec preveden (dolazi iz JSON-a), ali ga nijedna isporucena
    /// lekcija ne zadaje. Fallback radi za 1/3/5/9/∞.
    /// Ključevi su LITERALI (kao Android `valueUnitLabel`), da ih test kataloga vidi.
    static func valueLabel(_ value: String) -> String {
        switch value {
        case "1": return Loc("1 bod")
        case "3": return Loc("3 boda")
        case "5": return Loc("5 boda")
        case "9": return Loc("9 bodova")
        case "∞": return Loc("neprocenjiv")
        default:  return value
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Kartice vežbi (spec § 9, § 3)
//
// Kartica = `surface` + 1 pt ivica, `Radius.m`, `Space.m` unutra (bile
// `primary.opacity(0.04)` + ivica u akcentu). Ivica je `line` dok se igra,
// `success`/`danger` posle rešenja/greške. Pločica ikone `fill` + `ink` glif;
// oznaka „Mat u N" `fill` čip; status „u toku" (npr. „Na potezu si!") `ink`.
// ─────────────────────────────────────────────────────────────────────────────

private struct ExerciseHeader: View {
    let icon: String
    let title: String
    let hint: String
    let solved: Bool
    var badge: String? = nil
    var solvedIcon: String = "checkmark.circle.fill"

    var body: some View {
        HStack(spacing: DS.Space.s) {
            LessonSymbolView(name: icon, color: DS.ink, font: .dsBody)
                .frame(width: 32, height: 32)
                .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.s))
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(title)
                        .font(.dsBody.weight(.semibold))
                        .foregroundStyle(DS.ink)
                    if let badge {
                        Text(badge)
                            .font(.dsLabel.weight(.bold))
                            .foregroundStyle(DS.ink)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.s))
                    }
                }
                Text(hint)
                    .font(.dsCaption)
                    .foregroundStyle(DS.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if solved {
                Image(systemName: solvedIcon)
                    .foregroundStyle(DS.success)
                    .font(.dsHeading)
                    .accessibilityLabel(Loc("Rešeno"))
                    .transition(.scale.combined(with: .opacity))
            }
        }
    }
}

private struct ExerciseFooter<Leading: View>: View {
    let status: String
    let statusColor: Color
    let onReset: () -> Void
    @ViewBuilder var leading: Leading

    var body: some View {
        HStack(spacing: DS.Space.s) {
            leading
            Text(status)
                .font(.dsCaption.weight(.medium))
                .foregroundStyle(statusColor)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                withAnimation(.easeInOut(duration: 0.15)) { onReset() }
            } label: {
                Label(Loc("Ponovo"), systemImage: "arrow.counterclockwise")
                    .font(.dsCaption.weight(.medium))
                    .foregroundStyle(DS.ink)
                    .padding(.horizontal, DS.Space.m).padding(.vertical, 6)
                    .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.s))
            }
            .buttonStyle(.plain)
        }
    }
}

private extension View {
    func exerciseCard(border: Color) -> some View {
        self
            .padding(DS.Space.m)
            .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous)
                    .strokeBorder(border, lineWidth: 1)
            )
    }

    /// Lekcijska tabla u kartici: privremena granica, centrirana.
    func lessonBoardFrame() -> some View {
        self
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .modifier(LessonBoardCap())
            .frame(maxWidth: .infinity)
    }
}

// MARK: - Mate Puzzle Card

struct MatePuzzleCard: View {

    let mateIn: Int         // 1 or 2 (for badge display)

    @State private var vm: OpeningExerciseViewModel

    /// Poruke su parametri, ne konstante: sadrzaj je od Faze 3 u JSON-u.
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
        VStack(alignment: .leading, spacing: DS.Space.m) {
            ExerciseHeader(icon: vm.line.icon, title: vm.line.name, hint: vm.line.hint,
                           solved: vm.phase == .solved,
                           badge: LocF("Mat u %lld", mateIn),
                           solvedIcon: "trophy.fill")

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
            .lessonBoardFrame()

            ExerciseFooter(status: vm.statusMessage, statusColor: statusColor,
                           onReset: { vm.reset() }) { EmptyView() }
        }
        .exerciseCard(border: borderColor)
        .animation(.easeInOut(duration: 0.2), value: vm.phase)
    }

    private var statusColor: Color {
        switch vm.phase {
        case .solved:    return DS.success
        case .wrongMove: return DS.danger
        case .playing:   return DS.ink
        }
    }

    private var borderColor: Color {
        switch vm.phase {
        case .solved:    return DS.success
        case .wrongMove: return DS.danger
        case .playing:   return DS.line
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
        VStack(alignment: .leading, spacing: DS.Space.m) {
            ExerciseHeader(icon: line.icon, title: line.name, hint: line.hint,
                           solved: vm.phase == .solved)

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
            .lessonBoardFrame()

            ExerciseFooter(status: vm.statusMessage, statusColor: statusColor,
                           onReset: { vm.reset() }) {
                // Pilule napretka: odigrano `success`, preostalo `fill`.
                let whiteMoveCount = (line.uciMoves.count + 1) / 2
                HStack(spacing: 3) {
                    ForEach(0..<whiteMoveCount, id: \.self) { i in
                        let done = i * 2 < vm.movePointer
                        RoundedRectangle(cornerRadius: 2)
                            .fill(done ? DS.success : DS.fill)
                            .frame(width: 18, height: 4)
                    }
                }
            }
        }
        .exerciseCard(border: borderColor)
    }

    private var statusColor: Color {
        switch vm.phase {
        case .solved:    return DS.success
        case .wrongMove: return DS.danger
        case .playing:   return DS.ink
        }
    }

    private var borderColor: Color {
        switch vm.phase {
        case .solved:    return DS.success
        case .wrongMove: return DS.danger
        case .playing:   return DS.line
        }
    }
}

// MARK: - Mate Exercise Card

struct MateExerciseCard: View {

    let fen:   String
    let title: String
    let hint:  String
    let icon:  String

    @State private var vm: MateExerciseViewModel

    init(fen: String, title: String, hint: String, icon: String) {
        self.fen   = fen
        self.title = title
        self.hint  = hint
        self.icon  = icon
        _vm = State(initialValue: MateExerciseViewModel(fen: fen, title: title, hint: hint))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: DS.Space.m) {
            ExerciseHeader(icon: icon, title: title, hint: hint, solved: vm.isSolved)

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
            .lessonBoardFrame()

            ExerciseFooter(status: vm.statusMessage, statusColor: statusColor,
                           onReset: { vm.reset() }) {
                if vm.isThinking {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .scaleEffect(0.7)
                        .tint(DS.inkMuted)
                }
            }
        }
        .exerciseCard(border: borderColor)
    }

    private var statusColor: Color {
        if vm.isSolved { return DS.success }
        if case .draw = vm.gameState.status { return DS.warning }
        if case .checkmate(let c) = vm.gameState.status, c == .white { return DS.danger }
        if case .check = vm.gameState.status { return DS.warning }
        // „Na potezu si!" je `ink` (spec § 9).
        return DS.ink
    }

    private var borderColor: Color {
        if vm.isSolved { return DS.success }
        if case .checkmate(let c) = vm.gameState.status, c == .white { return DS.danger }
        return DS.line
    }
}
