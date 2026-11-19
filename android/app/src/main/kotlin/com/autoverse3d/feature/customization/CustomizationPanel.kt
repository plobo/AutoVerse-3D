package com.autoverse3d.feature.customization

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.autoverse3d.core.designsystem.ColorSwatch
import com.autoverse3d.core.designsystem.SectionHeader
import com.autoverse3d.feature.vehicleviewer.VehicleViewerViewModel
import com.autoverse3d.feature.vehicleviewer.ViewerUiState

/** Customization studio: paint, wheels, trim and interior; save, save-as-new, reset and compare. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizationPanel(state: ViewerUiState, vm: VehicleViewerViewModel) {
    val options = state.content?.customization ?: return
    val sel = state.selection ?: return
    var dialog by remember { mutableStateOf<Boolean?>(null) } // null = closed, true = save as new, false = update
    var name by remember(state.loadedConfiguration?.id, dialog) { (state.loadedConfiguration?.name ?: "My ${state.vehicle?.name}").let { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(it, androidx.compose.ui.text.TextRange(0, it.length))) } }

    state.loadedConfiguration?.let { saved ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Editing \"${saved.name}\"", fontWeight = FontWeight.SemiBold)
                val diffs = state.differences
                if (diffs.isEmpty()) Text("No changes from the saved configuration.", style = MaterialTheme.typography.bodySmall)
                else diffs.forEach { d -> Text("${d.field}: ${d.saved} → ${d.current}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    SectionHeader("Exterior paint")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.paints.forEach { p -> ColorSwatch(p.name, p.hex, sel.paintId == p.id) { vm.setPaint(p.id) } }
    }
    SectionHeader("Wheels")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.wheels.forEach { w -> FilterChip(selected = sel.wheelId == w.id, onClick = { vm.setWheel(w.id) }, label = { Text(w.name) }) }
    }
    Text(options.wheels.first { it.id == sel.wheelId }.description, style = MaterialTheme.typography.bodySmall)
    SectionHeader("Trim accents")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.trims.forEach { t -> ColorSwatch(t.name, t.hex, sel.trimId == t.id) { vm.setTrim(t.id) } }
    }
    SectionHeader("Interior")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.interiors.forEach { i -> ColorSwatch(i.name, i.hex, sel.interiorId == i.id) { vm.setInterior(i.id) } }
    }
    Text("Tip: switch to the Interior tab to see the cabin colour.", style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Button(onClick = { dialog = state.loadedConfiguration == null }, modifier = Modifier.testTag("save_config")) {
            Text(if (state.loadedConfiguration == null) "Save to My Garage" else "Save changes")
        }
        if (state.loadedConfiguration != null) OutlinedButton(onClick = { dialog = true }) { Text("Save as new") }
        TextButton(onClick = vm::resetDesign) { Text("Reset") }
    }

    dialog?.let { asNew ->
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (asNew) "Name this configuration" else "Save changes") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.testTag("config_name").focusRequester(focus))
                LaunchedEffect(Unit) { focus.requestFocus() } },
            confirmButton = { TextButton(onClick = { if (vm.saveConfiguration(name.text, asNew)) dialog = null }, modifier = Modifier.testTag("confirm_save")) { Text("Save") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}
