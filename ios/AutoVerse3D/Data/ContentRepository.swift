import Foundation

/// All offline content bundled with the app (copied from /shared at build time).
struct AppContent: Sendable {
    let vehicles: VehicleCatalog
    let components: ComponentCatalog
    let hotspots: HotspotCatalog
    let customization: CustomizationOptions
    let cameras: CameraPresets
    let learning: LearningContent
    let evLab: EvLabContent
    let bundledManifests: [String: AssetManifest]
    let searchIndex: [SearchDocument]
}

protocol ContentProviding: Sendable {
    func content() throws -> AppContent
}

enum ContentError: Error, LocalizedError {
    case missing(String)
    var errorDescription: String? { if case let .missing(p) = self { return "Missing bundled content: \(p)" }; return nil }
}

/// Reads shared JSON from the app bundle (folder references "data", "content", "build", "manifests").
final class BundleContentRepository: ContentProviding, @unchecked Sendable {
    private let bundle: Bundle
    private let lock = NSLock()
    private var cached: AppContent?

    init(bundle: Bundle = .main) { self.bundle = bundle }

    func url(_ path: String) throws -> URL {
        guard let url = bundle.resourceURL?.appendingPathComponent(path), FileManager.default.fileExists(atPath: url.path) else { throw ContentError.missing(path) }
        return url
    }

    private func decode<T: Decodable>(_ type: T.Type, _ path: String) throws -> T {
        try ContentParser.decode(type, from: Data(contentsOf: url(path)))
    }

    func content() throws -> AppContent {
        lock.lock(); defer { lock.unlock() }
        if let cached { return cached }
        let learning = try decode(LearningContent.self, "content/learning_modules.json")
        let components = try decode(ComponentCatalog.self, "data/components.json")
        let hotspots = try decode(HotspotCatalog.self, "data/hotspots.json")
        let ev = try decode(EvLabContent.self, "content/ev_lab.json")
        let manifestDir = try url("manifests")
        var manifests: [String: AssetManifest] = [:]
        for file in try FileManager.default.contentsOfDirectory(atPath: manifestDir.path) where file.hasSuffix(".json") {
            let m = try decode(AssetManifest.self, "manifests/\(file)")
            manifests[m.id] = m
        }
        let c = AppContent(
            vehicles: try decode(VehicleCatalog.self, "data/vehicles.json"), components: components, hotspots: hotspots,
            customization: try decode(CustomizationOptions.self, "data/customization_options.json"),
            cameras: try decode(CameraPresets.self, "data/camera_presets.json"), learning: learning, evLab: ev,
            bundledManifests: manifests, searchIndex: ContentSearch.buildIndex(learning: learning, components: components, hotspots: hotspots, ev: ev))
        cached = c
        return c
    }
}
