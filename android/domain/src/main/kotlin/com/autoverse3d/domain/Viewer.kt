package com.autoverse3d.domain

import com.autoverse3d.domain.model.ComponentCatalog
import com.autoverse3d.domain.model.InteriorPreset
import com.autoverse3d.domain.model.OrbitLimits
import com.autoverse3d.domain.model.OrbitPreset
import com.autoverse3d.domain.model.SceneDescriptor
import com.autoverse3d.domain.model.ViewerMode
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

typealias Vec3 = FloatArray

private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()
private fun deg(rad: Float) = Math.toDegrees(rad.toDouble()).toFloat()

/** Parent/child lookups over the scene descriptor generated with the asset. */
class SceneGraph(descriptor: SceneDescriptor) {
    private val children: Map<String, List<String>> = descriptor.nodes.filter { it.parent != null }.groupBy({ it.parent!! }, { it.name })
    private val parents: Map<String, String?> = descriptor.nodes.associate { it.name to it.parent }
    val materialByMesh: Map<String, String> = descriptor.nodes.filter { it.material != null }.associate { it.name to it.material!! }
    val names: Set<String> = parents.keys

    fun descendantsAndSelf(name: String): Set<String> {
        val out = linkedSetOf<String>()
        val stack = ArrayDeque(listOf(name))
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (out.add(n)) children[n]?.let { stack.addAll(it) }
        }
        return out
    }

    fun meshesUsing(material: String): List<String> = materialByMesh.filterValues { it == material }.keys.toList()

    /** Walks up from a tapped mesh to the closest ancestor in [candidates]. */
    fun nearestAncestorIn(name: String, candidates: Set<String>): String? {
        var cur: String? = name
        while (cur != null) {
            if (cur in candidates) return cur
            cur = parents[cur]
        }
        return null
    }
}

object VisibilityRules {
    /** Every node (with descendants) that must be hidden for a viewer mode and selected wheel style. */
    fun hiddenNodes(mode: ViewerMode, catalog: ComponentCatalog, graph: SceneGraph, activeWheelSuffix: String, allWheelSuffixes: List<String>): Set<String> {
        val hidden = mutableSetOf<String>()
        catalog.modeVisibility[mode.visibilityKey]?.hide?.forEach { hidden += graph.descendantsAndSelf(it) }
        for (wheel in listOf("FrontLeft", "FrontRight", "RearLeft", "RearRight")) {
            allWheelSuffixes.filter { it != activeWheelSuffix }.forEach { hidden += graph.descendantsAndSelf("${wheel}_$it") }
        }
        return hidden
    }
}

/** Orbit camera used by the exterior, mechanical, safety and customization viewers. */
data class OrbitCamera(
    val yaw: Float,
    val pitch: Float,
    val distance: Float,
    val target: List<Float>,
    val presetTarget: List<Float> = target,
    val limits: OrbitLimits,
) {
    fun eye(): Vec3 {
        val cp = cos(rad(pitch))
        return floatArrayOf(
            target[0] + distance * cp * sin(rad(yaw)),
            target[1] + distance * sin(rad(pitch)),
            target[2] + distance * cp * cos(rad(yaw)),
        )
    }

    fun rotated(dYawDeg: Float, dPitchDeg: Float) = copy(
        yaw = ((yaw + dYawDeg) % 360f + 360f) % 360f,
        pitch = (pitch + dPitchDeg).coerceIn(limits.minPitch, limits.maxPitch),
    )

    /** scale > 1 zooms in (pinch out). */
    fun zoomed(scale: Float) = copy(distance = (distance / scale.coerceAtLeast(0.01f)).coerceIn(limits.minDistance, limits.maxDistance))

    /** Pans the target in the camera's screen plane; dx/dy are in metres at the target. */
    fun panned(dx: Float, dy: Float): OrbitCamera {
        val right = floatArrayOf(cos(rad(yaw)), 0f, -sin(rad(yaw)))
        val nt = floatArrayOf(target[0] - right[0] * dx, target[1] + dy, target[2] - right[2] * dx)
        val off = FloatArray(3) { nt[it] - presetTarget[it] }
        val len = sqrt(off[0] * off[0] + off[1] * off[1] + off[2] * off[2])
        val k = if (len > limits.maxPan) limits.maxPan / len else 1f
        return copy(target = List(3) { presetTarget[it] + off[it] * k })
    }

    companion object {
        fun from(preset: OrbitPreset, limits: OrbitLimits) =
            OrbitCamera(preset.yaw, preset.pitch, preset.distance, preset.target, preset.target, limits)
    }
}

