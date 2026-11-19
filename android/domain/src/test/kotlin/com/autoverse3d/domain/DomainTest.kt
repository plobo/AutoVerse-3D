package com.autoverse3d.domain

import com.autoverse3d.domain.model.LearningModule
import com.autoverse3d.domain.model.ViewerMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val repo = File(System.getProperty("autoverse.repoRoot") ?: "../..")
private fun read(path: String) = File(repo, path).readText()

class ContentDecodingTest {
    @Test fun decodesAllSharedContracts() {
        val vehicles = ContentParser.vehicles(read("shared/data/vehicles.json"))
        assertEquals("urbanx_suv", vehicles.vehicles.first().id)
        assertTrue(vehicles.vehicles.first().isAvailable)
        assertEquals(1, vehicles.vehicles.count { it.isAvailable })
        val components = ContentParser.components(read("shared/data/components.json"))
        assertTrue(components.components.count { it.module == "mechanical" } >= 5)
        val brake = components.components.first { it.id == "urbanx_front_brake" }
        assertEquals("FrontBrakeAssembly", brake.nodeName)
        assertTrue(ContentParser.hotspots(read("shared/data/hotspots.json")).hotspots.any { it.module == "interior" })
        assertEquals("three_quarter", ContentParser.cameraPresets(read("shared/data/camera_presets.json")).orbit.default)
        assertTrue(ContentParser.learning(read("shared/content/learning_modules.json")).modules.size >= 5)
        assertEquals(3, ContentParser.evLab(read("shared/content/ev_lab.json")).modes.size)
        val manifest = ContentParser.manifest(read("assets/manifests/urbanx_suv_v1.json"))
        assertEquals("usdz", manifest.platformVariants.getValue("ios").format)
        val scene = ContentParser.scene(read("assets/build/urbanx_suv_v1.scene.json"))
        assertEquals(manifest.triangleCount, scene.triangleCount)
    }

    @Test fun decodesConfigurationExampleAndRoundTrips() {
        val config = ContentParser.json.decodeFromString<com.autoverse3d.domain.model.VehicleConfiguration>(read("shared/examples/configuration.example.json"))
        assertEquals("midnight_blue", config.paintId)
        val again = ContentParser.json.decodeFromString<com.autoverse3d.domain.model.VehicleConfiguration>(ContentParser.json.encodeToString(com.autoverse3d.domain.model.VehicleConfiguration.serializer(), config))
        assertEquals(config, again)
    }
}

class SearchTest {
    private val index by lazy {
        ContentSearch.buildIndex(
            ContentParser.learning(read("shared/content/learning_modules.json")),
            ContentParser.components(read("shared/data/components.json")),
            ContentParser.hotspots(read("shared/data/hotspots.json")),
            ContentParser.evLab(read("shared/content/ev_lab.json")),
        )
    }

    @Test fun tokenizeMatchesBackend() {
        assertEquals(listOf("inverter", "work"), ContentSearch.tokenize("How does the INVERTER work?"))
    }

    @Test fun scoringWeightsTitles() {
        assertEquals(4, ContentSearch.score(listOf("radiator"), SearchDocument("a", "k", "Radiator", "radiator coolant")))
    }

    @Test fun findsInverter() {
        val hits = ContentSearch.search(index, "what does the inverter do", 3)
        assertTrue(hits.first().document.title.contains("Inverter"))
    }

    @Test fun emptyQueryReturnsNothing() = assertTrue(ContentSearch.search(index, "the and of").isEmpty())
}

class LearningTest {
    private val module by lazy { ContentParser.learning(read("shared/content/learning_modules.json")).modules.first { it.id == "ev_fundamentals" } }

    @Test fun quizScoring() {
        val all = module.quiz.associate { it.id to it.correctIndex }
        assertEquals(QuizResult(4, 4), QuizScoring.score(module, all))
        assertTrue(QuizScoring.score(module, all).passed)
        val half = module.quiz.take(2).associate { it.id to it.correctIndex }
        val r = QuizScoring.score(module, half)
        assertEquals(50, r.percent)
        assertFalse(r.passed)
        assertEquals(0, QuizScoring.score(module, emptyMap()).correct)
    }

