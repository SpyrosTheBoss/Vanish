package com.spilol2.vanish.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spilol2.vanish.ui.AppState
import com.spilol2.vanish.ui.Tool

private val OnDark = Color(0xFFDCE6E3)

@Composable
fun androidx.compose.foundation.layout.BoxScope.TopBar(state: AppState, onBack: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x8C080614), Color(0x00080614))))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(54.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScrimIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { onBack() }
            Text(
                "Edit",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            ScrimIcon(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = state.canUndo) { state.undo() }
            ScrimIcon(Icons.AutoMirrored.Rounded.Redo, "Redo", enabled = state.canRedo) { state.redo() }
        }
    }
}

@Composable
fun androidx.compose.foundation.layout.BoxScope.BottomControls(
    state: AppState,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x00080614), Color(0x9E080614))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // auto-detect suggestion chips (stub for now)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestChip(Icons.Rounded.AutoFixHigh, "Auto-detect watermark") {
                // placeholder — real Florence-2/segmentation suggestion comes later
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // tool cluster
            Row(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xB8141B1A))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(28.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ToolButton(Icons.Rounded.TouchApp, "Tap", state.tool == Tool.Tap) { state.tool = Tool.Tap }
                ToolButton(Icons.Rounded.Gesture, "Lasso", state.tool == Tool.Lasso) { state.tool = Tool.Lasso }
                ToolButton(Icons.Rounded.Brush, "Brush", state.tool == Tool.Brush) { state.tool = Tool.Brush }
            }
            // remove FAB
            Row(
                Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onRemove() }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    Icons.Rounded.AutoFixHigh, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Remove",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun ScrimIcon(
    icon: ImageVector,
    desc: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

@Composable
private fun ToolButton(icon: ImageVector, desc: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = desc,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else OnDark,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun SuggestChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x9E101615))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = OnDark, modifier = Modifier.size(16.dp))
        Text(label, color = OnDark, style = MaterialTheme.typography.labelLarge)
    }
}
