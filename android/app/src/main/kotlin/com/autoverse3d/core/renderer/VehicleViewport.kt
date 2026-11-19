package com.autoverse3d.core.renderer

import android.view.SurfaceView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.autoverse3d.domain.model.ComponentCatalog

/** A camera request from the ViewModel; [nonce] changes when the same rig must be re-applied (reset). */
data class CameraCommand(val rig: CameraRig, val animate: Boolean, val nonce: Int)

/**
 * Hosts the native Filament renderer in Compose. Gestures: one finger rotates, two fingers pan and pinch-zoom,
 * tap selects. Non-3D alternatives for every action live in the surrounding screen (lists and buttons).
 */
@Composable
fun VehicleViewport(
    vehicle: ModelSource,
    studio: ModelSource?,
    catalog: ComponentCatalog,
    sceneState: SceneState,
    cameraCommand: CameraCommand,
    backgroundLinear: FloatArray,
    accessibilityDescription: String,
    onStatus: (RenderStatus) -> Unit,
    onTapNode: (String?) -> Unit,
    onCameraChanged: (CameraRig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var renderer by remember { mutableStateOf<FilamentVehicleRenderer?>(null) }
    val latestTap by rememberUpdatedState(onTapNode)
    val latestCamera by rememberUpdatedState(onCameraChanged)

    Box(modifier.semantics { contentDescription = accessibilityDescription }) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    renderer = FilamentVehicleRenderer(ctx, sv, catalog, backgroundLinear).also { it.start() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos -> renderer?.pick(pos.x, pos.y) { latestTap(it) } }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var last: Offset? = null
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val r = renderer
                            if (r != null && pressed.isNotEmpty() && event.changes.any { it.positionChanged() }) {
                                if (pressed.size == 1) {
                                    val c = pressed.first()
                                    val d = c.position - c.previousPosition
                                    r.onRotate(d.x, d.y)
                                } else {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f) r.onZoom(zoom)
                                    r.onPan(pan.x, pan.y)
                                }
                                last = event.calculateCentroid()
                            }
                        } while (event.changes.any { it.pressed })
                        if (last != null) renderer?.currentCamera()?.let { latestCamera(it) }
                    }
                },
        )
    }

    LaunchedEffect(renderer, vehicle, studio) {
        val r = renderer ?: return@LaunchedEffect
        onStatus(RenderStatus.Loading)
        try {
            r.loadVehicle(vehicle, studio)
            r.setCamera(cameraCommand.rig, animate = false)
            r.applyScene(sceneState)
            onStatus(RenderStatus.Ready)
        } catch (t: Throwable) {
            onStatus(RenderStatus.Failed(t.message ?: "Unknown rendering error"))
        }
    }
    LaunchedEffect(renderer, sceneState) { renderer?.applyScene(sceneState) }
    LaunchedEffect(renderer, cameraCommand) { renderer?.setCamera(cameraCommand.rig, cameraCommand.animate) }

    DisposableEffect(lifecycle, renderer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> renderer?.start()
                Lifecycle.Event.ON_PAUSE -> renderer?.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose {
            renderer?.destroy()
            renderer = null
        }
    }
}
