package com.company.vehiclevoice.core

import com.company.vehiclevoice.action.UnityActionJsonEncoder
import com.company.vehiclevoice.action.UnityActionMapper
import com.company.vehiclevoice.action.UnityEventSink
import com.company.vehiclevoice.asr.AsrEngine
import com.company.vehiclevoice.audio.AudioSource
import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.data.VehicleStateProjector
import com.company.vehiclevoice.data.VehicleStateStore
import com.company.vehiclevoice.kws.KeywordEvent
import com.company.vehiclevoice.kws.KeywordSpotter
import com.company.vehiclevoice.log.EventLogSink
import com.company.vehiclevoice.nlu.IntentParser
import com.company.vehiclevoice.template.ReplyTemplateEngine
import com.company.vehiclevoice.tts.TtsEngine
import com.company.vehiclevoice.vad.VadEngine
import com.company.vehiclevoice.vad.VadEvent

data class VoicePipelineResult(
    val wakeDetected: Boolean,
    val asrText: String?,
    val intentName: String?,
    val reply: String?,
    val unityJson: String?,
    val stateSnapshot: Map<String, String>,
    val framesRead: Int,
    val utterancesHandled: Int = if (asrText != null) 1 else 0
)

data class VoicePipelineRunConfig(
    val maxFrames: Int = 2_000,
    val maxUtterances: Int = 1,
    val continueAfterUtterance: Boolean = false,
    val rmsLogEveryFrames: Int = 0,
    val wakeTimeoutFrames: Int = 0,
    val maxUtteranceFrames: Int = 300
) {
    init {
        require(maxFrames > 0) { "maxFrames must be positive" }
        require(maxUtterances > 0) { "maxUtterances must be positive" }
        require(rmsLogEveryFrames >= 0) { "rmsLogEveryFrames must be non-negative" }
        require(wakeTimeoutFrames >= 0) { "wakeTimeoutFrames must be non-negative" }
        require(maxUtteranceFrames > 0) { "maxUtteranceFrames must be positive" }
    }
}

