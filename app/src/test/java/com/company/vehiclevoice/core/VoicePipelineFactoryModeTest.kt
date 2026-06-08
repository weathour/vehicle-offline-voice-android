package com.company.vehiclevoice.core

import com.company.vehiclevoice.log.RecordingEventLogSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePipelineFactoryModeTest {
    @Test
    fun virtualMicSmokePipeline_runsThroughExistingMockChainUsingVirtualAudioSource() {
        val logSink = RecordingEventLogSink()
        val result = VoicePipelineFactory.createServicePipeline(
            mode = VoiceRuntimeMode.VirtualMicSmoke,
            logSink = logSink
        ).runUntilSourceEnds()

        assertTrue(result.wakeDetected)
        assertEquals("air_conditioner_on", result.intentName)
        assertTrue(logSink.lines().any { it.contains("KWS wake") })
    }
}
