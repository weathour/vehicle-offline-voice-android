package com.company.vehiclevoice.tts

import android.content.Context
import android.os.Build
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
    private val pendingUtterances = ConcurrentHashMap<String, CountDownLatch>()
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready.set(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                pendingUtterances.remove(utteranceId)?.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                pendingUtterances.remove(utteranceId)?.countDown()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                pendingUtterances.remove(utteranceId)?.countDown()
            }
        })
    }

    override fun speak(text: String) {
        if (!ready.get()) {
            throw IllegalStateException("Android TTS is not initialized")
        }
        val speechText = TtsPronunciationFormatter.forSpeech(text)
        tts.language = Locale.CHINESE
        val utteranceId = "vehicle-voice-${UUID.randomUUID()}"
        val done = CountDownLatch(1)
        pendingUtterances[utteranceId] = done
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId))
        }
        if (result != TextToSpeech.SUCCESS) {
            pendingUtterances.remove(utteranceId)
            throw IllegalStateException("Android TTS failed to enqueue speech")
        }
        val completed = done.await(timeoutMsFor(speechText), TimeUnit.MILLISECONDS)
        pendingUtterances.remove(utteranceId)
        if (!completed) {
            throw IllegalStateException("Android TTS timed out")
        }
    }

    override fun close() {
        pendingUtterances.values.forEach { it.countDown() }
        pendingUtterances.clear()
        tts.shutdown()
    }

    private fun timeoutMsFor(text: String): Long =
        (1_500L + text.length * 450L).coerceIn(2_000L, 10_000L)
}
