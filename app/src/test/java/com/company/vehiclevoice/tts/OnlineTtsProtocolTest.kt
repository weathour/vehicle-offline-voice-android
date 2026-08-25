package com.company.vehiclevoice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OnlineTtsProtocolTest {
    @Test
    fun tencentTc3SigningMatchesFixedVector() {
        val authorization = TencentTc3Signer.authorization(
            secretId = "AKIDEXAMPLE",
            secretKey = "SECRETKEYEXAMPLE",
            payload = "{\"Text\":\"语音测试\"}",
            timestamp = 1_551_113_065L
        )

        assertEquals(
            "TC3-HMAC-SHA256 Credential=AKIDEXAMPLE/2019-02-25/tts/tc3_request, " +
                "SignedHeaders=content-type;host, " +
                "Signature=29ec0826a633ef7fd5f8917734b6abc10388ea4a50f5d3676f6f6cad919ace5c",
            authorization
        )
    }

    @Test
    fun edgeCanSynthesizeWhenOnlineSmokeIsEnabled() {
        assumeTrue(System.getenv("RUN_ONLINE_TTS_TESTS") == "1")

        val audio = EdgeTtsClient(EdgeTtsConfig()).synthesize("语音测试，V2X connection is ready。")

        assertEquals(".mp3", audio.fileSuffix)
        assertTrue(audio.bytes.size > 1_000)
    }

    @Test
    fun longRepliesSplitAtNaturalBoundariesWithoutLosingText() {
        val text = "第一段车辆状态正常。" + "协作车辆数据".repeat(30) + "，最后一段。"

        val chunks = splitOnlineTtsText(text)

        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 140 })
    }
}
