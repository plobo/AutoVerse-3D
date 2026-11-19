import SwiftUI

struct ExploreScreen: View {
    let open: (Route) -> Void
    private let items: [(String, String, String, ViewerMode?)] = [
        ("Interior explorer", "Step inside UrbanX: dashboard, seats, console and storage.", "carseat.left", .interior),
        ("Mechanical explorer", "Engine, transmission, brakes, suspension — with an exploded view.", "wrench.and.screwdriver", .mechanical),
        ("EV Lab", "Battery, inverter, motor, charging and regenerative braking.", "bolt.fill", nil),
        ("Safety lab", "Airbags, seat belts, crumple zones and driver-assistance sensors.", "shield.lefthalf.filled", .safety),
        ("Customization studio", "Paint, wheels, trim and interior colours you can save.", "paintpalette", .customize),
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Text("Learning modules built around the UrbanX reference vehicle.").font(.subheadline).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ForEach(items, id: \.0) { title, subtitle, icon, mode in
                    Shortcut(icon: icon, title: title, subtitle: subtitle) {
                        if let mode { open(.viewer(vehicleId: "urbanx_suv", mode: mode, configId: nil)) } else { open(.evLab) }
                    }
                }
            }
            .padding()
        }
        .navigationTitle("Explore")
    }
}
