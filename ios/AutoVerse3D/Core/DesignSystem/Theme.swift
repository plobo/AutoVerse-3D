import SwiftUI
import UIKit

/// Values mirror shared/design/tokens.json ("AutoVerse Studio").
enum Tokens {
    static let primary = Color(light: 0x0B7A75, dark: 0x4FD1C5)
    static let accent = Color(light: 0xF28C28, dark: 0xFFB466)
    static let surfaceVariant = Color(light: 0xE6EAEE, dark: 0x232934)
    static let studioLight = UIColor(rgb: 0xC9D0D7)
    static let studioDark = UIColor(rgb: 0x1A1F27)
}

extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(red: CGFloat((rgb >> 16) & 0xFF) / 255, green: CGFloat((rgb >> 8) & 0xFF) / 255, blue: CGFloat(rgb & 0xFF) / 255, alpha: 1)
    }

    convenience init(hex: String) {
        let c = ColorUtil.hexToSRGB(hex)
        self.init(red: CGFloat(c.x), green: CGFloat(c.y), blue: CGFloat(c.z), alpha: 1)
    }
}

extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(UIColor { $0.userInterfaceStyle == .dark ? UIColor(rgb: dark) : UIColor(rgb: light) })
    }

    init(hex: String) { self.init(UIColor(hex: hex)) }
}

struct Disclaimer: View {
    let text: String
    var body: some View {
        Label { Text(text).font(.footnote).foregroundStyle(.secondary) } icon: { Image(systemName: "info.circle").foregroundStyle(.secondary) }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Tokens.surfaceVariant, in: RoundedRectangle(cornerRadius: 12))
    }
}

struct InfoCard<Footer: View>: View {
    let title: String
    let bodyText: String
    @ViewBuilder var footer: Footer
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.headline)
            Text(bodyText).font(.subheadline).foregroundStyle(.secondary)
            footer
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.background, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(.quaternary))
    }
}

/// Never relies on colour alone: swatch + text label + selection ring + accessibility trait.
struct ColorSwatch: View {
    let name: String
    let hex: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Circle().fill(Color(hex: hex)).frame(width: 34, height: 34)
                    .padding(4)
                    .overlay(Circle().stroke(selected ? Tokens.primary : Color.secondary.opacity(0.4), lineWidth: selected ? 3 : 1))
                Text(name).font(.caption2).fontWeight(selected ? .bold : .regular).multilineTextAlignment(.center).lineLimit(2)
            }
            .frame(width: 68)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(name)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

struct Chip: View {
    let title: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).font(.subheadline.weight(selected ? .semibold : .regular))
                .padding(.horizontal, 12).padding(.vertical, 7)
                .background(selected ? Tokens.primary.opacity(0.18) : Color.clear, in: Capsule())
                .overlay(Capsule().stroke(selected ? Tokens.primary : Color.secondary.opacity(0.35)))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

struct SectionHeader: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).font(.headline).frame(maxWidth: .infinity, alignment: .leading).padding(.top, 6) }
}

/// Simple wrapping layout for chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > width, x > 0 { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing; rowH = max(rowH, sz.height); maxX = max(maxX, x)
        }
        return CGSize(width: min(maxX, width), height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > bounds.maxX, x > bounds.minX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += sz.width + spacing; rowH = max(rowH, sz.height)
        }
    }
}
