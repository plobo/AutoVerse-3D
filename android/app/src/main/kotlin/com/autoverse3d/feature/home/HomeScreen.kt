package com.autoverse3d.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.autoverse3d.R
import com.autoverse3d.data.AppContent
import com.autoverse3d.data.ContentRepository
import com.autoverse3d.data.ProgressRepository
import com.autoverse3d.data.SettingsRepository
import com.autoverse3d.domain.LearningProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class HomeState(val content: AppContent? = null, val lastExplored: Pair<String, String>? = null, val learningPercent: Int = 0)

@HiltViewModel
class HomeViewModel @Inject constructor(content: ContentRepository, settings: SettingsRepository, progress: ProgressRepository) : ViewModel() {
    val state = combine(flow { emit(content.content()) }, settings.lastExplored, progress.observe()) { c, last, p ->
        HomeState(c, last, LearningProgress.overallPercent(c.learning.modules, p))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

@Composable
fun HomeScreen(
    onOpenVehicle: (vehicleId: String, mode: String) -> Unit,
    onOpenLearn: () -> Unit,
    onOpenEvLab: () -> Unit,
    onOpenAssistant: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val content = state.content ?: return
    val featured = content.vehicles.vehicles.first { it.isAvailable }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(44.dp))
            Column {
                Text("AutoVerse 3D", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Your virtual automotive discovery studio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(150.dp)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer))),
            ) {
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text("FEATURED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                    Text(featured.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Text(featured.categoryLabel, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(featured.description, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { onOpenVehicle(featured.id, "exterior") }) { Text("Explore in 3D") }
            }
        }
        state.lastExplored?.let { (vehicleId, mode) ->
            val v = content.vehicles.vehicles.firstOrNull { it.id == vehicleId }
            if (v != null) {
                ShortcutCard(Icons.AutoMirrored.Filled.ArrowForward, "Continue exploring", "${v.name} · ${mode.replaceFirstChar { it.uppercase() }} view") { onOpenVehicle(vehicleId, mode) }
            }
        }
        Text("Learning shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onOpenLearn)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("Learning progress", fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(progress = { state.learningPercent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${state.learningPercent}% of ${content.learning.modules.size} modules complete", style = MaterialTheme.typography.bodySmall)
            }
        }
        ShortcutCard(Icons.Filled.ElectricBolt, "EV Lab", "See how energy flows from battery to wheels", onOpenEvLab)
        ShortcutCard(Icons.Filled.AutoAwesome, "Ask the assistant", "Questions about UrbanX or how cars work", onOpenAssistant)
        Text("All vehicles in AutoVerse 3D are original fictional designs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShortcutCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}
