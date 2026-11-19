package com.autoverse3d.core.assets

import android.content.Context
import com.autoverse3d.BuildConfig
import com.autoverse3d.core.database.InstalledPackEntity
import com.autoverse3d.core.database.PackDao
import com.autoverse3d.core.network.AutoVerseApi
import com.autoverse3d.core.renderer.ModelSource
import com.autoverse3d.data.ContentRepository
import com.autoverse3d.domain.AssetManifestValidator
import com.autoverse3d.domain.Checksum
import com.autoverse3d.domain.ContentParser
import com.autoverse3d.domain.DownloadEvent
import com.autoverse3d.domain.DownloadState
import com.autoverse3d.domain.DownloadStateMachine
import com.autoverse3d.domain.model.AssetManifest
import com.autoverse3d.domain.model.SceneDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ResolvedModel(val source: ModelSource, val studio: ModelSource?, val scene: SceneDescriptor, val originLabel: String)

data class PackStatus(val state: DownloadState, val message: String? = null)

/**
 * Resolves which model file to render (verified download first, bundled copy as the always-available
 * baseline) and manages downloadable vehicle packs: size shown before download, progress, pause/resume via
 * HTTP Range, checksum verification, and replacing an installed pack only after the new one is verified.
 */
@Singleton
class AssetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val content: ContentRepository,
    private val packs: PackDao,
    private val api: AutoVerseApi,
    private val http: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statuses = HashMap<String, MutableStateFlow<PackStatus>>()
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val manifests = java.util.concurrent.ConcurrentHashMap<String, AssetManifest>()

    private fun packDir(manifestId: String) = File(context.filesDir, "packs/$manifestId").apply { mkdirs() }

    fun status(manifestId: String): StateFlow<PackStatus> = flowFor(manifestId).asStateFlow()

    private fun flowFor(id: String) = synchronized(statuses) { statuses.getOrPut(id) { MutableStateFlow(PackStatus(DownloadState.NotDownloaded)) } }

    private fun dispatch(id: String, event: DownloadEvent, message: String? = null) {
        flowFor(id).update { PackStatus(DownloadStateMachine.reduce(it.state, event), message) }
    }

    suspend fun resolve(vehicleId: String, manifestId: String): ResolvedModel = withContext(Dispatchers.IO) {
        val studioSrc = ModelSource.Bundled("build/studio_platform_v1.glb")
        val installed = packs.latestFor(vehicleId)
        if (installed != null && File(installed.filePath).exists() && File(installed.sceneDescriptorPath).exists()) {
            val scene = ContentParser.scene(File(installed.sceneDescriptorPath).readText())
            return@withContext ResolvedModel(ModelSource.Downloaded(installed.filePath), studioSrc, scene, "Downloaded pack v${installed.version}")
        }
        val manifest = content.content().bundledManifests[manifestId] ?: error("No bundled manifest $manifestId")
        val scene = ContentParser.scene(content.readBundledText("build/${manifest.sceneDescriptor.fileName}"))
        ResolvedModel(ModelSource.Bundled("build/${manifest.fileName}"), studioSrc, scene, "Built-in copy v${manifest.version}")
    }

    /** Reads the installed state, then asks the backend for the latest manifest (works offline too). */
    fun refresh(manifestId: String, vehicleId: String) {
        scope.launch {
            val installed = packs.latestFor(vehicleId)
            if (installed != null && flowFor(manifestId).value.state is DownloadState.NotDownloaded) {
                flowFor(manifestId).value = PackStatus(DownloadState.Installed(installed.version, installed.sizeBytes))
            }
            try {
                val manifest = api.manifest(manifestId)
                val issues = AssetManifestValidator.validate(manifest, BuildConfig.VERSION_NAME, vehicleId)
                if (issues.isNotEmpty()) {
                    flowFor(manifestId).update { it.copy(message = "This pack needs a newer app version or is invalid.") }
                    return@launch
                }
                manifests[manifestId] = manifest
                dispatch(manifestId, DownloadEvent.ManifestLoaded(manifest.fileSizeBytes + manifest.sceneDescriptor.fileSizeBytes, manifest.version))
            } catch (e: Exception) {
                flowFor(manifestId).update { it.copy(message = "Offline — connect to the internet to download packs.") }
            }
        }
    }

    fun start(manifestId: String, vehicleId: String) {
        val manifest = manifests[manifestId] ?: return
        if (jobs[manifestId]?.isActive == true) return
        val state = flowFor(manifestId).value.state
        dispatch(manifestId, if (state is DownloadState.Paused) DownloadEvent.Resume else DownloadEvent.Start)
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { download(manifest, vehicleId) }
        jobs[manifestId] = job
        job.start()
    }

    fun pause(manifestId: String) {
        jobs.remove(manifestId)?.cancel()
        dispatch(manifestId, DownloadEvent.Pause)
    }

    private suspend fun download(manifest: AssetManifest, vehicleId: String) {
        val id = manifest.id
        val myJob = kotlin.coroutines.coroutineContext[Job]
        val dir = packDir(id)
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val modelUrl = base + (manifest.downloadUrls["android"] ?: "/v1/assets/$id/files/${manifest.fileName}")
        val sceneUrl = base + (manifest.downloadUrls["sceneDescriptor"] ?: "/v1/assets/$id/files/${manifest.sceneDescriptor.fileName}")
        val modelPart = File(dir, "${manifest.fileName}.part")
        val scenePart = File(dir, "${manifest.sceneDescriptor.fileName}.part")
        try {
            fetch(sceneUrl, scenePart) {}
            fetch(modelUrl, modelPart) { bytes -> dispatch(id, DownloadEvent.Progress(bytes + scenePart.length())) }
            if (!scopeActive()) return
            dispatch(id, DownloadEvent.TransferComplete)
            val okModel = modelPart.inputStream().use { Checksum.matches(it, manifest.checksum) }
            val okScene = scenePart.inputStream().use { Checksum.matches(it, manifest.sceneDescriptor.checksum) }
            if (!okModel || !okScene) {
                modelPart.delete(); scenePart.delete()
                dispatch(id, DownloadEvent.Error("Integrity check failed. The download was discarded — please retry."))
                return
            }
            val model = File(dir, manifest.fileName)
            val scene = File(dir, manifest.sceneDescriptor.fileName)
            modelPart.renameTo(model); scenePart.renameTo(scene)
            val previous = packs.latestFor(vehicleId)
            packs.insert(InstalledPackEntity(id, vehicleId, manifest.version, model.absolutePath, scene.absolutePath,
                model.length() + scene.length(), manifest.checksum, Instant.now().toString()))
            // Only now that the replacement is verified and recorded is the older pack removed.
            if (previous != null && previous.manifestId != id) {
                packs.delete(previous.manifestId)
                File(context.filesDir, "packs/${previous.manifestId}").deleteRecursively()
            }
            dispatch(id, DownloadEvent.Verified(manifest.version))
        } catch (e: IOException) {
            if (scopeActive()) dispatch(id, DownloadEvent.Error("Download interrupted. Tap retry to resume."))
        } finally {
            // Only clear our own entry: after pause + quick resume a newer job may already be registered.
            myJob?.let { jobs.remove(id, it) }
        }
    }

    private suspend fun scopeActive() = kotlin.coroutines.coroutineContext.isActive

    /** Streams [url] into [part], resuming from its current length with an HTTP Range request. */
    private suspend fun fetch(url: String, part: File, onProgress: (Long) -> Unit) {
        val existing = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(url).apply { if (existing > 0) header("Range", "bytes=$existing-") }.build()
        http.newCall(request).execute().use { response ->
            if (response.code == 416) return // already complete
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val append = response.code == 206
            val body = response.body ?: throw IOException("empty body")
            java.io.FileOutputStream(part, append).use { out ->
                var written = if (append) existing else 0L
                val buf = ByteArray(32 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        if (!kotlin.coroutines.coroutineContext.isActive) return
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        onProgress(written)
                    }
                }
            }
        }
    }

    fun remove(manifestId: String, vehicleId: String) {
        scope.launch {
            jobs.remove(manifestId)?.cancel()
            packs.delete(manifestId)
            packDir(manifestId).deleteRecursively()
            dispatch(manifestId, DownloadEvent.Remove)
            refresh(manifestId, vehicleId)
        }
    }

    fun observeInstalled() = packs.observeAll()
}
