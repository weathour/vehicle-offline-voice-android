package com.company.vehiclevoice.asr

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoskDomainGrammarConfigTest {
    @Test
    fun defaultCommandGrammarIncludesCooperationAsrConfusions() {
        assertTrue(VoskOfflineAsrEngine.DEFAULT_COMMAND_GRAMMAR.contains("当前协作场景是什么"))
        assertTrue(VoskOfflineAsrEngine.DEFAULT_COMMAND_GRAMMAR.contains("当前写作场景是什么"))
        assertTrue(VoskOfflineAsrEngine.DEFAULT_COMMAND_GRAMMAR.contains("协同场景是什么"))
        assertTrue(VoskOfflineAsrEngine.DEFAULT_COMMAND_GRAMMAR.contains("合作场景是什么"))
    }

    @Test
    fun defaultCommandGrammarIncludesRealVehicleRedisAndHomophoneTerms() {
        listOf(
            "Redis状态",
            "数据新鲜度",
            "Key诊断",
            "RTK状态",
            "SAM状态",
            "车道线状态",
            "规划轨迹",
            "瑞迪斯状态",
            "二梯开状态",
            "山姆状态",
            "车到线状态",
            "归迹点"
        ).forEach { phrase ->
            assertTrue("missing grammar phrase $phrase", VoskOfflineAsrEngine.DEFAULT_COMMAND_GRAMMAR.contains(phrase))
        }
    }

    @Test
    fun realMicPipelineKeepsOpenModelUntilSegmentedGrammarIsProven() {
        val source = File("src/main/java/com/company/vehiclevoice/core/VoicePipelineFactory.kt").readText()

        assertTrue(source.contains("asrEngine = VoskOfflineAsrEngine(modelPath = modelPath)"))
    }
}
