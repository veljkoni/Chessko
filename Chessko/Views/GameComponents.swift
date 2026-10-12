import SwiftUI

// MARK: - Zajedničke komponente (dizajnerski pregled, spec § 18 korak 4)
//
// Jedno mesto za komponente koje dele Igra (§ 5), Zadaci i koraci Puta (§ 7)
// i Analiza (§ 6). Android ima iste u
// `ChesskoAndroid/.../ui/GameComponents.kt` — ista imena uloga, iste vrednosti.
//
//  - `DSPlayerCard`        kartica igrača / protivnika
//  - `DSInstructionBox`    okvir uputstva (fill, bez ivice, sijalica)
//  - `GameActionBar`       Vrati · Predaj · Više
//  - `MoveClassBadge`      oznaka klase poteza (★ ! ✓ ?! ? ??)
//  - `DSPrimaryButtonStyle` / `DSSecondaryButtonStyle` / `DSTextButtonStyle`
//  - `DS.selectionTint`    accent 12 % (svetla) / 16 % (tamna) — izabran potez/red
//
// Lista poteza je `MoveHistoryView` (isti fajl kao i ranije).

// MARK: - Izvedena boja izbora

extension DS {
    /// Izabran red / potez: `accent` 12 % (svetla) / 16 % (tamna) preko `surface`
    /// (spec § 2 „Izvedene boje" — nije nov token, računa se iz `accent`-a).
    /// Tekst na njoj je `accent` (7,02 / 4,87).
    static let selectionTint = Color(UIColor { traits in
        let isDark = traits.userInterfaceStyle == .dark
        return UIColor(DS.accent).resolvedColor(with: traits)
            .withAlphaComponent(isDark ? 0.16 : 0.12)
    })
}

// MARK: - Dugmad

/// Primarno dugme: `accent` + `onAccent`, `Radius.m`, visina 48 (spec § 3).
struct DSPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.dsBody.weight(.semibold))
            .foregroundStyle(DS.onAccent)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 48)
            .background(DS.accent, in: RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// Sporedno dugme sa ispunom: `fill` + `ink` u obe teme (spec § 3).
struct DSSecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.dsBody.weight(.semibold))
            .foregroundStyle(DS.ink)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 48)
            .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// Sporedna radnja kao tekst-dugme u `accent` (8,53 / 6,43). „Zatvori"/„Otkaži"
/// nikad u `inkMuted` (spec § 3).
struct DSTextButtonStyle: ButtonStyle {
    var color: Color = DS.accent
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.dsBody.weight(.semibold))
            .foregroundStyle(color)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// Dugme bez sistemskog zatamnjenja u onemogućenom stanju — nedostupnost se
/// crta ručno (38 %, spec § 5), pa se ne sme sabrati sa sistemskom.
struct DSPressableButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.7 : 1)
    }
}

// MARK: - Kartica igrača

/// Status u kartici igrača (spec § 5). Poseban red „Tvoj potez" ne postoji —
/// stanje partije živi ovde.
enum PlayerCardStatus: Equatable {
    /// „Na potezu" (`Type.caption` medium, `ink`) + tačka `success` (5,04 / 8,78).
    /// `inCheck`: strana na potezu je u šahu → „Na potezu · Šah" (šah u `danger`),
    /// deo labele kartice za čitač ekrana.
    case toMove(inCheck: Bool)
    /// Računar razmišlja — tri tačke.
    case thinking
    /// Kraj partije (mat, predaja, remi) — `Type.body`, `ink`.
    case result(String)
}

/// Procena pozicije u kartici igrača (spec § 5) — logika je u `EvaluationLabel`
/// (`Logic/LocalFormat.swift`, pokrivena `swift test`-om); ovde samo jezik aplikacije.
typealias DSEvaluation = EvaluationLabel

extension EvaluationLabel {
    @MainActor static func make(score: Double, mateIn: Int?) -> EvaluationLabel {
        make(score: score, mateIn: mateIn, languageCode: LocalizationManager.shared.effectiveCode)
    }
}

