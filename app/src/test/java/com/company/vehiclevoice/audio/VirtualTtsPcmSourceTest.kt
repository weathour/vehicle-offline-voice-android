package com.company.vehiclevoice.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualTtsPcmSourceTest {
    @Test
    fun virtualTtsSource_emitsSilenceWakeCommandAndTrailingSilence() {
        val source = VirtualTtsPcmSource.singleCommand("打开空调", wakePhrase = "你好车机")
        source.start()
        val frames = generateSequence { source.read() }.toList()
        source.stop()

        assertFalse(source.isStarted)
        assertEquals(2 + "你好车机".length + 1 + "打开空调".length + 3, frames.size)
        assertTrue(frames.first().isSilent())
        assertTrue(frames.last().isSilent())
        assertTrue(frames.any { !it.isSilent() })
    }

    @Test
    fun virtualRenderer_isDeterministicAndTextDependent() {
        val first = VirtualTtsPcmSource.framesForText("打开空调")
        val second = VirtualTtsPcmSource.framesForText("打开空调")
        val different = VirtualTtsPcmSource.framesForText("关闭空调")

        assertEquals(first.map { it.rms() }, second.map { it.rms() })
        assertNotEquals(first.map { it.rms() }, different.map { it.rms() })
        assertNotNull(first.firstOrNull())
    }
}
