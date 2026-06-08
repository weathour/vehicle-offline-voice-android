package com.company.vehiclevoice.core

import com.company.vehiclevoice.action.RecordingUnityEventSink
import com.company.vehiclevoice.action.UnityActionJsonEncoder
import com.company.vehiclevoice.action.UnityActionMapper
import com.company.vehiclevoice.asr.VirtualPcmCommandAsrEngine
import com.company.vehiclevoice.audio.VirtualTtsPcmSource
import com.company.vehiclevoice.data.MockRedisStore
import com.company.vehiclevoice.data.VehicleStateProjector
import com.company.vehiclevoice.kws.VirtualPcmKeywordSpotter
import com.company.vehiclevoice.log.RecordingEventLogSink
import com.company.vehiclevoice.nlu.RuleIntentParser
import com.company.vehiclevoice.template.ReplyTemplateEngine
import com.company.vehiclevoice.tts.MockTtsEngine
import com.company.vehiclevoice.vad.EnergyVadEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCoreLoopVirtualE2eTest {
    @Test
    fun virtualMicLoop_coversInitialCommandSetThroughFullPipeline() {
        val cases = listOf(
            "打开空调" to "air_conditioner_on",
            "关闭空调" to "air_conditioner_off",
            "打开车窗" to "window_open",
            "关闭车窗" to "window_close",
            "调高温度" to "temperature_up",
            "调低温度" to "temperature_down"
        )

        cases.forEach { (command, expectedIntent) ->
            val store = MockRedisStore()
            val tts = MockTtsEngine()
            val unitySink = RecordingUnityEventSink()
            val logSink = RecordingEventLogSink()
            val pipeline = VoicePipeline(
                audioSource = VirtualTtsPcmSource.singleCommand(command),
                keywordSpotter = VirtualPcmKeywordSpotter(),
                vadEngine = EnergyVadEngine(),
                asrEngine = VirtualPcmCommandAsrEngine(),
                intentParser = RuleIntentParser(),
                stateStore = store,
                stateProjector = VehicleStateProjector(),
                replyTemplateEngine = ReplyTemplateEngine(),
                ttsEngine = tts,
                unityActionMapper = UnityActionMapper(clockMs = { 42L }),
                unityActionJsonEncoder = UnityActionJsonEncoder(),
                unityEventSink = unitySink,
                logSink = logSink
            )

            val result = pipeline.run(VoicePipelineRunConfig(maxFrames = 80, rmsLogEveryFrames = 5))

            assertTrue(command, result.wakeDetected)
            assertEquals(command, result.asrText)
            assertEquals(command, expectedIntent, result.intentName)
            assertNotNull(command, result.unityJson)
            assertEquals(command, 1, unitySink.events().size)
            assertEquals(command, 1, tts.spokenTexts().size)
            assertTrue(command, store.snapshot().containsKey("last_intent"))
            assertTrue(command, logSink.lines().any { it.contains("Audio frame=") })
        }
    }
}
