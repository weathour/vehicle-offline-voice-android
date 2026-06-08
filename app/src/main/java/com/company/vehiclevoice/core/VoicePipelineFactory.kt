package com.company.vehiclevoice.core

import com.company.vehiclevoice.action.RecordingUnityEventSink
import com.company.vehiclevoice.action.UnityActionJsonEncoder
import com.company.vehiclevoice.action.UnityActionMapper
import com.company.vehiclevoice.asr.ScriptedAsrEngine
import com.company.vehiclevoice.audio.FakePcmSource
import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.data.MockRedisStore
import com.company.vehiclevoice.data.VehicleStateProjector
import com.company.vehiclevoice.kws.ScriptedKeywordSpotter
import com.company.vehiclevoice.log.EventLogSink
import com.company.vehiclevoice.nlu.RuleIntentParser
import com.company.vehiclevoice.template.ReplyTemplateEngine
import com.company.vehiclevoice.tts.MockTtsEngine
import com.company.vehiclevoice.vad.EnergyVadEngine

object VoicePipelineFactory {
    fun createServicePreviewPipeline(logSink: EventLogSink): VoicePipeline {
        val frames = buildList {
            add(PcmFrame.silence(sequence = 0))
            add(PcmFrame.silence(sequence = 1))
            add(PcmFrame.silence(sequence = 2))
            add(PcmFrame.constantTone(sequence = 3, amplitude = 10_000))
            add(PcmFrame.constantTone(sequence = 4, amplitude = 10_000))
            add(PcmFrame.constantTone(sequence = 5, amplitude = 10_000))
            add(PcmFrame.silence(sequence = 6))
            add(PcmFrame.silence(sequence = 7))
            add(PcmFrame.silence(sequence = 8))
        }
        return VoicePipeline(
            audioSource = FakePcmSource(frames),
            keywordSpotter = ScriptedKeywordSpotter(wakeSequences = setOf(2L)),
            vadEngine = EnergyVadEngine(),
            asrEngine = ScriptedAsrEngine.single("打开空调"),
            intentParser = RuleIntentParser(),
            stateStore = MockRedisStore(),
            stateProjector = VehicleStateProjector(),
            replyTemplateEngine = ReplyTemplateEngine(),
            ttsEngine = MockTtsEngine(),
            unityActionMapper = UnityActionMapper(clockMs = { 1_234_567_890L }),
            unityActionJsonEncoder = UnityActionJsonEncoder(),
            unityEventSink = RecordingUnityEventSink(),
            logSink = logSink
        )
    }
}
