import SwiftData
import simd
import XCTest
@testable import AutoVerse3D

private let repo = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
private func fixture<T: Decodable>(_ t: T.Type, _ path: String) throws -> T { try ContentParser.decode(t, from: Data(contentsOf: repo.appendingPathComponent(path))) }

final class ContentDecodingTests: XCTestCase {
    func testDecodesAllSharedContracts() throws {
        let vehicles = try fixture(VehicleCatalog.self, "shared/data/vehicles.json")
        XCTAssertEqual(vehicles.vehicles.first?.id, "urbanx_suv")
        XCTAssertEqual(vehicles.vehicles.filter(\.isAvailable).count, 1)
        let comps = try fixture(ComponentCatalog.self, "shared/data/components.json")
        XCTAssertGreaterThanOrEqual(comps.components.filter { $0.module == "mechanical" }.count, 5)
        XCTAssertEqual(comps.components.first { $0.id == "urbanx_front_brake" }?.nodeName, "FrontBrakeAssembly")
        XCTAssertEqual(try fixture(CameraPresets.self, "shared/data/camera_presets.json").orbit.default, "three_quarter")
        XCTAssertGreaterThanOrEqual(try fixture(LearningContent.self, "shared/content/learning_modules.json").modules.count, 5)
        let manifest = try fixture(AssetManifest.self, "assets/manifests/urbanx_suv_v1.json")
        XCTAssertEqual(manifest.platformVariants["ios"]?.format, "usdz")
        XCTAssertEqual(try fixture(SceneDescriptor.self, "assets/build/urbanx_suv_v1.scene.json").triangleCount, manifest.triangleCount)
        let config = try fixture(VehicleConfiguration.self, "shared/examples/configuration.example.json")
        let roundTrip = try JSONDecoder().decode(VehicleConfiguration.self, from: JSONEncoder().encode(config))
        XCTAssertEqual(config, roundTrip)
    }

    func testBundledContentLoadsInApp() throws {
        let c = try BundleContentRepository(bundle: .main).content()
        XCTAssertNotNil(c.bundledManifests["urbanx_suv_v1"])
        XCTAssertFalse(c.searchIndex.isEmpty)
    }
}

final class SearchAndLearningTests: XCTestCase {
    func testTokenizeAndScoreMatchBackend() {
        XCTAssertEqual(ContentSearch.tokenize("How does the INVERTER work?"), ["inverter", "work"])
        XCTAssertEqual(ContentSearch.score(["radiator"], SearchDocument(id: "a", kind: "k", title: "Radiator", text: "radiator coolant")), 4)
    }

    func testSearchFindsInverter() throws {
        let c = try BundleContentRepository(bundle: .main).content()
        XCTAssertTrue(ContentSearch.search(c.searchIndex, query: "what does the inverter do").first?.document.title.contains("Inverter") ?? false)
        XCTAssertTrue(ContentSearch.search(c.searchIndex, query: "the and of").isEmpty)
    }

    func testQuizScoringAndProgress() throws {
        let m = try fixture(LearningContent.self, "shared/content/learning_modules.json").modules.first { $0.id == "ev_fundamentals" }!
        let all = Dictionary(uniqueKeysWithValues: m.quiz.map { ($0.id, $0.correctIndex) })
        XCTAssertEqual(QuizScoring.score(m, answers: all), QuizResult(correct: 4, total: 4))
        XCTAssertFalse(QuizScoring.score(m, answers: [:]).passed)
        var p = ModuleProgressValue(moduleId: m.id)
        for s in m.sections { p = LearningProgress.markSectionRead(p, module: m, sectionId: s.id) }
        XCTAssertFalse(p.completed)
        p = LearningProgress.recordQuiz(p, module: m, result: QuizResult(correct: 3, total: 4))
        XCTAssertTrue(p.completed)
        p = LearningProgress.recordQuiz(p, module: m, result: QuizResult(correct: 0, total: 4))
        XCTAssertEqual(p.bestQuizPercent, 75)
    }
}

