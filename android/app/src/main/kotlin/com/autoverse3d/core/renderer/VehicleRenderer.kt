package com.autoverse3d.core.renderer

import com.autoverse3d.domain.InteriorCamera
import com.autoverse3d.domain.OrbitCamera

/** Where a model comes from: a bundled APK asset or a verified downloaded file. */
sealed interface ModelSource {
    data class Bundled(val assetPath: String) : ModelSource
    data class Downloaded(val filePath: String) : ModelSource
}

sealed interface CameraRig {
    data class Orbit(val camera: OrbitCamera) : CameraRig
    data class Interior(val camera: InteriorCamera) : CameraRig
}

/** A material override: linear colour plus PBR factors. */
data class MaterialLook(val linearRgb: FloatArray, val metallic: Float, val roughness: Float)

/**
 * Declarative description of everything the viewer shows. The UI/ViewModel layer only produces this;
 * the renderer diffs it against what is on screen. Keeps Filament out of UI and domain code.
 */
data class SceneState(
    val hiddenNodes: Set<String> = emptySet(),
    val materialLooks: Map<String, MaterialLook> = emptyMap(),
    val lightsOn: Boolean = false,
    val openAnimations: Set<String> = emptySet(),
    val explodedViewId: String? = null,
    val highlightedNodes: Set<String> = emptySet(),
    val reducedMotion: Boolean = false,
)

/** Platform-neutral renderer operations from IMPLEMENTATION_PLAN §6 "Renderer abstraction". */
interface VehicleRenderer {
    suspend fun loadVehicle(vehicle: ModelSource, studio: ModelSource?)
    fun unload()
    fun setCamera(rig: CameraRig, animate: Boolean)
    fun currentCamera(): CameraRig?
    fun applyScene(state: SceneState)
    fun resetScene()
    /** viewport coordinates with origin top-left, in pixels. Result: name of the tapped mesh node or null. */
    fun pick(x: Float, y: Float, onResult: (String?) -> Unit)
    fun onRotate(dxPx: Float, dyPx: Float)
    fun onPan(dxPx: Float, dyPx: Float)
    fun onZoom(scale: Float)
}

sealed interface RenderStatus {
    data object Loading : RenderStatus
    data object Ready : RenderStatus
    data class Failed(val message: String) : RenderStatus
}
