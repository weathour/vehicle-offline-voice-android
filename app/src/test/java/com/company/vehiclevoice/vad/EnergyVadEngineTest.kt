package com.company.vehiclevoice.vad

import com.company.vehiclevoice.audio.PcmFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyVadEngineTest {
    @Test
    fun allSilence_staysSilent() {
        val vad = EnergyVadEngine(speechRmsThreshold = 0.02, minSpeechFrames = 2, endSilenceFrames = 2)
        repeat(5) { index ->
            assertTrue(vad.accept(PcmFrame.silence(sequence = index.toLong())) is VadEvent.Silence)
        }
    }

    @Test
    fun oneFrameNoise_doesNotTriggerSpeechStart() {
        val vad = EnergyVadEngine(speechRmsThreshold = 0.02, minSpeechFrames = 2, endSilenceFrames = 2)
        val event = vad.accept(PcmFrame.constantTone(sequence = 1, amplitude = 10_000))
        assertTrue(event is VadEvent.Silence)
    }

    @Test
    fun sustainedSpeech_thenSilence_emitsStartAndEnd() {
        val vad = EnergyVadEngine(speechRmsThreshold = 0.02, minSpeechFrames = 2, endSilenceFrames = 2)
        val events = listOf(
            PcmFrame.constantTone(sequence = 1, amplitude = 10_000),
            PcmFrame.constantTone(sequence = 2, amplitude = 10_000),
            PcmFrame.constantTone(sequence = 3, amplitude = 10_000),
            PcmFrame.silence(sequence = 4),
            PcmFrame.silence(sequence = 5)
        ).map { vad.accept(it) }

        assertTrue(events[0] is VadEvent.Silence)
        assertTrue(events[1] is VadEvent.SpeechStart)
        assertTrue(events[2] is VadEvent.Speech)
        assertTrue(events[4] is VadEvent.SpeechEnd)
    }
    @Test
    fun speechStart_containsPreRollFramesSoAsrDoesNotLoseFirstActiveFrame() {
        val vad = EnergyVadEngine(speechRmsThreshold = 0.02, minSpeechFrames = 2, endSilenceFrames = 2)
        assertTrue(vad.accept(PcmFrame.constantTone(sequence = 10, amplitude = 10_000)) is VadEvent.Silence)
        val event = vad.accept(PcmFrame.constantTone(sequence = 11, amplitude = 10_000))
        assertTrue(event is VadEvent.SpeechStart)
        val start = event as VadEvent.SpeechStart
        assertEquals(listOf(10L, 11L), start.preRollFrames.map { it.sequence })
    }

}
