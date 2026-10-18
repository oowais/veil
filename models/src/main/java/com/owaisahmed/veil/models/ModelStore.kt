package com.owaisahmed.veil.models

import android.content.Context
import java.io.File

/**
 * Models live in the app's external files dir so they can also be side-loaded with
 * `adb push <file> /sdcard/Android/data/com.owaisahmed.veil/files/models/<id>/`.
 */
class ModelStore(context: Context) {

    val root: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "models")

    fun dir(spec: ModelSpec): File = File(root, spec.id)

    fun file(spec: ModelSpec, fileName: String): File = File(dir(spec), fileName)

    fun isInstalled(spec: ModelSpec): Boolean =
        spec.files.all { file(spec, it.fileName).let { f -> f.isFile && f.length() == it.sizeBytes } }

    /** Removes the model's files (also partial downloads). */
    fun delete(spec: ModelSpec): Boolean = dir(spec).deleteRecursively()
}
