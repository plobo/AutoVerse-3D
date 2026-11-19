package com.autoverse3d.feature.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ExploreItem(val title: String, val subtitle: String, val icon: ImageVector, val mode: String?)

private val items = listOf(
    ExploreItem("Interior explorer", "Step inside UrbanX: dashboard, seats, console and storage.", Icons.Filled.EventSeat, "interior"),
    ExploreItem("Mechanical explorer", "Engine, transmission, brakes, suspension — with an exploded view.", Icons.Filled.Build, "mechanical"),
    ExploreItem("EV Lab", "Battery, inverter, motor, charging and regenerative braking.", Icons.Filled.ElectricBolt, null),
    ExploreItem("Safety lab", "Airbags, seat belts, crumple zones and driver-assistance sensors.", Icons.Filled.HealthAndSafety, "safety"),
    ExploreItem("Customization studio", "Paint, wheels, trim and interior colours you can save.", Icons.Filled.Palette, "customize"),
)

@Composable
fun ExploreScreen(onOpenMode: (vehicleId: String, mode: String) -> Unit, onOpenEvLab: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Explore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Learning modules built around the UrbanX reference vehicle.", style = MaterialTheme.typography.bodySmall)
        items.forEach { item ->
            Card(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).testTag("explore_${item.mode ?: "ev"}")
                    .clickable(role = Role.Button) { if (item.mode == null) onOpenEvLab() else onOpenMode("urbanx_suv", item.mode) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
