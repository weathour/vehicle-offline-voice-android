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
        assertTrue(manifest.contains("android.intent.action.TTS_SERVICE"))
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
    fun releasePath_showsServiceStatusAndLatestAsrTranscript() {
        val source = File("src/main/java/com/company/vehiclevoice/MainActivity.kt").readText()
        val releaseSurface = source.substringAfter("private fun buildContentView()")
            .substringBefore("val debugPanel = buildDebugPanel()")
        val feedbackSurface = source.substringAfter("private fun buildRuntimeFeedbackPanel()")
            .substringBefore("private fun buildRedisConfigPanel()")

        assertTrue(releaseSurface.contains("root.addView(buildStatusPanel()"))
        assertTrue(releaseSurface.contains("root.addView(buildRuntimeFeedbackPanel()"))
        assertTrue(feedbackSurface.contains("asrPanel = debugLine(\"最近听到\", \"尚无识别结果\")"))
        assertTrue(feedbackSurface.contains("addView(asrPanel"))
        assertTrue(source.contains("if (\"ASR text=\" in line)"))
        assertTrue(source.contains("未识别到语音"))
    }

    @Test
    fun releasePath_rejectsDirectActivityStartsAndMovesBehindAfterServiceStart() {
        val source = File("src/main/java/com/company/vehiclevoice/MainActivity.kt").readText()

        assertTrue(source.contains("eventIntent?.action == Intent.ACTION_MAIN"))
        assertTrue(source.contains("Intent.CATEGORY_LAUNCHER"))
        assertTrue(source.contains("Rejected non-launcher MainActivity start"))
        assertTrue(source.contains("moveTaskToBack(true)"))
        assertTrue(source.contains("PREF_ACTIVITY_HISTORY"))
    }

    @Test
    fun foregroundService_redeliversConfigurationAndRecoversUnexpectedPipelineStops() {
        val service = File("src/main/java/com/company/vehiclevoice/VoiceForegroundService.kt").readText()
        val audio = File("src/main/java/com/company/vehiclevoice/audio/AndroidAudioRecordSource.kt").readText()

        assertTrue(service.contains("return START_REDELIVER_INTENT"))
        assertFalse(service.contains("return START_STICKY"))
        assertTrue(service.contains("intent == null"))
        assertTrue(service.contains("checkPipelineHealth()"))
        assertTrue(service.contains("MAX_RECOVERY_ATTEMPTS = 3"))
        assertTrue(service.contains("keys = VehicleRedisKeys.fourQueryKeys"))
        assertTrue(audio.contains("check(count > 0)"))
    }
}
