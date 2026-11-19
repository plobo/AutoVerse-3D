import SwiftUI

struct ShowroomScreen: View {
    @Environment(AppDependencies.self) private var deps
    let open: (Route) -> Void
    @State private var filter: String? = nil

    var body: some View {
        let vehicles = (try? deps.content.content().vehicles.vehicles) ?? []
        List {
            Section {
                HStack {
                    Chip(title: "All", selected: filter == nil) { filter = nil }
                    Chip(title: "Available now", selected: filter == "available") { filter = "available" }
                    Chip(title: "Coming soon", selected: filter == "coming_soon") { filter = "coming_soon" }
                }
            } footer: { Text("Original fictional vehicles. More arrive in content updates.") }
            ForEach(vehicles.filter { filter == nil || $0.availability == filter }) { v in
                Button { if v.isAvailable { open(.viewer(vehicleId: v.id, mode: .exterior, configId: nil)) } } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: v.isAvailable ? "car.fill" : "lock.fill").foregroundStyle(v.isAvailable ? Tokens.primary : .secondary).frame(width: 28)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(v.name).font(.headline).foregroundStyle(.primary)
                            Text(v.categoryLabel).font(.subheadline).foregroundStyle(Tokens.primary)
                            Text(v.description).font(.footnote).foregroundStyle(.secondary).lineLimit(3)
                        }
                        Spacer()
                        Text(v.isAvailable ? "View 3D" : "Soon").font(.caption.weight(.semibold)).padding(6)
                            .background(Tokens.surfaceVariant, in: Capsule())
                    }
                }
                .disabled(!v.isAvailable)
                .accessibilityIdentifier("vehicle_\(v.id)")
            }
        }
        .navigationTitle("Showroom")
    }
}
