package com.spilol2.vanish.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spilol2.vanish.engine.InpaintModelId
import com.spilol2.vanish.engine.ModelStore
import com.spilol2.vanish.ui.AppState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // model id -> download progress [0,1]; present only while downloading
    val progress = remember { mutableStateMapOf<InpaintModelId, Float>() }

    fun selectOrDownload(m: InpaintModelId) {
        if (ModelStore.isReady(context, m)) {
            state.inpaintModel = m
            return
        }
        if (progress.containsKey(m)) return // already downloading
        scope.launch {
            progress[m] = 0f
            val ok = ModelStore.download(context, m) { p -> progress[m] = p }
            progress.remove(m)
            if (ok) {
                state.inpaintModel = m
                snackbar.showSnackbar("${m.display} downloaded")
            } else {
                snackbar.showSnackbar("Download failed — check your connection")
            }
        }
    }

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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 8.dp),
        ) {
            GroupLabel("Inpainting model")
            InpaintModelId.entries.forEach { m ->
                ModelRow(
                    title = m.display,
                    subtitle = "${m.tagline} · ${m.sizeLabel}",
                    selected = state.inpaintModel == m,
                    ready = ModelStore.isReady(context, m),
                    progress = progress[m],
                    onClick = { selectOrDownload(m) },
                )
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
private fun ModelRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    ready: Boolean,
    progress: Float?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = progress == null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = { onClick() }, enabled = progress == null)
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            progress != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(8.dp))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            !ready -> TextButton(onClick = onClick) { Text("Download") }
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
