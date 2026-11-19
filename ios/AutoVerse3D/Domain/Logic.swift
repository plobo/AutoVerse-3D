import CryptoKit
import Foundation
import simd

// MARK: - Offline content search (same algorithm as backend/src/content.js and Android ContentSearch)

struct SearchDocument: Sendable, Hashable { let id: String; let kind: String; let title: String; let text: String; var vehicleId: String? = nil }
struct SearchHit: Sendable, Hashable { let document: SearchDocument; let score: Int }

enum ContentSearch {
    static let stopwords: Set<String> = Set(("a an and are as at be by can do does for from how i in is it its me my of on or the this to what when "
        + "where which who why with you your").split(separator: " ").map(String.init))

    static func tokenize(_ text: String) -> [String] {
        var tokens: [String] = []
        var current = ""
        for scalar in text.lowercased().unicodeScalars {
            if ("a"..."z").contains(scalar) || ("0"..."9").contains(scalar) { current.unicodeScalars.append(scalar) }
            else if !current.isEmpty { tokens.append(current); current = "" }
        }
        if !current.isEmpty { tokens.append(current) }
        return tokens.filter { $0.count > 1 && !stopwords.contains($0) }
    }

    static func score(_ terms: [String], _ doc: SearchDocument) -> Int {
        let title = Set(tokenize(doc.title))
        let body = tokenize(doc.text)
        return Set(terms).reduce(0) { acc, term in acc + (title.contains(term) ? 3 : 0) + body.filter { $0 == term }.count }
    }

    static func search(_ docs: [SearchDocument], query: String, limit: Int = 4) -> [SearchHit] {
        let terms = tokenize(query)
        guard !terms.isEmpty else { return [] }
        return docs.map { SearchHit(document: $0, score: score(terms, $0)) }
            .filter { $0.score > 0 }
            .sorted { $0.score != $1.score ? $0.score > $1.score : $0.document.id < $1.document.id }
            .prefix(limit).map { $0 }
    }

    static func buildIndex(learning: LearningContent, components: ComponentCatalog, hotspots: HotspotCatalog, ev: EvLabContent) -> [SearchDocument] {
        var docs: [SearchDocument] = []
        for m in learning.modules { for s in m.sections { docs.append(.init(id: "learning:\(m.id):\(s.id)", kind: "learning", title: "\(m.title): \(s.heading)", text: s.body)) } }
        for c in components.components {
            docs.append(.init(id: "component:\(c.id)", kind: "component", title: c.name,
                              text: [c.description, c.purpose, c.detail].filter { !$0.isEmpty }.joined(separator: " "), vehicleId: c.vehicleId))
        }
        for h in hotspots.hotspots { docs.append(.init(id: "hotspot:\(h.id)", kind: "hotspot", title: h.title, text: "\(h.summary) \(h.detail)", vehicleId: hotspots.vehicleId)) }
        for n in ev.nodes { docs.append(.init(id: "ev:\(n.id)", kind: "ev", title: n.title, text: n.summary)) }
        return docs
    }
}

// MARK: - Learning

struct QuizResult: Sendable, Equatable {
    static let passPercent = 70
    let correct: Int
    let total: Int
    var percent: Int { total == 0 ? 0 : correct * 100 / total }
    var passed: Bool { total > 0 && percent >= Self.passPercent }
}

enum QuizScoring {
    static func score(_ module: LearningModule, answers: [String: Int]) -> QuizResult {
        QuizResult(correct: module.quiz.filter { answers[$0.id] == $0.correctIndex }.count, total: module.quiz.count)
    }
}

struct ModuleProgressValue: Sendable, Equatable {
    var moduleId: String
    var sectionsRead: Set<String> = []
    var bestQuizPercent: Int? = nil
    var completed = false
    var contentVersion = ""
}

enum LearningProgress {
    static func markSectionRead(_ p: ModuleProgressValue, module: LearningModule, sectionId: String) -> ModuleProgressValue {
        var n = p
        n.sectionsRead.insert(sectionId)
        n.completed = isComplete(module, read: n.sectionsRead, best: n.bestQuizPercent)
        return n
    }

    static func recordQuiz(_ p: ModuleProgressValue, module: LearningModule, result: QuizResult) -> ModuleProgressValue {
        var n = p
        n.bestQuizPercent = max(p.bestQuizPercent ?? 0, result.percent)
        n.completed = isComplete(module, read: n.sectionsRead, best: n.bestQuizPercent)
        return n
    }

