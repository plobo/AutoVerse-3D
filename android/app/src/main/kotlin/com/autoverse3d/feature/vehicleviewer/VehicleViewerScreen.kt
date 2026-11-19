package com.autoverse3d.feature.vehicleviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoverse3d.core.designsystem.studioBackgroundLinear
import com.autoverse3d.core.renderer.RenderStatus
import com.autoverse3d.core.renderer.VehicleViewport
import com.autoverse3d.domain.model.ViewerMode
import com.autoverse3d.feature.customization.CustomizationPanel
import com.autoverse3d.feature.interiorexplorer.InteriorPanel
import com.autoverse3d.feature.mechanicalexplorer.MechanicalPanel
import com.autoverse3d.feature.safetylab.SafetyPanel

private val tabModes = listOf(ViewerMode.Exterior, ViewerMode.Interior, ViewerMode.Mechanical, ViewerMode.Safety, ViewerMode.Customize)
private fun ViewerMode.label() = when (this) {
    ViewerMode.Exterior -> "Exterior"; ViewerMode.Interior -> "Interior"; ViewerMode.Mechanical -> "Mechanical"
    ViewerMode.Safety -> "Safety"; ViewerMode.Customize -> "Customize"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleViewerScreen(onBack: () -> Unit, onAskAssistant: (vehicleId: String, componentId: String?) -> Unit, vm: VehicleViewerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                title = { Text(state.vehicle?.name ?: "Vehicle") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { onAskAssistant(state.vehicle?.id ?: "urbanx_suv", state.selectedComponentId) }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask the AI assistant about this vehicle")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = vm::load, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
                }
                else -> ViewerContent(state, vm)
            }
        }
    }
}

@Composable
private fun ViewerContent(state: ViewerUiState, vm: VehicleViewerViewModel) {
    val content = state.content ?: return
    val model = state.model ?: return
    val bg = studioBackgroundLinear()
    ScrollableTabRow(selectedTabIndex = tabModes.indexOf(state.mode).coerceAtLeast(0), edgePadding = 8.dp) {
        tabModes.forEach { mode ->
            Tab(selected = state.mode == mode, onClick = { vm.setMode(mode) }, text = { Text(mode.label(), maxLines = 1) }, modifier = Modifier.testTag("mode_${mode.name}"))
        }
    }
    Box(Modifier.fillMaxWidth().height(330.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        state.cameraCommand?.let { cmd ->
            VehicleViewport(
                vehicle = model.source,
                studio = model.studio,
                catalog = content.components,
                sceneState = vm.sceneState(state),
                cameraCommand = cmd,
                backgroundLinear = bg,
                accessibilityDescription = "3D view of ${state.vehicle?.name}. Drag to rotate, pinch to zoom, two fingers to pan. All parts are also listed below.",
                onStatus = vm::onRenderStatus,
                onTapNode = vm::onTapNode,
                onCameraChanged = vm::onCameraChanged,
                modifier = Modifier.fillMaxSize().testTag("viewport"),
            )
        }
        when (val st = state.renderStatus) {
            RenderStatus.Loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Loading ${state.vehicle?.name}…", modifier = Modifier.padding(top = 8.dp))
            }
            is RenderStatus.Failed -> Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("The 3D view couldn't be shown.", style = MaterialTheme.typography.titleSmall)
                Text("You can still explore every part using the lists below.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = vm::load, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
            }
            RenderStatus.Ready -> Unit
        }
        FilledTonalIconButton(
            onClick = vm::resetCamera,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).semantics { contentDescription = "Reset camera" },
        ) { Icon(Icons.Filled.CenterFocusStrong, contentDescription = null) }
        Text(
            model.originLabel,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
    if (state.mode != ViewerMode.Interior) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content.cameras.orbit.presets.forEach { p ->
                FilterChip(selected = state.orbitPresetId == p.id, onClick = { vm.selectOrbitPreset(p.id) }, label = { Text(p.name) })
            }
        }
    }
    androidx.compose.runtime.key(state.mode) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.mode) {
            ViewerMode.Exterior -> ExteriorPanel(state, vm)
            ViewerMode.Interior -> InteriorPanel(state, vm)
            ViewerMode.Mechanical -> MechanicalPanel(state, vm)
            ViewerMode.Safety -> SafetyPanel(state, vm)
            ViewerMode.Customize -> CustomizationPanel(state, vm)
        }
    } }
}
