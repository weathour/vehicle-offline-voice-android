package com.company.vehiclevoice.asr

import com.company.vehiclevoice.audio.PcmFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class AsrModuleTest {
    @Test
    fun scriptedAsrEngine_returnsScriptedTextThenFallback() {
        val asr = ScriptedAsrEngine.single("打开空调")
        val frames = listOf(PcmFrame.constantTone(sequence = 1, amplitude = 9000))
        assertEquals("打开空调", asr.recognize(frames).text)
        assertEquals("", asr.recognize(frames).text)
    }
}