    @Test fun completionNeedsAllSectionsAndPassingQuiz() {
        var p = ModuleProgress(module.id)
        module.sections.forEach { p = LearningProgress.markSectionRead(p, module, it.id) }
        assertFalse(p.completed)
        p = LearningProgress.recordQuiz(p, module, QuizResult(1, 4))
        assertFalse(p.completed)
        p = LearningProgress.recordQuiz(p, module, QuizResult(3, 4))
        assertTrue(p.completed)
        p = LearningProgress.recordQuiz(p, module, QuizResult(0, 4))
        assertEquals("best score is kept", 75, p.bestQuizPercent)
        assertTrue(p.completed)
    }

    @Test fun moduleWithoutQuizCompletesOnReading() {
        val m = LearningModule("m", "t", "s", 1, emptyList(), listOf(com.autoverse3d.domain.model.LearningSection("a", "h", "b")), emptyList())
        assertTrue(LearningProgress.markSectionRead(ModuleProgress("m"), m, "a").completed)
    }
}

class AssetTest {
    private val manifest by lazy { ContentParser.manifest(read("assets/manifests/urbanx_suv_v1.json")) }

    @Test fun checksumOfCommittedAssetMatchesManifest() {
        File(repo, "assets/build/${manifest.fileName}").inputStream().use { assertTrue(Checksum.matches(it, manifest.checksum)) }
        val ios = manifest.platformVariants.getValue("ios")
        File(repo, "assets/build/${ios.fileName}").inputStream().use { assertTrue(Checksum.matches(it, ios.checksum)) }
    }

    @Test fun checksumDetectsTampering() {
        val bytes = File(repo, "assets/build/${manifest.fileName}").readBytes()
        bytes[100] = (bytes[100] + 1).toByte()
        assertFalse(Checksum.matches(bytes.inputStream(), manifest.checksum))
    }

    @Test fun manifestValidation() {
        assertTrue(AssetManifestValidator.validate(manifest, "1.0.0", "urbanx_suv").isEmpty())
        val issues = AssetManifestValidator.validate(manifest.copy(minimumAppVersion = "2.0.0", checksum = "md5:abc", fileName = "../x.glb"), "1.0.0", "terrax_4x4")
        assertTrue(issues.any { it is ManifestIssue.AppTooOld })
        assertTrue(issues.any { it is ManifestIssue.VehicleMismatch })
        assertTrue(issues.any { it is ManifestIssue.InvalidField && it.field == "checksum" })
        assertTrue(issues.any { it is ManifestIssue.InvalidField && it.field == "fileName" })
        assertEquals("urbanx_suv_v1.usdz", AssetManifestValidator.fileFor(manifest, "ios").first)
    }

    @Test fun semver() {
        assertTrue(SemVer.compare("1.10.0", "1.9.9") > 0)
        assertEquals(0, SemVer.compare("1.0", "1.0.0"))
    }

    @Test fun downloadHappyPathWithPauseResume() {
        val sm = DownloadStateMachine
        var s: DownloadState = DownloadState.NotDownloaded
        s = sm.reduce(s, DownloadEvent.ManifestLoaded(1000, 1)); assertEquals(DownloadState.Available(1000, 1), s)
        s = sm.reduce(s, DownloadEvent.Start); assertEquals(DownloadState.Downloading(0, 1000), s)
        s = sm.reduce(s, DownloadEvent.Progress(400)); assertEquals(0.4f, (s as DownloadState.Downloading).progress, 0.001f)
        s = sm.reduce(s, DownloadEvent.Pause); assertEquals(DownloadState.Paused(400, 1000), s)
        s = sm.reduce(s, DownloadEvent.Resume); assertEquals(DownloadState.Downloading(400, 1000), s)
        s = sm.reduce(s, DownloadEvent.Progress(5000)); assertEquals(DownloadState.Downloading(1000, 1000), s)
        s = sm.reduce(s, DownloadEvent.TransferComplete); assertEquals(DownloadState.Verifying(1000), s)
        s = sm.reduce(s, DownloadEvent.Verified(1)); assertEquals(DownloadState.Installed(1, 1000), s)
        s = sm.reduce(s, DownloadEvent.ManifestLoaded(1000, 1)); assertEquals("same version stays installed", DownloadState.Installed(1, 1000), s)
        s = sm.reduce(s, DownloadEvent.ManifestLoaded(1200, 2)); assertEquals("newer version offered", DownloadState.Available(1200, 2), s)
    }

