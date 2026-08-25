package com.company.vehiclevoice.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AndroidTtsEngine(context: Context) : TtsEngine, AutoCloseable {
    private val ready = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val initialized = CountDownLatch(1)
    private val pendingUtterances = ConcurrentHashMap<String, PendingUtterance>()
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready.set(status == TextToSpeech.SUCCESS)
        initialized.countDown()
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                pendingUtterances[utteranceId]?.done?.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                pendingUtterances[utteranceId]?.let {
                    it.failed.set(true)
                    it.done.countDown()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                pendingUtterances[utteranceId]?.let {
                    it.failed.set(true)
                    it.done.countDown()
                }
            }
        })
    }

    @Synchronized
    override fun speak(text: String) {
        check(!closed.get()) { "Android TTS is closed" }
        val initCompleted = initialized.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        check(initCompleted && ready.get()) { "Android TTS is not initialized" }
        val speechText = TtsPronunciationFormatter.forSpeech(text)
        val languageResult = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        check(languageResult >= TextToSpeech.LANG_AVAILABLE) { "Android TTS has no Chinese voice data" }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .build()
        )
        val utteranceId = "vehicle-voice-${UUID.randomUUID()}"
        val pending = PendingUtterance()
        pendingUtterances[utteranceId] = pending
        val result = tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            pendingUtterances.remove(utteranceId)
            throw IllegalStateException("Android TTS failed to enqueue speech")
        }
        val completed = pending.done.await(timeoutMsFor(speechText), TimeUnit.MILLISECONDS)
        pendingUtterances.remove(utteranceId)
        check(completed) { "Android TTS timed out" }
        check(!pending.failed.get()) { "Android TTS failed during speech" }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pendingUtterances.values.forEach { it.done.countDown() }
        pendingUtterances.clear()
        tts.stop()
        tts.shutdown()
    }

    private fun timeoutMsFor(text: String): Long =
        (2_000L + text.length * 500L).coerceIn(3_000L, 20_000L)

    private class PendingUtterance(
        val done: CountDownLatch = CountDownLatch(1),
        val failed: AtomicBoolean = AtomicBoolean(false)
    )

    companion object {
        private const val INIT_TIMEOUT_MS = 3_000L
    }
}