final class AssetTests: XCTestCase {
    func testCommittedAssetsMatchManifestChecksums() throws {
        let m = try fixture(AssetManifest.self, "assets/manifests/urbanx_suv_v1.json")
        XCTAssertEqual(try Checksum.sha256(fileAt: repo.appendingPathComponent("assets/build/\(m.fileName)")), m.checksum)
        let ios = m.platformVariants["ios"]!
        XCTAssertEqual(try Checksum.sha256(fileAt: repo.appendingPathComponent("assets/build/\(ios.fileName)")), ios.checksum)
        var tampered = try Data(contentsOf: repo.appendingPathComponent("assets/build/\(ios.fileName)"))
        tampered[100] ^= 0xFF
        XCTAssertNotEqual(Checksum.sha256(tampered), ios.checksum)
    }

    func testManifestValidation() throws {
        let m = try fixture(AssetManifest.self, "assets/manifests/urbanx_suv_v1.json")
        XCTAssertEqual(AssetManifestValidator.validate(m, appVersion: "1.0.0", expectedVehicleId: "urbanx_suv"), [])
        XCTAssertTrue(AssetManifestValidator.validate(m, appVersion: "0.9.0").contains(.appTooOld(required: "1.0.0", current: "0.9.0")))
        XCTAssertTrue(AssetManifestValidator.validate(m, appVersion: "1.0.0", expectedVehicleId: "terrax_4x4").contains(.vehicleMismatch(expected: "terrax_4x4", actual: "urbanx_suv")))
        XCTAssertEqual(SemVer.compare("1.10.0", "1.9.9"), 1)
    }

    func testDownloadStateMachine() {
        var s = DownloadState.notDownloaded
        s = DownloadStateMachine.reduce(s, .manifestLoaded(totalBytes: 1000, version: 1)); XCTAssertEqual(s, .available(totalBytes: 1000, version: 1))
        s = DownloadStateMachine.reduce(s, .start); XCTAssertEqual(s, .downloading(downloaded: 0, total: 1000))
        s = DownloadStateMachine.reduce(s, .progress(400)); XCTAssertEqual(s.progress, 0.4, accuracy: 0.001)
        s = DownloadStateMachine.reduce(s, .pause); XCTAssertEqual(s, .paused(downloaded: 400, total: 1000))
        s = DownloadStateMachine.reduce(s, .resume); XCTAssertEqual(s, .downloading(downloaded: 400, total: 1000))
        s = DownloadStateMachine.reduce(s, .error("net")); XCTAssertEqual(s, .failed(reason: "net", downloaded: 400, total: 1000))
        s = DownloadStateMachine.reduce(s, .start); XCTAssertEqual(s, .downloading(downloaded: 400, total: 1000))
        s = DownloadStateMachine.reduce(s, .transferComplete); XCTAssertEqual(s, .verifying(total: 1000))
        XCTAssertEqual(DownloadStateMachine.reduce(s, .error("checksum")), .failed(reason: "checksum", downloaded: 0, total: 1000))
        s = DownloadStateMachine.reduce(s, .verified(version: 1)); XCTAssertEqual(s, .installed(version: 1, sizeBytes: 1000))
        XCTAssertEqual(DownloadStateMachine.reduce(s, .manifestLoaded(totalBytes: 1000, version: 1)), s)
        XCTAssertEqual(DownloadStateMachine.reduce(s, .manifestLoaded(totalBytes: 1200, version: 2)), .available(totalBytes: 1200, version: 2))
        XCTAssertEqual(DownloadStateMachine.reduce(s, .remove), .notDownloaded)
    }
}

final class ViewerLogicTests: XCTestCase {
    private func fixtures() throws -> (ComponentCatalog, SceneGraph, CameraPresets) {
        (try fixture(ComponentCatalog.self, "shared/data/components.json"), SceneGraph(try fixture(SceneDescriptor.self, "assets/build/urbanx_suv_v1.scene.json")),
         try fixture(CameraPresets.self, "shared/data/camera_presets.json"))
    }

