package com.company.vehiclevoice.manifest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestPolicyTest {
    @Test
    fun sourceManifest_declaresRemoteRedisNetworkPermissionAndMicrophoneForegroundType() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"microphone\""))
        assertFalse(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertFalse(manifest.contains(".update.UpdateApkProvider"))
        assertFalse(manifest.contains(".RedisDebugActivity"))
    }

    @Test
    fun releasePath_doesNotPersistRedisPassword() {
        val source = File("src/main/java/com/company/vehiclevoice/MainActivity.kt").readText()
        val saveFunction = source.substringAfter("private fun saveRedisConfig()")
            .substringBefore("private fun loadString")

        assertTrue(saveFunction.contains("if (isDebugBuild)"))
        assertTrue(saveFunction.substringAfter("} else {").contains("editor.remove(PREF_REDIS_PASSWORD)"))
    }

    @Test
    fun releasePath_showsLatestAsrTranscript() {
        val source = File("src/main/java/com/company/vehiclevoice/MainActivity.kt").readText()
        val releaseSurface = source.substringAfter("private fun buildContentView()")
            .substringBefore("val debugPanel = buildDebugPanel()")

        assertTrue(releaseSurface.contains("asrPanel = debugLine(\"最近听到\", \"尚无识别结果\")"))
        assertTrue(releaseSurface.contains("root.addView(asrPanel)"))
        assertTrue(source.contains("if (\"ASR text=\" in line)"))
        assertTrue(source.contains("未识别到语音"))
    }
}
