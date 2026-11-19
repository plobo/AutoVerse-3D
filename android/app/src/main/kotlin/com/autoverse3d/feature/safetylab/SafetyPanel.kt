package com.autoverse3d.feature.safetylab

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.autoverse3d.core.designsystem.Disclaimer
import com.autoverse3d.core.designsystem.InfoCard
import com.autoverse3d.core.designsystem.SectionHeader
import com.autoverse3d.feature.vehicleviewer.VehicleViewerViewModel
import com.autoverse3d.feature.vehicleviewer.ViewerUiState

/** Safety lab: conceptual safety-system locations as 3D hotspots plus a text list. */
@Composable
fun SafetyPanel(state: ViewerUiState, vm: VehicleViewerViewModel) {
    val content = state.content ?: return
    Disclaimer(content.hotspots.safetyDisclaimer)
    state.selectedHotspot?.let { h ->
        InfoCard(h.title, h.summary) {
            Text(h.detail, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { vm.selectHotspot(null) }) { Text("Close") }
        }
    }
    SectionHeader("Safety systems")
    state.hotspotsFor("safety").forEach { h ->
        ListItem(
            headlineContent = { Text(h.title) },
            supportingContent = { Text(h.summary, maxLines = 2) },
            modifier = Modifier.clickable(role = Role.Button) { vm.selectHotspot(h.id) },
        )
    }
}