    @Test fun downloadFailuresAndRetry() {
        val sm = DownloadStateMachine
        var s: DownloadState = DownloadState.Downloading(300, 1000)
        s = sm.reduce(s, DownloadEvent.Error("network")); assertEquals(DownloadState.Failed("network", 300, 1000), s)
        s = sm.reduce(s, DownloadEvent.Start); assertEquals("retry resumes from partial", DownloadState.Downloading(300, 1000), s)
        s = sm.reduce(DownloadState.Verifying(1000), DownloadEvent.Error("checksum mismatch"))
        assertEquals("bad checksum restarts from zero", DownloadState.Failed("checksum mismatch", 0, 1000), s)
        assertEquals("invalid transitions are ignored", DownloadState.NotDownloaded, sm.reduce(DownloadState.NotDownloaded, DownloadEvent.Verified(1)))
        assertEquals(DownloadState.NotDownloaded, sm.reduce(DownloadState.Installed(1, 10), DownloadEvent.Remove))
    }
}

class ViewerTest {
    private val catalog by lazy { ContentParser.components(read("shared/data/components.json")) }
    private val graph by lazy { SceneGraph(ContentParser.scene(read("assets/build/urbanx_suv_v1.scene.json"))) }
    private val presets by lazy { ContentParser.cameraPresets(read("shared/data/camera_presets.json")) }

    @Test fun mechanicalModeHidesBodyAndShowsPowertrain() {
        val hidden = VisibilityRules.hiddenNodes(ViewerMode.Mechanical, catalog, graph, "Rim01", listOf("Rim01", "Rim02", "Rim03"))
        assertTrue("Body_Shell" in hidden)
        assertTrue("FrontLeftDoor_Panel" in hidden)
        assertFalse("EngineBlock_Block" in hidden)
        assertTrue("FrontLeft_Rim02" in hidden)
        assertFalse("FrontLeft_Rim01" in hidden)
    }

    @Test fun exteriorModeHidesMechanicalAndHotspots() {
        val hidden = VisibilityRules.hiddenNodes(ViewerMode.Exterior, catalog, graph, "Rim02", listOf("Rim01", "Rim02", "Rim03"))
        assertTrue("EngineBlock_Block" in hidden)
        assertTrue("Hotspot_dashboard_Marker" in hidden)
        assertFalse("Body_Shell" in hidden)
        assertFalse("RearRight_Rim02" in hidden)
    }

    @Test fun tappedMeshResolvesToComponent() {
        val nodes = catalog.components.map { it.nodeName }.toSet()
        assertEquals("FrontBrakeAssembly", graph.nearestAncestorIn("FrontLeftBrake_Caliper", nodes))
        assertEquals("EngineBlock", graph.nearestAncestorIn("EngineBlock_Coils", nodes))
        assertEquals(null, graph.nearestAncestorIn("Hotspot_dashboard_Marker", nodes))
    }

    @Test fun meshesUsingPaintIncludeDoorsHoodTrunk() {
        val paint = graph.meshesUsing("Paint")
        assertTrue(paint.containsAll(listOf("Body_Shell", "Hood_Panel", "Trunk_Panel", "FrontLeftDoor_Panel")))
    }

    @Test fun orbitCameraMathAndLimits() {
        val front = presets.orbit.presets.first { it.id == "front" }
        val cam = OrbitCamera.from(front.copy(pitch = 0f), presets.orbit.limits)
        val eye = cam.eye()
        assertEquals(0f, eye[0], 1e-4f); assertEquals(0.75f, eye[1], 1e-4f); assertEquals(6.5f, eye[2], 1e-4f)
        val side = cam.rotated(90f, 0f).eye()
        assertEquals("yaw 90 looks at the vehicle's left (+X) side", 6.5f, side[0], 1e-3f)
        assertEquals(presets.orbit.limits.maxPitch, cam.rotated(0f, 500f).pitch, 0f)
        assertEquals(presets.orbit.limits.minDistance, cam.zoomed(100f).distance, 0f)
        assertEquals(presets.orbit.limits.maxDistance, cam.zoomed(0.01f).distance, 0f)
        val panned = cam.panned(100f, 100f)
        val off = (0..2).map { panned.target[it] - front.target[it] }
        assertEquals(presets.orbit.limits.maxPan, kotlin.math.sqrt(off.sumOf { (it * it).toDouble() }).toFloat(), 1e-3f)
        assertEquals(350f, cam.rotated(-10f, 0f).yaw, 1e-3f)
    }

