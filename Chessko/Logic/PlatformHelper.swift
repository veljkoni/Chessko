import SwiftUI
import UIKit

extension Font {
    static func appFont(_ style: Font.TextStyle) -> Font {
        if UIDevice.current.userInterfaceIdiom == .mac {
            switch style {
            case .caption2:
                return .system(size: 13)
            case .caption:
                return .system(size: 15)
            case .footnote:
                return .system(size: 16)
            case .subheadline:
                return .system(size: 18)
            case .callout:
                return .system(size: 19)
            case .body:
                return .system(size: 20)
            case .headline:
                return .system(size: 20, weight: .semibold)
            case .title3:
                return .system(size: 23)
            case .title2:
                return .system(size: 26)
            case .title:
                return .system(size: 32)
            case .largeTitle:
                return .system(size: 40)
            @unknown default:
                return .system(style)
            }
        } else {
            return .system(style)
        }
    }
}
