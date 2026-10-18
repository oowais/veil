package com.owaisahmed.veil

import android.app.Application
import com.owaisahmed.veil.pipeline.Pipeline

class VeilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Pipeline.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Pipeline.onTrimMemory(level)
    }
}
