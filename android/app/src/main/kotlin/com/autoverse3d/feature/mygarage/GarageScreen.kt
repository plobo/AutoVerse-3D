package com.autoverse3d.feature.mygarage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.autoverse3d.core.assets.AssetRepository
import com.autoverse3d.core.assets.PackStatus
import com.autoverse3d.core.designsystem.SectionHeader
import com.autoverse3d.core.designsystem.hexColor
import com.autoverse3d.data.AppContent
import com.autoverse3d.data.ConfigurationRepository
import com.autoverse3d.data.ContentRepository
import com.autoverse3d.domain.ConfigurationRules
import com.autoverse3d.domain.DownloadState
import com.autoverse3d.domain.NameValidation
import com.autoverse3d.domain.formatBytes
import com.autoverse3d.domain.model.VehicleConfiguration
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GarageViewModel @Inject constructor(
    private val configs: ConfigurationRepository,
    private val assets: AssetRepository,
    contentRepo: ContentRepository,
) : ViewModel() {
    val content: StateFlow<AppContent?> = flow<AppContent?> { emit(contentRepo.content()) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val configurations = configs.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun pack(manifestId: String): StateFlow<PackStatus> = assets.status(manifestId)

    fun refreshPack(manifestId: String, vehicleId: String) = assets.refresh(manifestId, vehicleId)
    fun download(manifestId: String, vehicleId: String) = assets.start(manifestId, vehicleId)
    fun pause(manifestId: String) = assets.pause(manifestId)
    fun removePack(manifestId: String, vehicleId: String) = assets.remove(manifestId, vehicleId)

    fun rename(config: VehicleConfiguration, name: String): Boolean {
        if (ConfigurationRules.validateName(name) != NameValidation.Valid) return false
        viewModelScope.launch { configs.save(ConfigurationRules.rename(config, name, Instant.now().toString())) }
        return true
    }

    fun duplicate(config: VehicleConfiguration) = viewModelScope.launch {
        configs.save(ConfigurationRules.duplicate(config, UUID.randomUUID().toString(), configurations.value.map { it.name }, Instant.now().toString()))
    }

    fun delete(config: VehicleConfiguration) = viewModelScope.launch { configs.delete(config.id) }
}

@Composable
fun GarageScreen(onOpenConfiguration: (VehicleConfiguration) -> Unit, onCreate: () -> Unit, vm: GarageViewModel = hiltViewModel()) {
    val content by vm.content.collectAsStateWithLifecycle()
    val configs by vm.configurations.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<VehicleConfiguration?>(null) }
    var deleting by remember { mutableStateOf<VehicleConfiguration?>(null) }
    val c = content ?: return

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("My Garage", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { SectionHeader("Saved configurations") }
        if (configs.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No saved designs yet.")
                    Button(onClick = onCreate) { Text("Open the customization studio") }
                }
            }
        }
        items(configs, key = { it.id }) { cfg ->
            ConfigurationCard(cfg, c, onOpen = { onOpenConfiguration(cfg) }, onRename = { renaming = cfg }, onDuplicate = { vm.duplicate(cfg) }, onDelete = { deleting = cfg })
        }
        item { SectionHeader("Downloaded vehicles") }
        items(c.vehicles.vehicles.filter { it.isAvailable }) { v ->
            PackCard(v.name, v.assetManifestId!!, v.id, vm)
        }
        item {
            Text("Saved designs and downloaded vehicles are stored only on this device and work offline.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    renaming?.let { cfg ->
        var name by remember(cfg.id) { mutableStateOf(cfg.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { if (vm.rename(cfg, name)) renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { cfg ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${cfg.name}\"?") },
            text = { Text("This removes the saved design from this device.") },
            confirmButton = { TextButton(onClick = { vm.delete(cfg); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConfigurationCard(cfg: VehicleConfiguration, content: AppContent, onOpen: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    val o = content.customization
    val paint = o.paints.firstOrNull { it.id == cfg.paintId }
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("config_${cfg.name}")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(hexColor(paint?.hex ?: "#888888")))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(cfg.name, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(paint?.name, o.wheels.firstOrNull { it.id == cfg.wheelId }?.name, o.trims.firstOrNull { it.id == cfg.trimId }?.name,
                    o.interiors.firstOrNull { it.id == cfg.interiorId }?.name?.let { "$it interior" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More actions for ${cfg.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun PackCard(name: String, manifestId: String, vehicleId: String, vm: GarageViewModel) {
    val status by vm.pack(manifestId).collectAsStateWithLifecycle()
    LaunchedEffect(manifestId) { vm.refreshPack(manifestId, vehicleId) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$name asset pack", fontWeight = FontWeight.SemiBold)
            Text("A built-in copy is always available offline. Downloading fetches the latest verified pack.", style = MaterialTheme.typography.bodySmall)
            when (val s = status.state) {
                DownloadState.NotDownloaded -> Text("Checking for the latest pack…", style = MaterialTheme.typography.bodySmall)
                is DownloadState.Available -> Button(onClick = { vm.download(manifestId, vehicleId) }) { Text("Download v${s.version} (${formatBytes(s.totalBytes)})") }
                is DownloadState.Downloading -> {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatBytes(s.downloadedBytes)} of ${formatBytes(s.totalBytes)}", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { vm.pause(manifestId) }) { Text("Pause") }
                    }
                }
                is DownloadState.Paused -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Paused at ${formatBytes(s.downloadedBytes)}", modifier = Modifier.weight(1f))
                    Button(onClick = { vm.download(manifestId, vehicleId) }) { Text("Resume") }
                }
                is DownloadState.Verifying -> Text("Verifying integrity…")
                is DownloadState.Installed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Installed v${s.version} · ${formatBytes(s.sizeBytes)} · verified", modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.removePack(manifestId, vehicleId) }) { Text("Remove") }
                }
                is DownloadState.Failed -> Column {
                    Text(s.reason, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.download(manifestId, vehicleId) }) { Text("Retry") }
                }
            }
            status.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
