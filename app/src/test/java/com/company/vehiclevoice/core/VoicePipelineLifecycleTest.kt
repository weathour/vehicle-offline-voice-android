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

class VoicePipelineLifecycleTest {
    @Test
    fun pipeline_canResumeListeningForMultipleBoundedUtterances() {
        val logSink = RecordingEventLogSink()
        val frames = listOf(
            PcmFrame.silence(0),
            PcmFrame.silence(1),
            PcmFrame.constantTone(2, 10_000),
            PcmFrame.constantTone(3, 10_000),
            PcmFrame.silence(4),
            PcmFrame.silence(5),
            PcmFrame.silence(6),
            PcmFrame.constantTone(7, 10_000),
            PcmFrame.constantTone(8, 10_000),
            PcmFrame.silence(9),
            PcmFrame.silence(10)
        )
        val pipeline = testPipeline(
            frames = frames,
            wakeSequences = setOf(1L, 6L),
            logSink = logSink
        )

        val result = pipeline.run(
            VoicePipelineRunConfig(maxFrames = 30, maxUtterances = 2, continueAfterUtterance = true)
        )

        assertEquals(2, result.utterancesHandled)
        assertTrue(logSink.lines().any { it.contains("state=listening_resume") })
        assertTrue(logSink.lines().any { it.contains("state=audio_released") })
    }

    private fun testPipeline(
        frames: List<PcmFrame>,
        wakeSequences: Set<Long>,
        logSink: RecordingEventLogSink
    ): VoicePipeline = VoicePipeline(
        audioSource = FakePcmSource(frames),
        keywordSpotter = ScriptedKeywordSpotter(wakeSequences = wakeSequences),
        vadEngine = EnergyVadEngine(),
        asrEngine = ScriptedAsrEngine(listOf(AsrResult("打开空调"), AsrResult("关闭空调"))),
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
