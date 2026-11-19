import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class VehicleViewerModel {
    private(set) var content: AppContent?
    private(set) var vehicle: Vehicle?
    private(set) var model: ResolvedModel?
    private(set) var graph: SceneGraph?
    private(set) var error: String?
    var renderStatus: RenderStatus = .loading
    private(set) var mode: ViewerMode
    var selection: DesignSelection?
    private(set) var loadedConfiguration: VehicleConfiguration?
    private(set) var lightsOn = false
    private(set) var openAnimations: Set<String> = []
    private(set) var explodedViewId: String?
    private(set) var selectedComponentId: String?
    private(set) var selectedHotspotId: String?
    private(set) var orbitPresetId = "three_quarter"
    private(set) var interiorPresetId = "driver"
    var message: String?
    let reducedMotion = UIAccessibility.isReduceMotionEnabled

    @ObservationIgnored let deps: AppDependencies
    @ObservationIgnored private let vehicleId: String
    @ObservationIgnored private let configId: String?
    @ObservationIgnored private(set) var renderer: RealityVehicleRenderer?

    init(deps: AppDependencies, vehicleId: String, mode: ViewerMode, configId: String?) {
        self.deps = deps; self.vehicleId = vehicleId; self.mode = mode; self.configId = configId
    }

    var selectedComponent: VehicleComponent? { content?.components.components.first { $0.id == selectedComponentId } }
    var selectedHotspot: Hotspot? { content?.hotspots.hotspots.first { $0.id == selectedHotspotId } }
    func components(_ module: String) -> [VehicleComponent] { content?.components.components.filter { $0.module == module } ?? [] }
    func hotspots(_ module: String) -> [Hotspot] { content?.hotspots.hotspots.filter { $0.module == module } ?? [] }

    var differences: [ConfigurationDifference] {
        guard let saved = loadedConfiguration, let sel = selection, let o = content?.customization else { return [] }
        return ConfigurationRules.compare(saved: saved, current: sel, options: o)
    }

    func load(background: UIColor) async {
        error = nil
        renderStatus = .loading
        do {
            let c = try deps.content.content()
            guard let v = c.vehicles.vehicles.first(where: { $0.id == vehicleId }), let manifestId = v.assetManifestId else {
                error = "This vehicle is coming soon."; return
            }
            let resolved = try deps.assets.resolve(vehicleId: v.id, manifestId: manifestId)
            content = c; vehicle = v; model = resolved; graph = SceneGraph(resolved.scene)
            let saved = configId.flatMap { deps.configurations.get($0) }
            loadedConfiguration = saved
            selection = ConfigurationRules.sanitize(saved.map(DesignSelection.of) ?? .defaults(c.customization), options: c.customization)
            orbitPresetId = c.cameras.orbit.default; interiorPresetId = c.cameras.interior.default
            let r = renderer ?? RealityVehicleRenderer(catalog: c.components, background: background)
            r.onTap = { [weak self] names in self?.onTap(names) }
            renderer = r
            setMode(mode, animate: false)
            try await r.load(model: resolved.modelURL, studio: resolved.studioURL, scene: resolved.scene)
            pushScene()
            renderStatus = .ready
        } catch {
            renderStatus = .failed(error.localizedDescription)
            if content == nil { self.error = error.localizedDescription }
        }
    }

    // MARK: modes & cameras

    func setMode(_ m: ViewerMode, animate: Bool = true) {
        mode = m
        selectedComponentId = nil; selectedHotspotId = nil
        if m == .interior { openAnimations.remove("hood_open") }
        if m != .mechanical { explodedViewId = nil }
        if m == .interior { selectInteriorPreset(interiorPresetId) } else { selectOrbitPreset(orbitPresetId, animate: animate) }
        deps.recordExplored(vehicleId: vehicleId, mode: m)
        pushScene()
    }

    func selectOrbitPreset(_ id: String, animate: Bool = true) {
        guard let c = content, let p = c.cameras.orbit.presets.first(where: { $0.id == id }) else { return }
        orbitPresetId = id
        var cam = OrbitCamera(preset: p, limits: c.cameras.orbit.limits)
        if mode == .mechanical { cam.distance *= 0.85 }
        renderer?.setCamera(.orbit(cam), animate: animate && !reducedMotion)
    }

    func selectInteriorPreset(_ id: String) {
        guard let c = content, let p = c.cameras.interior.presets.first(where: { $0.id == id }) else { return }
        interiorPresetId = id
        renderer?.setCamera(.interior(InteriorCamera(preset: p, maxYaw: c.cameras.interior.limits.maxYawOffset, maxPitch: c.cameras.interior.limits.maxPitchOffset)), animate: false)
    }

    func resetCamera() {
        if mode == .interior { selectInteriorPreset(interiorPresetId) } else { selectOrbitPreset(content?.cameras.orbit.default ?? "three_quarter") }
    }

    // MARK: customization

    func setPaint(_ id: String) { selection?.paintId = id; pushScene() }
    func setWheel(_ id: String) { selection?.wheelId = id; pushScene() }
    func setTrim(_ id: String) { selection?.trimId = id; pushScene() }
    func setInterior(_ id: String) { selection?.interiorId = id; pushScene() }

    func resetDesign() {
        guard let c = content else { return }
        selection = loadedConfiguration.map(DesignSelection.of) ?? .defaults(c.customization)
        pushScene()
    }

    @discardableResult
    func saveConfiguration(name: String, asNew: Bool) -> Bool {
        guard let sel = selection else { return false }
        switch ConfigurationRules.validateName(name) {
        case .empty: message = "Give your configuration a name."; return false
        case .tooLong: message = "Names can be up to \(ConfigurationRules.maxNameLength) characters."; return false
        case .valid: break
        }
        let now = ISO8601.now()
        let config: VehicleConfiguration
        if var existing = loadedConfiguration, !asNew {
            existing.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
            existing.paintId = sel.paintId; existing.wheelId = sel.wheelId; existing.trimId = sel.trimId; existing.interiorId = sel.interiorId
            existing.updatedAt = now
            config = existing
        } else {
            config = ConfigurationRules.create(id: UUID().uuidString, vehicleId: vehicleId, name: name, selection: sel, now: now)
        }
        deps.configurations.save(config)
        deps.touch()
        loadedConfiguration = config
        message = "Saved \"\(config.name)\" to My Garage"
        return true
    }

    // MARK: parts, exploded views, selection

    func toggleLights() { lightsOn.toggle(); pushScene() }
    func togglePart(_ id: String) { if openAnimations.contains(id) { openAnimations.remove(id) } else { openAnimations.insert(id) }; pushScene() }
    func closeAllParts() { openAnimations.removeAll(); pushScene() }
    func setExploded(_ id: String?) { explodedViewId = id; pushScene() }
    func resetAssembly() { explodedViewId = nil; selectedComponentId = nil; pushScene() }
    func selectComponent(_ id: String?) { selectedComponentId = id; selectedHotspotId = nil; pushScene() }
    func selectHotspot(_ id: String?) { selectedHotspotId = id; selectedComponentId = nil; pushScene() }

    /// Tapped mesh names (nearest first) -> the first that belongs to a selectable item in the current mode.
    func onTap(_ names: [String]) {
        guard let graph else { return }
        switch mode {
        case .interior, .safety:
            let byNode = Dictionary(uniqueKeysWithValues: hotspots(mode == .interior ? "interior" : "safety").map { ($0.nodeName, $0) })
            let hit = names.lazy.compactMap { graph.nearestAncestor(of: $0, in: Set(byNode.keys)) }.first
            selectHotspot(hit.flatMap { byNode[$0]?.id })
        case .exterior, .customize, .mechanical:
            let byNode = Dictionary(uniqueKeysWithValues: components(mode == .mechanical ? "mechanical" : "exterior").map { ($0.nodeName, $0) })
            // Prefer specific parts over the whole body shell when several collision volumes are hit.
            let candidates = names.compactMap { graph.nearestAncestor(of: $0, in: Set(byNode.keys)) }
            let hit = candidates.first { $0 != "Body" } ?? candidates.first
            selectComponent(hit.flatMap { byNode[$0]?.id })
        }
    }

    // MARK: scene state

    func sceneState() -> SceneStateValue {
        guard let c = content, let graph else { return SceneStateValue() }
        let sel = selection ?? .defaults(c.customization)
        let wheel = c.customization.wheels.first { $0.id == sel.wheelId } ?? c.customization.wheels[0]
        let hidden = VisibilityRules.hiddenNodes(mode: mode, catalog: c.components, graph: graph, activeWheelSuffix: wheel.nodeSuffix,
                                                 allWheelSuffixes: c.customization.wheels.map(\.nodeSuffix))
        func look(_ list: [ColorOption], _ id: String) -> SceneStateValue.Look? {
            list.first { $0.id == id }.map { .init(hex: $0.hex, metallic: $0.metallic, roughness: $0.roughness) }
        }
        var looks: [String: SceneStateValue.Look] = [:]
        looks["Paint"] = look(c.customization.paints, sel.paintId)
        looks["Accent"] = look(c.customization.trims, sel.trimId)
        looks["InteriorFabric"] = look(c.customization.interiors, sel.interiorId)
        let focus = selectedComponent?.nodeName ?? selectedHotspot?.nodeName
        let highlighted = focus.map { graph.descendantsAndSelf($0).filter { graph.materialByMesh[$0] != nil } } ?? []
        return SceneStateValue(hiddenNodes: hidden, looks: looks, lightsOn: lightsOn, openAnimations: openAnimations,
                               explodedViewId: explodedViewId, highlightedNodes: Set(highlighted), reducedMotion: reducedMotion)
    }

    private func pushScene() { renderer?.apply(sceneState()) }
}
