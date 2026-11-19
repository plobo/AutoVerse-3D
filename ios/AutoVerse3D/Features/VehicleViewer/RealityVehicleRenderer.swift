import Combine
import RealityKit
import SwiftUI
import UIKit

/// Declarative scene description produced by the view model; the renderer diffs it against the screen.
struct SceneStateValue: Equatable {
    struct Look: Equatable { let hex: String; let metallic: Float; let roughness: Float }
    var hiddenNodes: Set<String> = []
    var looks: [String: Look] = [:]       // material name -> override
    var lightsOn = false
    var openAnimations: Set<String> = []
    var explodedViewId: String? = nil
    var highlightedNodes: Set<String> = []
    var reducedMotion = false
}

enum RenderStatus: Equatable { case loading, ready, failed(String) }

/// Platform-neutral renderer operations (IMPLEMENTATION_PLAN §6).
@MainActor
protocol VehicleRendering: AnyObject {
    func load(model: URL, studio: URL?, scene: SceneDescriptor) async throws
    func unload()
    func setCamera(_ rig: CameraRig, animate: Bool)
    func apply(_ state: SceneStateValue)
    func resetScene()
}

/// RealityKit implementation using a non-AR ARView. All work happens on the main actor; loading is async.
@MainActor
final class RealityVehicleRenderer: NSObject, VehicleRendering, UIGestureRecognizerDelegate {
    let arView: ARView
    private let anchor = AnchorEntity(world: .zero)
    private let camera = PerspectiveCamera()
    private var vehicle: Entity?
    private var studio: Entity?
    private var entities: [String: Entity] = [:]
    private var originalTransforms: [String: simd_float4x4] = [:]
    private var originalMaterials: [String: [any RealityKit.Material]] = [:]
    private var materialByMesh: [String: String] = [:]
    private var animator: PartAnimator
    private let catalog: ComponentCatalog
    private var state = SceneStateValue()
    private var rig: CameraRig?
    private var targetRig: CameraRig?
    private var updateSubscription: (any Cancellable)?

    var onTap: (([String]) -> Void)?
    var onCameraChanged: ((CameraRig) -> Void)?

    init(catalog: ComponentCatalog, background: UIColor) {
        self.catalog = catalog
        animator = PartAnimator(catalog: catalog)
        arView = ARView(frame: .zero, cameraMode: .nonAR, automaticallyConfigureSession: false)
        super.init()
        arView.environment.background = .color(background)
        arView.scene.addAnchor(anchor)
        camera.camera.fieldOfViewInDegrees = 40
        anchor.addChild(camera)

        let sun = DirectionalLight()
        sun.light.intensity = 3200
        sun.shadow = DirectionalLightComponent.Shadow(maximumDistance: 14, depthBias: 2)
        sun.look(at: .zero, from: SIMD3(3.5, 10, 4.5), relativeTo: nil)
        anchor.addChild(sun)
        let fill = DirectionalLight()
        fill.light.intensity = 900
        fill.look(at: .zero, from: SIMD3(-6, 3, -8), relativeTo: nil)
        anchor.addChild(fill)

        installGestures()
        updateSubscription = arView.scene.subscribe(to: SceneEvents.Update.self) { [weak self] event in
            let dt = Float(event.deltaTime)
            MainActor.assumeIsolated { self?.frame(dt: dt) }
        }
    }

    // MARK: loading

    func load(model: URL, studio studioURL: URL?, scene: SceneDescriptor) async throws {
        unload()
        let vehicle = try await Self.loadEntity(model)
        if let studioURL, let s = try? await Self.loadEntity(studioURL) { studio = s; anchor.addChild(s) }
        anchor.addChild(vehicle)
        self.vehicle = vehicle
        materialByMesh = Dictionary(uniqueKeysWithValues: scene.nodes.compactMap { n in n.material.map { (n.name, $0) } })
        index(vehicle)
        for node in animator.animatedNodes { if let e = entities[node] { originalTransforms[node] = e.transform.matrix } }
        let forced = state
        state = SceneStateValue()
        apply(forced)
    }

