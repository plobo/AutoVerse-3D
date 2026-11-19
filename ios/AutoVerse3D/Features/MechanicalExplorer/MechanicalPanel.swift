import SwiftUI

/// Mechanical explorer: simplified assembly, exploded views, selection and reset.
struct MechanicalPanel: View {
    let model: VehicleViewerModel
    var body: some View {
        if let c = model.content {
            Disclaimer(text: c.components.educationalDisclaimer)
            SectionHeader("Exploded view")
            FlowLayout {
                ForEach(c.components.explodedViews) { v in
                    if model.explodedViewId == v.id {
                        Button("Assemble \(v.name.lowercased())") { model.setExploded(nil) }.buttonStyle(.bordered)
                    } else {
                        Button("Explode: \(v.name)") { model.setExploded(v.id) }.buttonStyle(.borderedProminent).tint(Tokens.primary)
                    }
                }
                Button("Reset assembly") { model.resetAssembly() }.buttonStyle(.bordered)
            }
            if let comp = model.selectedComponent {
                InfoCard(title: comp.name, bodyText: comp.description) {
                    Text("Purpose: \(comp.purpose)").font(.subheadline)
                    Text(comp.detail).font(.footnote)
                    Button("Close") { model.selectComponent(nil) }
                }
            }
            SectionHeader("Components")
            VStack(spacing: 0) {
                ForEach(model.components("mechanical")) { comp in
                    Button { model.selectComponent(comp.id) } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(comp.category.capitalized).font(.caption2).foregroundStyle(.secondary)
                            Text(comp.name).font(.body.weight(.medium)).foregroundStyle(.primary)
                            Text(comp.purpose).font(.footnote).foregroundStyle(.secondary).lineLimit(2).multilineTextAlignment(.leading)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 8)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(comp.name)
                    .accessibilityHint(comp.purpose)
                    Divider()
                }
            }
        }
    }
}
