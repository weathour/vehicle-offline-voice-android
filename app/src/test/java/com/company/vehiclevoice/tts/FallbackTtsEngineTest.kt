package com.company.vehiclevoice.tts

import com.company.vehiclevoice.log.RecordingEventLogSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackTtsEngineTest {
    @Test
    fun fixedPromptBypassesBothBackends() {
        var embeddedCreated = 0
        var systemCreated = 0
        val engine = FallbackTtsEngine(
            primaryName = "online",
            primaryFactory = { embeddedCreated += 1; RecordingTtsEngine() },
            fallbackName = "system",
            fallbackFactory = { systemCreated += 1; RecordingTtsEngine() },
            playFixedPrompt = { it == "我在" },
            logSink = RecordingEventLogSink()
        )

        engine.speak("我在")

        assertEquals(0, embeddedCreated)
        assertEquals(0, systemCreated)
    }

    @Test
    fun failedPrimaryIsRetriedAndSystemHandlesBothUtterances() {
        val system = RecordingTtsEngine()
        var embeddedCreated = 0
        val logSink = RecordingEventLogSink()
        val engine = FallbackTtsEngine(
            primaryName = "online",
            primaryFactory = {
                embeddedCreated += 1
                RecordingTtsEngine(failure = IllegalStateException("bad model"))
            },
            fallbackName = "system",
            fallbackFactory = { system },
            logSink = logSink
        )

        engine.speak("第一句")
        engine.speak("第二句")

        assertEquals(2, embeddedCreated)
        assertEquals(listOf("第一句", "第二句"), system.spoken)
        assertTrue(logSink.lines().any { it.contains("engine=online status=failed") })
        assertTrue(logSink.lines().any { it.contains("engine=system status=success") })
    }

    @Test
    fun unavailablePromptHandlesFailureOfBothBackends() {
        var unavailablePlayed = 0
        val engine = FallbackTtsEngine(
            primaryName = "online",
            primaryFactory = { error("online missing") },
            fallbackName = "system",
            fallbackFactory = { error("system missing") },
            playUnavailablePrompt = { unavailablePlayed += 1 },
            logSink = RecordingEventLogSink()
        )

        assertThrows(IllegalStateException::class.java) { engine.speak("动态回复") }

        assertEquals(1, unavailablePlayed)
    }

    @Test
    fun configuredPrimaryRunsBeforeFallback() {
        val calls = mutableListOf<String>()
        val engine = FallbackTtsEngine(
            primaryName = "system",
            primaryFactory = {
                calls += "system"
                RecordingTtsEngine(failure = IllegalStateException("system unavailable"))
            },
            fallbackName = "online",
            fallbackFactory = { calls += "online"; RecordingTtsEngine() },
            logSink = RecordingEventLogSink()
        )

        engine.speak("测试")

        assertEquals(listOf("system", "online"), calls)
    }

    private class RecordingTtsEngine(
        private val failure: Throwable? = null
    ) : TtsEngine {
        val spoken = mutableListOf<String>()

        override fun speak(text: String) {
            failure?.let { throw it }
            spoken += text
        }
    }
}