    func testVisibilityRules() throws {
        let (catalog, graph, _) = try fixtures()
        let mech = VisibilityRules.hiddenNodes(mode: .mechanical, catalog: catalog, graph: graph, activeWheelSuffix: "Rim01", allWheelSuffixes: ["Rim01", "Rim02", "Rim03"])
        XCTAssertTrue(mech.contains("Body_Shell")); XCTAssertFalse(mech.contains("EngineBlock_Block"))
        XCTAssertTrue(mech.contains("FrontLeft_Rim02")); XCTAssertFalse(mech.contains("FrontLeft_Rim01"))
        let ext = VisibilityRules.hiddenNodes(mode: .customize, catalog: catalog, graph: graph, activeWheelSuffix: "Rim02", allWheelSuffixes: ["Rim01", "Rim02", "Rim03"])
        XCTAssertTrue(ext.contains("EngineBlock_Block")); XCTAssertTrue(ext.contains("Hotspot_dashboard_Marker"))
    }

    func testTapResolution() throws {
        let (catalog, graph, _) = try fixtures()
        let nodes = Set(catalog.components.map(\.nodeName))
        XCTAssertEqual(graph.nearestAncestor(of: "FrontLeftBrake_Caliper", in: nodes), "FrontBrakeAssembly")
        XCTAssertNil(graph.nearestAncestor(of: "Hotspot_dashboard_Marker", in: nodes))
    }

    func testOrbitCamera() throws {
        let (_, _, presets) = try fixtures()
        var front = presets.orbit.presets.first { $0.id == "front" }!
        front = OrbitPreset(id: front.id, name: front.name, yaw: 0, pitch: 0, distance: front.distance, target: front.target)
        let cam = OrbitCamera(preset: front, limits: presets.orbit.limits)
        XCTAssertEqual(cam.eye.z, 6.5, accuracy: 1e-4)
        XCTAssertEqual(cam.rotated(dYaw: 90, dPitch: 0).eye.x, 6.5, accuracy: 1e-3)
        XCTAssertEqual(cam.rotated(dYaw: 0, dPitch: 500).pitch, presets.orbit.limits.maxPitch)
        XCTAssertEqual(cam.zoomed(100).distance, presets.orbit.limits.minDistance)
        XCTAssertEqual(simd_length(cam.panned(dx: 100, dy: 100).target - cam.presetTarget), presets.orbit.limits.maxPan, accuracy: 1e-3)
    }

    func testPartAnimatorMatchesAndroid() throws {
        let (catalog, _, _) = try fixtures()
        var anim = PartAnimator(catalog: catalog)
        var original = matrix_identity_float4x4; original.columns.3 = SIMD4(0, 0.97, 0.98, 1)
        anim.setPartOpen("hood_open", open: true)
        anim.step(dt: 10, reducedMotion: false)
        let p = anim.localTransform("Hood", original: original) * SIMD4<Float>(0, 0, 1, 1)
        XCTAssertGreaterThan(p.y, 1.6, "hood front lifts")
        anim.setExploded("mechanical_exploded"); anim.step(dt: 10, reducedMotion: false)
        XCTAssertEqual(anim.localTransform("EngineBlock", original: matrix_identity_float4x4).columns.3.y, 0.6, accuracy: 1e-4)
        anim.resetAll(); anim.step(dt: 0.01, reducedMotion: true)
        XCTAssertEqual(anim.localTransform("Hood", original: original), original)
    }
}

final class ConfigurationTests: XCTestCase {
    func testRulesAndCompare() throws {
        let o = try fixture(CustomizationOptions.self, "shared/data/customization_options.json")
        var sel = DesignSelection.defaults(o); sel.paintId = "midnight_blue"
        let c = ConfigurationRules.create(id: "1", vehicleId: "urbanx_suv", name: "  Midnight ", selection: sel, now: "t")
        XCTAssertEqual(c.name, "Midnight")
        XCTAssertEqual(ConfigurationRules.duplicateName("Night copy", existing: ["Night", "Night copy"]), "Night copy 2")
        var cur = sel; cur.wheelId = "wheel_style_03"
        XCTAssertEqual(ConfigurationRules.compare(saved: c, current: cur, options: o).map(\.field), ["Wheels"])
        XCTAssertEqual(ConfigurationRules.validateName(" "), .empty)
        XCTAssertEqual(ConfigurationRules.validateName(String(repeating: "x", count: 61)), .tooLong)
        XCTAssertEqual(ConfigurationRules.sanitize(DesignSelection(paintId: "gone", wheelId: "wheel_style_02", trimId: "x", interiorId: "y"), options: o).paintId, o.defaults.paintId)
    }

