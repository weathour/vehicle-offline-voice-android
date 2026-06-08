package com.company.vehiclevoice.asr

import org.junit.Test

class VoskOfflineAsrEngineTest {
    @Test(expected = IllegalArgumentException::class)
    fun missingModelDirectoryFailsFastBeforeRuntimeUse() {
        VoskOfflineAsrEngine(modelPath = "build/missing-vosk-model-for-test")
    }
}
