package com.autoverse3d.feature.interiorexplorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.autoverse3d.core.designsystem.InfoCard
import com.autoverse3d.core.designsystem.SectionHeader
import com.autoverse3d.feature.vehicleviewer.VehicleViewerViewModel
import com.autoverse3d.feature.vehicleviewer.ViewerUiState

/** Interior explorer: cabin viewpoints, 3D hotspots and an accessible list of the same hotspots. */
@Composable
fun InteriorPanel(state: ViewerUiState, vm: VehicleViewerViewModel) {
    val content = state.content ?: return
    SectionHeader("Viewpoint")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content.cameras.interior.presets.forEach { p ->
            FilterChip(selected = state.interiorPresetId == p.id, onClick = { vm.selectInteriorPreset(p.id) }, label = { Text(p.name) })
        }
    }
    Text("Drag to look around. Tap an orange marker, or pick a feature from the list.", style = MaterialTheme.typography.bodySmall)
    state.selectedHotspot?.let { h ->
        InfoCard(h.title, h.summary) {
            Text(h.detail, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { vm.selectHotspot(null) }) { Text("Close") }
        }
    }
    SectionHeader("Cabin features")
    state.hotspotsFor("interior").forEach { h ->
        ListItem(
            headlineContent = { Text(h.title) },
            supportingContent = { Text(h.summary, maxLines = 2) },
            modifier = Modifier.clickable(role = Role.Button) { vm.selectHotspot(h.id) },
        )
    }
}
