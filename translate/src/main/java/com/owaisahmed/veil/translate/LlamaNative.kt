package com.owaisahmed.veil.translate

/** JNI surface of `veil_llama.cpp`. Handles are opaque native pointers; 0 means failure. */
internal object LlamaNative {
    init {
        System.loadLibrary("veil_llama")
    }

    external fun init(nativeLibDir: String)
    external fun load(modelPath: String, nCtx: Int, nThreads: Int, nThreadsBatch: Int): Long
    external fun complete(handle: Long, userMessage: String, maxTokens: Int): String?

    /** [promptTokens, promptMs, generatedTokens, generateMs] of the last [complete] call; prompt tokens
     *  count only those processed (a prefix shared with the previous call is reused from the KV cache). */
    external fun lastStats(handle: Long): DoubleArray
    external fun unload(handle: Long)
    external fun systemInfo(): String
}
