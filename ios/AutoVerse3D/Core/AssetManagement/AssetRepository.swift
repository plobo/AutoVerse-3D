import Foundation
import Observation
import SwiftData

struct ResolvedModel: Sendable {
    let modelURL: URL
    let studioURL: URL?
    let scene: SceneDescriptor
    let originLabel: String
}

/// Resolves which model to render (verified download first, bundled USDZ as the always-available baseline) and
/// manages downloadable packs: size before download, progress, pause/resume with HTTP Range, SHA-256 verification,
/// and removing an older pack only after its replacement is verified.
@MainActor
@Observable
final class AssetRepository {
    struct PackStatus: Equatable { var state: DownloadState = .notDownloaded; var message: String? = nil }

    private(set) var statuses: [String: PackStatus] = [:]
    @ObservationIgnored private var manifests: [String: AssetManifest] = [:]
    @ObservationIgnored private var tasks: [String: Task<Void, Never>] = [:]
    @ObservationIgnored private var taskTokens: [String: UUID] = [:]
    @ObservationIgnored private let content: BundleContentRepository
    @ObservationIgnored private let manifestService: ManifestService
    @ObservationIgnored private let baseURL: URL
    @ObservationIgnored private let context: ModelContext
    @ObservationIgnored private let appVersion: String

    init(content: BundleContentRepository, manifestService: ManifestService, baseURL: URL, context: ModelContext, appVersion: String) {
        self.content = content; self.manifestService = manifestService; self.baseURL = baseURL; self.context = context; self.appVersion = appVersion
    }