    private static func loadEntity(_ url: URL) async throws -> Entity {
        if #available(iOS 18.0, *) { return try await Entity(contentsOf: url) }
        return try Entity.load(contentsOf: url)
    }

    private func index(_ e: Entity) {
        if !e.name.isEmpty, entities[e.name] == nil { entities[e.name] = e }
        if let model = e as? ModelEntity ?? (e.components[ModelComponent.self] != nil ? e : nil),
           let comp = model.components[ModelComponent.self] {
            if !e.name.isEmpty { originalMaterials[e.name] = comp.materials }
            model.generateCollisionShapes(recursive: false)
        }
        for c in e.children { index(c) }
    }

    func unload() {
        vehicle?.removeFromParent(); studio?.removeFromParent()
        vehicle = nil; studio = nil
        entities.removeAll(); originalTransforms.removeAll(); originalMaterials.removeAll()
    }

    // MARK: scene state

    func apply(_ new: SceneStateValue) {
        let old = state
        state = new
        guard vehicle != nil else { return }
        if new.hiddenNodes != old.hiddenNodes || old == SceneStateValue() {
            for (name, e) in entities { e.isEnabled = !new.hiddenNodes.contains(name) }
        }
        if new.looks != old.looks || new.lightsOn != old.lightsOn || new.highlightedNodes != old.highlightedNodes {
            refreshMaterials()
        }
        for a in catalog.animations { animator.setPartOpen(a.id, open: new.openAnimations.contains(a.id)) }
        animator.setExploded(new.explodedViewId)
    }

    private func refreshMaterials() {
        for (mesh, originals) in originalMaterials {
            guard let entity = entities[mesh], var comp = entity.components[ModelComponent.self] else { continue }
            let materialName = materialByMesh[mesh]
            comp.materials = originals.map { original in
                guard var pbr = original as? PhysicallyBasedMaterial else { return original }
                if let name = materialName, let look = state.looks[name] {
                    pbr.baseColor = .init(tint: UIColor(hex: look.hex))
                    pbr.metallic = .init(floatLiteral: look.metallic)
                    pbr.roughness = .init(floatLiteral: look.roughness)
                }
                if materialName == "HeadlightLens" || materialName == "TaillightLens" {
                    let c: UIColor = materialName == "HeadlightLens" ? UIColor(rgb: 0xFFF6E0) : UIColor(rgb: 0xE01818)
                    pbr.emissiveColor = .init(color: state.lightsOn ? c : .black)
                    pbr.emissiveIntensity = state.lightsOn ? 2.5 : 0
                }
                if state.highlightedNodes.contains(mesh) {
                    pbr.emissiveColor = .init(color: UIColor(rgb: 0x2FBFB3))
                    pbr.emissiveIntensity = 0.8
                }
                return pbr
            }
            entity.components.set(comp)
        }
    }

    func resetScene() { animator.resetAll() }

    // MARK: camera

    func setCamera(_ rig: CameraRig, animate: Bool) {
        let sameKind: Bool = {
            switch (self.rig, rig) { case (.orbit, .orbit), (.interior, .interior): return true; default: return false }
        }()
        if animate, sameKind, !state.reducedMotion { targetRig = rig } else { self.rig = rig; targetRig = nil }
        applyCamera()
    }

    var currentRig: CameraRig? { targetRig ?? rig }

    private func applyCamera() {
        switch rig {
        case let .orbit(c):
            camera.camera.fieldOfViewInDegrees = 40
            camera.look(at: c.target, from: c.eye, relativeTo: nil)
        case let .interior(c):
            camera.camera.fieldOfViewInDegrees = c.fovDeg
            camera.camera.near = 0.02
            camera.look(at: c.lookAt, from: c.eye, relativeTo: nil)
        case nil: break
        }
    }

