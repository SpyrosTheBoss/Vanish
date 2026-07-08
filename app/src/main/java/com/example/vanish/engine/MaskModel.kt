package com.example.vanish.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * A single mask contribution, stored in IMAGE-pixel space so it is independent
 * of how the photo is currently scaled on screen and can be rasterised straight
 * to a full-resolution mask.
 */
sealed interface Stroke {
    /** Free-hand brush: a poly-line stamped with a round pen of [radius] px. */
    data class Brush(val points: List<FloatArray>, val radius: Float) : Stroke

    /** Lasso: a closed loop that gets filled. */
    data class Lasso(val points: List<FloatArray>) : Stroke
}

object MaskRaster {
    /**
     * Rasterise [strokes] into an ARGB_8888 mask of [width] x [height].
     * Masked pixels are opaque white (0xFFFFFFFF); everything else transparent.
     * The inpainter reads the red channel, so white == "erase".
     */
    fun toBitmap(width: Int, height: Int, strokes: List<Stroke>): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        for (s in strokes) when (s) {
            is Stroke.Brush -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s.radius * 2f
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                if (s.points.size == 1) {
                    val p = s.points[0]
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(p[0], p[1], s.radius, paint)
                } else {
                    val path = Path().apply {
                        val first = s.points.first()
                        moveTo(first[0], first[1])
                        for (i in 1 until s.points.size) lineTo(s.points[i][0], s.points[i][1])
                    }
                    canvas.drawPath(path, paint)
                }
            }
            is Stroke.Lasso -> {
                if (s.points.size >= 3) {
                    paint.style = Paint.Style.FILL
                    val path = Path().apply {
                        val first = s.points.first()
                        moveTo(first[0], first[1])
                        for (i in 1 until s.points.size) lineTo(s.points[i][0], s.points[i][1])
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
        return bmp
    }

    /** True if any stroke actually covers area. */
    fun isEmpty(strokes: List<Stroke>): Boolean = strokes.none {
        when (it) {
            is Stroke.Brush -> it.points.isNotEmpty()
            is Stroke.Lasso -> it.points.size >= 3
        }
    }
}
