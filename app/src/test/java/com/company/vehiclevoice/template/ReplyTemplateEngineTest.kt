package com.company.vehiclevoice.template

import com.company.vehiclevoice.data.MockRedisStore
import com.company.vehiclevoice.nlu.RuleIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyTemplateEngineTest {
    @Test
    fun rendersActionAndFallbackReplies() {
        val engine = ReplyTemplateEngine()
        val store = MockRedisStore()
        val parser = RuleIntentParser()

        assertEquals("已为你打开空调", engine.render(parser.parse("打开空调"), store))
        assertEquals("没有识别到有效指令", engine.render(parser.parse("无法识别"), store))
    }

    @Test
    fun rendersStatusFromStore() {
        val engine = ReplyTemplateEngine()
        val store = MockRedisStore(mapOf("air_conditioner" to "on"))
        val reply = engine.render(RuleIntentParser().parse("查询状态"), store)
        assertTrue(reply.contains("air_conditioner=on"))
    }
}
