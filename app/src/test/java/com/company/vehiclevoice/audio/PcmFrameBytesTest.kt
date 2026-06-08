package com.company.vehiclevoice.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PcmFrameBytesTest {
    @Test
    fun convertsSamplesToLittleEndianPcm16Bytes() {
        val frame = PcmFrame(samples = shortArrayOf(0x0102, (-2).toShort()), sequence = 1)
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0xfe.toByte(), 0xff.toByte()), frame.toLittleEndianPcm16Bytes())
    }
}
