package com.company.vehiclevoice.tts

class MockTtsEngine : TtsEngine {
    private val utterances = mutableListOf<String>()

    override fun speak(text: String) {
        utterances += text
    }

    fun spokenTexts(): List<String> = utterances.toList()

    fun clear() {
        utterances.clear()
    }
}
