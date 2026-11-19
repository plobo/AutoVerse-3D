import SwiftUI

/// Safety lab: conceptual safety-system locations as 3D hotspots plus a text list.
struct SafetyPanel: View {
    let model: VehicleViewerModel
    var body: some View {
        if let c = model.content {
            Disclaimer(text: c.hotspots.safetyDisclaimer)
            if let h = model.selectedHotspot {
                InfoCard(title: h.title, bodyText: h.summary) { Text(h.detail).font(.footnote); Button("Close") { model.selectHotspot(nil) } }
            }
            SectionHeader("Safety systems")
            HotspotList(items: model.hotspots("safety")) { model.selectHotspot($0) }
        }
    }
}
