package com.company.vehiclevoice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TtsModelAssetManifestTest {
    @Test
    fun committedOfflineTtsArtifactsMatchManifest() {
        val modelDir = File("src/main/assets/tts/${SherpaOfflineTtsEngine.MODEL_ID}")
        val manifest = File(modelDir, "MODEL_MANIFEST.json").readText()
        val entries = Regex(
            """\{\s*"path"\s*:\s*"(.*?)".*?"sha256"\s*:\s*"(.*?)".*?"bytes"\s*:\s*(\d+)""",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(manifest).map { match ->
            Triple(match.groupValues[1], match.groupValues[2], match.groupValues[3].toLong())
        }.toList()

        assertEquals(6, entries.size)
        entries.forEach { (path, expectedSha, expectedBytes) ->
            val file = File(modelDir, path)
            assertTrue("Missing TTS asset: $path", file.isFile)
            assertEquals("Unexpected size for $path", expectedBytes, file.length())
            assertEquals("Unexpected SHA-256 for $path", expectedSha, sha256(file))
        }
        assertFalse(File(modelDir, "rule.far").exists())

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
            assertEquals("RIFF", file.inputStream().use { String(it.readNBytes(4), Charsets.US_ASCII) })
        }
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
