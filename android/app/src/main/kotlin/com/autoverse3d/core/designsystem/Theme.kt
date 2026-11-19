package com.autoverse3d.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoverse3d.domain.ColorUtil

/** Values mirror shared/design/tokens.json ("AutoVerse Studio"). */
object Tokens {
    val primaryLight = Color(0xFF0B7A75)
    val primaryDark = Color(0xFF4FD1C5)
    val accentLight = Color(0xFFF28C28)
    val accentDark = Color(0xFFFFB466)
    const val studioLight = "#C9D0D7"
    const val studioDark = "#1A1F27"
}

private val Light = lightColorScheme(
    primary = Tokens.primaryLight, onPrimary = Color.White, secondary = Tokens.accentLight, onSecondary = Color.White,
    background = Color(0xFFF4F6F8), onBackground = Color(0xFF14181D), surface = Color.White, onSurface = Color(0xFF14181D),
    surfaceVariant = Color(0xFFE6EAEE), onSurfaceVariant = Color(0xFF56606B), outline = Color(0xFFC3CAD1), error = Color(0xFFB3261E),
    primaryContainer = Color(0xFFCDEDEA), onPrimaryContainer = Color(0xFF00201E),
    secondaryContainer = Color(0xFFCDEDEA), onSecondaryContainer = Color(0xFF00201E), surfaceContainer = Color(0xFFEDF1F4),
)
private val Dark = darkColorScheme(
    primary = Tokens.primaryDark, onPrimary = Color(0xFF00201E), secondary = Tokens.accentDark, onSecondary = Color(0xFF2B1600),
    background = Color(0xFF0E1116), onBackground = Color(0xFFEEF1F4), surface = Color(0xFF171B22), onSurface = Color(0xFFEEF1F4),
    surfaceVariant = Color(0xFF232934), onSurfaceVariant = Color(0xFF9AA4AF), outline = Color(0xFF3A424E), error = Color(0xFFF2B8B5),
    primaryContainer = Color(0xFF0E3B38), onPrimaryContainer = Color(0xFFCDEDEA),
    secondaryContainer = Color(0xFF0E3B38), onSecondaryContainer = Color(0xFFCDEDEA), surfaceContainer = Color(0xFF1B2028),
)

@Composable
fun AutoVerseTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}

@Composable
fun studioBackgroundLinear(): FloatArray = ColorUtil.hexToLinear(if (isSystemInDarkTheme()) Tokens.studioDark else Tokens.studioLight)

fun hexColor(hex: String): Color {
    val c = ColorUtil.hexToSrgb(hex)
    return Color(c[0], c[1], c[2])
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(vertical = 8.dp))
}

@Composable
fun Disclaimer(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoCard(title: String, body: String, modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            footer?.invoke()
        }
    }
}

/** Colour swatch that never relies on colour alone: it is always paired with a text label and selection ring. */
@Composable
fun ColorSwatch(name: String, hex: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name; this.selected = selected }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(hexColor(hex)),
        )
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
