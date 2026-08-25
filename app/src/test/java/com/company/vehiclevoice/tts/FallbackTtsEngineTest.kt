package com.company.vehiclevoice.tts

import com.company.vehiclevoice.log.RecordingEventLogSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackTtsEngineTest {
    @Test
    fun fixedPromptBypassesBothBackends() {
        var embeddedCreated = 0
        var systemCreated = 0
        val engine = FallbackTtsEngine(
            embeddedFactory = { embeddedCreated += 1; RecordingTtsEngine() },
            systemFactory = { systemCreated += 1; RecordingTtsEngine() },
            playFixedPrompt = { it == "我在" },
            logSink = RecordingEventLogSink()
        )

        engine.speak("我在")

        assertEquals(0, embeddedCreated)
        assertEquals(0, systemCreated)
    }

    @Test
    fun failedEmbeddedIsDisabledAndSystemHandlesLaterSpeech() {
        val system = RecordingTtsEngine()
        var embeddedCreated = 0
        val logSink = RecordingEventLogSink()
        val engine = FallbackTtsEngine(
            embeddedFactory = {
                embeddedCreated += 1
                RecordingTtsEngine(failure = IllegalStateException("bad model"))
            },
            systemFactory = { system },
            logSink = logSink
        )

        engine.speak("第一句")
        engine.speak("第二句")

        assertEquals(1, embeddedCreated)
        assertEquals(listOf("第一句", "第二句"), system.spoken)
        assertTrue(logSink.lines().any { it.contains("engine=embedded status=disabled") })
        assertTrue(logSink.lines().any { it.contains("engine=system status=success") })
    }

    @Test
    fun unavailablePromptHandlesFailureOfBothBackends() {
        var unavailablePlayed = 0
        val engine = FallbackTtsEngine(
            embeddedFactory = { error("embedded missing") },
            systemFactory = { error("system missing") },
            playUnavailablePrompt = { unavailablePlayed += 1 },
            logSink = RecordingEventLogSink()
        )

        engine.speak("动态回复")

        assertEquals(1, unavailablePlayed)
    }

    @Test
    fun systemPreferenceFallsBackToEmbeddedWhenSystemFails() {
        val calls = mutableListOf<String>()
        val engine = FallbackTtsEngine(
            embeddedFactory = { calls += "embedded"; RecordingTtsEngine() },
            systemFactory = {
                calls += "system"
                RecordingTtsEngine(failure = IllegalStateException("system unavailable"))
            },
            logSink = RecordingEventLogSink(),
            preferSystem = true
        )

        engine.speak("测试")

        assertEquals(listOf("system", "embedded"), calls)
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
