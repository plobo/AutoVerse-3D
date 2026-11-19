package com.autoverse3d.feature.evexplorer

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoverse3d.core.designsystem.Disclaimer
import com.autoverse3d.core.designsystem.InfoCard
import com.autoverse3d.core.designsystem.SectionHeader
import com.autoverse3d.feature.showroom.ContentViewModel

/** Conceptual EV powertrain with an animated energy-flow diagram and full text alternatives. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvLabScreen(onBack: () -> Unit, vm: ContentViewModel = hiltViewModel()) {
    val content by vm.content.collectAsStateWithLifecycle()
    val ev = content?.evLab ?: return
    var modeId by remember { mutableStateOf(ev.modes.first().id) }
    var selectedNode by remember { mutableStateOf<String?>(null) }
    val mode = ev.modes.first { it.id == modeId }
    val context = LocalContext.current
    val reducedMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val transition = rememberInfiniteTransition(label = "energy")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Scaffold(contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), topBar = {
        TopAppBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), title = { Text("EV Lab") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Disclaimer(ev.disclaimer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ev.modes.forEach { m -> FilterChip(selected = m.id == modeId, onClick = { modeId = m.id }, label = { Text(m.title) }) }
            }
            Canvas(
                Modifier.fillMaxWidth().height(240.dp).semantics { contentDescription = "Energy flow diagram. ${mode.textAlternative}" },
            ) {
                val nodeSize = Size(size.width * 0.2f, 44.dp.toPx())
                fun center(id: String): Offset {
                    val n = ev.nodes.first { it.id == id }
                    return Offset(n.x * size.width, n.y * size.height)
                }
                mode.flows.forEach { (from, to) ->
                    val a = center(from); val b = center(to)
                    drawLine(primary.copy(alpha = 0.35f), a, b, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
                    val dots = if (reducedMotion) listOf(0.5f) else listOf(0f, 0.33f, 0.66f).map { (it + phase) % 1f }
                    dots.forEach { t -> drawCircle(accent, 6.dp.toPx(), Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)) }
                }
                ev.nodes.forEach { n ->
                    val c = Offset(n.x * size.width, n.y * size.height)
                    val active = mode.flows.any { n.id in it }
                    val topLeft = Offset(c.x - nodeSize.width / 2, c.y - nodeSize.height / 2)
                    drawRoundRect(surface, topLeft, nodeSize, CornerRadius(12.dp.toPx()))
                    drawRoundRect(if (active) primary else onSurface.copy(alpha = 0.3f), topLeft, nodeSize, CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
                    drawLabel(measurer, n.title, c, nodeSize.width, onSurface)
                }
            }
            SectionHeader("What's happening")
            Text(mode.textAlternative, style = MaterialTheme.typography.bodyMedium)
            selectedNode?.let { id -> ev.nodes.firstOrNull { it.id == id }?.let { InfoCard(it.title, it.summary) } }
            SectionHeader("Components")
            ev.nodes.forEach { n ->
                ListItem(headlineContent = { Text(n.title) }, supportingContent = { Text(n.summary) }, modifier = Modifier.clickable { selectedNode = n.id })
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(measurer: TextMeasurer, text: String, center: Offset, maxWidth: Float, color: androidx.compose.ui.graphics.Color) {
    val layout = measurer.measure(text, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color),
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)))
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}
