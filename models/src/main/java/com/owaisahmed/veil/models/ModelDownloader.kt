package com.owaisahmed.veil.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Resumable (HTTP Range) downloader with SHA-256 verification. */
class ModelDownloader(private val store: ModelStore) {

    fun interface Progress {
        fun onProgress(downloadedBytes: Long, totalBytes: Long)
    }

    suspend fun download(spec: ModelSpec, progress: Progress) = withContext(Dispatchers.IO) {
        store.dir(spec).mkdirs()
        var doneBefore = 0L
        for (mf in spec.files) {
            val target = store.file(spec, mf.fileName)
            if (!(target.isFile && target.length() == mf.sizeBytes)) {
                fetch(mf, target) { bytes -> progress.onProgress(doneBefore + bytes, spec.totalBytes) }
                verify(mf, target)
            }
            doneBefore += mf.sizeBytes
            progress.onProgress(doneBefore, spec.totalBytes)
        }
    }

    private suspend fun fetch(mf: ModelFile, target: File, onBytes: (Long) -> Unit) {
        val part = File(target.path + ".part")
        val have = if (part.isFile) part.length() else 0L

        val conn = URL(mf.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        try {
            val code = conn.responseCode
            val resume = code == HttpURLConnection.HTTP_PARTIAL
            if (!resume && code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for ${mf.url}")

            RandomAccessFile(part, "rw").use { out ->
                var written = if (resume) have else 0L
                out.setLength(written)
                out.seek(written)
                conn.inputStream.use { input ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        onBytes(written)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (part.length() != mf.sizeBytes) throw IOException("Size mismatch for ${mf.fileName}: ${part.length()} != ${mf.sizeBytes}")
        if (!part.renameTo(target)) throw IOException("Cannot move ${part.name} into place")
    }

    private fun verify(mf: ModelFile, file: File) {
        val expected = mf.sha256 ?: return
        val actual = sha256(file)
        if (!actual.equals(expected, ignoreCase = true)) {
            file.delete()
            throw IOException("SHA-256 mismatch for ${mf.fileName}: got $actual")
        }
    }

    companion object {
        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