/// Kartica igrača / protivnika (spec § 5): `surface` + ivica (`line` 1 pt,
/// aktivna `accent` 2 pt — 7,69 / 7,03), avatar kralja, ime, opcioni drugi red
/// (nivo protivnika — ako ga nema, red se NE prikazuje i ime je vertikalno
/// centrirano; nikad „–"), status, uzete figure, procena desno i strelica kad
/// se karta tapa.
struct DSPlayerCard: View {
    let color: PieceColor
    let name: String
    var subtitle: String? = nil
    var status: PlayerCardStatus? = nil
    var isActive: Bool = false
    var materialAdvantage: Int = 0
    var capturedPieces: [ChessPiece] = []
    var flyingCapture: FlyingCapture? = nil
    var evaluation: DSEvaluation? = nil
    var showsChevron: Bool = false

    var body: some View {
        HStack(alignment: .center, spacing: DS.Space.m) {
            ZStack {
                RoundedRectangle(cornerRadius: DS.Radius.s, style: .continuous)
                    .fill(DS.fill)
                    .frame(width: 38, height: 38)
                PieceImageView(piece: ChessPiece(type: .king, color: color))
                    .frame(width: 26, height: 26)
            }
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(name)
                        .font(.dsBody.weight(.semibold))
                        .foregroundStyle(DS.ink)
                        .lineLimit(1)
                    if materialAdvantage > 0 {
                        Text("+\(materialAdvantage)")
                            .font(.dsCaption.weight(.medium))
                            .foregroundStyle(DS.inkMuted)
                    }
                }
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.dsCaption)
                        .foregroundStyle(DS.inkMuted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                }
                statusView
                if !capturedPieces.isEmpty {
                    CapturedPiecesView(
                        pieces: capturedPieces,
                        capturedByColor: color,
                        flyingCapture: flyingCapture
                    )
                }
            }

            Spacer(minLength: DS.Space.s)

            if let evaluation {
                EvaluationChip(evaluation: evaluation)
            }

            if showsChevron {
                Image(systemName: "chevron.down")
                    .font(.dsCaption.weight(.semibold))
                    .foregroundStyle(DS.inkMuted)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, DS.Space.m)
        .padding(.vertical, DS.Space.s)
        .frame(minHeight: 56)
        .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous)
                .strokeBorder(isActive ? DS.accent : DS.line, lineWidth: isActive ? 2 : 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
        .animation(.easeInOut(duration: 0.2), value: isActive)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var statusView: some View {
        switch status {
        case .toMove(let inCheck):
            HStack(spacing: 6) {
                Circle()
                    .fill(DS.success)
                    .frame(width: 7, height: 7)
                    .accessibilityHidden(true)
                Text(Loc("Na potezu"))
                    .font(.dsCaption.weight(.medium))
                    .foregroundStyle(DS.ink)
                if inCheck {
                    Text("· " + Loc("Šah"))
                        .font(.dsCaption.weight(.semibold))
                        .foregroundStyle(DS.danger)
                }
            }
        case .thinking:
            ThinkingIndicator(isActive: true)
                .padding(.vertical, 3)
                .accessibilityLabel(Loc("Računar razmišlja..."))
        case .result(let text):
            Text(text)
                .font(.dsBody)
                .foregroundStyle(DS.ink)
                .fixedSize(horizontal: false, vertical: true)
        case nil:
            EmptyView()
        }
    }
}

/// Procena u kartici: `fill` + ivica `line` + `ink`; mat nosi zlatnu oznaku
/// (`#FFD700`) na fiksnoj tamnoj podlozi `#1E293B` sa ivicom `white@18%` —
/// preslikan Android fiksni element (§ 0 „zlatna oznaka mata", `GameComponents.kt`
/// `EvaluationChip`), pa su fiksne hex vrednosti ovde dozvoljene.
private struct EvaluationChip: View {
    let evaluation: DSEvaluation

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        Text(evaluation.text)
            .font(.dsCaption.weight(.bold))
            .monospacedDigit()
            .foregroundStyle(evaluation.isMate ? Color(hex: "#FFD700") : DS.ink)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(evaluation.isMate ? Color(hex: "#1E293B") : DS.fill, in: shape)
            .overlay(shape.strokeBorder(evaluation.isMate ? Color.white.opacity(0.18) : DS.line, lineWidth: 1))
    }
}

