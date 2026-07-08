package com.example.vanish.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.vanish.engine.InpaintModelId
import com.example.vanish.engine.SamEmbedding
import com.example.vanish.engine.Stroke

enum class Screen { Home, Editor, Result, Settings }

enum class Tool { Tap, Lasso, Brush }

/**
 * Single source of truth for the whole (small) app. Held in a `remember` at the
 * root so it survives screen switches without a navigation library or a
 * ViewModel — a 4-screen tool doesn't need either.
 */
class AppState {
    var screen by mutableStateOf(Screen.Home)

    /** The photo being edited (null until one is picked). */
    var source by mutableStateOf<Bitmap?>(null)
    /** The inpainted output, shown on the Result screen. */
    var result by mutableStateOf<Bitmap?>(null)

    var tool by mutableStateOf(Tool.Brush)
    var brushRadius by mutableStateOf(36f) // image-space px; scaled per photo on load

    /** Committed strokes and the redo stack (for undo/redo). */
    val strokes: SnapshotStateList<Stroke> = mutableStateListOf()
    private val redo: SnapshotStateList<Stroke> = mutableStateListOf()

    var busy by mutableStateOf(false) // processing spinner
    var lastMs by mutableStateOf(0L)  // last inpaint duration, shown on Result

    // tap-to-segment: per-photo image encoding
    var embedding by mutableStateOf<SamEmbedding?>(null)
    var encoding by mutableStateOf(false)

    // settings
    var inpaintModel by mutableStateOf(InpaintModelId.MIGAN)
    var dynamicColor by mutableStateOf(true)
    var hapticsOnRemove by mutableStateOf(true)
    var keepOriginal by mutableStateOf(true)

    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun addStroke(s: Stroke) {
        strokes.add(s)
        redo.clear()
    }

    fun undo() {
        if (strokes.isNotEmpty()) redo.add(strokes.removeAt(strokes.lastIndex))
    }

    fun redo() {
        if (redo.isNotEmpty()) strokes.add(redo.removeAt(redo.lastIndex))
    }

    fun openEditor(bmp: Bitmap) {
        source = bmp
        result = null
        embedding = null
        strokes.clear()
        redo.clear()
        // scale a sensible default brush to the photo's size (~4% of min edge)
        brushRadius = (minOf(bmp.width, bmp.height) * 0.04f).coerceIn(12f, 120f)
        screen = Screen.Editor
    }

    fun clearMask() {
        strokes.clear()
        redo.clear()
    }
}
