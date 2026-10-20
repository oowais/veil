package com.owaisahmed.veil

import android.app.Application
import com.owaisahmed.veil.pipeline.Pipeline
import com.owaisahmed.veil.pipeline.PipelineLog

class VeilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PipelineLog.init(this)
        Pipeline.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Pipeline.onTrimMemory(level)
    }
}