    static func isComplete(_ m: LearningModule, read: Set<String>, best: Int?) -> Bool {
        m.sections.allSatisfy { read.contains($0.id) } && (m.quiz.isEmpty || (best ?? 0) >= QuizResult.passPercent)
    }

    static func overallPercent(_ modules: [LearningModule], progress: [String: ModuleProgressValue]) -> Int {
        guard !modules.isEmpty else { return 0 }
        return modules.filter { progress[$0.id]?.completed == true }.count * 100 / modules.count
    }
}

// MARK: - Assets, checksums and downloads

enum Checksum {
    static func sha256(_ data: Data) -> String {
        "sha256:" + SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    static func sha256(fileAt url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hasher = SHA256()
        while let chunk = try handle.read(upToCount: 64 * 1024), !chunk.isEmpty { hasher.update(data: chunk) }
        return "sha256:" + hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}

enum SemVer {
    static func compare(_ a: String, _ b: String) -> Int {
        let pa = a.split(separator: ".").map { Int($0) ?? 0 }, pb = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(pa.count, pb.count) {
            let x = i < pa.count ? pa[i] : 0, y = i < pb.count ? pb[i] : 0
            if x != y { return x < y ? -1 : 1 }
        }
        return 0
    }
}

enum ManifestIssue: Equatable, Sendable {
    case invalidField(String, String)
    case appTooOld(required: String, current: String)
    case vehicleMismatch(expected: String, actual: String)
}

enum AssetManifestValidator {
    static func fileFor(_ m: AssetManifest, platform: String) -> (name: String, checksum: String, size: Int64) {
        if let v = m.platformVariants[platform] { return (v.fileName, v.checksum, v.fileSizeBytes) }
        return (m.fileName, m.checksum, m.fileSizeBytes)
    }

    static func validate(_ m: AssetManifest, appVersion: String, expectedVehicleId: String? = nil, platform: String = "ios") -> [ManifestIssue] {
        var issues: [ManifestIssue] = []
        if m.id.range(of: "^[a-z0-9_]+_v[0-9]+$", options: .regularExpression) == nil { issues.append(.invalidField("id", "must match <name>_v<version>")) }
        if m.version < 1 { issues.append(.invalidField("version", "must be >= 1")) }
        if m.schemaVersion != 1 { issues.append(.invalidField("schemaVersion", "unsupported")) }
        let f = fileFor(m, platform: platform)
        if f.name.isEmpty || f.name.contains("/") || f.name.contains("..") { issues.append(.invalidField("fileName", "unsafe file name")) }
        if f.checksum.range(of: "^sha256:[0-9a-f]{64}$", options: .regularExpression) == nil { issues.append(.invalidField("checksum", "must be sha256:<64 hex>")) }
        if f.size <= 0 { issues.append(.invalidField("fileSizeBytes", "must be positive")) }
        if SemVer.compare(appVersion, m.minimumAppVersion) < 0 { issues.append(.appTooOld(required: m.minimumAppVersion, current: appVersion)) }
        if let e = expectedVehicleId, e != m.vehicleId { issues.append(.vehicleMismatch(expected: e, actual: m.vehicleId)) }
        return issues
    }
}

enum DownloadState: Equatable, Sendable {
    case notDownloaded
    case available(totalBytes: Int64, version: Int)
    case downloading(downloaded: Int64, total: Int64)
    case paused(downloaded: Int64, total: Int64)
    case verifying(total: Int64)
    case installed(version: Int, sizeBytes: Int64)
    case failed(reason: String, downloaded: Int64, total: Int64)

    var progress: Double {
        if case let .downloading(d, t) = self, t > 0 { return min(1, Double(d) / Double(t)) }
        return 0
    }
}

enum DownloadEvent: Sendable {
    case manifestLoaded(totalBytes: Int64, version: Int)
    case start, pause, resume, transferComplete, remove
    case progress(Int64)
    case verified(version: Int)
    case error(String)
}

enum DownloadStateMachine {
    static func reduce(_ s: DownloadState, _ e: DownloadEvent) -> DownloadState {
        switch (s, e) {
        case let (.installed(v, _), .manifestLoaded(total, newV)): return newV > v ? .available(totalBytes: total, version: newV) : s
        case (.downloading, .manifestLoaded), (.verifying, .manifestLoaded), (.paused, .manifestLoaded): return s
        case let (_, .manifestLoaded(total, v)): return .available(totalBytes: total, version: v)
        case let (.available(total, _), .start): return .downloading(downloaded: 0, total: total)
        case let (.failed(_, d, t), .start): return .downloading(downloaded: d, total: t)
        case let (.downloading(_, t), .progress(d)): return .downloading(downloaded: min(d, t), total: t)
        case let (.downloading(d, t), .pause): return .paused(downloaded: d, total: t)
        case let (.paused(d, t), .resume): return .downloading(downloaded: d, total: t)
        case let (.downloading(_, t), .transferComplete): return .verifying(total: t)
        case let (.verifying(t), .verified(v)): return .installed(version: v, sizeBytes: t)
        case let (.downloading(d, t), .error(r)): return .failed(reason: r, downloaded: d, total: t)
        case let (.verifying(t), .error(r)): return .failed(reason: r, downloaded: 0, total: t) // bad file discarded
        case (.installed, .remove): return .notDownloaded
        default: return s
        }
    }
}

func formatBytes(_ bytes: Int64) -> String {
    if bytes >= 1_000_000 { return String(format: "%.1f MB", Double(bytes) / 1_000_000) }
    if bytes >= 1_000 { return String(format: "%.0f KB", Double(bytes) / 1_000) }
    return "\(bytes) B"
}

// MARK: - Scene graph, visibility and cameras

struct SceneGraph: Sendable {
    private let children: [String: [String]]
    private let parents: [String: String]
    let materialByMesh: [String: String]

    init(_ d: SceneDescriptor) {
        var c: [String: [String]] = [:], p: [String: String] = [:], m: [String: String] = [:]
        for n in d.nodes {
            if let parent = n.parent { c[parent, default: []].append(n.name); p[n.name] = parent }
            if let mat = n.material { m[n.name] = mat }
        }
        children = c; parents = p; materialByMesh = m
    }

    func descendantsAndSelf(_ name: String) -> Set<String> {
        var out = Set<String>(), stack = [name]
        while let n = stack.popLast() { if out.insert(n).inserted { stack.append(contentsOf: children[n] ?? []) } }
        return out
    }

    func meshesUsing(_ material: String) -> [String] { materialByMesh.filter { $0.value == material }.map(\.key) }

    func nearestAncestor(of name: String, in candidates: Set<String>) -> String? {
        var cur: String? = name
        while let c = cur { if candidates.contains(c) { return c }; cur = parents[c] }
        return nil
    }
}

enum VisibilityRules {
    static func hiddenNodes(mode: ViewerMode, catalog: ComponentCatalog, graph: SceneGraph, activeWheelSuffix: String, allWheelSuffixes: [String]) -> Set<String> {
        var hidden = Set<String>()
        for n in catalog.modeVisibility[mode.visibilityKey]?.hide ?? [] { hidden.formUnion(graph.descendantsAndSelf(n)) }
        for wheel in ["FrontLeft", "FrontRight", "RearLeft", "RearRight"] {
            for s in allWheelSuffixes where s != activeWheelSuffix { hidden.formUnion(graph.descendantsAndSelf("\(wheel)_\(s)")) }
        }
        return hidden
    }
}

private func rad(_ d: Float) -> Float { d * .pi / 180 }
private func deg(_ r: Float) -> Float { r * 180 / .pi }

struct OrbitCamera: Equatable, Sendable {
    var yaw: Float, pitch: Float, distance: Float
    var target: SIMD3<Float>
    var presetTarget: SIMD3<Float>
    let limits: OrbitLimits

    init(preset: OrbitPreset, limits: OrbitLimits) {
        yaw = preset.yaw; pitch = preset.pitch; distance = preset.distance
        target = SIMD3(preset.target[0], preset.target[1], preset.target[2]); presetTarget = target
        self.limits = limits
    }

    var eye: SIMD3<Float> {
        let cp = cos(rad(pitch))
        return target + distance * SIMD3(cp * sin(rad(yaw)), sin(rad(pitch)), cp * cos(rad(yaw)))
    }

    func rotated(dYaw: Float, dPitch: Float) -> OrbitCamera {
        var c = self
        c.yaw = (yaw + dYaw).truncatingRemainder(dividingBy: 360); if c.yaw < 0 { c.yaw += 360 }
        c.pitch = min(max(pitch + dPitch, limits.minPitch), limits.maxPitch)
        return c
    }

    func zoomed(_ scale: Float) -> OrbitCamera {
        var c = self
        c.distance = min(max(distance / max(scale, 0.01), limits.minDistance), limits.maxDistance)
        return c
    }

    func panned(dx: Float, dy: Float) -> OrbitCamera {
        let right = SIMD3<Float>(cos(rad(yaw)), 0, -sin(rad(yaw)))
        var off = (target - right * dx + SIMD3(0, dy, 0)) - presetTarget
        let len = simd_length(off)
        if len > limits.maxPan { off *= limits.maxPan / len }
        var c = self
        c.target = presetTarget + off
        return c
    }
}

struct InteriorCamera: Equatable, Sendable {
    let eye: SIMD3<Float>
    let baseYaw: Float, basePitch: Float
    var yawOffset: Float = 0, pitchOffset: Float = 0
    let maxYawOffset: Float, maxPitchOffset: Float
    var fovDeg: Float = 70

    init(preset: InteriorPreset, maxYaw: Float, maxPitch: Float) {
        eye = SIMD3(preset.eye[0], preset.eye[1], preset.eye[2])
        let d = SIMD3(preset.lookAt[0], preset.lookAt[1], preset.lookAt[2]) - eye
        baseYaw = deg(atan2(d.x, d.z)); basePitch = deg(asin(d.y / simd_length(d)))
        maxYawOffset = maxYaw; maxPitchOffset = maxPitch
    }

    var lookAt: SIMD3<Float> {
        let y = rad(baseYaw + yawOffset), p = rad(basePitch + pitchOffset)
        return eye + SIMD3(cos(p) * sin(y), sin(p), cos(p) * cos(y))
    }

    func rotated(dYaw: Float, dPitch: Float) -> InteriorCamera {
        var c = self
        c.yawOffset = min(max(yawOffset + dYaw, -maxYawOffset), maxYawOffset)
        c.pitchOffset = min(max(pitchOffset + dPitch, -maxPitchOffset), maxPitchOffset)
        return c
    }

    func zoomed(_ s: Float) -> InteriorCamera { var c = self; c.fovDeg = min(max(fovDeg / s, 35), 85); return c }
}

enum CameraRig: Equatable, Sendable {
    case orbit(OrbitCamera)
    case interior(InteriorCamera)
}

enum Easing { static func smoothstep(_ t: Float) -> Float { let x = min(max(t, 0), 1); return x * x * (3 - 2 * x) } }

/// Drives door/hood/tailgate animations and exploded views; renderer-agnostic.
struct PartAnimator: Sendable {
    let catalog: ComponentCatalog
    private var progress: [String: Float] = [:]
    private var targets: [String: Float] = [:]
    private var durations: [String: Float] = [:]

    init(catalog: ComponentCatalog) { self.catalog = catalog }

    var animatedNodes: Set<String> {
        Set(catalog.animations.map(\.nodeName)).union(catalog.explodedViews.flatMap { $0.offsets.map(\.nodeName) })
    }

    mutating func setPartOpen(_ id: String, open: Bool) {
        guard let a = catalog.animations.first(where: { $0.id == id }) else { return }
        targets["anim:\(id)"] = open ? 1 : 0; durations["anim:\(id)"] = a.durationSec
    }

    mutating func setExploded(_ viewId: String?) {
        for v in catalog.explodedViews { targets["explode:\(v.id)"] = v.id == viewId ? 1 : 0; durations["explode:\(v.id)"] = v.durationSec }
    }

    mutating func resetAll() { for k in targets.keys { targets[k] = 0 } }

    @discardableResult
    mutating func step(dt: Float, reducedMotion: Bool) -> Bool {
        var moving = false
        for (k, target) in targets {
            let cur = progress[k] ?? 0
            if cur == target { continue }
            let next: Float
            if reducedMotion { next = target } else {
                let delta = dt / (durations[k] ?? 1)
                next = target > cur ? min(target, cur + delta) : max(target, cur - delta)
            }
            progress[k] = next
            if next != target { moving = true }
        }
        return moving
    }

    func isOpen(_ id: String) -> Bool { (targets["anim:\(id)"] ?? 0) > 0.5 }

    /// translate(explode offset) * original * rotate(part animation)
    func localTransform(_ node: String, original: simd_float4x4) -> simd_float4x4 {
        var offset = SIMD3<Float>(repeating: 0)
        for v in catalog.explodedViews {
            let p = Easing.smoothstep(progress["explode:\(v.id)"] ?? 0)
            if p > 0, let o = v.offsets.first(where: { $0.nodeName == node }) { offset += SIMD3(o.offset[0], o.offset[1], o.offset[2]) * p }
        }
        var m = original
        for a in catalog.animations where a.nodeName == node {
            let p = Easing.smoothstep(progress["anim:\(a.id)"] ?? 0)
            if p > 0 { m = m * simd_float4x4(simd_quatf(angle: rad(a.angleDeg * p), axis: simd_normalize(SIMD3(a.axis[0], a.axis[1], a.axis[2])))) }
        }
        if simd_length(offset) > 0 {
            var t = matrix_identity_float4x4
            t.columns.3 = SIMD4(offset.x, offset.y, offset.z, 1)
            m = t * m
        }
        return m
    }
}

// MARK: - Configurations

struct DesignSelection: Equatable, Sendable {
    var paintId: String, wheelId: String, trimId: String, interiorId: String

    static func defaults(_ o: CustomizationOptions) -> DesignSelection {
        .init(paintId: o.defaults.paintId, wheelId: o.defaults.wheelId, trimId: o.defaults.trimId, interiorId: o.defaults.interiorId)
    }

    static func of(_ c: VehicleConfiguration) -> DesignSelection { .init(paintId: c.paintId, wheelId: c.wheelId, trimId: c.trimId, interiorId: c.interiorId) }
}

struct ConfigurationDifference: Equatable, Sendable { let field: String; let saved: String; let current: String }

enum NameValidation: Equatable { case valid, empty, tooLong }

enum ConfigurationRules {
    static let maxNameLength = 60

    static func validateName(_ n: String) -> NameValidation {
        let t = n.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return .empty }
        return t.count > maxNameLength ? .tooLong : .valid
    }

    static func create(id: String, vehicleId: String, name: String, selection s: DesignSelection, now: String) -> VehicleConfiguration {
        VehicleConfiguration(id: id, vehicleId: vehicleId, name: name.trimmingCharacters(in: .whitespacesAndNewlines), paintId: s.paintId,
                             wheelId: s.wheelId, trimId: s.trimId, interiorId: s.interiorId, createdAt: now, updatedAt: now)
    }

    static func duplicateName(_ base: String, existing: Set<String>) -> String {
        let root = base.replacingOccurrences(of: " copy( \\d+)?$", with: "", options: .regularExpression)
        var candidate = "\(root) copy", n = 2
        while existing.contains(candidate) { candidate = "\(root) copy \(n)"; n += 1 }
        return String(candidate.prefix(maxNameLength))
    }

    static func compare(saved: VehicleConfiguration, current: DesignSelection, options o: CustomizationOptions) -> [ConfigurationDifference] {
        func name(_ list: [(String, String)], _ id: String) -> String { list.first { $0.0 == id }?.1 ?? id }
        let paints = o.paints.map { ($0.id, $0.name) }, wheels = o.wheels.map { ($0.id, $0.name) }
        let trims = o.trims.map { ($0.id, $0.name) }, interiors = o.interiors.map { ($0.id, $0.name) }
        var d: [ConfigurationDifference] = []
        if saved.paintId != current.paintId { d.append(.init(field: "Paint", saved: name(paints, saved.paintId), current: name(paints, current.paintId))) }
        if saved.wheelId != current.wheelId { d.append(.init(field: "Wheels", saved: name(wheels, saved.wheelId), current: name(wheels, current.wheelId))) }
        if saved.trimId != current.trimId { d.append(.init(field: "Trim", saved: name(trims, saved.trimId), current: name(trims, current.trimId))) }
        if saved.interiorId != current.interiorId { d.append(.init(field: "Interior", saved: name(interiors, saved.interiorId), current: name(interiors, current.interiorId))) }
        return d
    }

    static func sanitize(_ s: DesignSelection, options o: CustomizationOptions) -> DesignSelection {
        DesignSelection(
            paintId: o.paints.contains { $0.id == s.paintId } ? s.paintId : o.defaults.paintId,
            wheelId: o.wheels.contains { $0.id == s.wheelId } ? s.wheelId : o.defaults.wheelId,
            trimId: o.trims.contains { $0.id == s.trimId } ? s.trimId : o.defaults.trimId,
            interiorId: o.interiors.contains { $0.id == s.interiorId } ? s.interiorId : o.defaults.interiorId)
    }
}

enum ColorUtil {
    static func hexToSRGB(_ hex: String) -> SIMD3<Float> {
        let h = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        let v = UInt32(h, radix: 16) ?? 0
        return SIMD3(Float((v >> 16) & 0xFF) / 255, Float((v >> 8) & 0xFF) / 255, Float(v & 0xFF) / 255)
    }

    static func srgbToLinear(_ c: Float) -> Float { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
}

enum ISO8601 {
    static func now() -> String { ISO8601DateFormatter().string(from: Date()) }
}
