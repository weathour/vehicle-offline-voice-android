package com.company.vehiclevoice.tts

import com.company.vehiclevoice.log.EventLogSink

class FallbackTtsEngine(
    embeddedFactory: () -> TtsEngine,
    systemFactory: () -> TtsEngine,
    private val playFixedPrompt: (String) -> Boolean = { false },
    private val playUnavailablePrompt: (() -> Unit)? = null,
    private val logSink: EventLogSink
) : TtsEngine, AutoCloseable {
    private val backends = listOf(
        Backend("embedded", embeddedFactory),
        Backend("system", systemFactory)
    )
    private var closed = false

    @Synchronized
    override fun speak(text: String) {
        check(!closed) { "TTS fallback engine is closed" }
        if (text.isBlank()) return

        try {
            if (playFixedPrompt(text)) {
                logSink.info("TTS engine=fixed_prompt status=success")
                return
            }
        } catch (throwable: Throwable) {
            rethrowIfInterrupted(throwable)
            logSink.warn("TTS fixed prompt failed: ${throwable.message}")
        }

        val failures = mutableListOf<String>()
        for (backend in backends) {
            if (backend.disabled) continue
            val engine = try {
                backend.instance ?: backend.factory().also {
                    backend.instance = it
                    logSink.info("TTS engine=${backend.name} status=ready")
                }
            } catch (throwable: Throwable) {
                rethrowIfInterrupted(throwable)
                backend.disabled = true
                failures += "${backend.name} init: ${throwable.message}"
                logSink.warn("TTS engine=${backend.name} status=unavailable reason=${throwable.message}")
                continue
            }

            try {
                val startedAtMs = System.currentTimeMillis()
                engine.speak(text)
                logSink.info(
                    "TTS engine=${backend.name} status=success durationMs=${System.currentTimeMillis() - startedAtMs}"
                )
                return
            } catch (throwable: Throwable) {
                rethrowIfInterrupted(throwable)
                backend.disabled = true
                failures += "${backend.name}: ${throwable.message}"
                closeEngine(engine)
                backend.instance = null
                logSink.warn("TTS engine=${backend.name} status=disabled reason=${throwable.message}")
            }
        }

        val unavailablePrompt = playUnavailablePrompt
        if (unavailablePrompt != null) {
            try {
                unavailablePrompt()
                logSink.warn("TTS engine=fixed_unavailable status=success failures=${failures.joinToString(" | ")}")
                return
            } catch (throwable: Throwable) {
                rethrowIfInterrupted(throwable)
                failures += "fixed unavailable: ${throwable.message}"
            }
        }
        throw IllegalStateException("No TTS backend available: ${failures.joinToString(" | ")}")
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        backends.forEach { backend ->
            backend.instance?.let(::closeEngine)
            backend.instance = null
        }
    }

    private fun closeEngine(engine: TtsEngine) {
        if (engine is AutoCloseable) {
            runCatching { engine.close() }
                .onFailure { logSink.warn("TTS close failed: ${it.message}") }
        }
    }

    private fun rethrowIfInterrupted(throwable: Throwable) {
        if (throwable is InterruptedException || Thread.currentThread().isInterrupted) {
            Thread.currentThread().interrupt()
            throw InterruptedException("TTS interrupted").apply { initCause(throwable) }
        }
    }

    private class Backend(
        val name: String,
        val factory: () -> TtsEngine,
        var instance: TtsEngine? = null,
        var disabled: Boolean = false
    )
}
