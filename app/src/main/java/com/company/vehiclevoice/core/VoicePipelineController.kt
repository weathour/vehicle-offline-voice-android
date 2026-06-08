package com.company.vehiclevoice.core

import com.company.vehiclevoice.log.EventLogSink
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class VoicePipelineController(
    private val pipelineFactory: () -> VoicePipeline,
    private val logSink: EventLogSink,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vehicle-voice-pipeline").apply { isDaemon = true }
    }
) : AutoCloseable {
    enum class State {
        Idle,
        Running,
        Completed,
        Failed,
        Stopped,
        Closed
    }

    @Volatile
    private var state: State = State.Idle

    @Volatile
    private var lastResult: VoicePipelineResult? = null
    private var runningFuture: Future<*>? = null

    @Synchronized
    fun start(): Boolean {
        if (state == State.Closed) {
            logSink.warn("VoicePipelineController start ignored: closed")
            return false
        }
        if (state == State.Running) {
            logSink.warn("VoicePipelineController start ignored: already running")
            return false
        }
        state = State.Running
        logSink.info("VoicePipelineController starting")
        runningFuture = executor.submit {
            try {
                val result = pipelineFactory().runUntilSourceEnds()
                lastResult = result
                synchronized(this) {
                    if (state == State.Running) state = State.Completed
                }
                logSink.info("VoicePipelineController result intent=${result.intentName} json=${result.unityJson}")
            } catch (throwable: Throwable) {
                synchronized(this) {
                    if (state == State.Running) state = State.Failed
                }
                logSink.error("VoicePipelineController failed", throwable)
            }
        }
        return true
    }

    fun currentState(): State = state

    fun lastResult(): VoicePipelineResult? = lastResult

    @Synchronized
    fun stop() {
        if (state == State.Closed || state == State.Stopped) return
        runningFuture?.cancel(true)
        runningFuture = null
        state = State.Stopped
        logSink.info("VoicePipelineController stopped")
    }

    @Synchronized
    override fun close() {
        stop()
        state = State.Closed
        executor.shutdownNow()
    }
}
