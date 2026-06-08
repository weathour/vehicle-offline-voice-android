package com.company.vehiclevoice.tts

/**
 * Converts engineering abbreviations into speech-friendly text before Android TTS.
 *
 * UI/log text keeps canonical values such as V2I and V2X. Only spoken text is transformed.
 * For Chinese TTS engines, "突" is a reliable local approximation of the English "to" in V2X.
 */
object TtsPronunciationFormatter {
    fun forSpeech(text: String): String {
        return text.replace(V2X_REGEX) { match ->
            val target = match.groupValues[1].uppercase()
            "V突$target"
        }
    }

    private val V2X_REGEX = Regex("(?i)V2([VIX])")
}
