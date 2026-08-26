package com.company.vehiclevoice.core

import com.company.vehiclevoice.action.RecordingUnityEventSink
import com.company.vehiclevoice.action.UnityActionJsonEncoder
import com.company.vehiclevoice.action.UnityActionMapper
import com.company.vehiclevoice.asr.AsrEngine
import com.company.vehiclevoice.asr.ScriptedAsrEngine
import com.company.vehiclevoice.asr.VirtualPcmCommandAsrEngine
import com.company.vehiclevoice.asr.VoskOfflineAsrEngine
import com.company.vehiclevoice.audio.AndroidAudioRecordSource
import com.company.vehiclevoice.audio.AudioSource
import com.company.vehiclevoice.audio.FakePcmSource
import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.audio.VirtualTtsPcmSource
import com.company.vehiclevoice.data.MockRedisStore
import com.company.vehiclevoice.data.VehicleStateProjector
import com.company.vehiclevoice.data.readonly.RedisVehicleSnapshotProvider
import com.company.vehiclevoice.data.readonly.VehicleReadOnlySnapshotProvider
import com.company.vehiclevoice.kws.ScriptedKeywordSpotter
import com.company.vehiclevoice.kws.VirtualPcmKeywordSpotter
import com.company.vehiclevoice.kws.VoskKeywordSpotter
import com.company.vehiclevoice.log.EventLogSink
import com.company.vehiclevoice.nlu.IntentParser
import com.company.vehiclevoice.nlu.RuleIntentParser
import com.company.vehiclevoice.template.ReplyTemplateEngine
import com.company.vehiclevoice.tts.MockTtsEngine
import com.company.vehiclevoice.tts.TtsEngine
import com.company.vehiclevoice.vad.EnergyVadEngine

object VoicePipelineFactory {
    fun runConfigForMode(mode: VoiceRuntimeMode): VoicePipelineRunConfig = when (mode) {
        VoiceRuntimeMode.PreviewMock -> VoicePipelineRunConfig(maxFrames = 2_000, maxUtterances = 1)
        VoiceRuntimeMode.VirtualMicSmoke -> VoicePipelineRunConfig(maxFrames = 2_000, maxUtterances = 1, rmsLogEveryFrames = 5)
        VoiceRuntimeMode.RealMicManual -> VoicePipelineRunConfig(
            maxFrames = Int.MAX_VALUE,
            maxUtterances = Int.MAX_VALUE,
            continueAfterUtterance = true,
            rmsLogEveryFrames = 25,
            wakeTimeoutFrames = 300,
            maxUtteranceFrames = 300,
            postWakeAcknowledgementFrames = 2
        )
    }

    fun createServicePipeline(
        mode: VoiceRuntimeMode,
        logSink: EventLogSink,
        realMicPermissionGranted: () -> Boolean = { false },
        voskModelPath: () -> String? = { null },
        ttsEngineFactory: () -> TtsEngine = { MockTtsEngine() },
        readOnlySnapshotProviderFactory: () -> VehicleReadOnlySnapshotProvider? = { RedisVehicleSnapshotProvider.simulated() }
    ): VoicePipeline = when (mode) {
        VoiceRuntimeMode.PreviewMock -> createServicePreviewPipeline(logSink, readOnlySnapshotProviderFactory())
        VoiceRuntimeMode.VirtualMicSmoke -> createVirtualMicSmokePipeline(logSink, ttsEngineFactory, readOnlySnapshotProviderFactory())
        VoiceRuntimeMode.RealMicManual -> createRealMicManualPipeline(logSink, realMicPermissionGranted, voskModelPath, ttsEngineFactory, readOnlySnapshotProviderFactory())
    }

    fun createServicePreviewPipeline(
        logSink: EventLogSink,
        readOnlySnapshotProvider: VehicleReadOnlySnapshotProvider? = RedisVehicleSnapshotProvider.simulated()
    ): VoicePipeline {
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
            logSink = logSink,
            readOnlySnapshotProvider = readOnlySnapshotProvider
        )
    }


    fun createVirtualMicSmokePipeline(
        logSink: EventLogSink,
        ttsEngineFactory: () -> TtsEngine = { MockTtsEngine() },
        readOnlySnapshotProvider: VehicleReadOnlySnapshotProvider? = RedisVehicleSnapshotProvider.simulated()
    ): VoicePipeline = createPipeline(
        audioSource = VirtualTtsPcmSource.singleCommand("打开空调"),
        keywordSpotter = VirtualPcmKeywordSpotter(),
        asrEngine = VirtualPcmCommandAsrEngine(),
        ttsEngine = ttsEngineFactory(),
        logSink = logSink,
        readOnlySnapshotProvider = readOnlySnapshotProvider
    )

    fun createRealMicManualPipeline(
        logSink: EventLogSink,
        realMicPermissionGranted: () -> Boolean,
        voskModelPath: () -> String?,
        ttsEngineFactory: () -> TtsEngine = { MockTtsEngine() },
        readOnlySnapshotProvider: VehicleReadOnlySnapshotProvider? = RedisVehicleSnapshotProvider.simulated()
    ): VoicePipeline {
        val modelPath = voskModelPath()
            ?: error("RealMicManual requires a packaged/copied offline Vosk model; use PreviewMock or VirtualMicSmoke for non-real modes")
        return createPipeline(
            audioSource = AndroidAudioRecordSource(permissionGranted = realMicPermissionGranted),
            keywordSpotter = VoskKeywordSpotter(modelPath = modelPath),
            asrEngine = VoskOfflineAsrEngine(
                modelPath = modelPath,
                grammar = VoskOfflineAsrEngine.SIX_QUERY_GRAMMAR
            ),
            intentParser = RuleIntentParser(allowedIntentNames = RuleIntentParser.SIX_QUERY_INTENTS),
            ttsEngine = ttsEngineFactory(),
            logSink = logSink,
            readOnlySnapshotProvider = readOnlySnapshotProvider,
            allowVehicleControlActions = false,
            wakeAcknowledgementText = "我在",
            pauseAudioDuringTts = true
        )
    }

    private fun createPipeline(
        audioSource: AudioSource,
        keywordSpotter: com.company.vehiclevoice.kws.KeywordSpotter,
        asrEngine: AsrEngine = ScriptedAsrEngine.single("打开空调"),
        intentParser: IntentParser = RuleIntentParser(),
        ttsEngine: TtsEngine = MockTtsEngine(),
        logSink: EventLogSink,
        readOnlySnapshotProvider: VehicleReadOnlySnapshotProvider? = RedisVehicleSnapshotProvider.simulated(),
        allowVehicleControlActions: Boolean = true,
        wakeAcknowledgementText: String? = null,
        pauseAudioDuringTts: Boolean = false
    ): VoicePipeline = VoicePipeline(
        audioSource = audioSource,
        keywordSpotter = keywordSpotter,
        vadEngine = EnergyVadEngine(),
        asrEngine = asrEngine,
        intentParser = intentParser,
        stateStore = MockRedisStore(),
        stateProjector = VehicleStateProjector(),
        replyTemplateEngine = ReplyTemplateEngine(),
        ttsEngine = ttsEngine,
        unityActionMapper = UnityActionMapper(clockMs = { 1_234_567_890L }),
        unityActionJsonEncoder = UnityActionJsonEncoder(),
        unityEventSink = RecordingUnityEventSink(),
        logSink = logSink,
        readOnlySnapshotProvider = readOnlySnapshotProvider,
        allowVehicleControlActions = allowVehicleControlActions,
        wakeAcknowledgementText = wakeAcknowledgementText,
        pauseAudioDuringTts = pauseAudioDuringTts
    )
}
