import SwiftUI

// MARK: - Move History View
//
// Prikazuje odigrane poteze u srpskoj algebarskoj notaciji (K/D/T/L/S).
// Potezi su grupisani u parove: beli potez | crni potez.
// Lista se automatski skroluje do poslednjeg poteza.

struct MoveHistoryView: View {

    /// Notacije u redosledu igranja: [beli1, crni1, beli2, crni2, ...]
    let notations: [String]

    // MARK: - Data model

    private struct MovePair: Identifiable {
        let id: Int          // 0-based pair index, used as scroll anchor
        let number: Int      // 1-based move number shown to user
        let white: String
        let black: String?   // nil if game ended on white's move
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

    // MARK: - Body

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(pairs) { pair in
                        row(pair: pair)
                            .id(pair.id)
                    }
                }
                .padding(.vertical, 4)
            }
            .frame(maxHeight: 136)
            .onChange(of: notations.count) { _, _ in
                if let last = pairs.last {
                    withAnimation(.easeOut(duration: 0.2)) {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: - Row

    @ViewBuilder
    private func row(pair: MovePair) -> some View {
        let lastIndex = notations.count - 1
        let whiteIsLast = pair.id * 2 == lastIndex
        let blackIsLast = pair.black != nil && pair.id * 2 + 1 == lastIndex

        HStack(spacing: 0) {
            // Move number
            Text("\(pair.number).")
                .foregroundStyle(.secondary)
                .frame(width: 32, alignment: .trailing)

            // White's move
            Text(pair.white)
                .fontWeight(whiteIsLast ? .semibold : .regular)
                .foregroundStyle(whiteIsLast ? Color.primary : Color.primary.opacity(0.85))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, 8)

            // Black's move (or empty placeholder)
            Group {
                if let black = pair.black {
                    Text(black)
                        .fontWeight(blackIsLast ? .semibold : .regular)
                        .foregroundStyle(blackIsLast ? Color.primary : Color.primary.opacity(0.85))
                } else {
                    Text("…")
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .font(.system(.subheadline, design: .monospaced))
        .padding(.vertical, 5)
        .padding(.horizontal, 10)
        .background(pair.id % 2 == 0 ? Color.clear : Color.primary.opacity(0.04))
    }
}

// MARK: - Preview

#Preview {
    MoveHistoryView(notations: [
        "e4", "e5",
        "Sf3", "Sc6",
        "Lb5", "a6",
        "La4", "Sf6",
        "O-O", "Le7",
        "Te1", "b5",
        "Lb3", "d6",
    ])
    .padding()
}
