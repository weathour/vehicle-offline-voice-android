package com.company.vehiclevoice.kws

import com.company.vehiclevoice.audio.PcmFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KwsModuleTest {
    @Test
    fun scriptedKeywordSpotter_triggersOnlyConfiguredFrame() {
        val kws = ScriptedKeywordSpotter(wakeSequences = setOf(3L), keyword = "小车小车")
        assertTrue(kws.accept(PcmFrame.silence(sequence = 2)) is KeywordEvent.None)
        val event = kws.accept(PcmFrame.silence(sequence = 3))
        assertTrue(event is KeywordEvent.Wake)
        assertEquals("小车小车", (event as KeywordEvent.Wake).keyword)
    }
}
