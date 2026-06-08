package com.company.vehiclevoice.action

import com.company.vehiclevoice.nlu.RuleIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityActionTest {
    @Test
    fun mapsActionableIntentToStableJson() {
        val parse = RuleIntentParser().parse("打开空调")
        val action = UnityActionMapper(clockMs = { 123L }).map(parse)
        val json = UnityActionJsonEncoder().encode(action!!)

        assertEquals(
            "{\"type\":\"vehicle_action\",\"action\":\"air_conditioner_on\",\"slots\":{\"target\":\"air_conditioner\",\"operation\":\"on\"},\"source\":\"voice\",\"timestamp\":123}",
            json
        )
    }

    @Test
    fun fallbackIntentDoesNotCreateUnityAction() {
        val parse = RuleIntentParser().parse("无法识别")
        assertNull(UnityActionMapper(clockMs = { 123L }).map(parse))
    }

    @Test
    fun jsonEncoderEscapesQuotesBackslashesAndUnicodeText() {
        val json = UnityActionJsonEncoder().encode(
            UnityAction(action = "window_open", slots = mapOf("target" to "车窗\"左\\侧"), timestampMs = 9L)
        )
        assertTrue(json.contains("车窗\\\"左\\\\侧"))
    }
}
