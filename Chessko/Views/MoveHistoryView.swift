import SwiftUI

// MARK: - Move History View
//
// Prikazuje odigrane poteze u srpskoj algebarskoj notaciji (K/D/T/L/S).
// Potezi su grupisani u parove: beli potez | crni potez.
// Dva para po redu radi veće gustine. Auto-skrol do poslednjeg poteza.

struct MoveHistoryView: View {

    /// Notacije u redosledu igranja: [beli1, crni1, beli2, crni2, ...]
    let notations: [String]

    // MARK: - Data model

    private struct MovePair: Identifiable {
        let id: Int
        let number: Int
        let white: String
        let black: String?
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
                black: i + 1 < notations.count ? notations[i + 1] : nil
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
        let lastIndex = notations.count - 1
        let whiteIsLast = pair.id * 2 == lastIndex
        let blackIsLast = pair.black != nil && pair.id * 2 + 1 == lastIndex

        HStack(spacing: 0) {
            Text("\(pair.number).")
                .foregroundStyle(.secondary)
                .frame(width: 26, alignment: .trailing)

            Text(pair.white)
                .fontWeight(whiteIsLast ? .semibold : .regular)
                .foregroundStyle(whiteIsLast ? Color.primary : Color.primary.opacity(0.85))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, 6)

            Group {
                if let black = pair.black {
                    Text(black)
                        .fontWeight(blackIsLast ? .semibold : .regular)
                        .foregroundStyle(blackIsLast ? Color.primary : Color.primary.opacity(0.85))
                } else {
                    Text("").foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .font(.system(.subheadline, design: .monospaced))
        .padding(.vertical, 5)
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Preview

#Preview {
    MoveHistoryView(notations: [
        "e4", "e5", "Sf3", "Sc6", "Lb5", "a6",
        "La4", "Sf6", "O-O", "Le7", "Te1", "b5",
        "Lb3", "d6", "c3", "O-O", "h3", "Sb8",
    ])
    .padding()
}
