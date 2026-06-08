package com.company.vehiclevoice.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioModuleTest {
    @Test
    fun pcmFrame_rmsForSilenceIsZero() {
        val frame = PcmFrame.silence(sequence = 1, sampleCount = 4)
        assertEquals(0.0, frame.rms(), 0.0)
        assertTrue(frame.isSilent())
    }

    @Test
    fun pcmFrame_rmsForConstantAmplitudeIsStable() {
        val frame = PcmFrame.constantTone(sequence = 2, amplitude = 1000, sampleCount = 8)
        assertEquals(1000.0, frame.rms(), 0.0001)
        assertEquals(8, frame.sampleCount)
        assertEquals(PcmFrame.DEFAULT_SAMPLE_RATE_HZ, frame.sampleRateHz)
    }

    @Test
    fun fakePcmSource_startReadStopLifecycle() {
        val first = PcmFrame.silence(sequence = 1)
        val second = PcmFrame.constantTone(sequence = 2, amplitude = 900)
        val source = FakePcmSource(listOf(first, second))

        assertFalse(source.isStarted)
        source.start()
        assertTrue(source.isStarted)
        assertEquals(1L, source.read()?.sequence)
        assertEquals(2L, source.read()?.sequence)
        assertNull(source.read())
        source.stop()
        assertFalse(source.isStarted)
    }

    @Test(expected = SecurityException::class)
    fun androidAudioRecordSource_refusesStartWithoutRecordAudioPermission() {
        AndroidAudioRecordSource(permissionGranted = { false }).start()
    }
}
