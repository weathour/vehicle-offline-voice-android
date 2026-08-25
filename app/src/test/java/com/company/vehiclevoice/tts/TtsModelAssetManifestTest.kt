package com.company.vehiclevoice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sqrt

class TtsModelAssetManifestTest {
    @Test
    fun committedOfflineTtsArtifactsMatchManifest() {
        val modelDir = File("src/main/tts-model/${SherpaOfflineTtsEngine.MODEL_ID}")
        val manifest = File(modelDir, "MODEL_MANIFEST.json").readText()
        val sums = File(modelDir, "SHA256SUMS")
        val entries = sums.readLines().map { line ->
            line.substringBefore("  ") to line.substringAfter("  ")
        }

        assertEquals(261, entries.size)
        entries.forEach { (expectedSha, path) ->
            val file = File(modelDir, path)
            assertTrue("Missing TTS asset: $path", file.isFile)
            assertEquals("Unexpected SHA-256 for $path", expectedSha, sha256(file))
        }
        assertTrue(manifest.contains(sha256(sums)))
        assertTrue(manifest.contains("\"sample_rate_hz\": 24000"))
        assertTrue(manifest.contains("\"languages\": [\"zh-CN\", \"en-US\"]"))
        assertFalse(File("src/main/assets/tts/vits-icefall-zh-aishell3").exists())

        val packagedModel = File(
            "build/generated/kokoroAssets/tts/${SherpaOfflineTtsEngine.MODEL_ID}/model.int8.onnx"
        )
        assertEquals(114_299_010L, packagedModel.length())
        assertEquals(
            "bda15858163726a492d02a9a727bc263551b86ac77f90812c4b30ff41d380e26",
            sha256(packagedModel)
        )

        val aar = File("libs/sherpa-onnx-static-link-onnxruntime-${SherpaOfflineTtsEngine.ENGINE_VERSION}.aar")
        assertTrue(aar.isFile)
        assertEquals(
            "01e87037afca2ed49085062aace5c012e60321e8e23e3a72b6d9ac02c843f66c",
            sha256(aar)
        )
    }

    @Test
    fun fixedFallbackPromptsArePackagedWavFiles() {
        listOf("voice_wake_ack.wav", "voice_tts_unavailable.wav").forEach { name ->
            val file = File("src/main/res/raw/$name")
            assertTrue(file.length() > 44)
            val header = file.inputStream().use { it.readNBytes(28) }
            assertEquals("RIFF", String(header.copyOfRange(0, 4), Charsets.US_ASCII))
            assertEquals(24_000, ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(24))
        }
    }

    @Test
    fun playbackNormalizationRaisesQuietSpeechWithoutClipping() {
        val samples = FloatArray(1_000) { if (it % 2 == 0) 0.05f else -0.05f }

        normalizeTtsSamples(samples)

        val rms = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size)
        assertEquals(0.14, rms, 0.001)
        assertTrue(samples.all { it in -0.92f..0.92f })
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