    @MainActor
    func testSwiftDataPersistenceSurvivesNewContext() throws {
        let container = try Persistence.makeContainer(inMemory: true)
        let store = ConfigurationStore(context: container.mainContext)
        let c = VehicleConfiguration(id: "a", vehicleId: "urbanx_suv", name: "One", paintId: "ember_red", wheelId: "wheel_style_01", trimId: "dark_trim", interiorId: "interior_sand", createdAt: "t", updatedAt: "t")
        store.save(c)
        var renamed = c; renamed.name = "Renamed"; renamed.updatedAt = "t2"
        store.save(renamed)
        let reread = ConfigurationStore(context: ModelContext(container))
        XCTAssertEqual(reread.all(), [renamed])
        store.delete("a")
        XCTAssertTrue(store.all().isEmpty)
        let progress = ProgressStore(context: container.mainContext)
        progress.save(ModuleProgressValue(moduleId: "m", sectionsRead: ["b", "a"], bestQuizPercent: 80, completed: true, contentVersion: "v"))
        XCTAssertEqual(progress.get("m").sectionsRead, ["a", "b"])
    }
}

private struct StubService: AssistantService {
    let result: Result<AssistantResponse, APIError>
    func ask(_ request: AssistantRequest) async throws -> AssistantResponse { try result.get() }
}

final class AssistantTests: XCTestCase {
    func testRequestConstruction() throws {
        let r = try APIClient(baseURL: URL(string: "http://localhost:8787/")!).makeAskRequest(AssistantRequest(question: "Q", vehicleId: "urbanx_suv", componentId: nil))
        XCTAssertEqual(r.url?.absoluteString, "http://localhost:8787/v1/ai/automotive-assistant")
        XCTAssertEqual(r.httpMethod, "POST")
        let body = try JSONSerialization.jsonObject(with: r.httpBody!) as! [String: Any]
        XCTAssertEqual(body["question"] as? String, "Q"); XCTAssertEqual(body["vehicleId"] as? String, "urbanx_suv")
    }

    func testResponseParsing() throws {
        let json = #"{"answer":"A","sources":[{"id":"x","title":"T","kind":"learning"}],"scope":"general","safetyNotice":null,"refused":false,"provider":"mock","disclaimer":"d","schemaVersion":1}"#
        let r = try JSONDecoder().decode(AssistantResponse.self, from: Data(json.utf8))
        XCTAssertEqual(r.sources.first?.title, "T"); XCTAssertNil(r.safetyNotice)
    }

    func testFallbacks() async throws {
        let index = try BundleContentRepository(bundle: .main).content().searchIndex
        let offline = await AssistantRepository(service: StubService(result: .failure(.transport)), index: index).ask("inverter", vehicleId: nil, componentId: nil)
        guard case let .offline(_, matches) = offline else { return XCTFail("expected offline") }
        XCTAssertFalse(matches.isEmpty)
        let limited = await AssistantRepository(service: StubService(result: .failure(.http(status: 429, retryAfter: 30))), index: index).ask("brakes", vehicleId: nil, componentId: nil)
        guard case let .rateLimited(retry, _) = limited else { return XCTFail("expected rate limit") }
        XCTAssertEqual(retry, 30)
        let invalid = await AssistantRepository(service: StubService(result: .failure(.transport)), index: index).ask("   ", vehicleId: nil, componentId: nil)
        guard case .invalid = invalid else { return XCTFail("expected invalid") }
    }
}
