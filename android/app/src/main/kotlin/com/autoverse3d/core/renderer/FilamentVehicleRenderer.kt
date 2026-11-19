package com.autoverse3d.core.renderer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.autoverse3d.domain.PartAnimator
import com.autoverse3d.domain.model.ComponentCatalog
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Filament implementation of [VehicleRenderer]. All Filament calls happen on the main thread; only file
 * reading is done on an IO dispatcher. Resources are released in [destroy] when the viewer leaves the screen.
 */
class FilamentVehicleRenderer(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val catalog: ComponentCatalog,
    private val backgroundLinear: FloatArray,
) : VehicleRenderer {

    companion object {
        @Volatile private var initialized = false
        fun ensureInit() {
            if (!initialized) {
                Filament.init()
                Gltfio.init()
                initialized = true
            }
        }
        private const val VISIBLE_LAYER = 0x1
    }

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val camera: Camera
    private val cameraEntity: Int
    private val sunEntity: Int
    private val fillEntity: Int
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper: DisplayHelper
    private var swapChain: SwapChain? = null
    private val materialProvider: UbershaderProvider
    private val assetLoader: AssetLoader
    private val resourceLoader: ResourceLoader
    private var indirectLight: IndirectLight? = null
    private var reflections: Texture? = null

    private var vehicle: FilamentAsset? = null
    private var studio: FilamentAsset? = null
    private val entityByName = HashMap<String, Int>()
    private val nameByEntity = HashMap<Int, String>()
    private val originalTransforms = HashMap<String, FloatArray>()
    private val materialsByName = HashMap<String, MutableList<MaterialInstance>>()
    private val originalEmissive = HashMap<String, FloatArray>()
    private val highlightOverrides = HashMap<Int, List<Pair<Int, MaterialInstance>>>() // renderable -> (prim, original)
    private val highlightInstances = mutableListOf<MaterialInstance>()

    private val animator = PartAnimator(catalog)
    private var state = SceneState()
    private var appliedHidden: Set<String> = emptySet()
    private var appliedHighlight: Set<String> = emptySet()

    private var rig: CameraRig? = null
    private var targetRig: CameraRig? = null
    private var aspect = 1.0
    private var lastFrameNanos = 0L
    private var running = false
    private var destroyed = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || destroyed) return
            Choreographer.getInstance().postFrameCallback(this)
            val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
            lastFrameNanos = frameTimeNanos
            animator.step(dt, state.reducedMotion)
            applyAnimatedTransforms()
            stepCamera(dt)
            val sc = swapChain ?: return
            if (uiHelper.isReadyToRender && renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }

    init {
        ensureInit()
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        cameraEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)
        camera.setExposure(16f, 1f / 125f, 100f)
        view.scene = scene
        view.camera = camera
        view.antiAliasing = View.AntiAliasing.FXAA
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = true }
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = doubleArrayOf(backgroundLinear[0].toDouble(), backgroundLinear[1].toDouble(), backgroundLinear[2].toDouble(), 1.0)
        }
        displayHelper = DisplayHelper(context)
        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine)

        sunEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN)
            .color(1f, 0.98f, 0.95f).intensity(95_000f).direction(-0.35f, -1f, -0.45f).castShadows(true)
            .build(engine, sunEntity)
        fillEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.85f, 0.9f, 1f).intensity(30_000f).direction(0.6f, -0.3f, 0.8f).castShadows(false)
            .build(engine, fillEntity)
        scene.addEntity(sunEntity)
        scene.addEntity(fillEntity)
        try {
            buildStudioLighting()
        } catch (e: RuntimeException) {
            // Fallback: irradiance-only lighting if the device cannot build the reflection cubemap.
            reflections?.let { engine.destroyTexture(it) }
            reflections = null
            indirectLight = IndirectLight.Builder().irradiance(1, floatArrayOf(0.62f, 0.64f, 0.68f)).intensity(30_000f).build(engine)
            scene.indirectLight = indirectLight
        }

        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
                surfaceView.display?.let { displayHelper.attach(renderer, it) }
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                aspect = width.toDouble() / max(1, height)
                applyCamera()
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    /** Procedural studio environment: soft sky-to-floor gradient reflections + constant irradiance. */
    private fun buildStudioLighting() {
        val size = 32
        val levels = 6
        val tex = Texture.Builder().width(size).height(size).levels(levels).sampler(Texture.Sampler.SAMPLER_CUBEMAP)
            .format(Texture.InternalFormat.RGBA8)
            .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE).build(engine)
        val faceBytes = size * size * 4
        val buffer = ByteBuffer.allocateDirect(faceBytes * 6).order(ByteOrder.nativeOrder())
        for (face in 0 until 6) for (j in 0 until size) for (i in 0 until size) {
            val u = 2f * (i + 0.5f) / size - 1f
            val v = 2f * (j + 0.5f) / size - 1f
            val d = when (face) { // +X, -X, +Y, -Y, +Z, -Z
                0 -> floatArrayOf(1f, -v, -u); 1 -> floatArrayOf(-1f, -v, u)
                2 -> floatArrayOf(u, 1f, v); 3 -> floatArrayOf(u, -1f, -v)
                4 -> floatArrayOf(u, -v, 1f); else -> floatArrayOf(-u, -v, -1f)
            }
            val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
            val y = d[1] / len
            val t = ((y + 1f) / 2f).coerceIn(0f, 1f)
            val softbox = if (y > 0.75f) 0.35f else 0f
            val horizon = 0.15f * (1f - abs(y)).let { it * it * it }
            val c = (0.10f + 0.65f * t * t + softbox + horizon).coerceIn(0f, 1f)
            buffer.put((c * 255).toInt().toByte()); buffer.put((c * 255).toInt().toByte())
            buffer.put((minOf(1f, c * 1.05f) * 255).toInt().toByte()); buffer.put(255.toByte())
        }
        buffer.flip()
        // Cubemap faces are uploaded as 6 layers (z offset = face index).
        tex.setImage(engine, 0, 0, 0, 0, size, size, 6, Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE))
        tex.generateMipmaps(engine)
        reflections = tex
        val ibl = IndirectLight.Builder()
            .reflections(tex)
            .irradiance(1, floatArrayOf(0.62f, 0.64f, 0.68f))
            .intensity(28_000f)
            .build(engine)
        scene.indirectLight = ibl
        indirectLight = ibl
    }

    fun start() {
        if (running || destroyed) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override suspend fun loadVehicle(vehicle: ModelSource, studio: ModelSource?) {
        val vehicleBytes = withContext(Dispatchers.IO) { readSource(vehicle) }
        val studioBytes = studio?.let { withContext(Dispatchers.IO) { readSource(it) } }
        if (destroyed) return
        unload()
        studioBytes?.let { bytes ->
            assetLoader.createAsset(bytes)?.also { a ->
                resourceLoader.loadResources(a)
                a.releaseSourceData()
                scene.addEntities(a.entities)
                val rm = engine.renderableManager
                a.renderableEntities.forEach { e -> rm.getInstance(e).takeIf { it != 0 }?.let { rm.setReceiveShadows(it, true) } }
                this.studio = a
            }
        }
        val asset = assetLoader.createAsset(vehicleBytes) ?: throw IllegalStateException("The 3D model could not be parsed")
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        this.vehicle = asset
        val tm = engine.transformManager
        val rm = engine.renderableManager
        for (e in asset.entities) {
            val name = asset.getName(e) ?: continue
            entityByName[name] = e
            nameByEntity[e] = name
        }
        asset.renderableEntities.forEach { e ->
            rm.getInstance(e).takeIf { it != 0 }?.let { rm.setCastShadows(it, true); rm.setReceiveShadows(it, true) }
        }
        for (node in animator.animatedNodes) {
            val e = entityByName[node] ?: continue
            val m = FloatArray(16)
            tm.getTransform(tm.getInstance(e), m)
            originalTransforms[node] = m
        }
        asset.instance.materialInstances.forEach { mi ->
            materialsByName.getOrPut(mi.name) { mutableListOf() }.add(mi)
        }
        val forced = state
        state = SceneState()
        appliedHidden = emptySet()
        appliedHighlight = emptySet()
        applyScene(forced)
    }

    private fun readSource(source: ModelSource): ByteBuffer {
        val bytes = when (source) {
            is ModelSource.Bundled -> context.assets.open(source.assetPath).use { it.readBytes() }
            is ModelSource.Downloaded -> File(source.filePath).readBytes()
        }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
    }

    override fun unload() {
        clearHighlight()
        vehicle?.let { scene.removeEntities(it.entities); assetLoader.destroyAsset(it) }
        studio?.let { scene.removeEntities(it.entities); assetLoader.destroyAsset(it) }
        vehicle = null
        studio = null
        entityByName.clear(); nameByEntity.clear(); originalTransforms.clear(); materialsByName.clear(); originalEmissive.clear()
    }

    // ------------------------------------------------------------------ camera
    override fun setCamera(rig: CameraRig, animate: Boolean) {
        val current = this.rig
        if (!animate || current == null || current::class != rig::class || state.reducedMotion) {
            this.rig = rig
            targetRig = null
        } else {
            targetRig = rig
        }
        applyCamera()
    }

    override fun currentCamera(): CameraRig? = targetRig ?: rig

    private fun stepCamera(dt: Float) {
        val target = targetRig ?: return
        val cur = rig ?: return
        val k = (dt * 6f).coerceIn(0f, 1f)
        rig = when {
            cur is CameraRig.Orbit && target is CameraRig.Orbit -> {
                val a = cur.camera; val b = target.camera
                var dy = (b.yaw - a.yaw) % 360f
                if (dy > 180f) dy -= 360f
                if (dy < -180f) dy += 360f
                val next = a.copy(yaw = a.yaw + dy * k, pitch = a.pitch + (b.pitch - a.pitch) * k,
                    distance = a.distance + (b.distance - a.distance) * k,
                    target = List(3) { a.target[it] + (b.target[it] - a.target[it]) * k }, presetTarget = b.presetTarget)
                if (abs(dy) < 0.05f && abs(b.pitch - a.pitch) < 0.05f && abs(b.distance - a.distance) < 0.005f) { targetRig = null; target } else CameraRig.Orbit(next)
            }
            else -> { targetRig = null; target }
        }
        applyCamera()
    }

    private fun applyCamera() {
        when (val r = rig) {
            is CameraRig.Orbit -> {
                val eye = r.camera.eye()
                val t = r.camera.target
                camera.setProjection(40.0, aspect, 0.05, 80.0, Camera.Fov.VERTICAL)
                camera.lookAt(eye[0].toDouble(), eye[1].toDouble(), eye[2].toDouble(), t[0].toDouble(), t[1].toDouble(), t[2].toDouble(), 0.0, 1.0, 0.0)
            }
            is CameraRig.Interior -> {
                val e = r.camera.eye
                val l = r.camera.lookAt()
                camera.setProjection(r.camera.fovDeg.toDouble(), aspect, 0.02, 40.0, Camera.Fov.VERTICAL)
                camera.lookAt(e[0].toDouble(), e[1].toDouble(), e[2].toDouble(), l[0].toDouble(), l[1].toDouble(), l[2].toDouble(), 0.0, 1.0, 0.0)
            }
            null -> Unit
        }
    }

    private fun pixelsToDegrees(px: Float) = px / max(1, surfaceView.height) * 180f

    override fun onRotate(dxPx: Float, dyPx: Float) {
        targetRig = null
        rig = when (val r = rig) {
            is CameraRig.Orbit -> CameraRig.Orbit(r.camera.rotated(-pixelsToDegrees(dxPx), pixelsToDegrees(dyPx)))
            is CameraRig.Interior -> CameraRig.Interior(r.camera.rotated(pixelsToDegrees(dxPx) * 0.6f, -pixelsToDegrees(dyPx) * 0.6f))
            null -> null
        }
        applyCamera()
    }

    override fun onPan(dxPx: Float, dyPx: Float) {
        val r = rig as? CameraRig.Orbit ?: return
        targetRig = null
        val metresPerPx = (2 * r.camera.distance * tan(20.0 * PI / 180.0)).toFloat() / max(1, surfaceView.height)
        rig = CameraRig.Orbit(r.camera.panned(dxPx * metresPerPx, dyPx * metresPerPx))
        applyCamera()
    }

    override fun onZoom(scale: Float) {
        targetRig = null
        rig = when (val r = rig) {
            is CameraRig.Orbit -> CameraRig.Orbit(r.camera.zoomed(scale))
            is CameraRig.Interior -> CameraRig.Interior(r.camera.zoomed(scale))
            null -> null
        }
        applyCamera()
    }

    // ------------------------------------------------------------------ scene state
    override fun applyScene(state: SceneState) {
        val previous = this.state
        this.state = state
        if (vehicle == null) return
        if (state.hiddenNodes != appliedHidden) {
            val rm = engine.renderableManager
            for ((name, entity) in entityByName) {
                val ri = rm.getInstance(entity)
                if (ri != 0) rm.setLayerMask(ri, 0xff, if (name in state.hiddenNodes) 0 else VISIBLE_LAYER)
            }
            appliedHidden = state.hiddenNodes
        }
        if (state.materialLooks != previous.materialLooks) {
            state.materialLooks.forEach { (name, look) ->
                materialsByName[name]?.forEach { mi ->
                    mi.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, look.linearRgb[0], look.linearRgb[1], look.linearRgb[2], 1f)
                    mi.setParameter("metallicFactor", look.metallic)
                    mi.setParameter("roughnessFactor", look.roughness)
                }
            }
            if (state.highlightedNodes.isNotEmpty()) appliedHighlight = emptySet() // re-duplicate with new colours
        }
        applyLights(state.lightsOn)
        catalog.animations.forEach { a -> animator.setPartOpen(a.id, a.id in state.openAnimations) }
        animator.setExploded(state.explodedViewId)
        if (state.highlightedNodes != appliedHighlight) applyHighlight(state.highlightedNodes)
    }

    private fun applyLights(on: Boolean) {
        listOf("HeadlightLens" to floatArrayOf(1f, 0.95f, 0.85f), "TaillightLens" to floatArrayOf(0.8f, 0.02f, 0.02f)).forEach { (name, color) ->
            materialsByName[name]?.forEach { mi ->
                val k = if (on) 6f else 0f
                mi.setParameter("emissiveFactor", color[0] * k, color[1] * k, color[2] * k)
            }
        }
    }

    private fun clearHighlight() {
        val rm = engine.renderableManager
        highlightOverrides.forEach { (entity, prims) ->
            val ri = rm.getInstance(entity)
            if (ri != 0) prims.forEach { (prim, original) -> rm.setMaterialInstanceAt(ri, prim, original) }
        }
        highlightOverrides.clear()
        highlightInstances.forEach { engine.destroyMaterialInstance(it) }
        highlightInstances.clear()
    }

    private fun applyHighlight(nodes: Set<String>) {
        clearHighlight()
        appliedHighlight = nodes
        if (nodes.isEmpty()) return
        val rm = engine.renderableManager
        for (name in nodes) {
            val entity = entityByName[name] ?: continue
            val ri = rm.getInstance(entity)
            if (ri == 0) continue
            val prims = mutableListOf<Pair<Int, MaterialInstance>>()
            for (p in 0 until rm.getPrimitiveCount(ri)) {
                val original = rm.getMaterialInstanceAt(ri, p)
                val copy = MaterialInstance.duplicate(original, "${original.name}_highlight")
                copy.setParameter("emissiveFactor", 0.18f, 0.75f, 0.70f)
                rm.setMaterialInstanceAt(ri, p, copy)
                highlightInstances += copy
                prims += p to original
            }
            highlightOverrides[entity] = prims
        }
    }

    private fun applyAnimatedTransforms() {
        val tm = engine.transformManager
        for ((node, original) in originalTransforms) {
            val e = entityByName[node] ?: continue
            tm.setTransform(tm.getInstance(e), animator.localTransform(node, original))
        }
    }

    override fun resetScene() {
        animator.resetAll()
    }

    override fun pick(x: Float, y: Float, onResult: (String?) -> Unit) {
        if (vehicle == null) return onResult(null)
        val h = surfaceView.height
        view.pick(x.toInt(), (h - y).toInt(), Handler(Looper.getMainLooper())) { result ->
            onResult(nameByEntity[result.renderable])
        }
    }

    fun destroy() {
        if (destroyed) return
        stop()
        destroyed = true
        unload()
        uiHelper.detach()
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
        indirectLight?.let { engine.destroyIndirectLight(it) }
        reflections?.let { engine.destroyTexture(it) }
        engine.destroyEntity(sunEntity)
        engine.destroyEntity(fillEntity)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }
}
