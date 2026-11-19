import Foundation

// Swift mirrors of the versioned JSON contracts in /shared (docs/DATA_MODELS.md).

struct VehicleCatalog: Codable, Sendable {
    let schemaVersion: Int
    let contentVersion: String
    let vehicles: [Vehicle]
}

struct Spec: Codable, Sendable, Hashable { let label: String; let value: String }

struct Vehicle: Codable, Sendable, Identifiable, Hashable {
    let id: String
    let name: String
    let category: String
    let categoryLabel: String
    let description: String
    let assetManifestId: String?
    let featureIds: [String]
    let availability: String
    let specs: [Spec]
    let specsDisclaimer: String
    let schemaVersion: Int

    var isAvailable: Bool { availability == "available" && assetManifestId != nil }
}

struct VehicleComponent: Codable, Sendable, Identifiable, Hashable {
    let id: String
    let vehicleId: String
    let module: String
    let name: String
    let category: String
    let nodeName: String
    let description: String
    let purpose: String
    let detail: String
    let animationIds: [String]
    let schemaVersion: Int
}

struct PartAnimation: Codable, Sendable, Hashable {
    let id: String
    let type: String
    let nodeName: String
    let axis: [Float]
    let angleDeg: Float
    let durationSec: Float
}

struct NodeOffset: Codable, Sendable, Hashable { let nodeName: String; let offset: [Float] }

struct ExplodedView: Codable, Sendable, Hashable, Identifiable {
    let id: String
    let name: String
    let durationSec: Float
    let offsets: [NodeOffset]
}

struct ModeVisibility: Codable, Sendable { let hide: [String] }

struct ComponentCatalog: Codable, Sendable {
    let schemaVersion: Int
    let vehicleId: String
    let educationalDisclaimer: String
    let components: [VehicleComponent]
    let animations: [PartAnimation]
    let explodedViews: [ExplodedView]
    let modeVisibility: [String: ModeVisibility]
}

struct Hotspot: Codable, Sendable, Identifiable, Hashable {
    let id: String
    let module: String
    let title: String
    let position: [Float]
    let summary: String
    let detail: String
    var nodeName: String { "Hotspot_\(id)" }
}

struct HotspotCatalog: Codable, Sendable {
    let schemaVersion: Int
    let vehicleId: String
    let hotspots: [Hotspot]
    let safetyDisclaimer: String
}

struct ColorOption: Codable, Sendable, Identifiable, Hashable {
    let id: String
    let name: String
    let hex: String
    let metallic: Float
    let roughness: Float
}

struct WheelOption: Codable, Sendable, Identifiable, Hashable {
    let id: String
    let name: String
    let description: String
    let nodeSuffix: String
}

struct CustomizationDefaults: Codable, Sendable, Hashable {
    let paintId: String; let wheelId: String; let trimId: String; let interiorId: String
}

struct CustomizationOptions: Codable, Sendable {
    let schemaVersion: Int
    let vehicleId: String
    let defaults: CustomizationDefaults
    let paints: [ColorOption]
    let wheels: [WheelOption]
    let trims: [ColorOption]
    let interiors: [ColorOption]
}

struct OrbitLimits: Codable, Sendable, Hashable {
    let minDistance: Float; let maxDistance: Float; let minPitch: Float; let maxPitch: Float; let maxPan: Float
}

struct OrbitPreset: Codable, Sendable, Identifiable, Hashable {
    let id: String; let name: String; let yaw: Float; let pitch: Float; let distance: Float; let target: [Float]
}

struct OrbitPresets: Codable, Sendable { let limits: OrbitLimits; let presets: [OrbitPreset]; let `default`: String }

struct InteriorLimits: Codable, Sendable { let maxYawOffset: Float; let maxPitchOffset: Float }

struct InteriorPreset: Codable, Sendable, Identifiable, Hashable {
    let id: String; let name: String; let eye: [Float]; let lookAt: [Float]
}

