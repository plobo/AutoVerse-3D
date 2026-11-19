import Foundation
import SwiftData

@Model
final class SavedConfiguration {
    @Attribute(.unique) var id: String
    var vehicleId: String
    var name: String
    var paintId: String
    var wheelId: String
    var trimId: String
    var interiorId: String
    var createdAt: String
    var updatedAt: String
    var schemaVersion: Int

    init(_ c: VehicleConfiguration) {
        id = c.id; vehicleId = c.vehicleId; name = c.name; paintId = c.paintId; wheelId = c.wheelId
        trimId = c.trimId; interiorId = c.interiorId; createdAt = c.createdAt; updatedAt = c.updatedAt; schemaVersion = c.schemaVersion
    }

    var value: VehicleConfiguration {
        VehicleConfiguration(id: id, vehicleId: vehicleId, name: name, paintId: paintId, wheelId: wheelId, trimId: trimId,
                             interiorId: interiorId, createdAt: createdAt, updatedAt: updatedAt, schemaVersion: schemaVersion)
    }

    func update(from c: VehicleConfiguration) {
        name = c.name; paintId = c.paintId; wheelId = c.wheelId; trimId = c.trimId; interiorId = c.interiorId; updatedAt = c.updatedAt
    }
}

@Model
final class ModuleProgressRecord {
    @Attribute(.unique) var moduleId: String
    var sectionsRead: [String]
    var bestQuizPercent: Int?
    var completed: Bool
    var contentVersion: String

    init(moduleId: String) { self.moduleId = moduleId; sectionsRead = []; bestQuizPercent = nil; completed = false; contentVersion = "" }

    var value: ModuleProgressValue {
        ModuleProgressValue(moduleId: moduleId, sectionsRead: Set(sectionsRead), bestQuizPercent: bestQuizPercent, completed: completed, contentVersion: contentVersion)
    }

    func apply(_ v: ModuleProgressValue) {
        sectionsRead = v.sectionsRead.sorted(); bestQuizPercent = v.bestQuizPercent; completed = v.completed; contentVersion = v.contentVersion
    }
}

/// A downloaded, checksum-verified vehicle asset pack.
@Model
final class InstalledPack {
    @Attribute(.unique) var manifestId: String
    var vehicleId: String
    var version: Int
    var fileName: String
    var sceneFileName: String
    var sizeBytes: Int64
    var checksum: String
    var installedAt: String

    init(manifestId: String, vehicleId: String, version: Int, fileName: String, sceneFileName: String, sizeBytes: Int64, checksum: String, installedAt: String) {
        self.manifestId = manifestId; self.vehicleId = vehicleId; self.version = version; self.fileName = fileName
        self.sceneFileName = sceneFileName; self.sizeBytes = sizeBytes; self.checksum = checksum; self.installedAt = installedAt
    }
}

enum Persistence {
    // Computed: SwiftData's Schema is not Sendable on older SDKs (Xcode 16), so it cannot be a stored static.
    static var schema: Schema { Schema([SavedConfiguration.self, ModuleProgressRecord.self, InstalledPack.self]) }

    @MainActor
    static func makeContainer(inMemory: Bool = false) throws -> ModelContainer {
        try ModelContainer(for: schema, configurations: [ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)])
    }
}

/// SwiftData-backed repositories (all on the main actor, like the UI that observes them).
@MainActor
final class ConfigurationStore {
    private let context: ModelContext
    init(context: ModelContext) { self.context = context }

    func all() -> [VehicleConfiguration] {
        ((try? context.fetch(FetchDescriptor<SavedConfiguration>(sortBy: [SortDescriptor(\.updatedAt, order: .reverse)]))) ?? []).map(\.value)
    }

    func get(_ id: String) -> VehicleConfiguration? {
        (try? context.fetch(FetchDescriptor<SavedConfiguration>(predicate: #Predicate { $0.id == id })))?.first?.value
    }

    func save(_ c: VehicleConfiguration) {
        let id = c.id
        if let existing = (try? context.fetch(FetchDescriptor<SavedConfiguration>(predicate: #Predicate { $0.id == id })))?.first {
            existing.update(from: c)
        } else {
            context.insert(SavedConfiguration(c))
        }
        try? context.save()
    }

    func delete(_ id: String) {
        try? context.delete(model: SavedConfiguration.self, where: #Predicate { $0.id == id })
        try? context.save()
    }
}

@MainActor
final class ProgressStore {
    private let context: ModelContext
    init(context: ModelContext) { self.context = context }

    func all() -> [String: ModuleProgressValue] {
        Dictionary(uniqueKeysWithValues: ((try? context.fetch(FetchDescriptor<ModuleProgressRecord>())) ?? []).map { ($0.moduleId, $0.value) })
    }

    func get(_ id: String) -> ModuleProgressValue { all()[id] ?? ModuleProgressValue(moduleId: id) }

    func save(_ v: ModuleProgressValue) {
        let id = v.moduleId
        let record = (try? context.fetch(FetchDescriptor<ModuleProgressRecord>(predicate: #Predicate { $0.moduleId == id })))?.first ?? {
            let r = ModuleProgressRecord(moduleId: id); context.insert(r); return r
        }()
        record.apply(v)
        try? context.save()
    }
}
