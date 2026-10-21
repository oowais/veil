package com.owaisahmed.veil.translate

/** JNI surface of `veil_bergamot.cpp`. Handles are opaque native pointers; 0 means failure. */
internal object BergamotNative {
    init {
        System.loadLibrary("veil_bergamot")
    }

    /** [config]: Marian YAML with the model, vocab and shortlist paths. */
    external fun load(config: String): Long

    /** One translation per text, in order; null if the native side failed (see logcat tag VeilBergamot). */
    external fun translate(handle: Long, texts: Array<String>): Array<String>?
    external fun unload(handle: Long)
}
