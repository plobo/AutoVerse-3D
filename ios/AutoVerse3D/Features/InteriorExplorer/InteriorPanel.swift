import SwiftUI

/// Interior explorer: viewpoints, 3D hotspots and an accessible list of the same hotspots.
struct InteriorPanel: View {
    let model: VehicleViewerModel
    var body: some View {
        if let c = model.content {
            SectionHeader("Viewpoint")
            HStack { ForEach(c.cameras.interior.presets) { p in Chip(title: p.name, selected: model.interiorPresetId == p.id) { model.selectInteriorPreset(p.id) } } }
            Text("Drag to look around. Tap an orange marker, or pick a feature from the list.").font(.footnote).foregroundStyle(.secondary)
            if let h = model.selectedHotspot {
                InfoCard(title: h.title, bodyText: h.summary) { Text(h.detail).font(.footnote); Button("Close") { model.selectHotspot(nil) } }
            }
            SectionHeader("Cabin features")
            HotspotList(items: model.hotspots("interior")) { model.selectHotspot($0) }
        }
    }
}

struct HotspotList: View {
    let items: [Hotspot]
    let onSelect: (String) -> Void
    var body: some View {
        VStack(spacing: 0) {
            ForEach(items) { h in
                Button { onSelect(h.id) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(h.title).font(.body.weight(.medium)).foregroundStyle(.primary)
                        Text(h.summary).font(.footnote).foregroundStyle(.secondary).lineLimit(2).multilineTextAlignment(.leading)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 10)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(h.title)
                .accessibilityHint(h.summary)
                Divider()
            }
        }
    }
}
