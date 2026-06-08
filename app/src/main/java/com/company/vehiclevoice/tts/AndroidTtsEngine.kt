package com.company.vehiclevoice.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class AndroidTtsEngine(context: Context) : TtsEngine, AutoCloseable {
    private val ready = AtomicBoolean(false)
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready.set(status == TextToSpeech.SUCCESS)
    }

    override fun speak(text: String) {
        if (!ready.get()) {
            throw IllegalStateException("Android TTS is not initialized")
        }
        val speechText = TtsPronunciationFormatter.forSpeech(text)
        tts.language = Locale.CHINESE
        val utteranceId = "vehicle-voice-${UUID.randomUUID()}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId))
        }
    }

    override fun close() {
        tts.shutdown()
    }
}
