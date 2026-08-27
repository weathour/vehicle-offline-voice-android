package com.company.vehiclevoice.asr

import com.company.vehiclevoice.nlu.RuleIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoskDomainGrammarConfigTest {
    @Test
    fun fourQueryGrammarIsSegmentedAndMapsOnlyToTheApprovedIntents() {
        val parser = RuleIntentParser(allowedIntentNames = RuleIntentParser.FOUR_QUERY_INTENTS)
        val parsed = VoskOfflineAsrEngine.FOUR_QUERY_GRAMMAR.associateWith { phrase ->
            parser.parse(phrase).intent.name
        }

        assertTrue(parsed.keys.all { it.contains(' ') })
        assertFalse(parsed.entries.any { it.value == "fallback" })
        assertEquals(RuleIntentParser.FOUR_QUERY_INTENTS, parsed.values.toSet())
    }

    @Test
    fun fourQueryGrammarIncludesKnownConfusionsButNoRemovedTopicsOrSingleSoundShortcuts() {
        listOf(
            "车 素 多 少",
            "张 碍 物 情 况",
            "山 姆 状 态",
            "红 路 灯 状 态"
        ).forEach { phrase ->
            assertTrue("missing grammar phrase $phrase", VoskOfflineAsrEngine.FOUR_QUERY_GRAMMAR.contains(phrase))
        }
        assertFalse(VoskOfflineAsrEngine.FOUR_QUERY_GRAMMAR.any { it in setOf("u", "灯", "电", "点") })
        assertFalse(VoskOfflineAsrEngine.FOUR_QUERY_GRAMMAR.any { "电 量" in it || "轨 迹" in it })
    }

    @Test
    fun realMicPipelineUsesTheSegmentedGrammarAndRestrictedParser() {
        val source = File("src/main/java/com/company/vehiclevoice/core/VoicePipelineFactory.kt").readText()

        assertTrue(source.contains("grammar = VoskOfflineAsrEngine.FOUR_QUERY_GRAMMAR"))
        assertTrue(source.contains("RuleIntentParser(allowedIntentNames = RuleIntentParser.FOUR_QUERY_INTENTS)"))
    }
}
