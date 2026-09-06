import SwiftUI

struct EvalBarView: View {
    let evaluation: Double
    var mateIn: Int? = nil
    var isFlipped: Bool = false

    private var targetWhiteFraction: CGFloat {
        if let mate = mateIn {
            return mate > 0 ? 0.98 : 0.02
        }
        if evaluation >= 100.0 { return 0.98 }
        if evaluation <= -100.0 { return 0.02 }
        let winChance = 1.0 / (1.0 + pow(10.0, -evaluation / 4.0))
        return CGFloat(max(0.04, min(0.96, winChance)))
    }

    var body: some View {
        GeometryReader { geo in
            let topIsWhite = isFlipped
            let whiteFraction = targetWhiteFraction
            let topFraction = topIsWhite ? whiteFraction : (1.0 - whiteFraction)
            let bottomFraction = 1.0 - topFraction

            let whiteColor = Color.squareLight
            let blackColor = Color.boardBackground

            let topColor = topIsWhite ? whiteColor : blackColor
            let bottomColor = topIsWhite ? blackColor : whiteColor

            VStack(spacing: 0) {
                Rectangle()
                    .fill(topColor)
                    .frame(height: max(2, geo.size.height * topFraction))
                Rectangle()
                    .fill(bottomColor)
                    .frame(height: max(2, geo.size.height * bottomFraction))
            }
            .animation(.easeInOut(duration: 0.35), value: whiteFraction)
            .frame(width: geo.size.width, height: geo.size.height)
            .clipShape(RoundedRectangle(cornerRadius: 3))
            .overlay(
                RoundedRectangle(cornerRadius: 3)
                    .stroke(DS.line, lineWidth: 1)
            )
        }
        .frame(width: 10)
    }
}
