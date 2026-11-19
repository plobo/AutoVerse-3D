package com.autoverse3d.feature.vehicleviewer

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.autoverse3d.core.assets.AssetRepository
import com.autoverse3d.core.assets.ResolvedModel
import com.autoverse3d.core.renderer.CameraCommand
import com.autoverse3d.core.renderer.CameraRig
import com.autoverse3d.core.renderer.MaterialLook
import com.autoverse3d.core.renderer.RenderStatus
import com.autoverse3d.core.renderer.SceneState
import com.autoverse3d.data.AppContent
import com.autoverse3d.data.ConfigurationRepository
import com.autoverse3d.data.ContentRepository
import com.autoverse3d.data.SettingsRepository
import com.autoverse3d.domain.ColorUtil
import com.autoverse3d.domain.ConfigurationDifference
import com.autoverse3d.domain.ConfigurationRules
import com.autoverse3d.domain.DesignSelection
import com.autoverse3d.domain.InteriorCamera
import com.autoverse3d.domain.NameValidation
import com.autoverse3d.domain.OrbitCamera
import com.autoverse3d.domain.SceneGraph
import com.autoverse3d.domain.VisibilityRules
import com.autoverse3d.domain.model.ColorOption
import com.autoverse3d.domain.model.Hotspot
import com.autoverse3d.domain.model.Vehicle
import com.autoverse3d.domain.model.VehicleComponent
import com.autoverse3d.domain.model.VehicleConfiguration
import com.autoverse3d.domain.model.ViewerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val content: AppContent? = null,
    val vehicle: Vehicle? = null,
    val model: ResolvedModel? = null,
    val graph: SceneGraph? = null,
    val mode: ViewerMode = ViewerMode.Exterior,
    val renderStatus: RenderStatus = RenderStatus.Loading,
    val selection: DesignSelection? = null,
    val loadedConfiguration: VehicleConfiguration? = null,
    val lightsOn: Boolean = false,
    val openAnimations: Set<String> = emptySet(),
    val explodedViewId: String? = null,
    val selectedComponentId: String? = null,
    val selectedHotspotId: String? = null,
    val orbitPresetId: String = "three_quarter",
    val interiorPresetId: String = "driver",
    val cameraCommand: CameraCommand? = null,
    val reducedMotion: Boolean = false,
    val message: String? = null,
) {
    val selectedComponent: VehicleComponent? get() = content?.components?.components?.firstOrNull { it.id == selectedComponentId }
    val selectedHotspot: Hotspot? get() = content?.hotspots?.hotspots?.firstOrNull { it.id == selectedHotspotId }

    fun componentsFor(module: String) = content?.components?.components?.filter { it.module == module }.orEmpty()
    fun hotspotsFor(module: String) = content?.hotspots?.hotspots?.filter { it.module == module }.orEmpty()

    val differences: List<ConfigurationDifference>
        get() {
            val saved = loadedConfiguration ?: return emptyList()
            val sel = selection ?: return emptyList()
            return ConfigurationRules.compare(saved, sel, content?.customization ?: return emptyList())
        }
}

