package com.company.vehiclevoice.kws

import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.audio.VirtualTtsPcmSource
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualPcmKeywordSpotterTest {
    @Test
    fun detectsWakePhraseFromRenderedPcmFramesWithoutScriptedSequence() {
        val spotter = VirtualPcmKeywordSpotter(wakePhrase = "你好车机")
        val frames = listOf(PcmFrame.silence(0)) + VirtualTtsPcmSource.framesForText("你好车机", startSequence = 1)
        val events = frames.map { spotter.accept(it) }
        assertTrue(events.last() is KeywordEvent.Wake)
    }

    @Test
    fun doesNotDetectDifferentRenderedText() {
        val spotter = VirtualPcmKeywordSpotter(wakePhrase = "你好车机")
        val events = VirtualTtsPcmSource.framesForText("打开空调").map { spotter.accept(it) }
        assertTrue(events.none { it is KeywordEvent.Wake })
    }
}
