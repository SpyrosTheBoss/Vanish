package com.spilol2.vanish.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * A tactile press: the element scales down slightly while held and springs
 * back on release. Used on custom-drawn controls (tool buttons, the remove
 * FAB, icon buttons) — Material's own Button/FilledTonalButton already have
 * their own state-layer press feedback, so this isn't applied there.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    scaleDown: Float = 0.9f,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "press-scale",
    )
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}
