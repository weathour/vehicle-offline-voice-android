package com.company.vehiclevoice.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

class SherpaOfflineTtsEngine(
    context: Context,
    private val speakerId: Int = DEFAULT_SPEAKER_ID,
    private val speed: Float = DEFAULT_SPEED,
    numThreads: Int = DEFAULT_NUM_THREADS
) : TtsEngine, AutoCloseable {
    private val tts = OfflineTts(
        assetManager = context.applicationContext.assets,
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$MODEL_DIR/model.onnx",
                    lexicon = "$MODEL_DIR/lexicon.txt",
                    tokens = "$MODEL_DIR/tokens.txt"
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu"
            ),
            ruleFsts = listOf("phone.fst", "date.fst", "number.fst")
                .joinToString(",") { "$MODEL_DIR/$it" }
        )
    )
    private var closed = false

    init {
        require(numThreads > 0) { "TTS numThreads must be positive" }
        require(speed > 0f) { "TTS speed must be positive" }
        require(speakerId in 0 until tts.numSpeakers()) {
            "TTS speakerId $speakerId is outside 0 until ${tts.numSpeakers()}"
        }
    }

    @Synchronized
    override fun speak(text: String) {
        check(!closed) { "Embedded TTS is closed" }
        val speechText = TtsPronunciationFormatter.forSpeech(text).trim()
        if (speechText.isEmpty()) return

        val audio = tts.generate(speechText, sid = speakerId, speed = speed)
        check(audio.samples.isNotEmpty()) { "Embedded TTS generated no audio" }
        check(audio.sampleRate > 0) { "Embedded TTS returned an invalid sample rate" }
        play(audio.samples, audio.sampleRate)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        tts.release()
    }

    private fun play(samples: FloatArray, sampleRate: Int) {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .build()
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        check(minBufferBytes > 0) { "AudioTrack does not support sample rate $sampleRate" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBufferBytes, samples.size * Float.SIZE_BYTES))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) {
            track.release()
            "AudioTrack failed to initialize"
        }

        try {
            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            check(written == samples.size) { "AudioTrack wrote $written of ${samples.size} samples" }
            track.play()
            val durationMs = samples.size * 1_000L / sampleRate
            val deadlineMs = SystemClock.elapsedRealtime() + durationMs + PLAYBACK_TAIL_TIMEOUT_MS
            while (track.playbackHeadPosition < samples.size) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Embedded TTS interrupted")
                check(SystemClock.elapsedRealtime() < deadlineMs) { "Embedded TTS playback timed out" }
                Thread.sleep(PLAYBACK_POLL_MS)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    companion object {
        const val MODEL_ID = "vits-icefall-zh-aishell3"
        const val ENGINE_VERSION = "1.13.6"
        const val DEFAULT_SPEAKER_ID = 66
        const val DEFAULT_SPEED = 1.0f
        const val DEFAULT_NUM_THREADS = 2

        private const val MODEL_DIR = "tts/$MODEL_ID"
        private const val PLAYBACK_POLL_MS = 20L
        private const val PLAYBACK_TAIL_TIMEOUT_MS = 2_000L
    }
}
