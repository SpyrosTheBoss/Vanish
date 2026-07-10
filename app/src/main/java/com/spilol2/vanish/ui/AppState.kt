package com.spilol2.vanish.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.spilol2.vanish.engine.InpaintModelId
import com.spilol2.vanish.engine.SamEmbedding
import com.spilol2.vanish.engine.Stroke

enum class Screen { Home, Editor, Result, Settings }

enum class Tool { Tap, Lasso, Brush }

private const val PREFS_NAME = "vanish_prefs"
private const val KEY_MODEL = "inpaint_model"
private const val KEY_DYNAMIC_COLOR = "dynamic_color"
private const val KEY_HAPTICS = "haptics_on_remove"
private const val KEY_KEEP_ORIGINAL = "keep_original"

/**
 * Single source of truth for the whole (small) app. Held in a `remember` at the
 * root so it survives screen switches without a navigation library or a
 * ViewModel — a 4-screen tool doesn't need either.
 *
 * Settings (model choice, toggles) are backed by SharedPreferences so they
 * survive process death, not just screen navigation.
 */
class AppState(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    // settings — each persists to SharedPreferences immediately on write, so a
    // fully-closed-and-reopened app remembers your model choice and toggles.
    private val _inpaintModel = mutableStateOf(
        prefs.getString(KEY_MODEL, null)?.let { saved ->
            InpaintModelId.entries.find { it.name == saved }
        } ?: InpaintModelId.MIGAN
    )
    var inpaintModel: InpaintModelId
        get() = _inpaintModel.value
        set(v) { _inpaintModel.value = v; prefs.edit().putString(KEY_MODEL, v.name).apply() }

    private val _dynamicColor = mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    var dynamicColor: Boolean
        get() = _dynamicColor.value
        set(v) { _dynamicColor.value = v; prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, v).apply() }

    private val _hapticsOnRemove = mutableStateOf(prefs.getBoolean(KEY_HAPTICS, true))
    var hapticsOnRemove: Boolean
        get() = _hapticsOnRemove.value
        set(v) { _hapticsOnRemove.value = v; prefs.edit().putBoolean(KEY_HAPTICS, v).apply() }

    private val _keepOriginal = mutableStateOf(prefs.getBoolean(KEY_KEEP_ORIGINAL, true))
    var keepOriginal: Boolean
        get() = _keepOriginal.value
        set(v) { _keepOriginal.value = v; prefs.edit().putBoolean(KEY_KEEP_ORIGINAL, v).apply() }

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