    static var packsRoot: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("packs", isDirectory: true)
    }

    private func dir(_ manifestId: String) -> URL {
        let d = Self.packsRoot.appendingPathComponent(manifestId, isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    func status(_ id: String) -> PackStatus { statuses[id] ?? PackStatus() }

    private func dispatch(_ id: String, _ e: DownloadEvent, message: String? = nil) {
        statuses[id] = PackStatus(state: DownloadStateMachine.reduce(status(id).state, e), message: message)
    }

    private func installed(for vehicleId: String) -> InstalledPack? {
        (try? context.fetch(FetchDescriptor<InstalledPack>(predicate: #Predicate { $0.vehicleId == vehicleId }, sortBy: [SortDescriptor(\.version, order: .reverse)])))?.first
    }

    func resolve(vehicleId: String, manifestId: String) throws -> ResolvedModel {
        let studio = try? content.url("build/studio_platform_v1.usdz")
        if let pack = installed(for: vehicleId) {
            let model = dir(pack.manifestId).appendingPathComponent(pack.fileName)
            let sceneURL = dir(pack.manifestId).appendingPathComponent(pack.sceneFileName)
            if FileManager.default.fileExists(atPath: model.path), let data = try? Data(contentsOf: sceneURL),
               let scene = try? ContentParser.decode(SceneDescriptor.self, from: data) {
                return ResolvedModel(modelURL: model, studioURL: studio, scene: scene, originLabel: "Downloaded pack v\(pack.version)")
            }
        }
        let c = try content.content()
        guard let m = c.bundledManifests[manifestId], let ios = m.platformVariants["ios"] else { throw ContentError.missing("manifest \(manifestId)") }
        let scene = try ContentParser.decode(SceneDescriptor.self, from: Data(contentsOf: content.url("build/\(m.sceneDescriptor.fileName)")))
        return ResolvedModel(modelURL: try content.url("build/\(ios.fileName)"), studioURL: studio, scene: scene, originLabel: "Built-in copy v\(m.version)")
    }

    func refresh(manifestId: String, vehicleId: String) async {
        if let pack = installed(for: vehicleId), status(manifestId).state == .notDownloaded {
            statuses[manifestId] = PackStatus(state: .installed(version: pack.version, sizeBytes: pack.sizeBytes))
        }
        do {
            let m = try await manifestService.manifest(id: manifestId)
            guard AssetManifestValidator.validate(m, appVersion: appVersion, expectedVehicleId: vehicleId).isEmpty, let ios = m.platformVariants["ios"] else {
                statuses[manifestId, default: PackStatus()].message = "This pack needs a newer app version or is invalid."
                return
            }
            manifests[manifestId] = m
            dispatch(manifestId, .manifestLoaded(totalBytes: ios.fileSizeBytes + m.sceneDescriptor.fileSizeBytes, version: m.version))
        } catch {
            statuses[manifestId, default: PackStatus()].message = "Offline — connect to the internet to download packs."
        }
    }

    func start(manifestId: String, vehicleId: String) {
        guard let m = manifests[manifestId], tasks[manifestId] == nil else { return }
        if case .paused = status(manifestId).state { dispatch(manifestId, .resume) } else { dispatch(manifestId, .start) }
        let token = UUID()
        taskTokens[manifestId] = token
        tasks[manifestId] = Task { [weak self] in await self?.download(m, vehicleId: vehicleId, token: token) }
    }

    func pause(manifestId: String) {
        tasks.removeValue(forKey: manifestId)?.cancel()
        taskTokens[manifestId] = nil
        dispatch(manifestId, .pause)
    }

    func remove(manifestId: String, vehicleId: String) async {
        tasks.removeValue(forKey: manifestId)?.cancel()
        try? context.delete(model: InstalledPack.self, where: #Predicate { $0.manifestId == manifestId })
        try? context.save()
        try? FileManager.default.removeItem(at: dir(manifestId))
        dispatch(manifestId, .remove)
        await refresh(manifestId: manifestId, vehicleId: vehicleId)
    }

    private func url(for path: String?, fallback: String) -> URL {
        URL(string: (path ?? fallback).trimmingCharacters(in: CharacterSet(charactersIn: "/")), relativeTo: baseURL)!.absoluteURL
    }

    private func download(_ m: AssetManifest, vehicleId: String, token: UUID) async {
        // Only clear our own entry: after pause + quick resume a newer task may already be registered.
        defer { if taskTokens[m.id] == token { tasks[m.id] = nil; taskTokens[m.id] = nil } }
        guard let ios = m.platformVariants["ios"] else { return }
        let d = dir(m.id)
        let modelPart = d.appendingPathComponent(ios.fileName + ".part"), scenePart = d.appendingPathComponent(m.sceneDescriptor.fileName + ".part")
        let modelURL = url(for: m.downloadUrls?["ios"], fallback: "v1/assets/\(m.id)/files/\(ios.fileName)")
        let sceneURL = url(for: m.downloadUrls?["sceneDescriptor"], fallback: "v1/assets/\(m.id)/files/\(m.sceneDescriptor.fileName)")
        do {
            try await RangeDownloader.fetch(sceneURL, to: scenePart) { _ in }
            let sceneSize = (try? FileManager.default.attributesOfItem(atPath: scenePart.path)[.size] as? Int64) ?? 0
            try await RangeDownloader.fetch(modelURL, to: modelPart) { [weak self] written in
                await self?.dispatch(m.id, .progress(written + sceneSize))
            }
            if Task.isCancelled { return }
            dispatch(m.id, .transferComplete)
            let ok = (try? Checksum.sha256(fileAt: modelPart)) == ios.checksum && (try? Checksum.sha256(fileAt: scenePart)) == m.sceneDescriptor.checksum
            guard ok else {
                try? FileManager.default.removeItem(at: modelPart); try? FileManager.default.removeItem(at: scenePart)
                dispatch(m.id, .error("Integrity check failed. The download was discarded — please retry."))
                return
            }
            let model = d.appendingPathComponent(ios.fileName), scene = d.appendingPathComponent(m.sceneDescriptor.fileName)
            try? FileManager.default.removeItem(at: model); try? FileManager.default.removeItem(at: scene)
            try FileManager.default.moveItem(at: modelPart, to: model)
            try FileManager.default.moveItem(at: scenePart, to: scene)
            let previous = installed(for: vehicleId)
            let id = m.id
            try? context.delete(model: InstalledPack.self, where: #Predicate { $0.manifestId == id })
            context.insert(InstalledPack(manifestId: m.id, vehicleId: vehicleId, version: m.version, fileName: ios.fileName, sceneFileName: m.sceneDescriptor.fileName,
                                         sizeBytes: ios.fileSizeBytes + m.sceneDescriptor.fileSizeBytes, checksum: ios.checksum, installedAt: ISO8601.now()))
            try? context.save()
            // Only after the replacement is verified and recorded is an older pack removed.
            if let previous, previous.manifestId != m.id {
                let old = previous.manifestId
                try? context.delete(model: InstalledPack.self, where: #Predicate { $0.manifestId == old })
                try? FileManager.default.removeItem(at: Self.packsRoot.appendingPathComponent(old))
            }
            dispatch(m.id, .verified(version: m.version))
        } catch is CancellationError {
            return
        } catch {
            if !Task.isCancelled { dispatch(m.id, .error("Download interrupted. Tap retry to resume.")) }
        }
    }
}

/// Streams a URL into a .part file, resuming with an HTTP Range header when a partial file exists.
enum RangeDownloader {
    static func fetch(_ url: URL, to part: URL, onProgress: @Sendable (Int64) async -> Void) async throws {
        let fm = FileManager.default
        let existing = (try? fm.attributesOfItem(atPath: part.path)[.size] as? Int64) ?? 0
        var request = URLRequest(url: url)
        if existing > 0 { request.setValue("bytes=\(existing)-", forHTTPHeaderField: "Range") }
        let (bytes, response) = try await URLSession.shared.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.transport }
        if http.statusCode == 416 { return }
        guard (200..<300).contains(http.statusCode) else { throw APIError.http(status: http.statusCode, retryAfter: nil) }
        let append = http.statusCode == 206
        if !append || !fm.fileExists(atPath: part.path) { fm.createFile(atPath: part.path, contents: nil) }
        let handle = try FileHandle(forWritingTo: part)
        defer { try? handle.close() }
        if append { try handle.seekToEnd() } else { try handle.truncate(atOffset: 0) }
        var written = append ? existing : 0
        var buffer = Data(capacity: 64 * 1024)
        for try await byte in bytes {
            buffer.append(byte)
            if buffer.count >= 64 * 1024 {
                try Task.checkCancellation()
                try handle.write(contentsOf: buffer); written += Int64(buffer.count); buffer.removeAll(keepingCapacity: true)
                await onProgress(written)
            }
        }
        if !buffer.isEmpty { try handle.write(contentsOf: buffer); written += Int64(buffer.count); await onProgress(written) }
    }
}
