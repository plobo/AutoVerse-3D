import SwiftUI

/// Customization studio: paint, wheels, trim and interior; save, save-as-new, reset and compare.
struct CustomizationPanel: View {
    let model: VehicleViewerModel
    @State private var naming: Bool? = nil   // true = save as new, false = update existing
    @State private var name = ""

    var body: some View {
        if let o = model.content?.customization, let sel = model.selection {
            if let saved = model.loadedConfiguration {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Editing \"\(saved.name)\"").font(.headline)
                    if model.differences.isEmpty { Text("No changes from the saved configuration.").font(.footnote) }
                    ForEach(model.differences, id: \.field) { d in Text("\(d.field): \(d.saved) → \(d.current)").font(.footnote) }
                }
                .frame(maxWidth: .infinity, alignment: .leading).padding(12)
                .background(Tokens.surfaceVariant, in: RoundedRectangle(cornerRadius: 12))
            }
            SectionHeader("Exterior paint")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack { ForEach(o.paints) { p in ColorSwatch(name: p.name, hex: p.hex, selected: sel.paintId == p.id) { model.setPaint(p.id) } } }
            }
            SectionHeader("Wheels")
            HStack { ForEach(o.wheels) { w in Chip(title: w.name, selected: sel.wheelId == w.id) { model.setWheel(w.id) } } }
            Text(o.wheels.first { $0.id == sel.wheelId }?.description ?? "").font(.footnote).foregroundStyle(.secondary)
            SectionHeader("Trim accents")
            HStack { ForEach(o.trims) { t in ColorSwatch(name: t.name, hex: t.hex, selected: sel.trimId == t.id) { model.setTrim(t.id) } } }
            SectionHeader("Interior")
            HStack { ForEach(o.interiors) { i in ColorSwatch(name: i.name, hex: i.hex, selected: sel.interiorId == i.id) { model.setInterior(i.id) } } }
            Text("Tip: switch to Interior to see the cabin colour.").font(.footnote).foregroundStyle(.secondary)
            HStack {
                Button(model.loadedConfiguration == nil ? "Save to My Garage" : "Save changes") {
                    name = model.loadedConfiguration?.name ?? "My \(model.vehicle?.name ?? "car")"
                    naming = model.loadedConfiguration == nil
                }
                .buttonStyle(.borderedProminent).tint(Tokens.primary).accessibilityIdentifier("saveConfiguration")
                if model.loadedConfiguration != nil {
                    Button("Save as new") { name = "\(model.loadedConfiguration?.name ?? "") copy"; naming = true }.buttonStyle(.bordered)
                }
                Button("Reset") { model.resetDesign() }
            }
            .alert(naming == true ? "Name this configuration" : "Save changes", isPresented: Binding(get: { naming != nil }, set: { if !$0 { naming = nil } })) {
                TextField("Name", text: $name).accessibilityIdentifier("configurationName")
                Button("Save") { if model.saveConfiguration(name: name, asNew: naming ?? true) { naming = nil } }
                Button("Cancel", role: .cancel) { naming = nil }
            }
        }
    }
}
