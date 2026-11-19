import SwiftUI

struct GarageScreen: View {
    @Environment(AppDependencies.self) private var deps
    let open: (Route) -> Void
    @State private var renaming: VehicleConfiguration?
    @State private var newName = ""
    @State private var deleting: VehicleConfiguration?

    var body: some View {
        let _ = deps.dataVersion
        let content = try? deps.content.content()
        let configs = deps.configurations.all()
        List {
            Section("Saved configurations") {
                if configs.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("No saved designs yet.")
                        Button("Open the customization studio") { open(.viewer(vehicleId: "urbanx_suv", mode: .customize, configId: nil)) }
                    }
                }
                ForEach(configs) { cfg in
                    Button { open(.viewer(vehicleId: cfg.vehicleId, mode: .customize, configId: cfg.id)) } label: { row(cfg, content?.customization) }
                        .accessibilityIdentifier("config_\(cfg.name)")
                        .contextMenu { actions(cfg) }
                        .swipeActions { actions(cfg) }
                }
            }
            Section {
                ForEach(content?.vehicles.vehicles.filter(\.isAvailable) ?? []) { v in PackRow(vehicle: v) }
            } header: { Text("Downloaded vehicles") } footer: {
                Text("Saved designs and downloaded vehicles are stored only on this device and work offline.")
            }
        }
        .navigationTitle("My Garage")
        .alert("Rename", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("Name", text: $newName)
            Button("Save") {
                if var c = renaming, ConfigurationRules.validateName(newName) == .valid {
                    c.name = newName.trimmingCharacters(in: .whitespaces); c.updatedAt = ISO8601.now()
                    deps.configurations.save(c); deps.touch()
                }
                renaming = nil
            }
            Button("Cancel", role: .cancel) { renaming = nil }
        }
        .confirmationDialog("Delete \"\(deleting?.name ?? "")\"?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button("Delete", role: .destructive) { if let d = deleting { deps.configurations.delete(d.id); deps.touch() }; deleting = nil }
        } message: { Text("This removes the saved design from this device.") }
    }

    @ViewBuilder private func actions(_ cfg: VehicleConfiguration) -> some View {
        Button("Rename") { newName = cfg.name; renaming = cfg }
        Button("Duplicate") {
            let names = Set(deps.configurations.all().map(\.name)), now = ISO8601.now()
            var copy = cfg; copy.id = UUID().uuidString; copy.name = ConfigurationRules.duplicateName(cfg.name, existing: names)
            copy.createdAt = now; copy.updatedAt = now
            deps.configurations.save(copy); deps.touch()
        }
        Button("Delete", role: .destructive) { deleting = cfg }
    }

    private func row(_ cfg: VehicleConfiguration, _ o: CustomizationOptions?) -> some View {
        let paint = o?.paints.first { $0.id == cfg.paintId }
        let detail = [paint?.name, o?.wheels.first { $0.id == cfg.wheelId }?.name, o?.trims.first { $0.id == cfg.trimId }?.name,
                      o?.interiors.first { $0.id == cfg.interiorId }.map { "\($0.name) interior" }].compactMap { $0 }.joined(separator: " · ")
        return HStack(spacing: 12) {
            Circle().fill(Color(hex: paint?.hex ?? "#888888")).frame(width: 32, height: 32).overlay(Circle().stroke(.quaternary))
            VStack(alignment: .leading) { Text(cfg.name).font(.headline).foregroundStyle(.primary); Text(detail).font(.footnote).foregroundStyle(.secondary) }
        }
    }
}

struct PackRow: View {
    @Environment(AppDependencies.self) private var deps
    let vehicle: Vehicle

    var body: some View {
        let id = vehicle.assetManifestId ?? ""
        let status = deps.assets.status(id)
        VStack(alignment: .leading, spacing: 8) {
            Text("\(vehicle.name) asset pack").font(.headline)
            Text("A built-in copy is always available offline. Downloading fetches the latest verified pack.").font(.footnote).foregroundStyle(.secondary)
            switch status.state {
            case .notDownloaded: Text("Checking for the latest pack…").font(.footnote)
            case let .available(total, version):
                Button("Download v\(version) (\(formatBytes(total)))") { deps.assets.start(manifestId: id, vehicleId: vehicle.id) }.buttonStyle(.borderedProminent)
            case let .downloading(d, t):
                ProgressView(value: Double(d), total: Double(max(t, 1)))
                HStack { Text("\(formatBytes(d)) of \(formatBytes(t))").font(.footnote); Spacer(); Button("Pause") { deps.assets.pause(manifestId: id) } }
            case let .paused(d, _):
                HStack { Text("Paused at \(formatBytes(d))"); Spacer(); Button("Resume") { deps.assets.start(manifestId: id, vehicleId: vehicle.id) } }
            case .verifying: Text("Verifying integrity…")
            case let .installed(v, size):
                HStack {
                    Text("Installed v\(v) · \(formatBytes(size)) · verified").font(.subheadline)
                    Spacer()
                    Button("Remove", role: .destructive) { Task { await deps.assets.remove(manifestId: id, vehicleId: vehicle.id) } }
                }
            case let .failed(reason, _, _):
                Text(reason).foregroundStyle(.red).font(.footnote)
                Button("Retry") { deps.assets.start(manifestId: id, vehicleId: vehicle.id) }
            }
            if let m = status.message { Text(m).font(.caption).foregroundStyle(.secondary) }
        }
        .buttonStyle(.borderless)
        .task(id: id) { await deps.assets.refresh(manifestId: id, vehicleId: vehicle.id) }
    }
}
