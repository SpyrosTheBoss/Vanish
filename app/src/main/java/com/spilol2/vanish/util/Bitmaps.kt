package com.spilol2.vanish.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size

/** Longest edge we keep photos at, to bound memory and processing time. */
private const val MAX_EDGE = 2048

/**
 * Decode [uri] to a mutable software ARGB_8888 bitmap (so we can read/write
 * pixels), downscaled so its longest edge is at most [MAX_EDGE].
 */
fun decodeBitmap(context: Context, uri: Uri): Bitmap? = try {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
        val w = info.size.width
        val h = info.size.height
        val longest = maxOf(w, h)
        if (longest > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / longest
            decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        }
    }.let { bmp ->
        // Guarantee ARGB_8888 for pixel access.
        if (bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, true)
    }
} catch (e: Exception) {
    null
}

/** Fast, small thumbnail for a MediaStore [uri] — for grids, not editing. */
fun loadThumbnail(context: Context, uri: Uri, size: Size): Bitmap? = try {
    context.contentResolver.loadThumbnail(uri, size, null)
} catch (e: Exception) {
    null
}
