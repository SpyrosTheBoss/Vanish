package com.example.vanish.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Manages on-device model files: existence checks and on-demand download. */
object ModelStore {

    private fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun fileFor(context: Context, model: InpaintModelId): File? =
        model.downloadFile?.let { File(modelsDir(context), it) }

    /** A model is ready if it's bundled, or its download exists and looks complete. */
    fun isReady(context: Context, model: InpaintModelId): Boolean {
        if (model.bundled) return true
        val f = fileFor(context, model) ?: return false
        return f.exists() && f.length() > 1_000_000L
    }

    /**
     * Download [model] to internal storage, reporting progress in [0,1].
     * Writes to a .part file and renames on success so a partial download is
     * never mistaken for a complete one. No-op if already present.
     */
    suspend fun download(
        context: Context,
        model: InpaintModelId,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val url = model.downloadUrl ?: return@withContext true
        val target = fileFor(context, model) ?: return@withContext false
        if (target.exists() && target.length() > 1_000_000L) return@withContext true

        val part = File(target.parentFile, target.name + ".part")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode !in 200..299) return@withContext false
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        if (total != null) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (target.exists()) target.delete()
            part.renameTo(target)
            onProgress(1f)
            true
        } catch (e: Exception) {
            part.delete()
            false
        } finally {
            conn?.disconnect()
        }
    }
}
