package com.owaisahmed.veil.models

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Model downloads that belong to the app, not to a screen: leaving the Models screen no longer
 * cancels them (D-050). They still stop if Android ends the app process; the next download resumes
 * where it stopped (HTTP Range).
 */
object ModelDownloads {

    data class State(
        /** 0–1. */
        val progress: Float = 0f,
        val running: Boolean = false,
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<String, Job>()
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())

    /** By [ModelSpec.id]; specs never downloaded in this process are missing. */
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    fun start(store: ModelStore, spec: ModelSpec) {
        if (jobs[spec.id]?.isActive == true) return
        set(spec) { State(progress = it.progress, running = true) }
        jobs[spec.id] = scope.launch {
            try {
                var shown = -1
                ModelDownloader(store).download(spec) { done, total ->
                    // Called per 64 KB chunk on an IO thread; publish only when the permille changes.
                    val permille = (done * 1000 / total).toInt()
                    if (permille != shown) {
                        shown = permille
                        set(spec) { it.copy(progress = permille / 1000f) }
                    }
                }
                set(spec) { State(progress = 1f) }
            } catch (e: CancellationException) {
                set(spec) { it.copy(running = false) }
                throw e
            } catch (e: Exception) {
                set(spec) { it.copy(running = false, error = e.message ?: e::class.simpleName) }
            } finally {
                jobs.remove(spec.id)
            }
        }
    }

    fun cancel(spec: ModelSpec) {
        jobs[spec.id]?.cancel()
    }

    /** After the model's files were deleted: back to "not downloaded". */
    fun reset(spec: ModelSpec) = _states.update { it - spec.id }

    private fun set(spec: ModelSpec, change: (State) -> State) =
        _states.update { it + (spec.id to change(it[spec.id] ?: State())) }
}
