package com.company.vehiclevoice.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class MockTtsEngineTest {
    @Test
    fun recordsSpokenTexts() {
        val tts = MockTtsEngine()
        tts.speak("已打开空调")
        assertEquals(listOf("已打开空调"), tts.spokenTexts())
    }
}