// MARK: - Okvir uputstva

/// Ton okvira uputstva. Okvir ostaje isti (`fill`, tekst `ink`) — menja se samo
/// ikona i njena boja, da ishod (greška / rešeno / rešenje) bude vidljiv a da
/// okvir ne počne da liči na dugme (spec § 3 „status nikad ne izgleda kao dugme").
/// Ikone na `fill`: `inkMuted` 4,86 / 4,81, `danger` 5,43 / 5,93, `success`
/// 4,18 / 7,78 (≥ 3 : 1 za ikone), `warning` 4,75 / 7,51.
/// `problem` = zadatak/korak nije dostupan (Android tu crta zaseban ekran greške
/// sa istom `Warning` ikonom u `warning`).
enum DSInstructionTone: Equatable {
    case hint, wrong, solved, solution, problem

    var systemImage: String {
        switch self {
        case .hint:     return "lightbulb"
        case .wrong:    return "xmark.circle.fill"
        case .solved:   return "checkmark.seal.fill"
        case .solution: return "eye.fill"
        case .problem:  return "exclamationmark.triangle.fill"
        }
    }

    var iconColor: Color {
        switch self {
        case .hint:     return DS.inkMuted
        case .wrong:    return DS.danger
        case .solved:   return DS.success
        case .solution, .problem: return DS.warning
        }
    }
}

/// Okvir uputstva (spec § 7): `fill`, bez ivice, `Radius.m`; sijalica `inkMuted`
/// (4,86 / 4,81); tekst `Type.body` `ink` (14,47 / 13,08); uz krug strane na
/// potezu. Status — ne dugme i ne polje za unos.
struct DSInstructionBox: View {
    let text: String
    var sideToMove: PieceColor? = nil
    var tone: DSInstructionTone = .hint

    var body: some View {
        HStack(alignment: .top, spacing: DS.Space.s) {
            Image(systemName: tone.systemImage)
                .font(.dsBody)
                .foregroundStyle(tone.iconColor)
                .accessibilityHidden(true)
            Text(text)
                .font(.dsBody)
                .foregroundStyle(DS.ink)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .animation(.default, value: text)
            if let side = sideToMove {
                Circle()
                    .fill(side == .white ? Color.white : Color.black)
                    .frame(width: 14, height: 14)
                    .overlay(Circle().strokeBorder(DS.inkMuted, lineWidth: 1))
                    .padding(.top, 2)
                    .accessibilityLabel(side.srbAdjective.capitalized + ", " + Loc("Na potezu"))
            }
        }
        .padding(DS.Space.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
    }
}

// MARK: - Akciona traka

/// Jedno dugme akcione trake: `fill`, `Radius.m`, visina 48. Na uskom ekranu
/// (< 360 pt) ikona iznad labele (`Type.label`). Visina je najmanja, ne fiksna
/// (spec § 16: veći sistemski font ili viši glifovi hindija ne smeju da se seku).
struct DSActionButtonLabel: View {
    let systemImage: String
    let title: String
    var tint: Color = DS.ink
    var compact: Bool = false

