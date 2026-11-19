package com.autoverse3d.feature.vehicleviewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoverse3d.core.designsystem.ColorSwatch
import com.autoverse3d.core.designsystem.Disclaimer
import com.autoverse3d.core.designsystem.InfoCard
import com.autoverse3d.core.designsystem.SectionHeader

private val partLabels = linkedMapOf(
    "door_front_left_open" to "Front-left door", "door_front_right_open" to "Front-right door",
    "door_rear_left_open" to "Rear-left door", "door_rear_right_open" to "Rear-right door",
    "hood_open" to "Hood", "trunk_open" to "Tailgate",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExteriorPanel(state: ViewerUiState, vm: VehicleViewerViewModel) {
    val content = state.content ?: return
    val vehicle = state.vehicle ?: return
    val sel = state.selection ?: return

    state.selectedComponent?.let { c ->
        InfoCard(c.name, "${c.description} ${c.purpose}".trim()) {
            Text(c.detail, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { vm.selectComponent(null) }) { Text("Close") }
        }
    }

    Column {
        Text(vehicle.categoryLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(vehicle.description, style = MaterialTheme.typography.bodyMedium)
    }

    SectionHeader("Paint")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        content.customization.paints.forEach { p -> ColorSwatch(p.name, p.hex, sel.paintId == p.id) { vm.setPaint(p.id) } }
    }
    TextButton(onClick = { vm.setPaint(content.customization.defaults.paintId) }) { Text("Reset paint") }

    SectionHeader("Lights and moving parts")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = state.lightsOn, onClick = vm::toggleLights, label = { Text(if (state.lightsOn) "Lights on" else "Lights off") },
            leadingIcon = { Icon(if (state.lightsOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb, contentDescription = null) },
        )
        partLabels.forEach { (id, label) ->
            val open = id in state.openAnimations
            FilterChip(selected = open, onClick = { vm.togglePart(id) }, label = { Text(if (open) "Close ${label.lowercase()}" else "Open ${label.lowercase()}") })
        }
    }
    if (state.openAnimations.isNotEmpty()) OutlinedButton(onClick = vm::closeAllParts) { Text("Close everything") }

    SectionHeader("Parts (tap the model or choose here)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.componentsFor("exterior").forEach { c ->
            FilterChip(selected = state.selectedComponentId == c.id, onClick = { vm.selectComponent(c.id) }, label = { Text(c.name) })
        }
    }

    SectionHeader("Specifications")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        vehicle.specs.forEach { s ->
            Row(Modifier.fillMaxWidth()) {
                Text(s.label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.value, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Disclaimer(vehicle.specsDisclaimer)
    Text("", modifier = Modifier.padding(bottom = 24.dp))
}
