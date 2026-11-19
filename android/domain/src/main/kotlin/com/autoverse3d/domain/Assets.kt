package com.autoverse3d.domain

import com.autoverse3d.domain.model.AssetManifest
import java.io.InputStream
import java.security.MessageDigest

object Checksum {
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(bytes: ByteArray): String = sha256(bytes.inputStream())

    fun matches(input: InputStream, expected: String): Boolean = sha256(input).equals(expected, ignoreCase = true)
}

object SemVer {
    fun compare(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}

sealed interface ManifestIssue {
    data class InvalidField(val field: String, val reason: String) : ManifestIssue
    data class AppTooOld(val required: String, val current: String) : ManifestIssue
    data class VehicleMismatch(val expected: String, val actual: String) : ManifestIssue
}

object AssetManifestValidator {
    private val CHECKSUM = Regex("^sha256:[0-9a-f]{64}$")
    private val ID = Regex("^[a-z0-9_]+_v[0-9]+$")

    fun validate(manifest: AssetManifest, appVersion: String, expectedVehicleId: String? = null, platform: String = "android"): List<ManifestIssue> {
        val issues = mutableListOf<ManifestIssue>()
        if (!ID.matches(manifest.id)) issues += ManifestIssue.InvalidField("id", "must match <name>_v<version>")
        if (manifest.version < 1) issues += ManifestIssue.InvalidField("version", "must be >= 1")
        if (manifest.schemaVersion != 1) issues += ManifestIssue.InvalidField("schemaVersion", "unsupported ${manifest.schemaVersion}")
        val file = fileFor(manifest, platform)
        if (file.first.isBlank() || file.first.contains("/") || file.first.contains("..")) issues += ManifestIssue.InvalidField("fileName", "unsafe file name")
        if (!CHECKSUM.matches(file.second)) issues += ManifestIssue.InvalidField("checksum", "must be sha256:<64 hex>")
        if (file.third <= 0) issues += ManifestIssue.InvalidField("fileSizeBytes", "must be positive")
        if (SemVer.compare(appVersion, manifest.minimumAppVersion) < 0) issues += ManifestIssue.AppTooOld(manifest.minimumAppVersion, appVersion)
        if (expectedVehicleId != null && manifest.vehicleId != expectedVehicleId) issues += ManifestIssue.VehicleMismatch(expectedVehicleId, manifest.vehicleId)
        return issues
    }

    /** (fileName, checksum, size) for a platform: Android uses the primary GLB, iOS the USDZ variant. */
    fun fileFor(manifest: AssetManifest, platform: String): Triple<String, String, Long> {
        val v = manifest.platformVariants[platform]
        return if (v != null) Triple(v.fileName, v.checksum, v.fileSizeBytes) else Triple(manifest.fileName, manifest.checksum, manifest.fileSizeBytes)
    }
}

/** Download lifecycle for a vehicle asset pack. Pure state machine: (state, event) -> state. */
sealed interface DownloadState {
    data object NotDownloaded : DownloadState
    data class Available(val totalBytes: Long, val version: Int) : DownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : DownloadState {
        val progress: Float get() = if (totalBytes <= 0) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }
    data class Paused(val downloadedBytes: Long, val totalBytes: Long) : DownloadState
    data class Verifying(val totalBytes: Long) : DownloadState
    data class Installed(val version: Int, val sizeBytes: Long) : DownloadState
    data class Failed(val reason: String, val downloadedBytes: Long, val totalBytes: Long) : DownloadState
}

sealed interface DownloadEvent {
    data class ManifestLoaded(val totalBytes: Long, val version: Int) : DownloadEvent
    data object Start : DownloadEvent
    data class Progress(val downloadedBytes: Long) : DownloadEvent
    data object Pause : DownloadEvent
    data object Resume : DownloadEvent
    data object TransferComplete : DownloadEvent
    data class Verified(val version: Int) : DownloadEvent
    data class Error(val reason: String) : DownloadEvent
    data object Remove : DownloadEvent
}

object DownloadStateMachine {
    fun reduce(state: DownloadState, event: DownloadEvent): DownloadState = when (event) {
        is DownloadEvent.ManifestLoaded -> when (state) {
            is DownloadState.Installed -> if (event.version > state.version) DownloadState.Available(event.totalBytes, event.version) else state
            is DownloadState.Downloading, is DownloadState.Verifying, is DownloadState.Paused -> state
            else -> DownloadState.Available(event.totalBytes, event.version)
        }
        DownloadEvent.Start -> when (state) {
            is DownloadState.Available -> DownloadState.Downloading(0, state.totalBytes)
            is DownloadState.Failed -> DownloadState.Downloading(state.downloadedBytes, state.totalBytes) // retry resumes
            else -> state
        }
        is DownloadEvent.Progress -> if (state is DownloadState.Downloading) state.copy(downloadedBytes = event.downloadedBytes.coerceAtMost(state.totalBytes)) else state
        DownloadEvent.Pause -> if (state is DownloadState.Downloading) DownloadState.Paused(state.downloadedBytes, state.totalBytes) else state
        DownloadEvent.Resume -> if (state is DownloadState.Paused) DownloadState.Downloading(state.downloadedBytes, state.totalBytes) else state
        DownloadEvent.TransferComplete -> if (state is DownloadState.Downloading) DownloadState.Verifying(state.totalBytes) else state
        is DownloadEvent.Verified -> if (state is DownloadState.Verifying) DownloadState.Installed(event.version, state.totalBytes) else state
        is DownloadEvent.Error -> when (state) {
            is DownloadState.Downloading -> DownloadState.Failed(event.reason, state.downloadedBytes, state.totalBytes)
            // A failed verification discards the partial file, so a retry starts from zero.
            is DownloadState.Verifying -> DownloadState.Failed(event.reason, 0, state.totalBytes)
            else -> state
        }
        DownloadEvent.Remove -> if (state is DownloadState.Installed) DownloadState.NotDownloaded else state
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
