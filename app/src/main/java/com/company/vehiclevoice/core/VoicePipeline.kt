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
    val framesRead: Int
)

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
) {
    fun runUntilSourceEnds(maxFrames: Int = 2_000): VoicePipelineResult {
        var framesRead = 0
        var wakeDetected = false
        var asrText: String? = null
        var intentName: String? = null
        var reply: String? = null
        var unityJson: String? = null
        val utteranceFrames = mutableListOf<PcmFrame>()

        audioSource.start()
        try {
            while (framesRead < maxFrames) {
                val frame = audioSource.read() ?: break
                framesRead += 1

                if (!wakeDetected) {
                    when (val event = keywordSpotter.accept(frame)) {
                        KeywordEvent.None -> Unit
                        is KeywordEvent.Wake -> {
                            wakeDetected = true
                            vadEngine.reset()
                            logSink.info("KWS wake keyword=${event.keyword} confidence=${event.confidence} frame=${event.frameSequence}")
                        }
                    }
                    continue
                }

                when (val vad = vadEngine.accept(frame)) {
                    is VadEvent.Silence -> Unit
                    is VadEvent.SpeechStart -> {
                        utteranceFrames += vad.preRollFrames.ifEmpty { listOf(vad.frame) }
                        logSink.info("VAD speech_start rms=${vad.rms}")
                    }
                    is VadEvent.Speech -> {
                        utteranceFrames += vad.frame
                    }
                    is VadEvent.SpeechEnd -> {
                        logSink.info("VAD speech_end rms=${vad.rms} frames=${utteranceFrames.size}")
                        val result = handleUtterance(utteranceFrames)
                        asrText = result.asrText
                        intentName = result.intentName
                        reply = result.reply
                        unityJson = result.unityJson
                        break
                    }
                }
            }
        } finally {
            audioSource.stop()
        }

        return VoicePipelineResult(
            wakeDetected = wakeDetected,
            asrText = asrText,
            intentName = intentName,
            reply = reply,
            unityJson = unityJson,
            stateSnapshot = stateStore.snapshot(),
            framesRead = framesRead
        )
    }

    private fun handleUtterance(frames: List<PcmFrame>): VoicePipelineResult {
        val asr = asrEngine.recognize(frames)
        logSink.info("ASR text=${asr.text} confidence=${asr.confidence}")
        val parse = intentParser.parse(asr.text)
        logSink.info("NLU intent=${parse.intent.name} reason=${parse.reason}")
        val mutated = stateProjector.apply(parse, stateStore)
        if (!mutated) logSink.info("State not mutated for intent=${parse.intent.name}")
        val reply = replyTemplateEngine.render(parse, stateStore)
        ttsEngine.speak(reply)
        logSink.info("TTS reply=$reply")
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
            framesRead = frames.size
        )
    }
}
