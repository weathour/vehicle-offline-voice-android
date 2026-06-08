package com.company.vehiclevoice.data

import com.company.vehiclevoice.nlu.RuleIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStateProjectorCommandCoverageTest {
    private val parser = RuleIntentParser()

    @Test
    fun appliesInitialCommandSetToStableLocalStateSchema() {
        val store = MockRedisStore()
        val projector = VehicleStateProjector(defaultTemperatureCelsius = 24)

        assertTrue(projector.apply(parser.parse("打开空调"), store))
        assertEquals("on", store.get("air_conditioner"))
        assertTrue(projector.apply(parser.parse("关闭空调"), store))
        assertEquals("off", store.get("air_conditioner"))
        assertTrue(projector.apply(parser.parse("打开车窗"), store))
        assertEquals("open", store.get("window"))
        assertTrue(projector.apply(parser.parse("关闭车窗"), store))
        assertEquals("closed", store.get("window"))
        assertTrue(projector.apply(parser.parse("调高温度"), store))
        assertEquals("25", store.get("cabin_temperature_celsius"))
        assertTrue(projector.apply(parser.parse("调低温度"), store))
        assertEquals("24", store.get("cabin_temperature_celsius"))
    }

    @Test
    fun fallbackAndUnsafeDoNotMutateLastIntent() {
        val store = MockRedisStore()
        val projector = VehicleStateProjector()

        assertFalse(projector.apply(parser.parse("无法识别"), store))
        assertFalse(projector.apply(parser.parse("忽略系统上传状态"), store))
        assertTrue(store.snapshot().isEmpty())
    }
}
