package com.autoverse3d.domain

import com.autoverse3d.domain.model.CustomizationOptions
import com.autoverse3d.domain.model.VehicleConfiguration

/** The live (unsaved) design in the customization studio. */
data class DesignSelection(val paintId: String, val wheelId: String, val trimId: String, val interiorId: String) {
    companion object {
        fun defaults(options: CustomizationOptions) =
            DesignSelection(options.defaults.paintId, options.defaults.wheelId, options.defaults.trimId, options.defaults.interiorId)

        fun of(c: VehicleConfiguration) = DesignSelection(c.paintId, c.wheelId, c.trimId, c.interiorId)
    }
}

data class ConfigurationDifference(val field: String, val saved: String, val current: String)

sealed interface NameValidation {
    data object Valid : NameValidation
    data object Empty : NameValidation
    data object TooLong : NameValidation
}

object ConfigurationRules {
    const val MAX_NAME_LENGTH = 60

    fun validateName(name: String): NameValidation = when {
        name.isBlank() -> NameValidation.Empty
        name.trim().length > MAX_NAME_LENGTH -> NameValidation.TooLong
        else -> NameValidation.Valid
    }

    fun create(id: String, vehicleId: String, name: String, selection: DesignSelection, nowIso: String) = VehicleConfiguration(
        id = id, vehicleId = vehicleId, name = name.trim(), paintId = selection.paintId, wheelId = selection.wheelId,
        trimId = selection.trimId, interiorId = selection.interiorId, createdAt = nowIso, updatedAt = nowIso,
    )

    fun rename(config: VehicleConfiguration, newName: String, nowIso: String) = config.copy(name = newName.trim(), updatedAt = nowIso)

    /** "Midnight" -> "Midnight copy" -> "Midnight copy 2" ... unique among existing names. */
    fun duplicateName(base: String, existing: Collection<String>): String {
        val root = base.replace(Regex(" copy( \\d+)?$"), "")
        var candidate = "$root copy"
        var n = 2
        while (candidate in existing) candidate = "$root copy ${n++}"
        return candidate.take(MAX_NAME_LENGTH)
    }

    fun duplicate(config: VehicleConfiguration, newId: String, existingNames: Collection<String>, nowIso: String) =
        config.copy(id = newId, name = duplicateName(config.name, existingNames), createdAt = nowIso, updatedAt = nowIso)

    /** Human-readable differences between a saved configuration and the current design. */
    fun compare(saved: VehicleConfiguration, current: DesignSelection, options: CustomizationOptions): List<ConfigurationDifference> {
        val diffs = mutableListOf<ConfigurationDifference>()
        fun name(list: List<Pair<String, String>>, id: String) = list.firstOrNull { it.first == id }?.second ?: id
        val paints = options.paints.map { it.id to it.name }
        val wheels = options.wheels.map { it.id to it.name }
        val trims = options.trims.map { it.id to it.name }
        val interiors = options.interiors.map { it.id to it.name }
        if (saved.paintId != current.paintId) diffs += ConfigurationDifference("Paint", name(paints, saved.paintId), name(paints, current.paintId))
        if (saved.wheelId != current.wheelId) diffs += ConfigurationDifference("Wheels", name(wheels, saved.wheelId), name(wheels, current.wheelId))
        if (saved.trimId != current.trimId) diffs += ConfigurationDifference("Trim", name(trims, saved.trimId), name(trims, current.trimId))
        if (saved.interiorId != current.interiorId) diffs += ConfigurationDifference("Interior", name(interiors, saved.interiorId), name(interiors, current.interiorId))
        return diffs
    }

    /** Falls back to defaults for option ids that no longer exist (content updates). */
    fun sanitize(selection: DesignSelection, options: CustomizationOptions) = DesignSelection(
        paintId = selection.paintId.takeIf { id -> options.paints.any { it.id == id } } ?: options.defaults.paintId,
        wheelId = selection.wheelId.takeIf { id -> options.wheels.any { it.id == id } } ?: options.defaults.wheelId,
        trimId = selection.trimId.takeIf { id -> options.trims.any { it.id == id } } ?: options.defaults.trimId,
        interiorId = selection.interiorId.takeIf { id -> options.interiors.any { it.id == id } } ?: options.defaults.interiorId,
    )
}

object ColorUtil {
    /** "#RRGGBB" -> sRGB floats 0..1 */
    fun hexToSrgb(hex: String): FloatArray {
        val h = hex.removePrefix("#")
        require(h.length == 6) { "Expected #RRGGBB, got $hex" }
        return FloatArray(3) { h.substring(it * 2, it * 2 + 2).toInt(16) / 255f }
    }

    fun srgbToLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    fun hexToLinear(hex: String): FloatArray = hexToSrgb(hex).map { srgbToLinear(it) }.toFloatArray()
}