    @Test fun interiorCameraLooksTowardPresetTarget() {
        val p = presets.interior.presets.first { it.id == "driver" }
        val cam = InteriorCamera.from(p, 75f, 40f)
        val look = cam.lookAt()
        assertTrue("driver looks forward (+Z)", look[2] > p.eye[2])
        assertEquals(75f, cam.rotated(500f, 0f).yawOffset, 0f)
    }

    @Test fun rotationMatrixRotatesDoorOutward() {
        // Front-left door: hinge at the front edge, door extends toward -Z. -65 deg about +Y swings it to +X (outward).
        val m = Mat4.rotation(listOf(0f, 1f, 0f), -65f)
        val p = Mat4.transformPoint(m, floatArrayOf(0f, 0f, -1f))
        assertTrue(p[0] > 0.8f)
        val hood = Mat4.transformPoint(Mat4.rotation(listOf(1f, 0f, 0f), -50f), floatArrayOf(0f, 0f, 1f))
        assertTrue("hood front lifts", hood[1] > 0.7f)
    }

    @Test fun partAnimatorInterpolatesAndRespectsReducedMotion() {
        val anim = PartAnimator(catalog)
        val original = Mat4.translation(0f, 0.97f, 0.98f)
        anim.setPartOpen("hood_open", true)
        assertTrue(anim.step(0.45f, reducedMotion = false))
        val mid = Mat4.transformPoint(anim.localTransform("Hood", original), floatArrayOf(0f, 0f, 1f))
        assertTrue(mid[1] > 0.97f)
        anim.step(10f, false)
        assertTrue(anim.isOpen("hood_open"))
        anim.resetAll()
        assertFalse(anim.step(0.01f, reducedMotion = true))
        assertTrue(anim.localTransform("Hood", original).contentEquals(original))
        anim.setExploded("mechanical_exploded")
        anim.step(10f, false)
        val engine = anim.localTransform("EngineBlock", Mat4.identity())
        assertEquals(0.6f, engine[13], 1e-4f)
    }
}

class ConfigurationTest {
    private val options by lazy { ContentParser.customization(read("shared/data/customization_options.json")) }

    @Test fun createRenameDuplicateCompare() {
        val sel = DesignSelection.defaults(options).copy(paintId = "midnight_blue")
        val c = ConfigurationRules.create("1", "urbanx_suv", "  Midnight  ", sel, "2026-11-18T10:00:00Z")
        assertEquals("Midnight", c.name)
        val renamed = ConfigurationRules.rename(c, "Night", "2026-11-18T11:00:00Z")
        assertEquals("2026-11-18T10:00:00Z", renamed.createdAt)
        assertEquals("2026-11-18T11:00:00Z", renamed.updatedAt)
        assertEquals("Night copy", ConfigurationRules.duplicateName("Night", listOf("Night")))
        assertEquals("Night copy 2", ConfigurationRules.duplicateName("Night copy", listOf("Night", "Night copy")))
        val diffs = ConfigurationRules.compare(c, sel.copy(paintId = "ember_red", wheelId = "wheel_style_03"), options)
        assertEquals(listOf("Paint", "Wheels"), diffs.map { it.field })
        assertEquals("Midnight Blue", diffs[0].saved)
        assertEquals("Turbine", diffs[1].current)
    }

    @Test fun nameValidationAndSanitize() {
        assertEquals(NameValidation.Empty, ConfigurationRules.validateName("  "))
        assertEquals(NameValidation.TooLong, ConfigurationRules.validateName("x".repeat(61)))
        assertEquals(NameValidation.Valid, ConfigurationRules.validateName("Ok"))
        val s = ConfigurationRules.sanitize(DesignSelection("gone", "wheel_style_02", "gone", "gone"), options)
        assertEquals(options.defaults.paintId, s.paintId)
        assertEquals("wheel_style_02", s.wheelId)
    }

    @Test fun colorConversion() {
        val lin = ColorUtil.hexToLinear("#808080")
        assertEquals(0.2158f, lin[0], 1e-3f)
        assertEquals(1f, ColorUtil.hexToSrgb("#FFFFFF")[2], 0f)
    }
}
