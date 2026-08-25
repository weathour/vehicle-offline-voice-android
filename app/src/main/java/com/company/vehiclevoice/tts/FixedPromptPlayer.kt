package com.company.vehiclevoice.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.company.vehiclevoice.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class FixedPromptPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val attributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .build()

    fun playIfKnown(text: String): Boolean {
        if (text.trim().trimEnd('。', '！', '!') != WAKE_ACK_TEXT) return false
        play(R.raw.voice_wake_ack)
        return true
    }

    fun playUnavailable() = play(R.raw.voice_tts_unavailable)

    @Synchronized
    private fun play(resourceId: Int) {
        val player = MediaPlayer.create(
            appContext,
            resourceId,
            attributes,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ) ?: error("Fixed prompt failed to initialize")
        val done = CountDownLatch(1)
        val failed = AtomicBoolean(false)
        player.setOnCompletionListener { done.countDown() }
        player.setOnErrorListener { _, _, _ ->
            failed.set(true)
            done.countDown()
            true
        }
        try {
            player.start()
            val completed = done.await(
                (player.duration.toLong() + PROMPT_TIMEOUT_MARGIN_MS).coerceAtLeast(MIN_PROMPT_TIMEOUT_MS),
                TimeUnit.MILLISECONDS
            )
            check(completed) { "Fixed prompt playback timed out" }
            check(!failed.get()) { "Fixed prompt playback failed" }
        } finally {
            runCatching { if (player.isPlaying) player.stop() }
            player.release()
        }
    }

    companion object {
        private const val WAKE_ACK_TEXT = "我在"
        private const val PROMPT_TIMEOUT_MARGIN_MS = 2_000L
        private const val MIN_PROMPT_TIMEOUT_MS = 2_000L
    }
}
