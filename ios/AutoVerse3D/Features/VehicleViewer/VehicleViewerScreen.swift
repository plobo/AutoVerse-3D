import SwiftUI

struct VehicleViewerScreen: View {
    @State private var model: VehicleViewerModel
    @Environment(\.colorScheme) private var scheme
    @State private var showAssistant = false

    init(deps: AppDependencies, vehicleId: String, mode: ViewerMode, configId: String? = nil) {
        _model = State(initialValue: VehicleViewerModel(deps: deps, vehicleId: vehicleId, mode: mode, configId: configId))
    }

    var body: some View {
        VStack(spacing: 0) {
            if let error = model.error {
                ContentUnavailableView { Label(error, systemImage: "car") } actions: {
                    Button("Retry") { Task { await model.load(background: background) } }
                }
            } else {
                Picker("Explorer mode", selection: Binding(get: { model.mode }, set: { model.setMode($0) })) {
                    ForEach(ViewerMode.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal).padding(.bottom, 8)
                .accessibilityIdentifier("modePicker")
                viewport.frame(height: 300)
                if model.mode != .interior, let presets = model.content?.cameras.orbit.presets {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack { ForEach(presets) { p in Chip(title: p.name, selected: model.orbitPresetId == p.id) { model.selectOrbitPreset(p.id) } } }
                            .padding(.horizontal).padding(.vertical, 8)
                    }
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        switch model.mode {
                        case .exterior: ExteriorPanel(model: model)
                        case .interior: InteriorPanel(model: model)
                        case .mechanical: MechanicalPanel(model: model)
                        case .safety: SafetyPanel(model: model)
                        case .customize: CustomizationPanel(model: model)
                        }
                    }
                    .padding()
                }
                .id(model.mode)
            }
        }
        .navigationTitle(model.vehicle?.name ?? "Vehicle")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { showAssistant = true } label: { Image(systemName: "sparkles") }
                    .accessibilityLabel("Ask the AI assistant about this vehicle")
            }
        }
        .sheet(isPresented: $showAssistant) {
            NavigationStack { AssistantScreen(deps: model.deps, vehicleId: model.vehicle?.id, componentId: model.selectedComponentId) }
        }
        .alert(model.message ?? "", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) { Button("OK") {} }
        .task { if model.renderer == nil { await model.load(background: background) } }
    }

    private var background: UIColor { scheme == .dark ? Tokens.studioDark : Tokens.studioLight }

    @ViewBuilder private var viewport: some View {
        ZStack(alignment: .topTrailing) {
            if let r = model.renderer {
                VehicleRealityView(renderer: r)
                    .accessibilityElement()
                    .accessibilityLabel("3D view of \(model.vehicle?.name ?? "vehicle"). Drag to rotate, pinch to zoom, two fingers to pan. All parts are also listed below.")
                    .accessibilityIdentifier("viewport")
            } else {
                Color(Tokens.studioLight)
            }
            switch model.renderStatus {
            case .loading:
                ProgressView("Loading \(model.vehicle?.name ?? "")…").frame(maxWidth: .infinity, maxHeight: .infinity)
            case let .failed(msg):
                VStack(spacing: 6) {
                    Text("The 3D view couldn't be shown.").font(.headline)
                    Text("You can still explore every part using the lists below.").font(.caption)
                    Text(msg).font(.caption2).foregroundStyle(.secondary)
                    Button("Retry") { Task { await model.load(background: background) } }
                }
                .padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .ready: EmptyView()
            }
            Button { model.resetCamera() } label: {
                Image(systemName: "scope").font(.title3).padding(10).background(.thinMaterial, in: Circle())
            }
            .accessibilityLabel("Reset camera").padding(8)
            if let origin = model.model?.originLabel {
                Text(origin).font(.caption2).padding(.horizontal, 8).padding(.vertical, 3)
                    .background(.thinMaterial, in: Capsule())
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading).padding(8)
            }
        }
    }
}

private let partLabels: [(String, String)] = [
    ("door_front_left_open", "front-left door"), ("door_front_right_open", "front-right door"),
    ("door_rear_left_open", "rear-left door"), ("door_rear_right_open", "rear-right door"),
    ("hood_open", "hood"), ("trunk_open", "tailgate"),
]

struct ExteriorPanel: View {
    let model: VehicleViewerModel
    var body: some View {
        if let c = model.content, let v = model.vehicle, let sel = model.selection {
            if let comp = model.selectedComponent {
                InfoCard(title: comp.name, bodyText: "\(comp.description) \(comp.purpose)") {
                    Text(comp.detail).font(.footnote)
                    Button("Close") { model.selectComponent(nil) }
                }
            }
            Text(v.categoryLabel).font(.subheadline.weight(.semibold)).foregroundStyle(Tokens.primary)
            Text(v.description).font(.subheadline)
            SectionHeader("Paint")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack { ForEach(c.customization.paints) { p in ColorSwatch(name: p.name, hex: p.hex, selected: sel.paintId == p.id) { model.setPaint(p.id) } } }
            }
            Button("Reset paint") { model.setPaint(c.customization.defaults.paintId) }.font(.subheadline)
            SectionHeader("Lights and moving parts")
            FlowLayout {
                Chip(title: model.lightsOn ? "Lights on" : "Lights off", selected: model.lightsOn) { model.toggleLights() }
                ForEach(partLabels, id: \.0) { id, label in
                    let open = model.openAnimations.contains(id)
                    Chip(title: open ? "Close \(label)" : "Open \(label)", selected: open) { model.togglePart(id) }
                }
            }
            if !model.openAnimations.isEmpty { Button("Close everything") { model.closeAllParts() } }
            SectionHeader("Parts (tap the model or choose here)")
            FlowLayout { ForEach(model.components("exterior")) { comp in Chip(title: comp.name, selected: model.selectedComponentId == comp.id) { model.selectComponent(comp.id) } } }
            SectionHeader("Specifications")
            ForEach(v.specs, id: \.label) { s in
                HStack { Text(s.label).foregroundStyle(.secondary); Spacer(); Text(s.value).fontWeight(.semibold) }.font(.subheadline)
            }
            Disclaimer(text: v.specsDisclaimer)
        }
    }
}