@HiltViewModel
class VehicleViewerViewModel @Inject constructor(
    application: Application,
    savedState: SavedStateHandle,
    private val contentRepo: ContentRepository,
    private val assets: AssetRepository,
    private val configurations: ConfigurationRepository,
    private val settings: SettingsRepository,
) : AndroidViewModel(application) {

    private val vehicleId: String = savedState["vehicleId"] ?: "urbanx_suv"
    private val initialMode: String = savedState["mode"] ?: "exterior"
    private val configId: String? = savedState.get<String>("configId")?.takeIf { it.isNotBlank() }
    private var nonce = 0

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val content = contentRepo.content()
                val vehicle = content.vehicles.vehicles.first { it.id == vehicleId }
                val manifestId = vehicle.assetManifestId ?: error("${vehicle.name} is coming soon.")
                val model = assets.resolve(vehicle.id, manifestId)
                val saved = configId?.let { configurations.get(it) }
                val selection = ConfigurationRules.sanitize(saved?.let { DesignSelection.of(it) } ?: DesignSelection.defaults(content.customization), content.customization)
                val mode = ViewerMode.entries.firstOrNull { it.name.equals(initialMode, ignoreCase = true) } ?: ViewerMode.Exterior
                _state.update {
                    it.copy(loading = false, content = content, vehicle = vehicle, model = model, graph = SceneGraph(model.scene),
                        selection = selection, loadedConfiguration = saved, reducedMotion = systemReducedMotion(),
                        orbitPresetId = content.cameras.orbit.default, interiorPresetId = content.cameras.interior.default)
                }
                setMode(mode, animate = false)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not open this vehicle.") }
            }
        }
    }

    private fun systemReducedMotion(): Boolean = try {
        Settings.Global.getFloat(getApplication<Application>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) { false }

    fun onRenderStatus(status: RenderStatus) = _state.update { it.copy(renderStatus = status) }

    fun setMode(mode: ViewerMode, animate: Boolean = true) {
        _state.update { it.copy(mode = mode, openAnimations = if (mode == ViewerMode.Interior) it.openAnimations - "hood_open" else it.openAnimations, selectedComponentId = null, selectedHotspotId = null, explodedViewId = if (mode == ViewerMode.Mechanical) it.explodedViewId else null) }
        if (mode == ViewerMode.Interior) selectInteriorPreset(_state.value.interiorPresetId) else selectOrbitPreset(
            if (mode == ViewerMode.Mechanical && _state.value.orbitPresetId == "three_quarter") "three_quarter" else _state.value.orbitPresetId, animate)
        viewModelScope.launch { settings.recordExplored(vehicleId, mode.name.lowercase()) }
    }

    fun selectOrbitPreset(id: String, animate: Boolean = true) {
        val c = _state.value.content ?: return
        val preset = c.cameras.orbit.presets.firstOrNull { it.id == id } ?: return
        val cam = OrbitCamera.from(preset, c.cameras.orbit.limits).let { if (_state.value.mode == ViewerMode.Mechanical) it.copy(distance = it.distance * 0.85f) else it }
        _state.update { it.copy(orbitPresetId = id, cameraCommand = CameraCommand(CameraRig.Orbit(cam), animate && !it.reducedMotion, ++nonce)) }
    }

    fun selectInteriorPreset(id: String) {
        val c = _state.value.content ?: return
        val preset = c.cameras.interior.presets.firstOrNull { it.id == id } ?: return
        val cam = InteriorCamera.from(preset, c.cameras.interior.limits.maxYawOffset, c.cameras.interior.limits.maxPitchOffset)
        _state.update { it.copy(interiorPresetId = id, cameraCommand = CameraCommand(CameraRig.Interior(cam), false, ++nonce)) }
    }

    fun resetCamera() {
        val s = _state.value
        if (s.mode == ViewerMode.Interior) selectInteriorPreset(s.interiorPresetId)
        else selectOrbitPreset(s.content?.cameras?.orbit?.default ?: "three_quarter")
    }

    fun onCameraChanged(@Suppress("UNUSED_PARAMETER") rig: CameraRig) = Unit

    // ---------------------------------------------------------------- customization
    fun setPaint(id: String) = _state.update { it.copy(selection = it.selection?.copy(paintId = id)) }
    fun setWheel(id: String) = _state.update { it.copy(selection = it.selection?.copy(wheelId = id)) }
    fun setTrim(id: String) = _state.update { it.copy(selection = it.selection?.copy(trimId = id)) }
    fun setInterior(id: String) = _state.update { it.copy(selection = it.selection?.copy(interiorId = id)) }

    fun resetDesign() {
        val c = _state.value.content ?: return
        _state.update { it.copy(selection = it.loadedConfiguration?.let { s -> DesignSelection.of(s) } ?: DesignSelection.defaults(c.customization)) }
    }

    fun saveConfiguration(name: String, asNew: Boolean): Boolean {
        val s = _state.value
        val sel = s.selection ?: return false
        when (ConfigurationRules.validateName(name)) {
            NameValidation.Empty -> { _state.update { it.copy(message = "Give your configuration a name.") }; return false }
            NameValidation.TooLong -> { _state.update { it.copy(message = "Names can be up to ${ConfigurationRules.MAX_NAME_LENGTH} characters.") }; return false }
            NameValidation.Valid -> Unit
        }
        val now = Instant.now().toString()
        val existing = s.loadedConfiguration
        val config = if (existing != null && !asNew) {
            existing.copy(name = name.trim(), paintId = sel.paintId, wheelId = sel.wheelId, trimId = sel.trimId, interiorId = sel.interiorId, updatedAt = now)
        } else {
            ConfigurationRules.create(UUID.randomUUID().toString(), vehicleId, name, sel, now)
        }
        viewModelScope.launch {
            configurations.save(config)
            _state.update { it.copy(loadedConfiguration = config, message = "Saved \"${config.name}\" to My Garage") }
        }
        return true
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---------------------------------------------------------------- parts & exploded views
    fun toggleLights() = _state.update { it.copy(lightsOn = !it.lightsOn) }

    fun togglePart(animationId: String) = _state.update {
        it.copy(openAnimations = if (animationId in it.openAnimations) it.openAnimations - animationId else it.openAnimations + animationId)
    }

    fun closeAllParts() = _state.update { it.copy(openAnimations = emptySet()) }

    fun setExploded(viewId: String?) = _state.update { it.copy(explodedViewId = viewId) }

    fun resetAssembly() = _state.update { it.copy(explodedViewId = null, selectedComponentId = null) }

    // ---------------------------------------------------------------- selection
    fun selectComponent(id: String?) = _state.update { it.copy(selectedComponentId = id, selectedHotspotId = null) }
    fun selectHotspot(id: String?) = _state.update { it.copy(selectedHotspotId = id, selectedComponentId = null) }

    fun onTapNode(meshName: String?) {
        val s = _state.value
        val graph = s.graph ?: return
        if (meshName == null) return selectComponent(null)
        when (s.mode) {
            ViewerMode.Interior, ViewerMode.Safety -> {
                val module = if (s.mode == ViewerMode.Interior) "interior" else "safety"
                val nodes = s.hotspotsFor(module).associateBy { it.nodeName }
                val hit = graph.nearestAncestorIn(meshName, nodes.keys)
                selectHotspot(hit?.let { nodes[it]?.id })
            }
            ViewerMode.Mechanical, ViewerMode.Exterior, ViewerMode.Customize -> {
                val module = if (s.mode == ViewerMode.Mechanical) "mechanical" else "exterior"
                val byNode = s.componentsFor(module).associateBy { it.nodeName }
                val hit = graph.nearestAncestorIn(meshName, byNode.keys)
                selectComponent(hit?.let { byNode[it]?.id })
            }
        }
    }

    // ---------------------------------------------------------------- scene state for the renderer
    fun sceneState(s: ViewerUiState): SceneState {
        val c = s.content ?: return SceneState()
        val graph = s.graph ?: return SceneState()
        val sel = s.selection ?: DesignSelection.defaults(c.customization)
        val wheel = c.customization.wheels.firstOrNull { it.id == sel.wheelId } ?: c.customization.wheels.first()
        val hidden = VisibilityRules.hiddenNodes(s.mode, c.components, graph, wheel.nodeSuffix, c.customization.wheels.map { it.nodeSuffix })
        fun look(list: List<ColorOption>, id: String) = list.firstOrNull { it.id == id }?.let { MaterialLook(ColorUtil.hexToLinear(it.hex), it.metallic, it.roughness) }
        val looks = buildMap {
            look(c.customization.paints, sel.paintId)?.let { put("Paint", it) }
            look(c.customization.trims, sel.trimId)?.let { put("Accent", it) }
            look(c.customization.interiors, sel.interiorId)?.let { put("InteriorFabric", it) }
        }
        val highlighted = when {
            s.selectedComponent != null -> graph.descendantsAndSelf(s.selectedComponent!!.nodeName).filter { it in graph.materialByMesh }.toSet()
            s.selectedHotspot != null -> graph.descendantsAndSelf(s.selectedHotspot!!.nodeName).filter { it in graph.materialByMesh }.toSet()
            else -> emptySet()
        }
        return SceneState(hidden, looks, s.lightsOn, s.openAnimations, s.explodedViewId, highlighted, s.reducedMotion)
    }

    suspend fun lastMode(): String? = settings.lastExplored.first()?.second
}
