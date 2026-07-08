package com.example.vanish.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vanish.ui.AppState
import com.example.vanish.ui.Quality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 8.dp),
        ) {
            GroupLabel("Processing")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Quality.entries.forEachIndexed { i, q ->
                    SegmentedButton(
                        selected = state.quality == q,
                        onClick = { state.quality = q },
                        shape = SegmentedButtonDefaults.itemShape(i, Quality.entries.size),
                    ) { Text(q.name) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Inpainting model", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "MI-GAN · 27 MB · bundled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Divider()
            GroupLabel("Appearance")
            SwitchRow(
                "Dynamic color", "Match your wallpaper",
                state.dynamicColor,
            ) { state.dynamicColor = it }

            Divider()
            GroupLabel("Behavior")
            SwitchRow(
                "Haptics on remove", "Small tick when an object vanishes",
                state.hapticsOnRemove,
            ) { state.hapticsOnRemove = it }
            SwitchRow(
                "Keep original photo", "Edits are always saved as a copy",
                state.keepOriginal,
            ) { state.keepOriginal = it }

            Divider()
            GroupLabel("About")
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Column {
                    Text("Vanish v0.1", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Built on WatermarkRemover-AI-Revamped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun Divider() = HorizontalDivider(
    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
)

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
