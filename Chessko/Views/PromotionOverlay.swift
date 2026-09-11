import SwiftUI

// MARK: - Promotion Overlay
//
// Birac figure pri promociji pesaka. Izdvojen iz `GameView`-a u Fazi 4a jer ga
// od tada koriste DVA ekrana: slobodna partija i partija `game` koraka Puta.
//
// Bez njega bi ekran koraka imao promociju bez ijednog nacina da se figura
// izabere (osim podesavanja „automatski u damu"), pa bi partija stala na
// promociji — a korak koji se zavrsava tek kad partija dodje do kraja ne bi
// mogao da se zavrsi.
//
// Kod je PRENET doslovno iz `GameView`-a (ukljucujuci `.title3` umesto
// `Font.ds*`), da slobodna partija izgleda identicno kao pre izdvajanja.

struct PromotionOverlay: View {

    /// Boja figure koja se promovise.
    let color: PieceColor
    let onSelect: (PieceType) -> Void

    var body: some View {
        ZStack {
            DS.scrim.ignoresSafeArea()

            VStack(spacing: 20) {
                Text(Loc("Izaberi figuru"))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(DS.onScrim)

                HStack(spacing: 12) {
                    ForEach([PieceType.queen, .rook, .bishop, .knight], id: \.self) { type in
                        Button {
                            onSelect(type)
                        } label: {
                            PieceImageView(piece: ChessPiece(type: type, color: color))
                                .frame(width: 64, height: 64)
                                .padding(10)
                                .background(DS.onScrim.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(DS.onScrim.opacity(0.3), lineWidth: 1)
                                )
                        }
                    }
                }
            }
            .padding(28)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
            .padding(.horizontal, 24)
        }
        .transition(.opacity.combined(with: .scale(scale: 0.95)))
    }
}