    var body: some View {
        Group {
            if compact {
                VStack(spacing: 2) {
                    Image(systemName: systemImage)
                        .font(.dsBody.weight(.semibold))
                    Text(title)
                        .font(.dsLabel.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            } else {
                HStack(spacing: 6) {
                    Image(systemName: systemImage)
                        .font(.dsBody.weight(.semibold))
                    Text(title)
                        .font(.dsBody.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }
        }
        .foregroundStyle(tint)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 48)
        .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
    }
}

/// Akcije ispod table (spec § 5): Vrati · Predaj · Više. „Predaj" u `danger`
/// (na fill 5,43 / 5,93). Nedostupna dugmad ostaju na mestu sa 38 %
/// providnosti. Sadržaj menija „Više" daje ekran (Igra: Podeli, Nova partija,
/// Analiza).
struct GameActionBar<MoreMenu: View>: View {
    let canUndo: Bool
    let canResign: Bool
    var compact: Bool = false
    let onUndo: () -> Void
    let onResign: () -> Void
    @ViewBuilder let moreMenu: () -> MoreMenu

    var body: some View {
        HStack(spacing: DS.Space.s) {
            Button(action: onUndo) {
                DSActionButtonLabel(systemImage: "arrow.uturn.backward", title: Loc("Vrati"), compact: compact)
            }
            .buttonStyle(DSPressableButtonStyle())
            .disabled(!canUndo)
            .opacity(canUndo ? 1 : 0.38)

            Button(action: onResign) {
                DSActionButtonLabel(systemImage: "flag", title: Loc("Predaj"), tint: DS.danger, compact: compact)
            }
            .buttonStyle(DSPressableButtonStyle())
            .disabled(!canResign)
            .opacity(canResign ? 1 : 0.38)

            Menu {
                moreMenu()
            } label: {
                DSActionButtonLabel(systemImage: "ellipsis", title: Loc("Više"), compact: compact)
            }
            .buttonStyle(DSPressableButtonStyle())
        }
    }
}

// MARK: - Oznaka klase poteza

extension MoveClass {
    /// Simbol klase (spec § 6): ★ ! ✓ ?! ? ??
    var symbol: String {
        switch self {
        case .best:       return "\u{2605}"
        case .excellent:  return "!"
        case .good:       return "\u{2713}"
        case .inaccuracy: return "?!"
        case .mistake:    return "?"
        case .blunder:    return "??"
        }
    }

    /// Boja = porodica. `accent` se NE koristi za klasu — u istoj listi znači
    /// „izabran potez".
    var badgeColor: Color {
        switch self {
        case .best, .excellent:     return DS.success
        case .good:                 return DS.inkMuted
        case .inaccuracy, .mistake: return DS.warning
        case .blunder:              return DS.danger
        }
    }

    /// Ispuna = klasa: puna (najbolji, greška, promašaj) ili obris.
    var badgeFilled: Bool {
        switch self {
        case .best, .mistake, .blunder: return true
        default:                        return false
        }
    }

    /// Naziv klase za čitač ekrana („Promašaj, 2. g4").
    var accessibilityName: String {
        let raw: String
        switch self {
        case .best:       raw = Loc("najbolji")
        case .excellent:  raw = Loc("odličan")
        case .good:       raw = Loc("dobar")
        case .inaccuracy: raw = Loc("netačnost")
        case .mistake:    raw = Loc("greška")
        case .blunder:    raw = Loc("promašaj")
        }
        return raw.prefix(1).uppercased() + raw.dropFirst()
    }
}

/// Oznaka klase poteza (spec § 6): `Radius.s`, visina 22, `Type.caption` bold.
/// Puna oznaka: `surface` na boji klase (5,04 / 8,78 za `success`); obris: boja
/// klase na providnom. Upotreba u Analizi je Task 6.
struct MoveClassBadge: View {
    let moveClass: MoveClass

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: DS.Radius.s, style: .continuous)
        Text(moveClass.symbol)
            .font(.dsCaption.weight(.bold))
            .foregroundStyle(moveClass.badgeFilled ? DS.surface : moveClass.badgeColor)
            .padding(.horizontal, 5)
            .frame(minWidth: 22, minHeight: 22)
            .background(moveClass.badgeFilled ? moveClass.badgeColor : Color.clear, in: shape)
            .overlay(shape.strokeBorder(moveClass.badgeColor, lineWidth: moveClass.badgeFilled ? 0 : 1))
            .accessibilityLabel(moveClass.accessibilityName)
    }
}
