import SwiftUI

// MARK: - Move History View
//
// Prikazuje odigrane poteze u srpskoj algebarskoj notaciji (K/D/T/L/S).
// Potezi su grupisani u parove: beli potez | crni potez.
// Dva para po redu radi veće gustine. Auto-skrol do poslednjeg poteza.

struct MoveHistoryView: View {

    /// Notacije u redosledu igranja: [beli1, crni1, beli2, crni2, ...]
    let notations: [String]
    var selectedMoveIndex: Int? = nil
    var onSelectMove: ((Int) -> Void)? = nil

    // MARK: - Data model

    private struct MovePair: Identifiable {
        let id: Int
        let number: Int
        let white: String
        let whiteIndex: Int
        let black: String?
        let blackIndex: Int?
    }

    private struct PairRow: Identifiable {
        let id: Int
        let left: MovePair
        let right: MovePair?
    }

    private var pairs: [MovePair] {
        stride(from: 0, to: notations.count, by: 2).map { i in
            MovePair(
                id: i / 2,
                number: i / 2 + 1,
                white: notations[i],
                whiteIndex: i + 1,
                black: i + 1 < notations.count ? notations[i + 1] : nil,
                blackIndex: i + 1 < notations.count ? i + 2 : nil
            )
        }
    }

    private var pairRows: [PairRow] {
        stride(from: 0, to: pairs.count, by: 2).map { i in
            PairRow(id: i / 2, left: pairs[i], right: i + 1 < pairs.count ? pairs[i + 1] : nil)
        }
    }

    // MARK: - Body

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(pairRows) { row in
                        pairRowView(row: row)
                            .id(row.id)
                    }
                }
                .padding(.vertical, 3)
            }
            .frame(maxHeight: 150)
            .onChange(of: notations.count) { _, _ in
                if let last = pairRows.last {
                    withAnimation(.easeOut(duration: 0.2)) {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
            .onChange(of: selectedMoveIndex) { _, newIndex in
                if let idx = newIndex, idx > 0 {
                    let pairId = (idx - 1) / 4
                    withAnimation(.easeOut(duration: 0.2)) {
                        proxy.scrollTo(pairId, anchor: .center)
                    }
                }
            }
        }
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: - Row (two pairs side by side)

    @ViewBuilder
    private func pairRowView(row: PairRow) -> some View {
        HStack(spacing: 0) {
            pairCell(pair: row.left)

            if let right = row.right {
                Rectangle()
                    .fill(Color.primary.opacity(0.08))
                    .frame(width: 0.5)
                    .padding(.vertical, 4)
                pairCell(pair: right)
            } else {
                Spacer().frame(maxWidth: .infinity)
            }
        }
        .background(row.id % 2 == 0 ? Color.clear : Color.primary.opacity(0.04))
    }

    // MARK: - Single pair cell

    @ViewBuilder
    private func pairCell(pair: MovePair) -> some View {
        let whiteSelected = selectedMoveIndex == pair.whiteIndex
        let blackSelected = pair.blackIndex != nil && selectedMoveIndex == pair.blackIndex

        HStack(spacing: 0) {
            Text("\(pair.number).")
                .foregroundStyle(.secondary)
                .frame(width: 26, alignment: .trailing)

            Button {
                onSelectMove?(pair.whiteIndex)
            } label: {
                Text(pair.white)
                    .fontWeight(whiteSelected ? .bold : .regular)
                    .foregroundStyle(whiteSelected ? DS.accent : Color.primary.opacity(0.85))
                    .padding(.horizontal, 4)
                    .padding(.vertical, 2)
                    .background(whiteSelected ? DS.accent.opacity(0.18) : Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, 4)

            Group {
                if let black = pair.black, let bIdx = pair.blackIndex {
                    Button {
                        onSelectMove?(bIdx)
                    } label: {
                        Text(black)
                            .fontWeight(blackSelected ? .bold : .regular)
                            .foregroundStyle(blackSelected ? DS.accent : Color.primary.opacity(0.85))
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(blackSelected ? DS.accent.opacity(0.18) : Color.clear)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                    }
                    .buttonStyle(.plain)
                } else {
                    Text("").foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .font(.system(.subheadline, design: .monospaced))
        .padding(.vertical, 4)
        .padding(.horizontal, 6)
        .frame(maxWidth: .infinity)
    }
}
