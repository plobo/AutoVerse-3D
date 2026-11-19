import Foundation
import Observation
import SwiftData
import UIKit

/// Lightweight protocol-based dependency container, created once at launch and passed through the environment.
@MainActor
@Observable
final class AppDependencies {
    let content: BundleContentRepository
    let api: APIClient
    let configurations: ConfigurationStore
    let progress: ProgressStore
    let assets: AssetRepository
    let assistant: AssistantRepository
    /// Bumped whenever persisted data changes so observing screens refresh.
    var dataVersion = 0

    init(container: ModelContainer, content: BundleContentRepository = BundleContentRepository(), api: APIClient = .fromBundle()) {
        self.content = content
        self.api = api
        let context = container.mainContext
        configurations = ConfigurationStore(context: context)
        progress = ProgressStore(context: context)
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.0"
        assets = AssetRepository(content: content, manifestService: api, baseURL: api.baseURL, context: context, appVersion: version)
        assistant = AssistantRepository(service: api, index: (try? content.content().searchIndex) ?? [])
    }

    func touch() { dataVersion += 1 }

    /// Last explored vehicle/mode for "Continue exploring" (non-personal preference).
    var lastExplored: (vehicleId: String, mode: String)? {
        get {
            access(keyPath: \.dataVersion)
            guard let v = UserDefaults.standard.string(forKey: "lastVehicle") else { return nil }
            return (v, UserDefaults.standard.string(forKey: "lastMode") ?? "exterior")
        }
    }

    func recordExplored(vehicleId: String, mode: ViewerMode) {
        UserDefaults.standard.set(vehicleId, forKey: "lastVehicle")
        UserDefaults.standard.set(mode.rawValue, forKey: "lastMode")
    }
}
