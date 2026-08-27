package com.company.vehiclevoice.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskableVoiceContentTest {
    private val parser = RuleIntentParser(allowedIntentNames = RuleIntentParser.FOUR_QUERY_INTENTS)

    @Test
    fun visibleSupportedQuestionsMatchParserIntents() {
        AskableVoiceContent.supportedQuestions.forEach { question ->
            val result = parser.parse(question.phrase)

            assertEquals(question.phrase, question.intent, result.intent.name)
            assertFalse(question.phrase, result.isActionable)
        }
    }

    @Test
    fun visibleContentIsExactlyTheFourApprovedReadOnlyQueries() {
        assertEquals(1, AskableVoiceContent.categories.size)
        assertEquals("四类只读查询", AskableVoiceContent.categories.single().title)
        assertEquals(4, AskableVoiceContent.supportedQuestions.size)
        assertEquals(RuleIntentParser.FOUR_QUERY_INTENTS, AskableVoiceContent.supportedQuestions.map { it.intent }.toSet())
        assertFalse(AskableVoiceContent.supportedQuestions.any { it.phrase.contains("电量") || it.phrase.contains("轨迹") })
    }

    @Test
    fun visibleQuestionsContainNoDeferredEntries() {
        assertTrue(AskableVoiceContent.supportedQuestions.all { !it.intent.startsWith("deferred_") })
    }
}
