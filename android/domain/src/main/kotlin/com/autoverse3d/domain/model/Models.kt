package com.autoverse3d.domain.model

import kotlinx.serialization.Serializable

// Kotlin mirrors of the versioned JSON contracts in /shared (see docs/DATA_MODELS.md).

@Serializable
data class VehicleCatalog(val schemaVersion: Int, val contentVersion: String, val vehicles: List<Vehicle>)

@Serializable
data class Spec(val label: String, val value: String)

@Serializable
data class Vehicle(
    val id: String,
    val name: String,
    val category: String,
    val categoryLabel: String = "",
    val description: String,
    val assetManifestId: String? = null,
    val featureIds: List<String> = emptyList(),
    val availability: String,
    val specs: List<Spec> = emptyList(),
    val specsDisclaimer: String = "",
    val schemaVersion: Int,
) {
    val isAvailable: Boolean get() = availability == "available" && assetManifestId != null
}

@Serializable
data class VehicleComponent(
    val id: String,
    val vehicleId: String,
    val module: String,
    val name: String,
    val category: String,
    val nodeName: String,
    val description: String,
    val purpose: String = "",
    val detail: String = "",
    val animationIds: List<String> = emptyList(),
    val schemaVersion: Int,
)

@Serializable
data class PartAnimation(
    val id: String,
    val type: String,
    val nodeName: String,
    val axis: List<Float>,
    val angleDeg: Float,
    val durationSec: Float,
)

@Serializable
data class NodeOffset(val nodeName: String, val offset: List<Float>)

@Serializable
data class ExplodedView(val id: String, val name: String, val durationSec: Float, val offsets: List<NodeOffset>)

@Serializable
data class ModeVisibility(val hide: List<String>)

@Serializable
data class ComponentCatalog(
    val schemaVersion: Int,
    val vehicleId: String,
    val educationalDisclaimer: String = "",
    val components: List<VehicleComponent>,
    val animations: List<PartAnimation>,
    val explodedViews: List<ExplodedView>,
    val modeVisibility: Map<String, ModeVisibility>,
)

@Serializable
data class Hotspot(
    val id: String,
    val module: String,
    val title: String,
    val position: List<Float>,
    val summary: String,
    val detail: String,
) {
    val nodeName: String get() = "Hotspot_$id"
}

@Serializable
data class HotspotCatalog(val schemaVersion: Int, val vehicleId: String, val hotspots: List<Hotspot>, val safetyDisclaimer: String = "")

@Serializable
data class ColorOption(val id: String, val name: String, val hex: String, val metallic: Float = 0f, val roughness: Float = 0.5f)

@Serializable
data class WheelOption(val id: String, val name: String, val description: String, val nodeSuffix: String)

@Serializable
data class CustomizationDefaults(val paintId: String, val wheelId: String, val trimId: String, val interiorId: String)

@Serializable
data class CustomizationOptions(
    val schemaVersion: Int,
    val vehicleId: String,
    val defaults: CustomizationDefaults,
    val paints: List<ColorOption>,
    val wheels: List<WheelOption>,
    val trims: List<ColorOption>,
    val interiors: List<ColorOption>,
)

@Serializable
data class OrbitLimits(val minDistance: Float, val maxDistance: Float, val minPitch: Float, val maxPitch: Float, val maxPan: Float)

@Serializable
data class OrbitPreset(val id: String, val name: String, val yaw: Float, val pitch: Float, val distance: Float, val target: List<Float>)

@Serializable
data class OrbitPresets(val limits: OrbitLimits, val presets: List<OrbitPreset>, val default: String)

@Serializable
data class InteriorLimits(val maxYawOffset: Float, val maxPitchOffset: Float)

@Serializable
data class InteriorPreset(val id: String, val name: String, val eye: List<Float>, val lookAt: List<Float>)

@Serializable
data class InteriorPresets(val limits: InteriorLimits, val presets: List<InteriorPreset>, val default: String)

@Serializable
data class CameraPresets(val schemaVersion: Int, val orbit: OrbitPresets, val interior: InteriorPresets)

@Serializable
data class QuizQuestion(val id: String, val prompt: String, val choices: List<String>, val correctIndex: Int, val explanation: String)

@Serializable
data class LearningSection(val id: String, val heading: String, val body: String)

@Serializable
data class LearningModule(
    val id: String,
    val title: String,
    val summary: String,
    val estimatedMinutes: Int,
    val relatedComponentIds: List<String> = emptyList(),
    val sections: List<LearningSection>,
    val quiz: List<QuizQuestion>,
)

@Serializable
data class LearningContent(val schemaVersion: Int, val contentVersion: String, val disclaimer: String = "", val modules: List<LearningModule>)

@Serializable
data class EvNode(val id: String, val title: String, val x: Float, val y: Float, val summary: String)

@Serializable
data class EvMode(val id: String, val title: String, val flows: List<List<String>>, val textAlternative: String)

@Serializable
data class EvLabContent(val schemaVersion: Int, val contentVersion: String, val disclaimer: String, val nodes: List<EvNode>, val modes: List<EvMode>)

@Serializable
data class AssetVariant(val format: String, val fileName: String, val fileSizeBytes: Long, val checksum: String)

@Serializable
data class AssetManifest(
    val id: String,
    val vehicleId: String,
    val version: Int,
    val format: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val checksum: String,
    val minimumAppVersion: String,
    val licenceRecordId: String,
    val platformVariants: Map<String, AssetVariant> = emptyMap(),
    val sceneDescriptor: AssetVariant,
    val triangleCount: Int? = null,
    val schemaVersion: Int,
    val downloadUrls: Map<String, String> = emptyMap(),
)

@Serializable
data class SceneNode(
    val name: String,
    val parent: String? = null,
    val kind: String,
    val pivot: List<Float>,
    val material: String? = null,
    val triangles: Int? = null,
)

@Serializable
data class SceneMaterial(val baseColorHex: String, val metallic: Float, val roughness: Float, val emissiveHex: String? = null, val opacity: Float = 1f)

@Serializable
data class SceneDescriptor(
    val schemaVersion: Int,
    val assetId: String,
    val triangleCount: Int,
    val nodes: List<SceneNode>,
    val materials: Map<String, SceneMaterial>,
)

/** A saved customization (shared/schemas/configuration.schema.json). Timestamps are ISO-8601 UTC. */
@Serializable
data class VehicleConfiguration(
    val id: String,
    val vehicleId: String,
    val name: String,
    val paintId: String,
    val wheelId: String,
    val trimId: String,
    val interiorId: String,
    val createdAt: String,
    val updatedAt: String,
    val schemaVersion: Int = 1,
)

@Serializable
data class AssistantRequest(val question: String, val vehicleId: String? = null, val componentId: String? = null)

@Serializable
data class AssistantSource(val id: String, val title: String, val kind: String)

@Serializable
data class AssistantResponse(
    val answer: String,
    val sources: List<AssistantSource> = emptyList(),
    val scope: String = "general",
    val safetyNotice: String? = null,
    val refused: Boolean = false,
    val provider: String = "",
    val disclaimer: String = "",
    val schemaVersion: Int = 1,
)

@Serializable
data class ApiErrorBody(val error: ApiError)

@Serializable
data class ApiError(val code: String, val message: String)

/** Viewer modes; [visibilityKey] selects the entry in ComponentCatalog.modeVisibility. */
enum class ViewerMode(val visibilityKey: String) {
    Exterior("exterior"), Interior("interior"), Mechanical("mechanical"), Safety("safety"), Customize("exterior");
}
