package com.owaisahmed.veil.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlin.math.max

/** Loading photos for photo mode and saving results to the gallery (D-057). */
object PhotoFiles {
    /** Longest side photos are scaled to: plenty for OCR (its detector works at 1280) and for saving. */
    private const val MAX_SIDE = 2400

    /** Where the camera app writes a new photo; overwritten each time (it lives in the cache). */
    fun cameraTarget(context: Context): Uri {
        val file = File(context.cacheDir, "photos/camera.jpg").also { it.parentFile?.mkdirs() }
        return FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    }

    /** Decodes [uri] upright (EXIF rotation applied) as a software bitmap, at most [MAX_SIDE] px. */
    fun load(context: Context, uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = max(info.size.width, info.size.height)
            if (side > MAX_SIDE) {
                val f = MAX_SIDE.toFloat() / side
                decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
        }

    /** A copy of [bitmap] turned [degrees] clockwise. */
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)

    /** Saves [bitmap] as a JPEG in Pictures/Veil; no permission needed for the app's own files. */
    fun saveToGallery(context: Context, bitmap: Bitmap, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Veil")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("the gallery refused the file")
        try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                ?: throw IOException("can't write the file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
