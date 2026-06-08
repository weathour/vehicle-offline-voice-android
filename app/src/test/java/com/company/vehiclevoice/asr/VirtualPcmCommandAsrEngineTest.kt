package com.company.vehiclevoice.asr

import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.audio.VirtualTtsPcmSource
import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualPcmCommandAsrEngineTest {
    @Test
    fun recognizesCommandFromRenderedPcmFrames() {
        val frames = VirtualTtsPcmSource.framesForText("打开空调") + PcmFrame.silence(99)
        val result = VirtualPcmCommandAsrEngine().recognize(frames)
        assertEquals("打开空调", result.text)
    }

    @Test
    fun unknownRenderedPcmReturnsFallback() {
        val frames = VirtualTtsPcmSource.framesForText("今天天气")
        val result = VirtualPcmCommandAsrEngine().recognize(frames)
        assertEquals("", result.text)
        assertEquals(0.0, result.confidence, 0.0)
    }
}
