package com.company.vehiclevoice.kws

import org.junit.Test

class VoskKeywordSpotterTest {
    @Test(expected = IllegalArgumentException::class)
    fun missingModelDirectoryFailsFastBeforeRuntimeUse() {
        VoskKeywordSpotter(modelPath = "build/missing-vosk-model-for-test")
    }
}
