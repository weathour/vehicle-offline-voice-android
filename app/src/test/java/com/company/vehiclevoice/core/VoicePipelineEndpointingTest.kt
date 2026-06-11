package com.company.vehiclevoice.core

import com.company.vehiclevoice.action.RecordingUnityEventSink
import com.company.vehiclevoice.action.UnityActionJsonEncoder
import com.company.vehiclevoice.action.UnityActionMapper
import com.company.vehiclevoice.asr.AsrResult
import com.company.vehiclevoice.asr.ScriptedAsrEngine
import com.company.vehiclevoice.audio.FakePcmSource
import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.data.MockRedisStore
import com.company.vehiclevoice.data.VehicleStateProjector
import com.company.vehiclevoice.kws.ScriptedKeywordSpotter
import com.company.vehiclevoice.log.RecordingEventLogSink
import com.company.vehiclevoice.nlu.RuleIntentParser
import com.company.vehiclevoice.template.ReplyTemplateEngine
import com.company.vehiclevoice.tts.MockTtsEngine
import com.company.vehiclevoice.vad.EnergyVadEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePipelineEndpointingTest {
    @Test
    fun maxUtteranceFrames_recognizesBoundedSpeechWithoutSpeechEnd() {
        val logSink = RecordingEventLogSink()
        val frames = buildList {
            add(PcmFrame.silence(0))
            add(PcmFrame.silence(1))
            repeat(10) { add(PcmFrame.constantTone((it + 2).toLong(), 10_000)) }
        }
        val result = pipeline(frames, logSink).run(
            VoicePipelineRunConfig(maxFrames = 20, maxUtteranceFrames = 3)
        )

        assertEquals(1, result.utterancesHandled)
        assertEquals("air_conditioner_on", result.intentName)
        assertTrue(logSink.lines().any { it.contains("utterance_max_frames") })
    }

    @Test
    fun wakeTimeout_resetsWakeWhenNoSpeechArrives() {
        val logSink = RecordingEventLogSink()
        val frames = List(8) { PcmFrame.silence(it.toLong()) }
        val result = pipeline(frames, logSink).run(
            VoicePipelineRunConfig(maxFrames = 8, wakeTimeoutFrames = 2)
        )

        assertEquals(0, result.utterancesHandled)
        assertTrue(logSink.lines().any { it.contains("wake_timeout") })
    }

    @Test
    fun realMicMode_keepsLongCommandWindowButShortPostAckTailGuard() {
        val config = VoicePipelineFactory.runConfigForMode(VoiceRuntimeMode.RealMicManual)

        assertEquals(300, config.wakeTimeoutFrames)
        assertEquals(300, config.maxUtteranceFrames)
        assertEquals(8, config.postWakeAcknowledgementFrames)
    }

    private fun pipeline(frames: List<PcmFrame>, logSink: RecordingEventLogSink): VoicePipeline = VoicePipeline(
        audioSource = FakePcmSource(frames),
        keywordSpotter = ScriptedKeywordSpotter(wakeSequences = setOf(1L)),
        vadEngine = EnergyVadEngine(),
        asrEngine = ScriptedAsrEngine(listOf(AsrResult("打开空调"))),
        intentParser = RuleIntentParser(),
        stateStore = MockRedisStore(),
        stateProjector = VehicleStateProjector(),
        replyTemplateEngine = ReplyTemplateEngine(),
        ttsEngine = MockTtsEngine(),
        unityActionMapper = UnityActionMapper(clockMs = { 123L }),
        unityActionJsonEncoder = UnityActionJsonEncoder(),
        unityEventSink = RecordingUnityEventSink(),
        logSink = logSink
    )
}
