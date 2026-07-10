package com.spilol2.vanish.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spilol2.vanish.ui.AppState
import com.spilol2.vanish.util.Haptics
import com.spilol2.vanish.util.saveToGallery
import com.spilol2.vanish.util.shareImage
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val MAX_OVERSCROLL = 1.1f // how far past the limit a pinch can stretch, in scale units

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    state: AppState,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val before = state.source ?: return
    val after = state.result ?: return
    val beforeImg = remember(before) { before.asImageBitmap() }
    val afterImg = remember(after) { after.asImageBitmap() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = remember { Haptics(context) }

    var cut by remember { mutableFloatStateOf(0.5f) }
    var lastSide by remember { mutableFloatStateOf(0f) }

    // Reveal animation: the before/after card scales + fades in on arrival —
    // the "ta-da" beat right after the vanish haptic on the previous screen.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(320)) }

    // ---- zoom/pan state ----
    // rawZoom accumulates freely from pinch deltas, unclamped, so the rubber
    // band has something to measure "how far past the limit" against; zoom is
    // what's actually drawn (clamped + damped overscroll).
    var rawZoom by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var lastOverscrollLevel by remember { mutableFloatStateOf(-1f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) { saveToGallery(context, after) }
                            if (uri != null) shareImage(context, uri)
                            else { if (state.hapticsOnRemove) haptics.warning(); snackbar.showSnackbar("Couldn't prepare image") }
                        }
                    }) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- before / after comparison ----
            fun setCut(v: Float) {
                val new = v.coerceIn(0.04f, 0.96f)
                val newSide = sign(new - 0.5f)
                if (newSide != 0f && newSide != lastSide && state.hapticsOnRemove) {
                    haptics.snap() // crossed the 50% detent
                }
                lastSide = newSide
                cut = new
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f) // fill the leftover space instead of a fixed height
                    .scale(0.97f + 0.03f * reveal.value)
                    .alpha(reveal.value)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF161D1B))
                    .pointerInput(Unit) {
                        val w = size.width.toFloat()
                        detectTapGestures { if (zoom <= 1.001f) setCut(it.x / w) }
                    }
                    .pointerInput(Unit) {
                        while (true) {
                            var overW = 0f
                            var overH = 0f
                            detectTransformGestures(panZoomLock = false) { _, pan, gestureZoom, _ ->
                                overW = size.width.toFloat()
                                overH = size.height.toFloat()
                                if (zoom > 1.001f || gestureZoom != 1f) {
                                    // pinch/pan mode
                                    rawZoom = (rawZoom * gestureZoom).coerceIn(MIN_ZOOM - MAX_OVERSCROLL, MAX_ZOOM + MAX_OVERSCROLL)
                                    val clamped = rawZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    val overBy = rawZoom - clamped
                                    val damped = if (overBy == 0f) 0f else
                                        sign(overBy) * MAX_OVERSCROLL * (1f - exp(-abs(overBy) / MAX_OVERSCROLL))
                                    zoom = clamped + damped

                                    val maxOffX = (overW * (zoom - 1f) / 2f).coerceAtLeast(0f)
                                    val maxOffY = (overH * (zoom - 1f) / 2f).coerceAtLeast(0f)
                                    panOffset = Offset(
                                        (panOffset.x + pan.x * zoom).coerceIn(-maxOffX, maxOffX),
                                        (panOffset.y + pan.y * zoom).coerceIn(-maxOffY, maxOffY),
                                    )

                                    val ratio = (abs(damped) / MAX_OVERSCROLL).coerceIn(0f, 1f)
                                    val level = (ratio * 12).toInt() / 12f
                                    if (ratio > 0.02f && level > lastOverscrollLevel && state.hapticsOnRemove) {
                                        haptics.overscroll(ratio)
                                    }
                                    lastOverscrollLevel = if (ratio > 0.02f) level else -1f
                                } else {
                                    // not zoomed: a one-finger drag repositions the before/after divider
                                    setCut((cut * overW + pan.x) / overW)
                                }
                            }
                            // gesture ended (all fingers lifted) — spring back if overscrolled
                            val clamped = rawZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
                            if (rawZoom != clamped) {
                                if (state.hapticsOnRemove) haptics.springRelease()
                                val startZoom = zoom
                                val startOffset = panOffset
                                val targetOffset = if (clamped <= 1.001f) Offset.Zero else Offset(
                                    startOffset.x.coerceIn(-(overW * (clamped - 1f) / 2f), overW * (clamped - 1f) / 2f),
                                    startOffset.y.coerceIn(-(overH * (clamped - 1f) / 2f), overH * (clamped - 1f) / 2f),
                                )
                                rawZoom = clamped
                                scope.launch {
                                    animate(
                                        0f, 1f,
                                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                    ) { v, _ ->
                                        zoom = startZoom + (clamped - startZoom) * v
                                        panOffset = Offset(
                                            startOffset.x + (targetOffset.x - startOffset.x) * v,
                                            startOffset.y + (targetOffset.y - startOffset.y) * v,
                                        )
                                    }
                                }
                            }
                            lastOverscrollLevel = -1f
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom; scaleY = zoom
                            translationX = panOffset.x; translationY = panOffset.y
                        },
                ) {
                    // after = base
                    Image(afterImg, contentDescription = "After", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    // before = clipped to the left of the divider
                    Image(
                        beforeImg,
                        contentDescription = "Before",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                clipRect(right = size.width * cut) { this@drawWithContent.drawContent() }
                            },
                    )
                    if (zoom <= 1.05f) {
                        // divider + handle — hidden once zoomed in, since dragging then pans instead
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    drawContent()
                                    val x = size.width * cut
                                    drawLine(
                                        Color.White,
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = 3f,
                                    )
                                },
                        ) {}
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offsetFraction(cut)
                                .size(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null,
                                tint = Color(0xFF1A1F1E), modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Tag("BEFORE", Modifier.align(Alignment.TopStart), Color(0x8C0C0E14), Color.White)
                Tag("AFTER", Modifier.align(Alignment.TopEnd), Color(0xB8005048), Color(0xFF9FF6E8))
            }

            // meta
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(
                    Icons.Rounded.Shield, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp),
                )
                val secs = if (state.lastMs > 0) " · ${"%.1f".format(state.lastMs / 1000f)} s" else ""
                Text(
                    "Processed on device$secs",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // actions
            Button(
                onClick = {
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { saveToGallery(context, after) }
                        if (state.hapticsOnRemove) { if (uri != null) haptics.success() else haptics.warning() }
                        snackbar.showSnackbar(if (uri != null) "Saved to gallery" else "Save failed")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Save to gallery")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) { saveToGallery(context, after) }
                            if (uri != null) shareImage(context, uri)
                            else { if (state.hapticsOnRemove) haptics.warning(); snackbar.showSnackbar("Couldn't prepare image") }
                        }
                    },
                ) { Text("Share") }
                TextButton(onClick = onBack) { Text("Continue editing") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

/** Position a fixed-width handle centred on the divider fraction. */
private fun Modifier.offsetFraction(fraction: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val x = (constraints.maxWidth * fraction - placeable.width / 2f).toInt()
        layout(constraints.maxWidth, placeable.height) {
            placeable.placeRelative(x, 0)
        }
    }
)

@Composable
private fun Tag(text: String, modifier: Modifier, bg: Color, fg: Color) {
    Box(
        modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