    private func frame(dt: Float) {
        animator.step(dt: dt, reducedMotion: state.reducedMotion)
        for (node, original) in originalTransforms {
            entities[node]?.transform = Transform(matrix: animator.localTransform(node, original: original))
        }
        if case let .orbit(target)? = targetRig, case let .orbit(cur)? = rig {
            let k = min(max(dt * 6, 0), 1)
            var dy = (target.yaw - cur.yaw).truncatingRemainder(dividingBy: 360)
            if dy > 180 { dy -= 360 }; if dy < -180 { dy += 360 }
            var n = cur
            n.yaw = cur.yaw + dy * k; n.pitch = cur.pitch + (target.pitch - cur.pitch) * k
            n.distance = cur.distance + (target.distance - cur.distance) * k
            n.target = cur.target + (target.target - cur.target) * k; n.presetTarget = target.presetTarget
            if abs(dy) < 0.05, abs(target.pitch - cur.pitch) < 0.05, abs(target.distance - cur.distance) < 0.005 { rig = targetRig; targetRig = nil } else { rig = .orbit(n) }
            applyCamera()
        }
    }

    // MARK: gestures

    private func installGestures() {
        let rotate = UIPanGestureRecognizer(target: self, action: #selector(onRotate(_:)))
        rotate.maximumNumberOfTouches = 1
        let pan = UIPanGestureRecognizer(target: self, action: #selector(onPan(_:)))
        pan.minimumNumberOfTouches = 2
        let pinch = UIPinchGestureRecognizer(target: self, action: #selector(onPinch(_:)))
        let tap = UITapGestureRecognizer(target: self, action: #selector(onTapGesture(_:)))
        for g in [rotate, pan, pinch, tap] as [UIGestureRecognizer] { g.delegate = self; arView.addGestureRecognizer(g) }
    }

    nonisolated func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

    private func degrees(_ px: CGFloat) -> Float { Float(px / max(arView.bounds.height, 1) * 180) }

    @objc private func onRotate(_ g: UIPanGestureRecognizer) {
        let t = g.translation(in: arView); g.setTranslation(.zero, in: arView)
        targetRig = nil
        switch rig {
        case let .orbit(c): rig = .orbit(c.rotated(dYaw: -degrees(t.x), dPitch: degrees(t.y)))
        case let .interior(c): rig = .interior(c.rotated(dYaw: -degrees(t.x) * 0.6, dPitch: -degrees(t.y) * 0.6))
        case nil: break
        }
        applyCamera()
        if g.state == .ended, let r = rig { onCameraChanged?(r) }
    }

    @objc private func onPan(_ g: UIPanGestureRecognizer) {
        guard case let .orbit(c)? = rig else { return }
        let t = g.translation(in: arView); g.setTranslation(.zero, in: arView)
        let metresPerPt = 2 * c.distance * tan(20 * .pi / 180) / Float(max(arView.bounds.height, 1))
        targetRig = nil
        rig = .orbit(c.panned(dx: Float(t.x) * metresPerPt, dy: Float(t.y) * metresPerPt))
        applyCamera()
    }

    @objc private func onPinch(_ g: UIPinchGestureRecognizer) {
        let s = Float(g.scale); g.scale = 1
        targetRig = nil
        switch rig {
        case let .orbit(c): rig = .orbit(c.zoomed(s))
        case let .interior(c): rig = .interior(c.zoomed(s))
        case nil: break
        }
        applyCamera()
    }

    @objc private func onTapGesture(_ g: UITapGestureRecognizer) {
        let p = g.location(in: arView)
        let names = arView.hitTest(p).compactMap { hit -> String? in
            var e: Entity? = hit.entity
            while let cur = e, cur.name.isEmpty { e = cur.parent }
            return e?.name
        }
        onTap?(names)
    }
}

/// SwiftUI host for the renderer. Non-3D alternatives for every action live in the surrounding screen.
struct VehicleRealityView: UIViewRepresentable {
    let renderer: RealityVehicleRenderer
    func makeUIView(context: Context) -> ARView { renderer.arView }
    func updateUIView(_ uiView: ARView, context: Context) {}
}