class VoicePipeline(
    private val audioSource: AudioSource,
    private val keywordSpotter: KeywordSpotter,
    private val vadEngine: VadEngine,
    private val asrEngine: AsrEngine,
    private val intentParser: IntentParser,
    private val stateStore: VehicleStateStore,
    private val stateProjector: VehicleStateProjector,
    private val replyTemplateEngine: ReplyTemplateEngine,
    private val ttsEngine: TtsEngine,
    private val unityActionMapper: UnityActionMapper,
    private val unityActionJsonEncoder: UnityActionJsonEncoder,
    private val unityEventSink: UnityEventSink,
    private val logSink: EventLogSink
) : AutoCloseable {
    fun runUntilSourceEnds(maxFrames: Int = 2_000): VoicePipelineResult = run(
        VoicePipelineRunConfig(maxFrames = maxFrames)
    )

    fun run(config: VoicePipelineRunConfig): VoicePipelineResult {
        var framesRead = 0
        var wakeDetected = false
        var asrText: String? = null
        var intentName: String? = null
        var reply: String? = null
        var unityJson: String? = null
        var utterancesHandled = 0
        val utteranceFrames = mutableListOf<PcmFrame>()
        var framesSinceWake = 0

        logSink.info("Pipeline state=listening_start maxFrames=${config.maxFrames} maxUtterances=${config.maxUtterances}")
        audioSource.start()
        try {
            while (framesRead < config.maxFrames && utterancesHandled < config.maxUtterances) {
                if (Thread.currentThread().isInterrupted) {
                    logSink.warn("Pipeline state=interrupted frames=$framesRead")
                    break
                }
                val frame = audioSource.read() ?: break
                framesRead += 1
                if (config.rmsLogEveryFrames > 0 && framesRead % config.rmsLogEveryFrames == 0) {
                    logSink.info("Audio frame=${frame.sequence} rms=${frame.normalizedRms()}")
                }

                if (!wakeDetected) {
                    when (val event = keywordSpotter.accept(frame)) {
                        KeywordEvent.None -> Unit
                        is KeywordEvent.Wake -> {
                            wakeDetected = true
                            vadEngine.reset()
                            logSink.info("Pipeline state=wake_detected frame=${event.frameSequence}")
                            framesSinceWake = 0
                            logSink.info("KWS wake keyword=${event.keyword} confidence=${event.confidence} frame=${event.frameSequence}")
                        }
                    }
                    continue
                }

                framesSinceWake += 1
                if (config.wakeTimeoutFrames > 0 && utteranceFrames.isEmpty() && framesSinceWake > config.wakeTimeoutFrames) {
                    logSink.warn("Pipeline state=wake_timeout framesSinceWake=$framesSinceWake")
                    wakeDetected = false
                    keywordSpotter.reset()
                    vadEngine.reset()
                    framesSinceWake = 0
                    continue
                }

                when (val vad = vadEngine.accept(frame)) {
                    is VadEvent.Silence -> Unit
                    is VadEvent.SpeechStart -> {
                        utteranceFrames += vad.preRollFrames.ifEmpty { listOf(vad.frame) }
                        logSink.info("Pipeline state=recording_utterance startFrame=${vad.frame.sequence}")
                        logSink.info("VAD speech_start rms=${vad.rms}")
                    }
                    is VadEvent.Speech -> {
                        utteranceFrames += vad.frame
                        if (utteranceFrames.size >= config.maxUtteranceFrames) {
                            logSink.warn("Pipeline state=utterance_max_frames frames=${utteranceFrames.size}")
                            logSink.info("Pipeline state=recognizing frames=${utteranceFrames.size}")
                            val result = handleUtterance(utteranceFrames)
                            utterancesHandled += 1
                            asrText = result.asrText
                            intentName = result.intentName
                            reply = result.reply
                            unityJson = result.unityJson
                            utteranceFrames.clear()
                            if (config.continueAfterUtterance && utterancesHandled < config.maxUtterances) {
                                wakeDetected = false
                                framesSinceWake = 0
                                keywordSpotter.reset()
                                vadEngine.reset()
                                logSink.info("Pipeline state=listening_resume utterances=$utterancesHandled")
                            } else {
                                break
                            }
                        }
                    }
                    is VadEvent.SpeechEnd -> {
                        logSink.info("VAD speech_end rms=${vad.rms} frames=${utteranceFrames.size}")
                        logSink.info("Pipeline state=recognizing frames=${utteranceFrames.size}")
                        val result = handleUtterance(utteranceFrames)
                        utterancesHandled += 1
                        asrText = result.asrText
                        intentName = result.intentName
                        reply = result.reply
                        unityJson = result.unityJson
                        utteranceFrames.clear()
                        if (config.continueAfterUtterance && utterancesHandled < config.maxUtterances) {
                            wakeDetected = false
                            framesSinceWake = 0
                            keywordSpotter.reset()
                            vadEngine.reset()
                            logSink.info("Pipeline state=listening_resume utterances=$utterancesHandled")
                        } else {
                            break
                        }
                    }
                }
            }
        } finally {
            audioSource.stop()
            logSink.info("Pipeline state=audio_released frames=$framesRead utterances=$utterancesHandled")
        }

        return VoicePipelineResult(
            wakeDetected = wakeDetected,
            asrText = asrText,
            intentName = intentName,
            reply = reply,
            unityJson = unityJson,
            stateSnapshot = stateStore.snapshot(),
            framesRead = framesRead,
            utterancesHandled = utterancesHandled
        )
    }

    override fun close() {
        closeIfNeeded(audioSource)
        closeIfNeeded(keywordSpotter)
        closeIfNeeded(asrEngine)
        closeIfNeeded(ttsEngine)
        closeIfNeeded(unityEventSink)
    }

    private fun closeIfNeeded(value: Any) {
        if (value is AutoCloseable) {
            runCatching { value.close() }.onFailure { throwable ->
                logSink.warn("Resource close failed: ${throwable.message}")
            }
        }
    }

    private fun handleUtterance(frames: List<PcmFrame>): VoicePipelineResult {
        val asr = asrEngine.recognize(frames)
        logSink.info("ASR text=${asr.text} confidence=${asr.confidence}")
        val parse = intentParser.parse(asr.text)
        logSink.info("NLU intent=${parse.intent.name} reason=${parse.reason}")
        val mutated = stateProjector.apply(parse, stateStore)
        if (!mutated) logSink.info("State not mutated for intent=${parse.intent.name}")
        val reply = replyTemplateEngine.render(parse, stateStore)
        try {
            ttsEngine.speak(reply)
            logSink.info("TTS reply=$reply")
        } catch (throwable: Throwable) {
            logSink.warn("TTS failed but action pipeline continues: ${throwable.message}")
        }
        val unityJson = unityActionMapper.map(parse)?.let { action ->
            unityActionJsonEncoder.encode(action).also { json ->
                unityEventSink.send(json)
                logSink.info("Unity action json=$json")
            }
        }
        return VoicePipelineResult(
            wakeDetected = true,
            asrText = asr.text,
            intentName = parse.intent.name,
            reply = reply,
            unityJson = unityJson,
            stateSnapshot = stateStore.snapshot(),
            framesRead = frames.size,
            utterancesHandled = 1
        )
    }
}
