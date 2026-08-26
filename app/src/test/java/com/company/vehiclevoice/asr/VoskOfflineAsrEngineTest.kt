package com.company.vehiclevoice.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class VoskOfflineAsrEngineTest {
    @Test(expected = IllegalArgumentException::class)
    fun missingModelDirectoryFailsFastBeforeRuntimeUse() {
        VoskOfflineAsrEngine(modelPath = "build/missing-vosk-model-for-test")
    }

    @Test
    fun parsesNBestJsonAndKeepsNativeLikelihoodValues() {
        val result = VoskOfflineAsrEngine.parseResultJson(
            """{"alternatives":[{"text":"车 速 多 少","confidence":86.93486},{"text":"电 量 多 少","confidence":86.55477}]}"""
        )

        assertEquals("车 速 多 少", result.text)
        assertEquals(86.93486, result.confidence, 0.00001)
        assertEquals(listOf("车 速 多 少", "电 量 多 少"), result.alternatives.map { it.text })
    }
}
