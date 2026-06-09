package com.company.vehiclevoice.manifest

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestPolicyTest {
    @Test
    fun sourceManifest_declaresRemoteRedisNetworkPermissionAndMicrophoneForegroundType() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"microphone\""))
        assertTrue(manifest.contains(".update.UpdateApkProvider"))
    }
}
