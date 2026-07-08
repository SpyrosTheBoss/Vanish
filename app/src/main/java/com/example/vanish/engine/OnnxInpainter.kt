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
 * On-device inpainter backed by ONNX Runtime, supporting multiple models.
 *
 * Every model runs at a fixed 512x512, so we use the same crop-around-the-mask
 * strategy: crop a padded square around the masked region, run the model at 512,
 * scale the generated crop back, and paste only the masked pixels into the
 * original. Untouched areas stay pixel-perfect.
 *
 * Per-model input/output differs (see [run512]):
 *  - MI-GAN: single 4-ch input concat(0.5-mask, erased_rgb[-1,1]); out [-1,1]
 *  - LaMa:   two inputs image[0,1] + mask{0,1}; out [0,255]
 */
class OnnxInpainter(context: Context) : Inpainter {

    private val appContext = context.applicationContext
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessions = HashMap<InpaintModelId, OrtSession>()

    private fun session(model: InpaintModelId): OrtSession = sessions.getOrPut(model) {
        val bytes: ByteArray = when {
            model.bundledAsset != null ->
                appContext.assets.open(model.bundledAsset).use { it.readBytes() }
            else -> {
                val f = ModelStore.fileFor(appContext, model)
                    ?: error("No file for ${model.display}")
                require(f.exists()) { "${model.display} not downloaded" }
                f.readBytes()
            }
        }
        env.createSession(bytes, OrtSession.SessionOptions())
    }

    override suspend fun inpaint(source: Bitmap, mask: Bitmap, model: InpaintModelId): Bitmap =
        withContext(Dispatchers.Default) {
            val w = source.width
            val h = source.height

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

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val margin = (maxOf(bw, bh) * 0.6f).toInt().coerceAtLeast(48)
            var side = (maxOf(bw, bh) + margin * 2).coerceAtMost(minOf(w, h))
            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            val left = (cx - side / 2).coerceIn(0, w - side)
            val top = (cy - side / 2).coerceIn(0, h - side)

            val cropSrc = Bitmap.createBitmap(source, left, top, side, side)
            val cropMask = Bitmap.createBitmap(mask, left, top, side, side)
            val src512 = cropSrc.scaleTo(SIZE)
            val mask512 = cropMask.scaleTo(SIZE)

            val out512 = run512(model, src512, mask512)

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

    private fun run512(model: InpaintModelId, src512: Bitmap, mask512: Bitmap): Bitmap {
        val s = session(model)
        return when (model) {
            InpaintModelId.MIGAN -> {
                val input = buildMiganInput(src512, mask512)
                val shape = longArrayOf(1, 4, SIZE.toLong(), SIZE.toLong())
                OnnxTensor.createTensor(env, input, shape).use { t ->
                    s.run(mapOf("input" to t)).use { r ->
                        tensorToBitmap((r[0] as OnnxTensor).floatBuffer, scale = 127.5f, bias = 127.5f)
                    }
                }
            }
            InpaintModelId.LAMA -> {
                val (img, msk) = buildLamaInputs(src512, mask512)
                val imgShape = longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())
                val mskShape = longArrayOf(1, 1, SIZE.toLong(), SIZE.toLong())
                OnnxTensor.createTensor(env, img, imgShape).use { it ->
                    OnnxTensor.createTensor(env, msk, mskShape).use { mt ->
                        s.run(mapOf("image" to it, "mask" to mt)).use { r ->
                            tensorToBitmap((r[0] as OnnxTensor).floatBuffer, scale = 1f, bias = 0f)
                        }
                    }
                }
            }
        }
    }

    /** MI-GAN: [1,4,512,512] = (0.5 - mask) then erased R,G,B in [-1,1]. */
    private fun buildMiganInput(img: Bitmap, mask: Bitmap): FloatBuffer {
        val n = SIZE * SIZE
        val buf = FloatBuffer.allocate(4 * n)
        val ip = IntArray(n); img.getPixels(ip, 0, SIZE, 0, 0, SIZE, SIZE)
        val mp = IntArray(n); mask.getPixels(mp, 0, SIZE, 0, 0, SIZE, SIZE)
        val a = buf.array()
        for (i in 0 until n) {
            val hole = if ((mp[i] ushr 16 and 0xFF) > 120) 1f else 0f
            a[i] = 0.5f - hole
        }
        for (c in 0 until 3) {
            val base = (c + 1) * n
            val shift = 16 - c * 8
            for (i in 0 until n) {
                val hole = if ((mp[i] ushr 16 and 0xFF) > 120) 1f else 0f
                val v = (ip[i] ushr shift and 0xFF) / 255f * 2f - 1f
                a[base + i] = v * (1f - hole)
            }
        }
        return buf
    }

    /** LaMa: image [1,3,512,512] in [0,1], mask [1,1,512,512] in {0,1}. */
    private fun buildLamaInputs(img: Bitmap, mask: Bitmap): Pair<FloatBuffer, FloatBuffer> {
        val n = SIZE * SIZE
        val ib = FloatBuffer.allocate(3 * n)
        val mb = FloatBuffer.allocate(n)
        val ip = IntArray(n); img.getPixels(ip, 0, SIZE, 0, 0, SIZE, SIZE)
        val mp = IntArray(n); mask.getPixels(mp, 0, SIZE, 0, 0, SIZE, SIZE)
        val ia = ib.array(); val ma = mb.array()
        for (c in 0 until 3) {
            val base = c * n
            val shift = 16 - c * 8
            for (i in 0 until n) ia[base + i] = (ip[i] ushr shift and 0xFF) / 255f
        }
        for (i in 0 until n) ma[i] = if ((mp[i] ushr 16 and 0xFF) > 120) 1f else 0f
        return ib to mb
    }

    /** [1,3,512,512] float -> ARGB_8888, with value = f*scale + bias. */
    private fun tensorToBitmap(fb: FloatBuffer, scale: Float, bias: Float): Bitmap {
        val n = SIZE * SIZE
        val px = IntArray(n)
        for (i in 0 until n) {
            val r = ((fb.get(i) * scale + bias) + 0.5f).toInt().coerceIn(0, 255)
            val g = ((fb.get(n + i) * scale + bias) + 0.5f).toInt().coerceIn(0, 255)
            val b = ((fb.get(2 * n + i) * scale + bias) + 0.5f).toInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun Bitmap.scaleTo(size: Int): Bitmap = Bitmap.createScaledBitmap(this, size, size, true)

    companion object { private const val SIZE = 512 }
}
