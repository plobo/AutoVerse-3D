package com.autoverse3d.data

import android.content.Context
import com.autoverse3d.domain.ContentParser
import com.autoverse3d.domain.ContentSearch
import com.autoverse3d.domain.SearchDocument
import com.autoverse3d.domain.model.AssetManifest
import com.autoverse3d.domain.model.CameraPresets
import com.autoverse3d.domain.model.ComponentCatalog
import com.autoverse3d.domain.model.CustomizationOptions
import com.autoverse3d.domain.model.EvLabContent
import com.autoverse3d.domain.model.HotspotCatalog
import com.autoverse3d.domain.model.LearningContent
import com.autoverse3d.domain.model.VehicleCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** All offline content bundled with the app (copied from /shared at build time). */
data class AppContent(
    val vehicles: VehicleCatalog,
    val components: ComponentCatalog,
    val hotspots: HotspotCatalog,
    val customization: CustomizationOptions,
    val cameras: CameraPresets,
    val learning: LearningContent,
    val evLab: EvLabContent,
    val bundledManifests: Map<String, AssetManifest>,
    val searchIndex: List<SearchDocument>,
)

interface ContentSource {
    suspend fun content(): AppContent
}

@Singleton
class ContentRepository @Inject constructor(@ApplicationContext private val context: Context) : ContentSource {
    private val mutex = Mutex()
    private var cached: AppContent? = null

    private fun read(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }

    override suspend fun content(): AppContent = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val components = ContentParser.components(read("data/components.json"))
            val hotspots = ContentParser.hotspots(read("data/hotspots.json"))
            val learning = ContentParser.learning(read("content/learning_modules.json"))
            val ev = ContentParser.evLab(read("content/ev_lab.json"))
            val manifests = (context.assets.list("manifests") ?: emptyArray()).filter { it.endsWith(".json") }
                .map { ContentParser.manifest(read("manifests/$it")) }.associateBy { it.id }
            AppContent(
                vehicles = ContentParser.vehicles(read("data/vehicles.json")),
                components = components,
                hotspots = hotspots,
                customization = ContentParser.customization(read("data/customization_options.json")),
                cameras = ContentParser.cameraPresets(read("data/camera_presets.json")),
                learning = learning,
                evLab = ev,
                bundledManifests = manifests,
                searchIndex = ContentSearch.buildIndex(learning, components, hotspots, ev),
            )
        }.also { cached = it }
    }

    fun readBundledText(path: String) = read(path)
}