struct InteriorPresets: Codable, Sendable { let limits: InteriorLimits; let presets: [InteriorPreset]; let `default`: String }

struct CameraPresets: Codable, Sendable { let schemaVersion: Int; let orbit: OrbitPresets; let interior: InteriorPresets }

struct QuizQuestion: Codable, Sendable, Identifiable, Hashable {
    let id: String; let prompt: String; let choices: [String]; let correctIndex: Int; let explanation: String
}

struct LearningSection: Codable, Sendable, Identifiable, Hashable { let id: String; let heading: String; let body: String }

struct LearningModule: Codable, Sendable, Identifiable, Hashable {
    let id: String
    let title: String
    let summary: String
    let estimatedMinutes: Int
    let relatedComponentIds: [String]
    let sections: [LearningSection]
    let quiz: [QuizQuestion]
}

struct LearningContent: Codable, Sendable {
    let schemaVersion: Int
    let contentVersion: String
    let disclaimer: String
    let modules: [LearningModule]
}

struct EvNode: Codable, Sendable, Identifiable, Hashable { let id: String; let title: String; let x: Float; let y: Float; let summary: String }

struct EvMode: Codable, Sendable, Identifiable, Hashable { let id: String; let title: String; let flows: [[String]]; let textAlternative: String }

struct EvLabContent: Codable, Sendable { let schemaVersion: Int; let contentVersion: String; let disclaimer: String; let nodes: [EvNode]; let modes: [EvMode] }

struct AssetVariant: Codable, Sendable, Hashable { let format: String; let fileName: String; let fileSizeBytes: Int64; let checksum: String }

struct AssetManifest: Codable, Sendable, Hashable {
    let id: String
    let vehicleId: String
    let version: Int
    let format: String
    let fileName: String
    let fileSizeBytes: Int64
    let checksum: String
    let minimumAppVersion: String
    let licenceRecordId: String
    let platformVariants: [String: AssetVariant]
    let sceneDescriptor: AssetVariant
    let triangleCount: Int?
    let schemaVersion: Int
    let downloadUrls: [String: String]?
}

struct SceneNode: Codable, Sendable, Hashable {
    let name: String
    let parent: String?
    let kind: String
    let pivot: [Float]
    let material: String?
    let triangles: Int?
}

struct SceneMaterial: Codable, Sendable, Hashable {
    let baseColorHex: String; let metallic: Float; let roughness: Float; let emissiveHex: String?; let opacity: Float
}

struct SceneDescriptor: Codable, Sendable {
    let schemaVersion: Int
    let assetId: String
    let triangleCount: Int
    let nodes: [SceneNode]
    let materials: [String: SceneMaterial]
}

/// shared/schemas/configuration.schema.json
struct VehicleConfiguration: Codable, Sendable, Hashable, Identifiable {
    var id: String
    var vehicleId: String
    var name: String
    var paintId: String
    var wheelId: String
    var trimId: String
    var interiorId: String
    var createdAt: String
    var updatedAt: String
    var schemaVersion: Int = 1
}

struct AssistantRequest: Codable, Sendable { let question: String; let vehicleId: String?; let componentId: String? }

struct AssistantSource: Codable, Sendable, Hashable { let id: String; let title: String; let kind: String }

struct AssistantResponse: Codable, Sendable {
    let answer: String
    let sources: [AssistantSource]
    let scope: String
    let safetyNotice: String?
    let refused: Bool
    let provider: String
    let disclaimer: String
}

enum ViewerMode: String, CaseIterable, Identifiable, Sendable {
    case exterior, interior, mechanical, safety, customize
    var id: String { rawValue }
    var title: String { rawValue.capitalized }
    /// Key into ComponentCatalog.modeVisibility.
    var visibilityKey: String { self == .customize ? "exterior" : rawValue }
}

enum ContentParser {
    static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        try JSONDecoder().decode(type, from: data)
    }
}
