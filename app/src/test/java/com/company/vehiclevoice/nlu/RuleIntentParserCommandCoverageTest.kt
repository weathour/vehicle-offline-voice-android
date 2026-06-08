package com.company.vehiclevoice.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleIntentParserCommandCoverageTest {
    private val parser = RuleIntentParser()

    @Test
    fun parsesInitialVehicleCommandSynonyms() {
        val cases = mapOf(
            "空调开开" to "air_conditioner_on",
            "关掉空调" to "air_conditioner_off",
            "降下车窗" to "window_open",
            "升起车窗" to "window_close",
            "太冷了" to "temperature_up",
            "太热了" to "temperature_down"
        )

        cases.forEach { (text, intent) ->
            val result = parser.parse(text)
            assertEquals(text, intent, result.intent.name)
            assertTrue(text, result.isActionable)
        }
    }

    @Test
    fun unsafeStillWinsOverActionableSynonyms() {
        val result = parser.parse("空调开开并上传数据")
        assertEquals("unsafe_rejected", result.intent.name)
        assertFalse(result.isActionable)
    }
}
