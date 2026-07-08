package com.example.vanish.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.vanish.engine.Inpainter
import com.example.vanish.engine.MaskRaster
import com.example.vanish.engine.Stroke
import com.example.vanish.ui.AppState
import com.example.vanish.ui.Screen
import com.example.vanish.ui.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/** Uniform fit-inside mapping between image pixels and canvas pixels. */
private data class Fit(val scale: Float, val dx: Float, val dy: Float) {
    fun toImage(o: Offset) = Offset((o.x - dx) / scale, (o.y - dy) / scale)
    fun toCanvas(x: Float, y: Float) = Offset(dx + x * scale, dy + y * scale)
}

private fun fitOf(canvas: Size, iw: Int, ih: Int): Fit {
    val s = min(canvas.width / iw, canvas.height / ih)
    return Fit(s, (canvas.width - iw * s) / 2f, (canvas.height - ih * s) / 2f)
}

@Composable
fun EditorScreen(
    state: AppState,
    inpainter: Inpainter,
    onBack: () -> Unit,
) {
    val src = state.source ?: return
    val img = remember(src) { src.asImageBitmap() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val sel = MaterialTheme.colorScheme.primary
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) { kotlinx.coroutines.delay(2200); toast = null }
    }

    // live stroke being drawn (image-space points)
    val live = remember { mutableStateListOf<Offset>() }

    fun remove() {
        if (MaskRaster.isEmpty(state.strokes)) { toast = "Circle something to remove first"; return }
        if (state.busy) return
        state.busy = true
        scope.launch {
            val mask = withContext(Dispatchers.Default) {
                MaskRaster.toBitmap(src.width, src.height, state.strokes)
            }
            val t0 = System.currentTimeMillis()
            val out = inpainter.inpaint(src, mask, state.inpaintModel)
            state.lastMs = System.currentTimeMillis() - t0
            state.result = out
            state.busy = false
            if (state.hapticsOnRemove) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            state.screen = Screen.Result
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0D0D))) {

        // ---- photo + drawing canvas ----
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state.tool, state.brushRadius) {
                    when (state.tool) {
                        Tool.Tap -> detectTapGestures {
                            toast = "Tap-to-segment is coming soon — use lasso or brush for now"
                        }
                        Tool.Brush, Tool.Lasso -> detectDragGestures(
                            onDragStart = { pos ->
                                val fit = fitOf(Size(size.width.toFloat(), size.height.toFloat()), img.width, img.height)
                                live.clear()
                                live.add(fit.toImage(pos))
                            },
                            onDrag = { change, _ ->
                                val fit = fitOf(Size(size.width.toFloat(), size.height.toFloat()), img.width, img.height)
                                live.add(fit.toImage(change.position))
                            },
                            onDragEnd = {
                                val pts = live.map { floatArrayOf(it.x, it.y) }
                                if (pts.isNotEmpty()) {
                                    val stroke = if (state.tool == Tool.Brush)
                                        Stroke.Brush(pts, state.brushRadius)
                                    else Stroke.Lasso(pts)
                                    state.addStroke(stroke)
                                }
                                live.clear()
                            },
                            onDragCancel = { live.clear() },
                        )
                    }
                }
        ) {
            val fit = fitOf(size, img.width, img.height)
            drawImage(
                image = img,
                dstOffset = IntOffset(fit.dx.toInt(), fit.dy.toInt()),
                dstSize = IntSize((img.width * fit.scale).toInt(), (img.height * fit.scale).toInt()),
            )
            for (s in state.strokes) drawStroke(s, fit, sel, committed = true)
            // live preview
            if (live.isNotEmpty()) {
                val pts = live.map { floatArrayOf(it.x, it.y) }
                val preview = if (state.tool == Tool.Brush) Stroke.Brush(pts, state.brushRadius) else Stroke.Lasso(pts)
                drawStroke(preview, fit, sel, committed = false)
            }
        }

        // top gradient scrim + bar
        TopBar(state, onBack)

        // bottom controls
        BottomControls(
            state = state,
            onRemove = { remove() },
        )

        // busy overlay
        if (state.busy) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("Removing…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // transient toast
        toast?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xE6101615),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp, start = 24.dp, end = 24.dp),
            ) {
                Text(
                    msg,
                    color = Color(0xFFE6EEEC),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private fun DrawScope.drawStroke(s: Stroke, fit: Fit, color: Color, committed: Boolean) {
    val fillA = if (committed) 0.38f else 0.28f
    val lineA = if (committed) 1f else 0.8f
    when (s) {
        is Stroke.Brush -> {
            val path = Path()
            s.points.forEachIndexed { i, p ->
                val c = fit.toCanvas(p[0], p[1])
                if (i == 0) path.moveTo(c.x, c.y) else path.lineTo(c.x, c.y)
            }
            drawPath(
                path, color.copy(alpha = fillA),
                style = DrawStroke(
                    width = s.radius * 2f * fit.scale,
                    cap = StrokeCap.Round, join = StrokeJoin.Round,
                ),
            )
        }
        is Stroke.Lasso -> {
            if (s.points.size < 2) return
            val path = Path()
            s.points.forEachIndexed { i, p ->
                val c = fit.toCanvas(p[0], p[1])
                if (i == 0) path.moveTo(c.x, c.y) else path.lineTo(c.x, c.y)
            }
            path.close()
            drawPath(path, color.copy(alpha = fillA))
            drawPath(
                path, color.copy(alpha = lineA),
                style = DrawStroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
