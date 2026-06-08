package com.company.vehiclevoice.tts

import com.company.vehiclevoice.action.RecordingUnityEventSink
import com.company.vehiclevoice.action.UnityActionJsonEncoder
import com.company.vehiclevoice.action.UnityActionMapper
import com.company.vehiclevoice.asr.ScriptedAsrEngine
import com.company.vehiclevoice.audio.FakePcmSource
import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.core.VoicePipeline
import com.company.vehiclevoice.data.MockRedisStore
import com.company.vehiclevoice.data.VehicleStateProjector
import com.company.vehiclevoice.kws.ScriptedKeywordSpotter
import com.company.vehiclevoice.log.RecordingEventLogSink
import com.company.vehiclevoice.nlu.RuleIntentParser
import com.company.vehiclevoice.template.ReplyTemplateEngine
import com.company.vehiclevoice.vad.EnergyVadEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsFailurePipelineTest {
    @Test
    fun ttsFailureDoesNotBlockStateOrUnityAction() {
        val store = MockRedisStore()
        val unitySink = RecordingUnityEventSink()
        val logSink = RecordingEventLogSink()
        val pipeline = VoicePipeline(
            audioSource = FakePcmSource(listOf(
                PcmFrame.silence(0), PcmFrame.silence(1),
                PcmFrame.constantTone(2, 10_000), PcmFrame.constantTone(3, 10_000),
                PcmFrame.silence(4), PcmFrame.silence(5)
            )),
            keywordSpotter = ScriptedKeywordSpotter(setOf(1L)),
            vadEngine = EnergyVadEngine(),
            asrEngine = ScriptedAsrEngine.single("打开空调"),
            intentParser = RuleIntentParser(),
            stateStore = store,
            stateProjector = VehicleStateProjector(),
            replyTemplateEngine = ReplyTemplateEngine(),
            ttsEngine = object : TtsEngine { override fun speak(text: String) { error("boom") } },
            unityActionMapper = UnityActionMapper(clockMs = { 1L }),
            unityActionJsonEncoder = UnityActionJsonEncoder(),
            unityEventSink = unitySink,
            logSink = logSink
        )

        val result = pipeline.runUntilSourceEnds()

        assertEquals("on", store.get("air_conditioner"))
        assertNotNull(result.unityJson)
        assertEquals(1, unitySink.events().size)
        assertTrue(logSink.lines().any { it.contains("TTS failed") })
    }
}