/** First-person cabin camera: fixed eye, look direction adjustable within limits. */
data class InteriorCamera(
    val eye: List<Float>,
    val baseYaw: Float,
    val basePitch: Float,
    val yawOffset: Float = 0f,
    val pitchOffset: Float = 0f,
    val maxYawOffset: Float,
    val maxPitchOffset: Float,
    val fovDeg: Float = 70f,
) {
    fun lookAt(): Vec3 {
        val y = rad(baseYaw + yawOffset)
        val p = rad(basePitch + pitchOffset)
        return floatArrayOf(eye[0] + cos(p) * sin(y), eye[1] + sin(p), eye[2] + cos(p) * cos(y))
    }

    fun rotated(dYaw: Float, dPitch: Float) = copy(
        yawOffset = (yawOffset + dYaw).coerceIn(-maxYawOffset, maxYawOffset),
        pitchOffset = (pitchOffset + dPitch).coerceIn(-maxPitchOffset, maxPitchOffset),
    )

    fun zoomed(scale: Float) = copy(fovDeg = (fovDeg / scale).coerceIn(35f, 85f))

    companion object {
        fun from(preset: InteriorPreset, maxYaw: Float, maxPitch: Float): InteriorCamera {
            val d = FloatArray(3) { preset.lookAt[it] - preset.eye[it] }
            val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
            return InteriorCamera(preset.eye, deg(atan2(d[0], d[2])), deg(asin(d[1] / len)), maxYawOffset = maxYaw, maxPitchOffset = maxPitch)
        }
    }
}

object Easing {
    fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }
}

/** Column-major 4x4 matrices (Filament / OpenGL layout). */
object Mat4 {
    fun identity() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun translation(x: Float, y: Float, z: Float) = identity().also { it[12] = x; it[13] = y; it[14] = z }

    fun rotation(axis: List<Float>, angleDeg: Float): FloatArray {
        val len = sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2])
        val x = axis[0] / len; val y = axis[1] / len; val z = axis[2] / len
        val a = rad(angleDeg); val c = cos(a); val s = sin(a); val t = 1 - c
        return floatArrayOf(
            t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0f,
            t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0f,
            t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (col in 0 until 4) for (row in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
            r[col * 4 + row] = sum
        }
        return r
    }

    fun transformPoint(m: FloatArray, p: FloatArray) = floatArrayOf(
        m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12],
        m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13],
        m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14],
    )
}

/**
 * Drives part animations (doors/hood/trunk) and exploded views toward their targets.
 * Renderer-agnostic: the renderer asks for each node's local transform every frame.
 */
class PartAnimator(private val catalog: ComponentCatalog) {
    private val progress = mutableMapOf<String, Float>()  // key -> 0..1
    private val targets = mutableMapOf<String, Float>()
    private val durations = mutableMapOf<String, Float>()

    val animatedNodes: Set<String> =
        catalog.animations.map { it.nodeName }.toSet() + catalog.explodedViews.flatMap { v -> v.offsets.map { it.nodeName } }

    fun setPartOpen(animationId: String, open: Boolean) {
        val a = catalog.animations.firstOrNull { it.id == animationId } ?: return
        targets["anim:${a.id}"] = if (open) 1f else 0f
        durations["anim:${a.id}"] = a.durationSec
    }

    fun setExploded(viewId: String?) {
        catalog.explodedViews.forEach { v ->
            targets["explode:${v.id}"] = if (v.id == viewId) 1f else 0f
            durations["explode:${v.id}"] = v.durationSec
        }
    }

    fun resetAll() { targets.keys.forEach { targets[it] = 0f } }

    /** Advances time; returns true while anything is still moving. */
    fun step(dtSec: Float, reducedMotion: Boolean): Boolean {
        var moving = false
        for ((key, target) in targets) {
            val cur = progress[key] ?: 0f
            if (cur == target) continue
            val next = if (reducedMotion) target else {
                val delta = dtSec / (durations[key] ?: 1f)
                if (target > cur) minOf(target, cur + delta) else maxOf(target, cur - delta)
            }
            progress[key] = next
            if (next != target) moving = true
        }
        return moving
    }

    fun isOpen(animationId: String) = (targets["anim:$animationId"] ?: 0f) > 0.5f

    /** Local transform = translate(explode offset) * original * rotate(part animation). */
    fun localTransform(nodeName: String, original: FloatArray): FloatArray {
        var offset = floatArrayOf(0f, 0f, 0f)
        catalog.explodedViews.forEach { v ->
            val p = Easing.smoothstep(progress["explode:${v.id}"] ?: 0f)
            if (p > 0f) v.offsets.firstOrNull { it.nodeName == nodeName }?.let { o -> offset = FloatArray(3) { offset[it] + o.offset[it] * p } }
        }
        var m = original
        catalog.animations.filter { it.nodeName == nodeName }.forEach { a ->
            val p = Easing.smoothstep(progress["anim:${a.id}"] ?: 0f)
            if (p > 0f) m = Mat4.multiply(m, Mat4.rotation(a.axis, a.angleDeg * p))
        }
        return if (abs(offset[0]) + abs(offset[1]) + abs(offset[2]) > 0f) Mat4.multiply(Mat4.translation(offset[0], offset[1], offset[2]), m) else m
    }
}
