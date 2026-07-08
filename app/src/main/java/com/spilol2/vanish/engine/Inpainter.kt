package com.spilol2.vanish.engine

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fills the masked region of [source] and returns a new bitmap.
 *
 * @param source ARGB_8888 image.
 * @param mask   ARGB_8888, same size as [source]; a pixel is "remove me" when
 *               its alpha (or luminance) is high. We use the red channel here:
 *               red >= 128 means erase.
 */
interface Inpainter {
    suspend fun inpaint(source: Bitmap, mask: Bitmap, model: InpaintModelId): Bitmap
}

/**
 * Placeholder inpainter used until the ONNX MI-GAN engine is wired in.
 * It fills each masked region with the average colour of the ring of pixels
 * just outside that region's bounding box, plus a little noise — enough that
 * "Remove" visibly does something and the whole pick → mask → result → save
 * flow can be exercised. Replaced by OnnxInpainter in the MI-GAN integration.
 */
class StubInpainter : Inpainter {
    override suspend fun inpaint(source: Bitmap, mask: Bitmap, model: InpaintModelId): Bitmap =
        withContext(Dispatchers.Default) {
            val w = source.width
            val h = source.height
            val px = IntArray(w * h)
            val mk = IntArray(w * h)
            source.getPixels(px, 0, w, 0, 0, w, h)
            mask.getPixels(mk, 0, w, 0, 0, w, h)

            fun masked(i: Int) = (mk[i] ushr 16 and 0xFF) >= 128 // red channel

            // bounding box of the mask
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) for (x in 0 until w) {
                if (masked(y * w + x)) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
            }
            if (maxX < 0) return@withContext source.copy(Bitmap.Config.ARGB_8888, false)

            // average colour of unmasked pixels in a ring around the bbox
            val pad = 12
            var rSum = 0L; var gSum = 0L; var bSum = 0L; var n = 0L
            val rx0 = (minX - pad).coerceAtLeast(0)
            val ry0 = (minY - pad).coerceAtLeast(0)
            val rx1 = (maxX + pad).coerceAtMost(w - 1)
            val ry1 = (maxY + pad).coerceAtMost(h - 1)
            for (y in ry0..ry1) for (x in rx0..rx1) {
                val i = y * w + x
                if (!masked(i)) {
                    val c = px[i]
                    rSum += (c ushr 16 and 0xFF); gSum += (c ushr 8 and 0xFF); bSum += (c and 0xFF); n++
                }
            }
            if (n == 0L) n = 1
            val ar = (rSum / n).toInt(); val ag = (gSum / n).toInt(); val ab = (bSum / n).toInt()

            val rnd = java.util.Random(1)
            for (i in px.indices) {
                if (masked(i)) {
                    val j = rnd.nextInt(17) - 8
                    val r = (ar + j).coerceIn(0, 255)
                    val g = (ag + j).coerceIn(0, 255)
                    val b = (ab + j).coerceIn(0, 255)
                    px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }
}
