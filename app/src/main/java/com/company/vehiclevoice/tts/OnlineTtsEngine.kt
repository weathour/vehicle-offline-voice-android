package com.company.vehiclevoice.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.company.vehiclevoice.log.EventLogSink
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OnlineTtsEngine(
    context: Context,
    private val client: OnlineTtsClient
) : TtsEngine {
    private val player = CloudAudioPlayer(context)

    @Synchronized
    override fun speak(text: String) {
        if (text.isBlank()) return
        splitOnlineTtsText(TtsPronunciationFormatter.forSpeech(text)).forEach { chunk ->
            player.play(client.synthesize(chunk))
        }
    }
}

internal fun splitOnlineTtsText(text: String, maxChars: Int = 140): List<String> {
    require(maxChars > 0)
    if (text.length <= maxChars) return listOf(text)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = (start + maxChars).coerceAtMost(text.length)
        if (end < text.length) {
            val boundary = (end - 1 downTo start + maxChars / 2).firstOrNull {
                text[it] in "。！？；，,.!?; \n"
            }
            if (boundary != null) end = boundary + 1
        }
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}

fun createConfiguredTtsEngine(
    context: Context,
    provider: TtsProvider,
    logSink: EventLogSink
): TtsEngine {
    val prompts = FixedPromptPlayer(context)
    val config = runCatching { TtsConfigStore(context).load() }
        .onFailure { logSink.warn("TTS secure config unavailable: ${it.message}") }
        .getOrDefault(OnlineTtsConfig())

    val primaryFactory: () -> TtsEngine = when (provider) {
        TtsProvider.System -> ({ AndroidTtsEngine(context) })
        else -> ({
            check(config.isConfigured(provider)) { "${provider.displayName}尚未配置" }
            OnlineTtsEngine(context, createOnlineTtsClient(provider, config))
        })
    }
    return FallbackTtsEngine(
        primaryName = provider.wireValue,
        primaryFactory = primaryFactory,
        fallbackName = "system",
        fallbackFactory = if (provider == TtsProvider.System) null else ({ AndroidTtsEngine(context) }),
        playFixedPrompt = prompts::playIfKnown,
        playUnavailablePrompt = prompts::playUnavailable,
        logSink = logSink
    )
}

private class CloudAudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val attributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .build()

    fun play(audio: TtsAudio) {
        val file = File(appContext.cacheDir, "online-tts-${UUID.randomUUID()}${audio.fileSuffix}")
        file.outputStream().use { it.write(audio.bytes) }
        val mediaPlayer = MediaPlayer()
        val done = CountDownLatch(1)
        val failed = AtomicBoolean(false)
        mediaPlayer.setAudioAttributes(attributes)
        mediaPlayer.setVolume(1f, 1f)
        mediaPlayer.setOnCompletionListener { done.countDown() }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            failed.set(true)
            done.countDown()
            true
        }
        try {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            val timeoutMs = (mediaPlayer.duration.toLong() + PLAYBACK_MARGIN_MS)
                .coerceIn(MIN_PLAYBACK_TIMEOUT_MS, MAX_PLAYBACK_TIMEOUT_MS)
            check(done.await(timeoutMs, TimeUnit.MILLISECONDS)) { "在线语音播放超时" }
            check(!failed.get()) { "在线语音播放失败" }
        } finally {
            runCatching { if (mediaPlayer.isPlaying) mediaPlayer.stop() }
            mediaPlayer.release()
            file.delete()
        }
    }

    companion object {
        private const val PLAYBACK_MARGIN_MS = 3_000L
        private const val MIN_PLAYBACK_TIMEOUT_MS = 5_000L
        private const val MAX_PLAYBACK_TIMEOUT_MS = 60_000L
    }
}
