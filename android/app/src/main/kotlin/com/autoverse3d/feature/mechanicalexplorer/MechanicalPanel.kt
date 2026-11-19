package com.autoverse3d.feature.mechanicalexplorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.autoverse3d.core.designsystem.Disclaimer
import com.autoverse3d.core.designsystem.InfoCard
import com.autoverse3d.core.designsystem.SectionHeader
import com.autoverse3d.feature.vehicleviewer.VehicleViewerViewModel
import com.autoverse3d.feature.vehicleviewer.ViewerUiState

/** Mechanical explorer: simplified assembly, exploded views, component selection and reset. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MechanicalPanel(state: ViewerUiState, vm: VehicleViewerViewModel) {
    val content = state.content ?: return
    Disclaimer(content.components.educationalDisclaimer)
    SectionHeader("Exploded view")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content.components.explodedViews.forEach { v ->
            if (state.explodedViewId == v.id) OutlinedButton(onClick = { vm.setExploded(null) }) { Text("Assemble ${v.name.lowercase()}") }
            else Button(onClick = { vm.setExploded(v.id) }) { Text("Explode: ${v.name}") }
        }
        OutlinedButton(onClick = vm::resetAssembly) { Text("Reset assembly") }
    }
    state.selectedComponent?.let { c ->
        InfoCard(c.name, c.description) {
            Text("Purpose: ${c.purpose}", style = MaterialTheme.typography.bodyMedium)
            Text(c.detail, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { vm.selectComponent(null) }) { Text("Close") }
        }
    }
    SectionHeader("Components")
    state.componentsFor("mechanical").forEach { c ->
        ListItem(
            headlineContent = { Text(c.name) },
            supportingContent = { Text(c.purpose, maxLines = 2) },
            overlineContent = { Text(c.category.replaceFirstChar { it.uppercase() }) },
            modifier = Modifier.clickable(role = Role.Button) { vm.selectComponent(c.id) },
        )
    }
}
