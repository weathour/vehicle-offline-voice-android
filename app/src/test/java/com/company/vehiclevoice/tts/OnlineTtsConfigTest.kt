package com.company.vehiclevoice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineTtsConfigTest {
    @Test
    fun roundTripKeepsProviderSettings() {
        val expected = OnlineTtsConfig(
            defaultProvider = TtsProvider.Baidu,
            baidu = BaiduTtsConfig("app", "api", "secret", voice = 3, volume = 12),
            tencent = TencentTtsConfig("id", "key", voiceType = 101001)
        )

        assertEquals(expected, OnlineTtsConfig.parse(expected.toJsonString()))
    }

    @Test
    fun rejectsUnknownProviderAndExampleSecrets() {
        assertThrows(IllegalArgumentException::class.java) {
            OnlineTtsConfig.parse("""{"version":1,"defaultProvider":"unknown"}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnlineTtsConfig.parse(
                """{"version":1,"defaultProvider":"baidu","baidu":{"appId":"YOUR_APP_ID","apiKey":"api","secretKey":"secret"}}"""
            )
        }
    }
}
