package com.owaisahmed.veil.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Copies model files picked by the user (e.g. downloaded on a computer) into the store (D-055).
 * Files are matched to the model's files by size, since the downloaded names differ (both OCR
 * files are called `inference.onnx`); each is checked against its SHA-256 like a download.
 */
object ModelImport {

    /** What happened, for one line under the model. */
    data class Result(val imported: Int, val skipped: List<String>)

    suspend fun import(context: Context, store: ModelStore, spec: ModelSpec, uris: List<Uri>): Result = withContext(Dispatchers.IO) {
        val skipped = ArrayList<String>()
        var imported = 0
        for (uri in uris) {
            val (name, size) = describe(context, uri)
            val mf = spec.files.firstOrNull { it.sizeBytes == size }
            if (mf == null) {
                skipped += "$name (not part of this model)"
                continue
            }
            val target = store.file(spec, mf.fileName)
            val part = File(target.path + ".import")
            target.parentFile?.mkdirs()
            try {
                context.contentResolver.openInputStream(uri)?.use { input -> part.outputStream().use { input.copyTo(it, 1 shl 16) } }
                    ?: throw IOException("can't open")
                val expected = mf.sha256
                if (expected != null && !ModelDownloader.sha256(part).equals(expected, ignoreCase = true)) {
                    part.delete()
                    skipped += "$name (checksum doesn't match)"
                    continue
                }
                if (!part.renameTo(target)) {
                    target.delete()
                    if (!part.renameTo(target)) throw IOException("can't move into place")
                }
                imported++
            } catch (e: IOException) {
                part.delete()
                skipped += "$name (${e.message})"
            }
        }
        Result(imported, skipped)
    }

    private fun describe(context: Context, uri: Uri): Pair<String, Long> {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0) ?: uri.lastPathSegment ?: "file"
                val size = if (c.isNull(1)) -1L else c.getLong(1)
                return name to size
            }
        }
        return (uri.lastPathSegment ?: "file") to -1L
    }
}
