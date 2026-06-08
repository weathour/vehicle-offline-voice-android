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
    fun realMicPipelineKeepsOpenModelUntilSegmentedGrammarIsProven() {
        val source = File("src/main/java/com/company/vehiclevoice/core/VoicePipelineFactory.kt").readText()

        assertTrue(source.contains("asrEngine = VoskOfflineAsrEngine(modelPath = modelPath)"))
    }
}
