package com.spilol2.vanish.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.OutputStream

/** Save [bmp] as a PNG into the device gallery (Pictures/Vanish). Returns its uri. */
fun saveToGallery(context: Context, bmp: Bitmap): Uri? {
    val name = "Vanish_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Vanish")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    try {
        resolver.openOutputStream(uri)?.use { out: OutputStream ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        return null
    }
}

/** The most recent images Vanish has saved to the gallery, newest first. */
fun queryRecentEdits(context: Context, limit: Int = 6): List<Uri> {
    val resolver = context.contentResolver
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val selection: String?
    val args: Array<String>?
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        args = arrayOf("Pictures/Vanish/")
    } else {
        selection = null
        args = null
    }
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    val uris = mutableListOf<Uri>()
    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args, sort)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && uris.size < limit) {
            uris.add(
                Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol).toString())
            )
        }
    }
    return uris
}

/** Fire the system share sheet for an image [uri]. */
fun shareImage(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share image"))
}
