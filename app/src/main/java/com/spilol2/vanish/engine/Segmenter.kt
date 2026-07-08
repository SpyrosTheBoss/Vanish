package com.spilol2.vanish.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/** Cached image encoding for the current photo (reused across taps). */
class SamEmbedding(
    val data: FloatArray,   // [1,256,64,64] flattened
    val origW: Int,
    val origH: Int,
    val scale: Float,       // 1024 / max(origW, origH)
)

/**
 * Tap-to-segment via MobileSAM (two ONNX models). The TinyViT encoder runs once
 * per photo (~1-2s); the lightweight decoder runs per tap (fast) to turn a click
 * into a pixel mask.
 */
class Segmenter(context: Context) {

    private val appContext = context.applicationContext
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private val encoder: OrtSession by lazy { load("sam_encoder.onnx") }
    private val decoder: OrtSession by lazy { load("sam_decoder.onnx") }

    private fun load(asset: String): OrtSession =
        appContext.assets.open(asset).use { env.createSession(it.readBytes(), OrtSession.SessionOptions()) }

    /** Encode [bmp] into an embedding reusable for many taps. */
    suspend fun encode(bmp: Bitmap): SamEmbedding = withContext(Dispatchers.Default) {
        val w = bmp.width
        val h = bmp.height
        val scale = SIZE.toFloat() / maxOf(w, h)
        val rw = (w * scale).roundToInt().coerceAtMost(SIZE)
        val rh = (h * scale).roundToInt().coerceAtMost(SIZE)
        val resized = Bitmap.createScaledBitmap(bmp, rw, rh, true)

        val buf = FloatBuffer.allocate(3 * SIZE * SIZE) // zero-padded
        val a = buf.array()
        val px = IntArray(rw * rh)
        resized.getPixels(px, 0, rw, 0, 0, rw, rh)
        val plane = SIZE * SIZE
        for (y in 0 until rh) {
            for (x in 0 until rw) {
                val c = px[y * rw + x]
                val r = (c ushr 16 and 0xFF).toFloat()
                val g = (c ushr 8 and 0xFF).toFloat()
                val b = (c and 0xFF).toFloat()
                val idx = y * SIZE + x
                a[idx] = (r - MEAN[0]) / STD[0]
                a[plane + idx] = (g - MEAN[1]) / STD[1]
                a[2 * plane + idx] = (b - MEAN[2]) / STD[2]
            }
        }

        val embeddings = OnnxTensor.createTensor(
            env, buf, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())
        ).use { t ->
            encoder.run(mapOf("image" to t)).use { r ->
                (r[0] as OnnxTensor).floatBuffer.let { fb ->
                    FloatArray(fb.remaining()).also { fb.get(it) }
                }
            }
        }
        SamEmbedding(embeddings, w, h, scale)
    }

    /**
     * Segment the object under a click at ([px],[py]) in original-image pixels.
     * Returns an ARGB mask (opaque white where selected, transparent elsewhere),
     * same size as the source photo.
     */
    suspend fun segment(emb: SamEmbedding, px: Float, py: Float): Bitmap =
        withContext(Dispatchers.Default) {
            val embT = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(emb.data), longArrayOf(1, 256, 64, 64)
            )
            val coords = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(floatArrayOf(px * emb.scale, py * emb.scale)),
                longArrayOf(1, 1, 2)
            )
            val labels = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(floatArrayOf(1f)), longArrayOf(1, 1)
            )
            val maskInput = OnnxTensor.createTensor(
                env, FloatBuffer.allocate(256 * 256), longArrayOf(1, 1, 256, 256)
            )
            val hasMask = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(floatArrayOf(0f)), longArrayOf(1)
            )
            val origSize = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(floatArrayOf(emb.origH.toFloat(), emb.origW.toFloat())),
                longArrayOf(2)
            )

            val out = decoder.run(
                mapOf(
                    "image_embeddings" to embT,
                    "point_coords" to coords,
                    "point_labels" to labels,
                    "mask_input" to maskInput,
                    "has_mask_input" to hasMask,
                    "orig_im_size" to origSize,
                )
            )
            val masks = out[0] as OnnxTensor
            val fb = masks.floatBuffer
            val w = emb.origW
            val h = emb.origH
            val pixels = IntArray(w * h)
            for (i in 0 until w * h) {
                pixels[i] = if (fb.get(i) > 0f) 0xFFFFFFFF.toInt() else 0
            }
            out.close()
            embT.close(); coords.close(); labels.close(); maskInput.close(); hasMask.close(); origSize.close()
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }

    companion object {
        private const val SIZE = 1024
        private val MEAN = floatArrayOf(123.675f, 116.28f, 103.53f)
        private val STD = floatArrayOf(58.395f, 57.12f, 57.375f)
    }
}
