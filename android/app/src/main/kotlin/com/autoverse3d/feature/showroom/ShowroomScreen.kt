package com.autoverse3d.feature.showroom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.autoverse3d.data.AppContent
import com.autoverse3d.data.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ContentViewModel @Inject constructor(repo: ContentRepository) : ViewModel() {
    val content = flow<AppContent?> { emit(repo.content()) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun ShowroomScreen(onOpenVehicle: (String) -> Unit, vm: ContentViewModel = hiltViewModel()) {
    val content by vm.content.collectAsStateWithLifecycle()
    val vehicles = content?.vehicles?.vehicles ?: return
    var filter by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Showroom", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Original fictional vehicles. More arrive in content updates.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                FilterChip(selected = filter == "available", onClick = { filter = "available" }, label = { Text("Available now") })
                FilterChip(selected = filter == "coming_soon", onClick = { filter = "coming_soon" }, label = { Text("Coming soon") })
            }
        }
        items(vehicles.filter { filter == null || it.availability == filter }, key = { it.id }) { v ->
            Card(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).testTag("vehicle_${v.id}")
                    .clickable(enabled = v.isAvailable, role = Role.Button) { onOpenVehicle(v.id) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (v.isAvailable) Icons.Filled.DirectionsCar else Icons.Filled.Lock, contentDescription = null,
                        tint = if (v.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(v.categoryLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(v.description, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                    AssistChip(onClick = { if (v.isAvailable) onOpenVehicle(v.id) }, label = { Text(if (v.isAvailable) "View 3D" else "Soon") })
                }
            }
        }
    }
}
