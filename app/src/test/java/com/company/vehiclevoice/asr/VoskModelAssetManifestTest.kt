package com.company.vehiclevoice.asr

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoskModelAssetManifestTest {
    @Test
    fun committedModelManifestValidatesCriticalFiles() {
        val modelDir = File("src/main/assets/model-cn")
        assertTrue(VoskModelAssetInstaller.isValidModel(modelDir))
    }
}
