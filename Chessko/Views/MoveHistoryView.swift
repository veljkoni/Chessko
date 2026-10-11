import SwiftUI

// MARK: - Move History View
//
// Lista poteza (spec § 5, deli je i Analiza — § 6): kartica `surface` + `line`,
// mreža broj · beli · crni, red 36, `Type.mono` (`.dsMono`). Izabran potez je
// `accent` tekst na `DS.selectionTint` (accent 12 % / 16 %). Prazno stanje:
// „Potezi će se pojaviti ovde" (`Type.body`, `inkMuted`).
//
// Visinu određuje pozivalac (`.frame(height:)` / `maxHeight`) — u Igri lista
// rezerviše ostatak visine ispod table. Auto-skrol do poslednjeg ili izabranog
// poteza. Android: `ui/MoveHistoryView.kt`, ista pravila.

struct MoveHistoryView: View {

    /// Notacije u redosledu igranja: [beli1, crni1, beli2, crni2, ...]
    let notations: [String]
    /// Izabrana pozicija kao broj odigranih poluteza (1 = posle prvog belog
    /// poteza); `nil` = živa partija.
    var selectedMoveIndex: Int? = nil
    /// U živoj partiji (`selectedMoveIndex == nil`) istakni poslednji potez.
    var highlightsLastMoveWhenLive: Bool = true
    /// Opciona klasa poteza po polutezu (od 0) — oznaka ispred notacije (Analiza).
    var moveClass: ((Int) -> MoveClass?)? = nil
    var onSelectMove: ((Int) -> Void)? = nil

    static let rowHeight: CGFloat = 36

    private struct MoveRow: Identifiable {
        let id: Int          // 0-based broj reda
        var number: Int { id + 1 }
        let whitePly: Int
        let blackPly: Int?
    }

    private var rows: [MoveRow] {
        stride(from: 0, to: notations.count, by: 2).map { i in
            MoveRow(id: i / 2, whitePly: i, blackPly: i + 1 < notations.count ? i + 1 : nil)
        }
    }

    private func isSelected(_ ply: Int) -> Bool {
        if let sel = selectedMoveIndex { return sel == ply + 1 }
        return highlightsLastMoveWhenLive && ply == notations.count - 1
    }

    var body: some View {
        Group {
            if notations.isEmpty {
                Text(Loc("Potezi će se pojaviti ovde"))
                    .font(.dsBody)
                    .foregroundStyle(DS.inkMuted)
                    .multilineTextAlignment(.center)
                    .padding(DS.Space.l)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(rows) { row in
                                rowView(row).id(row.id)
                            }
                        }
                        .padding(.vertical, DS.Space.xs)
                    }
                    .scrollBounceBehavior(.basedOnSize)
                    .onAppear { scrollToCurrent(proxy, animated: false) }
                    .onChange(of: notations.count) { _, _ in scrollToCurrent(proxy, animated: true) }
                    .onChange(of: selectedMoveIndex) { _, _ in scrollToCurrent(proxy, animated: true) }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .dsCard()
    }

    private func scrollToCurrent(_ proxy: ScrollViewProxy, animated: Bool) {
        let ply: Int
        if let sel = selectedMoveIndex, sel > 0 { ply = sel - 1 } else { ply = notations.count - 1 }
        guard ply >= 0 else { return }
        let target = ply / 2
        if animated {
            withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(target, anchor: .center) }
        } else {
            proxy.scrollTo(target, anchor: .center)
        }
    }

    @ViewBuilder
    private func rowView(_ row: MoveRow) -> some View {
        HStack(spacing: DS.Space.xs) {
            Text("\(row.number).")
                .font(.dsMono)
                .foregroundStyle(DS.inkMuted)
                .frame(width: 36, alignment: .leading)
                .accessibilityHidden(true)

            moveCell(ply: row.whitePly, number: row.number, isWhite: true)
                .frame(maxWidth: .infinity, alignment: .leading)

            Group {
                if let black = row.blackPly {
                    moveCell(ply: black, number: row.number, isWhite: false)
                } else {
                    Color.clear.frame(height: 1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, DS.Space.m)
        .frame(height: Self.rowHeight)
    }

    @ViewBuilder
    private func moveCell(ply: Int, number: Int, isWhite: Bool) -> some View {
        let selected = isSelected(ply)
        let notation = notations[ply]
        let cls = moveClass?(ply)
        let moveLabel = isWhite ? "\(number). \(notation)" : "\(number)… \(notation)"

        Button {
            onSelectMove?(ply + 1)
        } label: {
            HStack(spacing: 6) {
                if let cls {
                    MoveClassBadge(moveClass: cls)
                }
                Text(notation)
                    .font(.dsMono)
                    .fontWeight(selected ? .semibold : .regular)
                    .foregroundStyle(selected ? DS.accent : DS.ink)
                    .lineLimit(1)
            }
            .padding(.horizontal, 6)
            .frame(height: 28)
            .background(selected ? DS.selectionTint : Color.clear,
                        in: RoundedRectangle(cornerRadius: DS.Radius.s, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(DSPressableButtonStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(cls.map { "\($0.accessibilityName), \(moveLabel)" } ?? moveLabel)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
