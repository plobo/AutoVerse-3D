package com.autoverse3d.domain

import com.autoverse3d.domain.model.AssetManifest
import com.autoverse3d.domain.model.CameraPresets
import com.autoverse3d.domain.model.ComponentCatalog
import com.autoverse3d.domain.model.CustomizationOptions
import com.autoverse3d.domain.model.EvLabContent
import com.autoverse3d.domain.model.HotspotCatalog
import com.autoverse3d.domain.model.LearningContent
import com.autoverse3d.domain.model.SceneDescriptor
import com.autoverse3d.domain.model.VehicleCatalog
import kotlinx.serialization.json.Json

/** Decoding for the shared JSON contracts. Unknown keys are ignored so newer content stays readable. */
object ContentParser {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    fun vehicles(text: String) = json.decodeFromString<VehicleCatalog>(text)
    fun components(text: String) = json.decodeFromString<ComponentCatalog>(text)
    fun hotspots(text: String) = json.decodeFromString<HotspotCatalog>(text)
    fun customization(text: String) = json.decodeFromString<CustomizationOptions>(text)
    fun cameraPresets(text: String) = json.decodeFromString<CameraPresets>(text)
    fun learning(text: String) = json.decodeFromString<LearningContent>(text)
    fun evLab(text: String) = json.decodeFromString<EvLabContent>(text)
    fun manifest(text: String) = json.decodeFromString<AssetManifest>(text)
    fun scene(text: String) = json.decodeFromString<SceneDescriptor>(text)
}
