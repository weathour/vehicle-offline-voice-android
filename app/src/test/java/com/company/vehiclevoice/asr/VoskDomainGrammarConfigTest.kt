package com.company.vehiclevoice.asr

import com.company.vehiclevoice.nlu.RuleIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoskDomainGrammarConfigTest {
    @Test
    fun sixQueryGrammarIsSegmentedAndMapsOnlyToTheApprovedIntents() {
        val parser = RuleIntentParser(allowedIntentNames = RuleIntentParser.SIX_QUERY_INTENTS)
        val parsed = VoskOfflineAsrEngine.SIX_QUERY_GRAMMAR.associateWith { phrase ->
            parser.parse(phrase).intent.name
        }

        assertTrue(parsed.keys.all { it.contains(' ') })
        assertFalse(parsed.entries.any { it.value == "fallback" })
        assertEquals(RuleIntentParser.SIX_QUERY_INTENTS, parsed.values.toSet())
    }

    @Test
    fun sixQueryGrammarIncludesKnownConfusionsButNoSingleSoundShortcuts() {
        listOf(
            "车 素 多 少",
            "店 量 多 少",
            "张 碍 物 情 况",
            "山 姆 状 态",
            "归 迹 点",
            "红 路 灯 状 态"
        ).forEach { phrase ->
            assertTrue("missing grammar phrase $phrase", VoskOfflineAsrEngine.SIX_QUERY_GRAMMAR.contains(phrase))
        }
        assertFalse(VoskOfflineAsrEngine.SIX_QUERY_GRAMMAR.any { it in setOf("u", "灯", "电", "点") })
    }

    @Test
    fun realMicPipelineUsesTheSegmentedGrammarAndRestrictedParser() {
        val source = File("src/main/java/com/company/vehiclevoice/core/VoicePipelineFactory.kt").readText()

        assertTrue(source.contains("grammar = VoskOfflineAsrEngine.SIX_QUERY_GRAMMAR"))
        assertTrue(source.contains("RuleIntentParser(allowedIntentNames = RuleIntentParser.SIX_QUERY_INTENTS)"))
    }
}
