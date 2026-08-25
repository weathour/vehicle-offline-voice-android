package com.company.vehiclevoice.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.company.vehiclevoice.copyAssetTree
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class SherpaOfflineTtsEngine(
    context: Context,
    private val speakerId: Int = DEFAULT_SPEAKER_ID,
    private val speed: Float = DEFAULT_SPEED,
    numThreads: Int = DEFAULT_NUM_THREADS
) : TtsEngine, AutoCloseable {
    private val appContext = context.applicationContext
    private val espeakDataDir = ensureEspeakDataDir(appContext)
    private val tts = OfflineTts(
        assetManager = appContext.assets,
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = "$MODEL_DIR/model.int8.onnx",
                    voices = "$MODEL_DIR/voices.bin",
                    tokens = "$MODEL_DIR/tokens.txt",
                    dataDir = espeakDataDir.absolutePath,
                    lexicon = "$MODEL_DIR/lexicon-us-en.txt,$MODEL_DIR/lexicon-zh.txt"
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu"
            ),
            ruleFsts = listOf("phone-zh.fst", "date-zh.fst", "number-zh.fst")
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
        play(normalizeTtsSamples(audio.samples), audio.sampleRate)
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
        const val MODEL_ID = "kokoro-int8-multi-lang-v1_1"
        const val ENGINE_VERSION = "1.13.6"
        const val DEFAULT_SPEAKER_ID = 3
        const val DEFAULT_SPEED = 1.0f
        const val DEFAULT_NUM_THREADS = 4

        private const val MODEL_DIR = "tts/$MODEL_ID"
        private const val PLAYBACK_POLL_MS = 20L
        private const val PLAYBACK_TAIL_TIMEOUT_MS = 2_000L

        @Synchronized
        private fun ensureEspeakDataDir(context: Context): File {
            val target = File(context.filesDir, "$MODEL_DIR/espeak-ng-data")
            val marker = File(target.parentFile, "espeak-ng-data.complete")
            val requiredFiles = listOf("phondata", "phonindex", "phontab", "cmn_dict", "en_dict")
            if (runCatching { marker.readText() }.getOrNull() == ENGINE_VERSION &&
                requiredFiles.all { File(target, it).isFile }
            ) return target
            copyAssetTree(context, "$MODEL_DIR/espeak-ng-data", target)
            check(requiredFiles.all { File(target, it).isFile }) {
                "Embedded TTS eSpeak data copy is incomplete"
            }
            marker.writeText(ENGINE_VERSION)
            return target
        }
    }
}

internal fun normalizeTtsSamples(samples: FloatArray): FloatArray {
    if (samples.isEmpty()) return samples
    var peak = 0f
    var sumSquares = 0.0
    samples.forEach { sample ->
        check(sample.isFinite()) { "Embedded TTS generated a non-finite sample" }
        peak = maxOf(peak, abs(sample))
        sumSquares += sample * sample
    }
    val rms = sqrt(sumSquares / samples.size).toFloat()
    if (rms < 0.001f || peak == 0f) return samples
    val gain = minOf(MAX_VOLUME_GAIN, TARGET_RMS / rms, TARGET_PEAK / peak)
    samples.indices.forEach { index ->
        samples[index] = (samples[index] * gain).coerceIn(-TARGET_PEAK, TARGET_PEAK)
    }
    return samples
}

private const val TARGET_RMS = 0.14f
private const val TARGET_PEAK = 0.92f
private const val MAX_VOLUME_GAIN = 3.0f
