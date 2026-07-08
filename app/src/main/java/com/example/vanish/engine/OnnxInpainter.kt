package com.example.vanish.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * Real on-device inpainter backed by the bundled MI-GAN ONNX model.
 *
 * MI-GAN runs at a fixed 512x512. To keep quality high on large photos we
 * follow the same crop-around-the-mask strategy the desktop app uses: crop a
 * padded square around the masked region, run the model at 512, then paste
 * only the generated (masked) pixels back into the original — untouched areas
 * stay pixel-perfect.
 *
 * Input/output contract (must match scripts/export_migan_onnx.py):
 *   input  [1,4,512,512] = concat(0.5 - mask, erased_rgb) ; rgb in [-1,1]
 *   output [1,3,512,512] in [-1,1], RGB
 */
class OnnxInpainter(context: Context) : Inpainter {

    private val appContext = context.applicationContext
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private val session: OrtSession by lazy {
        val bytes = appContext.assets.open("migan_512.onnx").use { it.readBytes() }
        env.createSession(bytes, OrtSession.SessionOptions())
    }

    override suspend fun inpaint(source: Bitmap, mask: Bitmap): Bitmap =
        withContext(Dispatchers.Default) {
            val w = source.width
            val h = source.height

            // 1) bounding box of the masked pixels (red channel >= 128)
            val mPix = IntArray(w * h)
            mask.getPixels(mPix, 0, w, 0, 0, w, h)
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if ((mPix[row + x] ushr 16 and 0xFF) >= 128) {
                        if (x < minX) minX = x; if (x > maxX) maxX = x
                        if (y < minY) minY = y; if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < 0) return@withContext source.copy(Bitmap.Config.ARGB_8888, false)

            // 2) pad + square the crop, clamped to the image
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val margin = (maxOf(bw, bh) * 0.6f).toInt().coerceAtLeast(48)
            var side = maxOf(bw, bh) + margin * 2
            side = side.coerceAtMost(minOf(w, h))
            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            var left = (cx - side / 2).coerceIn(0, w - side)
            var top = (cy - side / 2).coerceIn(0, h - side)

            // 3) crop source + mask, scale both to 512
            val cropSrc = Bitmap.createBitmap(source, left, top, side, side)
            val cropMask = Bitmap.createBitmap(mask, left, top, side, side)
            val src512 = cropSrc.scaleTo(SIZE)
            val mask512 = cropMask.scaleTo(SIZE)

            // 4) build the [1,4,512,512] input
            val input = buildInput(src512, mask512)
            val shape = longArrayOf(1, 4, SIZE.toLong(), SIZE.toLong())
            val out512: Bitmap = OnnxTensor.createTensor(env, input, shape).use { tensor ->
                session.run(mapOf("input" to tensor)).use { result ->
                    val outTensor = result[0] as OnnxTensor
                    tensorToBitmap(outTensor.floatBuffer)
                }
            }

            // 5) scale generated crop back and paste only masked pixels
            val resultCrop = out512.scaleTo(side)
            val out = source.copy(Bitmap.Config.ARGB_8888, true)
            val rPix = IntArray(side * side)
            val cmPix = IntArray(side * side)
            resultCrop.getPixels(rPix, 0, side, 0, 0, side, side)
            cropMask.getPixels(cmPix, 0, side, 0, 0, side, side)
            val outPix = IntArray(side * side)
            out.getPixels(outPix, 0, side, left, top, side, side)
            for (i in 0 until side * side) {
                if ((cmPix[i] ushr 16 and 0xFF) >= 128) outPix[i] = rPix[i]
            }
            out.setPixels(outPix, 0, side, left, top, side, side)
            out
        }

    /** Compose the 4-channel MI-GAN input plane (0.5 - mask, then erased R,G,B). */
    private fun buildInput(img512: Bitmap, mask512: Bitmap): FloatBuffer {
        val n = SIZE * SIZE
        val buf = FloatBuffer.allocate(4 * n)
        val ip = IntArray(n); img512.getPixels(ip, 0, SIZE, 0, 0, SIZE, SIZE)
        val mp = IntArray(n); mask512.getPixels(mp, 0, SIZE, 0, 0, SIZE, SIZE)

        val arr = buf.array()
        // channel 0: 0.5 - mask   (mask is 1 in the hole)
        for (i in 0 until n) {
            val hole = if ((mp[i] ushr 16 and 0xFF) > 120) 1f else 0f
            arr[i] = 0.5f - hole
        }
        // channels 1..3: erased rgb in [-1,1] = (rgb/255*2-1) * (1 - mask)
        for (c in 0 until 3) {
            val base = (c + 1) * n
            val shift = 16 - c * 8 // R=16, G=8, B=0
            for (i in 0 until n) {
                val hole = if ((mp[i] ushr 16 and 0xFF) > 120) 1f else 0f
                val v = (ip[i] ushr shift and 0xFF) / 255f * 2f - 1f
                arr[base + i] = v * (1f - hole)
            }
        }
        return buf
    }

    /** [1,3,512,512] float in [-1,1] -> ARGB_8888 bitmap. */
    private fun tensorToBitmap(fb: FloatBuffer): Bitmap {
        val n = SIZE * SIZE
        val px = IntArray(n)
        for (i in 0 until n) {
            val r = ((fb.get(i) * 127.5f + 127.5f) + 0.5f).toInt().coerceIn(0, 255)
            val g = ((fb.get(n + i) * 127.5f + 127.5f) + 0.5f).toInt().coerceIn(0, 255)
            val b = ((fb.get(2 * n + i) * 127.5f + 127.5f) + 0.5f).toInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun Bitmap.scaleTo(size: Int): Bitmap = Bitmap.createScaledBitmap(this, size, size, true)

    companion object { private const val SIZE = 512 }
}
