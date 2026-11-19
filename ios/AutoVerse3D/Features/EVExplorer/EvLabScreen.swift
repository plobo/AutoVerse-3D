import SwiftUI

/// Conceptual EV powertrain with an animated energy-flow diagram and full text alternatives.
struct EvLabScreen: View {
    @Environment(AppDependencies.self) private var deps
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var modeId = "drive"
    @State private var selectedNode: String?

    var body: some View {
        if let ev = try? deps.content.content().evLab, let mode = ev.modes.first(where: { $0.id == modeId }) ?? ev.modes.first {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Disclaimer(text: ev.disclaimer)
                    Picker("Energy flow", selection: $modeId) { ForEach(ev.modes) { Text($0.title).tag($0.id) } }.pickerStyle(.segmented)
                    TimelineView(.animation(paused: reduceMotion)) { timeline in
                        let phase = reduceMotion ? 0.5 : timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.6) / 1.6
                        Canvas { ctx, size in draw(ctx: ctx, size: size, ev: ev, mode: mode, phase: phase) }
                    }
                    .frame(height: 230)
                    .accessibilityElement()
                    .accessibilityLabel("Energy flow diagram. \(mode.textAlternative)")
                    SectionHeader("What's happening")
                    Text(mode.textAlternative).font(.body)
                    if let id = selectedNode, let n = ev.nodes.first(where: { $0.id == id }) { InfoCard(title: n.title, bodyText: n.summary) { EmptyView() } }
                    SectionHeader("Components")
                    ForEach(ev.nodes) { n in
                        Button { selectedNode = n.id } label: {
                            VStack(alignment: .leading) { Text(n.title).font(.body.weight(.medium)); Text(n.summary).font(.footnote).foregroundStyle(.secondary) }
                                .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 6)
                        }.buttonStyle(.plain)
                        Divider()
                    }
                }
                .padding()
            }
            .navigationTitle("EV Lab")
        }
    }

    private func draw(ctx: GraphicsContext, size: CGSize, ev: EvLabContent, mode: EvMode, phase: Double) {
        func center(_ id: String) -> CGPoint {
            let n = ev.nodes.first { $0.id == id }!
            return CGPoint(x: CGFloat(n.x) * size.width, y: CGFloat(n.y) * size.height)
        }
        for flow in mode.flows where flow.count == 2 {
            let a = center(flow[0]), b = center(flow[1])
            var path = Path(); path.move(to: a); path.addLine(to: b)
            ctx.stroke(path, with: .color(Tokens.primary.opacity(0.35)), style: StrokeStyle(lineWidth: 8, lineCap: .round))
            for offset in reduceMotion ? [0.0] : [0.0, 0.33, 0.66] {
                let t = (phase + offset).truncatingRemainder(dividingBy: 1)
                let p = CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)
                ctx.fill(Path(ellipseIn: CGRect(x: p.x - 6, y: p.y - 6, width: 12, height: 12)), with: .color(Tokens.accent))
            }
        }
        let w = size.width * 0.2, h: CGFloat = 42
        for n in ev.nodes {
            let c = center(n.id)
            let rect = CGRect(x: c.x - w / 2, y: c.y - h / 2, width: w, height: h)
            let active = mode.flows.contains { $0.contains(n.id) }
            ctx.fill(Path(roundedRect: rect, cornerRadius: 12), with: .color(Tokens.surfaceVariant))
            ctx.stroke(Path(roundedRect: rect, cornerRadius: 12), with: .color(active ? Tokens.primary : .secondary.opacity(0.4)), lineWidth: 2)
            ctx.draw(Text(n.title).font(.caption2.weight(.semibold)), in: rect.insetBy(dx: 4, dy: 4))
        }
    }
}
